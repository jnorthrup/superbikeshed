package moneyfan.ui

import moneyfan.core.*
import moneyfan.attention.*
import borg.trikeshed.lib.*

/**
 * MDI (Multiple Document Interface) with trace logging on function key hover+hold
 */

// Trace logging system using TrikeShed patterns
@JvmInline
value class TraceLevel(val value: Int) {
    companion object {
        val DEBUG = TraceLevel(0)
        val INFO = TraceLevel(1)
        val WARN = TraceLevel(2)
        val ERROR = TraceLevel(3)
    }
}

@JvmInline
value class SpaceGraphNode(val nodeId: String) {
    companion object {
        val ATTENTION = SpaceGraphNode("attention_ticker")
        val PORTFOLIO = SpaceGraphNode("portfolio_manager")
        val TECHNICAL = SpaceGraphNode("technical_analysis")
        val BACKTEST = SpaceGraphNode("backtest_results")
        val TRACE = SpaceGraphNode("trace_viewer")
        val JSON = SpaceGraphNode("json_scanner")
        val BTC_CHART = SpaceGraphNode("btc_chart")
        val ETH_CHART = SpaceGraphNode("eth_chart")
        val TILE = SpaceGraphNode("tile_windows")
        val CASCADE = SpaceGraphNode("cascade_windows")
        val CLEAR = SpaceGraphNode("clear_trace")
        val EXIT = SpaceGraphNode("exit_application")
    }
}

typealias TraceEntry = Join<TraceLevel, String>
typealias TraceLog = Indexed<TraceEntry>

// MDI Window management
data class MDIWindow(
    val id: String,
    val title: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val isActive: Boolean = false,
    val content: WindowContent
)

sealed class WindowContent {
    object AttentionTicker : WindowContent()
    object PortfolioManager : WindowContent()
    object TechnicalAnalysis : WindowContent()
    object BacktestResults : WindowContent()
    object TraceViewer : WindowContent()
    object JSONScanner : WindowContent()
    data class Chart(val symbol: Symbol) : WindowContent()
}

class MDITraceLogger {
    private val traceBuffer = mutableListOf<TraceEntry>()
    private val maxTraceEntries = 1000
    
    fun log(level: TraceLevel, message: String) {
        val entry = level j message
        traceBuffer.add(entry)
        
        if (traceBuffer.size > maxTraceEntries) {
            traceBuffer.removeAt(0)
        }
        
        // Store trace entry - no console output per CLAUDE.md compliance
    }
    
    fun getTraceLog(): TraceLog = Indexed.of(traceBuffer.size) { traceBuffer[it] }
    
    fun clearLog() {
        traceBuffer.clear()
    }
}

class MDIInterface {
    private val windows = mutableMapOf<String, MDIWindow>()
    private val tracer = MDITraceLogger()
    private var activeWindowId: String? = null
    private val spaceGraphNodeHandlers = mutableMapOf<SpaceGraphNode, () -> Unit>()
    private val nodeActivationStates = mutableMapOf<SpaceGraphNode, Boolean>()
    
    init {
        setupSpaceGraphNodes()
        createDefaultWindows()
    }
    
    private fun setupSpaceGraphNodes() {
        // Attention Ticker Node
        spaceGraphNodeHandlers[SpaceGraphNode.ATTENTION] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening Attention Ticker Window")
            openOrFocusWindow("attention", "Attention Ticker", WindowContent.AttentionTicker)
        }
        
        // Portfolio Manager Node
        spaceGraphNodeHandlers[SpaceGraphNode.PORTFOLIO] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening Portfolio Manager Window")
            openOrFocusWindow("portfolio", "Portfolio Manager", WindowContent.PortfolioManager)
        }
        
        // Technical Analysis Node
        spaceGraphNodeHandlers[SpaceGraphNode.TECHNICAL] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening Technical Analysis Window")
            openOrFocusWindow("technical", "Technical Analysis", WindowContent.TechnicalAnalysis)
        }
        
        // Backtest Results Node
        spaceGraphNodeHandlers[SpaceGraphNode.BACKTEST] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening Backtest Results Window")
            openOrFocusWindow("backtest", "Backtest Results", WindowContent.BacktestResults)
        }
        
        // Trace Viewer Node
        spaceGraphNodeHandlers[SpaceGraphNode.TRACE] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening Trace Viewer Window")
            openOrFocusWindow("trace", "Trace Viewer", WindowContent.TraceViewer)
        }
        
        // JSON Scanner Node
        spaceGraphNodeHandlers[SpaceGraphNode.JSON] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening JSON Scanner Window")
            openOrFocusWindow("json", "JSON Scanner", WindowContent.JSONScanner)
        }
        
        // BTC Chart Node
        spaceGraphNodeHandlers[SpaceGraphNode.BTC_CHART] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening BTC Chart Window")
            openOrFocusWindow("btc_chart", "BTC Chart", WindowContent.Chart(Symbol("BTC")))
        }
        
        // ETH Chart Node
        spaceGraphNodeHandlers[SpaceGraphNode.ETH_CHART] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Opening ETH Chart Window")
            openOrFocusWindow("eth_chart", "ETH Chart", WindowContent.Chart(Symbol("ETH")))
        }
        
        // Tile Windows Node
        spaceGraphNodeHandlers[SpaceGraphNode.TILE] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Tiling all windows")
            tileWindows()
        }
        
        // Cascade Windows Node
        spaceGraphNodeHandlers[SpaceGraphNode.CASCADE] = {
            tracer.log(TraceLevel.INFO, "SpaceGraph Node: Cascading all windows")
            cascadeWindows()
        }
        
        // Clear Trace Log Node
        spaceGraphNodeHandlers[SpaceGraphNode.CLEAR] = {
            tracer.log(TraceLevel.WARN, "SpaceGraph Node: Clearing trace log")
            tracer.clearLog()
        }
        
        // Exit Application Node
        spaceGraphNodeHandlers[SpaceGraphNode.EXIT] = {
            tracer.log(TraceLevel.ERROR, "SpaceGraph Node: Exit application requested")
            exitApplication()
        }
    }
    
    private fun createDefaultWindows() {
        tracer.log(TraceLevel.INFO, "Creating default MDI windows")
        
        // Create initial windows but don't show them yet
        windows["attention"] = MDIWindow(
            id = "attention",
            title = "Attention Ticker",
            x = 10, y = 10, width = 400, height = 300,
            content = WindowContent.AttentionTicker
        )
        
        windows["portfolio"] = MDIWindow(
            id = "portfolio", 
            title = "Portfolio Manager",
            x = 420, y = 10, width = 400, height = 300,
            content = WindowContent.PortfolioManager
        )
        
        windows["trace"] = MDIWindow(
            id = "trace",
            title = "Trace Viewer", 
            x = 10, y = 320, width = 810, height = 200,
            isActive = true,
            content = WindowContent.TraceViewer
        )
        
        activeWindowId = "trace"
    }
    
    fun onSpaceGraphNodeHover(node: SpaceGraphNode, isHovering: Boolean) {
        val wasHovering = nodeActivationStates[node] ?: false
        nodeActivationStates[node] = isHovering
        
        if (isHovering && !wasHovering) {
            tracer.log(TraceLevel.DEBUG, "Node HOVER START: ${getSpaceGraphNodeDescription(node)}")
        } else if (!isHovering && wasHovering) {
            tracer.log(TraceLevel.DEBUG, "Node HOVER END: ${getSpaceGraphNodeDescription(node)}")
        }
    }
    
    fun onSpaceGraphNodeActivate(node: SpaceGraphNode) {
        tracer.log(TraceLevel.INFO, "Node ACTIVATE: ${getSpaceGraphNodeDescription(node)} - Executing action")
        spaceGraphNodeHandlers[node]?.invoke()
    }
    
    private fun getSpaceGraphNodeDescription(node: SpaceGraphNode): String = when (node) {
        SpaceGraphNode.ATTENTION -> "Attention Ticker Node"
        SpaceGraphNode.PORTFOLIO -> "Portfolio Manager Node"
        SpaceGraphNode.TECHNICAL -> "Technical Analysis Node"
        SpaceGraphNode.BACKTEST -> "Backtest Results Node"
        SpaceGraphNode.TRACE -> "Trace Viewer Node"
        SpaceGraphNode.JSON -> "JSON Scanner Node"
        SpaceGraphNode.BTC_CHART -> "BTC Chart Node"
        SpaceGraphNode.ETH_CHART -> "ETH Chart Node"
        SpaceGraphNode.TILE -> "Tile Windows Node"
        SpaceGraphNode.CASCADE -> "Cascade Windows Node"
        SpaceGraphNode.CLEAR -> "Clear Trace Node"
        SpaceGraphNode.EXIT -> "Exit Application Node"
        else -> "Unknown Node (${node.nodeId})"
    }
    
    private fun openOrFocusWindow(id: String, title: String, content: WindowContent) {
        val existing = windows[id]
        if (existing != null) {
            // Focus existing window
            focusWindow(id)
            tracer.log(TraceLevel.DEBUG, "Focused existing window: $title")
        } else {
            // Create new window
            val newWindow = MDIWindow(
                id = id,
                title = title,
                x = 50 + windows.size * 30,
                y = 50 + windows.size * 30,
                width = 600,
                height = 400,
                content = content
            )
            windows[id] = newWindow
            focusWindow(id)
            tracer.log(TraceLevel.INFO, "Created new window: $title")
        }
    }
    
    private fun focusWindow(id: String) {
        // Deactivate all windows
        windows.replaceAll { _, window -> window.copy(isActive = false) }
        
        // Activate target window
        windows[id]?.let { window ->
            windows[id] = window.copy(isActive = true)
            activeWindowId = id
            tracer.log(TraceLevel.DEBUG, "Window focused: ${window.title}")
        }
    }
    
    private fun tileWindows() {
        val visibleWindows = windows.values.toList()
        val count = visibleWindows.size
        if (count == 0) return
        
        val cols = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()
        val rows = kotlin.math.ceil(count.toDouble() / cols).toInt()
        
        val windowWidth = 1200 / cols
        val windowHeight = 800 / rows
        
        visibleWindows.forEachIndexed { index, window ->
            val col = index % cols
            val row = index / cols
            
            val newWindow = window.copy(
                x = col * windowWidth,
                y = row * windowHeight,
                width = windowWidth,
                height = windowHeight
            )
            windows[window.id] = newWindow
        }
        
        tracer.log(TraceLevel.INFO, "Tiled $count windows in ${cols}x${rows} grid")
    }
    
    private fun cascadeWindows() {
        val visibleWindows = windows.values.toList()
        
        visibleWindows.forEachIndexed { index, window ->
            val offset = index * 30
            val newWindow = window.copy(
                x = 50 + offset,
                y = 50 + offset,
                width = 600,
                height = 400
            )
            windows[window.id] = newWindow
        }
        
        tracer.log(TraceLevel.INFO, "Cascaded ${visibleWindows.size} windows")
    }
    
    private fun exitApplication() {
        tracer.log(TraceLevel.ERROR, "Application exit initiated")
        // In real implementation, this would close the application
    }
    
    // Public interface for external systems
    fun getActiveWindow(): MDIWindow? = activeWindowId?.let { windows[it] }
    
    fun getAllWindows(): Indexed<MDIWindow> {
        val windowList = windows.values.toList()
        return Indexed.of(windowList.size) { windowList[it] }
    }
    
    fun getTraceLog(): TraceLog = tracer.getTraceLog()
    
    // Interface state for external rendering systems
    fun getInterfaceState(): MDIInterfaceState {
        val windowList = windows.values.toList()
        val recentTraces = tracer.getTraceLog()
        
        return MDIInterfaceState(
            activeWindowId = activeWindowId,
            windows = Indexed.of(windowList.size) { windowList[it] },
            recentTraces = recentTraces,
            spaceGraphNodeMappings = getSpaceGraphNodeMappings()
        )
    }
    
    private fun getSpaceGraphNodeMappings(): Indexed<Join<SpaceGraphNode, String>> {
        val mappings = listOf(
            SpaceGraphNode.ATTENTION j "Attention Ticker",
            SpaceGraphNode.PORTFOLIO j "Portfolio Manager",
            SpaceGraphNode.TECHNICAL j "Technical Analysis", 
            SpaceGraphNode.BACKTEST j "Backtest Results",
            SpaceGraphNode.TRACE j "Trace Viewer",
            SpaceGraphNode.JSON j "JSON Scanner",
            SpaceGraphNode.BTC_CHART j "BTC Chart",
            SpaceGraphNode.ETH_CHART j "ETH Chart",
            SpaceGraphNode.TILE j "Tile Windows",
            SpaceGraphNode.CASCADE j "Cascade Windows",
            SpaceGraphNode.CLEAR j "Clear Trace",
            SpaceGraphNode.EXIT j "Exit Application"
        )
        return Indexed.of(mappings.size) { mappings[it] }
    }
}

data class MDIInterfaceState(
    val activeWindowId: String?,
    val windows: Indexed<MDIWindow>,
    val recentTraces: TraceLog,
    val spaceGraphNodeMappings: Indexed<Join<SpaceGraphNode, String>>
)
}