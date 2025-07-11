package rtsgame.demo

import rtsgame.core.*
import kotlinx.coroutines.*

/**
 * Interactive WebGPU demo for RTS game
 */
class InteractiveWebGPUDemo {
    private val engine = GameEngine()
    private var gameState = engine.tick()
    private var _isRunning = false
    
    val isRunning: Boolean get() = _isRunning
    
    /**
     * Render a frame and return the current game state
     */
    fun renderFrame(): DemoState {
        if (_isRunning) {
            gameState = engine.simulateTick(gameState)
        }
        
        return DemoState(
            isRunning = _isRunning,
            entityCount = gameState.entities.size,
            tick = gameState.tick.value
        )
    }
    
    /**
     * Handle mouse click at given coordinates
     */
    fun handleMouseClick(x: Float, y: Float): MouseInteraction {
        // Check if click is on START button (around 75, 65)
        if (x >= 50f && x <= 100f && y >= 50f && y <= 80f) {
            _isRunning = true
            return MouseInteraction.StartButton
        }
        
        // Check if click is on STOP button 
        if (x >= 110f && x <= 160f && y >= 50f && y <= 80f) {
            _isRunning = false
            return MouseInteraction.StopButton
        }
        
        return MouseInteraction.GameArea(x, y)
    }
    
    /**
     * Handle mouse release
     */
    fun handleMouseRelease(x: Float, y: Float): MouseInteraction {
        return MouseInteraction.Release(x, y)
    }
    
    /**
     * Dispose resources
     */
    fun dispose() {
        _isRunning = false
    }
}

/**
 * Demo state representation
 */
data class DemoState(
    val isRunning: Boolean,
    val entityCount: Int,
    val tick: Int
)

/**
 * Mouse interaction types
 */
sealed class MouseInteraction {
    object StartButton : MouseInteraction()
    object StopButton : MouseInteraction()
    data class GameArea(val x: Float, val y: Float) : MouseInteraction()
    data class Release(val x: Float, val y: Float) : MouseInteraction()
}

/**
 * Create and run the interactive WebGPU demo
 */
suspend fun runInteractiveWebGPUDemo(): InteractiveWebGPUDemo {
    return withContext(Dispatchers.Default) {
        InteractiveWebGPUDemo()
    }
}