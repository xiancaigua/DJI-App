package com.example.uavmobile.dji

import com.example.uavmobile.core.ObstacleAvoidanceMode
import com.example.uavmobile.core.ObstacleAvoidanceSnapshot
import com.example.uavmobile.core.ObstacleAvoidanceSwitchState
import com.example.uavmobile.core.ObstacleDirection
import com.example.uavmobile.core.ObstacleSafetyState
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.debug.DeveloperLogStore
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.perception.PerceptionManager
import dji.v5.manager.aircraft.perception.data.ObstacleAvoidanceType
import dji.v5.manager.aircraft.perception.data.ObstacleData
import dji.v5.manager.aircraft.perception.data.PerceptionDirection
import dji.v5.manager.aircraft.perception.listener.ObstacleDataListener
import dji.v5.manager.interfaces.IPerceptionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class ObstacleAvoidancePrepareResult(
    val success: Boolean,
    val message: String,
    val warnings: List<String> = emptyList(),
    val snapshot: ObstacleAvoidanceSnapshot,
)

internal data class DjiObstacleDataReading(
    val horizontalObstacleDistanceMillimeters: List<Int> = emptyList(),
    val upwardObstacleDistanceMillimeters: Int = 0,
    val downwardObstacleDistanceMillimeters: Int = 0,
    val horizontalAngleIntervalDegrees: Int = 0,
)

internal fun interface DjiObstacleDataCallback {
    fun onUpdate(reading: DjiObstacleDataReading)
}

internal interface DjiPerceptionClient {
    suspend fun getObstacleAvoidanceType(): ObstacleAvoidanceMode
    suspend fun setObstacleAvoidanceType(mode: ObstacleAvoidanceMode)
    suspend fun getObstacleAvoidanceEnabled(direction: ObstacleDirection): Boolean
    suspend fun setObstacleAvoidanceEnabled(direction: ObstacleDirection, enabled: Boolean)
    suspend fun getObstacleAvoidanceWarningDistance(direction: ObstacleDirection): Double
    suspend fun setObstacleAvoidanceWarningDistance(direction: ObstacleDirection, distanceMeters: Double)
    suspend fun getObstacleAvoidanceBrakingDistance(direction: ObstacleDirection): Double
    suspend fun setObstacleAvoidanceBrakingDistance(direction: ObstacleDirection, distanceMeters: Double)
    fun addObstacleDataListener(callback: DjiObstacleDataCallback)
    fun removeObstacleDataListener(callback: DjiObstacleDataCallback)
}

object ObstacleAvoidanceSafetyManager {
    private const val TAG = "ObstacleAvoidanceSafety"

    val DEFAULT_OBSTACLE_AVOIDANCE_TYPE = ObstacleAvoidanceMode.BRAKE
    const val WARNING_DISTANCE_HORIZONTAL_M = 10.0
    const val WARNING_DISTANCE_UPWARD_M = 5.0
    const val WARNING_DISTANCE_DOWNWARD_M = 5.0
    const val BRAKING_DISTANCE_HORIZONTAL_M = 6.0
    const val BRAKING_DISTANCE_UPWARD_M = 3.0
    const val BRAKING_DISTANCE_DOWNWARD_M = 3.0
    const val WARNING_STATUS_DISTANCE_M = 6.0
    const val EMERGENCY_DISTANCE_THRESHOLD_M = 3.0
    const val EMERGENCY_TRIGGER_COUNT = 3
    const val PAUSE_DEBOUNCE_MS = 10_000L

    private val _safetyState = MutableStateFlow(ObstacleAvoidanceSnapshot())
    val safetyState: StateFlow<ObstacleAvoidanceSnapshot> = _safetyState.asStateFlow()

    private var perceptionClientForTest: DjiPerceptionClient? = null
    private var monitorDispatcher: CoroutineDispatcher = Dispatchers.Default
    private var monitorScope = CoroutineScope(SupervisorJob() + monitorDispatcher)
    private var monitorJob: Job? = null
    private var obstacleCallback: DjiObstacleDataCallback? = null
    private var isMissionExecuting: () -> Boolean = { false }
    private var pauseAction: suspend () -> ActionResult = {
        ActionResult(false, "No waypoint pause action is attached", -1)
    }
    private var onSnapshotUpdate: (ObstacleAvoidanceSnapshot) -> Unit = {}
    private var nowMs: () -> Long = { System.currentTimeMillis() }
    private var emergencyHitCount = 0
    private var lastPauseRequestAtMs = 0L
    private var lastLoggedSafetyState = ObstacleSafetyState.UNKNOWN

    suspend fun prepareForWaypointMission(
        missionId: String,
        missionState: String,
    ): ObstacleAvoidancePrepareResult {
        val productType = DjiConnectionManager.currentProductType()?.name ?: "UNKNOWN"
        val warnings = mutableListOf<String>()
        DeveloperLogStore.info(
            TAG,
            "Preparing DJI obstacle avoidance safety",
            "missionId=$missionId, productType=$productType, missionState=$missionState",
        )

        val mode = readModeOrBlock(
            missionId = missionId,
            productType = productType,
            missionState = missionState,
        ) ?: return blockPrepare(
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            message = "避障状态未知，已阻止航线任务启动。建议检查 DJI Pilot 2 感知设置后重试。",
        )

        val safeMode = ensureSafeMode(
            currentMode = mode,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        if (safeMode != ObstacleAvoidanceMode.BRAKE && safeMode != ObstacleAvoidanceMode.BYPASS) {
            return blockPrepare(
                missionId = missionId,
                productType = productType,
                missionState = missionState,
                message = "避障未开启，已阻止航线任务启动。当前模式=$safeMode，建议检查 DJI Pilot 2 感知设置。",
            )
        }

        val horizontalSwitch = configureSwitch(
            direction = ObstacleDirection.HORIZONTAL,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        val upwardSwitch = configureSwitch(
            direction = ObstacleDirection.UPWARD,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        val downwardSwitch = configureSwitch(
            direction = ObstacleDirection.DOWNWARD,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )

        val horizontalWarningDistance = configureWarningDistance(
            direction = ObstacleDirection.HORIZONTAL,
            defaultMeters = WARNING_DISTANCE_HORIZONTAL_M,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        val upwardWarningDistance = configureWarningDistance(
            direction = ObstacleDirection.UPWARD,
            defaultMeters = WARNING_DISTANCE_UPWARD_M,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        val downwardWarningDistance = configureWarningDistance(
            direction = ObstacleDirection.DOWNWARD,
            defaultMeters = WARNING_DISTANCE_DOWNWARD_M,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        val horizontalBrakingDistance = configureBrakingDistance(
            direction = ObstacleDirection.HORIZONTAL,
            defaultMeters = BRAKING_DISTANCE_HORIZONTAL_M,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        val upwardBrakingDistance = configureBrakingDistance(
            direction = ObstacleDirection.UPWARD,
            defaultMeters = BRAKING_DISTANCE_UPWARD_M,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )
        val downwardBrakingDistance = configureBrakingDistance(
            direction = ObstacleDirection.DOWNWARD,
            defaultMeters = BRAKING_DISTANCE_DOWNWARD_M,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
        )

        val message = buildString {
            append("避障检查完成：mode=$safeMode")
            if (warnings.isNotEmpty()) {
                append("；warnings=${warnings.size}")
            }
        }
        val snapshot = publishSnapshot(
            _safetyState.value.copy(
                mode = safeMode,
                horizontalSwitch = horizontalSwitch,
                upwardSwitch = upwardSwitch,
                downwardSwitch = downwardSwitch,
                horizontalWarningDistanceMeters = horizontalWarningDistance,
                upwardWarningDistanceMeters = upwardWarningDistance,
                downwardWarningDistanceMeters = downwardWarningDistance,
                horizontalBrakingDistanceMeters = horizontalBrakingDistance,
                upwardBrakingDistanceMeters = upwardBrakingDistance,
                downwardBrakingDistanceMeters = downwardBrakingDistance,
                safetyState = ObstacleSafetyState.SAFE,
                lastPrepareSucceeded = true,
                lastPrepareWarning = warnings.joinToString(separator = "；"),
                lastPrepareError = "",
                appPauseRequested = false,
                lastMessage = message,
                updatedAt = nowText(),
            ),
        )
        if (warnings.isEmpty()) {
            DeveloperLogStore.info(TAG, message, "missionId=$missionId")
        } else {
            DeveloperLogStore.warn(TAG, message, warnings.joinToString(separator = " | "))
        }
        return ObstacleAvoidancePrepareResult(
            success = true,
            message = message,
            warnings = warnings,
            snapshot = snapshot,
        )
    }

    fun startMissionMonitoring(
        missionId: String,
        isMissionExecuting: () -> Boolean,
        pauseAction: suspend () -> ActionResult,
        onSnapshotUpdate: (ObstacleAvoidanceSnapshot) -> Unit,
    ) {
        stopMissionMonitoring("restart mission obstacle monitor")
        this.isMissionExecuting = isMissionExecuting
        this.pauseAction = pauseAction
        this.onSnapshotUpdate = onSnapshotUpdate
        emergencyHitCount = 0
        lastPauseRequestAtMs = 0L
        lastLoggedSafetyState = safetyState.value.safetyState

        val callback = DjiObstacleDataCallback { reading ->
            handleObstacleData(missionId, reading)
        }
        obstacleCallback = callback
        runCatching {
            client().addObstacleDataListener(callback)
        }.onSuccess {
            val snapshot = publishSnapshot(
                safetyState.value.copy(
                    monitoringActive = true,
                    lastMessage = "避障监听已启动，等待障碍物数据",
                    updatedAt = nowText(),
                ),
            )
            onSnapshotUpdate(snapshot)
            DeveloperLogStore.info(TAG, "Obstacle data listener started", "missionId=$missionId")
        }.onFailure { throwable ->
            val snapshot = publishSnapshot(
                safetyState.value.copy(
                    monitoringActive = false,
                    safetyState = ObstacleSafetyState.UNSUPPORTED,
                    lastPrepareWarning = appendWarning(
                        safetyState.value.lastPrepareWarning,
                        buildErrorDetails(
                            apiName = "addObstacleDataListener",
                            productType = DjiConnectionManager.currentProductType()?.name ?: "UNKNOWN",
                            missionState = "STARTING",
                            throwable = throwable,
                        ),
                    ),
                    lastMessage = "避障监听启动失败，任务可继续但无法显示实时障碍物数据",
                    updatedAt = nowText(),
                ),
            )
            onSnapshotUpdate(snapshot)
            DeveloperLogStore.warn(TAG, "Obstacle data listener failed", throwable.message)
        }
    }

    fun stopMissionMonitoring(reason: String) {
        monitorJob?.cancel()
        monitorJob = null
        val callback = obstacleCallback
        if (callback != null) {
            runCatching { client().removeObstacleDataListener(callback) }
                .onFailure { throwable ->
                    DeveloperLogStore.warn(TAG, "Remove obstacle listener failed", throwable.message)
                }
        }
        obstacleCallback = null
        isMissionExecuting = { false }
        pauseAction = { ActionResult(false, "No waypoint pause action is attached", -1) }
        onSnapshotUpdate = {}
        emergencyHitCount = 0
        publishSnapshot(
            safetyState.value.copy(
                monitoringActive = false,
                lastMessage = "避障监听已停止：$reason",
                updatedAt = nowText(),
            ),
        )
    }

    private suspend fun readModeOrBlock(
        missionId: String,
        productType: String,
        missionState: String,
    ): ObstacleAvoidanceMode? {
        return runCatching {
            client().getObstacleAvoidanceType()
        }.onFailure { throwable ->
            val details = buildErrorDetails(
                apiName = "getObstacleAvoidanceType",
                productType = productType,
                missionState = missionState,
                throwable = throwable,
            )
            DeveloperLogStore.error(TAG, "Obstacle avoidance mode read failed", "missionId=$missionId, $details")
        }.getOrNull()
    }

    private suspend fun ensureSafeMode(
        currentMode: ObstacleAvoidanceMode,
        missionId: String,
        productType: String,
        missionState: String,
        warnings: MutableList<String>,
    ): ObstacleAvoidanceMode {
        if (currentMode == ObstacleAvoidanceMode.BRAKE || currentMode == ObstacleAvoidanceMode.BYPASS) {
            return currentMode
        }
        if (currentMode != ObstacleAvoidanceMode.CLOSE) {
            warnings += "当前避障模式未知：$currentMode"
            return currentMode
        }

        val setResult = runCatching {
            client().setObstacleAvoidanceType(DEFAULT_OBSTACLE_AVOIDANCE_TYPE)
        }
        if (setResult.isFailure) {
            warnings += buildErrorDetails(
                apiName = "setObstacleAvoidanceType(BRAKE)",
                productType = productType,
                missionState = missionState,
                throwable = setResult.exceptionOrNull(),
            )
        }

        return runCatching {
            client().getObstacleAvoidanceType()
        }.onFailure { throwable ->
            warnings += buildErrorDetails(
                apiName = "getObstacleAvoidanceType after set BRAKE",
                productType = productType,
                missionState = missionState,
                throwable = throwable,
            )
        }.getOrDefault(currentMode).also { finalMode ->
            if (finalMode == ObstacleAvoidanceMode.BRAKE) {
                DeveloperLogStore.info(TAG, "Obstacle avoidance type set to BRAKE", "missionId=$missionId")
            }
        }
    }

    private suspend fun configureSwitch(
        direction: ObstacleDirection,
        missionId: String,
        productType: String,
        missionState: String,
        warnings: MutableList<String>,
    ): ObstacleAvoidanceSwitchState {
        val currentEnabled = runCatching {
            client().getObstacleAvoidanceEnabled(direction)
        }.onFailure { throwable ->
            warnings += buildErrorDetails(
                apiName = "getObstacleAvoidanceEnabled($direction)",
                productType = productType,
                missionState = missionState,
                throwable = throwable,
            )
        }.getOrNull() ?: return ObstacleAvoidanceSwitchState.UNSUPPORTED

        if (currentEnabled) {
            return ObstacleAvoidanceSwitchState.ON
        }

        val setResult = runCatching {
            client().setObstacleAvoidanceEnabled(direction, true)
        }
        if (setResult.isFailure) {
            warnings += buildErrorDetails(
                apiName = "setObstacleAvoidanceEnabled($direction,true)",
                productType = productType,
                missionState = missionState,
                throwable = setResult.exceptionOrNull(),
            )
            return ObstacleAvoidanceSwitchState.OFF
        }

        val finalEnabled = runCatching {
            client().getObstacleAvoidanceEnabled(direction)
        }.onFailure { throwable ->
            warnings += buildErrorDetails(
                apiName = "getObstacleAvoidanceEnabled($direction) after set",
                productType = productType,
                missionState = missionState,
                throwable = throwable,
            )
        }.getOrNull()

        return when (finalEnabled) {
            true -> {
                DeveloperLogStore.info(TAG, "Obstacle avoidance switch enabled", "missionId=$missionId, direction=$direction")
                ObstacleAvoidanceSwitchState.ON
            }
            false -> {
                warnings += "避障方向 $direction 仍为 OFF，任务将继续但需人工确认现场风险。"
                ObstacleAvoidanceSwitchState.OFF
            }
            null -> ObstacleAvoidanceSwitchState.UNKNOWN
        }
    }

    private suspend fun configureWarningDistance(
        direction: ObstacleDirection,
        defaultMeters: Double,
        missionId: String,
        productType: String,
        missionState: String,
        warnings: MutableList<String>,
    ): Double? {
        return configureDistance(
            apiName = "ObstacleAvoidanceWarningDistance",
            direction = direction,
            defaultMeters = defaultMeters,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
            setValue = { client().setObstacleAvoidanceWarningDistance(direction, defaultMeters) },
            readValue = { client().getObstacleAvoidanceWarningDistance(direction) },
        )
    }

    private suspend fun configureBrakingDistance(
        direction: ObstacleDirection,
        defaultMeters: Double,
        missionId: String,
        productType: String,
        missionState: String,
        warnings: MutableList<String>,
    ): Double? {
        return configureDistance(
            apiName = "ObstacleAvoidanceBrakingDistance",
            direction = direction,
            defaultMeters = defaultMeters,
            missionId = missionId,
            productType = productType,
            missionState = missionState,
            warnings = warnings,
            setValue = { client().setObstacleAvoidanceBrakingDistance(direction, defaultMeters) },
            readValue = { client().getObstacleAvoidanceBrakingDistance(direction) },
        )
    }

    private suspend fun configureDistance(
        apiName: String,
        direction: ObstacleDirection,
        defaultMeters: Double,
        missionId: String,
        productType: String,
        missionState: String,
        warnings: MutableList<String>,
        setValue: suspend () -> Unit,
        readValue: suspend () -> Double,
    ): Double? {
        runCatching {
            setValue()
        }.onFailure { throwable ->
            warnings += buildErrorDetails(
                apiName = "set$apiName($direction,$defaultMeters)",
                productType = productType,
                missionState = missionState,
                throwable = throwable,
            )
        }

        return runCatching {
            readValue()
        }.onFailure { throwable ->
            warnings += buildErrorDetails(
                apiName = "get$apiName($direction)",
                productType = productType,
                missionState = missionState,
                throwable = throwable,
            )
        }.onSuccess { value ->
            DeveloperLogStore.debug(TAG, "$apiName configured", "missionId=$missionId, direction=$direction, value=$value")
        }.getOrNull()
    }

    private fun blockPrepare(
        missionId: String,
        productType: String,
        missionState: String,
        message: String,
    ): ObstacleAvoidancePrepareResult {
        val error = "missionId=$missionId, productType=$productType, missionState=$missionState, $message"
        val snapshot = publishSnapshot(
            safetyState.value.copy(
                mode = ObstacleAvoidanceMode.UNKNOWN,
                safetyState = ObstacleSafetyState.UNKNOWN,
                lastPrepareSucceeded = false,
                lastPrepareError = message,
                lastMessage = message,
                updatedAt = nowText(),
            ),
        )
        DeveloperLogStore.error(TAG, "Obstacle avoidance precheck blocked mission", error)
        return ObstacleAvoidancePrepareResult(
            success = false,
            message = message,
            snapshot = snapshot,
        )
    }

    private fun handleObstacleData(
        missionId: String,
        reading: DjiObstacleDataReading,
    ) {
        val nearest = nearestObstacle(reading)
        val safety = when {
            nearest == null -> ObstacleSafetyState.UNKNOWN
            nearest.distanceMeters < EMERGENCY_DISTANCE_THRESHOLD_M -> ObstacleSafetyState.EMERGENCY
            nearest.distanceMeters < WARNING_STATUS_DISTANCE_M -> ObstacleSafetyState.WARNING
            else -> ObstacleSafetyState.SAFE
        }
        emergencyHitCount = if (safety == ObstacleSafetyState.EMERGENCY) {
            emergencyHitCount + 1
        } else {
            0
        }

        val shouldPause = safety == ObstacleSafetyState.EMERGENCY &&
            emergencyHitCount >= EMERGENCY_TRIGGER_COUNT &&
            isMissionExecuting() &&
            nowMs() - lastPauseRequestAtMs >= PAUSE_DEBOUNCE_MS
        val message = when {
            shouldPause -> "检测到近距离障碍物，已请求暂停航线任务"
            safety == ObstacleSafetyState.EMERGENCY -> "避障告警：距离过近，建议暂停任务"
            safety == ObstacleSafetyState.WARNING -> "避障告警：最近障碍物 ${nearest?.format() ?: "未知"}"
            safety == ObstacleSafetyState.SAFE -> "避障：已开启 / ${safetyState.value.mode}"
            else -> "避障：等待有效障碍物距离"
        }
        val snapshot = publishSnapshot(
            safetyState.value.copy(
                nearestObstacleDistanceMeters = nearest?.distanceMeters,
                nearestObstacleDirection = nearest?.direction ?: ObstacleDirection.UNKNOWN,
                safetyState = safety,
                appPauseRequested = safetyState.value.appPauseRequested || shouldPause,
                lastMessage = message,
                updatedAt = nowText(),
            ),
        )
        onSnapshotUpdate(snapshot)
        logObstacleStateIfNeeded(missionId, snapshot, reading, shouldPause)

        if (shouldPause) {
            lastPauseRequestAtMs = nowMs()
            monitorJob = monitorScope.launch {
                val result = runCatching { pauseAction() }.getOrElse { throwable ->
                    ActionResult(false, throwable.message ?: "Pause mission failed after obstacle emergency", -1)
                }
                val pauseSnapshot = publishSnapshot(
                    safetyState.value.copy(
                        appPauseRequested = true,
                        lastMessage = if (result.success) {
                            "检测到近距离障碍物，已请求暂停航线任务"
                        } else {
                            "近距离障碍物暂停请求失败：${result.message}"
                        },
                        updatedAt = nowText(),
                    ),
                )
                onSnapshotUpdate(pauseSnapshot)
                if (result.success) {
                    DeveloperLogStore.warn(TAG, "Mission pause requested by obstacle safety", result.message)
                } else {
                    DeveloperLogStore.error(TAG, "Mission pause request failed after obstacle emergency", result.message)
                }
            }
        }
    }

    private fun nearestObstacle(reading: DjiObstacleDataReading): NearestObstacle? {
        val candidates = mutableListOf<NearestObstacle>()
        reading.horizontalObstacleDistanceMillimeters
            .filter { it > 0 }
            .minOrNull()
            ?.let { millimeters ->
                candidates += NearestObstacle(
                    direction = ObstacleDirection.HORIZONTAL,
                    distanceMeters = millimeters / 1000.0,
                )
            }
        if (reading.upwardObstacleDistanceMillimeters > 0) {
            candidates += NearestObstacle(
                direction = ObstacleDirection.UPWARD,
                distanceMeters = reading.upwardObstacleDistanceMillimeters / 1000.0,
            )
        }
        if (reading.downwardObstacleDistanceMillimeters > 0) {
            candidates += NearestObstacle(
                direction = ObstacleDirection.DOWNWARD,
                distanceMeters = reading.downwardObstacleDistanceMillimeters / 1000.0,
            )
        }
        return candidates.minByOrNull { it.distanceMeters }
    }

    private fun logObstacleStateIfNeeded(
        missionId: String,
        snapshot: ObstacleAvoidanceSnapshot,
        reading: DjiObstacleDataReading,
        shouldPause: Boolean,
    ) {
        val changed = snapshot.safetyState != lastLoggedSafetyState
        if (!changed && !shouldPause && snapshot.safetyState == ObstacleSafetyState.SAFE) {
            return
        }
        lastLoggedSafetyState = snapshot.safetyState
        val details = "missionId=$missionId, safety=${snapshot.safetyState}, nearest=${snapshot.nearestObstacleDistanceMeters}, " +
            "direction=${snapshot.nearestObstacleDirection}, horizontalSamples=${reading.horizontalObstacleDistanceMillimeters.size}, " +
            "angleInterval=${reading.horizontalAngleIntervalDegrees}"
        when (snapshot.safetyState) {
            ObstacleSafetyState.EMERGENCY -> DeveloperLogStore.warn(TAG, snapshot.lastMessage, details)
            ObstacleSafetyState.WARNING -> DeveloperLogStore.warn(TAG, snapshot.lastMessage, details)
            else -> DeveloperLogStore.info(TAG, snapshot.lastMessage, details)
        }
    }

    private fun publishSnapshot(snapshot: ObstacleAvoidanceSnapshot): ObstacleAvoidanceSnapshot {
        _safetyState.value = snapshot
        return snapshot
    }

    private fun client(): DjiPerceptionClient = perceptionClientForTest ?: DefaultDjiPerceptionClient

    internal fun setPerceptionClientForTest(client: DjiPerceptionClient) {
        stopMissionMonitoring("test reset")
        perceptionClientForTest = client
    }

    internal fun setMonitorDispatcherForTest(dispatcher: CoroutineDispatcher) {
        stopMissionMonitoring("test dispatcher reset")
        monitorDispatcher = dispatcher
        monitorScope = CoroutineScope(SupervisorJob() + monitorDispatcher)
    }

    internal fun setClockForTest(clock: () -> Long) {
        nowMs = clock
    }

    internal fun resetForTest() {
        stopMissionMonitoring("test reset")
        perceptionClientForTest = null
        monitorDispatcher = Dispatchers.Default
        monitorScope = CoroutineScope(SupervisorJob() + monitorDispatcher)
        nowMs = { System.currentTimeMillis() }
        emergencyHitCount = 0
        lastPauseRequestAtMs = 0L
        lastLoggedSafetyState = ObstacleSafetyState.UNKNOWN
        _safetyState.value = ObstacleAvoidanceSnapshot()
    }

    private fun buildErrorDetails(
        apiName: String,
        productType: String,
        missionState: String,
        throwable: Throwable?,
    ): String {
        val error = throwable?.message ?: "未知错误"
        return "api=$apiName, productType=$productType, missionState=$missionState, error=$error, " +
            "suggestion=请检查当前机型/固件是否支持该感知避障接口，并确认 DJI Pilot 2 感知设置。"
    }

    private fun appendWarning(existing: String, warning: String): String {
        return listOf(existing, warning)
            .filter { it.isNotBlank() }
            .joinToString(separator = "；")
    }

    private fun nowText(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
}

private data class NearestObstacle(
    val direction: ObstacleDirection,
    val distanceMeters: Double,
) {
    fun format(): String = "${direction.name} ${"%.1f".format(distanceMeters)} m"
}

private object DefaultDjiPerceptionClient : DjiPerceptionClient {
    private val listeners = ConcurrentHashMap<DjiObstacleDataCallback, ObstacleDataListener>()

    override suspend fun getObstacleAvoidanceType(): ObstacleAvoidanceMode {
        return readParam("getObstacleAvoidanceType") { callback ->
            manager().getObstacleAvoidanceType(callback)
        }.toCoreMode()
    }

    override suspend fun setObstacleAvoidanceType(mode: ObstacleAvoidanceMode) {
        val sdkMode = mode.toSdkMode() ?: error("Unsupported obstacle avoidance mode: $mode")
        runAction("setObstacleAvoidanceType($mode)") { callback ->
            manager().setObstacleAvoidanceType(sdkMode, callback)
        }
    }

    override suspend fun getObstacleAvoidanceEnabled(direction: ObstacleDirection): Boolean {
        return readParam("getObstacleAvoidanceEnabled($direction)") { callback ->
            manager().getObstacleAvoidanceEnabled(direction.toSdkDirection(), callback)
        }
    }

    override suspend fun setObstacleAvoidanceEnabled(direction: ObstacleDirection, enabled: Boolean) {
        runAction("setObstacleAvoidanceEnabled($direction,$enabled)") { callback ->
            manager().setObstacleAvoidanceEnabled(enabled, direction.toSdkDirection(), callback)
        }
    }

    override suspend fun getObstacleAvoidanceWarningDistance(direction: ObstacleDirection): Double {
        return readParam("getObstacleAvoidanceWarningDistance($direction)") { callback ->
            manager().getObstacleAvoidanceWarningDistance(direction.toSdkDirection(), callback)
        }
    }

    override suspend fun setObstacleAvoidanceWarningDistance(direction: ObstacleDirection, distanceMeters: Double) {
        runAction("setObstacleAvoidanceWarningDistance($direction,$distanceMeters)") { callback ->
            manager().setObstacleAvoidanceWarningDistance(distanceMeters, direction.toSdkDirection(), callback)
        }
    }

    override suspend fun getObstacleAvoidanceBrakingDistance(direction: ObstacleDirection): Double {
        return readParam("getObstacleAvoidanceBrakingDistance($direction)") { callback ->
            manager().getObstacleAvoidanceBrakingDistance(direction.toSdkDirection(), callback)
        }
    }

    override suspend fun setObstacleAvoidanceBrakingDistance(direction: ObstacleDirection, distanceMeters: Double) {
        runAction("setObstacleAvoidanceBrakingDistance($direction,$distanceMeters)") { callback ->
            manager().setObstacleAvoidanceBrakingDistance(distanceMeters, direction.toSdkDirection(), callback)
        }
    }

    override fun addObstacleDataListener(callback: DjiObstacleDataCallback) {
        val sdkListener = ObstacleDataListener { obstacleData ->
            callback.onUpdate(obstacleData.toReading())
        }
        listeners[callback] = sdkListener
        manager().addObstacleDataListener(sdkListener)
    }

    override fun removeObstacleDataListener(callback: DjiObstacleDataCallback) {
        listeners.remove(callback)?.let { listener ->
            manager().removeObstacleDataListener(listener)
        }
    }

    private fun manager(): IPerceptionManager {
        return PerceptionManager.getInstance()
    }

    private suspend fun runAction(
        apiName: String,
        block: (CommonCallbacks.CompletionCallback) -> Unit,
    ) {
        suspendCancellableCoroutine { continuation ->
            block(
                object : CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onFailure(error: IDJIError) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("$apiName failed: ${DjiErrorFormatter.describe(error)}"),
                            )
                        }
                    }
                },
            )
        }
    }

    private suspend fun <T> readParam(
        apiName: String,
        block: (CommonCallbacks.CompletionCallbackWithParam<T>) -> Unit,
    ): T {
        return suspendCancellableCoroutine { continuation ->
            block(
                object : CommonCallbacks.CompletionCallbackWithParam<T> {
                    override fun onSuccess(result: T) {
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    override fun onFailure(error: IDJIError) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("$apiName failed: ${DjiErrorFormatter.describe(error)}"),
                            )
                        }
                    }
                },
            )
        }
    }
}

private fun ObstacleAvoidanceType.toCoreMode(): ObstacleAvoidanceMode {
    return when (this) {
        ObstacleAvoidanceType.BRAKE -> ObstacleAvoidanceMode.BRAKE
        ObstacleAvoidanceType.BYPASS -> ObstacleAvoidanceMode.BYPASS
        ObstacleAvoidanceType.CLOSE -> ObstacleAvoidanceMode.CLOSE
    }
}

private fun ObstacleAvoidanceMode.toSdkMode(): ObstacleAvoidanceType? {
    return when (this) {
        ObstacleAvoidanceMode.BRAKE -> ObstacleAvoidanceType.BRAKE
        ObstacleAvoidanceMode.BYPASS -> ObstacleAvoidanceType.BYPASS
        ObstacleAvoidanceMode.CLOSE -> ObstacleAvoidanceType.CLOSE
        ObstacleAvoidanceMode.UNKNOWN -> null
    }
}

private fun ObstacleDirection.toSdkDirection(): PerceptionDirection {
    return when (this) {
        ObstacleDirection.HORIZONTAL -> PerceptionDirection.HORIZONTAL
        ObstacleDirection.UPWARD -> PerceptionDirection.UPWARD
        ObstacleDirection.DOWNWARD -> PerceptionDirection.DOWNWARD
        ObstacleDirection.UNKNOWN -> error("UNKNOWN obstacle direction cannot be used with DJI MSDK")
    }
}

private fun ObstacleData.toReading(): DjiObstacleDataReading {
    return DjiObstacleDataReading(
        horizontalObstacleDistanceMillimeters = horizontalObstacleDistance.orEmpty(),
        upwardObstacleDistanceMillimeters = upwardObstacleDistance,
        downwardObstacleDistanceMillimeters = downwardObstacleDistance,
        horizontalAngleIntervalDegrees = horizontalAngleInterval,
    )
}
