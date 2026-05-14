package com.example.uavmobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.ui.viewmodel.UavUiState

@Composable
fun ControlScreen(
    state: UavUiState,
    onMissionSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
    onRtl: () -> Unit,
    onLand: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Task Control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = if (state.activeBackend == DroneBackend.DJI) {
                "Mission control is routed through DJI MSDK. Stop replaces Resume on the current DJI path."
            } else {
                "Mission-level operations are routed through ROS /mobile/* services. Arm and takeoff stay outside this client."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onRefresh, enabled = !state.busy) {
            Text(if (state.activeBackend == DroneBackend.DJI) "Refresh Mission State" else "Refresh Mission List")
        }

        if (state.missions.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (state.activeBackend == DroneBackend.DJI) {
                        "No DJI mission prepared yet. Use the Mission tab to prepare and upload one."
                    } else {
                        "No uploaded missions yet. Use the Mission tab to create and upload one."
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            state.missions.forEach { mission ->
                val selected = mission.missionId == state.selectedMissionId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMissionSelected(mission.missionId) },
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = mission.missionId + if (selected) "  [selected]" else "",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Waypoints: ${mission.waypointCount}")
                        Text("Status: ${mission.status}")
                        Text("Progress: %.0f%%".format(mission.progress * 100f))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStart, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Start")
            }
            Button(onClick = onPause, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Pause")
            }
            Button(onClick = onSecondaryAction, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text(secondaryActionLabel)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRtl, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("RTL")
            }
            OutlinedButton(onClick = onLand, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Land")
            }
        }
    }
}
