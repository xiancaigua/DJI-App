package com.example.uavmobile.dji

import android.Manifest
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiPermissionManagerTest {
    @Test
    fun `android 12 plus requires bluetooth scan and connect`() {
        val permissions = DjiPermissionManager.requiredPermissionsForSdkInt(Build.VERSION_CODES.S)

        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test
    fun `android 9 still includes legacy storage permissions`() {
        val permissions = DjiPermissionManager.requiredPermissionsForSdkInt(Build.VERSION_CODES.P)

        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue(permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        assertFalse(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertFalse(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }
}
