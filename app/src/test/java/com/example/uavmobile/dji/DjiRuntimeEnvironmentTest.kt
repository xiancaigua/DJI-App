package com.example.uavmobile.dji

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjiRuntimeEnvironmentTest {
    @Test
    fun `virtual device fingerprint is skipped`() {
        val decision = DjiRuntimeEnvironment.decide(
            runtimeEnabled = true,
            fingerprint = "generic/sdk_gphone64_arm64/emulator:14/UPB5:userdebug/test-keys",
            model = "sdk_gphone64_arm64",
            manufacturer = "Google",
            brand = "generic",
            device = "generic_x86_64",
            hardware = "ranchu",
            product = "sdk_gphone64_arm64",
        )

        assertTrue(decision.shouldSkip)
        assertTrue(decision.reason.orEmpty().contains("虚拟设备"))
    }

    @Test
    fun `physical device shape does not skip when runtime is enabled`() {
        val decision = DjiRuntimeEnvironment.decide(
            runtimeEnabled = true,
            fingerprint = "Xiaomi/odin_global/odin:14/UKQ1.231003.002/V816.0.6.0.UMNCNXM:user/release-keys",
            model = "24031PN0DC",
            manufacturer = "Xiaomi",
            brand = "Redmi",
            device = "odin",
            hardware = "qcom",
            product = "odin_global",
        )

        assertFalse(decision.shouldSkip)
    }

    @Test
    fun `runtime disabled is skipped before device shape is evaluated`() {
        val decision = DjiRuntimeEnvironment.decide(
            runtimeEnabled = false,
            fingerprint = "physical-device-fingerprint",
            model = "Physical Device",
            manufacturer = "Vendor",
            brand = "Brand",
            device = "device",
            hardware = "hardware",
            product = "product",
        )

        assertTrue(decision.shouldSkip)
        assertTrue(decision.reason.orEmpty().contains("AIRCRAFT_API_KEY"))
    }
}
