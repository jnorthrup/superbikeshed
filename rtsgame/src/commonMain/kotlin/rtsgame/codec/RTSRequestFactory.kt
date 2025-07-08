import kotlin.math.*
package rtsgame.codec
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.services.RequestFactoryService
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import rtsgame.core.Simulation
import rtsgame.core.SimulationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * RTS RequestFactory Service - Handles all game commands through RequestFactory pattern
 * Ensures deterministic execution and replay compatibility with JS version
 */
class RTSRequestFactory(
    internal val simulation: Simulation
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
                frameNumber = simulation.gameState.gameTime.toLong()
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
        simulation.gameState.reset()
        return "replay.started".toByteArray().let { bytes ->
            bytes.size j { i -> bytes[i] }
        }
    }
    
    internal fun stopReplay(): Indexed<Byte> {
        isReplaying = false
        return "replay.stopped".toByteArray().let { bytes ->
            bytes.size j { i -> bytes[i] }
        }
    }
    
    internal fun exportReplay(): Indexed<Byte> {
        return RTSCodec.encodeBatch(requestHistory)
    }
    
    internal fun getSyncState(): Indexed<Byte> {
        // Create deterministic state snapshot for synchronization check
        val state = mapOf(
            "frameNumber" to simulation.gameState.gameTime,
            "checksum" to calculateStateChecksum()
        )
        val stateJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.serializer<Map<String, Int>>(),
            state
        )
        return stateJson.toByteArray().let { bytes ->
            bytes.size j { i -> bytes[i] }
        }
    }
    
    internal fun calculateStateChecksum(): Int {
        // Simple deterministic checksum for sync validation
        var checksum = 0
        checksum = checksum xor simulation.gameState.gameTime
        checksum = checksum xor simulation.units.size
        checksum = checksum xor simulation.buildings.size
        // Add more state elements as needed
        return checksum
    }
}

/**
 * Create RTS simulation with RequestFactory integration
 */
fun createRTSSimulation(seed: Long = 12345): Pair<Simulation, RTSRequestFactory> {
    val context = SimulationContext(
        GAME_SEED = seed,
        seedRandom = Random(seed),
        HEADLESS_MODE = true,
        RECORD_AI_DECISIONS = true,
        RECORD_AI_DECISIONS_DURATION_SECONDS = 0,
        battleJournal = null
    )
    
    val simulation = Simulation(context)
    val requestFactory = RTSRequestFactory(simulation)
    
    return simulation to requestFactory
}