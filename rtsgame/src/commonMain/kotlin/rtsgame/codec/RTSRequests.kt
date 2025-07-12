import kotlin.math.*
package rtsgame.codec
import kotlinx.datetime.*
import kotlin.time.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

/**
 * RTS Request Types - All game commands as serializable requests
 * These match JS command structure exactly for deterministic replay
 */
sealed class RTSRequest {
    abstract val frameNumber: Long
    abstract val timestamp: Double
    
    // GameUnit Commands
    @Serializable
    data class MoveUnit(
        val unitId: Int,
        val x: Double,
        val y: Double,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    @Serializable
    data class AttackTarget(
        val attackerId: Int,
        val targetId: Int,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    @Serializable
    data class StopUnit(
        val unitId: Int,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    // Building Commands
    @Serializable
    data class BuildStructure(
        val builderId: Int,
        val buildingType: String,
        val x: Double,
        val y: Double,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    @Serializable
    data class QueueUnit(
        val factoryId: Int,
        val unitType: String,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    @Serializable
    data class CancelProduction(
        val factoryId: Int,
        val queueIndex: Int,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    // Resource Commands
    @Serializable
    data class SetRallyPoint(
        val buildingId: Int,
        val x: Double,
        val y: Double,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    // AI Override Commands
    @Serializable
    data class AIDecisionOverride(
        val team: String,
        val decisionType: String,
        val originalDecision: String,
        val overrideDecision: String,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    // Simulation Control
    @Serializable
    data class SimulationTick(
        val deltaTime: Double,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
    
    @Serializable
    data class PlayerJoin(
        val playerId: String,
        val team: String,
        override val frameNumber: Long,
        override val timestamp: Double
    ) : RTSRequest()
}

/**
 * RTS Response Types - Results from command execution
 */
sealed class RTSResponse {
    abstract val frameNumber: Long
    abstract val success: Boolean
    abstract val message: String?
    
    @Serializable
    data class CommandAccepted(
        val requestId: String,
        override val frameNumber: Long,
        override val success: Boolean = true,
        override val message: String? = null
    ) : RTSResponse()
    
    @Serializable
    data class CommandRejected(
        val requestId: String,
        val reason: String,
        override val frameNumber: Long,
        override val success: Boolean = false,
        override val message: String? = null
    ) : RTSResponse()
    
    @Serializable
    data class StateUpdate(
        val entities: List<EntityState>,
        override val frameNumber: Long,
        override val success: Boolean = true,
        override val message: String? = null
    ) : RTSResponse()
}

/**
 * Entity state for synchronization
 */
data class EntityState(
    val id: Int,
    val type: String,
    val team: String,
    val x: Double,
    val y: Double,
    val hp: Double,
    val maxHp: Double
)

/**
 * RTS Codec - Handles serialization between JS and KMP
 */
object RTSCodec {
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // Match JS number handling
        coerceInputValues = true
    }
    
    fun encodeRequest(request: RTSRequest): Indexed<Byte> {
        val jsonString = json.encodeToString(RTSRequest.serializer(), request)
        val bytes = jsonString.encodeToByteArray()
        return \1 j { \2: Int -> bytes[i] }
    }
    
    fun decodeRequest(data: Indexed<Byte>): RTSRequest {
        val bytes = ByteArray(data.component1()) { i -> data[i] }
        val jsonString = bytes.decodeToString()
        return json.decodeFromString(RTSRequest.serializer(), jsonString)
    }
    
    fun encodeResponse(response: RTSResponse): Indexed<Byte> {
        val jsonString = json.encodeToString(RTSResponse.serializer(), response)
        val bytes = jsonString.encodeToByteArray()
        return \1 j { \2: Int -> bytes[i] }
    }
    
    fun decodeResponse(data: Indexed<Byte>): RTSResponse {
        val bytes = ByteArray(data.component1()) { i -> data[i] }
        val jsonString = bytes.decodeToString()
        return json.decodeFromString(RTSResponse.serializer(), jsonString)
    }
    
    /**
     * Create request batch for efficient network transmission
     */
    fun encodeBatch(requests: List<RTSRequest>): Indexed<Byte> {
        val batchJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RTSRequest.serializer()),
            requests
        )
        val bytes = batchJson.encodeToByteArray()
        return \1 j { \2: Int -> bytes[i] }
    }
}