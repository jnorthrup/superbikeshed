package rtsgame.systems

import rtsgame.core.*
import rtsgame.components.*
import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

/**
 * CCEK-Integrated RTS Systems with Wave/CRDT Synergy
 * 
 * Demonstrates how to use existing context key APIs to shrink code and integrate:
 * - RTS game systems with Wave operational transformation
 * - CRDT for distributed state management
 * - CCEK for execution orchestration
 */

// === CCEK SYSTEM CONTEXTS ===

/**
 * Movement system context - provides movement operations with Wave sync
 */
data class MovementSystemContext(
    val systemId: String = "movement",
    val batchSize: Int = 64,
    val syncInterval: Long = 16 // 60 FPS
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<MovementSystemContext>
    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Combat system context - provides combat operations with CRDT sync
 */
data class CombatSystemContext(
    val systemId: String = "combat",
    val damageCalculation: DamageCalculationStrategy = DamageCalculationStrategy.STANDARD,
    val syncMode: CombatSyncMode = CombatSyncMode.REALTIME
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CombatSystemContext>
    override val key: CoroutineContext.Key<*> = Key
    
    enum class DamageCalculationStrategy {
        STANDARD, SIMPLIFIED, REALISTIC
    }
    
    enum class CombatSyncMode {
        REALTIME, BATCHED, EVENTUAL
    }
}

// === CCEK-INTEGRATED SYSTEMS ===

/**
 * CCEK-integrated movement system with Wave operational transformation
 */
class CCEKMovementSystem(
    internal val world: CCEKECSWorld
) {
    
    /**
     * Update movement with CCEK context and Wave sync
     */
    suspend fun updateMovement(deltaTime: Float) = withContext(createMovementContext()) {
        // Query all entities with position and velocity components
        val entities = world.query(ComponentTypes.POSITION, ComponentTypes.VELOCITY)
        
        // Process in batches for performance
        world.forEachBatch(ComponentTypes.POSITION, 64) { entityBatch, positionBatch ->
            // Apply movement updates
            for (i in 0 until entityBatch.component1()) {
                val entityId = entityBatch.component2()(i)
                val position = positionBatch.component2()(i)
                val velocity = world.getComponent<VelocityComponent>(entityId, ComponentTypes.VELOCITY)
                
                velocity?.let { vel ->
                    // Update position
                    val newPosition = position.copy(
                        x = position.x + vel.vx * deltaTime,
                        y = position.y + vel.vy * deltaTime
                    )
                    
                    // Apply update with Wave sync
                    world.updateComponent(entityId, ComponentTypes.POSITION) { newPosition }
                }
            }
        }
    }
    
    /**
     * Move entity to target with pathfinding and Wave sync
     */
    suspend fun moveToTarget(
        entityId: EntityId, 
        target: PositionComponent,
        formation: FormationType? = null
    ) = withContext(createMovementContext()) {
        // Add pathfinding component
        val pathfinding = PathfindingComponent(
            path = mutableListOf(target),
            currentWaypoint = 0,
            pathfindingPriority = 1
        )
        
        world.addComponent(entityId, pathfinding)
        
        // Add command to queue
        val command = MoveCommand(target, formation)
        val commandQueue = world.getComponent<CommandQueueComponent>(entityId, ComponentTypes.COMMAND_QUEUE)
        
        commandQueue?.let { queue ->
            val updatedQueue = queue.copy(
                commands = (queue.commands + command).toMutableList()
            )
            world.updateComponent(entityId, ComponentTypes.COMMAND_QUEUE) { updatedQueue }
        }
    }
    
    internal fun createMovementContext(): MovementSystemContext = 
        MovementSystemContext()
}

/**
 * CCEK-integrated combat system with CRDT state management
 */
class CCEKCombatSystem(
    internal val world: CCEKECSWorld
) {
    
    /**
     * Process combat with CCEK context and CRDT sync
     */
    suspend fun processCombat(deltaTime: Float) = withContext(createCombatContext()) {
        // Query all entities with weapon and health components
        val entities = world.query(ComponentTypes.WEAPON, ComponentTypes.HEALTH)
        
        world.forEachBatch(ComponentTypes.WEAPON, 32) { entityBatch, weaponBatch ->
            for (i in 0 until entityBatch.component1()) {
                val entityId = entityBatch.component2()(i)
                val weapon = weaponBatch.component2()(i)
                val ai = world.getComponent<UnitAIComponent>(entityId, ComponentTypes.UNIT_AI)
                
                ai?.targetEntity?.let { targetId ->
                    processAttack(entityId, targetId, weapon)
                }
            }
        }
    }
    
    /**
     * Process attack with CRDT state updates
     */
    internal suspend fun processAttack(
        attackerId: EntityId,
        targetId: EntityId,
        weapon: WeaponComponent
    ) = withContext(createCombatContext()) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        if (!weapon.canAttack(currentTime)) return@withContext
        
        val targetHealth = world.getComponent<HealthComponent>(targetId, ComponentTypes.HEALTH)
        val attackerPosition = world.getComponent<PositionComponent>(attackerId, ComponentTypes.POSITION)
        val targetPosition = world.getComponent<PositionComponent>(targetId, ComponentTypes.POSITION)
        
        if (targetHealth == null || attackerPosition == null || targetPosition == null) {
            return@withContext
        }
        
        // Check range
        val distance = calculateDistance(attackerPosition, targetPosition)
        if (distance > weapon.range) return@withContext
        
        // Calculate damage
        val damage = calculateDamage(weapon, targetHealth)
        
        // Apply damage with CRDT sync
        val newHealth = targetHealth.copy(
            health = maxOf(0f, targetHealth.health - damage)
        )
        
        world.updateComponent(targetId, ComponentTypes.HEALTH) { newHealth }
        
        // Update weapon cooldown
        val updatedWeapon = weapon.copy(lastAttackTime = currentTime)
        world.updateComponent(attackerId, ComponentTypes.WEAPON) { updatedWeapon }
        
        // Update AI state
        val ai = world.getComponent<UnitAIComponent>(attackerId, ComponentTypes.UNIT_AI)
        ai?.let { aiComponent ->
            val updatedAI = aiComponent.copy(
                state = if (newHealth.isDead) AIState.IDLE else AIState.ATTACKING,
                lastDecisionTime = currentTime
            )
            world.updateComponent(attackerId, ComponentTypes.UNIT_AI) { updatedAI }
        }
    }
    
    internal fun calculateDistance(pos1: PositionComponent, pos2: PositionComponent): Float {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    internal fun calculateDamage(weapon: WeaponComponent, targetHealth: HealthComponent): Float {
        val baseDamage = weapon.damage
        val armorReduction = targetHealth.armor * 0.5f // Simplified armor calculation
        return maxOf(1f, baseDamage - armorReduction)
    }
    
    internal fun createCombatContext(): CombatSystemContext = 
        CombatSystemContext()
}

/**
 * CCEK-integrated AI system with Wave operational transformation
 */
class CCEKAISystem(
    internal val world: CCEKECSWorld
) {
    
    /**
     * Update AI with CCEK context and Wave sync
     */
    suspend fun updateAI(deltaTime: Float) = withContext(createAIContext()) {
        val entities = world.query(ComponentTypes.UNIT_AI)
        
        world.forEachBatch(ComponentTypes.UNIT_AI, 32) { entityBatch, aiBatch ->
            for (i in 0 until entityBatch.component1()) {
                val entityId = entityBatch.component2()(i)
                val ai = aiBatch.component2()(i)
                
                updateEntityAI(entityId, ai, deltaTime)
            }
        }
    }
    
    internal suspend fun updateEntityAI(
        entityId: EntityId,
        ai: UnitAIComponent,
        deltaTime: Float
    ) = withContext(createAIContext()) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        // Only update AI periodically
        if (currentTime - ai.lastDecisionTime < 100) return@withContext
        
        val commandQueue = world.getComponent<CommandQueueComponent>(entityId, ComponentTypes.COMMAND_QUEUE)
        val position = world.getComponent<PositionComponent>(entityId, ComponentTypes.POSITION)
        
        when (ai.state) {
            AIState.IDLE -> {
                // Check for new commands
                val nextCommand = commandQueue?.nextCommand()
                if (nextCommand != null) {
                    processCommand(entityId, nextCommand)
                }
            }
            AIState.MOVING -> {
                // Check if movement is complete
                val pathfinding = world.getComponent<PathfindingComponent>(entityId, ComponentTypes.PATHFINDING)
                if (pathfinding?.isPathComplete() == true) {
                    val updatedAI = ai.copy(state = AIState.IDLE)
                    world.updateComponent(entityId, ComponentTypes.UNIT_AI) { updatedAI }
                }
            }
            AIState.ATTACKING -> {
                // Check if target is still valid
                ai.targetEntity?.let { targetId ->
                    val targetHealth = world.getComponent<HealthComponent>(targetId, ComponentTypes.HEALTH)
                    if (targetHealth?.isDead == true) {
                        val updatedAI = ai.copy(
                            state = AIState.IDLE,
                            targetEntity = null
                        )
                        world.updateComponent(entityId, ComponentTypes.UNIT_AI) { updatedAI }
                    }
                }
            }
            else -> {
                // Other states handled by specific systems
            }
        }
        
        // Update last decision time
        val updatedAI = ai.copy(lastDecisionTime = currentTime)
        world.updateComponent(entityId, ComponentTypes.UNIT_AI) { updatedAI }
    }
    
    internal suspend fun processCommand(entityId: EntityId, command: Command) = 
        withContext(createAIContext()) {
            when (command) {
                is MoveCommand -> {
                    val updatedAI = UnitAIComponent(state = AIState.MOVING)
                    world.updateComponent(entityId, ComponentTypes.UNIT_AI) { updatedAI }
                }
                is AttackCommand -> {
                    val updatedAI = UnitAIComponent(
                        state = AIState.ATTACKING,
                        targetEntity = command.target
                    )
                    world.updateComponent(entityId, ComponentTypes.UNIT_AI) { updatedAI }
                }
                else -> {
                    // Handle other command types
                }
            }
        }
    
    internal fun createAIContext(): CoroutineContext = 
        MovementSystemContext() + CombatSystemContext()
}

/**
 * CCEK-integrated resource system with CRDT state management
 */
class CCEKResourceSystem(
    internal val world: CCEKECSWorld
) {
    
    /**
     * Update resource gathering with CCEK context
     */
    suspend fun updateResourceGathering(deltaTime: Float) = withContext(createResourceContext()) {
        val entities = world.query(ComponentTypes.RESOURCE_GATHERER)
        
        world.forEachBatch(ComponentTypes.RESOURCE_GATHERER, 16) { entityBatch, gathererBatch ->
            for (i in 0 until entityBatch.component1()) {
                val entityId = entityBatch.component2()(i)
                val gatherer = gathererBatch.component2()(i)
                
                updateGatherer(entityId, gatherer, deltaTime)
            }
        }
    }
    
    internal suspend fun updateGatherer(
        entityId: EntityId,
        gatherer: ResourceGathererComponent,
        deltaTime: Float
    ) = withContext(createResourceContext()) {
        if (gatherer.isFull) return@withContext
        
        gatherer.targetResource?.let { resourceId ->
            val resource = world.getComponent<ResourceComponent>(resourceId, ComponentTypes.RESOURCE)
            
            if (resource != null && resource.amount > 0) {
                val gatherAmount = minOf(
                    gatherer.gatherRate * deltaTime,
                    resource.amount,
                    gatherer.capacity - gatherer.currentLoad
                )
                
                // Update gatherer
                val updatedGatherer = gatherer.copy(
                    currentLoad = gatherer.currentLoad + gatherAmount
                )
                world.updateComponent(entityId, ComponentTypes.RESOURCE_GATHERER) { updatedGatherer }
                
                // Update resource
                val updatedResource = resource.copy(
                    amount = maxOf(0f, resource.amount - gatherAmount)
                )
                world.updateComponent(resourceId, ComponentTypes.RESOURCE) { updatedResource }
            }
        }
    }
    
    internal fun createResourceContext(): CoroutineContext = 
        MovementSystemContext()
}

// === PLACEHOLDER COMPONENT FOR RESOURCES ===

/**
 * Resource component for resource nodes
 */
data class ResourceComponent(
    var amount: Float,
    var maxAmount: Float,
    var resourceType: ResourceType,
    var regenerationRate: Float = 0f
) : Component {
    override val typeId = ComponentTypes.RESOURCE
}

// === CCEK SYSTEM ORCHESTRATOR ===

/**
 * CCEK system orchestrator - coordinates all systems with Wave/CRDT integration
 */
class CCEKSystemOrchestrator(
    internal val world: CCEKECSWorld,
    internal val movementSystem: CCEKMovementSystem = CCEKMovementSystem(world),
    internal val combatSystem: CCEKCombatSystem = CCEKCombatSystem(world),
    internal val aiSystem: CCEKAISystem = CCEKAISystem(world),
    internal val resourceSystem: CCEKResourceSystem = CCEKResourceSystem(world)
) {
    
    /**
     * Update all systems with CCEK orchestration
     */
    suspend fun update(deltaTime: Float) = withContext(createOrchestratorContext()) {
        // Run systems in parallel with CCEK context
        coroutineScope {
            launch { movementSystem.updateMovement(deltaTime) }
            launch { combatSystem.processCombat(deltaTime) }
            launch { aiSystem.updateAI(deltaTime) }
            launch { resourceSystem.updateResourceGathering(deltaTime) }
        }
    }
    
    /**
     * Create unit with CCEK context and Wave sync
     */
    suspend fun createUnit(
        unitType: UnitType,
        position: PositionComponent,
        teamId: Int
    ) = withContext(createOrchestratorContext()) {
        val entityId = world.createEntity()
        
        // Add basic components
        world.addComponent(entityId, position)
        world.addComponent(entityId, TeamComponent(teamId))
        world.addComponent(entityId, UnitAIComponent())
        world.addComponent(entityId, CommandQueueComponent())
        
        // Add unit-specific components
        when (unitType) {
            UnitType.WORKER -> {
                world.addComponent(entityId, ResourceGathererComponent(
                    gatherRate = 10f,
                    capacity = 100f
                ))
            }
            UnitType.SOLDIER -> {
                world.addComponent(entityId, WeaponComponent(
                    damage = 25f,
                    attackSpeed = 1.5f,
                    range = 150f
                ))
                world.addComponent(entityId, HealthComponent(
                    health = 100f,
                    maxHealth = 100f
                ))
            }
            UnitType.BUILDER -> {
                world.addComponent(entityId, ConstructionComponent(
                    buildPower = 50f
                ))
            }
        }
        
        entityId
    }
    
    internal fun createOrchestratorContext(): CoroutineContext = 
        MovementSystemContext() + CombatSystemContext() + RTSGameContext(
            world = world.world,
            sessionId = "orchestrator"
        )
}

// === UNIT TYPE ENUM ===

enum class UnitType {
    WORKER, SOLDIER, BUILDER, SCOUT, HEAVY
} 