package rtsgame.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.time.*

/**
 * Dense networking layer using delta compression and predictive rollback
 */

// Network types
typealias Frame = Long
typealias PlayerId = Int
typealias Snapshot = Pair<Frame, World>
typealias Delta = Map<EntityId, Entity?>

// Network messages
sealed class Net {
    data class Input(val player: PlayerId, val frame: Frame, val cmds: List<Cmd>) : Net()
    data class Sync(val frame: Frame, val delta: Delta, val hash: Int) : Net()
    data class Full(val frame: Frame, val world: World) : Net()
    data class Ack(val player: PlayerId, val frame: Frame) : Net()
}

// Deterministic lockstep with client prediction
class DenseNetworkEngine(
    val playerId: PlayerId,
    val inputDelay: Int = 3, // frames
    val syncRate: Int = 60   // frames between syncs
) {
    private val history = mutableMapOf<Frame, Snapshot>()
    private val inputs = mutableMapOf<PlayerId, MutableMap<Frame, List<Cmd>>>()
    private val confirmedFrame = MutableStateFlow(0L)
    
    // Client prediction with rollback
    suspend fun clientPredict(
        world: World,
        localCmds: Flow<Cmd>,
        network: Channel<Net>
    ): Flow<Pair<World, Boolean>> = flow {
        var currentFrame = 0L
        var predictedWorld = world
        var authoritative = world
        
        // Command buffer
        val cmdBuffer = mutableListOf<Cmd>()
        
        coroutineScope {
            // Collect local commands
            launch {
                localCmds.collect { cmd ->
                    cmdBuffer.add(cmd)
                }
            }
            
            // Network receive
            launch {
                for (msg in network) {
                    when (msg) {
                        is Net.Input -> {
                            inputs.getOrPut(msg.player) { mutableMapOf() }[msg.frame] = msg.cmds
                        }
                        is Net.Sync -> {
                            // Apply delta and check for misprediction
                            val syncWorld = applyDelta(authoritative, msg.delta)
                            if (syncWorld.hash() != msg.hash) {
                                // Desync detected, request full state
                                network.send(Net.Ack(playerId, -1))
                            } else {
                                authoritative = syncWorld
                                confirmedFrame.value = msg.frame
                                
                                // Rollback and replay if needed
                                if (needsRollback(predictedWorld, syncWorld, msg.frame)) {
                                    predictedWorld = rollback(syncWorld, msg.frame, currentFrame)
                                    emit(predictedWorld to true) // true = rollback occurred
                                    continue
                                }
                            }
                        }
                        is Net.Full -> {
                            authoritative = msg.world
                            predictedWorld = msg.world
                            confirmedFrame.value = msg.frame
                            history.clear()
                        }
                        else -> {}
                    }
                }
            }
            
            // Main prediction loop
            while (currentCoroutineContext().isActive) {
                val frameStart = System.currentTimeMillis()
                
                // Send local input
                if (cmdBuffer.isNotEmpty()) {
                    val futureFrame = currentFrame + inputDelay
                    inputs.getOrPut(playerId) { mutableMapOf() }[futureFrame] = cmdBuffer.toList()
                    network.send(Net.Input(playerId, futureFrame, cmdBuffer.toList()))
                    cmdBuffer.clear()
                }
                
                // Simulate frame
                val frameCmds = gatherInputs(currentFrame)
                predictedWorld = simulateFrame(predictedWorld, frameCmds, 16.milliseconds)
                
                // Store history for rollback
                history[currentFrame] = currentFrame to predictedWorld
                cleanHistory(currentFrame - 120) // Keep 2 seconds
                
                emit(predictedWorld to false)
                
                currentFrame++
                
                // Frame timing
                val elapsed = System.currentTimeMillis() - frameStart
                delay((16 - elapsed).coerceAtLeast(0))
            }
        }
    }
    
    // Server authoritative simulation
    suspend fun serverSimulate(
        world: World,
        players: Set<PlayerId>,
        network: Channel<Net>
    ): Flow<World> = flow {
        var currentFrame = 0L
        var currentWorld = world
        val lastSync = mutableMapOf<PlayerId, Frame>()
        
        while (currentCoroutineContext().isActive) {
            val frameStart = System.currentTimeMillis()
            
            // Gather all inputs for this frame
            val frameCmds = gatherInputs(currentFrame)
            currentWorld = simulateFrame(currentWorld, frameCmds, 16.milliseconds)
            
            // Send sync updates
            players.forEach { player ->
                val last = lastSync[player] ?: 0
                
                if (currentFrame - last >= syncRate) {
                    val delta = if (last > 0) {
                        computeDelta(history[last]?.second ?: world, currentWorld)
                    } else {
                        currentWorld.mapValues { (_, entity) -> entity }
                    }
                    
                    network.send(Net.Sync(currentFrame, delta, currentWorld.hash()))
                    lastSync[player] = currentFrame
                }
            }
            
            history[currentFrame] = currentFrame to currentWorld
            cleanHistory(currentFrame - 300) // Keep 5 seconds
            
            emit(currentWorld)
            
            currentFrame++
            
            // Frame timing
            val elapsed = System.currentTimeMillis() - frameStart
            delay((16 - elapsed).coerceAtLeast(0))
        }
    }
    
    // Helpers
    private fun gatherInputs(frame: Frame): List<Cmd> =
        inputs.values.flatMap { it[frame] ?: emptyList() }
    
    private suspend fun simulateFrame(world: World, cmds: List<Cmd>, dt: Duration): World {
        var w = world
        
        // Apply commands
        cmds.forEach { cmd ->
            w = Game.interpret(cmd)(w).first
        }
        
        // Run systems
        Game.systems.forEach { system ->
            w = system(w, dt)
        }
        
        return w
    }
    
    private fun needsRollback(predicted: World, authoritative: World, frame: Frame): Boolean =
        predicted.hash() != authoritative.hash()
    
    private suspend fun rollback(from: World, fromFrame: Frame, toFrame: Frame): World {
        var world = from
        
        for (frame in fromFrame + 1..toFrame) {
            val cmds = gatherInputs(frame)
            world = simulateFrame(world, cmds, 16.milliseconds)
        }
        
        return world
    }
    
    private fun computeDelta(old: World, new: World): Delta {
        val delta = mutableMapOf<EntityId, Entity?>()
        
        // Changed/new entities
        new.forEach { (id, entity) ->
            if (old[id] != entity) {
                delta[id] = entity
            }
        }
        
        // Removed entities
        old.keys.filter { it !in new }.forEach { id ->
            delta[id] = null
        }
        
        return delta
    }
    
    private fun applyDelta(world: World, delta: Delta): World =
        delta.entries.fold(world) { w, (id, entity) ->
            if (entity == null) w - id
            else w + (id to entity)
        }
    
    private fun cleanHistory(before: Frame) {
        history.keys.filter { it < before }.forEach { history.remove(it) }
    }
}

// Efficient world hashing for sync verification
fun World.hash(): Int {
    var hash = 0
    forEach { (id, entity) ->
        hash = hash * 31 + id
        entity.forEach { (key, value) ->
            hash = hash * 31 + key.hashCode()
            hash = hash * 31 + when (value) {
                is Pos -> value.vec.hashCode()
                is HP -> (value.value.first * 1000).toInt()
                is Team -> value.id
                else -> value.hashCode()
            }
        }
    }
    return hash
}

// Binary network protocol
object NetCodec {
    fun Net.encode(): ByteArray = when (this) {
        is Net.Input -> {
            byteArrayOf(0) + player.toVarInt() + frame.toVarInt() + 
            cmds.size.toVarInt() + cmds.flatMap { it.encode().toList() }.toByteArray()
        }
        is Net.Sync -> {
            byteArrayOf(1) + frame.toVarInt() + delta.encode() + hash.toBytes()
        }
        is Net.Full -> {
            byteArrayOf(2) + frame.toVarInt() + world.encode()
        }
        is Net.Ack -> {
            byteArrayOf(3) + player.toVarInt() + frame.toVarInt()
        }
    }
    
    private fun Delta.encode(): ByteArray {
        val bytes = mutableListOf<Byte>()
        bytes.addAll(size.toVarInt().toList())
        
        forEach { (id, entity) ->
            bytes.addAll(id.toVarInt().toList())
            if (entity == null) {
                bytes.add(0)
            } else {
                bytes.add(1)
                bytes.addAll(entity.encode().toList())
            }
        }
        
        return bytes.toByteArray()
    }
    
    private fun World.encode(): ByteArray {
        val bytes = mutableListOf<Byte>()
        bytes.addAll(size.toVarInt().toList())
        
        forEach { (id, entity) ->
            bytes.addAll(id.toVarInt().toList())
            bytes.addAll(entity.encode().toList())
        }
        
        return bytes.toByteArray()
    }
    
    private fun Entity.encode(): ByteArray {
        val bytes = mutableListOf<Byte>()
        bytes.addAll(size.toVarInt().toList())
        
        forEach { (key, value) ->
            bytes.addAll(key.encodeToByteArray().size.toVarInt().toList())
            bytes.addAll(key.encodeToByteArray().toList())
            
            when (value) {
                is Pos -> {
                    bytes.add(0)
                    bytes.addAll(value.vec.toBytes().toList())
                }
                is HP -> {
                    bytes.add(1)
                    bytes.addAll(value.value.first.toBits().toBytes().toList())
                    bytes.addAll(value.value.second.toBits().toBytes().toList())
                }
                is Team -> {
                    bytes.add(2)
                    bytes.addAll(value.id.toVarInt().toList())
                }
                is Dmg -> {
                    bytes.add(3)
                    bytes.addAll(value.value.toBits().toBytes().toList())
                }
                else -> {
                    bytes.add(255)
                    val str = value.toString()
                    bytes.addAll(str.encodeToByteArray().size.toVarInt().toList())
                    bytes.addAll(str.encodeToByteArray().toList())
                }
            }
        }
        
        return bytes.toByteArray()
    }
    
    private fun Int.toVarInt() = DenseCodec.run { toVarInt() }
    private fun Vec3.toBytes() = DenseCodec.run { toBytes() }
    private fun Int.toBytes() = DenseCodec.run { toBytes() }
}

// Usage example
suspend fun networkExample() {
    val network = Channel<Net>(Channel.UNLIMITED)
    val engine = DenseNetworkEngine(playerId = 1)
    
    val world = mapOf(
        0 to entityOf(
            "pos" to Pos(Vec3(0f, 0f, 0f)),
            "team" to Team(1),
            "hp" to HP(100f to 100f)
        )
    )
    
    val commands = flow {
        emit(Cmd.Move(0, Vec3(100f, 0f, 0f)))
    }
    
    // Client
    launch {
        engine.clientPredict(world, commands, network).collect { (w, rollback) ->
            if (rollback) println("Rollback!")
            println("Client world: ${w.size} entities")
        }
    }
    
    // Server
    launch {
        engine.serverSimulate(world, setOf(1), network).collect { w ->
            println("Server world: ${w.size} entities")
        }
    }
}