import kotlin.math.*
package rtsgame.components
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import kotlin.jvm.JvmInline

/**
 * High-performance RTS component definitions
 * Optimized for cache locality and SIMD operations
 */

object ComponentTypes {
    val POSITION = ComponentTypeId(0)
    val VELOCITY = ComponentTypeId(1)
    val HEALTH = ComponentTypeId(2)
    val TEAM = ComponentTypeId(3)
    val UNIT_AI = ComponentTypeId(4)
    val WEAPON = ComponentTypeId(5)
    val BUILDING = ComponentTypeId(6)
    val RESOURCE_GATHERER = ComponentTypeId(7)
    val CONSTRUCTION = ComponentTypeId(8)
    val VISION = ComponentTypeId(9)
    val PATHFINDING = ComponentTypeId(10)
    val FORMATION = ComponentTypeId(11)
    val COMMAND_QUEUE = ComponentTypeId(12)
    val PHYSICS = ComponentTypeId(13)
    val NETWORK_SYNC = ComponentTypeId(14)
}

/**
 * Position component - hot path, optimized for batch processing
 */
data class PositionComponent(
    var x: Float,
    var y: Float,
    var rotation: Float = 0f
) : Component {
    override val typeId = ComponentTypes.POSITION
}

/**
 * Velocity component for movement
 */
data class VelocityComponent(
    var vx: Float,
    var vy: Float,
    var maxSpeed: Float
) : Component {
    override val typeId = ComponentTypes.VELOCITY
}

/**
 * Health component with shield support
 */
data class HealthComponent(
    var health: Float,
    var maxHealth: Float,
    var shield: Float = 0f,
    var maxShield: Float = 0f,
    var armor: Float = 0f,
    var regeneration: Float = 0f
) : Component {
    override val typeId = ComponentTypes.HEALTH
    
    val isDead: Boolean get() = health <= 0f
    val healthPercent: Float get() = health / maxHealth
    val shieldPercent: Float get() = if (maxShield > 0) shield / maxShield else 0f
}

/**
 * Team/ownership component
 */
value class TeamComponent(val teamId: Int) : Component {
    override val typeId get() = ComponentTypes.TEAM
}

/**
 * AI state for units
 */
data class UnitAIComponent(
    var state: AIState = AIState.IDLE,
    var targetEntity: EntityId? = null,
    var targetPosition: PositionComponent? = null,
    var aggroRange: Float = 150f,
    var attackRange: Float = 100f,
    var lastDecisionTime: Long = 0
) : Component {
    override val typeId = ComponentTypes.UNIT_AI
}

enum class AIState {
    IDLE, MOVING, ATTACKING, FLEEING, GATHERING, BUILDING, PATROLLING
}

/**
 * Weapon component for combat units
 */
data class WeaponComponent(
    var damage: Float,
    var attackSpeed: Float,
    var range: Float,
    var projectileSpeed: Float = 0f,
    var lastAttackTime: Long = 0,
    var damageType: DamageType = DamageType.KINETIC,
    var areaOfEffect: Float = 0f
) : Component {
    override val typeId = ComponentTypes.WEAPON
    
    fun canAttack(currentTime: Long): Boolean {
        return currentTime - lastAttackTime >= (1000f / attackSpeed).toLong()
    }
}

enum class DamageType {
    KINETIC, ENERGY, EXPLOSIVE, PLASMA, EMP
}

/**
 * Building component
 */
data class BuildingComponent(
    var buildingType: BuildingType,
    var constructionProgress: Float = 0f,
    var powerConsumption: Float = 0f,
    var isOperational: Boolean = false
) : Component {
    override val typeId = ComponentTypes.BUILDING
    
    val isConstructed: Boolean get() = constructionProgress >= 100f
}

enum class BuildingType {
    COMMAND_CENTER, BARRACKS, FACTORY, POWER_PLANT, REFINERY, TURRET, WALL, RESEARCH_LAB
}

/**
 * Resource gathering component
 */
data class ResourceGathererComponent(
    var gatherRate: Float,
    var capacity: Float,
    var currentLoad: Float = 0f,
    var targetResource: EntityId? = null,
    var resourceType: ResourceType = ResourceType.MINERALS
) : Component {
    override val typeId = ComponentTypes.RESOURCE_GATHERER
    
    val isFull: Boolean get() = currentLoad >= capacity
}

enum class ResourceType {
    MINERALS, GAS, ENERGY, RARE_MINERALS
}

/**
 * Construction component for builders
 */
data class ConstructionComponent(
    var buildPower: Float,
    var currentTarget: EntityId? = null,
    var buildQueue: MutableList<BuildOrder> = mutableListOf()
) : Component {
    override val typeId = ComponentTypes.CONSTRUCTION
}

data class BuildOrder(
    val buildingType: BuildingType,
    val position: PositionComponent,
    val cost: ResourceCost
)

data class ResourceCost(
    val minerals: Int = 0,
    val gas: Int = 0,
    val energy: Int = 0
)

/**
 * Vision/fog of war component
 */
data class VisionComponent(
    var sightRange: Float,
    var detectionRange: Float = 0f,
    var isCloaked: Boolean = false,
    var cloakEnergyCost: Float = 0f
) : Component {
    override val typeId = ComponentTypes.VISION
}

/**
 * Pathfinding component for movement
 */
data class PathfindingComponent(
    var path: MutableList<PositionComponent> = mutableListOf(),
    var currentWaypoint: Int = 0,
    var pathfindingPriority: Int = 0,
    var avoidanceRadius: Float = 10f
) : Component {
    override val typeId = ComponentTypes.PATHFINDING
    
    fun hasPath(): Boolean = path.isNotEmpty()
    fun isPathComplete(): Boolean = currentWaypoint >= path.size
    fun getCurrentTarget(): PositionComponent? = path.getOrNull(currentWaypoint)
}

/**
 * Formation component for group movement
 */
data class FormationComponent(
    var formationType: FormationType,
    var formationPosition: Int,
    var formationLeader: EntityId? = null,
    var spacing: Float = 20f
) : Component {
    override val typeId = ComponentTypes.FORMATION
}

enum class FormationType {
    LINE, COLUMN, WEDGE, CIRCLE, SCATTER
}

/**
 * Command queue for unit orders
 */
data class CommandQueueComponent(
    val commands: MutableList<Command> = mutableListOf(),
    var currentCommand: Command? = null
) : Component {
    override val typeId = ComponentTypes.COMMAND_QUEUE
    
    fun hasCommands(): Boolean = currentCommand != null || commands.isNotEmpty()
    
    fun nextCommand(): Command? {
        if (currentCommand == null && commands.isNotEmpty()) {
            currentCommand = commands.removeAt(0)
        }
        return currentCommand
    }
}

sealed class Command
data class MoveCommand(val target: PositionComponent, val formation: FormationType? = null) : Command()
data class AttackCommand(val target: EntityId) : Command()
data class AttackMoveCommand(val target: PositionComponent) : Command()
data class GatherCommand(val resource: EntityId) : Command()
data class BuildCommand(val building: BuildOrder) : Command()
data class PatrolCommand(val waypoints: List<PositionComponent>) : Command()

/**
 * Physics component for advanced movement
 */
data class PhysicsComponent(
    var mass: Float = 1f,
    var drag: Float = 0.1f,
    var maxForce: Float = 100f,
    var acceleration: VelocityComponent = VelocityComponent(0f, 0f, 0f)
) : Component {
    override val typeId = ComponentTypes.PHYSICS
}

/**
 * Network synchronization component
 */
data class NetworkSyncComponent(
    var lastSyncTime: Long = 0,
    var syncPriority: Int = 0,
    var isDirty: Boolean = true,
    var owner: Int = -1
) : Component {
    override val typeId = ComponentTypes.NETWORK_SYNC
}