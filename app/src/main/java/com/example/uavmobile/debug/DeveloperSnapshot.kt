package com.example.uavmobile.debug

data class DeveloperSnapshot(
    val applicationId: String = "",
    val versionName: String = "",
    val activeBackendLabel: String = "",
    val selectedDjiAircraftFamilyLabel: String = "",
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
) {
    fun formatSummary(): String {
        return buildString {
            appendLine("applicationId: $applicationId")
            appendLine("versionName: $versionName")
            appendLine("activeBackend: $activeBackendLabel")
            appendLine("selectedDjiAircraftFamily: $selectedDjiAircraftFamilyLabel")
            appendLine("rosWebsocketUrl: $rosWebsocketUrl")
            appendLine("rosConnectionStatus: $rosConnectionStatus")
            appendLine("rosSessionActive: $rosSessionActive")
            appendLine("rosMissionCacheCount: $rosMissionCacheCount")
            appendLine("rosLatestAlert: ${rosLatestAlert.ifBlank { "none" }}")
            appendLine("djiSdkInitState: $djiSdkInitState")
            appendLine("djiSdkStatusMessage: $djiSdkStatusMessage")
            appendLine("djiProductConnected: $djiProductConnected")
            appendLine("djiProductId: ${djiProductId ?: "n/a"}")
            appendLine("djiProductType: ${djiProductTypeLabel.ifBlank { "n/a" }}")
            appendLine("djiProductStatusMessage: $djiProductStatusMessage")
            appendLine("djiPermissionsGranted: $djiPermissionsGranted")
            appendLine("djiMissingPermissions: ${if (djiMissingPermissions.isEmpty()) "none" else djiMissingPermissions.joinToString()}")
            appendLine("currentLatitude: ${currentLatitude?.let { "%.6f".format(it) } ?: "n/a"}")
            appendLine("currentLongitude: ${currentLongitude?.let { "%.6f".format(it) } ?: "n/a"}")
            appendLine("currentAltitudeMeters: ${currentAltitudeMeters?.let { "%.2f".format(it) } ?: "n/a"}")
            appendLine("currentHeadingDegrees: ${currentHeadingDegrees?.let { "%.1f".format(it) } ?: "n/a"}")
            appendLine("currentHomeLatitude: ${currentHomeLatitude?.let { "%.6f".format(it) } ?: "n/a"}")
            appendLine("currentHomeLongitude: ${currentHomeLongitude?.let { "%.6f".format(it) } ?: "n/a"}")
            appendLine("currentStateMessage: ${currentStateMessage.ifBlank { "none" }}")
            appendLine("selectedMissionId: ${selectedMissionId.ifBlank { "none" }}")
            appendLine("displayedMissionCount: $displayedMissionCount")
            appendLine("selectedMissionStatus: ${selectedMissionStatus.ifBlank { "none" }}")
            appendLine("selectedMissionProgress: ${"%.0f".format(selectedMissionProgress * 100f)}%")
        }
    }
}
