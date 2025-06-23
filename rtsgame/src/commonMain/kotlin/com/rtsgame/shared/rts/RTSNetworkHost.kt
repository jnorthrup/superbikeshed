package com.rtsgame.shared.rts

import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.map.Position
import com.rtsgame.shared.map.ResourceType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.net.*
import borg.trikeshed.net.quic.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import borg.trikeshed.ksp.TrikeShedDsl

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
    
    private val commandChannel = Channel<GameCommand>(Channel.BUFFERED)
    private val tickJob = scope.launch {
        var currentTime = gameState.currentTime
        while (isActive) {
            delay(tickRate)
            currentTime++
            
            // Process all commands for this tick
            val commands = mutableListOf<GameCommand>()
            while (!commandChannel.isEmpty) {
                commands.add(commandChannel.receive())
            }
            
            // Apply commands in deterministic order
            var currentState = _gameStateFlow.value
            commands.sortedBy { it.timestamp }.forEach { command ->
                currentState = when (command) {
                    is GameCommand.MoveEntity -> {
                        val entity = currentState.entities[command.entityId]
                        if (entity != null) {
                            currentState.updateEntity(command.entityId, entity.move(command.targetPosition))
                        } else currentState
                    }
                    is GameCommand.UpdateResources -> {
                        currentState.updateResources(command.playerId, command.resourceType, command.amount)
                    }
                }
            }
            
            _gameStateFlow.value = currentState.copy(currentTime = currentTime)
        }
    }
    
    suspend fun sendCommand(command: GameCommand) {
        commandChannel.send(command)
    }
    
    fun stop() {
        tickJob.cancel()
    }
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
    abstract val timestamp: Long

    data class MoveEntity(
        override val timestamp: Long,
        val entityId: String,
        val targetPosition: Position
    ) : GameCommand()

    data class UpdateResources(
        override val timestamp: Long,
        val playerId: Int,
        val resourceType: ResourceType,
        val amount: Int
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