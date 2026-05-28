package com.example.uavmobile.debug

data class DjiWaypointDiagnosticSnapshot(
    val preparedMissionId: String = "",
    val preparedMissionFileName: String = "",
    val kmzPath: String = "",
    val kmzFileExists: Boolean = false,
    val kmzFileSizeBytes: Long = 0L,
    val selectedDjiAircraftFamily: String = "",
    val resolvedWaylineDroneType: String = "",
    val lastDjiWaypointAction: String = "",
    val lastDjiWaypointActionSuccess: Boolean? = null,
    val lastDjiWaypointError: String = "",
    val lastDjiWaypointErrorHint: String = "",
    val missionExecutionState: String = "",
    val sdkMissionExecuteState: String = "",
    val currentWaypointIndex: Int = -1,
    val missionProgress: Double = 0.0,
    val lastInterruptionReason: String = "",
    val lastInterruptionDiagnostics: String = "",
)

data class DjiAircraftDiagnosticSnapshot(
    val productConnected: Boolean = false,
    val productType: String = "",
    val flightMode: String = "",
    val motorsOn: Boolean? = null,
    val isFlying: Boolean? = null,
    val isOnGround: Boolean? = null,
    val isSimulatorStarted: Boolean? = null,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val gpsSignalLevel: String = "",
    val gpsSatelliteCount: Int? = null,
    val batteryPercent: Float? = null,
    val batteryVoltage: Float? = null,
    val groundSpeedMps: Float? = null,
    val locationReadSucceeded: Boolean = false,
    val locationReadError: String = "",
    val telemetryReadSucceeded: Boolean = false,
    val telemetryReadError: String = "",
    val rtkStatus: String = "",
)

data class DjiConnectionDiagnosticSnapshot(
    val productConnected: Boolean = false,
    val productType: String = "",
    val keyConnectionValue: Boolean? = null,
    val lastConnectionSource: String = "",
    val lastRefreshReason: String = "",
    val lastRefreshSucceeded: Boolean = false,
    val lastRefreshError: String = "",
    val monitorRunning: Boolean = false,
    val monitorStartedAt: String = "",
    val monitorTickCount: Int = 0,
    val statusMessage: String = "",
)

data class DjiObstacleAvoidanceDiagnosticSnapshot(
    val mode: String = "UNKNOWN",
    val horizontalSwitch: String = "UNKNOWN",
    val upwardSwitch: String = "UNKNOWN",
    val downwardSwitch: String = "UNKNOWN",
    val horizontalWarningDistanceMeters: Double? = null,
    val upwardWarningDistanceMeters: Double? = null,
    val downwardWarningDistanceMeters: Double? = null,
    val horizontalBrakingDistanceMeters: Double? = null,
    val upwardBrakingDistanceMeters: Double? = null,
    val downwardBrakingDistanceMeters: Double? = null,
    val nearestObstacleDistanceMeters: Double? = null,
    val nearestObstacleDirection: String = "UNKNOWN",
    val safetyState: String = "UNKNOWN",
    val monitoringActive: Boolean = false,
    val lastPrepareSucceeded: Boolean? = null,
    val lastPrepareWarning: String = "",
    val lastPrepareError: String = "",
    val appPauseRequested: Boolean = false,
    val lastMessage: String = "",
    val updatedAt: String = "",
)

data class DjiCameraStreamDiagnosticSnapshot(
    val aircraftModel: String = "",
    val availableSources: String = "",
    val currentSourceName: String = "",
    val currentCameraIndexName: String = "",
    val currentCameraIndexValue: Int? = null,
    val currentLensSourceName: String = "",
    val surfaceReady: Boolean = false,
    val streamDisplaying: Boolean = false,
    val status: String = "",
    val statusMessage: String = "",
    val warningMessage: String = "",
    val errorMessage: String = "",
    val frameListening: Boolean = false,
    val rawStreamListening: Boolean = false,
    val lastFrameInfo: String = "",
    val updatedAt: String = "",
)

data class DeveloperSnapshot(
    val applicationId: String = "",
    val versionName: String = "",
    val activeBackendLabel: String = "",
    val selectedDjiAircraftFamilyLabel: String = "",
    val topStatusLabel: String = "",
    val topStatusKind: String = "",
    val vehicleConnected: Boolean = false,
    val rosWebsocketUrl: String = "",
    val rosConnectionStatus: String = "",
    val rosSessionActive: Boolean = false,
    val rosMissionCacheCount: Int = 0,
    val rosLatestAlert: String = "",
    val djiSdkInitState: String = "",
    val djiSdkStatusMessage: String = "",
    val djiProductConnected: Boolean = false,
    val djiProductId: Int? = null,
    val djiProductTypeLabel: String = "",
    val djiProductStatusMessage: String = "",
    val djiKeyConnectionValue: Boolean? = null,
    val djiLastConnectionSource: String = "",
    val djiLastRefreshReason: String = "",
    val djiLastRefreshSucceeded: Boolean = false,
    val djiLastRefreshError: String = "",
    val djiConnectionMonitorRunning: Boolean = false,
    val djiConnectionMonitorTickCount: Int = 0,
    val djiPermissionsGranted: Boolean = false,
    val djiMissingPermissions: List<String> = emptyList(),
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentAltitudeMeters: Double? = null,
    val currentHeadingDegrees: Float? = null,
    val currentHomeLatitude: Double? = null,
    val currentHomeLongitude: Double? = null,
    val currentStateMessage: String = "",
    val selectedMissionId: String = "",
    val displayedMissionCount: Int = 0,
    val selectedMissionStatus: String = "",
    val selectedMissionProgress: Float = 0f,
    val djiWaypointDiagnostics: DjiWaypointDiagnosticSnapshot = DjiWaypointDiagnosticSnapshot(),
    val djiAircraftDiagnostics: DjiAircraftDiagnosticSnapshot = DjiAircraftDiagnosticSnapshot(),
    val djiConnectionDiagnostics: DjiConnectionDiagnosticSnapshot = DjiConnectionDiagnosticSnapshot(),
    val djiObstacleAvoidanceDiagnostics: DjiObstacleAvoidanceDiagnosticSnapshot = DjiObstacleAvoidanceDiagnosticSnapshot(),
    val djiCameraStreamDiagnostics: DjiCameraStreamDiagnosticSnapshot = DjiCameraStreamDiagnosticSnapshot(),
) {
    fun formatSummary(): String {
        return buildString {
            appendLine("诊断阅读说明：")
            appendLine("- 先看应用信息：applicationId 必须和 DJI 平台包名一致。")
            appendLine("- 再看 DJI 状态：sdkInitState 必须到 REGISTERED 才能上传或启动。")
            appendLine("- 再看 DJI 连接诊断：SDK registered 不等于飞机已连接，KeyConnection=true 才能作为主动确认。")
            appendLine("- 如果 callback 没来但 KeyConnection=true，以 KeyConnection 主动刷新结果为准。")
            appendLine("- 然后看 DJI 航点诊断里的 KMZ 路径、大小、机型映射、最后动作和最后错误。")
            appendLine("- 最后看最近日志，确认初始化、准备、上传、启动和失败回调顺序。")
            appendLine()
            appendLine("应用信息：")
            appendLine("applicationId: $applicationId")
            appendLine("versionName: $versionName")
            appendLine("activeBackend: $activeBackendLabel")
            appendLine("selectedDjiAircraftFamily: $selectedDjiAircraftFamilyLabel")
            appendLine("topStatusLabel: ${topStatusLabel.ifBlank { "无" }}")
            appendLine("topStatusKind: ${topStatusKind.ifBlank { "无" }}")
            appendLine("vehicleConnected: ${vehicleConnected.toChineseBool()}")
            appendLine()
            appendLine("ROS 状态：")
            appendLine("rosWebsocketUrl: $rosWebsocketUrl")
            appendLine("rosConnectionStatus: $rosConnectionStatus")
            appendLine("rosSessionActive: ${rosSessionActive.toChineseBool()}")
            appendLine("rosMissionCacheCount: $rosMissionCacheCount")
            appendLine("rosLatestAlert: ${rosLatestAlert.ifBlank { "无" }}")
            appendLine()
            appendLine("DJI 状态：")
            appendLine("djiSdkInitState: $djiSdkInitState")
            appendLine("djiSdkStatusMessage: $djiSdkStatusMessage")
            appendLine("djiProductConnected: ${djiProductConnected.toChineseBool()}")
            appendLine("djiProductId: ${djiProductId ?: "无"}")
            appendLine("djiProductType: ${djiProductTypeLabel.ifBlank { "无" }}")
            appendLine("djiProductStatusMessage: $djiProductStatusMessage")
            appendLine("djiKeyConnectionValue: ${djiKeyConnectionValue?.toChineseBool() ?: "无"}")
            appendLine("djiLastConnectionSource: ${djiLastConnectionSource.ifBlank { "无" }}")
            appendLine("djiLastRefreshReason: ${djiLastRefreshReason.ifBlank { "无" }}")
            appendLine("djiLastRefreshSucceeded: ${djiLastRefreshSucceeded.toChineseBool()}")
            appendLine("djiLastRefreshError: ${djiLastRefreshError.ifBlank { "无" }}")
            appendLine("djiConnectionMonitorRunning: ${djiConnectionMonitorRunning.toChineseBool()}")
            appendLine("djiConnectionMonitorTickCount: $djiConnectionMonitorTickCount")
            appendLine()
            appendLine("DJI Connection Diagnostics：")
            appendLine("productConnected: ${djiConnectionDiagnostics.productConnected.toChineseBool()}")
            appendLine("productType: ${djiConnectionDiagnostics.productType.ifBlank { "无" }}")
            appendLine("keyConnectionValue: ${djiConnectionDiagnostics.keyConnectionValue?.toChineseBool() ?: "无"}")
            appendLine("lastConnectionSource: ${djiConnectionDiagnostics.lastConnectionSource.ifBlank { "无" }}")
            appendLine("lastRefreshReason: ${djiConnectionDiagnostics.lastRefreshReason.ifBlank { "无" }}")
            appendLine("lastRefreshSucceeded: ${djiConnectionDiagnostics.lastRefreshSucceeded.toChineseBool()}")
            appendLine("lastRefreshError: ${djiConnectionDiagnostics.lastRefreshError.ifBlank { "无" }}")
            appendLine("monitorRunning: ${djiConnectionDiagnostics.monitorRunning.toChineseBool()}")
            appendLine("monitorStartedAt: ${djiConnectionDiagnostics.monitorStartedAt.ifBlank { "无" }}")
            appendLine("monitorTickCount: ${djiConnectionDiagnostics.monitorTickCount}")
            appendLine("statusMessage: ${djiConnectionDiagnostics.statusMessage.ifBlank { "无" }}")
            appendLine()
            appendLine("DJI Camera Stream Diagnostics：")
            appendLine("aircraftModel: ${djiCameraStreamDiagnostics.aircraftModel.ifBlank { "UNKNOWN" }}")
            appendLine("availableSources: ${djiCameraStreamDiagnostics.availableSources.ifBlank { "无" }}")
            appendLine("currentSource: ${djiCameraStreamDiagnostics.currentSourceName.ifBlank { "无" }}")
            appendLine("cameraIndex: ${djiCameraStreamDiagnostics.currentCameraIndexName.ifBlank { "无" }}")
            appendLine("cameraIndexValue: ${djiCameraStreamDiagnostics.currentCameraIndexValue ?: "无"}")
            appendLine("lensSource: ${djiCameraStreamDiagnostics.currentLensSourceName.ifBlank { "无" }}")
            appendLine("surfaceReady: ${djiCameraStreamDiagnostics.surfaceReady.toChineseBool()}")
            appendLine("streamDisplaying: ${djiCameraStreamDiagnostics.streamDisplaying.toChineseBool()}")
            appendLine("status: ${djiCameraStreamDiagnostics.status.ifBlank { "无" }}")
            appendLine("statusMessage: ${djiCameraStreamDiagnostics.statusMessage.ifBlank { "无" }}")
            appendLine("warningMessage: ${djiCameraStreamDiagnostics.warningMessage.ifBlank { "无" }}")
            appendLine("errorMessage: ${djiCameraStreamDiagnostics.errorMessage.ifBlank { "无" }}")
            appendLine("frameListening: ${djiCameraStreamDiagnostics.frameListening.toChineseBool()}")
            appendLine("rawStreamListening: ${djiCameraStreamDiagnostics.rawStreamListening.toChineseBool()}")
            appendLine("lastFrameInfo: ${djiCameraStreamDiagnostics.lastFrameInfo.ifBlank { "无" }}")
            appendLine("updatedAt: ${djiCameraStreamDiagnostics.updatedAt.ifBlank { "无" }}")
            appendLine()
            appendLine("DJI Obstacle Avoidance Diagnostics：")
            appendLine("mode: ${djiObstacleAvoidanceDiagnostics.mode.ifBlank { "UNKNOWN" }}")
            appendLine("horizontalSwitch: ${djiObstacleAvoidanceDiagnostics.horizontalSwitch.ifBlank { "UNKNOWN" }}")
            appendLine("upwardSwitch: ${djiObstacleAvoidanceDiagnostics.upwardSwitch.ifBlank { "UNKNOWN" }}")
            appendLine("downwardSwitch: ${djiObstacleAvoidanceDiagnostics.downwardSwitch.ifBlank { "UNKNOWN" }}")
            appendLine("horizontalWarningDistanceMeters: ${djiObstacleAvoidanceDiagnostics.horizontalWarningDistanceMeters?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("upwardWarningDistanceMeters: ${djiObstacleAvoidanceDiagnostics.upwardWarningDistanceMeters?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("downwardWarningDistanceMeters: ${djiObstacleAvoidanceDiagnostics.downwardWarningDistanceMeters?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("horizontalBrakingDistanceMeters: ${djiObstacleAvoidanceDiagnostics.horizontalBrakingDistanceMeters?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("upwardBrakingDistanceMeters: ${djiObstacleAvoidanceDiagnostics.upwardBrakingDistanceMeters?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("downwardBrakingDistanceMeters: ${djiObstacleAvoidanceDiagnostics.downwardBrakingDistanceMeters?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("nearestObstacleDistanceMeters: ${djiObstacleAvoidanceDiagnostics.nearestObstacleDistanceMeters?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("nearestObstacleDirection: ${djiObstacleAvoidanceDiagnostics.nearestObstacleDirection.ifBlank { "UNKNOWN" }}")
            appendLine("safetyState: ${djiObstacleAvoidanceDiagnostics.safetyState.ifBlank { "UNKNOWN" }}")
            appendLine("monitoringActive: ${djiObstacleAvoidanceDiagnostics.monitoringActive.toChineseBool()}")
            appendLine("lastPrepareSucceeded: ${djiObstacleAvoidanceDiagnostics.lastPrepareSucceeded?.toChineseBool() ?: "无"}")
            appendLine("lastPrepareWarning: ${djiObstacleAvoidanceDiagnostics.lastPrepareWarning.ifBlank { "无" }}")
            appendLine("lastPrepareError: ${djiObstacleAvoidanceDiagnostics.lastPrepareError.ifBlank { "无" }}")
            appendLine("appPauseRequested: ${djiObstacleAvoidanceDiagnostics.appPauseRequested.toChineseBool()}")
            appendLine("lastMessage: ${djiObstacleAvoidanceDiagnostics.lastMessage.ifBlank { "无" }}")
            appendLine("updatedAt: ${djiObstacleAvoidanceDiagnostics.updatedAt.ifBlank { "无" }}")
            appendLine()
            appendLine("DJI 飞机诊断：")
            appendLine("productConnected: ${djiAircraftDiagnostics.productConnected.toChineseBool()}")
            appendLine("productType: ${djiAircraftDiagnostics.productType.ifBlank { "无" }}")
            appendLine("flightMode: ${djiAircraftDiagnostics.flightMode.ifBlank { "无" }}")
            appendLine("motorsOn: ${djiAircraftDiagnostics.motorsOn?.toChineseBool() ?: "无"}")
            appendLine("isFlying: ${djiAircraftDiagnostics.isFlying?.toChineseBool() ?: "无"}")
            appendLine("isOnGround: ${djiAircraftDiagnostics.isOnGround?.toChineseBool() ?: "无"}")
            appendLine("isSimulatorStarted: ${djiAircraftDiagnostics.isSimulatorStarted?.toChineseBool() ?: "无"}")
            appendLine("homeLatitude: ${djiAircraftDiagnostics.homeLatitude?.let { "%.6f".format(it) } ?: "无"}")
            appendLine("homeLongitude: ${djiAircraftDiagnostics.homeLongitude?.let { "%.6f".format(it) } ?: "无"}")
            appendLine("gpsSignalLevel: ${djiAircraftDiagnostics.gpsSignalLevel.ifBlank { "无" }}")
            appendLine("gpsSatelliteCount: ${djiAircraftDiagnostics.gpsSatelliteCount?.toString() ?: "无"}")
            appendLine("batteryPercent: ${djiAircraftDiagnostics.batteryPercent?.let { "%.0f%%".format(it * 100f) } ?: "无"}")
            appendLine("batteryVoltage: ${djiAircraftDiagnostics.batteryVoltage?.let { "%.2f V".format(it) } ?: "无"}")
            appendLine("groundSpeedMps: ${djiAircraftDiagnostics.groundSpeedMps?.let { "%.1f m/s".format(it) } ?: "无"}")
            appendLine("locationReadSucceeded: ${djiAircraftDiagnostics.locationReadSucceeded.toChineseBool()}")
            appendLine("locationReadError: ${djiAircraftDiagnostics.locationReadError.ifBlank { "无" }}")
            appendLine("telemetryReadSucceeded: ${djiAircraftDiagnostics.telemetryReadSucceeded.toChineseBool()}")
            appendLine("telemetryReadError: ${djiAircraftDiagnostics.telemetryReadError.ifBlank { "无" }}")
            appendLine("rtkStatus: ${djiAircraftDiagnostics.rtkStatus.ifBlank { "无" }}")
            appendLine()
            appendLine("位置与 Home：")
            appendLine("djiPermissionsGranted: ${djiPermissionsGranted.toChineseBool()}")
            appendLine("djiMissingPermissions: ${if (djiMissingPermissions.isEmpty()) "无" else djiMissingPermissions.joinToString()}")
            appendLine("currentLatitude: ${currentLatitude?.let { "%.6f".format(it) } ?: "无"}")
            appendLine("currentLongitude: ${currentLongitude?.let { "%.6f".format(it) } ?: "无"}")
            appendLine("currentAltitudeMeters: ${currentAltitudeMeters?.let { "%.2f".format(it) } ?: "无"}")
            appendLine("currentHeadingDegrees: ${currentHeadingDegrees?.let { "%.1f".format(it) } ?: "无"}")
            appendLine("currentHomeLatitude: ${currentHomeLatitude?.let { "%.6f".format(it) } ?: "无"}")
            appendLine("currentHomeLongitude: ${currentHomeLongitude?.let { "%.6f".format(it) } ?: "无"}")
            appendLine("currentStateMessage: ${currentStateMessage.ifBlank { "无" }}")
            appendLine()
            appendLine("DJI 航点诊断：")
            appendLine("preparedMissionId: ${djiWaypointDiagnostics.preparedMissionId.ifBlank { "无" }}")
            appendLine("preparedMissionFileName: ${djiWaypointDiagnostics.preparedMissionFileName.ifBlank { "无" }}")
            appendLine("kmzPath: ${djiWaypointDiagnostics.kmzPath.ifBlank { "无" }}")
            appendLine("kmzFileExists: ${djiWaypointDiagnostics.kmzFileExists.toChineseBool()}")
            appendLine("kmzFileSizeBytes: ${djiWaypointDiagnostics.kmzFileSizeBytes}")
            appendLine("selectedDjiAircraftFamily: ${djiWaypointDiagnostics.selectedDjiAircraftFamily.ifBlank { "无" }}")
            appendLine("resolvedWaylineDroneType: ${djiWaypointDiagnostics.resolvedWaylineDroneType.ifBlank { "无" }}")
            appendLine("lastDjiWaypointAction: ${djiWaypointDiagnostics.lastDjiWaypointAction.ifBlank { "无" }}")
            appendLine("lastDjiWaypointActionSuccess: ${djiWaypointDiagnostics.lastDjiWaypointActionSuccess?.toChineseBool() ?: "无"}")
            appendLine("lastDjiWaypointError: ${djiWaypointDiagnostics.lastDjiWaypointError.ifBlank { "无" }}")
            appendLine("lastDjiWaypointErrorHint: ${djiWaypointDiagnostics.lastDjiWaypointErrorHint.ifBlank { "无" }}")
            appendLine("missionExecutionState: ${djiWaypointDiagnostics.missionExecutionState.ifBlank { "无" }}")
            appendLine("sdkMissionExecuteState: ${djiWaypointDiagnostics.sdkMissionExecuteState.ifBlank { "无" }}")
            appendLine("currentWaypointIndex: ${djiWaypointDiagnostics.currentWaypointIndex}")
            appendLine("missionProgress: ${"%.0f".format(djiWaypointDiagnostics.missionProgress * 100.0)}%")
            appendLine("lastInterruptionReason: ${djiWaypointDiagnostics.lastInterruptionReason.ifBlank { "无" }}")
            appendLine("lastInterruptionDiagnostics: ${djiWaypointDiagnostics.lastInterruptionDiagnostics.ifBlank { "无" }}")
            appendLine()
            appendLine("任务选择：")
            appendLine("selectedMissionId: ${selectedMissionId.ifBlank { "无" }}")
            appendLine("displayedMissionCount: $displayedMissionCount")
            appendLine("selectedMissionStatus: ${selectedMissionStatus.ifBlank { "无" }}")
            appendLine("selectedMissionProgress: ${"%.0f".format(selectedMissionProgress * 100f)}%")
        }
    }
}

private fun Boolean.toChineseBool(): String = if (this) "是" else "否"
