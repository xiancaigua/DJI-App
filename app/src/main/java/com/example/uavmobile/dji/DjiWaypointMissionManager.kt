package com.example.uavmobile.dji

import android.util.Log
import com.dji.wpmzsdk.manager.WPMZManager
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.hasValidCoordinates
import com.example.uavmobile.core.hasValidHomeCoordinates
import com.example.uavmobile.core.MissionExecutionSnapshot
import com.example.uavmobile.core.MissionExecutionState
import com.example.uavmobile.core.ObstacleAvoidanceSnapshot
import com.example.uavmobile.core.ObstacleDirection
import com.example.uavmobile.core.PreparedMission
import com.example.uavmobile.core.Waypoint
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.debug.DeveloperLogStore
import dji.sdk.wpmz.value.mission.AgricultureWorkMode
import dji.sdk.wpmz.value.mission.WaylineAltitudeMode
import dji.sdk.wpmz.value.mission.WaylineCheckErrorMsg
import dji.sdk.wpmz.value.mission.WaylineCoordinateMode
import dji.sdk.wpmz.value.mission.WaylineCoordinateParam
import dji.sdk.wpmz.value.mission.WaylineDroneInfo
import dji.sdk.wpmz.value.mission.Wayline
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostAction
import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostBehavior
import dji.sdk.wpmz.value.mission.WaylineExecuteAltitudeMode
import dji.sdk.wpmz.value.mission.WaylineExecuteCoordinateMode
import dji.sdk.wpmz.value.mission.WaylineExecuteWaypoint
import dji.sdk.wpmz.value.mission.WaylineFinishedAction
import dji.sdk.wpmz.value.mission.WaylineFlyToWaylineMode
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate3D
import dji.sdk.wpmz.value.mission.WaylineLocationCoordinate2D
import dji.sdk.wpmz.value.mission.WaylineMission
import dji.sdk.wpmz.value.mission.WaylineMissionConfig
import dji.sdk.wpmz.value.mission.WaylinePrecisionType
import dji.sdk.wpmz.value.mission.WaylinePositioningType
import dji.sdk.wpmz.value.mission.WaylineWaypointGimbalHeadingMode
import dji.sdk.wpmz.value.mission.WaylineWaypointGimbalHeadingParam
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnMode
import dji.sdk.wpmz.value.mission.WaylineWaypointTurnParam
import dji.sdk.wpmz.value.mission.WaylineWaypointYawMode
import dji.sdk.wpmz.value.mission.WaylineWaypointYawParam
import dji.sdk.wpmz.value.mission.WaylineWaypointYawPathMode
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaylineExecutingInfoListener
import dji.v5.manager.aircraft.waypoint3.WaypointMissionExecuteStateListener
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager as SdkWaypointMissionManager
import dji.v5.manager.aircraft.waypoint3.model.WaylineExecutingInfo
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState
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
    private const val DEFAULT_WAYLINE_ID = 0

    private val _missionState = MutableStateFlow(MissionExecutionSnapshot())
    val missionState: StateFlow<MissionExecutionSnapshot> = _missionState.asStateFlow()

    private var preparedMission: PreparedMission? = null
    private var progressListenerInstalled = false
    private var executeStateListenerInstalled = false
    private val progressListener = object : WaylineExecutingInfoListener {
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
                "mission=${info.missionFileName}, waypointIndex=${info.currentWaypointIndex}, waylineId=${info.waylineID}",
            )
            _missionState.value = _missionState.value.copy(
                state = MissionExecutionState.RUNNING,
                currentWaypointIndex = info.currentWaypointIndex,
                progress = normalizedProgress,
                message = "正在执行航点 ${info.currentWaypointIndex + 1}，${obstacleAvoidanceBrief()}",
                lastDjiWaypointAction = "missionProgress",
                sdkMissionExecuteState = if (_missionState.value.sdkMissionExecuteState.isBlank()) {
                    "EXECUTING"
                } else {
                    _missionState.value.sdkMissionExecuteState
                },
                obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
            )
        }

        override fun onWaylineExecutingInterruptReasonUpdate(error: IDJIError?) {
            if (error != null) {
                val errorText = DjiErrorFormatter.describe(error)
                val interruptionDiagnostics = buildInterruptionDiagnostics()
                val obstacleSnapshot = ObstacleAvoidanceSafetyManager.safetyState.value
                val interruptedByObstacle = obstacleSnapshot.appPauseRequested
                ObstacleAvoidanceSafetyManager.stopMissionMonitoring("mission interrupted reason")
                DeveloperLogStore.error(TAG, "DJI 任务中断", errorText)
                DeveloperLogStore.warn(TAG, "DJI 中断瞬间快照", interruptionDiagnostics)
                _missionState.value = _missionState.value.copy(
                    state = MissionExecutionState.FAILED,
                    message = if (interruptedByObstacle) {
                        "因近距离障碍物暂停任务：$errorText"
                    } else {
                        "任务中断：$errorText"
                    },
                    lastDjiWaypointAction = "missionInterrupted",
                    lastDjiWaypointActionSuccess = false,
                    lastDjiWaypointError = errorText,
                    lastDjiWaypointErrorHint = buildWaypointErrorHint(errorText),
                    lastInterruptionReason = errorText,
                    lastInterruptionDiagnostics = interruptionDiagnostics,
                    obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                )
            }
        }
    }
    private val executeStateListener = object : WaypointMissionExecuteStateListener {
        override fun onMissionStateUpdate(state: WaypointMissionExecuteState) {
            DeveloperLogStore.info(TAG, "DJI 任务执行状态", state.name)
            when (state) {
                WaypointMissionExecuteState.PREPARING -> {
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.PREPARING,
                        sdkMissionExecuteState = state.name,
                        message = "飞机正在准备任务，${obstacleAvoidanceBrief()}",
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                    )
                }

                WaypointMissionExecuteState.READY -> {
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.UPLOADED,
                        sdkMissionExecuteState = state.name,
                        message = "飞机任务已就绪，等待开始",
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                    )
                }

                WaypointMissionExecuteState.ENTER_WAYLINE,
                WaypointMissionExecuteState.EXECUTING,
                -> {
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.RUNNING,
                        sdkMissionExecuteState = state.name,
                        message = if (state == WaypointMissionExecuteState.ENTER_WAYLINE) {
                            "飞机正在进入航线，${obstacleAvoidanceBrief()}"
                        } else {
                            obstacleExecutingMessage()
                        },
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                    )
                }

                WaypointMissionExecuteState.INTERRUPTED -> {
                    val obstacleSnapshot = ObstacleAvoidanceSafetyManager.safetyState.value
                    val interruptedByObstacle = obstacleSnapshot.appPauseRequested
                    ObstacleAvoidanceSafetyManager.stopMissionMonitoring("mission interrupted")
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.FAILED,
                        sdkMissionExecuteState = state.name,
                        message = if (interruptedByObstacle) {
                            "因近距离障碍物暂停任务"
                        } else {
                            "DJI 任务已中断"
                        },
                        lastDjiWaypointAction = "missionStateUpdate",
                        lastDjiWaypointActionSuccess = false,
                        lastDjiWaypointError = if (interruptedByObstacle) {
                            "WaypointMissionExecuteState=INTERRUPTED by obstacle safety"
                        } else {
                            "WaypointMissionExecuteState=INTERRUPTED"
                        },
                        lastDjiWaypointErrorHint = if (interruptedByObstacle) {
                            "App 已因近距离障碍物请求暂停航线，请确认现场环境、飞机悬停状态和 DJI Pilot 2 感知告警。"
                        } else {
                            "请检查飞行模式、电机状态、Home 点、遥控器状态和 DJI 最近日志。"
                        },
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                    )
                }

                WaypointMissionExecuteState.FINISHED -> {
                    ObstacleAvoidanceSafetyManager.stopMissionMonitoring("mission finished")
                    val waypointCount = _missionState.value.waypointCount.coerceAtLeast(1)
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.FINISHED,
                        sdkMissionExecuteState = state.name,
                        currentWaypointIndex = waypointCount - 1,
                        progress = 1.0,
                        message = "DJI 任务已完成",
                        lastDjiWaypointAction = "missionStateUpdate",
                        lastDjiWaypointActionSuccess = true,
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                    )
                }

                WaypointMissionExecuteState.RECOVERING,
                WaypointMissionExecuteState.RETURN_TO_START_POINT,
                WaypointMissionExecuteState.UPLOADING,
                WaypointMissionExecuteState.DISCONNECTED,
                WaypointMissionExecuteState.IDLE,
                WaypointMissionExecuteState.NOT_SUPPORTED,
                WaypointMissionExecuteState.UNKNOWN,
                -> {
                    _missionState.value = _missionState.value.copy(
                        sdkMissionExecuteState = state.name,
                        message = when (state) {
                            WaypointMissionExecuteState.RECOVERING -> "DJI 任务恢复中"
                            WaypointMissionExecuteState.RETURN_TO_START_POINT -> "飞机正在返回任务起点"
                            WaypointMissionExecuteState.UPLOADING -> "飞机侧任务上传中"
                            WaypointMissionExecuteState.DISCONNECTED -> "任务管理器未连接"
                            WaypointMissionExecuteState.IDLE -> "任务管理器空闲"
                            WaypointMissionExecuteState.NOT_SUPPORTED -> "当前机型不支持该任务状态"
                            WaypointMissionExecuteState.UNKNOWN -> "任务状态未知"
                            else -> _missionState.value.message
                        },
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                    )
                }
            }
        }
    }

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
                currentWaypointIndex = -1,
                progress = 0.0,
                uploadProgress = 0.0,
                sdkMissionExecuteState = "",
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
            val aircraftState = DjiAircraftStateReader.getState().getOrNull()
            val currentLatitude = aircraftState?.latitude
            val currentLongitude = aircraftState?.longitude
            val hasValidReferenceLocation = currentLatitude != null &&
                currentLongitude != null &&
                currentLatitude in -90.0..90.0 &&
                currentLongitude in -180.0..180.0 &&
                !(currentLatitude == 0.0 && currentLongitude == 0.0)
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
                // WPMZ Java model leaves several list fields null by default.
                // Some SDK internals call size() directly, so initialize them eagerly.
                payloadInfo = emptyList()
                precisionType = WaylinePrecisionType.GPS
                isPrecisionSet = true
                if (hasValidReferenceLocation) {
                    val referenceLocation = WaylineLocationCoordinate3D(
                        currentLatitude,
                        currentLongitude,
                        0.0,
                    )
                    takeOffPositionRef = referenceLocation
                    isTakeOffPositionRefSet = true
                    startPositionRef = referenceLocation
                    isStartPositionRefSet = true
                }
            }

            val executeWaypoints = waypoints.mapIndexed { index, waypoint ->
                createExecuteWaypoint(waypoint, index)
            }
            val wayline = Wayline().apply {
                waylineId = DEFAULT_WAYLINE_ID
                templateId = TEMPLATE_ID
                autoFlightSpeed = DEFAULT_SPEED_MPS
                mode = WaylineExecuteAltitudeMode.RELATIVE_TO_START_POINT
                coordinateMode = WaylineExecuteCoordinateMode.WGS84
                this.waypoints = executeWaypoints
                actionGroups = emptyList()
                waylineStartActions = emptyList()
                agricultureWorkMode = AgricultureWorkMode.DEFAULT_MISSION
            }

            DeveloperLogStore.info(
                TAG,
                "WPMZ 参数已构建",
                "missionId=$missionId, waypointCount=${waypoints.size}, " +
                    "payloadInfo=${missionConfig.payloadInfo?.size ?: 0}, " +
                    "waylineId=${wayline.waylineId}, " +
                    "executeWaypointCount=${executeWaypoints.size}, " +
                    "referenceLocation=" + if (hasValidReferenceLocation) {
                        "$currentLatitude,$currentLongitude"
                    } else {
                        "无有效参考点"
                    } +
                    ", waypoints=" + executeWaypoints.joinToString(separator = " | ") { waypoint ->
                        "idx=${waypoint.waypointIndex}, lat=${waypoint.location?.latitude}, lon=${waypoint.location?.longitude}, h=${waypoint.executeHeight}"
                    },
            )

            wpmzManager.generateKMZFile(kmzFile.absolutePath, mission, missionConfig, wayline)

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
                currentWaypointIndex = -1,
                progress = 0.0,
                uploadProgress = 0.0,
                sdkMissionExecuteState = "",
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
            progress = 0.0,
            uploadProgress = 0.0,
            sdkMissionExecuteState = "",
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
                            progress = 0.0,
                            uploadProgress = progress,
                            sdkMissionExecuteState = "UPLOADING",
                            message = "正在上传 ${mission.missionId}：${(progress * 100).toInt()}%",
                            lastDjiWaypointAction = "pushKMZFileToAircraft",
                        )
                    }

                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.UPLOADED,
                            currentWaypointIndex = -1,
                            progress = 0.0,
                            uploadProgress = 1.0,
                            sdkMissionExecuteState = "READY",
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
        ensureExecuteStateListenerInstalled()
        val aircraftState = DjiAircraftStateReader.getState().getOrNull()
        val preflightFailure = validateMissionStartPreconditions(aircraftState)
        if (preflightFailure != null) {
            return failResult(
                message = preflightFailure,
                action = "startMissionPrecheck",
            )
        }
        val preflightWarnings = buildMissionStartWarnings(aircraftState)
        val obstaclePrepareResult = ObstacleAvoidanceSafetyManager.prepareForWaypointMission(
            missionId = mission.missionId,
            missionState = _missionState.value.sdkMissionExecuteState.ifBlank { _missionState.value.state.name },
        )
        _missionState.value = _missionState.value.copy(
            obstacleAvoidance = obstaclePrepareResult.snapshot,
        )
        if (!obstaclePrepareResult.success) {
            return failResult(
                message = obstaclePrepareResult.message,
                action = "obstacleAvoidancePrecheck",
            )
        }
        ObstacleAvoidanceSafetyManager.startMissionMonitoring(
            missionId = mission.missionId,
            isMissionExecuting = {
                _missionState.value.state == MissionExecutionState.RUNNING &&
                    _missionState.value.sdkMissionExecuteState in setOf(
                        WaypointMissionExecuteState.ENTER_WAYLINE.name,
                        WaypointMissionExecuteState.EXECUTING.name,
                    )
            },
            pauseAction = { pauseMission() },
            onSnapshotUpdate = ::updateObstacleAvoidanceSnapshot,
        )
        val obstacleWarnings = obstaclePrepareResult.warnings.joinToString(separator = "；")
        val selectedWaylineIds = SdkWaypointMissionManager.getInstance()
            .getAvailableWaylineIDs(mission.missionFileName)
            ?.ifEmpty { listOf(DEFAULT_WAYLINE_ID) }
            ?: listOf(DEFAULT_WAYLINE_ID)
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.STARTING,
            currentWaypointIndex = -1,
            progress = 0.0,
            message = "正在启动 DJI 任务 ${mission.missionId}",
            lastDjiWaypointAction = "startMission",
            lastDjiWaypointActionSuccess = null,
            lastDjiWaypointError = "",
            lastDjiWaypointErrorHint = "",
            obstacleAvoidance = obstaclePrepareResult.snapshot,
        )
        DeveloperLogStore.info(
            TAG,
            "正在启动 DJI 任务",
            "missionId=${mission.missionId}, waylineIds=$selectedWaylineIds, warnings=$preflightWarnings, obstacleWarnings=$obstacleWarnings",
        )
        if (preflightWarnings.isNotBlank()) {
            DeveloperLogStore.warn(TAG, "DJI 启动前提醒", preflightWarnings)
        }
        if (obstacleWarnings.isNotBlank()) {
            DeveloperLogStore.warn(TAG, "DJI 避障启动前提醒", obstacleWarnings)
        }

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().startMission(
                mission.missionFileName,
                selectedWaylineIds,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.STARTING,
                            currentWaypointIndex = -1,
                            progress = 0.0,
                            sdkMissionExecuteState = "START_COMMAND_ACCEPTED",
                            message = buildString {
                                append("DJI 任务启动命令已发送，等待飞机进入任务")
                                if (preflightWarnings.isNotBlank()) {
                                    append("；")
                                    append(preflightWarnings)
                                }
                                if (obstacleWarnings.isNotBlank()) {
                                    append("；避障提醒：")
                                    append(obstacleWarnings)
                                }
                            },
                            lastDjiWaypointAction = "startMission",
                            lastDjiWaypointActionSuccess = true,
                            obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                        )
                        DeveloperLogStore.info(TAG, "DJI 任务启动命令已发送", mission.missionId)
                        continuation.resume(ActionResult(true, "已启动 DJI 任务 ${mission.missionId}"))
                    }

                    override fun onFailure(error: IDJIError) {
                        ObstacleAvoidanceSafetyManager.stopMissionMonitoring("startMission failed")
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
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
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
                    ObstacleAvoidanceSafetyManager.stopMissionMonitoring("stopMission success")
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.STOPPED,
                        message = "DJI 任务 ${mission.missionId} 已停止",
                        lastDjiWaypointAction = "stopMission",
                        lastDjiWaypointActionSuccess = true,
                        obstacleAvoidance = ObstacleAvoidanceSafetyManager.safetyState.value,
                    )
                    DeveloperLogStore.info(TAG, "DJI 任务已停止", mission.missionId)
                    continuation.resume(ActionResult(true, "已停止 DJI 任务 ${mission.missionId}"))
                }

                override fun onFailure(error: IDJIError) {
                    ObstacleAvoidanceSafetyManager.stopMissionMonitoring("stopMission failed")
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

    private fun createExecuteWaypoint(
        waypoint: Waypoint,
        index: Int,
    ): WaylineExecuteWaypoint {
        return WaylineExecuteWaypoint().apply {
            waypointIndex = index
            location = WaylineLocationCoordinate2D(waypoint.lat, waypoint.lon)
            executeHeight = waypoint.altM.toDouble()
            speed = DEFAULT_SPEED_MPS
            useStraightLine = true
            yawParam = WaylineWaypointYawParam().apply {
                yawMode = WaylineWaypointYawMode.FOLLOW_WAYLINE
                enableYawAngle = false
                yawAngle = waypoint.yawDeg.toDouble()
                yawPathMode = WaylineWaypointYawPathMode.CLOCKWISE
            }
            gimbalHeadingParam = WaylineWaypointGimbalHeadingParam().apply {
                headingMode = WaylineWaypointGimbalHeadingMode.FOLLOW_WAYLINE
                yawAngle = 0.0
                pitchAngle = 0.0
            }
            turnParam = WaylineWaypointTurnParam(
                WaylineWaypointTurnMode.TO_POINT_AND_STOP_WITH_DISCONTINUITY_CURVATURE,
                0.2,
            )
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

        SdkWaypointMissionManager.getInstance().addWaylineExecutingInfoListener(progressListener)
        progressListenerInstalled = true
    }

    private fun ensureExecuteStateListenerInstalled() {
        if (executeStateListenerInstalled) {
            return
        }

        SdkWaypointMissionManager.getInstance().addWaypointMissionExecuteStateListener(executeStateListener)
        executeStateListenerInstalled = true
    }

    private fun updateObstacleAvoidanceSnapshot(snapshot: ObstacleAvoidanceSnapshot) {
        _missionState.value = _missionState.value.copy(
            obstacleAvoidance = snapshot,
            message = when {
                snapshot.appPauseRequested -> snapshot.lastMessage
                _missionState.value.state == MissionExecutionState.RUNNING -> obstacleExecutingMessage(snapshot)
                else -> _missionState.value.message
            },
        )
    }

    private fun obstacleAvoidanceBrief(
        snapshot: ObstacleAvoidanceSnapshot = ObstacleAvoidanceSafetyManager.safetyState.value,
    ): String {
        return "避障模式：${snapshot.mode.name}，安全状态：${snapshot.safetyState.name}"
    }

    private fun obstacleExecutingMessage(
        snapshot: ObstacleAvoidanceSnapshot = ObstacleAvoidanceSafetyManager.safetyState.value,
    ): String {
        val nearest = snapshot.nearestObstacleDistanceMeters
        return if (nearest == null || snapshot.nearestObstacleDirection == ObstacleDirection.UNKNOWN) {
            "飞机正在执行任务，${obstacleAvoidanceBrief(snapshot)}"
        } else {
            "飞机正在执行任务，最近障碍物：${snapshot.nearestObstacleDirection.name} ${"%.1f".format(nearest)} m，安全状态：${snapshot.safetyState.name}"
        }
    }

    private fun validateMissionStartPreconditions(
        aircraftState: com.example.uavmobile.core.DroneState?,
    ): String? {
        if (aircraftState == null) {
            return "启动前无法读取 DJI 飞机状态，请先刷新后重试"
        }
        if (aircraftState.connectionState != com.example.uavmobile.core.DroneConnectionState.CONNECTED) {
            return "DJI 飞机未连接，不能启动任务"
        }
        if (!aircraftState.locationReadSucceeded || !aircraftState.hasValidCoordinates()) {
            return aircraftState.locationReadError.ifBlank {
                "飞机当前位置不可用，不能启动任务"
            }
        }
        if (!aircraftState.hasValidHomeCoordinates()) {
            return "Home 点未就绪，不能启动任务"
        }
        if (aircraftState.gpsSatelliteCount != null && aircraftState.gpsSatelliteCount <= 0) {
            return "卫星数为 0，不能启动任务"
        }
        return null
    }

    private fun buildMissionStartWarnings(
        aircraftState: com.example.uavmobile.core.DroneState?,
    ): String {
        if (aircraftState == null) {
            return ""
        }
        val warnings = mutableListOf<String>()
        if (aircraftState.isSimulatorStarted == true) {
            warnings += "当前为模拟器模式"
        }
        if (aircraftState.gpsSignalLevel.isBlank()) {
            warnings += "GPS 信号等级暂不可用"
        }
        if (aircraftState.gpsSatelliteCount != null && aircraftState.gpsSatelliteCount < 8) {
            warnings += "卫星数偏少"
        }
        if (aircraftState.batteryPercent != null && aircraftState.batteryPercent < 0.2f) {
            warnings += "电量低于 20%"
        }
        if (aircraftState.motorsOn == false && aircraftState.isFlying == false) {
            warnings += "飞机当前在地面，启动后应由飞控接管起飞"
        }
        return warnings.joinToString(separator = "；")
    }

    private fun buildInterruptionDiagnostics(): String {
        val aircraftState = DjiAircraftStateReader.getState().getOrNull()
        val missionState = _missionState.value
        if (aircraftState == null) {
            return "中断时未能读取飞机状态；currentWaypointIndex=${missionState.currentWaypointIndex}, sdkMissionExecuteState=${missionState.sdkMissionExecuteState}"
        }
        return buildString {
            append("waypointIndex=${missionState.currentWaypointIndex}")
            append(", sdkState=${missionState.sdkMissionExecuteState.ifBlank { "无" }}")
            append(", flightMode=${aircraftState.flightMode.ifBlank { "无" }}")
            append(", heading=${aircraftState.headingDegrees?.let { "%.1f".format(it) } ?: "无"}")
            append(", gpsSignal=${aircraftState.gpsSignalLevel.ifBlank { "无" }}")
            append(", satellites=${aircraftState.gpsSatelliteCount?.toString() ?: "无"}")
            append(", lat=${aircraftState.latitude?.let { "%.6f".format(it) } ?: "无"}")
            append(", lon=${aircraftState.longitude?.let { "%.6f".format(it) } ?: "无"}")
            append(", alt=${aircraftState.altitudeMeters?.let { "%.2f".format(it) } ?: "无"}")
            append(", homeSet=")
            append(if (aircraftState.homeLatitude != null && aircraftState.homeLongitude != null) "是" else "否")
            append(", homeLat=${aircraftState.homeLatitude?.let { "%.6f".format(it) } ?: "无"}")
            append(", homeLon=${aircraftState.homeLongitude?.let { "%.6f".format(it) } ?: "无"}")
            append(", motorsOn=${aircraftState.motorsOn?.toString() ?: "无"}")
            append(", isFlying=${aircraftState.isFlying?.toString() ?: "无"}")
            append(", groundSpeed=${aircraftState.groundSpeedMps?.let { "%.1f".format(it) } ?: "无"}")
            append(", status=${aircraftState.statusMessage.ifBlank { "无" }}")
        }
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
