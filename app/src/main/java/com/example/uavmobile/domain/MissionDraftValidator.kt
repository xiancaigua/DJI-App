package com.example.uavmobile.domain

import com.example.uavmobile.data.model.ConnectionConfig
import com.example.uavmobile.data.model.MissionWaypoint
import com.example.uavmobile.data.model.MissionWaypointDraft

object MissionDraftValidator {
    fun validateConnection(config: ConnectionConfig): String? {
        if (config.host.isBlank()) {
            return "Host/IP is required"
        }
        if (config.port.toIntOrNull() == null) {
            return "Port must be a valid integer"
        }
        return null
    }

    fun parseMissionWaypoints(drafts: List<MissionWaypointDraft>): Result<List<MissionWaypoint>> {
        if (drafts.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one waypoint is required"))
        }

        val parsed = drafts.mapIndexed { index, draft ->
            val lat = draft.lat.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: invalid latitude"))
            val lon = draft.lon.toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: invalid longitude"))
            val altM = draft.altM.toFloatOrNull()
                ?: return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: invalid altitude"))
            val holdSec = draft.holdSec.toFloatOrNull()
                ?: return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: invalid hold time"))
            val yawDeg = draft.yawDeg.toFloatOrNull()
                ?: return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: invalid yaw"))

            if (lat !in -90.0..90.0) {
                return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: latitude out of range"))
            }
            if (lon !in -180.0..180.0) {
                return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: longitude out of range"))
            }
            if (altM <= 0f) {
                return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: altitude must be > 0"))
            }
            if (holdSec < 0f) {
                return Result.failure(IllegalArgumentException("Waypoint ${index + 1}: hold time must be >= 0"))
            }

            MissionWaypoint(
                lat = lat,
                lon = lon,
                altM = altM,
                holdSec = holdSec,
                yawDeg = yawDeg,
            )
        }

        return Result.success(parsed)
    }
}
