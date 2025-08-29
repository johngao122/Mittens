package com.example.knit.demo.core.services

import knit.Provides
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: LocalDateTime,
    val level: LogLevel,
    val component: String,
    val message: String,
    val exception: Throwable? = null
)

@Provides
class LoggingService {
    
    private val logs = mutableListOf<LogEntry>()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    fun debug(component: String, message: String) {
        log(LogLevel.DEBUG, component, message)
    }
    
    fun info(component: String, message: String) {
        log(LogLevel.INFO, component, message)
    }
    
    fun warn(component: String, message: String) {
        log(LogLevel.WARN, component, message)
    }
    
    fun error(component: String, message: String, exception: Throwable? = null) {
        log(LogLevel.ERROR, component, message, exception)
    }
    
    private fun log(level: LogLevel, component: String, message: String, exception: Throwable? = null) {
        val entry = LogEntry(
            timestamp = LocalDateTime.now(),
            level = level,
            component = component,
            message = message,
            exception = exception
        )
        
        logs.add(entry)
        
        val levelIcon = when (level) {
            LogLevel.DEBUG -> "Debug"
            LogLevel.INFO -> "Info"
            LogLevel.WARN -> "Warn"
            LogLevel.ERROR -> "Error"
        }
        
        val timestamp = entry.timestamp.format(formatter)
        println("$levelIcon [$timestamp] $component: $message")
        
        if (exception != null) {
            println("   Exception: ${exception.message}")
        }
    }
    
    fun getLogs(level: LogLevel? = null, component: String? = null, limit: Int = 100): List<LogEntry> {
        return logs
            .filter { level == null || it.level == level }
            .filter { component == null || it.component.contains(component, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    fun clearLogs() {
        logs.clear()
        println("LoggingService: Log history cleared")
    }
    
    fun getLogStats(): Map<LogLevel, Int> {
        return logs.groupingBy { it.level }.eachCount()
    }
}