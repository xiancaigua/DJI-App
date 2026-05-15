package com.example.uavmobile.dji

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.example.uavmobile.BuildConfig
import com.example.uavmobile.debug.DeveloperLogStore
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
        runtimeBlockedReason.set(DjiRuntimeEnvironment.skipReason())

        if (runtimeBlockedReason.get() != null) {
            _initState.value = DjiSdkInitState.SKIPPED
            _statusMessage.value = runtimeBlockedReason.get().orEmpty()
            Log.i(TAG, _statusMessage.value)
            DeveloperLogStore.warn(TAG, "DJI runtime skipped", _statusMessage.value)
            return
        }

        if (!BuildConfig.DJI_ENABLE_RUNTIME) {
            _initState.value = DjiSdkInitState.SKIPPED
            _statusMessage.value = "DJI runtime disabled by configuration"
            Log.i(TAG, _statusMessage.value)
            DeveloperLogStore.warn(TAG, "DJI runtime disabled", _statusMessage.value)
            return
        }

        registerNetworkRecoveryCallback(context.applicationContext)

        if (initRequested.getAndSet(true)) {
            if (sdkInitComplete.get() && _initState.value != DjiSdkInitState.REGISTERED) {
                retryRegisterIfNeeded("Application requested init again")
            }
            return
        }

        _initState.value = DjiSdkInitState.INITIALIZING
        _statusMessage.value = "Initializing DJI MSDK"
        Log.i(TAG, _statusMessage.value)
        DeveloperLogStore.info(TAG, "Initializing DJI MSDK")

        runCatching {
            val callback = object : SDKManagerCallback {
                override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                    val eventText = event?.name ?: "UNKNOWN"
                    _statusMessage.value = "Initializing DJI MSDK: $eventText ($totalProcess)"
                    Log.i(TAG, _statusMessage.value)
                    DeveloperLogStore.debug(TAG, "DJI init progress", _statusMessage.value)

                    if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                        sdkInitComplete.set(true)
                        _initState.value = DjiSdkInitState.READY_TO_REGISTER
                        _statusMessage.value = "DJI MSDK initialized, registering app"
                        DeveloperLogStore.info(TAG, "DJI SDK initialized", "registerApp next")
                        retryRegisterIfNeeded("SDK init complete")
                    }
                }

                override fun onRegisterSuccess() {
                    registerInFlight.set(false)
                    _initState.value = DjiSdkInitState.REGISTERED
                    _statusMessage.value = "DJI registerApp succeeded, waiting for product connection"
                    Log.i(TAG, _statusMessage.value)
                    DeveloperLogStore.info(TAG, "DJI registerApp succeeded")
                }

                override fun onRegisterFailure(error: IDJIError?) {
                    registerInFlight.set(false)
                    _initState.value = DjiSdkInitState.FAILED
                    _statusMessage.value = "DJI registerApp failed: ${DjiErrorFormatter.describe(error)}"
                    Log.e(TAG, _statusMessage.value)
                    DeveloperLogStore.error(TAG, "DJI registerApp failed", _statusMessage.value)
                }

                override fun onProductConnect(productId: Int) {
                    DjiConnectionManager.onProductConnected(productId)
                    Log.i(TAG, "DJI product connected: productId=$productId")
                    DeveloperLogStore.info(TAG, "DJI product connected", "productId=$productId")
                }

                override fun onProductDisconnect(productId: Int) {
                    DjiConnectionManager.onProductDisconnected(productId)
                    Log.w(TAG, "DJI product disconnected: productId=$productId")
                    DeveloperLogStore.warn(TAG, "DJI product disconnected", "productId=$productId")
                }

                override fun onProductChanged(productId: Int) {
                    DjiConnectionManager.onProductChanged(productId)
                    Log.i(TAG, "DJI product changed: productId=$productId")
                    DeveloperLogStore.info(TAG, "DJI product changed", "productId=$productId")
                }

                override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                    if (total > 0L) {
                        Log.i(TAG, "DJI database download progress: $current/$total")
                        DeveloperLogStore.debug(TAG, "DJI DB download", "$current/$total")
                    }
                }
            }
            SDKManager.getInstance().init(context.applicationContext, callback)
        }.onFailure { throwable ->
            _initState.value = DjiSdkInitState.FAILED
            _statusMessage.value = "DJI init() failed: ${throwable.message}"
            Log.e(TAG, _statusMessage.value, throwable)
            DeveloperLogStore.error(TAG, "DJI init() failed", throwable.message)
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
        _statusMessage.value = "Calling registerApp(): $reason (applicationId=${BuildConfig.APPLICATION_ID})"
        Log.i(TAG, _statusMessage.value)
        DeveloperLogStore.info(TAG, "Calling registerApp()", _statusMessage.value)

        runCatching {
            SDKManager.getInstance().registerApp()
        }.onFailure { throwable ->
            registerInFlight.set(false)
            _initState.value = DjiSdkInitState.FAILED
            _statusMessage.value = "registerApp() invocation failed: ${throwable.message}"
            Log.e(TAG, _statusMessage.value, throwable)
            DeveloperLogStore.error(TAG, "registerApp() invocation failed", throwable.message)
        }
    }

    fun isSdkPresent(): Boolean {
        if (!BuildConfig.DJI_ENABLE_RUNTIME || runtimeBlockedReason.get() != null) {
            return false
        }
        return when (_initState.value) {
            DjiSdkInitState.INITIALIZING,
            DjiSdkInitState.READY_TO_REGISTER,
            DjiSdkInitState.REGISTERING,
            DjiSdkInitState.REGISTERED,
            -> true

            else -> false
        }
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
        return DjiRuntimeEnvironment.isProbablyVirtualDevice()
    }

    private fun detectBlockedRuntimeReason(): String? {
        return DjiRuntimeEnvironment.skipReason()
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
            DeveloperLogStore.warn(TAG, "Unable to register network recovery callback", throwable.message)
        }
    }
}
