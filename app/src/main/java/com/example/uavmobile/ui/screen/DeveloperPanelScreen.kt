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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uavmobile.debug.DeveloperLogStore
import com.example.uavmobile.ui.viewmodel.UavUiState

@Composable
fun DeveloperPanelScreen(
    state: UavUiState,
    onClose: () -> Unit,
    onRefreshSnapshot: () -> Unit,
    onCopyDiagnosticSummary: () -> Unit,
    onCopyRecentLogs: (Boolean) -> Unit,
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
            Text("开发者面板", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "用于查看 ROS、DJI、任务和运行日志。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRefreshSnapshot, modifier = Modifier.weight(1f)) {
                    Text("刷新快照")
                }
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(snapshot.formatSummary()))
                        onCopyDiagnosticSummary()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("复制诊断摘要")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val hasLogs = state.developerLogs.isNotEmpty()
                        clipboardManager.setText(
                            AnnotatedString(
                                if (hasLogs) recentLogs else DeveloperLogStore.NO_LOGS_AVAILABLE_MESSAGE,
                            ),
                        )
                        onCopyRecentLogs(hasLogs)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("复制日志")
                }
                OutlinedButton(onClick = onClearLogs, modifier = Modifier.weight(1f)) {
                    Text("清空日志")
                }
            }

            OutlinedButton(onClick = onClose) {
                Text("关闭面板")
            }

            DeveloperSectionCard(
                title = "面板状态",
                lines = listOf(state.developerPanelStatusMessage),
            )

            DeveloperSectionCard(
                title = "功能说明",
                lines = listOf(
                    "刷新快照：重新读取 App、ROS、DJI、飞机和任务状态。执行连接、上传、启动后先刷新一次。",
                    "复制诊断摘要：复制结构化诊断，适合发给开发者或 ChatGPT 分析。",
                    "复制日志：复制最近运行日志，用来查看初始化、注册、上传、启动和报错顺序。",
                    "清空日志：清掉当前内存日志。清空后会显示“日志已清空”。",
                    "关闭面板：返回主界面，不会重置连接或任务状态。",
                ),
            )

            DeveloperSectionCard(
                title = "应用信息",
                lines = listOf(
                    "应用 ID：${snapshot.applicationId}",
                    "版本：${snapshot.versionName}",
                    "当前后端：${snapshot.activeBackendLabel}",
                    "目标机型：${snapshot.selectedDjiAircraftFamilyLabel}",
                    "顶部状态：${snapshot.topStatusLabel.ifBlank { "无" }}",
                    "状态类型：${snapshot.topStatusKind.ifBlank { "无" }}",
                    "飞机已连接：${if (snapshot.vehicleConnected) "是" else "否"}",
                ),
            )

            DeveloperSectionCard(
                title = "ROS 状态",
                lines = listOf(
                    "WebSocket：${snapshot.rosWebsocketUrl}",
                    "连接状态：${snapshot.rosConnectionStatus}",
                    "会话活跃：${if (snapshot.rosSessionActive) "是" else "否"}",
                    "任务缓存数：${snapshot.rosMissionCacheCount}",
                    "最新告警：${snapshot.rosLatestAlert.ifBlank { "无" }}",
                ),
            )

            DeveloperSectionCard(
                title = "DJI 状态",
                lines = listOf(
                    "SDK 状态：${snapshot.djiSdkInitState}",
                    "SDK 文本：${snapshot.djiSdkStatusMessage}",
                    "产品已连接：${if (snapshot.djiProductConnected) "是" else "否"}",
                    "产品 ID：${snapshot.djiProductId ?: "无"}",
                    "产品类型：${snapshot.djiProductTypeLabel.ifBlank { "无" }}",
                    "连接文本：${snapshot.djiProductStatusMessage}",
                ),
            )

            DeveloperSectionCard(
                title = "DJI Connection Diagnostics",
                lines = listOf(
                    "djiProductConnected：${snapshot.djiConnectionDiagnostics.productConnected.toChineseBool()}",
                    "productType：${snapshot.djiConnectionDiagnostics.productType.ifBlank { "无" }}",
                    "keyConnectionValue：${snapshot.djiConnectionDiagnostics.keyConnectionValue?.toChineseBool() ?: "无"}",
                    "lastConnectionSource：${snapshot.djiConnectionDiagnostics.lastConnectionSource.ifBlank { "无" }}",
                    "lastRefreshReason：${snapshot.djiConnectionDiagnostics.lastRefreshReason.ifBlank { "无" }}",
                    "lastRefreshSucceeded：${snapshot.djiConnectionDiagnostics.lastRefreshSucceeded.toChineseBool()}",
                    "lastRefreshError：${snapshot.djiConnectionDiagnostics.lastRefreshError.ifBlank { "无" }}",
                    "monitorRunning：${snapshot.djiConnectionDiagnostics.monitorRunning.toChineseBool()}",
                    "monitorStartedAt：${snapshot.djiConnectionDiagnostics.monitorStartedAt.ifBlank { "无" }}",
                    "monitorTickCount：${snapshot.djiConnectionDiagnostics.monitorTickCount}",
                    "djiProductStatusMessage：${snapshot.djiConnectionDiagnostics.statusMessage.ifBlank { "无" }}",
                ),
            )

            DeveloperSectionCard(
                title = "DJI 连接判断说明",
                lines = listOf(
                    "SDK registered 不等于飞机已连接。",
                    "如果 callback 没来，但 KeyConnection=true，说明应以 KeyConnection 主动刷新结果为准。",
                    "如果官方 DJI App 能连接但本 App KeyConnection=false，优先检查官方 App 是否占用连接、当前 App 是否运行在正确设备、包名/App Key 是否正确、权限是否完整、MSDK 是否初始化完成。",
                ),
            )

            DeveloperSectionCard(
                title = "DJI 飞机诊断",
                lines = listOf(
                    "产品已连接：${if (snapshot.djiAircraftDiagnostics.productConnected) "是" else "否"}",
                    "产品类型：${snapshot.djiAircraftDiagnostics.productType.ifBlank { "无" }}",
                    "飞行模式：${snapshot.djiAircraftDiagnostics.flightMode.ifBlank { "无" }}",
                    "电机已启动：${snapshot.djiAircraftDiagnostics.motorsOn?.toChineseBool() ?: "无"}",
                    "正在飞行：${snapshot.djiAircraftDiagnostics.isFlying?.toChineseBool() ?: "无"}",
                    "地面状态：${snapshot.djiAircraftDiagnostics.isOnGround?.toChineseBool() ?: "无"}",
                    "Home 纬度：${snapshot.djiAircraftDiagnostics.homeLatitude?.let { "%.6f".format(it) } ?: "无"}",
                    "Home 经度：${snapshot.djiAircraftDiagnostics.homeLongitude?.let { "%.6f".format(it) } ?: "无"}",
                    "GPS 信号：${snapshot.djiAircraftDiagnostics.gpsSignalLevel.ifBlank { "无" }}",
                    "卫星数：${snapshot.djiAircraftDiagnostics.gpsSatelliteCount?.toString() ?: "无"}",
                    "RTK：${snapshot.djiAircraftDiagnostics.rtkStatus.ifBlank { "无" }}",
                ),
            )

            DeveloperSectionCard(
                title = "位置与 Home",
                lines = listOf(
                    "当前纬度：${snapshot.currentLatitude?.let { "%.6f".format(it) } ?: "无"}",
                    "当前经度：${snapshot.currentLongitude?.let { "%.6f".format(it) } ?: "无"}",
                    "当前高度：${snapshot.currentAltitudeMeters?.let { "%.2f".format(it) } ?: "无"}",
                    "当前航向：${snapshot.currentHeadingDegrees?.let { "%.1f".format(it) } ?: "无"}",
                    "Home 纬度：${snapshot.currentHomeLatitude?.let { "%.6f".format(it) } ?: "无"}",
                    "Home 经度：${snapshot.currentHomeLongitude?.let { "%.6f".format(it) } ?: "无"}",
                    "状态说明：${snapshot.currentStateMessage.ifBlank { "无" }}",
                ),
            )

            DeveloperSectionCard(
                title = "权限与任务",
                lines = listOf(
                    "DJI 权限已授予：${if (snapshot.djiPermissionsGranted) "是" else "否"}",
                    "缺失权限：${if (snapshot.djiMissingPermissions.isEmpty()) "无" else snapshot.djiMissingPermissions.joinToString()}",
                    "当前任务：${snapshot.selectedMissionId.ifBlank { "无" }}",
                    "显示任务数：${snapshot.displayedMissionCount}",
                    "任务状态：${snapshot.selectedMissionStatus.ifBlank { "无" }}",
                    "任务进度：${"%.0f".format(snapshot.selectedMissionProgress * 100f)}%",
                ),
            )

            DeveloperSectionCard(
                title = "DJI 航点诊断",
                lines = listOf(
                    "任务 ID：${snapshot.djiWaypointDiagnostics.preparedMissionId.ifBlank { "无" }}",
                    "任务文件名：${snapshot.djiWaypointDiagnostics.preparedMissionFileName.ifBlank { "无" }}",
                    "KMZ 路径：${snapshot.djiWaypointDiagnostics.kmzPath.ifBlank { "无" }}",
                    "KMZ 存在：${if (snapshot.djiWaypointDiagnostics.kmzFileExists) "是" else "否"}",
                    "KMZ 大小：${snapshot.djiWaypointDiagnostics.kmzFileSizeBytes}",
                    "所选机型：${snapshot.djiWaypointDiagnostics.selectedDjiAircraftFamily.ifBlank { "无" }}",
                    "解析机型：${snapshot.djiWaypointDiagnostics.resolvedWaylineDroneType.ifBlank { "无" }}",
                    "最后动作：${snapshot.djiWaypointDiagnostics.lastDjiWaypointAction.ifBlank { "无" }}",
                    "最后动作成功：${snapshot.djiWaypointDiagnostics.lastDjiWaypointActionSuccess?.toChineseBool() ?: "无"}",
                    "最后错误：${snapshot.djiWaypointDiagnostics.lastDjiWaypointError.ifBlank { "无" }}",
                    "错误提示：${snapshot.djiWaypointDiagnostics.lastDjiWaypointErrorHint.ifBlank { "无" }}",
                    "执行状态：${snapshot.djiWaypointDiagnostics.missionExecutionState.ifBlank { "无" }}",
                    "当前航点：${snapshot.djiWaypointDiagnostics.currentWaypointIndex}",
                    "任务进度：${"%.0f".format(snapshot.djiWaypointDiagnostics.missionProgress * 100.0)}%",
                ),
            )

            DeveloperSectionCard(
                title = "航点诊断怎么看",
                lines = listOf(
                    "任务 ID：确认当前准备的是哪条任务。",
                    "任务文件名：上传成功但启动失败时，先核对文件名是否一致。",
                    "KMZ 路径 / 存在 / 大小：用于判断文件是否真的生成成功。",
                    "所选机型 / 解析机型：用于判断写入 WPMZ 的机型是否正确。",
                    "最后动作 / 成功 / 错误 / 提示：定位失败发生在准备、上传还是启动。",
                    "执行状态 / 当前航点 / 任务进度：用于看回调是否真的推进。",
                ),
            )

            DeveloperSectionCard(
                title = "飞机诊断怎么看",
                lines = listOf(
                    "产品已连接：表示 MSDK 是否认为飞机已经连上。",
                    "产品类型：用于判断机型映射。",
                    "飞行模式：看当前飞控状态。",
                    "电机已启动：如果起飞或任务启动异常，优先看这里。",
                    "正在飞行 / 地面状态：判断飞机当前是否已经离地。",
                    "Home 点：为空时通常说明飞控状态还没准备好。",
                    "GPS / 卫星 / RTK：用于判断定位是否满足任务条件。",
                ),
            )

            DeveloperSectionCard(
                title = "日志格式说明",
                lines = listOf(
                    "格式：时间 [级别] 来源：消息 | 详情",
                    "时间：App 本地时间，不代表飞控时间。",
                    "级别：DEBUG / INFO / WARN / ERROR。",
                    "来源：产生日志的模块，如 UavViewModel、DjiMsdkManager、DjiConnectionManager。",
                    "消息：动作或状态摘要。",
                    "详情：附加参数，如 missionId、航点数、KMZ 路径、产品类型、错误、进度。",
                    "DEBUG：过程细节，一般不是错误。",
                    "INFO：正常关键事件。",
                    "WARN：可恢复异常或条件不满足。",
                    "ERROR：明确失败。",
                ),
            )

            DeveloperSectionCard(
                title = "常见日志来源",
                lines = listOf(
                    "UavViewModel：UI 调度、后端路由、权限门控。",
                    "DjiMsdkManager：DJI 初始化和 registerApp。",
                    "DjiConnectionManager：DJI 产品连接、断开、产品类型读取。",
                    "DjiWaypointMissionManager：航点生成、校验、上传、启动、暂停、停止和进度。",
                    "DjiAircraftStateReader：飞机位置、高度、航向、飞行状态、Home 点读取。",
                    "UavRepository：ROS rosbridge、/mobile/* 服务、遥测、事件、任务列表。",
                    "RosbridgeClient：WebSocket 和 ROS service_response。",
                ),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("最近日志", fontWeight = FontWeight.SemiBold)
                    if (state.developerLogs.isEmpty()) {
                        Text(state.developerLogsStateMessage)
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

private fun Boolean.toChineseBool(): String = if (this) "是" else "否"
