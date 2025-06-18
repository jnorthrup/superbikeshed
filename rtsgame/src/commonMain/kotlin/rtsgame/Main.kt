package rtsgame

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.Position
import rtsgame.core.*
import rtsgame.demo.startSpaceGraphHttpServer

class RTSGameEngine {
    private var gameState = GameState(
        entities = Series.of(0) { throw IndexOutOfBoundsException() }
    )
    
    fun initialize(): GameState {
        // Create initial game state
        val initialEntities = listOf(
            Entity(
                id = EntityId("unit_001"),
                position = Position(100f, 100f),
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            Entity(
                id = EntityId("unit_002"),
                position = Position(200f, 200f),
                health = Health(80f),
                playerId = PlayerId(2)
            )
        )
        
        gameState = GameState(
            entities = Series.of(initialEntities.size) { i -> initialEntities[i] }
        )
        
        return gameState
    }
    
    fun tick(): GameState {
        gameState = gameState.advance()
        return gameState
    }
    
    fun getState(): GameState = gameState
}

expect fun platformMain()

fun startGame(): RTSGameEngine {
    val engine = RTSGameEngine()
    engine.initialize()
    return engine
}

fun demonstrateGame() {
    println("RTS Game Demonstration")
    val engine = startGame()
    
    repeat(5) { i ->
        val state = engine.tick()
        println("Tick ${state.tick.value}: ${state.entities.play.size} entities")
        state.entities.play.forEachIndexed { index, entity ->
            println("  Entity $index: ${entity.id.value} at (${entity.position.x}, ${entity.position.y})")
        }
    }
}

/**
 * Main entry point for RTS game
 */
suspend fun main() {
    // Start SpaceGraph HTTP server
    startSpaceGraphHttpServer(8080)
}