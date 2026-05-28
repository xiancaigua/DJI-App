package com.example.uavmobile.ui.screen

import android.os.SystemClock
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    CONNECT("连接", Icons.Outlined.CellTower),
    DASHBOARD("总览", Icons.Outlined.Dashboard),
    MISSION("任务", Icons.Outlined.Map),
    CONTROL("控制", Icons.Outlined.Flight),
    EVENTS("事件", Icons.Outlined.Notifications),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UavApp(
    viewModel: UavViewModel,
    onRequestDjiPermissions: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentSection by rememberSaveable { mutableStateOf(AppSection.CONNECT) }
    var statusChipTapCount by remember { mutableStateOf(0) }
    var lastStatusChipTapAt by remember { mutableStateOf(0L) }

    if (state.developerPanelVisible) {
        DeveloperPanelScreen(
            state = state,
            onClose = viewModel::closeDeveloperPanel,
            onRefreshSnapshot = viewModel::refreshDeveloperSnapshot,
            onCopyDiagnosticSummary = viewModel::onDeveloperSummaryCopied,
            onCopyRecentLogs = viewModel::onDeveloperLogsCopied,
            onClearLogs = viewModel::clearDeveloperLogs,
            onToggleLogsPaused = viewModel::toggleDeveloperLogsPaused,
            onToggleWarnErrorOnly = viewModel::toggleDeveloperLogsWarnErrorOnly,
        )
        return
    }

    fun handleStatusChipTap() {
        val now = SystemClock.elapsedRealtime()
        statusChipTapCount = if (now - lastStatusChipTapAt <= 1_200L) {
            statusChipTapCount + 1
        } else {
            1
        }
        lastStatusChipTapAt = now
        if (statusChipTapCount >= 3) {
            statusChipTapCount = 0
            lastStatusChipTapAt = 0L
            viewModel.openDeveloperPanel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "无人机任务端 · ${state.activeBackend.displayLabel()}",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = ::handleStatusChipTap,
                        label = {
                            Text(
                                text = state.topStatusLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = when (state.topStatusKind) {
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
                    secondaryActionLabel = if (state.activeBackend == DroneBackend.DJI) "停止" else "继续",
                    onSecondaryAction = if (state.activeBackend == DroneBackend.DJI) {
                        viewModel::stopMission
                    } else {
                        viewModel::resumeMission
                    },
                    onRtl = viewModel::rtl,
                    onLand = viewModel::land,
                    onCameraPreviewEntered = viewModel::onCameraPreviewEntered,
                    onCameraPreviewExited = viewModel::onCameraPreviewExited,
                    onCameraSurfaceAvailable = viewModel::onCameraSurfaceAvailable,
                    onCameraSurfaceDestroyed = viewModel::onCameraSurfaceDestroyed,
                    onRefreshCameraSources = viewModel::refreshCameraSources,
                    onSwitchCameraSource = viewModel::switchCameraSource,
                )

                AppSection.EVENTS -> EventScreen(events = state.events)
            }
        }
    }
}

private fun DroneBackend.displayLabel(): String {
    return when (this) {
        DroneBackend.SELF_ROS -> "自研 ROS"
        DroneBackend.DJI -> "DJI MSDK"
    }
}
