package com.example.uavmobile.ui.viewmodel

import com.example.uavmobile.MainDispatcherRule
import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.core.DroneBackend
import com.example.uavmobile.core.DroneController
import com.example.uavmobile.core.DroneState
import com.example.uavmobile.core.Waypoint
import com.example.uavmobile.data.model.ActionResult
import com.example.uavmobile.data.model.ConnectionConfig
import com.example.uavmobile.data.repository.UavRepository
import com.example.uavmobile.dji.DjiConnectionManager
import com.example.uavmobile.dji.AircraftCameraStreamManager
import com.example.uavmobile.dji.DjiCameraFrameInfo
import com.example.uavmobile.dji.DjiCameraIndex
import com.example.uavmobile.dji.DjiCameraStreamClient
import com.example.uavmobile.dji.DjiProductConnectionReader
import com.example.uavmobile.dji.DjiRawStreamInfo
import com.example.uavmobile.dji.DjiPermissionSnapshot
import dji.sdk.keyvalue.value.product.ProductType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UavViewModelRoutingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        AircraftCameraStreamManager.resetForTest()
        DjiConnectionManager.resetForTest()
    }

    @Test
    fun `ros backend mission actions route to self controller`() = runTest {
        val selfController = FakeDroneController("self")
        val djiController = FakeDroneController("dji")
        val viewModel = UavViewModel(
            repository = UavRepository(),
            selfDroneController = selfController,
            djiDroneController = djiController,
        )
        try {

            viewModel.onActiveBackendChanged(DroneBackend.SELF_ROS)
            viewModel.selectMission("ros-mission-1")

            viewModel.uploadDraftMission()
            viewModel.startMission()
            viewModel.pauseMission()
            viewModel.resumeMission()
            viewModel.rtl()
            viewModel.land()
            runCurrent()

            assertEquals(1, selfController.uploadMissionCalls)
            assertEquals(1, selfController.startMissionCalls)
            assertEquals(1, selfController.pauseMissionCalls)
            assertEquals(1, selfController.resumeMissionCalls)
            assertEquals(1, selfController.returnHomeCalls)
            assertEquals(1, selfController.landCalls)
            assertEquals(0, djiController.uploadMissionCalls)
            assertEquals(0, djiController.startMissionCalls)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun `dji backend mission actions route to dji controller`() = runTest {
        DjiConnectionManager.setConnectionReaderForTest(FakeConnectionReader(connectionValue = true))
        DjiConnectionManager.refreshFromKeyManager("routing test")
        val selfController = FakeDroneController("self")
        val djiController = FakeDroneController("dji")
        val viewModel = UavViewModel(
            repository = UavRepository(),
            selfDroneController = selfController,
            djiDroneController = djiController,
        )
        try {

            viewModel.onDjiPermissionStateChanged(
                DjiPermissionSnapshot(
                    requiredPermissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
                    missingPermissions = emptyList(),
                ),
            )
            viewModel.onActiveBackendChanged(DroneBackend.DJI)
            viewModel.onSelectedDjiAircraftFamilyChanged(DjiAircraftFamily.M400)
            viewModel.selectMission("dji-mission-1")

            viewModel.uploadDraftMission()
            viewModel.startMission()
            viewModel.pauseMission()
            viewModel.stopMission()
            viewModel.rtl()
            viewModel.land()
            runCurrent()

            assertEquals(0, selfController.uploadMissionCalls)
            assertEquals(1, djiController.uploadMissionCalls)
            assertEquals(1, djiController.startMissionCalls)
            assertEquals(1, djiController.pauseMissionCalls)
            assertEquals(1, djiController.stopMissionCalls)
            assertEquals(1, djiController.returnHomeCalls)
            assertEquals(1, djiController.landCalls)
            assertEquals(DjiAircraftFamily.M400, djiController.lastSelectedDjiAircraftFamily)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun `entering dji control page initializes camera stream`() = runTest {
        val fakeCamera = FakeCameraStreamClient(
            productTypeName = "DJI_MATRICE_400",
            indexes = listOf(DjiCameraIndex("FPV", 7)),
            sourceRanges = mapOf("FPV" to listOf("DEFAULT_CAMERA")),
        )
        AircraftCameraStreamManager.setClientForTest(fakeCamera)
        val viewModel = UavViewModel(
            repository = UavRepository(),
            selfDroneController = FakeDroneController("self"),
            djiDroneController = FakeDroneController("dji"),
        )
        try {
            viewModel.onActiveBackendChanged(DroneBackend.DJI)
            viewModel.onCameraPreviewEntered()
            runCurrent()

            assertEquals(1, fakeCamera.initCalls)
            assertEquals(1, fakeCamera.listenerAdds)
            assertEquals("FPV", viewModel.uiState.value.cameraStream.currentCameraIndexName)
        } finally {
            viewModel.clearForTest()
        }
    }

    @Test
    fun `dji start mission warns when video unavailable but does not block start`() = runTest {
        DjiConnectionManager.setConnectionReaderForTest(FakeConnectionReader(connectionValue = true))
        DjiConnectionManager.refreshFromKeyManager("routing test")
        val djiController = FakeDroneController("dji")
        val viewModel = UavViewModel(
            repository = UavRepository(),
            selfDroneController = FakeDroneController("self"),
            djiDroneController = djiController,
        )
        try {
            viewModel.onDjiPermissionStateChanged(
                DjiPermissionSnapshot(
                    requiredPermissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
                    missingPermissions = emptyList(),
                ),
            )
            viewModel.onActiveBackendChanged(DroneBackend.DJI)

            viewModel.startMission()
            runCurrent()

            assertEquals(1, djiController.startMissionCalls)
            assertTrue(viewModel.uiState.value.cameraStream.warningMessage.contains("视频"))
        } finally {
            viewModel.clearForTest()
        }
    }

    private class FakeDroneController(
        private val name: String,
    ) : DroneController {
        var uploadMissionCalls = 0
        var startMissionCalls = 0
        var pauseMissionCalls = 0
        var resumeMissionCalls = 0
        var stopMissionCalls = 0
        var returnHomeCalls = 0
        var landCalls = 0
        var lastSelectedDjiAircraftFamily: DjiAircraftFamily? = null

        override suspend fun connect(connectionConfig: ConnectionConfig?): ActionResult {
            return ActionResult(true, "$name connect")
        }

        override suspend fun disconnect(): ActionResult {
            return ActionResult(true, "$name disconnect")
        }

        override fun getState(): Result<DroneState> {
            return Result.success(DroneState(backend = DroneBackend.SELF_ROS))
        }

        override suspend fun refreshMissions(): ActionResult {
            return ActionResult(true, "$name refresh")
        }

        override suspend fun uploadMission(
            missionId: String,
            waypoints: List<Waypoint>,
            selectedDjiAircraftFamily: DjiAircraftFamily,
        ): ActionResult {
            uploadMissionCalls += 1
            lastSelectedDjiAircraftFamily = selectedDjiAircraftFamily
            return ActionResult(true, "$name upload $missionId with ${waypoints.size} waypoints")
        }

        override suspend fun startMission(missionId: String?): ActionResult {
            startMissionCalls += 1
            return ActionResult(true, "$name start ${missionId.orEmpty()}")
        }

        override suspend fun pauseMission(missionId: String?): ActionResult {
            pauseMissionCalls += 1
            return ActionResult(true, "$name pause ${missionId.orEmpty()}")
        }

        override suspend fun resumeMission(missionId: String?): ActionResult {
            resumeMissionCalls += 1
            return ActionResult(true, "$name resume ${missionId.orEmpty()}")
        }

        override suspend fun stopMission(missionId: String?): ActionResult {
            stopMissionCalls += 1
            return ActionResult(true, "$name stop ${missionId.orEmpty()}")
        }

        override suspend fun returnHome(missionId: String?): ActionResult {
            returnHomeCalls += 1
            return ActionResult(true, "$name rtl ${missionId.orEmpty()}")
        }

        override suspend fun land(missionId: String?): ActionResult {
            landCalls += 1
            return ActionResult(true, "$name land ${missionId.orEmpty()}")
        }
    }

    private class FakeConnectionReader(
        private val connectionValue: Boolean?,
    ) : DjiProductConnectionReader {
        override fun readConnection(): Boolean? = connectionValue

        override fun readProductType(): ProductType? = null

        override fun listenConnection(holder: Any, getOnce: Boolean, onValueChange: (Boolean?) -> Unit) = Unit

        override fun cancelListen(holder: Any) = Unit
    }

    private class FakeCameraStreamClient(
        private val productTypeName: String,
        private val indexes: List<DjiCameraIndex>,
        private val sourceRanges: Map<String, List<String>>,
    ) : DjiCameraStreamClient {
        var initCalls = 0
        var listenerAdds = 0

        override fun init() {
            initCalls += 1
        }

        override fun release() = Unit

        override fun addAvailableCameraUpdatedListener(callback: (List<DjiCameraIndex>) -> Unit) {
            listenerAdds += 1
        }

        override fun removeAvailableCameraUpdatedListener() = Unit

        override fun refreshAvailableCameraIndexes(): List<DjiCameraIndex> = indexes

        override fun readProductTypeName(): String = productTypeName

        override fun readVideoSourceRange(cameraIndex: DjiCameraIndex): List<String> {
            return sourceRanges[cameraIndex.name].orEmpty()
        }

        override fun setVideoSource(cameraIndex: DjiCameraIndex, sourceName: String): Boolean = true

        override fun bindSurface(cameraIndex: DjiCameraIndex, surface: Any, width: Int, height: Int) = Unit

        override fun unbindSurface(surface: Any) = Unit

        override fun startFrameListening(
            cameraIndex: DjiCameraIndex,
            formatName: String,
            callback: (DjiCameraFrameInfo) -> Unit,
        ) = Unit

        override fun stopFrameListening() = Unit

        override fun startRawStreamListening(
            cameraIndex: DjiCameraIndex,
            callback: (DjiRawStreamInfo) -> Unit,
        ) = Unit

        override fun stopRawStreamListening() = Unit
    }
}
