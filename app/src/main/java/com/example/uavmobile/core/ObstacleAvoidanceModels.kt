package com.example.uavmobile.core

enum class ObstacleAvoidanceMode {
    BRAKE,
    BYPASS,
    CLOSE,
    UNKNOWN,
}

enum class ObstacleAvoidanceSwitchState {
    ON,
    OFF,
    UNKNOWN,
    UNSUPPORTED,
}

enum class ObstacleSafetyState {
    SAFE,
    WARNING,
    EMERGENCY,
    UNSUPPORTED,
    UNKNOWN,
}

enum class ObstacleDirection {
    HORIZONTAL,
    UPWARD,
    DOWNWARD,
    UNKNOWN,
}

data class ObstacleAvoidanceSnapshot(
    val mode: ObstacleAvoidanceMode = ObstacleAvoidanceMode.UNKNOWN,
    val horizontalSwitch: ObstacleAvoidanceSwitchState = ObstacleAvoidanceSwitchState.UNKNOWN,
    val upwardSwitch: ObstacleAvoidanceSwitchState = ObstacleAvoidanceSwitchState.UNKNOWN,
    val downwardSwitch: ObstacleAvoidanceSwitchState = ObstacleAvoidanceSwitchState.UNKNOWN,
    val horizontalWarningDistanceMeters: Double? = null,
    val upwardWarningDistanceMeters: Double? = null,
    val downwardWarningDistanceMeters: Double? = null,
    val horizontalBrakingDistanceMeters: Double? = null,
    val upwardBrakingDistanceMeters: Double? = null,
    val downwardBrakingDistanceMeters: Double? = null,
    val nearestObstacleDistanceMeters: Double? = null,
    val nearestObstacleDirection: ObstacleDirection = ObstacleDirection.UNKNOWN,
    val safetyState: ObstacleSafetyState = ObstacleSafetyState.UNKNOWN,
    val monitoringActive: Boolean = false,
    val lastPrepareSucceeded: Boolean? = null,
    val lastPrepareWarning: String = "",
    val lastPrepareError: String = "",
    val appPauseRequested: Boolean = false,
    val lastMessage: String = "避障状态尚未检查",
    val updatedAt: String = "",
)
