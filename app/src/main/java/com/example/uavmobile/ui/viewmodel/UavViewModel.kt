package com.example.uavmobile.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uavmobile.BuildConfig
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneController
import com.example.uavmobile.core.DroneState
import com.example.uavmobile.core.SelfDroneController
import com.example.uavmobile.core.toDroneState
import com.example.uavmobile.core.toMissionSummaryOrNull
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.data.model.ConnectionConfig
import com.example.uavmobile.data.model.ConnectionStatus
import com.example.uavmobile.data.model.MobileEvent
import com.example.uavmobile.data.model.MissionSummary
import com.example.uavmobile.data.model.MissionWaypointDraft
import com.example.uavmobile.data.model.TelemetrySnapshot
import com.example.uavmobile.data.repository.UavRepository
import com.example.uavmobile.debug.DeveloperLogEntry
import com.example.uavmobile.debug.DeveloperLogLevel
import com.example.uavmobile.debug.DeveloperLogStore
import com.example.uavmobile.debug.DeveloperSnapshot
import com.example.uavmobile.debug.DjiAircraftDiagnosticSnapshot
import com.example.uavmobile.debug.DjiConnectionDiagnosticSnapshot
import com.example.uavmobile.debug.DjiWaypointDiagnosticSnapshot
import com.example.uavmobile.dji.DjiConnectionManager
import com.example.uavmobile.dji.DjiDroneController
import com.example.uavmobile.dji.DjiMsdkManager
import com.example.uavmobile.dji.DjiPermissionSnapshot
import com.example.uavmobile.dji.DjiSdkInitState
import com.example.uavmobile.dji.DjiWaypointMissionManager
import com.example.uavmobile.domain.MissionDraftValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UavUiState(
    val activeBackend: DroneBackend = DroneBackend.SELF_ROS,
    val selectedDjiAircraftFamily: DjiAircraftFamily = DjiAircraftFamily.AUTO,
    val connectionConfig: ConnectionConfig = ConnectionConfig(),
    val rosConnectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
    val currentDroneState: DroneState = TelemetrySnapshot().toDroneState(),
    val missions: List<MissionSummary> = emptyList(),
    val rosMissionCache: List<MissionSummary> = emptyList(),
    val djiMissionSummary: MissionSummary? = null,
    val events: List<MobileEvent> = emptyList(),
    val draftMissionId: String = "mission-alpha",
    val draftWaypoints: List<MissionWaypointDraft> = listOf(
        MissionWaypointDraft(lat = "31.2304", lon = "121.4737", altM = "30"),
        MissionWaypointDraft(lat = "31.2312", lon = "121.4753", altM = "35"),
    ),
    val selectedMissionId: String = "",
    val statusMessage: String = "准备连接 rosbridge 或初始化 DJI",
    val busy: Boolean = false,
    val djiPermissionsGranted: Boolean = false,
    val djiMissingPermissions: List<String> = emptyList(),
    val djiPermissionStatusMessage: String = "DJI 权限尚未检查",
    val djiSdkInitState: DjiSdkInitState = DjiMsdkManager.initState.value,
    val djiSdkStatusMessage: String = DjiMsdkManager.describeStatus(),
    val djiProductConnected: Boolean = false,
    val djiProductId: Int? = null,
    val djiProductTypeLabel: String = "",
    val djiProductStatusMessage: String = DjiConnectionManager.describeStatus(),
    val djiKeyConnectionValue: Boolean? = null,
    val djiLastConnectionSource: String = "",
    val djiLastRefreshReason: String = "",
    val djiLastRefreshSucceeded: Boolean = false,
    val djiLastRefreshError: String = "",
    val djiConnectionMonitorRunning: Boolean = false,
    val djiConnectionMonitorStartedAt: String = "",
    val djiConnectionMonitorTickCount: Int = 0,
    val vehicleConnected: Boolean = false,
    val topStatusLabel: String = "飞机离线",
    val topStatusKind: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val developerPanelVisible: Boolean = false,
    val developerLogs: List<DeveloperLogEntry> = emptyList(),
    val developerLogsPaused: Boolean = false,
    val developerLogsWarnErrorOnly: Boolean = false,
    val developerLogsPausedSnapshot: List<DeveloperLogEntry>? = null,
    val developerPanelStatusMessage: String = "关键操作后先刷新快照，再复制摘要和日志。",
    val developerLogsStateMessage: String = DeveloperLogStore.NO_LOGS_RECORDED_YET_MESSAGE,
    val developerSnapshot: DeveloperSnapshot = DeveloperSnapshot(),
)

data class TopVehicleStatus(
    val vehicleConnected: Boolean,
    val label: String,
    val kind: ConnectionStatus,
)

internal fun UavUiState.resolveTopVehicleStatus(): TopVehicleStatus {
    return when (activeBackend) {
        DroneBackend.SELF_ROS -> when {
            telemetry.connected -> TopVehicleStatus(
                vehicleConnected = true,
                label = "飞机已连接",
                kind = ConnectionStatus.CONNECTED,
            )

            rosConnectionStatus == ConnectionStatus.CONNECTED -> TopVehicleStatus(
                vehicleConnected = false,
                label = "ROS 已连通",
                kind = ConnectionStatus.CONNECTING,
            )

            rosConnectionStatus == ConnectionStatus.CONNECTING -> TopVehicleStatus(
                vehicleConnected = false,
                label = "ROS 连接中",
                kind = ConnectionStatus.CONNECTING,
            )

            rosConnectionStatus == ConnectionStatus.FAILED -> TopVehicleStatus(
                vehicleConnected = false,
                label = "ROS 连接失败",
                kind = ConnectionStatus.FAILED,
            )

            else -> TopVehicleStatus(
                vehicleConnected = false,
                label = "飞机离线",
                kind = ConnectionStatus.DISCONNECTED,
            )
        }

        DroneBackend.DJI -> when {
            djiProductConnected -> TopVehicleStatus(
                vehicleConnected = true,
                label = "DJI 飞机已连接",
                kind = ConnectionStatus.CONNECTED,
            )

            djiSdkInitState == DjiSdkInitState.REGISTERED -> TopVehicleStatus(
                vehicleConnected = false,
                label = "DJI 已就绪 · 飞机离线",
                kind = ConnectionStatus.CONNECTING,
            )

            djiSdkInitState == DjiSdkInitState.REGISTERING -> TopVehicleStatus(
                vehicleConnected = false,
                label = "DJI 注册中",
                kind = ConnectionStatus.CONNECTING,
            )

            djiSdkInitState == DjiSdkInitState.INITIALIZING ||
                djiSdkInitState == DjiSdkInitState.READY_TO_REGISTER -> TopVehicleStatus(
                vehicleConnected = false,
                label = "DJI 初始化中",
                kind = ConnectionStatus.CONNECTING,
            )

            djiSdkInitState == DjiSdkInitState.FAILED -> TopVehicleStatus(
                vehicleConnected = false,
                label = "DJI 失败",
                kind = ConnectionStatus.FAILED,
            )

            else -> TopVehicleStatus(
                vehicleConnected = false,
                label = "DJI 离线",
                kind = ConnectionStatus.DISCONNECTED,
            )
        }
    }
}

class UavViewModel(
    private val repository: UavRepository = UavRepository(),
    private val selfDroneController: DroneController = SelfDroneController(repository),
    private val djiDroneController: DroneController = DjiDroneController,
) : ViewModel() {
    constructor() : this(UavRepository())

    private var djiStatePollingJob: Job? = null

    private val _uiState = MutableStateFlow(syncDerivedState(UavUiState()))
    val uiState: StateFlow<UavUiState> = _uiState.asStateFlow()

    init {
        DeveloperLogStore.info(TAG, "ViewModel 已初始化")

        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                updateState { current ->
                    current.copy(
                        rosConnectionStatus = status,
                        connectionStatus = current.resolveActiveConnectionStatus(rosConnectionStatus = status),
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.telemetry.collect { telemetry ->
                updateState { current ->
                    current.copy(
                        telemetry = telemetry,
                        currentDroneState = if (current.activeBackend == DroneBackend.SELF_ROS) {
                            telemetry.toDroneState()
                        } else {
                            current.currentDroneState
                        },
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.events.collect { events ->
                updateState { it.copy(events = events) }
            }
        }

        viewModelScope.launch {
            repository.missions.collect { missions ->
                updateState { current ->
                    current.withDisplayedMissions(
                        rosMissionCache = missions,
                    )
                }
            }
        }

        viewModelScope.launch {
            DjiMsdkManager.initState.collect { initState ->
                updateState { current ->
                    current.copy(
                        djiSdkInitState = initState,
                        connectionStatus = current.resolveActiveConnectionStatus(djiSdkInitState = initState),
                    )
                }
            }
        }

        viewModelScope.launch {
            DjiMsdkManager.statusMessage.collect { message ->
                updateState { it.copy(djiSdkStatusMessage = message) }
                if (uiState.value.activeBackend == DroneBackend.DJI) {
                    refreshCurrentDroneState(force = true)
                }
                syncDjiStatePolling()
            }
        }

        viewModelScope.launch {
            DjiConnectionManager.connectionState.collect { connection ->
                updateState { current ->
                    current.copy(
                        djiProductConnected = connection.connected,
                        djiProductId = connection.productId,
                        djiProductTypeLabel = connection.productType?.name.orEmpty(),
                        djiProductStatusMessage = connection.statusMessage,
                        djiKeyConnectionValue = connection.keyConnectionValue,
                        djiLastConnectionSource = connection.lastConnectionSource.name,
                        djiLastRefreshReason = connection.lastRefreshReason,
                        djiLastRefreshSucceeded = connection.lastRefreshSucceeded,
                        djiLastRefreshError = connection.lastRefreshError,
                        djiConnectionMonitorRunning = connection.monitorRunning,
                        djiConnectionMonitorStartedAt = connection.monitorStartedAt,
                        djiConnectionMonitorTickCount = connection.monitorTickCount,
                        connectionStatus = current.resolveActiveConnectionStatus(djiProductConnected = connection.connected),
                    )
                }
                if (uiState.value.activeBackend == DroneBackend.DJI) {
                    refreshCurrentDroneState(force = true)
                }
                syncDjiStatePolling()
            }
        }

        viewModelScope.launch {
            DjiWaypointMissionManager.missionState.collect { missionState ->
                updateState { current ->
                    current.withDisplayedMissions(
                        djiMissionSummary = missionState.toMissionSummaryOrNull(),
                    )
                }
            }
        }

        viewModelScope.launch {
            DeveloperLogStore.entries.collect { logs ->
                updateState { current ->
                    if (current.developerLogsPaused) {
                        return@updateState current
                    }
                    val displayedLogs = filterDeveloperLogs(
                        logs = logs,
                        warnErrorOnly = current.developerLogsWarnErrorOnly,
                    )
                    current.copy(
                        developerLogs = displayedLogs,
                        developerLogsStateMessage = if (displayedLogs.isEmpty()) {
                            if (logs.isEmpty()) {
                                current.developerLogsStateMessage
                            } else {
                                "当前筛选条件下没有日志。"
                            }
                        } else {
                            "正在显示最近 ${displayedLogs.takeLast(80).size} 条日志。"
                        },
                    )
                }
            }
        }
    }

    fun onActiveBackendChanged(activeBackend: DroneBackend) {
        DeveloperLogStore.info(TAG, "后端已切换", activeBackend.name)
        updateState { current ->
            current.copy(
                activeBackend = activeBackend,
                statusMessage = when (activeBackend) {
                    DroneBackend.SELF_ROS -> "已切换到自研 ROS 后端"
                    DroneBackend.DJI -> "已切换到 DJI MSDK 后端"
                },
            ).withDisplayedMissions()
        }
        syncDjiStatePolling()
        refreshCurrentDroneState(force = true)
    }

    fun onSelectedDjiAircraftFamilyChanged(family: DjiAircraftFamily) {
        DeveloperLogStore.info(TAG, "已选择 DJI 机型", family.name)
        updateState { it.copy(selectedDjiAircraftFamily = family) }
    }

    fun onDjiPermissionStateChanged(snapshot: DjiPermissionSnapshot) {
        updateState { current ->
            current.copy(
                djiPermissionsGranted = snapshot.allGranted,
                djiMissingPermissions = snapshot.missingPermissions,
                djiPermissionStatusMessage = snapshot.statusMessage,
                statusMessage = if (current.activeBackend == DroneBackend.DJI) {
                    snapshot.statusMessage
                } else {
                    current.statusMessage
                },
            )
        }
    }

    fun onHostChanged(host: String) {
        updateState { it.copy(connectionConfig = it.connectionConfig.copy(host = host)) }
    }

    fun onPortChanged(port: String) {
        updateState { it.copy(connectionConfig = it.connectionConfig.copy(port = port)) }
    }

    fun connect() {
        val state = uiState.value
        DeveloperLogStore.info(TAG, "收到连接请求", state.activeBackend.name)
        when (state.activeBackend) {
            DroneBackend.SELF_ROS -> {
                val error = MissionDraftValidator.validateConnection(state.connectionConfig)
                if (error != null) {
                    pushStatus(error)
                    return
                }
                executeAction("正在连接 ${state.connectionConfig.websocketUrl}") {
                    selfDroneController.connect(state.connectionConfig)
                }
            }

            DroneBackend.DJI -> {
                if (!state.djiPermissionsGranted) {
                    pushStatus("请先授予 DJI 权限。${state.djiPermissionStatusMessage}")
                    return
                }
                executeAction("正在初始化 DJI") {
                    djiDroneController.connect(null)
                }
            }
        }
    }

    fun disconnect() {
        val state = uiState.value
        DeveloperLogStore.info(TAG, "收到断开请求", state.activeBackend.name)
        if (state.activeBackend == DroneBackend.DJI) {
            stopDjiStatePolling("收到 DJI 断开请求")
        }
        executeAction(
            pendingMessage = when (state.activeBackend) {
                DroneBackend.SELF_ROS -> "正在断开 rosbridge"
                DroneBackend.DJI -> "正在断开 DJI"
            },
        ) {
            controllerFor(state).disconnect()
        }
    }

    fun refreshMissions() {
        val state = uiState.value
        DeveloperLogStore.info(TAG, "收到刷新任务请求", state.activeBackend.name)
        executeAction(
            pendingMessage = when (state.activeBackend) {
                DroneBackend.SELF_ROS -> "正在刷新 ROS 任务"
                DroneBackend.DJI -> "正在刷新 DJI 任务状态"
            },
        ) {
            controllerFor(state).refreshMissions()
        }
    }

    fun refreshDeveloperSnapshot() {
        DeveloperLogStore.debug(TAG, "正在刷新开发者快照")
        if (uiState.value.activeBackend == DroneBackend.DJI) {
            DjiConnectionManager.refreshFromKeyManager("Developer Panel refresh snapshot")
            DjiConnectionManager.startConnectionMonitor("Developer Panel refresh snapshot")
        }
        refreshCurrentDroneState(force = true)
        updateState {
            it.copy(
                statusMessage = "诊断快照已刷新",
                developerPanelStatusMessage = "快照已刷新。先看 DJI 航点诊断，再按需复制摘要。",
            )
        }
    }

    fun openDeveloperPanel() {
        DeveloperLogStore.info(TAG, "开发者面板已打开")
        refreshCurrentDroneState(force = true)
        updateState {
            it.copy(
                developerPanelVisible = true,
                developerPanelStatusMessage = "开发者面板已打开。关键操作后先刷新快照。",
            )
        }
    }

    fun closeDeveloperPanel() {
        updateState { it.copy(developerPanelVisible = false) }
    }

    fun clearDeveloperLogs() {
        DeveloperLogStore.clear()
        updateState {
            it.copy(
                statusMessage = "开发日志已清空",
                developerLogs = emptyList(),
                developerLogsPausedSnapshot = null,
                developerPanelStatusMessage = DeveloperLogStore.LOGS_CLEARED_SUCCESSFULLY_MESSAGE,
                developerLogsStateMessage = DeveloperLogStore.LOGS_CLEARED_SUCCESSFULLY_MESSAGE,
            )
        }
    }

    fun toggleDeveloperLogsPaused() {
        updateState { current ->
            val paused = !current.developerLogsPaused
            val sourceLogs = if (paused) {
                DeveloperLogStore.entries.value
            } else {
                DeveloperLogStore.entries.value
            }
            val displayedLogs = filterDeveloperLogs(
                logs = if (paused) sourceLogs else sourceLogs,
                warnErrorOnly = current.developerLogsWarnErrorOnly,
            )
            current.copy(
                developerLogsPaused = paused,
                developerLogsPausedSnapshot = if (paused) sourceLogs else null,
                developerLogs = displayedLogs,
                developerPanelStatusMessage = if (paused) {
                    "日志自动刷新已暂停。当前列表已冻结，适合查看关键错误。"
                } else {
                    "日志自动刷新已恢复。当前显示最新日志。"
                },
                developerLogsStateMessage = when {
                    displayedLogs.isEmpty() && sourceLogs.isEmpty() -> DeveloperLogStore.NO_LOGS_RECORDED_YET_MESSAGE
                    displayedLogs.isEmpty() -> "当前筛选条件下没有日志。"
                    else -> "正在显示最近 ${displayedLogs.takeLast(80).size} 条日志。"
                },
            )
        }
    }

    fun toggleDeveloperLogsWarnErrorOnly() {
        updateState { current ->
            val warnErrorOnly = !current.developerLogsWarnErrorOnly
            val sourceLogs = current.developerLogsPausedSnapshot ?: DeveloperLogStore.entries.value
            val displayedLogs = filterDeveloperLogs(
                logs = sourceLogs,
                warnErrorOnly = warnErrorOnly,
            )
            current.copy(
                developerLogsWarnErrorOnly = warnErrorOnly,
                developerLogs = displayedLogs,
                developerPanelStatusMessage = if (warnErrorOnly) {
                    "日志已切换为只看 WARN / ERROR。"
                } else {
                    "日志已恢复显示全部级别。"
                },
                developerLogsStateMessage = when {
                    displayedLogs.isEmpty() && sourceLogs.isEmpty() -> DeveloperLogStore.NO_LOGS_RECORDED_YET_MESSAGE
                    displayedLogs.isEmpty() -> "当前筛选条件下没有日志。"
                    else -> "正在显示最近 ${displayedLogs.takeLast(80).size} 条日志。"
                },
            )
        }
    }

    fun onDeveloperSummaryCopied() {
        updateState {
            it.copy(
                developerPanelStatusMessage =
                    "诊断摘要已复制。需要分析时建议连同最近日志一起发送。",
            )
        }
    }

    fun onDeveloperLogsCopied(hasLogs: Boolean) {
        updateState {
            it.copy(
                developerPanelStatusMessage = if (hasLogs) {
                    "最近日志已复制，可用于核对初始化、上传、启动和失败回调顺序。"
                } else {
                    "当前没有日志可复制，已复制占位提示。"
                },
            )
        }
    }

    fun onDraftMissionIdChanged(missionId: String) {
        updateState { it.copy(draftMissionId = missionId) }
    }

    fun addWaypoint() {
        DeveloperLogStore.info(TAG, "收到新增航点请求")
        updateState { state ->
            state.copy(
                draftWaypoints = state.draftWaypoints + MissionWaypointDraft(),
                statusMessage = "已新增一个航点草稿",
            )
        }
    }

    fun importCurrentPositionAsWaypoint() {
        val state = uiState.value
        val refreshedState = controllerFor(state).getState().getOrElse { state.currentDroneState }
        updateState { it.copy(currentDroneState = refreshedState) }
        val result = WaypointImportSupport.createWaypointFromCurrentPosition(
            backend = state.activeBackend,
            currentDroneState = refreshedState,
            existingWaypoints = state.draftWaypoints,
        )
        result.onSuccess { waypoint ->
            DeveloperLogStore.info(
                TAG,
                "已导入当前位置",
                "lat=${waypoint.lat}, lon=${waypoint.lon}",
            )
            updateState { current ->
                current.copy(
                    draftWaypoints = listOf(waypoint) + current.draftWaypoints,
                    statusMessage = "已将当前位置插入为航点 1，原航点顺延",
                )
            }
        }.onFailure { throwable ->
            DeveloperLogStore.warn(TAG, "Import current position failed", throwable.message)
            pushStatus(throwable.message ?: "当前没有可用位置")
        }
    }

    fun removeWaypoint(index: Int) {
        updateState { state ->
            if (state.draftWaypoints.size <= 1) {
                state.copy(statusMessage = "至少保留一个航点")
            } else {
                DeveloperLogStore.info(TAG, "Removed waypoint", "index=${index + 1}")
                state.copy(
                    draftWaypoints = state.draftWaypoints.filterIndexed { waypointIndex, _ -> waypointIndex != index },
                    statusMessage = "已删除航点 ${index + 1}",
                )
            }
        }
    }

    fun updateWaypoint(index: Int, waypointDraft: MissionWaypointDraft) {
        updateState { state ->
            state.copy(
                draftWaypoints = state.draftWaypoints.mapIndexed { waypointIndex, item ->
                    if (waypointIndex == index) waypointDraft else item
                },
            )
        }
    }

    fun uploadDraftMission() {
        val state = uiState.value
        if (state.draftMissionId.isBlank()) {
            pushStatus("任务 ID 不能为空")
            return
        }
        if (state.activeBackend == DroneBackend.DJI && !ensureDjiReady("上传 DJI 任务")) {
            return
        }

        val parsed = MissionDraftValidator.parseMissionWaypoints(state.draftWaypoints)
        if (parsed.isFailure) {
            pushStatus(parsed.exceptionOrNull()?.message ?: "航点列表无效")
            return
        }

        DeveloperLogStore.info(TAG, "收到上传任务请求", state.draftMissionId)
        executeAction("正在上传 ${state.draftMissionId}") {
            controllerFor(state).uploadMission(
                missionId = state.draftMissionId,
                waypoints = parsed.getOrThrow(),
                selectedDjiAircraftFamily = state.selectedDjiAircraftFamily,
            )
        }
    }

    fun selectMission(missionId: String) {
        updateState { it.copy(selectedMissionId = missionId) }
    }

    fun startMission() = executeMissionAction(
        actionLabel = "正在开始任务",
        djiReadinessAction = "开始 DJI 任务",
    ) { controller, missionId ->
        controller.startMission(missionId)
    }

    fun pauseMission() = executeMissionAction(
        actionLabel = "正在暂停任务",
        djiReadinessAction = "暂停 DJI 任务",
    ) { controller, missionId ->
        controller.pauseMission(missionId)
    }

    fun resumeMission() = executeMissionAction(actionLabel = "正在继续任务") { controller, missionId ->
        controller.resumeMission(missionId)
    }

    fun stopMission() = executeMissionAction(
        actionLabel = "正在停止任务",
        djiReadinessAction = "停止 DJI 任务",
    ) { controller, missionId ->
        controller.stopMission(missionId)
    }

    fun rtl() = executeMissionAction(
        actionLabel = "正在请求返航",
        djiReadinessAction = "请求 DJI 返航",
    ) { controller, missionId ->
        controller.returnHome(missionId)
    }

    fun land() = executeMissionAction(
        actionLabel = "正在请求降落",
        djiReadinessAction = "请求 DJI 降落",
    ) { controller, missionId ->
        controller.land(missionId)
    }

    private fun executeMissionAction(
        actionLabel: String,
        djiReadinessAction: String? = null,
        block: suspend (DroneController, String?) -> ActionResult,
    ) {
        val state = uiState.value
        val controller = controllerFor(state)
        val missionId = state.selectedMissionId.takeIf { it.isNotBlank() }

        if (state.activeBackend == DroneBackend.SELF_ROS && missionId == null) {
            pushStatus("请先选择任务")
            return
        }

        if (state.activeBackend == DroneBackend.DJI && djiReadinessAction != null && !ensureDjiReady(djiReadinessAction)) {
            return
        }

        val pendingMessage = missionId?.let { "$actionLabel: $it" } ?: actionLabel
        DeveloperLogStore.info(TAG, actionLabel, missionId ?: state.activeBackend.name)
        executeAction(pendingMessage) {
            block(controller, missionId)
        }
    }

    private fun executeAction(
        pendingMessage: String,
        block: suspend () -> ActionResult,
    ) {
        updateState { it.copy(busy = true, statusMessage = pendingMessage) }
        viewModelScope.launch {
            val result = try {
                block()
            } catch (exception: Exception) {
                Log.e(TAG, "客户端操作失败: ${exception.message}", exception)
                DeveloperLogStore.error(TAG, "客户端操作失败", exception.message)
                ActionResult(
                    success = false,
                    errorCode = -1,
                    message = exception.message ?: "未知客户端错误",
                )
            }

            DeveloperLogStore.info(
                TAG,
                if (result.success) "操作完成" else "操作失败",
                result.message,
            )
            updateState {
                it.copy(
                    busy = false,
                    statusMessage = if (result.success) result.message else "错误 ${result.errorCode}: ${result.message}",
                )
            }
        }
    }

    private fun controllerFor(state: UavUiState): DroneController {
        return when (state.activeBackend) {
            DroneBackend.SELF_ROS -> selfDroneController
            DroneBackend.DJI -> djiDroneController
        }
    }

    private fun ensureDjiReady(actionName: String): Boolean {
        val state = uiState.value
        if (!state.djiPermissionsGranted) {
            pushStatus("执行前请先授予 DJI 权限。${state.djiPermissionStatusMessage}")
            return false
        }
        return when (state.djiSdkInitState) {
            DjiSdkInitState.SKIPPED,
            DjiSdkInitState.FAILED,
            -> {
                pushStatus("DJI 当前不可执行 $actionName。${state.djiSdkStatusMessage}")
                false
            }

            else -> {
                if (!state.djiProductConnected) {
                    pushStatus(
                        "DJI 当前不可执行 $actionName。SDK registered 不等于飞机已连接；" +
                            "当前飞机离线，KeyConnection=${state.djiKeyConnectionValue ?: "无"}。",
                    )
                    false
                } else {
                    true
                }
            }
        }
    }

    private fun pushStatus(message: String) {
        DeveloperLogStore.warn(TAG, "状态更新", message)
        updateState { it.copy(statusMessage = message) }
    }

    private fun refreshCurrentDroneState(force: Boolean = false) {
        val state = uiState.value
        val result = controllerFor(state).getState()
        val snapshot = result.getOrElse { throwable ->
            if (state.activeBackend == DroneBackend.DJI) {
                DeveloperLogStore.warn(
                    TAG,
                    "DJI 飞机状态读取失败",
                    throwable.message ?: "未知错误",
                )
            }
            DroneState(
                backend = state.activeBackend,
                statusMessage = if (state.activeBackend == DroneBackend.DJI) {
                    "DJI 飞机状态读取失败：${throwable.message ?: "无法读取当前飞机状态"}"
                } else {
                    throwable.message ?: "无法读取当前飞机状态"
                },
            )
        }
        if (force || snapshot != state.currentDroneState) {
            updateState { current ->
                current.copy(
                    currentDroneState = snapshot,
                    statusMessage = if (
                        current.activeBackend == DroneBackend.DJI &&
                        snapshot.statusMessage.isNotBlank()
                    ) {
                        snapshot.statusMessage
                    } else {
                        current.statusMessage
                    },
                )
            }
        }
    }

    private fun syncDjiStatePolling() {
        val state = uiState.value
        val shouldPoll = state.activeBackend == DroneBackend.DJI && state.djiProductConnected
        if (shouldPoll) {
            startDjiStatePolling()
        } else {
            stopDjiStatePolling(
                when {
                    state.activeBackend != DroneBackend.DJI -> "当前后端不是 DJI"
                    !state.djiProductConnected -> "DJI 飞机未连接"
                    else -> "DJI 状态轮询已停止"
                },
            )
        }
    }

    private fun startDjiStatePolling() {
        if (djiStatePollingJob?.isActive == true) {
            return
        }
        DeveloperLogStore.info(TAG, "启动 DJI 状态轮询", "间隔 2 秒")
        djiStatePollingJob = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    refreshCurrentDroneState(force = true)
                }.onFailure { throwable ->
                    val message = throwable.message ?: "未知错误"
                    DeveloperLogStore.warn(TAG, "DJI 状态轮询失败", message)
                    updateState { current ->
                        current.copy(
                            statusMessage = "DJI 飞行信息刷新失败：$message",
                            currentDroneState = current.currentDroneState.copy(
                                backend = DroneBackend.DJI,
                                connectionState = if (current.djiProductConnected) {
                                    current.currentDroneState.connectionState
                                } else {
                                    com.example.uavmobile.core.DroneConnectionState.DISCONNECTED
                                },
                                statusMessage = "DJI 飞行信息刷新失败：$message",
                            ),
                        )
                    }
                }
                delay(2000)
            }
        }
    }

    private fun stopDjiStatePolling(reason: String) {
        val job = djiStatePollingJob ?: return
        job.cancel()
        djiStatePollingJob = null
        DeveloperLogStore.info(TAG, "停止 DJI 状态轮询", reason)
    }

    override fun onCleared() {
        stopDjiStatePolling("ViewModel 已销毁")
        super.onCleared()
    }

    private fun updateState(transform: (UavUiState) -> UavUiState) {
        _uiState.update { current ->
            syncDerivedState(transform(current))
        }
    }

    private fun syncDerivedState(state: UavUiState): UavUiState {
        val topStatus = state.resolveTopVehicleStatus()
        val syncedState = state.copy(
            vehicleConnected = topStatus.vehicleConnected,
            topStatusLabel = topStatus.label,
            topStatusKind = topStatus.kind,
        )
        return syncedState.copy(
            developerSnapshot = buildDeveloperSnapshot(syncedState),
        )
    }

    private fun buildDeveloperSnapshot(state: UavUiState): DeveloperSnapshot {
        val selectedMission = state.missions.firstOrNull { it.missionId == state.selectedMissionId }
        return DeveloperSnapshot(
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            activeBackendLabel = state.activeBackend.displayLabel(),
            selectedDjiAircraftFamilyLabel = state.selectedDjiAircraftFamily.displayLabel(),
            topStatusLabel = state.topStatusLabel,
            topStatusKind = state.topStatusKind.name,
            vehicleConnected = state.vehicleConnected,
            rosWebsocketUrl = state.connectionConfig.websocketUrl,
            rosConnectionStatus = state.rosConnectionStatus.name,
            rosSessionActive = state.telemetry.sessionActive,
            rosMissionCacheCount = state.rosMissionCache.size,
            rosLatestAlert = state.telemetry.latestAlert,
            djiSdkInitState = state.djiSdkInitState.name,
            djiSdkStatusMessage = state.djiSdkStatusMessage,
            djiProductConnected = state.djiProductConnected,
            djiProductId = state.djiProductId,
            djiProductTypeLabel = state.djiProductTypeLabel,
            djiProductStatusMessage = state.djiProductStatusMessage,
            djiKeyConnectionValue = state.djiKeyConnectionValue,
            djiLastConnectionSource = state.djiLastConnectionSource,
            djiLastRefreshReason = state.djiLastRefreshReason,
            djiLastRefreshSucceeded = state.djiLastRefreshSucceeded,
            djiLastRefreshError = state.djiLastRefreshError,
            djiConnectionMonitorRunning = state.djiConnectionMonitorRunning,
            djiConnectionMonitorTickCount = state.djiConnectionMonitorTickCount,
            djiPermissionsGranted = state.djiPermissionsGranted,
            djiMissingPermissions = state.djiMissingPermissions,
            currentLatitude = state.currentDroneState.latitude,
            currentLongitude = state.currentDroneState.longitude,
            currentAltitudeMeters = state.currentDroneState.altitudeMeters,
            currentHeadingDegrees = state.currentDroneState.headingDegrees,
            currentHomeLatitude = state.currentDroneState.homeLatitude,
            currentHomeLongitude = state.currentDroneState.homeLongitude,
            currentStateMessage = state.currentDroneState.statusMessage,
            selectedMissionId = state.selectedMissionId,
            displayedMissionCount = state.missions.size,
            selectedMissionStatus = selectedMission?.status.orEmpty(),
            selectedMissionProgress = selectedMission?.progress ?: 0f,
            djiWaypointDiagnostics = DjiWaypointDiagnosticSnapshot(
                preparedMissionId = DjiWaypointMissionManager.missionState.value.missionId,
                preparedMissionFileName = DjiWaypointMissionManager.missionState.value.missionFileName,
                kmzPath = DjiWaypointMissionManager.missionState.value.kmzPath,
                kmzFileExists = DjiWaypointMissionManager.missionState.value.kmzFileExists,
                kmzFileSizeBytes = DjiWaypointMissionManager.missionState.value.kmzFileSizeBytes,
                selectedDjiAircraftFamily = DjiWaypointMissionManager.missionState.value.selectedDjiAircraftFamily,
                resolvedWaylineDroneType = DjiWaypointMissionManager.missionState.value.resolvedWaylineDroneType,
                lastDjiWaypointAction = DjiWaypointMissionManager.missionState.value.lastDjiWaypointAction,
                lastDjiWaypointActionSuccess = DjiWaypointMissionManager.missionState.value.lastDjiWaypointActionSuccess,
                lastDjiWaypointError = DjiWaypointMissionManager.missionState.value.lastDjiWaypointError,
                lastDjiWaypointErrorHint = DjiWaypointMissionManager.missionState.value.lastDjiWaypointErrorHint,
                missionExecutionState = DjiWaypointMissionManager.missionState.value.state.name,
                sdkMissionExecuteState = DjiWaypointMissionManager.missionState.value.sdkMissionExecuteState,
                currentWaypointIndex = DjiWaypointMissionManager.missionState.value.currentWaypointIndex,
                missionProgress = DjiWaypointMissionManager.missionState.value.progress,
                lastInterruptionReason = DjiWaypointMissionManager.missionState.value.lastInterruptionReason,
                lastInterruptionDiagnostics = DjiWaypointMissionManager.missionState.value.lastInterruptionDiagnostics,
            ),
            djiAircraftDiagnostics = DjiAircraftDiagnosticSnapshot(
                productConnected = state.djiProductConnected,
                productType = state.djiProductTypeLabel,
                flightMode = state.currentDroneState.flightMode,
                motorsOn = state.currentDroneState.motorsOn,
                isFlying = state.currentDroneState.isFlying,
                isOnGround = state.currentDroneState.isOnGround,
                isSimulatorStarted = state.currentDroneState.isSimulatorStarted,
                homeLatitude = state.currentDroneState.homeLatitude,
                homeLongitude = state.currentDroneState.homeLongitude,
                gpsSignalLevel = state.currentDroneState.gpsSignalLevel,
                gpsSatelliteCount = state.currentDroneState.gpsSatelliteCount,
                batteryPercent = state.currentDroneState.batteryPercent,
                batteryVoltage = state.currentDroneState.batteryVoltage,
                groundSpeedMps = state.currentDroneState.groundSpeedMps,
                locationReadSucceeded = state.currentDroneState.locationReadSucceeded,
                locationReadError = state.currentDroneState.locationReadError,
                telemetryReadSucceeded = state.currentDroneState.telemetryReadSucceeded,
                telemetryReadError = state.currentDroneState.telemetryReadError,
                rtkStatus = state.currentDroneState.rtkStatus,
            ),
            djiConnectionDiagnostics = DjiConnectionDiagnosticSnapshot(
                productConnected = state.djiProductConnected,
                productType = state.djiProductTypeLabel,
                keyConnectionValue = state.djiKeyConnectionValue,
                lastConnectionSource = state.djiLastConnectionSource,
                lastRefreshReason = state.djiLastRefreshReason,
                lastRefreshSucceeded = state.djiLastRefreshSucceeded,
                lastRefreshError = state.djiLastRefreshError,
                monitorRunning = state.djiConnectionMonitorRunning,
                monitorStartedAt = state.djiConnectionMonitorStartedAt,
                monitorTickCount = state.djiConnectionMonitorTickCount,
                statusMessage = state.djiProductStatusMessage,
            ),
        )
    }

    private fun UavUiState.withDisplayedMissions(
        rosMissionCache: List<MissionSummary> = this.rosMissionCache,
        djiMissionSummary: MissionSummary? = this.djiMissionSummary,
    ): UavUiState {
        val activeMissions = when (activeBackend) {
            DroneBackend.SELF_ROS -> rosMissionCache
            DroneBackend.DJI -> listOfNotNull(djiMissionSummary)
        }
        val selectedMissionId = when {
            this.selectedMissionId.isNotBlank() && activeMissions.any { it.missionId == this.selectedMissionId } -> this.selectedMissionId
            activeMissions.isNotEmpty() -> activeMissions.first().missionId
            else -> ""
        }
        return copy(
            rosMissionCache = rosMissionCache,
            djiMissionSummary = djiMissionSummary,
            missions = activeMissions,
            selectedMissionId = selectedMissionId,
            connectionStatus = resolveActiveConnectionStatus(),
        )
    }

    private fun UavUiState.resolveActiveConnectionStatus(
        rosConnectionStatus: ConnectionStatus = this.rosConnectionStatus,
        djiSdkInitState: DjiSdkInitState = this.djiSdkInitState,
        djiProductConnected: Boolean = this.djiProductConnected,
    ): ConnectionStatus {
        return when (activeBackend) {
            DroneBackend.SELF_ROS -> rosConnectionStatus
            DroneBackend.DJI -> when {
                djiProductConnected -> ConnectionStatus.CONNECTED
                djiSdkInitState == DjiSdkInitState.INITIALIZING ||
                    djiSdkInitState == DjiSdkInitState.READY_TO_REGISTER ||
                    djiSdkInitState == DjiSdkInitState.REGISTERING ||
                    djiSdkInitState == DjiSdkInitState.REGISTERED -> ConnectionStatus.CONNECTING

                djiSdkInitState == DjiSdkInitState.FAILED -> ConnectionStatus.FAILED
                else -> ConnectionStatus.DISCONNECTED
            }
        }
    }

    companion object {
        private const val TAG = "UavViewModel"
    }
}

private fun filterDeveloperLogs(
    logs: List<DeveloperLogEntry>,
    warnErrorOnly: Boolean,
): List<DeveloperLogEntry> {
    if (!warnErrorOnly) {
        return logs
    }
    return logs.filter { entry ->
        entry.level == DeveloperLogLevel.WARN || entry.level == DeveloperLogLevel.ERROR
    }
}

private fun DroneBackend.displayLabel(): String {
    return when (this) {
        DroneBackend.SELF_ROS -> "自研 ROS"
        DroneBackend.DJI -> "DJI MSDK"
    }
}

private fun DjiAircraftFamily.displayLabel(): String {
    return when (this) {
        DjiAircraftFamily.AUTO -> "自动"
        DjiAircraftFamily.M400 -> "M400"
        DjiAircraftFamily.MATRICE_4_SERIES -> "御 4 / M4"
    }
}
