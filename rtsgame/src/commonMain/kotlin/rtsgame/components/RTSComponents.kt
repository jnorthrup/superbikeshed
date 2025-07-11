package rtsgame.components

import kotlin.math.*

/**
 * Core component types for RTS game entities
 */
object ComponentTypeId {
    const val POSITION = 1
    const val HEALTH = 2
    const val VELOCITY = 3
    const val OWNER = 4
    const val ENTITY_TYPE = 5
    const val COMMAND = 6
    const val WEAPON = 7
    const val SHIELD = 8
    const val RESOURCE = 9
    const val COMPUTRONIUM = 10
}

/**
 * Position component for entity location
 */
data class PositionComponent(
    val x: Float,
    val y: Float,
    val z: Float = 0f
) {
    
    fun distanceTo(other: PositionComponent): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    fun directionTo(other: PositionComponent): PositionComponent {
        val dx = other.x - x
        val dy = other.y - y
        val dz = other.z - z
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        return if (length > 0f) {
            PositionComponent(dx / length, dy / length, dz / length)
        } else {
            PositionComponent(0f, 0f, 0f)
        }
    }
}

/**
 * Health component for entity vitality
 */
data class HealthComponent(
    val current: Float,
    val maximum: Float,
    val lastAttacker: Int? = null
) {
    
    fun isAlive(): Boolean = currentHp > 0f
    fun healthRatio(): Float = if (maxHp > 0f) currentHp / maxHp else 0f
    
    fun takeDamage(amount: Float): HealthComponent {
        return HealthComponent(
            currentHp = (currentHp - amount).coerceAtLeast(0f),
            maxHp = maxHp,
            lastAttacker = lastAttacker
        )
    }
    
    fun heal(amount: Float): HealthComponent {
        return HealthComponent(
            currentHp = (currentHp + amount).coerceAtMost(maxHp),
            maxHp = maxHp,
            lastAttacker = lastAttacker
        )
    }
}

/**
 * Velocity component for entity movement
 */
@JvmInline
value class VelocityComponent(
    val vx: Float,
    val vy: Float,
    val vz: Float = 0f
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.VELOCITY
    
    fun speed(): Float = kotlin.math.sqrt(vx * vx + vy * vy + vz * vz)
    
    fun normalize(): VelocityComponent {
        val speed = speed()
        return if (speed > 0f) {
            VelocityComponent(vx / speed, vy / speed, vz / speed)
        } else {
            VelocityComponent(0f, 0f, 0f)
        }
    }
}

/**
 * Owner component for team/player identification
 */
@JvmInline
value class OwnerComponent(
    val teamId: Int,
    val playerId: String? = null
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.OWNER
}

/**
 * Entity type component for unit/building classification
 */
@JvmInline
value class EntityTypeComponent(
    val type: String,
    val name: String,
    val category: String
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.ENTITY_TYPE
}

/**
 * Command component for unit orders
 */
@JvmInline
value class CommandComponent(
    val commandType: String,
    val targetId: Int? = null,
    val targetX: Float? = null,
    val targetY: Float? = null,
    val priority: Int = 1
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.COMMAND
}

/**
 * Weapon component for combat capabilities
 */
@JvmInline
value class WeaponComponent(
    val damage: Float,
    val range: Float,
    val cooldown: Float,
    val currentCooldown: Float = 0f
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.WEAPON
    
    fun canFire(): Boolean = currentCooldown <= 0f
    
    fun updateCooldown(deltaTime: Float): WeaponComponent {
        return WeaponComponent(
            damage = damage,
            range = range,
            cooldown = cooldown,
            currentCooldown = (currentCooldown - deltaTime).coerceAtLeast(0f)
        )
    }
    
    fun fire(): WeaponComponent {
        return WeaponComponent(
            damage = damage,
            range = range,
            cooldown = cooldown,
            currentCooldown = cooldown
        )
    }
}

/**
 * Shield component for defensive capabilities
 */
@JvmInline
value class ShieldComponent(
    val currentShields: Float,
    val maxShields: Float,
    val regenerationRate: Float = 1f
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.SHIELD
    
    fun shieldRatio(): Float = if (maxShields > 0f) currentShields / maxShields else 0f
    
    fun takeDamage(amount: Float): ShieldComponent {
        return ShieldComponent(
            currentShields = (currentShields - amount).coerceAtLeast(0f),
            maxShields = maxShields,
            regenerationRate = regenerationRate
        )
    }
    
    fun regenerate(deltaTime: Float): ShieldComponent {
        return ShieldComponent(
            currentShields = (currentShields + regenerationRate * deltaTime).coerceAtMost(maxShields),
            maxShields = maxShields,
            regenerationRate = regenerationRate
        )
    }
}

/**
 * Resource component for economic entities
 */
@JvmInline
value class ResourceComponent(
    val resourceType: String,
    val amount: Float,
    val maxAmount: Float,
    val generationRate: Float = 0f
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.RESOURCE
    
    fun canExtract(amount: Float): Boolean = this.amount >= amount
    
    fun extract(amount: Float): ResourceComponent {
        return ResourceComponent(
            resourceType = resourceType,
            amount = (this.amount - amount).coerceAtLeast(0f),
            maxAmount = maxAmount,
            generationRate = generationRate
        )
    }
    
    fun generate(deltaTime: Float): ResourceComponent {
        return ResourceComponent(
            resourceType = resourceType,
            amount = (amount + generationRate * deltaTime).coerceAtMost(maxAmount),
            maxAmount = maxAmount,
            generationRate = generationRate
        )
    }
}

/**
 * Computronium component for advanced AI capabilities
 */
@JvmInline
value class ComputroniumComponent(
    val currentComputronium: Float,
    val maxComputronium: Float,
    val generationRate: Float = 1f,
    val focusMode: String = "balanced"
) : Component {
    override val typeId: ComponentTypeId = ComponentTypeId.COMPUTRONIUM
    
    fun computroniumRatio(): Float = if (maxComputronium > 0f) currentComputronium / maxComputronium else 0f
    
    fun canSpend(amount: Float): Boolean = currentComputronium >= amount
    
    fun spend(amount: Float): ComputroniumComponent {
        return ComputroniumComponent(
            currentComputronium = (currentComputronium - amount).coerceAtLeast(0f),
            maxComputronium = maxComputronium,
            generationRate = generationRate,
            focusMode = focusMode
        )
    }
    
    fun generate(deltaTime: Float): ComputroniumComponent {
        return ComputroniumComponent(
            currentComputronium = (currentComputronium + generationRate * deltaTime).coerceAtMost(maxComputronium),
            maxComputronium = maxComputronium,
            generationRate = generationRate,
            focusMode = focusMode
        )
    }
}