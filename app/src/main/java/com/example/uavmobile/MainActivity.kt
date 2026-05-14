package com.example.uavmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner as PlatformLocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner as LifecycleComposeLocalLifecycleOwner
import com.example.uavmobile.dji.DjiPermissionManager
import com.example.uavmobile.ui.screen.UavApp
import com.example.uavmobile.ui.theme.Px4MobileTheme
import com.example.uavmobile.ui.viewmodel.UavViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: UavViewModel by viewModels()

    private val requestDjiPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            syncDjiPermissionState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verifyLifecycleComposeHost()
        syncDjiPermissionState()
        setContent {
            val lifecycleOwner = PlatformLocalLifecycleOwner.current
            CompositionLocalProvider(
                LifecycleComposeLocalLifecycleOwner provides lifecycleOwner,
            ) {
                Px4MobileTheme {
                    Surface {
                        UavApp(
                            viewModel = viewModel,
                            onRequestDjiPermissions = ::requestDjiPermissions,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncDjiPermissionState()
    }

    private fun requestDjiPermissions() {
        val snapshot = DjiPermissionManager.evaluate(this)
        viewModel.onDjiPermissionStateChanged(snapshot)
        if (snapshot.allGranted) {
            return
        }
        requestDjiPermissionsLauncher.launch(snapshot.missingPermissions.toTypedArray())
    }

    private fun syncDjiPermissionState() {
        viewModel.onDjiPermissionStateChanged(DjiPermissionManager.evaluate(this))
    }

    private fun verifyLifecycleComposeHost() {
        check(this is LifecycleOwner) {
            "MainActivity must provide LifecycleOwner for collectAsStateWithLifecycle"
        }
    }
}
