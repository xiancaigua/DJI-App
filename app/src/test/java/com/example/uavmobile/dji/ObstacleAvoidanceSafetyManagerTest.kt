package com.example.uavmobile.dji

import com.example.uavmobile.core.ObstacleAvoidanceMode
import com.example.uavmobile.core.ObstacleAvoidanceSwitchState
import com.example.uavmobile.core.ObstacleDirection
import com.example.uavmobile.core.ObstacleSafetyState
import com.example.uavmobile.data.model.ActionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObstacleAvoidanceSafetyManagerTest {
    @After
    fun tearDown() {
        ObstacleAvoidanceSafetyManager.resetForTest()
        DjiConnectionManager.resetForTest()
    }

    @Test
    fun `prepare sets BRAKE when current mode is CLOSE`() = runTest {
        val client = FakePerceptionClient(mode = ObstacleAvoidanceMode.CLOSE)
        ObstacleAvoidanceSafetyManager.setPerceptionClientForTest(client)

        val result = ObstacleAvoidanceSafetyManager.prepareForWaypointMission(
            missionId = "mission-1",
            missionState = "READY",
        )

        assertTrue(result.success)
        assertEquals(listOf(ObstacleAvoidanceMode.BRAKE), client.setModeCalls)
        assertEquals(ObstacleAvoidanceMode.BRAKE, result.snapshot.mode)
    }

    @Test
    fun `prepare blocks mission when CLOSE cannot be changed to BRAKE`() = runTest {
        val client = FakePerceptionClient(mode = ObstacleAvoidanceMode.CLOSE).apply {
            failSetMode = true
        }
        ObstacleAvoidanceSafetyManager.setPerceptionClientForTest(client)

        val result = ObstacleAvoidanceSafetyManager.prepareForWaypointMission(
            missionId = "mission-1",
            missionState = "READY",
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("避障未开启"))
    }

    @Test
    fun `prepare allows mission with BRAKE and partial unsupported direction warnings`() = runTest {
        val client = FakePerceptionClient(mode = ObstacleAvoidanceMode.BRAKE).apply {
            failGetSwitchDirections += ObstacleDirection.UPWARD
            failSetWarningDirections += ObstacleDirection.DOWNWARD
        }
        ObstacleAvoidanceSafetyManager.setPerceptionClientForTest(client)

        val result = ObstacleAvoidanceSafetyManager.prepareForWaypointMission(
            missionId = "mission-1",
            missionState = "READY",
        )

        assertTrue(result.success)
        assertTrue(result.warnings.isNotEmpty())
        assertEquals(ObstacleAvoidanceMode.BRAKE, result.snapshot.mode)
        assertEquals(ObstacleAvoidanceSwitchState.UNSUPPORTED, result.snapshot.upwardSwitch)
    }

    @Test
    fun `obstacle listener converts millimeters and updates warning state`() = runTest {
        val client = FakePerceptionClient(mode = ObstacleAvoidanceMode.BRAKE)
        ObstacleAvoidanceSafetyManager.setPerceptionClientForTest(client)

        ObstacleAvoidanceSafetyManager.startMissionMonitoring(
            missionId = "mission-1",
            isMissionExecuting = { true },
            pauseAction = { ActionResult(true, "paused") },
            onSnapshotUpdate = {},
        )

        client.emit(
            DjiObstacleDataReading(
                horizontalObstacleDistanceMillimeters = listOf(5200, 9000),
                upwardObstacleDistanceMillimeters = 8000,
                downwardObstacleDistanceMillimeters = 10_000,
                horizontalAngleIntervalDegrees = 10,
            ),
        )

        val snapshot = ObstacleAvoidanceSafetyManager.safetyState.value
        assertEquals(5.2, snapshot.nearestObstacleDistanceMeters ?: 0.0, 0.001)
        assertEquals(ObstacleDirection.HORIZONTAL, snapshot.nearestObstacleDirection)
        assertEquals(ObstacleSafetyState.WARNING, snapshot.safetyState)
    }

    @Test
    fun `emergency obstacle pauses once during debounce window`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakePerceptionClient(mode = ObstacleAvoidanceMode.BRAKE)
        var pauseCalls = 0
        var now = 100_000L
        ObstacleAvoidanceSafetyManager.setMonitorDispatcherForTest(dispatcher)
        ObstacleAvoidanceSafetyManager.setClockForTest { now }
        ObstacleAvoidanceSafetyManager.setPerceptionClientForTest(client)

        ObstacleAvoidanceSafetyManager.startMissionMonitoring(
            missionId = "mission-1",
            isMissionExecuting = { true },
            pauseAction = {
                pauseCalls += 1
                ActionResult(true, "paused")
            },
            onSnapshotUpdate = {},
        )

        repeat(3) {
            client.emit(DjiObstacleDataReading(horizontalObstacleDistanceMillimeters = listOf(2500)))
        }
        runCurrent()
        assertEquals(1, pauseCalls)

        repeat(3) {
            client.emit(DjiObstacleDataReading(horizontalObstacleDistanceMillimeters = listOf(2400)))
        }
        runCurrent()
        assertEquals(1, pauseCalls)

        now += ObstacleAvoidanceSafetyManager.PAUSE_DEBOUNCE_MS + 1
        repeat(3) {
            client.emit(DjiObstacleDataReading(horizontalObstacleDistanceMillimeters = listOf(2300)))
        }
        runCurrent()
        assertEquals(2, pauseCalls)
    }

    @Test
    fun `stop monitoring removes obstacle listener`() = runTest {
        val client = FakePerceptionClient(mode = ObstacleAvoidanceMode.BRAKE)
        ObstacleAvoidanceSafetyManager.setPerceptionClientForTest(client)

        ObstacleAvoidanceSafetyManager.startMissionMonitoring(
            missionId = "mission-1",
            isMissionExecuting = { true },
            pauseAction = { ActionResult(true, "paused") },
            onSnapshotUpdate = {},
        )
        assertEquals(1, client.listenerCount)

        ObstacleAvoidanceSafetyManager.stopMissionMonitoring("unit test")

        assertEquals(0, client.listenerCount)
        assertFalse(ObstacleAvoidanceSafetyManager.safetyState.value.monitoringActive)
    }

    private class FakePerceptionClient(
        var mode: ObstacleAvoidanceMode,
    ) : DjiPerceptionClient {
        var failSetMode = false
        val setModeCalls = mutableListOf<ObstacleAvoidanceMode>()
        val failGetSwitchDirections = mutableSetOf<ObstacleDirection>()
        val failSetWarningDirections = mutableSetOf<ObstacleDirection>()
        private val listeners = mutableListOf<DjiObstacleDataCallback>()
        val listenerCount: Int
            get() = listeners.size
        private val switches = mutableMapOf(
            ObstacleDirection.HORIZONTAL to true,
            ObstacleDirection.UPWARD to true,
            ObstacleDirection.DOWNWARD to true,
        )

        override suspend fun getObstacleAvoidanceType(): ObstacleAvoidanceMode = mode

        override suspend fun setObstacleAvoidanceType(mode: ObstacleAvoidanceMode) {
            setModeCalls += mode
            if (failSetMode) {
                error("setObstacleAvoidanceType failed")
            }
            this.mode = mode
        }

        override suspend fun getObstacleAvoidanceEnabled(direction: ObstacleDirection): Boolean {
            if (direction in failGetSwitchDirections) {
                error("getObstacleAvoidanceEnabled failed: $direction")
            }
            return switches[direction] ?: false
        }

        override suspend fun setObstacleAvoidanceEnabled(direction: ObstacleDirection, enabled: Boolean) {
            switches[direction] = enabled
        }

        override suspend fun getObstacleAvoidanceWarningDistance(direction: ObstacleDirection): Double = 10.0

        override suspend fun setObstacleAvoidanceWarningDistance(
            direction: ObstacleDirection,
            distanceMeters: Double,
        ) {
            if (direction in failSetWarningDirections) {
                error("setObstacleAvoidanceWarningDistance failed: $direction")
            }
        }

        override suspend fun getObstacleAvoidanceBrakingDistance(direction: ObstacleDirection): Double = 6.0

        override suspend fun setObstacleAvoidanceBrakingDistance(
            direction: ObstacleDirection,
            distanceMeters: Double,
        ) = Unit

        override fun addObstacleDataListener(callback: DjiObstacleDataCallback) {
            listeners += callback
        }

        override fun removeObstacleDataListener(callback: DjiObstacleDataCallback) {
            listeners -= callback
        }

        fun emit(reading: DjiObstacleDataReading) {
            listeners.toList().forEach { it.onUpdate(reading) }
        }
    }
}
