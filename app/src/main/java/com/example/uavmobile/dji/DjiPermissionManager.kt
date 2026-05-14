package com.example.uavmobile.dji

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class DjiPermissionSnapshot(
    val requiredPermissions: List<String>,
    val missingPermissions: List<String>,
) {
    val allGranted: Boolean
        get() = missingPermissions.isEmpty()

    val statusMessage: String
        get() = if (allGranted) {
            "DJI runtime permissions granted"
        } else {
            "Missing DJI permissions: ${missingPermissions.joinToString()}"
        }
}

object DjiPermissionManager {
    fun evaluate(context: Context): DjiPermissionSnapshot {
        val requiredPermissions = requiredPermissionsForSdkInt(Build.VERSION.SDK_INT)
        val missingPermissions = requiredPermissions.filterNot { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        return DjiPermissionSnapshot(requiredPermissions, missingPermissions)
    }

    fun evaluateGrantedPermissions(
        sdkInt: Int,
        grantedPermissions: Set<String>,
    ): DjiPermissionSnapshot {
        val requiredPermissions = requiredPermissionsForSdkInt(sdkInt)
        val missingPermissions = requiredPermissions.filterNot(grantedPermissions::contains)
        return DjiPermissionSnapshot(requiredPermissions, missingPermissions)
    }

    fun requiredPermissionsForSdkInt(sdkInt: Int): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        if (sdkInt >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (sdkInt <= Build.VERSION_CODES.S_V2) {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (sdkInt <= Build.VERSION_CODES.P) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        return permissions.distinct()
    }
}
