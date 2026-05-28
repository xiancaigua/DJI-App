package com.example.uavmobile.debug

import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSnapshotTest {
    @Test
    fun `format summary includes debug reading guide and waypoint diagnostics`() {
        val snapshot = DeveloperSnapshot(
            applicationId = "com.jzapp.mobile",
            applicationIdIsDefault = false,
            djiSdkInitState = "REGISTERED",
            buildConfigDjiEnableRuntime = true,
            djiAppKeyEmpty = false,
            djiRuntimeSkipped = false,
            djiKeyConnectionValue = true,
            djiLastCallbackConnected = true,
            djiEffectiveConnected = true,
            djiLastConnectionSource = "KEY_MANAGER_REFRESH",
            djiConnectionMonitorRunning = true,
            djiConnectionDiagnostics = DjiConnectionDiagnosticSnapshot(
                productConnected = true,
                effectiveConnected = true,
                keyConnectionValue = true,
                callbackConnected = true,
                lastConnectionSource = "KEY_MANAGER_REFRESH",
                monitorRunning = true,
                monitorTickCount = 2,
            ),
            djiObstacleAvoidanceDiagnostics = DjiObstacleAvoidanceDiagnosticSnapshot(
                mode = "BRAKE",
                horizontalSwitch = "ON",
                upwardSwitch = "ON",
                downwardSwitch = "ON",
                nearestObstacleDistanceMeters = 5.2,
                nearestObstacleDirection = "HORIZONTAL",
                safetyState = "WARNING",
                monitoringActive = true,
                lastPrepareSucceeded = true,
                appPauseRequested = false,
            ),
            djiCameraStreamDiagnostics = DjiCameraStreamDiagnosticSnapshot(
                aircraftModel = "DJI_MATRICE_400",
                availableSources = "FPV:M400 FPV Camera",
                currentSourceName = "M400 FPV Camera",
                currentCameraIndexName = "FPV",
                currentCameraIndexValue = 7,
                currentLensSourceName = "DEFAULT_CAMERA",
                surfaceReady = true,
                streamDisplaying = true,
                status = "DISPLAYING",
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
        assertTrue(summary.contains("buildConfig.DJI_ENABLE_RUNTIME: 是"))
        assertTrue(summary.contains("djiAppKeyEmpty: 否"))
        assertTrue(summary.contains("DJI Connection Diagnostics："))
        assertTrue(summary.contains("keyConnectionValue: 是"))
        assertTrue(summary.contains("callbackConnected: 是"))
        assertTrue(summary.contains("effectiveConnected: 是"))
        assertTrue(summary.contains("monitorRunning: 是"))
        assertTrue(summary.contains("lastConnectionSource: KEY_MANAGER_REFRESH"))
        assertTrue(summary.contains("DJI Obstacle Avoidance Diagnostics："))
        assertTrue(summary.contains("mode: BRAKE"))
        assertTrue(summary.contains("nearestObstacleDirection: HORIZONTAL"))
        assertTrue(summary.contains("safetyState: WARNING"))
        assertTrue(summary.contains("DJI Camera Stream Diagnostics"))
        assertTrue(summary.contains("cameraIndex: FPV"))
        assertTrue(summary.contains("currentSource: M400 FPV Camera"))
        assertTrue(summary.contains("surfaceReady:"))
        assertTrue(summary.contains("streamDisplaying:"))
        assertTrue(summary.contains("DJI 航点诊断："))
        assertTrue(summary.contains("preparedMissionId: mission-1"))
        assertTrue(summary.contains("resolvedWaylineDroneType: PM440"))
        assertTrue(summary.contains("lastDjiWaypointAction: startMission"))
    }
}
