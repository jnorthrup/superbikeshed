package moneyfan.ui

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.TitledBorder
import kotlin.math.*

/**
 * SpaceGraph Master Loading Visualization for MDI
 * Shows async data loading progress across multiple latency scales
 */
class SpaceGraphMasterLoader : JPanel(BorderLayout()) {
    
    @kotlin.jvm.JvmInline
    value class LoadingPhase(val name: String)
    
    @kotlin.jvm.JvmInline 
    value class LatencyScale(val milliseconds: Long)
    
    @kotlin.jvm.JvmInline
    value class LoadingProgress(val percentage: Double)
    
    // Loading phases with different latency scales
    private val loadingPhases = listOf(
        LoadingPhase("Archive Discovery") to LatencyScale(100),      // 100ms - file system scan
        LoadingPhase("Symbol Metadata") to LatencyScale(500),       // 500ms - parse symbol info  
        LoadingPhase("Historical Klines") to LatencyScale(2000),    // 2s - load OHLCV data
        LoadingPhase("Attention Analysis") to LatencyScale(1500),   // 1.5s - volume/volatility calc
        LoadingPhase("Strategy Calculation") to LatencyScale(800),  // 800ms - RSI/SMA computation
        LoadingPhase("Signal Generation") to LatencyScale(300),     // 300ms - combine strategies
        LoadingPhase("Visualization Prep") to LatencyScale(200)     // 200ms - prepare charts
    )
    
    private val loadingStates = mutableMapOf<LoadingPhase, LoadingProgress>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isLoading = false
    
    // Disk Utility style components
    private val progressBars = mutableMapOf<LoadingPhase, JProgressBar>()
    private val statusLabels = mutableMapOf<LoadingPhase, JLabel>()
    private val controlPanel = JPanel(FlowLayout())
    private val statusLabel = JLabel("Ready to load historical data")
    
    init {
        setupSpaceGraphUI()
        initializeLoadingStates()
    }
    
    private fun setupSpaceGraphUI() {
        background = Color.BLACK
        
        // Top control panel
        controlPanel.apply {
            background = Color.BLACK
            
            val loadButton = JButton("Start Master Load").apply {
                background = Color.DARK_GRAY
                foreground = Color.CYAN
                addActionListener { startMasterLoading() }
            }
            add(loadButton)
            
            val resetButton = JButton("Reset").apply {
                background = Color.DARK_GRAY
                foreground = Color.YELLOW
                addActionListener { resetLoading() }
            }
            add(resetButton)
            
            add(Box.createHorizontalStrut(20))
            add(statusLabel.apply { foreground = Color.WHITE })
        }
        add(controlPanel, BorderLayout.NORTH)
        
        // Progress bars panel (Disk Utility style)
        val progressPanel = createProgressBarsPanel()
        add(progressPanel, BorderLayout.CENTER)
    }
    
    private fun createProgressBarsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Color.BLACK
            border = TitledBorder("Master Loading Progress").apply {
                titleColor = Color.CYAN
            }
        }
        
        loadingPhases.forEach { (phase, latency) ->
            val phasePanel = JPanel(BorderLayout()).apply {
                background = Color.BLACK
                maximumSize = Dimension(Int.MAX_VALUE, 80)
                border = javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10)
            }
            
            // Phase info panel (left side)
            val infoPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = Color.BLACK
                preferredSize = Dimension(200, 60)
            }
            
            val nameLabel = JLabel(phase.name).apply {
                foreground = Color.WHITE
                font = Font(Font.SANS_SERIF, Font.BOLD, 12)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            
            val latencyLabel = JLabel("Latency: ${latency.milliseconds}ms").apply {
                foreground = Color.GRAY
                font = Font(Font.MONOSPACED, Font.PLAIN, 10)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            
            val statusLabel = JLabel("Waiting...").apply {
                foreground = Color.YELLOW
                font = Font(Font.MONOSPACED, Font.PLAIN, 9)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            statusLabels[phase] = statusLabel
            
            infoPanel.add(nameLabel)
            infoPanel.add(Box.createVerticalStrut(2))
            infoPanel.add(latencyLabel)
            infoPanel.add(Box.createVerticalStrut(2))
            infoPanel.add(statusLabel)
            
            phasePanel.add(infoPanel, BorderLayout.WEST)
            
            // Progress bar (center/right)
            val progressBar = JProgressBar(0, 100).apply {
                background = Color.BLACK
                foreground = Color.CYAN
                
                // Disk Utility style colors
                putClientProperty("JProgressBar.style", "circular")
                setBorder(javax.swing.BorderFactory.createRaisedBevelBorder())
                
                // Custom UI for better visibility
                stringPainted = true
                string = "0%"
                font = Font(Font.MONOSPACED, Font.BOLD, 10)
            }
            progressBars[phase] = progressBar
            
            val progressPanel = JPanel(BorderLayout()).apply {
                background = Color.BLACK
                add(progressBar, BorderLayout.CENTER)
                
                // Add some padding
                border = javax.swing.BorderFactory.createEmptyBorder(15, 20, 15, 20)
            }
            
            phasePanel.add(progressPanel, BorderLayout.CENTER)
            
            // Speed indicator (right side)
            val speedPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = Color.BLACK
                preferredSize = Dimension(100, 60)
            }
            
            val speedLabel = JLabel("0 KB/s").apply {
                foreground = Color.GREEN
                font = Font(Font.MONOSPACED, Font.PLAIN, 10)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            
            val etaLabel = JLabel("ETA: --").apply {
                foreground = Color.ORANGE
                font = Font(Font.MONOSPACED, Font.PLAIN, 9)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            
            speedPanel.add(Box.createVerticalGlue())
            speedPanel.add(speedLabel)
            speedPanel.add(Box.createVerticalStrut(2))
            speedPanel.add(etaLabel)
            speedPanel.add(Box.createVerticalGlue())
            
            phasePanel.add(speedPanel, BorderLayout.EAST)
            
            panel.add(phasePanel)
            panel.add(Box.createVerticalStrut(5))
        }
        
        // Overall progress at bottom
        val overallPanel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createRaisedBevelBorder(),
                javax.swing.BorderFactory.createEmptyBorder(10, 15, 10, 15)
            )
        }
        
        val overallLabel = JLabel("Overall Progress").apply {
            foreground = Color.WHITE
            font = Font(Font.SANS_SERIF, Font.BOLD, 14)
        }
        
        val overallProgressBar = JProgressBar(0, 100).apply {
            background = Color.BLACK
            foreground = Color.GREEN
            stringPainted = true
            string = "0%"
            font = Font(Font.MONOSPACED, Font.BOLD, 12)
            preferredSize = Dimension(0, 25)
        }
        progressBars[LoadingPhase("Overall")] = overallProgressBar
        
        overallPanel.add(overallLabel, BorderLayout.WEST)
        overallPanel.add(Box.createHorizontalStrut(20), BorderLayout.CENTER)
        overallPanel.add(overallProgressBar, BorderLayout.CENTER)
        
        panel.add(Box.createVerticalStrut(10))
        panel.add(overallPanel)
        
        return JScrollPane(panel).apply {
            background = Color.BLACK
            viewport.background = Color.BLACK
            border = null
        }
    }
    
    private fun initializeLoadingStates() {
        loadingPhases.forEach { (phase, _) ->
            loadingStates[phase] = LoadingProgress(0.0)
        }
    }
    
    private fun startMasterLoading() {
        if (isLoading) return
        
        isLoading = true
        statusLabel.text = "Loading historical data across multiple latency scales..."
        statusLabel.foreground = Color.YELLOW
        
        scope.launch {
            try {
                loadingPhases.forEach { (phase, latency) ->
                    launch {
                        simulatePhaseLoading(phase, latency)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "Loading failed: ${e.message}"
                    statusLabel.foreground = Color.RED
                }
            }
        }
    }
    
    private suspend fun simulatePhaseLoading(phase: LoadingPhase, latency: LatencyScale) {
        val steps = 100
        val stepDelay = latency.milliseconds / steps
        
        statusLabels[phase]?.let { label ->
            SwingUtilities.invokeLater {
                label.text = "Loading..."
                label.foreground = Color.ORANGE
            }
        }
        
        repeat(steps) { step ->
            val progress = LoadingProgress((step + 1).toDouble() / steps)
            loadingStates[phase] = progress
            
            SwingUtilities.invokeLater {
                updateProgressBar(phase, progress)
                updateStatusIfComplete()
            }
            
            delay(stepDelay)
        }
        
        statusLabels[phase]?.let { label ->
            SwingUtilities.invokeLater {
                label.text = "Complete"
                label.foreground = Color.GREEN
            }
        }
    }
    
    private fun updateProgressBar(phase: LoadingPhase, progress: LoadingProgress) {
        progressBars[phase]?.let { bar ->
            val percentage = (progress.percentage * 100).toInt()
            bar.value = percentage
            bar.string = "$percentage%"
            
            // Update color based on progress
            bar.foreground = when {
                percentage >= 100 -> Color.GREEN
                percentage >= 50 -> Color.YELLOW
                else -> Color.CYAN
            }
        }
        
        // Update overall progress
        val totalProgress = loadingStates.values.map { it.percentage }.average()
        progressBars[LoadingPhase("Overall")]?.let { bar ->
            val overallPercentage = (totalProgress * 100).toInt()
            bar.value = overallPercentage
            bar.string = "$overallPercentage%"
        }
    }
    
    private fun updateStatusIfComplete() {
        val allComplete = loadingStates.values.all { it.percentage >= 1.0 }
        if (allComplete && isLoading) {
            isLoading = false
            statusLabel.text = "Master loading complete - Launching SpaceGraph..."
            statusLabel.foreground = Color.GREEN
            showSpaceGraphVisualization()
        }
    }
    
    private fun resetLoading() {
        scope.cancel()
        isLoading = false
        initializeLoadingStates()
        statusLabel.text = "Ready to load historical data"
        statusLabel.foreground = Color.WHITE
        
        // Reset all progress bars
        progressBars.values.forEach { bar ->
            bar.value = 0
            bar.string = "0%"
            bar.foreground = Color.CYAN
        }
        
        // Reset status labels
        statusLabels.values.forEach { label ->
            label.text = "Waiting..."
            label.foreground = Color.YELLOW
        }
    }
    
    // Add SpaceGraph JVM visualization at completion
    private fun showSpaceGraphVisualization() {
        if (loadingStates.values.all { it.percentage >= 1.0 }) {
            scope.launch {
                delay(1000) // Brief pause after completion
                SwingUtilities.invokeLater {
                    try {
                        // Import SpaceGraph JVM components
                        val spaceGraphWindow = createSpaceGraphWindow()
                        
                        // Find parent MDI frame and add SpaceGraph window
                        var parent = this@SpaceGraphMasterLoader.parent
                        while (parent != null && parent !is JDesktopPane) {
                            parent = parent.parent
                        }
                        
                        if (parent is JDesktopPane) {
                            parent.add(spaceGraphWindow)
                            spaceGraphWindow.isVisible = true
                            spaceGraphWindow.toFront()
                        }
                    } catch (e: Exception) {
                        statusLabel.text = "SpaceGraph integration failed: ${e.message}"
                        statusLabel.foreground = Color.RED
                    }
                }
            }
        }
    }
    
    private fun createSpaceGraphWindow(): JInternalFrame {
        return JInternalFrame("SpaceGraph Data Visualization", true, true, true, true).apply {
            val spaceGraphPanel = JPanel(BorderLayout()).apply {
                background = Color.BLACK
                
                // SpaceGraph placeholder - would integrate with actual SpaceGraph JVM
                val graphArea = JPanel().apply {
                    background = Color.BLACK
                    preferredSize = Dimension(600, 400)
                    
                    add(JLabel("<html><center>" +
                        "<h2 style='color: cyan;'>SpaceGraph JVM Integration</h2>" +
                        "<p style='color: white;'>Historical Data Network Graph</p>" +
                        "<p style='color: yellow;'>Nodes: Symbols, Edges: Correlations</p>" +
                        "<p style='color: green;'>Interactive 3D Visualization Ready</p>" +
                        "</center></html>"))
                }
                
                add(graphArea, BorderLayout.CENTER)
                
                val controlsPanel = JPanel(FlowLayout()).apply {
                    background = Color.BLACK
                    
                    add(JButton("Reset View").apply {
                        background = Color.DARK_GRAY
                        foreground = Color.CYAN
                    })
                    
                    add(JButton("Export Graph").apply {
                        background = Color.DARK_GRAY  
                        foreground = Color.GREEN
                    })
                    
                    add(JButton("3D Mode").apply {
                        background = Color.DARK_GRAY
                        foreground = Color.YELLOW
                    })
                }
                
                add(controlsPanel, BorderLayout.SOUTH)
            }
            
            contentPane = spaceGraphPanel
            size = Dimension(700, 500)
            setLocation(100, 100)
        }
    }
    
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * Factory for creating SpaceGraph Master Loader in MDI
 */
object SpaceGraphMasterLoaderFactory {
    
    fun createLoaderWindow(): JInternalFrame {
        val loader = SpaceGraphMasterLoader()
        
        return JInternalFrame("SpaceGraph Master Loader", true, true, true, true).apply {
            contentPane = loader
            size = Dimension(800, 600)
            setLocation(50, 50)
            
            addInternalFrameListener(object : javax.swing.event.InternalFrameAdapter() {
                override fun internalFrameClosing(e: javax.swing.event.InternalFrameEvent) {
                    loader.cleanup()
                }
            })
        }
    }
}