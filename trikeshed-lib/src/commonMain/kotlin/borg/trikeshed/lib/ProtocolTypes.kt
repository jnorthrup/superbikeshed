package borg.trikeshed.lib

import kotlinx.serialization.Serializable


// Type alias for Series<T> as used in TrikeShed
typealias Series<T> = Indexed<T>

// ===== CORE PROTOCOL TYPES =====

/**
 * Core protocol value classes for type safety and performance
 */
@Serializable
expect value class NodeId(val bytes: UByteArray)

@Serializable
expect value class MessageId(val bytes: UByteArray)

@Serializable
expect value class DataKey(val bytes: UByteArray)

@Serializable
expect value class DataValue(val bytes: UByteArray)

@Serializable
expect value class Checksum(val crc32: UInt)

@Serializable
expect value class Timestamp(val epochMillis: Long)

@Serializable
expect value class TimeToLive(val seconds: Int)

@Serializable
expect value class KademliaNodeId(val bytes: UByteArray)

@Serializable
expect value class ProtocolVersion(val version: UByte)

@Serializable
expect value class TrikeShedProtocol(val id: UByte)

@Serializable
expect value class NetworkAddress(val value: String)

@Serializable
expect value class NetworkPort(val value: Int)

// ===== GOSSIP PROTOCOL TYPES =====

/**
 * Gossip protocol message types for anti-entropy and rumor spreading
 */
@Serializable
data class MessageDigest(
    val messageId: MessageId,
    val version: Long,
    val checksum: Checksum
)

@Serializable
data class GossipDigestRequest(
    val nodeId: NodeId,
    val digests: Series<MessageDigest>,
    val timestamp: Timestamp
)

@Serializable
data class GossipSyncResponse(
    val nodeId: NodeId,
    val messages: Series<GossipMessage>,
    val timestamp: Timestamp
)

@Serializable
data class GossipMessage(
    val messageId: MessageId,
    val publisherId: NodeId,
    val content: DataValue,
    val targetSubnets: Series<String>,
    val timestamp: Timestamp,
    val ttl: TimeToLive,
    val hopCount: Int
)

// ===== QUIC INTEGRATION TYPES =====

/**
 * QUIC stream types for multiplexed protocol support
 */
@Serializable
enum class QuicStreamType(val id: Long) {
    DHT_MESSAGES(0L),
    GOSSIP_MESSAGES(1L),
    TENSOR_DATA(2L),
    ISAM_OPERATIONS(3L),
    JSON_RPC(4L)
}

@Serializable
data class QuicStreamFrame(
    val streamId: Long,
    val streamType: QuicStreamType,
    val trikeShedMessage: TrikeShedMessageFrame
)

// ===== SECURITY AND AUTHENTICATION TYPES =====

/**
 * Cryptographic primitives for secure communication
 */
@Serializable
data class PublicKey(val bytes: UByteArray) {
    init { require(bytes.size == 32) { "PublicKey must be 32 bytes" } }
    
    fun hash(): ByteArray = bytes.toByteArray()
    
    fun verify(data: UByteArray, signature: Signature): Boolean {
        // TODO: Implement Ed25519 verification
        return true // Placeholder
    }
}

@Serializable
data class PrivateKey(val bytes: UByteArray) {
    init { require(bytes.size == 64) { "PrivateKey must be 64 bytes" } }
}

@Serializable
data class Signature(val bytes: UByteArray) {
    init { require(bytes.size == 64) { "Signature must be 64 bytes" } }
}

@Serializable
data class SessionKey(val bytes: UByteArray) {
    init { require(bytes.size == 32) { "SessionKey must be 32 bytes" } }
}

@Serializable
data class NodeIdentity(
    val nodeId: NodeId,
    val publicKey: PublicKey,
    val signature: Signature
) {
    fun verifySignature(): Boolean = publicKey.verify(nodeId.bytes, signature)
}

@Serializable
data class SecureMessage(
    val senderId: NodeId,
    val recipientId: NodeId,
    val nonce: UByteArray,
    val encryptedPayload: UByteArray,
    val authTag: UByteArray
) {
    companion object {
        fun create(
            senderId: NodeId,
            recipientId: NodeId,
            payload: UByteArray,
            sessionKey: SessionKey,
            nonce: UByteArray
        ): SecureMessage {
            val encrypted = ChaCha20Poly1305.encrypt(payload, sessionKey, nonce)
            return SecureMessage(senderId, recipientId, nonce, encrypted.payload, encrypted.tag)
        }
    }
    
    fun decrypt(recipientKeypair: Ed25519Keypair, senderPublicKey: PublicKey): UByteArray {
        val sessionKey = X25519KeyExchange.perform(recipientKeypair, senderPublicKey)
        return ChaCha20Poly1305.decrypt(encryptedPayload, sessionKey, nonce, authTag)
    }
}

// ===== ERROR HANDLING TYPES =====

/**
 * Protocol error codes and exception types
 */
@Serializable
enum class TrikeShedError(val code: Int) {
    SUCCESS(0),
    PROTOCOL_VERSION_MISMATCH(1001),
    INVALID_MESSAGE_TYPE(1002),
    CHECKSUM_MISMATCH(1003),
    PAYLOAD_TOO_LARGE(1004),
    UNSUPPORTED_SERIALIZATION(1005),
    AUTHENTICATION_FAILED(1006),
    NODE_UNREACHABLE(1007),
    STORAGE_ERROR(1008),
    COMPRESSION_ERROR(1009),
    TIMEOUT(1010)
}

@Serializable
data class ErrorResponse(
    val errorCode: TrikeShedError,
    val errorMessage: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Timestamp
)

class ProtocolVersionMismatchException(message: String, val errorCode: TrikeShedError) : Exception(message)
class ChecksumMismatchException(message: String, val errorCode: TrikeShedError) : Exception(message)

// ===== COMPRESSION TYPES =====

/**
 * Compression algorithms and types
 */
@Serializable
enum class CompressionType(val id: UByte) {
    NONE(0u),
    LZ4(1u),
    ZSTD(2u),
    GZIP(3u),
    BROTLI(4u)
}

// ===== ISAM PROTOCOL TYPES =====

/**
 * ISAM storage protocol types
 */
@Serializable
data class CursorDataResponse(
    val cursorId: Long,
    val rows: Series<Series<DataValue>>,
    val hasMore: Boolean
)

@Serializable
data class CursorOpenRequest(
    val dataFile: String,
    val columns: Series<IOMemento>,
    val readOnly: Boolean
)

@Serializable
data class CursorReadRequest(
    val cursorId: Long,
    val offset: Long,
    val limit: Int
)

// ===== DHT PROTOCOL TYPES =====

/**
 * Kademlia DHT protocol types
 */
@Serializable
data class PingRequest(
    val nodeId: KademliaNodeId,
    val timestamp: Timestamp
)

@Serializable
data class PongResponse(
    val nodeId: KademliaNodeId,
    val timestamp: Timestamp,
    val uptime: Duration
)

@Serializable
data class FindNodeRequest(
    val nodeId: KademliaNodeId,
    val targetId: KademliaNodeId,
    val timestamp: Timestamp
)

@Serializable
data class NodesResponse(
    val nodeId: KademliaNodeId,
    val nodes: Series<KademliaNodeInfo>,
    val timestamp: Timestamp
)

@Serializable
data class KademliaNodeInfo(
    val nodeId: KademliaNodeId,
    val address: NetworkAddress,
    val port: NetworkPort,
    val lastSeen: Timestamp
)

@Serializable
data class StoreRequest(
    val nodeId: KademliaNodeId,
    val key: DataKey,
    val value: DataValue,
    val ttl: TimeToLive,
    val timestamp: Timestamp
)

@Serializable
data class FindValueRequest(
    val nodeId: KademliaNodeId,
    val key: DataKey,
    val timestamp: Timestamp
)

// ===== WIRE PROTOCOL TYPES =====

/**
 * TrikeShed wire protocol message frame
 */
@Serializable
data class TrikeShedMessageFrame(
    val magic: UShort,
    val version: ProtocolVersion,
    val proto: TrikeShedProtocol,
    val messageLength: Int,
    val messageTypeLength: Int,
    val messageType: String,
    val payload: UByteArray,
    val checksum: Checksum
) {
    companion object {
        const val MAGIC_BYTES: UShort = 0x5452u
        const val CURRENT_VERSION: UByte = 1u
        
        fun create(proto: TrikeShedProtocol, messageType: String, payload: UByteArray): TrikeShedMessageFrame {
            return TrikeShedMessageFrame(
                magic = MAGIC_BYTES,
                version = ProtocolVersion(CURRENT_VERSION),
                proto = proto,
                messageLength = payload.size,
                messageTypeLength = messageType.length,
                messageType = messageType,
                payload = payload,
                checksum = Checksum(calculateCrc32(payload))
            )
        }
        
        internal fun calculateCrc32(data: UByteArray): UInt {
            var crc = 0xFFFFFFFFu
            for (byte in data) {
                crc = crc xor byte.toUInt()
                repeat(8) {
                    crc = if ((crc and 1u) != 0u) {
                        (crc shr 1) xor 0xEDB88320u
                    } else {
                        crc shr 1
                    }
                }
            }
            return crc xor 0xFFFFFFFFu
        }
    }
}

// ===== CRYPTOGRAPHIC IMPLEMENTATIONS =====

/**
 * Cryptographic implementations (stubs for TDD)
 */
class Ed25519Keypair(val publicKey: PublicKey, internal val privateKey: PrivateKey) {
    companion object {
        fun generate(): Ed25519Keypair {
            // TODO: Implement actual Ed25519 key generation
            val publicBytes = ByteArray(32) { it.toByte() }.toUByteArray()
            val privateBytes = ByteArray(64) { it.toByte() }.toUByteArray()
            return Ed25519Keypair(PublicKey(publicBytes), PrivateKey(privateBytes))
        }
    }
    
    fun sign(data: UByteArray): Signature {
        // TODO: Implement actual Ed25519 signing
        return Signature(ByteArray(64) { it.toByte() }.toUByteArray())
    }
}

class X25519KeyExchange {
    companion object {
        fun perform(keypair: Ed25519Keypair, publicKey: PublicKey): SessionKey {
            // TODO: Implement actual X25519 key exchange
            return SessionKey(ByteArray(32) { it.toByte() }.toUByteArray())
        }
    }
}

class ChaCha20Poly1305 {
    companion object {
        fun encrypt(data: UByteArray, key: SessionKey, nonce: UByteArray): EncryptedData {
            // TODO: Implement actual ChaCha20-Poly1305 encryption
            return EncryptedData(data, ByteArray(16) { it.toByte() }.toUByteArray())
        }
        
        fun decrypt(data: UByteArray, key: SessionKey, nonce: UByteArray, tag: UByteArray): UByteArray {
            // TODO: Implement actual ChaCha20-Poly1305 decryption
            return data
        }
    }
}

@Serializable
data class EncryptedData(val payload: UByteArray, val tag: UByteArray)

// ===== PROTOCOL ENUMERATIONS =====

/**
 * Protocol enumerations as specified in the protocol specification
 */
@Serializable
enum class TrikeShedProtocolEnum(val id: UByte) {
    WIRE_PROTO(1u),
    DHT_KADEMLIA(2u),
    GOSSIP(3u),
    QUIC(4u),
    HTTP(5u),
    ISAM(6u),
    JSON_RPC(7u),
    TENSOR_PROTO(8u)
}

@Serializable
enum class SerializationFormat(val id: UByte) {
    TRIKESHED_NATIVE(1u),
    CBOR(2u),
    MSGPACK(3u),
    JSON(4u),
    PROTOBUF(5u)
}

@Serializable
enum class NativeTypeId(val id: UByte) {
    NULL(0u),
    BOOLEAN(1u),
    BYTE(2u),
    SHORT(3u),
    INT(4u),
    LONG(5u),
    FLOAT(6u),
    DOUBLE(7u),
    STRING(8u),
    BYTEARRAY(9u),
    SERIES(10u),
    TENSOR(11u),
    JOIN(12u),
    IOMEMENTO(13u)
}

// ===== UTILITY TYPES =====

@Serializable
expect value class Duration(val millis: Long)

// ===== EXTENSION FUNCTIONS =====

/**
 * Extension functions for protocol types
 */
fun ByteArray.toUByteArray(): UByteArray = this.map { it.toUByte() }.toUByteArray()

fun UByteArray.toByteArray(): ByteArray = this.map { it.toByte() }.toByteArray()

fun generateNonce(): UByteArray = ByteArray(24) { it.toByte() }.toUByteArray()

// ===== HTTP PROTOCOL TYPES =====

/**
 * HTTP protocol versions
 */
@Serializable
enum class HTTPProtocol {
    HTTP_0_9,
    HTTP_1_0,
    HTTP_1_1,
    HTTP_2,
    HTTP_3
}

/**
 * HTTP request interface
 */
interface HTTPRequest {
    val method: String
    val path: String
    val headers: Map<String, List<String>>
    val body: ByteArray?
    val protocolHint: HTTPProtocol?
}

/**
 * HTTP response interface
 */
interface HTTPResponse {
    val status: Int
    val headers: Map<String, List<String>>
    val body: ByteArray?
    val protocol: HTTPProtocol
}

/**
 * HTTP chunk for streaming
 */
@Serializable
data class HTTPChunk(
    val data: ByteArray,
    val final: Boolean = false
)

/**
 * Request handle interface
 */
interface RequestHandle {
    val protocol: HTTPProtocol
    suspend fun execute(request: HTTPRequest): Result<HTTPResponse>
    suspend fun stream(request: HTTPRequest): kotlinx.coroutines.flow.Flow<HTTPChunk>
    suspend fun upgrade(to: UpgradeProtocol): Result<UpgradedConnection>
}

/**
 * Upgrade protocols
 */
@Serializable
enum class UpgradeProtocol {
    WEBSOCKET,
    HTTP_2,
    HTTP_3
}

/**
 * Upgraded connection interface
 */
interface UpgradedConnection {
    val protocol: UpgradeProtocol
    suspend fun send(data: ByteArray): Result<Unit>
    suspend fun receive(): Result<ByteArray>
    suspend fun close()
} 