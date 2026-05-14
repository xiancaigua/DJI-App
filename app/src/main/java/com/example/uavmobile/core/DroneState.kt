package com.example.uavmobile.core

import com.example.uavmobile.data.model.TelemetrySnapshot

enum class DroneBackend {
    SELF_ROS,
    DJI,
}

enum class DroneConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

data class DroneState(
    val backend: DroneBackend,
    val connectionState: DroneConnectionState = DroneConnectionState.DISCONNECTED,
    val isArmed: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val headingDegrees: Float? = null,
    val missionStatus: String = "",
    val statusMessage: String = "",
)

fun TelemetrySnapshot.toDroneState(): DroneState {
    return DroneState(
        backend = DroneBackend.SELF_ROS,
        connectionState = if (connected) DroneConnectionState.CONNECTED else DroneConnectionState.DISCONNECTED,
        isArmed = armed,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = relativeAltitudeM,
        headingDegrees = headingDeg,
        missionStatus = missionStage,
        statusMessage = latestAlert,
    )
}
