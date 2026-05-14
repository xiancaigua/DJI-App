package com.example.uavmobile.core

import com.example.uavmobile.data.model.ConnectionConfig
import com.example.uavmobile.data.model.ActionResult

interface DroneController {
    suspend fun connect(connectionConfig: ConnectionConfig? = null): ActionResult

    suspend fun disconnect(): ActionResult

    fun getState(): Result<DroneState>

    suspend fun refreshMissions(): ActionResult

    suspend fun uploadMission(
        missionId: String,
        waypoints: List<Waypoint>,
        selectedDjiAircraftFamily: DjiAircraftFamily = DjiAircraftFamily.AUTO,
    ): ActionResult

    suspend fun startMission(missionId: String? = null): ActionResult

    suspend fun pauseMission(missionId: String? = null): ActionResult

    suspend fun resumeMission(missionId: String? = null): ActionResult

    suspend fun stopMission(missionId: String? = null): ActionResult

    suspend fun returnHome(missionId: String? = null): ActionResult

    suspend fun land(missionId: String? = null): ActionResult
}
