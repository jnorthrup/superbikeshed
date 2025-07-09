package rtsgame.core

import rtsgame.components.*
import rtsgame.systems.*
import rtsgame.ai.*

/**
 * Next-generation simulation engine using ECS architecture
 */
class NextGenSimulation {
    val world = ECSWorld()
    val systemManager = SystemManager()
    
    var currentTick: Long = 0
    var updateTime: Long = 0
    var isRunning: Boolean = false
    
    init {
        initializeSystems()
    }
    
    private fun initializeSystems() {
        // Add core systems
        systemManager.addSystem(MovementSystem())
        systemManager.addSystem(PhysicsSystem())
        systemManager.addSystem(SteeringSystem())
        systemManager.addSystem(FormationSystem())
        systemManager.addSystem(FlowFieldPathfindingSystem())
        
        // Add combat systems
        systemManager.addSystem(AdvancedCombatSystem())
        
        // Add economic systems
        systemManager.addSystem(ResourceSystem())
        systemManager.addSystem(ConstructionSystem())
        systemManager.addSystem(ProductionSystem())
        
        // Add AI systems
        systemManager.addSystem(StrategicAISystem())
        systemManager.addSystem(TacticalAISystem())
        systemManager.addSystem(SwarmAI())
        systemManager.addSystem(NeuralNetworkAI())
    }
    
    fun update(deltaTime: Float) {
        val startTime = TimeUtils.nanoTime()
        
        // Update all systems
        systemManager.update(world, deltaTime)
        
        // Update simulation state
        currentTick++
        updateTime = TimeUtils.nanoTime() - startTime
    }
    
    fun createUnit(type: String, teamId: Int, x: Float, y: Float): EntityId {
        val entityId = world.createEntity()
        
        // Add basic components
        world.addComponent(entityId, PositionComponent(x, y, 0f))
        world.addComponent(entityId, VelocityComponent(0f, 0f, 0f))
        world.addComponent(entityId, HealthComponent(100f, 100f))
        world.addComponent(entityId, OwnerComponent(teamId))
        world.addComponent(entityId, EntityTypeComponent(type, type, "unit"))
        
        // Add type-specific components
        when (type) {
            "commander" -> {
                world.addComponent(entityId, WeaponComponent(25f, 150f, 1f))
                world.addComponent(entityId, ShieldComponent(50f, 50f, 2f))
                world.addComponent(entityId, ComputroniumComponent(10f, 100f, 5f))
            }
            "tank" -> {
                world.addComponent(entityId, WeaponComponent(50f, 120f, 2f))
                world.addComponent(entityId, ShieldComponent(25f, 25f, 1f))
            }
            "scout" -> {
                world.addComponent(entityId, WeaponComponent(15f, 80f, 0.5f))
            }
        }
        
        return entityId
    }
    
    fun createBuilding(type: String, teamId: Int, x: Float, y: Float): EntityId {
        val entityId = world.createEntity()
        
        // Add basic components
        world.addComponent(entityId, PositionComponent(x, y, 0f))
        world.addComponent(entityId, HealthComponent(200f, 200f))
        world.addComponent(entityId, OwnerComponent(teamId))
        world.addComponent(entityId, EntityTypeComponent(type, type, "building"))
        
        // Add type-specific components
        when (type) {
            "massExtractor" -> {
                world.addComponent(entityId, ResourceComponent("mass", 0f, 1000f, 10f))
            }
            "energyExtractor" -> {
                world.addComponent(entityId, ResourceComponent("energy", 0f, 1000f, 15f))
            }
            "factory" -> {
                world.addComponent(entityId, ComputroniumComponent(5f, 50f, 2f))
            }
        }
        
        return entityId
    }
    
    fun issueCommand(entityIds: List<EntityId>, command: CommandComponent) {
        entityIds.forEach { entityId ->
            if (world.hasEntity(entityId)) {
                world.addComponent(entityId, command)
            }
        }
    }
    
    fun getEntityCount(): Int = world.getAllEntities().size
    
    fun start() {
        isRunning = true
    }
    
    fun stop() {
        isRunning = false
    }
    
    fun reset() {
        world.clear()
        currentTick = 0
        updateTime = 0
    }
} 