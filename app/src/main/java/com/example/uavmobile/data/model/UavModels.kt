package com.example.uavmobile.data.model

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

data class ConnectionConfig(
    val host: String = "192.168.1.10",
    val port: String = "9090",
) {
    val websocketUrl: String
        get() = "ws://$host:$port"
}

data class TelemetrySnapshot(
    val flightMode: String = "UNKNOWN",
    val armed: Boolean = false,
    val connected: Boolean = false,
    val gpsFixType: Int = 0,
    val satellitesVisible: Int = 0,
    val batteryPercent: Float = 0f,
    val batteryVoltage: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val homeLatitude: Double = 0.0,
    val homeLongitude: Double = 0.0,
    val homeAvailable: Boolean = false,
    val relativeAltitudeM: Double = 0.0,
    val headingDeg: Float = 0f,
    val groundSpeedMps: Float = 0f,
    val missionStage: String = "IDLE",
    val missionProgress: Float = 0f,
    val sessionActive: Boolean = false,
    val latestAlert: String = "",
) {
    val positionLabel: String
        get() = "%.5f, %.5f".format(latitude, longitude)

    val batteryPercentLabel: String
        get() = "%.0f%%".format(batteryPercent * 100f)

    val altitudeLabel: String
        get() = "%.1f m".format(relativeAltitudeM)

    val speedLabel: String
        get() = "%.1f m/s".format(groundSpeedMps)
}

data class MobileEvent(
    val level: String,
    val code: String,
    val errorCode: Int,
    val message: String,
    val relatedMissionId: String,
    val receivedAt: String,
)

data class MissionWaypointDraft(
    val lat: String = "31.2304",
    val lon: String = "121.4737",
    val altM: String = "30",
    val holdSec: String = "0",
    val yawDeg: String = "0",
)

data class MissionWaypoint(
    val lat: Double,
    val lon: Double,
    val altM: Float,
    val holdSec: Float,
    val yawDeg: Float,
)

data class MissionSummary(
    val missionId: String,
    val waypointCount: Int,
    val status: String,
    val progress: Float,
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val errorCode: Int = 0,
)
