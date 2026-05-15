package com.example.uavmobile.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeveloperLogStoreTest {
    @Before
    fun setUp() {
        DeveloperLogStore.clear()
    }

    @Test
    fun `log store trims to ring buffer capacity`() {
        repeat(200) { index ->
            DeveloperLogStore.info("TestSource", "entry-$index")
        }

        val entries = DeveloperLogStore.entries.value
        assertEquals(150, entries.size)
        assertTrue(entries.first().message.startsWith("entry-50"))
        assertTrue(entries.last().message.startsWith("entry-199"))
    }

    @Test
    fun `clear removes all log entries`() {
        DeveloperLogStore.warn("TestSource", "something happened")
        DeveloperLogStore.clear()

        assertTrue(DeveloperLogStore.entries.value.isEmpty())
    }
}
