import kotlin.math.*
package rtsgame.codec
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Network protocol definitions for RTS multiplayer
 * Optimized for low latency and bandwidth efficiency
 */

/**
 * Wire format messages using protobuf-style encoding
 */
sealed class NetworkMessage {
    abstract val messageId: Long
    abstract val timestamp: Long
    
    @Serializable
    data class Connect(
        @ProtoNumber(1) override val messageId: Long,
        @ProtoNumber(2) override val timestamp: Long,
        @ProtoNumber(3) val playerId: String,
        @ProtoNumber(4) val version: String,
        @ProtoNumber(5) val desiredTeam: String?
    ) : NetworkMessage()
    
    @Serializable
    data class Connected(
        @ProtoNumber(1) override val messageId: Long,
        @ProtoNumber(2) override val timestamp: Long,
        @ProtoNumber(3) val assignedTeam: String,
        @ProtoNumber(4) val gameState: CompressedGameState,
        @ProtoNumber(5) val syncFrame: Long
    ) : NetworkMessage()
    
    @Serializable
    data class CommandBatch(
        @ProtoNumber(1) override val messageId: Long,
        @ProtoNumber(2) override val timestamp: Long,
        @ProtoNumber(3) val frameNumber: Long,
        @ProtoNumber(4) val commands: List<CompressedCommand>
    ) : NetworkMessage()
    
    @Serializable
    data class StateSync(
        @ProtoNumber(1) override val messageId: Long,
        @ProtoNumber(2) override val timestamp: Long,
        @ProtoNumber(3) val frameNumber: Long,
        @ProtoNumber(4) val checksum: Int,
        @ProtoNumber(5) val entities: List<CompressedEntity>?
    ) : NetworkMessage()
    
    @Serializable
    data class Disconnect(
        @ProtoNumber(1) override val messageId: Long,
        @ProtoNumber(2) override val timestamp: Long,
        @ProtoNumber(3) val reason: String
    ) : NetworkMessage()
}

/**
 * Compressed command format for bandwidth efficiency
 */
data class CompressedCommand(
    @ProtoNumber(1) val type: Byte, // Command type as byte
    @ProtoNumber(2) val entityId: Int,
    @ProtoNumber(3) val targetId: Int = -1,
    @ProtoNumber(4) val x: Short = 0, // Position as fixed point
    @ProtoNumber(5) val y: Short = 0,
    @ProtoNumber(6) val data: ByteArray? = null // Additional data
) {
    companion object {
        // Command type constants
        const val MOVE: Byte = 1
        const val ATTACK: Byte = 2
        const val STOP: Byte = 3
        const val BUILD: Byte = 4
        const val QUEUE: Byte = 5
        const val CANCEL: Byte = 6
        const val RALLY: Byte = 7
        
        fun fromRequest(request: RTSRequest): CompressedCommand {
            return when (request) {
                is RTSRequest.MoveUnit -> CompressedCommand(
                    type = MOVE,
                    entityId = request.unitId,
                    x = (request.x * 10).toInt().toShort(),
                    y = (request.y * 10).toInt().toShort()
                )
                is RTSRequest.AttackTarget -> CompressedCommand(
                    type = ATTACK,
                    entityId = request.attackerId,
                    targetId = request.targetId
                )
                is RTSRequest.StopUnit -> CompressedCommand(
                    type = STOP,
                    entityId = request.unitId
                )
                else -> throw IllegalArgumentException("Unsupported request type")
            }
        }
        
        fun toRequest(cmd: CompressedCommand, frameNumber: Long, timestamp: Double): RTSRequest {
            return when (cmd.type) {
                MOVE -> RTSRequest.MoveUnit(
                    unitId = cmd.entityId,
                    x = cmd.x / 10.0,
                    y = cmd.y / 10.0,
                    frameNumber = frameNumber,
                    timestamp = timestamp
                )
                ATTACK -> RTSRequest.AttackTarget(
                    attackerId = cmd.entityId,
                    targetId = cmd.targetId,
                    frameNumber = frameNumber,
                    timestamp = timestamp
                )
                STOP -> RTSRequest.StopUnit(
                    unitId = cmd.entityId,
                    frameNumber = frameNumber,
                    timestamp = timestamp
                )
                else -> throw IllegalArgumentException("Unsupported command type: ${cmd.type}")
            }
        }
    }
}

/**
 * Compressed entity state for sync messages
 */
data class CompressedEntity(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val type: Byte,
    @ProtoNumber(3) val team: Byte,
    @ProtoNumber(4) val x: Short,
    @ProtoNumber(5) val y: Short,
    @ProtoNumber(6) val hp: Short,
    @ProtoNumber(7) val flags: Byte = 0 // Status flags
)

/**
 * Compressed game state for initial sync
 */
data class CompressedGameState(
    @ProtoNumber(1) val seed: Long,
    @ProtoNumber(2) val frameNumber: Long,
    @ProtoNumber(3) val resources: Map<Byte, ResourceState>,
    @ProtoNumber(4) val entities: List<CompressedEntity>
)

data class ResourceState(
    @ProtoNumber(1) val mass: Int,
    @ProtoNumber(2) val energy: Int,
    @ProtoNumber(3) val computronium: Int
)

/**
 * Network codec using efficient binary protocols
 */
object NetworkCodec {
    internal val protobuf = ProtoBuf {
        encodeDefaults = false
    }
    
    fun encodeMessage(message: NetworkMessage): Indexed<Byte> {
        val bytes = protobuf.encodeToByteArray(NetworkMessage.serializer(), message)
        return \1 j { \2: Int -> bytes[i] }
    }
    
    fun decodeMessage(data: Indexed<Byte>): NetworkMessage {
        val bytes = ByteArray(data.component1()) { i -> data[i] }
        return protobuf.decodeFromByteArray(NetworkMessage.serializer(), bytes)
    }
    
    /**
     * Delta compression for state updates
     */
    fun deltaCompress(
        previous: List<CompressedEntity>,
        current: List<CompressedEntity>
    ): DeltaUpdate {
        val added = mutableListOf<CompressedEntity>()
        val updated = mutableListOf<CompressedEntity>()
        val removed = mutableListOf<Int>()
        
        val prevMap = previous.associateBy { it.id }
        val currMap = current.associateBy { it.id }
        
        // Find added and updated
        for (entity in current) {
            val prev = prevMap[entity.id]
            if (prev == null) {
                added.add(entity)
            } else if (prev != entity) {
                updated.add(entity)
            }
        }
        
        // Find removed
        for (entity in previous) {
            if (entity.id !in currMap) {
                removed.add(entity.id)
            }
        }
        
        return DeltaUpdate(added, updated, removed)
    }
    
    data class DeltaUpdate(
        val added: List<CompressedEntity>,
        val updated: List<CompressedEntity>,
        val removed: List<Int>
    )
}

/**
 * Connection state management
 */
class ConnectionState(
    val playerId: String,
    val team: String
) {
    var lastMessageId: Long = 0
    var lastSyncFrame: Long = 0
    var latencyMs: Long = 0
    var packetLoss: Float = 0f
    
    internal val sentMessages = mutableMapOf<Long, Long>() // messageId -> timestamp
    internal val receivedAcks = mutableSetOf<Long>()
    
    fun trackSentMessage(messageId: Long) {
        sentMessages[messageId] = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    }
    
    fun trackReceivedAck(messageId: Long) {
        val sentTime = sentMessages[messageId]
        if (sentTime != null) {
            latencyMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - sentTime
            receivedAcks.add(messageId)
            sentMessages.remove(messageId)
        }
    }
    
    fun calculatePacketLoss(): Float {
        val total = sentMessages.size + receivedAcks.size
        return if (total > 0) {
            sentMessages.size.toFloat() / total
        } else 0f
    }
    
}