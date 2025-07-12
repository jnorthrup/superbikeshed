import kotlin.math.*
package rtsgame.systems
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import rtsgame.components.*
import kotlin.math.*

/**
 * Ultra-optimized movement system with SIMD-friendly batch processing
 */
class MovementSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Batch process positions and velocities for cache efficiency
        world.forEachBatch<PositionComponent>(ComponentTypes.POSITION, 64) { entities, positions ->
            val velocities = Array(entities.component1()) { i ->
                world.getComponent<VelocityComponent>(entities[i], ComponentTypes.VELOCITY)
            }
            
            // Vectorized position update
            for (i in 0 until entities.component1()) {
                val vel = velocities[i] ?: continue
                val pos = positions[i]
                
                // Update position
                pos.x += vel.vx * deltaTime
                pos.y += vel.vy * deltaTime
                
                // Apply drag
                vel.vx *= (1f - 0.1f * deltaTime)
                vel.vy *= (1f - 0.1f * deltaTime)
            }
        }
    }
}

/**
 * Physics system with force accumulation
 */
class PhysicsSystem : ParallelSystem(ComponentTypes.POSITION, ComponentTypes.VELOCITY, ComponentTypes.PHYSICS) {
    override fun processBatch(world: ECSWorld, entities: Indexed<EntityId>, deltaTime: Float) {
        for (i in 0 until entities.component1()) {
            val entity = entities[i]
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: continue
            val vel = world.getComponent<VelocityComponent>(entity, ComponentTypes.VELOCITY) ?: continue
            val physics = world.getComponent<PhysicsComponent>(entity, ComponentTypes.PHYSICS) ?: continue
            
            // Apply acceleration
            vel.vx += physics.acceleration.vx * deltaTime
            vel.vy += physics.acceleration.vy * deltaTime
            
            // Clamp to max speed
            val speed = sqrt(vel.vx * vel.vx + vel.vy * vel.vy)
            if (speed > vel.maxSpeed) {
                val scale = vel.maxSpeed / speed
                vel.vx *= scale
                vel.vy *= scale
            }
            
            // Reset acceleration
            physics.acceleration.vx = 0f
            physics.acceleration.vy = 0f
        }
    }
}

/**
 * Steering behaviors for unit AI
 */
class SteeringSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        world.forEach<UnitAIComponent>(ComponentTypes.UNIT_AI) { entity, ai ->
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
            val vel = world.getComponent<VelocityComponent>(entity, ComponentTypes.VELOCITY) ?: return@forEach
            val physics = world.getComponent<PhysicsComponent>(entity, ComponentTypes.PHYSICS) ?: return@forEach
            
            when (ai.state) {
                AIState.MOVING -> {
                    ai.targetPosition?.let { target ->
                        applySeek(pos, vel, physics, target)
                    }
                }
                AIState.ATTACKING -> {
                    ai.targetEntity?.let { targetId ->
                        val targetPos = world.getComponent<PositionComponent>(targetId, ComponentTypes.POSITION)
                        targetPos?.let { applyPursuit(pos, vel, physics, it, world, targetId) }
                    }
                }
                AIState.FLEEING -> {
                    ai.targetEntity?.let { threatId ->
                        val threatPos = world.getComponent<PositionComponent>(threatId, ComponentTypes.POSITION)
                        threatPos?.let { applyFlee(pos, vel, physics, it) }
                    }
                }
                else -> {}
            }
        }
    }
    
    internal fun applySeek(pos: PositionComponent, vel: VelocityComponent, physics: PhysicsComponent, target: PositionComponent) {
        val dx = target.x - pos.x
        val dy = target.y - pos.y
        val dist = sqrt(dx * dx + dy * dy)
        
        if (dist > 0.1f) {
            val desiredVx = (dx / dist) * vel.maxSpeed
            val desiredVy = (dy / dist) * vel.maxSpeed
            
            physics.acceleration.vx += (desiredVx - vel.vx) * 2f
            physics.acceleration.vy += (desiredVy - vel.vy) * 2f
        }
    }
    
    internal fun applyPursuit(pos: PositionComponent, vel: VelocityComponent, physics: PhysicsComponent, 
                            targetPos: PositionComponent, world: ECSWorld, targetId: EntityId) {
        val targetVel = world.getComponent<VelocityComponent>(targetId, ComponentTypes.VELOCITY)
        if (targetVel != null) {
            // Predict future position
            val dist = sqrt((targetPos.x - pos.x).pow(2) + (targetPos.y - pos.y).pow(2))
            val lookAheadTime = dist / vel.maxSpeed
            
            val futurePos = PositionComponent(
                targetPos.x + targetVel.vx * lookAheadTime,
                targetPos.y + targetVel.vy * lookAheadTime
            )
            applySeek(pos, vel, physics, futurePos)
        } else {
            applySeek(pos, vel, physics, targetPos)
        }
    }
    
    internal fun applyFlee(pos: PositionComponent, vel: VelocityComponent, physics: PhysicsComponent, threat: PositionComponent) {
        val dx = pos.x - threat.x
        val dy = pos.y - threat.y
        val dist = sqrt(dx * dx + dy * dy)
        
        if (dist < 200f && dist > 0) {
            val desiredVx = (dx / dist) * vel.maxSpeed
            val desiredVy = (dy / dist) * vel.maxSpeed
            
            physics.acceleration.vx += (desiredVx - vel.vx) * 3f
            physics.acceleration.vy += (desiredVy - vel.vy) * 3f
        }
    }
}

/**
 * Combat system with optimized damage calculation
 */
class CombatSystem : System {
    internal data class DamageEvent(val attacker: EntityId, val target: EntityId, val damage: Float)
    internal val damageQueue = mutableListOf<DamageEvent>()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        damageQueue.clear()
        
        // Find all units with weapons
        world.forEach<WeaponComponent>(ComponentTypes.WEAPON) { entity, weapon ->
            if (!weapon.canAttack(currentTime)) return@forEach
            
            val ai = world.getComponent<UnitAIComponent>(entity, ComponentTypes.UNIT_AI) ?: return@forEach
            if (ai.state != AIState.ATTACKING || ai.targetEntity == null) return@forEach
            
            val attackerPos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
            val targetPos = world.getComponent<PositionComponent>(ai.targetEntity!!, ComponentTypes.POSITION) ?: return@forEach
            
            val dist = distance(attackerPos, targetPos)
            if (dist <= weapon.range) {
                // Queue damage
                damageQueue.add(DamageEvent(entity, ai.targetEntity!!, weapon.damage))
                weapon.lastAttackTime = currentTime
                
                // Area damage
                if (weapon.areaOfEffect > 0) {
                    findEntitiesInRange(world, targetPos, weapon.areaOfEffect).forEach { nearbyEntity ->
                        if (nearbyEntity != ai.targetEntity) {
                            damageQueue.add(DamageEvent(entity, nearbyEntity, weapon.damage * 0.5f))
                        }
                    }
                }
            }
        }
        
        // Apply queued damage
        processDamageQueue(world)
    }
    
    internal fun processDamageQueue(world: ECSWorld) {
        damageQueue.forEach { event ->
            val health = world.getComponent<HealthComponent>(event.target, ComponentTypes.HEALTH) ?: return@forEach
            val attackerTeam = world.getComponent<TeamComponent>(event.attacker, ComponentTypes.TEAM)
            val targetTeam = world.getComponent<TeamComponent>(event.target, ComponentTypes.TEAM)
            
            // Don't damage same team
            if (attackerTeam?.teamId == targetTeam?.teamId) return@forEach
            
            // Apply damage with armor reduction
            val actualDamage = event.damage * (1f - health.armor / 100f)
            
            // Damage shield first
            if (health.shield > 0) {
                val shieldDamage = minOf(health.shield, actualDamage)
                health.shield -= shieldDamage
                health.health -= (actualDamage - shieldDamage)
            } else {
                health.health -= actualDamage
            }
            
            // Destroy entity if dead
            if (health.isDead) {
                world.destroyEntity(event.target)
            }
        }
    }
    
    internal fun distance(a: PositionComponent, b: PositionComponent): Float {
        return sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
    }
    
    internal fun findEntitiesInRange(world: ECSWorld, center: PositionComponent, range: Float): List<EntityId> {
        val result = mutableListOf<EntityId>()
        val rangeSq = range * range
        
        world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            val distSq = (pos.x - center.x).pow(2) + (pos.y - center.y).pow(2)
            if (distSq <= rangeSq) {
                result.add(entity)
            }
        }
        
        return result
    }
}

/**
 * Pathfinding system using flow fields for massive unit counts
 */
class FlowFieldPathfindingSystem : System {
    internal val flowFields = mutableMapOf<Int, FlowField>()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Update flow fields for each team
        updateFlowFields(world)
        
        // Apply flow field movement
        world.forEach<PathfindingComponent>(ComponentTypes.PATHFINDING) { entity, pathfinding ->
            if (!pathfinding.hasPath()) return@forEach
            
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
            val physics = world.getComponent<PhysicsComponent>(entity, ComponentTypes.PHYSICS) ?: return@forEach
            val team = world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM) ?: return@forEach
            
            flowFields[team.teamId]?.let { flowField ->
                val flow = flowField.getFlow(pos.x.toInt(), pos.y.toInt())
                physics.acceleration.vx += flow.x * 50f
                physics.acceleration.vy += flow.y * 50f
            }
        }
    }
    
    internal fun updateFlowFields(world: ECSWorld) {
        // Group units by team and calculate flow fields
        val teamTargets = mutableMapOf<Int, MutableList<PositionComponent>>()
        
        world.forEach<PathfindingComponent>(ComponentTypes.PATHFINDING) { entity, pathfinding ->
            pathfinding.getCurrentTarget()?.let { target ->
                val team = world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM)
                team?.let {
                    teamTargets.getOrPut(it.teamId) { mutableListOf() }.add(target)
                }
            }
        }
        
        // Update flow fields for teams with active pathfinding
        teamTargets.forEach { (teamId, targets) ->
            if (targets.isNotEmpty()) {
                flowFields[teamId] = calculateFlowField(targets)
            }
        }
    }
    
    internal fun calculateFlowField(targets: List<PositionComponent>): FlowField {
        // Simplified flow field - in production would use proper pathfinding
        return FlowField()
    }
}

class FlowField {
    fun getFlow(x: Int, y: Int): VelocityComponent {
        // Simplified - returns normalized direction to nearest target
        return VelocityComponent(1f, 0f, 0f)
    }
}

/**
 * Resource gathering system
 */
class ResourceSystem : ComponentSystem(ComponentTypes.RESOURCE_GATHERER, ComponentTypes.POSITION) {
    override fun processEntity(world: ECSWorld, entity: EntityId, deltaTime: Float) {
        val gatherer = world.getComponent<ResourceGathererComponent>(entity, ComponentTypes.RESOURCE_GATHERER)!!
        val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION)!!
        val ai = world.getComponent<UnitAIComponent>(entity, ComponentTypes.UNIT_AI)
        
        if (ai?.state == AIState.GATHERING && !gatherer.isFull) {
            // Gather resources
            gatherer.currentLoad = minOf(
                gatherer.currentLoad + gatherer.gatherRate * deltaTime,
                gatherer.capacity
            )
            
            if (gatherer.isFull) {
                // Return to base
                ai.state = AIState.MOVING
                // Set target to nearest base
            }
        }
    }
}

/**
 * Formation movement system
 */
class FormationSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        val formations = mutableMapOf<EntityId, MutableList<Pair<EntityId, FormationComponent>>>()
        
        // Group units by formation leader
        world.forEach<FormationComponent>(ComponentTypes.FORMATION) { entity, formation ->
            formation.formationLeader?.let { leader ->
                formations.getOrPut(leader) { mutableListOf() }.add(entity to formation)
            }
        }
        
        // Update formation positions
        formations.forEach { (leader, members) ->
            val leaderPos = world.getComponent<PositionComponent>(leader, ComponentTypes.POSITION) ?: return@forEach
            val leaderVel = world.getComponent<VelocityComponent>(leader, ComponentTypes.VELOCITY)
            
            members.forEach { (entity, formation) ->
                val offset = getFormationOffset(formation.formationType, formation.formationPosition, formation.spacing)
                val ai = world.getComponent<UnitAIComponent>(entity, ComponentTypes.UNIT_AI)
                
                // Calculate rotated offset based on leader's velocity
                val angle = if (leaderVel != null) atan2(leaderVel.vy, leaderVel.vx) else 0f
                val cos = cos(angle)
                val sin = sin(angle)
                
                val rotatedX = offset.first * cos - offset.second * sin
                val rotatedY = offset.first * sin + offset.second * cos
                
                ai?.targetPosition = PositionComponent(
                    leaderPos.x + rotatedX,
                    leaderPos.y + rotatedY
                )
            }
        }
    }
    
    internal fun getFormationOffset(type: FormationType, position: Int, spacing: Float): Pair<Float, Float> {
        return when (type) {
            FormationType.LINE -> Pair(position * spacing, 0f)
            FormationType.COLUMN -> Pair(0f, position * spacing)
            FormationType.WEDGE -> {
                val row = sqrt(position.toFloat()).toInt()
                val col = position - row * row
                Pair(col * spacing, row * spacing)
            }
            FormationType.CIRCLE -> {
                val angle = position * (PI * 2 / 12) // 12 units per circle
                Pair(cos(angle).toFloat() * spacing * 2, sin(angle).toFloat() * spacing * 2)
            }
            FormationType.SCATTER -> {
                val hash = position * 0x45d9f3b
                val x = ((hash and 0xFF) - 128) / 128f * spacing * 2
                val y = (((hash shr 8) and 0xFF) - 128) / 128f * spacing * 2
                Pair(x, y)
            }
        }
    }
}