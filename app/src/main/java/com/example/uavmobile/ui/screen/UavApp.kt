package com.example.uavmobile.ui.screen

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.data.model.ConnectionStatus
import com.example.uavmobile.ui.theme.Alert
import com.example.uavmobile.ui.theme.Success
import com.example.uavmobile.ui.viewmodel.UavViewModel

private enum class AppSection(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    CONNECT("Connect", Icons.Outlined.CellTower),
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    MISSION("Mission", Icons.Outlined.Map),
    CONTROL("Control", Icons.Outlined.Flight),
    EVENTS("Events", Icons.Outlined.Notifications),
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UavApp(
    viewModel: UavViewModel,
    onRequestDjiPermissions: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentSection by rememberSaveable { mutableStateOf(AppSection.CONNECT) }

    if (state.developerPanelVisible) {
        DeveloperPanelScreen(
            state = state,
            onClose = viewModel::closeDeveloperPanel,
            onRefreshSnapshot = viewModel::refreshDeveloperSnapshot,
            onClearLogs = viewModel::clearDeveloperLogs,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PX4 Mobile Task Client", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Active backend: ${state.activeBackend.displayLabel()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    AssistChip(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = viewModel::openDeveloperPanel,
                        ),
                        onClick = {},
                        label = {
                            Text(
                                when (state.connectionStatus) {
                                    ConnectionStatus.CONNECTED -> "Connected"
                                    ConnectionStatus.CONNECTING -> "Connecting"
                                    ConnectionStatus.FAILED -> "Failed"
                                    ConnectionStatus.DISCONNECTED -> "Offline"
                                },
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = when (state.connectionStatus) {
                                            ConnectionStatus.CONNECTED -> Success
                                            ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondary
                                            ConnectionStatus.FAILED -> Alert
                                            ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                                        },
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .padding(6.dp),
                            )
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = currentSection == section,
                        onClick = { currentSection = section },
                        icon = { Icon(section.icon, contentDescription = section.label) },
                        label = { Text(section.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (currentSection) {
                AppSection.CONNECT -> ConnectionScreen(
                    state = state,
                    onActiveBackendChanged = viewModel::onActiveBackendChanged,
                    onSelectedDjiAircraftFamilyChanged = viewModel::onSelectedDjiAircraftFamilyChanged,
                    onHostChanged = viewModel::onHostChanged,
                    onPortChanged = viewModel::onPortChanged,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onRefreshMissions = viewModel::refreshMissions,
                    onRequestDjiPermissions = onRequestDjiPermissions,
                )

                AppSection.DASHBOARD -> DashboardScreen(state = state)
                AppSection.MISSION -> MissionScreen(
                    state = state,
                    onMissionIdChanged = viewModel::onDraftMissionIdChanged,
                    onAddWaypoint = viewModel::addWaypoint,
                    onImportCurrentPosition = viewModel::importCurrentPositionAsWaypoint,
                    onRemoveWaypoint = viewModel::removeWaypoint,
                    onWaypointChanged = viewModel::updateWaypoint,
                    onUploadMission = viewModel::uploadDraftMission,
                )

                AppSection.CONTROL -> ControlScreen(
                    state = state,
                    onMissionSelected = viewModel::selectMission,
                    onRefresh = viewModel::refreshMissions,
                    onStart = viewModel::startMission,
                    onPause = viewModel::pauseMission,
                    secondaryActionLabel = if (state.activeBackend == DroneBackend.DJI) "Stop" else "Resume",
                    onSecondaryAction = if (state.activeBackend == DroneBackend.DJI) {
                        viewModel::stopMission
                    } else {
                        viewModel::resumeMission
                    },
                    onRtl = viewModel::rtl,
                    onLand = viewModel::land,
                )

                AppSection.EVENTS -> EventScreen(events = state.events)
            }
        }
    }
}

private fun DroneBackend.displayLabel(): String {
    return when (this) {
        DroneBackend.SELF_ROS -> "Self ROS"
        DroneBackend.DJI -> "DJI MSDK"
    }
}
