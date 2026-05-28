package com.example.uavmobile.dji

import com.example.uavmobile.debug.DeveloperLogStore
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.manager.KeyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DjiConnectionSource {
    SDK_CALLBACK_CONNECT,
    SDK_CALLBACK_CHANGED,
    SDK_CALLBACK_DISCONNECT,
    KEY_MANAGER_REFRESH,
    KEY_CONNECTION_LISTENER,
    CONNECTION_MONITOR_POLL,
    UNKNOWN,
}

data class DjiConnectionSnapshot(
    val connected: Boolean = false,
    val effectiveConnected: Boolean = false,
    val productId: Int? = null,
    val productType: ProductType? = null,
    val keyConnectionValue: Boolean? = null,
    val callbackConnected: Boolean? = null,
    val lastConnectionSource: DjiConnectionSource = DjiConnectionSource.UNKNOWN,
    val lastRefreshReason: String = "",
    val lastRefreshSucceeded: Boolean = false,
    val lastRefreshError: String = "",
    val monitorRunning: Boolean = false,
    val monitorStartedAt: String = "",
    val monitorTickCount: Int = 0,
    val statusMessage: String = "DJI 产品未连接",
)

internal interface DjiProductConnectionReader {
    fun readConnection(): Boolean?
    fun readProductType(): ProductType?
    fun listenConnection(holder: Any, getOnce: Boolean, onValueChange: (Boolean?) -> Unit)
    fun cancelListen(holder: Any)
}

private object DefaultDjiProductConnectionReader : DjiProductConnectionReader {
    override fun readConnection(): Boolean? {
        val keyManager = KeyManager.getInstance() ?: error("DJI KeyManager 不可用")
        return keyManager.getValue(KeyTools.createKey(ProductKey.KeyConnection))
    }

    override fun readProductType(): ProductType? {
        val keyManager = KeyManager.getInstance() ?: error("DJI KeyManager 不可用")
        return keyManager.getValue(KeyTools.createKey(ProductKey.KeyProductType))
    }

    override fun listenConnection(holder: Any, getOnce: Boolean, onValueChange: (Boolean?) -> Unit) {
        val keyManager = KeyManager.getInstance() ?: error("DJI KeyManager 不可用")
        keyManager.listen(
            KeyTools.createKey(ProductKey.KeyConnection),
            holder,
            getOnce,
            object : CommonCallbacks.KeyListener<Boolean> {
                override fun onValueChange(oldValue: Boolean?, newValue: Boolean?) {
                    onValueChange(newValue)
                }
            },
        )
    }

    override fun cancelListen(holder: Any) {
        KeyManager.getInstance()?.cancelListen(holder)
    }
}

object DjiConnectionManager {
    private const val TAG = "DjiConnectionManager"
    private const val DISCONNECTED_POLL_INTERVAL_MS = 1_000L
    private const val CONNECTED_POLL_INTERVAL_MS = 5_000L
    private const val HEARTBEAT_LOG_TICKS = 10

    private val lock = Any()
    private val monitorActive = AtomicBoolean(false)
    private val listenerHolder = Any()
    private var monitorJob: Job? = null
    private var monitorDispatcher: CoroutineDispatcher = Dispatchers.IO
    private var monitorScope = CoroutineScope(SupervisorJob() + monitorDispatcher)
    private var reader: DjiProductConnectionReader = DefaultDjiProductConnectionReader

    private val _connectionState = MutableStateFlow(DjiConnectionSnapshot())
    val connectionState: StateFlow<DjiConnectionSnapshot> = _connectionState.asStateFlow()

    fun isConnected(): Boolean = connectionState.value.connected

    fun describeStatus(): String = connectionState.value.statusMessage

    fun currentProductType(): ProductType? = connectionState.value.productType

    fun startConnectionMonitor(reason: String) {
        if (!monitorActive.compareAndSet(false, true)) {
            publishMonitorMetadata(monitorRunning = true)
            DeveloperLogStore.debug(TAG, "DJI 连接监视器已在运行", reason)
            return
        }

        val startedAt = nowText()
        publishMonitorMetadata(
            monitorRunning = true,
            monitorStartedAt = startedAt,
            monitorTickCount = 0,
            reason = reason,
        )
        DeveloperLogStore.info(TAG, "DJI 连接监视器已启动", "reason=$reason, startedAt=$startedAt")
        installKeyConnectionListener(reason)

        monitorJob = monitorScope.launch {
            while (monitorActive.get()) {
                val tick = incrementMonitorTick(reason)
                val snapshot = refreshFromKeyManagerInternal(
                    reason = "monitor poll: $reason",
                    source = DjiConnectionSource.CONNECTION_MONITOR_POLL,
                    logUnchanged = tick % HEARTBEAT_LOG_TICKS == 0,
                )
                delay(
                    if (snapshot.keyConnectionValue == true) {
                        CONNECTED_POLL_INTERVAL_MS
                    } else {
                        DISCONNECTED_POLL_INTERVAL_MS
                    },
                )
            }
        }
    }

    fun stopConnectionMonitor(reason: String) {
        if (!monitorActive.getAndSet(false)) {
            publishMonitorMetadata(monitorRunning = false, reason = reason)
            return
        }
        monitorJob?.cancel()
        monitorJob = null
        runCatching { reader.cancelListen(listenerHolder) }
            .onFailure { throwable ->
                DeveloperLogStore.warn(TAG, "取消 KeyConnection listener 失败", throwable.message)
            }
        publishMonitorMetadata(monitorRunning = false, reason = reason)
        DeveloperLogStore.info(TAG, "DJI 连接监视器已停止", reason)
    }

    fun refreshFromKeyManager(reason: String = "manual refresh"): DjiConnectionSnapshot {
        return refreshFromKeyManagerInternal(
            reason = reason,
            source = DjiConnectionSource.KEY_MANAGER_REFRESH,
            logUnchanged = true,
        )
    }

    internal fun onProductConnected(productId: Int) {
        refreshFromKeyManagerInternal(
            reason = "onProductConnect callback productId=$productId",
            source = DjiConnectionSource.SDK_CALLBACK_CONNECT,
            callbackProductId = productId,
            callbackConnected = true,
            logUnchanged = true,
        )
    }

    internal fun onProductDisconnected(productId: Int) {
        refreshFromKeyManagerInternal(
            reason = "onProductDisconnect callback productId=$productId",
            source = DjiConnectionSource.SDK_CALLBACK_DISCONNECT,
            callbackProductId = productId,
            callbackConnected = false,
            logUnchanged = true,
        )
    }

    internal fun onProductChanged(productId: Int) {
        refreshFromKeyManagerInternal(
            reason = "onProductChanged callback productId=$productId",
            source = DjiConnectionSource.SDK_CALLBACK_CHANGED,
            callbackProductId = productId,
            callbackConnected = true,
            logUnchanged = true,
        )
    }

    private fun installKeyConnectionListener(reason: String) {
        runCatching {
            reader.listenConnection(listenerHolder, getOnce = true) { value ->
                handleKeyConnectionListenerValue(value, reason)
            }
        }.onSuccess {
            DeveloperLogStore.info(TAG, "KeyConnection listener 已安装", reason)
        }.onFailure { throwable ->
            DeveloperLogStore.warn(TAG, "KeyConnection listener 安装失败", throwable.message ?: "未知错误")
            val previous = connectionState.value
            publishSnapshot(
                keyConnectionValue = previous.keyConnectionValue,
                callbackConnected = previous.callbackConnected,
                effectiveConnected = previous.effectiveConnected,
                productType = previous.productType,
                productId = previous.productId,
                source = DjiConnectionSource.KEY_CONNECTION_LISTENER,
                reason = reason,
                succeeded = false,
                error = throwable.message ?: "KeyConnection listener 安装失败",
                statusMessage = previous.statusMessage,
                logUnchanged = true,
            )
        }
    }

    private fun handleKeyConnectionListenerValue(value: Boolean?, reason: String) {
        val callbackConnected = connectionState.value.callbackConnected
        val productTypeRead = if (value == true || (value == null && callbackConnected == true)) {
            readProductTypeSafely()
        } else {
            KeyReadResult<ProductType?>(null, null)
        }
        val error = productTypeRead.error.orEmpty()
        if (callbackConnected != null && value != null && callbackConnected != value) {
            DeveloperLogStore.warn(
                TAG,
                "DJI callback 与 KeyConnection 不一致",
                "source=${DjiConnectionSource.KEY_CONNECTION_LISTENER}, callbackConnected=$callbackConnected, keyConnection=$value, reason=$reason",
            )
        }
        val effectiveConnected = resolveEffectiveConnected(
            keyConnectionValue = value,
            callbackConnected = callbackConnected,
        )
        publishSnapshot(
            keyConnectionValue = value,
            callbackConnected = callbackConnected,
            effectiveConnected = effectiveConnected,
            productType = productTypeRead.value.takeIf { effectiveConnected },
            source = DjiConnectionSource.KEY_CONNECTION_LISTENER,
            reason = "KeyConnection listener: $reason",
            succeeded = error.isBlank(),
            error = error,
            statusMessage = buildStatusMessage(
                keyConnectionValue = value,
                callbackConnected = callbackConnected,
                effectiveConnected = effectiveConnected,
                productType = productTypeRead.value,
                source = DjiConnectionSource.KEY_CONNECTION_LISTENER,
                error = error,
            ),
            logUnchanged = false,
        )
    }

    private fun refreshFromKeyManagerInternal(
        reason: String,
        source: DjiConnectionSource,
        callbackProductId: Int? = null,
        callbackConnected: Boolean? = null,
        logUnchanged: Boolean,
    ): DjiConnectionSnapshot {
        val previous = connectionState.value
        val resolvedCallbackConnected = callbackConnected ?: previous.callbackConnected
        val connectionRead = readConnectionSafely()
        val keyConnection = connectionRead.value
        val productTypeRead = if (keyConnection == true || (keyConnection == null && resolvedCallbackConnected == true)) {
            readProductTypeSafely()
        } else {
            KeyReadResult<ProductType?>(null, null)
        }
        val error = listOfNotNull(connectionRead.error, productTypeRead.error)
            .filter { it.isNotBlank() }
            .joinToString("; ")
        val succeeded = error.isBlank()
        val effectiveConnected = resolveEffectiveConnected(
            keyConnectionValue = keyConnection,
            callbackConnected = resolvedCallbackConnected,
        )

        if (resolvedCallbackConnected != null && keyConnection != null && resolvedCallbackConnected != keyConnection) {
            DeveloperLogStore.warn(
                TAG,
                "DJI callback 与 KeyConnection 不一致",
                "source=$source, callbackConnected=$resolvedCallbackConnected, keyConnection=$keyConnection, reason=$reason",
            )
        }

        return publishSnapshot(
            keyConnectionValue = keyConnection,
            callbackConnected = resolvedCallbackConnected,
            effectiveConnected = effectiveConnected,
            productType = productTypeRead.value.takeIf { effectiveConnected },
            productId = resolveProductId(
                previous = previous,
                callbackProductId = callbackProductId,
                keyConnectionValue = keyConnection,
                effectiveConnected = effectiveConnected,
                callbackConnected = resolvedCallbackConnected,
            ),
            source = source,
            reason = reason,
            succeeded = succeeded,
            error = error,
            statusMessage = buildStatusMessage(
                keyConnectionValue = keyConnection,
                callbackConnected = resolvedCallbackConnected,
                effectiveConnected = effectiveConnected,
                productType = productTypeRead.value,
                source = source,
                error = error,
            ),
            logUnchanged = logUnchanged,
        )
    }

    private fun readConnectionSafely(): KeyReadResult<Boolean?> {
        return runCatching {
            reader.readConnection()
        }.fold(
            onSuccess = { KeyReadResult(it, null) },
            onFailure = { throwable ->
                KeyReadResult(
                    value = null,
                    error = throwable.message ?: "DJI KeyManager 不可用",
                )
            },
        )
    }

    private fun readProductTypeSafely(): KeyReadResult<ProductType?> {
        return runCatching {
            reader.readProductType()
        }.fold(
            onSuccess = { KeyReadResult(it, null) },
            onFailure = { throwable ->
                KeyReadResult(
                    value = null,
                    error = throwable.message ?: "读取 ProductKey.KeyProductType 失败",
                )
            },
        )
    }

    private fun publishSnapshot(
        keyConnectionValue: Boolean?,
        callbackConnected: Boolean?,
        effectiveConnected: Boolean,
        productType: ProductType?,
        productId: Int? = connectionState.value.productId,
        source: DjiConnectionSource,
        reason: String,
        succeeded: Boolean,
        error: String,
        statusMessage: String,
        logUnchanged: Boolean,
    ): DjiConnectionSnapshot {
        val previous = connectionState.value
        val next = previous.copy(
            connected = effectiveConnected,
            effectiveConnected = effectiveConnected,
            productId = productId,
            productType = productType,
            keyConnectionValue = keyConnectionValue,
            callbackConnected = callbackConnected,
            lastConnectionSource = source,
            lastRefreshReason = reason,
            lastRefreshSucceeded = succeeded,
            lastRefreshError = error,
            statusMessage = statusMessage,
        )
        _connectionState.value = next

        val changed = previous.connected != next.connected ||
            previous.effectiveConnected != next.effectiveConnected ||
            previous.productType != next.productType ||
            previous.keyConnectionValue != next.keyConnectionValue ||
            previous.callbackConnected != next.callbackConnected ||
            previous.lastRefreshError != next.lastRefreshError

        if (changed || logUnchanged || !succeeded) {
            val details = "source=$source, reason=$reason, callbackConnected=$callbackConnected, " +
                "keyConnection=$keyConnectionValue, effectiveConnected=$effectiveConnected, " +
                "productType=${productType?.name ?: "无"}, monitorRunning=${next.monitorRunning}, " +
                "error=${error.ifBlank { "无" }}"
            when {
                !succeeded -> DeveloperLogStore.warn(TAG, statusMessage, details)
                next.connected -> DeveloperLogStore.info(TAG, statusMessage, details)
                else -> DeveloperLogStore.debug(TAG, statusMessage, details)
            }
        }
        return next
    }

    private fun publishMonitorMetadata(
        monitorRunning: Boolean,
        monitorStartedAt: String = connectionState.value.monitorStartedAt,
        monitorTickCount: Int = connectionState.value.monitorTickCount,
        reason: String = connectionState.value.lastRefreshReason,
    ) {
        synchronized(lock) {
            _connectionState.value = connectionState.value.copy(
                monitorRunning = monitorRunning,
                monitorStartedAt = monitorStartedAt,
                monitorTickCount = monitorTickCount,
                lastRefreshReason = reason,
            )
        }
    }

    private fun incrementMonitorTick(reason: String): Int {
        return synchronized(lock) {
            val nextTick = connectionState.value.monitorTickCount + 1
            _connectionState.value = connectionState.value.copy(
                monitorRunning = true,
                monitorTickCount = nextTick,
                lastRefreshReason = reason,
            )
            nextTick
        }
    }

    private fun buildStatusMessage(
        keyConnectionValue: Boolean?,
        callbackConnected: Boolean?,
        effectiveConnected: Boolean,
        productType: ProductType?,
        source: DjiConnectionSource,
        error: String,
    ): String {
        if (keyConnectionValue == false) {
            return buildString {
                append("DJI 飞机离线：ProductKey.KeyConnection=false")
                if (callbackConnected == true) {
                    append("，SDK callback 之前报告已连接，但当前以 KeyConnection=false 为准")
                }
                if (error.isNotBlank()) {
                    append("，lastError=$error")
                }
                append("，source=$source")
            }
        }

        if (keyConnectionValue == true) {
            return buildString {
                append("DJI 飞机已连接")
                if (productType != null) {
                    append("，productType=${productType.name}")
                } else {
                    append("，ProductType not ready yet")
                }
                if (error.isNotBlank()) {
                    append("，lastError=$error")
                }
                append("，source=$source")
            }
        }

        if (callbackConnected == true && effectiveConnected) {
            return buildString {
                append("DJI SDK callback shows product connected, waiting for ProductKey.KeyConnection synchronization")
                if (productType != null) {
                    append("，productType=${productType.name}")
                } else {
                    append("，ProductType not ready yet")
                }
                if (error.isNotBlank()) {
                    append("，lastError=$error")
                }
                append("，source=$source")
            }
        }

        if (error.isNotBlank()) {
            return error
        }
        return "DJI 飞机离线，source=$source"
    }

    private fun resolveEffectiveConnected(
        keyConnectionValue: Boolean?,
        callbackConnected: Boolean?,
    ): Boolean {
        return when (keyConnectionValue) {
            true -> true
            false -> false
            null -> callbackConnected == true
        }
    }

    private fun resolveProductId(
        previous: DjiConnectionSnapshot,
        callbackProductId: Int?,
        keyConnectionValue: Boolean?,
        effectiveConnected: Boolean,
        callbackConnected: Boolean?,
    ): Int? {
        return when {
            effectiveConnected -> callbackProductId ?: previous.productId
            keyConnectionValue == false || callbackConnected == false -> null
            else -> callbackProductId ?: previous.productId
        }
    }

    private fun nowText(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }

    internal fun setConnectionReaderForTest(testReader: DjiProductConnectionReader) {
        stopConnectionMonitor("test reset")
        reader = testReader
    }

    internal fun setMonitorDispatcherForTest(dispatcher: CoroutineDispatcher) {
        stopConnectionMonitor("test dispatcher reset")
        monitorDispatcher = dispatcher
        monitorScope = CoroutineScope(SupervisorJob() + monitorDispatcher)
    }

    internal fun resetForTest() {
        stopConnectionMonitor("test reset")
        reader = DefaultDjiProductConnectionReader
        monitorDispatcher = Dispatchers.IO
        monitorScope = CoroutineScope(SupervisorJob() + monitorDispatcher)
        _connectionState.value = DjiConnectionSnapshot()
    }
}

private data class KeyReadResult<T>(
    val value: T,
    val error: String?,
)
