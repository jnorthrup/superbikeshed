// ECS Core for RTS Game (Kotlin)
package ecs

// --- Entity Definition ---
typealias EntityId = Int

// --- Component Marker ---
interface Component

// --- Example Components ---
data class Position(var x: Float, var y: Float, var angle: Float = 0f) : Component

data class Velocity(var vx: Float, var vy: Float) : Component

data class Health(var hp: Int, var maxHp: Int) : Component

// Extend with more components as needed (Movement, Combat, AIState, Formation, etc.)

// --- Component Storage ---
class ComponentRegistry {
    private val position = mutableMapOf<EntityId, Position>()
    private val velocity = mutableMapOf<EntityId, Velocity>()
    private val health = mutableMapOf<EntityId, Health>()
    // Add more component maps as needed

    fun addPosition(id: EntityId, comp: Position) { position[id] = comp }
    fun getPosition(id: EntityId): Position? = position[id]

    fun addVelocity(id: EntityId, comp: Velocity) { velocity[id] = comp }
    fun getVelocity(id: EntityId): Velocity? = velocity[id]

    fun addHealth(id: EntityId, comp: Health) { health[id] = comp }
    fun getHealth(id: EntityId): Health? = health[id]

    // Add similar methods for other components

    // Example: get all entities with Position and Velocity
    fun entitiesWithPositionAndVelocity(): List<EntityId> =
        position.keys.intersect(velocity.keys).toList()
}

// --- System Stubs ---
abstract class System {
    abstract fun update(registry: ComponentRegistry, deltaTime: Float)
}

class MovementSystem : System() {
    override fun update(registry: ComponentRegistry, deltaTime: Float) {
        for (id in registry.entitiesWithPositionAndVelocity()) {
            val pos = registry.getPosition(id) ?: continue
            val vel = registry.getVelocity(id) ?: continue
            pos.x += vel.vx * deltaTime
            pos.y += vel.vy * deltaTime
        }
    }
}

class FormationSystem : System() {
    override fun update(registry: ComponentRegistry, deltaTime: Float) {
        // TODO: Implement formation logic based on Formation component
        // (leader/follower, slotting, steering, etc.)
    }
}

// --- Extension Points ---
// - Add more components (Combat, AIState, Resource, etc.)
// - Add more systems (CombatSystem, AISystem, ResourceSystem, etc.)
// - Implement entity creation/destruction logic
// - Add event system for interactions 