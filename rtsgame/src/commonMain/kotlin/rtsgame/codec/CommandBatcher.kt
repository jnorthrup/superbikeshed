import kotlin.math.*
package rtsgame.codec
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Command batching for efficient network transmission
 * Groups commands by frame for deterministic execution
 */
class CommandBatcher(
    internal val maxBatchSize: Int = 100,
    internal val maxLatencyMs: Long = 16 // ~60 FPS
) {
    internal val mutex = Mutex()
    internal val pendingCommands = mutableListOf<RTSRequest>()
    internal var lastFlushTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    
    internal val _batchFlow = MutableStateFlow<Indexed<RTSRequest>?>(null)
    val batchFlow: StateFlow<Indexed<RTSRequest>?> = _batchFlow
    
    suspend fun addCommand(command: RTSRequest) {
        mutex.withLock {
            pendingCommands.add(command)
            
            if (shouldFlush()) {
                flush()
            }
        }
    }
    
    suspend fun forceFlush() {
        mutex.withLock {
            if (pendingCommands.isNotEmpty()) {
                flush()
            }
        }
    }
    
    internal fun shouldFlush(): Boolean {
        val timeSinceLastFlush = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - lastFlushTime
        return pendingCommands.size >= maxBatchSize || timeSinceLastFlush >= maxLatencyMs
    }
    
    internal suspend fun flush() {
        if (pendingCommands.isEmpty()) return
        
        // Create immutable batch
        val batch = pendingCommands.toList()
        val series = \1 j { \2: Int -> batch[i] }
        
        pendingCommands.clear()
        lastFlushTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        _batchFlow.emit(series)
    }
    
}

/**
 * Command interpolation for smooth visual updates
 * Separates visual state from simulation state
 */
class CommandInterpolator {
    internal data class InterpolationState(
        val entityId: Int,
        val startX: Double,
        val startY: Double,
        val targetX: Double,
        val targetY: Double,
        val startTime: Long,
        val duration: Long
    )
    
    internal val activeInterpolations = DeterministicMap<Int, InterpolationState>()
    
    fun startInterpolation(
        entityId: Int,
        currentX: Double,
        currentY: Double,
        targetX: Double,
        targetY: Double,
        durationMs: Long = 100
    ) {
        activeInterpolations[entityId] = InterpolationState(
            entityId = entityId,
            startX = currentX,
            startY = currentY,
            targetX = targetX,
            targetY = targetY,
            startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            duration = durationMs
        )
    }
    
    fun interpolate(entityId: Int): Pair<Double, Double>? {
        val state = activeInterpolations[entityId] ?: return null
        
        val elapsed = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - state.startTime
        val progress = (elapsed.toDouble() / state.duration).coerceIn(0.0, 1.0)
        
        if (progress >= 1.0) {
            activeInterpolations.remove(entityId)
            return state.targetX to state.targetY
        }
        
        // Smooth interpolation using ease-in-out
        val t = smoothstep(progress)
        val x = lerp(state.startX, state.targetX, t)
        val y = lerp(state.startY, state.targetY, t)
        
        return x to y
    }
    
    internal fun smoothstep(t: Double): Double {
        // 3t² - 2t³
        return t * t * (3.0 - 2.0 * t)
    }
    
    internal fun lerp(start: Double, end: Double, t: Double): Double {
        return start + (end - start) * t
    }
    
}

/**
 * Command priority queue for AI decision ordering
 */
class CommandPriorityQueue {
    internal data class PrioritizedCommand(
        val command: RTSRequest,
        val priority: Int,
        val insertionOrder: Int
    )
    
    internal val queue = mutableListOf<PrioritizedCommand>()
    internal var insertionCounter = 0
    internal val mutex = Mutex()
    
    suspend fun enqueue(command: RTSRequest, priority: Int = 0) {
        mutex.withLock {
            queue.add(PrioritizedCommand(command, priority, insertionCounter++))
            queue.sortWith(compareByDescending<PrioritizedCommand> { it.priority }
                .thenBy { it.insertionOrder })
        }
    }
    
    suspend fun dequeue(): RTSRequest? {
        return mutex.withLock {
            if (queue.isNotEmpty()) {
                queue.removeAt(0).command
            } else null
        }
    }
    
    suspend fun dequeueAll(): Indexed<RTSRequest> {
        return mutex.withLock {
            val commands = queue.map { it.command }
            queue.clear()
            \1 j { \2: Int -> commands[i] }
        }
    }
    
    suspend fun peek(): RTSRequest? {
        return mutex.withLock {
            queue.firstOrNull()?.command
        }
    }
    
    suspend fun size(): Int {
        return mutex.withLock {
            queue.size
        }
    }
}

/**
 * Command validation for anti-cheat and rule enforcement
 */
object CommandValidator {
    fun validateCommand(
        command: RTSRequest,
        gameState: rtsgame.core.GameState,
        player: String
    ): ValidationResult {
        return when (command) {
            is RTSRequest.MoveUnit -> validateMoveUnit(command, gameState, player)
            is RTSRequest.BuildStructure -> validateBuildStructure(command, gameState, player)
            is RTSRequest.QueueUnit -> validateQueueUnit(command, gameState, player)
            else -> ValidationResult(true, null)
        }
    }
    
    internal fun validateMoveUnit(
        command: RTSRequest.MoveUnit,
        gameState: rtsgame.core.GameState,
        player: String
    ): ValidationResult {
        // Check unit ownership
        // Check if position is valid
        // Check if unit can move
        return ValidationResult(true, null)
    }
    
    internal fun validateBuildStructure(
        command: RTSRequest.BuildStructure,
        gameState: rtsgame.core.GameState,
        player: String
    ): ValidationResult {
        // Check builder ownership
        // Check resources
        // Check build location
        // Check tech requirements
        return ValidationResult(true, null)
    }
    
    internal fun validateQueueUnit(
        command: RTSRequest.QueueUnit,
        gameState: rtsgame.core.GameState,
        player: String
    ): ValidationResult {
        // Check factory ownership
        // Check resources
        // Check build capacity
        // Check tech requirements
        return ValidationResult(true, null)
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val reason: String?
    )
}