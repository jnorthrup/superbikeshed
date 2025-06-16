package rtsgame

import borg.trikeshed.lib.*
import rtsgame.demo.*
import kotlinx.coroutines.*
import javax.swing.*
import java.awt.*
import java.awt.event.*

actual fun platformMain() {
    runBlocking {
        val demo = runInteractiveWebGPUDemo()
        
        // Create Swing window with canvas for interaction
        SwingUtilities.invokeLater {
            createInteractiveWindow(demo)
        }
        
        // Keep running for interactive demo
        while (true) {
            delay(1000)
        }
    }
}

private fun createInteractiveWindow(demo: InteractiveWebGPUDemo) {
    val frame = JFrame("Interactive RTS WebGPU Demo")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(1024, 768)
    
    val canvas = object : JPanel() {
        private var lastState: InteractiveDemoState? = null
        
        init {
            background = Color.BLACK
            preferredSize = Dimension(1024, 768)
            
            // Mouse handling
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    demo.handleMouseClick(e.x.toFloat(), e.y.toFloat())
                    repaint()
                }
                
                override fun mouseReleased(e: MouseEvent) {
                    demo.handleMouseRelease(e.x.toFloat(), e.y.toFloat())
                }
            })
        }
        
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            // Render current state
            runBlocking {
                lastState = demo.renderFrame()
            }
            
            lastState?.let { state ->
                renderGameWorld(g2d, state)
                renderUI(g2d, state)
                renderMetrics(g2d, state)
            }
        }
        
        private fun renderGameWorld(g2d: Graphics2D, state: InteractiveDemoState) {
            // Render entities
            state.gameState.entities.play.forEach { entity ->
                val color = when (entity.playerId.value) {
                    1 -> Color.BLUE
                    2 -> Color.RED
                    else -> Color.GRAY
                }
                
                g2d.color = color
                val x = entity.position.a.value.toInt() - 10
                val y = entity.position.b.value.toInt() - 10
                
                if (entity.health.value > 80f) {
                    g2d.fillOval(x, y, 20, 20) // Commander
                } else {
                    g2d.fillRect(x, y, 15, 15) // Unit
                }
                
                // Health bar
                g2d.color = Color.WHITE
                g2d.drawRect(x, y - 8, 20, 3)
                g2d.color = Color.GREEN
                g2d.fillRect(x + 1, y - 7, (18 * entity.health.value / 100f).toInt(), 2)
            }
        }
        
        private fun renderUI(g2d: Graphics2D, state: InteractiveDemoState) {
            // Render control panel
            g2d.color = Color.DARK_GRAY
            g2d.fillRect(0, 0, width, 100)
            
            // Render buttons
            state.panelState.buttons.play.forEach { button ->
                val x = button.position.x.toInt()
                val y = button.position.y.toInt()
                val w = button.size.x.toInt()
                val h = button.size.y.toInt()
                
                // Button background
                g2d.color = if (button.enabled) Color.LIGHT_GRAY else Color.GRAY
                g2d.fillRect(x, y, w, h)
                
                // Button border
                g2d.color = Color.WHITE
                g2d.drawRect(x, y, w, h)
                
                // Button text
                g2d.color = Color.BLACK
                val fm = g2d.fontMetrics
                val textX = x + (w - fm.stringWidth(button.label)) / 2
                val textY = y + (h + fm.ascent) / 2
                g2d.drawString(button.label, textX, textY)
            }
        }
        
        private fun renderMetrics(g2d: Graphics2D, state: InteractiveDemoState) {
            g2d.color = Color.WHITE
            g2d.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            g2d.drawString(state.metricsText, 10, height - 10)
        }
    }
    
    frame.add(canvas)
    frame.setLocationRelativeTo(null)
    frame.isVisible = true
    
    // Animation timer
    Timer(16) { // ~60 FPS
        canvas.repaint()
    }.start()
}

fun main() {
    platformMain()
}