package com.example.uavmobile.dji

import android.os.Build
import com.example.uavmobile.BuildConfig

data class DjiRuntimeDecision(
    val shouldSkip: Boolean,
    val reason: String? = null,
)

object DjiRuntimeEnvironment {
    private const val VIRTUAL_DEVICE_REASON = "Running on a virtual device, DJI runtime init is skipped"
    private const val DISABLED_REASON = "DJI runtime disabled by configuration"

    fun currentDecision(): DjiRuntimeDecision {
        return decide(
            runtimeEnabled = BuildConfig.DJI_ENABLE_RUNTIME,
            fingerprint = Build.FINGERPRINT,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            product = Build.PRODUCT,
        )
    }

    fun shouldSkipDjiRuntime(): Boolean = currentDecision().shouldSkip

    fun skipReason(): String? = currentDecision().reason

    fun isProbablyVirtualDevice(): Boolean {
        return isProbablyVirtualDevice(
            fingerprint = Build.FINGERPRINT,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            product = Build.PRODUCT,
        )
    }

    internal fun decide(
        runtimeEnabled: Boolean,
        fingerprint: String,
        model: String,
        manufacturer: String,
        brand: String,
        device: String,
        hardware: String,
        product: String,
    ): DjiRuntimeDecision {
        if (!runtimeEnabled) {
            return DjiRuntimeDecision(shouldSkip = true, reason = DISABLED_REASON)
        }
        if (isProbablyVirtualDevice(fingerprint, model, manufacturer, brand, device, hardware, product)) {
            return DjiRuntimeDecision(shouldSkip = true, reason = VIRTUAL_DEVICE_REASON)
        }
        return DjiRuntimeDecision(shouldSkip = false)
    }

    internal fun isProbablyVirtualDevice(
        fingerprint: String,
        model: String,
        manufacturer: String,
        brand: String,
        device: String,
        hardware: String,
        product: String,
    ): Boolean {
        val normalizedFingerprint = fingerprint.lowercase()
        val normalizedModel = model.lowercase()
        val normalizedManufacturer = manufacturer.lowercase()
        val normalizedBrand = brand.lowercase()
        val normalizedDevice = device.lowercase()
        val normalizedHardware = hardware.lowercase()
        val normalizedProduct = product.lowercase()

        return normalizedFingerprint.startsWith("generic") ||
            normalizedFingerprint.contains("emulator") ||
            normalizedFingerprint.contains("vbox") ||
            normalizedModel.contains("android sdk built for") ||
            normalizedModel.contains("emulator") ||
            normalizedModel.contains("sdk_gphone") ||
            normalizedManufacturer.contains("genymotion") ||
            normalizedBrand.startsWith("generic") ||
            normalizedDevice.startsWith("generic") ||
            normalizedHardware.contains("goldfish") ||
            normalizedHardware.contains("ranchu") ||
            normalizedHardware.contains("vbox") ||
            normalizedProduct.contains("sdk") ||
            normalizedProduct.contains("emulator")
    }
}
