package com.example.uavmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.example.uavmobile.BuildConfig
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
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "连接设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "在这里切换自研 ROS 和 DJI MSDK。",
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConnect,
                enabled = state.connectionStatus != ConnectionStatus.CONNECTING && !state.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.activeBackend == DroneBackend.DJI) "初始化 DJI" else "连接")
            }

            OutlinedButton(onClick = onDisconnect, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("断开")
            }

            OutlinedButton(onClick = onRefreshMissions, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text(if (state.activeBackend == DroneBackend.DJI) "刷新状态" else "同步任务")
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("飞行后端", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = activeBackend == DroneBackend.SELF_ROS,
                    onClick = { onBackendSelected(DroneBackend.SELF_ROS) },
                    label = { Text("自研 ROS") },
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ROS 连接", fontWeight = FontWeight.SemiBold)
            Text(
                "连接上位机 rosbridge，调用 /mobile/* 接口。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 520.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.connectionConfig.host,
                            onValueChange = onHostChanged,
                            modifier = Modifier.weight(2f),
                            label = { Text("主机 / IP") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.connectionConfig.port,
                            onValueChange = onPortChanged,
                            modifier = Modifier.weight(1f),
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.connectionConfig.host,
                            onValueChange = onHostChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("主机 / IP") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.connectionConfig.port,
                            onValueChange = onPortChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
            }
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("DJI 连接", fontWeight = FontWeight.SemiBold)
            Text(
                "在当前 App 内初始化 DJI，并通过已连接飞机执行控制和航点任务。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("目标机型", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = state.selectedDjiAircraftFamily == DjiAircraftFamily.AUTO,
                    onClick = { onSelectedDjiAircraftFamilyChanged(DjiAircraftFamily.AUTO) },
                    label = { Text("自动") },
                )
                FilterChip(
                    selected = state.selectedDjiAircraftFamily == DjiAircraftFamily.M400,
                    onClick = { onSelectedDjiAircraftFamilyChanged(DjiAircraftFamily.M400) },
                    label = { Text("M400") },
                )
                FilterChip(
                    selected = state.selectedDjiAircraftFamily == DjiAircraftFamily.MATRICE_4_SERIES,
                    onClick = { onSelectedDjiAircraftFamilyChanged(DjiAircraftFamily.MATRICE_4_SERIES) },
                    label = { Text("御 4 / M4") },
                )
            }

            Text("权限", fontWeight = FontWeight.Medium)
            CompactInfoGrid(
                items = listOf(
                    CompactInfoItem("权限状态", state.djiPermissionStatusMessage),
                    CompactInfoItem("应用 ID", BuildConfig.APPLICATION_ID),
                ),
                maxColumns = 2,
                minItemWidth = 180.dp,
                valueMaxLines = 2,
            )
            Text(
                "DJI 平台包名必须与上面的应用 ID 完全一致。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.djiPermissionsGranted) {
                Button(onClick = onRequestDjiPermissions) {
                    Text("授予权限")
                }
            }

            if (state.selectedDjiAircraftFamily == DjiAircraftFamily.MATRICE_4_SERIES) {
                Text(
                    "M4 系列手动任务使用 WA345；如果连接的是 M4D，会自动识别为 EA230。",
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("状态", fontWeight = FontWeight.SemiBold)
            if (state.activeBackend == DroneBackend.SELF_ROS) {
                CompactInfoGrid(
                    items = listOf(
                        CompactInfoItem("顶部状态", state.topStatusLabel),
                        CompactInfoItem("飞机连接", if (state.vehicleConnected) "是" else "否"),
                        CompactInfoItem("Backend link", state.rosConnectionStatus.name),
                        CompactInfoItem("Aircraft status", if (state.telemetry.connected) "飞机已连接" else "飞机离线"),
                        CompactInfoItem("WebSocket", state.connectionConfig.websocketUrl),
                        CompactInfoItem("遥测连机", if (state.telemetry.connected) "是" else "否"),
                        CompactInfoItem("会话活跃", if (state.telemetry.sessionActive) "是" else "否"),
                        CompactInfoItem("最新告警", state.telemetry.latestAlert.ifBlank { "无" }),
                    ),
                    minItemWidth = 150.dp,
                    valueMaxLines = 2,
                )
            } else {
                CompactInfoGrid(
                    items = listOf(
                        CompactInfoItem("顶部状态", state.topStatusLabel),
                        CompactInfoItem("飞机连接", if (state.vehicleConnected) "是" else "否"),
                        CompactInfoItem("Backend link", "DJI MSDK"),
                        CompactInfoItem("Aircraft status", if (state.djiProductConnected) "DJI 飞机已连接" else "飞机离线"),
                        CompactInfoItem("SDK 注册", state.djiSdkInitState.name),
                        CompactInfoItem("SDK 文本", state.djiSdkStatusMessage),
                        CompactInfoItem("Product status", state.djiProductStatusMessage),
                        CompactInfoItem("KeyConnection", state.djiKeyConnectionValue?.let { if (it) "true" else "false" } ?: "无"),
                        CompactInfoItem("ProductType", state.djiProductTypeLabel.ifBlank { "无" }),
                        CompactInfoItem("连接来源", state.djiLastConnectionSource.ifBlank { "无" }),
                        CompactInfoItem("最后刷新", state.djiLastRefreshReason.ifBlank { "无" }),
                        CompactInfoItem("最后错误", state.djiLastRefreshError.ifBlank { "无" }),
                        CompactInfoItem("Monitor", "${if (state.djiConnectionMonitorRunning) "运行中" else "未运行"} / tick=${state.djiConnectionMonitorTickCount}"),
                        CompactInfoItem("权限", if (state.djiPermissionsGranted) "已授予" else "未授予"),
                    ),
                    minItemWidth = 150.dp,
                    valueMaxLines = 2,
                )
                Text(
                    "模拟器只验证初始化和日志，不代表真实连机。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
