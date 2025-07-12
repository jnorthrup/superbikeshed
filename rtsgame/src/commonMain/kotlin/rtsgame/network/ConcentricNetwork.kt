package rtsgame.network

import rtsgame.core.*
import rtsgame.codec.*
import trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * Concentric network architecture for multi-protocol player interaction
 * Supports lobby, game, observation, and RPC across any protocol medium
 * Following Kademlia-inspired concentric subnet patterns from CLAUDE.md
 */

// ============================================================================
// Concentric Ring CCEK
// ============================================================================

data class RingCCEK(
    val ring: Ring,
    val distance: Int,
    val protocol: Protocol
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<RingCCEK>
    override val key: CoroutineContext.Key<*> get() = Key
}

// ============================================================================
// Ring Types
// ============================================================================

enum class Ring {
    INNER,    // Core game state
    GAME,     // Active players
    LOBBY,    // Waiting players
    OBSERVER, // Spectators
    OUTER     // Discovery/matchmaking
}

sealed class Protocol {
    object TCP : Protocol()
    object UDP : Protocol()
    object WebSocket : Protocol()
    object WebRTC : Protocol()
    object QUIC : Protocol()
    object CouchDB : Protocol()
    object IPFS : Protocol()
    object SSH : Protocol()
    object REST : Protocol()
    object Wave : Protocol()
}

// ============================================================================
// Node Identity
// ============================================================================

data class NodeId(val bytes: ByteArray) {
    fun xor(other: NodeId): Int {
        var distance = 0
        for (i in bytes.indices) {
            distance += (bytes[i].toInt() xor other.bytes[i].toInt()).countOneBits()
        }
        return distance
    }
    
    override fun equals(other: Any?) = other is NodeId && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
}

data class Node(
    val id: NodeId,
    val ring: Ring,
    val protocols: Set<Protocol>,
    val endpoint: String,
    val capabilities: Set<Capability>
)

enum class Capability {
    HOST,
    RELAY,
    STORAGE,
    COMPUTE,
    OBSERVE
}

// ============================================================================
// Concentric Network
// ============================================================================

class ConcentricNetwork(
    private val localNode: Node,
    private val config: ConcentricConfig = ConcentricConfig()
) {
    data class ConcentricConfig(
        val k: Int = 8,  // Kademlia K-bucket size
        val alpha: Int = 3,  // Concurrency parameter
        val ringDistances: Map<Ring, IntRange> = mapOf(
            Ring.INNER to 0..10,
            Ring.GAME to 11..50,
            Ring.LOBBY to 51..100,
            Ring.OBSERVER to 101..200,
            Ring.OUTER to 201..Int.MAX_VALUE
        )
    )
    
    // Routing table per ring
    private val routingTables = Ring.values().associateWith { 
        RoutingTable(config.k) 
    }
    
    // Active connections by protocol
    private val connections = mutableMapOf<Protocol, MutableMap<NodeId, Connection>>()
    
    // Message channels per ring
    private val ringChannels = Ring.values().associateWith {
        Channel<RingMessage>(Channel.UNLIMITED)
    }
    
    // ========================================================================
    // Ring Operations
    // ========================================================================
    
    suspend fun join(ring: Ring, protocol: Protocol): Flow<RingEvent> = flow {
        withContext(RingCCEK(ring, 0, protocol)) {
            // Bootstrap into ring
            val bootstrapNodes = findClosestNodes(localNode.id, ring, config.k)
            
            // Announce presence
            bootstrapNodes.forEach { node ->
                sendTo(node, RingMessage.localNode j ring)
            }
            
            // Listen for ring events
            ringChannels[ring]?.let { channel ->
                for (msg in channel) {
                    val event = processRingMessage(msg, ring)
                    event?.let { emit(it) }
                }
            }
        }
    }
    
    suspend fun moveToRing(from: Ring, to: Ring) {
        // Leave current ring
        broadcast(from, RingMessage.Leave(localNode.id))
        
        // Join new ring
        join(to, localNode.protocols.first()).collect {
            // Handle join events
        }
    }
    
    // ========================================================================
    // Multi-Protocol Communication
    // ========================================================================
    
    suspend fun send(
        target: NodeId,
        message: Message,
        preferredProtocol: Protocol? = null
    ): Result<Unit> = runCatching {
        val node = findNode(target) ?: throw NoSuchElementException("Node not found")
        
        // Select protocol
        val protocol = preferredProtocol 
            ?: (node.protocols intersect localNode.protocols).firstOrNull()
            ?: throw UnsupportedOperationException("No common protocol")
        
        // Get or create connection
        val conn = getConnection(node, protocol)
        
        // Send message
        conn.send(message)
    }
    
    suspend fun rpc(
        target: NodeId,
        method: String,
        params: Map<String, Any>,
        timeout: Long = 5000
    ): Result<Any> = withTimeout(timeout) {
        val id = generateRpcId()
        val request = RpcRequest(id, method, params)
        
        // Send RPC
        send(target, Message.Rpc(request))
        
        // Wait for response
        rpcResponses[id]?.receive() ?: throw TimeoutException("RPC timeout")
    }
    
    // ========================================================================
    // Ring-Based Discovery
    // ========================================================================
    
    suspend fun discoverGameHosts(): Flow<GameHost> = flow {
        // Query GAME ring for hosts
        val hosts = queryRing(Ring.GAME) { node ->
            Capability.HOST in node.capabilities
        }
        
        hosts.forEach { host ->
            val info = rpc(host.id, "getGameInfo", emptyMap())
            info.getOrNull()?.let { gameInfo ->
                emit(GameHost(host, gameInfo as GameInfo))
            }
        }
    }
    
    suspend fun findLobby(criteria: LobbyCriteria): Lobby? {
        // Search LOBBY ring
        val lobbies = queryRing(Ring.LOBBY) { node ->
            true // Filter in next step
        }
        
        // Query each lobby
        for (node in lobbies) {
            val info = rpc(node.id, "getLobbyInfo", emptyMap()).getOrNull() as? LobbyInfo
            if (info != null && criteria.matches(info)) {
                return Lobby(node, info)
            }
        }
        
        return null
    }
    
    // ========================================================================
    // Observation Mode
    // ========================================================================
    
    suspend fun observeGame(gameId: String): Flow<GameState> = flow {
        // Move to OBSERVER ring
        moveToRing(localNode.ring, Ring.OBSERVER)
        
        // Find game host
        val host = findGameHost(gameId) ?: throw NoSuchElementException("Game not found")
        
        // Subscribe to game updates
        val subscription = Message.Subscribe(
            topic = "game/$gameId/state",
            filter = ObserverFilter.PUBLIC
        )
        
        send(host.id, subscription)
        
        // Receive game states
        gameStateChannel.consumeAsFlow()
            .filter { it.gameId == gameId }
            .collect { emit(it) }
    }
    
    // ========================================================================
    // Protocol Adapters
    // ========================================================================
    
    private suspend fun getConnection(node: Node, protocol: Protocol): Connection {
        val connMap = connections.getOrPut(protocol) { mutableMapOf() }
        
        return connMap.getOrPut(node.id) {
            when (protocol) {
                Protocol.TCP -> TcpConnection(node.endpoint)
                Protocol.UDP -> UdpConnection(node.endpoint)
                Protocol.WebSocket -> WsConnection(node.endpoint)
                Protocol.WebRTC -> WebRtcConnection(node.endpoint)
                Protocol.QUIC -> QuicConnection(node.endpoint)
                Protocol.CouchDB -> CouchConnection(node.endpoint)
                Protocol.IPFS -> IpfsConnection(node.endpoint)
                Protocol.SSH -> SshConnection(node.endpoint)
                Protocol.REST -> RestConnection(node.endpoint)
                Protocol.Wave -> WaveConnection(node.endpoint)
            }.also { it.connect() }
        }
    }
    
    // ========================================================================
    // Ring Management
    // ========================================================================
    
    private fun findClosestNodes(target: NodeId, ring: Ring, count: Int): List<Node> {
        val table = routingTables[ring] ?: return emptyList()
        return table.findClosest(target, count)
    }
    
    private suspend fun queryRing(ring: Ring, predicate: (Node) -> Boolean): List<Node> {
        val nodes = mutableListOf<Node>()
        val visited = mutableSetOf<NodeId>()
        val toVisit = findClosestNodes(localNode.id, ring, config.alpha).toMutableList()
        
        while (toVisit.isNotEmpty() && nodes.size < config.k) {
            val node = toVisit.removeAt(0)
            if (node.id in visited) continue
            
            visited.add(node.id)
            
            if (predicate(node)) {
                nodes.add(node)
            }
            
            // Ask node for its neighbors
            val neighbors = rpc(node.id, "getNeighbors", mapOf("ring" to ring))
                .getOrNull() as? List<Node> ?: emptyList()
            
            neighbors.forEach { neighbor ->
                if (neighbor.id !in visited) {
                    toVisit.add(neighbor)
                }
            }
        }
        
        return nodes
    }
    
    private suspend fun broadcast(ring: Ring, message: RingMessage) {
        val nodes = findClosestNodes(localNode.id, ring, config.k)
        nodes.forEach { node ->
            sendTo(node, message)
        }
    }
    
    private suspend fun sendTo(node: Node, message: RingMessage) {
        val protocol = (node.protocols intersect localNode.protocols).firstOrNull() ?: return
        val conn = getConnection(node, protocol)
        conn.send(Message.Ring(message))
    }
    
    private fun processRingMessage(msg: RingMessage, ring: Ring): RingEvent? {
        return when (msg) {
            is RingMessage.Join -> {
                routingTables[ring]?.add(msg.node)
                RingEvent.NodeJoined(msg.node, ring)
            }
            is RingMessage.Leave -> {
                routingTables[ring]?.remove(msg.nodeId)
                RingEvent.NodeLeft(msg.nodeId, ring)
            }
            is RingMessage.Update -> {
                RingEvent.StateUpdate(msg.state)
            }
        }
    }
    
    // ========================================================================
    // Helper Functions
    // ========================================================================
    
    private fun findNode(id: NodeId): Node? {
        for ((ring, table) in routingTables) {
            table.find(id)?.let { return it }
        }
        return null
    }
    
    private suspend fun findGameHost(gameId: String): Node? {
        val hosts = queryRing(Ring.GAME) { node ->
            Capability.HOST in node.capabilities
        }
        
        for (host in hosts) {
            val games = rpc(host.id, "getHostedGames", emptyMap())
                .getOrNull() as? List<String> ?: emptyList()
            
            if (gameId in games) {
                return host
            }
        }
        
        return null
    }
    
    private fun generateRpcId(): String = 
        "rpc_${System.currentTimeMillis()}_${(0..10000).random()}"
    
    // ========================================================================
    // Channels and State
    // ========================================================================
    
    private val rpcResponses = mutableMapOf<String, Channel<Any>>()
    private val gameStateChannel = Channel<GameState>(Channel.UNLIMITED)
}

// ============================================================================
// Message Types
// ============================================================================

sealed class Message {
    data class Ring(val msg: RingMessage) : Message()
    data class Game(val cmd: Cmd) : Message()
    data class Rpc(val request: RpcRequest) : Message()
    data class Subscribe(val topic: String, val filter: ObserverFilter) : Message()
}

sealed class RingMessage {
    data class val node: Node j val ring: Ring : RingMessage()
    data class Leave(val nodeId: NodeId) : RingMessage()
    data class Update(val state: Any) : RingMessage()
}

sealed class RingEvent {
    data class NodeJoined(val node: Node, val ring: Ring) : RingEvent()
    data class NodeLeft(val nodeId: NodeId, val ring: Ring) : RingEvent()
    data class StateUpdate(val state: Any) : RingEvent()
}

// ============================================================================
// Data Classes
// ============================================================================

data class RpcRequest(
    val id: String,
    val method: String,
    val params: Map<String, Any>
)

data class GameHost(
    val node: Node,
    val info: GameInfo
)

data class GameInfo(
    val id: String,
    val name: String,
    val players: Int,
    val maxPlayers: Int,
    val map: String,
    val state: GameState.State
)

data class GameState(
    val gameId: String,
    val tick: Tick,
    val world: W,
    val state: State
) {
    enum class State {
        WAITING, STARTING, PLAYING, PAUSED, ENDED
    }
}

data class Lobby(
    val node: Node,
    val info: LobbyInfo
)

data class LobbyInfo(
    val id: String,
    val name: String,
    val host: String,
    val players: List<String>,
    val settings: Map<String, Any>
)

data class LobbyCriteria(
    val minPlayers: Int? = null,
    val maxPlayers: Int? = null,
    val map: String? = null,
    val mods: Set<String> = emptySet()
) {
    fun matches(info: LobbyInfo): Boolean {
        minPlayers?.let { if (info.players.size < it) return false }
        maxPlayers?.let { if (info.players.size > it) return false }
        map?.let { if (info.settings["map"] != it) return false }
        return true
    }
}

enum class ObserverFilter {
    PUBLIC,    // Only public game state
    DETAILED,  // Includes unit positions
    FULL       // Everything except player inputs
}

// ============================================================================
// Routing Table
// ============================================================================

class RoutingTable(private val k: Int) {
    private val buckets = mutableMapOf<Int, MutableList<Node>>()
    
    fun add(node: Node) {
        val distance = localNode.id.xor(node.id)
        val bucket = buckets.getOrPut(distance) { mutableListOf() }
        
        if (node in bucket) {
            // Move to end (most recently seen)
            bucket.remove(node)
            bucket.add(node)
        } else if (bucket.size < k) {
            bucket.add(node)
        } else {
            // Bucket full, replace least recently seen if offline
            // Simplified - in real impl would ping oldest node
            bucket.removeAt(0)
            bucket.add(node)
        }
    }
    
    fun remove(nodeId: NodeId) {
        buckets.values.forEach { bucket ->
            bucket.removeAll { it.id == nodeId }
        }
    }
    
    fun find(nodeId: NodeId): Node? {
        for (bucket in buckets.values) {
            bucket.find { it.id == nodeId }?.let { return it }
        }
        return null
    }
    
    fun findClosest(target: NodeId, count: Int): List<Node> {
        return buckets.values
            .flatten()
            .sortedBy { it.id.xor(target) }
            .take(count)
    }
    
    companion object {
        lateinit var localNode: Node
    }
}

// ============================================================================
// Connection Interface
// ============================================================================

interface Connection {
    suspend fun connect()
    suspend fun send(message: Message)
    suspend fun close()
}

// Placeholder implementations
class TcpConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class UdpConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class WsConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class WebRtcConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class QuicConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class CouchConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class IpfsConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class SshConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class RestConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}

class WaveConnection(endpoint: String) : Connection {
    override suspend fun connect() {}
    override suspend fun send(message: Message) {}
    override suspend fun close() {}
}