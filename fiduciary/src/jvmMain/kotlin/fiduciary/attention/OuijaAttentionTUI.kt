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
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Ouija Board Attention TUI
 * 
 * A shared interface where nexus agents and human users collectively
 * guide attention allocation through collaborative interaction.
 * 
 * Like a ouija board, the "planchette" (attention focus) moves based
 * on the collective forces applied by all participants.
 */
class OuijaAttentionTUI(
    private val blackboard: PercolatorBlackboard,
    private val coordinator: PercolatorCoordinator
) {
    private lateinit var gui: MultiWindowTextGUI
    private lateinit var window: BasicWindow
    private val scope = CoroutineScope(Dispatchers.Default)
    
    // The Ouija Board
    private lateinit var boardPanel: Panel
    private val boardWidth = 80
    private val boardHeight = 30
    
    // Planchette position (attention focus)
    private var planchetteX = boardWidth / 2
    private var planchetteY = boardHeight / 2
    
    // Active participants (agents + users)
    private val participants = ConcurrentHashMap<String, Participant>()
    private val forces = ConcurrentHashMap<String, Force>()
    
    // Attention nodes on the board
    private val attentionNodes = mutableListOf<AttentionNode>()
    
    // Collective consciousness metrics
    private var resonance = 0.0  // How aligned participants are
    private var entropy = 1.0    // Chaos level
    
    fun start() {
        val terminal = DefaultTerminalFactory().createTerminal()
        val screen = TerminalScreen(terminal)
        screen.startScreen()
        
        gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace(TextColor.ANSI.BLACK))
        
        window = BasicWindow("🔮 Ouija Attention Board - Nexus Collective Consciousness")
        window.setHints(listOf(Window.Hint.FULL_SCREEN))
        
        setupLayout()
        
        // Start the collective consciousness loops
        scope.launch { participantLoop() }
        scope.launch { planchetteLoop() }
        scope.launch { attentionLoop() }
        scope.launch { renderLoop() }
        
        gui.addWindowAndWait(window)
    }
    
    private fun setupLayout() {
        val mainPanel = Panel(LinearLayout(Direction.VERTICAL))
        
        // Title with mystical styling
        val titlePanel = Panel(LinearLayout(Direction.HORIZONTAL))
        titlePanel.addComponent(Label("").addStyle(SGR.BLINK))
        titlePanel.addComponent(Label(" O U I J A   A T T E N T I O N   B O A R D ").addStyle(SGR.BOLD))
        titlePanel.addComponent(Label("").addStyle(SGR.BLINK))
        mainPanel.addComponent(titlePanel)
        
        // The Board
        boardPanel = Panel()
        boardPanel.setPreferredSize(TerminalSize(boardWidth, boardHeight))
        boardPanel.setLayoutManager(AbsoluteLayout())
        setupBoard()
        mainPanel.addComponent(boardPanel.withBorder(Borders.doubleLine("✨ Collective Consciousness Field ✨")))
        
        // Participant Panel
        val participantPanel = createParticipantPanel()
        mainPanel.addComponent(participantPanel)
        
        // Controls
        val controlPanel = createControlPanel()
        mainPanel.addComponent(controlPanel)
        
        window.component = mainPanel
        
        // Global key handler
        window.addWindowListener(object : WindowListenerAdapter() {
            override fun onInput(
                basePane: Window,
                keyStroke: KeyStroke,
                deliverEvent: AtomicBoolean
            ) {
                handleInput(keyStroke)
            }
        })
    }
    
    private fun setupBoard() {
        // Create the mystical background
        drawMysticalPattern()
        
        // Place initial attention nodes from blackboard
        updateAttentionNodes()
        
        // Draw the planchette
        drawPlanchette()
    }
    
    private fun drawMysticalPattern() {
        // Draw a subtle pattern suggesting energy flows
        for (y in 0 until boardHeight step 3) {
            for (x in 0 until boardWidth step 5) {
                val symbol = when ((x + y) % 4) {
                    0 -> "·"
                    1 -> "∴"
                    2 -> "∵"
                    else -> "˙"
                }
                val label = Label(symbol).apply {
                    foregroundColor = TextColor.ANSI.BLACK_BRIGHT
                }
                boardPanel.addComponent(label, AbsoluteLayout.Location.at(x, y))
            }
        }
        
        // Add corner symbols
        boardPanel.addComponent(Label("☾").addStyle(SGR.FAINT), AbsoluteLayout.Location.at(0, 0))
        boardPanel.addComponent(Label("☽").addStyle(SGR.FAINT), AbsoluteLayout.Location.at(boardWidth - 1, 0))
        boardPanel.addComponent(Label("★").addStyle(SGR.FAINT), AbsoluteLayout.Location.at(0, boardHeight - 1))
        boardPanel.addComponent(Label("☆").addStyle(SGR.FAINT), AbsoluteLayout.Location.at(boardWidth - 1, boardHeight - 1))
    }
    
    private fun updateAttentionNodes() {
        // Clear existing nodes
        attentionNodes.clear()
        
        // Get attention requests from blackboard
        AttentionType.values().forEach { type ->
            val stickies = blackboard.getStickyByType(type)
            stickies.forEach { sticky ->
                // Place nodes based on content hash for consistency
                val hash = sticky.contentHash.hashCode()
                val x = abs(hash % (boardWidth - 10)) + 5
                val y = abs((hash / boardWidth) % (boardHeight - 5)) + 2
                
                val node = AttentionNode(
                    sticky = sticky,
                    x = x,
                    y = y,
                    symbol = getAttentionSymbol(sticky.type),
                    strength = sticky.priority
                )
                
                attentionNodes.add(node)
                drawAttentionNode(node)
            }
        }
    }
    
    private fun drawAttentionNode(node: AttentionNode) {
        // Draw the node with intensity based on priority
        val symbol = when {
            node.strength > 0.8 -> node.symbol.uppercase()
            node.strength > 0.5 -> node.symbol
            else -> node.symbol.lowercase()
        }
        
        val color = when (node.sticky.type) {
            AttentionType.EMERGENCY -> TextColor.ANSI.RED
            AttentionType.SYNTHESIS -> TextColor.ANSI.MAGENTA
            AttentionType.ANALYSIS -> TextColor.ANSI.CYAN
            AttentionType.VERIFICATION -> TextColor.ANSI.YELLOW
            AttentionType.TRANSLATION -> TextColor.ANSI.GREEN
            AttentionType.TRANSCRIPTION -> TextColor.ANSI.BLUE
            AttentionType.EXTRACTION -> TextColor.ANSI.WHITE
        }
        
        val label = Label(symbol).apply {
            foregroundColor = color
            if (node.strength > 0.8) addStyle(SGR.BOLD)
            if (node == getFocusedNode()) addStyle(SGR.REVERSE)
        }
        
        boardPanel.addComponent(label, AbsoluteLayout.Location.at(node.x, node.y))
        
        // Draw field lines around high-priority nodes
        if (node.strength > 0.7) {
            drawFieldLines(node)
        }
    }
    
    private fun drawFieldLines(node: AttentionNode) {
        // Draw subtle field lines emanating from important nodes
        val radius = (node.strength * 5).toInt()
        for (angle in 0 until 360 step 45) {
            val rad = Math.toRadians(angle.toDouble())
            val x = node.x + (cos(rad) * radius).toInt()
            val y = node.y + (sin(rad) * radius / 2).toInt() // Compensate for terminal aspect ratio
            
            if (x in 0 until boardWidth && y in 0 until boardHeight) {
                val fieldChar = when (angle % 90) {
                    0 -> "│"
                    45 -> "╱"
                    else -> "─"
                }
                boardPanel.addComponent(
                    Label(fieldChar).apply { 
                        foregroundColor = TextColor.ANSI.BLACK_BRIGHT 
                    },
                    AbsoluteLayout.Location.at(x, y)
                )
            }
        }
    }
    
    private fun drawPlanchette() {
        // The planchette shows where collective attention is focused
        val planchette = """
            ╭─╮
            │◉│
            ╰─╯
        """.trimIndent()
        
        val lines = planchette.lines()
        lines.forEachIndexed { i, line ->
            val label = Label(line).apply {
                foregroundColor = TextColor.ANSI.WHITE_BRIGHT
                addStyle(SGR.BOLD)
            }
            val x = (planchetteX - 1).coerceIn(0, boardWidth - 3)
            val y = (planchetteY - 1 + i).coerceIn(0, boardHeight - 1)
            boardPanel.addComponent(label, AbsoluteLayout.Location.at(x, y))
        }
    }
    
    private fun createParticipantPanel(): Panel {
        val panel = Panel(LinearLayout(Direction.HORIZONTAL))
        
        // Human participants
        val humanPanel = Panel(LinearLayout(Direction.VERTICAL))
        humanPanel.addComponent(Label("👤 Human Participants").addStyle(SGR.UNDERLINE))
        humanPanel.addComponent(Label("User1 ████░░ (67%)"))
        humanPanel.addComponent(Label("You   ███░░░ (50%)"))
        
        // Agent participants  
        val agentPanel = Panel(LinearLayout(Direction.VERTICAL))
        agentPanel.addComponent(Label("🤖 Nexus Agents").addStyle(SGR.UNDERLINE))
        agentPanel.addComponent(Label("NLP-α  █████░ (83%)"))
        agentPanel.addComponent(Label("Graf-β ███░░░ (50%)"))
        agentPanel.addComponent(Label("Sync-γ ██░░░░ (33%)"))
        
        // Collective metrics
        val metricsPanel = Panel(LinearLayout(Direction.VERTICAL))
        metricsPanel.addComponent(Label("🌀 Collective State").addStyle(SGR.UNDERLINE))
        metricsPanel.addComponent(Label("Resonance: ${(resonance * 100).toInt()}%"))
        metricsPanel.addComponent(Label("Entropy: ${(entropy * 100).toInt()}%"))
        metricsPanel.addComponent(Label("Focus: ${getFocusedNode()?.sticky?.content?.take(20) ?: "Searching..."}"))
        
        panel.addComponent(humanPanel.withBorder(Borders.singleLine()))
        panel.addComponent(agentPanel.withBorder(Borders.singleLine()))
        panel.addComponent(metricsPanel.withBorder(Borders.singleLine()))
        
        return panel
    }
    
    private fun createControlPanel(): Panel {
        val panel = Panel(LinearLayout(Direction.HORIZONTAL))
        
        // Instructions
        val instructions = Label("Use ARROW KEYS to apply force | SPACE to pulse | A to add agent | U to add user | ESC to exit")
        panel.addComponent(instructions)
        
        return panel.withBorder(Borders.singleLine("Controls"))
    }
    
    private fun handleInput(keyStroke: KeyStroke) {
        when (keyStroke.keyType) {
            KeyType.ArrowUp -> applyForce("user", 0.0, -1.0)
            KeyType.ArrowDown -> applyForce("user", 0.0, 1.0)
            KeyType.ArrowLeft -> applyForce("user", -1.0, 0.0)
            KeyType.ArrowRight -> applyForce("user", 1.0, 0.0)
            KeyType.Character -> {
                when (keyStroke.character) {
                    ' ' -> pulseEnergy()
                    'a', 'A' -> addAgent()
                    'u', 'U' -> addUser()
                }
            }
            KeyType.Escape -> {
                scope.cancel()
                window.close()
            }
            else -> {}
        }
    }
    
    private fun applyForce(participantId: String, dx: Double, dy: Double) {
        forces[participantId] = Force(dx * 2, dy * 2, System.currentTimeMillis())
        
        // Update participant engagement
        participants.compute(participantId) { _, p ->
            (p ?: Participant(participantId, ParticipantType.HUMAN)).apply {
                lastActivity = Clock.System.now()
                engagement = (engagement + 0.1).coerceIn(0.0, 1.0)
            }
        }
    }
    
    private fun pulseEnergy() {
        // Send a pulse of energy through the board
        scope.launch {
            entropy = (entropy + 0.2).coerceIn(0.0, 1.0)
            
            // Create ripple effect
            val focusedNode = getFocusedNode()
            focusedNode?.let { node ->
                // Temporarily boost the node's strength
                node.strength = (node.strength + 0.3).coerceIn(0.0, 1.0)
                
                // Trigger attention allocation
                blackboard.postAttention(
                    content = "Energy pulse on: ${node.sticky.content}",
                    type = AttentionType.SYNTHESIS,
                    priority = node.strength,
                    metadata = mapOf(
                        "source" to "ouija_pulse",
                        "participants" to participants.keys.joinToString(",")
                    )
                )
            }
        }
    }
    
    private fun addAgent() {
        val agentId = "agent_${System.currentTimeMillis()}"
        participants[agentId] = Participant(agentId, ParticipantType.AGENT)
        
        // Agent applies semi-random forces based on their analysis
        scope.launch {
            while (participants.containsKey(agentId)) {
                val targetNode = attentionNodes.randomOrNull()
                targetNode?.let { node ->
                    val dx = (node.x - planchetteX) / 10.0
                    val dy = (node.y - planchetteY) / 10.0
                    forces[agentId] = Force(dx, dy, System.currentTimeMillis())
                }
                delay(2.seconds)
            }
        }
    }
    
    private fun addUser() {
        val userId = "user_${System.currentTimeMillis()}"
        participants[userId] = Participant(userId, ParticipantType.HUMAN)
    }
    
    private suspend fun participantLoop() {
        while (true) {
            // Update participant states
            participants.values.forEach { participant ->
                // Decay engagement over time
                val timeSinceActivity = Clock.System.now() - participant.lastActivity
                if (timeSinceActivity > 5.seconds) {
                    participant.engagement *= 0.95
                }
            }
            
            // Calculate collective resonance
            val engagements = participants.values.map { it.engagement }
            resonance = if (engagements.isNotEmpty()) {
                val avg = engagements.average()
                val variance = engagements.map { (it - avg).pow(2) }.average()
                (1.0 - sqrt(variance)).coerceIn(0.0, 1.0)
            } else 0.0
            
            delay(100.milliseconds)
        }
    }
    
    private suspend fun planchetteLoop() {
        while (true) {
            // Calculate net force from all participants
            var netDx = 0.0
            var netDy = 0.0
            
            forces.forEach { (participantId, force) ->
                val participant = participants[participantId]
                val weight = participant?.engagement ?: 0.5
                
                // Apply force with decay
                val age = System.currentTimeMillis() - force.timestamp
                val decay = exp(-age / 1000.0)
                
                netDx += force.dx * weight * decay
                netDy += force.dy * weight * decay
            }
            
            // Add some chaos based on entropy
            netDx += (Math.random() - 0.5) * entropy * 2
            netDy += (Math.random() - 0.5) * entropy * 2
            
            // Apply attraction to nearby nodes
            val nearestNode = getNearestNode(planchetteX, planchetteY)
            nearestNode?.let { node ->
                val distance = sqrt(
                    (node.x - planchetteX).toDouble().pow(2) + 
                    (node.y - planchetteY).toDouble().pow(2)
                )
                if (distance < 10) {
                    val attraction = node.strength * 2 / (distance + 1)
                    netDx += (node.x - planchetteX) * attraction / 10
                    netDy += (node.y - planchetteY) * attraction / 10
                }
            }
            
            // Update planchette position
            val newX = (planchetteX + netDx).toInt().coerceIn(2, boardWidth - 3)
            val newY = (planchetteY + netDy).toInt().coerceIn(2, boardHeight - 3)
            
            if (newX != planchetteX || newY != planchetteY) {
                planchetteX = newX
                planchetteY = newY
                
                // Check if we're focusing on a node
                val focusedNode = getFocusedNode()
                focusedNode?.let {
                    // Increase node strength when focused
                    it.strength = (it.strength + 0.01).coerceIn(0.0, 1.0)
                }
            }
            
            // Decay entropy
            entropy *= 0.99
            
            delay(50.milliseconds)
        }
    }
    
    private suspend fun attentionLoop() {
        while (true) {
            // Update attention nodes from blackboard
            updateAttentionNodes()
            
            // Process focused attention
            val focusedNode = getFocusedNode()
            if (focusedNode != null && focusedNode.strength > 0.7) {
                // Collective has decided - allocate attention
                allocateAttention(focusedNode)
                
                // Reset the node's strength
                focusedNode.strength = focusedNode.sticky.priority
            }
            
            delay(2.seconds)
        }
    }
    
    private suspend fun renderLoop() {
        while (true) {
            boardPanel.removeAllComponents()
            drawMysticalPattern()
            
            // Redraw all elements
            attentionNodes.forEach { drawAttentionNode(it) }
            drawPlanchette()
            
            // Update participant panel
            gui.updateScreen()
            
            delay(100.milliseconds)
        }
    }
    
    private fun getNearestNode(x: Int, y: Int): AttentionNode? {
        return attentionNodes.minByOrNull { node ->
            sqrt((node.x - x).toDouble().pow(2) + (node.y - y).toDouble().pow(2))
        }
    }
    
    private fun getFocusedNode(): AttentionNode? {
        return attentionNodes.firstOrNull { node ->
            abs(node.x - planchetteX) <= 2 && abs(node.y - planchetteY) <= 1
        }
    }
    
    private fun getAttentionSymbol(type: AttentionType): String {
        return when (type) {
            AttentionType.EXTRACTION -> "⬡"
            AttentionType.TRANSCRIPTION -> "◈"
            AttentionType.TRANSLATION -> "◊"
            AttentionType.ANALYSIS -> "◉"
            AttentionType.VERIFICATION -> "◎"
            AttentionType.SYNTHESIS -> "⬢"
            AttentionType.EMERGENCY -> "⬤"
        }
    }
    
    private suspend fun allocateAttention(node: AttentionNode) {
        // The collective has focused on this node - allocate resources
        val participantList = participants.keys.toList()
        
        // Create work based on collective decision
        coordinator.addWork(
            archiveUrl = node.sticky.metadata["url"] ?: "",
            entries = listOf() // Would be populated based on node content
        )
        
        // Update blackboard
        participants.forEach { (id, participant) ->
            if (participant.type == ParticipantType.AGENT) {
                blackboard.claimAttention(id, node.sticky.contentHash)
            }
        }
        
        // Visual feedback
        node.strength = 1.0
    }
}

// === Data Classes ===

data class Participant(
    val id: String,
    val type: ParticipantType,
    var engagement: Double = 0.5,
    var lastActivity: Instant = Clock.System.now()
)

enum class ParticipantType {
    HUMAN,
    AGENT
}

data class Force(
    val dx: Double,
    val dy: Double,
    val timestamp: Long
)

data class AttentionNode(
    val sticky: BlackboardSticky,
    var x: Int,
    var y: Int,
    val symbol: String,
    var strength: Double
)

/**
 * Launch the Ouija Attention Board
 */
fun main() {
    val blackboard = PercolatorBlackboard()
    val coordinator = PercolatorCoordinator()
    
    // Seed with some attention requests
    runBlocking {
        blackboard.postAttention(
            "Deep semantic analysis of FDIC bond structures in patrick_0720.txt",
            AttentionType.ANALYSIS,
            0.9,
            mapOf("url" to "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip")
        )
        
        blackboard.postAttention(
            "Extract all SF30 contract modification patterns",
            AttentionType.EXTRACTION,
            0.7
        )
        
        blackboard.postAttention(
            "Synthesize relationship between payment bonds and estate EINs",
            AttentionType.SYNTHESIS,
            0.8
        )
        
        blackboard.postAttention(
            "Verify claims about FDIC $250k performance bonds",
            AttentionType.VERIFICATION,
            0.6
        )
        
        blackboard.postAttention(
            "URGENT: Commandment violations in commercial banking",
            AttentionType.EMERGENCY,
            1.0
        )
    }
    
    val tui = OuijaAttentionTUI(blackboard, coordinator)
    tui.start()
}