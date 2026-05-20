package com.example.uavmobile.dji

import com.example.uavmobile.BuildConfig

object DjiErrorFormatter {
    fun describe(error: Any?): String {
        if (error == null) {
            return "未知 DJI 错误"
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
            ?: "未知 DJI 错误"

        val hint = buildHint(rawText)
        return if (hint == null) rawText else "$rawText。提示：$hint"
    }

    private fun buildHint(rawText: String): String? {
        val lowered = rawText.lowercase()
        return when {
            "元数据有误" in rawText || "metadata" in lowered ->
                "当前 Android applicationId 是 ${BuildConfig.APPLICATION_ID}。DJI Developer Center 里的 Package Name 必须与它完全一致，App Key 也必须来自同一包名的 Android Mobile SDK 应用。"

            "第一次注册时需要连接互联网" in rawText || "internet" in lowered ->
                "请让遥控器保持联网，并至少成功完成一次 registerApp()，再尝试离线使用。"

            "bundle" in lowered || "package" in lowered ->
                "当前 Android applicationId 是 ${BuildConfig.APPLICATION_ID}。请确认 DJI Developer Center 使用完全相同的 Package Name。"

            "not registered" in lowered || "app key" in lowered ->
                "请检查 API Key 注入、网络状态，并确认 DJI Developer Center 包名与 ${BuildConfig.APPLICATION_ID} 一致。"

            "disconnect" in lowered || "not connected" in lowered ->
                "请检查遥控器、飞机和移动设备连接。"

            "heading_level_poor" in lowered || "compass" in lowered || "yaw" in lowered ->
                "航向质量不足。请先等待指南针/航向稳定，远离金属和强磁干扰源，确认飞机完成定位且机头朝向不再明显跳变后再启动任务。"

            "timeout" in lowered ->
                "请等待遥控器和飞机完成任务状态同步后重试。"

            "fileparseerror" in lowered || "kmz" in lowered ->
                "请检查生成的 WPMZ/KMZ 文件和航点参数。"

            else -> null
        }
    }
}
