package com.example.uavmobile.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.hasValidCoordinates
import com.example.uavmobile.core.hasValidHomeCoordinates
import com.example.uavmobile.data.model.MissionWaypointDraft
import com.example.uavmobile.ui.theme.Alert
import com.example.uavmobile.ui.theme.SkyAccent
import com.example.uavmobile.ui.viewmodel.UavUiState
import kotlin.math.max
import kotlin.math.min
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete

@Composable
fun MissionScreen(
    state: UavUiState,
    onMissionIdChanged: (String) -> Unit,
    onAddWaypoint: () -> Unit,
    onImportCurrentPosition: () -> Unit,
    onRemoveWaypoint: (Int) -> Unit,
    onWaypointChanged: (Int, MissionWaypointDraft) -> Unit,
    onUploadMission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Mission Planner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = when (state.activeBackend) {
                DroneBackend.SELF_ROS -> {
                    "This MVP uses a lightweight mission canvas. Upload goes through the self-drone ROS backend."
                }

                DroneBackend.DJI -> {
                    "This MVP uses a lightweight mission canvas. Upload goes through DJI MSDK and supports M400 plus Matrice 4 Series wayline generation."
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MissionCanvasCard(state = state)

        OutlinedTextField(
            value = state.draftMissionId,
            onValueChange = onMissionIdChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mission ID") },
            singleLine = true,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAddWaypoint, modifier = Modifier.weight(1f)) {
                Text("Add Waypoint")
            }
            Button(onClick = onImportCurrentPosition, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("Import Current Position")
            }
        }

        Button(onClick = onUploadMission, enabled = !state.busy) {
            Text("Upload Mission")
        }

        state.draftWaypoints.forEachIndexed { index, waypoint ->
            WaypointEditorCard(
                index = index,
                waypoint = waypoint,
                onWaypointChanged = { onWaypointChanged(index, it) },
                onRemove = { onRemoveWaypoint(index) },
            )
        }
    }
}

@Composable
private fun MissionCanvasCard(state: UavUiState) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Mission Canvas", fontWeight = FontWeight.SemiBold)
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                ) {
                    val parsedPoints = state.draftWaypoints.mapNotNull { draft ->
                        val lat = draft.lat.toDoubleOrNull() ?: return@mapNotNull null
                        val lon = draft.lon.toDoubleOrNull() ?: return@mapNotNull null
                        Offset(lon.toFloat(), lat.toFloat())
                    }

                    drawRect(color = surfaceVariant)

                    val allPoints = buildList {
                        addAll(parsedPoints)
                        if (state.currentDroneState.hasValidHomeCoordinates()) {
                            add(Offset(state.currentDroneState.homeLongitude!!.toFloat(), state.currentDroneState.homeLatitude!!.toFloat()))
                        }
                        if (state.currentDroneState.hasValidCoordinates()) {
                            add(Offset(state.currentDroneState.longitude!!.toFloat(), state.currentDroneState.latitude!!.toFloat()))
                        }
                    }

                    if (allPoints.isEmpty()) {
                        return@Canvas
                    }

                    var minX = allPoints.first().x
                    var maxX = allPoints.first().x
                    var minY = allPoints.first().y
                    var maxY = allPoints.first().y
                    allPoints.forEach { point ->
                        minX = min(minX, point.x)
                        maxX = max(maxX, point.x)
                        minY = min(minY, point.y)
                        maxY = max(maxY, point.y)
                    }

                    fun project(point: Offset): Offset {
                        val xSpan = max(maxX - minX, 0.0001f)
                        val ySpan = max(maxY - minY, 0.0001f)
                        val padding = 28f
                        val normalizedX = (point.x - minX) / xSpan
                        val normalizedY = (point.y - minY) / ySpan
                        return Offset(
                            x = padding + normalizedX * (size.width - padding * 2f),
                            y = size.height - (padding + normalizedY * (size.height - padding * 2f)),
                        )
                    }

                    val missionPath = Path()
                    parsedPoints.map(::project).forEachIndexed { index, point ->
                        if (index == 0) {
                            missionPath.moveTo(point.x, point.y)
                        } else {
                            missionPath.lineTo(point.x, point.y)
                        }
                    }

                    if (parsedPoints.size > 1) {
                        drawPath(
                            path = missionPath,
                            color = SkyAccent,
                            style = Stroke(width = 6f, cap = StrokeCap.Round),
                        )
                    }

                    parsedPoints.map(::project).forEach { point ->
                        drawCircle(color = SkyAccent, radius = 11f, center = point)
                    }

                    if (state.currentDroneState.hasValidHomeCoordinates()) {
                        drawCircle(
                            color = Alert,
                            radius = 12f,
                            center = project(
                                Offset(
                                    state.currentDroneState.homeLongitude!!.toFloat(),
                                    state.currentDroneState.homeLatitude!!.toFloat(),
                                ),
                            ),
                        )
                    }

                    if (state.currentDroneState.hasValidCoordinates()) {
                        drawCircle(
                            color = Alert.copy(alpha = 0.6f),
                            radius = 10f,
                            center = project(
                                Offset(
                                    state.currentDroneState.longitude!!.toFloat(),
                                    state.currentDroneState.latitude!!.toFloat(),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaypointEditorCard(
    index: Int,
    waypoint: MissionWaypointDraft,
    onWaypointChanged: (MissionWaypointDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Waypoint ${index + 1}", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onRemove) {
                    androidx.compose.material3.Icon(Icons.Outlined.Delete, contentDescription = "Delete waypoint")
                }
            }

            NumericField(
                label = "Latitude",
                value = waypoint.lat,
                onValueChange = { onWaypointChanged(waypoint.copy(lat = it)) },
            )
            NumericField(
                label = "Longitude",
                value = waypoint.lon,
                onValueChange = { onWaypointChanged(waypoint.copy(lon = it)) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumericField(
                    label = "Altitude (m)",
                    value = waypoint.altM,
                    onValueChange = { onWaypointChanged(waypoint.copy(altM = it)) },
                    modifier = Modifier.weight(1f),
                )
                NumericField(
                    label = "Hold (s)",
                    value = waypoint.holdSec,
                    onValueChange = { onWaypointChanged(waypoint.copy(holdSec = it)) },
                    modifier = Modifier.weight(1f),
                )
            }

            NumericField(
                label = "Yaw (deg)",
                value = waypoint.yawDeg,
                onValueChange = { onWaypointChanged(waypoint.copy(yawDeg = it)) },
            )
        }
    }
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}
