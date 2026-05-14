package com.example.uavmobile

import androidx.activity.ComponentActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecycleHostTest {
    @Test
    fun `main activity remains a ComponentActivity host for Compose lifecycle`() {
        assertTrue(ComponentActivity::class.java.isAssignableFrom(MainActivity::class.java))
    }
}
