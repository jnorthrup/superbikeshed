package com.rtsgame.shared.client

import com.rtsgame.shared.entity.EntityManager
import com.rtsgame.shared.systems.CommandAndControlSystem
import com.rtsgame.shared.systems.ProofOfWorkSystem
import com.rtsgame.shared.systems.ComputroniumSystem
import com.rtsgame.shared.network.NetworkPhysics
import com.rtsgame.shared.network.CacheCoherence
import kotlin.math.*

/**
 * Move Client Interface - Phase 3 Integration
 * 
 * Key Features:
 * - Command Translation: Player actions to codec-compatible moves
 * - State Synchronization: Client-server state reconciliation
 * - Prediction Engine: Client-side move prediction
 * - SpaceGraph Bridge: Graphics integration interface
 */
class MoveClient(
    internal val entityManager: EntityManager,
    internal val commandSystem: CommandAndControlSystem,
    internal val proofOfWorkSystem: ProofOfWorkSystem,
    internal val computroniumSystem: ComputroniumSystem,
    internal val networkPhysics: NetworkPhysics,
    internal val cacheCoherence: CacheCoherence
) {
    
    // Player action types
    enum class PlayerActionType {
        MOVE_UNIT,
        ATTACK_TARGET,
        BUILD_STRUCTURE,
        RESEARCH_TECH,
        ALLOCATE_RESOURCES,
        FORMATION_COMMAND,
        BREACH_ATTEMPT,
        CONVERT_COMPUTRONIUM
    }
    
    // Player action
    data class PlayerAction(
        val id: Long,
        val playerId: Long,
        val actionType: PlayerActionType,
        val targetEntityId: Long?,
        val parameters: Map<String, Any>,
        val timestamp: Long,
        val priority: Int = 0
    )
    
    // Move command (codec-compatible)
    data class MoveCommand(
        val id: Long,
        val sourceId: Long,
        val targetId: Long,
        val commandType: CommandAndControlSystem.Command.CommandType,
        val parameters: Map<String, Any>,
        val timestamp: Long,
        val predictionId: Long? = null,
        val requiresValidation: Boolean = true
    )
    
    // Client state
    data class ClientState(
        val playerId: Long,
        val predictedMoves: MutableList<MoveCommand> = mutableListOf(),
        val confirmedMoves: MutableList<MoveCommand> = mutableListOf(),
        val pendingActions: MutableList<PlayerAction> = mutableListOf(),
        val lastSyncTimestamp: Long = System.currentTimeMillis(),
        val predictionBuffer: MutableMap<Long, MoveCommand> = mutableMapOf()
    )
    
    // Validation result
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val correctedMove: MoveCommand? = null,
        val latency: Double = 0.0
    )
    
    // Client instances
    internal val clients = mutableMapOf<Long, ClientState>()
    
    /**
     * Register a new client
     */
    fun registerClient(playerId: Long): ClientState {
        val clientState = ClientState(playerId)
        clients[playerId] = clientState
        return clientState
    }
    
    /**
     * Unregister a client
     */
    fun unregisterClient(playerId: Long) {
        clients.remove(playerId)
    }
    
    /**
     * Process player action and translate to move command
     */
    fun processPlayerAction(action: PlayerAction): MoveCommand? {
        val clientState = clients[action.playerId] ?: return null
        
        // Validate action
        val validation = validatePlayerAction(action)
        if (!validation.isValid) {
            return null
        }
        
        // Translate action to move command
        val moveCommand = translateActionToMove(action)
        if (moveCommand == null) {
            return null
        }
        
        // Add to pending actions
        clientState.pendingActions.add(action)
        
        // Apply client-side prediction
        val predictedMove = applyPrediction(moveCommand, clientState)
        
        // Add to prediction buffer
        clientState.predictionBuffer[predictedMove.id] = predictedMove
        clientState.predictedMoves.add(predictedMove)
        
        return predictedMove
    }
    
    /**
     * Validate player action
     */
    internal fun validatePlayerAction(action: PlayerAction): ValidationResult {
        // Check if player has authority over target entity
        if (action.targetEntityId != null) {
            val hasAuthority = commandSystem.validateCommandAuthority(
                CommandAndControlSystem.Command(
                    id = action.id,
                    sourceId = action.playerId,
                    targetId = action.targetEntityId,
                    commandType = CommandAndControlSystem.Command.CommandType.MOVE,
                    parameters = action.parameters,
                    timestamp = action.timestamp,
                    priority = action.priority
                )
            )
            
            if (!hasAuthority) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Insufficient authority over target entity"
                )
            }
        }
        
        // Check resource requirements
        when (action.actionType) {
            PlayerActionType.CONVERT_COMPUTRONIUM -> {
                val energyAmount = action.parameters["energyAmount"] as? Double ?: 0.0
                val playerEntity = entityManager.getEntity(action.playerId)
                val energyComponent = playerEntity?.getComponent<ComputroniumSystem.EnergyComponent>()
                
                if (energyComponent == null || energyComponent.amount < energyAmount) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Insufficient energy for computronium conversion"
                    )
                }
            }
            PlayerActionType.BREACH_ATTEMPT -> {
                val targetId = action.targetEntityId ?: return ValidationResult(
                    isValid = false,
                    errorMessage = "Breach attempt requires target entity"
                )
                
                val breachType = action.parameters["breachType"] as? ProofOfWorkSystem.BreachType
                if (breachType == null) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Invalid breach type"
                    )
                }
                
                val probability = proofOfWorkSystem.calculateBreachProbability(
                    action.playerId, targetId, breachType
                )
                
                if (probability < 0.01) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Breach probability too low"
                    )
                }
            }
            else -> {
                // Other action types have basic validation
            }
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Translate player action to move command
     */
    internal fun translateActionToMove(action: PlayerAction): MoveCommand? {
        val commandType = when (action.actionType) {
            PlayerActionType.MOVE_UNIT -> CommandAndControlSystem.Command.CommandType.MOVE
            PlayerActionType.ATTACK_TARGET -> CommandAndControlSystem.Command.CommandType.ATTACK
            PlayerActionType.BUILD_STRUCTURE -> CommandAndControlSystem.Command.CommandType.ABILITY_USE
            PlayerActionType.RESEARCH_TECH -> CommandAndControlSystem.Command.CommandType.ABILITY_USE
            PlayerActionType.ALLOCATE_RESOURCES -> CommandAndControlSystem.Command.CommandType.ABILITY_USE
            PlayerActionType.FORMATION_COMMAND -> CommandAndControlSystem.Command.CommandType.FORMATION
            PlayerActionType.BREACH_ATTEMPT -> CommandAndControlSystem.Command.CommandType.ABILITY_USE
            PlayerActionType.CONVERT_COMPUTRONIUM -> CommandAndControlSystem.Command.CommandType.ABILITY_USE
        }
        
        return MoveCommand(
            id = action.id,
            sourceId = action.playerId,
            targetId = action.targetEntityId ?: action.playerId,
            commandType = commandType,
            parameters = action.parameters,
            timestamp = action.timestamp,
            requiresValidation = true
        )
    }
    
    /**
     * Apply client-side prediction
     */
    internal fun applyPrediction(moveCommand: MoveCommand, clientState: ClientState): MoveCommand {
        // Calculate network latency
        val latency = networkPhysics.calculateLatency(
            x1 = 0.0, y1 = 0.0, // Client position (simplified)
            x2 = 0.0, y2 = 0.0, // Server position (simplified)
            connectionType = NetworkPhysics.ConnectionType.FIBER_OPTIC,
            utilization = 0.5 // Assume moderate network utilization
        )
        
        // Apply prediction based on latency
        val predictedTimestamp = moveCommand.timestamp + latency.toLong()
        
        return moveCommand.copy(
            timestamp = predictedTimestamp,
            predictionId = moveCommand.id
        )
    }
    
    /**
     * Synchronize with server state
     */
    fun synchronizeWithServer(
        playerId: Long,
        serverMoves: List<MoveCommand>,
        serverTimestamp: Long
    ): List<MoveCommand> {
        val clientState = clients[playerId] ?: return emptyList()
        val corrections = mutableListOf<MoveCommand>()
        
        // Remove confirmed moves from prediction buffer
        serverMoves.forEach { serverMove ->
            clientState.predictionBuffer.remove(serverMove.predictionId)
            clientState.confirmedMoves.add(serverMove)
        }
        
        // Apply corrections for remaining predictions
        clientState.predictionBuffer.values.forEach { predictedMove ->
            val correction = calculateCorrection(predictedMove, serverMoves)
            if (correction != null) {
                corrections.add(correction)
            }
        }
        
        // Update last sync timestamp
        clientState.lastSyncTimestamp = serverTimestamp
        
        return corrections
    }
    
    /**
     * Calculate correction for predicted move
     */
    internal fun calculateCorrection(
        predictedMove: MoveCommand,
        serverMoves: List<MoveCommand>
    ): MoveCommand? {
        // Find corresponding server move
        val serverMove = serverMoves.find { it.predictionId == predictedMove.predictionId }
        
        if (serverMove == null) {
            // Move not yet confirmed by server
            return null
        }
        
        // Check if prediction was accurate
        if (predictedMove == serverMove) {
            return null // No correction needed
        }
        
        // Return corrected move
        return serverMove
    }
    
    /**
     * Get client state
     */
    fun getClientState(playerId: Long): ClientState? {
        return clients[playerId]
    }
    
    /**
     * Get all predicted moves for a client
     */
    fun getPredictedMoves(playerId: Long): List<MoveCommand> {
        return clients[playerId]?.predictedMoves ?: emptyList()
    }
    
    /**
     * Get all confirmed moves for a client
     */
    fun getConfirmedMoves(playerId: Long): List<MoveCommand> {
        return clients[playerId]?.confirmedMoves ?: emptyList()
    }
    
    /**
     * Clear old moves to prevent memory buildup
     */
    fun cleanupOldMoves(playerId: Long, cutoffTimestamp: Long) {
        val clientState = clients[playerId] ?: return
        
        clientState.predictedMoves.removeAll { it.timestamp < cutoffTimestamp }
        clientState.confirmedMoves.removeAll { it.timestamp < cutoffTimestamp }
        clientState.pendingActions.removeAll { it.timestamp < cutoffTimestamp }
        
        // Clean up prediction buffer
        clientState.predictionBuffer.entries.removeIf { it.value.timestamp < cutoffTimestamp }
    }
    
    /**
     * Get client statistics
     */
    fun getClientStats(playerId: Long): ClientStats {
        val clientState = clients[playerId] ?: return ClientStats()
        
        return ClientStats(
            playerId = playerId,
            predictedMovesCount = clientState.predictedMoves.size,
            confirmedMovesCount = clientState.confirmedMoves.size,
            pendingActionsCount = clientState.pendingActions.size,
            predictionBufferSize = clientState.predictionBuffer.size,
            lastSyncTimestamp = clientState.lastSyncTimestamp
        )
    }
    
    /**
     * Client statistics
     */
    data class ClientStats(
        val playerId: Long = 0L,
        val predictedMovesCount: Int = 0,
        val confirmedMovesCount: Int = 0,
        val pendingActionsCount: Int = 0,
        val predictionBufferSize: Int = 0,
        val lastSyncTimestamp: Long = 0L
    )
} 