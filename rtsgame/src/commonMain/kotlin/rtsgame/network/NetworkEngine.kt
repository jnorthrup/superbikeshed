package rtsgame.network

import rtsgame.core.*
import rtsgame.codec.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * Network engine with deterministic lockstep, client prediction, and rollback
 * Uses CCEK pattern for context propagation
 */

// ============================================================================
// Network Context Keys
// ============================================================================

data class NetworkSessionContext(
    val sessionId: String,
    val players: Map<PlayerId, PlayerInfo>,
    val startTick: GameTick
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NetworkSessionContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

data class NetworkStatsContext(
    val rtt: Long,
    val jitter: Long,
    val packetLoss: Float,
    val bandwidth: Long
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NetworkStatsContext>
    override val key: CoroutineContext.Key<*> get() = Key
}

data class PlayerInfo(
    val id: PlayerId,
    val name: String,
    val team: TeamId,
    val isHost: Boolean,
    val connected: Boolean
)

// ============================================================================
// Network Engine
// ============================================================================

class NetworkEngine(
    private val playerId: PlayerId,
    private val isHost: Boolean,
    private val config: NetworkConfig = NetworkConfig()
) {
    data class NetworkConfig(
        val tickRate: Int = 60,
        val syncRate: Int = 20,
        val inputDelay: Int = 3,
        val maxRollback: Int = 10,
        val timeout: Long = 5000
    )
    
    // State management
    private val confirmedStates = mutableMapOf<GameTick, World>()
    private val predictedStates = mutableMapOf<GameTick, World>()
    private val pendingInputs = mutableMapOf<GameTick, MutableList<Command>>()
    private val acknowledgedInputs = mutableSetOf<PacketSequence>()
    
    // Network state
    private var sequence = 0
    private var lastReceivedSequence = -1
    private var currentTick = 0L
    
    // ========================================================================
    // Server-side simulation
    // ========================================================================
    
    suspend fun serverSimulate(
        initialWorld: World,
        players: Set<PlayerId>,
        network: Channel<Packet>
    ): Flow<World> = flow {
        var world = initialWorld
        var tick = 0L
        
        // Create session context
        val session = NetworkSessionContext(
            sessionId = generateSessionId(),
            players = players.associateWith { pid ->
                PlayerInfo(pid, "Player$pid", pid % 4, pid == 0, true)
            },
            startTick = tick
        )
        
        withContext(session) {
            while (currentCoroutineContext().isActive) {
                // Collect inputs for this tick
                val tickInputs = collectInputsForTick(tick, network)
                
                // Simulate tick with all inputs
                world = simulateTick(world, tick, tickInputs)
                confirmedStates[tick] = world
                
                // Broadcast state sync
                if (tick % (config.tickRate / config.syncRate) == 0L) {
                    broadcastStateSync(tick, world, network)
                }
                
                // Clean old states
                cleanOldStates(tick)
                
                emit(world)
                tick++
                
                // Tick timing
                delay(1000L / config.tickRate)
            }
        }
    }
    
    // ========================================================================
    // Client-side prediction
    // ========================================================================
    
    suspend fun clientPredict(
        initialWorld: World,
        localCommands: Flow<Command>,
        network: Channel<Packet>
    ): Flow<Pair<World, Boolean>> = flow {
        var confirmedWorld = initialWorld
        var predictedWorld = initialWorld
        var lastConfirmedTick = 0L
        var rollback = false
        
        // Command buffer for local inputs
        val commandBuffer = Channel<Command>(Channel.UNLIMITED)
        
        // Collect local commands
        launch {
            localCommands.collect { cmd ->
                val bufferedCmd = cmd.copy(tick = currentTick + config.inputDelay)
                commandBuffer.send(bufferedCmd)
                pendingInputs.getOrPut(bufferedCmd.tick) { mutableListOf() }.add(bufferedCmd)
            }
        }
        
        // Process network packets
        launch {
            for (packet in network) {
                when (packet) {
                    is Packet.StateSync -> {
                        // Server state update
                        if (packet.tick > lastConfirmedTick) {
                            confirmedWorld = DeltaCompressor.apply(confirmedWorld, packet.entityDeltas)
                            confirmedStates[packet.tick] = confirmedWorld
                            lastConfirmedTick = packet.tick
                            
                            // Check for misprediction
                            val predictedHash = predictedStates[packet.tick]?.hash() ?: 0
                            if (predictedHash != packet.worldHash) {
                                // Rollback and replay
                                rollback = true
                                predictedWorld = rollbackAndReplay(
                                    confirmedWorld,
                                    packet.tick,
                                    currentTick
                                )
                            }
                        }
                    }
                    is Packet.Commands -> {
                        // Other player commands
                        packet.commands.forEach { cmd ->
                            pendingInputs.getOrPut(cmd.tick) { mutableListOf() }.add(cmd)
                        }
                    }
                    else -> {}
                }
            }
        }
        
        // Main prediction loop
        while (currentCoroutineContext().isActive) {
            // Get inputs for current tick
            val inputs = pendingInputs[currentTick] ?: emptyList()
            
            // Add local command if buffered
            commandBuffer.tryReceive().getOrNull()?.let { localCmd ->
                inputs.toMutableList().add(localCmd)
                sendCommand(localCmd, network)
            }
            
            // Predict next state
            predictedWorld = simulateTick(predictedWorld, currentTick, inputs)
            predictedStates[currentTick] = predictedWorld
            
            emit(predictedWorld to rollback)
            rollback = false
            currentTick++
            
            delay(1000L / config.tickRate)
        }
    }
    
    // ========================================================================
    // Rollback and replay
    // ========================================================================
    
    private suspend fun rollbackAndReplay(
        confirmedWorld: World,
        confirmedTick: GameTick,
        currentTick: GameTick
    ): World {
        var world = confirmedWorld
        
        // Replay from confirmed tick to current
        for (tick in (confirmedTick + 1)..currentTick) {
            val inputs = pendingInputs[tick] ?: emptyList()
            world = simulateTick(world, tick, inputs)
            predictedStates[tick] = world
        }
        
        return world
    }
    
    // ========================================================================
    // Simulation
    // ========================================================================
    
    private suspend fun simulateTick(
        world: World,
        tick: GameTick,
        commands: List<Command>
    ): World = withTick(tick) {
        var newWorld = world
        
        // Apply commands
        commands.forEach { cmd ->
            newWorld = applyCommand(newWorld, cmd)
        }
        
        // Run systems
        newWorld = MovementSystem.update(newWorld, tick)
        newWorld = CombatSystem.update(newWorld, tick)
        newWorld = ResourceSystem.update(newWorld, tick)
        
        newWorld
    }
    
    private fun applyCommand(world: World, command: Command): World {
        return when (command) {
            is Command.Move -> applyMoveCommand(world, command)
            is Command.Attack -> applyAttackCommand(world, command)
            is Command.Build -> applyBuildCommand(world, command)
            is Command.Harvest -> applyHarvestCommand(world, command)
            is Command.Stop -> applyStopCommand(world, command)
        }
    }
    
    // ========================================================================
    // Command application
    // ========================================================================
    
    private fun applyMoveCommand(world: World, cmd: Command.Move): World = 
        world.toMutableMap().apply {
            cmd.entities.forEach { entityId ->
                this[entityId]?.let { entity ->
                    this[entityId] = entity + ("target" to Position(cmd.target))
                }
            }
        }
    
    private fun applyAttackCommand(world: World, cmd: Command.Attack): World =
        world.toMutableMap().apply {
            cmd.attackers.forEach { attackerId ->
                this[attackerId]?.let { entity ->
                    this[attackerId] = entity + ("attackTarget" to Target(cmd.target))
                }
            }
        }
    
    private fun applyBuildCommand(world: World, cmd: Command.Build): World {
        val newId = world.keys.maxOrNull()?.plus(1) ?: 0
        return world + (newId to entityOf(
            "type" to UnitType(cmd.unitType),
            "position" to Position(cmd.position),
            "owner" to Owner(cmd.playerId),
            "health" to Health(100f, 100f),
            "building" to Building(progress = 0f)
        ))
    }
    
    private fun applyHarvestCommand(world: World, cmd: Command.Harvest): World =
        world.toMutableMap().apply {
            cmd.harvesters.forEach { harvesterId ->
                this[harvesterId]?.let { entity ->
                    this[harvesterId] = entity + ("harvestTarget" to Target(cmd.resourceId))
                }
            }
        }
    
    private fun applyStopCommand(world: World, cmd: Command.Stop): World =
        world.toMutableMap().apply {
            cmd.entities.forEach { entityId ->
                this[entityId]?.let { entity ->
                    val updated = entity.toMutableMap()
                    updated.remove("target")
                    updated.remove("attackTarget")
                    updated.remove("harvestTarget")
                    this[entityId] = updated
                }
            }
        }
    
    // ========================================================================
    // Network communication
    // ========================================================================
    
    private suspend fun sendCommand(command: Command, network: Channel<Packet>) {
        val packet = Packet.Commands(
            sequence = nextSequence(),
            timestamp = System.currentTimeMillis(),
            commands = listOf(command)
        )
        network.send(packet)
    }
    
    private suspend fun broadcastStateSync(tick: GameTick, world: World, network: Channel<Packet>) {
        val lastState = confirmedStates[tick - 1] ?: emptyMap()
        val deltas = DeltaCompressor.compress(lastState, world)
        
        val packet = Packet.StateSync(
            sequence = nextSequence(),
            timestamp = System.currentTimeMillis(),
            tick = tick,
            worldHash = world.hash(),
            entityDeltas = deltas
        )
        
        network.send(packet)
    }
    
    private suspend fun collectInputsForTick(tick: GameTick, network: Channel<Packet>): List<Command> {
        val inputs = mutableListOf<Command>()
        val deadline = System.currentTimeMillis() + (1000L / config.tickRate)
        
        while (System.currentTimeMillis() < deadline) {
            network.tryReceive().getOrNull()?.let { packet ->
                if (packet is Packet.Commands) {
                    inputs.addAll(packet.commands.filter { it.tick == tick })
                }
            }
        }
        
        return inputs
    }
    
    // ========================================================================
    // Utility functions
    // ========================================================================
    
    private fun nextSequence(): PacketSequence = sequence++
    
    private fun generateSessionId(): String = 
        "session_${System.currentTimeMillis()}_${(0..1000).random()}"
    
    private fun cleanOldStates(currentTick: GameTick) {
        val keepAfter = currentTick - config.maxRollback - 10
        confirmedStates.keys.removeAll { it < keepAfter }
        predictedStates.keys.removeAll { it < keepAfter }
        pendingInputs.keys.removeAll { it < keepAfter }
    }
    
    private fun World.hash(): Hash {
        var hash = 0
        forEach { (id, entity) ->
            hash = hash * 31 + id
            entity.forEach { (key, _) ->
                hash = hash * 31 + key.hashCode()
            }
        }
        return hash
    }
}

// ============================================================================
// Additional Components
// ============================================================================

data class Target(val entityId: EntityId) : Component
data class Building(val progress: Float) : Component

// ============================================================================
// Network Systems
// ============================================================================

object MovementSystem {
    suspend fun update(world: World, tick: GameTick): World = world.mapValues { (_, entity) ->
        entity.get<Position>("position")?.let { pos ->
            entity.get<Position>("target")?.let { target ->
                val speed = entity.get<Speed>("speed")?.value ?: 1f
                val direction = (target.vec - pos.vec).normalize()
                val distance = pos.vec.dist(target.vec)
                
                if (distance > speed) {
                    // Move towards target
                    val newPos = pos.vec + (direction * speed)
                    entity + ("position" to Position(newPos))
                } else {
                    // Reached target
                    entity.toMutableMap().apply {
                        put("position", Position(target.vec))
                        remove("target")
                    }
                }
            } ?: entity
        } ?: entity
    }
}

object CombatSystem {
    suspend fun update(world: World, tick: GameTick): World = world
}

object ResourceSystem {
    suspend fun update(world: World, tick: GameTick): World = world
}

// ============================================================================
// Context Extensions
// ============================================================================

val CoroutineContext.networkSession: NetworkSessionContext?
    get() = this[NetworkSessionContext]

val CoroutineContext.networkStats: NetworkStatsContext?
    get() = this[NetworkStatsContext]

suspend fun <T> withNetworkSession(
    sessionId: String,
    players: Map<PlayerId, PlayerInfo>,
    startTick: GameTick,
    block: suspend CoroutineScope.() -> T
): T = withContext(NetworkSessionContext(sessionId, players, startTick), block)

suspend fun <T> withNetworkStats(
    rtt: Long,
    jitter: Long,
    packetLoss: Float,
    bandwidth: Long,
    block: suspend CoroutineScope.() -> T
): T = withContext(NetworkStatsContext(rtt, jitter, packetLoss, bandwidth), block)

// Helper to copy command with new tick
private fun Command.copy(tick: GameTick): Command = when (this) {
    is Command.Move -> copy(tick = tick)
    is Command.Attack -> copy(tick = tick)
    is Command.Build -> copy(tick = tick)
    is Command.Harvest -> copy(tick = tick)
    is Command.Stop -> copy(tick = tick)
}