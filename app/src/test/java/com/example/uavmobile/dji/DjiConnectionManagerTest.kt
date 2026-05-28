package com.example.uavmobile.dji

import com.example.uavmobile.MainDispatcherRule
import dji.sdk.keyvalue.value.product.ProductType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DjiConnectionManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        DjiConnectionManager.resetForTest()
    }

    @Test
    fun `refreshFromKeyManager returns disconnected snapshot when key manager is unavailable`() {
        DjiConnectionManager.setConnectionReaderForTest(
            FakeConnectionReader(connectionFailure = "DJI KeyManager 不可用"),
        )

        val snapshot = DjiConnectionManager.refreshFromKeyManager("unit test unavailable")

        assertFalse(snapshot.connected)
        assertNull(snapshot.keyConnectionValue)
        assertFalse(snapshot.lastRefreshSucceeded)
        assertEquals("DJI KeyManager 不可用", snapshot.lastRefreshError)
    }

    @Test
    fun `sdk callback connected with null key connection keeps effective connected while waiting sync`() {
        DjiConnectionManager.setConnectionReaderForTest(
            FakeConnectionReader(connectionValue = null, productType = null),
        )

        DjiConnectionManager.onProductConnected(101)

        val snapshot = DjiConnectionManager.connectionState.value
        assertTrue(snapshot.connected)
        assertTrue(snapshot.effectiveConnected)
        assertEquals(true, snapshot.callbackConnected)
        assertNull(snapshot.keyConnectionValue)
        assertTrue(snapshot.statusMessage.contains("waiting for ProductKey.KeyConnection synchronization"))
    }

    @Test
    fun `key connection false overrides previous connected callback`() {
        val reader = FakeConnectionReader(connectionValue = null, productType = null)
        DjiConnectionManager.setConnectionReaderForTest(reader)

        DjiConnectionManager.onProductConnected(102)
        reader.connectionValue = false

        val snapshot = DjiConnectionManager.refreshFromKeyManager("unit test false wins")

        assertFalse(snapshot.connected)
        assertFalse(snapshot.effectiveConnected)
        assertEquals(true, snapshot.callbackConnected)
        assertEquals(false, snapshot.keyConnectionValue)
        assertTrue(snapshot.statusMessage.contains("KeyConnection=false"))
    }

    @Test
    fun `key connection true with null product type still reports connected`() {
        DjiConnectionManager.setConnectionReaderForTest(
            FakeConnectionReader(connectionValue = true, productType = null),
        )

        val snapshot = DjiConnectionManager.refreshFromKeyManager("unit test product type not ready")

        assertTrue(snapshot.connected)
        assertTrue(snapshot.effectiveConnected)
        assertEquals(true, snapshot.keyConnectionValue)
        assertNull(snapshot.productType)
        assertTrue(snapshot.statusMessage.contains("ProductType not ready yet"))
    }

    @Test
    fun `monitor start is idempotent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        DjiConnectionManager.setMonitorDispatcherForTest(dispatcher)
        DjiConnectionManager.setConnectionReaderForTest(FakeConnectionReader(connectionValue = false))

        DjiConnectionManager.startConnectionMonitor("first")
        val firstStartedAt = DjiConnectionManager.connectionState.value.monitorStartedAt
        DjiConnectionManager.startConnectionMonitor("second")

        assertTrue(DjiConnectionManager.connectionState.value.monitorRunning)
        assertEquals(firstStartedAt, DjiConnectionManager.connectionState.value.monitorStartedAt)
        DjiConnectionManager.stopConnectionMonitor("unit test complete")
    }

    @Test
    fun `monitor polling updates connection when KeyConnection becomes true`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reader = FakeConnectionReader(connectionValue = false)
        DjiConnectionManager.setMonitorDispatcherForTest(dispatcher)
        DjiConnectionManager.setConnectionReaderForTest(reader)

        DjiConnectionManager.startConnectionMonitor("poll test")
        runCurrent()
        assertFalse(DjiConnectionManager.connectionState.value.connected)

        reader.connectionValue = true
        advanceTimeBy(1_000)
        runCurrent()

        val snapshot = DjiConnectionManager.connectionState.value
        assertTrue(snapshot.connected)
        assertEquals(true, snapshot.keyConnectionValue)
        assertEquals(DjiConnectionSource.CONNECTION_MONITOR_POLL, snapshot.lastConnectionSource)
        DjiConnectionManager.stopConnectionMonitor("unit test complete")
    }

    private class FakeConnectionReader(
        var connectionValue: Boolean? = null,
        var productType: ProductType? = null,
        private val connectionFailure: String? = null,
    ) : DjiProductConnectionReader {
        override fun readConnection(): Boolean? {
            connectionFailure?.let { error(it) }
            return connectionValue
        }

        override fun readProductType(): ProductType? = productType

        override fun listenConnection(holder: Any, getOnce: Boolean, onValueChange: (Boolean?) -> Unit) = Unit

        override fun cancelListen(holder: Any) = Unit
    }
}
