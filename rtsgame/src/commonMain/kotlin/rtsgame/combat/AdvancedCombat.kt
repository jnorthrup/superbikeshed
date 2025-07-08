import kotlin.math.*
package rtsgame.combat
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import rtsgame.components.*
import borg.trikeshed.lib.*
import kotlin.math.*

/**
 * Advanced combat system with realistic ballistics and damage modeling
 */
class AdvancedCombatSystem : System {
    internal val projectilePool = ProjectilePool(1024)
    internal val damageEvents = mutableListOf<DamageEvent>()
    internal val explosions = mutableListOf<Explosion>()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Process weapon firing
        processWeaponFiring(world, deltaTime)
        
        // Update projectiles
        updateProjectiles(world, deltaTime)
        
        // Process damage events
        processDamageEvents(world)
        
        // Update explosions
        updateExplosions(world, deltaTime)
    }
    
    internal fun processWeaponFiring(world: ECSWorld, deltaTime: Float) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        world.forEach<WeaponComponent>(ComponentTypes.WEAPON) { entity, weapon ->
            if (!weapon.canAttack(currentTime)) return@forEach
            
            val ai = world.getComponent<UnitAIComponent>(entity, ComponentTypes.UNIT_AI) ?: return@forEach
            val target = ai.targetEntity ?: return@forEach
            
            val attackerPos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
            val targetPos = world.getComponent<PositionComponent>(target, ComponentTypes.POSITION) ?: return@forEach
            val attackerTeam = world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM) ?: return@forEach
            
            // Check range and line of sight
            val distance = distance(attackerPos, targetPos)
            if (distance > weapon.range) return@forEach
            
            if (!hasLineOfSight(world, attackerPos, targetPos)) return@forEach
            
            // Fire weapon
            fireWeapon(world, entity, target, weapon, attackerPos, targetPos, attackerTeam.teamId)
            weapon.lastAttackTime = currentTime
        }
    }
    
    internal fun fireWeapon(
        world: ECSWorld,
        attacker: EntityId,
        target: EntityId,
        weapon: WeaponComponent,
        attackerPos: PositionComponent,
        targetPos: PositionComponent,
        teamId: Int
    ) {
        when (weapon.damageType) {
            DamageType.KINETIC, DamageType.ENERGY -> {
                // Create projectile
                val projectile = projectilePool.obtain()
                projectile.init(
                    attackerPos.x, attackerPos.y,
                    targetPos.x, targetPos.y,
                    weapon.projectileSpeed,
                    weapon.damage,
                    weapon.damageType,
                    teamId,
                    target
                )
            }
            DamageType.EXPLOSIVE -> {
                // Create explosive projectile
                val projectile = projectilePool.obtain()
                projectile.init(
                    attackerPos.x, attackerPos.y,
                    targetPos.x, targetPos.y,
                    weapon.projectileSpeed,
                    weapon.damage,
                    weapon.damageType,
                    teamId,
                    target,
                    weapon.areaOfEffect
                )
            }
            DamageType.PLASMA -> {
                // Instant hit with burn effect
                damageEvents.add(DamageEvent(
                    target = target,
                    damage = weapon.damage,
                    damageType = weapon.damageType,
                    attacker = attacker
                ))
                
                // Add burn DoT
                world.addComponent(target, BurnComponent(
                    damagePerSecond = weapon.damage * 0.2f,
                    duration = 3f
                ))
            }
            DamageType.EMP -> {
                // EMP effect
                val targetVel = world.getComponent<VelocityComponent>(target, ComponentTypes.VELOCITY)
                targetVel?.let {
                    it.maxSpeed *= 0.3f // Slow target
                }
                
                // Disable shields
                val targetHealth = world.getComponent<HealthComponent>(target, ComponentTypes.HEALTH)
                targetHealth?.shield = 0f
            }
        }
    }
    
    internal fun updateProjectiles(world: ECSWorld, deltaTime: Float) {
        val activeProjectiles = projectilePool.getActive()
        
        for (i in activeProjectiles.indices) {
            val projectile = activeProjectiles[i]
            if (!projectile.active) continue
            
            // Update position
            projectile.x += projectile.vx * deltaTime
            projectile.y += projectile.vy * deltaTime
            projectile.lifetime -= deltaTime
            
            // Check collision with target
            projectile.target?.let { targetId ->
                val targetPos = world.getComponent<PositionComponent>(targetId, ComponentTypes.POSITION)
                if (targetPos != null) {
                    val dist = distance(projectile.x, projectile.y, targetPos.x, targetPos.y)
                    
                    if (dist < 10f) { // Hit radius
                        onProjectileHit(world, projectile, targetId)
                        projectilePool.free(projectile)
                        continue
                    }
                }
            }
            
            // Check area collision for explosive projectiles
            if (projectile.areaOfEffect > 0) {
                checkAreaCollisions(world, projectile)
            }
            
            // Check lifetime
            if (projectile.lifetime <= 0) {
                if (projectile.areaOfEffect > 0) {
                    createExplosion(world, projectile)
                }
                projectilePool.free(projectile)
            }
        }
    }
    
    internal fun onProjectileHit(world: ECSWorld, projectile: Projectile, target: EntityId) {
        if (projectile.areaOfEffect > 0) {
            // Explosive damage
            createExplosion(world, projectile)
        } else {
            // Direct damage
            damageEvents.add(DamageEvent(
                target = target,
                damage = projectile.damage,
                damageType = projectile.damageType,
                attacker = null
            ))
        }
    }
    
    internal fun createExplosion(world: ECSWorld, projectile: Projectile) {
        explosions.add(Explosion(
            x = projectile.x,
            y = projectile.y,
            radius = projectile.areaOfEffect,
            damage = projectile.damage,
            damageType = projectile.damageType,
            teamId = projectile.teamId
        ))
    }
    
    internal fun checkAreaCollisions(world: ECSWorld, projectile: Projectile) {
        // Check for units near projectile path
        val nearbyEntities = findEntitiesInRange(world, projectile.x, projectile.y, 20f)
        
        nearbyEntities.forEach { entity ->
            val team = world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM)
            if (team?.teamId != projectile.teamId) {
                val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION)!!
                val dist = distance(projectile.x, projectile.y, pos.x, pos.y)
                
                if (dist < 5f) { // Direct hit on non-target
                    onProjectileHit(world, projectile, entity)
                    projectilePool.free(projectile)
                }
            }
        }
    }
    
    internal fun processDamageEvents(world: ECSWorld) {
        damageEvents.forEach { event ->
            val health = world.getComponent<HealthComponent>(event.target, ComponentTypes.HEALTH) ?: return@forEach
            
            // Calculate damage with resistances
            val actualDamage = calculateDamage(event.damage, event.damageType, health)
            
            // Apply to shield first
            if (health.shield > 0) {
                val shieldDamage = minOf(health.shield, actualDamage)
                health.shield -= shieldDamage
                val remainingDamage = actualDamage - shieldDamage
                
                if (remainingDamage > 0) {
                    health.health -= remainingDamage
                }
            } else {
                health.health -= actualDamage
            }
            
            // Check death
            if (health.isDead) {
                onUnitDeath(world, event.target, event.attacker)
            }
        }
        
        damageEvents.clear()
    }
    
    internal fun calculateDamage(
        baseDamage: Float,
        damageType: DamageType,
        health: HealthComponent
    ): Float {
        var damage = baseDamage
        
        // Apply armor reduction
        when (damageType) {
            DamageType.KINETIC -> damage *= (1f - health.armor / 200f)
            DamageType.ENERGY -> damage *= 1.2f // Bonus vs shields
            DamageType.EXPLOSIVE -> damage *= (1f - health.armor / 300f)
            DamageType.PLASMA -> damage *= 1.5f // High damage
            DamageType.EMP -> damage = baseDamage // Ignores armor
        }
        
        return damage
    }
    
    internal fun updateExplosions(world: ECSWorld, deltaTime: Float) {
        explosions.forEach { explosion ->
            // Find all entities in explosion radius
            val affected = findEntitiesInRange(world, explosion.x, explosion.y, explosion.radius)
            
            affected.forEach { entity ->
                val team = world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM)
                if (team?.teamId != explosion.teamId) {
                    val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION)!!
                    val dist = distance(explosion.x, explosion.y, pos.x, pos.y)
                    
                    // Damage falloff
                    val falloff = 1f - (dist / explosion.radius).coerceIn(0f, 1f)
                    val damage = explosion.damage * falloff
                    
                    damageEvents.add(DamageEvent(
                        target = entity,
                        damage = damage,
                        damageType = explosion.damageType,
                        attacker = null
                    ))
                }
            }
        }
        
        explosions.clear()
    }
    
    internal fun onUnitDeath(world: ECSWorld, unit: EntityId, killer: EntityId?) {
        // Award experience to killer
        killer?.let { attackerId ->
            val attackerLevel = world.getComponent<LevelComponent>(attackerId, ComponentTypes.LEVEL)
            attackerLevel?.addExperience(100)
        }
        
        // Create death effect
        val pos = world.getComponent<PositionComponent>(unit, ComponentTypes.POSITION)
        pos?.let {
            // Spawn death particles/effects
        }
        
        // Destroy entity
        world.destroyEntity(unit)
    }
    
    internal fun hasLineOfSight(
        world: ECSWorld,
        from: PositionComponent,
        to: PositionComponent
    ): Boolean {
        // Simple LOS check - in production would check terrain/obstacles
        return true
    }
    
    internal fun findEntitiesInRange(
        world: ECSWorld,
        x: Float,
        y: Float,
        range: Float
    ): List<EntityId> {
        val result = mutableListOf<EntityId>()
        val rangeSq = range * range
        
        world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            val distSq = (pos.x - x) * (pos.x - x) + (pos.y - y) * (pos.y - y)
            if (distSq <= rangeSq) {
                result.add(entity)
            }
        }
        
        return result
    }
    
    internal fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    internal fun distance(a: PositionComponent, b: PositionComponent): Float {
        return distance(a.x, a.y, b.x, b.y)
    }
}

/**
 * Projectile object pool for performance
 */
class ProjectilePool(initialCapacity: Int) {
    internal val pool = mutableListOf<Projectile>()
    internal val active = mutableListOf<Projectile>()
    
    init {
        repeat(initialCapacity) {
            pool.add(Projectile())
        }
    }
    
    fun obtain(): Projectile {
        val projectile = if (pool.isNotEmpty()) {
            pool.removeAt(pool.lastIndex)
        } else {
            Projectile()
        }
        
        active.add(projectile)
        return projectile
    }
    
    fun free(projectile: Projectile) {
        projectile.active = false
        active.remove(projectile)
        pool.add(projectile)
    }
    
    fun getActive(): List<Projectile> = active
}

/**
 * Projectile data
 */
class Projectile {
    var active = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var damage = 0f
    var damageType = DamageType.KINETIC
    var teamId = 0
    var target: EntityId? = null
    var areaOfEffect = 0f
    var lifetime = 2f
    
    fun init(
        startX: Float, startY: Float,
        targetX: Float, targetY: Float,
        speed: Float,
        damage: Float,
        damageType: DamageType,
        teamId: Int,
        target: EntityId? = null,
        areaOfEffect: Float = 0f
    ) {
        this.active = true
        this.x = startX
        this.y = startY
        
        // Calculate velocity
        val dx = targetX - startX
        val dy = targetY - startY
        val dist = sqrt(dx * dx + dy * dy)
        
        this.vx = if (dist > 0) (dx / dist) * speed else 0f
        this.vy = if (dist > 0) (dy / dist) * speed else 0f
        
        this.damage = damage
        this.damageType = damageType
        this.teamId = teamId
        this.target = target
        this.areaOfEffect = areaOfEffect
        this.lifetime = dist / speed + 0.5f
    }
}

/**
 * Damage event
 */
data class DamageEvent(
    val target: EntityId,
    val damage: Float,
    val damageType: DamageType,
    val attacker: EntityId?
)

/**
 * Explosion effect
 */
data class Explosion(
    val x: Float,
    val y: Float,
    val radius: Float,
    val damage: Float,
    val damageType: DamageType,
    val teamId: Int
)

/**
 * Additional combat components
 */
data class BurnComponent(
    var damagePerSecond: Float,
    var duration: Float,
    var elapsed: Float = 0f
) : Component {
    override val typeId = ComponentTypeId(100)
}

data class LevelComponent(
    var level: Int = 1,
    var experience: Int = 0,
    var nextLevelExp: Int = 100
) : Component {
    override val typeId = ComponentTypeId(101)
    
    fun addExperience(amount: Int) {
        experience += amount
        while (experience >= nextLevelExp) {
            experience -= nextLevelExp
            level++
            nextLevelExp = (nextLevelExp * 1.5).toInt()
        }
    }
}

/**
 * Ballistics calculation for artillery
 */
object BallisticsCalculator {
    fun calculateArtilleryTrajectory(
        start: PositionComponent,
        target: PositionComponent,
        muzzleVelocity: Float,
        gravity: Float = 9.81f
    ): Pair<Float, Float>? {
        val dx = target.x - start.x
        val dy = target.y - start.y
        val range = sqrt(dx * dx + dy * dy)
        
        // Calculate launch angle for max range
        val angle = calculateLaunchAngle(range, muzzleVelocity, gravity) ?: return null
        
        // Calculate velocity components
        val vx = cos(angle) * muzzleVelocity * (dx / range)
        val vy = sin(angle) * muzzleVelocity
        
        return vx to vy
    }
    
    internal fun calculateLaunchAngle(range: Float, velocity: Float, gravity: Float): Float? {
        // Optimal angle for max range: 45 degrees
        // For specific range: angle = 0.5 * asin(g * range / v²)
        val sinAngle = (gravity * range) / (velocity * velocity)
        
        if (sinAngle > 1f) return null // Out of range
        
        return asin(sinAngle) / 2f
    }
}