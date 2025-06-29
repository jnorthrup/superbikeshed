package rtsgame.core

import rtsgame.config.UNIT_TYPES
import rtsgame.config.BUILDING_TYPES
import rtsgame.config.UnitType
import rtsgame.config.BuildingType
import rtsgame.entities.GameUnit
import rtsgame.entities.Building
// import rtsgame.entities.Projectile // Assuming Projectile class will be defined
// import rtsgame.entities.Effect // Assuming Effect class will be defined
// import rtsgame.entities.Caption // Assuming Caption class will be defined

/**
 * EntityFactory - Responsible for creating game entities.
 * Direct translation of js/core/entityFactory.js patterns.
 * NO IMPROVEMENTS - preserving exact JS behavior where possible.
 */
class EntityFactory(private val simulation: Simulation) {
    private var nextIdOffset = 0 // Per-factory offset to ensure unique IDs if multiple factories exist, though typically one per simulation.

    // Public method to get next unique ID, managed by EntityManager in Simulation
    private fun getNextEntityId(): Int {
        return simulation.entityManager.nextEntityId++
    }

    fun createUnit(
        unitTypeName: String,
        team: String,
        x: Double,
        y: Double
        // Options like specific hp, maxHp, speed, etc. are now primarily driven by UnitType config.
        // If direct overrides are needed, they can be applied after creation or via a more complex constructor.
    ): GameUnit? {
        val unitTypeData = UNIT_TYPES.byName(unitTypeName)
            ?: run {
                println("Error: Unknown unit type '$unitTypeName' in EntityFactory.createUnit")
                return null
            }

        val unitId = getNextEntityId()
        val unit = GameUnit(
            id = unitId,
            unitTypeName = unitTypeName, // Pass the name, GameUnit constructor will fetch UnitType
            team = team,
            x = x,
            y = y,
            simulation = simulation // Pass the simulation instance
        )
        // Most properties are now set within GameUnit's constructor based on its UnitType.
        // If specific overrides were passed in JS options, they would be applied here:
        // e.g., if (options.hp != null) unit.hp = options.hp
        return unit
    }

    fun createBuilding(
        buildingTypeName: String,
        team: String,
        x: Double,
        y: Double
    ): Building? {
        val buildingTypeData = BUILDING_TYPES.byName(buildingTypeName)
            ?: run {
                println("Error: Unknown building type '$buildingTypeName' in EntityFactory.createBuilding")
                return null
            }

        val buildingId = getNextEntityId()
        val building = Building(
            id = buildingId,
            buildingTypeName = buildingTypeName, // Pass the name
            team = team,
            x = x,
            y = y,
            simulationInstance = simulation // Pass the simulation instance
        )
        // Properties are set within Building's constructor from BuildingType.
        return building
    }

    fun createResourceNode(
        resourceType: String, // e.g., "mass", "energy"
        x: Double,
        y: Double,
        amount: Int = 1000
    ): ResourceNode { // Assuming ResourceNode is the data class in Simulation.kt
        // Resource nodes might not need an ID from the main sequence if they are static
        // or managed differently. If they are dynamic entities, use getNextEntityId().
        return ResourceNode(
            x = x,
            y = y,
            type = resourceType,
            amount = amount,
            maxAmount = amount,
            occupied = false
        )
    }

    // TODO: Port Projectile, Effect, Caption classes first
    /*
    fun createProjectile(
        sourceUnit: GameUnit,
        targetEntity: Any, // GameUnit or Building
        projectileTypeName: String // If projectiles have types
    ): Projectile? {
        val projectileId = getNextEntityId()
        // val projectileType = PROJECTILE_TYPES.byName(projectileTypeName) ?: return null
        // return Projectile(projectileId, sourceUnit, targetEntity, projectileType, simulation)
        return null // Placeholder
    }

    fun createEffect(
        effectTypeName: String,
        x: Double,
        y: Double
    ): Effect? {
        val effectId = getNextEntityId()
        // val effectType = EFFECT_TYPES.byName(effectTypeName) ?: return null
        // return Effect(effectId, x, y, effectType, simulation)
        return null // Placeholder
    }

    fun createCaption(
        text: String,
        x: Double,
        y: Double,
        color: String = "#FFFFFF",
        durationSeconds: Double = 2.0
    ): Caption? {
        val captionId = getNextEntityId()
        // return Caption(captionId, text, x, y, color, durationSeconds, simulation)
        return null // Placeholder
    }
    */
}
