package borg.trikeshed.rts

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.net.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * RTS Network Host - Deterministic simulation for real-time strategy games
 * Supports lockstep networking with rollback
 */
class RTSNetworkHost(
    private val tickRate: Int = 60,
    private val maxPlayers: Int = 8,
    private val port: Int = 7777,
    private val enableRollback: Boolean = true
) {
    // Game state
    private var currentTick = 0L
    private val gameStates = mutableMapOf<Long, GameState>()
    private val inputBuffer = mutableMapOf<PlayerId, MutableMap<Long, PlayerInput>>()
    
    // Network state
    private val players = mutableMapOf<PlayerId, PlayerConnection>()
    private val confirmedTick = mutableMapOf<PlayerId, Long>()
    private var hostTick = 0L
    
    // Deterministic random
    private var randomSeed = 12345L
    private val random = DeterministicRandom(randomSeed)
    
    // Command queue for deterministic execution
    private val commandQueue = Channel<GameCommand>(Channel.UNLIMITED)
    
    // QUIC server
    private val c10kServer = C10KServer(
        port = port,
        staticRoot = "/rts/static",
        enableQuic = true,
        deterministicMode = true
    )
    
    /**
     * Start the RTS host
     */
    suspend fun start() = coroutineScope {
        println("RTS Network Host starting on port $port")
        println("Tick rate: $tickRate, Max players: $maxPlayers")
        println("Rollback enabled: $enableRollback")
        
        // Initialize game state
        gameStates[0] = createInitialState()
        
        // Start network server
        launch { c10kServer.start() }
        
        // Start game loop
        launch { gameLoop() }
        
        // Start input processor
        launch { inputProcessor() }
        
        // Start state broadcaster
        launch { stateBroadcaster() }
    }
    
    /**
     * Main game loop - deterministic simulation
     */
    private suspend fun gameLoop() {
        val tickInterval = 1000L / tickRate
        
        while (isActive) {
            val startTime = System.currentTimeMillis()
            
            // Get current state
            val currentState = gameStates[currentTick] ?: createInitialState()
            
            // Collect inputs for this tick
            val inputs = collectInputsForTick(currentTick)
            
            // Simulate tick
            val nextState = simulateTick(currentState, inputs, currentTick)
            
            // Store state
            gameStates[currentTick + 1] = nextState
            currentTick++
            
            // Cleanup old states
            cleanupOldStates()
            
            // Sleep for remaining time
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < tickInterval) {
                delay(tickInterval - elapsed)
            } else {
                println("Warning: Tick ${currentTick} took ${elapsed}ms (target: ${tickInterval}ms)")
            }
        }
    }
    
    /**
     * Simulate one game tick deterministically
     */
    private fun simulateTick(
        state: GameState,
        inputs: Indexed<PlayerInput>,
        tick: Long
    ): GameState {
        val newUnits = mutableMapOf<UnitId, Unit>()
        val newResources = mutableMapOf<PlayerId, Resources>()
        
        // Copy existing state
        state.units.forEach { (id, unit) ->
            newUnits[id] = unit.copy()
        }
        state.resources.forEach { (id, res) ->
            newResources[id] = res.copy()
        }
        
        // Process player inputs
        for (i in 0 until inputs.a) {
            val input = inputs.b(i)
            processPlayerInput(input, newUnits, newResources)
        }
        
        // Update unit positions
        newUnits.values.forEach { unit ->
            updateUnit(unit, newUnits)
        }
        
        // Check collisions
        checkCollisions(newUnits)
        
        // Update resources
        newResources.forEach { (playerId, resources) ->
            newResources[playerId] = resources.copy(
                minerals = resources.minerals + 1,
                gas = resources.gas
            )
        }
        
        return GameState(
            tick = tick + 1,
            units = newUnits,
            resources = newResources,
            checksum = calculateChecksum(newUnits, newResources)
        )
    }
    
    /**
     * Process player input
     */
    private fun processPlayerInput(
        input: PlayerInput,
        units: MutableMap<UnitId, Unit>,
        resources: MutableMap<PlayerId, Resources>
    ) {
        when (input) {
            is PlayerInput.Move -> {
                units[input.unitId]?.let { unit ->
                    if (unit.owner == input.playerId) {
                        units[input.unitId] = unit.copy(
                            targetX = input.targetX,
                            targetY = input.targetY
                        )
                    }
                }
            }
            is PlayerInput.Attack -> {
                units[input.unitId]?.let { unit ->
                    if (unit.owner == input.playerId) {
                        units[input.unitId] = unit.copy(
                            targetUnit = input.targetId
                        )
                    }
                }
            }
            is PlayerInput.Build -> {
                val playerResources = resources[input.playerId] ?: return
                val cost = getUnitCost(input.unitType)
                
                if (playerResources.minerals >= cost) {
                    // Create new unit
                    val newUnit = Unit(
                        id = generateUnitId(),
                        type = input.unitType,
                        owner = input.playerId,
                        x = input.x,
                        y = input.y,
                        health = 100,
                        maxHealth = 100
                    )
                    units[newUnit.id] = newUnit
                    
                    // Deduct resources
                    resources[input.playerId] = playerResources.copy(
                        minerals = playerResources.minerals - cost
                    )
                }
            }
        }
    }
    
    /**
     * Update unit position and state
     */
    private fun updateUnit(unit: Unit, allUnits: Map<UnitId, Unit>) {
        // Move towards target
        if (unit.targetX != null && unit.targetY != null) {
            val dx = unit.targetX - unit.x
            val dy = unit.targetY - unit.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            
            if (distance > 1.0) {
                val speed = 2.0
                unit.x += (dx / distance * speed).toInt()
                unit.y += (dy / distance * speed).toInt()
            } else {
                unit.targetX = null
                unit.targetY = null
            }
        }
        
        // Attack target
        unit.targetUnit?.let { targetId ->
            allUnits[targetId]?.let { target ->
                val dx = target.x - unit.x
                val dy = target.y - unit.y
                val distance = kotlin.math.sqrt(dx.toDouble() * dx + dy * dy)
                
                if (distance < 50.0) { // Attack range
                    target.health -= 10
                    if (target.health <= 0) {
                        unit.targetUnit = null
                    }
                }
            }
        }
    }
    
    /**
     * Check unit collisions
     */
    private fun checkCollisions(units: MutableMap<UnitId, Unit>) {
        val toRemove = mutableListOf<UnitId>()
        
        units.forEach { (id, unit) ->
            if (unit.health <= 0) {
                toRemove.add(id)
            }
        }
        
        toRemove.forEach { units.remove(it) }
    }
    
    /**
     * Collect inputs for current tick
     */
    private fun collectInputsForTick(tick: Long): Indexed<PlayerInput> {
        val inputs = mutableListOf<PlayerInput>()
        
        inputBuffer.forEach { (playerId, playerInputs) ->
            playerInputs[tick]?.let { inputs.add(it) }
        }
        
        return inputs.size j { inputs[it] }
    }
    
    /**
     * Input processor - receives inputs from network
     */
    private suspend fun inputProcessor() {
        while (isActive) {
            val command = commandQueue.tryReceive().getOrNull()
            if (command != null) {
                when (command) {
                    is GameCommand.Input -> {
                        // Store input for future tick
                        val playerInputs = inputBuffer.getOrPut(command.playerId) { mutableMapOf() }
                        playerInputs[command.tick] = command.input
                        
                        // Update confirmed tick
                        confirmedTick[command.playerId] = command.tick
                        
                        // Check if we need to rollback
                        if (enableRollback && command.tick < currentTick) {
                            rollbackToTick(command.tick)
                        }
                    }
                    is GameCommand.Join -> {
                        handlePlayerJoin(command.playerId, command.connection)
                    }
                    is GameCommand.Leave -> {
                        handlePlayerLeave(command.playerId)
                    }
                }
            } else {
                delay(1)
            }
        }
    }
    
    /**
     * Rollback to specific tick and resimulate
     */
    private suspend fun rollbackToTick(tick: Long) {
        if (tick >= currentTick) return
        
        println("Rolling back from tick $currentTick to $tick")
        
        // Get state at rollback point
        val rollbackState = gameStates[tick] ?: return
        
        // Resimulate from rollback point
        var simTick = tick
        var state = rollbackState
        
        while (simTick < currentTick) {
            val inputs = collectInputsForTick(simTick)
            state = simulateTick(state, inputs, simTick)
            gameStates[simTick + 1] = state
            simTick++
        }
    }
    
    /**
     * Broadcast game state to players
     */
    private suspend fun stateBroadcaster() {
        while (isActive) {
            val state = gameStates[currentTick] ?: continue
            
            // Create state update
            val update = StateUpdate(
                tick = currentTick,
                state = state,
                confirmedTicks = confirmedTick.toMap()
            )
            
            // Broadcast to all players
            players.values.forEach { player ->
                launch {
                    player.sendStateUpdate(update)
                }
            }
            
            delay(50) // 20 Hz state updates
        }
    }
    
    /**
     * Handle player join
     */
    private fun handlePlayerJoin(playerId: PlayerId, connection: PlayerConnection) {
        players[playerId] = connection
        inputBuffer[playerId] = mutableMapOf()
        confirmedTick[playerId] = 0L
        
        // Initialize player resources
        gameStates[currentTick]?.let { state ->
            state.resources[playerId] = Resources(1000, 0)
        }
        
        println("Player $playerId joined")
    }
    
    /**
     * Handle player leave
     */
    private fun handlePlayerLeave(playerId: PlayerId) {
        players.remove(playerId)
        inputBuffer.remove(playerId)
        confirmedTick.remove(playerId)
        
        println("Player $playerId left")
    }
    
    /**
     * Cleanup old states
     */
    private fun cleanupOldStates() {
        // Find oldest confirmed tick
        val oldestConfirmed = confirmedTick.values.minOrNull() ?: 0L
        val keepFrom = oldestConfirmed - 100 // Keep 100 ticks for safety
        
        // Remove old states
        gameStates.keys.filter { it < keepFrom }.forEach {
            gameStates.remove(it)
        }
        
        // Remove old inputs
        inputBuffer.values.forEach { playerInputs ->
            playerInputs.keys.filter { it < keepFrom }.forEach {
                playerInputs.remove(it)
            }
        }
    }
    
    // Helper functions
    
    private fun createInitialState(): GameState {
        return GameState(
            tick = 0,
            units = mutableMapOf(),
            resources = mutableMapOf(),
            checksum = 0
        )
    }
    
    private fun calculateChecksum(units: Map<UnitId, Unit>, resources: Map<PlayerId, Resources>): Long {
        var checksum = 0L
        
        units.values.sortedBy { it.id }.forEach { unit ->
            checksum = checksum * 31 + unit.hashCode()
        }
        
        resources.entries.sortedBy { it.key }.forEach { (_, res) ->
            checksum = checksum * 31 + res.hashCode()
        }
        
        return checksum
    }
    
    private fun getUnitCost(type: UnitType): Int = when (type) {
        UnitType.WORKER -> 50
        UnitType.SOLDIER -> 100
        UnitType.TANK -> 300
        UnitType.AIRCRAFT -> 400
    }
    
    private var nextUnitId = 1L
    private fun generateUnitId(): UnitId = nextUnitId++
}

// Data classes

typealias PlayerId = String
typealias UnitId = Long

enum class UnitType {
    WORKER, SOLDIER, TANK, AIRCRAFT
}

@Serializable
data class Unit(
    val id: UnitId,
    val type: UnitType,
    val owner: PlayerId,
    var x: Int,
    var y: Int,
    var health: Int,
    val maxHealth: Int,
    var targetX: Int? = null,
    var targetY: Int? = null,
    var targetUnit: UnitId? = null
)

@Serializable
data class Resources(
    val minerals: Int,
    val gas: Int
)

@Serializable
data class GameState(
    val tick: Long,
    val units: Map<UnitId, Unit>,
    val resources: Map<PlayerId, Resources>,
    val checksum: Long
)

@Serializable
data class StateUpdate(
    val tick: Long,
    val state: GameState,
    val confirmedTicks: Map<PlayerId, Long>
)

// Player input types
@Serializable
sealed class PlayerInput {
    abstract val playerId: PlayerId
    abstract val tick: Long
    
    @Serializable
    data class Move(
        override val playerId: PlayerId,
        override val tick: Long,
        val unitId: UnitId,
        val targetX: Int,
        val targetY: Int
    ) : PlayerInput()
    
    @Serializable
    data class Attack(
        override val playerId: PlayerId,
        override val tick: Long,
        val unitId: UnitId,
        val targetId: UnitId
    ) : PlayerInput()
    
    @Serializable
    data class Build(
        override val playerId: PlayerId,
        override val tick: Long,
        val unitType: UnitType,
        val x: Int,
        val y: Int
    ) : PlayerInput()
}

// Game commands
sealed class GameCommand {
    data class Input(
        val playerId: PlayerId,
        val tick: Long,
        val input: PlayerInput
    ) : GameCommand()
    
    data class Join(
        val playerId: PlayerId,
        val connection: PlayerConnection
    ) : GameCommand()
    
    data class Leave(
        val playerId: PlayerId
    ) : GameCommand()
}

// Player connection
data class PlayerConnection(
    val playerId: PlayerId,
    private val sendChannel: SendChannel<StateUpdate>
) {
    suspend fun sendStateUpdate(update: StateUpdate) {
        sendChannel.send(update)
    }
}

// Deterministic random
class DeterministicRandom(private var seed: Long) {
    fun nextInt(bound: Int): Int {
        seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFF
        return (seed % bound).toInt()
    }
    
    fun nextFloat(): Float {
        return nextInt(1000000) / 1000000f
    }
}