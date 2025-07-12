package rtsgame.core

import kotlin.coroutines.CoroutineContext

/**
 * TrikeShed-compliant type system following CLAUDE.md guidelines
 * Using Indexed, Series, Join, Cursor patterns as specified
 */

// ============================================================================
// TrikeShed Core Types - From CLAUDE.md
// ============================================================================

// Indexed is List, bad in speculative loops
typealias Indexed<T> = List<T>
typealias ArrayCowView<T> = List<T>  // Lazy mutable view
typealias ListCowView<T> = List<T>   // Lazy mutable view

// Series for lazy sequences
typealias Series<T> = Sequence<T>

// Cursor for iteration
typealias Cursor<T> = Iterator<T>

// ============================================================================
// Join Types - Core Algebraic Data Types
// ============================================================================

data class Twin<T>(val l: T, val r: T)
data class Join<A, B>(val l: A, val r: B)
data class Join3<A, B, C>(val a: A, val b: B, val c: C)
data class Join4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

// Infix operators
infix fun <A, B> A.j(that: B): Join<A, B> = this j that
infix fun <A, B, C> Join<A, B>.j(that: C): Join3<A, B, C> = Join3(l, r, that)
infix fun <A, B, C, D> Join3<A, B, C>.j(that: D): Join4<A, B, C, D> = Join4(a, b, c, that)

// Indexed2 for ambiguous K|V scenarios
interface Indexed2<K, V> {
    fun getByKey(key: K): V?
    fun getByValue(value: V): K?
}

// ============================================================================
// CCEK - CoroutineContext.Element.Key Pattern
// ============================================================================

// Game tick CCEK
data class GameTickCCEK(val tick: Long) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<GameTickCCEK>
    override val key: CoroutineContext.Key<*> get() = Key
}

// Player CCEK
data class PlayerCCEK(val id: Int) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<PlayerCCEK>
    override val key: CoroutineContext.Key<*> get() = Key
}

// Network CCEK
data class NetworkCCEK(
    val isHost: Boolean,
    val latency: Long,
    val packetLoss: Float
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NetworkCCEK>
    override val key: CoroutineContext.Key<*> get() = Key
}

// Codec CCEK
data class CodecCCEK(
    val version: Int,
    val compression: Boolean
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CodecCCEK>
    override val key: CoroutineContext.Key<*> get() = Key
}

// Session CCEK
data class SessionCCEK(
    val id: String,
    val players: Indexed<PlayerInfo>,
    val startTick: Long
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<SessionCCEK>
    override val key: CoroutineContext.Key<*> get() = Key
}

// ============================================================================
// Core Game Types Using TrikeShed Patterns
// ============================================================================

// Numeric aliases
typealias Tick = Long
typealias EntityId = Int
typealias PlayerId = Int
typealias TeamId = Int

// Vec types using proper TrikeShed patterns
data class V2(val x: Float, val y: Float) {
    operator fun plus(o: V2) = V2(x + o.x, y + o.y)
    operator fun minus(o: V2) = V2(x - o.x, y - o.y)
    operator fun times(s: Float) = V2(x * s, y * s)
    operator fun div(s: Float) = V2(x / s, y / s)
    fun dot(o: V2) = x * o.x + y * o.y
    fun len() = kotlin.math.sqrt(x * x + y * y)
    fun norm() = this / len()
    fun dist(o: V2) = (this - o).len()
}

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    operator fun div(s: Float) = V3(x / s, y / s, z / s)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun len() = kotlin.math.sqrt(x * x + y * y + z * z)
    fun norm() = this / len()
    fun dist(o: V3) = (this - o).len()
}

// Component marker
interface Comp

// Core components using short names
data class Pos(val v: V3) : Comp
data class Vel(val v: V3) : Comp
data class HP(val cur: Float, val max: Float) : Comp
data class Dmg(val v: Float) : Comp
data class Team(val id: TeamId) : Comp
data class Owner(val id: PlayerId) : Comp
data class Type(val t: String) : Comp
data class Rng(val v: Float) : Comp
data class Spd(val v: Float) : Comp
data class Vis(val r: Float) : Comp
data class Res(val t: String, val amt: Float) : Comp

// Entity as component map
typealias Ent = Map<String, Comp>
typealias MutEnt = MutableMap<String, Comp>

// World as entity map
typealias W = Map<EntityId, Ent>
typealias MutW = MutableMap<EntityId, MutEnt>

// ============================================================================
// Command Types - Terse
// ============================================================================

sealed class Cmd {
    abstract val t: Tick
    abstract val p: PlayerId
    
    data class Mv(
        override val t: Tick,
        override val p: PlayerId,
        val e: Indexed<EntityId>,
        val tgt: V3
    ) : Cmd()
    
    data class Atk(
        override val t: Tick,
        override val p: PlayerId,
        val a: Indexed<EntityId>,
        val tgt: EntityId
    ) : Cmd()
    
    data class Bld(
        override val t: Tick,
        override val p: PlayerId,
        val b: EntityId,
        val ut: String,
        val pos: V3
    ) : Cmd()
    
    data class Hrv(
        override val t: Tick,
        override val p: PlayerId,
        val h: Indexed<EntityId>,
        val r: EntityId
    ) : Cmd()
    
    data class Stp(
        override val t: Tick,
        override val p: PlayerId,
        val e: Indexed<EntityId>
    ) : Cmd()
}

// ============================================================================
// Network Packet Types - Terse
// ============================================================================

sealed class Pkt {
    abstract val seq: Int
    abstract val ts: Long
    
    data class Cmds(
        override val seq: Int,
        override val ts: Long,
        val c: Indexed<Cmd>
    ) : Pkt()
    
    data class Sync(
        override val seq: Int,
        override val ts: Long,
        val t: Tick,
        val h: Int,
        val d: Map<EntityId, EDelta>
    ) : Pkt()
    
    data class Jn(
        override val seq: Int,
        override val ts: Long,
        val p: PlayerId,
        val v: Int
    ) : Pkt()
    
    data class Lv(
        override val seq: Int,
        override val ts: Long,
        val p: PlayerId
    ) : Pkt()
}

// Entity delta
data class EDelta(
    val c: Boolean = false,  // created
    val d: Boolean = false,  // destroyed
    val cmp: Map<String, CDelta>
)

// Component delta
data class CDelta(
    val o: Comp?,  // old
    val n: Comp?   // new
)

// ============================================================================
// Player Info
// ============================================================================

data class PlayerInfo(
    val id: PlayerId,
    val name: String,
    val team: TeamId,
    val host: Boolean,
    val conn: Boolean
)

// ============================================================================
// Helper Functions - Terse
// ============================================================================

fun ent(vararg c: Pair<String, Comp>): Ent = c.toMap()

inline fun <reified T : Comp> Ent.g(k: String): T? = get(k) as? T

fun W.q(p: (Ent) -> Boolean): Series<Join<EntityId, Ent>> =
    asSequence().filter { (_, e) -> p(e) }.map { (id, e) -> id j e }

fun W.wc(c: String): Series<Join<EntityId, Ent>> =
    q { it.containsKey(c) }

// ============================================================================
// Context Extensions - CCEK Pattern
// ============================================================================

val CoroutineContext.tick: Tick?
    get() = this[GameTickCCEK]?.tick

val CoroutineContext.player: PlayerId?
    get() = this[PlayerCCEK]?.id

val CoroutineContext.codec: Int?
    get() = this[CodecCCEK]?.version

val CoroutineContext.session: SessionCCEK?
    get() = this[SessionCCEK]

// Context builders
suspend fun <T> withTick(t: Tick, block: suspend () -> T): T =
    kotlinx.coroutines.withContext(GameTickCCEK(t), block)

suspend fun <T> withPlayer(p: PlayerId, block: suspend () -> T): T =
    kotlinx.coroutines.withContext(PlayerCCEK(p), block)

suspend fun <T> withCodec(v: Int, c: Boolean, block: suspend () -> T): T =
    kotlinx.coroutines.withContext(CodecCCEK(v, c), block)