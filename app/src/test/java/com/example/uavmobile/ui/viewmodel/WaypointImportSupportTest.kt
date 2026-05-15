package com.example.uavmobile.ui.viewmodel

import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneConnectionState
import com.example.uavmobile.core.DroneState
import com.example.uavmobile.data.model.MissionWaypointDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointImportSupportTest {
    @Test
    fun `ros valid coordinates import into new waypoint`() {
        val result = WaypointImportSupport.createWaypointFromCurrentPosition(
            backend = DroneBackend.SELF_ROS,
            currentDroneState = DroneState(
                backend = DroneBackend.SELF_ROS,
                connectionState = DroneConnectionState.CONNECTED,
                latitude = 31.123456,
                longitude = 121.654321,
            ),
            existingWaypoints = listOf(MissionWaypointDraft()),
        )

        assertTrue(result.isSuccess)
        val waypoint = result.getOrThrow()
        assertEquals("31.123456", waypoint.lat)
        assertEquals("121.654321", waypoint.lon)
    }

    @Test
    fun `dji valid coordinates copy last waypoint non position fields`() {
        val result = WaypointImportSupport.createWaypointFromCurrentPosition(
            backend = DroneBackend.DJI,
            currentDroneState = DroneState(
                backend = DroneBackend.DJI,
                connectionState = DroneConnectionState.CONNECTED,
                latitude = 32.000001,
                longitude = 118.000002,
            ),
            existingWaypoints = listOf(
                MissionWaypointDraft(lat = "1", lon = "2", altM = "60", holdSec = "5", yawDeg = "90"),
            ),
        )

        val waypoint = result.getOrThrow()
        assertEquals("32.000001", waypoint.lat)
        assertEquals("118.000002", waypoint.lon)
        assertEquals("60", waypoint.altM)
        assertEquals("5", waypoint.holdSec)
        assertEquals("90", waypoint.yawDeg)
    }

    @Test
    fun `ros invalid coordinates fail clearly`() {
        val result = WaypointImportSupport.createWaypointFromCurrentPosition(
            backend = DroneBackend.SELF_ROS,
            currentDroneState = DroneState(
                backend = DroneBackend.SELF_ROS,
                connectionState = DroneConnectionState.DISCONNECTED,
                latitude = 0.0,
                longitude = 0.0,
            ),
            existingWaypoints = emptyList(),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("ROS telemetry"))
    }

    @Test
    fun `dji invalid coordinates fail clearly`() {
        val result = WaypointImportSupport.createWaypointFromCurrentPosition(
            backend = DroneBackend.DJI,
            currentDroneState = DroneState(
                backend = DroneBackend.DJI,
                connectionState = DroneConnectionState.DISCONNECTED,
                statusMessage = "DJI product not connected",
            ),
            existingWaypoints = emptyList(),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("DJI"))
    }
}
