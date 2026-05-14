package com.example.uavmobile.core

import android.util.Log
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.data.model.ConnectionConfig
import com.example.uavmobile.data.repository.UavRepository

class SelfDroneController(
    private val repository: UavRepository,
) : DroneController {
    override suspend fun connect(connectionConfig: ConnectionConfig?): ActionResult {
        val config = connectionConfig ?: return ActionResult(false, "ROS backend requires a connection config", -1)
        Log.i(TAG, "Connecting ROS backend to ${config.websocketUrl}")
        repository.connect(config)
        return ActionResult(true, "Connecting to ${config.websocketUrl}")
    }

    override suspend fun disconnect(): ActionResult {
        Log.i(TAG, "Disconnecting ROS backend")
        repository.disconnect()
        return ActionResult(true, "Disconnected from rosbridge")
    }

    override fun getState(): Result<DroneState> {
        return Result.success(repository.telemetry.value.toDroneState())
    }

    override suspend fun refreshMissions(): ActionResult {
        Log.i(TAG, "Refreshing ROS mission list")
        return repository.refreshMissions()
    }

    override suspend fun uploadMission(
        missionId: String,
        waypoints: List<Waypoint>,
        selectedDjiAircraftFamily: DjiAircraftFamily,
    ): ActionResult {
        Log.i(TAG, "Uploading ROS mission $missionId with ${waypoints.size} waypoint(s)")
        return repository.uploadMission(missionId, waypoints)
    }

    override suspend fun startMission(missionId: String?): ActionResult {
        return withMissionId(missionId, "start") { repository.startMission(it) }
    }

    override suspend fun pauseMission(missionId: String?): ActionResult {
        return withMissionId(missionId, "pause") { repository.pauseMission(it) }
    }

    override suspend fun resumeMission(missionId: String?): ActionResult {
        return withMissionId(missionId, "resume") { repository.resumeMission(it) }
    }

    override suspend fun stopMission(missionId: String?): ActionResult {
        Log.w(TAG, "Stop mission is not supported for ROS backend")
        return ActionResult(false, "Stop mission is not supported for the ROS backend", -1)
    }

    override suspend fun returnHome(missionId: String?): ActionResult {
        return withMissionId(missionId, "returnHome") { repository.rtl(it) }
    }

    override suspend fun land(missionId: String?): ActionResult {
        return withMissionId(missionId, "land") { repository.land(it) }
    }

    private suspend fun withMissionId(
        missionId: String?,
        actionName: String,
        block: suspend (String) -> ActionResult,
    ): ActionResult {
        val selectedMissionId = missionId?.takeIf { it.isNotBlank() }
            ?: return ActionResult(false, "Select a mission first", -1)
        Log.i(TAG, "Executing ROS action $actionName for mission $selectedMissionId")
        return block(selectedMissionId)
    }

    companion object {
        private const val TAG = "SelfDroneController"
    }
}
