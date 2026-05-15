package com.example.uavmobile.ui.viewmodel

import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneState
import com.example.uavmobile.data.model.MissionWaypointDraft
import java.util.Locale

object WaypointImportSupport {
    fun createWaypointFromCurrentPosition(
        backend: DroneBackend,
        currentDroneState: DroneState?,
        existingWaypoints: List<MissionWaypointDraft>,
    ): Result<MissionWaypointDraft> {
        val latitude = currentDroneState?.latitude
        val longitude = currentDroneState?.longitude

        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
            return Result.failure(
                IllegalStateException(
                    when (backend) {
                        DroneBackend.SELF_ROS -> {
                            "ROS telemetry is not ready yet, no valid aircraft coordinates are available."
                        }

                        DroneBackend.DJI -> {
                            currentDroneState?.statusMessage
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "DJI current position is unavailable: $it" }
                                ?: "DJI product is not providing a valid current position yet."
                        }
                    },
                ),
            )
        }

        val template = existingWaypoints.lastOrNull() ?: MissionWaypointDraft()
        return Result.success(
            template.copy(
                lat = String.format(Locale.US, "%.6f", latitude),
                lon = String.format(Locale.US, "%.6f", longitude),
            ),
        )
    }
}
