package com.example.uavmobile.ui.viewmodel

import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.data.model.ConnectionStatus
import com.example.uavmobile.data.model.TelemetrySnapshot
import com.example.uavmobile.dji.DjiSdkInitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopVehicleStatusTest {
    @Test
    fun `self ros bridge connected without telemetry aircraft connection is not top connected`() {
        val status = UavUiState(
            activeBackend = DroneBackend.SELF_ROS,
            rosConnectionStatus = ConnectionStatus.CONNECTED,
            telemetry = TelemetrySnapshot(connected = false),
        ).resolveTopVehicleStatus()

        assertEquals("ROS 已连通", status.label)
        assertEquals(ConnectionStatus.CONNECTING, status.kind)
        assertFalse(status.vehicleConnected)
        assertFalse(status.label == "Connected")
    }

    @Test
    fun `self ros telemetry connected is aircraft connected`() {
        val status = UavUiState(
            activeBackend = DroneBackend.SELF_ROS,
            rosConnectionStatus = ConnectionStatus.CONNECTED,
            telemetry = TelemetrySnapshot(connected = true),
        ).resolveTopVehicleStatus()

        assertEquals("飞机已连接", status.label)
        assertEquals(ConnectionStatus.CONNECTED, status.kind)
        assertTrue(status.vehicleConnected)
    }

    @Test
    fun `dji registered without product is sdk ready and aircraft offline`() {
        val status = UavUiState(
            activeBackend = DroneBackend.DJI,
            djiSdkInitState = DjiSdkInitState.REGISTERED,
            djiProductConnected = false,
        ).resolveTopVehicleStatus()

        assertEquals("DJI 已就绪 · 飞机离线", status.label)
        assertEquals(ConnectionStatus.CONNECTING, status.kind)
        assertFalse(status.vehicleConnected)
        assertFalse(status.label == "Connected")
    }

    @Test
    fun `dji product connected is dji aircraft connected`() {
        val status = UavUiState(
            activeBackend = DroneBackend.DJI,
            djiSdkInitState = DjiSdkInitState.REGISTERED,
            djiProductConnected = true,
        ).resolveTopVehicleStatus()

        assertEquals("DJI 飞机已连接", status.label)
        assertEquals(ConnectionStatus.CONNECTED, status.kind)
        assertTrue(status.vehicleConnected)
    }

    @Test
    fun `dji failed is top failed`() {
        val status = UavUiState(
            activeBackend = DroneBackend.DJI,
            djiSdkInitState = DjiSdkInitState.FAILED,
            djiProductConnected = false,
        ).resolveTopVehicleStatus()

        assertEquals("DJI 失败", status.label)
        assertEquals(ConnectionStatus.FAILED, status.kind)
        assertFalse(status.vehicleConnected)
    }
}
