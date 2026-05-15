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
import com.example.uavmobile.debug.DeveloperLogStore
import com.example.uavmobile.debug.DeveloperSnapshot
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
    val statusMessage: String = "Ready to connect to rosbridge or initialize DJI MSDK",
    val busy: Boolean = false,
    val djiPermissionsGranted: Boolean = false,
    val djiMissingPermissions: List<String> = emptyList(),
    val djiPermissionStatusMessage: String = "DJI runtime permissions not checked",
    val djiSdkInitState: DjiSdkInitState = DjiMsdkManager.initState.value,
    val djiSdkStatusMessage: String = DjiMsdkManager.describeStatus(),
    val djiProductConnected: Boolean = false,
    val djiProductId: Int? = null,
    val djiProductTypeLabel: String = "",
    val djiProductStatusMessage: String = DjiConnectionManager.describeStatus(),
    val developerPanelVisible: Boolean = false,
    val developerLogs: List<DeveloperLogEntry> = emptyList(),
    val developerSnapshot: DeveloperSnapshot = DeveloperSnapshot(),
)

class UavViewModel(
    private val repository: UavRepository = UavRepository(),
    private val selfDroneController: DroneController = SelfDroneController(repository),
    private val djiDroneController: DroneController = DjiDroneController,
) : ViewModel() {
    constructor() : this(UavRepository())

    private val _uiState = MutableStateFlow(syncDerivedState(UavUiState()))
    val uiState: StateFlow<UavUiState> = _uiState.asStateFlow()

    init {
        DeveloperLogStore.info(TAG, "UavViewModel initialized")

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
                        connectionStatus = current.resolveActiveConnectionStatus(djiProductConnected = connection.connected),
                    )
                }
                if (uiState.value.activeBackend == DroneBackend.DJI) {
                    refreshCurrentDroneState(force = true)
                }
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
                updateState { it.copy(developerLogs = logs) }
            }
        }
    }

    fun onActiveBackendChanged(activeBackend: DroneBackend) {
        DeveloperLogStore.info(TAG, "Active backend changed", activeBackend.name)
        updateState { current ->
            current.copy(
                activeBackend = activeBackend,
                statusMessage = when (activeBackend) {
                    DroneBackend.SELF_ROS -> "Switched to self-drone ROS backend"
                    DroneBackend.DJI -> "Switched to DJI MSDK backend"
                },
            ).withDisplayedMissions()
        }
        refreshCurrentDroneState(force = true)
    }

    fun onSelectedDjiAircraftFamilyChanged(family: DjiAircraftFamily) {
        DeveloperLogStore.info(TAG, "Selected DJI aircraft family", family.name)
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
        DeveloperLogStore.info(TAG, "Connect requested", state.activeBackend.name)
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
        DeveloperLogStore.info(TAG, "Disconnect requested", state.activeBackend.name)
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
        DeveloperLogStore.info(TAG, "Refresh mission state requested", state.activeBackend.name)
        executeAction(
            pendingMessage = when (state.activeBackend) {
                DroneBackend.SELF_ROS -> "Refreshing ROS missions"
                DroneBackend.DJI -> "Refreshing DJI mission state"
            },
        ) {
            controllerFor(state).refreshMissions()
        }
    }

    fun refreshDeveloperSnapshot() {
        DeveloperLogStore.debug(TAG, "Refreshing developer snapshot")
        refreshCurrentDroneState(force = true)
        updateState { it.copy(statusMessage = "Developer snapshot refreshed") }
    }

    fun openDeveloperPanel() {
        DeveloperLogStore.info(TAG, "Developer panel opened")
        refreshCurrentDroneState(force = true)
        updateState { it.copy(developerPanelVisible = true) }
    }

    fun closeDeveloperPanel() {
        updateState { it.copy(developerPanelVisible = false) }
    }

    fun clearDeveloperLogs() {
        DeveloperLogStore.clear()
        updateState { it.copy(statusMessage = "Developer logs cleared") }
    }

    fun onDraftMissionIdChanged(missionId: String) {
        updateState { it.copy(draftMissionId = missionId) }
    }

    fun addWaypoint() {
        DeveloperLogStore.info(TAG, "Add waypoint requested")
        updateState { state ->
            state.copy(
                draftWaypoints = state.draftWaypoints + MissionWaypointDraft(),
                statusMessage = "Added a new waypoint draft",
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
                "Imported current aircraft position",
                "lat=${waypoint.lat}, lon=${waypoint.lon}",
            )
            updateState { current ->
                current.copy(
                    draftWaypoints = current.draftWaypoints + waypoint,
                    statusMessage = "Imported current aircraft position into a new waypoint",
                )
            }
        }.onFailure { throwable ->
            DeveloperLogStore.warn(TAG, "Import current position failed", throwable.message)
            pushStatus(throwable.message ?: "Current aircraft position is not available")
        }
    }

    fun removeWaypoint(index: Int) {
        updateState { state ->
            if (state.draftWaypoints.size <= 1) {
                state.copy(statusMessage = "At least one waypoint is required")
            } else {
                DeveloperLogStore.info(TAG, "Removed waypoint", "index=${index + 1}")
                state.copy(
                    draftWaypoints = state.draftWaypoints.filterIndexed { waypointIndex, _ -> waypointIndex != index },
                    statusMessage = "Removed waypoint ${index + 1}",
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

        DeveloperLogStore.info(TAG, "Upload mission requested", state.draftMissionId)
        executeAction("Uploading ${state.draftMissionId}") {
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
                Log.e(TAG, "Client action failed: ${exception.message}", exception)
                DeveloperLogStore.error(TAG, "Client action failed", exception.message)
                ActionResult(
                    success = false,
                    errorCode = -1,
                    message = exception.message ?: "Unknown client error",
                )
            }

            DeveloperLogStore.info(
                TAG,
                if (result.success) "Action completed" else "Action failed",
                result.message,
            )
            updateState {
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
        DeveloperLogStore.warn(TAG, "Status update", message)
        updateState { it.copy(statusMessage = message) }
    }

    private fun refreshCurrentDroneState(force: Boolean = false) {
        val state = uiState.value
        val result = controllerFor(state).getState()
        val snapshot = result.getOrElse { throwable ->
            DroneState(
                backend = state.activeBackend,
                statusMessage = throwable.message ?: "Unable to read current aircraft state",
            )
        }
        if (force || snapshot != state.currentDroneState) {
            updateState { it.copy(currentDroneState = snapshot) }
        }
    }

    private fun updateState(transform: (UavUiState) -> UavUiState) {
        _uiState.update { current ->
            syncDerivedState(transform(current))
        }
    }

    private fun syncDerivedState(state: UavUiState): UavUiState {
        return state.copy(
            developerSnapshot = buildDeveloperSnapshot(state),
        )
    }

    private fun buildDeveloperSnapshot(state: UavUiState): DeveloperSnapshot {
        val selectedMission = state.missions.firstOrNull { it.missionId == state.selectedMissionId }
        return DeveloperSnapshot(
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            activeBackendLabel = state.activeBackend.name,
            selectedDjiAircraftFamilyLabel = state.selectedDjiAircraftFamily.name,
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
