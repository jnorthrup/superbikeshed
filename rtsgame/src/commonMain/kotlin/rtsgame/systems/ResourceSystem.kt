package rtsgame.systems

import rtsgame.core.*
import rtsgame.components.*

/**
 * Resource system for managing economic entities
 */
class ResourceSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Update resource generation
        updateResourceGeneration(world, deltaTime)
        
        // Update computronium generation
        updateComputroniumGeneration(world, deltaTime)
        
        // Process resource extraction commands
        processResourceExtraction(world, deltaTime)
    }
    
    private fun updateResourceGeneration(world: ECSWorld, deltaTime: Float) {
        val entitiesWithResources = world.getEntitiesWithComponents(ComponentTypeId.RESOURCE)
        
        entitiesWithResources.forEach { entityId ->
            val resource = world.getComponent<ResourceComponent>(entityId, ComponentTypeId.RESOURCE)
            if (resource != null && resource.generationRate > 0f) {
                val updatedResource = resource.generate(deltaTime)
                world.addComponent(entityId, updatedResource)
            }
        }
    }
    
    private fun updateComputroniumGeneration(world: ECSWorld, deltaTime: Float) {
        val entitiesWithComputronium = world.getEntitiesWithComponents(ComponentTypeId.COMPUTRONIUM)
        
        entitiesWithComputronium.forEach { entityId ->
            val computronium = world.getComponent<ComputroniumComponent>(entityId, ComponentTypeId.COMPUTRONIUM)
            if (computronium != null && computronium.generationRate > 0f) {
                val updatedComputronium = computronium.generate(deltaTime)
                world.addComponent(entityId, updatedComputronium)
            }
        }
    }
    
    private fun processResourceExtraction(world: ECSWorld, deltaTime: Float) {
        val entitiesWithExtraction = world.getEntitiesWithComponents(
            ComponentTypeId.POSITION,
            ComponentTypeId.COMMAND
        )
        
        entitiesWithExtraction.forEach { entityId ->
            val position = world.getComponent<PositionComponent>(entityId, ComponentTypeId.POSITION)
            val command = world.getComponent<CommandComponent>(entityId, ComponentTypeId.COMMAND)
            
            if (position != null && command != null) {
                when (command.commandType) {
                    "extract" -> {
                        if (command.targetId != null) {
                            val targetEntityId = EntityId(command.targetId)
                            if (world.hasEntity(targetEntityId)) {
                                val targetPosition = world.getComponent<PositionComponent>(targetEntityId, ComponentTypeId.POSITION)
                                val targetResource = world.getComponent<ResourceComponent>(targetEntityId, ComponentTypeId.RESOURCE)
                                
                                if (targetPosition != null && targetResource != null) {
                                    val distance = position.distanceTo(targetPosition)
                                    val extractionRange = 10f // Extraction range
                                    
                                    if (distance <= extractionRange) {
                                        val extractionRate = 5f * deltaTime // Units per second
                                        if (targetResource.canExtract(extractionRate)) {
                                            val extractedResource = targetResource.extract(extractionRate)
                                            world.addComponent(targetEntityId, extractedResource)
                                            
                                            // TODO: Add extracted resources to player's resource pool
                                            // This would require a player/team resource management system
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Construction system for building management
 */
class ConstructionSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // TODO: Implement building construction logic
        // This would handle building progress, resource costs, and completion
    }
}

/**
 * Production system for unit/building production
 */
class ProductionSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // TODO: Implement unit/building production logic
        // This would handle production queues, build times, and resource costs
    }
} 