package com.example.uavmobile.ui.screen

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.uavmobile.core.CameraStreamSnapshot
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.ObstacleAvoidanceSnapshot
import com.example.uavmobile.core.ObstacleDirection
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
    onCameraPreviewEntered: () -> Unit,
    onCameraPreviewExited: () -> Unit,
    onCameraSurfaceAvailable: (Surface, Int, Int) -> Unit,
    onCameraSurfaceDestroyed: (Surface) -> Unit,
    onRefreshCameraSources: () -> Unit,
    onSwitchCameraSource: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("任务控制", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = if (state.activeBackend == DroneBackend.DJI) {
                "当前通过 DJI MSDK 控制任务，DJI 模式下“停止”替代“继续”。"
            } else {
                "当前通过 ROS /mobile/* 控制任务，解锁和起飞不在本客户端内。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onRefresh, enabled = !state.busy) {
            Text(if (state.activeBackend == DroneBackend.DJI) "刷新任务状态" else "刷新任务列表")
        }

        if (state.activeBackend == DroneBackend.DJI) {
            ObstacleAvoidanceStatusCard(snapshot = state.djiObstacleAvoidance)
            AircraftCameraPreviewCard(
                snapshot = state.cameraStream,
                onPreviewEntered = onCameraPreviewEntered,
                onPreviewExited = onCameraPreviewExited,
                onSurfaceAvailable = onCameraSurfaceAvailable,
                onSurfaceDestroyed = onCameraSurfaceDestroyed,
                onRefreshCameraSources = onRefreshCameraSources,
                onSwitchCameraSource = onSwitchCameraSource,
            )
        }

        if (state.missions.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (state.activeBackend == DroneBackend.DJI) {
                        "还没有准备 DJI 任务，请先到“任务”页创建并上传。"
                    } else {
                        "还没有已上传任务，请先到“任务”页创建并上传。"
                    },
                    modifier = Modifier.padding(12.dp),
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
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = mission.missionId + if (selected) " [已选]" else "",
                            fontWeight = FontWeight.SemiBold,
                        )
                        CompactInfoGrid(
                            items = listOf(
                                CompactInfoItem("航点数", mission.waypointCount.toString()),
                                CompactInfoItem("状态", mission.status),
                                CompactInfoItem("进度", "%.0f%%".format(mission.progress * 100f)),
                            ),
                            maxColumns = 3,
                            minItemWidth = 110.dp,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStart, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("开始")
            }
            Button(onClick = onPause, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("暂停")
            }
            Button(onClick = onSecondaryAction, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text(secondaryActionLabel)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRtl, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("返航")
            }
            OutlinedButton(onClick = onLand, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                Text("降落")
            }
        }
    }
}

@Composable
private fun AircraftCameraPreviewCard(
    snapshot: CameraStreamSnapshot,
    onPreviewEntered: () -> Unit,
    onPreviewExited: () -> Unit,
    onSurfaceAvailable: (Surface, Int, Int) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
    onRefreshCameraSources: () -> Unit,
    onSwitchCameraSource: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val switchableSources = snapshot.availableSources.filter { it.enabled && !it.isVisionAssist }

    DisposableEffect(Unit) {
        onPreviewEntered()
        onDispose { onPreviewExited() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("机身摄像头回传", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshCameraSources) {
                        Text("刷新视频源")
                    }
                    Box {
                        OutlinedButton(
                            onClick = { menuExpanded = true },
                            enabled = switchableSources.size > 1,
                        ) {
                            Text("切换视频源")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            switchableSources.forEach { source ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${source.indexName} · ${source.label}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onSwitchCameraSource(source.indexName)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            val cameraInfoItems = listOf(
                CompactInfoItem("视频源", snapshot.currentSourceName.ifBlank { "无" }),
                CompactInfoItem("cameraIndex", snapshot.currentCameraIndexName.ifBlank { "无" }),
                CompactInfoItem("飞机型号", snapshot.aircraftModel.ifBlank { "UNKNOWN" }),
                CompactInfoItem("视频状态", snapshot.status.name),
                CompactInfoItem("Surface", if (snapshot.isSurfaceReady) "READY" else "WAITING"),
                CompactInfoItem("显示", if (snapshot.isStreamDisplaying) "ON" else "OFF"),
                CompactInfoItem("可用源", snapshot.availableSources.size.toString()),
                CompactInfoItem("lens/source", snapshot.currentLensSourceName.ifBlank { "无" }),
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 560.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        CameraPreviewSurfaceBox(
                            snapshot = snapshot,
                            onSurfaceAvailable = onSurfaceAvailable,
                            onSurfaceDestroyed = onSurfaceDestroyed,
                            modifier = Modifier
                                .weight(0.9f)
                                .height(128.dp),
                        )
                        CompactInfoGrid(
                            items = cameraInfoItems,
                            modifier = Modifier.weight(1.1f),
                            minItemWidth = 112.dp,
                            valueMaxLines = 2,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CameraPreviewSurfaceBox(
                            snapshot = snapshot,
                            onSurfaceAvailable = onSurfaceAvailable,
                            onSurfaceDestroyed = onSurfaceDestroyed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp),
                        )
                        CompactInfoGrid(
                            items = cameraInfoItems,
                            minItemWidth = 120.dp,
                            valueMaxLines = 2,
                        )
                    }
                }
            }

            val detail = snapshot.errorMessage.ifBlank { snapshot.warningMessage }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = if (snapshot.errorMessage.isNotBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewSurfaceBox(
    snapshot: CameraStreamSnapshot,
    onSurfaceAvailable: (Surface, Int, Int) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var previewSurface: Surface? = null

                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            val nextSurface = Surface(surface)
                            previewSurface = nextSurface
                            if (width > 0 && height > 0) {
                                onSurfaceAvailable(nextSurface, width, height)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            val activeSurface = previewSurface ?: Surface(surface).also {
                                previewSurface = it
                            }
                            if (width > 0 && height > 0) {
                                onSurfaceAvailable(activeSurface, width, height)
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            previewSurface?.let {
                                onSurfaceDestroyed(it)
                                it.release()
                            }
                            previewSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                }
            },
        )
        if (!snapshot.isStreamDisplaying) {
            Text(
                text = snapshot.statusMessage.ifBlank { "相机流未就绪" },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun ObstacleAvoidanceStatusCard(snapshot: ObstacleAvoidanceSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("避障安全", fontWeight = FontWeight.SemiBold)
            CompactInfoGrid(
                items = listOf(
                    CompactInfoItem("避障模式", snapshot.mode.name),
                    CompactInfoItem("水平避障", snapshot.horizontalSwitch.name),
                    CompactInfoItem("上方避障", snapshot.upwardSwitch.name),
                    CompactInfoItem("下方避障", snapshot.downwardSwitch.name),
                    CompactInfoItem("最近障碍物", snapshot.nearestObstacleLabel()),
                    CompactInfoItem("安全状态", snapshot.safetyState.name),
                    CompactInfoItem("避障监听", if (snapshot.monitoringActive) "已开启" else "未开启"),
                    CompactInfoItem("状态说明", snapshot.lastMessage.ifBlank { "无" }),
                ),
                minItemWidth = 120.dp,
                verticalSpacing = 4.dp,
                valueMaxLines = 1,
            )
        }
    }
}

private fun ObstacleAvoidanceSnapshot.nearestObstacleLabel(): String {
    val distance = nearestObstacleDistanceMeters ?: return "无"
    val direction = if (nearestObstacleDirection == ObstacleDirection.UNKNOWN) {
        "UNKNOWN"
    } else {
        nearestObstacleDirection.name
    }
    return "$direction ${"%.1f".format(distance)} m"
}
