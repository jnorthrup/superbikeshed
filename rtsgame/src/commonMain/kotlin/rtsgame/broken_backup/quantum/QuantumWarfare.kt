import kotlin.math.*
package rtsgame.quantum
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import rtsgame.components.*
import borg.trikeshed.lib.*
import kotlin.math.*

/**
 * Quantum warfare mechanics - units can exist in superposition
 * and tunnel through obstacles
 */
class QuantumWarfareSystem : System {
    internal val quantumStates = mutableMapOf<EntityId, QuantumState>()
    internal val entanglements = mutableMapOf<EntityId, Set<EntityId>>()
    internal val waveCollapse = WaveCollapseCalculator()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Update quantum states
        updateQuantumStates(world, deltaTime)
        
        // Process quantum tunneling
        processQuantumTunneling(world, deltaTime)
        
        // Handle entangled units
        processEntanglements(world)
        
        // Collapse wavefunctions when observed
        collapseObservedStates(world)
    }
    
    internal fun updateQuantumStates(world: ECSWorld, deltaTime: Float) {
        world.forEach<QuantumComponent>(ComponentTypes.QUANTUM) { entity, quantum ->
            val state = quantumStates.getOrPut(entity) { 
                QuantumState(entity, quantum.coherence)
            }
            
            // Quantum drift - position uncertainty increases over time
            state.positionUncertainty += quantum.driftRate * deltaTime
            
            // Coherence decay
            quantum.coherence *= (1f - quantum.decoherenceRate * deltaTime)
            
            if (quantum.coherence < 0.1f) {
                // Force collapse if coherence too low
                collapseWavefunction(world, entity, state)
            }
        }
    }
    
    internal fun processQuantumTunneling(world: ECSWorld, deltaTime: Float) {
        world.forEach<QuantumComponent>(ComponentTypes.QUANTUM) { entity, quantum ->
            if (!quantum.canTunnel) return@forEach
            
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
            val physics = world.getComponent<PhysicsComponent>(entity, ComponentTypes.PHYSICS) ?: return@forEach
            
            // Check for obstacles ahead
            val ahead = PositionComponent(
                pos.x + physics.acceleration.vx * 10f,
                pos.y + physics.acceleration.vy * 10f
            )
            
            if (hasObstacle(world, pos, ahead)) {
                // Calculate tunneling probability
                val distance = distance(pos, ahead)
                val probability = exp(-distance / quantum.tunnelRange) * quantum.coherence
                
                if (kotlin.random.Random.nextFloat() < probability * deltaTime) {
                    // Tunnel through!
                    pos.x = ahead.x
                    pos.y = ahead.y
                    
                    // Energy cost
                    quantum.coherence *= 0.7f
                    
                    // Visual effect
                    createQuantumTunnelEffect(world, pos, ahead)
                }
            }
        }
    }
    
    internal fun processEntanglements(world: ECSWorld) {
        entanglements.forEach { (entity, entangled) ->
            val sourceQuantum = world.getComponent<QuantumComponent>(entity, ComponentTypes.QUANTUM)
            
            if (sourceQuantum?.isEntangled == true) {
                // Propagate quantum effects to entangled units
                entangled.forEach { target ->
                    propagateQuantumEffect(world, entity, target)
                }
            }
        }
    }
    
    internal fun propagateQuantumEffect(world: ECSWorld, source: EntityId, target: EntityId) {
        val sourceHealth = world.getComponent<HealthComponent>(source, ComponentTypes.HEALTH)
        val targetHealth = world.getComponent<HealthComponent>(target, ComponentTypes.HEALTH)
        
        // Shared damage/healing
        if (sourceHealth != null && targetHealth != null) {
            val healthDiff = sourceHealth.health - targetHealth.health
            val transfer = healthDiff * 0.1f
            
            sourceHealth.health -= transfer
            targetHealth.health += transfer
        }
        
        // Shared position uncertainty
        val sourceState = quantumStates[source]
        val targetState = quantumStates[target]
        
        if (sourceState != null && targetState != null) {
            val avgUncertainty = (sourceState.positionUncertainty + targetState.positionUncertainty) / 2f
            sourceState.positionUncertainty = avgUncertainty
            targetState.positionUncertainty = avgUncertainty
        }
    }
    
    internal fun collapseObservedStates(world: ECSWorld) {
        world.forEach<VisionComponent>(ComponentTypes.VISION) { observer, vision ->
            val observerPos = world.getComponent<PositionComponent>(observer, ComponentTypes.POSITION) ?: return@forEach
            val observerTeam = world.getComponent<TeamComponent>(observer, ComponentTypes.TEAM)
            
            // Find quantum units in vision range
            quantumStates.forEach { (entity, state) ->
                if (entity == observer) return@forEach
                
                val quantum = world.getComponent<QuantumComponent>(entity, ComponentTypes.QUANTUM) ?: return@forEach
                val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return@forEach
                val team = world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM)
                
                // Only enemies cause wavefunction collapse
                if (team?.teamId == observerTeam?.teamId) return@forEach
                
                val dist = distance(observerPos, pos)
                if (dist <= vision.sightRange) {
                    // Observation causes collapse
                    val collapseStrength = 1f - (dist / vision.sightRange)
                    
                    if (kotlin.random.Random.nextFloat() < collapseStrength) {
                        collapseWavefunction(world, entity, state)
                    }
                }
            }
        }
    }
    
    internal fun collapseWavefunction(world: ECSWorld, entity: EntityId, state: QuantumState) {
        val quantum = world.getComponent<QuantumComponent>(entity, ComponentTypes.QUANTUM) ?: return
        val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return
        
        // Collapse to definite position
        if (state.positionUncertainty > 0) {
            val angle = kotlin.random.Random.nextFloat() * PI * 2
            val radius = kotlin.random.Random.nextFloat() * state.positionUncertainty
            
            pos.x += cos(angle).toFloat() * radius
            pos.y += sin(angle).toFloat() * radius
            
            state.positionUncertainty = 0f
        }
        
        // Reset quantum properties
        quantum.coherence = 0f
        quantum.isEntangled = false
        
        // Remove from entanglements
        entanglements.remove(entity)
        entanglements.values.forEach { it.minus(entity) }
        
        // Visual effect
        createCollapseEffect(world, pos)
    }
    
    internal fun createQuantumTunnelEffect(world: ECSWorld, from: PositionComponent, to: PositionComponent) {
        // Create particle trail
        val particles = 20
        for (i in 0 until particles) {
            val t = i.toFloat() / particles
            val x = lerp(from.x, to.x, t)
            val y = lerp(from.y, to.y, t)
            
            val particle = world.createEntity()
            world.addComponent(particle, PositionComponent(x, y))
            world.addComponent(particle, ParticleComponent(
                lifetime = 0.5f,
                color = Color(0.5f, 0f, 1f),
                size = 5f
            ))
        }
    }
    
    internal fun createCollapseEffect(world: ECSWorld, pos: PositionComponent) {
        // Shockwave effect
        repeat(30) {
            val angle = it * (PI * 2 / 30)
            val particle = world.createEntity()
            
            world.addComponent(particle, PositionComponent(pos.x, pos.y))
            world.addComponent(particle, VelocityComponent(
                cos(angle).toFloat() * 100f,
                sin(angle).toFloat() * 100f,
                100f
            ))
            world.addComponent(particle, ParticleComponent(
                lifetime = 1f,
                color = Color(0f, 1f, 1f),
                size = 3f
            ))
        }
    }
    
    internal fun hasObstacle(world: ECSWorld, from: PositionComponent, to: PositionComponent): Boolean {
        // Check if path is blocked
        return false // Simplified
    }
    
    internal fun distance(a: PositionComponent, b: PositionComponent): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
    
    internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/**
 * Temporal manipulation system - units can slow/speed up time locally
 */
class TemporalWarfareSystem : System {
    internal val timeFields = mutableListOf<TimeField>()
    internal val chronoAnchors = mutableMapOf<EntityId, ChronoAnchor>()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Update time fields
        updateTimeFields(deltaTime)
        
        // Apply temporal effects
        applyTemporalEffects(world, deltaTime)
        
        // Process chrono anchors
        processChronoAnchors(world, deltaTime)
    }
    
    internal fun updateTimeFields(deltaTime: Float) {
        timeFields.removeAll { field ->
            field.duration -= deltaTime
            field.duration <= 0
        }
    }
    
    internal fun applyTemporalEffects(world: ECSWorld, deltaTime: Float) {
        world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            var timeScale = 1f
            
            // Check if entity is in any time field
            timeFields.forEach { field ->
                val dist = distance(pos.x, pos.y, field.x, field.y)
                if (dist <= field.radius) {
                    val strength = 1f - (dist / field.radius)
                    timeScale *= lerp(1f, field.timeScale, strength)
                }
            }
            
            // Apply time dilation to entity
            val temporal = world.getComponent<TemporalComponent>(entity, ComponentTypes.TEMPORAL)
            if (temporal != null) {
                temporal.localTimeScale = timeScale
            } else if (abs(timeScale - 1f) > 0.1f) {
                // Add temporal component if significantly affected
                world.addComponent(entity, TemporalComponent(timeScale))
            }
        }
    }
    
    internal fun processChronoAnchors(world: ECSWorld, deltaTime: Float) {
        chronoAnchors.forEach { (entity, anchor) ->
            if (anchor.isActive) {
                // Record state
                val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION)
                val health = world.getComponent<HealthComponent>(entity, ComponentTypes.HEALTH)
                
                if (pos != null && health != null) {
                    anchor.recordState(
                        TemporalSnapshot(
                            position = pos.copy(),
                            health = health.health,
                            shield = health.shield,
                            timestamp = anchor.elapsedTime
                        )
                    )
                }
                
                anchor.elapsedTime += deltaTime
                
                // Check for rewind trigger
                if (health?.health ?: 0f < health?.maxHealth ?: 1f * 0.3f) {
                    rewindTime(world, entity, anchor, 3f) // Rewind 3 seconds
                    anchor.isActive = false
                }
            }
        }
    }
    
    internal fun rewindTime(world: ECSWorld, entity: EntityId, anchor: ChronoAnchor, seconds: Float) {
        val targetTime = anchor.elapsedTime - seconds
        val snapshot = anchor.getSnapshotAt(targetTime) ?: return
        
        // Restore state
        val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION)
        val health = world.getComponent<HealthComponent>(entity, ComponentTypes.HEALTH)
        
        pos?.let {
            it.x = snapshot.position.x
            it.y = snapshot.position.y
        }
        
        health?.let {
            it.health = snapshot.health
            it.shield = snapshot.shield
        }
        
        // Temporal shockwave
        createTemporalShockwave(world, pos!!, seconds)
    }
    
    internal fun createTemporalShockwave(world: ECSWorld, center: PositionComponent, strength: Float) {
        timeFields.add(TimeField(
            x = center.x,
            y = center.y,
            radius = 100f * strength,
            timeScale = 0.1f,
            duration = 2f
        ))
    }
    
    fun createTimeField(x: Float, y: Float, radius: Float, timeScale: Float, duration: Float) {
        timeFields.add(TimeField(x, y, radius, timeScale, duration))
    }
    
    fun createChronoAnchor(entity: EntityId) {
        chronoAnchors[entity] = ChronoAnchor()
    }
    
    internal fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}

/**
 * Dimensional warfare - units can phase between dimensions
 */
class DimensionalWarfareSystem : System {
    internal val dimensions = Array(3) { Dimension(it) }
    internal val phaseShifts = mutableMapOf<EntityId, PhaseShift>()
    internal val dimensionalRifts = mutableListOf<DimensionalRift>()
    
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Update phase shifts
        updatePhaseShifts(world, deltaTime)
        
        // Process dimensional rifts
        processRifts(world, deltaTime)
        
        // Handle cross-dimensional interactions
        processCrossDimensionalCombat(world)
    }
    
    internal fun updatePhaseShifts(world: ECSWorld, deltaTime: Float) {
        phaseShifts.entries.removeAll { (entity, shift) ->
            shift.progress += deltaTime / shift.duration
            
            if (shift.progress >= 1f) {
                // Complete phase shift
                completePhaseShift(world, entity, shift)
                true
            } else {
                // Update partial visibility
                updatePhasedVisibility(world, entity, shift)
                false
            }
        }
    }
    
    internal fun completePhaseShift(world: ECSWorld, entity: EntityId, shift: PhaseShift) {
        val dimensional = world.getComponent<DimensionalComponent>(entity, ComponentTypes.DIMENSIONAL)
        dimensional?.currentDimension = shift.targetDimension
    }
    
    internal fun updatePhasedVisibility(world: ECSWorld, entity: EntityId, shift: PhaseShift) {
        val visibility = world.getComponent<VisibilityComponent>(entity, ComponentTypes.VISIBILITY)
        visibility?.opacity = 1f - shift.progress * 0.7f
    }
    
    internal fun processRifts(world: ECSWorld, deltaTime: Float) {
        dimensionalRifts.removeAll { rift ->
            rift.lifetime -= deltaTime
            
            if (rift.lifetime > 0) {
                // Pull nearby units
                pullUnitsIntoRift(world, rift)
                false
            } else {
                // Collapse rift
                collapseRift(world, rift)
                true
            }
        }
    }
    
    internal fun pullUnitsIntoRift(world: ECSWorld, rift: DimensionalRift) {
        val pullRadius = rift.radius * 2f
        val pullStrength = 200f
        
        world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            val physics = world.getComponent<PhysicsComponent>(entity, ComponentTypes.PHYSICS) ?: return@forEach
            
            val dist = distance(pos.x, pos.y, rift.x, rift.y)
            if (dist < pullRadius && dist > rift.radius) {
                // Pull towards rift
                val force = pullStrength * (1f - dist / pullRadius)
                val dx = (rift.x - pos.x) / dist
                val dy = (rift.y - pos.y) / dist
                
                physics.acceleration.vx += dx * force
                physics.acceleration.vy += dy * force
            } else if (dist < rift.radius) {
                // Transport through rift
                transportThroughRift(world, entity, rift)
            }
        }
    }
    
    internal fun transportThroughRift(world: ECSWorld, entity: EntityId, rift: DimensionalRift) {
        val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION) ?: return
        
        // Transport to connected dimension
        pos.x = rift.exitX
        pos.y = rift.exitY
        
        // Shift dimension
        startPhaseShift(entity, rift.targetDimension, 0.1f)
    }
    
    internal fun processCrossDimensionalCombat(world: ECSWorld) {
        // Units in different dimensions can partially affect each other
        world.forEach<WeaponComponent>(ComponentTypes.WEAPON) { attacker, weapon ->
            val attackerDim = world.getComponent<DimensionalComponent>(attacker, ComponentTypes.DIMENSIONAL)
                ?.currentDimension ?: 0
            
            // Dimensional weapons can hit across dimensions
            if (weapon.damageType == DamageType.PLASMA) {
                // Check all dimensions for targets
                dimensions.forEach { dimension ->
                    if (dimension.id != attackerDim) {
                        // Reduced damage across dimensions
                        val dimensionalPenalty = 0.3f
                        // Apply damage with penalty
                    }
                }
            }
        }
    }
    
    fun startPhaseShift(entity: EntityId, targetDimension: Int, duration: Float) {
        phaseShifts[entity] = PhaseShift(targetDimension, duration)
    }
    
    fun createDimensionalRift(x: Float, y: Float, targetDimension: Int) {
        // Find exit point in target dimension
        val exitX = x + kotlin.random.Random.nextFloat() * 200f - 100f
        val exitY = y + kotlin.random.Random.nextFloat() * 200f - 100f
        
        dimensionalRifts.add(DimensionalRift(
            x = x,
            y = y,
            exitX = exitX,
            exitY = exitY,
            radius = 50f,
            targetDimension = targetDimension,
            lifetime = 10f
        ))
    }
    
    internal fun collapseRift(world: ECSWorld, rift: DimensionalRift) {
        // Explosion in both dimensions
        createDimensionalExplosion(world, rift.x, rift.y, rift.radius * 2f)
    }
    
    internal fun createDimensionalExplosion(world: ECSWorld, x: Float, y: Float, radius: Float) {
        // Damage across all dimensions
        dimensions.forEach { dimension ->
            // Create explosion effect in each dimension
        }
    }
    
    internal fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}

// Quantum components and data structures
data class QuantumComponent(
    var coherence: Float = 1f,
    var driftRate: Float = 0.1f,
    var decoherenceRate: Float = 0.05f,
    var canTunnel: Boolean = true,
    var tunnelRange: Float = 50f,
    var isEntangled: Boolean = false
) : Component {
    override val typeId = ComponentTypeId(300)
}

data class TemporalComponent(
    var localTimeScale: Float = 1f,
    var maxRewind: Float = 5f,
    var chronoEnergy: Float = 100f
) : Component {
    override val typeId = ComponentTypeId(301)
}

data class DimensionalComponent(
    var currentDimension: Int = 0,
    var phaseStrength: Float = 1f,
    var canPhaseShift: Boolean = true
) : Component {
    override val typeId = ComponentTypeId(302)
}

data class VisibilityComponent(
    var opacity: Float = 1f,
    var isPhased: Boolean = false
) : Component {
    override val typeId = ComponentTypeId(303)
}

data class ParticleComponent(
    var lifetime: Float,
    val color: Color,
    var size: Float
) : Component {
    override val typeId = ComponentTypeId(304)
}

class QuantumState(
    val entity: EntityId,
    var coherence: Float,
    var positionUncertainty: Float = 0f
)

class WaveCollapseCalculator {
    fun calculateCollapseProbability(
        observer: EntityId,
        observed: EntityId,
        distance: Float
    ): Float {
        return exp(-distance / 100f)
    }
}

data class TimeField(
    val x: Float,
    val y: Float,
    val radius: Float,
    val timeScale: Float,
    var duration: Float
)

class ChronoAnchor {
    internal val stateHistory = mutableListOf<TemporalSnapshot>()
    var isActive = true
    var elapsedTime = 0f
    
    fun recordState(snapshot: TemporalSnapshot) {
        stateHistory.add(snapshot)
        
        // Limit history size
        if (stateHistory.size > 300) { // 5 seconds at 60 FPS
            stateHistory.removeAt(0)
        }
    }
    
    fun getSnapshotAt(time: Float): TemporalSnapshot? {
        return stateHistory.findLast { it.timestamp <= time }
    }
}

data class TemporalSnapshot(
    val position: PositionComponent,
    val health: Float,
    val shield: Float,
    val timestamp: Float
)

class Dimension(val id: Int) {
    val entities = mutableSetOf<EntityId>()
    var energyLevel = 1f
    var stability = 1f
}

data class PhaseShift(
    val targetDimension: Int,
    val duration: Float,
    var progress: Float = 0f
)

data class DimensionalRift(
    val x: Float,
    val y: Float,
    val exitX: Float,
    val exitY: Float,
    val radius: Float,
    val targetDimension: Int,
    var lifetime: Float
)

// Extension to Component types
object QuantumComponentTypes {
    val QUANTUM = ComponentTypeId(300)
    val TEMPORAL = ComponentTypeId(301)
    val DIMENSIONAL = ComponentTypeId(302)
    val VISIBILITY = ComponentTypeId(303)
    val PARTICLE = ComponentTypeId(304)
}