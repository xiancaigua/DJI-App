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
            return fail("DJI missions currently require at least 2 waypoints")
        }
        if (!DjiMsdkManager.isMissionFeatureAvailable()) {
            return fail("DJI mission runtime is not ready: ${DjiMsdkManager.describeStatus()}")
        }

        val aircraftResolution = DjiAircraftResolver.resolve(
            currentProductType = DjiConnectionManager.currentProductType(),
            selectedAircraftFamily = selectedAircraftFamily,
        )
        when (aircraftResolution) {
            is DjiAircraftResolution.Supported -> {
                Log.i(TAG, aircraftResolution.message)
                DeveloperLogStore.info(TAG, "Aircraft resolution", aircraftResolution.message)
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
                waypointCount = waypoints.size,
                message = "Preparing DJI mission package",
            )
            DeveloperLogStore.info(TAG, "Preparing DJI mission", "missionId=$missionId, waypoints=${waypoints.size}")

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
                message = "DJI mission prepared at ${kmzFile.absolutePath}",
            )
            DeveloperLogStore.info(TAG, "DJI mission prepared", kmzFile.absolutePath)

            ActionResult(
                success = true,
                message = "Prepared DJI mission $missionId",
            )
        }.getOrElse { throwable ->
            fail(throwable.message ?: "Failed to prepare DJI mission")
        }
    }

    suspend fun uploadMission(): ActionResult {
        val mission = preparedMission ?: return fail("Prepare a DJI mission before uploading")
        if (!DjiMsdkManager.isMissionFeatureAvailable()) {
            return fail("DJI mission runtime is not ready: ${DjiMsdkManager.describeStatus()}")
        }

        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.UPLOADING,
            message = "Uploading ${mission.missionId} to the aircraft",
        )
        DeveloperLogStore.info(TAG, "Uploading DJI mission", mission.missionId)

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().pushKMZFileToAircraft(
                mission.kmzPath,
                object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                    override fun onProgressUpdate(progress: Double) {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.UPLOADING,
                            progress = progress,
                            message = "Uploading ${mission.missionId}: ${(progress * 100).toInt()}%",
                        )
                    }

                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.UPLOADED,
                            progress = 1.0,
                            message = "Uploaded ${mission.missionId} to the aircraft",
                        )
                        DeveloperLogStore.info(TAG, "DJI mission uploaded", mission.missionId)
                        continuation.resume(ActionResult(true, "Uploaded DJI mission ${mission.missionId}"))
                    }

                    override fun onFailure(error: IDJIError) {
                        continuation.resume(failResult("DJI upload failed: ${DjiErrorFormatter.describe(error)}"))
                    }
                },
            )
        }
    }

    suspend fun startMission(): ActionResult {
        val mission = preparedMission ?: return fail("Prepare a DJI mission before starting")
        ensureProgressListenerInstalled()
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.STARTING,
            message = "Starting DJI mission ${mission.missionId}",
        )
        DeveloperLogStore.info(TAG, "Starting DJI mission", mission.missionId)

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().startMission(
                mission.missionFileName,
                arrayListOf(),
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.RUNNING,
                            message = "DJI mission ${mission.missionId} started",
                        )
                        DeveloperLogStore.info(TAG, "DJI mission started", mission.missionId)
                        continuation.resume(ActionResult(true, "Started DJI mission ${mission.missionId}"))
                    }

                    override fun onFailure(error: IDJIError) {
                        continuation.resume(failResult("DJI startMission failed: ${DjiErrorFormatter.describe(error)}"))
                    }
                },
            )
        }
    }

    suspend fun pauseMission(): ActionResult {
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.PAUSED,
            message = "Requesting DJI mission pause",
        )
        DeveloperLogStore.info(TAG, "Pausing DJI mission")

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().pauseMission(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.PAUSED,
                        message = "DJI mission paused",
                    )
                    DeveloperLogStore.info(TAG, "DJI mission paused")
                    continuation.resume(ActionResult(true, "Paused DJI mission"))
                }

                override fun onFailure(error: IDJIError) {
                    continuation.resume(failResult("DJI pauseMission failed: ${DjiErrorFormatter.describe(error)}"))
                }
            })
        }
    }

    suspend fun stopMission(): ActionResult {
        val mission = preparedMission ?: return fail("Prepare a DJI mission before stopping")
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.STOPPING,
            message = "Stopping DJI mission ${mission.missionId}",
        )
        DeveloperLogStore.info(TAG, "Stopping DJI mission", mission.missionId)

        return suspendCancellableCoroutine { continuation ->
            SdkWaypointMissionManager.getInstance().stopMission(
                mission.missionFileName,
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        _missionState.value = _missionState.value.copy(
                            state = MissionExecutionState.STOPPED,
                            message = "DJI mission ${mission.missionId} stopped",
                        )
                        DeveloperLogStore.info(TAG, "DJI mission stopped", mission.missionId)
                        continuation.resume(ActionResult(true, "Stopped DJI mission ${mission.missionId}"))
                    }

                    override fun onFailure(error: IDJIError) {
                        continuation.resume(failResult("DJI stopMission failed: ${DjiErrorFormatter.describe(error)}"))
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
                    "DJI mission progress",
                    "mission=${info.missionFileName}, waypointIndex=${info.currentWaypointIndex}",
                )
                _missionState.value = _missionState.value.copy(
                    state = MissionExecutionState.RUNNING,
                    currentWaypointIndex = info.currentWaypointIndex,
                    progress = normalizedProgress,
                    message = "Executing waypoint ${info.currentWaypointIndex + 1}",
                )
            }

            override fun onWaylineExecutingInterruptReasonUpdate(error: IDJIError?) {
                if (error != null) {
                    DeveloperLogStore.error(TAG, "DJI mission interrupted", DjiErrorFormatter.describe(error))
                    _missionState.value = _missionState.value.copy(
                        state = MissionExecutionState.FAILED,
                        message = "Mission interrupted: ${DjiErrorFormatter.describe(error)}",
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
        )
        Log.e(TAG, message)
        DeveloperLogStore.error(TAG, message)
        return ActionResult(false, message, -1)
    }

    private fun failResult(message: String): ActionResult {
        _missionState.value = _missionState.value.copy(
            state = MissionExecutionState.FAILED,
            message = message,
        )
        Log.e(TAG, message)
        DeveloperLogStore.error(TAG, message)
        return ActionResult(false, message, -1)
    }
}
