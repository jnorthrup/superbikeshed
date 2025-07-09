package rtsgame

import kotlinx.coroutines.*
import javax.swing.*
import java.awt.*
import java.awt.event.*

actual fun platformMain() {
    println("🚀 Starting RTS Game on JVM")
    
    runBlocking {
        // Create and launch the game
        val launcher = RTSGameLauncher()
        
        // Create Swing window for the game
        SwingUtilities.invokeLater {
            createGameWindow(launcher)
        }
        
        // Keep running
        while (true) {
            delay(1000)
        }
    }
}

internal fun createGameWindow(launcher: RTSGameLauncher) {
    val frame = JFrame("RTS Game - JVM")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(1024, 768)
    frame.setLocationRelativeTo(null)
    
    val canvas = object : JPanel() {
        init {
            background = Color.BLACK
            preferredSize = Dimension(1024, 768)
            
            // Add mouse handling
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    handleMouseClick(e.x.toFloat(), e.y.toFloat(), launcher)
                    repaint()
                }
                
                override fun mouseReleased(e: MouseEvent) {
                    handleMouseRelease(e.x.toFloat(), e.y.toFloat(), launcher)
                }
            })
            
            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    handleMouseMove(e.x.toFloat(), e.y.toFloat(), launcher)
                }
            })
        }
        
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            // Render the game state
            renderGameState(g2d, launcher)
        }
    }
    
    frame.add(canvas)
    frame.isVisible = true
    
    // Start the game loop
    Timer(16) { // ~60 FPS
        canvas.repaint()
    }.start()
}

private fun handleMouseClick(x: Float, y: Float, launcher: RTSGameLauncher) {
    // Convert screen coordinates to world coordinates
    val worldX = x * 2f // Simple scaling
    val worldY = y * 2f
    
    // Issue move command to selected units
    val selectedUnits = listOf(EntityId(0)) // TODO: Implement unit selection
    val moveCommand = CommandComponent("move", targetX = worldX, targetY = worldY)
    launcher.simulation.issueCommand(selectedUnits, moveCommand)
}

private fun handleMouseRelease(x: Float, y: Float, launcher: RTSGameLauncher) {
    // Handle mouse release events
}

private fun handleMouseMove(x: Float, y: Float, launcher: RTSGameLauncher) {
    // Handle mouse movement events
}

private fun renderGameState(g2d: Graphics2D, launcher: RTSGameLauncher) {
    // Clear background
    g2d.color = Color(20, 20, 40)
    g2d.fillRect(0, 0, width, height)
    
    // Render entities
    val world = launcher.simulation.world
    val entities = world.getAllEntities()
    
    entities.forEach { entityId ->
        val position = world.getComponent<PositionComponent>(entityId, ComponentTypeId.POSITION)
        val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER)
        val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
        
        if (position != null && owner != null && entityType != null) {
            // Convert world coordinates to screen coordinates
            val screenX = (position.x / 2f).toInt()
            val screenY = (position.y / 2f).toInt()
            
            // Choose color based on team
            g2d.color = when (owner.teamId) {
                1 -> Color.BLUE
                2 -> Color.RED
                else -> Color.GRAY
            }
            
            // Draw entity
            when (entityType.category) {
                "unit" -> {
                    g2d.fillOval(screenX - 5, screenY - 5, 10, 10)
                }
                "building" -> {
                    g2d.fillRect(screenX - 8, screenY - 8, 16, 16)
                }
                else -> {
                    g2d.fillOval(screenX - 3, screenY - 3, 6, 6)
                }
            }
            
            // Draw entity type label
            g2d.color = Color.WHITE
            g2d.font = Font("Arial", Font.PLAIN, 10)
            g2d.drawString(entityType.name, screenX + 8, screenY)
        }
    }
    
    // Draw UI
    drawUI(g2d, launcher)
}

private fun drawUI(g2d: Graphics2D, launcher: RTSGameLauncher) {
    g2d.color = Color.WHITE
    g2d.font = Font("Arial", Font.BOLD, 14)
    
    val stats = """
        Entities: ${launcher.simulation.getEntityCount()}
        Tick: ${launcher.simulation.currentTick}
        Update Time: ${launcher.simulation.updateTime / 1_000_000}ms
    """.trimIndent()
    
    val lines = stats.split("\n")
    lines.forEachIndexed { index, line ->
        g2d.drawString(line, 10, 20 + index * 20)
    }
}