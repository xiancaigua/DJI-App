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
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val altitudeMeters: Double? = null,
    val headingDegrees: Float? = null,
    val flightMode: String = "",
    val motorsOn: Boolean? = null,
    val isFlying: Boolean? = null,
    val isOnGround: Boolean? = null,
    val gpsSignalLevel: String = "",
    val gpsSatelliteCount: Int? = null,
    val rtkStatus: String = "",
    val missionStatus: String = "",
    val statusMessage: String = "",
)

fun DroneState.hasValidCoordinates(): Boolean {
    val lat = latitude
    val lon = longitude
    return lat != null && lon != null && !(lat == 0.0 && lon == 0.0)
}

fun DroneState.hasValidHomeCoordinates(): Boolean {
    val lat = homeLatitude
    val lon = homeLongitude
    return lat != null && lon != null && !(lat == 0.0 && lon == 0.0)
}

fun TelemetrySnapshot.toDroneState(): DroneState {
    return DroneState(
        backend = DroneBackend.SELF_ROS,
        connectionState = if (connected) DroneConnectionState.CONNECTED else DroneConnectionState.DISCONNECTED,
        isArmed = armed,
        latitude = latitude,
        longitude = longitude,
        homeLatitude = homeLatitude.takeIf { homeAvailable },
        homeLongitude = homeLongitude.takeIf { homeAvailable },
        altitudeMeters = relativeAltitudeM,
        headingDegrees = headingDeg,
        isOnGround = connected && !armed,
        missionStatus = missionStage,
        statusMessage = latestAlert,
    )
}
