package com.example.uavmobile.dji

import dji.sdk.wpmz.value.mission.WaylineDroneType

fun DjiWaylineAircraftType.toWaylineDroneType(): WaylineDroneType {
    return when (this) {
        DjiWaylineAircraftType.PM440 -> WaylineDroneType.PM440
    }
}
