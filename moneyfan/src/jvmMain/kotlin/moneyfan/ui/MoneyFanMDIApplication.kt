package moneyfan.ui

import com.ta4k.acapulco.*
import moneyfan.core.*
import moneyfan.attention.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.TitledBorder
import java.math.BigDecimal
import kotlin.math.*

/**
 * MoneyFan MDI Application integrating TradingDashboard, SpaceGraph, and attention systems
 * Combines the proven coinbaseXChangeBot patterns with full UI implementation
 */
class MoneyFanMDIApplication : JFrame("MoneyFan Trading System") {
    
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal val desktopPane = JDesktopPane()
    internal val moneyfanBridge = MoneyfanBridge()
    
    // Core components
    internal val tradingDashboard = TradingDashboard()
    internal val spaceGraphLoader = SpaceGraphMasterLoader()
    internal val attentionTicker = AttentionBasedTicker()
    
    // Window management
    internal val openWindows = mutableMapOf<String, JInternalFrame>()
    
    init {
        setupMDIInterface()
        createMenuBar()
        startBackgroundServices()
    }
    
    internal fun setupMDIInterface() {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        size = Dimension(1200, 800)
        setLocationRelativeTo(null)
        
        // Setup desktop pane
        desktopPane.background = Color.BLACK
        desktopPane.dragMode = JDesktopPane.OUTLINE_DRAG_MODE
        add(desktopPane)
        
        // Create initial windows
        createTradingDashboardWindow()
        createSpaceGraphWindow()
        createAttentionTickerWindow()
    }
    
    internal fun createMenuBar() {
        val menuBar = JMenuBar().apply {
            background = Color.BLACK
            
            // SpaceGraph Control Panel instead of traditional menus
            add(createSpaceGraphControlPanel())
        }
        
        jMenuBar = menuBar
    }
    
    internal fun createSpaceGraphControlPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            background = Color.BLACK
            
            // Node-based UI controls styled like SpaceGraph interface
            add(createSpaceGraphNode("Trading", Color.GREEN) {
                showOrFocusWindow("dashboard")
            })
            
            add(createSpaceGraphNode("Portfolio", Color.CYAN) {
                showOrFocusWindow("portfolio") 
            })
            
            add(createSpaceGraphNode("SpaceGraph", Color.YELLOW) {
                showOrFocusWindow("spacegraph")
            })
            
            add(createSpaceGraphNode("Attention", Color.ORANGE) {
                showOrFocusWindow("attention")
            })
            
            add(createSpaceGraphNode("Analysis", Color.MAGENTA) {
                createTechnicalAnalysisWindow()
            })
            
            add(createSpaceGraphNode("Data", Color.BLUE) {
                loadHistoricalData()
            })
            
            add(createSpaceGraphNode("Archive", Color.WHITE) {
                loadBinanceArchive()
            })
            
            // Window control nodes
            add(Box.createHorizontalStrut(20))
            
            add(createSpaceGraphNode("Tile", Color.LIGHT_GRAY) {
                tileWindows()
            })
            
            add(createSpaceGraphNode("Cascade", Color.GRAY) {
                cascadeWindows()
            })
            
            add(createSpaceGraphNode("Reset", Color.RED) {
                closeAllWindows()
            })
        }
    }
    
    internal fun createSpaceGraphNode(label: String, color: Color, action: () -> Unit): JButton {
        return JButton(label).apply {
            background = Color.BLACK
            foreground = color
            border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(color, 2),
                javax.swing.BorderFactory.createEmptyBorder(8, 12, 8, 12)
            )
            font = Font(Font.SANS_SERIF, Font.BOLD, 11)
            isFocusPainted = false
            isContentAreaFilled = false
            isOpaque = true
            
            addActionListener { action() }
            
            // SpaceGraph-style hover effects
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    background = color.darker()
                    foreground = Color.BLACK
                }
                
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    background = Color.BLACK
                    foreground = color
                }
            })
        }
    }
    
    internal fun createTradingDashboardWindow() {
        val window = JInternalFrame("Trading Dashboard", true, true, true, true).apply {
            contentPane = tradingDashboard
            size = Dimension(800, 600)
            setLocation(50, 50)
            isVisible = true
            
            addInternalFrameListener(object : javax.swing.event.InternalFrameAdapter() {
                override fun internalFrameClosing(e: javax.swing.event.InternalFrameEvent) {
                    tradingDashboard.cleanup()
                    openWindows.remove("dashboard")
                }
            })
        }
        
        openWindows["dashboard"] = window
        desktopPane.add(window)
        window.toFront()
    }
    
    internal fun createSpaceGraphWindow() {
        val window = JInternalFrame("SpaceGraph Master Loader", true, true, true, true).apply {
            contentPane = spaceGraphLoader
            size = Dimension(700, 500)
            setLocation(100, 100)
            
            addInternalFrameListener(object : javax.swing.event.InternalFrameAdapter() {
                override fun internalFrameClosing(e: javax.swing.event.InternalFrameEvent) {
                    spaceGraphLoader.cleanup()
                    openWindows.remove("spacegraph")
                }
            })
        }
        
        openWindows["spacegraph"] = window
        desktopPane.add(window)
    }
    
    internal fun createAttentionTickerWindow() {
        val attentionPanel = createAttentionTickerPanel()
        
        val window = JInternalFrame("Attention Ticker", true, true, true, true).apply {
            contentPane = attentionPanel
            size = Dimension(600, 400)
            setLocation(150, 150)
            
            addInternalFrameListener(object : javax.swing.event.InternalFrameAdapter() {
                override fun internalFrameClosing(e: javax.swing.event.InternalFrameEvent) {
                    openWindows.remove("attention")
                }
            })
        }
        
        openWindows["attention"] = window
        desktopPane.add(window)
    }
    
    internal fun createAttentionTickerPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = Color.BLACK
            border = TitledBorder("Variable Time Gauge Attention System").apply { 
                titleColor = Color.CYAN 
            }
            
            val symbolPanel = JPanel(GridLayout(0, 1)).apply {
                background = Color.BLACK
            }
            
            val symbols = listOf("BTC", "ETH", "AAPL", "GOOGL", "TSLA")
            symbols.forEach { symbol ->
                attentionTicker.addPairToWatch(Symbol(symbol))
                
                val symbolLabel = JLabel("$symbol: Initializing...").apply {
                    foreground = Color.WHITE
                    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                }
                symbolPanel.add(symbolLabel)
            }
            
            add(JScrollPane(symbolPanel), BorderLayout.CENTER)
            
            val controlPanel = JPanel(FlowLayout()).apply {
                background = Color.BLACK
                
                add(JButton("Start Ticker").apply {
                    background = Color.DARK_GRAY
                    foreground = Color.GREEN
                    addActionListener { startAttentionTicker() }
                })
                
                add(JButton("Stop Ticker").apply {
                    background = Color.DARK_GRAY
                    foreground = Color.RED
                    addActionListener { stopAttentionTicker() }
                })
            }
            
            add(controlPanel, BorderLayout.SOUTH)
        }
    }
    
    internal fun createTechnicalAnalysisWindow() {
        val analysisPanel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            border = TitledBorder("Technical Analysis").apply { 
                titleColor = Color.YELLOW 
            }
            
            val chartArea = JPanel().apply {
                background = Color.BLACK
                preferredSize = Dimension(600, 400)
                
                add(JLabel("<html><center>" +
                    "<h2 style='color: cyan;'>Technical Indicators</h2>" +
                    "<p style='color: white;'>RSI, SMA, Bollinger Bands</p>" +
                    "<p style='color: yellow;'>Carlos RSI2 & Kraken Skimmer Strategies</p>" +
                    "<p style='color: green;'>Real-time Signal Generation</p>" +
                    "</center></html>"))
            }
            
            add(chartArea, BorderLayout.CENTER)
        }
        
        val window = JInternalFrame("Technical Analysis", true, true, true, true).apply {
            contentPane = analysisPanel
            size = Dimension(650, 450)
            setLocation(200, 200)
            isVisible = true
        }
        
        val windowId = "technical_${System.currentTimeMillis()}"
        openWindows[windowId] = window
        desktopPane.add(window)
        window.toFront()
    }
    
    internal fun showOrFocusWindow(windowId: String) {
        openWindows[windowId]?.let { window ->
            if (!window.isVisible) {
                window.isVisible = true
            }
            window.toFront()
            try {
                window.isSelected = true
            } catch (e: java.beans.PropertyVetoException) {
                // Ignore selection veto
            }
        }
    }
    
    internal fun startBackgroundServices() {
        scope.launch {
            // Subscribe to bridge state changes
            moneyfanBridge.portfolioState.collect { portfolioState ->
                portfolioState?.let { 
                    SwingUtilities.invokeLater {
                        updatePortfolioDisplay(it)
                    }
                }
            }
        }
    }
    
    internal fun startTradingBot() {
        scope.launch {
            // Simulate trading activity using proven patterns from coinbaseXChangeBot
            val samplePortfolioRows = listOf(
                com.ta4k.acapulco.model.PortfolioRow(
                    symbol = "BTC",
                    quantity = BigDecimal("0.5"),
                    price = BigDecimal("45000.00"),
                    value = BigDecimal("22500.00"),
                    baseline = 20000.0,
                    deviation = 0.125,
                    priceChange = BigDecimal("1500.00")
                ),
                com.ta4k.acapulco.model.PortfolioRow(
                    symbol = "ETH", 
                    quantity = BigDecimal("10.0"),
                    price = BigDecimal("3000.00"),
                    value = BigDecimal("30000.00"),
                    baseline = 25000.0,
                    deviation = 0.20,
                    priceChange = BigDecimal("200.00")
                )
            )
            
            moneyfanBridge.updatePortfolioState(
                portfolioRows = samplePortfolioRows,
                totalValue = BigDecimal("52500.00"),
                cashBalance = BigDecimal("5000.00")
            )
            
            moneyfanBridge.updateTradingState(
                harvestedAmount = BigDecimal("1250.00"),
                anyTrades = true,
                portfolioDeviation = 0.15,
                crashProtectionActive = false
            )
        }
    }
    
    internal fun stopTradingBot() {
        // Stop trading operations
    }
    
    internal fun startAttentionTicker() {
        scope.launch {
            attentionTicker.startTicker(TickerInterval(1000)).collect { attentionSeries ->
                SwingUtilities.invokeLater {
                    updateAttentionDisplay(attentionSeries)
                }
            }
        }
    }
    
    internal fun stopAttentionTicker() {
        attentionTicker.stop()
    }
    
    internal fun loadHistoricalData() {
        showOrFocusWindow("spacegraph")
    }
    
    internal fun loadBinanceArchive() {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
        }
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            scope.launch {
                // Process Binance archive using BinanceDataProcessor
                // This would integrate with the real data processing
            }
        }
    }
    
    internal fun updatePortfolioDisplay(portfolioState: AcapulcoPortfolioState) {
        // Update would be handled by TradingDashboard component
    }
    
    internal fun updateAttentionDisplay(attentionSeries: AttentionSeries) {
        openWindows["attention"]?.let { window ->
            val panel = window.contentPane as JPanel
            val scrollPane = panel.getComponent(0) as JScrollPane
            val symbolPanel = scrollPane.viewport.view as JPanel
            
            attentionSeries.play.forEachIndexed { index, window ->
                if (index < symbolPanel.componentCount) {
                    val label = symbolPanel.getComponent(index) as JLabel
                    val (volGauge, volumeGauge) = window.gaugeReading
                    val span = window.dynamicSpan.millis
                    
                    val activity = when {
                        span < 1000 -> "VERY HIGH"
                        span < 2000 -> "HIGH" 
                        span < 4000 -> "MEDIUM"
                        else -> "LOW"
                    }
                    
                    label.text = "${window.symbol.value}: $activity (${span}ms) vol=${volGauge.value.format(4)}"
                    label.foreground = when (activity) {
                        "VERY HIGH" -> Color.RED
                        "HIGH" -> Color.ORANGE
                        "MEDIUM" -> Color.YELLOW
                        else -> Color.GREEN
                    }
                }
            }
            
            symbolPanel.revalidate()
            symbolPanel.repaint()
        }
    }
    
    internal fun tileWindows() {
        val windows = openWindows.values.filter { it.isVisible }
        if (windows.isEmpty()) return
        
        val cols = kotlin.math.ceil(kotlin.math.sqrt(windows.size.toDouble())).toInt()
        val rows = kotlin.math.ceil(windows.size.toDouble() / cols).toInt()
        
        val windowWidth = desktopPane.width / cols
        val windowHeight = desktopPane.height / rows
        
        windows.forEachIndexed { index, window ->
            val col = index % cols
            val row = index / cols
            
            window.setBounds(
                col * windowWidth,
                row * windowHeight,
                windowWidth,
                windowHeight
            )
        }
    }
    
    internal fun cascadeWindows() {
        val windows = openWindows.values.filter { it.isVisible }
        
        windows.forEachIndexed { index, window ->
            val offset = index * 30
            window.setBounds(
                50 + offset,
                50 + offset,
                600,
                400
            )
        }
    }
    
    internal fun closeAllWindows() {
        openWindows.values.forEach { window ->
            window.dispose()
        }
        openWindows.clear()
    }
    
    fun cleanup() {
        scope.cancel()
        tradingDashboard.cleanup()
        spaceGraphLoader.cleanup()
        attentionTicker.stop()
    }
    
    internal fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}

/**
 * Main application entry point
 */
fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel())
        } catch (e: Exception) {
            // Use default look and feel
        }
        
        val app = MoneyFanMDIApplication()
        app.isVisible = true
        
        // Cleanup on shutdown
        Runtime.getRuntime().addShutdownHook(Thread {
            app.cleanup()
        })
    }
}