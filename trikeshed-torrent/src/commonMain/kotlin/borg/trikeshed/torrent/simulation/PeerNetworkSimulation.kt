@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.simulation

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * Peer Network Simulation
 * 
 * Simulates peer-to-peer network communication:
 * - Peer connections
 * - Message routing
 * - Network topology
 * - All operations use coroutine context wiring
 */
class PeerNetworkSimulation(
    internal val context: CoroutineContext = Dispatchers.IO
) {
    
    // Peer network state
    internal val peers = mutableMapOf<String, SimulatedPeer>()
    internal val connections = mutableMapOf<String, MutableSet<String>>()
    internal val torrentPeers = mutableMapOf<InfoHash, MutableSet<SimulatedPeer>>()
    
    // Communication channels
    internal val peerChannel = Channel<PeerEvent>(capacity = 1000)
    internal val peerScope = CoroutineScope(context + CoroutineName("peer-sim"))
    
    /**
     * Start peer network simulation
     */
    suspend fun start() = withContext(context) {
        println("🌐 Starting Peer Network Simulation")
        
        // Start peer event processor
        peerScope.launch {
            processPeerEvents()
        }
        
        println("✅ Peer Network Simulation started")
    }
    
    /**
     * Register a peer in the network
     */
    suspend fun registerPeer(peer: SimulatedPeer) = withContext(context) {
        val peerKey = "${peer.address}:${peer.port}"
        peers[peerKey] = peer
        
        // Add to torrent peer list
        torrentPeers.getOrPut(peer.torrent.infoHash) { mutableSetOf() }.add(peer)
        
        // Create connections to other peers in same torrent
        val otherPeers = torrentPeers[peer.torrent.infoHash]?.filter { it != peer } ?: emptyList()
        connections[peerKey] = otherPeers.map { "${it.address}:${it.port}" }.toMutableSet()
        
        peerChannel.send(PeerEvent.PeerJoined(peer))
        
        println("👤 Peer registered: ${peer.address}:${peer.port}")
    }
    
    /**
     * Get all peers
     */
    fun getAllPeers(): List<SimulatedPeer> = peers.values.toList()
    
    /**
     * Get peers for a specific torrent
     */
    fun getPeersForTorrent(infoHash: InfoHash): List<SimulatedPeer> = 
        torrentPeers[infoHash]?.toList() ?: emptyList()
    
    /**
     * Send a message between peers
     */
    suspend fun sendMessage(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage
    ) = withContext(context) {
        val fromKey = "${fromPeer.address}:${fromPeer.port}"
        val toKey = "${toPeer.address}:${toPeer.port}"
        
        // Check if peers are connected
        if (connections[fromKey]?.contains(toKey) == true) {
            // Simulate network delay
            delay((10..100).random().toLong())
            
            // Process message
            when (message) {
                is PeerMessage.PieceRequest -> {
                    handlePieceRequest(fromPeer, toPeer, message)
                }
                is PeerMessage.PieceResponse -> {
                    handlePieceResponse(fromPeer, toPeer, message)
                }
                is PeerMessage.Have -> {
                    handleHaveMessage(fromPeer, toPeer, message)
                }
                is PeerMessage.Bitfield -> {
                    handleBitfieldMessage(fromPeer, toPeer, message)
                }
                is PeerMessage.Interested -> {
                    handleInterestedMessage(fromPeer, toPeer, message)
                }
                is PeerMessage.NotInterested -> {
                    handleNotInterestedMessage(fromPeer, toPeer, message)
                }
                is PeerMessage.Choke -> {
                    handleChokeMessage(fromPeer, toPeer, message)
                }
                is PeerMessage.Unchoke -> {
                    handleUnchokeMessage(fromPeer, toPeer, message)
                }
            }
            
            peerChannel.send(PeerEvent.MessageSent(fromPeer, toPeer, message))
        } else {
            peerChannel.send(PeerEvent.MessageFailed(fromPeer, toPeer, message, "Peers not connected"))
        }
    }
    
    /**
     * Simulate peer discovery
     */
    suspend fun discoverPeers(infoHash: InfoHash): List<SimulatedPeer> = withContext(context) {
        // Simulate discovery delay
        delay((200..800).random().toLong())
        
        val discoveredPeers = torrentPeers[infoHash]?.take((3..8).random()) ?: emptyList()
        
        peerChannel.send(PeerEvent.PeersDiscovered(infoHash, discoveredPeers))
        
        println("🔍 Discovered ${discoveredPeers.size} peers for torrent")
        return@withContext discoveredPeers
    }
    
    /**
     * Simulate peer connection
     */
    suspend fun connectPeers(peer1: SimulatedPeer, peer2: SimulatedPeer) = withContext(context) {
        val key1 = "${peer1.address}:${peer1.port}"
        val key2 = "${peer2.address}:${peer2.port}"
        
        connections.getOrPut(key1) { mutableSetOf() }.add(key2)
        connections.getOrPut(key2) { mutableSetOf() }.add(key1)
        
        peerChannel.send(PeerEvent.PeersConnected(peer1, peer2))
        
        println("🔗 Connected peers: ${peer1.address} ↔ ${peer2.address}")
    }
    
    /**
     * Process peer events
     */
    internal suspend fun processPeerEvents() = withContext(context) {
        try {
            for (event in peerChannel) {
                when (event) {
                    is PeerEvent.PeerJoined -> {
                        handlePeerJoined(event)
                    }
                    is PeerEvent.MessageSent -> {
                        handleMessageSent(event)
                    }
                    is PeerEvent.MessageFailed -> {
                        handleMessageFailed(event)
                    }
                    is PeerEvent.PeersDiscovered -> {
                        handlePeersDiscovered(event)
                    }
                    is PeerEvent.PeersConnected -> {
                        handlePeersConnected(event)
                    }
                    is PeerEvent.PeerLeft -> {
                        handlePeerLeft(event)
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Peer event processing error: ${e.message}")
        }
    }
    
    // Message handlers
    internal suspend fun handlePieceRequest(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.PieceRequest
    ) = withContext(context) {
        if (toPeer.hasPieces.contains(message.pieceIndex)) {
            // Simulate piece data generation
            val pieceData = generatePieceData(message.length)
            
            val response = PeerMessage.PieceResponse(
                pieceIndex = message.pieceIndex,
                offset = message.offset,
                data = pieceData
            )
            
            // Send response back
            sendMessage(toPeer, fromPeer, response)
        }
    }
    
    internal suspend fun handlePieceResponse(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.PieceResponse
    ) = withContext(context) {
        // Update peer stats
        toPeer.downloadedPieces.add(message.pieceIndex)
        fromPeer.uploadedPieces.add(message.pieceIndex)
        
        println("📦 Piece ${message.pieceIndex} transferred: ${fromPeer.address} → ${toPeer.address}")
    }
    
    internal suspend fun handleHaveMessage(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.Have
    ) = withContext(context) {
        fromPeer.hasPieces.add(message.pieceIndex)
        println("📋 Have message: ${fromPeer.address} has piece ${message.pieceIndex}")
    }
    
    internal suspend fun handleBitfieldMessage(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.Bitfield
    ) = withContext(context) {
        fromPeer.hasPieces.clear()
        message.pieces.forEachIndexed { index, hasPiece ->
            if (hasPiece) fromPeer.hasPieces.add(index)
        }
        println("📊 Bitfield: ${fromPeer.address} has ${fromPeer.hasPieces.size} pieces")
    }
    
    internal suspend fun handleInterestedMessage(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.Interested
    ) = withContext(context) {
        println("👍 Interested: ${fromPeer.address} is interested in ${toPeer.address}")
    }
    
    internal suspend fun handleNotInterestedMessage(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.NotInterested
    ) = withContext(context) {
        println("👎 Not interested: ${fromPeer.address} is not interested in ${toPeer.address}")
    }
    
    internal suspend fun handleChokeMessage(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.Choke
    ) = withContext(context) {
        println("🚫 Choke: ${fromPeer.address} choked ${toPeer.address}")
    }
    
    internal suspend fun handleUnchokeMessage(
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        message: PeerMessage.Unchoke
    ) = withContext(context) {
        println("✅ Unchoke: ${fromPeer.address} unchoked ${toPeer.address}")
    }
    
    // Event handlers
    internal suspend fun handlePeerJoined(event: PeerEvent.PeerJoined) = withContext(context) {
        println("👤 Peer joined network: ${event.peer.address}:${event.peer.port}")
    }
    
    internal suspend fun handleMessageSent(event: PeerEvent.MessageSent) = withContext(context) {
        // Message sent successfully
    }
    
    internal suspend fun handleMessageFailed(event: PeerEvent.MessageFailed) = withContext(context) {
        println("❌ Message failed: ${event.error}")
    }
    
    internal suspend fun handlePeersDiscovered(event: PeerEvent.PeersDiscovered) = withContext(context) {
        println("🔍 Discovered ${event.peers.size} peers")
    }
    
    internal suspend fun handlePeersConnected(event: PeerEvent.PeersConnected) = withContext(context) {
        println("🔗 Peers connected")
    }
    
    internal suspend fun handlePeerLeft(event: PeerEvent.PeerLeft) = withContext(context) {
        val peerKey = "${event.peer.address}:${event.peer.port}"
        peers.remove(peerKey)
        connections.remove(peerKey)
        println("👋 Peer left: ${event.peer.address}:${event.peer.port}")
    }
    
    /**
     * Get peer network statistics
     */
    suspend fun getPeerStats(): PeerStats = withContext(context) {
        PeerStats(
            totalPeers = peers.size,
            totalConnections = connections.values.sumOf { it.size } / 2, // Divide by 2 as each connection is counted twice
            totalTorrents = torrentPeers.size,
            averagePeersPerTorrent = torrentPeers.values.map { it.size }.average()
        )
    }
    
    /**
     * Stop peer network simulation
     */
    suspend fun stop() = withContext(context) {
        println("🛑 Stopping Peer Network Simulation")
        peerScope.cancel()
        peerChannel.close()
    }
    
    // Helper functions
    internal fun generatePieceData(size: Int): ByteIndexed = size j { i -> (i % 256).toByte() }
}

// Peer message types
sealed class PeerMessage {
    data class PieceRequest(
        val pieceIndex: Int,
        val offset: Int,
        val length: Int
    ) : PeerMessage()
    
    data class PieceResponse(
        val pieceIndex: Int,
        val offset: Int,
        val data: ByteIndexed
    ) : PeerMessage()
    
    data class Have(val pieceIndex: Int) : PeerMessage()
    data class Bitfield(val pieces: BooleanArray) : PeerMessage()
    data class Interested : PeerMessage()
    data class NotInterested : PeerMessage()
    data class Choke : PeerMessage()
    data class Unchoke : PeerMessage()
}

// Peer data classes
data class PeerStats(
    val totalPeers: Int,
    val totalConnections: Int,
    val totalTorrents: Int,
    val averagePeersPerTorrent: Double
)

// Peer events
sealed class PeerEvent {
    data class PeerJoined(val peer: SimulatedPeer) : PeerEvent()
    data class MessageSent(
        val fromPeer: SimulatedPeer,
        val toPeer: SimulatedPeer,
        val message: PeerMessage
    ) : PeerEvent()
    data class MessageFailed(
        val fromPeer: SimulatedPeer,
        val toPeer: SimulatedPeer,
        val message: PeerMessage,
        val error: String
    ) : PeerEvent()
    data class PeersDiscovered(val infoHash: InfoHash, val peers: List<SimulatedPeer>) : PeerEvent()
    data class PeersConnected(val peer1: SimulatedPeer, val peer2: SimulatedPeer) : PeerEvent()
    data class PeerLeft(val peer: SimulatedPeer) : PeerEvent()
} 