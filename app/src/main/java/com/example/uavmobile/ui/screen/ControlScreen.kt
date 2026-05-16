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

        if (state.missions.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (state.activeBackend == DroneBackend.DJI) {
                        "还没有准备 DJI 任务，请先到“任务”页创建并上传。"
                    } else {
                        "还没有已上传任务，请先到“任务”页创建并上传。"
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
                            text = mission.missionId + if (selected) " [已选]" else "",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("航点数：${mission.waypointCount}")
                        Text("状态：${mission.status}")
                        Text("进度：%.0f%%".format(mission.progress * 100f))
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
