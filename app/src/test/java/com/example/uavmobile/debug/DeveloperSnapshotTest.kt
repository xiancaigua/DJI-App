package com.example.uavmobile.debug

import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSnapshotTest {
    @Test
    fun `format summary includes debug reading guide and waypoint diagnostics`() {
        val snapshot = DeveloperSnapshot(
            applicationId = "com.jzapp.mobile",
            djiSdkInitState = "REGISTERED",
            djiKeyConnectionValue = true,
            djiLastConnectionSource = "KEY_MANAGER_REFRESH",
            djiConnectionMonitorRunning = true,
            djiConnectionDiagnostics = DjiConnectionDiagnosticSnapshot(
                productConnected = true,
                keyConnectionValue = true,
                lastConnectionSource = "KEY_MANAGER_REFRESH",
                monitorRunning = true,
                monitorTickCount = 2,
            ),
            djiWaypointDiagnostics = DjiWaypointDiagnosticSnapshot(
                preparedMissionId = "mission-1",
                kmzPath = "/tmp/mission-1.kmz",
                resolvedWaylineDroneType = "PM440",
                lastDjiWaypointAction = "startMission",
                lastDjiWaypointError = "DJI startMission failed",
            ),
        )

        val summary = snapshot.formatSummary()

        assertTrue(summary.contains("诊断阅读说明："))
        assertTrue(summary.contains("applicationId 必须和 DJI 平台包名一致"))
        assertTrue(summary.contains("DJI Connection Diagnostics："))
        assertTrue(summary.contains("keyConnectionValue: 是"))
        assertTrue(summary.contains("monitorRunning: 是"))
        assertTrue(summary.contains("lastConnectionSource: KEY_MANAGER_REFRESH"))
        assertTrue(summary.contains("DJI 航点诊断："))
        assertTrue(summary.contains("preparedMissionId: mission-1"))
        assertTrue(summary.contains("resolvedWaylineDroneType: PM440"))
        assertTrue(summary.contains("lastDjiWaypointAction: startMission"))
    }
}
