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
value class FunctionKey(val keyCode: Int) {
    companion object {
        val F1 = FunctionKey(112)
        val F2 = FunctionKey(113)
        val F3 = FunctionKey(114)
        val F4 = FunctionKey(115)
        val F5 = FunctionKey(116)
        val F6 = FunctionKey(117)
        val F7 = FunctionKey(118)
        val F8 = FunctionKey(119)
        val F9 = FunctionKey(120)
        val F10 = FunctionKey(121)
        val F11 = FunctionKey(122)
        val F12 = FunctionKey(123)
    }
}

typealias TraceEntry = Join<TraceLevel, String>
typealias TraceLog = Series<TraceEntry>

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
        
        // Console output for immediate feedback
        val levelStr = when (level) {
            TraceLevel.DEBUG -> "DEBUG"
            TraceLevel.INFO -> "INFO"
            TraceLevel.WARN -> "WARN"
            TraceLevel.ERROR -> "ERROR"
            else -> "TRACE"
        }
        println("[$levelStr] $message")
    }
    
    fun getTraceLog(): TraceLog = Series.of(traceBuffer.size) { traceBuffer[it] }
    
    fun clearLog() {
        traceBuffer.clear()
    }
}

class MDIInterface {
    private val windows = mutableMapOf<String, MDIWindow>()
    private val tracer = MDITraceLogger()
    private var activeWindowId: String? = null
    private val functionKeyHandlers = mutableMapOf<FunctionKey, () -> Unit>()
    private val hoverStates = mutableMapOf<FunctionKey, Boolean>()
    
    init {
        setupFunctionKeys()
        createDefaultWindows()
    }
    
    private fun setupFunctionKeys() {
        // F1 - Attention Ticker Window
        functionKeyHandlers[FunctionKey.F1] = {
            tracer.log(TraceLevel.INFO, "F1: Opening Attention Ticker Window")
            openOrFocusWindow("attention", "Attention Ticker", WindowContent.AttentionTicker)
        }
        
        // F2 - Portfolio Manager
        functionKeyHandlers[FunctionKey.F2] = {
            tracer.log(TraceLevel.INFO, "F2: Opening Portfolio Manager Window")
            openOrFocusWindow("portfolio", "Portfolio Manager", WindowContent.PortfolioManager)
        }
        
        // F3 - Technical Analysis
        functionKeyHandlers[FunctionKey.F3] = {
            tracer.log(TraceLevel.INFO, "F3: Opening Technical Analysis Window")
            openOrFocusWindow("technical", "Technical Analysis", WindowContent.TechnicalAnalysis)
        }
        
        // F4 - Backtest Results
        functionKeyHandlers[FunctionKey.F4] = {
            tracer.log(TraceLevel.INFO, "F4: Opening Backtest Results Window")
            openOrFocusWindow("backtest", "Backtest Results", WindowContent.BacktestResults)
        }
        
        // F5 - Trace Viewer
        functionKeyHandlers[FunctionKey.F5] = {
            tracer.log(TraceLevel.INFO, "F5: Opening Trace Viewer Window")
            openOrFocusWindow("trace", "Trace Viewer", WindowContent.TraceViewer)
        }
        
        // F6 - JSON Scanner
        functionKeyHandlers[FunctionKey.F6] = {
            tracer.log(TraceLevel.INFO, "F6: Opening JSON Scanner Window")
            openOrFocusWindow("json", "JSON Scanner", WindowContent.JSONScanner)
        }
        
        // F7 - BTC Chart
        functionKeyHandlers[FunctionKey.F7] = {
            tracer.log(TraceLevel.INFO, "F7: Opening BTC Chart Window")
            openOrFocusWindow("btc_chart", "BTC Chart", WindowContent.Chart(Symbol("BTC")))
        }
        
        // F8 - ETH Chart
        functionKeyHandlers[FunctionKey.F8] = {
            tracer.log(TraceLevel.INFO, "F8: Opening ETH Chart Window")
            openOrFocusWindow("eth_chart", "ETH Chart", WindowContent.Chart(Symbol("ETH")))
        }
        
        // F9 - Tile Windows
        functionKeyHandlers[FunctionKey.F9] = {
            tracer.log(TraceLevel.INFO, "F9: Tiling all windows")
            tileWindows()
        }
        
        // F10 - Cascade Windows
        functionKeyHandlers[FunctionKey.F10] = {
            tracer.log(TraceLevel.INFO, "F10: Cascading all windows")
            cascadeWindows()
        }
        
        // F11 - Clear Trace Log
        functionKeyHandlers[FunctionKey.F11] = {
            tracer.log(TraceLevel.WARN, "F11: Clearing trace log")
            tracer.clearLog()
        }
        
        // F12 - Exit Application
        functionKeyHandlers[FunctionKey.F12] = {
            tracer.log(TraceLevel.ERROR, "F12: Exit application requested")
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
    
    fun onFunctionKeyHover(key: FunctionKey, isHovering: Boolean) {
        val wasHovering = hoverStates[key] ?: false
        hoverStates[key] = isHovering
        
        if (isHovering && !wasHovering) {
            tracer.log(TraceLevel.DEBUG, "Hover START: ${getFunctionKeyDescription(key)}")
        } else if (!isHovering && wasHovering) {
            tracer.log(TraceLevel.DEBUG, "Hover END: ${getFunctionKeyDescription(key)}")
        }
    }
    
    fun onFunctionKeyHold(key: FunctionKey) {
        tracer.log(TraceLevel.INFO, "Key HOLD: ${getFunctionKeyDescription(key)} - Executing action")
        functionKeyHandlers[key]?.invoke()
    }
    
    private fun getFunctionKeyDescription(key: FunctionKey): String = when (key) {
        FunctionKey.F1 -> "F1 (Attention Ticker)"
        FunctionKey.F2 -> "F2 (Portfolio Manager)"
        FunctionKey.F3 -> "F3 (Technical Analysis)"
        FunctionKey.F4 -> "F4 (Backtest Results)"
        FunctionKey.F5 -> "F5 (Trace Viewer)"
        FunctionKey.F6 -> "F6 (JSON Scanner)"
        FunctionKey.F7 -> "F7 (BTC Chart)"
        FunctionKey.F8 -> "F8 (ETH Chart)"
        FunctionKey.F9 -> "F9 (Tile Windows)"
        FunctionKey.F10 -> "F10 (Cascade Windows)"
        FunctionKey.F11 -> "F11 (Clear Trace)"
        FunctionKey.F12 -> "F12 (Exit)"
        else -> "F? (Unknown)"
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
        println("MDI Application would exit here")
    }
    
    // Public interface for external systems
    fun getActiveWindow(): MDIWindow? = activeWindowId?.let { windows[it] }
    
    fun getAllWindows(): Series<MDIWindow> {
        val windowList = windows.values.toList()
        return Series.of(windowList.size) { windowList[it] }
    }
    
    fun getTraceLog(): TraceLog = tracer.getTraceLog()
    
    fun renderInterface() {
        println("\n" + "=".repeat(80))
        println("MDI Interface - Function Key Actions")
        println("=".repeat(80))
        
        // Show function key mappings
        println("Function Keys:")
        println("F1: Attention Ticker  F2: Portfolio    F3: Technical    F4: Backtest")
        println("F5: Trace Viewer      F6: JSON Scanner F7: BTC Chart    F8: ETH Chart")
        println("F9: Tile Windows      F10: Cascade     F11: Clear Trace F12: Exit")
        
        println("\nActive Windows:")
        windows.values.forEach { window ->
            val status = if (window.isActive) "[ACTIVE]" else "       "
            println("$status ${window.title.padEnd(20)} (${window.x},${window.y}) ${window.width}x${window.height}")
        }
        
        // Show recent trace entries
        val recentTraces = tracer.getTraceLog()
        if (recentTraces.size > 0) {
            println("\nRecent Trace Log:")
            recentTraces.play.takeLast(5).forEach { entry ->
                val (level, message) = entry
                val levelStr = when (level) {
                    TraceLevel.DEBUG -> "DBG"
                    TraceLevel.INFO -> "INF"
                    TraceLevel.WARN -> "WRN"
                    TraceLevel.ERROR -> "ERR"
                    else -> "TRC"
                }
                println("[$levelStr] $message")
            }
        }
        
        println("=".repeat(80))
    }
}