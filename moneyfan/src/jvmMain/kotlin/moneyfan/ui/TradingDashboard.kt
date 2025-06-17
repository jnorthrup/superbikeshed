package moneyfan.ui

import com.ta4k.acapulco.*
import moneyfan.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.TitledBorder
import java.math.BigDecimal
import kotlin.math.*

/**
 * Integrated Trading Dashboard combining SpaceGraph, Moneyfan core, and Acapulco bridge
 */
class TradingDashboard : JPanel(BorderLayout()) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val moneyfanBridge = MoneyfanBridge()
    private val spaceGraphLoader = SpaceGraphMasterLoader()
    
    // UI Components
    private val portfolioPanel = JPanel(BorderLayout())
    private val tradingPanel = JPanel(BorderLayout()) 
    private val marketPanel = JPanel(BorderLayout())
    private val controlPanel = JPanel(FlowLayout())
    
    // Display components
    private val portfolioTable = createPortfolioTable()
    private val tradingStatusLabel = JLabel("Trading Status: Ready")
    private val marketIndicatorsPanel = JPanel(GridLayout(2, 3))
    private val chartPanel = JPanel(BorderLayout())
    
    init {
        setupDashboardUI()
        startDataSubscriptions()
    }
    
    private fun setupDashboardUI() {
        background = Color.BLACK
        
        // Create tabbed pane for main content
        val tabbedPane = JTabbedPane().apply {
            background = Color.BLACK
            foreground = Color.WHITE
            
            // Portfolio tab
            portfolioPanel.apply {
                background = Color.BLACK
                border = TitledBorder("Portfolio Overview").apply { titleColor = Color.CYAN }
                add(JScrollPane(portfolioTable), BorderLayout.CENTER)
                add(createPortfolioSummary(), BorderLayout.SOUTH)
            }
            addTab("Portfolio", portfolioPanel)
            
            // Trading tab
            tradingPanel.apply {
                background = Color.BLACK
                border = TitledBorder("Trading Operations").apply { titleColor = Color.GREEN }
                add(createTradingControls(), BorderLayout.NORTH)
                add(chartPanel, BorderLayout.CENTER)
                add(tradingStatusLabel, BorderLayout.SOUTH)
            }
            addTab("Trading", tradingPanel)
            
            // Market tab
            marketPanel.apply {
                background = Color.BLACK
                border = TitledBorder("Market Analysis").apply { titleColor = Color.YELLOW }
                add(marketIndicatorsPanel, BorderLayout.NORTH)
                add(createMarketChart(), BorderLayout.CENTER)
            }
            addTab("Market", marketPanel)
            
            // SpaceGraph tab
            addTab("SpaceGraph", spaceGraphLoader)
        }
        
        add(tabbedPane, BorderLayout.CENTER)
        
        // Control panel at top
        controlPanel.apply {
            background = Color.BLACK
            
            add(JButton("Start Trading").apply {
                background = Color.DARK_GRAY
                foreground = Color.GREEN
                addActionListener { startTrading() }
            })
            
            add(JButton("Stop Trading").apply {
                background = Color.DARK_GRAY
                foreground = Color.RED
                addActionListener { stopTrading() }
            })
            
            add(JButton("Load Data").apply {
                background = Color.DARK_GRAY
                foreground = Color.CYAN
                addActionListener { loadHistoricalData() }
            })
            
            add(JButton("Reset").apply {
                background = Color.DARK_GRAY
                foreground = Color.YELLOW
                addActionListener { resetDashboard() }
            })
        }
        add(controlPanel, BorderLayout.NORTH)
    }
    
    private fun createPortfolioTable(): JTable {
        val columnNames = arrayOf("Symbol", "Quantity", "Price", "Value", "Baseline", "Deviation", "Status")
        val tableModel = javax.swing.table.DefaultTableModel(columnNames, 0)
        
        return JTable(tableModel).apply {
            background = Color.BLACK
            foreground = Color.WHITE
            gridColor = Color.GRAY
            selectionBackground = Color.DARK_GRAY
            
            // Custom cell renderer for colored values
            setDefaultRenderer(Object::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?, value: Any?, isSelected: Boolean,
                    hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    
                    if (!isSelected) {
                        cell.background = Color.BLACK
                        
                        // Color code based on column
                        when (column) {
                            5 -> { // Deviation column
                                val deviation = value?.toString()?.replace("%", "")?.toDoubleOrNull()
                                cell.foreground = when {
                                    deviation == null -> Color.WHITE
                                    deviation > 3.0 -> Color.GREEN
                                    deviation < -3.0 -> Color.RED
                                    else -> Color.YELLOW
                                }
                            }
                            2, 3 -> cell.foreground = Color.CYAN // Price and Value
                            else -> cell.foreground = Color.WHITE
                        }
                    }
                    
                    return cell
                }
            })
        }
    }
    
    private fun createPortfolioSummary(): JPanel {
        return JPanel(GridLayout(1, 4)).apply {
            background = Color.BLACK
            
            val totalValueLabel = JLabel("Total Value: $0.00").apply { 
                foreground = Color.WHITE 
                font = Font(Font.MONOSPACED, Font.BOLD, 12)
            }
            val cashBalanceLabel = JLabel("Cash: $0.00").apply { 
                foreground = Color.GREEN
                font = Font(Font.MONOSPACED, Font.BOLD, 12)
            }
            val dayPnLLabel = JLabel("Day P&L: $0.00").apply { 
                foreground = Color.YELLOW
                font = Font(Font.MONOSPACED, Font.BOLD, 12)
            }
            val deviationLabel = JLabel("Portfolio Dev: 0.00%").apply { 
                foreground = Color.CYAN
                font = Font(Font.MONOSPACED, Font.BOLD, 12)
            }
            
            add(totalValueLabel)
            add(cashBalanceLabel)
            add(dayPnLLabel)
            add(deviationLabel)
        }
    }
    
    private fun createTradingControls(): JPanel {
        return JPanel(GridLayout(2, 4)).apply {
            background = Color.BLACK
            
            // Strategy controls
            add(JLabel("Strategy:").apply { foreground = Color.WHITE })
            add(JComboBox(arrayOf("Carlos RSI2", "Kraken Skimmer", "Combined")).apply {
                background = Color.DARK_GRAY
                foreground = Color.WHITE
            })
            
            add(JLabel("Risk Level:").apply { foreground = Color.WHITE })
            add(JSlider(1, 10, 5).apply {
                background = Color.BLACK
                foreground = Color.WHITE
            })
            
            // Trading parameters
            add(JLabel("Position Size:").apply { foreground = Color.WHITE })
            add(JTextField("1000").apply {
                background = Color.DARK_GRAY
                foreground = Color.WHITE
            })
            
            add(JLabel("Stop Loss %:").apply { foreground = Color.WHITE })
            add(JTextField("2.0").apply {
                background = Color.DARK_GRAY
                foreground = Color.WHITE
            })
        }
    }
    
    private fun createMarketChart(): JPanel {
        return JPanel().apply {
            background = Color.BLACK
            preferredSize = Dimension(600, 300)
            
            // Placeholder for market chart - would integrate with actual charting library
            add(JLabel("<html><center>" +
                "<h3 style='color: cyan;'>Market Chart</h3>" +
                "<p style='color: white;'>Real-time price visualization</p>" +
                "<p style='color: yellow;'>RSI, SMA, and volume indicators</p>" +
                "</center></html>"))
        }
    }
    
    private fun startDataSubscriptions() {
        // Subscribe to portfolio state changes
        scope.launch {
            moneyfanBridge.portfolioState.collect { portfolioState ->
                portfolioState?.let { updatePortfolioDisplay(it) }
            }
        }
        
        // Subscribe to trading state changes
        scope.launch {
            moneyfanBridge.tradingState.collect { tradingState ->
                tradingState?.let { updateTradingDisplay(it) }
            }
        }
        
        // Subscribe to market state changes
        scope.launch {
            moneyfanBridge.marketState.collect { marketState ->
                marketState?.let { updateMarketDisplay(it) }
            }
        }
    }
    
    private fun updatePortfolioDisplay(portfolioState: AcapulcoPortfolioState) {
        SwingUtilities.invokeLater {
            val tableModel = portfolioTable.model as javax.swing.table.DefaultTableModel
            tableModel.rowCount = 0 // Clear existing rows
            
            portfolioState.assets.forEach { asset ->
                tableModel.addRow(arrayOf(
                    asset.symbol,
                    asset.quantity.toPlainString(),
                    asset.price?.toPlainString() ?: "N/A",
                    asset.value?.toPlainString() ?: "N/A",
                    asset.baseline?.let { "%.2f".format(it) } ?: "N/A",
                    asset.deviation?.let { "%.2f%%".format(it * 100) } ?: "N/A",
                    if (asset.deviation != null && asset.deviation > 0.03) "HARVEST" else "HOLD"
                ))
            }
            
            // Update summary labels
            val summaryPanel = portfolioPanel.getComponent(1) as JPanel
            val labels = summaryPanel.components
            
            (labels[0] as JLabel).text = "Total Value: ${portfolioState.totalValue.toPlainString()}"
            (labels[1] as JLabel).text = "Cash: ${portfolioState.cashBalance.toPlainString()}"
            (labels[2] as JLabel).text = "Day P&L: ${portfolioState.coreState.dayPnL.value}"
            (labels[3] as JLabel).text = "Return: ${"%.2f%%".format(portfolioState.coreState.totalReturn * 100)}"
        }
    }
    
    private fun updateTradingDisplay(tradingState: AcapulcoTradingState) {
        SwingUtilities.invokeLater {
            tradingStatusLabel.text = "Trading Status: " + when {
                tradingState.anyTrades -> "Active - Harvested: ${tradingState.harvestedAmount}"
                tradingState.crashProtectionActive -> "Crash Protection Active"
                else -> "Monitoring - Portfolio Dev: ${"%.2f%%".format(tradingState.portfolioDeviation * 100)}"
            }
            
            tradingStatusLabel.foreground = when {
                tradingState.crashProtectionActive -> Color.RED
                tradingState.anyTrades -> Color.GREEN
                else -> Color.YELLOW
            }
        }
    }
    
    private fun updateMarketDisplay(marketState: MarketState) {
        SwingUtilities.invokeLater {
            marketIndicatorsPanel.removeAll()
            
            // Update market indicators
            val candleCount = marketState.candleSeries.size
            val latestRSI = if (marketState.rsi.size > 0) marketState.rsi[marketState.rsi.size - 1].value else 0.0
            val latestSMA = if (marketState.sma.size > 0) marketState.sma[marketState.sma.size - 1].value else 0.0
            
            marketIndicatorsPanel.add(createIndicatorLabel("Candles", candleCount.toString(), Color.WHITE))
            marketIndicatorsPanel.add(createIndicatorLabel("RSI", "%.2f".format(latestRSI), 
                if (latestRSI > 70) Color.RED else if (latestRSI < 30) Color.GREEN else Color.YELLOW))
            marketIndicatorsPanel.add(createIndicatorLabel("SMA", "%.2f".format(latestSMA), Color.CYAN))
            
            marketIndicatorsPanel.revalidate()
            marketIndicatorsPanel.repaint()
        }
    }
    
    private fun createIndicatorLabel(name: String, value: String, color: Color): JPanel {
        return JPanel(BorderLayout()).apply {
            background = Color.BLACK
            
            add(JLabel(name).apply { 
                foreground = Color.WHITE
                font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
            }, BorderLayout.NORTH)
            
            add(JLabel(value).apply { 
                foreground = color
                font = Font(Font.MONOSPACED, Font.BOLD, 14)
            }, BorderLayout.CENTER)
        }
    }
    
    private fun startTrading() {
        scope.launch {
            // Simulate trading activity
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
    
    private fun stopTrading() {
        // Stop trading operations
        SwingUtilities.invokeLater {
            tradingStatusLabel.text = "Trading Status: Stopped"
            tradingStatusLabel.foreground = Color.RED
        }
    }
    
    private fun loadHistoricalData() {
        // Trigger SpaceGraph master loader
        SwingUtilities.invokeLater {
            val tabbedPane = getComponent(0) as JTabbedPane
            tabbedPane.selectedIndex = 3 // Switch to SpaceGraph tab
        }
    }
    
    private fun resetDashboard() {
        SwingUtilities.invokeLater {
            val tableModel = portfolioTable.model as javax.swing.table.DefaultTableModel
            tableModel.rowCount = 0
            
            tradingStatusLabel.text = "Trading Status: Ready"
            tradingStatusLabel.foreground = Color.WHITE
            
            marketIndicatorsPanel.removeAll()
            marketIndicatorsPanel.revalidate()
            marketIndicatorsPanel.repaint()
        }
    }
    
    fun cleanup() {
        scope.cancel()
        spaceGraphLoader.cleanup()
    }
}

/**
 * Factory for creating Trading Dashboard in MDI
 */
object TradingDashboardFactory {
    
    fun createDashboardWindow(): JInternalFrame {
        val dashboard = TradingDashboard()
        
        return JInternalFrame("Trading Dashboard", true, true, true, true).apply {
            contentPane = dashboard
            size = Dimension(1000, 700)
            setLocation(25, 25)
            
            addInternalFrameListener(object : javax.swing.event.InternalFrameAdapter() {
                override fun internalFrameClosing(e: javax.swing.event.InternalFrameEvent) {
                    dashboard.cleanup()
                }
            })
        }
    }
}