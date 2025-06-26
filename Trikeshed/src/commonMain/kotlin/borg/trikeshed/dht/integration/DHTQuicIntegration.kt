package borg.trikeshed.dht.integration

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.dht.kademlia.events.*
import borg.trikeshed.dht.kademlia.codec.KademliaCodec
import borg.trikeshed.dht.kademlia.routing.RoutingTable
import borg.trikeshed.dht.kademlia.subnet.SubnetManager
import borg.trikeshed.dht.gossip.GossipService
import borg.trikeshed.dht.agent.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*

/**
 * Integrates Kademlia DHT agent bus with QUIC transport and TrikeShed reactor patterns
 * Provides the complete networking stack for distributed agent communication
 */
class DHTQuicIntegration(
    private val localNodeId: NUID,
    private val quicPort: Int = 4433,
    private val bootstrapNodes: Indexed<NodeInfo> = 0 j { NodeInfo(NUID.ZERO, "", 0, 0 j { "" }) }
) : AgentNetworkingService {
    
    // Core components
    private val routingTable = RoutingTable(localNodeId)
    private val subnetManager = SubnetManager(localNodeId)
    private val codec = KademliaCodec()
    
    // QUIC networking
    private lateinit var quicServer: QuicServer
    private lateinit var quicEngine: QuicEngine
    private val connections = mutableMapOf<NUID, QuicConnection>()
    
    // Gossip service
    private lateinit var gossipService: GossipService
    
    // Storage
    private val localStorage = mutableMapOf<String, Indexed<Byte>>()
    
    // Reactor integration
    private val network = ReactorNetwork()
    private lateinit var dhtReactor: Reactor<KademliaEvent>
    
    /**
     * Initialize the DHT agent bus
     */
    suspend fun initialize(): Boolean {
        try {
            // Initialize QUIC engine
            quicEngine = QuicEngine(
                role = QuicEngine.Role.SERVER,
                initialState = QuicConnectionState(
                    localConnectionId = ConnectionId(8 j { i: Int -> localNodeId.bytes[i % localNodeId.size] }),
                    remoteConnectionId = ConnectionId(8 j { i: Int -> 0.toByte() })
                )
            )
            
            // Initialize QUIC server
            quicServer = QuicServer(quicEngine, quicPort)
            quicServer.onConnection { connection ->
                handleNewQuicConnection(connection)
            }
            
            // Initialize gossip service
            gossipService = GossipService(
                localNodeId = localNodeId,
                subnetManager = subnetManager,
                sendToNode = { nodeId, message ->
                    sendGossipToPeer(nodeId, message)
                }
            )
            
            // Initialize reactor
            dhtReactor = Reactor("dht-reactor") { event ->
                handleDHTEvent(event)
            }
            network.addReactor(dhtReactor)
            
            // Start QUIC server
            GlobalScope.launch { quicServer.start() }
            
            // Bootstrap DHT
            bootstrapDHT()
            
            return true
        } catch (e: Exception) {
            println("Failed to initialize DHT-QUIC integration: ${e.message}")
            return false
        }
    }
    
    /**
     * Bootstrap the DHT by connecting to known nodes
     */
    private suspend fun bootstrapDHT() {
        for (i in 0 until bootstrapNodes.a) {
            val node = bootstrapNodes[i]
            if (node.nodeId != NUID.ZERO) {
                connectToNode(node)
                sendFindNode(node.nodeId, localNodeId)
            }
        }
    }
    
    /**
     * Connect to a remote node
     */
    private suspend fun connectToNode(node: NodeInfo): QuicConnection? {
        return try {
            val connection = QuicConnection(
                config = QuicConfig(),
                sessionCache = DefaultQuicSessionCache(),
                coroutineScope = GlobalScope
            )
            
            if (connection.connect(node.ipAddress, node.port)) {
                connections[node.nodeId] = connection
                routingTable.addNode(node)
                connection
            } else {
                null
            }
        } catch (e: Exception) {
            println("Failed to connect to ${node.nodeId}: ${e.message}")
            null
        }
    }
    
    /**
     * Handle new QUIC connection
     */
    private fun handleNewQuicConnection(connection: QuicConnection) {
        GlobalScope.launch {
            while (true) {
                val stream = connection.createStream() ?: break
                handleQuicStream(connection, stream)
            }
        }
    }
    
    /**
     * Handle QUIC stream
     */
    private suspend fun handleQuicStream(connection: QuicConnection, stream: QuicStream) {
        try {
            // Read Kademlia message
            val messageData = stream.readBytes(stream.getAvailableBytes())
            val event = codec.decode(messageData)
            
            // Process through reactor
            dhtReactor.receive(event)
            
        } catch (e: Exception) {
            println("Error handling QUIC stream: ${e.message}")
        } finally {
            stream.close()
        }
    }
    
    /**
     * Handle DHT event through reactor pattern
     */
    private suspend fun handleDHTEvent(event: KademliaEvent) {
        when (event) {
            is PingEvent -> handlePing(event)
            is PongEvent -> handlePong(event)
            is FindNodeEvent -> handleFindNode(event)
            is FoundNodesEvent -> handleFoundNodes(event)
            is StoreEvent -> handleStore(event)
            is FindValueEvent -> handleFindValue(event)
            is JoinRequestEvent -> handleJoinRequest(event)
            else -> {}
        }
    }
    
    /**
     * Send Kademlia event to peer
     */
    private suspend fun sendEventToPeer(nodeId: NUID, event: KademliaEvent): Boolean {
        val connection = connections[nodeId] ?: return false
        val stream = connection.createStream() ?: return false
        
        return try {
            val data = codec.encode(event)
            stream.writeBytes(data)
            true
        } catch (e: Exception) {
            println("Failed to send event to $nodeId: ${e.message}")
            false
        } finally {
            stream.close()
        }
    }
    
    /**
     * Send gossip message to peer
     */
    private suspend fun sendGossipToPeer(nodeId: NUID, message: GossipMessage): Boolean {
        // Wrap gossip in Kademlia event
        val gossipData = encodeGossipMessage(message)
        val event = StoreEvent(
            timestamp = getCurrentTimeMillis(),
            messageId = message.messageId,
            sourceNodeId = localNodeId,
            key = message.messageId.bytes,
            value = gossipData,
            ttl = 300000, // 5 minutes
            replicationFactor = 0 // Don't replicate gossip
        )
        
        return sendEventToPeer(nodeId, event)
    }
    
    // AgentNetworkingService implementation
    
    override suspend fun put(key: Indexed<Byte>, value: Indexed<Byte>): Boolean {
        // Store locally
        val keyStr = key.play.joinToString("") { it.toString(16).padStart(2, '0') }
        localStorage[keyStr] = value
        
        // Find nodes to store on
        val keyNuid = NUID(key)
        val closestNodes = routingTable.findClosestNodes(keyNuid)
        
        // Send STORE to closest nodes
        var stored = 0
        for (i in 0 until minOf(3, closestNodes.a)) {
            val node = closestNodes[i]
            val event = StoreEvent(
                timestamp = getCurrentTimeMillis(),
                messageId = NUID.random(),
                sourceNodeId = localNodeId,
                key = key,
                value = value,
                ttl = 3600000, // 1 hour
                replicationFactor = 3
            )
            
            if (sendEventToPeer(node.nodeId, event)) {
                stored++
            }
        }
        
        return stored > 0
    }
    
    override suspend fun get(key: Indexed<Byte>): Indexed<Byte>? {
        // Check local storage
        val keyStr = key.play.joinToString("") { it.toString(16).padStart(2, '0') }
        localStorage[keyStr]?.let { return it }
        
        // Find value in DHT
        val keyNuid = NUID(key)
        val closestNodes = routingTable.findClosestNodes(keyNuid)
        
        for (i in 0 until closestNodes.a) {
            val node = closestNodes[i]
            val event = FindValueEvent(
                timestamp = getCurrentTimeMillis(),
                messageId = NUID.random(),
                sourceNodeId = localNodeId,
                key = key,
                maxHops = 20
            )
            
            // Send and wait for response (simplified - should use proper async)
            sendEventToPeer(node.nodeId, event)
            // TODO: Wait for FoundValueEvent response
        }
        
        return null
    }
    
    override suspend fun findNode(nodeId: NUID): Indexed<NodeInfo> {
        return routingTable.findClosestNodes(nodeId)
    }
    
    override suspend fun publishGossip(payload: Indexed<Byte>, targetSubnets: Indexed<String>): Boolean {
        return gossipService.publish(payload, targetSubnets)
    }
    
    override fun subscribeToGossip(handler: (message: GossipMessage) -> Unit): SubscriptionHandle {
        return gossipService.subscribe(handler = handler)
    }
    
    // DHT event handlers
    
    private suspend fun handlePing(event: PingEvent) {
        val pong = PongEvent(
            timestamp = getCurrentTimeMillis(),
            messageId = NUID.random(),
            sourceNodeId = localNodeId,
            respondingToMessageId = event.messageId,
            respondingToNodeId = event.sourceNodeId,
            activeSubnets = subnetManager.getNodeSubnets(localNodeId),
            routingTableSize = routingTable.getNodeCount()
        )
        
        sendEventToPeer(event.sourceNodeId, pong)
    }
    
    private suspend fun handlePong(event: PongEvent) {
        // Update routing table with responding node
        val nodeInfo = NodeInfo(
            nodeId = event.sourceNodeId,
            ipAddress = "", // TODO: Get from connection
            port = 0, // TODO: Get from connection
            subnets = event.activeSubnets,
            lastSeen = getCurrentTimeMillis(),
            reliability = 1.0
        )
        routingTable.addNode(nodeInfo)
    }
    
    private suspend fun handleFindNode(event: FindNodeEvent) {
        val closestNodes = routingTable.findClosestNodes(event.targetNodeId, event.maxResults)
        
        val response = FoundNodesEvent(
            timestamp = getCurrentTimeMillis(),
            messageId = NUID.random(),
            sourceNodeId = localNodeId,
            respondingToMessageId = event.messageId,
            targetNodeId = event.targetNodeId,
            nodes = closestNodes
        )
        
        sendEventToPeer(event.sourceNodeId, response)
    }
    
    private suspend fun handleFoundNodes(event: FoundNodesEvent) {
        // Add found nodes to routing table
        for (i in 0 until event.nodes.a) {
            val node = event.nodes[i]
            routingTable.addNode(node)
        }
    }
    
    private suspend fun handleStore(event: StoreEvent) {
        // Store value locally
        val keyStr = event.key.play.joinToString("") { it.toString(16).padStart(2, '0') }
        localStorage[keyStr] = event.value
        
        // Send response
        val response = StoreResponseEvent(
            timestamp = getCurrentTimeMillis(),
            messageId = NUID.random(),
            sourceNodeId = localNodeId,
            respondingToMessageId = event.messageId,
            key = event.key,
            success = true,
            errorCode = 0
        )
        
        sendEventToPeer(event.sourceNodeId, response)
    }
    
    private suspend fun handleFindValue(event: FindValueEvent) {
        val keyStr = event.key.play.joinToString("") { it.toString(16).padStart(2, '0') }
        val value = localStorage[keyStr]
        
        if (value != null) {
            // Found value
            val response = FoundValueEvent(
                timestamp = getCurrentTimeMillis(),
                messageId = NUID.random(),
                sourceNodeId = localNodeId,
                respondingToMessageId = event.messageId,
                key = event.key,
                value = value,
                providingNodes = 1 j { i: Int ->
                    NodeInfo(
                        nodeId = localNodeId,
                        ipAddress = "localhost",
                        port = quicPort,
                        subnets = subnetManager.getNodeSubnets(localNodeId),
                        lastSeen = getCurrentTimeMillis(),
                        reliability = 1.0
                    )
                }
            )
            
            sendEventToPeer(event.sourceNodeId, response)
        } else {
            // Don't have value, return closest nodes
            val keyNuid = NUID(event.key)
            handleFindNode(FindNodeEvent(
                timestamp = event.timestamp,
                messageId = event.messageId,
                sourceNodeId = event.sourceNodeId,
                targetNodeId = keyNuid,
                maxResults = 20,
                preferredSubnets = 0 j { "" }
            ))
        }
    }
    
    private suspend fun handleJoinRequest(event: JoinRequestEvent) {
        // Accept join request and send known nodes
        val knownNodes = routingTable.findClosestNodes(event.proposedNodeId, 20)
        
        val response = JoinResponseEvent(
            timestamp = getCurrentTimeMillis(),
            messageId = NUID.random(),
            sourceNodeId = localNodeId,
            respondingToMessageId = event.messageId,
            accepted = true,
            message = "Welcome to the DHT network",
            knownNodes = knownNodes
        )
        
        sendEventToPeer(event.sourceNodeId, response)
    }
    
    private suspend fun sendFindNode(targetNodeId: NUID, lookupNodeId: NUID) {
        val event = FindNodeEvent(
            timestamp = getCurrentTimeMillis(),
            messageId = NUID.random(),
            sourceNodeId = localNodeId,
            targetNodeId = lookupNodeId,
            maxResults = 20,
            preferredSubnets = subnetManager.getNodeSubnets(localNodeId)
        )
        
        sendEventToPeer(targetNodeId, event)
    }
    
    private fun encodeGossipMessage(message: GossipMessage): Indexed<Byte> {
        // Simple encoding - in production would use proper serialization
        val parts = listOf(
            message.messageId.toByteArray(),
            message.publisherId.toByteArray(),
            message.targetSubnets.play.joinToString(",").encodeToByteArray(),
            message.payload.play.toByteArray(),
            message.timestamp.toString().encodeToByteArray(),
            message.signature.play.toByteArray()
        )
        
        val totalSize = parts.sumOf { it.size }
        var offset = 0
        
        return totalSize j { i: Int ->
            var currentPart = 0
            var currentOffset = offset
            
            while (currentPart < parts.size && currentOffset >= parts[currentPart].size) {
                currentOffset -= parts[currentPart].size
                currentPart++
            }
            
            if (currentPart < parts.size) {
                parts[currentPart][currentOffset]
            } else {
                0.toByte()
            }
        }
    }
}

/**
 * Extension to convert NUID to ByteArray
 */
private fun NUID.toByteArray(): ByteArray {
    return ByteArray(size) { i -> bytes[i] }
}