package com.example.uavmobile.dji

import com.example.uavmobile.core.CameraStreamSourceKind
import com.example.uavmobile.core.CameraStreamStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftCameraStreamManagerTest {
    @After
    fun tearDown() {
        AircraftCameraStreamManager.resetForTest()
        DjiConnectionManager.resetForTest()
    }

    @Test
    fun `m4t defaults to non vision wide camera source`() {
        val fake = FakeCameraStreamClient(
            productTypeName = "DJI_MATRICE_4_SERIES",
            indexes = listOf(
                DjiCameraIndex("VISION_ASSIST", 14),
                DjiCameraIndex("LEFT_OR_MAIN", 0),
            ),
            sourceRanges = mapOf(
                "VISION_ASSIST" to listOf("VISION_CAMERA"),
                "LEFT_OR_MAIN" to listOf("WIDE_CAMERA", "ZOOM_CAMERA", "INFRARED_CAMERA"),
            ),
        )
        AircraftCameraStreamManager.setClientForTest(fake)

        AircraftCameraStreamManager.init("unit test")

        val snapshot = AircraftCameraStreamManager.cameraStreamState.value
        assertEquals("LEFT_OR_MAIN", snapshot.currentCameraIndexName)
        assertEquals(CameraStreamSourceKind.WIDE, snapshot.currentSource?.kind)
        assertFalse(snapshot.currentSource?.isVisionAssist ?: true)
        assertTrue(snapshot.availableSources.any { it.isVisionAssist })
    }

    @Test
    fun `m400 defaults to fpv camera source`() {
        val fake = FakeCameraStreamClient(
            productTypeName = "DJI_MATRICE_400",
            indexes = listOf(
                DjiCameraIndex("LEFT_OR_MAIN", 0),
                DjiCameraIndex("FPV", 7),
            ),
            sourceRanges = mapOf(
                "LEFT_OR_MAIN" to listOf("DEFAULT_CAMERA"),
                "FPV" to listOf("DEFAULT_CAMERA"),
            ),
        )
        AircraftCameraStreamManager.setClientForTest(fake)

        AircraftCameraStreamManager.init("unit test")

        val snapshot = AircraftCameraStreamManager.cameraStreamState.value
        assertEquals("FPV", snapshot.currentCameraIndexName)
        assertEquals(CameraStreamSourceKind.FPV, snapshot.currentSource?.kind)
        assertTrue(snapshot.currentSourceName.contains("FPV"))
    }

    @Test
    fun `unknown aircraft falls back to first non vision source with warning`() {
        val fake = FakeCameraStreamClient(
            productTypeName = "",
            indexes = listOf(
                DjiCameraIndex("VISION_ASSIST", 14),
                DjiCameraIndex("PORT_1", 16),
            ),
            sourceRanges = mapOf(
                "VISION_ASSIST" to listOf("VISION_CAMERA"),
                "PORT_1" to listOf("DEFAULT_CAMERA"),
            ),
        )
        AircraftCameraStreamManager.setClientForTest(fake)

        AircraftCameraStreamManager.init("unit test")

        val snapshot = AircraftCameraStreamManager.cameraStreamState.value
        assertEquals("PORT_1", snapshot.currentCameraIndexName)
        assertTrue(snapshot.warningMessage.contains("机型未知"))
    }

    @Test
    fun `vision assist alone is not selected as mission monitoring source`() {
        val fake = FakeCameraStreamClient(
            productTypeName = "DJI_MATRICE_400",
            indexes = listOf(DjiCameraIndex("VISION_ASSIST", 14)),
            sourceRanges = mapOf("VISION_ASSIST" to listOf("VISION_CAMERA")),
        )
        AircraftCameraStreamManager.setClientForTest(fake)

        AircraftCameraStreamManager.init("unit test")

        val snapshot = AircraftCameraStreamManager.cameraStreamState.value
        assertNull(snapshot.currentSource)
        assertEquals(CameraStreamStatus.NO_SUPPORTED_SOURCE, snapshot.status)
        assertTrue(snapshot.warningMessage.contains("Vision Assist"))
    }

    @Test
    fun `surface ready binds and surface destroyed removes stream surface`() {
        val fake = FakeCameraStreamClient(
            productTypeName = "DJI_MATRICE_400",
            indexes = listOf(DjiCameraIndex("FPV", 7)),
            sourceRanges = mapOf("FPV" to listOf("DEFAULT_CAMERA")),
        )
        val surfaceToken = Any()
        AircraftCameraStreamManager.setClientForTest(fake)
        AircraftCameraStreamManager.init("unit test")

        AircraftCameraStreamManager.bindSurfaceForTest(surfaceToken, 1280, 720)

        assertEquals("FPV", fake.boundIndex?.name)
        assertEquals(1280, fake.boundWidth)
        assertEquals(720, fake.boundHeight)
        assertTrue(AircraftCameraStreamManager.cameraStreamState.value.isStreamDisplaying)

        AircraftCameraStreamManager.unbindSurfaceForTest(surfaceToken)

        assertEquals(surfaceToken, fake.unboundSurface)
        assertFalse(AircraftCameraStreamManager.cameraStreamState.value.isStreamDisplaying)
    }

    @Test
    fun `switch failure restores previous camera source`() {
        val fake = FakeCameraStreamClient(
            productTypeName = "UNKNOWN",
            indexes = listOf(
                DjiCameraIndex("LEFT_OR_MAIN", 0),
                DjiCameraIndex("RIGHT", 1),
            ),
            sourceRanges = mapOf(
                "LEFT_OR_MAIN" to listOf("DEFAULT_CAMERA"),
                "RIGHT" to listOf("DEFAULT_CAMERA"),
            ),
            failBindNames = setOf("RIGHT"),
        )
        val surfaceToken = Any()
        AircraftCameraStreamManager.setClientForTest(fake)
        AircraftCameraStreamManager.init("unit test")
        AircraftCameraStreamManager.bindSurfaceForTest(surfaceToken, 800, 450)

        AircraftCameraStreamManager.switchCamera("RIGHT")

        val snapshot = AircraftCameraStreamManager.cameraStreamState.value
        assertEquals("LEFT_OR_MAIN", snapshot.currentCameraIndexName)
        assertEquals("LEFT_OR_MAIN", fake.boundIndex?.name)
        assertTrue(snapshot.statusMessage.contains("恢复"))
    }

    @Test
    fun `frame and raw stream listeners are opt in and can be cleared`() {
        val fake = FakeCameraStreamClient(
            productTypeName = "DJI_MATRICE_400",
            indexes = listOf(DjiCameraIndex("FPV", 7)),
            sourceRanges = mapOf("FPV" to listOf("DEFAULT_CAMERA")),
        )
        AircraftCameraStreamManager.setClientForTest(fake)
        AircraftCameraStreamManager.init("unit test")

        assertFalse(AircraftCameraStreamManager.cameraStreamState.value.frameListening)
        assertFalse(AircraftCameraStreamManager.cameraStreamState.value.rawStreamListening)

        AircraftCameraStreamManager.startFrameListening("NV21")
        AircraftCameraStreamManager.startRawStreamListening()

        assertTrue(fake.frameListening)
        assertTrue(fake.rawListening)
        assertTrue(AircraftCameraStreamManager.cameraStreamState.value.frameListening)
        assertTrue(AircraftCameraStreamManager.cameraStreamState.value.rawStreamListening)

        AircraftCameraStreamManager.stopFrameListening()
        AircraftCameraStreamManager.stopRawStreamListening()

        assertFalse(fake.frameListening)
        assertFalse(fake.rawListening)
    }

    private class FakeCameraStreamClient(
        private val productTypeName: String,
        private val indexes: List<DjiCameraIndex>,
        private val sourceRanges: Map<String, List<String>>,
        private val failBindNames: Set<String> = emptySet(),
    ) : DjiCameraStreamClient {
        var boundIndex: DjiCameraIndex? = null
        var boundWidth: Int = 0
        var boundHeight: Int = 0
        var unboundSurface: Any? = null
        var frameListening: Boolean = false
        var rawListening: Boolean = false
        private var callback: ((List<DjiCameraIndex>) -> Unit)? = null

        override fun init() = Unit

        override fun release() = Unit

        override fun addAvailableCameraUpdatedListener(callback: (List<DjiCameraIndex>) -> Unit) {
            this.callback = callback
        }

        override fun removeAvailableCameraUpdatedListener() {
            callback = null
        }

        override fun refreshAvailableCameraIndexes(): List<DjiCameraIndex> = indexes

        override fun readProductTypeName(): String = productTypeName

        override fun readVideoSourceRange(cameraIndex: DjiCameraIndex): List<String> {
            return sourceRanges[cameraIndex.name].orEmpty()
        }

        override fun setVideoSource(cameraIndex: DjiCameraIndex, sourceName: String): Boolean = true

        override fun bindSurface(cameraIndex: DjiCameraIndex, surface: Any, width: Int, height: Int) {
            if (failBindNames.contains(cameraIndex.name)) {
                error("bind failed for ${cameraIndex.name}")
            }
            boundIndex = cameraIndex
            boundWidth = width
            boundHeight = height
        }

        override fun unbindSurface(surface: Any) {
            unboundSurface = surface
            boundIndex = null
        }

        override fun startFrameListening(
            cameraIndex: DjiCameraIndex,
            formatName: String,
            callback: (DjiCameraFrameInfo) -> Unit,
        ) {
            frameListening = true
            callback(DjiCameraFrameInfo(width = 1920, height = 1080, frameRate = 30, formatName = formatName))
        }

        override fun stopFrameListening() {
            frameListening = false
        }

        override fun startRawStreamListening(
            cameraIndex: DjiCameraIndex,
            callback: (DjiRawStreamInfo) -> Unit,
        ) {
            rawListening = true
            callback(
                DjiRawStreamInfo(
                    width = 1920,
                    height = 1080,
                    frameRate = 30,
                    mimeType = "H264",
                    keyFrame = true,
                ),
            )
        }

        override fun stopRawStreamListening() {
            rawListening = false
        }
    }
}
