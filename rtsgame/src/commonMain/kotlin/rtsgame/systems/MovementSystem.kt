package rtsgame.systems

import rtsgame.core.*
import rtsgame.components.*

/**
 * Movement system for updating entity positions based on velocity
 */
class MovementSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        val entitiesWithMovement = world.getEntitiesWithComponents(
            ComponentTypeId.POSITION,
            ComponentTypeId.VELOCITY
        )
        
        entitiesWithMovement.forEach { entityId ->
            val position = world.getComponent<PositionComponent>(entityId, ComponentTypeId.POSITION)
            val velocity = world.getComponent<VelocityComponent>(entityId, ComponentTypeId.VELOCITY)
            
            if (position != null && velocity != null) {
                val newPosition = PositionComponent(
                    x = position.x + velocity.vx * deltaTime,
                    y = position.y + velocity.vy * deltaTime,
                    z = position.z + velocity.vz * deltaTime
                )
                
                world.addComponent(entityId, newPosition)
            }
        }
    }
}

/**
 * Physics system for applying forces and constraints
 */
class PhysicsSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Apply gravity, friction, and other physics effects
        val entitiesWithPhysics = world.getEntitiesWithComponents(
            ComponentTypeId.POSITION,
            ComponentTypeId.VELOCITY
        )
        
        entitiesWithPhysics.forEach { entityId ->
            val velocity = world.getComponent<VelocityComponent>(entityId, ComponentTypeId.VELOCITY)
            
            if (velocity != null) {
                // Apply friction
                val friction = 0.95f
                val newVelocity = VelocityComponent(
                    vx = velocity.vx * friction,
                    vy = velocity.vy * friction,
                    vz = velocity.vz * friction
                )
                
                world.addComponent(entityId, newVelocity)
            }
        }
    }
}

/**
 * Steering system for AI-controlled movement
 */
class SteeringSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        val entitiesWithSteering = world.getEntitiesWithComponents(
            ComponentTypeId.POSITION,
            ComponentTypeId.VELOCITY,
            ComponentTypeId.COMMAND
        )
        
        entitiesWithSteering.forEach { entityId ->
            val position = world.getComponent<PositionComponent>(entityId, ComponentTypeId.POSITION)
            val velocity = world.getComponent<VelocityComponent>(entityId, ComponentTypeId.VELOCITY)
            val command = world.getComponent<CommandComponent>(entityId, ComponentTypeId.COMMAND)
            
            if (position != null && velocity != null && command != null) {
                when (command.commandType) {
                    "move" -> {
                        if (command.targetX != null && command.targetY != null) {
                            val targetPos = PositionComponent(command.targetX, command.targetY, 0f)
                            val direction = position.directionTo(targetPos)
                            val distance = position.distanceTo(targetPos)
                            
                            if (distance > 1f) {
                                val speed = 5f // Base movement speed
                                val newVelocity = VelocityComponent(
                                    vx = direction.x * speed,
                                    vy = direction.y * speed,
                                    vz = 0f
                                )
                                world.addComponent(entityId, newVelocity)
                            } else {
                                // Stop when close to target
                                world.addComponent(entityId, VelocityComponent(0f, 0f, 0f))
                            }
                        }
                    }
                    "stop" -> {
                        world.addComponent(entityId, VelocityComponent(0f, 0f, 0f))
                    }
                }
            }
        }
    }
}

/**
 * Formation system for group movement
 */
class FormationSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // TODO: Implement formation-based movement
        // This would coordinate multiple units to move in formations
    }
}

/**
 * Flow field pathfinding system
 */
class FlowFieldPathfindingSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // TODO: Implement flow field pathfinding
        // This would provide efficient pathfinding for large numbers of units
    }
} 