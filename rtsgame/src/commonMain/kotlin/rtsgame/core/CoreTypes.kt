package rtsgame.core

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * Core type system with comprehensive taxonomies for RTS game
 * Following TrikeShed patterns and CCEK (CoroutineContext.Element.Key) design
 */

// ============================================================================
// Base Type Aliases - Foundation
// ============================================================================

// Numeric types
typealias GameTick = Long
typealias EntityId = Int
typealias PlayerId = Int
typealias TeamId = Int
typealias ComponentId = Int
typealias ProtocolVersion = Int
typealias PacketSequence = Int
typealias Timestamp = Long
typealias Hash = Int 
// Functional types
typealias Effect<T> = (World) -> Pair<World, T>
typealias Pure<T> = (World) -> T
typealias Lens<S, A> = Pair<(S) -> A, (S, A) -> S>

// ============================================================================
// Vector & Math Types
// ============================================================================

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Vec2(x / scalar, y / scalar)
    fun dot(other: Vec2) = x * other.x + y * other.y
    fun length() = kotlin.math.sqrt(x * x + y * y)
    fun normalize() = this / length()
    fun dist(other: Vec2) = (this - other).length()
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vec3(x / scalar, y / scalar, z / scalar)
    fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    fun length() = kotlin.math.sqrt(x * x + y * y + z * z)
    fun normalize() = this / length()
    fun dist(other: Vec3) = (this - other).length()
}

data class Rect(val min: Vec2, val max: Vec2) {
    val width get() = max.x - min.x
    val height get() = max.y - min.y
    val center get() = Vec2((min.x + max.x) / 2, (min.y + max.y) / 2)
    
    fun contains(point: Vec2) = 
        point.x >= min.x && point.x <= max.x &&
        point.y >= min.y && point.y <= max.y
        
    fun intersects(other: Rect) =
        min.x <= other.max.x && max.x >= other.min.x &&
        min.y <= other.max.y && max.y >= other.min.y
}
 
data class Join3<A, B, C>(val first: A, val second: B, val third: C)
data class Join4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

infix fun <A, B> A.j(second: B) = this j second
infix fun <A, B, C> Join<A, B>.j(third: C) = Join3(first, second, third)
infix fun <A, B, C, D> Join3<A, B, C>.j(fourth: D) = Join4(first, second, third, fourth)

// ============================================================================
// Component System Types
// ============================================================================

// Component marker interface
interface Component

// Core component types
data class Position(val vec: Vec3) : Component
data class Velocity(val vec: Vec3) : Component
data class Health(val current: Float, val max: Float) : Component
data class Damage(val value: Float) : Component
data class Team(val id: TeamId) : Component
data class Owner(val playerId: PlayerId) : Component
data class UnitType(val type: String) : Component
data class Range(val value: Float) : Component
data class Speed(val value: Float) : Component
data class Vision(val range: Float) : Component
data class Resource(val type: String, val amount: Float) : Component

// Entity is a collection of components
typealias Entity = Map<String, Component>
typealias MutableEntity = MutableMap<String, Component>

// World is a collection of entities
typealias World = Map<EntityId, Entity>
typealias MutableWorld = MutableMap<EntityId, MutableEntity>

// ============================================================================
// Command Types - Game Actions
// ============================================================================

sealed class Command {
    abstract val tick: GameTick
    abstract val playerId: PlayerId
    
    data class Move(
        override val tick: GameTick,
        override val playerId: PlayerId,
        val entities: Indexed<EntityId>,
        val target: Vec3
    ) : Command()
    
    data class Attack(
        override val tick: GameTick,
        override val playerId: PlayerId,
        val attackers: Indexed<EntityId>,
        val target: EntityId
    ) : Command()
    
    data class Build(
        override val tick: GameTick,
        override val playerId: PlayerId,
        val builderId: EntityId,
        val unitType: String,
        val position: Vec3
    ) : Command()
    
    data class Harvest(
        override val tick: GameTick,
        override val playerId: PlayerId,
        val harvesters: Indexed<EntityId>,
        val resourceId: EntityId
    ) : Command()
    
    data class Stop(
        override val tick: GameTick,
        override val playerId: PlayerId,
        val entities: Indexed<EntityId>
    ) : Command()
}

// ============================================================================
// Network Types
// ============================================================================

sealed class Packet {
    abstract val sequence: PacketSequence
    abstract val timestamp: Timestamp
    
    data class Commands(
        override val sequence: PacketSequence,
        override val timestamp: Timestamp,
        val commands: Indexed<Command>
    ) : Packet()
    
    data class StateSync(
        override val sequence: PacketSequence,
        override val timestamp: Timestamp,
        val tick: GameTick,
        val worldHash: Hash,
        val entityDeltas: Map<EntityId, EntityDelta>
    ) : Packet()
    
    data class Join(
        override val sequence: PacketSequence,
        override val timestamp: Timestamp,
        val playerId: PlayerId,
        val protocolVersion: ProtocolVersion
    ) : Packet()
    
    data class Leave(
        override val sequence: PacketSequence,
        override val timestamp: Timestamp,
        val playerId: PlayerId
    ) : Packet()
}

data class EntityDelta(
    val created: Boolean = false,
    val destroyed: Boolean = false,
    val components: Map<String, ComponentDelta>
)

data class ComponentDelta(
    val old: Component?,
    val new: Component?
)

// ============================================================================
// CCEK Context Keys - Coroutine Context Element Keys
// ============================================================================

// Base context element
interface GameContext : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<GameContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

// Game tick context
data class TickContext(val tick: GameTick) : GameContext

// Player context
data class PlayerContext(val playerId: PlayerId) : GameContext

// Network context
data class NetworkContext(
    val isHost: Boolean,
    val latency: Long,
    val packetLoss: Float
) : GameContext

// Codec context
data class CodecContext(
    val version: ProtocolVersion,
    val compression: Boolean
) : GameContext

// Simulation context
data class SimulationContext(
    val deterministicMode: Boolean,
    val tickRate: Int,
    val interpolation: Boolean
) : GameContext

// ============================================================================
// Codec Types
// ============================================================================

// Binary buffer for serialization
class BinaryBuffer(var data: ByteArray = ByteArray(256), var position: Int = 0) {
    fun ensureCapacity(needed: Int) {
        if (position + needed > data.size) {
            data = data.copyOf((data.size * 2).coerceAtLeast(position + needed))
        }
    }
    
    fun writeByte(value: Byte) {
        ensureCapacity(1)
        data[position++] = value
    }
    
    fun readByte(): Byte = data[position++]
    
    fun writeVarInt(value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            writeByte(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        writeByte(v.toByte())
    }
    
    fun readVarInt(): Int {
        var value = 0
        var shift = 0
        var b: Byte
        do {
            b = readByte()
            value = value or ((b.toInt() and 0x7F) shl shift)
            shift += 7
        } while (b.toInt() and 0x80 != 0)
        return value
    }
    
    fun writeFloat(value: Float) {
        writeVarInt(value.toBits())
    }
    
    fun readFloat(): Float = Float.fromBits(readVarInt())
    
    fun toByteArray(): ByteArray = data.copyOf(position)
}

// Codec interface
interface Codec<T> {
    fun encode(value: T, buffer: BinaryBuffer)
    fun decode(buffer: BinaryBuffer): T
}

// ============================================================================
// System Types
// ============================================================================

// System that processes entities
typealias System = suspend (World, GameTick) -> World

// System with side effects
typealias EffectSystem = suspend (World, GameTick, CoroutineScope) -> World

// Query for filtering entities
typealias Query = (Entity) -> Boolean

// ============================================================================
// AI Types
// ============================================================================

data class AIState(
    val goal: AIGoal,
    val blackboard: Map<String, Any>,
    val memory: List<AIMemory>
)

sealed class AIGoal {
    object Idle : AIGoal()
    data class MoveTo(val target: Vec3) : AIGoal()
    data class Attack(val target: EntityId) : AIGoal()
    data class Defend(val position: Vec3, val radius: Float) : AIGoal()
    data class Harvest(val resourceType: String) : AIGoal()
    data class Build(val unitType: String) : AIGoal()
}

data class AIMemory(
    val tick: GameTick,
    val event: String,
    val data: Map<String, Any>
)

// ============================================================================
// Pathfinding Types
// ============================================================================

data class Path(
    val waypoints: Indexed<Vec3>,
    val cost: Float,
    val complete: Boolean
)

data class NavMesh(
    val nodes: Indexed<NavNode>,
    val edges: Map<Int, Indexed<Int>>
)

data class NavNode(
    val id: Int,
    val position: Vec3,
    val radius: Float
)

// ============================================================================
// Rendering Types
// ============================================================================

data class RenderCommand(
    val type: RenderType,
    val transform: Mat4,
    val mesh: String,
    val material: Material
)

enum class RenderType {
    MESH, SPRITE, PARTICLE, UI
}

data class Material(
    val diffuse: Vec3,
    val specular: Vec3,
    val shininess: Float,
    val texture: String?
)

data class Mat4(val values: FloatArray = FloatArray(16)) {
    companion object {
        fun identity() = Mat4(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        ))
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

// Entity builders
fun entityOf(vararg components: Pair<String, Component>): Entity = 
    components.toMap()

fun mutableEntityOf(vararg components: Pair<String, Component>): MutableEntity = 
    components.toMap().toMutableMap()

// Component access
inline fun <reified T : Component> Entity.get(key: String): T? = 
    get(key) as? T

inline fun <reified T : Component> Entity.require(key: String): T = 
    get(key) as T

// World queries
fun World.query(predicate: Query): Sequence<Pair<EntityId, Entity>> =
    asSequence().filter { (_, entity) -> predicate(entity) }

fun World.withComponent(component: String): Sequence<Pair<EntityId, Entity>> =
    query { it.containsKey(component) }

// ============================================================================
// Extension Functions
// ============================================================================

// Coroutine context extensions
suspend fun <T> withTick(tick: GameTick, block: suspend CoroutineScope.() -> T): T =
    kotlinx.coroutines.withContext(TickContext(tick), block)

suspend fun <T> withPlayer(playerId: PlayerId, block: suspend CoroutineScope.() -> T): T =
    kotlinx.coroutines.withContext(PlayerContext(playerId), block)

suspend fun <T> withCodec(version: ProtocolVersion, compression: Boolean, block: suspend CoroutineScope.() -> T): T =
    kotlinx.coroutines.withContext(CodecContext(version, compression), block)

// Current context accessors
val CoroutineContext.currentTick: GameTick?
    get() = this[TickContext]?.tick

val CoroutineContext.currentPlayer: PlayerId?
    get() = this[PlayerContext]?.playerId

val CoroutineContext.codecVersion: ProtocolVersion?
    get() = this[CodecContext]?.version