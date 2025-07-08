import kotlin.math.*
package rtsgame.demo
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.ui.*
import rtsgame.webgpu.*
import rtsgame.compat.*

/**
 * Interactive WebGPU demo with real buttons and controls
 * NO PRINTLN - only actual interactive rendering
 */
class InteractiveWebGPUDemo {
    internal val panel = InteractiveWebGPUPanel()
    internal val renderer = InteractiveWebGPURenderer()
    internal var lastFrameTime = 0L
    internal val targetFPS = 60
    internal val frameTimeMs = 1000L / targetFPS
    
    suspend fun initialize(): Boolean {
        return renderer.initialize()
    }
    
    // Main interactive loop - call this repeatedly
    suspend fun renderFrame(): InteractiveDemoState {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val deltaTime = if (lastFrameTime > 0) {
            (currentTime - lastFrameTime) / 1000f
        } else {
            0.016f // ~60 FPS
        }
        lastFrameTime = currentTime
        
        // Update simulation
        val gameState = panel.tick()
        val (_, panelState) = panel.getCurrentState()
        
        // Render everything
        val renderResult = renderer.renderFrame(gameState, panelState, deltaTime)
        
        return InteractiveDemoState(
            gameState = gameState,
            panelState = panelState,
            renderResult = renderResult,
            frameTime = deltaTime,
            isRunning = true
        )
    }
    
    // Handle mouse input - call when user clicks
    fun handleMouseClick(x: Float, y: Float): UIInteraction {
        val interaction = panel.handleMouseInput(x, y, true)
        
        // Handle camera controls
        interaction.buttonClicked?.let { buttonId ->
            when (buttonId.value) {
                "zoom_in" -> renderer.adjustCamera(true)
                "zoom_out" -> renderer.adjustCamera(false)
            }
        }
        
        return interaction
    }
    
    // Handle mouse release
    fun handleMouseRelease(x: Float, y: Float): UIInteraction {
        return panel.handleMouseInput(x, y, false)
    }
    
    fun dispose() {
        renderer.dispose()
    }
}

data class InteractiveDemoState(
    val gameState: GameState,
    val panelState: PanelState,
    val renderResult: InteractiveRenderResult,
    val frameTime: Float,
    val isRunning: Boolean
) {
    // Expose metrics for platform-specific display
    val metricsText: String get() = buildString {
        append("Entities: ${renderResult.entityCount} | ")
        append("Triangles: ${renderResult.totalTriangles} | ")
        append("Frame: ${formatFloat(renderResult.frameTime, 1)}ms | ")
        append("Tick: ${renderResult.tick} | ")
        append("Buttons: ${renderResult.buttonCount}")
    }
}

/**
 * Platform-agnostic entry point for interactive demo
 */
suspend fun runInteractiveWebGPUDemo(): InteractiveWebGPUDemo {
    val demo = InteractiveWebGPUDemo()
    
    if (!demo.initialize()) {
        throw RuntimeException("Failed to initialize WebGPU demo")
    }
    
    return demo
}