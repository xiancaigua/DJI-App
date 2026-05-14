package com.example.uavmobile.dji

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
            "not registered" in lowered || "app key" in lowered ->
                "Check API key injection, network access, and registerApp() state."

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
