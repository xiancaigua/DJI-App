package com.example.uavmobile.dji

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import com.example.uavmobile.BuildConfig
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DjiSdkInitState {
    IDLE,
    SKIPPED,
    INITIALIZING,
    READY_TO_REGISTER,
    REGISTERING,
    REGISTERED,
    FAILED,
}

object DjiMsdkManager {
    private const val TAG = "DjiMsdkManager"

    private val _initState = MutableStateFlow(DjiSdkInitState.IDLE)
    private val _statusMessage = MutableStateFlow("DJI MSDK not initialized")

    val initState: StateFlow<DjiSdkInitState> = _initState.asStateFlow()
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val appContextRef = AtomicReference<Context?>()
    private val runtimeBlockedReason = AtomicReference<String?>(null)
    private val initRequested = AtomicBoolean(false)
    private val sdkInitComplete = AtomicBoolean(false)
    private val registerInFlight = AtomicBoolean(false)
    private val networkCallbackRegistered = AtomicBoolean(false)

    fun init(context: Context) {
        appContextRef.set(context.applicationContext)
        runtimeBlockedReason.set(detectBlockedRuntimeReason())

        if (runtimeBlockedReason.get() != null) {
            _initState.value = DjiSdkInitState.SKIPPED
            _statusMessage.value = runtimeBlockedReason.get().orEmpty()
            Log.i(TAG, _statusMessage.value)
            return
        }

        if (!BuildConfig.DJI_ENABLE_RUNTIME) {
            _initState.value = DjiSdkInitState.SKIPPED
            _statusMessage.value = "DJI runtime disabled by configuration"
            Log.i(TAG, _statusMessage.value)
            return
        }

        registerNetworkRecoveryCallback(context.applicationContext)

        if (!isSdkPresent()) {
            _initState.value = DjiSdkInitState.FAILED
            _statusMessage.value = "DJI MSDK classes are not available in the current build"
            Log.e(TAG, _statusMessage.value)
            return
        }

        if (initRequested.getAndSet(true)) {
            if (sdkInitComplete.get() && _initState.value != DjiSdkInitState.REGISTERED) {
                retryRegisterIfNeeded("Application requested init again")
            }
            return
        }

        _initState.value = DjiSdkInitState.INITIALIZING
        _statusMessage.value = "Initializing DJI MSDK"
        Log.i(TAG, _statusMessage.value)

        val callback = object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                val eventText = event?.name ?: "UNKNOWN"
                _statusMessage.value = "Initializing DJI MSDK: $eventText ($totalProcess)"
                Log.i(TAG, _statusMessage.value)

                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    sdkInitComplete.set(true)
                    _initState.value = DjiSdkInitState.READY_TO_REGISTER
                    _statusMessage.value = "DJI MSDK initialized, registering app"
                    retryRegisterIfNeeded("SDK init complete")
                }
            }

            override fun onRegisterSuccess() {
                registerInFlight.set(false)
                _initState.value = DjiSdkInitState.REGISTERED
                _statusMessage.value = "DJI registerApp succeeded, waiting for product connection"
                Log.i(TAG, _statusMessage.value)
            }

            override fun onRegisterFailure(error: IDJIError?) {
                registerInFlight.set(false)
                _initState.value = DjiSdkInitState.FAILED
                _statusMessage.value = "DJI registerApp failed: ${DjiErrorFormatter.describe(error)}"
                Log.e(TAG, _statusMessage.value)
            }

            override fun onProductConnect(productId: Int) {
                DjiConnectionManager.onProductConnected(productId)
                Log.i(TAG, "DJI product connected: productId=$productId")
            }

            override fun onProductDisconnect(productId: Int) {
                DjiConnectionManager.onProductDisconnected(productId)
                Log.w(TAG, "DJI product disconnected: productId=$productId")
            }

            override fun onProductChanged(productId: Int) {
                DjiConnectionManager.onProductChanged(productId)
                Log.i(TAG, "DJI product changed: productId=$productId")
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                if (total > 0L) {
                    Log.i(TAG, "DJI database download progress: $current/$total")
                }
            }
        }

        runCatching {
            SDKManager.getInstance().init(context.applicationContext, callback)
        }.onFailure { throwable ->
            _initState.value = DjiSdkInitState.FAILED
            _statusMessage.value = "DJI init() failed: ${throwable.message}"
            Log.e(TAG, _statusMessage.value, throwable)
        }
    }

    fun retryRegisterIfNeeded(reason: String = "manual retry") {
        if (!BuildConfig.DJI_ENABLE_RUNTIME || runtimeBlockedReason.get() != null) {
            return
        }
        if (!sdkInitComplete.get()) {
            return
        }
        if (_initState.value == DjiSdkInitState.REGISTERED || registerInFlight.get()) {
            return
        }

        registerInFlight.set(true)
        _initState.value = DjiSdkInitState.REGISTERING
        _statusMessage.value = "Calling registerApp(): $reason"
        Log.i(TAG, _statusMessage.value)

        runCatching {
            SDKManager.getInstance().registerApp()
        }.onFailure { throwable ->
            registerInFlight.set(false)
            _initState.value = DjiSdkInitState.FAILED
            _statusMessage.value = "registerApp() invocation failed: ${throwable.message}"
            Log.e(TAG, _statusMessage.value, throwable)
        }
    }

    fun isSdkPresent(): Boolean {
        if (!BuildConfig.DJI_ENABLE_RUNTIME || runtimeBlockedReason.get() != null) {
            return false
        }
        return runCatching {
            KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)
            KeyTools.createKey(ProductKey.KeyConnection)
            LocationCoordinate3D()
            SDKManager.getInstance()
        }.isSuccess
    }

    fun isWpmzPresent(): Boolean {
        if (!BuildConfig.DJI_ENABLE_RUNTIME || runtimeBlockedReason.get() != null) {
            return false
        }
        return runCatching {
            Class.forName("dji.sdk.wpmz.value.mission.WaylineMission")
            Class.forName("dji.sdk.wpmz.value.mission.WaylineMissionConfig")
        }.isSuccess
    }

    fun isMissionFeatureAvailable(): Boolean {
        return isSdkPresent() && isWpmzPresent()
    }

    fun describeStatus(): String = statusMessage.value

    fun requireAppContext(): Context {
        return appContextRef.get() ?: error("Application context is not available yet")
    }

    fun isProbablyVirtualDevice(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("vbox") ||
            model.contains("android sdk built for") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            manufacturer.contains("genymotion") ||
            brand.startsWith("generic") ||
            device.startsWith("generic") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("vbox") ||
            product.contains("sdk") ||
            product.contains("emulator")
    }

    private fun detectBlockedRuntimeReason(): String? {
        if (isProbablyVirtualDevice()) {
            return "Running on a virtual device, DJI runtime init is skipped"
        }
        return null
    }

    private fun registerNetworkRecoveryCallback(context: Context) {
        if (!networkCallbackRegistered.compareAndSet(false, true)) {
            return
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

        runCatching {
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (sdkInitComplete.get() && _initState.value != DjiSdkInitState.REGISTERED) {
                        retryRegisterIfNeeded("Network available")
                    }
                }
            })
        }.onFailure { throwable ->
            networkCallbackRegistered.set(false)
            Log.w(TAG, "Unable to register network recovery callback: ${throwable.message}")
        }
    }
}
