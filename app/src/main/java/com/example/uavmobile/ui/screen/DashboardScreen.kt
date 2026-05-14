package com.example.uavmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uavmobile.ui.viewmodel.UavUiState

@Composable
fun DashboardScreen(state: UavUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Flight Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "Mode", value = state.telemetry.flightMode, modifier = Modifier.weight(1f))
            MetricCard(title = "Mission", value = state.telemetry.missionStage, modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "Battery", value = state.telemetry.batteryPercentLabel, modifier = Modifier.weight(1f))
            MetricCard(title = "Voltage", value = "%.2f V".format(state.telemetry.batteryVoltage), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "GPS", value = "Fix ${state.telemetry.gpsFixType}", modifier = Modifier.weight(1f))
            MetricCard(title = "Satellites", value = state.telemetry.satellitesVisible.toString(), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "Altitude", value = state.telemetry.altitudeLabel, modifier = Modifier.weight(1f))
            MetricCard(title = "Ground Speed", value = state.telemetry.speedLabel, modifier = Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Position", fontWeight = FontWeight.SemiBold)
                Text("Aircraft: ${state.telemetry.positionLabel}")
                Text(
                    "Home: ${
                        if (state.telemetry.homeAvailable) {
                            "%.5f, %.5f".format(state.telemetry.homeLatitude, state.telemetry.homeLongitude)
                        } else {
                            "not available"
                        }
                    }",
                )
                Text("Heading: %.1f deg".format(state.telemetry.headingDeg))
                Text("Mission progress: %.0f%%".format(state.telemetry.missionProgress * 100f))
                Text("Mobile session: ${if (state.telemetry.sessionActive) "healthy" else "stale"}")
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
