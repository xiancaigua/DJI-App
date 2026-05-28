package com.example.uavmobile.dji

import android.view.Surface
import com.example.uavmobile.core.CameraStreamSnapshot
import com.example.uavmobile.core.CameraStreamSourceKind
import com.example.uavmobile.core.CameraStreamSourceOption
import com.example.uavmobile.core.CameraStreamStatus
import com.example.uavmobile.debug.DeveloperLogStore
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.KeyManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.camera.StreamInfo
import dji.v5.manager.interfaces.ICameraStreamManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class DjiCameraIndex(
    val name: String,
    val value: Int? = null,
)

internal data class DjiCameraFrameInfo(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val formatName: String,
)

internal data class DjiRawStreamInfo(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val mimeType: String,
    val keyFrame: Boolean,
)

internal interface DjiCameraStreamClient {
    fun init()
    fun release()
    fun addAvailableCameraUpdatedListener(callback: (List<DjiCameraIndex>) -> Unit)
    fun removeAvailableCameraUpdatedListener()
    fun refreshAvailableCameraIndexes(): List<DjiCameraIndex>
    fun readProductTypeName(): String
    fun readVideoSourceRange(cameraIndex: DjiCameraIndex): List<String>
    fun setVideoSource(cameraIndex: DjiCameraIndex, sourceName: String): Boolean
    fun bindSurface(cameraIndex: DjiCameraIndex, surface: Any, width: Int, height: Int)
    fun unbindSurface(surface: Any)
    fun startFrameListening(
        cameraIndex: DjiCameraIndex,
        formatName: String,
        callback: (DjiCameraFrameInfo) -> Unit,
    )
    fun stopFrameListening()
    fun startRawStreamListening(
        cameraIndex: DjiCameraIndex,
        callback: (DjiRawStreamInfo) -> Unit,
    )
    fun stopRawStreamListening()
}

/**
 * 本模块基于 DJI MSDK V5 的 CameraStreamManager 获取飞机端可用视频源，并将机载相机或机身
 * FPV 相机的实时画面显示到遥控器自研 App 中。它不实现自主避障算法，也不把 Vision Assist
 * 作为航线任务监管主画面。
 */
object AircraftCameraStreamManager {
    private const val TAG = "AircraftCameraStream"
    private const val DEFAULT_SCALE_TYPE = "CENTER_CROP"

    private val lock = Any()
    private var streamClient: DjiCameraStreamClient = DefaultDjiCameraStreamClient()
    private var currentSurface: Any? = null
    private var currentSurfaceWidth: Int = 0
    private var currentSurfaceHeight: Int = 0
    private var initialized = false

    private val _cameraStreamState = MutableStateFlow(CameraStreamSnapshot())
    val cameraStreamState: StateFlow<CameraStreamSnapshot> = _cameraStreamState.asStateFlow()

    fun init(reason: String) {
        synchronized(lock) {
            if (initialized) {
                DeveloperLogStore.debug(TAG, "视频流模块已初始化", reason)
                refreshAvailableCameras(reason)
                return
            }
            initialized = true
        }
        updateSnapshot(
            status = CameraStreamStatus.INITIALIZING,
            statusMessage = "正在初始化机身摄像头回传",
            warningMessage = "",
            errorMessage = "",
            reason = reason,
        )
        runCatching {
            streamClient.init()
            streamClient.addAvailableCameraUpdatedListener { indexes ->
                handleAvailableCameraIndexes(indexes, "available camera listener")
            }
        }.onSuccess {
            DeveloperLogStore.info(TAG, "视频流模块已初始化", reason)
            refreshAvailableCameras(reason)
        }.onFailure { throwable ->
            initialized = false
            val message = throwable.message ?: throwable::class.java.simpleName
            DeveloperLogStore.error(TAG, "视频流模块初始化失败", "reason=$reason, error=$message")
            updateSnapshot(
                status = CameraStreamStatus.UNSUPPORTED,
                statusMessage = "视频流模块不可用",
                errorMessage = "CameraStreamManager 初始化失败：$message",
                reason = reason,
            )
        }
    }

    fun release(reason: String) {
        val surfaceToRemove = currentSurface
        runCatching {
            stopFrameListening()
            stopRawStreamListening()
            if (surfaceToRemove != null) {
                streamClient.unbindSurface(surfaceToRemove)
            }
            streamClient.removeAvailableCameraUpdatedListener()
            streamClient.release()
        }.onFailure { throwable ->
            DeveloperLogStore.warn(TAG, "释放视频流资源失败", "reason=$reason, error=${throwable.message}")
        }
        synchronized(lock) {
            initialized = false
            currentSurface = null
            currentSurfaceWidth = 0
            currentSurfaceHeight = 0
        }
        DeveloperLogStore.info(TAG, "视频流资源已释放", reason)
        _cameraStreamState.value = CameraStreamSnapshot(
            status = CameraStreamStatus.IDLE,
            statusMessage = "视频预览已释放",
            updatedAt = nowText(),
        )
    }

    fun refreshAvailableCameras(reason: String) {
        runCatching {
            streamClient.refreshAvailableCameraIndexes()
        }.onSuccess { indexes ->
            handleAvailableCameraIndexes(indexes, reason)
        }.onFailure { throwable ->
            val message = throwable.message ?: throwable::class.java.simpleName
            DeveloperLogStore.warn(TAG, "刷新视频源失败", "reason=$reason, error=$message")
            updateSnapshot(
                status = CameraStreamStatus.FAILED,
                statusMessage = "视频源刷新失败",
                errorMessage = "refreshAvailableCameras failed: $message",
                reason = reason,
            )
        }
    }

    fun bindSurface(surface: Surface, width: Int, height: Int) {
        bindSurfaceInternal(surface, width, height, "Surface ready")
    }

    internal fun bindSurfaceForTest(surface: Any, width: Int, height: Int) {
        bindSurfaceInternal(surface, width, height, "Surface test ready")
    }

    fun unbindSurface(surface: Surface) {
        unbindSurfaceInternal(surface, "Surface destroyed")
    }

    internal fun unbindSurfaceForTest(surface: Any) {
        unbindSurfaceInternal(surface, "Surface test destroyed")
    }

    fun switchCamera(indexName: String) {
        val previousSource = cameraStreamState.value.currentSource
        val nextSource = cameraStreamState.value.availableSources.firstOrNull { it.indexName == indexName }
        if (nextSource == null || nextSource.isVisionAssist || !nextSource.enabled) {
            val message = "视频源不可切换：cameraIndex=$indexName"
            DeveloperLogStore.warn(TAG, "切换视频源失败", message)
            updateSnapshot(
                status = CameraStreamStatus.FAILED,
                statusMessage = "视频源切换失败",
                errorMessage = message,
                reason = "switchCamera",
            )
            return
        }

        val cameraIndex = DjiCameraIndex(nextSource.indexName, nextSource.indexValue)
        runCatching {
            if (nextSource.lensSourceName.isNotBlank()) {
                streamClient.setVideoSource(cameraIndex, nextSource.lensSourceName)
            }
            currentSurface?.let { surface ->
                streamClient.bindSurface(cameraIndex, surface, currentSurfaceWidth, currentSurfaceHeight)
            }
        }.onSuccess {
            DeveloperLogStore.info(TAG, "视频源已切换", "cameraIndex=${nextSource.indexName}, source=${nextSource.label}")
            publishSelectedSource(
                source = nextSource,
                status = if (currentSurface != null) CameraStreamStatus.DISPLAYING else CameraStreamStatus.SURFACE_WAITING,
                statusMessage = if (currentSurface != null) "视频显示中" else "视频源已选择，等待 Surface",
                warningMessage = "",
                errorMessage = "",
            )
        }.onFailure { throwable ->
            val message = throwable.message ?: throwable::class.java.simpleName
            DeveloperLogStore.warn(
                TAG,
                "切换视频源失败，恢复上一视频源",
                "requested=${nextSource.indexName}, previous=${previousSource?.indexName.orEmpty()}, error=$message",
            )
            previousSource?.let { previous ->
                runCatching {
                    currentSurface?.let { surface ->
                        streamClient.bindSurface(
                            DjiCameraIndex(previous.indexName, previous.indexValue),
                            surface,
                            currentSurfaceWidth,
                            currentSurfaceHeight,
                        )
                    }
                }
                publishSelectedSource(
                    source = previous,
                    status = if (currentSurface != null) CameraStreamStatus.DISPLAYING else CameraStreamStatus.SURFACE_WAITING,
                    statusMessage = "视频源切换失败，已恢复上一源",
                    warningMessage = "当前视频源显示失败，已尝试切换默认视频源",
                    errorMessage = message,
                )
            } ?: updateSnapshot(
                status = CameraStreamStatus.FAILED,
                statusMessage = "视频源切换失败",
                errorMessage = message,
                reason = "switchCamera",
            )
        }
    }

    fun startFrameListening(formatName: String = "YUV420_888") {
        val source = cameraStreamState.value.currentSource
        if (source == null) {
            updateSnapshot(
                warningMessage = "没有可用视频源，未启动帧监听",
                reason = "startFrameListening",
            )
            return
        }
        runCatching {
            streamClient.startFrameListening(DjiCameraIndex(source.indexName, source.indexValue), formatName) { frame ->
                _cameraStreamState.update {
                    it.copy(
                        frameListening = true,
                        lastFrameInfo = "${frame.width}x${frame.height}@${frame.frameRate} ${frame.formatName}",
                        updatedAt = nowText(),
                    )
                }
            }
        }.onSuccess {
            DeveloperLogStore.info(TAG, "视频帧监听已启动", "cameraIndex=${source.indexName}, format=$formatName")
            _cameraStreamState.update { it.copy(frameListening = true, updatedAt = nowText()) }
        }.onFailure { throwable ->
            DeveloperLogStore.warn(TAG, "视频帧监听启动失败", throwable.message)
            updateSnapshot(
                warningMessage = "视频帧监听启动失败：${throwable.message ?: "未知错误"}",
                reason = "startFrameListening",
            )
        }
    }

    fun stopFrameListening() {
        runCatching { streamClient.stopFrameListening() }
            .onFailure { DeveloperLogStore.warn(TAG, "停止视频帧监听失败", it.message) }
        _cameraStreamState.update { it.copy(frameListening = false, updatedAt = nowText()) }
    }

    fun startRawStreamListening() {
        val source = cameraStreamState.value.currentSource
        if (source == null) {
            updateSnapshot(
                warningMessage = "没有可用视频源，未启动原始码流监听",
                reason = "startRawStreamListening",
            )
            return
        }
        runCatching {
            streamClient.startRawStreamListening(DjiCameraIndex(source.indexName, source.indexValue)) { info ->
                _cameraStreamState.update {
                    it.copy(
                        rawStreamListening = true,
                        lastFrameInfo = "${info.mimeType} ${info.width}x${info.height}@${info.frameRate}, keyFrame=${info.keyFrame}",
                        updatedAt = nowText(),
                    )
                }
            }
        }.onSuccess {
            DeveloperLogStore.info(TAG, "原始码流监听已启动", "cameraIndex=${source.indexName}")
            _cameraStreamState.update { it.copy(rawStreamListening = true, updatedAt = nowText()) }
        }.onFailure { throwable ->
            DeveloperLogStore.warn(TAG, "原始码流监听启动失败", throwable.message)
            updateSnapshot(
                warningMessage = "原始码流监听启动失败：${throwable.message ?: "未知错误"}",
                reason = "startRawStreamListening",
            )
        }
    }

    fun stopRawStreamListening() {
        runCatching { streamClient.stopRawStreamListening() }
            .onFailure { DeveloperLogStore.warn(TAG, "停止原始码流监听失败", it.message) }
        _cameraStreamState.update { it.copy(rawStreamListening = false, updatedAt = nowText()) }
    }

    fun markMissionStartWarning(reason: String) {
        val snapshot = cameraStreamState.value
        val warning = when {
            snapshot.availableSources.none { !it.isVisionAssist && it.enabled } -> "当前无可用视频回传画面，请确认是否继续执行航线任务"
            !snapshot.isStreamDisplaying -> "视频回传未显示，请确认是否继续执行航线任务"
            else -> ""
        }
        if (warning.isNotBlank()) {
            DeveloperLogStore.warn(TAG, "航线启动前视频回传提醒", "reason=$reason, warning=$warning")
            updateSnapshot(warningMessage = warning, reason = reason)
        }
    }

    internal fun selectDefaultCameraIndex(
        aircraftModel: String,
        availableSources: List<CameraStreamSourceOption>,
    ): CameraStreamSourceOption? {
        val missionSources = availableSources.filter { it.enabled && !it.isVisionAssist }
        if (missionSources.isEmpty()) {
            return null
        }
        val model = aircraftModel.uppercase(Locale.US)
        return when {
            model.isMatrice4SeriesName() -> {
                missionSources.firstOrNull { it.kind == CameraStreamSourceKind.WIDE } ?:
                    missionSources.firstOrNull { it.kind == CameraStreamSourceKind.MAIN_GIMBAL } ?:
                    missionSources.firstOrNull { it.kind == CameraStreamSourceKind.DEFAULT } ?:
                    missionSources.first()
            }

            model.isMatrice400Name() -> {
                missionSources.firstOrNull { it.kind == CameraStreamSourceKind.FPV } ?: missionSources.first()
            }

            else -> missionSources.first()
        }
    }

    internal fun handleAvailableCameraIndexesForTest(indexes: List<DjiCameraIndex>, reason: String = "unit test") {
        handleAvailableCameraIndexes(indexes, reason)
    }

    internal fun setClientForTest(client: DjiCameraStreamClient) {
        streamClient = client
        initialized = false
        currentSurface = null
        currentSurfaceWidth = 0
        currentSurfaceHeight = 0
        _cameraStreamState.value = CameraStreamSnapshot(updatedAt = nowText())
    }

    internal fun resetForTest() {
        runCatching { release("unit test reset") }
        streamClient = DefaultDjiCameraStreamClient()
        initialized = false
        currentSurface = null
        currentSurfaceWidth = 0
        currentSurfaceHeight = 0
        _cameraStreamState.value = CameraStreamSnapshot(updatedAt = nowText())
    }

    private fun handleAvailableCameraIndexes(indexes: List<DjiCameraIndex>, reason: String) {
        val aircraftModel = runCatching { streamClient.readProductTypeName() }.getOrDefault("")
        val options = indexes.map { cameraIndex ->
            val lensSources = runCatching { streamClient.readVideoSourceRange(cameraIndex) }
                .onFailure { throwable ->
                    DeveloperLogStore.warn(
                        TAG,
                        "读取视频源 lens range 失败",
                        "cameraIndex=${cameraIndex.name}, model=$aircraftModel, error=${throwable.message}",
                    )
                }
                .getOrDefault(emptyList())
            cameraIndex.toSourceOption(aircraftModel, lensSources)
        }
        val selected = cameraStreamState.value.currentSource
            ?.takeIf { current -> options.any { it.indexName == current.indexName && !it.isVisionAssist && it.enabled } }
            ?: selectDefaultCameraIndex(aircraftModel, options)
        val warning = buildSelectionWarning(aircraftModel, options, selected)
        DeveloperLogStore.info(
            TAG,
            "可用视频源已更新",
            "reason=$reason, model=${aircraftModel.ifBlank { "UNKNOWN" }}, indexes=${options.joinToString { it.indexName + ":" + it.label }}",
        )
        val status = when {
            options.isEmpty() && !DjiConnectionManager.isConnected() -> CameraStreamStatus.NO_AIRCRAFT
            options.isEmpty() -> CameraStreamStatus.NO_SUPPORTED_SOURCE
            selected == null -> CameraStreamStatus.NO_SUPPORTED_SOURCE
            currentSurface == null -> CameraStreamStatus.SURFACE_WAITING
            else -> CameraStreamStatus.DISPLAYING
        }
        val message = when (status) {
            CameraStreamStatus.NO_AIRCRAFT -> "飞机未连接，视频源不可用"
            CameraStreamStatus.NO_SUPPORTED_SOURCE -> "暂无可用于航线监管的视频源"
            CameraStreamStatus.SURFACE_WAITING -> "视频源已就绪，等待 Surface"
            CameraStreamStatus.DISPLAYING -> "视频显示中"
            else -> "视频源已刷新"
        }
        _cameraStreamState.update {
            it.copy(
                aircraftModel = aircraftModel.ifBlank { "UNKNOWN" },
                availableSources = options,
                currentSource = selected,
                currentSourceName = selected?.label.orEmpty(),
                currentCameraIndexName = selected?.indexName.orEmpty(),
                currentCameraIndexValue = selected?.indexValue,
                currentLensSourceName = selected?.lensSourceName.orEmpty(),
                status = status,
                statusMessage = message,
                warningMessage = warning,
                errorMessage = "",
                updatedAt = nowText(),
            )
        }
        if (selected != null && currentSurface != null) {
            bindSurfaceInternal(currentSurface ?: return, currentSurfaceWidth, currentSurfaceHeight, "available camera updated")
        }
    }

    private fun bindSurfaceInternal(surface: Any, width: Int, height: Int, reason: String) {
        currentSurface = surface
        currentSurfaceWidth = width
        currentSurfaceHeight = height
        val selected = cameraStreamState.value.currentSource
        if (selected == null) {
            updateSnapshot(
                isSurfaceReady = true,
                isStreamDisplaying = false,
                status = CameraStreamStatus.SURFACE_WAITING,
                statusMessage = "Surface 已创建，等待可用视频源",
                warningMessage = "暂无可用视频源",
                reason = reason,
            )
            return
        }
        runCatching {
            if (selected.lensSourceName.isNotBlank()) {
                streamClient.setVideoSource(DjiCameraIndex(selected.indexName, selected.indexValue), selected.lensSourceName)
            }
            streamClient.bindSurface(DjiCameraIndex(selected.indexName, selected.indexValue), surface, width, height)
        }.onSuccess {
            DeveloperLogStore.info(
                TAG,
                "视频 Surface 已绑定",
                "cameraIndex=${selected.indexName}, width=$width, height=$height, scaleType=$DEFAULT_SCALE_TYPE",
            )
            publishSelectedSource(
                source = selected,
                status = CameraStreamStatus.DISPLAYING,
                statusMessage = "视频显示中",
                warningMessage = "",
                errorMessage = "",
                surfaceReady = true,
                displaying = true,
            )
        }.onFailure { throwable ->
            val message = throwable.message ?: throwable::class.java.simpleName
            DeveloperLogStore.error(
                TAG,
                "视频 Surface 绑定失败",
                "cameraIndex=${selected.indexName}, model=${cameraStreamState.value.aircraftModel}, error=$message",
            )
            updateSnapshot(
                isSurfaceReady = true,
                isStreamDisplaying = false,
                status = CameraStreamStatus.FAILED,
                statusMessage = "视频显示失败",
                errorMessage = "putCameraStreamSurface failed: $message",
                reason = reason,
            )
        }
    }

    private fun unbindSurfaceInternal(surface: Any, reason: String) {
        runCatching { streamClient.unbindSurface(surface) }
            .onFailure { throwable ->
                DeveloperLogStore.warn(TAG, "解绑视频 Surface 失败", "reason=$reason, error=${throwable.message}")
            }
        if (currentSurface === surface || currentSurface == surface) {
            currentSurface = null
            currentSurfaceWidth = 0
            currentSurfaceHeight = 0
        }
        DeveloperLogStore.info(TAG, "视频 Surface 已解绑", reason)
        updateSnapshot(
            isSurfaceReady = false,
            isStreamDisplaying = false,
            status = if (cameraStreamState.value.currentSource != null) {
                CameraStreamStatus.SURFACE_WAITING
            } else {
                cameraStreamState.value.status
            },
            statusMessage = "Surface 已销毁，视频显示已停止",
            reason = reason,
        )
    }

    private fun publishSelectedSource(
        source: CameraStreamSourceOption,
        status: CameraStreamStatus,
        statusMessage: String,
        warningMessage: String,
        errorMessage: String,
        surfaceReady: Boolean = currentSurface != null,
        displaying: Boolean = currentSurface != null && status == CameraStreamStatus.DISPLAYING,
    ) {
        _cameraStreamState.update {
            it.copy(
                currentSource = source,
                currentSourceName = source.label,
                currentCameraIndexName = source.indexName,
                currentCameraIndexValue = source.indexValue,
                currentLensSourceName = source.lensSourceName,
                isSurfaceReady = surfaceReady,
                isStreamDisplaying = displaying,
                status = status,
                statusMessage = statusMessage,
                warningMessage = warningMessage,
                errorMessage = errorMessage,
                updatedAt = nowText(),
            )
        }
    }

    private fun updateSnapshot(
        status: CameraStreamStatus? = null,
        statusMessage: String? = null,
        warningMessage: String? = null,
        errorMessage: String? = null,
        isSurfaceReady: Boolean? = null,
        isStreamDisplaying: Boolean? = null,
        reason: String,
    ) {
        _cameraStreamState.update {
            it.copy(
                status = status ?: it.status,
                statusMessage = statusMessage ?: it.statusMessage,
                warningMessage = warningMessage ?: it.warningMessage,
                errorMessage = errorMessage ?: it.errorMessage,
                isSurfaceReady = isSurfaceReady ?: it.isSurfaceReady,
                isStreamDisplaying = isStreamDisplaying ?: it.isStreamDisplaying,
                updatedAt = nowText(),
            )
        }
        DeveloperLogStore.debug(TAG, "视频状态已更新", reason)
    }

    private fun DjiCameraIndex.toSourceOption(
        aircraftModel: String,
        lensSources: List<String>,
    ): CameraStreamSourceOption {
        val indexUpper = name.uppercase(Locale.US)
        val preferredLens = preferredLensSource(aircraftModel, indexUpper, lensSources)
        val kind = when {
            indexUpper == "VISION_ASSIST" || preferredLens == "VISION_CAMERA" -> CameraStreamSourceKind.VISION_ASSIST
            indexUpper == "FPV" -> CameraStreamSourceKind.FPV
            preferredLens == "WIDE_CAMERA" -> CameraStreamSourceKind.WIDE
            preferredLens == "ZOOM_CAMERA" -> CameraStreamSourceKind.ZOOM
            preferredLens == "INFRARED_CAMERA" -> CameraStreamSourceKind.THERMAL
            preferredLens == "DEFAULT_CAMERA" -> CameraStreamSourceKind.DEFAULT
            indexUpper.startsWith("PORT_") -> CameraStreamSourceKind.PAYLOAD
            indexUpper == "LEFT_OR_MAIN" || indexUpper == "RIGHT" || indexUpper == "UP" -> CameraStreamSourceKind.MAIN_GIMBAL
            else -> CameraStreamSourceKind.UNKNOWN
        }
        val visionAssist = kind == CameraStreamSourceKind.VISION_ASSIST
        val label = buildSourceLabel(aircraftModel, this, kind, preferredLens, visionAssist)
        return CameraStreamSourceOption(
            indexName = name,
            indexValue = value,
            label = label,
            kind = kind,
            lensSourceName = preferredLens,
            isVisionAssist = visionAssist,
            enabled = !visionAssist,
        )
    }

    private fun preferredLensSource(
        aircraftModel: String,
        indexName: String,
        lensSources: List<String>,
    ): String {
        val normalized = lensSources.map { it.uppercase(Locale.US) }
        return when {
            indexName == "VISION_ASSIST" -> "VISION_CAMERA"
            aircraftModel.uppercase(Locale.US).isMatrice4SeriesName() -> {
                listOf("WIDE_CAMERA", "DEFAULT_CAMERA", "ZOOM_CAMERA", "INFRARED_CAMERA")
                    .firstOrNull { normalized.contains(it) }
                    .orEmpty()
            }

            aircraftModel.uppercase(Locale.US).isMatrice400Name() && indexName == "FPV" -> {
                normalized.firstOrNull { it != "VISION_CAMERA" } ?: "DEFAULT_CAMERA"
            }

            else -> normalized.firstOrNull { it != "VISION_CAMERA" }.orEmpty()
        }
    }

    private fun buildSourceLabel(
        aircraftModel: String,
        index: DjiCameraIndex,
        kind: CameraStreamSourceKind,
        lensSource: String,
        visionAssist: Boolean,
    ): String {
        if (visionAssist) {
            return "Vision Assist（不用于航线监管）"
        }
        val model = aircraftModel.uppercase(Locale.US)
        return when {
            model.isMatrice4SeriesName() -> when (kind) {
                CameraStreamSourceKind.WIDE -> "M4T 主相机 / 广角"
                CameraStreamSourceKind.ZOOM -> "M4T 变焦相机"
                CameraStreamSourceKind.THERMAL -> "M4T 热成像"
                else -> "M4T 内置一体化云台 cameraIndex=${index.name}"
            }

            model.isMatrice400Name() && kind == CameraStreamSourceKind.FPV -> "M400 FPV Camera"
            model.isMatrice400Name() -> "M400 可用视频源 cameraIndex=${index.name}"
            kind == CameraStreamSourceKind.FPV -> "FPV Camera"
            kind == CameraStreamSourceKind.PAYLOAD -> "Payload cameraIndex=${index.name}"
            lensSource.isNotBlank() -> "UNKNOWN cameraIndex=${index.name} source=$lensSource"
            else -> "UNKNOWN cameraIndex=${index.name}"
        }
    }

    private fun buildSelectionWarning(
        aircraftModel: String,
        options: List<CameraStreamSourceOption>,
        selected: CameraStreamSourceOption?,
    ): String {
        if (options.isEmpty()) {
            return "暂无可用视频源"
        }
        if (selected == null) {
            return "暂无可用于航线监管的视频源；Vision Assist 不作为任务主画面"
        }
        val model = aircraftModel.uppercase(Locale.US)
        return when {
            model.isMatrice400Name() && selected.kind != CameraStreamSourceKind.FPV -> {
                "无法精确识别 FPV，使用可用默认视频源；请真机确认 cameraIndex=${selected.indexName}"
            }

            model.isBlank() || model == "UNKNOWN" -> {
                "当前机型未知，已使用默认视频源；请真机确认 cameraIndex=${selected.indexName}"
            }

            else -> ""
        }
    }

    private fun String.isMatrice4SeriesName(): Boolean {
        return !isMatrice400Name() &&
            (contains("MATRICE_4_SERIES") || contains("MATRICE_4D") || contains("MATRICE 4") ||
                contains("M4T") || contains("M4D"))
    }

    private fun String.isMatrice400Name(): Boolean {
        return contains("MATRICE_400") || contains("MATRICE 400") || contains("M400") || contains("PM440")
    }

    private fun nowText(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}

private class DefaultDjiCameraStreamClient : DjiCameraStreamClient {
    private var cameraUpdatedListener: ICameraStreamManager.AvailableCameraUpdatedListener? = null
    private var frameListener: ICameraStreamManager.CameraFrameListener? = null
    private var rawStreamListener: ICameraStreamManager.ReceiveStreamListener? = null
    private var lastAvailableCameraIndexes: List<DjiCameraIndex> = emptyList()

    override fun init() {
        cameraManager()
        DeveloperLogStore.debug(TAG, "CameraStreamManager 可用")
    }

    override fun release() {
        removeAvailableCameraUpdatedListener()
        stopFrameListening()
        stopRawStreamListening()
    }

    override fun addAvailableCameraUpdatedListener(callback: (List<DjiCameraIndex>) -> Unit) {
        removeAvailableCameraUpdatedListener()
        val listener = object : ICameraStreamManager.AvailableCameraUpdatedListener {
            override fun onAvailableCameraUpdated(availableCameraList: MutableList<ComponentIndexType>) {
                lastAvailableCameraIndexes = availableCameraList.map { it.toDjiCameraIndex() }
                callback(lastAvailableCameraIndexes)
            }

            override fun onCameraStreamEnableUpdate(cameraStreamEnableMap: MutableMap<ComponentIndexType, Boolean>) = Unit
        }
        cameraUpdatedListener = listener
        cameraManager().addAvailableCameraUpdatedListener(listener)
    }

    override fun removeAvailableCameraUpdatedListener() {
        val listener = cameraUpdatedListener ?: return
        runCatching { cameraManager().removeAvailableCameraUpdatedListener(listener) }
        cameraUpdatedListener = null
    }

    override fun refreshAvailableCameraIndexes(): List<DjiCameraIndex> {
        return lastAvailableCameraIndexes
    }

    override fun readProductTypeName(): String {
        return DjiConnectionManager.currentProductType()?.name.orEmpty()
    }

    override fun readVideoSourceRange(cameraIndex: DjiCameraIndex): List<String> {
        val keyManager = KeyManager.getInstance() ?: return emptyList()
        val componentIndex = cameraIndex.toComponentIndexType()
        val key = KeyTools.createKey(CameraKey.KeyCameraVideoStreamSourceRange, componentIndex)
        val sourceRange: List<CameraVideoStreamSourceType>? = keyManager.getValue(key)
        return sourceRange.orEmpty().map { it.name }
    }

    override fun setVideoSource(cameraIndex: DjiCameraIndex, sourceName: String): Boolean {
        val source = runCatching { CameraVideoStreamSourceType.valueOf(sourceName) }.getOrNull() ?: return false
        val keyManager = KeyManager.getInstance() ?: return false
        val componentIndex = cameraIndex.toComponentIndexType()
        val key = KeyTools.createKey(CameraKey.KeyCameraVideoStreamSource, componentIndex)
        keyManager.setValue(
            key,
            source,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    DeveloperLogStore.info(
                        TAG,
                        "视频 lens/source 已设置",
                        "cameraIndex=${cameraIndex.name}, source=$sourceName",
                    )
                }

                override fun onFailure(error: IDJIError) {
                    DeveloperLogStore.warn(
                        TAG,
                        "视频 lens/source 设置失败",
                        "cameraIndex=${cameraIndex.name}, source=$sourceName, error=${error.description()}",
                    )
                }
            },
        )
        return true
    }

    override fun bindSurface(cameraIndex: DjiCameraIndex, surface: Any, width: Int, height: Int) {
        val androidSurface = surface as? Surface ?: error("Surface 类型无效")
        cameraManager().putCameraStreamSurface(
            cameraIndex.toComponentIndexType(),
            androidSurface,
            width,
            height,
            ICameraStreamManager.ScaleType.CENTER_CROP,
        )
    }

    override fun unbindSurface(surface: Any) {
        val androidSurface = surface as? Surface ?: return
        cameraManager().removeCameraStreamSurface(androidSurface)
    }

    override fun startFrameListening(
        cameraIndex: DjiCameraIndex,
        formatName: String,
        callback: (DjiCameraFrameInfo) -> Unit,
    ) {
        stopFrameListening()
        val format = runCatching { ICameraStreamManager.FrameFormat.valueOf(formatName) }
            .getOrDefault(ICameraStreamManager.FrameFormat.YUV420_888)
        val listener = object : ICameraStreamManager.CameraFrameListener {
            override fun onFrame(
                frameData: ByteArray,
                offset: Int,
                length: Int,
                width: Int,
                height: Int,
                format: ICameraStreamManager.FrameFormat,
            ) {
                val frameInfo = cameraManager().getAircraftStreamFrameInfo(cameraIndex.toComponentIndexType())
                callback(
                    DjiCameraFrameInfo(
                        width = width,
                        height = height,
                        frameRate = frameInfo?.frameRate ?: 0,
                        formatName = format.name,
                    ),
                )
            }
        }
        frameListener = listener
        cameraManager().addFrameListener(cameraIndex.toComponentIndexType(), format, listener)
    }

    override fun stopFrameListening() {
        val listener = frameListener ?: return
        runCatching { cameraManager().removeFrameListener(listener) }
        frameListener = null
    }

    override fun startRawStreamListening(
        cameraIndex: DjiCameraIndex,
        callback: (DjiRawStreamInfo) -> Unit,
    ) {
        stopRawStreamListening()
        val listener = object : ICameraStreamManager.ReceiveStreamListener {
            override fun onReceiveStream(data: ByteArray, offset: Int, length: Int, info: StreamInfo) {
                callback(
                    DjiRawStreamInfo(
                        width = info.width,
                        height = info.height,
                        frameRate = info.frameRate,
                        mimeType = info.mimeType?.name.orEmpty(),
                        keyFrame = info.isKeyFrame,
                    ),
                )
            }
        }
        rawStreamListener = listener
        cameraManager().addReceiveStreamListener(cameraIndex.toComponentIndexType(), listener)
    }

    override fun stopRawStreamListening() {
        val listener = rawStreamListener ?: return
        runCatching { cameraManager().removeReceiveStreamListener(listener) }
        rawStreamListener = null
    }

    private fun cameraManager(): ICameraStreamManager {
        return MediaDataCenter.getInstance().cameraStreamManager
            ?: error("DJI CameraStreamManager 不可用")
    }

    private fun DjiCameraIndex.toComponentIndexType(): ComponentIndexType {
        return runCatching { ComponentIndexType.valueOf(name) }.getOrDefault(ComponentIndexType.UNKNOWN)
    }

    private fun ComponentIndexType.toDjiCameraIndex(): DjiCameraIndex {
        return DjiCameraIndex(name = name, value = value())
    }

    private companion object {
        private const val TAG = "AircraftCameraStream"
    }
}
