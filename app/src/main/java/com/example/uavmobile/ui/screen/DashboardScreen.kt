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
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.hasValidCoordinates
import com.example.uavmobile.core.hasValidHomeCoordinates
import com.example.uavmobile.ui.viewmodel.UavUiState

@Composable
fun DashboardScreen(state: UavUiState) {
    val currentAircraft = state.currentDroneState
    val altitudeValue = currentAircraft.altitudeMeters?.let { "%.1f m".format(it) } ?: state.telemetry.altitudeLabel
    val headingValue = currentAircraft.headingDegrees?.let { "%.1f°".format(it) } ?: "%.1f°".format(state.telemetry.headingDeg)
    val missionStatusValue = if (state.activeBackend == DroneBackend.DJI) {
        state.djiMissionSummary?.status ?: currentAircraft.missionStatus.ifBlank { "空闲" }
    } else {
        state.telemetry.missionStage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("飞行总览", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "模式", value = state.telemetry.flightMode, modifier = Modifier.weight(1f))
            MetricCard(title = "任务", value = missionStatusValue, modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "电量", value = state.telemetry.batteryPercentLabel, modifier = Modifier.weight(1f))
            MetricCard(title = "电压", value = "%.2f V".format(state.telemetry.batteryVoltage), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "GPS", value = "Fix ${state.telemetry.gpsFixType}", modifier = Modifier.weight(1f))
            MetricCard(title = "卫星", value = state.telemetry.satellitesVisible.toString(), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "高度", value = altitudeValue, modifier = Modifier.weight(1f))
            MetricCard(title = "速度", value = state.telemetry.speedLabel, modifier = Modifier.weight(1f))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("位置", fontWeight = FontWeight.SemiBold)
                Text(
                    "飞机：${
                        if (currentAircraft.hasValidCoordinates()) {
                            "%.5f, %.5f".format(currentAircraft.latitude, currentAircraft.longitude)
                        } else {
                            state.telemetry.positionLabel
                        }
                    }",
                )
                Text(
                    "Home 点：${
                        if (currentAircraft.hasValidHomeCoordinates()) {
                            "%.5f, %.5f".format(currentAircraft.homeLatitude, currentAircraft.homeLongitude)
                        } else if (state.telemetry.homeAvailable) {
                            "%.5f, %.5f".format(state.telemetry.homeLatitude, state.telemetry.homeLongitude)
                        } else {
                            "不可用"
                        }
                    }",
                )
                Text("航向：$headingValue")
                Text("任务进度：%.0f%%".format(state.telemetry.missionProgress * 100f))
                Text("移动会话：${if (state.telemetry.sessionActive) "正常" else "过期"}")
                Text("后端状态：${currentAircraft.statusMessage.ifBlank { "无附加状态" }}")
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
