package rtsgame

import kotlinx.coroutines.*
import kotlinx.datetime.*

// Top-level data classes for minimal flow mode
data class GameState(
    val entities: MutableList<GameEntity> = mutableListOf(),
    var currentTime: Long = 0L,
    val resources: Map<String, Int> = mapOf(
        "gold" to 1000,
        "wood" to 500,
        "food" to 200
    )
)

data class GameEntity(
    val id: String,
    val type: String,
    val position: Position,
    val health: Int = 100,
    val playerId: Int = 1
)

data class Position(val x: Float, val y: Float, val z: Float = 0f)

/**
 * Native macOS entry point for RTS Game Flow Mode
 * Simplified version that works with the current codebase
 */
actual fun platformMain() {
    println("🚀 Starting RTS Game Flow Mode on Native macOS...")
    
    // Run the flow mode demo
    runBlocking {
        runFlowModeDemo()
    }
}

/**
 * Main flow mode demo that showcases basic game simulation
 */
suspend fun runFlowModeDemo() {
    println("🌊 Initializing Flow Mode...")
    
    // Simple game state
    val gameState = GameState()
    
    // Add some initial entities
    gameState.entities.addAll(listOf(
        GameEntity("unit1", "scout", Position(10f, 20f), 100, 1),
        GameEntity("unit2", "warrior", Position(30f, 40f), 150, 1),
        GameEntity("unit3", "enemy", Position(50f, 60f), 120, 2)
    ))
    
    println("✅ Game state initialized with ${gameState.entities.size} entities")
    
    // Simple game loop
    var tick = 0
    while (tick < 100) {
        tick++
        gameState.currentTime = Clock.System.now().toEpochMilliseconds()
        
        // Update entity positions (simple movement)
        gameState.entities.forEach { entity ->
            val newX = entity.position.x + (if (entity.playerId == 1) 1f else -1f)
            val newY = entity.position.y + 0.5f
            val newPosition = Position(newX, newY, entity.position.z)
            
            // Update entity position
            val index = gameState.entities.indexOfFirst { it.id == entity.id }
            if (index != -1) {
                gameState.entities[index] = entity.copy(position = newPosition)
            }
        }
        
        // Print status every 10 ticks
        if (tick % 10 == 0) {
            println("⏱️ Tick $tick - Entities: ${gameState.entities.size}")
            gameState.entities.forEach { entity ->
                println("  ${entity.type} (${entity.id}) at (${entity.position.x}, ${entity.position.y}) - HP: ${entity.health}")
            }
        }
        
        // Simulate some time passing
        delay(100)
    }
    
    println("🎮 Flow Mode Demo Complete!")
    println("📊 Final Stats:")
    println("  Total ticks: $tick")
    println("  Final entities: ${gameState.entities.size}")
    println("  Resources: ${gameState.resources}")
}