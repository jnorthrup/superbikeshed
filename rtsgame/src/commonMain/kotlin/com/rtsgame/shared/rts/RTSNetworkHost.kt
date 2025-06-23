package com.rtsgame.shared.rts

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.net.*
import borg.trikeshed.net.quic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import borg.trikeshed.ksp.TrikeShedDsl
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.map.Position
import com.rtsgame.shared.map.ResourceType
import kotlinx.coroutines.flow.*

/**
 * RTS Network Host - Deterministic simulation for real-time strategy games
 * Supports lockstep networking with rollback
 */
class RTSNetworkHost(
    private val scope: CoroutineScope,
    private val gameState: GameState,
    private val tickRate: Long = 50L // 20 ticks per second
) {
    private val _gameStateFlow = MutableStateFlow(gameState)
    val gameStateFlow: StateFlow<GameState> = _gameStateFlow.asStateFlow()
    
    private val _entityUpdates = Channel<Entity>()
    private val _resourceUpdates = Channel<Triple<Int, ResourceType, Int>>()
    private val _entityRemovals = Channel<String>()
    
    private var isRunning = false
    private var job: Job? = null
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        job = scope.launch {
            while (isActive) {
                processUpdates()
                delay(tickRate)
            }
        }
    }
    
    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }
    
    private suspend fun processUpdates() {
        var currentState = _gameStateFlow.value
        
        // Process entity updates
        while (!_entityUpdates.isEmpty) {
            val entity = _entityUpdates.tryReceive().getOrNull() ?: break
            currentState = currentState.updateEntity(entity)
        }
        
        // Process resource updates
        while (!_resourceUpdates.isEmpty) {
            val (playerId, resourceType, amount) = _resourceUpdates.tryReceive().getOrNull() ?: break
            currentState = currentState.updateResources(playerId, resourceType, amount)
        }
        
        // Process entity removals
        while (!_entityRemovals.isEmpty) {
            val entityId = _entityRemovals.tryReceive().getOrNull() ?: break
            currentState = currentState.removeEntity(entityId)
        }
        
        // Advance time
        currentState = currentState.advance()
        
        _gameStateFlow.value = currentState
    }
    
    suspend fun updateEntity(entity: Entity) {
        _entityUpdates.send(entity)
    }
    
    suspend fun updateResources(playerId: Int, resourceType: ResourceType, amount: Int) {
        _resourceUpdates.send(Triple(playerId, resourceType, amount))
    }
    
    suspend fun removeEntity(entityId: String) {
        _entityRemovals.send(entityId)
    }
    
    fun getEntity(id: String): Entity? = _gameStateFlow.value.getEntity(id)
    fun getResources(playerId: Int): Map<ResourceType, Int> = _gameStateFlow.value.getResources(playerId)
}

// Data classes

typealias PlayerId = String
typealias UnitId = Long

enum class UnitType {
    WORKER, SOLDIER, TANK, AIRCRAFT
}

@Serializable
data class RtsUnit(
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
    val units: Indexed<Join<UnitId, RtsUnit>>,
    val resources: Indexed<Join<PlayerId, Resources>>,
    val checksum: Long
)

@Serializable
data class StateUpdate(
    val tick: Long,
    val state: GameState,
    val confirmedTicks: Indexed<Join<PlayerId, Long>>
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

@TrikeShedDsl
class RTSConfig {
    var tickRate = 60
    var maxPlayers = 8
    var port = 7777
    var enableRollback = true
}

// Player state
@Serializable
data class PlayerState(
    val playerId: PlayerId,
    val units: MutableMap<Long, RtsUnit> = mutableMapOf()
)