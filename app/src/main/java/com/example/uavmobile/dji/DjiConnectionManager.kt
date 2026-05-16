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
    val productId: Int? = null,
    val productType: ProductType? = null,
    val keyConnectionValue: Boolean? = null,
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
                delay(if (snapshot.connected) CONNECTED_POLL_INTERVAL_MS else DISCONNECTED_POLL_INTERVAL_MS)
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
            publishSnapshot(
                connectionValue = connectionState.value.keyConnectionValue,
                productType = connectionState.value.productType,
                source = DjiConnectionSource.KEY_CONNECTION_LISTENER,
                reason = reason,
                succeeded = false,
                error = throwable.message ?: "KeyConnection listener 安装失败",
                statusMessage = connectionState.value.statusMessage,
                logUnchanged = true,
            )
        }
    }

    private fun handleKeyConnectionListenerValue(value: Boolean?, reason: String) {
        val productTypeRead = if (value == true) readProductTypeSafely() else KeyReadResult<ProductType?>(null, null)
        val error = productTypeRead.error.orEmpty()
        publishSnapshot(
            connectionValue = value,
            productType = productTypeRead.value,
            source = DjiConnectionSource.KEY_CONNECTION_LISTENER,
            reason = "KeyConnection listener: $reason",
            succeeded = error.isBlank(),
            error = error,
            statusMessage = buildStatusMessage(
                connected = value == true,
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
        val connectionRead = readConnectionSafely()
        val keyConnection = connectionRead.value
        val productTypeRead = if (keyConnection == true) {
            readProductTypeSafely()
        } else {
            KeyReadResult<ProductType?>(null, null)
        }
        val error = listOfNotNull(connectionRead.error, productTypeRead.error)
            .filter { it.isNotBlank() }
            .joinToString("; ")
        val succeeded = error.isBlank()

        if (callbackConnected != null && keyConnection != null && callbackConnected != keyConnection) {
            DeveloperLogStore.warn(
                TAG,
                "DJI callback 与 KeyConnection 不一致",
                "source=$source, callbackConnected=$callbackConnected, keyConnection=$keyConnection, reason=$reason",
            )
        }

        return publishSnapshot(
            connectionValue = keyConnection,
            productType = productTypeRead.value,
            productId = callbackProductId ?: connectionState.value.productId,
            source = source,
            reason = reason,
            succeeded = succeeded,
            error = error,
            statusMessage = buildStatusMessage(
                connected = keyConnection == true,
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
        connectionValue: Boolean?,
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
            connected = connectionValue == true,
            productId = productId,
            productType = productType,
            keyConnectionValue = connectionValue,
            lastConnectionSource = source,
            lastRefreshReason = reason,
            lastRefreshSucceeded = succeeded,
            lastRefreshError = error,
            statusMessage = statusMessage,
        )
        _connectionState.value = next

        val changed = previous.connected != next.connected ||
            previous.productType != next.productType ||
            previous.keyConnectionValue != next.keyConnectionValue ||
            previous.lastRefreshError != next.lastRefreshError

        if (changed || logUnchanged || !succeeded) {
            val details = "source=$source, reason=$reason, keyConnection=$connectionValue, " +
                "productType=${productType?.name ?: "无"}, monitorRunning=${next.monitorRunning}, error=${error.ifBlank { "无" }}"
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
        connected: Boolean,
        productType: ProductType?,
        source: DjiConnectionSource,
        error: String,
    ): String {
        if (error.isNotBlank()) {
            return error
        }
        return if (connected) {
            buildString {
                append("DJI 飞机已连接")
                productType?.let { append("，productType=${it.name}") }
                append("，source=$source")
            }
        } else {
            "DJI 飞机离线，source=$source"
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
