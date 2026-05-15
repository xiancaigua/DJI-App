package com.example.uavmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import com.example.uavmobile.debug.DeveloperLogStore
import com.example.uavmobile.ui.viewmodel.UavUiState

@Composable
fun DeveloperPanelScreen(
    state: UavUiState,
    onClose: () -> Unit,
    onRefreshSnapshot: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val snapshot = state.developerSnapshot
    val recentLogs = DeveloperLogStore.formatRecentLogs()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Developer Panel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Hidden diagnostics for ROS and DJI integration. Use this for runtime inspection and field debugging.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRefreshSnapshot, modifier = Modifier.weight(1f)) {
                    Text("Refresh Snapshot")
                }
                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(snapshot.formatSummary())) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copy Diagnostic Summary")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(recentLogs)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copy Recent Logs")
                }
                OutlinedButton(onClick = onClearLogs, modifier = Modifier.weight(1f)) {
                    Text("Clear Logs")
                }
            }

            OutlinedButton(onClick = onClose) {
                Text("Close Developer Panel")
            }

            DeveloperSectionCard(
                title = "App Identity",
                lines = listOf(
                    "applicationId: ${snapshot.applicationId}",
                    "versionName: ${snapshot.versionName}",
                    "activeBackend: ${snapshot.activeBackendLabel}",
                    "selectedDjiAircraftFamily: ${snapshot.selectedDjiAircraftFamilyLabel}",
                ),
            )

            DeveloperSectionCard(
                title = "ROS Status",
                lines = listOf(
                    "websocketUrl: ${snapshot.rosWebsocketUrl}",
                    "connectionStatus: ${snapshot.rosConnectionStatus}",
                    "sessionActive: ${snapshot.rosSessionActive}",
                    "missionCacheCount: ${snapshot.rosMissionCacheCount}",
                    "latestAlert: ${snapshot.rosLatestAlert.ifBlank { "none" }}",
                ),
            )

            DeveloperSectionCard(
                title = "DJI Status",
                lines = listOf(
                    "sdkInitState: ${snapshot.djiSdkInitState}",
                    "sdkStatus: ${snapshot.djiSdkStatusMessage}",
                    "productConnected: ${snapshot.djiProductConnected}",
                    "productId: ${snapshot.djiProductId ?: "n/a"}",
                    "productType: ${snapshot.djiProductTypeLabel.ifBlank { "n/a" }}",
                    "productStatus: ${snapshot.djiProductStatusMessage}",
                ),
            )

            DeveloperSectionCard(
                title = "Position and Home",
                lines = listOf(
                    "latitude: ${snapshot.currentLatitude?.let { "%.6f".format(it) } ?: "n/a"}",
                    "longitude: ${snapshot.currentLongitude?.let { "%.6f".format(it) } ?: "n/a"}",
                    "altitudeMeters: ${snapshot.currentAltitudeMeters?.let { "%.2f".format(it) } ?: "n/a"}",
                    "headingDegrees: ${snapshot.currentHeadingDegrees?.let { "%.1f".format(it) } ?: "n/a"}",
                    "homeLatitude: ${snapshot.currentHomeLatitude?.let { "%.6f".format(it) } ?: "n/a"}",
                    "homeLongitude: ${snapshot.currentHomeLongitude?.let { "%.6f".format(it) } ?: "n/a"}",
                    "stateMessage: ${snapshot.currentStateMessage.ifBlank { "none" }}",
                ),
            )

            DeveloperSectionCard(
                title = "Permissions and Mission",
                lines = listOf(
                    "djiPermissionsGranted: ${snapshot.djiPermissionsGranted}",
                    "djiMissingPermissions: ${if (snapshot.djiMissingPermissions.isEmpty()) "none" else snapshot.djiMissingPermissions.joinToString()}",
                    "selectedMissionId: ${snapshot.selectedMissionId.ifBlank { "none" }}",
                    "displayedMissionCount: ${snapshot.displayedMissionCount}",
                    "selectedMissionStatus: ${snapshot.selectedMissionStatus.ifBlank { "none" }}",
                    "selectedMissionProgress: ${"%.0f".format(snapshot.selectedMissionProgress * 100f)}%",
                ),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Recent Logs", fontWeight = FontWeight.SemiBold)
                    if (state.developerLogs.isEmpty()) {
                        Text("No developer logs recorded yet.")
                    } else {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.developerLogs.takeLast(80).forEach { entry ->
                                    Text(
                                        "${entry.timestamp} [${entry.level.name}] ${entry.source}: ${entry.message}" +
                                            entry.details?.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperSectionCard(
    title: String,
    lines: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            lines.forEach { line ->
                Text(line)
            }
        }
    }
}
