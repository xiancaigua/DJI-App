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
    FAILED,
}

data class MissionExecutionSnapshot(
    val state: MissionExecutionState = MissionExecutionState.IDLE,
    val missionId: String = "",
    val missionFileName: String = "",
    val waypointCount: Int = 0,
    val currentWaypointIndex: Int = -1,
    val progress: Double = 0.0,
    val message: String = "No DJI mission prepared",
)

fun MissionExecutionSnapshot.toMissionSummaryOrNull(): MissionSummary? {
    if (missionId.isBlank()) {
        return null
    }

    return MissionSummary(
        missionId = missionId,
        waypointCount = waypointCount,
        status = state.name,
        progress = progress.toFloat().coerceIn(0f, 1f),
    )
}
