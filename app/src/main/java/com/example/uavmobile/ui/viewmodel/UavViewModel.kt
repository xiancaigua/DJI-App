package com.example.uavmobile.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneController
import com.example.uavmobile.core.MissionExecutionState
import com.example.uavmobile.core.SelfDroneController
import com.example.uavmobile.core.toMissionSummaryOrNull
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.data.model.ConnectionConfig
import com.example.uavmobile.data.model.ConnectionStatus
import com.example.uavmobile.data.model.MobileEvent
import com.example.uavmobile.data.model.MissionSummary
import com.example.uavmobile.data.model.MissionWaypointDraft
import com.example.uavmobile.data.model.TelemetrySnapshot
import com.example.uavmobile.data.repository.UavRepository
import com.example.uavmobile.dji.DjiConnectionManager
import com.example.uavmobile.dji.DjiDroneController
import com.example.uavmobile.dji.DjiMsdkManager
import com.example.uavmobile.dji.DjiPermissionSnapshot
import com.example.uavmobile.dji.DjiSdkInitState
import com.example.uavmobile.dji.DjiWaypointMissionManager
import com.example.uavmobile.domain.MissionDraftValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UavUiState(
    val activeBackend: DroneBackend = DroneBackend.SELF_ROS,
    val selectedDjiAircraftFamily: DjiAircraftFamily = DjiAircraftFamily.AUTO,
    val connectionConfig: ConnectionConfig = ConnectionConfig(),
    val rosConnectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
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
    val statusMessage: String = "Ready to connect to rosbridge or initialize DJI MSDK",
    val busy: Boolean = false,
    val djiPermissionsGranted: Boolean = false,
    val djiMissingPermissions: List<String> = emptyList(),
    val djiPermissionStatusMessage: String = "DJI runtime permissions not checked",
    val djiSdkInitState: DjiSdkInitState = DjiMsdkManager.initState.value,
    val djiSdkStatusMessage: String = DjiMsdkManager.describeStatus(),
    val djiProductConnected: Boolean = false,
    val djiProductStatusMessage: String = DjiConnectionManager.describeStatus(),
)

class UavViewModel(
    private val repository: UavRepository = UavRepository(),
    private val selfDroneController: DroneController = SelfDroneController(repository),
    private val djiDroneController: DroneController = DjiDroneController,
) : ViewModel() {
    constructor() : this(UavRepository())

    private val _uiState = MutableStateFlow(UavUiState())
    val uiState: StateFlow<UavUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                _uiState.update { current ->
                    current.copy(
                        rosConnectionStatus = status,
                        connectionStatus = current.resolveActiveConnectionStatus(rosConnectionStatus = status),
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.telemetry.collect { telemetry ->
                _uiState.update { it.copy(telemetry = telemetry) }
            }
        }

        viewModelScope.launch {
            repository.events.collect { events ->
                _uiState.update { it.copy(events = events) }
            }
        }

        viewModelScope.launch {
            repository.missions.collect { missions ->
                _uiState.update { current ->
                    current.withDisplayedMissions(
                        rosMissionCache = missions,
                    )
                }
            }
        }

        viewModelScope.launch {
            DjiMsdkManager.initState.collect { initState ->
                _uiState.update { current ->
                    current.copy(
                        djiSdkInitState = initState,
                        connectionStatus = current.resolveActiveConnectionStatus(djiSdkInitState = initState),
                    )
                }
            }
        }

        viewModelScope.launch {
            DjiMsdkManager.statusMessage.collect { message ->
                _uiState.update { it.copy(djiSdkStatusMessage = message) }
            }
        }

        viewModelScope.launch {
            DjiConnectionManager.connectionState.collect { connection ->
                _uiState.update { current ->
                    current.copy(
                        djiProductConnected = connection.connected,
                        djiProductStatusMessage = connection.statusMessage,
                        connectionStatus = current.resolveActiveConnectionStatus(djiProductConnected = connection.connected),
                    )
                }
            }
        }

        viewModelScope.launch {
            DjiWaypointMissionManager.missionState.collect { missionState ->
                _uiState.update { current ->
                    current.withDisplayedMissions(
                        djiMissionSummary = missionState.toMissionSummaryOrNull(),
                    )
                }
            }
        }
    }

    fun onActiveBackendChanged(activeBackend: DroneBackend) {
        _uiState.update { current ->
            current.copy(
                activeBackend = activeBackend,
                statusMessage = when (activeBackend) {
                    DroneBackend.SELF_ROS -> "Switched to self-drone ROS backend"
                    DroneBackend.DJI -> "Switched to DJI MSDK backend"
                },
            ).withDisplayedMissions()
        }
    }

    fun onSelectedDjiAircraftFamilyChanged(family: DjiAircraftFamily) {
        _uiState.update { it.copy(selectedDjiAircraftFamily = family) }
    }

    fun onDjiPermissionStateChanged(snapshot: DjiPermissionSnapshot) {
        _uiState.update { current ->
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
        _uiState.update { it.copy(connectionConfig = it.connectionConfig.copy(host = host)) }
    }

    fun onPortChanged(port: String) {
        _uiState.update { it.copy(connectionConfig = it.connectionConfig.copy(port = port)) }
    }

    fun connect() {
        val state = uiState.value
        when (state.activeBackend) {
            DroneBackend.SELF_ROS -> {
                val error = MissionDraftValidator.validateConnection(state.connectionConfig)
                if (error != null) {
                    pushStatus(error)
                    return
                }
                executeAction("Connecting to ${state.connectionConfig.websocketUrl}") {
                    selfDroneController.connect(state.connectionConfig)
                }
            }

            DroneBackend.DJI -> {
                if (!state.djiPermissionsGranted) {
                    pushStatus("Grant DJI permissions before initializing MSDK. ${state.djiPermissionStatusMessage}")
                    return
                }
                executeAction("Initializing DJI MSDK") {
                    djiDroneController.connect(null)
                }
            }
        }
    }

    fun disconnect() {
        val state = uiState.value
        executeAction(
            pendingMessage = when (state.activeBackend) {
                DroneBackend.SELF_ROS -> "Disconnecting from rosbridge"
                DroneBackend.DJI -> "Disconnecting DJI backend"
            },
        ) {
            controllerFor(state).disconnect()
        }
    }

    fun refreshMissions() {
        val state = uiState.value
        executeAction(
            pendingMessage = when (state.activeBackend) {
                DroneBackend.SELF_ROS -> "Refreshing ROS missions"
                DroneBackend.DJI -> "Refreshing DJI mission state"
            },
        ) {
            controllerFor(state).refreshMissions()
        }
    }

    fun onDraftMissionIdChanged(missionId: String) {
        _uiState.update { it.copy(draftMissionId = missionId) }
    }

    fun addWaypoint() {
        _uiState.update { state ->
            state.copy(
                draftWaypoints = state.draftWaypoints + MissionWaypointDraft(),
                statusMessage = "Added a new waypoint draft",
            )
        }
    }

    fun removeWaypoint(index: Int) {
        _uiState.update { state ->
            if (state.draftWaypoints.size <= 1) {
                state.copy(statusMessage = "At least one waypoint is required")
            } else {
                state.copy(
                    draftWaypoints = state.draftWaypoints.filterIndexed { waypointIndex, _ -> waypointIndex != index },
                    statusMessage = "Removed waypoint ${index + 1}",
                )
            }
        }
    }

    fun updateWaypoint(index: Int, waypointDraft: MissionWaypointDraft) {
        _uiState.update { state ->
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
            pushStatus("Mission ID is required")
            return
        }
        if (state.activeBackend == DroneBackend.DJI && !ensureDjiReady("upload a DJI mission")) {
            return
        }

        val parsed = MissionDraftValidator.parseMissionWaypoints(state.draftWaypoints)
        if (parsed.isFailure) {
            pushStatus(parsed.exceptionOrNull()?.message ?: "Invalid waypoint list")
            return
        }

        executeAction("Uploading ${state.draftMissionId}") {
            controllerFor(state).uploadMission(
                missionId = state.draftMissionId,
                waypoints = parsed.getOrThrow(),
                selectedDjiAircraftFamily = state.selectedDjiAircraftFamily,
            )
        }
    }

    fun selectMission(missionId: String) {
        _uiState.update { it.copy(selectedMissionId = missionId) }
    }

    fun startMission() = executeMissionAction(
        actionLabel = "Starting mission",
        djiReadinessAction = "start a DJI mission",
    ) { controller, missionId ->
        controller.startMission(missionId)
    }

    fun pauseMission() = executeMissionAction(
        actionLabel = "Pausing mission",
        djiReadinessAction = "pause a DJI mission",
    ) { controller, missionId ->
        controller.pauseMission(missionId)
    }

    fun resumeMission() = executeMissionAction(actionLabel = "Resuming mission") { controller, missionId ->
        controller.resumeMission(missionId)
    }

    fun stopMission() = executeMissionAction(
        actionLabel = "Stopping mission",
        djiReadinessAction = "stop a DJI mission",
    ) { controller, missionId ->
        controller.stopMission(missionId)
    }

    fun rtl() = executeMissionAction(
        actionLabel = "Requesting RTL",
        djiReadinessAction = "request DJI return-to-home",
    ) { controller, missionId ->
        controller.returnHome(missionId)
    }

    fun land() = executeMissionAction(
        actionLabel = "Requesting landing",
        djiReadinessAction = "request DJI landing",
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
            pushStatus("Select a mission first")
            return
        }

        if (state.activeBackend == DroneBackend.DJI && djiReadinessAction != null && !ensureDjiReady(djiReadinessAction)) {
            return
        }

        val pendingMessage = missionId?.let { "$actionLabel: $it" } ?: actionLabel
        executeAction(pendingMessage) {
            block(controller, missionId)
        }
    }

    private fun executeAction(
        pendingMessage: String,
        block: suspend () -> ActionResult,
    ) {
        _uiState.update { it.copy(busy = true, statusMessage = pendingMessage) }
        viewModelScope.launch {
            val result = try {
                block()
            } catch (exception: Exception) {
                Log.e(TAG, "Client action failed: ${exception.message}", exception)
                ActionResult(
                    success = false,
                    errorCode = -1,
                    message = exception.message ?: "Unknown client error",
                )
            }

            _uiState.update {
                it.copy(
                    busy = false,
                    statusMessage = if (result.success) result.message else "Error ${result.errorCode}: ${result.message}",
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
            pushStatus("Grant DJI permissions before you $actionName. ${state.djiPermissionStatusMessage}")
            return false
        }
        return when (state.djiSdkInitState) {
            DjiSdkInitState.SKIPPED,
            DjiSdkInitState.FAILED,
            -> {
                pushStatus("DJI backend is not ready to $actionName. ${state.djiSdkStatusMessage}")
                false
            }

            else -> true
        }
    }

    private fun pushStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
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
