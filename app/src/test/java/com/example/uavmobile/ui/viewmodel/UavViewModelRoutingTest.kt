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
import com.example.uavmobile.dji.DjiPermissionSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UavViewModelRoutingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `ros backend mission actions route to self controller`() = runTest {
        val selfController = FakeDroneController("self")
        val djiController = FakeDroneController("dji")
        val viewModel = UavViewModel(
            repository = UavRepository(),
            selfDroneController = selfController,
            djiDroneController = djiController,
        )

        viewModel.onActiveBackendChanged(DroneBackend.SELF_ROS)
        viewModel.selectMission("ros-mission-1")

        viewModel.uploadDraftMission()
        viewModel.startMission()
        viewModel.pauseMission()
        viewModel.resumeMission()
        viewModel.rtl()
        viewModel.land()
        advanceUntilIdle()

        assertEquals(1, selfController.uploadMissionCalls)
        assertEquals(1, selfController.startMissionCalls)
        assertEquals(1, selfController.pauseMissionCalls)
        assertEquals(1, selfController.resumeMissionCalls)
        assertEquals(1, selfController.returnHomeCalls)
        assertEquals(1, selfController.landCalls)
        assertEquals(0, djiController.uploadMissionCalls)
        assertEquals(0, djiController.startMissionCalls)
    }

    @Test
    fun `dji backend mission actions route to dji controller`() = runTest {
        val selfController = FakeDroneController("self")
        val djiController = FakeDroneController("dji")
        val viewModel = UavViewModel(
            repository = UavRepository(),
            selfDroneController = selfController,
            djiDroneController = djiController,
        )

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
        advanceUntilIdle()

        assertEquals(0, selfController.uploadMissionCalls)
        assertEquals(1, djiController.uploadMissionCalls)
        assertEquals(1, djiController.startMissionCalls)
        assertEquals(1, djiController.pauseMissionCalls)
        assertEquals(1, djiController.stopMissionCalls)
        assertEquals(1, djiController.returnHomeCalls)
        assertEquals(1, djiController.landCalls)
        assertEquals(DjiAircraftFamily.M400, djiController.lastSelectedDjiAircraftFamily)
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
}
