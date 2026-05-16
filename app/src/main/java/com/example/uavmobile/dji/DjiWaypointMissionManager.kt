package com.example.uavmobile.dji

import android.util.Log
import com.dji.wpmzsdk.common.data.Template
import com.dji.wpmzsdk.manager.WPMZManager
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.MissionExecutionSnapshot
import com.example.uavmobile.core.MissionExecutionState
import com.example.uavmobile.core.PreparedMission
import com.example.uavmobile.core.Waypoint
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.debug.DeveloperLogStore
import dji.sdk.wpmz.value.mission.WaylineAltitudeMode
import dji.sdk.wpmz.value.mission.WaylineCheckErrorMsg
import dji.sdk.wpmz.value.mission.WaylineCoordinateMode
import dji.sdk.wpmz.value.mission.WaylineCoordinateParam
import dji.sdk.wpmz.value.mission.WaylineDroneInfo
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostAction
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostBehavior
import dji.sdk.wpmz.value.mission.WaylineFinishedAction
import dji.sdk.wpmz.value.mission.WaylineFlyToWaylineMode
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate2D
import dji.sdk.wpmz.value.mission.WaylineMission
import dji.sdk.wpmz.value.mission.WaylineMissionConfig
import dji.sdk.wpmz.value.mission.WaylinePositioningType
import dji.sdk.wpmz.value.mission.WaylineTemplateWaypointInfo
import dji.sdk.wpmz.value.mission.WaylineWaypoint
import dji.sdk.wpmz.value.mission.WaylineWaypointGimbalHeadingMode
import dji.sdk.wpmz.value.mission.WaylineWaypointGimbalHeadingParam
import dji.sdk.wpmz.value.mission.WaylineWaypointPitchMode
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnMode
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnParam
import dji.sdk.wpmz.value.mission.WaylineWaypointYawMode
import dji.sdk.wpmz.value.mission.WaylineWaypointYawParam
import dji.sdk.wpmz.value.mission.WaylineWaypointYawPathMode
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager as SdkWaypointMissionManager
import dji.v5.manager.aircraft.waypoint3.model.WaylineExecutingInfo
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

object DjiWaypointMissionManager {
    private const val TAG = "DjiWaypointMissionManager"
    private const val DEFAULT_SPEED_MPS = 10.0
    private const val TEMPLATE_ID = 0

    private val _missionState = MutableStateFlow(MissionExecutionSnapshot())
    val missionState: StateFlow<MissionExecutionSnapshot> = _missionState.asStateFlow()

    private var preparedMission: PreparedMission? = null
    private var progressListenerInstalled = false

    fun prepareMission(
        waypoints: List<Waypoint>,
        selectedAircraftFamily: DjiAircraftFamily = DjiAircraftFamily.AUTO,
        missionId: String = "dji_mission_${System.currentTimeMillis()}",
    ): ActionResult {
        if (waypoints.size < 2) {
            return fail("DJI 任务当前至少需要 2 个航点")
        }
        if (!DjiMsdkManager.isMissionFeatureAvailable()) {
            return fail("DJI 任务运行时未就绪：${DjiMsdkManager.describeStatus()}")
        }

        val aircraftResolution = DjiAircraftResolver.resolve(
            currentProductType = DjiConnectionManager.currentProductType(),
            selectedAircraftFamily = selectedAircraftFamily,
        )
        when (aircraftResolution) {
            is DjiAircraftResolution.Supported -> {
                Log.i(TAG, aircraftResolution.message)
                DeveloperLogStore.info(TAG, "机型解析结果", aircraftResolution.message)
            }

            is DjiAircraftResolution.Missing -> {
                Log.e(TAG, aircraftResolution.message)
                return fail(aircraftResolution.message)
            }

            is DjiAircraftResolution.Unsupported -> {
                Log.e(TAG, aircraftResolution.message)
                return fail(aircraftResolution.message)
            }
        }

        return runCatching {
            _missionState.value = MissionExecutionSnapshot(
                state = MissionExecutionState.PREPARING,
                missionId = missionId,
                missionFileName = missionId,
                waypointCount = waypoints.size,
                message = "正在准备 DJI 任务包",
                selectedDjiAircraftFamily = selectedAircraftFamily.name,
                resolvedWaylineDroneType = aircraftResolution.resolvedAircraft.toWaylineDroneType().name,
                lastDjiWaypointAction = "prepareMission",
            )
            DeveloperLogStore.info(TAG, "正在准备 DJI 任务", "missionId=$missionId, waypoints=${waypoints.size}")

            val appContext = DjiMsdkManager.requireAppContext()
            val missionDir = appContext.getExternalFilesDir("dji-waypoint") ?: appContext.cacheDir
            if (!missionDir.exists()) {
                missionDir.mkdirs()
            }

            val kmzFile = File(missionDir, "$missionId.kmz")
            val wpmzManager = WPMZManager.getInstance().apply {
                init(appContext)
            }

            val mission = WaylineMission().apply {
                val now = System.currentTimeMillis().toDouble()
                createTime = now
                updateTime = now
                author = "Px4MobileClient"
            }

            val firstAltitude = waypoints.first().altM.toDouble()
            val missionConfig = WaylineMissionConfig().apply {
                flyToWaylineMode = WaylineFlyToWaylineMode.SAFELY
                finishAction = WaylineFinishedAction.NO_ACTION
                exitOnRCLostBehavior = WaylineExitOnRCLostBehavior.EXCUTE_RC_LOST_ACTION
                exitOnRCLostType = WaylineExitOnRCLostAction.GO_BACK
                globalTransitionalSpeed = DEFAULT_SPEED_MPS
                securityTakeOffHeight = firstAltitude
                isSecurityTakeOffHeightSet = true
                globalRTHHeight = firstAltitude
                isGlobalRTHHeightSet = true

                droneInfo = WaylineDroneInfo(aircraftResolution.resolvedAircraft.toWaylineDroneType(), 0)
            }

            val template = Template().apply {
                templateId = TEMPLATE_ID
                coordinateParam = WaylineCoordinateParam().apply {
                    coordinateMode = WaylineCoordinateMode.WGS84
                    altitudeMode = WaylineAltitudeMode.RELATIVE_TO_START_POINT
                    positioningType = WaylinePositioningType.GPS
                    isWaylinePositioningTypeSet = true
                }
                autoFlightSpeed = DEFAULT_SPEED_MPS
                transitionalSpeed = DEFAULT_SPEED_MPS
                useGlobalTransitionalSpeed = true
                waypointInfo = WaylineTemplateWaypointInfo().apply {
                    globalFlightHeight = firstAltitude
                    isGlobalFlightHeightSet = false
                    useStraightLine = true
                    pitchMode = WaylineWaypointPitchMode.MANUALLY
                    this.waypoints = waypoints.mapIndexed { index, waypoint ->
                        createTemplateWaypoint(waypoint, index)
                    }
                }
            }

            wpmzManager.generateKMZFile(kmzFile.absolutePath, mission, missionConfig, template)

            val validationErrors = wpmzManager.checkValidation(kmzFile.absolutePath).toValidationText()
            check(validationErrors.isEmpty()) {
                "KMZ validation failed: $validationErrors"
            }

            preparedMission = PreparedMission(
                missionId = missionId,
                missionFileName = missionId,
                kmzPath = kmzFile.absolutePath,
                waypointCount = waypoints.size,
            )
            _missionState.value = MissionExecutionSnapshot(
                state = MissionExecutionState.READY_TO_UPLOAD,
                missionId = missionId,
                missionFileName = missionId,
                waypointCount = waypoints.size,
                message = "DJI 任务已准备：${kmzFile.absolutePath}",
                selectedDjiAircraftFamily = selectedAircraftFamily.name,
                resolvedWaylineDroneType = aircraftResolution.resolvedAircraft.toWaylineDroneType().name,
                kmzPath = kmzFile.absolutePath,
                kmzFileExists = kmzFile.exists(),
                kmzFileSizeBytes = kmzFile.length(),
                lastDjiWaypointAction = "prepareMission",
                lastDjiWaypointActionSuccess = true,
            )
            DeveloperLogStore.info(TAG, "DJI 任务已准备", kmzFile.absolutePath)

            ActionResult(
                success = true,
                message = "已准备 DJI 任务 $missionId",
            )
        }.getOrElse { throwable ->
            fail(throwable.message ?: "准备 DJI 任务失败")
        }
    }

    suspend fun uploadMission(): ActionResult {
        val mission = preparedMission ?: return fail("上传前请先准备 DJI 任务")
        if (!DjiMsdkManager.isMissionFeatureAvailable()) {
            return fail("DJI 任务运行时未就绪：${DjiMsdkManager.describeStatus()}")
        }

        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.UPLOADING,
            message = "正在上传 ${mission.missionId}",
            lastDjiWaypointAction = "pushKMZFileToAircraft",
            lastDjiWaypointActionSuccess = null,
            lastDjiWaypointError = "",
            lastDjiWaypointErrorHint = "",
        )
        DeveloperLogStore.info(TAG, "正在上传 DJI 任务", mission.missionId)

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().pushKMZFileToAircraft(
                mission.kmzPath,
                object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                    override fun onProgressUpdate(progress: Double) {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.UPLOADING,
                            progress = progress,
                            message = "正在上传 ${mission.missionId}：${(progress * 100).toInt()}%",
                            lastDjiWaypointAction = "pushKMZFileToAircraft",
                        )
                    }

                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.UPLOADED,
                            progress = 1.0,
                            message = "已上传 ${mission.missionId}",
                            lastDjiWaypointAction = "pushKMZFileToAircraft",
                            lastDjiWaypointActionSuccess = true,
                        )
                        DeveloperLogStore.info(TAG, "DJI 任务已上传", mission.missionId)
                        continuation.resume(ActionResult(true, "已上传 DJI 任务 ${mission.missionId}"))
                    }

                    override fun onFailure(error: IDJIError) {
                        continuation.resume(
                            failResult(
                                message = "DJI 上传失败：${DjiErrorFormatter.describe(error)}",
                                action = "pushKMZFileToAircraft",
                            ),
                        )
                    }
                },
            )
        }
    }

    suspend fun startMission(): ActionResult {
        val mission = preparedMission ?: return fail("启动前请先准备 DJI 任务")
        ensureProgressListenerInstalled()
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.STARTING,
            message = "正在启动 DJI 任务 ${mission.missionId}",
            lastDjiWaypointAction = "startMission",
            lastDjiWaypointActionSuccess = null,
            lastDjiWaypointError = "",
            lastDjiWaypointErrorHint = "",
        )
        DeveloperLogStore.info(TAG, "正在启动 DJI 任务", mission.missionId)

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().startMission(
                mission.missionFileName,
                arrayListOf(),
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.RUNNING,
                            message = "DJI 任务 ${mission.missionId} 已启动",
                            lastDjiWaypointAction = "startMission",
                            lastDjiWaypointActionSuccess = true,
                        )
                        DeveloperLogStore.info(TAG, "DJI 任务已启动", mission.missionId)
                        continuation.resume(ActionResult(true, "已启动 DJI 任务 ${mission.missionId}"))
                    }

                    override fun onFailure(error: IDJIError) {
                        continuation.resume(
                            failResult(
                                message = "DJI startMission 失败：${DjiErrorFormatter.describe(error)}",
                                action = "startMission",
                            ),
                        )
                    }
                },
            )
        }
    }

    suspend fun pauseMission(): ActionResult {
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.PAUSED,
            message = "正在请求暂停 DJI 任务",
            lastDjiWaypointAction = "pauseMission",
            lastDjiWaypointActionSuccess = null,
            lastDjiWaypointError = "",
            lastDjiWaypointErrorHint = "",
        )
        DeveloperLogStore.info(TAG, "正在暂停 DJI 任务")

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().pauseMission(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.PAUSED,
                        message = "DJI 任务已暂停",
                        lastDjiWaypointAction = "pauseMission",
                        lastDjiWaypointActionSuccess = true,
                    )
                    DeveloperLogStore.info(TAG, "DJI 任务已暂停")
                    continuation.resume(ActionResult(true, "已暂停 DJI 任务"))
                }

                override fun onFailure(error: IDJIError) {
                    continuation.resume(
                        failResult(
                            message = "DJI pauseMission 失败：${DjiErrorFormatter.describe(error)}",
                            action = "pauseMission",
                        ),
                    )
                }
            })
        }
    }

    suspend fun stopMission(): ActionResult {
        val mission = preparedMission ?: return fail("停止前请先准备 DJI 任务")
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.STOPPING,
            message = "正在停止 DJI 任务 ${mission.missionId}",
            lastDjiWaypointAction = "stopMission",
            lastDjiWaypointActionSuccess = null,
            lastDjiWaypointError = "",
            lastDjiWaypointErrorHint = "",
        )
        DeveloperLogStore.info(TAG, "正在停止 DJI 任务", mission.missionId)

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().stopMission(
                mission.missionFileName,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.STOPPED,
                            message = "DJI 任务 ${mission.missionId} 已停止",
                            lastDjiWaypointAction = "stopMission",
                            lastDjiWaypointActionSuccess = true,
                        )
                        DeveloperLogStore.info(TAG, "DJI 任务已停止", mission.missionId)
                        continuation.resume(ActionResult(true, "已停止 DJI 任务 ${mission.missionId}"))
                    }

                    override fun onFailure(error: IDJIError) {
                        continuation.resume(
                            failResult(
                                message = "DJI stopMission 失败：${DjiErrorFormatter.describe(error)}",
                                action = "stopMission",
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun createTemplateWaypoint(
        waypoint: Waypoint,
        index: Int,
    ): WaylineWaypoint {
        return WaylineWaypoint().apply {
            waypointIndex = index
            location = WaylineLocationCoordinate2D(waypoint.lat, waypoint.lon)
            height = waypoint.altM.toDouble()
            useGlobalFlightHeight = false
            speed = DEFAULT_SPEED_MPS
            useGlobalAutoFlightSpeed = false
            useStraightLine = true
            yawParam = WaylineWaypointYawParam().apply {
                yawMode = WaylineWaypointYawMode.FOLLOW_WAYLINE
                enableYawAngle = false
                yawPathMode = WaylineWaypointYawPathMode.CLOCKWISE
            }
            isWaylineWaypointYawParamSet = true
            useGlobalYawParam = false
            gimbalHeadingParam = WaylineWaypointGimbalHeadingParam().apply {
                headingMode = WaylineWaypointGimbalHeadingMode.FOLLOW_WAYLINE
                yawAngle = 0.0
                pitchAngle = 0.0
            }
            isWaylineWaypointGimbalHeadingParamSet = true
            useGlobalGimbalHeadingParam = false
            turnParam = WaylineWaypointTurnParam().apply {
                turnMode = WaylineWaypointTurnMode.TO_POINT_AND_STOP_WITH_DISCONTINUITY_CURVATURE
                turnDampingDistance = 0.0
            }
            isWaylineWaypointTurnParamSet = true
            useGlobalTurnParam = false
            useGlobalActionGroup = true
            isRisky = false
        }
    }

    private fun WaylineCheckErrorMsg?.toValidationText(): String {
        if (this == null) {
            return ""
        }
        return value
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "; ") { it.toString() }
            .orEmpty()
    }

    private fun ensureProgressListenerInstalled() {
        if (progressListenerInstalled) {
            return
        }

        SdkWaypointMissionManager.getInstance().addWaylineExecutingInfoListener(object : WaylineExecutingInfoListener {
            override fun onWaylineExecutingInfoUpdate(info: WaylineExecutingInfo) {
                Log.i(
                    TAG,
                    "Mission ${info.missionFileName} is executing waypoint ${info.currentWaypointIndex}",
                )
                val waypointCount = _missionState.value.waypointCount.coerceAtLeast(1)
                val normalizedProgress = ((info.currentWaypointIndex + 1).toDouble() / waypointCount)
                    .coerceIn(0.0, 1.0)
                DeveloperLogStore.debug(
                    TAG,
                    "DJI 任务进度",
                    "mission=${info.missionFileName}, waypointIndex=${info.currentWaypointIndex}",
                )
                _missionState.value = _missionState.value.copy(
                    state = MissionExecutionState.RUNNING,
                    currentWaypointIndex = info.currentWaypointIndex,
                    progress = normalizedProgress,
                    message = "正在执行航点 ${info.currentWaypointIndex + 1}",
                    lastDjiWaypointAction = "missionProgress",
                )
            }

            override fun onWaylineExecutingInterruptReasonUpdate(error: IDJIError?) {
                if (error != null) {
                    DeveloperLogStore.error(TAG, "DJI 任务中断", DjiErrorFormatter.describe(error))
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.FAILED,
                        message = "任务中断：${DjiErrorFormatter.describe(error)}",
                        lastDjiWaypointAction = "missionInterrupted",
                        lastDjiWaypointActionSuccess = false,
                        lastDjiWaypointError = DjiErrorFormatter.describe(error),
                        lastDjiWaypointErrorHint = buildWaypointErrorHint(DjiErrorFormatter.describe(error)),
                    )
                }
            }
        })
        progressListenerInstalled = true
    }

    private fun fail(message: String): ActionResult {
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.FAILED,
            message = message,
            lastDjiWaypointAction = "prepareMission",
            lastDjiWaypointActionSuccess = false,
            lastDjiWaypointError = message,
            lastDjiWaypointErrorHint = buildWaypointErrorHint(message),
        )
        Log.e(TAG, message)
        DeveloperLogStore.error(TAG, message)
        return ActionResult(false, message, -1)
    }

    private fun failResult(
        message: String,
        action: String,
    ): ActionResult {
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.FAILED,
            message = message,
            lastDjiWaypointAction = action,
            lastDjiWaypointActionSuccess = false,
            lastDjiWaypointError = message,
            lastDjiWaypointErrorHint = buildWaypointErrorHint(message),
        )
        Log.e(TAG, message)
        DeveloperLogStore.error(TAG, message)
        return ActionResult(false, message, -1)
    }

    private fun buildWaypointErrorHint(message: String): String {
        val lower = message.lowercase()
        return when {
            "validation failed" in lower || "kmz" in lower && "failed" in lower ->
                "请检查 kmzPath、kmzFileExists、kmzFileSizeBytes、resolvedWaylineDroneType 和航点输入。"

            "registerapp" in lower || "runtime is not ready" in lower ->
                "请先检查 applicationId、DJI App Key、sdkInitState、网络和 DJI 注册状态。"

            "no dji product connected" in lower || "product connected" in lower ->
                "请检查遥控器与飞机连接、productConnected、productType 和 USB/RC 链路。"

            "upload" in lower || "pushkmzfiletoaircraft" in lower ->
                "请检查飞机是否已连接、kmzPath 是否存在，以及 KMZ 是否已成功准备。"

            "startmission" in lower ->
                "请检查 preparedMissionFileName、上传结果、电机状态、飞行状态、Home 点和产品状态。"

            "pausemission" in lower || "stopmission" in lower ->
                "请检查 DJI 任务是否正在运行，以及飞机是否仍然连接。"

            else ->
                "请按顺序检查 DJI 状态、产品连接、航点诊断和最近日志。"
        }
    }
}
