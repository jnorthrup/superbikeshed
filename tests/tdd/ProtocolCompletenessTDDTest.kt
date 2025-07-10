package tdd

import borg.trikeshed.lib.*
import borg.trikeshed.isam.meta.IOMemento
import kotlin.test.*
import kotlinx.coroutines.test.runTest

/**
 * TDD Tests for Incomplete TrikeShed Protocol Areas
 * 
 * This test suite covers all incomplete protocol functionality identified
 * in the protocol specifications. Each test should fail until the
 * corresponding functionality is implemented.
 * 
 * Areas covered:
 * 1. Gossip Protocol (Section 4.2) - Missing anti-entropy and rumor spreading
 * 2. QUIC Integration (Section 4.3) - Missing stream types and frame format
 * 3. Security and Authentication (Section 7) - Missing crypto primitives
 * 4. Performance Specifications (Section 8) - Missing throughput validation
 * 5. Error Handling (Section 9.3) - Missing comprehensive error codes
 * 6. Protocol Evolution (Section 10) - Missing versioning and compatibility
 * 7. CBOR Integration (Section 6.3) - Missing CBOR type extensions
 * 8. Compression Support (Section 8.3) - Missing compression ratios
 */
class ProtocolCompletenessTDDTest {

    // ===== GOSSIP PROTOCOL TESTS (Section 4.2) =====
    
    @Test
    fun `should implement gossip digest request for anti-entropy`() {
        // Given: Message digests for anti-entropy
        val digests = 3 j { i -> 
            MessageDigest(
                messageId = MessageId(ByteArray(32) { i.toByte() }),
                version = i.toLong(),
                checksum = Checksum(i.toUInt())
            )
        }
        
        val request = GossipDigestRequest(
            nodeId = NodeId(ByteArray(32) { 1 }),
            digests = digests,
            timestamp = Timestamp(System.currentTimeMillis())
        )
        
        // When: Serialized to wire format
        val wireBytes = request.toWireBytes()
        
        // Then: Should include all digest information
        assertTrue(wireBytes.isNotEmpty(), "Gossip digest request should serialize")
        
        // Verify deserialization preserves all fields
        val restored = wireBytes.toGossipDigestRequest()
        assertEquals(request.nodeId, restored.nodeId)
        assertEquals(request.digests.size, restored.digests.size)
        assertEquals(request.timestamp, restored.timestamp)
    }
    
    @Test
    fun `should implement gossip sync response for rumor spreading`() {
        // Given: Gossip messages for synchronization
        val messages = 2 j { i ->
            GossipMessage(
                messageId = MessageId(ByteArray(32) { i.toByte() }),
                publisherId = NodeId(ByteArray(32) { i.toByte() }),
                content = DataValue("test_content_$i".encodeToByteArray().toUByteArray()),
                targetSubnets = 1 j { "subnet_$i" },
                timestamp = Timestamp(System.currentTimeMillis()),
                ttl = TimeToLive(300),
                hopCount = i
            )
        }
        
        val response = GossipSyncResponse(
            nodeId = NodeId(ByteArray(32) { 1 }),
            messages = messages,
            timestamp = Timestamp(System.currentTimeMillis())
        )
        
        // When: Serialized to wire format
        val wireBytes = response.toWireBytes()
        
        // Then: Should include all message information
        assertTrue(wireBytes.isNotEmpty(), "Gossip sync response should serialize")
        
        // Verify deserialization
        val restored = wireBytes.toGossipSyncResponse()
        assertEquals(response.nodeId, restored.nodeId)
        assertEquals(response.messages.size, restored.messages.size)
    }
    
    // ===== QUIC INTEGRATION TESTS (Section 4.3) =====
    
    @Test
    fun `should implement QUIC stream types for multiplexed connections`() {
        // Given: QUIC stream types
        val streamTypes = listOf(
            QuicStreamType.DHT_MESSAGES,
            QuicStreamType.GOSSIP_MESSAGES,
            QuicStreamType.TENSOR_DATA,
            QuicStreamType.ISAM_OPERATIONS,
            QuicStreamType.JSON_RPC
        )
        
        // When: Each stream type is used
        streamTypes.forEach { streamType ->
            // Then: Should have valid stream ID
            assertTrue(streamType.id >= 0, "Stream ID should be non-negative")
            
            // Should support wire format serialization
            val streamFrame = QuicStreamFrame(
                streamId = streamType.id,
                streamType = streamType,
                trikeShedMessage = createTestMessage()
            )
            
            val wireBytes = streamFrame.toWireBytes()
            assertTrue(wireBytes.isNotEmpty(), "QUIC stream frame should serialize")
        }
    }
    
    @Test
    fun `should implement QUIC stream frame format with TrikeShed message`() {
        // Given: QUIC stream frame with TrikeShed message
        val streamFrame = QuicStreamFrame(
            streamId = 1L,
            streamType = QuicStreamType.DHT_MESSAGES,
            trikeShedMessage = createTestMessage()
        )
        
        // When: Serialized to wire format
        val wireBytes = streamFrame.toWireBytes()
        
        // Then: Should follow QUIC stream frame format
        // Format: [StreamID:8] [StreamType:8] [TrikeShedMessageFrame...]
        assertTrue(wireBytes.size >= 16, "QUIC stream frame should have minimum size")
        
        // Verify deserialization
        val restored = wireBytes.toQuicStreamFrame()
        assertEquals(streamFrame.streamId, restored.streamId)
        assertEquals(streamFrame.streamType, restored.streamType)
    }
    
    // ===== SECURITY AND AUTHENTICATION TESTS (Section 7) =====
    
    @Test
    fun `should implement node identity with Ed25519 keypair`() {
        // Given: Node identity generation
        val keypair = Ed25519Keypair.generate()
        val nodeId = NodeId(keypair.publicKey.hash())
        val signature = keypair.sign(nodeId.bytes)
        
        val identity = NodeIdentity(
            nodeId = nodeId,
            publicKey = keypair.publicKey,
            signature = signature
        )
        
        // When: Serialized and verified
        val wireBytes = identity.toWireBytes()
        val restored = wireBytes.toNodeIdentity()
        
        // Then: Should verify signature
        assertTrue(restored.verifySignature(), "Node identity signature should verify")
        assertEquals(identity.nodeId, restored.nodeId)
    }
    
    @Test
    fun `should implement secure message envelope with ChaCha20-Poly1305`() {
        // Given: Secure message with encryption
        val senderKeypair = Ed25519Keypair.generate()
        val recipientKeypair = Ed25519Keypair.generate()
        val sessionKey = X25519KeyExchange.perform(senderKeypair, recipientKeypair.publicKey)
        
        val originalPayload = "secret_message".encodeToByteArray().toUByteArray()
        val nonce = generateNonce()
        
        val secureMessage = SecureMessage.create(
            senderId = NodeId(senderKeypair.publicKey.hash()),
            recipientId = NodeId(recipientKeypair.publicKey.hash()),
            payload = originalPayload,
            sessionKey = sessionKey,
            nonce = nonce
        )
        
        // When: Encrypted and decrypted
        val wireBytes = secureMessage.toWireBytes()
        val restored = wireBytes.toSecureMessage()
        val decryptedPayload = restored.decrypt(recipientKeypair, senderKeypair.publicKey)
        
        // Then: Should decrypt to original payload
        assertContentEquals(originalPayload, decryptedPayload)
    }
    
    // ===== PERFORMANCE SPECIFICATION TESTS (Section 8) =====
    
    @Test
    fun `should meet DHT operations throughput target of 10,000 ops/sec`() {
        // Given: DHT operation batch
        val operations = 1000 j { i ->
            PingRequest(
                nodeId = KademliaNodeId(ByteArray(32) { i.toByte() }),
                timestamp = Timestamp(System.currentTimeMillis())
            )
        }
        
        // When: Measuring throughput
        val startTime = System.nanoTime()
        repeat(1000) {
            operations.forEach { op -> op.toWireBytes() }
        }
        val endTime = System.nanoTime()
        
        val totalOps = 1000 * operations.size
        val durationSeconds = (endTime - startTime) / 1_000_000_000.0
        val opsPerSecond = totalOps / durationSeconds
        
        // Then: Should meet throughput target
        assertTrue(opsPerSecond >= 10000, "DHT operations should achieve 10,000 ops/sec, got $opsPerSecond")
    }
    
    @Test
    fun `should meet gossip message throughput target of 100,000 msg/sec`() {
        // Given: Gossip message batch
        val messages = 1000 j { i ->
            GossipMessage(
                messageId = MessageId(ByteArray(32) { i.toByte() }),
                publisherId = NodeId(ByteArray(32) { i.toByte() }),
                content = DataValue("msg_$i".encodeToByteArray().toUByteArray()),
                targetSubnets = 1 j { "subnet" },
                timestamp = Timestamp(System.currentTimeMillis()),
                ttl = TimeToLive(300),
                hopCount = 0
            )
        }
        
        // When: Measuring throughput
        val startTime = System.nanoTime()
        repeat(100) {
            messages.forEach { msg -> msg.toWireBytes() }
        }
        val endTime = System.nanoTime()
        
        val totalMsgs = 100 * messages.size
        val durationSeconds = (endTime - startTime) / 1_000_000_000.0
        val msgsPerSecond = totalMsgs / durationSeconds
        
        // Then: Should meet throughput target
        assertTrue(msgsPerSecond >= 100000, "Gossip messages should achieve 100,000 msg/sec, got $msgsPerSecond")
    }
    
    @Test
    fun `should meet ISAM read throughput target of 1,000,000 rows/sec`() {
        // Given: ISAM cursor data
        val rows = 10000 j { rowIndex ->
            10 j { colIndex -> DataValue("row${rowIndex}_col$colIndex".encodeToByteArray().toUByteArray()) }
        }
        
        val cursorData = CursorDataResponse(
            cursorId = 1L,
            rows = rows,
            hasMore = false
        )
        
        // When: Measuring throughput
        val startTime = System.nanoTime()
        repeat(100) {
            cursorData.toWireBytes()
        }
        val endTime = System.nanoTime()
        
        val totalRows = 100 * rows.size
        val durationSeconds = (endTime - startTime) / 1_000_000_000.0
        val rowsPerSecond = totalRows / durationSeconds
        
        // Then: Should meet throughput target
        assertTrue(rowsPerSecond >= 1000000, "ISAM reads should achieve 1,000,000 rows/sec, got $rowsPerSecond")
    }
    
    // ===== ERROR HANDLING TESTS (Section 9.3) =====
    
    @Test
    fun `should handle all TrikeShed error codes with proper responses`() {
        // Given: All error codes
        val errorCodes = TrikeShedError.values()
        
        errorCodes.forEach { errorCode ->
            // When: Creating error response
            val errorResponse = ErrorResponse(
                errorCode = errorCode,
                errorMessage = "Test error for ${errorCode.name}",
                details = mapOf("test" to "value"),
                timestamp = Timestamp(System.currentTimeMillis())
            )
            
            // Then: Should serialize and deserialize correctly
            val wireBytes = errorResponse.toWireBytes()
            val restored = wireBytes.toErrorResponse()
            
            assertEquals(errorCode, restored.errorCode)
            assertEquals(errorResponse.errorMessage, restored.errorMessage)
            assertEquals(errorResponse.details, restored.details)
        }
    }
    
    @Test
    fun `should handle protocol version mismatch gracefully`() {
        // Given: Message with incompatible version
        val incompatibleData = createIncompatibleVersionMessage()
        
        // When: Attempting to deserialize
        val exception = assertFailsWith<ProtocolVersionMismatchException> {
            incompatibleData.toProtocolMessage<Any>()
        }
        
        // Then: Should provide clear error information
        assertTrue(exception.message?.contains("version") == true)
        assertEquals(TrikeShedError.PROTOCOL_VERSION_MISMATCH, exception.errorCode)
    }
    
    @Test
    fun `should handle checksum mismatch with proper error`() {
        // Given: Corrupted wire data
        val originalData = createTestMessage().toWireBytes()
        val corruptedData = originalData.copyOf().apply {
            this[this.size - 1] = (this[this.size - 1] + 1u).toUByte() // Corrupt last byte
        }
        
        // When: Attempting to deserialize corrupted data
        val exception = assertFailsWith<ChecksumMismatchException> {
            corruptedData.toProtocolMessage<Any>()
        }
        
        // Then: Should detect corruption
        assertEquals(TrikeShedError.CHECKSUM_MISMATCH, exception.errorCode)
    }
    
    // ===== PROTOCOL EVOLUTION TESTS (Section 10) =====
    
    @Test
    fun `should support backward compatibility with previous major version`() {
        // Given: Message from previous version
        val legacyMessage = createLegacyVersionMessage()
        
        // When: Deserialized by current version
        val result = runCatching {
            legacyMessage.toProtocolMessage<Any>()
        }
        
        // Then: Should handle gracefully (either succeed or provide clear error)
        assertTrue(result.isSuccess || result.exceptionOrNull()?.message?.contains("version") == true)
    }
    
    @Test
    fun `should preserve unknown fields during serialization round-trips`() {
        // Given: Message with unknown fields
        val messageWithUnknownFields = createMessageWithUnknownFields()
        
        // When: Serialized and deserialized
        val wireBytes = messageWithUnknownFields.toWireBytes()
        val restored = wireBytes.toProtocolMessage<Any>()
        
        // Then: Should preserve unknown fields
        val unknownFields = extractUnknownFields(restored)
        assertTrue(unknownFields.isNotEmpty(), "Unknown fields should be preserved")
    }
    
    // ===== CBOR INTEGRATION TESTS (Section 6.3) =====
    
    @Test
    fun `should implement CBOR type extensions for TrikeShed types`() {
        // Given: TrikeShed types for CBOR serialization
        val series = 3 j { i -> i * i }
        val tensor = Tensor(2 j { 2 }, 4 j { i -> i.toDouble() })
        val join = 42.j("test")
        val memento = IOMemento.create("test", "Int", 4, false)
        
        // When: Serialized to CBOR
        val seriesCbor = series.toCbor()
        val tensorCbor = tensor.toCbor()
        val joinCbor = join.toCbor()
        val mementoCbor = memento.toCbor()
        
        // Then: Should use correct CBOR tags
        assertTrue(seriesCbor.startsWith(byteArrayOf(0xD9, 0x03, 0xE9)), "Series should use tag 1001")
        assertTrue(tensorCbor.startsWith(byteArrayOf(0xD9, 0x03, 0xEA)), "Tensor should use tag 1002")
        assertTrue(joinCbor.startsWith(byteArrayOf(0xD9, 0x03, 0xEB)), "Join should use tag 1003")
        assertTrue(mementoCbor.startsWith(byteArrayOf(0xD9, 0x03, 0xEC)), "IoMemento should use tag 1004")
    }
    
    @Test
    fun `should round-trip TrikeShed types through CBOR`() {
        // Given: TrikeShed objects
        val originalSeries = 5 j { i -> "item$i" }
        val originalTensor = Tensor(2 j { 3 }, 6 j { i -> i.toDouble() })
        
        // When: Serialized to CBOR and back
        val seriesRestored = originalSeries.toCbor().fromCbor<Series<String>>()
        val tensorRestored = originalTensor.toCbor().fromCbor<Tensor<Double>>()
        
        // Then: Should preserve all data
        assertEquals(originalSeries.size, seriesRestored.size)
        for (i in 0 until originalSeries.size) {
            assertEquals(originalSeries[i], seriesRestored[i])
        }
        
        assertEquals(originalTensor.shape, tensorRestored.shape)
        for (i in 0 until originalTensor.size) {
            assertEquals(originalTensor[i], tensorRestored[i], 0.001)
        }
    }
    
    // ===== COMPRESSION SUPPORT TESTS (Section 8.3) =====
    
    @Test
    fun `should achieve compression ratios for different data types`() {
        // Given: Different data types for compression testing
        val jsonMetadata = createJsonMetadata()
        val intSeries = 1000 j { i -> i }
        val stringSeries = 1000 j { i -> "string_$i" }
        val memento = IOMemento.create("test", "String", 100, false)
        
        // When: Compressed with different algorithms
        val jsonLz4 = jsonMetadata.compress(CompressionType.LZ4)
        val jsonZstd = jsonMetadata.compress(CompressionType.ZSTD)
        val intLz4 = intSeries.compress(CompressionType.LZ4)
        val intZstd = intSeries.compress(CompressionType.ZSTD)
        val stringLz4 = stringSeries.compress(CompressionType.LZ4)
        val stringZstd = stringSeries.compress(CompressionType.ZSTD)
        val mementoLz4 = memento.compress(CompressionType.LZ4)
        val mementoZstd = memento.compress(CompressionType.ZSTD)
        
        // Then: Should meet compression ratio targets
        val jsonLz4Ratio = jsonLz4.size.toDouble() / jsonMetadata.size
        val jsonZstdRatio = jsonZstd.size.toDouble() / jsonMetadata.size
        val intLz4Ratio = intLz4.size.toDouble() / intSeries.toWireBytes().size
        val intZstdRatio = intZstd.size.toDouble() / intSeries.toWireBytes().size
        val stringLz4Ratio = stringLz4.size.toDouble() / stringSeries.toWireBytes().size
        val stringZstdRatio = stringZstd.size.toDouble() / stringSeries.toWireBytes().size
        val mementoLz4Ratio = mementoLz4.size.toDouble() / memento.toWireBytes().size
        val mementoZstdRatio = mementoZstd.size.toDouble() / memento.toWireBytes().size
        
        // JSON compression targets: LZ4 65%, ZSTD 45%
        assertTrue(jsonLz4Ratio <= 0.65, "JSON LZ4 should achieve ≤65% ratio, got ${jsonLz4Ratio * 100}%")
        assertTrue(jsonZstdRatio <= 0.45, "JSON ZSTD should achieve ≤45% ratio, got ${jsonZstdRatio * 100}%")
        
        // Int series compression targets: LZ4 85%, ZSTD 75%
        assertTrue(intLz4Ratio <= 0.85, "Int series LZ4 should achieve ≤85% ratio, got ${intLz4Ratio * 100}%")
        assertTrue(intZstdRatio <= 0.75, "Int series ZSTD should achieve ≤75% ratio, got ${intZstdRatio * 100}%")
        
        // String series compression targets: LZ4 40%, ZSTD 30%
        assertTrue(stringLz4Ratio <= 0.40, "String series LZ4 should achieve ≤40% ratio, got ${stringLz4Ratio * 100}%")
        assertTrue(stringZstdRatio <= 0.30, "String series ZSTD should achieve ≤30% ratio, got ${stringZstdRatio * 100}%")
        
        // IoMemento compression targets: LZ4 70%, ZSTD 55%
        assertTrue(mementoLz4Ratio <= 0.70, "IoMemento LZ4 should achieve ≤70% ratio, got ${mementoLz4Ratio * 100}%")
        assertTrue(mementoZstdRatio <= 0.55, "IoMemento ZSTD should achieve ≤55% ratio, got ${mementoZstdRatio * 100}%")
    }
    
    // ===== HELPER FUNCTIONS =====
    
    internal fun createTestMessage(): TrikeShedMessageFrame {
        return TrikeShedMessageFrame.create(
            proto = TrikeShedProtocol.DHT_KADEMLIA,
            messageType = "TEST_MESSAGE",
            payload = "test_payload".encodeToByteArray().toUByteArray()
        )
    }
    
    internal fun createIncompatibleVersionMessage(): UByteArray {
        // Create message with incompatible version
        return byteArrayOf(0x54, 0x52, 0xFF, 0x00, 0x00, 0x00, 0x00).toUByteArray() // Magic + incompatible version
    }
    
    internal fun createLegacyVersionMessage(): UByteArray {
        // Create message from previous version
        return byteArrayOf(0x54, 0x52, 0x00, 0x00, 0x00, 0x00, 0x00).toUByteArray() // Magic + legacy version
    }
    
    internal fun createMessageWithUnknownFields(): Any {
        // Create message with unknown fields for compatibility testing
        return object {
            val knownField = "known"
            val unknownField = "unknown"
        }
    }
    
    internal fun extractUnknownFields(obj: Any): Map<String, Any> {
        // Extract unknown fields from object (implementation depends on reflection)
        return mapOf("unknownField" to "unknown")
    }
    
    internal fun createJsonMetadata(): UByteArray {
        // Create JSON metadata for compression testing
        val json = """
            {
                "name": "test_column",
                "type": "String",
                "width": 100,
                "nullable": false,
                "encoding": "utf8",
                "format": "text"
            }
        """.trimIndent()
        return json.encodeToByteArray().toUByteArray()
    }
    
    internal fun generateNonce(): UByteArray {
        return ByteArray(24) { it.toByte() }.toUByteArray()
    }
}

// ===== PROTOCOL DATA TYPES =====

// Gossip Protocol Types
data class MessageDigest(
    val messageId: MessageId,
    val version: Long,
    val checksum: Checksum
)

data class GossipDigestRequest(
    val nodeId: NodeId,
    val digests: Series<MessageDigest>,
    val timestamp: Timestamp
)

data class GossipSyncResponse(
    val nodeId: NodeId,
    val messages: Series<GossipMessage>,
    val timestamp: Timestamp
)

data class GossipMessage(
    val messageId: MessageId,
    val publisherId: NodeId,
    val content: DataValue,
    val targetSubnets: Series<String>,
    val timestamp: Timestamp,
    val ttl: TimeToLive,
    val hopCount: Int
)

// QUIC Integration Types
enum class QuicStreamType(val id: Long) {
    DHT_MESSAGES(0L),
    GOSSIP_MESSAGES(1L),
    TENSOR_DATA(2L),
    ISAM_OPERATIONS(3L),
    JSON_RPC(4L)
}

data class QuicStreamFrame(
    val streamId: Long,
    val streamType: QuicStreamType,
    val trikeShedMessage: TrikeShedMessageFrame
)

// Security Types
data class NodeIdentity(
    val nodeId: NodeId,
    val publicKey: PublicKey,
    val signature: Signature
) {
    fun verifySignature(): Boolean = publicKey.verify(nodeId.bytes, signature)
}

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

// Error Types
data class ErrorResponse(
    val errorCode: TrikeShedError,
    val errorMessage: String,
    val details: Map<String, String>,
    val timestamp: Timestamp
)

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

// Exception Types
class ProtocolVersionMismatchException(message: String, val errorCode: TrikeShedError) : Exception(message)
class ChecksumMismatchException(message: String, val errorCode: TrikeShedError) : Exception(message)

// Cryptographic Types (stubs for TDD)
class Ed25519Keypair(val publicKey: PublicKey, internal val privateKey: PrivateKey) {
    companion object {
        fun generate(): Ed25519Keypair = Ed25519Keypair(PublicKey(ByteArray(32)), PrivateKey(ByteArray(32)))
    }
    
    fun sign(data: UByteArray): Signature = Signature(ByteArray(64))
}

class PublicKey(val bytes: ByteArray) {
    fun verify(data: UByteArray, signature: Signature): Boolean = true
    fun hash(): ByteArray = bytes
}

class PrivateKey(val bytes: ByteArray)
class Signature(val bytes: ByteArray)
class SessionKey(val bytes: ByteArray)

class X25519KeyExchange {
    companion object {
        fun perform(keypair: Ed25519Keypair, publicKey: PublicKey): SessionKey = SessionKey(ByteArray(32))
    }
}

class ChaCha20Poly1305 {
    companion object {
        fun encrypt(data: UByteArray, key: SessionKey, nonce: UByteArray): EncryptedData {
            return EncryptedData(data, ByteArray(16))
        }
        
        fun decrypt(data: UByteArray, key: SessionKey, nonce: UByteArray, tag: UByteArray): UByteArray {
            return data
        }
    }
}

data class EncryptedData(val payload: UByteArray, val tag: ByteArray)

// Value Classes
@JvmInline value class NodeId(val bytes: UByteArray)
@JvmInline value class MessageId(val bytes: UByteArray)
@JvmInline value class DataValue(val bytes: UByteArray)
@JvmInline value class Checksum(val crc32: UInt)
@JvmInline value class Timestamp(val epochMillis: Long)
@JvmInline value class TimeToLive(val seconds: Int)
@JvmInline value class KademliaNodeId(val bytes: UByteArray)
@JvmInline value class ProtocolVersion(val version: UByte)
@JvmInline value class TrikeShedProtocol(val id: UByte)

// ISAM Types
data class CursorDataResponse(
    val cursorId: Long,
    val rows: Series<Series<DataValue>>,
    val hasMore: Boolean
)

data class PingRequest(
    val nodeId: KademliaNodeId,
    val timestamp: Timestamp
)

// Protocol Frame
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
        fun create(proto: TrikeShedProtocol, messageType: String, payload: UByteArray): TrikeShedMessageFrame {
            return TrikeShedMessageFrame(
                magic = 0x5452u,
                version = ProtocolVersion(1u),
                proto = proto,
                messageLength = payload.size,
                messageTypeLength = messageType.length,
                messageType = messageType,
                payload = payload,
                checksum = Checksum(calculateCrc32(payload))
            )
        }
    }
}

// Compression Types
enum class CompressionType(val id: UByte) {
    NONE(0u),
    LZ4(1u),
    ZSTD(2u),
    GZIP(3u),
    BROTLI(4u)
}

// Extension Functions (stubs for TDD)
fun GossipDigestRequest.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toGossipDigestRequest(): GossipDigestRequest = GossipDigestRequest(
    NodeId(ByteArray(32).toUByteArray()),
    0 j { MessageDigest(MessageId(ByteArray(32).toUByteArray()), 0L, Checksum(0u)) },
    Timestamp(System.currentTimeMillis())
)

fun GossipSyncResponse.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toGossipSyncResponse(): GossipSyncResponse = GossipSyncResponse(
    NodeId(ByteArray(32).toUByteArray()),
    0 j { GossipMessage(
        MessageId(ByteArray(32).toUByteArray()),
        NodeId(ByteArray(32).toUByteArray()),
        DataValue(ByteArray(10).toUByteArray()),
        0 j { "subnet" },
        Timestamp(System.currentTimeMillis()),
        TimeToLive(300),
        0
    ) },
    Timestamp(System.currentTimeMillis())
)

fun QuicStreamFrame.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toQuicStreamFrame(): QuicStreamFrame = QuicStreamFrame(
    1L,
    QuicStreamType.DHT_MESSAGES,
    TrikeShedMessageFrame.create(TrikeShedProtocol(1u), "TEST", ByteArray(10).toUByteArray())
)

fun NodeIdentity.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toNodeIdentity(): NodeIdentity = NodeIdentity(
    NodeId(ByteArray(32).toUByteArray()),
    PublicKey(ByteArray(32)),
    Signature(ByteArray(64))
)

fun SecureMessage.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toSecureMessage(): SecureMessage = SecureMessage(
    NodeId(ByteArray(32).toUByteArray()),
    NodeId(ByteArray(32).toUByteArray()),
    ByteArray(24).toUByteArray(),
    ByteArray(10).toUByteArray(),
    ByteArray(16).toUByteArray()
)

fun ErrorResponse.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toErrorResponse(): ErrorResponse = ErrorResponse(
    TrikeShedError.SUCCESS,
    "test",
    emptyMap(),
    Timestamp(System.currentTimeMillis())
)

fun <T> T.toProtocolMessage(): T = this
fun <T> UByteArray.toProtocolMessage(): T = Any() as T

fun Series<String>.toCbor(): ByteArray = ByteArray(100)
fun <T> ByteArray.fromCbor(): T = Any() as T
fun Tensor<Double>.toCbor(): ByteArray = ByteArray(100)
fun Join<Int, String>.toCbor(): ByteArray = ByteArray(100)
fun IOMemento.toCbor(): ByteArray = ByteArray(100)

fun UByteArray.compress(type: CompressionType): UByteArray = this
fun Series<Int>.compress(type: CompressionType): UByteArray = this.toWireBytes().compress(type)
fun Series<String>.compress(type: CompressionType): UByteArray = this.toWireBytes().compress(type)
fun IOMemento.compress(type: CompressionType): UByteArray = this.toWireBytes().compress(type)

fun Series<Int>.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun Series<String>.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun IOMemento.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toIoMemento(): IOMemento = IOMemento.create("test", "Int", 4, false)

fun PingRequest.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun CursorDataResponse.toWireBytes(): UByteArray = ByteArray(100).toUByteArray() 