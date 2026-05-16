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
    private val _statusMessage = MutableStateFlow("DJI MSDK 尚未初始化")

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
            DeveloperLogStore.warn(TAG, "DJI 运行时已跳过", _statusMessage.value)
            DjiConnectionManager.stopConnectionMonitor("DJI runtime skipped")
            return
        }

        if (!BuildConfig.DJI_ENABLE_RUNTIME) {
            _initState.value = DjiSdkInitState.SKIPPED
            _statusMessage.value = "DJI 运行时已被配置关闭"
            Log.i(TAG, _statusMessage.value)
            DeveloperLogStore.warn(TAG, "DJI 运行时已关闭", _statusMessage.value)
            DjiConnectionManager.stopConnectionMonitor("DJI runtime disabled")
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
        _statusMessage.value = "正在初始化 DJI MSDK"
        Log.i(TAG, _statusMessage.value)
        DeveloperLogStore.info(TAG, "开始初始化 DJI MSDK")

        runCatching {
            val callback = object : SDKManagerCallback {
                override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                    val eventText = event?.name ?: "UNKNOWN"
                    _statusMessage.value = "正在初始化 DJI MSDK：$eventText ($totalProcess)"
                    Log.i(TAG, _statusMessage.value)
                    DeveloperLogStore.debug(TAG, "DJI 初始化进度", _statusMessage.value)

                    if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                        sdkInitComplete.set(true)
                        _initState.value = DjiSdkInitState.READY_TO_REGISTER
                        _statusMessage.value = "DJI MSDK 已初始化，开始注册应用"
                        DeveloperLogStore.info(TAG, "DJI SDK 已初始化", "下一步 registerApp")
                        retryRegisterIfNeeded("SDK init complete")
                    }
                }

                override fun onRegisterSuccess() {
                    handleRegisterSuccess("SDK callback")
                }

                override fun onRegisterFailure(error: IDJIError?) {
                    registerInFlight.set(false)
                    _initState.value = DjiSdkInitState.FAILED
                    _statusMessage.value = "DJI registerApp 失败：${DjiErrorFormatter.describe(error)}"
                    Log.e(TAG, _statusMessage.value)
                    DeveloperLogStore.error(TAG, "DJI registerApp 失败", _statusMessage.value)
                    DjiConnectionManager.stopConnectionMonitor("registerApp failure")
                }

                override fun onProductConnect(productId: Int) {
                    DjiConnectionManager.onProductConnected(productId)
                    Log.i(TAG, "DJI product connected: productId=$productId")
                    DeveloperLogStore.info(
                        TAG,
                        "DJI product callback: onProductConnect",
                        "productId=$productId, keyConnection=${DjiConnectionManager.connectionState.value.keyConnectionValue}",
                    )
                }

                override fun onProductDisconnect(productId: Int) {
                    DjiConnectionManager.onProductDisconnected(productId)
                    Log.w(TAG, "DJI product disconnected: productId=$productId")
                    DeveloperLogStore.warn(
                        TAG,
                        "DJI product callback: onProductDisconnect",
                        "productId=$productId, keyConnection=${DjiConnectionManager.connectionState.value.keyConnectionValue}",
                    )
                }

                override fun onProductChanged(productId: Int) {
                    DjiConnectionManager.onProductChanged(productId)
                    Log.i(TAG, "DJI product changed: productId=$productId")
                    DeveloperLogStore.info(
                        TAG,
                        "DJI product callback: onProductChanged",
                        "productId=$productId, keyConnection=${DjiConnectionManager.connectionState.value.keyConnectionValue}",
                    )
                }

                override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                    if (total > 0L) {
                        Log.i(TAG, "DJI database download progress: $current/$total")
                        DeveloperLogStore.debug(TAG, "DJI 数据库下载", "$current/$total")
                    }
                }
            }
            SDKManager.getInstance().init(context.applicationContext, callback)
        }.onFailure { throwable ->
            _initState.value = DjiSdkInitState.FAILED
            _statusMessage.value = "DJI init() 失败：${throwable.message}"
            Log.e(TAG, _statusMessage.value, throwable)
            DeveloperLogStore.error(TAG, "DJI init() 失败", throwable.message)
            DjiConnectionManager.stopConnectionMonitor("DJI init failed")
        }
    }

    internal fun handleRegisterSuccess(reason: String = "registerApp success") {
        registerInFlight.set(false)
        _initState.value = DjiSdkInitState.REGISTERED
        DeveloperLogStore.info(TAG, "DJI registerApp 成功", reason)
        val snapshot = DjiConnectionManager.refreshFromKeyManager("registerApp success")
        DjiConnectionManager.startConnectionMonitor("registerApp success")
        _statusMessage.value = buildString {
            append("DJI registerApp 成功，已启动飞机连接监视器")
            append("，KeyConnection=${snapshot.keyConnectionValue}")
            append("，productType=${snapshot.productType?.name ?: "无"}")
        }
        Log.i(TAG, _statusMessage.value)
        DeveloperLogStore.info(TAG, "DJI registerApp 后连接刷新完成", _statusMessage.value)
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
        _statusMessage.value = "正在调用 registerApp()：$reason (applicationId=${BuildConfig.APPLICATION_ID})"
        Log.i(TAG, _statusMessage.value)
        DeveloperLogStore.info(TAG, "正在调用 registerApp()", _statusMessage.value)

        runCatching {
            SDKManager.getInstance().registerApp()
        }.onFailure { throwable ->
            registerInFlight.set(false)
            _initState.value = DjiSdkInitState.FAILED
            _statusMessage.value = "registerApp() 调用失败：${throwable.message}"
            Log.e(TAG, _statusMessage.value, throwable)
            DeveloperLogStore.error(TAG, "registerApp() 调用失败", throwable.message)
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
            DjiSdkInitState.REGISTERED -> true

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
        return appContextRef.get() ?: error("Application context 尚未可用")
    }

    fun isProbablyVirtualDevice(): Boolean {
        return DjiRuntimeEnvironment.isProbablyVirtualDevice()
    }

    private fun detectBlockedRuntimeReason(): String? {
        return DjiRuntimeEnvironment.skipReason()
    }

    internal fun setInitStateForTest(state: DjiSdkInitState, sdkReady: Boolean = state == DjiSdkInitState.REGISTERED) {
        _initState.value = state
        _statusMessage.value = when (state) {
            DjiSdkInitState.REGISTERED -> "DJI registerApp 成功，测试状态"
            DjiSdkInitState.FAILED -> "DJI registerApp 失败，测试状态"
            else -> "DJI 测试状态：$state"
        }
        sdkInitComplete.set(sdkReady)
        registerInFlight.set(false)
    }

    internal fun resetForTest() {
        _initState.value = DjiSdkInitState.IDLE
        _statusMessage.value = "DJI MSDK 尚未初始化"
        appContextRef.set(null)
        runtimeBlockedReason.set(null)
        initRequested.set(false)
        sdkInitComplete.set(false)
        registerInFlight.set(false)
        networkCallbackRegistered.set(false)
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
            DeveloperLogStore.warn(TAG, "无法注册网络恢复回调", throwable.message)
        }
    }
}
