package com.example.uavmobile.dji

import com.example.uavmobile.core.DjiAircraftFamily
import com.example.uavmobile.data.model.MissionWaypoint
import dji.sdk.keyvalue.value.product.ProductType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiDroneControllerTest {
    @After
    fun tearDown() {
        DjiConnectionManager.resetForTest()
        DjiMsdkManager.resetForTest()
    }

    @Test
    fun `connect refreshes key manager when sdk is registered`() = runTest {
        val reader = CountingConnectionReader(connectionValue = false)
        DjiConnectionManager.setConnectionReaderForTest(reader)
        DjiMsdkManager.setInitStateForTest(DjiSdkInitState.REGISTERED)

        val result = DjiDroneController.connect(null)

        assertTrue(result.success)
        assertTrue(reader.readConnectionCalls > 0)
        assertTrue(DjiConnectionManager.connectionState.value.monitorRunning)
    }

    @Test
    fun `upload mission is blocked when aircraft is not connected`() = runTest {
        DjiConnectionManager.setConnectionReaderForTest(CountingConnectionReader(connectionValue = false))
        DjiMsdkManager.setInitStateForTest(DjiSdkInitState.REGISTERED)

        val result = DjiDroneController.uploadMission(
            missionId = "mission-1",
            waypoints = listOf(MissionWaypoint(31.0, 121.0, 30f, 0f, 0f)),
            selectedDjiAircraftFamily = DjiAircraftFamily.M400,
        )

        assertFalse(result.success)
        assertTrue(result.message.contains("当前没有连接 DJI 飞机"))
    }

    private class CountingConnectionReader(
        private val connectionValue: Boolean?,
    ) : DjiProductConnectionReader {
        var readConnectionCalls = 0

        override fun readConnection(): Boolean? {
            readConnectionCalls += 1
            return connectionValue
        }

        override fun readProductType(): ProductType? = null

        override fun listenConnection(holder: Any, getOnce: Boolean, onValueChange: (Boolean?) -> Unit) = Unit

        override fun cancelListen(holder: Any) = Unit
    }
}
