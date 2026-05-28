package com.example.uavmobile.core

enum class CameraStreamStatus {
    IDLE,
    INITIALIZING,
    READY,
    SURFACE_WAITING,
    DISPLAYING,
    NO_AIRCRAFT,
    NO_SUPPORTED_SOURCE,
    FAILED,
    UNSUPPORTED,
}

enum class CameraStreamSourceKind {
    MAIN_GIMBAL,
    WIDE,
    ZOOM,
    THERMAL,
    FPV,
    PAYLOAD,
    DEFAULT,
    VISION_ASSIST,
    UNKNOWN,
}

data class CameraStreamSourceOption(
    val indexName: String,
    val indexValue: Int? = null,
    val label: String,
    val kind: CameraStreamSourceKind = CameraStreamSourceKind.UNKNOWN,
    val lensSourceName: String = "",
    val isVisionAssist: Boolean = false,
    val enabled: Boolean = true,
)

data class CameraStreamSnapshot(
    val aircraftModel: String = "",
    val availableSources: List<CameraStreamSourceOption> = emptyList(),
    val currentSource: CameraStreamSourceOption? = null,
    val currentSourceName: String = "",
    val currentCameraIndexName: String = "",
    val currentCameraIndexValue: Int? = null,
    val currentLensSourceName: String = "",
    val isSurfaceReady: Boolean = false,
    val isStreamDisplaying: Boolean = false,
    val status: CameraStreamStatus = CameraStreamStatus.IDLE,
    val statusMessage: String = "视频预览尚未初始化",
    val warningMessage: String = "",
    val errorMessage: String = "",
    val frameListening: Boolean = false,
    val rawStreamListening: Boolean = false,
    val lastFrameInfo: String = "",
    val updatedAt: String = "",
)
