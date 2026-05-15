package com.example.uavmobile.dji

import com.example.uavmobile.BuildConfig

object DjiErrorFormatter {
    fun describe(error: Any?): String {
        if (error == null) {
            return "Unknown DJI error"
        }

        val description = runCatching {
            error.javaClass.methods.firstOrNull { it.name == "description" && it.parameterCount == 0 }
                ?.invoke(error)
                ?.toString()
        }.getOrNull().orEmpty()

        val errorCode = runCatching {
            error.javaClass.methods.firstOrNull { it.name == "errorCode" && it.parameterCount == 0 }
                ?.invoke(error)
                ?.toString()
        }.getOrNull().orEmpty()

        val rawText = listOf(description, errorCode, error.toString())
            .firstOrNull { it.isNotBlank() }
            ?: "Unknown DJI error"

        val hint = buildHint(rawText)
        return if (hint == null) rawText else "$rawText. Hint: $hint"
    }

    private fun buildHint(rawText: String): String? {
        val lowered = rawText.lowercase()
        return when {
            "元数据有误" in rawText || "metadata" in lowered ->
                "Current Android applicationId is ${BuildConfig.APPLICATION_ID}. DJI Developer Center -> Package Name must exactly match this value, and the App Key must come from an Android Mobile SDK app created for the same package."

            "第一次注册时需要连接互联网" in rawText || "internet" in lowered ->
                "Keep the remote controller online and let registerApp() finish successfully once before retrying offline use."

            "bundle" in lowered || "package" in lowered ->
                "Current Android applicationId is ${BuildConfig.APPLICATION_ID}. Make sure DJI Developer Center uses the exact same Package Name."

            "not registered" in lowered || "app key" in lowered ->
                "Check API key injection, network access, and confirm DJI Developer Center Package Name matches ${BuildConfig.APPLICATION_ID}."

            "disconnect" in lowered || "not connected" in lowered ->
                "Verify controller, aircraft, and mobile device connectivity."

            "timeout" in lowered ->
                "Retry after the controller and aircraft finish syncing their mission state."

            "fileparseerror" in lowered || "kmz" in lowered ->
                "Verify the generated WPMZ/KMZ mission file and waypoint parameters."

            else -> null
        }
    }
}
