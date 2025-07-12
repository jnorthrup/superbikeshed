package rtsgame.codec

import kotlin.math.*
import kotlinx.datetime.*
import kotlin.time.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

// TrikeShed-compatible types
typealias Indexed<T> = List<T>
data class Join<A, B>(val first: A, val second: B)
infix fun <A, B> A.j(second: B): Join<A, B> = this j second

// Mock RequestFactoryService interface
interface RequestFactoryService {
    fun process(requestPayload: Indexed<Byte>): Indexed<Byte>
    fun registerServiceLocator(serviceClass: String, locator: () -> Any)
    fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean)
    suspend fun invokeService(serviceName: String, data: Indexed<Byte>): Indexed<Byte>
}

// Mock registry
object RequestFactoryRegistry {
    fun registerService(serviceClass: String, locator: () -> Any) {
        // Implementation stub
    }
    
    fun registerValidator(methodName: String, validator: (Any) -> Boolean) {
        // Implementation stub
    }
}

// RTS Request types
sealed class RTSRequest {
    data class MoveUnit(val unitId: Int, val x: Float, val y: Float, val frameNumber: Long) : RTSRequest()
    data class AttackTarget(val attackerId: Int, val targetId: Int, val frameNumber: Long) : RTSRequest()
    data class StopUnit(val unitId: Int, val frameNumber: Long) : RTSRequest()
    data class BuildStructure(val buildingType: String, val x: Float, val y: Float, val frameNumber: Long) : RTSRequest()
    data class QueueUnit(val unitType: String, val factoryId: Int, val frameNumber: Long) : RTSRequest()
    data class CancelProduction(val factoryId: Int, val queueIndex: Int, val frameNumber: Long) : RTSRequest()
    data class SetRallyPoint(val buildingId: Int, val x: Float, val y: Float, val frameNumber: Long) : RTSRequest()
    data class AIDecisionOverride(val team: Int, val decision: String, val frameNumber: Long) : RTSRequest()
    data class SimulationTick(val deltaTime: Float, val frameNumber: Long) : RTSRequest()
    data class Playerval playerId: Int j val teamId: Int, val frameNumber: Long : RTSRequest()
}

// RTS Response types
sealed class RTSResponse {
    data class CommandAccepted(val requestId: String, val frameNumber: Long) : RTSResponse()
    data class CommandRejected(val requestId: String, val reason: String, val frameNumber: Long) : RTSResponse()
    data class StateUpdate(val entities: List<EntityState>, val frameNumber: Long) : RTSResponse()
}

// Entity state for sync
data class EntityState(
    val id: Int,
    val x: Float,
    val y: Float,
    val health: Float,
    val type: String
)

// Codec for encoding/decoding
object RTSCodec {
    fun decodeRequest(data: Indexed<Byte>): RTSRequest {
        // Mock implementation
        return RTSRequest.SimulationTick(1.0f / 60.0f, 0)
    }
    
    fun encodeResponse(response: RTSResponse): Indexed<Byte> {
        // Mock implementation
        return "response".toByteArray().toList()
    }
    
    fun encodeBatch(requests: List<RTSRequest>): Indexed<Byte> {
        // Mock implementation
        return "batch".toByteArray().toList()
    }
}

// Simulation context for compatibility
data class SimulationContext(
    val GAME_SEED: Long,
    val seedRandom: Random,
    val HEADLESS_MODE: Boolean,
    val RECORD_AI_DECISIONS: Boolean,
    val RECORD_AI_DECISIONS_DURATION_SECONDS: Int,
    val battleJournal: Any?
)

/**
 * RTS RequestFactory Service - Handles all game commands through RequestFactory pattern
 * Ensures deterministic execution and replay compatibility with JS version
 */
class RTSRequestFactory(
    internal val simulation: rtsgame.codec.Simulation
) : RequestFactoryService {
    
    internal val requestHistory = mutableListOf<RTSRequest>()
    internal val mutex = Mutex()
    internal var isReplaying = false
    internal var replayIndex = 0
    
    override fun process(requestPayload: Indexed<Byte>): Indexed<Byte> {
        return try {
            val request = RTSCodec.decodeRequest(requestPayload)
            
            // Store request for replay
            if (!isReplaying) {
                requestHistory.add(request)
            }
            
            // Process request based on type
            val response = when (request) {
                is RTSRequest.MoveUnit -> processMoveUnit(request)
                is RTSRequest.AttackTarget -> processAttackTarget(request)
                is RTSRequest.StopUnit -> processStopUnit(request)
                is RTSRequest.BuildStructure -> processBuildStructure(request)
                is RTSRequest.QueueUnit -> processQueueUnit(request)
                is RTSRequest.CancelProduction -> processCancelProduction(request)
                is RTSRequest.SetRallyPoint -> processSetRallyPoint(request)
                is RTSRequest.AIDecisionOverride -> processAIOverride(request)
                is RTSRequest.SimulationTick -> processSimulationTick(request)
                is RTSRequest.PlayerJoin -> processPlayerJoin(request)
            }
            
            RTSCodec.encodeResponse(response)
        } catch (e: Exception) {
            val errorResponse = RTSResponse.CommandRejected(
                requestId = "error",
                reason = e.message ?: "Unknown error",
                frameNumber = simulation.getCurrentTick()
            )
            RTSCodec.encodeResponse(errorResponse)
        }
    }
    
    internal fun processMoveUnit(request: RTSRequest.MoveUnit): RTSResponse {
        // TODO: Find unit by ID and issue move command
        // For now, return success to match JS behavior
        return RTSResponse.CommandAccepted(
            requestId = "move-${request.unitId}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processAttackTarget(request: RTSRequest.AttackTarget): RTSResponse {
        // TODO: Find attacker and target, issue attack command
        return RTSResponse.CommandAccepted(
            requestId = "attack-${request.attackerId}-${request.targetId}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processStopUnit(request: RTSRequest.StopUnit): RTSResponse {
        // TODO: Find unit and stop current action
        return RTSResponse.CommandAccepted(
            requestId = "stop-${request.unitId}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processBuildStructure(request: RTSRequest.BuildStructure): RTSResponse {
        // TODO: Validate build location, deduct resources, start construction
        return RTSResponse.CommandAccepted(
            requestId = "build-${request.buildingType}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processQueueUnit(request: RTSRequest.QueueUnit): RTSResponse {
        // TODO: Find factory, check resources, add to build queue
        return RTSResponse.CommandAccepted(
            requestId = "queue-${request.unitType}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processCancelProduction(request: RTSRequest.CancelProduction): RTSResponse {
        // TODO: Find factory, cancel item in queue, refund resources
        return RTSResponse.CommandAccepted(
            requestId = "cancel-${request.factoryId}-${request.queueIndex}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processSetRallyPoint(request: RTSRequest.SetRallyPoint): RTSResponse {
        // TODO: Find building, update rally point
        return RTSResponse.CommandAccepted(
            requestId = "rally-${request.buildingId}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processAIOverride(request: RTSRequest.AIDecisionOverride): RTSResponse {
        // TODO: Override AI decision in command hierarchy
        return RTSResponse.CommandAccepted(
            requestId = "ai-override-${request.team}",
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processSimulationTick(request: RTSRequest.SimulationTick): RTSResponse {
        // Update simulation
        simulation.update(request.deltaTime)
        
        // TODO: Gather entity states for synchronization
        val entities = emptyList<EntityState>()
        
        return RTSResponse.StateUpdate(
            entities = entities,
            frameNumber = request.frameNumber
        )
    }
    
    internal fun processPlayerJoin(request: RTSRequest.PlayerJoin): RTSResponse {
        // TODO: Add player to team
        return RTSResponse.CommandAccepted(
            requestId = "join-${request.playerId}",
            frameNumber = request.frameNumber
        )
    }
    
    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        // Register RTS services (units, buildings, AI, etc.)
        borg.trikeshed.services.RequestFactoryRegistry.registerService(serviceClass, locator)
    }
    
    override fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean) {
        // Register validators for game rules
        borg.trikeshed.services.RequestFactoryRegistry.registerValidator(methodName, validator)
    }
    
    override suspend fun invokeService(serviceName: String, data: Indexed<Byte>): Indexed<Byte> {
        return mutex.withLock {
            when (serviceName) {
                "replay.start" -> startReplay(data)
                "replay.stop" -> stopReplay()
                "replay.export" -> exportReplay()
                "sync.state" -> getSyncState()
                else -> process(data)
            }
        }
    }
    
    internal fun startReplay(data: Indexed<Byte>): Indexed<Byte> {
        isReplaying = true
        replayIndex = 0
        // Reset simulation to initial state
        simulation.reset()
        return "replay.started".toByteArray().let { bytes ->
            \1 j { \2: Int -> bytes[i] }
        }
    }
    
    internal fun stopReplay(): Indexed<Byte> {
        isReplaying = false
        return "replay.stopped".toByteArray().let { bytes ->
            \1 j { \2: Int -> bytes[i] }
        }
    }
    
    internal fun exportReplay(): Indexed<Byte> {
        return RTSCodec.encodeBatch(requestHistory)
    }
    
    internal fun getSyncState(): Indexed<Byte> {
        // Create deterministic state snapshot for synchronization check
        val state = mapOf(
            "frameNumber" to simulation.getCurrentTick().toInt(),
            "checksum" to calculateStateChecksum()
        )
        val stateJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.serializer<Map<String, Int>>(),
            state
        )
        return stateJson.toByteArray().let { bytes ->
            \1 j { \2: Int -> bytes[i] }
        }
    }
    
    internal fun calculateStateChecksum(): Int {
        // Simple deterministic checksum for sync validation
        var checksum = 0
        checksum = checksum xor simulation.getCurrentTick().toInt()
        checksum = checksum xor simulation.getEntityCount()
        // Add more state elements as needed
        return checksum
    }
    
    // Simple method for tests
    fun createMoveRequest(unitId: Int, targetX: Float, targetY: Float): SimpleRTSRequest {
        return SimpleRTSRequest("move", mapOf(
            "unitId" to unitId,
            "targetX" to targetX,
            "targetY" to targetY
        ))
    }
}

/**
 * Simple RTS request for testing
 */
data class SimpleRTSRequest(
    val type: String,
    val parameters: Map<String, Any>
)
}

