package moneyfan.gui

import moneyfan.ui.*
import moneyfan.binance.*
import moneyfan.core.*
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * Real Swing MDI Interface with actual windows
 */
class SwingMDIInterface : JFrame("Moneyfan MDI Trading Interface") {
    private val desktop = JDesktopPane()
    private val mdiInterface = MDIInterface()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val windows = mutableMapOf<String, JInternalFrame>()
    private var binanceIngest: BinanceAttentionIngest? = null
    private var isIngestRunning = false
    
    // Trace and data areas
    private val traceArea = JTextArea(10, 50).apply {
        isEditable = false
        background = Color.BLACK
        foreground = Color.GREEN
        font = Font(Font.MONOSPACED, Font.PLAIN, 10)
    }
    
    private val binanceArea = JTextArea(10, 50).apply {
        isEditable = false
        background = Color.BLACK
        foreground = Color.WHITE
        font = Font(Font.MONOSPACED, Font.PLAIN, 10)
    }
    
    init {
        setupMainWindow()
        setupMenuBar()
        setupFunctionKeys()
        createInitialWindows()
        
        // Initialize ta4k strategy bridge
        initializeStrategyBridge()
        
        // Start trace log updates
        scope.launch {
            while (isActive) {
                updateTraceLog()
                delay(500)
            }
        }
    }
    
    private fun setupMainWindow() {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        size = Dimension(1200, 800)
        setLocationRelativeTo(null)
        
        layout = BorderLayout()
        
        // Main desktop area
        desktop.background = Color.DARK_GRAY
        add(desktop, BorderLayout.CENTER)
        
        // Bottom panel for trace and binance data
        val bottomPanel = JPanel(GridLayout(1, 2))
        
        val tracePanel = JPanel(BorderLayout()).apply {
            border = TitledBorder("Trace Log")
            add(JScrollPane(traceArea), BorderLayout.CENTER)
        }
        
        val binancePanel = JPanel(BorderLayout()).apply {
            border = TitledBorder("Binance Feed")
            add(JScrollPane(binanceArea), BorderLayout.CENTER)
            
            // Add start/stop button
            val controlPanel = JPanel(FlowLayout())
            val startStopButton = JButton("Start Binance Feed").apply {
                addActionListener {
                    if (isIngestRunning) {
                        stopBinanceFeed()
                    } else {
                        startBinanceFeed()
                    }
                }
            }
            controlPanel.add(startStopButton)
            add(controlPanel, BorderLayout.NORTH)
        }
        
        bottomPanel.add(tracePanel)
        bottomPanel.add(binancePanel)
        bottomPanel.preferredSize = Dimension(1200, 300)
        
        add(bottomPanel, BorderLayout.SOUTH)
        
        // Function key toolbar
        val toolbar = JToolBar("Function Keys")
        for (i in 1..12) {
            val button = JButton("F$i").apply {
                addActionListener { handleFunctionKey(i) }
                preferredSize = Dimension(50, 30)
            }
            toolbar.add(button)
        }
        add(toolbar, BorderLayout.NORTH)
    }
    
    private fun setupMenuBar() {
        val menuBar = JMenuBar()
        
        val windowMenu = JMenu("Windows")
        windowMenu.add(JMenuItem("Attention Ticker").apply { addActionListener { openAttentionWindow() } })
        windowMenu.add(JMenuItem("Portfolio Manager").apply { addActionListener { openPortfolioWindow() } })
        windowMenu.add(JMenuItem("BTC Chart").apply { addActionListener { openChartWindow("BTC") } })
        windowMenu.add(JMenuItem("ETH Chart").apply { addActionListener { openChartWindow("ETH") } })
        windowMenu.addSeparator()
        windowMenu.add(JMenuItem("Tile Windows").apply { addActionListener { tileWindows() } })
        windowMenu.add(JMenuItem("Cascade Windows").apply { addActionListener { cascadeWindows() } })
        
        menuBar.add(windowMenu)
        jMenuBar = menuBar
    }
    
    private fun setupFunctionKeys() {
        // Add key bindings for function keys
        val inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = rootPane.actionMap
        
        for (i in 1..12) {
            val keyStroke = KeyStroke.getKeyStroke("F$i")
            val actionKey = "F$i"
            inputMap.put(keyStroke, actionKey)
            actionMap.put(actionKey, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    handleFunctionKey(i)
                }
            })
        }
    }
    
    private fun handleFunctionKey(keyNum: Int) {
        val functionKey = when (keyNum) {
            1 -> FunctionKey.F1
            2 -> FunctionKey.F2
            3 -> FunctionKey.F3
            4 -> FunctionKey.F4
            5 -> FunctionKey.F5
            6 -> FunctionKey.F6
            7 -> FunctionKey.F7
            8 -> FunctionKey.F8
            9 -> FunctionKey.F9
            10 -> FunctionKey.F10
            11 -> FunctionKey.F11
            12 -> FunctionKey.F12
            else -> return
        }
        
        mdiInterface.onFunctionKeyHold(functionKey)
        
        when (keyNum) {
            1 -> openAttentionWindow()
            2 -> openPortfolioWindow()
            3 -> openTechnicalWindow()
            4 -> openBacktestWindow()
            5 -> focusTraceLog()
            6 -> openJSONWindow()
            7 -> openChartWindow("BTC")
            8 -> openChartWindow("ETH")
            9 -> tileWindows()
            10 -> cascadeWindows()
            11 -> clearTraceLog()
            12 -> exitApplication()
        }
    }
    
    private fun createInitialWindows() {
        openAttentionWindow()
        openPortfolioWindow()
    }
    
    private fun openAttentionWindow() {
        if ("attention" in windows) {
            windows["attention"]?.toFront()
            return
        }
        
        val content = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            
            val label = JLabel("<html><h2 style='color: yellow;'>Attention Ticker</h2>" +
                    "<p style='color: white;'>Variable time gauge attention system</p>" +
                    "<p style='color: cyan;'>Focus: BTC (5000ms span)</p>" +
                    "<p style='color: green;'>Volatility: 0.75%</p>" +
                    "<p style='color: orange;'>Volume: 4,892</p></html>")
            add(label, BorderLayout.CENTER)
        }
        
        val window = createInternalFrame("Attention Ticker", content, 50, 50, 400, 300)
        windows["attention"] = window
    }
    
    private fun openPortfolioWindow() {
        if ("portfolio" in windows) {
            windows["portfolio"]?.toFront()
            return
        }
        
        val content = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            
            val portfolioText = """
                <html>
                <h2 style='color: yellow;'>Portfolio Manager</h2>
                <table style='color: white; font-family: monospace;'>
                <tr><td>Cash Balance:</td><td style='color: green;'>$6,097.31</td></tr>
                <tr><td>Total Value:</td><td style='color: green;'>$10,000.00</td></tr>
                <tr><td>Day P&L:</td><td style='color: white;'>$0.00</td></tr>
                <tr><td>Total Return:</td><td style='color: white;'>0.00%</td></tr>
                </table>
                <br>
                <h3 style='color: cyan;'>Crypto Positions:</h3>
                <table style='color: white; font-family: monospace; font-size: 11px;'>
                <tr><td>BTC:</td><td>0.25 @ $43,127.50</td></tr>
                <tr><td>ETH:</td><td>1.5 @ $2,847.82</td></tr>
                <tr><td>ADA:</td><td>2500.0 @ $0.387</td></tr>
                <tr><td>SOL:</td><td>15.0 @ $97.23</td></tr>
                </table>
                </html>
            """.trimIndent()
            
            val label = JLabel(portfolioText)
            add(label, BorderLayout.CENTER)
        }
        
        val window = createInternalFrame("Portfolio Manager", content, 470, 50, 400, 300)
        windows["portfolio"] = window
    }
    
    private fun openTechnicalWindow() {
        if ("technical" in windows) {
            windows["technical"]?.toFront()
            return
        }
        
        // Try to create real ta4k integration, fallback to mock display
        val content = try {
            moneyfan.ui.TechnicalAnalysisDisplayFactory.createDisplay() 
                ?: moneyfan.ui.TechnicalAnalysisDisplayFactory.createMockDisplay()
        } catch (e: Exception) {
            JPanel(BorderLayout()).apply {
                background = Color.BLACK
                add(JLabel("<html><h2 style='color: yellow;'>Technical Analysis</h2>" +
                        "<p style='color: white;'>ta4k Strategy Integration</p>" +
                        "<p style='color: red;'>Error: ${e.message}</p></html>"), BorderLayout.CENTER)
            }
        }
        
        val window = createInternalFrame("Technical Analysis - ta4k Strategies", content, 100, 100, 800, 600)
        windows["technical"] = window
    }
    
    private fun openBacktestWindow() {
        if ("backtest" in windows) {
            windows["backtest"]?.toFront()
            return
        }
        
        val content = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(JLabel("<html><h2 style='color: yellow;'>Backtest Results</h2>" +
                    "<p style='color: white;'>Cap-based backtesting with 2.5% skimmer</p>" +
                    "<p style='color: green;'>Top 10 by cap performance analysis</p></html>"), BorderLayout.CENTER)
        }
        
        val window = createInternalFrame("Backtest Results", content, 150, 150, 500, 350)
        windows["backtest"] = window
    }
    
    private fun openJSONWindow() {
        if ("json" in windows) {
            windows["json"]?.toFront()
            return
        }
        
        val content = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(JLabel("<html><h2 style='color: yellow;'>JSON Scanner</h2>" +
                    "<p style='color: white;'>TrikeShed JSON DSEL</p></html>"), BorderLayout.CENTER)
        }
        
        val window = createInternalFrame("JSON Scanner", content, 200, 200, 500, 350)
        windows["json"] = window
    }
    
    private fun openChartWindow(symbol: String) {
        val windowId = "${symbol.lowercase()}_chart"
        if (windowId in windows) {
            windows[windowId]?.toFront()
            return
        }
        
        val content = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(JLabel("<html><h2 style='color: yellow;'>$symbol Chart</h2>" +
                    "<p style='color: white;'>Real-time price chart for $symbol</p>" +
                    "<p style='color: green;'>Current: ${getCurrentCryptoPrice(symbol)}</p></html>"), 
                BorderLayout.CENTER)
        }
        
        val window = createInternalFrame("$symbol Chart", content, 250, 250, 600, 400)
        windows[windowId] = window
    }
    
    private fun createInternalFrame(title: String, content: JComponent, x: Int, y: Int, width: Int, height: Int): JInternalFrame {
        val frame = JInternalFrame(title, true, true, true, true).apply {
            setBounds(x, y, width, height)
            add(content)
            isVisible = true
        }
        desktop.add(frame)
        frame.toFront()
        return frame
    }
    
    private fun tileWindows() {
        val frames = desktop.allFrames
        val count = frames.size
        if (count == 0) return
        
        val cols = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()
        val rows = kotlin.math.ceil(count.toDouble() / cols).toInt()
        
        val width = desktop.width / cols
        val height = (desktop.height - 100) / rows // Account for bottom panel
        
        frames.forEachIndexed { index, frame ->
            val col = index % cols
            val row = index / cols
            frame.setBounds(col * width, row * height, width, height)
        }
    }
    
    private fun cascadeWindows() {
        val frames = desktop.allFrames
        frames.forEachIndexed { index, frame ->
            val offset = index * 30
            frame.setBounds(50 + offset, 50 + offset, 600, 400)
        }
    }
    
    private fun focusTraceLog() {
        traceArea.requestFocus()
    }
    
    private fun clearTraceLog() {
        traceArea.text = ""
    }
    
    private fun exitApplication() {
        // Cleanup ta4k strategy bridge
        moneyfan.ta4k.StrategyBridge.getInstance().cleanup()
        
        // Cleanup technical analysis display if present
        windows["technical"]?.let { window ->
            val content = window.contentPane.getComponent(0)
            if (content is moneyfan.ui.TechnicalAnalysisDisplay) {
                content.cleanup()
            }
        }
        
        scope.cancel()
        dispose()
        System.exit(0)
    }
    
    private fun startBinanceFeed() {
        if (isIngestRunning) return
        
        isIngestRunning = true
        binanceIngest = BinanceAttentionIngest().apply {
            addSymbolForAttention("BTCUSDT", 0.005)
            addSymbolForAttention("ETHUSDT", 0.01)
            addSymbolForAttention("ADAUSDT", 0.02)
            addSymbolForAttention("SOLUSDT", 0.015)
        }
        
        scope.launch {
            var updateCount = 0
            try {
                binanceIngest?.startIngest()?.collect { ticker ->
                    updateCount++
                    val text = "Update #$updateCount: ${ticker.symbol.value} ${ticker.price.value.format(2)} (${ticker.priceChangePercent.format(2)}%)\n"
                    
                    SwingUtilities.invokeLater {
                        binanceArea.append(text)
                        binanceArea.caretPosition = binanceArea.document.length
                        
                        // Keep only last 20 lines
                        val lines = binanceArea.text.split("\n")
                        if (lines.size > 20) {
                            binanceArea.text = lines.takeLast(20).joinToString("\n")
                        }
                    }
                    
                    if (updateCount >= 100) {
                        stopBinanceFeed()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    binanceArea.append("Feed stopped: ${e.message}\n")
                }
                isIngestRunning = false
            }
        }
        
        // Update button text
        SwingUtilities.invokeLater {
            val button = (((getContentPane().getComponent(2) as JPanel).getComponent(1) as JPanel)
                .getComponent(0) as JPanel).getComponent(0) as JButton
            button.text = "Stop Binance Feed"
            button.background = Color.RED
        }
    }
    
    private fun stopBinanceFeed() {
        binanceIngest?.stop()
        binanceIngest = null
        isIngestRunning = false
        
        SwingUtilities.invokeLater {
            val button = (((getContentPane().getComponent(2) as JPanel).getComponent(1) as JPanel)
                .getComponent(0) as JPanel).getComponent(0) as JButton
            button.text = "Start Binance Feed"
            button.background = Color.GREEN
        }
    }
    
    private fun initializeStrategyBridge() {
        scope.launch {
            try {
                val bridge = moneyfan.ta4k.StrategyBridge.getInstance()
                val result = bridge.initializeStrategies()
                
                SwingUtilities.invokeLater {
                    if (result.isSuccess) {
                        traceArea.append("[INF] ta4k Strategy Bridge initialized successfully\n")
                        traceArea.append("[INF] Carlos RSI2 + Kraken Skimmer strategies ready\n")
                        traceArea.append("[INF] Press F3 for Technical Analysis window\n")
                    } else {
                        traceArea.append("[ERR] Strategy Bridge initialization failed: ${result.exceptionOrNull()?.message}\n")
                    }
                    traceArea.caretPosition = traceArea.document.length
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    traceArea.append("[ERR] Strategy Bridge error: ${e.message}\n")
                    traceArea.caretPosition = traceArea.document.length
                }
            }
        }
    }
    
    private fun updateTraceLog() {
        val traces = mdiInterface.getTraceLog()
        val traceText = traces.play.takeLast(15).joinToString("\n") { entry ->
            val (level, message) = entry
            val levelStr = when (level) {
                TraceLevel.DEBUG -> "DBG"
                TraceLevel.INFO -> "INF"
                TraceLevel.WARN -> "WRN"
                TraceLevel.ERROR -> "ERR"
                else -> "TRC"
            }
            "[$levelStr] $message"
        }
        
        SwingUtilities.invokeLater {
            if (traceArea.text != traceText) {
                traceArea.text = traceText
                traceArea.caretPosition = traceArea.document.length
            }
        }
    }
    
    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
    
    private fun getCurrentCryptoPrice(symbol: String): String {
        return when (symbol) {
            "BTC" -> "$43,127.98"
            "ETH" -> "$2,847.82"
            "ADA" -> "$0.387"
            "SOL" -> "$97.23"
            "DOT" -> "$6.45"
            "LINK" -> "$14.23"
            "AVAX" -> "$36.78"
            "MATIC" -> "$0.89"
            else -> "$1.00"
        }
    }
}

/**
 * Launch the real Swing MDI application
 */
fun launchSwingMDI() {
    SwingUtilities.invokeLater {
        // Set look and feel
        
        val app = SwingMDIInterface()
        app.isVisible = true
    }
}