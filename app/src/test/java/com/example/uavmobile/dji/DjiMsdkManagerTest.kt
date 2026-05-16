package com.example.uavmobile.dji

import dji.sdk.keyvalue.value.product.ProductType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiMsdkManagerTest {
    @After
    fun tearDown() {
        DjiConnectionManager.resetForTest()
        DjiMsdkManager.resetForTest()
    }

    @Test
    fun `registerApp success refreshes key manager and starts connection monitor`() {
        val reader = CountingConnectionReader(connectionValue = false)
        DjiConnectionManager.setConnectionReaderForTest(reader)

        DjiMsdkManager.handleRegisterSuccess("unit test")

        val snapshot = DjiConnectionManager.connectionState.value
        assertEquals(DjiSdkInitState.REGISTERED, DjiMsdkManager.initState.value)
        assertTrue(snapshot.monitorRunning)
        assertEquals(false, snapshot.keyConnectionValue)
        assertTrue(reader.readConnectionCalls > 0)
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
