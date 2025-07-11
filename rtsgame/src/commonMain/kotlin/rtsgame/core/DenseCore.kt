package rtsgame.core

import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.*

/**
 * Ultra-dense RTS core using functional composition and type-level programming
 * 
 * Core philosophy: Everything is a transformation stream
 */

// Fundamental spacetime types
typealias Tick = Long
typealias Coord = Float
typealias Vec3 = Triple<Coord, Coord, Coord>
typealias Transform = (Vec3) -> Vec3

// Entity as pure data lens
typealias EntityId = Int
typealias Component = Any
typealias Entity = Map<String, Component>
typealias World = Map<EntityId, Entity>

// Commands as algebraic data types
sealed class Cmd {
    data class Move(val id: EntityId, val pos: Vec3) : Cmd()
    data class Attack(val from: EntityId, val to: EntityId) : Cmd()
    data class Build(val type: String, val pos: Vec3) : Cmd()
    data class Spawn(val type: String, val team: Int, val pos: Vec3) : Cmd()
}

// Effects as monadic transformations
typealias Effect<T> = suspend (World) -> Pair<World, T>
typealias System = suspend (World, Duration) -> World

// Dense component definitions using inline classes
@JvmInline value class Pos(val vec: Vec3)
@JvmInline value class Vel(val vec: Vec3)
@JvmInline value class HP(val value: Pair<Float, Float>) // current, max
@JvmInline value class Team(val id: Int)
@JvmInline value class Dmg(val value: Float)
@JvmInline value class Range(val value: Float)

// Component lens operators
inline operator fun <reified T> Entity.get(key: String): T? = this[key] as? T
inline operator fun Entity.plus(pair: Pair<String, Component>): Entity = this + pair
inline operator fun World.get(id: EntityId): Entity? = this[id]
inline operator fun World.plus(pair: Pair<EntityId, Entity>): World = this + pair

// Functional entity queries
inline fun <reified T> World.with(component: String): Sequence<Pair<EntityId, T>> =
    asSequence()
        .mapNotNull { (id, entity) -> 
            entity.get<T>(component)?.let { id to it }
        }

// Core game algebra
object Game {
    // Pure functional command interpreter
    val interpret: (Cmd) -> Effect<Unit> = { cmd ->
        { world ->
            when (cmd) {
                is Cmd.Move -> world.update(cmd.id) { 
                    it + ("pos" to Pos(cmd.pos))
                } to Unit
                
                is Cmd.Attack -> {
                    val damage = world[cmd.from]?.get<Dmg>("dmg")?.value ?: 0f
                    world.update(cmd.to) { target ->
                        val hp = target.get<HP>("hp") ?: HP(100f to 100f)
                        val newHp = hp.value.first - damage
                        if (newHp <= 0) target - "hp"
                        else target + ("hp" to HP(newHp to hp.value.second))
                    } to Unit
                }
                
                is Cmd.Build -> {
                    val newId = world.keys.maxOrNull()?.plus(1) ?: 0
                    val building = entityOf(
                        "type" to cmd.type,
                        "pos" to Pos(cmd.pos),
                        "hp" to HP(1000f to 1000f)
                    )
                    world + (newId to building) to Unit
                }
                
                is Cmd.Spawn -> {
                    val newId = world.keys.maxOrNull()?.plus(1) ?: 0
                    val unit = entityOf(
                        "type" to cmd.type,
                        "pos" to Pos(cmd.pos),
                        "team" to Team(cmd.team),
                        "hp" to HP(100f to 100f),
                        "dmg" to Dmg(10f),
                        "range" to Range(50f)
                    )
                    world + (newId to unit) to Unit
                }
            }
        }
    }
    
    // System combinators
    val movement: System = { world, dt ->
        world.with<Vel>("vel").fold(world) { w, (id, vel) ->
            w.update(id) { entity ->
                val pos = entity.get<Pos>("pos") ?: return@update entity
                entity + ("pos" to Pos(pos.vec + vel.vec * dt.inWholeMilliseconds / 1000f))
            }
        }
    }
    
    val combat: System = { world, dt ->
        world.with<Dmg>("dmg").fold(world) { w, (attackerId, dmg) ->
            val attacker = w[attackerId] ?: return@fold w
            val pos = attacker.get<Pos>("pos")?.vec ?: return@fold w
            val range = attacker.get<Range>("range")?.value ?: 50f
            val team = attacker.get<Team>("team")?.id ?: -1
            
            // Find nearest enemy
            val target = w.with<Team>("team")
                .filter { (_, t) -> t.id != team }
                .mapNotNull { (id, _) -> 
                    w[id]?.get<Pos>("pos")?.let { id to it.vec.dist(pos) }
                }
                .filter { (_, dist) -> dist <= range }
                .minByOrNull { (_, dist) -> dist }
                ?.first
            
            if (target != null) {
                w.update(target) { t ->
                    val hp = t.get<HP>("hp") ?: return@update t
                    val newHp = hp.value.first - dmg.value * dt.inWholeMilliseconds / 1000f
                    if (newHp <= 0) t - "hp"
                    else t + ("hp" to HP(newHp to hp.value.second))
                }
            } else w
        }
    }
    
    // Compose systems
    val systems = listOf(movement, combat)
    
    // Main game loop as a Flow
    fun gameLoop(
        initialWorld: World,
        commands: Flow<Cmd>,
        tickRate: Duration = 16.milliseconds
    ): Flow<World> = flow {
        var world = initialWorld
        val cmdChannel = commands.produceIn(this)
        
        while (currentCoroutineContext().isActive) {
            val startTime = System.currentTimeMillis()
            
            // Process all pending commands
            while (!cmdChannel.isEmpty) {
                val cmd = cmdChannel.tryReceive().getOrNull() ?: break
                world = interpret(cmd)(world).first
            }
            
            // Run all systems
            world = systems.fold(world) { w, system ->
                system(w, tickRate)
            }
            
            emit(world)
            
            // Frame timing
            val elapsed = System.currentTimeMillis() - startTime
            delay((tickRate.inWholeMilliseconds - elapsed).coerceAtLeast(0))
        }
    }
}

// Extension utilities
fun entityOf(vararg components: Pair<String, Component>): Entity = mapOf(*components)

fun World.update(id: EntityId, transform: (Entity) -> Entity): World =
    this[id]?.let { this + (id to transform(it)) } ?: this

operator fun Vec3.plus(other: Vec3): Vec3 = 
    Triple(first + other.first, second + other.second, third + other.third)

operator fun Vec3.times(scalar: Float): Vec3 =
    Triple(first * scalar, second * scalar, third * scalar)

fun Vec3.dist(other: Vec3): Float {
    val dx = first - other.first
    val dy = second - other.second
    val dz = third - other.third
    return sqrt(dx * dx + dy * dy + dz * dz)
}

// Dense codec for network serialization
object DenseCodec {
    // Command encoding as varints + float arrays
    fun Cmd.encode(): ByteArray = when (this) {
        is Cmd.Move -> byteArrayOf(0) + id.toVarInt() + pos.toBytes()
        is Cmd.Attack -> byteArrayOf(1) + from.toVarInt() + to.toVarInt()
        is Cmd.Build -> byteArrayOf(2) + type.toBytes() + pos.toBytes()
        is Cmd.Spawn -> byteArrayOf(3) + type.toBytes() + team.toVarInt() + pos.toBytes()
    }
    
    fun ByteArray.decodeCmd(): Cmd? = try {
        var offset = 0
        when (this[offset++]) {
            0.toByte() -> {
                val (id, len1) = readVarInt(offset)
                offset += len1
                val pos = readVec3(offset)
                Cmd.Move(id, pos)
            }
            1.toByte() -> {
                val (from, len1) = readVarInt(offset)
                offset += len1
                val (to, _) = readVarInt(offset)
                Cmd.Attack(from, to)
            }
            2.toByte() -> {
                val (type, len1) = readString(offset)
                offset += len1
                val pos = readVec3(offset)
                Cmd.Build(type, pos)
            }
            3.toByte() -> {
                val (type, len1) = readString(offset)
                offset += len1
                val (team, len2) = readVarInt(offset)
                offset += len2
                val pos = readVec3(offset)
                Cmd.Spawn(type, team, pos)
            }
            else -> null
        }
    } catch (e: Exception) { null }
    
    // Efficient binary helpers
    private fun Int.toVarInt(): ByteArray {
        val bytes = mutableListOf<Byte>()
        var value = this
        while (value > 0x7F) {
            bytes.add((value and 0x7F or 0x80).toByte())
            value = value ushr 7
        }
        bytes.add(value.toByte())
        return bytes.toByteArray()
    }
    
    private fun ByteArray.readVarInt(offset: Int): Pair<Int, Int> {
        var value = 0
        var shift = 0
        var i = offset
        while (i < size && shift < 32) {
            val b = this[i++].toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return value to (i - offset)
    }
    
    private fun Vec3.toBytes(): ByteArray = 
        first.toBits().toBytes() + second.toBits().toBytes() + third.toBits().toBytes()
    
    private fun ByteArray.readVec3(offset: Int): Vec3 =
        Triple(
            Float.fromBits(readInt(offset)),
            Float.fromBits(readInt(offset + 4)),
            Float.fromBits(readInt(offset + 8))
        )
    
    private fun String.toBytes(): ByteArray = 
        encodeToByteArray().size.toVarInt() + encodeToByteArray()
    
    private fun ByteArray.readString(offset: Int): Pair<String, Int> {
        val (len, lenSize) = readVarInt(offset)
        val str = decodeToString(offset + lenSize, offset + lenSize + len)
        return str to (lenSize + len)
    }
    
    private fun Int.toBytes(): ByteArray = byteArrayOf(
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        this.toByte()
    )
    
    private fun ByteArray.readInt(offset: Int): Int =
        (this[offset].toInt() and 0xFF shl 24) or
        (this[offset + 1].toInt() and 0xFF shl 16) or
        (this[offset + 2].toInt() and 0xFF shl 8) or
        (this[offset + 3].toInt() and 0xFF)
}

// AI as pure functions
object AI {
    // Simple aggressive AI
    val aggressive: suspend (World, Team) -> List<Cmd> = { world, team ->
        world.with<Team>("team")
            .filter { (_, t) -> t.id == team.id }
            .mapNotNull { (id, _) ->
                val entity = world[id] ?: return@mapNotNull null
                val pos = entity.get<Pos>("pos")?.vec ?: return@mapNotNull null
                
                // Find nearest enemy
                val enemy = world.with<Team>("team")
                    .filter { (_, t) -> t.id != team.id }
                    .mapNotNull { (enemyId, _) ->
                        world[enemyId]?.get<Pos>("pos")?.let { 
                            enemyId to it.vec.dist(pos)
                        }
                    }
                    .minByOrNull { (_, dist) -> dist }
                    ?.first
                
                enemy?.let { target ->
                    val targetPos = world[target]?.get<Pos>("pos")?.vec
                    targetPos?.let { Cmd.Move(id, it) }
                }
            }
            .toList()
    }
    
    // Economic AI
    val economic: suspend (World, Team) -> List<Cmd> = { world, team ->
        val teamUnits = world.with<Team>("team").count { (_, t) -> t.id == team.id }
        if (teamUnits < 10) {
            val spawnPos = world.with<Team>("team")
                .filter { (_, t) -> t.id == team.id }
                .mapNotNull { (id, _) -> world[id]?.get<Pos>("pos")?.vec }
                .firstOrNull() ?: Vec3(0f, 0f, 0f)
            
            listOf(Cmd.Spawn("scout", team.id, spawnPos + Vec3(10f, 10f, 0f)))
        } else emptyList()
    }
}

// Example usage
suspend fun example() {
    val world = mapOf(
        0 to entityOf(
            "type" to "commander",
            "pos" to Pos(Vec3(0f, 0f, 0f)),
            "team" to Team(1),
            "hp" to HP(1000f to 1000f),
            "dmg" to Dmg(50f)
        )
    )
    
    val commands = channelFlow {
        // Player commands
        send(Cmd.Move(0, Vec3(100f, 100f, 0f)))
        
        // AI commands
        while (true) {
            AI.aggressive(world, Team(1)).forEach { send(it) }
            delay(1000)
        }
    }
    
    Game.gameLoop(world, commands).collect { newWorld ->
        println("Entities: ${newWorld.size}")
    }
}