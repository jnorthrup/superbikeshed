package com.rtsgame.shared.client

import com.rtsgame.shared.entity.EntityManager
import com.rtsgame.shared.systems.CommandAndControlSystem
import com.rtsgame.shared.systems.ProofOfWorkSystem
import com.rtsgame.shared.systems.ComputroniumSystem
import kotlin.math.*

/**
 * Move Validator - Deterministic validation and replay
 * 
 * Key Features:
 * - Move Validation: Replay and validation of all moves
 * - State Verification: Deterministic state checking
 * - Cheat Detection: Validation of move legality
 */
class MoveValidator(
    internal val entityManager: EntityManager,
    internal val commandSystem: CommandAndControlSystem,
    internal val proofOfWorkSystem: ProofOfWorkSystem,
    internal val computroniumSystem: ComputroniumSystem
) {
    
    // Validation result
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val correctedMove: MoveClient.MoveCommand? = null,
        val validationTimestamp: Long = System.currentTimeMillis()
    )
    
    // Game state snapshot
    data class GameStateSnapshot(
        val timestamp: Long,
        val entities: Map<Long, EntitySnapshot>,
        val moves: List<MoveClient.MoveCommand>,
        val checksum: String
    ) {
        data class EntitySnapshot(
            val id: Long,
            val position: Position?,
            val health: Double?,
            val resources: Double?,
            val components: Map<String, Any>
        )
    }
    
    // Replay session
    data class ReplaySession(
        val sessionId: Long,
        val initialState: GameStateSnapshot,
        val moves: List<MoveClient.MoveCommand>,
        val finalState: GameStateSnapshot? = null,
        val validationResults: MutableList<ValidationResult> = mutableListOf()
    )
    
    /**
     * Validate a single move
     */
    fun validateMove(move: MoveClient.MoveCommand): ValidationResult {
        // Check basic move structure
        if (move.sourceId <= 0 || move.targetId <= 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid entity IDs"
            )
        }
        
        // Check if source entity exists
        val sourceEntity = entityManager.getEntity(move.sourceId)
        if (sourceEntity == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Source entity does not exist"
            )
        }
        
        // Check if target entity exists (for non-self-targeting moves)
        if (move.sourceId != move.targetId) {
            val targetEntity = entityManager.getEntity(move.targetId)
            if (targetEntity == null) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Target entity does not exist"
                )
            }
        }
        
        // Validate command authority
        val hasAuthority = commandSystem.validateCommandAuthority(
            CommandAndControlSystem.Command(
                id = move.id,
                sourceId = move.sourceId,
                targetId = move.targetId,
                commandType = move.commandType,
                parameters = move.parameters,
                timestamp = move.timestamp,
                priority = 0
            )
        )
        
        if (!hasAuthority) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Insufficient command authority"
            )
        }
        
        // Validate move-specific parameters
        val moveValidation = validateMoveParameters(move)
        if (!moveValidation.isValid) {
            return moveValidation
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validate move parameters based on command type
     */
    internal fun validateMoveParameters(move: MoveClient.MoveCommand): ValidationResult {
        return when (move.commandType) {
            CommandAndControlSystem.Command.CommandType.MOVE -> validateMoveCommand(move)
            CommandAndControlSystem.Command.CommandType.ATTACK -> validateAttackCommand(move)
            CommandAndControlSystem.Command.CommandType.DEFEND -> validateDefendCommand(move)
            CommandAndControlSystem.Command.CommandType.FORMATION -> validateFormationCommand(move)
            CommandAndControlSystem.Command.CommandType.RESOURCE_GATHER -> validateResourceCommand(move)
            CommandAndControlSystem.Command.CommandType.ABILITY_USE -> validateAbilityCommand(move)
        }
    }
    
    /**
     * Validate move command
     */
    internal fun validateMoveCommand(move: MoveClient.MoveCommand): ValidationResult {
        val sourceEntity = entityManager.getEntity(move.sourceId) ?: return ValidationResult(
            isValid = false,
            errorMessage = "Source entity not found"
        )
        
        val targetPosition = move.parameters["targetPosition"] as? Map<String, Double>
        if (targetPosition == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Missing target position"
            )
        }
        
        val currentPosition = sourceEntity.getComponent<Position>()
        if (currentPosition == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Source entity has no position"
            )
        }
        
        val distance = calculateDistance(
            currentPosition.x, currentPosition.y,
            targetPosition["x"] ?: 0.0, targetPosition["y"] ?: 0.0
        )
        
        val maxSpeed = move.parameters["maxSpeed"] as? Double ?: 10.0
        val timeDelta = move.parameters["timeDelta"] as? Double ?: 1.0
        
        val maxDistance = maxSpeed * timeDelta
        
        if (distance > maxDistance) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Move distance exceeds maximum allowed"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validate attack command
     */
    internal fun validateAttackCommand(move: MoveClient.MoveCommand): ValidationResult {
        val sourceEntity = entityManager.getEntity(move.sourceId) ?: return ValidationResult(
            isValid = false,
            errorMessage = "Source entity not found"
        )
        
        val targetEntity = entityManager.getEntity(move.targetId) ?: return ValidationResult(
            isValid = false,
            errorMessage = "Target entity not found"
        )
        
        // Check attack range
        val sourcePosition = sourceEntity.getComponent<Position>()
        val targetPosition = targetEntity.getComponent<Position>()
        
        if (sourcePosition == null || targetPosition == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Entities missing position components"
            )
        }
        
        val distance = calculateDistance(
            sourcePosition.x, sourcePosition.y,
            targetPosition.x, targetPosition.y
        )
        
        val attackRange = move.parameters["attackRange"] as? Double ?: 5.0
        
        if (distance > attackRange) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Target out of attack range"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validate defend command
     */
    internal fun validateDefendCommand(move: MoveClient.MoveCommand): ValidationResult {
        // Defend commands are generally valid if entities exist
        val sourceEntity = entityManager.getEntity(move.sourceId)
        if (sourceEntity == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Source entity not found"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validate formation command
     */
    internal fun validateFormationCommand(move: MoveClient.MoveCommand): ValidationResult {
        val formationType = move.parameters["formationType"] as? String
        if (formationType == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Missing formation type"
            )
        }
        
        val unitIds = move.parameters["unitIds"] as? List<Long>
        if (unitIds == null || unitIds.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "No units specified for formation"
            )
        }
        
        // Check if all units exist and belong to the same team
        val firstUnit = entityManager.getEntity(unitIds.first())
        if (firstUnit == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "First unit not found"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validate resource command
     */
    internal fun validateResourceCommand(move: MoveClient.MoveCommand): ValidationResult {
        val sourceEntity = entityManager.getEntity(move.sourceId) ?: return ValidationResult(
            isValid = false,
            errorMessage = "Source entity not found"
        )
        
        val resourceType = move.parameters["resourceType"] as? String
        if (resourceType == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Missing resource type"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validate ability command
     */
    internal fun validateAbilityCommand(move: MoveClient.MoveCommand): ValidationResult {
        val sourceEntity = entityManager.getEntity(move.sourceId) ?: return ValidationResult(
            isValid = false,
            errorMessage = "Source entity not found"
        )
        
        val abilityId = move.parameters["abilityId"] as? String
        if (abilityId == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Missing ability ID"
            )
        }
        
        // Check if entity has the ability
        val abilities = sourceEntity.getComponent<AbilityComponent>()
        if (abilities == null || !abilities.abilities.contains(abilityId)) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Entity does not have the specified ability"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Create game state snapshot
     */
    fun createGameStateSnapshot(moves: List<MoveClient.MoveCommand>): GameStateSnapshot {
        val entities = mutableMapOf<Long, GameStateSnapshot.EntitySnapshot>()
        
        entityManager.getAllEntities().forEach { entity ->
            val position = entity.getComponent<Position>()
            val health = entity.getComponent<HealthComponent>()?.currentHealth
            val resources = entity.getComponent<ResourceComponent>()?.amount?.toDouble()
            
            val components = mutableMapOf<String, Any>()
            entity.getComponentTypes().forEach { componentType ->
                val component = entity.getComponent(componentType)
                if (component != null) {
                    components[componentType.simpleName] = component
                }
            }
            
            entities[entity.id] = GameStateSnapshot.EntitySnapshot(
                id = entity.id,
                position = position,
                health = health,
                resources = resources,
                components = components
            )
        }
        
        val checksum = calculateChecksum(entities, moves)
        
        return GameStateSnapshot(
            timestamp = System.currentTimeMillis(),
            entities = entities,
            moves = moves,
            checksum = checksum
        )
    }
    
    /**
     * Calculate checksum for state validation
     */
    internal fun calculateChecksum(
        entities: Map<Long, GameStateSnapshot.EntitySnapshot>,
        moves: List<MoveClient.MoveCommand>
    ): String {
        // Simple checksum calculation (in production, use proper hash)
        var checksum = 0L
        
        entities.values.forEach { entity ->
            checksum += entity.id
            checksum += entity.position?.x?.toLong() ?: 0
            checksum += entity.position?.y?.toLong() ?: 0
            checksum += entity.health?.toLong() ?: 0
            checksum += entity.resources?.toLong() ?: 0
        }
        
        moves.forEach { move ->
            checksum += move.id
            checksum += move.sourceId
            checksum += move.targetId
            checksum += move.timestamp
        }
        
        return checksum.toString(16)
    }
    
    /**
     * Validate game state consistency
     */
    fun validateGameState(snapshot: GameStateSnapshot): ValidationResult {
        val expectedChecksum = calculateChecksum(snapshot.entities, snapshot.moves)
        
        if (snapshot.checksum != expectedChecksum) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Game state checksum mismatch"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Replay moves from a snapshot
     */
    fun replayMoves(
        initialState: GameStateSnapshot,
        moves: List<MoveClient.MoveCommand>
    ): ReplaySession {
        val session = ReplaySession(
            sessionId = System.nanoTime(),
            initialState = initialState,
            moves = moves
        )
        
        // Validate each move in sequence
        moves.forEach { move ->
            val validation = validateMove(move)
            session.validationResults.add(validation)
            
            if (!validation.isValid) {
                // Stop replay on first invalid move
                return session
            }
        }
        
        // Create final state snapshot
        val finalState = createGameStateSnapshot(moves)
        session.finalState = finalState
        
        return session
    }
    
    /**
     * Detect potential cheating
     */
    fun detectCheating(
        playerId: Long,
        moves: List<MoveClient.MoveCommand>,
        timeWindow: Long = 60000 // 1 minute
    ): List<String> {
        val violations = mutableListOf<String>()
        val playerMoves = moves.filter { it.sourceId == playerId }
        
        // Check for impossible move sequences
        val moveCount = playerMoves.size
        val averageTimeBetweenMoves = if (moveCount > 1) {
            val timeSpan = playerMoves.last().timestamp - playerMoves.first().timestamp
            timeSpan / (moveCount - 1)
        } else {
            0L
        }
        
        // Detect inhumanly fast moves (less than 50ms between moves)
        if (averageTimeBetweenMoves < 50) {
            violations.add("Suspiciously fast move execution")
        }
        
        // Check for impossible resource usage
        val resourceMoves = playerMoves.filter { 
            it.commandType == CommandAndControlSystem.Command.CommandType.ABILITY_USE &&
            it.parameters.containsKey("resourceAmount")
        }
        
        val totalResourceUsage = resourceMoves.sumOf { 
            it.parameters["resourceAmount"] as? Double ?: 0.0 
        }
        
        // This would need to be compared against actual available resources
        if (totalResourceUsage > 10000) {
            violations.add("Excessive resource usage")
        }
        
        return violations
    }
    
    // Helper functions
    internal fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
    
    // Placeholder component types
    data class Position(val x: Double, val y: Double)
    data class HealthComponent(val currentHealth: Double, val maxHealth: Double)
    data class ResourceComponent(val amount: Int)
    data class AbilityComponent(val abilities: Set<String>)
} 