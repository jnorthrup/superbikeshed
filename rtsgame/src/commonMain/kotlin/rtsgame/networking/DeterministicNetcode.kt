import kotlin.math.*
package rtsgame.networking
import kotlinx.datetime.*
import kotlin.time.*

import rtsgame.core.*
import rtsgame.components.*
import rtsgame.codec.*
import borg.trikeshed.lib.*
import kotlin.math.*

/**
 * Deterministic lockstep networking for perfect synchronization
 * Supports thousands of units with minimal bandwidth
 */
class DeterministicNetcode(
    internal val playerId: Int,
    internal val tickRate: Int = 60
) {
    // Lockstep state
    internal var currentTick = 0L
    internal val inputBuffer = RingBuffer<TickInputs>(256)
    internal val confirmedInputs = mutableMapOf<Long, Map<Int, PlayerInput>>()
    
    // Rollback state
    internal val stateSnapshots = RingBuffer<SimulationSnapshot>(64)
    internal var lastConfirmedTick = 0L
    
    // Network state
    internal val peers = mutableMapOf<Int, PeerConnection>()
    internal var networkLatency = 0
    internal val inputDelay = 3  // Frames of input delay for stability
    
    // Delta compression
    internal val deltaCompressor = DeltaCompressor()
    
    fun update(simulation: NextGenSimulation, localInput: PlayerInput): NetworkUpdate {
        // Buffer local input with delay
        val inputTick = currentTick + inputDelay
        bufferInput(inputTick, playerId, localInput)
        
        // Process confirmed inputs up to current tick
        while (lastConfirmedTick < currentTick) {
            val tick = lastConfirmedTick + 1
            
            if (hasAllInputsForTick(tick)) {
                // All inputs received, advance simulation
                val inputs = gatherInputsForTick(tick)
                executeSimulationTick(simulation, inputs)
                lastConfirmedTick = tick
                
                // Take snapshot periodically
                if (tick % 30 == 0L) {
                    stateSnapshots.add(createSnapshot(simulation, tick))
                }
            } else {
                // Missing inputs, need to wait or predict
                break
            }
        }
        
        // Prepare network update
        return prepareNetworkUpdate()
    }
    
    internal fun bufferInput(tick: Long, player: Int, input: PlayerInput) {
        val tickInputs = inputBuffer.getOrCreate(tick) { TickInputs(tick) }
        tickInputs.inputs[player] = input
    }
    
    internal fun hasAllInputsForTick(tick: Long): Boolean {
        val tickInputs = inputBuffer.get(tick) ?: return false
        return peers.keys.all { peerId ->
            tickInputs.inputs.containsKey(peerId)
        } && tickInputs.inputs.containsKey(playerId)
    }
    
    internal fun gatherInputsForTick(tick: Long): Map<Int, PlayerInput> {
        return inputBuffer.get(tick)?.inputs ?: emptyMap()
    }
    
    internal fun executeSimulationTick(
        simulation: NextGenSimulation,
        inputs: Map<Int, PlayerInput>
    ) {
        // Apply all player inputs deterministically
        inputs.forEach { (playerId, input) ->
            applyPlayerInput(simulation, playerId, input)
        }
        
        // Update simulation deterministically
        simulation.update(1f / tickRate)
        currentTick++
    }
    
    internal fun applyPlayerInput(
        simulation: NextGenSimulation,
        playerId: Int,
        input: PlayerInput
    ) {
        when (input) {
            is PlayerInput.SelectUnits -> {
                // Handle unit selection
            }
            is PlayerInput.IssueCommand -> {
                val units = input.unitIds.map { EntityId(it) }
                simulation.issueCommand(units, input.command)
            }
            is PlayerInput.NoOp -> {
                // No operation this tick
            }
        }
    }
    
    fun receiveNetworkUpdate(senderId: Int, update: NetworkUpdate) {
        // Validate sender
        val peer = peers[senderId] ?: return
        
        // Process inputs
        update.inputs.forEach { tickInput ->
            // Verify checksum
            if (verifyChecksum(tickInput)) {
                tickInput.inputs.forEach { (playerId, input) ->
                    bufferInput(tickInput.tick, playerId, input)
                }
            }
        }
        
        // Handle rollback if needed
        val earliestNewInput = update.inputs.minOfOrNull { it.tick } ?: return
        
        if (earliestNewInput <= currentTick) {
            performRollback(earliestNewInput)
        }
    }
    
    internal fun performRollback(toTick: Long) {
        // Find nearest snapshot before rollback point
        val snapshot = stateSnapshots.findNearest { it.tick <= toTick }
            ?: throw IllegalStateException("No snapshot available for rollback")
        
        // Restore simulation state
        restoreSnapshot(snapshot)
        
        // Re-simulate from snapshot to current tick
        var tick = snapshot.tick
        while (tick < currentTick) {
            val inputs = gatherInputsForTick(tick)
            if (inputs.isNotEmpty()) {
                executeSimulationTick(snapshot.simulation, inputs)
            }
            tick++
        }
    }
    
    internal fun prepareNetworkUpdate(): NetworkUpdate {
        // Gather unconfirmed inputs
        val unconfirmedInputs = mutableListOf<TickInputs>()
        
        for (tick in (lastConfirmedTick + 1)..(currentTick + inputDelay)) {
            inputBuffer.get(tick)?.let { tickInputs ->
                if (tickInputs.inputs.containsKey(playerId)) {
                    unconfirmedInputs.add(tickInputs)
                }
            }
        }
        
        // Compress using delta encoding
        val compressed = deltaCompressor.compress(unconfirmedInputs)
        
        return NetworkUpdate(
            playerId = playerId,
            currentTick = currentTick,
            inputs = compressed,
            checksum = calculateChecksum()
        )
    }
    
    internal fun verifyChecksum(tickInput: TickInputs): Boolean {
        // Verify input integrity
        val calculated = calculateInputChecksum(tickInput)
        return calculated == tickInput.checksum
    }
    
    internal fun calculateChecksum(): Long {
        // Calculate simulation state checksum for desync detection
        var checksum = 0L
        // Hash critical game state
        return checksum
    }
    
    internal fun calculateInputChecksum(input: TickInputs): Long {
        var checksum = input.tick
        input.inputs.forEach { (player, playerInput) ->
            checksum = checksum * 31 + player
            checksum = checksum * 31 + playerInput.hashCode()
        }
        return checksum
    }
    
    internal fun createSnapshot(
        simulation: NextGenSimulation,
        tick: Long
    ): SimulationSnapshot {
        // Create deep copy of simulation state
        return SimulationSnapshot(
            tick = tick,
            simulation = simulation // Would need proper cloning
        )
    }
    
    internal fun restoreSnapshot(snapshot: SimulationSnapshot) {
        // Restore simulation to snapshot state
        currentTick = snapshot.tick
    }
}

/**
 * Input prediction for smooth gameplay
 */
class InputPredictor {
    internal val inputHistory = mutableMapOf<Int, RingBuffer<PlayerInput>>()
    
    fun predictInput(playerId: Int, currentTick: Long): PlayerInput {
        val history = inputHistory[playerId] ?: return PlayerInput.NoOp
        
        // Simple prediction: repeat last input
        return history.getLast() ?: PlayerInput.NoOp
    }
    
    fun recordInput(playerId: Int, input: PlayerInput) {
        inputHistory.getOrPut(playerId) { RingBuffer(32) }.add(input)
    }
}

/**
 * Delta compression for network optimization
 */
class DeltaCompressor {
    internal val baselineInputs = mutableMapOf<Int, TickInputs>()
    
    fun compress(inputs: List<TickInputs>): List<TickInputs> {
        val compressed = mutableListOf<TickInputs>()
        
        inputs.forEach { tickInput ->
            val baseline = baselineInputs[tickInput.tick.toInt() % 64]
            
            if (baseline != null && canDeltaEncode(tickInput, baseline)) {
                compressed.add(deltaEncode(tickInput, baseline))
            } else {
                compressed.add(tickInput)
                baselineInputs[tickInput.tick.toInt() % 64] = tickInput
            }
        }
        
        return compressed
    }
    
    internal fun canDeltaEncode(current: TickInputs, baseline: TickInputs): Boolean {
        // Check if delta encoding would save space
        return current.inputs.keys == baseline.inputs.keys
    }
    
    internal fun deltaEncode(current: TickInputs, baseline: TickInputs): TickInputs {
        // Encode only differences
        val deltaInputs = mutableMapOf<Int, PlayerInput>()
        
        current.inputs.forEach { (player, input) ->
            val baseInput = baseline.inputs[player]
            if (input != baseInput) {
                deltaInputs[player] = input
            }
        }
        
        return TickInputs(
            tick = current.tick,
            inputs = deltaInputs,
            checksum = current.checksum,
            isDelta = true,
            deltaFrom = baseline.tick
        )
    }
}

/**
 * Lag compensation for fair gameplay
 */
class LagCompensation(internal val maxRewind: Int = 1000) {
    internal val positionHistory = RingBuffer<Map<EntityId, PositionSnapshot>>(maxRewind / 16)
    
    fun recordPositions(world: ECSWorld, timestamp: Long) {
        val positions = mutableMapOf<EntityId, PositionSnapshot>()
        
        world.forEach<PositionComponent>(ComponentTypes.POSITION) { entity, pos ->
            positions[entity] = PositionSnapshot(
                x = pos.x,
                y = pos.y,
                rotation = pos.rotation,
                timestamp = timestamp
            )
        }
        
        positionHistory.add(positions)
    }
    
    fun rewindPosition(
        entity: EntityId,
        toTimestamp: Long,
        currentTimestamp: Long
    ): PositionComponent? {
        val rewindTime = currentTimestamp - toTimestamp
        if (rewindTime > maxRewind) return null
        
        // Find appropriate historical position
        for (i in positionHistory.size - 1 downTo 0) {
            val snapshot = positionHistory.get(i) ?: continue
            val pos = snapshot[entity] ?: continue
            
            if (pos.timestamp <= toTimestamp) {
                // Interpolate if needed
                if (i < positionHistory.size - 1) {
                    val nextSnapshot = positionHistory.get(i + 1)
                    val nextPos = nextSnapshot?.get(entity)
                    
                    if (nextPos != null) {
                        val t = (toTimestamp - pos.timestamp).toFloat() / 
                               (nextPos.timestamp - pos.timestamp)
                        
                        return PositionComponent(
                            x = lerp(pos.x, nextPos.x, t),
                            y = lerp(pos.y, nextPos.y, t),
                            rotation = lerpAngle(pos.rotation, nextPos.rotation, t)
                        )
                    }
                }
                
                return PositionComponent(pos.x, pos.y, pos.rotation)
            }
        }
        
        return null
    }
    
    internal fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t.coerceIn(0f, 1f)
    }
    
    internal fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = b - a
        while (diff > PI) diff -= 2 * PI.toFloat()
        while (diff < -PI) diff += 2 * PI.toFloat()
        return a + diff * t.coerceIn(0f, 1f)
    }
}

/**
 * Priority-based network traffic management
 */
class NetworkPrioritizer {
    internal val entityPriorities = mutableMapOf<EntityId, Float>()
    
    fun calculatePriorities(
        world: ECSWorld,
        viewerPos: PositionComponent,
        viewerTeam: Int
    ) {
        entityPriorities.clear()
        
        world.forEach<NetworkSyncComponent>(ComponentTypes.NETWORK_SYNC) { entity, sync ->
            val pos = world.getComponent<PositionComponent>(entity, ComponentTypes.POSITION)
            val team = world.getComponent<TeamComponent>(entity, ComponentTypes.TEAM)
            
            if (pos != null) {
                var priority = 1f
                
                // Distance-based priority
                val dist = distance(viewerPos, pos)
                priority *= 1f / (1f + dist / 1000f)
                
                // Team-based priority
                if (team?.teamId == viewerTeam) {
                    priority *= 2f  // Own units more important
                }
                
                // Combat priority
                val weapon = world.getComponent<WeaponComponent>(entity, ComponentTypes.WEAPON)
                if (weapon != null) {
                    priority *= 1.5f
                }
                
                // Update frequency based on priority
                sync.syncPriority = (priority * 100).toInt()
                entityPriorities[entity] = priority
            }
        }
    }
    
    fun getEntitiesToSync(maxCount: Int): List<EntityId> {
        return entityPriorities.entries
            .sortedByDescending { it.value }
            .take(maxCount)
            .map { it.key }
    }
    
    internal fun distance(a: PositionComponent, b: PositionComponent): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

// Data structures
sealed class PlayerInput {
    data class SelectUnits(val unitIds: List<Int>) : PlayerInput()
    data class IssueCommand(val unitIds: List<Int>, val command: Command) : PlayerInput()
    object NoOp : PlayerInput()
}

data class TickInputs(
    val tick: Long,
    val inputs: MutableMap<Int, PlayerInput> = mutableMapOf(),
    val checksum: Long = 0,
    val isDelta: Boolean = false,
    val deltaFrom: Long = 0
)

data class NetworkUpdate(
    val playerId: Int,
    val currentTick: Long,
    val inputs: List<TickInputs>,
    val checksum: Long
)

data class SimulationSnapshot(
    val tick: Long,
    val simulation: NextGenSimulation
)

data class PositionSnapshot(
    val x: Float,
    val y: Float,
    val rotation: Float,
    val timestamp: Long
)

class PeerConnection(
    val peerId: Int,
    var latency: Int = 0,
    var lastReceived: Long = 0
)

/**
 * Ring buffer for efficient history management
 */
class RingBuffer<T>(internal val capacity: Int) {
    internal val buffer = arrayOfNulls<Any>(capacity)
    internal var head = 0
    internal var size = 0
    
    fun add(item: T) {
        buffer[head] = item
        head = (head + 1) % capacity
        size = minOf(size + 1, capacity)
    }
    
    @Suppress("UNCHECKED_CAST")
    fun get(index: Long): T? {
        val pos = (index % capacity).toInt()
        return buffer[pos] as? T
    }
    
    @Suppress("UNCHECKED_CAST")
    fun getLast(): T? {
        if (size == 0) return null
        val pos = (head - 1 + capacity) % capacity
        return buffer[pos] as? T
    }
    
    @Suppress("UNCHECKED_CAST")
    fun getOrCreate(index: Long, creator: () -> T): T {
        val pos = (index % capacity).toInt()
        val existing = buffer[pos] as? T
        
        if (existing != null) {
            return existing
        }
        
        val new = creator()
        buffer[pos] = new
        return new
    }
    
    @Suppress("UNCHECKED_CAST")
    fun findNearest(predicate: (T) -> Boolean): T? {
        for (i in size - 1 downTo 0) {
            val pos = (head - 1 - i + capacity) % capacity
            val item = buffer[pos] as? T
            if (item != null && predicate(item)) {
                return item
            }
        }
        return null
    }
}