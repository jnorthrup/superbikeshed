package nexus.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * CCEK-based Telemetry Attention System
 * 
 * Tracks developer attention patterns across IDEs using CoroutineContext.Element.Key
 */

// CCEK keys for different attention contexts
object TelemetryKeys {
    object FileAttention : CoroutineContext.Key<FileAttentionElement>
    object CursorAttention : CoroutineContext.Key<CursorAttentionElement>
    object SelectionAttention : CoroutineContext.Key<SelectionAttentionElement>
    object NavigationAttention : CoroutineContext.Key<NavigationAttentionElement>
    object BuildAttention : CoroutineContext.Key<BuildAttentionElement>
    object DebugAttention : CoroutineContext.Key<DebugAttentionElement>
    object SearchAttention : CoroutineContext.Key<SearchAttentionElement>
    object RefactorAttention : CoroutineContext.Key<RefactorAttentionElement>
}

// Attention elements that can be composed in coroutine context
data class FileAttentionElement(
    val filePath: String,
    val startTime: Instant,
    var duration: Duration = Duration.ZERO,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.FileAttention
}

data class CursorAttentionElement(
    val position: CursorPosition,
    val timestamp: Instant,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.CursorAttention
}

data class SelectionAttentionElement(
    val selection: TextSelection,
    val timestamp: Instant,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.SelectionAttention
}

data class NavigationAttentionElement(
    val from: Location,
    val to: Location,
    val navigationType: NavigationType,
    val timestamp: Instant,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.NavigationAttention
}

data class BuildAttentionElement(
    val buildType: BuildType,
    val startTime: Instant,
    var duration: Duration = Duration.ZERO,
    val success: Boolean? = null,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.BuildAttention
}

data class DebugAttentionElement(
    val breakpoint: Breakpoint?,
    val stackFrame: StackFrame?,
    val timestamp: Instant,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.DebugAttention
}

data class SearchAttentionElement(
    val query: String,
    val searchType: SearchType,
    val resultsCount: Int,
    val timestamp: Instant,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.SearchAttention
}

data class RefactorAttentionElement(
    val refactorType: RefactorType,
    val scope: RefactorScope,
    val timestamp: Instant,
    val ide: IDE
) : CoroutineContext.Element {
    override val key = TelemetryKeys.RefactorAttention
}

// Supporting data types
enum class IDE { INTELLIJ, VSCODE, ECLIPSE }
data class CursorPosition(val line: Int, val column: Int, val file: String)
data class TextSelection(val startLine: Int, val startCol: Int, val endLine: Int, val endCol: Int, val file: String)
data class Location(val file: String, val line: Int, val symbol: String?)
enum class NavigationType { GOTO_DEFINITION, FIND_USAGES, GOTO_IMPLEMENTATION, GOTO_TYPE }
enum class BuildType { FULL, INCREMENTAL, TEST, CLEAN }
data class Breakpoint(val file: String, val line: Int, val condition: String?)
data class StackFrame(val method: String, val file: String, val line: Int)
enum class SearchType { TEXT, SYMBOL, FILE, USAGE }
enum class RefactorType { RENAME, EXTRACT_METHOD, INLINE, MOVE, CHANGE_SIGNATURE }
data class RefactorScope(val files: List<String>, val symbols: List<String>)

/**
 * Telemetry Attention Collector using CCEK composition
 */
class TelemetryAttentionCollector {
    private val attentionFlow = MutableSharedFlow<AttentionEvent>()
    private val attentionContexts = mutableMapOf<String, CoroutineContext>()
    
    data class AttentionEvent(
        val context: CoroutineContext,
        val timestamp: Instant,
        val sessionId: String
    )
    
    // Collect attention with CCEK context
    suspend fun collectAttention(
        sessionId: String,
        builder: suspend CoroutineScope.() -> CoroutineContext
    ) = coroutineScope {
        val context = builder()
        
        attentionContexts[sessionId] = context
        attentionFlow.emit(AttentionEvent(context, Instant.now(), sessionId))
    }
    
    // Track file attention
    suspend fun trackFileAttention(
        sessionId: String,
        filePath: String,
        ide: IDE
    ) = collectAttention(sessionId) {
        coroutineContext + FileAttentionElement(filePath, Instant.now(), ide = ide)
    }
    
    // Track cursor movement
    suspend fun trackCursorAttention(
        sessionId: String,
        position: CursorPosition,
        ide: IDE
    ) = collectAttention(sessionId) {
        coroutineContext + CursorAttentionElement(position, Instant.now(), ide)
    }
    
    // Track code selection
    suspend fun trackSelectionAttention(
        sessionId: String,
        selection: TextSelection,
        ide: IDE
    ) = collectAttention(sessionId) {
        coroutineContext + SelectionAttentionElement(selection, Instant.now(), ide)
    }
    
    // Track navigation
    suspend fun trackNavigationAttention(
        sessionId: String,
        from: Location,
        to: Location,
        navigationType: NavigationType,
        ide: IDE
    ) = collectAttention(sessionId) {
        coroutineContext + NavigationAttentionElement(from, to, navigationType, Instant.now(), ide)
    }
    
    // Compose multiple attention types
    suspend fun trackCompositeAttention(
        sessionId: String,
        fileAttention: FileAttentionElement? = null,
        cursorAttention: CursorAttentionElement? = null,
        searchAttention: SearchAttentionElement? = null
    ) = collectAttention(sessionId) {
        var ctx = coroutineContext
        fileAttention?.let { ctx = ctx + it }
        cursorAttention?.let { ctx = ctx + it }
        searchAttention?.let { ctx = ctx + it }
        ctx
    }
    
    // Query attention patterns
    fun getAttentionFlow(): Flow<AttentionEvent> = attentionFlow.asSharedFlow()
    
    fun getCurrentContext(sessionId: String): CoroutineContext? = 
        attentionContexts[sessionId]
    
    // Attention pattern analysis
    suspend fun analyzeAttentionPatterns(): AttentionAnalysis {
        val events = mutableListOf<AttentionEvent>()
        attentionFlow
            .take(1000) // Last 1000 events
            .collect { events.add(it) }
        
        return AttentionAnalysis(
            mostFocusedFiles = analyzeMostFocusedFiles(events),
            navigationPatterns = analyzeNavigationPatterns(events),
            buildPatterns = analyzeBuildPatterns(events),
            attentionHeatmap = generateAttentionHeatmap(events)
        )
    }
    
    private fun analyzeMostFocusedFiles(events: List<AttentionEvent>): List<FileFocus> {
        return events
            .mapNotNull { event ->
                event.context[TelemetryKeys.FileAttention]?.let { element ->
                    FileFocus(element.filePath, element.duration, element.ide)
                }
            }
            .groupBy { it.filePath }
            .map { (file, focuses) ->
                FileFocus(
                    file,
                    focuses.map { focus -> focus.totalDuration }.fold(Duration.ZERO) { acc, d -> acc.plus(d) },
                    focuses.first().ide
                )
            }
            .sortedByDescending { it.totalDuration }
    }
    
    private fun analyzeNavigationPatterns(events: List<AttentionEvent>): List<NavigationPattern> {
        return events
            .mapNotNull { it.context[TelemetryKeys.NavigationAttention] }
            .groupBy { it.navigationType }
            .map { (type, navs) ->
                NavigationPattern(type, navs.size, navs.groupBy { it.ide })
            }
    }
    
    private fun analyzeBuildPatterns(events: List<AttentionEvent>): BuildAnalysis {
        val builds = events.mapNotNull { it.context[TelemetryKeys.BuildAttention] }
        return BuildAnalysis(
            totalBuilds = builds.size,
            successRate = builds.count { it.success == true }.toDouble() / builds.size,
            averageDuration = builds.map { it.duration }.average(),
            buildsByIDE = builds.groupBy { it.ide }
        )
    }
    
    private fun generateAttentionHeatmap(events: List<AttentionEvent>): AttentionHeatmap {
        // Group events by hour of day and file
        val hourlyAttention = events
            .filter { it.context[TelemetryKeys.FileAttention] != null }
            .groupBy { 
                val hour = it.timestamp.atZone(java.time.ZoneId.systemDefault()).hour
                hour to it.context[TelemetryKeys.FileAttention]!!.filePath
            }
        
        return AttentionHeatmap(hourlyAttention)
    }
}

// Analysis result types
data class AttentionAnalysis(
    val mostFocusedFiles: List<FileFocus>,
    val navigationPatterns: List<NavigationPattern>,
    val buildPatterns: BuildAnalysis,
    val attentionHeatmap: AttentionHeatmap
)

data class FileFocus(val filePath: String, val totalDuration: Duration, val ide: IDE)
data class NavigationPattern(val type: NavigationType, val count: Int, val byIDE: Map<IDE, List<NavigationAttentionElement>>)
data class BuildAnalysis(val totalBuilds: Int, val successRate: Double, val averageDuration: Duration, val buildsByIDE: Map<IDE, List<BuildAttentionElement>>)
data class AttentionHeatmap(val hourlyData: Map<Pair<Int, String>, List<TelemetryAttentionCollector.AttentionEvent>>)

// Extension functions for Duration operations
private fun List<Duration>.average(): Duration {
    if (isEmpty()) return Duration.ZERO
    val totalMillis = sumOf { it.toMillis() }
    return Duration.ofMillis(totalMillis / size)
}

/**
 * IDE-specific telemetry adapters
 */
interface IDETelemetryAdapter {
    suspend fun connect()
    suspend fun startCollecting(collector: TelemetryAttentionCollector)
    suspend fun stop()
}

class IntelliJTelemetryAdapter : IDETelemetryAdapter {
    override suspend fun connect() {
        // Connect to IntelliJ's event system via plugin API
    }
    
    override suspend fun startCollecting(collector: TelemetryAttentionCollector) {
        // Map IntelliJ events to attention events
    }
    
    override suspend fun stop() {
        // Cleanup
    }
}

class VSCodeTelemetryAdapter : IDETelemetryAdapter {
    override suspend fun connect() {
        // Connect to VS Code extension host
    }
    
    override suspend fun startCollecting(collector: TelemetryAttentionCollector) {
        // Map VS Code events to attention events
    }
    
    override suspend fun stop() {
        // Cleanup
    }
}

class EclipseTelemetryAdapter : IDETelemetryAdapter {
    override suspend fun connect() {
        // Connect to Eclipse platform
    }
    
    override suspend fun startCollecting(collector: TelemetryAttentionCollector) {
        // Map Eclipse events to attention events
    }
    
    override suspend fun stop() {
        // Cleanup
    }
}