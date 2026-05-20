package com.example.uavmobile.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DeveloperLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class DeveloperLogEntry(
    val timestamp: String,
    val level: DeveloperLogLevel,
    val source: String,
    val message: String,
    val details: String? = null,
)

object DeveloperLogStore {
    private const val MAX_ENTRIES = 150
    const val NO_LOGS_RECORDED_YET_MESSAGE =
        "暂时没有开发日志。请执行初始化 DJI、上传任务、开始任务或刷新快照来生成日志。"
    const val LOGS_CLEARED_SUCCESSFULLY_MESSAGE =
        "日志已清空。新的运行事件会显示在这里。"
    const val NO_LOGS_AVAILABLE_MESSAGE = "当前没有可复制的开发日志。"

    private val _entries = MutableStateFlow<List<DeveloperLogEntry>>(emptyList())
    val entries: StateFlow<List<DeveloperLogEntry>> = _entries.asStateFlow()

    fun debug(
        source: String,
        message: String,
        details: String? = null,
    ) = append(DeveloperLogLevel.DEBUG, source, message, details)

    fun info(
        source: String,
        message: String,
        details: String? = null,
    ) = append(DeveloperLogLevel.INFO, source, message, details)

    fun warn(
        source: String,
        message: String,
        details: String? = null,
    ) = append(DeveloperLogLevel.WARN, source, message, details)

    fun error(
        source: String,
        message: String,
        details: String? = null,
    ) = append(DeveloperLogLevel.ERROR, source, message, details)

    fun clear() {
        _entries.value = emptyList()
    }

    fun formatRecentLogs(limit: Int = 100): String {
        if (entries.value.isEmpty()) {
            return NO_LOGS_AVAILABLE_MESSAGE
        }
        return formatEntries(entries.value.takeLast(limit))
    }

    fun formatEntries(entries: List<DeveloperLogEntry>): String {
        if (entries.isEmpty()) {
            return NO_LOGS_AVAILABLE_MESSAGE
        }
        return entries.joinToString(separator = "\n") { entry ->
            buildString {
                append(entry.timestamp)
                append(" [")
                append(entry.level.name)
                append("] ")
                append(entry.source)
                append(": ")
                append(entry.message)
                entry.details?.takeIf { it.isNotBlank() }?.let {
                    append(" | ")
                    append(it)
                }
            }
        }
    }

    private fun append(
        level: DeveloperLogLevel,
        source: String,
        message: String,
        details: String? = null,
    ) {
        val entry = DeveloperLogEntry(
            timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            level = level,
            source = source,
            message = message,
            details = details,
        )
        _entries.update { current ->
            (current + entry).takeLast(MAX_ENTRIES)
        }
    }
}
