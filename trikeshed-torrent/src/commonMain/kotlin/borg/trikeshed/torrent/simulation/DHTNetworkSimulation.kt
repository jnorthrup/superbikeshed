@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.simulation

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

// Torrent type aliases - imported from TorrentKettle

/**
 * DHT Network Simulation
 * 
 * Simulates the Distributed Hash Table network for peer discovery:
 * - Torrent announcements
 * - Peer lookups
 * - Node routing
 * - All operations use coroutine context wiring
 */
class DHTNetworkSimulation(
    internal val context: CoroutineContext = Dispatchers.IO
) {
    
    // DHT state
    internal val dhtNodes = mutableMapOf<String, DHTNode>()
    internal val torrentAnnouncements = mutableMapOf<InfoHash, MutableSet<DHTAnnouncement>>()
    internal val peerLookups = mutableMapOf<InfoHash, MutableSet<PeerId>>()
    
    // Communication channels
    internal val dhtChannel = Channel<DHTEvent>(capacity = 1000)
    internal val dhtScope = CoroutineScope(context + CoroutineName("dht-sim"))
    
    /**
     * Start DHT simulation
     */
    suspend fun start() = withContext(context) {
        println("🌐 Starting DHT Network Simulation")
        
        // Start DHT event processor
        dhtScope.launch {
            processDHTEvents()
        }
        
        // Create initial DHT nodes
        createInitialNodes()
        
        println("✅ DHT Network Simulation started")
    }
    
    /**
     * Create initial DHT nodes
     */
    internal suspend fun createInitialNodes() = withContext(context) {
        val initialNodes = listOf(
            "router.bittorrent.com:6881",
            "dht.transmissionbt.com:6881", 
            "router.utorrent.com:6881",
            "dht.aelitis.com:6881"
        )
        
        initialNodes.forEach { address ->
            val (host, port) = address.split(":")
            val node = DHTNode(
                nodeId = generateNodeId(),
                address = host,
                port = port.toInt(),
                lastSeen = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                isActive = true
            )
            dhtNodes[node.nodeId] = node
        }
        
        println("🌐 Created ${dhtNodes.size} initial DHT nodes")
    }
    
    /**
     * Announce torrent to DHT network
     */
    suspend fun announceTorrent(torrent: SimulatedTorrent) = withContext(context) {
        val announcement = DHTAnnouncement(
            infoHash = torrent.infoHash,
            nodeId = generateNodeId(),
            address = "127.0.0.1",
            port = (6881..6889).random(),
            announcedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        torrentAnnouncements.getOrPut(torrent.infoHash) { mutableSetOf() }.add(announcement)
        
        // Simulate DHT propagation
        dhtChannel.send(DHTEvent.TorrentAnnounced(announcement))
        
        println("📢 Announced torrent ${torrent.name} to DHT network")
    }
    
    /**
     * Look up peers for a torrent
     */
    suspend fun lookupPeers(infoHash: InfoHash): List<PeerId> = withContext(context) {
        // Simulate DHT lookup delay
        delay((100..500).random().toLong())
        
        val peers = peerLookups[infoHash]?.toList() ?: emptyList()
        
        // Simulate finding some peers
        val foundPeers = (1..(3..8).random()).map { generatePeerId() }
        peerLookups.getOrPut(infoHash) { mutableSetOf() }.addAll(foundPeers)
        
        dhtChannel.send(DHTEvent.PeerLookup(infoHash, foundPeers))
        
        println("🔍 DHT lookup found ${foundPeers.size} peers for torrent")
        return@withContext foundPeers
    }
    
    /**
     * Add peer to DHT network
     */
    suspend fun addPeer(infoHash: InfoHash, peerId: PeerId) = withContext(context) {
        peerLookups.getOrPut(infoHash) { mutableSetOf() }.add(peerId)
        
        dhtChannel.send(DHTEvent.PeerAdded(infoHash, peerId))
        
        println("👤 Added peer to DHT network")
    }
    
    /**
     * Process DHT events
     */
    internal suspend fun processDHTEvents() = withContext(context) {
        try {
            for (event in dhtChannel) {
                when (event) {
                    is DHTEvent.TorrentAnnounced -> {
                        handleTorrentAnnounced(event)
                    }
                    is DHTEvent.PeerLookup -> {
                        handlePeerLookup(event)
                    }
                    is DHTEvent.PeerAdded -> {
                        handlePeerAdded(event)
                    }
                    is DHTEvent.NodeJoined -> {
                        handleNodeJoined(event)
                    }
                    is DHTEvent.NodeLeft -> {
                        handleNodeLeft(event)
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ DHT event processing error: ${e.message}")
        }
    }
    
    // Event handlers
    internal suspend fun handleTorrentAnnounced(event: DHTEvent.TorrentAnnounced) = withContext(context) {
        // Simulate DHT propagation to other nodes
        delay((50..200).random().toLong())
        println("📢 DHT: Torrent announced by ${event.announcement.nodeId}")
    }
    
    internal suspend fun handlePeerLookup(event: DHTEvent.PeerLookup) = withContext(context) {
        // Simulate DHT routing
        delay((20..100).random().toLong())
        println("🔍 DHT: Peer lookup for ${event.infoHash.contentToString().take(8)}")
    }
    
    internal suspend fun handlePeerAdded(event: DHTEvent.PeerAdded) = withContext(context) {
        println("👤 DHT: Peer added to network")
    }
    
    internal suspend fun handleNodeJoined(event: DHTEvent.NodeJoined) = withContext(context) {
        dhtNodes[event.node.nodeId] = event.node
        println("🌐 DHT: Node joined: ${event.node.address}:${event.node.port}")
    }
    
    internal suspend fun handleNodeLeft(event: DHTEvent.NodeLeft) = withContext(context) {
        dhtNodes.remove(event.nodeId)
        println("🌐 DHT: Node left: $event.nodeId")
    }
    
    /**
     * Get DHT statistics
     */
    suspend fun getDHTStats(): DHTStats = withContext(context) {
        DHTStats(
            totalNodes = dhtNodes.size,
            activeNodes = dhtNodes.count { it.value.isActive },
            totalAnnouncements = torrentAnnouncements.values.sumOf { it.size },
            totalPeerLookups = peerLookups.values.sumOf { it.size }
        )
    }
    
    /**
     * Stop DHT simulation
     */
    suspend fun stop() = withContext(context) {
        println("🛑 Stopping DHT Network Simulation")
        dhtScope.cancel()
        dhtChannel.close()
    }
    
    // Helper functions
    internal fun generateNodeId(): String = "node_${(0..999999).random()}"
    internal fun generatePeerId(): PeerId = 20 j { i -> (i * 7).toByte() }
}

// DHT data classes
data class DHTNode(
    val nodeId: String,
    val address: String,
    val port: Int,
    val lastSeen: Long,
    val isActive: Boolean
)

data class DHTAnnouncement(
    val infoHash: InfoHash,
    val nodeId: String,
    val address: String,
    val port: Int,
    val announcedAt: Long
)

data class DHTStats(
    val totalNodes: Int,
    val activeNodes: Int,
    val totalAnnouncements: Int,
    val totalPeerLookups: Int
)

// DHT events
sealed class DHTEvent {
    data class TorrentAnnounced(val announcement: DHTAnnouncement) : DHTEvent()
    data class PeerLookup(val infoHash: InfoHash, val peers: List<PeerId>) : DHTEvent()
    data class PeerAdded(val infoHash: InfoHash, val peerId: PeerId) : DHTEvent()
    data class NodeJoined(val node: DHTNode) : DHTEvent()
    data class NodeLeft(val nodeId: String) : DHTEvent()
} 