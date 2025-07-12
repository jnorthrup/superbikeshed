package rtsgame.core

import rtsgame.compat.*

// TrikeShed-compatible types
typealias Indexed<T> = List<T>
data class Join<A, B>(val first: A, val second: B)
infix fun <A, B> A.j(second: B): Join<A, B> = this j second
typealias Series<T> = List<T>

/**
 * Game engine that implements the TrikeShed-compatible game state management
 * Expected by tests using Series and Join types
 */
class GameEngine {
    
    /**
     * Game state with TrikeShed types
     */
    data class GameState(
        val entities: Indexed<GameEntity>,
        val tick: GameTick
    )
    
    /**
     * Game entity with TrikeShed Join types
     */
    data class GameEntity(
        val id: EntityId,
        val position: Join<XCoord, YCoord>,
        val health: Health,
        val playerId: PlayerId
    )
    
    // TrikeShed type definitions
    @JvmInline value class GameTick(val value: Int)
    @JvmInline value class EntityId(val value: String)
    @JvmInline value class XCoord(val value: Float)
    @JvmInline value class YCoord(val value: Float)
    @JvmInline value class Health(val value: Float)
    @JvmInline value class PlayerId(val value: Int)
    
    private var currentTick = 0
    
    /**
     * Create initial game state
     */
    fun tick(): GameState {
        val entities = createInitialEntities()
        return GameState(
            entities = entities,
            tick = GameTick(currentTick)
        )
    }
    
    /**
     * Simulate one game tick
     */
    fun simulateTick(state: GameState): GameState {
        val newTick = GameTick(state.tick.value + 1)
        
        // Update entities (simple movement simulation)
        val updatedEntities = state.entities.`play`.map { entity ->
            updateEntityPosition(entity)
        }
        
        return GameState(
            entities = createIndexedFromList(updatedEntities),
            tick = newTick
        )
    }
    
    private fun createInitialEntities(): Indexed<GameEntity> {
        val entities = listOf(
            GameEntity(
                id = EntityId("unit_1"),
                position = XCoord(100f) j YCoord(150f),
                health = Health(100f),
                playerId = PlayerId(1)
            ),
            GameEntity(
                id = EntityId("unit_2"),
                position = XCoord(200f) j YCoord(250f),
                health = Health(100f),
                playerId = PlayerId(2)
            )
        )
        
        return createIndexedFromList(entities)
    }
    
    private fun updateEntityPosition(entity: GameEntity): GameEntity {
        // Simple random movement within bounds
        val currentX = entity.position.component1().value
        val currentY = entity.position.component2().value
        
        // Random movement with small steps
        val deltaX = (kotlin.random.Random.nextFloat() - 0.5f) * 10f
        val deltaY = (kotlin.random.Random.nextFloat() - 0.5f) * 10f
        
        val newX = (currentX + deltaX).coerceIn(20f, 800f)
        val newY = (currentY + deltaY).coerceIn(120f, 600f)
        
        return entity.copy(
            position = XCoord(newX) j YCoord(newY)
        )
    }
    
    private fun createIndexedFromList(entities: List<GameEntity>): Indexed<GameEntity> {
        return \1 j { \2: Int -> entities[index] }
    }
}