package com.example.uavmobile.core

import com.example.uavmobile.data.model.MissionSummary
import com.example.uavmobile.data.model.MissionWaypoint

typealias Waypoint = MissionWaypoint

enum class DjiAircraftFamily {
    AUTO,
    M400,
    MATRICE_4_SERIES,
}

data class PreparedMission(
    val missionId: String,
    val missionFileName: String,
    val kmzPath: String,
    val waypointCount: Int,
)

enum class MissionExecutionState {
    IDLE,
    PREPARING,
    READY_TO_UPLOAD,
    UPLOADING,
    UPLOADED,
    STARTING,
    RUNNING,
    PAUSED,
    STOPPING,
    STOPPED,
    FINISHED,
    FAILED,
}

data class MissionExecutionSnapshot(
    val state: MissionExecutionState = MissionExecutionState.IDLE,
    val missionId: String = "",
    val missionFileName: String = "",
    val waypointCount: Int = 0,
    val currentWaypointIndex: Int = -1,
    val progress: Double = 0.0,
    val uploadProgress: Double = 0.0,
    val sdkMissionExecuteState: String = "",
    val message: String = "当前还没有准备 DJI 任务",
    val selectedDjiAircraftFamily: String = "",
    val resolvedWaylineDroneType: String = "",
    val kmzPath: String = "",
    val kmzFileExists: Boolean = false,
    val kmzFileSizeBytes: Long = 0L,
    val lastDjiWaypointAction: String = "",
    val lastDjiWaypointActionSuccess: Boolean? = null,
    val lastDjiWaypointError: String = "",
    val lastDjiWaypointErrorHint: String = "",
    val lastInterruptionReason: String = "",
    val lastInterruptionDiagnostics: String = "",
)

fun MissionExecutionSnapshot.toMissionSummaryOrNull(): MissionSummary? {
    if (missionId.isBlank()) {
        return null
    }

    return MissionSummary(
        missionId = missionId,
        waypointCount = waypointCount,
        status = state.displayLabel(),
        progress = when (state) {
            MissionExecutionState.RUNNING,
            MissionExecutionState.PAUSED,
            MissionExecutionState.STOPPING,
            MissionExecutionState.STOPPED,
            MissionExecutionState.FINISHED,
            -> progress.toFloat().coerceIn(0f, 1f)

            else -> 0f
        },
    )
}

fun MissionExecutionState.displayLabel(): String {
    return when (this) {
        MissionExecutionState.IDLE -> "空闲"
        MissionExecutionState.PREPARING -> "准备中"
        MissionExecutionState.READY_TO_UPLOAD -> "待上传"
        MissionExecutionState.UPLOADING -> "上传中"
        MissionExecutionState.UPLOADED -> "已上传"
        MissionExecutionState.STARTING -> "启动中"
        MissionExecutionState.RUNNING -> "执行中"
        MissionExecutionState.PAUSED -> "已暂停"
        MissionExecutionState.STOPPING -> "停止中"
        MissionExecutionState.STOPPED -> "已停止"
        MissionExecutionState.FINISHED -> "已完成"
        MissionExecutionState.FAILED -> "失败"
    }
}
