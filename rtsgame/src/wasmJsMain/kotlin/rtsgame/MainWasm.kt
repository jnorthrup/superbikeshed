package rtsgame

import borg.trikeshed.lib.*
import rtsgame.demo.*
import rtsgame.core.*

actual fun platformMain() {
    println("Interactive RTS WebGPU Demo - WASM Version")
    println("Click buttons to interact with the simulation!")
    
    // Simple demo state for WASM
    val gameEngine = GameEngine()
    var gameState = gameEngine.tick()
    
    println("Initial state: ${gameState.entities.play.size} entities")
    
    // Simulate a few ticks
    repeat(3) { i: Int ->
        gameState = gameEngine.simulateTick(gameState)
        println("Tick ${gameState.tick.value}: ${gameState.entities.play.size} entities")
    }
    
    println("WASM demo ready - integrate with HTML canvas for full interactivity")
}

fun main() {
    platformMain()
}