package com.example.uavmobile.dji

import dji.sdk.wpmz.value.mission.WaylineDroneType

fun DjiWaylineAircraftType.toWaylineDroneType(): WaylineDroneType {
    return when (this) {
        DjiWaylineAircraftType.PM440 -> WaylineDroneType.PM440
        DjiWaylineAircraftType.EA220 -> WaylineDroneType.EA220
        DjiWaylineAircraftType.WA345 -> WaylineDroneType.WA345
        DjiWaylineAircraftType.EA230 -> WaylineDroneType.EA230
    }
}
