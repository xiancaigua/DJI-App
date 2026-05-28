package com.example.uavmobile.dji

import android.util.Log
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.DroneController
import com.example.uavmobile.core.DroneState
import com.example.uavmobile.core.Waypoint
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.data.model.ConnectionConfig
import dji.sdk.keyvalue.key.DJIKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.v5.common.callback.CommonCallbacks
import dji.v5.manager.KeyManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object DjiDroneController : DroneController {
    override suspend fun connect(connectionConfig: ConnectionConfig?): ActionResult {
        Log.i(TAG, "Requesting DJI backend connect")
        DjiMsdkManager.retryRegisterIfNeeded("DjiDroneController.connect()")
        val state = DjiMsdkManager.initState.value
        return when (state) {
            DjiSdkInitState.REGISTERED -> {
                val snapshot = DjiConnectionManager.refreshFromKeyManager("DjiDroneController.connect")
                DjiConnectionManager.startConnectionMonitor("DjiDroneController.connect")
                if (snapshot.connected) {
                    ActionResult(true, "DJI 飞机已连接")
                } else {
                    ActionResult(
                        success = true,
                        message = "DJI SDK 已注册，正在等待飞机连接，连接监视器已启动。" +
                            " KeyConnection=${snapshot.keyConnectionValue}, productType=${snapshot.productType?.name ?: "无"}",
                    )
                }
            }

            DjiSdkInitState.SKIPPED,
            DjiSdkInitState.FAILED,
            -> ActionResult(false, DjiMsdkManager.describeStatus(), -1)

            else -> ActionResult(true, DjiMsdkManager.describeStatus())
        }
    }

    override suspend fun disconnect(): ActionResult {
        Log.i(TAG, "DJI disconnect requested; stopping local connection monitor")
        DjiConnectionManager.stopConnectionMonitor("DjiDroneController.disconnect")
        ObstacleAvoidanceSafetyManager.stopMissionMonitoring("DjiDroneController.disconnect")
        return ActionResult(
            success = true,
            message = "DJI SDK 生命周期仍由系统管理，已停止本地飞机连接监视器",
        )
    }

    override fun getState(): Result<DroneState> = DjiAircraftStateReader.getState()

    override suspend fun refreshMissions(): ActionResult {
        Log.i(TAG, "Refreshing local DJI mission and connection state")
        val snapshot = DjiConnectionManager.refreshFromKeyManager("refresh DJI state")
        DjiConnectionManager.startConnectionMonitor("refresh DJI state")
        val missionState = DjiWaypointMissionManager.missionState.value
        return ActionResult(
            success = true,
            message = "DJI 状态已刷新：" +
                "productConnected=${snapshot.connected}, " +
                "keyConnectionValue=${snapshot.keyConnectionValue}, " +
                "productType=${snapshot.productType?.name ?: "无"}, " +
                "monitorRunning=${snapshot.monitorRunning}, " +
                "missionState=${missionState.message}",
        )
    }

    override suspend fun uploadMission(
        missionId: String,
        waypoints: List<Waypoint>,
        selectedDjiAircraftFamily: DjiAircraftFamily,
    ): ActionResult {
        Log.i(
            TAG,
            "Preparing DJI mission $missionId with ${waypoints.size} waypoint(s), selectedFamily=$selectedDjiAircraftFamily",
        )
        val connectionError = ensureConnectedForMission("uploadMission")
        if (connectionError != null) {
            return connectionError
        }
        val prepared = DjiWaypointMissionManager.prepareMission(
            waypoints = waypoints,
            selectedAircraftFamily = selectedDjiAircraftFamily,
            missionId = missionId,
        )
        if (!prepared.success) {
            return prepared
        }
        return DjiWaypointMissionManager.uploadMission()
    }

    override suspend fun startMission(missionId: String?): ActionResult {
        val connectionError = ensureConnectedForMission("startMission")
        if (connectionError != null) {
            return connectionError
        }
        Log.i(TAG, "Starting DJI mission ${missionId.orEmpty()}")
        return DjiWaypointMissionManager.startMission()
    }

    override suspend fun pauseMission(missionId: String?): ActionResult {
        val connectionError = ensureConnectedForMission("pauseMission")
        if (connectionError != null) {
            return connectionError
        }
        Log.i(TAG, "Pausing DJI mission ${missionId.orEmpty()}")
        return DjiWaypointMissionManager.pauseMission()
    }

    override suspend fun resumeMission(missionId: String?): ActionResult {
        Log.w(TAG, "Resume mission is not supported in current DJI waypoint flow")
        return ActionResult(
            success = false,
            message = "当前 DJI 航点流程暂不支持继续，请使用停止/开始。",
            errorCode = -1,
        )
    }

    override suspend fun stopMission(missionId: String?): ActionResult {
        val connectionError = ensureConnectedForMission("stopMission")
        if (connectionError != null) {
            return connectionError
        }
        Log.i(TAG, "Stopping DJI mission ${missionId.orEmpty()}")
        return DjiWaypointMissionManager.stopMission()
    }

    override suspend fun returnHome(missionId: String?): ActionResult {
        val goHomeKey = KeyTools.createKey(FlightControllerKey.KeyStartGoHome)
        return performFlightAction(goHomeKey, "已请求 DJI 返航")
    }

    override suspend fun land(missionId: String?): ActionResult {
        val autoLandingKey = KeyTools.createKey(FlightControllerKey.KeyStartAutoLanding)
        return performFlightAction(autoLandingKey, "已请求 DJI 自动降落")
    }

    private suspend fun <P, R> performFlightAction(
        key: DJIKey.ActionKey<P, R>,
        successMessage: String,
    ): ActionResult {
        val connectionError = ensureConnectedForMission(successMessage)
        if (connectionError != null) {
            return connectionError
        }
        if (!DjiMsdkManager.isSdkPresent()) {
            return ActionResult(false, DjiMsdkManager.describeStatus(), -1)
        }

        val keyManager = KeyManager.getInstance()
            ?: return ActionResult(false, "DJI KeyManager 不可用", -1)

        return suspendCancellableCoroutine { continuation ->
            keyManager.performAction(key, object : CommonCallbacks.CompletionCallbackWithParam<R> {
                override fun onSuccess(result: R) {
                    continuation.resume(ActionResult(true, successMessage))
                }

                override fun onFailure(error: dji.v5.common.error.IDJIError) {
                    continuation.resume(
                        ActionResult(
                            success = false,
                            message = DjiErrorFormatter.describe(error),
                            errorCode = -1,
                        ),
                    )
                }
            })
        }
    }

    private fun ensureConnectedForMission(actionName: String): ActionResult? {
        if (DjiMsdkManager.initState.value != DjiSdkInitState.REGISTERED) {
            return ActionResult(
                false,
                "DJI SDK 尚未注册，不能执行 $actionName。当前状态=${DjiMsdkManager.initState.value}，${DjiMsdkManager.describeStatus()}",
                -1,
            )
        }

        val snapshot = if (DjiConnectionManager.isConnected()) {
            DjiConnectionManager.connectionState.value
        } else {
            DjiConnectionManager.refreshFromKeyManager("ensureConnectedForMission: $actionName")
        }
        if (!snapshot.connected) {
            DjiConnectionManager.startConnectionMonitor("ensureConnectedForMission: $actionName")
            Log.w(TAG, "DJI action $actionName rejected because no aircraft is connected")
            return ActionResult(
                false,
                "当前没有连接 DJI 飞机。KeyConnection=${snapshot.keyConnectionValue}, " +
                    "lastError=${snapshot.lastRefreshError.ifBlank { "无" }}",
                -1,
            )
        }
        return null
    }

    private const val TAG = "DjiDroneController"
}
