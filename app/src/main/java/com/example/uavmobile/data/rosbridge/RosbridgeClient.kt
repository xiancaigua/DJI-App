package com.example.uavmobile.data.rosbridge

import com.example.uavmobile.data.model.ConnectionStatus
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

data class RosbridgePublication(
    val topic: String,
    val payload: JSONObject,
)

class RosbridgeClient {
    private val httpClient = OkHttpClient.Builder().build()
    private val pendingServiceCalls = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _publications = MutableSharedFlow<RosbridgePublication>(extraBufferCapacity = 64)
    val publications: SharedFlow<RosbridgePublication> = _publications.asSharedFlow()

    private var webSocket: WebSocket? = null

    fun connect(url: String) {
        disconnect()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failPendingCalls(IOException(t))
                _connectionStatus.value = ConnectionStatus.FAILED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failPendingCalls(IOException(reason))
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "user disconnect")
        webSocket = null
        if (_connectionStatus.value != ConnectionStatus.FAILED) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    fun subscribe(topic: String, throttleRateMs: Int = 0) {
        val payload = JSONObject()
            .put("op", "subscribe")
            .put("topic", topic)
        if (throttleRateMs > 0) {
            payload.put("throttle_rate", throttleRateMs)
        }
        send(payload)
    }

    fun advertise(topic: String, type: String) {
        send(
            JSONObject()
                .put("op", "advertise")
                .put("topic", topic)
                .put("type", type),
        )
    }

    fun publish(topic: String, message: JSONObject) {
        send(
            JSONObject()
                .put("op", "publish")
                .put("topic", topic)
                .put("msg", message),
        )
    }

    suspend fun callService(service: String, args: JSONObject = JSONObject()): JSONObject {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pendingServiceCalls[requestId] = deferred

        send(
            JSONObject()
                .put("op", "call_service")
                .put("id", requestId)
                .put("service", service)
                .put("args", args),
        )

        return deferred.await()
    }

    private fun send(payload: JSONObject) {
        webSocket?.send(payload.toString())
    }

    private fun handleMessage(text: String) {
        val json = JSONObject(text)
        when (json.optString("op")) {
            "publish" -> {
                val topic = json.optString("topic")
                val msg = json.optJSONObject("msg") ?: JSONObject()
                _publications.tryEmit(RosbridgePublication(topic = topic, payload = msg))
            }

            "service_response" -> {
                val id = json.optString("id")
                val deferred = pendingServiceCalls.remove(id)
                if (deferred != null) {
                    deferred.complete(json)
                }
            }
        }
    }

    private fun failPendingCalls(exception: IOException) {
        pendingServiceCalls.values.forEach { it.completeExceptionally(exception) }
        pendingServiceCalls.clear()
    }
}
