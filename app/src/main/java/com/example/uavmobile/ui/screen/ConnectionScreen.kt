package com.example.uavmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.data.model.ConnectionStatus
import com.example.uavmobile.ui.viewmodel.UavUiState

@Composable
fun ConnectionScreen(
    state: UavUiState,
    onActiveBackendChanged: (DroneBackend) -> Unit,
    onSelectedDjiAircraftFamilyChanged: (DjiAircraftFamily) -> Unit,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshMissions: () -> Unit,
    onRequestDjiPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Vehicle Backend",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Switch between the self-drone ROS link and the DJI MSDK link. The final app keeps both control paths in one UI.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BackendSelector(
            activeBackend = state.activeBackend,
            onBackendSelected = onActiveBackendChanged,
        )

        if (state.activeBackend == DroneBackend.SELF_ROS) {
            RosBackendCard(
                state = state,
                onHostChanged = onHostChanged,
                onPortChanged = onPortChanged,
            )
        } else {
            DjiBackendCard(
                state = state,
                onSelectedDjiAircraftFamilyChanged = onSelectedDjiAircraftFamilyChanged,
                onRequestDjiPermissions = onRequestDjiPermissions,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onConnect,
                enabled = state.connectionStatus != ConnectionStatus.CONNECTING && !state.busy,
            ) {
                Text(if (state.activeBackend == DroneBackend.DJI) "Init DJI" else "Connect")
            }

            OutlinedButton(onClick = onDisconnect, enabled = !state.busy) {
                Text("Disconnect")
            }

            OutlinedButton(onClick = onRefreshMissions, enabled = !state.busy) {
                Text(if (state.activeBackend == DroneBackend.DJI) "Refresh State" else "Sync Missions")
            }
        }

        StatusCard(state = state)
    }
}

@Composable
private fun BackendSelector(
    activeBackend: DroneBackend,
    onBackendSelected: (DroneBackend) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Active aircraft backend", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = activeBackend == DroneBackend.SELF_ROS,
                    onClick = { onBackendSelected(DroneBackend.SELF_ROS) },
                    label = { Text("Self ROS") },
                )
                FilterChip(
                    selected = activeBackend == DroneBackend.DJI,
                    onClick = { onBackendSelected(DroneBackend.DJI) },
                    label = { Text("DJI MSDK") },
                )
            }
        }
    }
}

@Composable
private fun RosBackendCard(
    state: UavUiState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ROS companion link", fontWeight = FontWeight.SemiBold)
            Text(
                "Connect the phone to rosbridge on the companion computer. This backend talks to /mobile/* ROS endpoints.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.connectionConfig.host,
                onValueChange = onHostChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Host / IP") },
                singleLine = true,
            )

            OutlinedTextField(
                value = state.connectionConfig.port,
                onValueChange = onPortChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun DjiBackendCard(
    state: UavUiState,
    onSelectedDjiAircraftFamilyChanged: (DjiAircraftFamily) -> Unit,
    onRequestDjiPermissions: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("DJI MSDK link", fontWeight = FontWeight.SemiBold)
            Text(
                "Initialize DJI MSDK from this final app, then route waypoint upload/control through the connected DJI aircraft.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Target aircraft family", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = state.selectedDjiAircraftFamily == DjiAircraftFamily.AUTO,
                    onClick = { onSelectedDjiAircraftFamilyChanged(DjiAircraftFamily.AUTO) },
                    label = { Text("Auto") },
                )
                FilterChip(
                    selected = state.selectedDjiAircraftFamily == DjiAircraftFamily.M400,
                    onClick = { onSelectedDjiAircraftFamilyChanged(DjiAircraftFamily.M400) },
                    label = { Text("M400") },
                )
                FilterChip(
                    selected = state.selectedDjiAircraftFamily == DjiAircraftFamily.MATRICE_4_SERIES,
                    onClick = { onSelectedDjiAircraftFamilyChanged(DjiAircraftFamily.MATRICE_4_SERIES) },
                    label = { Text("Matrice 4") },
                )
            }

            Text("Permissions", fontWeight = FontWeight.Medium)
            Text(state.djiPermissionStatusMessage)
            if (!state.djiPermissionsGranted) {
                Button(onClick = onRequestDjiPermissions) {
                    Text("Grant DJI Permissions")
                }
            }

            if (state.selectedDjiAircraftFamily == DjiAircraftFamily.MATRICE_4_SERIES) {
                Text(
                    "Matrice 4 Series is intentionally blocked until the correct WaylineDroneType is confirmed against DJI docs or real hardware.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: UavUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Backend status", fontWeight = FontWeight.SemiBold)
            if (state.activeBackend == DroneBackend.SELF_ROS) {
                Text("WebSocket URL: ${state.connectionConfig.websocketUrl}")
                Text("ROS connection: ${state.rosConnectionStatus.name}")
                Text("Session active: ${if (state.telemetry.sessionActive) "yes" else "no"}")
                Text("Latest alert: ${state.telemetry.latestAlert.ifBlank { "none" }}")
            } else {
                Text(state.djiSdkStatusMessage)
                Text(state.djiProductStatusMessage)
                Text("Permissions granted: ${if (state.djiPermissionsGranted) "yes" else "no"}")
                Text(
                    "MSDK is compiled into the final app. On emulators we only expect init/logging readiness, not real aircraft connectivity.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
