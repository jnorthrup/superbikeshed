package nexus.telemetry

import java.time.Instant
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * # Cursor Telemetry System
 * 
 * Tracks IDE interactions, code generation events, and performance metrics
 * for the Cursor IDE integration with Claude-code.
 * 
 * ## Features
 * - Code generation tracking
 * - IDE interaction monitoring
 * - Performance metrics collection
 * - Error tracking and reporting
 * - User behavior analytics
 * 
 * ## Usage
 * 
 * ```kotlin
 * val telemetry = CursorTelemetry()
 * telemetry.trackCodeGeneration(
 *     prompt = "Create a function to sort a list",
 *     language = "kotlin",
 *     responseTime = Duration.ofMillis(1500),
 *     success = true
 * )
 * ```
 */
class CursorTelemetry(
    private val sessionId: String = UUID.randomUUID().toString(),
    private val userId: String? = null,
    private val telemetryDir: Path = Paths.get(System.getProperty("user.home"), ".cursor", "telemetry"),
    private val enablePersistence: Boolean = true,
    private val enableRealTime: Boolean = true
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventChannel = Channel<TelemetryEvent>(Channel.UNLIMITED)
    private val metrics = ConcurrentHashMap<String, AtomicLong>()
    private val sessionStart = Instant.now()
    
    init {
        if (enablePersistence) {
            Files.createDirectories(telemetryDir)
        }
        
        if (enableRealTime) {
            startEventProcessor()
        }
        
        // Track session start
        trackEvent(TelemetryEvent.SessionStart(sessionId, userId, sessionStart))
    }
    
    /**
     * Telemetry event types
     */
    sealed class TelemetryEvent {
        data class SessionStart(
            val sessionId: String,
            val userId: String?,
            val timestamp: Instant
        ) : TelemetryEvent()
        
        data class CodeGeneration(
            val sessionId: String,
            val userId: String?,
            val timestamp: Instant,
            val prompt: String,
            val language: String,
            val responseTime: Long, // milliseconds
            val success: Boolean,
            val errorMessage: String? = null,
            val tokensGenerated: Int? = null,
            val modelUsed: String? = null
        ) : TelemetryEvent()
        
        data class IDEInteraction(
            val sessionId: String,
            val userId: String?,
            val timestamp: Instant,
            val action: String, // "file_open", "file_save", "code_completion", etc.
            val filePath: String? = null,
            val language: String? = null,
            val duration: Long? = null // milliseconds
        ) : TelemetryEvent()
        
        data class PerformanceMetric(
            val sessionId: String,
            val userId: String?,
            val timestamp: Instant,
            val metricName: String,
            val value: Double,
            val unit: String? = null
        ) : TelemetryEvent()
        
        data class Error(
            val sessionId: String,
            val userId: String?,
            val timestamp: Instant,
            val errorType: String,
            val errorMessage: String,
            val stackTrace: String? = null,
            val context: Map<String, String> = emptyMap()
        ) : TelemetryEvent()
        
        data class UserBehavior(
            val sessionId: String,
            val userId: String?,
            val timestamp: Instant,
            val behavior: String, // "feature_used", "preference_changed", etc.
            val details: Map<String, String> = emptyMap()
        ) : TelemetryEvent()
    }
    
    /**
     * Track code generation event
     */
    fun trackCodeGeneration(
        prompt: String,
        language: String,
        responseTime: Long,
        success: Boolean,
        errorMessage: String? = null,
        tokensGenerated: Int? = null,
        modelUsed: String? = null
    ) {
        val event = TelemetryEvent.CodeGeneration(
            sessionId = sessionId,
            userId = userId,
            timestamp = Instant.now(),
            prompt = prompt,
            language = language,
            responseTime = responseTime,
            success = success,
            errorMessage = errorMessage,
            tokensGenerated = tokensGenerated,
            modelUsed = modelUsed
        )
        
        trackEvent(event)
        incrementMetric("code_generation_attempts")
        
        if (success) {
            incrementMetric("code_generation_success")
        } else {
            incrementMetric("code_generation_failures")
        }
    }
    
    /**
     * Track IDE interaction
     */
    fun trackIDEInteraction(
        action: String,
        filePath: String? = null,
        language: String? = null,
        duration: Long? = null
    ) {
        val event = TelemetryEvent.IDEInteraction(
            sessionId = sessionId,
            userId = userId,
            timestamp = Instant.now(),
            action = action,
            filePath = filePath,
            language = language,
            duration = duration
        )
        
        trackEvent(event)
        incrementMetric("ide_interactions")
    }
    
    /**
     * Track performance metric
     */
    fun trackPerformanceMetric(
        metricName: String,
        value: Double,
        unit: String? = null
    ) {
        val event = TelemetryEvent.PerformanceMetric(
            sessionId = sessionId,
            userId = userId,
            timestamp = Instant.now(),
            metricName = metricName,
            value = value,
            unit = unit
        )
        
        trackEvent(event)
    }
    
    /**
     * Track error
     */
    fun trackError(
        errorType: String,
        errorMessage: String,
        stackTrace: String? = null,
        context: Map<String, String> = emptyMap()
    ) {
        val event = TelemetryEvent.Error(
            sessionId = sessionId,
            userId = userId,
            timestamp = Instant.now(),
            errorType = errorType,
            errorMessage = errorMessage,
            stackTrace = stackTrace,
            context = context
        )
        
        trackEvent(event)
        incrementMetric("errors")
    }
    
    /**
     * Track user behavior
     */
    fun trackUserBehavior(
        behavior: String,
        details: Map<String, String> = emptyMap()
    ) {
        val event = TelemetryEvent.UserBehavior(
            sessionId = sessionId,
            userId = userId,
            timestamp = Instant.now(),
            behavior = behavior,
            details = details
        )
        
        trackEvent(event)
        incrementMetric("user_behaviors")
    }
    
    /**
     * Track file operations
     */
    fun trackFileOperation(
        operation: String, // "open", "save", "create", "delete"
        filePath: String,
        language: String? = null,
        fileSize: Long? = null
    ) {
        trackIDEInteraction(
            action = "file_$operation",
            filePath = filePath,
            language = language
        )
        
        trackUserBehavior(
            behavior = "file_operation",
            details = mapOf(
                "operation" to operation,
                "file_path" to filePath,
                "language" to (language ?: "unknown"),
                "file_size" to (fileSize?.toString() ?: "unknown")
            )
        )
    }
    
    /**
     * Track code completion
     */
    fun trackCodeCompletion(
        language: String,
        completionType: String, // "snippet", "function", "variable", etc.
        accepted: Boolean,
        responseTime: Long
    ) {
        trackIDEInteraction(
            action = "code_completion",
            language = language,
            duration = responseTime
        )
        
        trackUserBehavior(
            behavior = "code_completion",
            details = mapOf(
                "language" to language,
                "completion_type" to completionType,
                "accepted" to accepted.toString(),
                "response_time" to responseTime.toString()
            )
        )
        
        if (accepted) {
            incrementMetric("code_completion_accepted")
        } else {
            incrementMetric("code_completion_rejected")
        }
    }
    
    /**
     * Track Claude-code specific events
     */
    fun trackClaudeCodeEvent(
        eventType: String,
        details: Map<String, String> = emptyMap()
    ) {
        trackUserBehavior(
            behavior = "claude_code_$eventType",
            details = details
        )
    }
    
    /**
     * Track model usage
     */
    fun trackModelUsage(
        modelName: String,
        operation: String,
        duration: Long,
        success: Boolean
    ) {
        trackPerformanceMetric(
            metricName = "model_${operation}_duration",
            value = duration.toDouble(),
            unit = "ms"
        )
        
        trackUserBehavior(
            behavior = "model_usage",
            details = mapOf(
                "model" to modelName,
                "operation" to operation,
                "duration" to duration.toString(),
                "success" to success.toString()
            )
        )
    }
    
    /**
     * Get session statistics
     */
    fun getSessionStats(): Map<String, Any> {
        val sessionDuration = Duration.between(sessionStart, Instant.now())
        
        return mapOf(
            "session_id" to sessionId,
            "user_id" to (userId ?: "anonymous"),
            "session_start" to sessionStart.toString(),
            "session_duration_ms" to sessionDuration.toMillis(),
            "metrics" to metrics.mapValues { it.value.get() },
            "event_count" to getEventCount()
        )
    }
    
    /**
     * Export telemetry data
     */
    suspend fun exportTelemetryData(format: String = "json"): String {
        val events = mutableListOf<TelemetryEvent>()
        
        // Collect all events from channel
        while (!eventChannel.isEmpty) {
            events.add(eventChannel.receive())
        }
        
        return when (format.lowercase()) {
            "json" -> exportAsJson(events)
            "csv" -> exportAsCsv(events)
            else -> exportAsJson(events)
        }
    }
    
    /**
     * Shutdown telemetry system
     */
    suspend fun shutdown() {
        // Track session end
        trackEvent(TelemetryEvent.UserBehavior(
            sessionId = sessionId,
            userId = userId,
            timestamp = Instant.now(),
            behavior = "session_end",
            details = mapOf(
                "session_duration_ms" to Duration.between(sessionStart, Instant.now()).toMillis().toString()
            )
        ))
        
        // Close event channel
        eventChannel.close()
        
        // Cancel coroutine scope
        scope.cancel()
        
        // Export final data
        if (enablePersistence) {
            exportTelemetryData()
        }
    }
    
    // Private methods
    
    private fun trackEvent(event: TelemetryEvent) {
        if (enableRealTime) {
            scope.launch {
                eventChannel.send(event)
            }
        }
        
        if (enablePersistence) {
            scope.launch {
                persistEvent(event)
            }
        }
    }
    
    private fun incrementMetric(name: String) {
        metrics.computeIfAbsent(name) { AtomicLong(0) }.incrementAndGet()
    }
    
    private fun startEventProcessor() {
        scope.launch {
            for (event in eventChannel) {
                processEvent(event)
            }
        }
    }
    
    private suspend fun processEvent(event: TelemetryEvent) {
        // Real-time event processing
        when (event) {
            is TelemetryEvent.Error -> {
                // Log errors immediately
                println("❌ Cursor Telemetry Error: ${event.errorType} - ${event.errorMessage}")
            }
            is TelemetryEvent.CodeGeneration -> {
                // Track code generation performance
                if (event.success) {
                    trackPerformanceMetric(
                        "code_generation_success_rate",
                        1.0,
                        "percentage"
                    )
                }
            }
            else -> {
                // Process other events
            }
        }
    }
    
    private suspend fun persistEvent(event: TelemetryEvent) {
        try {
            val eventFile = telemetryDir.resolve("events_${sessionId}.jsonl")
            val eventJson = serializeEvent(event)
            
            Files.write(
                eventFile,
                "$eventJson\n".toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        } catch (e: Exception) {
            println("⚠️ Failed to persist telemetry event: ${e.message}")
        }
    }
    
    private fun serializeEvent(event: TelemetryEvent): String {
        // Simple JSON serialization for telemetry events
        return when (event) {
            is TelemetryEvent.SessionStart -> """
                {"type":"session_start","session_id":"${event.sessionId}","user_id":"${event.userId}","timestamp":"${event.timestamp}"}
            """.trimIndent()
            
            is TelemetryEvent.CodeGeneration -> """
                {"type":"code_generation","session_id":"${event.sessionId}","language":"${event.language}","response_time":${event.responseTime},"success":${event.success},"timestamp":"${event.timestamp}"}
            """.trimIndent()
            
            is TelemetryEvent.IDEInteraction -> """
                {"type":"ide_interaction","session_id":"${event.sessionId}","action":"${event.action}","timestamp":"${event.timestamp}"}
            """.trimIndent()
            
            is TelemetryEvent.PerformanceMetric -> """
                {"type":"performance_metric","session_id":"${event.sessionId}","metric_name":"${event.metricName}","value":${event.value},"timestamp":"${event.timestamp}"}
            """.trimIndent()
            
            is TelemetryEvent.Error -> """
                {"type":"error","session_id":"${event.sessionId}","error_type":"${event.errorType}","error_message":"${event.errorMessage}","timestamp":"${event.timestamp}"}
            """.trimIndent()
            
            is TelemetryEvent.UserBehavior -> """
                {"type":"user_behavior","session_id":"${event.sessionId}","behavior":"${event.behavior}","timestamp":"${event.timestamp}"}
            """.trimIndent()
        }
    }
    
    private fun exportAsJson(events: List<TelemetryEvent>): String {
        return buildString {
            appendLine("[")
            events.forEachIndexed { index, event ->
                append(serializeEvent(event))
                if (index < events.size - 1) appendLine(",")
            }
            appendLine("]")
        }
    }
    
    private fun exportAsCsv(events: List<TelemetryEvent>): String {
        return buildString {
            appendLine("type,session_id,timestamp,details")
            events.forEach { event ->
                val details = when (event) {
                    is TelemetryEvent.CodeGeneration -> "language=${event.language},success=${event.success}"
                    is TelemetryEvent.IDEInteraction -> "action=${event.action}"
                    is TelemetryEvent.PerformanceMetric -> "metric=${event.metricName},value=${event.value}"
                    is TelemetryEvent.Error -> "error_type=${event.errorType}"
                    is TelemetryEvent.UserBehavior -> "behavior=${event.behavior}"
                    is TelemetryEvent.SessionStart -> "session_start"
                }
                val eventSessionId = when (event) {
                    is TelemetryEvent.CodeGeneration -> event.sessionId
                    is TelemetryEvent.IDEInteraction -> event.sessionId
                    is TelemetryEvent.PerformanceMetric -> event.sessionId
                    is TelemetryEvent.Error -> event.sessionId
                    is TelemetryEvent.UserBehavior -> event.sessionId
                    is TelemetryEvent.SessionStart -> event.sessionId
                }
                val eventTimestamp = when (event) {
                    is TelemetryEvent.CodeGeneration -> event.timestamp
                    is TelemetryEvent.IDEInteraction -> event.timestamp
                    is TelemetryEvent.PerformanceMetric -> event.timestamp
                    is TelemetryEvent.Error -> event.timestamp
                    is TelemetryEvent.UserBehavior -> event.timestamp
                    is TelemetryEvent.SessionStart -> event.timestamp
                }
                appendLine("${event::class.simpleName},$eventSessionId,$eventTimestamp,\"$details\"")
            }
        }
    }
    
    private fun getEventCount(): Int {
        // Since Channel.size is not available, we'll return a placeholder
        // In a real implementation, you might want to track this separately
        return 0
    }
    
    // TODO: Add support for remote telemetry endpoints
    TODO("Implement remote telemetry endpoint support")
    
    // TODO: Add support for telemetry data compression
    TODO("Implement telemetry data compression")
    
    // TODO: Add support for telemetry data encryption
    TODO("Implement telemetry data encryption")
    
    // TODO: Add support for telemetry data anonymization
    TODO("Implement telemetry data anonymization")
    
    // TODO: Add support for telemetry data retention policies
    TODO("Implement telemetry data retention policies")
}

/**
 * Global telemetry instance for Cursor
 */
object CursorTelemetryGlobal {
    private var instance: CursorTelemetry? = null
    
    fun initialize(
        userId: String? = null,
        enablePersistence: Boolean = true,
        enableRealTime: Boolean = true
    ): CursorTelemetry {
        if (instance == null) {
            instance = CursorTelemetry(
                userId = userId,
                enablePersistence = enablePersistence,
                enableRealTime = enableRealTime
            )
        }
        return instance!!
    }
    
    fun getInstance(): CursorTelemetry? = instance
    
    suspend fun shutdown() {
        instance?.shutdown()
        instance = null
    }
} 