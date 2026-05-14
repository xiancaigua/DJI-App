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
            DjiSdkInitState.REGISTERED ->
                ActionResult(true, DjiMsdkManager.describeStatus())

            DjiSdkInitState.SKIPPED,
            DjiSdkInitState.FAILED,
            -> ActionResult(false, DjiMsdkManager.describeStatus(), -1)

            else -> ActionResult(true, DjiMsdkManager.describeStatus())
        }
    }

    override suspend fun disconnect(): ActionResult {
        Log.i(TAG, "DJI disconnect requested; lifecycle remains managed by SDK/product state")
        return ActionResult(
            success = true,
            message = "DJI connection lifecycle is managed by controller/aircraft state",
        )
    }

    override fun getState(): Result<DroneState> = DjiAircraftStateReader.getState()

    override suspend fun refreshMissions(): ActionResult {
        Log.i(TAG, "Refreshing local DJI mission state")
        return ActionResult(true, DjiWaypointMissionManager.missionState.value.message)
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
        val prepared = DjiWaypointMissionManager.prepareMission(
            waypoints = waypoints,
            selectedAircraftFamily = selectedDjiAircraftFamily,
            missionId = missionId,
        )
        if (!prepared.success) {
            return prepared
        }
        val connectionError = ensureConnectedForMission("uploadMission")
        if (connectionError != null) {
            return connectionError
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
            message = "DJI waypoint flow does not expose resumeMission yet; use Stop/Start on the current UI path.",
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
        return performFlightAction(goHomeKey, "Requested DJI return-to-home")
    }

    override suspend fun land(missionId: String?): ActionResult {
        val autoLandingKey = KeyTools.createKey(FlightControllerKey.KeyStartAutoLanding)
        return performFlightAction(autoLandingKey, "Requested DJI auto landing")
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
            ?: return ActionResult(false, "DJI KeyManager is not available", -1)

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
        if (!DjiConnectionManager.isConnected()) {
            Log.w(TAG, "DJI action $actionName rejected because no product is connected")
            return ActionResult(false, "No DJI product connected", -1)
        }
        return null
    }

    private const val TAG = "DjiDroneController"
}
