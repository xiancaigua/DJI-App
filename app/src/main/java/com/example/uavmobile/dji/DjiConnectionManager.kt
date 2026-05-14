package com.example.uavmobile.dji

import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.manager.KeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DjiConnectionSnapshot(
    val connected: Boolean = false,
    val productId: Int? = null,
    val productType: ProductType? = null,
    val statusMessage: String = "DJI product not connected",
)

object DjiConnectionManager {
    private val _connectionState = MutableStateFlow(DjiConnectionSnapshot())
    val connectionState: StateFlow<DjiConnectionSnapshot> = _connectionState.asStateFlow()

    fun isConnected(): Boolean = connectionState.value.connected

    fun describeStatus(): String = connectionState.value.statusMessage

    fun currentProductType(): ProductType? = connectionState.value.productType

    internal fun onProductConnected(productId: Int) {
        updateConnectionSnapshot(connected = true, productId = productId, eventLabel = "connected")
    }

    internal fun onProductDisconnected(productId: Int) {
        updateConnectionSnapshot(connected = false, productId = productId, eventLabel = "disconnected")
    }

    internal fun onProductChanged(productId: Int) {
        updateConnectionSnapshot(connected = true, productId = productId, eventLabel = "changed")
    }

    private fun updateConnectionSnapshot(
        connected: Boolean,
        productId: Int?,
        eventLabel: String,
    ) {
        val productType = if (connected) {
            KeyManager.getInstance()
                ?.getValue(KeyTools.createKey(ProductKey.KeyProductType))
        } else {
            null
        }
        _connectionState.value = DjiConnectionSnapshot(
            connected = connected,
            productId = productId,
            productType = productType,
            statusMessage = buildString {
                append("DJI product $eventLabel")
                productId?.let { append(" (productId=$it)") }
                productType?.let { append(", productType=$it") }
            },
        )
    }
}
