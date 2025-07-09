package rtsgame

import rtsgame.core.*
import rtsgame.components.*
import javax.swing.*
import java.awt.*
import java.awt.event.*

/**
 * JVM platform-specific main entry point
 */
actual fun platformMain() {
    println("🚀 Starting RTS Game on JVM")
    
    // Create and launch the game
    val launcher = RTSGameLauncher()
    
    // Create a simple Swing window for visualization
    val frame = JFrame("RTS Game - Backend Test")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.size = Dimension(800, 600)
    frame.locationRelativeTo(null)
    
    val canvas = GameCanvas(launcher)
    frame.add(canvas)
    
    frame.isVisible = true
    
    // Start the game loop in a separate thread
    Thread {
        launcher.launch()
    }.start()
}

/**
 * Simple Swing canvas for rendering the game state
 */
class GameCanvas(private val launcher: RTSGameLauncher) : JPanel() {
    private var lastUpdate = 0L
    
    init {
        // Set up timer for rendering
        Timer(16) { // ~60 FPS
            repaint()
        }.start()
        
        // Add mouse listener for input
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // Handle mouse input
                val x = e.x.toFloat()
                val y = e.y.toFloat()
                println("Mouse click at ($x, $y)")
            }
        })
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        
        // Enable antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Clear background
        g2d.color = Color.BLACK
        g2d.fillRect(0, 0, width, height)
        
        // Draw game state
        drawGameState(g2d)
        
        // Draw UI
        drawUI(g2d)
    }
    
    private fun drawGameState(g2d: Graphics2D) {
        val world = launcher.simulation.world
        
        // Draw entities
        val entities = world.getAllEntities()
        entities.forEach { entityId ->
            val position = world.getComponent<PositionComponent>(entityId, ComponentTypeId.POSITION)
            val owner = world.getComponent<OwnerComponent>(entityId, ComponentTypeId.OWNER)
            val entityType = world.getComponent<EntityTypeComponent>(entityId, ComponentTypeId.ENTITY_TYPE)
            val health = world.getComponent<HealthComponent>(entityId, ComponentTypeId.HEALTH)
            
            if (position != null && owner != null && entityType != null) {
                // Scale position to screen coordinates
                val screenX = (position.x / 10f).toInt()
                val screenY = (position.y / 10f).toInt()
                
                // Choose color based on team
                g2d.color = when (owner.teamId) {
                    0 -> Color.BLUE
                    1 -> Color.RED
                    else -> Color.GRAY
                }
                
                // Draw entity
                val size = when (entityType.category) {
                    "unit" -> 8
                    "building" -> 16
                    else -> 6
                }
                
                g2d.fillOval(screenX - size/2, screenY - size/2, size, size)
                
                // Draw health bar
                if (health != null) {
                    val healthRatio = health.healthRatio()
                    g2d.color = when {
                        healthRatio > 0.6f -> Color.GREEN
                        healthRatio > 0.3f -> Color.YELLOW
                        else -> Color.RED
                    }
                    g2d.fillRect(screenX - 10, screenY - size/2 - 8, (20 * healthRatio).toInt(), 3)
                }
                
                // Draw label
                g2d.color = Color.WHITE
                g2d.font = Font("Arial", Font.PLAIN, 10)
                g2d.drawString(entityType.type, screenX + size/2 + 2, screenY + 4)
            }
        }
    }
    
    private fun drawUI(g2d: Graphics2D) {
        g2d.color = Color.WHITE
        g2d.font = Font("Arial", Font.BOLD, 14)
        
        val entityCount = launcher.simulation.getEntityCount()
        val tick = launcher.simulation.currentTick
        
        g2d.drawString("Entities: $entityCount", 10, 20)
        g2d.drawString("Tick: $tick", 10, 40)
        g2d.drawString("FPS: ${1000/16}", 10, 60)
    }
} 