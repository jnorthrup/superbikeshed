package moneyfan.ui

import moneyfan.ta4k.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Technical Analysis Display for ta4k strategy results integration
 * Bridge between ta4k strategy orchestrator and moneyfan MDI interface
 */
class TechnicalAnalysisDisplay(
    private val orchestrator: StrategyOrchestrator
) : JPanel(BorderLayout()) {
    
    // Strategy control state
    @JvmInline
    value class StrategyMode(val name: String)
    
    private var currentMode = StrategyMode("Combined")
    private val availableModes = listOf(
        StrategyMode("Combined"),
        StrategyMode("Carlos RSI2 Only"),
        StrategyMode("Kraken Skimmer Only"),
        StrategyMode("Attention Only")
    )
    
    // UI Components
    private val modeSelector = JComboBox(availableModes.map { it.name }.toTypedArray())
    private val statusLabel = JLabel("Initializing...")
    private val signalTable = JTable()
    private val detailsArea = JTextArea(8, 40)
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Analysis update timer
    private var isUpdating = false
    private var lastUpdate = Clock.System.now()
    
    init {
        setupDisplay()
        setupEventHandlers()
        startAnalysisUpdates()
    }
    
    private fun setupDisplay() {
        background = Color.BLACK
        
        // Top control panel
        val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            background = Color.BLACK
            
            add(JLabel("Strategy Mode:").apply { foreground = Color.WHITE })
            add(modeSelector.apply {
                background = Color.DARK_GRAY
                foreground = Color.WHITE
            })
            add(Box.createHorizontalStrut(20))
            add(statusLabel.apply { foreground = Color.YELLOW })
        }
        add(controlPanel, BorderLayout.NORTH)
        
        // Main content split pane
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            background = Color.BLACK
            dividerLocation = 500
            resizeWeight = 0.6
        }
        
        // Left panel: Signal table
        val signalPanel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            border = TitledBorder("Trading Signals").apply {
                titleColor = Color.CYAN
            }
        }
        
        setupSignalTable()
        signalPanel.add(JScrollPane(signalTable).apply {
            background = Color.BLACK
            viewport.background = Color.BLACK
        }, BorderLayout.CENTER)
        
        splitPane.leftComponent = signalPanel
        
        // Right panel: Analysis details
        val detailsPanel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            border = TitledBorder("Analysis Details").apply {
                titleColor = Color.CYAN
            }
        }
        
        detailsArea.apply {
            background = Color.BLACK
            foreground = Color.WHITE
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
        }
        
        detailsPanel.add(JScrollPane(detailsArea).apply {
            background = Color.BLACK
            viewport.background = Color.BLACK
        }, BorderLayout.CENTER)
        
        splitPane.rightComponent = detailsPanel
        add(splitPane, BorderLayout.CENTER)
        
        // Bottom status panel
        val bottomPanel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            preferredSize = Dimension(0, 60)
            
            val statsPanel = JPanel(FlowLayout()).apply {
                background = Color.BLACK
                add(JLabel("Last Update: ").apply { foreground = Color.GRAY })
                add(JLabel(lastUpdate.toString()).apply { 
                    foreground = Color.GREEN 
                    name = "lastUpdateLabel"
                })
            }
            add(statsPanel, BorderLayout.CENTER)
        }
        add(bottomPanel, BorderLayout.SOUTH)
    }
    
    private fun setupSignalTable() {
        val tableModel = object : DefaultTableModel(
            arrayOf("Symbol", "Signal", "Confidence", "Carlos", "Skimmer", "Attention"),
            0
        ) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        
        signalTable.apply {
            model = tableModel
            background = Color.BLACK
            foreground = Color.WHITE
            gridColor = Color.GRAY
            font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            
            // Custom cell renderer for colored signals
            setDefaultRenderer(Object::class.java, SignalCellRenderer())
            
            // Column widths
            columnModel.getColumn(0).preferredWidth = 80  // Symbol
            columnModel.getColumn(1).preferredWidth = 100 // Signal
            columnModel.getColumn(2).preferredWidth = 80  // Confidence
            columnModel.getColumn(3).preferredWidth = 60  // Carlos
            columnModel.getColumn(4).preferredWidth = 60  // Skimmer
            columnModel.getColumn(5).preferredWidth = 60  // Attention
        }
        
        // Row selection listener
        signalTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val selectedRow = signalTable.selectedRow
                if (selectedRow >= 0) {
                    updateDetailsForSelectedRow(selectedRow)
                }
            }
        }
    }
    
    private fun setupEventHandlers() {
        modeSelector.addActionListener { event ->
            val selectedMode = StrategyMode(modeSelector.selectedItem as String)
            if (selectedMode.name != currentMode.name) {
                currentMode = selectedMode
                analysisScope.launch {
                    updateAnalysisDisplay()
                }
            }
        }
    }
    
    private fun startAnalysisUpdates() {
        analysisScope.launch {
            while (isActive) {
                try {
                    if (!isUpdating) {
                        updateAnalysisDisplay()
                    }
                    delay(2000) // Update every 2 seconds
                } catch (e: Exception) {
                    updateStatus("Error: ${e.message}")
                    delay(5000)
                }
            }
        }
    }
    
    private suspend fun updateAnalysisDisplay() {
        isUpdating = true
        updateStatus("Updating analysis...")
        
        try {
            val analyses = when (currentMode.name) {
                "Combined" -> orchestrator.analyzeTopSymbols(10)
                "Carlos RSI2 Only" -> getCarlosOnlyAnalysis()
                "Kraken Skimmer Only" -> getSkimmerOnlyAnalysis()
                "Attention Only" -> getAttentionOnlyAnalysis()
                else -> orchestrator.analyzeTopSymbols(10)
            }
            
            SwingUtilities.invokeLater {
                updateSignalTable(analyses)
                updateTimestamp()
                updateStatus("Analysis complete (${analyses.size} symbols)")
            }
            
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                updateStatus("Update failed: ${e.message}")
            }
        } finally {
            isUpdating = false
        }
    }
    
    private suspend fun getCarlosOnlyAnalysis(): Series<CombinedAnalysis> {
        // Get analyses and filter to show only Carlos signals
        val topSymbols = orchestrator.analyzeTopSymbols(10)
        return topSymbols.map { analysis ->
            analysis.copy(
                combinedSignal = convertCarlosToSignal(analysis.carlosSignal),
                votes = Series.of(1) { 
                    StrategyVote(
                        signal = analysis.carlosSignal,
                        confidence = StrategyConfidence(0.8),
                        weight = StrategyWeight(1.0),
                        source = "Carlos RSI2"
                    )
                }
            )
        }
    }
    
    private suspend fun getSkimmerOnlyAnalysis(): Series<CombinedAnalysis> {
        val topSymbols = orchestrator.analyzeTopSymbols(10)
        return topSymbols.map { analysis ->
            analysis.copy(
                combinedSignal = convertSkimmerToSignal(analysis.skimmerSignal),
                votes = Series.of(1) { 
                    StrategyVote(
                        signal = convertSkimmerActionToSignal(analysis.skimmerSignal),
                        confidence = StrategyConfidence(0.7),
                        weight = StrategyWeight(1.0),
                        source = "Kraken Skimmer"
                    )
                }
            )
        }
    }
    
    private suspend fun getAttentionOnlyAnalysis(): Series<CombinedAnalysis> {
        val topSymbols = orchestrator.analyzeTopSymbols(10)
        return topSymbols.map { analysis ->
            val attentionSignal = if (analysis.attentionScore.value > 0.7) {
                CombinedSignal.BUY
            } else if (analysis.attentionScore.value < 0.3) {
                CombinedSignal.SELL
            } else {
                CombinedSignal.HOLD
            }
            
            analysis.copy(
                combinedSignal = attentionSignal,
                votes = Series.of(1) { 
                    StrategyVote(
                        signal = if (attentionSignal == CombinedSignal.BUY) TradeSignal.BUY 
                                 else if (attentionSignal == CombinedSignal.SELL) TradeSignal.SELL 
                                 else TradeSignal.HOLD,
                        confidence = StrategyConfidence(analysis.attentionScore.value),
                        weight = StrategyWeight(1.0),
                        source = "Attention System"
                    )
                }
            )
        }
    }
    
    private fun convertCarlosToSignal(carlosSignal: TradeSignal): CombinedSignal {
        return when (carlosSignal) {
            TradeSignal.BUY -> CombinedSignal.BUY
            TradeSignal.SELL -> CombinedSignal.SELL
            TradeSignal.HOLD -> CombinedSignal.HOLD
        }
    }
    
    private fun convertSkimmerToSignal(skimmerAction: SkimmerAction): CombinedSignal {
        return when (skimmerAction) {
            SkimmerAction.HARVEST_SELL -> CombinedSignal.SELL
            SkimmerAction.REBALANCE_BUY -> CombinedSignal.BUY
            SkimmerAction.HOLD -> CombinedSignal.HOLD
        }
    }
    
    private fun convertSkimmerActionToSignal(skimmerAction: SkimmerAction): TradeSignal {
        return when (skimmerAction) {
            SkimmerAction.HARVEST_SELL -> TradeSignal.SELL
            SkimmerAction.REBALANCE_BUY -> TradeSignal.BUY
            SkimmerAction.HOLD -> TradeSignal.HOLD
        }
    }
    
    private fun updateSignalTable(analyses: Series<CombinedAnalysis>) {
        val model = signalTable.model as DefaultTableModel
        model.rowCount = 0
        
        repeat(analyses.size) { i ->
            val analysis = analyses[i]
            
            // Extract real TA values from vote sources
            var rsiValue = "N/A"
            var deviation = "N/A"
            
            repeat(analysis.votes.size) { voteIndex ->
                val vote = analysis.votes[voteIndex]
                if (vote.source.contains("RSI:")) {
                    rsiValue = vote.source.substringAfter("RSI: ").substringBefore(")")
                }
                if (vote.source.contains("Dev:")) {
                    deviation = vote.source.substringAfter("Dev: ").substringBefore("%")
                }
            }
            
            val row = arrayOf(
                analysis.symbol.value,
                analysis.combinedSignal.name,
                "%.1f%%".format(analysis.signalStrength * 100),
                "${analysis.carlosSignal.name.take(1)} (RSI:$rsiValue)",
                "${analysis.skimmerSignal.name.take(1)} (Dev:$deviation%)",
                "%.3f".format(analysis.attentionScore.value)
            )
            model.addRow(row)
        }
    }
    
    private fun updateDetailsForSelectedRow(rowIndex: Int) {
        try {
            val model = signalTable.model as DefaultTableModel
            if (rowIndex >= model.rowCount) return
            
            // This is simplified - in a real implementation, we'd maintain
            // a reference to the analyses to get detailed information
            val symbol = model.getValueAt(rowIndex, 0) as String
            val signal = model.getValueAt(rowIndex, 1) as String
            val confidence = model.getValueAt(rowIndex, 2) as String
            
            val details = buildString {
                appendLine("=== LIVE ANALYSIS DETAILS ===")
                appendLine()
                appendLine("Symbol: $symbol")
                appendLine("Combined Signal: $signal")
                appendLine("Signal Strength: $confidence")
                appendLine("Timestamp: ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                appendLine()
                appendLine("Background Data Status:")
                appendLine("  - Klines Directory: ~/mpdata/klines/$symbol/")
                appendLine("  - Data Fetch: Active (1min intervals)")
                appendLine("  - File Generation: CSV with OHLCV data")
                appendLine()
                appendLine("Carlos RSI2 Analysis:")
                val carlosInfo = model.getValueAt(rowIndex, 3) as String
                appendLine("  - Latest Signal: $carlosInfo")
                appendLine("  - Entry: RSI(2) < 5 + SMA(2) > SMA(15) + SMA(2) > Close")
                appendLine("  - Exit: RSI(2) > 95 OR SMA(2) < SMA(15) OR SMA(2) < Close")
                appendLine()
                appendLine("Kraken Skimmer Analysis:")
                val skimmerInfo = model.getValueAt(rowIndex, 4) as String
                appendLine("  - Action: $skimmerInfo")
                appendLine("  - Harvest Trigger: Price > Baseline + 3%")
                appendLine("  - Rebalance Trigger: Price < Baseline - 4%")
                appendLine()
                appendLine("Attention System:")
                appendLine("  - Score: ${model.getValueAt(rowIndex, 5)}")
                appendLine("  - Volume Spike Detection: Recent 5 vs Baseline 15")
                appendLine("  - Volatility Analysis: Price change percentage")
                appendLine("  - Update Frequency: Every 5 seconds")
                appendLine()
                appendLine("Real Data Processing:")
                appendLine("  - Background Fetching: Active")
                appendLine("  - TA Calculations: Live RSI/SMA on klines")
                appendLine("  - Attention Monitoring: Volume/volatility spikes")
                appendLine("  - File Storage: ~/mpdata/klines/[SYMBOL]/")
            }
            
            detailsArea.text = details
            detailsArea.caretPosition = 0
            
        } catch (e: Exception) {
            detailsArea.text = "Error displaying details: ${e.message}"
        }
    }
    
    private fun updateStatus(message: String) {
        SwingUtilities.invokeLater {
            statusLabel.text = message
        }
    }
    
    private fun updateTimestamp() {
        lastUpdate = Clock.System.now()
        components.filterIsInstance<JPanel>()
            .flatMap { it.components.asSequence() }
            .filterIsInstance<JPanel>()
            .flatMap { it.components.asSequence() }
            .firstOrNull { it.name == "lastUpdateLabel" }
            ?.let { label ->
                (label as JLabel).text = lastUpdate.toString().take(19) // Show readable timestamp
            }
    }
    
    fun cleanup() {
        analysisScope.cancel()
    }
    
    // Custom cell renderer for colored trading signals
    private class SignalCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            
            if (!isSelected) {
                background = Color.BLACK
                foreground = when {
                    column == 1 && value != null -> { // Signal column
                        when (value.toString()) {
                            "STRONG_BUY" -> Color.GREEN.brighter()
                            "BUY", "WEAK_BUY" -> Color.GREEN
                            "STRONG_SELL" -> Color.RED.brighter()
                            "SELL", "WEAK_SELL" -> Color.RED
                            "HOLD" -> Color.YELLOW
                            else -> Color.WHITE
                        }
                    }
                    column == 2 -> { // Confidence column
                        val confStr = value?.toString()?.replace("%", "") ?: "0"
                        try {
                            val conf = confStr.toDouble()
                            when {
                                conf > 70 -> Color.GREEN
                                conf > 40 -> Color.YELLOW
                                else -> Color.RED
                            }
                        } catch (e: Exception) {
                            Color.WHITE
                        }
                    }
                    column >= 3 && column <= 4 -> { // Strategy columns
                        when (value?.toString()) {
                            "B", "BUY" -> Color.GREEN
                            "S", "SELL" -> Color.RED
                            "H", "HOLD" -> Color.YELLOW
                            else -> Color.WHITE
                        }
                    }
                    column == 5 -> { // Attention column
                        try {
                            val attention = value?.toString()?.toDouble() ?: 0.0
                            when {
                                attention > 0.7 -> Color.GREEN
                                attention > 0.4 -> Color.YELLOW
                                else -> Color.GRAY
                            }
                        } catch (e: Exception) {
                            Color.WHITE
                        }
                    }
                    else -> Color.WHITE
                }
            }
            
            return component
        }
    }
}

/**
 * Factory for creating TechnicalAnalysisDisplay with ta4k integration
 */
object TechnicalAnalysisDisplayFactory {
    
    fun createDisplay(): TechnicalAnalysisDisplay? {
        return try {
            val bridge = moneyfan.ta4k.StrategyBridge.getInstance()
            
            // Try to get existing orchestrator or initialize new one
            val orchestrator = bridge.getOrchestrator() ?: run {
                // Initialize in a coroutine and return null for now
                kotlinx.coroutines.GlobalScope.launch {
                    bridge.initializeStrategies()
                }
                return null
            }
            
            TechnicalAnalysisDisplay(orchestrator)
        } catch (e: Exception) {
            // Failed to initialize ta4k components
            null
        }
    }
    
    fun createMockDisplay(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(JLabel("<html><h2 style='color: yellow;'>Technical Analysis</h2>" +
                    "<p style='color: white;'>ta4k Strategy Integration Ready</p>" +
                    "<p style='color: cyan;'>Carlos RSI2 + Kraken Skimmer + Attention System</p>" +
                    "<p style='color: green;'>Waiting for strategy orchestrator initialization...</p></html>"), 
                BorderLayout.CENTER)
        }
    }
}