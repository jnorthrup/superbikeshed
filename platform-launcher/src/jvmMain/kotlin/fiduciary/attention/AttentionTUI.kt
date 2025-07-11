package fiduciary.attention

import fiduciary.percolator.*
import com.googlecode.lanterna.*
import com.googlecode.lanterna.graphics.*
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.dialogs.*
import com.googlecode.lanterna.input.*
import com.googlecode.lanterna.terminal.*
import com.googlecode.lanterna.screen.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * Terminal User Interface for Attention Management
 * 
 * Provides interactive buttons to drill into attention requests
 * and drives actual attention allocation in the system
 */
class AttentionTUI(
    private val blackboard: PercolatorBlackboard,
    private val coordinator: PercolatorCoordinator
) {
    private lateinit var gui: MultiWindowTextGUI
    private lateinit var window: BasicWindow
    private val running = AtomicBoolean(true)
    private val scope = CoroutineScope(Dispatchers.Default)
    
    // UI Components
    private lateinit var attentionPanel: Panel
    private lateinit var detailPanel: Panel
    private lateinit var metricsPanel: Panel
    private lateinit var actionPanel: Panel
    
    // Current selection
    private var selectedSticky: BlackboardSticky? = null
    private var selectedNode: String? = null
    
    fun start() {
        // Create terminal
        val terminal = DefaultTerminalFactory().createTerminal()
        val screen = TerminalScreen(terminal)
        screen.startScreen()
        
        gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace(TextColor.ANSI.BLUE))
        
        // Create main window
        window = BasicWindow("🧠 Attention Management TUI")
        window.setHints(listOf(Window.Hint.FULL_SCREEN))
        
        // Setup layout
        setupLayout()
        
        // Start update loop
        scope.launch {
            while (running.get()) {
                updateDisplay()
                delay(1.seconds)
            }
        }
        
        // Run GUI
        gui.addWindowAndWait(window)
    }
    
    private fun setupLayout() {
        val mainPanel = Panel(GridLayout(2))
        
        // Left side - Attention requests and metrics
        val leftPanel = Panel(LinearLayout(Direction.VERTICAL))
        
        // Attention Panel
        attentionPanel = Panel(LinearLayout(Direction.VERTICAL))
        attentionPanel.addComponent(Label("📋 Active Attention Requests").addStyle(SGR.BOLD))
        attentionPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
        
        leftPanel.addComponent(attentionPanel.withBorder(Borders.singleLine("Attention Queue")))
        
        // Metrics Panel
        metricsPanel = Panel(LinearLayout(Direction.VERTICAL))
        setupMetricsPanel()
        leftPanel.addComponent(metricsPanel.withBorder(Borders.singleLine("Network Metrics")))
        
        // Right side - Details and actions
        val rightPanel = Panel(LinearLayout(Direction.VERTICAL))
        
        // Detail Panel
        detailPanel = Panel(LinearLayout(Direction.VERTICAL))
        detailPanel.addComponent(Label("🔍 Details").addStyle(SGR.BOLD))
        detailPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
        detailPanel.addComponent(Label("Select an attention request to view details"))
        
        rightPanel.addComponent(detailPanel.withBorder(Borders.singleLine("Request Details")))
        
        // Action Panel
        actionPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        setupActionPanel()
        rightPanel.addComponent(actionPanel.withBorder(Borders.singleLine("Actions")))
        
        // Add to main panel
        mainPanel.addComponent(leftPanel)
        mainPanel.addComponent(rightPanel)
        
        // Status bar
        val statusBar = Panel(LinearLayout(Direction.HORIZONTAL))
        statusBar.addComponent(Label("Press ESC to exit | Use TAB to navigate | ENTER to select"))
        
        // Final layout
        val contentPanel = Panel(LinearLayout(Direction.VERTICAL))
        contentPanel.addComponent(mainPanel)
        contentPanel.addComponent(statusBar)
        
        window.component = contentPanel
        
        // Key bindings
        window.addWindowListener(object : WindowListenerAdapter() {
            override fun onInput(
                basePane: Window,
                keyStroke: KeyStroke,
                deliverEvent: AtomicBoolean
            ) {
                when (keyStroke.keyType) {
                    KeyType.Escape -> {
                        running.set(false)
                        window.close()
                    }
                    else -> {}
                }
            }
        })
    }
    
    private fun setupMetricsPanel() {
        val stats = coordinator.getNetworkStats()
        
        metricsPanel.addComponent(Label("📊 Network Status").addStyle(SGR.BOLD))
        metricsPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
        
        val grid = Panel(GridLayout(2))
        grid.addComponent(Label("Active Nodes:"))
        grid.addComponent(Label("${stats.activeNodes}/${stats.totalNodes}"))
        
        grid.addComponent(Label("Pending Work:"))
        grid.addComponent(Label("${stats.pendingWork}"))
        
        grid.addComponent(Label("In Progress:"))
        grid.addComponent(Label("${stats.claimedWork}"))
        
        grid.addComponent(Label("Completed:"))
        grid.addComponent(Label("${stats.completedWork}"))
        
        metricsPanel.addComponent(grid)
    }
    
    private fun setupActionPanel() {
        // Claim button
        val claimButton = Button("🎯 Claim Work") {
            selectedSticky?.let { sticky ->
                showNodeSelector { nodeId ->
                    claimAttention(nodeId, sticky.contentHash)
                }
            }
        }
        
        // Drill down button
        val drillButton = Button("🔍 Drill Down") {
            selectedSticky?.let { sticky ->
                showDetailedView(sticky)
            }
        }
        
        // Allocate attention button
        val allocateButton = Button("🧠 Allocate Attention") {
            selectedSticky?.let { sticky ->
                allocateAttention(sticky)
            }
        }
        
        // Emergency button
        val emergencyButton = Button("🚨 Emergency") {
            createEmergencyAttention()
        }
        emergencyButton.setRenderer(object : Button.FlatButtonRenderer() {
            override fun getThemeDefinition(button: Button): ThemeDefinition {
                return ThemeDefinition(TextColor.ANSI.WHITE, TextColor.ANSI.RED, SGR.BOLD)
            }
        })
        
        actionPanel.addComponent(claimButton)
        actionPanel.addComponent(drillButton)
        actionPanel.addComponent(allocateButton)
        actionPanel.addComponent(emergencyButton)
    }
    
    private suspend fun updateDisplay() {
        // Update attention requests
        updateAttentionPanel()
        
        // Update metrics
        updateMetricsPanel()
        
        // Update details if something selected
        selectedSticky?.let { updateDetailPanel(it) }
    }
    
    private fun updateAttentionPanel() {
        runBlocking {
            gui.updateScreen()
        }
        
        attentionPanel.removeAllComponents()
        attentionPanel.addComponent(Label("📋 Active Attention Requests").addStyle(SGR.BOLD))
        attentionPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
        
        // Get all attention types
        AttentionType.values().forEach { type ->
            val stickies = blackboard.getStickyByType(type)
            if (stickies.isNotEmpty()) {
                attentionPanel.addComponent(Label("${getTypeIcon(type)} $type (${stickies.size})").addStyle(SGR.UNDERLINE))
                
                stickies.forEach { sticky ->
                    val button = createAttentionButton(sticky)
                    attentionPanel.addComponent(button)
                }
                
                attentionPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
            }
        }
    }
    
    private fun createAttentionButton(sticky: BlackboardSticky): Button {
        val timeAgo = Clock.System.now() - sticky.postedAt
        val label = buildString {
            append("[${sticky.priority}] ")
            append(sticky.content.take(40))
            if (sticky.content.length > 40) append("...")
            append(" (${formatDuration(timeAgo)})")
        }
        
        val button = Button(label) {
            selectedSticky = sticky
            updateDetailPanel(sticky)
        }
        
        // Color based on priority
        when {
            sticky.priority > 0.8 -> button.setRenderer(createColoredRenderer(TextColor.ANSI.RED))
            sticky.priority > 0.5 -> button.setRenderer(createColoredRenderer(TextColor.ANSI.YELLOW))
            else -> button.setRenderer(createColoredRenderer(TextColor.ANSI.GREEN))
        }
        
        return button
    }
    
    private fun updateDetailPanel(sticky: BlackboardSticky) {
        detailPanel.removeAllComponents()
        detailPanel.addComponent(Label("🔍 Request Details").addStyle(SGR.BOLD))
        detailPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
        
        // Basic info
        val infoGrid = Panel(GridLayout(2))
        infoGrid.addComponent(Label("Type:"))
        infoGrid.addComponent(Label("${getTypeIcon(sticky.type)} ${sticky.type}"))
        
        infoGrid.addComponent(Label("Priority:"))
        infoGrid.addComponent(Label("${sticky.priority}"))
        
        infoGrid.addComponent(Label("Content Hash:"))
        infoGrid.addComponent(Label(sticky.contentHash.take(12) + "..."))
        
        infoGrid.addComponent(Label("Posted:"))
        infoGrid.addComponent(Label(sticky.postedAt.toString()))
        
        infoGrid.addComponent(Label("Status:"))
        infoGrid.addComponent(Label(sticky.status.toString()))
        
        detailPanel.addComponent(infoGrid)
        detailPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
        
        // Content
        detailPanel.addComponent(Label("Content:").addStyle(SGR.UNDERLINE))
        val contentText = TextBox(TerminalSize(40, 5), sticky.content)
        contentText.isReadOnly = true
        detailPanel.addComponent(contentText)
        
        // Claims
        if (sticky.claimedBy.isNotEmpty()) {
            detailPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
            detailPanel.addComponent(Label("Claims (${sticky.claimedBy.size}):").addStyle(SGR.UNDERLINE))
            sticky.claimedBy.forEach { claim ->
                detailPanel.addComponent(Label("  • ${claim.nodeId} at ${claim.claimedAt}"))
            }
        }
        
        // Solutions
        if (sticky.solutions.isNotEmpty()) {
            detailPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
            detailPanel.addComponent(Label("Solutions (${sticky.solutions.size}):").addStyle(SGR.UNDERLINE))
            sticky.solutions.forEach { solution ->
                detailPanel.addComponent(Label("  • ${solution.nodeId}: ${solution.content.take(50)}..."))
            }
        }
        
        // Metadata
        if (sticky.metadata.isNotEmpty()) {
            detailPanel.addComponent(EmptySpace(TerminalSize(0, 1)))
            detailPanel.addComponent(Label("Metadata:").addStyle(SGR.UNDERLINE))
            sticky.metadata.forEach { (key, value) ->
                detailPanel.addComponent(Label("  $key: $value"))
            }
        }
    }
    
    private fun updateMetricsPanel() {
        metricsPanel.removeAllComponents()
        setupMetricsPanel()
    }
    
    private fun showNodeSelector(onSelect: (String) -> Unit) {
        val dialog = ActionListDialogBuilder()
            .setTitle("Select Node")
            .setDescription("Choose a node to perform this action")
        
        // Get available nodes
        val nodes = listOf("node_1", "node_2", "node_3") // Would get from coordinator
        
        nodes.forEach { nodeId ->
            dialog.addAction(nodeId) { onSelect(nodeId) }
        }
        
        dialog.build().showDialog(gui)
    }
    
    private fun claimAttention(nodeId: String, contentHash: String) {
        scope.launch {
            when (val result = blackboard.claimAttention(nodeId, contentHash)) {
                is ClaimResult.Success -> {
                    MessageDialog.showMessageDialog(
                        gui,
                        "Success",
                        "Attention claimed by $nodeId\nDebenture: ${result.debenture.id}"
                    )
                    updateDisplay()
                }
                is ClaimResult.NotFound -> {
                    MessageDialog.showMessageDialog(gui, "Error", "Attention request not found")
                }
                is ClaimResult.NoDebenture -> {
                    MessageDialog.showMessageDialog(gui, "Error", "No debenture available for this node")
                }
            }
        }
    }
    
    private fun showDetailedView(sticky: BlackboardSticky) {
        val detailWindow = BasicWindow("Detailed View: ${sticky.contentHash}")
        val panel = Panel(LinearLayout(Direction.VERTICAL))
        
        // Add comprehensive details
        panel.addComponent(Label("Full Content:").addStyle(SGR.BOLD))
        val fullContent = TextBox(TerminalSize(60, 20), sticky.content)
        fullContent.isReadOnly = true
        panel.addComponent(fullContent)
        
        // Add graph visualization placeholder
        panel.addComponent(EmptySpace(TerminalSize(0, 1)))
        panel.addComponent(Label("Knowledge Graph Connections:").addStyle(SGR.BOLD))
        panel.addComponent(Label("[Graph visualization would appear here]"))
        
        // Close button
        panel.addComponent(EmptySpace(TerminalSize(0, 1)))
        panel.addComponent(Button("Close") { detailWindow.close() })
        
        detailWindow.component = panel
        gui.addWindow(detailWindow)
    }
    
    private fun allocateAttention(sticky: BlackboardSticky) {
        // This is where we actually drive attention in the system
        val dialog = TextInputDialogBuilder()
            .setTitle("Allocate Attention")
            .setDescription("How many nodes should focus on this?")
            .setInitialContent("5")
            .build()
        
        val result = dialog.showDialog(gui)
        result?.toIntOrNull()?.let { nodeCount ->
            scope.launch {
                // Actually allocate attention resources
                repeat(nodeCount) { i ->
                    blackboard.postAttention(
                        content = "Allocated subtask $i for: ${sticky.content}",
                        type = sticky.type,
                        priority = sticky.priority * 0.8,
                        metadata = mapOf("parent" to sticky.contentHash)
                    )
                }
                
                MessageDialog.showMessageDialog(
                    gui,
                    "Attention Allocated",
                    "Created $nodeCount subtasks for distributed processing"
                )
                
                updateDisplay()
            }
        }
    }
    
    private fun createEmergencyAttention() {
        val dialog = TextInputDialogBuilder()
            .setTitle("🚨 Emergency Attention Request")
            .setDescription("Enter emergency attention content:")
            .build()
        
        val content = dialog.showDialog(gui)
        if (!content.isNullOrBlank()) {
            scope.launch {
                blackboard.postAttention(
                    content = content,
                    type = AttentionType.EMERGENCY,
                    priority = 1.0,
                    metadata = mapOf(
                        "source" to "TUI",
                        "timestamp" to Clock.System.now().toString()
                    )
                )
                
                MessageDialog.showMessageDialog(
                    gui,
                    "Emergency Posted",
                    "Emergency attention request has been broadcast to all nodes"
                )
                
                updateDisplay()
            }
        }
    }
    
    // === Utility Functions ===
    
    private fun getTypeIcon(type: AttentionType): String {
        return when (type) {
            AttentionType.EXTRACTION -> "📤"
            AttentionType.TRANSCRIPTION -> "🎤"
            AttentionType.TRANSLATION -> "🌐"
            AttentionType.ANALYSIS -> "📊"
            AttentionType.VERIFICATION -> "✓"
            AttentionType.SYNTHESIS -> "🔀"
            AttentionType.EMERGENCY -> "🚨"
        }
    }
    
    private fun formatDuration(duration: kotlin.time.Duration): String {
        return when {
            duration.inWholeMinutes < 1 -> "${duration.inWholeSeconds}s ago"
            duration.inWholeHours < 1 -> "${duration.inWholeMinutes}m ago"
            duration.inWholeDays < 1 -> "${duration.inWholeHours}h ago"
            else -> "${duration.inWholeDays}d ago"
        }
    }
    
    private fun createColoredRenderer(color: TextColor): Button.ButtonRenderer {
        return object : Button.FlatButtonRenderer() {
            override fun getThemeDefinition(button: Button): ThemeDefinition {
                return if (button.isFocused) {
                    ThemeDefinition(TextColor.ANSI.BLACK, color, SGR.BOLD)
                } else {
                    ThemeDefinition(color, TextColor.ANSI.DEFAULT, SGR.NORMAL)
                }
            }
        }
    }
    
    fun stop() {
        running.set(false)
        scope.cancel()
    }
}

/**
 * Main entry point for Attention TUI
 */
fun main() {
    val blackboard = PercolatorBlackboard()
    val coordinator = PercolatorCoordinator()
    
    // Add some sample attention requests
    runBlocking {
        blackboard.postAttention(
            "Extract and analyze patrick_0720.txt for FDIC references",
            AttentionType.EXTRACTION,
            0.9
        )
        
        blackboard.postAttention(
            "Transcribe audio file patrick_call_0721.mp3",
            AttentionType.TRANSCRIPTION,
            0.7
        )
        
        blackboard.postAttention(
            "Verify claims about SF30 contract modifications",
            AttentionType.VERIFICATION,
            0.8
        )
        
        blackboard.postAttention(
            "Analyze topic distribution across Patrick Devine corpus",
            AttentionType.ANALYSIS,
            0.6
        )
    }
    
    val tui = AttentionTUI(blackboard, coordinator)
    tui.start()
}