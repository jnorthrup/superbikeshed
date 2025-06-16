package rtsgame.core

import borg.trikeshed.lib.*
import rtsgame.compat.*

data class GameTick(val value: Long)
data class EntityId(val value: String)
data class PlayerId(val value: Int)
data class XCoord(val value: Float)
data class YCoord(val value: Float)
data class Health(val value: Float)
data class Energy(val value: Float)

typealias Position = Join<XCoord, YCoord>
typealias ResourceCount = Join<Energy, Int>

fun Position(x: Float, y: Float): Position = XCoord(x) j YCoord(y)

val Position.x: Float get() = this.a.value
val Position.y: Float get() = this.b.value

data class Entity(
    val id: EntityId,
    val position: Position,
    val health: Health,
    val playerId: PlayerId
)

typealias EntitySeries = Series<Entity>

data class GameState(
    val entities: EntitySeries,
    val tick: GameTick = GameTick(0)
) {
    fun advance(): GameState = copy(tick = GameTick(tick.value + 1))
    
    fun addEntity(entity: Entity): GameState {
        val newEntities = entities.α { it } // TODO: Proper entity addition
        return copy(entities = newEntities)
    }
}

/**
 * Game engine for running RTS simulation
 */
class GameEngine {
    fun tick(): GameState {
        // Simple static game state for demo
        val entities = mutableListOf<Entity>()
        
        entities.add(Entity(
            id = EntityId("unit_1"),
            position = Position(100f, 100f),
            health = Health(100f),
            playerId = PlayerId(1)
        ))
        
        entities.add(Entity(
            id = EntityId("unit_2"),
            position = Position(200f, 150f),
            health = Health(75f),
            playerId = PlayerId(2)
        ))
        
        return GameState(
            entities = Series.of(entities.size) { i -> entities[i] },
            tick = GameTick(currentTimeMillis() / 1000)
        )
    }
    
    fun simulateTick(currentState: GameState): GameState {
        // Advance tick and simulate entity movement
        val newEntities = currentState.entities.play.map { entity ->
            // Simple movement simulation
            val newX = entity.position.x + (kotlin.random.Random.nextFloat() - 0.5f) * 10f
            val newY = entity.position.y + (kotlin.random.Random.nextFloat() - 0.5f) * 10f
            
            entity.copy(
                position = Position(newX, newY)
            )
        }
        
        return GameState(
            entities = Series.of(newEntities.size) { i -> newEntities[i] },
            tick = GameTick(currentState.tick.value + 1)
        )
    }
}

/**
 * Start a new game and return the engine
 */
fun startGame(): GameEngine = GameEngine()