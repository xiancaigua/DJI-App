package com.example.uavmobile.dji

import android.os.Build
import com.example.uavmobile.BuildConfig

data class DjiRuntimeDecision(
    val shouldSkip: Boolean,
    val reason: String? = null,
)

data class DjiRuntimeConfigSnapshot(
    val applicationId: String,
    val applicationIdIsDefault: Boolean,
    val djiAppKeyEmpty: Boolean,
    val djiEnableRuntime: Boolean,
    val djiSdkVersion: String,
    val djiWpmzVersion: String,
)

object DjiRuntimeEnvironment {
    private const val VIRTUAL_DEVICE_REASON = "当前是虚拟设备，已跳过 DJI 运行时初始化"
    private const val DISABLED_REASON = "DJI runtime disabled. Check AIRCRAFT_API_KEY / DJI_ENABLE_RUNTIME / local.properties."

    fun currentConfig(): DjiRuntimeConfigSnapshot {
        return DjiRuntimeConfigSnapshot(
            applicationId = BuildConfig.APPLICATION_ID,
            applicationIdIsDefault = BuildConfig.APP_APPLICATION_ID_IS_DEFAULT,
            djiAppKeyEmpty = BuildConfig.DJI_APP_KEY_EMPTY,
            djiEnableRuntime = BuildConfig.DJI_ENABLE_RUNTIME,
            djiSdkVersion = BuildConfig.DJI_SDK_VERSION,
            djiWpmzVersion = BuildConfig.DJI_WPMZ_VERSION,
        )
    }

    fun currentDecision(): DjiRuntimeDecision {
        val config = currentConfig()
        return decide(
            runtimeEnabled = config.djiEnableRuntime,
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

    fun configSummary(config: DjiRuntimeConfigSnapshot = currentConfig()): String {
        return buildString {
            append("APPLICATION_ID=${config.applicationId}")
            append(", APP_APPLICATION_ID_IS_DEFAULT=${config.applicationIdIsDefault}")
            append(", DJI_APP_KEY_EMPTY=${config.djiAppKeyEmpty}")
            append(", DJI_ENABLE_RUNTIME=${config.djiEnableRuntime}")
            append(", DJI_SDK_VERSION=${config.djiSdkVersion}")
            append(", DJI_WPMZ_VERSION=${config.djiWpmzVersion}")
        }
    }

    fun decisionSummary(
        config: DjiRuntimeConfigSnapshot = currentConfig(),
        decision: DjiRuntimeDecision = currentDecision(),
    ): String {
        return buildString {
            append(configSummary(config))
            append(", runtimeSkipReason=${decision.reason ?: "none"}")
        }
    }

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
        fingerprint: String?,
        model: String?,
        manufacturer: String?,
        brand: String?,
        device: String?,
        hardware: String?,
        product: String?,
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
        fingerprint: String?,
        model: String?,
        manufacturer: String?,
        brand: String?,
        device: String?,
        hardware: String?,
        product: String?,
    ): Boolean {
        val normalizedFingerprint = fingerprint.orEmpty().lowercase()
        val normalizedModel = model.orEmpty().lowercase()
        val normalizedManufacturer = manufacturer.orEmpty().lowercase()
        val normalizedBrand = brand.orEmpty().lowercase()
        val normalizedDevice = device.orEmpty().lowercase()
        val normalizedHardware = hardware.orEmpty().lowercase()
        val normalizedProduct = product.orEmpty().lowercase()

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
