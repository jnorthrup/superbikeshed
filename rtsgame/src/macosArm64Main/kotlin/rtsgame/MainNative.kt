package rtsgame

import kotlinx.coroutines.*

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
    
    val gameState = GameState()
    
    // Create initial entities
    val entities = listOf(
        GameEntity("commander1", "COMMANDER", Position(0f, 0f)),
        GameEntity("scout1", "SCOUT", Position(10f, 5f)),
        GameEntity("worker1", "WORKER", Position(-5f, 3f)),
        GameEntity("soldier1", "SOLDIER", Position(15f, -2f)),
        GameEntity("archer1", "ARCHER", Position(-10f, 8f)),
        GameEntity("cavalry1", "CAVALRY", Position(20f, 12f)),
        GameEntity("siege1", "SIEGE", Position(-15f, -5f)),
        GameEntity("navy1", "NAVY", Position(25f, 25f))
    )
    
    gameState.entities.addAll(entities)
    
    println("✅ Game state initialized with ${gameState.entities.size} entities")
    
    // Game loop
    var tick = 0
    while (tick < 100) {
        tick++
        
        // Update game state
        gameState.currentTime = System.currentTimeMillis()
        
        // Simulate entity movement and interactions
        gameState.entities.forEachIndexed { index, entity ->
            // Simple movement simulation
            val newX = entity.position.x + (kotlin.random.Random.nextFloat() - 0.5f) * 2f
            val newY = entity.position.y + (kotlin.random.Random.nextFloat() - 0.5f) * 2f
            
            val updatedEntity = entity.copy(
                position = Position(newX, newY, entity.position.z)
            )
            gameState.entities[index] = updatedEntity
        }
        
        // Print game state every 10 ticks
        if (tick % 10 == 0) {
            println("⏱️  Tick $tick - ${gameState.entities.size} entities active")
            gameState.entities.take(3).forEach { entity ->
                println("  ${entity.type} at (${entity.position.x.toInt()}, ${entity.position.y.toInt()})")
            }
        }
        
        delay(100) // 100ms per tick
    }
    
    println("🎮 Flow Mode Demo Complete!")
    println("📊 Final Stats:")
    println("  - Total entities: ${gameState.entities.size}")
    println("  - Total ticks: $tick")
    println("  - Resources: ${gameState.resources}")
}