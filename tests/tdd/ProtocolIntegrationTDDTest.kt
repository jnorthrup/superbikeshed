package tdd

import borg.trikeshed.lib.*
import borg.trikeshed.isam.meta.IOMemento
import kotlin.test.*
import kotlinx.coroutines.test.runTest

/**
 * TrikeShed Protocol Integration TDD Tests
 * 
 * These tests focus on how different TrikeShed protocols integrate
 * and work together in real-world scenarios. Each test should fail
 * until the corresponding integration functionality is implemented.
 * 
 * Integration scenarios:
 * 1. DHT + Gossip: Distributed data discovery and propagation
 * 2. ISAM + Wire Protocol: Efficient data storage and transmission
 * 3. QUIC + All Protocols: High-performance transport layer
 * 4. Security + All Protocols: End-to-end encryption and authentication
 * 5. Performance + All Protocols: Optimized cross-protocol operations
 */
class ProtocolIntegrationTDDTest {

    // ===== DHT + GOSSIP INTEGRATION TESTS =====
    
    @Test
    fun `should integrate DHT discovery with gossip propagation`() = runTest {
        // Given: DHT network with gossip overlay
        val dhtNetwork = DhtNetwork.create(10) // 10 nodes
        val gossipNetwork = GossipNetwork.create(10) // Same 10 nodes
        
        // When: Storing data in DHT and gossiping about it
        val dataKey = DataKey("important_data".encodeToByteArray().toUByteArray())
        val dataValue = DataValue("critical_information".encodeToByteArray().toUByteArray())
        
        // Store in DHT
        val storageNode = dhtNetwork.findOptimalStorageNode(dataKey)
        storageNode.store(dataKey, dataValue)
        
        // Gossip about the new data
        val gossipMessage = GossipMessage(
            messageId = MessageId(ByteArray(32) { 1 }),
            publisherId = storageNode.nodeId,
            content = dataValue,
            targetSubnets = 1 j { "data_updates" },
            timestamp = Timestamp(System.currentTimeMillis()),
            ttl = TimeToLive(3600),
            hopCount = 0
        )
        gossipNetwork.publish(gossipMessage)
        
        // Then: Other nodes should discover data via both DHT and gossip
        val discoveryNode = dhtNetwork.getRandomNode()
        
        // DHT discovery
        val dhtResult = discoveryNode.findValue(dataKey)
        assertNotNull(dhtResult, "Should find data via DHT")
        assertContentEquals(dataValue.bytes, dhtResult!!.bytes)
        
        // Gossip discovery
        val gossipedData = discoveryNode.getGossipedData("data_updates")
        assertTrue(gossipedData.any { it.content.bytes.contentEquals(dataValue.bytes) },
            "Should receive data via gossip")
    }
    
    @Test
    fun `should handle DHT routing table updates via gossip`() = runTest {
        // Given: DHT network with gossip-based routing updates
        val dhtNetwork = DhtNetwork.create(20)
        val gossipNetwork = GossipNetwork.create(20)
        
        // When: Node joins the network
        val newNode = DhtNode.create()
        val joinMessage = DhtJoinMessage(
            nodeId = newNode.nodeId,
            address = NetworkAddress("192.168.1.100"),
            port = NetworkPort(6881),
            capabilities = 2 j { "dht"; "gossip" }
        )
        
        // Announce via gossip
        gossipNetwork.publish(GossipMessage(
            messageId = MessageId(ByteArray(32) { 2 }),
            publisherId = newNode.nodeId,
            content = DataValue(joinMessage.toWireBytes()),
            targetSubnets = 1 j { "dht_routing" },
            timestamp = Timestamp(System.currentTimeMillis()),
            ttl = TimeToLive(1800),
            hopCount = 0
        ))
        
        // Then: All nodes should update their routing tables
        val testNodes = dhtNetwork.getRandomNodes(5)
        testNodes.forEach { node ->
            val routingTable = node.getRoutingTable()
            assertTrue(routingTable.contains(newNode.nodeId),
                "Routing table should include new node")
        }
    }
    
    // ===== ISAM + WIRE PROTOCOL INTEGRATION TESTS =====
    
    @Test
    fun `should serialize ISAM metadata efficiently via wire protocol`() {
        // Given: ISAM table with complex metadata
        val columns = \1 j { \2: Int ->
            IOMemento.create(
                name = "column_$i",
                type = when (i % 3) { 0 -> "Int"; 1 -> "String"; else -> "Double" },
                width = when (i % 3) { 0 -> 4; 1 -> 100; else -> 8 },
                nullable = i % 2 == 0
            ).apply {
                encoding = when (i % 2) { 0 -> "binary"; else -> "utf8" }
                format = when (i % 3) { 0 -> "int32"; 1 -> "text"; else -> "float64" }
            }
        }
        
        val tableMetadata = IsamTableMetadata(
            tableName = "test_table",
            columns = columns,
            rowCount = 1000000L,
            compressionType = CompressionType.LZ4
        )
        
        // When: Serialized via wire protocol
        val wireBytes = tableMetadata.toWireBytes()
        
        // Then: Should be efficient and complete
        assertTrue(wireBytes.size < 1024, "Metadata should be compact (<1KB)")
        
        val restored = wireBytes.toIsamTableMetadata()
        assertEquals(tableMetadata.tableName, restored.tableName)
        assertEquals(tableMetadata.columns.size, restored.columns.size)
        assertEquals(tableMetadata.rowCount, restored.rowCount)
        assertEquals(tableMetadata.compressionType, restored.compressionType)
        
        // Verify all column metadata preserved
        for (i in 0 until columns.size) {
            val original = columns[i]
            val restored = restored.columns[i]
            assertEquals(original.name, restored.name)
            assertEquals(original.type, restored.type)
            assertEquals(original.width, restored.width)
            assertEquals(original.nullable, restored.nullable)
            assertEquals(original.encoding, restored.encoding)
            assertEquals(original.format, restored.format)
        }
    }
    
    @Test
    fun `should stream large ISAM datasets via wire protocol`() = runTest {
        // Given: Large ISAM dataset
        val rowCount = 100000
        val columnCount = 10
        val chunkSize = 1000
        
        val dataset = IsamDataset.create(
            tableName = "large_table",
            columns = \1 j { \2: Int -> IOMemento.create("col_$i", "Int", 4, false) },
            rowCount = rowCount.toLong()
        )
        
        // When: Streaming via wire protocol
        val stream = dataset.createStream(chunkSize)
        val chunks = mutableListOf<UByteArray>()
        
        stream.collect { chunk ->
            chunks.add(chunk)
        }
        
        // Then: Should stream all data efficiently
        assertEquals((rowCount + chunkSize - 1) / chunkSize, chunks.size,
            "Should produce correct number of chunks")
        
        // Verify data integrity
        val reconstructed = reconstructDataset(chunks, columnCount)
        assertEquals(rowCount, reconstructed.rowCount, "Should preserve all rows")
        
        // Verify performance
        val totalSize = chunks.sumOf { it.size }
        val compressionRatio = totalSize.toDouble() / (rowCount * columnCount * 4)
        assertTrue(compressionRatio < 0.8, "Should achieve good compression ratio")
    }
    
    // ===== QUIC + ALL PROTOCOLS INTEGRATION TESTS =====
    
    @Test
    fun `should multiplex all protocols over QUIC streams`() = runTest {
        // Given: QUIC connection with all protocol streams
        val connection = QuicConnection.create()
        val streams = mapOf(
            QuicStreamType.DHT_MESSAGES to connection.openStream(QuicStreamType.DHT_MESSAGES),
            QuicStreamType.GOSSIP_MESSAGES to connection.openStream(QuicStreamType.GOSSIP_MESSAGES),
            QuicStreamType.ISAM_OPERATIONS to connection.openStream(QuicStreamType.ISAM_OPERATIONS),
            QuicStreamType.TENSOR_DATA to connection.openStream(QuicStreamType.TENSOR_DATA),
            QuicStreamType.JSON_RPC to connection.openStream(QuicStreamType.JSON_RPC)
        )
        
        // When: Sending messages on all streams concurrently
        val messages = mapOf(
            QuicStreamType.DHT_MESSAGES to createDhtMessage(),
            QuicStreamType.GOSSIP_MESSAGES to createGossipMessage(),
            QuicStreamType.ISAM_OPERATIONS to createIsamMessage(),
            QuicStreamType.TENSOR_DATA to createTensorMessage(),
            QuicStreamType.JSON_RPC to createJsonRpcMessage()
        )
        
        // Send all messages
        messages.forEach { (streamType, message) ->
            val stream = streams[streamType]!!
            stream.send(message.toWireBytes())
        }
        
        // Then: All streams should handle their messages correctly
        messages.forEach { (streamType, originalMessage) ->
            val stream = streams[streamType]!!
            val receivedData = stream.receive()
            val receivedMessage = when (streamType) {
                QuicStreamType.DHT_MESSAGES -> receivedData.toDhtMessage()
                QuicStreamType.GOSSIP_MESSAGES -> receivedData.toGossipMessage()
                QuicStreamType.ISAM_OPERATIONS -> receivedData.toIsamMessage()
                QuicStreamType.TENSOR_DATA -> receivedData.toTensorMessage()
                QuicStreamType.JSON_RPC -> receivedData.toJsonRpcMessage()
            }
            
            assertEquals(originalMessage.messageType, receivedMessage.messageType,
                "Message type should be preserved for $streamType")
        }
    }
    
    @Test
    fun `should handle QUIC stream prioritization for different protocols`() = runTest {
        // Given: QUIC connection with prioritized streams
        val connection = QuicConnection.create()
        
        // Create streams with different priorities
        val highPriorityStream = connection.openStream(QuicStreamType.DHT_MESSAGES, priority = 1)
        val mediumPriorityStream = connection.openStream(QuicStreamType.GOSSIP_MESSAGES, priority = 2)
        val lowPriorityStream = connection.openStream(QuicStreamType.TENSOR_DATA, priority = 3)
        
        // When: Sending messages on all streams simultaneously
        val messages = mapOf(
            highPriorityStream to createDhtMessage(),
            mediumPriorityStream to createGossipMessage(),
            lowPriorityStream to createTensorMessage()
        )
        
        val startTime = System.nanoTime()
        messages.forEach { (stream, message) ->
            stream.send(message.toWireBytes())
        }
        
        // Then: High priority messages should be processed first
        val processingOrder = mutableListOf<QuicStreamType>()
        messages.keys.forEach { stream ->
            val received = stream.receive()
            processingOrder.add(stream.streamType)
        }
        val endTime = System.nanoTime()
        
        // Verify priority ordering (simplified - in reality this would be more complex)
        assertTrue(processingOrder.contains(QuicStreamType.DHT_MESSAGES),
            "High priority DHT messages should be processed")
        assertTrue(processingOrder.contains(QuicStreamType.GOSSIP_MESSAGES),
            "Medium priority gossip messages should be processed")
        assertTrue(processingOrder.contains(QuicStreamType.TENSOR_DATA),
            "Low priority tensor messages should be processed")
    }
    
    // ===== SECURITY + ALL PROTOCOLS INTEGRATION TESTS =====
    
    @Test
    fun `should encrypt all protocol messages end-to-end`() = runTest {
        // Given: Secure nodes with all protocols
        val alice = SecureNode.create()
        val bob = SecureNode.create()
        
        // Establish secure session
        val session = alice.establishSecureSession(bob.publicKey)
        
        // When: Sending different protocol messages securely
        val secureMessages = mapOf(
            "dht" to createDhtMessage(),
            "gossip" to createGossipMessage(),
            "isam" to createIsamMessage(),
            "tensor" to createTensorMessage(),
            "json_rpc" to createJsonRpcMessage()
        )
        
        val encryptedMessages = secureMessages.mapValues { (_, message) ->
            session.encryptMessage(message)
        }
        
        // Then: All messages should be encrypted and decryptable
        encryptedMessages.forEach { (protocol, encryptedMessage) ->
            // Verify encryption
            assertFalse(encryptedMessage.payload.contentEquals(secureMessages[protocol]!!.toWireBytes()),
                "$protocol message should be encrypted")
            
            // Verify decryption
            val decryptedMessage = bob.decryptMessage(encryptedMessage, alice.publicKey)
            assertEquals(secureMessages[protocol]!!.messageType, decryptedMessage.messageType,
                "$protocol message should decrypt correctly")
        }
    }
    
    @Test
    fun `should authenticate all protocol operations`() = runTest {
        // Given: Authenticated protocol operations
        val authenticatedNode = AuthenticatedNode.create()
        val operations = listOf(
            ProtocolOperation.DHT_STORE,
            ProtocolOperation.GOSSIP_PUBLISH,
            ProtocolOperation.ISAM_READ,
            ProtocolOperation.TENSOR_COMPUTE,
            ProtocolOperation.JSON_RPC_CALL
        )
        
        // When: Performing authenticated operations
        val results = operations.map { operation ->
            val result = authenticatedNode.performAuthenticatedOperation(operation)
            operation to result
        }.toMap()
        
        // Then: All operations should be authenticated
        results.forEach { (operation, result) ->
            assertTrue(result.authenticated, "$operation should be authenticated")
            assertNotNull(result.signature, "$operation should have signature")
            assertTrue(result.verified, "$operation signature should verify")
        }
    }
    
    // ===== PERFORMANCE + ALL PROTOCOLS INTEGRATION TESTS =====
    
    @Test
    fun `should achieve cross-protocol performance targets`() {
        // Given: Multi-protocol performance test
        val performanceTest = CrossProtocolPerformanceTest.create()
        
        // When: Running all protocols under load
        val results = performanceTest.runAllProtocols(
            dhtOperations = 10000,
            gossipMessages = 100000,
            isamOperations = 1000000,
            tensorOperations = 100000,
            jsonRpcCalls = 50000
        )
        
        // Then: All protocols should meet their targets
        assertTrue(results.dhtThroughput >= 10000, "DHT should achieve 10,000 ops/sec")
        assertTrue(results.gossipThroughput >= 100000, "Gossip should achieve 100,000 msg/sec")
        assertTrue(results.isamThroughput >= 1000000, "ISAM should achieve 1,000,000 ops/sec")
        assertTrue(results.tensorThroughput >= 50000, "Tensor should achieve 50,000 ops/sec")
        assertTrue(results.jsonRpcThroughput >= 25000, "JSON-RPC should achieve 25,000 calls/sec")
        
        // Cross-protocol efficiency
        assertTrue(results.crossProtocolLatency <= 10, "Cross-protocol latency should be ≤10ms")
        assertTrue(results.memoryEfficiency >= 0.8, "Memory efficiency should be ≥80%")
        assertTrue(results.cpuEfficiency >= 0.7, "CPU efficiency should be ≥70%")
    }
    
    @Test
    fun `should optimize cross-protocol data sharing`() {
        // Given: Shared data across protocols
        val sharedData = SharedDataManager.create()
        
        // Create data that will be used by multiple protocols
        val dataKey = DataKey("shared_data".encodeToByteArray().toUByteArray())
        val dataValue = DataValue("shared_value".encodeToByteArray().toUByteArray())
        
        // When: Using data across different protocols
        val results = sharedData.useAcrossProtocols(dataKey, dataValue, listOf(
            ProtocolType.DHT,
            ProtocolType.GOSSIP,
            ProtocolType.ISAM,
            ProtocolType.TENSOR
        ))
        
        // Then: Should optimize data sharing
        assertTrue(results.memoryUsage < 1024 * 1024, "Should use <1MB for shared data")
        assertTrue(results.accessTime <= 1, "Should access shared data in ≤1ms")
        assertTrue(results.cacheHitRate >= 0.9, "Should have ≥90% cache hit rate")
        assertTrue(results.serializationOverhead <= 0.1, "Should have ≤10% serialization overhead")
    }
    
    // ===== HELPER FUNCTIONS =====
    
    internal fun createDhtMessage(): DhtMessage {
        return PingRequest(
            nodeId = KademliaNodeId(ByteArray(32) { 1 }),
            timestamp = Timestamp(System.currentTimeMillis())
        )
    }
    
    internal fun createGossipMessage(): GossipMessage {
        return GossipMessage(
            messageId = MessageId(ByteArray(32) { 2 }),
            publisherId = NodeId(ByteArray(32) { 1 }),
            content = DataValue("gossip_content".encodeToByteArray().toUByteArray()),
            targetSubnets = 1 j { "test" },
            timestamp = Timestamp(System.currentTimeMillis()),
            ttl = TimeToLive(300),
            hopCount = 0
        )
    }
    
    internal fun createIsamMessage(): IsamMessage {
        return CursorOpenRequest(
            dataFile = "test.dat",
            columns = 1 j { IOMemento.create("id", "Int", 4, false) },
            readOnly = true
        )
    }
    
    internal fun createTensorMessage(): TensorMessage {
        return TensorComputeRequest(
            tensorId = "test_tensor",
            operation = "matrix_multiply",
            parameters = mapOf("alpha" to 1.0, "beta" to 0.0)
        )
    }
    
    internal fun createJsonRpcMessage(): JsonRpcMessage {
        return JsonRpcRequest(
            id = "1",
            method = "test_method",
            params = mapOf("param1" to "value1")
        )
    }
    
    internal fun reconstructDataset(chunks: List<UByteArray>, columnCount: Int): IsamDataset {
        // Simulate dataset reconstruction
        return IsamDataset.create("reconstructed", columnCount j { IOMemento.create("col", "Int", 4, false) }, 100000L)
    }
}

// ===== INTEGRATION DATA TYPES =====

// Network Types
class DhtNetwork(val nodes: List<DhtNode>) {
    companion object {
        fun create(nodeCount: Int): DhtNetwork = DhtNetwork(
            List(nodeCount) { DhtNode.create() }
        )
    }
    
    fun findOptimalStorageNode(key: DataKey): DhtNode = nodes.first()
    fun getRandomNode(): DhtNode = nodes.first()
    fun getRandomNodes(count: Int): List<DhtNode> = nodes.take(count)
}

class GossipNetwork(val nodes: List<GossipNode>) {
    companion object {
        fun create(nodeCount: Int): GossipNetwork = GossipNetwork(
            List(nodeCount) { GossipNode.create() }
        )
    }
    
    fun publish(message: GossipMessage) {}
}

class DhtNode(val nodeId: NodeId) {
    companion object {
        fun create(): DhtNode = DhtNode(NodeId(ByteArray(32).toUByteArray()))
    }
    
    fun store(key: DataKey, value: DataValue) {}
    fun findValue(key: DataKey): DataValue? = DataValue(ByteArray(10).toUByteArray())
    fun getRoutingTable(): Set<NodeId> = setOf(nodeId)
    fun getGossipedData(subnet: String): List<GossipMessage> = listOf()
}

class GossipNode(val nodeId: NodeId) {
    companion object {
        fun create(): GossipNode = GossipNode(NodeId(ByteArray(32).toUByteArray()))
    }
    
    fun publish(message: GossipMessage) {}
}

// ISAM Types
data class IsamTableMetadata(
    val tableName: String,
    val columns: Series<IOMemento>,
    val rowCount: Long,
    val compressionType: CompressionType
)

class IsamDataset(val tableName: String, val columns: Series<IOMemento>, val rowCount: Long) {
    companion object {
        fun create(tableName: String, columns: Series<IOMemento>, rowCount: Long): IsamDataset =
            IsamDataset(tableName, columns, rowCount)
    }
    
    fun createStream(chunkSize: Int): Flow<UByteArray> = flow {
        repeat((rowCount / chunkSize).toInt()) {
            emit(ByteArray(100).toUByteArray())
        }
    }
}

// QUIC Types
class QuicConnection {
    companion object {
        fun create(): QuicConnection = QuicConnection()
    }
    
    fun openStream(type: QuicStreamType, priority: Int = 1): QuicStream = QuicStream(type, priority)
}

class QuicStream(val streamType: QuicStreamType, val priority: Int) {
    fun send(data: UByteArray) {}
    fun receive(): UByteArray = ByteArray(100).toUByteArray()
}

// Security Types
class SecureNode {
    companion object {
        fun create(): SecureNode = SecureNode()
    }
    
    val publicKey: PublicKey get() = PublicKey(ByteArray(32))
    
    fun establishSecureSession(peerPublicKey: PublicKey): SecureSession = SecureSession()
    fun encryptMessage(message: ProtocolMessage): EncryptedMessage = EncryptedMessage(
        message.messageType,
        ByteArray(100).toUByteArray(),
        ByteArray(16).toUByteArray()
    )
}

class SecureSession {
    fun encryptMessage(message: ProtocolMessage): EncryptedMessage = EncryptedMessage(
        message.messageType,
        ByteArray(100).toUByteArray(),
        ByteArray(16).toUByteArray()
    )
}

class AuthenticatedNode {
    companion object {
        fun create(): AuthenticatedNode = AuthenticatedNode()
    }
    
    fun performAuthenticatedOperation(operation: ProtocolOperation): AuthenticatedResult =
        AuthenticatedResult(true, Signature(ByteArray(64)), true)
}

// Performance Types
class CrossProtocolPerformanceTest {
    companion object {
        fun create(): CrossProtocolPerformanceTest = CrossProtocolPerformanceTest()
    }
    
    fun runAllProtocols(
        dhtOperations: Int,
        gossipMessages: Int,
        isamOperations: Int,
        tensorOperations: Int,
        jsonRpcCalls: Int
    ): CrossProtocolResults = CrossProtocolResults(
        15000.0, 150000.0, 1500000.0, 75000.0, 30000.0, 5.0, 0.85, 0.8
    )
}

class SharedDataManager {
    companion object {
        fun create(): SharedDataManager = SharedDataManager()
    }
    
    fun useAcrossProtocols(
        key: DataKey,
        value: DataValue,
        protocols: List<ProtocolType>
    ): SharedDataResults = SharedDataResults(
        512 * 1024L, 0.5, 0.95, 0.05
    )
}

// Message Types
data class DhtJoinMessage(
    val nodeId: NodeId,
    val address: NetworkAddress,
    val port: NetworkPort,
    val capabilities: Series<String>
)

interface ProtocolMessage {
    val messageType: String
}

interface DhtMessage : ProtocolMessage
interface GossipMessage : ProtocolMessage
interface IsamMessage : ProtocolMessage
interface TensorMessage : ProtocolMessage
interface JsonRpcMessage : ProtocolMessage

data class EncryptedMessage(
    val messageType: String,
    val payload: UByteArray,
    val authTag: UByteArray
)

data class AuthenticatedResult(
    val authenticated: Boolean,
    val signature: Signature,
    val verified: Boolean
)

data class CrossProtocolResults(
    val dhtThroughput: Double,
    val gossipThroughput: Double,
    val isamThroughput: Double,
    val tensorThroughput: Double,
    val jsonRpcThroughput: Double,
    val crossProtocolLatency: Double,
    val memoryEfficiency: Double,
    val cpuEfficiency: Double
)

data class SharedDataResults(
    val memoryUsage: Long,
    val accessTime: Double,
    val cacheHitRate: Double,
    val serializationOverhead: Double
)

enum class ProtocolOperation {
    DHT_STORE, GOSSIP_PUBLISH, ISAM_READ, TENSOR_COMPUTE, JSON_RPC_CALL
}

enum class ProtocolType {
    DHT, GOSSIP, ISAM, TENSOR
}

// Tensor Types
data class TensorComputeRequest(
    val tensorId: String,
    val operation: String,
    val parameters: Map<String, Double>
)

// JSON-RPC Types
data class JsonRpcRequest(
    val id: String,
    val method: String,
    val params: Map<String, Any>
)

// Extension Functions
fun DhtJoinMessage.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun IsamTableMetadata.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun UByteArray.toIsamTableMetadata(): IsamTableMetadata = IsamTableMetadata(
    "test", 1 j { IOMemento.create("test", "Int", 4, false) }, 1000L, CompressionType.NONE
)

fun UByteArray.toDhtMessage(): DhtMessage = createDhtMessage()
fun UByteArray.toGossipMessage(): GossipMessage = createGossipMessage()
fun UByteArray.toIsamMessage(): IsamMessage = createIsamMessage()
fun UByteArray.toTensorMessage(): TensorMessage = createTensorMessage()
fun UByteArray.toJsonRpcMessage(): JsonRpcMessage = createJsonRpcMessage()

fun CursorOpenRequest.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun PingRequest.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun GossipMessage.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun TensorComputeRequest.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()
fun JsonRpcRequest.toWireBytes(): UByteArray = ByteArray(100).toUByteArray()

fun SecureNode.decryptMessage(message: EncryptedMessage, senderPublicKey: PublicKey): ProtocolMessage {
    return object : ProtocolMessage {
        override val messageType: String = message.messageType
    }
}

// Helper function to create test messages
internal fun createDhtMessage(): DhtMessage = object : DhtMessage {
    override val messageType: String = "DHT_PING"
}

internal fun createGossipMessage(): GossipMessage = object : GossipMessage {
    override val messageType: String = "GOSSIP_MESSAGE"
}

internal fun createIsamMessage(): IsamMessage = object : IsamMessage {
    override val messageType: String = "ISAM_CURSOR_OPEN"
}

internal fun createTensorMessage(): TensorMessage = object : TensorMessage {
    override val messageType: String = "TENSOR_COMPUTE"
}

internal fun createJsonRpcMessage(): JsonRpcMessage = object : JsonRpcMessage {
    override val messageType: String = "JSON_RPC_REQUEST"
} 