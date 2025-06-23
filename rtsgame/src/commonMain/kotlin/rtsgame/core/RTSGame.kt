package rtsgame.core

import rtsgame.compat.*
// Canonical type imports
import com.rtsgame.shared.map.ResourceType
import com.rtsgame.shared.map.Position // Using canonical Position
import com.rtsgame.shared.entity.Entity // Using canonical Entity interface
import com.rtsgame.shared.game.GameState // Using canonical GameState

// borg.trikeshed.lib may still be needed for other things if this file grows.
// For now, it's not directly used by the refactored GameState/Entity.
// import borg.trikeshed.lib.*


data class GameTick(val value: Long) // Remains local, seems fine.
// Local wrapper types for IDs and stats. CoreEntity will bridge to interface's primitive types.
data class LocalEntityId(val value: String)
data class LocalPlayerId(val value: Int)
data class LocalHealth(val value: Float)

/**
 * Core implementation of the Entity interface
 */
data class CoreEntity(
    override val id: String,
    override var position: Position,
    override var health: Float,
    override val maxHealth: Float,
    override val speed: Float,
    override val team: Int,
    val name: String = "Unit"
) : Entity {
    override fun copy(
        id: String,
        position: Position,
        health: Float,
        maxHealth: Float,
        speed: Float,
        team: Int
    ): Entity = CoreEntity(
        id = id,
        position = position,
        health = health,
        maxHealth = maxHealth,
        speed = speed,
        team = team,
        name = this.name
    )
}

/**
 * Game engine for running RTS simulation
 */
class GameEngine {
    fun tick(): GameState {
        // Create initial game state
        val entitiesMap = mutableMapOf<String, Entity>()
        
        // Add some initial entities
        val entity1 = CoreEntity(
            id = "unit_1",
            position = Position(100f, 100f),
            health = 100f,
            maxHealth = 100f,
            speed = 5f,
            team = 1,
            name = "Alpha"
        )
        entitiesMap[entity1.id] = entity1
        
        val entity2 = CoreEntity(
            id = "unit_2",
            position = Position(200f, 150f),
            health = 75f,
            maxHealth = 100f,
            speed = 3f,
            team = 2,
            name = "Beta"
        )
        entitiesMap[entity2.id] = entity2
        
        // Set up initial resources
        val player1Resources = mapOf(
            ResourceType.GOLD to 1000,
            ResourceType.WOOD to 1000,
            ResourceType.FOOD to 1000
        )
        val resources = mapOf(1 to player1Resources)
        
        return GameState(
            entities = entitiesMap,
            resources = resources,
            currentTime = currentTimeMillis() / 1000
        )
    }
    
    fun simulateTick(currentState: GameState): GameState {
        val newEntities = mutableMapOf<String, Entity>()
        
        currentState.entities.values.forEach { entity ->
            // Simple movement simulation
            val newX = entity.position.x + (kotlin.random.Random.nextFloat() - 0.5f) * entity.speed
            val newY = entity.position.y + (kotlin.random.Random.nextFloat() - 0.5f) * entity.speed
            
            // Create new position and update entity
            val newPosition = Position(newX, newY)
            val updatedEntity = entity.copy(position = newPosition)
            newEntities[updatedEntity.id] = updatedEntity
        }
        
        return currentState.copy(
            entities = newEntities,
            currentTime = currentState.currentTime + 1
        )
    }
}

/**
 * Start a new game and return the engine
 */
fun startGame(): GameEngine = GameEngine()