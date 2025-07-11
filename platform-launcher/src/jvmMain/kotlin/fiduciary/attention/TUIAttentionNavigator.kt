package fiduciary.attention

import com.googlecode.lanterna.*
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.input.*
import com.googlecode.lanterna.terminal.*
import com.googlecode.lanterna.screen.*
import com.googlecode.lanterna.TextColor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * TUI Attention Navigator for Fiduciary System
 * 
 * File Commander-style dual-pane interface for navigating data sources
 * with attention focus management
 */
class TUIAttentionNavigator {
    private lateinit var terminal: Terminal
    private lateinit var screen: Screen
    private lateinit var graphics: TextGraphics
    
    // Dual-pane state (like Midnight Commander)
    private var activePane = PanePosition.LEFT
    private var leftPaneSource: DataSource? = null
    private var rightPaneSource: DataSource? = null
    private var leftPaneIndex = 0
    private var rightPaneIndex = 0
    private var leftPaneScroll = 0
    private var rightPaneScroll = 0
    
    // Selection state
    private val leftPaneSelection = mutableSetOf<String>()
    private val rightPaneSelection = mutableSetOf<String>()
    
    // View options
    private var showHidden = false
    private var sortBy = SortBy.NAME
    private var filterPattern = ""
    
    // Data sources
    private val dataSources = mutableListOf<DataSource>()
    private val sourceContent = mutableMapOf<String, List<ContentItem>>()
    private val attentionHistory = mutableListOf<AttentionEvent>()
    
    enum class PanePosition { LEFT, RIGHT }
    enum class SortBy { NAME, SIZE, DATE, TYPE }
    
    enum class ViewMode {
        BROWSE,          // Normal browsing
        AI_ASSIST,       // AI-assisted navigation with prefetch
        ANALYSIS,        // Pattern analysis mode
        MONITOR          // Real-time monitoring
    }
    
    data class DataSource(
        val id: String,
        val name: String,
        val type: SourceType,
        val status: SourceStatus,
        val itemCount: Int,
        val lastUpdated: Instant
    )
    
    enum class SourceType {
        DIVINE_INDEX,     // Patrick Devine indexes
        OCR_DOCUMENT,     // OCR processed documents
        TRANSCRIPT,       // Audio/video transcripts
        BLACKBOARD,       // Blackboard lattice data
        WAVE_CRDT,        // Apache Wave CRDT
        COUCHDB,          // CouchDB documents
        IPFS,             // IPFS content
        GIT_HISTORY,      // Git forensics
        TELEMETRY,        // IDE telemetry
        REALTIME          // Real-time streams
    }
    
    enum class SourceStatus {
        ONLINE,
        OFFLINE,
        SYNCING,
        ERROR
    }
    
    data class ContentItem(
        val id: String,
        val title: String,
        val preview: String,
        val metadata: Map<String, String>,
        val score: Double = 0.0,
        val prefetched: Boolean = false
    )
    
    data class AttentionEvent(
        val timestamp: Instant,
        val focus: AttentionFocus,
        val sourceId: String?,
        val itemId: String?,
        val action: String
    )
    
    suspend fun start() = coroutineScope {
        initializeTerminal()
        loadDataSources()
        
        try {
            // Main UI loop
            launch {
                while (isActive) {
                    render()
                    delay(100) // 10 FPS refresh
                }
            }
            
            // Input handling
            launch {
                handleInput()
            }
            
            // Background prefetching for AI
            if (prefetchEnabled) {
                launch {
                    prefetchForAI()
                }
            }
            
            // Keep running
            awaitCancellation()
            
        } finally {
            screen.close()
            terminal.close()
        }
    }
    
    private fun initializeTerminal() {
        terminal = DefaultTerminalFactory().createTerminal()
        screen = TerminalScreen(terminal)
        screen.startScreen()
        screen.cursorPosition = null
        graphics = screen.newTextGraphics()
    }
    
    private suspend fun loadDataSources() {
        // Load all fiduciary data sources
        dataSources.addAll(listOf(
            DataSource("divine-1", "Patrick Devine Indexes", SourceType.DIVINE_INDEX, 
                SourceStatus.ONLINE, 1523, Instant.now()),
            DataSource("ocr-1", "OCR Documents", SourceType.OCR_DOCUMENT,
                SourceStatus.ONLINE, 342, Instant.now()),
            DataSource("transcript-1", "Whisper Transcripts", SourceType.TRANSCRIPT,
                SourceStatus.SYNCING, 89, Instant.now()),
            DataSource("blackboard-1", "Blackboard Lattice", SourceType.BLACKBOARD,
                SourceStatus.ONLINE, 4567, Instant.now()),
            DataSource("wave-1", "Wave CRDT", SourceType.WAVE_CRDT,
                SourceStatus.ONLINE, 234, Instant.now()),
            DataSource("couch-1", "CouchDB Views", SourceType.COUCHDB,
                SourceStatus.ONLINE, 8901, Instant.now()),
            DataSource("ipfs-1", "IPFS Content", SourceType.IPFS,
                SourceStatus.OFFLINE, 567, Instant.now()),
            DataSource("git-1", "Git Forensics", SourceType.GIT_HISTORY,
                SourceStatus.ONLINE, 12345, Instant.now()),
            DataSource("telemetry-1", "IDE Telemetry", SourceType.TELEMETRY,
                SourceStatus.ONLINE, 98765, Instant.now()),
            DataSource("realtime-1", "Live Streams", SourceType.REALTIME,
                SourceStatus.ONLINE, 42, Instant.now())
        ))
    }
    
    private fun render() {
        graphics.fillRectangle(TerminalPosition.TOP_LEFT_CORNER, 
            terminal.terminalSize, ' ')
        
        val size = terminal.terminalSize
        val width = size.columns
        val height = size.rows
        
        // File Commander layout: dual panes with equal width
        val paneWidth = (width - 1) / 2
        val paneHeight = height - 4  // Leave room for header, status, and function keys
        
        // Draw header
        drawHeader(0, 0, width)
        
        // Draw dual panes
        drawPane(0, 1, paneWidth, paneHeight, PanePosition.LEFT)
        drawPane(paneWidth + 1, 1, paneWidth, paneHeight, PanePosition.RIGHT)
        
        // Draw info bar (current item details)
        drawInfoBar(0, height - 3, width)
        
        // Draw command line
        drawCommandLine(0, height - 2, width)
        
        // Draw function key bar (F1-F10)
        drawFunctionBar(0, height - 1, width)
        
        screen.refresh()
    }
    
    private fun drawSourcesPanel(x: Int, y: Int, width: Int, height: Int) {
        // Border
        drawBox(x, y, width, height, "Data Sources")
        
        // Content
        dataSources.forEachIndexed { index, source ->
            if (index < height - 2) {
                val row = y + index + 1
                val selected = currentFocus == AttentionFocus.SOURCES_LIST && 
                              selectedSourceIndex == index
                
                // Status indicator
                val statusChar = when (source.status) {
                    SourceStatus.ONLINE -> "●"
                    SourceStatus.OFFLINE -> "○"
                    SourceStatus.SYNCING -> "◐"
                    SourceStatus.ERROR -> "✗"
                }
                
                val statusColor = when (source.status) {
                    SourceStatus.ONLINE -> ANSI.GREEN
                    SourceStatus.OFFLINE -> ANSI.RED
                    SourceStatus.SYNCING -> ANSI.YELLOW
                    SourceStatus.ERROR -> ANSI.RED
                }
                
                graphics.foregroundColor = statusColor
                graphics.putString(x + 1, row, statusChar)
                
                // Source name
                graphics.foregroundColor = if (selected) ANSI.BLACK else ANSI.WHITE
                graphics.backgroundColor = if (selected) ANSI.CYAN else ANSI.BLACK
                
                val displayName = source.name.take(width - 10)
                graphics.putString(x + 3, row, displayName)
                
                // Item count
                graphics.foregroundColor = ANSI.CYAN
                graphics.backgroundColor = ANSI.BLACK
                val count = source.itemCount.toString()
                graphics.putString(x + width - count.length - 2, row, count)
            }
        }
    }
    
    private fun drawContentPanel(x: Int, y: Int, width: Int, height: Int) {
        val source = dataSources.getOrNull(selectedSourceIndex)
        drawBox(x, y, width, height, source?.name ?: "Content")
        
        if (source != null) {
            val items = sourceContent[source.id] ?: loadContentItems(source)
            
            items.forEachIndexed { index, item ->
                if (index < height - 2) {
                    val row = y + index + 1
                    val selected = currentFocus == AttentionFocus.CONTENT_LIST && 
                                  selectedItemIndex == index
                    
                    graphics.foregroundColor = if (selected) ANSI.BLACK else ANSI.WHITE
                    graphics.backgroundColor = if (selected) ANSI.YELLOW else ANSI.BLACK
                    
                    // Prefetch indicator for AI
                    if (item.prefetched) {
                        graphics.foregroundColor = ANSI.GREEN
                        graphics.putString(x + 1, row, "⚡")
                    }
                    
                    // Title
                    val title = item.title.take(width - 15)
                    graphics.foregroundColor = if (selected) ANSI.BLACK else ANSI.WHITE
                    graphics.putString(x + 3, row, title)
                    
                    // Score (relevance)
                    if (item.score > 0) {
                        graphics.foregroundColor = ANSI.MAGENTA
                        graphics.backgroundColor = ANSI.BLACK
                        val score = "%.2f".format(item.score)
                        graphics.putString(x + width - score.length - 2, row, score)
                    }
                }
            }
        }
    }
    
    private fun drawDetailsPanel(x: Int, y: Int, width: Int, height: Int) {
        drawBox(x, y, width, height, "Details [F1: AI Assist]")
        
        val source = dataSources.getOrNull(selectedSourceIndex)
        val items = source?.let { sourceContent[it.id] }
        val item = items?.getOrNull(selectedItemIndex)
        
        if (item != null) {
            var row = y + 1
            
            // Preview
            graphics.foregroundColor = ANSI.WHITE
            item.preview.lines().take(height / 2).forEach { line ->
                if (row < y + height - 1) {
                    graphics.putString(x + 1, row++, line.take(width - 2))
                }
            }
            
            // Metadata
            row += 1
            graphics.foregroundColor = ANSI.CYAN
            graphics.putString(x + 1, row++, "─".repeat(width - 2))
            
            item.metadata.entries.take(10).forEach { (key, value) ->
                if (row < y + height - 1) {
                    graphics.foregroundColor = ANSI.BLUE
                    graphics.putString(x + 1, row, "$key:")
                    graphics.foregroundColor = ANSI.WHITE
                    graphics.putString(x + key.length + 3, row, value.take(width - key.length - 4))
                    row++
                }
            }
        }
    }
    
    private fun drawStatusBar(x: Int, y: Int, width: Int) {
        graphics.backgroundColor = ANSI.BLUE
        graphics.foregroundColor = ANSI.WHITE
        graphics.fillRectangle(TerminalPosition(x, y), TerminalSize(width, 1), ' ')
        
        // Mode indicator
        val modeStr = "Mode: $viewMode"
        graphics.putString(x + 1, y, modeStr)
        
        // Stats
        val totalItems = dataSources.sumOf { it.itemCount }
        val stats = "Sources: ${dataSources.size} | Items: $totalItems"
        graphics.putString(x + width - stats.length - 1, y, stats)
        
        // Current time
        val time = DateTimeFormatter.ISO_LOCAL_TIME.format(java.time.LocalTime.now())
        graphics.putString(x + width / 2 - time.length / 2, y, time)
        
        graphics.backgroundColor = ANSI.BLACK
    }
    
    private fun drawCommandLine(x: Int, y: Int, width: Int) {
        graphics.foregroundColor = ANSI.GREEN
        graphics.putString(x, y, ">")
        
        graphics.foregroundColor = ANSI.WHITE
        val commands = when (currentFocus) {
            AttentionFocus.SOURCES_LIST -> "[↑↓] Navigate [→] Enter [/] Search [q] Quit"
            AttentionFocus.CONTENT_LIST -> "[↑↓] Navigate [←→] Switch [Enter] Open [b] Back"
            AttentionFocus.DETAIL_VIEW -> "[←] Back [e] Edit [x] Export [a] Analyze"
            AttentionFocus.SEARCH_BOX -> "Type to search, [Esc] Cancel"
            AttentionFocus.COMMAND_LINE -> "Enter command..."
        }
        graphics.putString(x + 2, y, commands)
    }
    
    private fun drawBox(x: Int, y: Int, width: Int, height: Int, title: String) {
        graphics.foregroundColor = ANSI.WHITE
        graphics.drawRectangle(TerminalPosition(x, y), TerminalSize(width, height), '│')
        graphics.putString(x + 2, y, "┤ $title ├")
    }
    
    private suspend fun handleInput() {
        while (true) {
            val keyStroke = screen.readInput()
            when (keyStroke.keyType) {
                KeyType.ArrowUp -> navigateUp()
                KeyType.ArrowDown -> navigateDown()
                KeyType.ArrowLeft -> navigateLeft()
                KeyType.ArrowRight -> navigateRight()
                KeyType.Enter -> selectItem()
                KeyType.F1 -> toggleAIAssist()
                KeyType.Character -> {
                    when (keyStroke.character) {
                        'q' -> break
                        '/' -> startSearch()
                        'a' -> analyzePatterns()
                        'p' -> togglePrefetch()
                        'm' -> cycleViewMode()
                    }
                }
                else -> {}
            }
            
            // Record attention event
            recordAttention(keyStroke)
        }
    }
    
    private fun navigateUp() {
        when (currentFocus) {
            AttentionFocus.SOURCES_LIST -> {
                if (selectedSourceIndex > 0) selectedSourceIndex--
            }
            AttentionFocus.CONTENT_LIST -> {
                if (selectedItemIndex > 0) selectedItemIndex--
            }
            else -> {}
        }
    }
    
    private fun navigateDown() {
        when (currentFocus) {
            AttentionFocus.SOURCES_LIST -> {
                if (selectedSourceIndex < dataSources.size - 1) selectedSourceIndex++
            }
            AttentionFocus.CONTENT_LIST -> {
                val items = sourceContent[dataSources[selectedSourceIndex].id]
                if (items != null && selectedItemIndex < items.size - 1) {
                    selectedItemIndex++
                }
            }
            else -> {}
        }
    }
    
    private fun navigateLeft() {
        currentFocus = when (currentFocus) {
            AttentionFocus.DETAIL_VIEW -> AttentionFocus.CONTENT_LIST
            AttentionFocus.CONTENT_LIST -> AttentionFocus.SOURCES_LIST
            else -> currentFocus
        }
    }
    
    private fun navigateRight() {
        currentFocus = when (currentFocus) {
            AttentionFocus.SOURCES_LIST -> {
                selectedItemIndex = 0
                AttentionFocus.CONTENT_LIST
            }
            AttentionFocus.CONTENT_LIST -> AttentionFocus.DETAIL_VIEW
            else -> currentFocus
        }
    }
    
    private fun selectItem() {
        // Handle item selection based on current focus
    }
    
    private fun toggleAIAssist() {
        viewMode = if (viewMode == ViewMode.AI_ASSIST) ViewMode.BROWSE else ViewMode.AI_ASSIST
        if (viewMode == ViewMode.AI_ASSIST) {
            prefetchEnabled = true
        }
    }
    
    private fun togglePrefetch() {
        prefetchEnabled = !prefetchEnabled
    }
    
    private fun cycleViewMode() {
        viewMode = ViewMode.values()[(viewMode.ordinal + 1) % ViewMode.values().size]
    }
    
    private fun startSearch() {
        currentFocus = AttentionFocus.SEARCH_BOX
    }
    
    private suspend fun analyzePatterns() {
        // Analyze attention patterns
    }
    
    private fun recordAttention(keyStroke: KeyStroke) {
        val source = dataSources.getOrNull(selectedSourceIndex)
        val items = source?.let { sourceContent[it.id] }
        val item = items?.getOrNull(selectedItemIndex)
        
        attentionHistory.add(AttentionEvent(
            timestamp = Instant.now(),
            focus = currentFocus,
            sourceId = source?.id,
            itemId = item?.id,
            action = keyStroke.toString()
        ))
    }
    
    private fun loadContentItems(source: DataSource): List<ContentItem> {
        // Load items based on source type
        val items = when (source.type) {
            SourceType.DIVINE_INDEX -> loadDivineIndexItems()
            SourceType.OCR_DOCUMENT -> loadOCRItems()
            SourceType.TRANSCRIPT -> loadTranscriptItems()
            SourceType.BLACKBOARD -> loadBlackboardItems()
            SourceType.WAVE_CRDT -> loadWaveCRDTItems()
            SourceType.COUCHDB -> loadCouchDBItems()
            SourceType.IPFS -> loadIPFSItems()
            SourceType.GIT_HISTORY -> loadGitHistoryItems()
            SourceType.TELEMETRY -> loadTelemetryItems()
            SourceType.REALTIME -> loadRealtimeItems()
        }
        
        sourceContent[source.id] = items
        return items
    }
    
    // Content loaders for each source type
    private fun loadDivineIndexItems(): List<ContentItem> {
        return listOf(
            ContentItem("div-1", "Market Analysis 2024", 
                "Comprehensive market trends and predictions...",
                mapOf("author" to "Patrick Devine", "date" to "2024-01-15"),
                score = 0.95),
            ContentItem("div-2", "Technology Forecast",
                "Emerging technologies and their impact...",
                mapOf("author" to "Patrick Devine", "date" to "2024-02-01"),
                score = 0.88)
        )
    }
    
    private fun loadOCRItems(): List<ContentItem> = emptyList()
    private fun loadTranscriptItems(): List<ContentItem> = emptyList()
    private fun loadBlackboardItems(): List<ContentItem> = emptyList()
    private fun loadWaveCRDTItems(): List<ContentItem> = emptyList()
    private fun loadCouchDBItems(): List<ContentItem> = emptyList()
    private fun loadIPFSItems(): List<ContentItem> = emptyList()
    private fun loadGitHistoryItems(): List<ContentItem> = emptyList()
    private fun loadTelemetryItems(): List<ContentItem> = emptyList()
    private fun loadRealtimeItems(): List<ContentItem> = emptyList()
    
    private suspend fun prefetchForAI() {
        while (prefetchEnabled) {
            // Prefetch items likely to be accessed by AI
            val source = dataSources.getOrNull(selectedSourceIndex)
            if (source != null) {
                val items = sourceContent[source.id]
                items?.forEachIndexed { index, item ->
                    if (index in (selectedItemIndex - 2)..(selectedItemIndex + 2)) {
                        // Mark as prefetched
                        sourceContent[source.id] = items.toMutableList().apply {
                            set(index, item.copy(prefetched = true))
                        }
                    }
                }
            }
            delay(1000)
        }
    }
}

/**
 * AI Navigation Assistant
 * 
 * Provides AI-friendly interface to the same navigation
 */
class AINavigationAssistant(
    private val navigator: TUIAttentionNavigator
) {
    suspend fun getAvailableSources(): List<DataSource> {
        // Return sources with prefetch hints
        return navigator.dataSources.map { source ->
            source.copy(
                metadata = source.metadata + mapOf(
                    "prefetch_priority" to calculatePrefetchPriority(source).toString(),
                    "ai_relevance" to calculateAIRelevance(source).toString()
                )
            )
        }
    }
    
    suspend fun navigateToSource(sourceId: String) {
        // AI-optimized navigation
    }
    
    suspend fun searchAcrossSources(query: String): List<ContentItem> {
        // Multi-source search with ranking
        return emptyList()
    }
    
    private fun calculatePrefetchPriority(source: DataSource): Double {
        return when (source.type) {
            SourceType.DIVINE_INDEX -> 0.95  // High value content
            SourceType.TELEMETRY -> 0.90     // Real-time insights
            SourceType.BLACKBOARD -> 0.85    // Structured knowledge
            else -> 0.50
        }
    }
    
    private fun calculateAIRelevance(source: DataSource): Double {
        // Calculate based on recency, size, and type
        return 0.75
    }
}