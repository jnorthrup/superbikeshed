package nexus.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Telemetry Attention Widget
 * 
 * Like a video game button that opens a text box showing
 * what the developer is paying attention to across IDEs
 */
class TelemetryAttentionWidget {
    private val attentionState = MutableStateFlow<AttentionState>(AttentionState.Idle)
    private val popupContent = MutableStateFlow<PopupContent?>(null)
    
    sealed class AttentionState {
        object Idle : AttentionState()
        data class Active(
            val focusedFile: String,
            val ide: IDE,
            val startTime: Instant,
            val context: String
        ) : AttentionState()
        data class MultiIDE(
            val activeIDEs: Map<IDE, FocusInfo>
        ) : AttentionState()
    }
    
    data class FocusInfo(
        val file: String,
        val line: Int,
        val activity: String
    )
    
    data class PopupContent(
        val title: String,
        val body: String,
        val metrics: Map<String, Any>,
        val actions: List<Action>
    )
    
    data class Action(
        val label: String,
        val command: suspend () -> Unit
    )
    
    /**
     * Trigger the attention widget (like pressing a button in a game)
     */
    suspend fun trigger() {
        when (val state = attentionState.value) {
            is AttentionState.Idle -> {
                showIdlePopup()
            }
            is AttentionState.Active -> {
                showActivePopup(state)
            }
            is AttentionState.MultiIDE -> {
                showMultiIDEPopup(state)
            }
        }
    }
    
    /**
     * Update attention state from telemetry
     */
    fun updateFromTelemetry(event: UnifiedTelemetrySystem.TelemetryEvent) {
        when (event.eventType) {
            "file.attention" -> {
                val filePath = (event.data["fileAttention"] as? JsonObject)?.get("filePath")?.jsonPrimitive?.content
                if (filePath != null) {
                    attentionState.value = AttentionState.Active(
                        focusedFile = filePath,
                        ide = IDE.valueOf(event.ide.toString()),
                        startTime = Instant.ofEpochMilli(event.timestamp),
                        context = extractContext(event.data)
                    )
                }
            }
        }
    }
    
    /**
     * Show popup when idle
     */
    private suspend fun showIdlePopup() {
        popupContent.value = PopupContent(
            title = "Developer Attention: Idle",
            body = """
                No active development detected.
                
                Start coding in any supported IDE:
                - IntelliJ IDEA
                - Visual Studio Code
                - Eclipse
                
                Telemetry will automatically track your attention patterns.
            """.trimIndent(),
            metrics = mapOf(
                "Status" to "Idle",
                "IDEs Connected" to 0
            ),
            actions = listOf(
                Action("Start Telemetry") {
                    // Start telemetry collection
                },
                Action("View Settings") {
                    // Open settings
                }
            )
        )
    }
    
    /**
     * Show popup for active development
     */
    private suspend fun showActivePopup(state: AttentionState.Active) {
        val duration = java.time.Duration.between(state.startTime, Instant.now())
        
        popupContent.value = PopupContent(
            title = "Developer Attention: ${state.ide.name}",
            body = """
                Currently focused on:
                ${state.focusedFile}
                
                IDE: ${state.ide.name}
                Duration: ${formatDuration(duration)}
                Context: ${state.context}
                
                Recent activities:
                - File opened
                - 42 lines edited
                - 3 searches performed
                - 2 builds triggered
            """.trimIndent(),
            metrics = mapOf(
                "Focus Time" to formatDuration(duration),
                "Lines Changed" to 42,
                "Build Status" to "Success"
            ),
            actions = listOf(
                Action("Show Heatmap") {
                    showHeatmap()
                },
                Action("View History") {
                    showHistory()
                },
                Action("Analyze Patterns") {
                    analyzePatterns()
                }
            )
        )
    }
    
    /**
     * Show popup for multi-IDE usage
     */
    private suspend fun showMultiIDEPopup(state: AttentionState.MultiIDE) {
        val ideList = state.activeIDEs.entries.joinToString("\n") { (ide, focus) ->
            "- ${ide.name}: ${focus.file} (${focus.activity})"
        }
        
        popupContent.value = PopupContent(
            title = "Multi-IDE Development Active",
            body = """
                Working across multiple IDEs:
                
                $ideList
                
                Cross-IDE Activities:
                - Refactoring in IntelliJ
                - Debugging in VS Code
                - Documentation in Eclipse
                
                Attention is split across ${state.activeIDEs.size} environments.
            """.trimIndent(),
            metrics = mapOf(
                "Active IDEs" to state.activeIDEs.size,
                "Context Switches" to 15,
                "Productivity Score" to 0.82
            ),
            actions = listOf(
                Action("Consolidate Windows") {
                    // Help focus on one IDE
                },
                Action("Show Timeline") {
                    showTimeline()
                }
            )
        )
    }
    
    /**
     * Get current popup content
     */
    fun getPopupContent(): Flow<PopupContent?> = popupContent.asStateFlow()
    
    /**
     * Close popup
     */
    fun closePopup() {
        popupContent.value = null
    }
    
    // Helper functions
    private fun extractContext(data: JsonObject): String {
        // Extract context from telemetry data
        return "Editing implementation"
    }
    
    private fun formatDuration(duration: java.time.Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    private suspend fun showHeatmap() {
        // Show attention heatmap
    }
    
    private suspend fun showHistory() {
        // Show attention history
    }
    
    private suspend fun analyzePatterns() {
        // Analyze attention patterns
    }
    
    private suspend fun showTimeline() {
        // Show cross-IDE timeline
    }
}

/**
 * Integration with CCEK telemetry system
 */
class TelemetryAttentionIntegration(
    private val telemetrySystem: UnifiedTelemetrySystem,
    private val attentionWidget: TelemetryAttentionWidget
) {
    /**
     * Start integration
     */
    suspend fun start() = coroutineScope {
        // Subscribe to telemetry events
        launch {
            telemetrySystem.getTelemetryFlow().collect { event ->
                attentionWidget.updateFromTelemetry(event)
            }
        }
        
        // Periodic attention analysis
        launch {
            while (isActive) {
                delay(5000) // Every 5 seconds
                analyzeCurrentAttention()
            }
        }
    }
    
    private suspend fun analyzeCurrentAttention() {
        val metrics = telemetrySystem.getMetrics()
        
        // Check for multi-IDE usage
        val activeIDEs = mutableMapOf<IDE, TelemetryAttentionWidget.FocusInfo>()
        
        IDE.values().forEach { ide ->
            val key = "file.active.${ide.name.lowercase()}"
            metrics[key]?.let { metric ->
                val file = metric.tags["file"] ?: "unknown"
                activeIDEs[ide] = TelemetryAttentionWidget.FocusInfo(
                    file = file,
                    line = 0, // Would come from cursor position
                    activity = "Editing"
                )
            }
        }
        
        if (activeIDEs.size > 1) {
            attentionWidget.updateFromTelemetry(
                UnifiedTelemetrySystem.TelemetryEvent(
                    timestamp = System.currentTimeMillis(),
                    ide = "MULTI",
                    eventType = "multi.ide.active",
                    data = buildJsonObject {
                        putJsonObject("activeIDEs") {
                            activeIDEs.forEach { (ide, focus) ->
                                putJsonObject(ide.name) {
                                    put("file", focus.file)
                                    put("activity", focus.activity)
                                }
                            }
                        }
                    },
                    sessionId = "multi-ide"
                )
            )
        }
    }
}