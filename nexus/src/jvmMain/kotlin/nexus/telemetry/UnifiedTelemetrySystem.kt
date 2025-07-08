package nexus.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified Telemetry System for all IDEs
 * 
 * Aggregates attention telemetry from IntelliJ, VS Code, and Eclipse
 * using the CCEK attention system
 */
class UnifiedTelemetrySystem {
    private val collector = TelemetryAttentionCollector()
    private val adapters = mutableListOf<IDETelemetryAdapter>()
    private val aggregatedMetrics = ConcurrentHashMap<String, MetricValue>()
    private val telemetryFlow = MutableSharedFlow<TelemetryEvent>()
    
    private var isRunning = false
    private var collectJob: Job? = null
    
    @Serializable
    data class TelemetryEvent(
        val timestamp: Long,
        val ide: String,
        val eventType: String,
        val data: JsonObject,
        val sessionId: String,
        val userId: String? = null
    )
    
    @Serializable
    data class MetricValue(
        val name: String,
        val value: Double,
        val unit: String,
        val tags: Map<String, String> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Initialize the telemetry system with IDE adapters
     */
    fun initialize() {
        adapters.add(IntelliJTelemetryAdapter())
        adapters.add(VSCodeTelemetryAdapter())
        adapters.add(EclipseTelemetryAdapter())
    }
    
    /**
     * Start collecting telemetry from all IDEs
     */
    suspend fun start() = coroutineScope {
        isRunning = true
        
        // Connect all adapters
        adapters.forEach { adapter ->
            launch {
                adapter.connect()
                adapter.startCollecting(collector)
            }
        }
        
        // Start aggregation job
        collectJob = launch {
            aggregateTelemetry()
        }
        
        // Start metrics computation
        launch {
            computeMetrics()
        }
        
        // Start persistence
        launch {
            persistTelemetry()
        }
    }
    
    /**
     * Stop telemetry collection
     */
    suspend fun stop() {
        isRunning = false
        collectJob?.cancel()
        
        adapters.forEach { it.stop() }
    }
    
    /**
     * Aggregate telemetry from all sources
     */
    private suspend fun aggregateTelemetry() {
        collector.getAttentionFlow().collect { event ->
            // Convert attention event to telemetry event
            val telemetryEvent = convertToTelemetryEvent(event)
            telemetryFlow.emit(telemetryEvent)
            
            // Update real-time metrics
            updateMetrics(event)
        }
    }
    
    /**
     * Compute derived metrics
     */
    private suspend fun computeMetrics() {
        while (isRunning) {
            delay(5000) // Compute every 5 seconds
            
            val analysis = collector.analyzeAttentionPatterns()
            
            // File focus metrics
            analysis.mostFocusedFiles.take(10).forEachIndexed { index, focus ->
                aggregatedMetrics["file.focus.top${index + 1}"] = MetricValue(
                    name = "file.focus.duration",
                    value = focus.totalDuration.toMinutes().toDouble(),
                    unit = "minutes",
                    tags = mapOf(
                        "file" to focus.filePath,
                        "ide" to focus.ide.name,
                        "rank" to (index + 1).toString()
                    )
                )
            }
            
            // Navigation metrics
            analysis.navigationPatterns.forEach { pattern ->
                aggregatedMetrics["nav.${pattern.type.name.lowercase()}.count"] = MetricValue(
                    name = "navigation.count",
                    value = pattern.count.toDouble(),
                    unit = "count",
                    tags = mapOf("type" to pattern.type.name)
                )
            }
            
            // Build metrics
            aggregatedMetrics["build.success.rate"] = MetricValue(
                name = "build.success.rate",
                value = analysis.buildPatterns.successRate,
                unit = "percentage"
            )
            
            aggregatedMetrics["build.avg.duration"] = MetricValue(
                name = "build.average.duration",
                value = analysis.buildPatterns.averageDuration.toSeconds().toDouble(),
                unit = "seconds"
            )
        }
    }
    
    /**
     * Update real-time metrics based on attention events
     */
    private fun updateMetrics(event: TelemetryAttentionCollector.AttentionEvent) {
        // Extract metrics from different attention types
        event.context[TelemetryKeys.FileAttention]?.let { fileAttention ->
            val key = "file.active.${fileAttention.ide.name.lowercase()}"
            aggregatedMetrics[key] = MetricValue(
                name = "file.active",
                value = 1.0,
                unit = "boolean",
                tags = mapOf(
                    "file" to fileAttention.filePath,
                    "ide" to fileAttention.ide.name
                )
            )
        }
        
        event.context[TelemetryKeys.BuildAttention]?.let { buildAttention ->
            if (buildAttention.success != null) {
                val key = "build.result.${buildAttention.ide.name.lowercase()}"
                aggregatedMetrics[key] = MetricValue(
                    name = "build.result",
                    value = if (buildAttention.success) 1.0 else 0.0,
                    unit = "boolean",
                    tags = mapOf(
                        "type" to buildAttention.buildType.name,
                        "ide" to buildAttention.ide.name
                    )
                )
            }
        }
        
        event.context[TelemetryKeys.SearchAttention]?.let { searchAttention ->
            val key = "search.results.${searchAttention.ide.name.lowercase()}"
            aggregatedMetrics[key] = MetricValue(
                name = "search.results.count",
                value = searchAttention.resultsCount.toDouble(),
                unit = "count",
                tags = mapOf(
                    "query" to searchAttention.query.take(50), // Truncate long queries
                    "type" to searchAttention.searchType.name,
                    "ide" to searchAttention.ide.name
                )
            )
        }
    }
    
    /**
     * Convert attention event to telemetry event
     */
    private fun convertToTelemetryEvent(
        event: TelemetryAttentionCollector.AttentionEvent
    ): TelemetryEvent {
        val ide = event.context[TelemetryKeys.FileAttention]?.ide
            ?: event.context[TelemetryKeys.CursorAttention]?.ide
            ?: event.context[TelemetryKeys.BuildAttention]?.ide
            ?: IDE.INTELLIJ // Default
        
        val eventType = when {
            event.context[TelemetryKeys.FileAttention] != null -> "file.attention"
            event.context[TelemetryKeys.CursorAttention] != null -> "cursor.attention"
            event.context[TelemetryKeys.SelectionAttention] != null -> "selection.attention"
            event.context[TelemetryKeys.NavigationAttention] != null -> "navigation.attention"
            event.context[TelemetryKeys.BuildAttention] != null -> "build.attention"
            event.context[TelemetryKeys.DebugAttention] != null -> "debug.attention"
            event.context[TelemetryKeys.SearchAttention] != null -> "search.attention"
            event.context[TelemetryKeys.RefactorAttention] != null -> "refactor.attention"
            else -> "unknown"
        }
        
        val data = buildJsonObject {
            // Add all attention elements as JSON
            event.context[TelemetryKeys.FileAttention]?.let {
                putJsonObject("fileAttention") {
                    put("filePath", it.filePath)
                    put("duration", it.duration.toMillis())
                }
            }
            event.context[TelemetryKeys.CursorAttention]?.let {
                putJsonObject("cursorAttention") {
                    put("line", it.position.line)
                    put("column", it.position.column)
                    put("file", it.position.file)
                }
            }
            // Add other attention types...
        }
        
        return TelemetryEvent(
            timestamp = event.timestamp.toEpochMilli(),
            ide = ide.name,
            eventType = eventType,
            data = data,
            sessionId = event.sessionId
        )
    }
    
    /**
     * Persist telemetry data
     */
    private suspend fun persistTelemetry() {
        val buffer = mutableListOf<TelemetryEvent>()
        val file = File("nexus-telemetry.jsonl")
        
        telemetryFlow.collect { event ->
            buffer.add(event)
            
            // Flush every 100 events or 10 seconds
            if (buffer.size >= 100) {
                flushToFile(buffer, file)
                buffer.clear()
            }
        }
    }
    
    private suspend fun flushToFile(events: List<TelemetryEvent>, file: File) = withContext(Dispatchers.IO) {
        file.appendText(events.joinToString("\n") { Json.encodeToString(it) } + "\n")
    }
    
    /**
     * Query telemetry data
     */
    fun getMetrics(): Map<String, MetricValue> = aggregatedMetrics.toMap()
    
    fun getMetric(name: String): MetricValue? = aggregatedMetrics[name]
    
    fun getTelemetryFlow(): Flow<TelemetryEvent> = telemetryFlow.asSharedFlow()
    
    /**
     * Generate telemetry report
     */
    suspend fun generateReport(duration: Duration = Duration.ofHours(24)): TelemetryReport {
        val endTime = Instant.now()
        val startTime = endTime.minus(duration)
        
        val events = mutableListOf<TelemetryEvent>()
        telemetryFlow
            .filter { it.timestamp >= startTime.toEpochMilli() }
            .take(10000) // Limit to 10k events
            .toList(events)
        
        return TelemetryReport(
            startTime = startTime,
            endTime = endTime,
            totalEvents = events.size,
            eventsByIDE = events.groupBy { it.ide }.mapValues { it.value.size },
            eventsByType = events.groupBy { it.eventType }.mapValues { it.value.size },
            topMetrics = aggregatedMetrics.values.sortedByDescending { it.value }.take(10),
            summary = generateSummary(events)
        )
    }
    
    private fun generateSummary(events: List<TelemetryEvent>): String {
        return buildString {
            appendLine("=== Telemetry Summary ===")
            appendLine("Total events: ${events.size}")
            appendLine("IDEs: ${events.map { it.ide }.distinct().joinToString()}")
            appendLine("Event types: ${events.map { it.eventType }.distinct().joinToString()}")
            appendLine("Sessions: ${events.map { it.sessionId }.distinct().size}")
        }
    }
}

@Serializable
data class TelemetryReport(
    @Contextual val startTime: Instant,
    @Contextual val endTime: Instant,
    val totalEvents: Int,
    val eventsByIDE: Map<String, Int>,
    val eventsByType: Map<String, Int>,
    val topMetrics: List<UnifiedTelemetrySystem.MetricValue>,
    val summary: String
)

/**
 * Telemetry server for remote collection
 */
class TelemetryServer(
    private val telemetrySystem: UnifiedTelemetrySystem,
    private val port: Int = 9999
) {
    suspend fun start() = coroutineScope {
        // Start HTTP server to receive telemetry from remote IDEs
        // This would integrate with the LSP server or run standalone
    }
}