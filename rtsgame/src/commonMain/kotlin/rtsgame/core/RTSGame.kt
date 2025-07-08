import kotlin.math.*
package rtsgame.core
import kotlinx.datetime.*
import kotlin.time.*

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

// CoreEntity: Concrete implementation of the canonical Entity interface
data class CoreEntity(
    override val id: String, // From Entity interface
    override var position: Position, // From Entity interface, using canonical Position
    override var health: Float, // From Entity interface
    override val maxHealth: Float, // From Entity interface
    override val speed: Float, // From Entity interface
    override val team: Int, // From Entity interface

    // Potentially other CoreEntity-specific properties can be added here
    val name: String = "Unit"
) : Entity {
    // Helper constructor using local wrapper types if needed for transition
    constructor(
        localId: LocalEntityId,
        pos: Position,
        localHealth: LocalHealth,
        maxHp: Float,
        spd: Float,
        localPlayerId: LocalPlayerId,
        entityName: String = "Unit"
    ) : this(
        id = localId.value,
        position = pos,
        health = localHealth.value,
        maxHealth = maxHp,
        speed = spd,
        team = localPlayerId.value,
        name = entityName
    )
}

/**
 * Game engine for running RTS simulation
 */
class GameEngine {
    fun tick(): GameState {
        // Simple static game state for demo, using canonical types
        val entitiesMap = mutableMapOf<String, Entity>()

        val entity1 = CoreEntity(
            id = "unit_1",
            position = Position(100f, 100f), // Canonical Position
            health = 100f,
            maxHealth = 100f,
            speed = 5f,
            team = 1,
            name = "Alpha"
        )
        entitiesMap[entity1.id] = entity1

        val entity2 = CoreEntity(
            localId = LocalEntityId("unit_2"), // Example using helper constructor
            pos = Position(200f, 150f),
            localHealth = LocalHealth(75f),
            maxHp = 100f,
            spd = 5f,
            localPlayerId = LocalPlayerId(2),
            entityName = "Beta"
        )
        entitiesMap[entity2.id] = entity2
        
        // Resources map for GameState (PlayerID -> ResourceType -> Amount)
        // Example: Player 1 has 1000 of each basic resource.
        val player1Resources = mapOf(
            ResourceType.GOLD to 1000,
            ResourceType.WOOD to 1000,
            ResourceType.FOOD to 1000
            // Add other resources as defined in canonical ResourceType
        )
        val resources = mapOf(1 to player1Resources)

        return GameState(
            entities = entitiesMap,
            resources = resources, // Added resources to GameState
            currentTime = Platform.getCurrentTime() / 1000 // Assuming currentTime is a Long timestamp
        )
    }
    
    fun simulateTick(currentState: GameState): GameState {
        val newEntities = mutableMapOf<String, Entity>()
        currentState.entities.values.forEach { entity ->
            // Ensure we are working with CoreEntity if we need specific fields not on Entity interface
            // For now, position is on Entity interface.
            // val coreEntity = entity as? CoreEntity ?: entity // Keep as Entity if no specific fields needed

            // Simple movement simulation using canonical Position
            val newX = entity.position.x + (kotlin.random.Random.nextFloat() - 0.5f) * (entity.speed)
            val newY = entity.position.y + (kotlin.random.Random.nextFloat() - 0.5f) * (entity.speed)
            
            // Update position: Entity interface's position is a val, so we need a new instance or make it a var.
            // For CoreEntity, position is a var.
            if (entity is CoreEntity) { // Check if it's our concrete type to modify
                 val movedEntity = entity.copy(position = Position(newX, newY))
                 newEntities[movedEntity.id] = movedEntity
            } else {
                // If it's not a CoreEntity, we can't easily change its position unless Entity interface's position is var
                // Or we'd need specific logic for other Entity implementers.
                // For now, just copy non-CoreEntity types.
                newEntities[entity.id] = entity
            }
        }
        
        return currentState.copy(
            entities = newEntities,
            currentTime = currentState.currentTime + 1 // Increment game time (tick)
        )
    }
}

/**
 * Start a new game and return the engine
 */
fun startGame(): GameEngine = GameEngine()