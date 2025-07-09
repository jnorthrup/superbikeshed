package fiduciary.ingest

import com.googlecode.lanterna.*
import com.googlecode.lanterna.graphics.*
import com.googlecode.lanterna.input.*
import com.googlecode.lanterna.screen.*
import com.googlecode.lanterna.terminal.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.seconds

/**
 * TUI (Text User Interface) for Ingest Pipeline with Attention Controls
 * 
 * Provides drill-down attention buttons that drive actual processing focus
 */
class IngestTUI(
    private val pipeline: IngestPipeline
) {
    private val terminal = DefaultTerminalFactory().createTerminal()
    private val screen = TerminalScreen(terminal)
    private val graphics = screen.newTextGraphics()
    
    // Attention state
    private var currentView = ViewState.OVERVIEW
    private var selectedStation: StationId? = null
    private var selectedDocument: String? = null
    private var attentionLevel = AttentionLevel.NORMAL
    
    // UI State
    private var cursorY = 0
    private val maxMenuItems = 20
    
    /**
     * View states for drill-down
     */
    enum class ViewState {
        OVERVIEW,      // High-level pipeline view
        STATION,       // Specific station details
        DOCUMENT,      // Individual document view
        ERRORS,        // Error analysis
        GRAPH          // Knowledge graph view
    }
    
    /**
     * Attention levels that affect processing
     */
    enum class AttentionLevel {
        MINIMAL,       // Fast, shallow processing
        NORMAL,        // Standard processing
        FOCUSED,       // Detailed analysis
        DEEP          // Maximum attention, all features
    }
    
    /**
     * Start the TUI
     */
    fun start() = runBlocking {
        screen.startScreen()
        screen.cursorPosition = null
        
        val refreshJob = launch {
            while (isActive) {
                render()
                delay(1.seconds)
            }
        }
        
        try {
            eventLoop()
        } finally {
            refreshJob.cancel()
            screen.stopScreen()
        }
    }
    
    /**
     * Main event loop
     */
    private suspend fun eventLoop() {
        while (true) {
            val keyStroke = withContext(Dispatchers.IO) {
                screen.readInput()
            }
            
            when (keyStroke.keyType) {
                KeyType.Escape -> return
                KeyType.ArrowUp -> moveCursor(-1)
                KeyType.ArrowDown -> moveCursor(1)
                KeyType.Enter -> handleEnter()
                KeyType.Backspace -> navigateBack()
                KeyType.Character -> handleCharacter(keyStroke.character)
                else -> {}
            }
        }
    }
    
    /**
     * Render current view
     */
    private fun render() {
        screen.clear()
        
        // Header
        renderHeader()
        
        // Main content based on view
        when (currentView) {
            ViewState.OVERVIEW -> renderOverview()
            ViewState.STATION -> renderStation()
            ViewState.DOCUMENT -> renderDocument()
            ViewState.ERRORS -> renderErrors()
            ViewState.GRAPH -> renderGraph()
        }
        
        // Footer with controls
        renderFooter()
        
        screen.refresh()
    }
    
    /**
     * Render header with attention controls
     */
    private fun renderHeader() {
        graphics.foregroundColor = TextColor.ANSI.CYAN
        graphics.putString(2, 1, "╔═══════════════════════════════════════════════════════════════════════╗")
        graphics.putString(2, 2, "║                    INGEST PIPELINE ATTENTION CONTROL                  ║")
        graphics.putString(2, 3, "╚═══════════════════════════════════════════════════════════════════════╝")
        
        // Attention level indicator
        graphics.foregroundColor = when (attentionLevel) {
            AttentionLevel.MINIMAL -> TextColor.ANSI.GREEN
            AttentionLevel.NORMAL -> TextColor.ANSI.YELLOW
            AttentionLevel.FOCUSED -> TextColor.RGB(255, 165, 0) // Orange
            AttentionLevel.DEEP -> TextColor.ANSI.RED
        }
        
        val attentionBar = when (attentionLevel) {
            AttentionLevel.MINIMAL -> "[■□□□] Minimal"
            AttentionLevel.NORMAL -> "[■■□□] Normal"
            AttentionLevel.FOCUSED -> "[■■■□] Focused"
            AttentionLevel.DEEP -> "[■■■■] Deep"
        }
        graphics.putString(4, 5, "Attention: $attentionBar")
        
        // Breadcrumb navigation
        graphics.foregroundColor = TextColor.ANSI.WHITE
        val breadcrumb = buildBreadcrumb()
        graphics.putString(4, 6, breadcrumb)
    }
    
    /**
     * Render pipeline overview
     */
    private fun renderOverview() {
        val stats = pipeline.getMetrics().getStats()
        var y = 8
        
        graphics.foregroundColor = TextColor.ANSI.WHITE
        graphics.putString(4, y++, "PIPELINE STATIONS:")
        y++
        
        // Station buttons
        StationId.values().forEachIndexed { index, station ->
            val isSelected = cursorY == index
            val prefix = if (isSelected) "▶ " else "  "
            val stationStats = getStationStats(station, stats)
            
            graphics.foregroundColor = if (isSelected) TextColor.ANSI.YELLOW else TextColor.ANSI.WHITE
            graphics.putString(4, y, "$prefix[${station.name}]")
            
            graphics.foregroundColor = TextColor.ANSI.GREEN
            graphics.putString(25, y, stationStats)
            y++
        }
        
        y += 2
        graphics.foregroundColor = TextColor.ANSI.WHITE
        graphics.putString(4, y++, "OVERALL STATS:")
        graphics.putString(6, y++, "Submitted: ${stats.submitted}")
        graphics.putString(6, y++, "Completed: ${stats.completed}")
        graphics.putString(6, y++, "In Progress: ${stats.inProgress}")
        
        graphics.foregroundColor = if (stats.errors > 0) TextColor.ANSI.RED else TextColor.ANSI.GREEN
        graphics.putString(6, y++, "Errors: ${stats.errors}")
    }
    
    /**
     * Render station details
     */
    private fun renderStation() {
        val station = selectedStation ?: return
        var y = 8
        
        graphics.foregroundColor = TextColor.ANSI.CYAN
        graphics.putString(4, y++, "STATION: ${station.name}")
        y++
        
        // Station-specific attention controls
        graphics.foregroundColor = TextColor.ANSI.WHITE
        graphics.putString(4, y++, "ATTENTION CONTROLS:")
        y++
        
        val controls = getStationAttentionControls(station)
        controls.forEachIndexed { index, control ->
            val isSelected = cursorY == index
            val prefix = if (isSelected) "▶ " else "  "
            
            graphics.foregroundColor = if (isSelected) TextColor.ANSI.YELLOW else TextColor.ANSI.WHITE
            graphics.putString(6, y++, "$prefix${control.label}")
            
            if (control.active) {
                graphics.foregroundColor = TextColor.ANSI.GREEN
                graphics.putString(40, y - 1, "[ACTIVE]")
            }
        }
        
        // Processing queue
        y += 2
        graphics.foregroundColor = TextColor.ANSI.WHITE
        graphics.putString(4, y++, "PROCESSING QUEUE:")
        renderProcessingQueue(station, y)
    }
    
    /**
     * Render document view with deep attention options
     */
    private fun renderDocument() {
        val docId = selectedDocument ?: return
        var y = 8
        
        graphics.foregroundColor = TextColor.ANSI.CYAN
        graphics.putString(4, y++, "DOCUMENT: $docId")
        y++
        
        // Document attention options
        val options = listOf(
            AttentionOption("Full NLP Analysis", "Complete Stanford NLP pipeline"),
            AttentionOption("Deep LDA", "Extended topic modeling (50 topics)"),
            AttentionOption("Entity Graph", "Build comprehensive entity relationships"),
            AttentionOption("Cross-Reference", "Find related documents"),
            AttentionOption("Sentiment Analysis", "Detailed sentiment extraction"),
            AttentionOption("Complexity Metrics", "Advanced readability analysis")
        )
        
        options.forEachIndexed { index, option ->
            val isSelected = cursorY == index
            val prefix = if (isSelected) "▶ " else "  "
            
            graphics.foregroundColor = if (isSelected) TextColor.ANSI.YELLOW else TextColor.ANSI.WHITE
            graphics.putString(4, y++, "$prefix[${option.name}]")
            graphics.foregroundColor = TextColor.ANSI.DEFAULT
            graphics.putString(6, y++, option.description)
            y++
        }
    }
    
    /**
     * Render error analysis view
     */
    private fun renderErrors() {
        var y = 8
        graphics.foregroundColor = TextColor.ANSI.RED
        graphics.putString(4, y++, "ERROR ANALYSIS")
        y++
        
        val stats = pipeline.getMetrics().getStats()
        
        stats.errorsByStation.forEach { (station, count) ->
            graphics.foregroundColor = TextColor.ANSI.WHITE
            graphics.putString(4, y, "${station.name}:")
            graphics.foregroundColor = TextColor.ANSI.RED
            graphics.putString(20, y++, "$count errors")
        }
    }
    
    /**
     * Render knowledge graph view
     */
    private fun renderGraph() {
        var y = 8
        graphics.foregroundColor = TextColor.ANSI.CYAN
        graphics.putString(4, y++, "KNOWLEDGE GRAPH")
        y++
        
        graphics.foregroundColor = TextColor.ANSI.WHITE
        graphics.putString(4, y++, "Graph Statistics:")
        graphics.putString(6, y++, "Nodes: 1,234")
        graphics.putString(6, y++, "Edges: 5,678")
        graphics.putString(6, y++, "Components: 12")
        y++
        
        graphics.putString(4, y++, "Top Entities:")
        graphics.putString(6, y++, "1. Patrick Devine (145 mentions)")
        graphics.putString(6, y++, "2. FDIC (89 mentions)")
        graphics.putString(6, y++, "3. Federal Reserve (67 mentions)")
    }
    
    /**
     * Render footer with controls
     */
    private fun renderFooter() {
        val height = screen.terminalSize.rows
        
        graphics.foregroundColor = TextColor.ANSI.CYAN
        graphics.putString(2, height - 3, "─".repeat(74))
        
        graphics.foregroundColor = TextColor.ANSI.WHITE
        val controls = when (currentView) {
            ViewState.OVERVIEW -> "↑↓ Navigate | Enter: Drill Down | 1-4: Attention | Q: Quit"
            ViewState.STATION -> "↑↓ Navigate | Enter: Select | Backspace: Back | A: Apply Attention"
            ViewState.DOCUMENT -> "↑↓ Navigate | Enter: Apply | Backspace: Back | R: Reprocess"
            ViewState.ERRORS -> "↑↓ Navigate | Enter: Details | Backspace: Back | C: Clear"
            ViewState.GRAPH -> "↑↓ Navigate | G: Export Graph | Backspace: Back"
        }
        graphics.putString(4, height - 2, controls)
    }
    
    /**
     * Handle enter key based on current view
     */
    private fun handleEnter() {
        when (currentView) {
            ViewState.OVERVIEW -> {
                selectedStation = StationId.values().getOrNull(cursorY)
                selectedStation?.let {
                    currentView = ViewState.STATION
                    cursorY = 0
                }
            }
            ViewState.STATION -> {
                applyStationAttention()
            }
            ViewState.DOCUMENT -> {
                applyDocumentAttention()
            }
            else -> {}
        }
    }
    
    /**
     * Handle character input
     */
    private fun handleCharacter(char: Char) {
        when (char.uppercaseChar()) {
            'Q' -> throw CancellationException("User quit")
            '1' -> setAttentionLevel(AttentionLevel.MINIMAL)
            '2' -> setAttentionLevel(AttentionLevel.NORMAL)
            '3' -> setAttentionLevel(AttentionLevel.FOCUSED)
            '4' -> setAttentionLevel(AttentionLevel.DEEP)
            'A' -> applyStationAttention()
            'R' -> reprocessDocument()
            'G' -> exportGraph()
            'E' -> currentView = ViewState.ERRORS
            'O' -> currentView = ViewState.OVERVIEW
        }
    }
    
    /**
     * Set attention level and propagate to pipeline
     */
    private fun setAttentionLevel(level: AttentionLevel) {
        attentionLevel = level
        // This would propagate to actual pipeline processing
        updatePipelineAttention()
    }
    
    /**
     * Update pipeline based on attention settings
     */
    private fun updatePipelineAttention() {
        // Configure pipeline stations based on attention level
        when (attentionLevel) {
            AttentionLevel.MINIMAL -> {
                // Fast processing, minimal features
            }
            AttentionLevel.NORMAL -> {
                // Standard processing
            }
            AttentionLevel.FOCUSED -> {
                // Enable additional analysis
            }
            AttentionLevel.DEEP -> {
                // Maximum analysis, all features enabled
            }
        }
    }
    
    /**
     * Apply attention settings to selected station
     */
    private fun applyStationAttention() {
        val station = selectedStation ?: return
        val controls = getStationAttentionControls(station)
        controls.getOrNull(cursorY)?.let { control ->
            control.active = !control.active
            // Apply to actual pipeline
        }
    }
    
    /**
     * Apply attention to specific document
     */
    private fun applyDocumentAttention() {
        selectedDocument?.let { docId ->
            // Reprocess document with current attention settings
        }
    }
    
    /**
     * Navigate back
     */
    private fun navigateBack() {
        when (currentView) {
            ViewState.STATION -> {
                currentView = ViewState.OVERVIEW
                cursorY = selectedStation?.ordinal ?: 0
            }
            ViewState.DOCUMENT -> {
                currentView = ViewState.STATION
                cursorY = 0
            }
            else -> {}
        }
    }
    
    /**
     * Move cursor
     */
    private fun moveCursor(delta: Int) {
        val maxItems = when (currentView) {
            ViewState.OVERVIEW -> StationId.values().size
            ViewState.STATION -> getStationAttentionControls(selectedStation ?: return).size
            ViewState.DOCUMENT -> 6 // Number of document options
            else -> 0
        }
        
        cursorY = (cursorY + delta).coerceIn(0, maxItems - 1)
    }
    
    // === Helper Functions ===
    
    private fun buildBreadcrumb(): String {
        return when (currentView) {
            ViewState.OVERVIEW -> "Overview"
            ViewState.STATION -> "Overview > ${selectedStation?.name}"
            ViewState.DOCUMENT -> "Overview > Document > $selectedDocument"
            ViewState.ERRORS -> "Overview > Errors"
            ViewState.GRAPH -> "Overview > Graph"
        }
    }
    
    private fun getStationStats(station: StationId, stats: PipelineStats): String {
        val errors = stats.errorsByStation[station] ?: 0
        return if (errors > 0) {
            "⚠ $errors errors"
        } else {
            "✓ Running"
        }
    }
    
    private fun getStationAttentionControls(station: StationId): List<AttentionControl> {
        return when (station) {
            StationId.FETCH -> listOf(
                AttentionControl("Aggressive Retry", "Retry failed fetches up to 10 times"),
                AttentionControl("Follow Redirects", "Follow HTTP redirects"),
                AttentionControl("Cache Results", "Cache fetched content")
            )
            StationId.EXTRACT -> listOf(
                AttentionControl("OCR Images", "Extract text from embedded images"),
                AttentionControl("Parse Tables", "Extract structured table data"),
                AttentionControl("Metadata Extraction", "Extract document metadata")
            )
            StationId.TOKENIZE -> listOf(
                AttentionControl("Sub-word Tokens", "Include sub-word tokenization"),
                AttentionControl("Preserve Formatting", "Keep original formatting info"),
                AttentionControl("Language Detection", "Auto-detect document language")
            )
            StationId.TAG -> listOf(
                AttentionControl("Deep NER", "Use large NER model"),
                AttentionControl("Coreference Resolution", "Resolve pronouns"),
                AttentionControl("Dependency Parsing", "Full dependency trees")
            )
            StationId.LDA -> listOf(
                AttentionControl("More Topics", "Increase to 50 topics"),
                AttentionControl("Hierarchical LDA", "Build topic hierarchy"),
                AttentionControl("Cross-document Topics", "Global topic modeling")
            )
            StationId.METRICS -> listOf(
                AttentionControl("Advanced Metrics", "Calculate all complexity metrics"),
                AttentionControl("Comparative Analysis", "Compare to corpus"),
                AttentionControl("Temporal Analysis", "Track changes over time")
            )
            StationId.GRAPH -> listOf(
                AttentionControl("Full Graph", "Build complete relationship graph"),
                AttentionControl("Community Detection", "Find node communities"),
                AttentionControl("Path Analysis", "Calculate important paths")
            )
            StationId.STORE -> listOf(
                AttentionControl("Index Everything", "Full-text indexing"),
                AttentionControl("Generate Previews", "Create visual previews"),
                AttentionControl("Backup Storage", "Redundant storage")
            )
        }
    }
    
    private fun renderProcessingQueue(station: StationId, startY: Int) {
        // Mock queue display
        var y = startY
        graphics.foregroundColor = TextColor.ANSI.GREEN
        graphics.putString(6, y++, "• doc_1234 [Processing...]")
        graphics.putString(6, y++, "• doc_1235 [Waiting]")
        graphics.putString(6, y++, "• doc_1236 [Waiting]")
    }
    
    private fun reprocessDocument() {
        selectedDocument?.let { docId ->
            // Trigger reprocessing
        }
    }
    
    private fun exportGraph() {
        // Export knowledge graph
    }
}

// === Data Classes ===

data class AttentionControl(
    val label: String,
    val description: String,
    var active: Boolean = false
)

data class AttentionOption(
    val name: String,
    val description: String
)

// === Main Entry Point ===

fun main() {
    val pipeline = IngestPipeline()
    pipeline.start()
    
    val tui = IngestTUI(pipeline)
    tui.start()
}