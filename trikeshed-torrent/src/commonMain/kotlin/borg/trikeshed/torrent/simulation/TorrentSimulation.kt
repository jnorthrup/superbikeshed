@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.simulation

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import java.security.MessageDigest

// Torrent type aliases - imported from TorrentKettle

/**
 * Complete Torrent Simulation Framework
 * 
 * Simulates the entire torrent ecosystem through channelized mocks:
 * - DHT network simulation
 * - Tracker simulation  
 * - Peer network simulation
 * - File I/O simulation
 * - All operations use coroutine context wiring
 */
class TorrentSimulation(
    internal val context: CoroutineContext = Dispatchers.IO,
    internal val simulationId: String = "sim_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
) {
    
    // Simulation state
    internal val dhtNetwork = DHTNetworkSimulation(context)
    internal val trackerNetwork = TrackerNetworkSimulation(context)
    internal val peerNetwork = PeerNetworkSimulation(context)
    internal val fileSystem = FileSystemSimulation(context)
    internal val torrentRegistry = TorrentRegistrySimulation(context)
    
    // Global simulation coordinator
    internal val simulationScope = CoroutineScope(context + CoroutineName("torrent-sim-$simulationId"))
    internal val simulationChannel = Channel<SimulationEvent>(capacity = 1000)
    
    /**
     * Start the complete torrent simulation
     */
    suspend fun startSimulation() = withContext(context) {
        println("🚀 Starting Torrent Simulation: $simulationId")
        
        // Start all simulation components
        simulationScope.launch {
            dhtNetwork.start()
        }
        
        simulationScope.launch {
            trackerNetwork.start()
        }
        
        simulationScope.launch {
            peerNetwork.start()
        }
        
        simulationScope.launch {
            fileSystem.start()
        }
        
        simulationScope.launch {
            torrentRegistry.start()
        }
        
        // Start simulation coordinator
        simulationScope.launch {
            coordinateSimulation()
        }
        
        println("✅ Torrent Simulation started successfully")
    }
    
    /**
     * Start the simulation (alias for startSimulation)
     */
    suspend fun start() = startSimulation()
    
    /**
     * Check if simulation is running
     */
    fun isRunning(): Boolean = simulationScope.isActive
    
    /**
     * Coordinate all simulation components
     */
    internal suspend fun coordinateSimulation() = withContext(context) {
        try {
            for (event in simulationChannel) {
                when (event) {
                    is SimulationEvent.TorrentCreated -> {
                        handleTorrentCreated(event)
                    }
                    is SimulationEvent.PeerJoined -> {
                        handlePeerJoined(event)
                    }
                    is SimulationEvent.PieceRequested -> {
                        handlePieceRequested(event)
                    }
                    is SimulationEvent.PieceCompleted -> {
                        handlePieceCompleted(event)
                    }
                    is SimulationEvent.DownloadCompleted -> {
                        handleDownloadCompleted(event)
                    }
                    is SimulationEvent.NetworkError -> {
                        handleNetworkError(event)
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Simulation coordination error: ${e.message}")
        }
    }
    
    /**
     * Create a simulated torrent
     */
    suspend fun createTorrent(
        infoHash: InfoHash,
        name: String,
        totalSize: Long,
        pieceSize: Int = 16384,
        files: List<SimulatedFile> = emptyList()
    ): SimulatedTorrent = withContext(context) {
        val torrent = SimulatedTorrent(
            infoHash = infoHash,
            name = name,
            totalSize = totalSize,
            pieceSize = pieceSize,
            files = files,
            totalPieces = (totalSize / pieceSize).toInt() + 1,
            createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        // Register with all simulation components
        torrentRegistry.registerTorrent(torrent)
        dhtNetwork.announceTorrent(torrent)
        trackerNetwork.announceTorrent(torrent)
        
        // Notify simulation
        simulationChannel.send(SimulationEvent.TorrentCreated(torrent))
        
        println("📦 Created simulated torrent: ${torrent.name} (${torrent.totalPieces} pieces)")
        return@withContext torrent
    }
    
    /**
     * Simulate a peer joining the torrent
     */
    suspend fun simulatePeerJoin(
        torrent: SimulatedTorrent,
        peerId: PeerId,
        address: String,
        port: Int,
        hasPieces: Set<Int> = emptySet()
    ): SimulatedPeer = withContext(context) {
        val peer = SimulatedPeer(
            peerId = peerId,
            address = address,
            port = port,
            torrent = torrent,
            hasPieces = hasPieces.toMutableSet(),
            downloadSpeed = (1024..10240).random().toLong(), // 1-10 KB/s
            uploadSpeed = (512..5120).random().toLong(), // 0.5-5 KB/s
            joinedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        // Register peer
        peerNetwork.registerPeer(peer)
        torrentRegistry.addPeer(torrent.infoHash, peer)
        
        // Notify simulation
        simulationChannel.send(SimulationEvent.PeerJoined(peer))
        
        println("👤 Peer joined: ${peer.address}:${peer.port} (${peer.hasPieces.size} pieces)")
        return@withContext peer
    }
    
    /**
     * Simulate piece download
     */
    suspend fun simulatePieceDownload(
        torrent: SimulatedTorrent,
        peer: SimulatedPeer,
        pieceIndex: Int
    ) = withContext(context) {
        try {
            // Simulate network delay
            delay((100..500).random().toLong())
            
            // Check if peer has the piece
            if (peer.hasPieces.contains(pieceIndex)) {
                // Simulate piece transfer
                val pieceData = generatePieceData(torrent.pieceSize)
                
                // Verify piece (simulated)
                if (verifyPieceData(pieceIndex, pieceData)) {
                    // Store piece
                    fileSystem.storePiece(torrent.infoHash, pieceIndex, pieceData)
                    
                    // Update peer stats
                    peer.downloadedPieces.add(pieceIndex)
                    peer.lastActivity = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    
                    // Notify simulation
                    simulationChannel.send(SimulationEvent.PieceCompleted(
                        torrent = torrent,
                        peer = peer,
                        pieceIndex = pieceIndex,
                        data = pieceData
                    ))
                    
                    println("✅ Piece $pieceIndex downloaded from ${peer.address}")
                } else {
                    println("❌ Piece $pieceIndex verification failed from ${peer.address}")
                }
            } else {
                println("❌ Peer ${peer.address} doesn't have piece $pieceIndex")
            }
        } catch (e: Exception) {
            simulationChannel.send(SimulationEvent.NetworkError(
                torrent = torrent,
                peer = peer,
                error = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Simulate complete torrent download
     */
    suspend fun simulateCompleteDownload(
        torrent: SimulatedTorrent,
        downloaderPeer: SimulatedPeer
    ) = withContext(context) {
        println("🔄 Starting complete download simulation for ${torrent.name}")
        
        val neededPieces = (0 until torrent.totalPieces).toMutableSet()
        val availablePeers = peerNetwork.getPeersForTorrent(torrent.infoHash)
        
        while (neededPieces.isNotEmpty() && availablePeers.isNotEmpty()) {
            // Select random piece to download
            val pieceIndex = neededPieces.random()
            
            // Find peer with this piece
            val peerWithPiece = availablePeers.find { it.hasPieces.contains(pieceIndex) }
            
            if (peerWithPiece != null) {
                simulatePieceDownload(torrent, peerWithPiece, pieceIndex)
                neededPieces.remove(pieceIndex)
                
                // Simulate download speed
                delay((50..200).random().toLong())
            } else {
                // No peer has this piece, wait a bit
                delay(1000)
            }
        }
        
        if (neededPieces.isEmpty()) {
            simulationChannel.send(SimulationEvent.DownloadCompleted(
                torrent = torrent,
                peer = downloaderPeer,
                totalPieces = torrent.totalPieces
            ))
            println("🎉 Complete download simulation finished for ${torrent.name}")
        } else {
            println("⚠️ Download simulation incomplete: ${neededPieces.size} pieces missing")
        }
    }
    
    // Event handlers
    internal suspend fun handleTorrentCreated(event: SimulationEvent.TorrentCreated) = withContext(context) {
        println("📦 Torrent created: ${event.torrent.name}")
    }
    
    internal suspend fun handlePeerJoined(event: SimulationEvent.PeerJoined) = withContext(context) {
        println("👤 Peer joined: ${event.peer.address}:${event.peer.port}")
    }
    
    internal suspend fun handlePieceRequested(event: SimulationEvent.PieceRequested) = withContext(context) {
        // Simulate piece request processing
        delay((10..50).random().toLong())
    }
    
    internal suspend fun handlePieceCompleted(event: SimulationEvent.PieceCompleted) = withContext(context) {
        println("✅ Piece ${event.pieceIndex} completed for ${event.torrent.name}")
    }
    
    internal suspend fun handleDownloadCompleted(event: SimulationEvent.DownloadCompleted) = withContext(context) {
        println("🎉 Download completed: ${event.torrent.name} (${event.totalPieces} pieces)")
    }
    
    internal suspend fun handleNetworkError(event: SimulationEvent.NetworkError) = withContext(context) {
        println("❌ Network error: ${event.error}")
    }
    
    // Helper functions
    internal fun generatePieceData(size: Int): ByteIndexed {
        return \1 j { \2: Int -> (i % 256).toByte() }
    }
    
    internal fun verifyPieceData(pieceIndex: Int, data: ByteIndexed): Boolean {
        // Simulated verification - just check data is not empty
        return data.component1() > 0
    }
    
    /**
     * Get simulation statistics
     */
    suspend fun getSimulationStats(): SimulationStats = withContext(context) {
        val torrents = torrentRegistry.getAllTorrents()
        val peers = peerNetwork.getAllPeers()
        
        SimulationStats(
            totalTorrents = torrents.size,
            totalPeers = peers.size,
            activeDownloads = torrents.count { it.isActive },
            totalPiecesDownloaded = torrents.sumOf { it.downloadedPieces.size },
            totalDataTransferred = torrents.sumOf { it.downloadedBytes },
            simulationUptime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - torrents.firstOrNull()?.createdAt ?: 0
        )
    }
    
    /**
     * Stop the simulation
     */
    suspend fun stopSimulation() = withContext(context) {
        println("🛑 Stopping Torrent Simulation: $simulationId")
        
        simulationScope.cancel()
        simulationChannel.close()
        
        println("✅ Torrent Simulation stopped")
    }
    
    /**
     * Stop the simulation (alias for stopSimulation)
     */
    suspend fun stop() = stopSimulation()
}

// Data classes for simulation
data class SimulatedTorrent(
    val infoHash: InfoHash,
    val name: String,
    val totalSize: Long,
    val pieceSize: Int,
    val files: List<SimulatedFile>,
    val totalPieces: Int,
    val createdAt: Long,
    val isActive: Boolean = true,
    val downloadedPieces: MutableSet<Int> = mutableSetOf(),
    val downloadedBytes: Long = 0
)

data class SimulatedFile(
    val path: String,
    val size: Long,
    val offset: Long
)

data class SimulatedPeer(
    val peerId: PeerId,
    val address: String,
    val port: Int,
    val torrent: SimulatedTorrent,
    val hasPieces: MutableSet<Int>,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val joinedAt: Long,
    val lastActivity: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
    val downloadedPieces: MutableSet<Int> = mutableSetOf(),
    val uploadedPieces: MutableSet<Int> = mutableSetOf()
)

data class SimulationStats(
    val totalTorrents: Int,
    val totalPeers: Int,
    val activeDownloads: Int,
    val totalPiecesDownloaded: Int,
    val totalDataTransferred: Long,
    val simulationUptime: Long
)

// Simulation events
sealed class SimulationEvent {
    data class TorrentCreated(val torrent: SimulatedTorrent) : SimulationEvent()
    data class PeerJoined(val peer: SimulatedPeer) : SimulationEvent()
    data class PieceRequested(
        val torrent: SimulatedTorrent,
        val peer: SimulatedPeer,
        val pieceIndex: Int
    ) : SimulationEvent()
    data class PieceCompleted(
        val torrent: SimulatedTorrent,
        val peer: SimulatedPeer,
        val pieceIndex: Int,
        val data: ByteIndexed
    ) : SimulationEvent()
    data class DownloadCompleted(
        val torrent: SimulatedTorrent,
        val peer: SimulatedPeer,
        val totalPieces: Int
    ) : SimulationEvent()
    data class NetworkError(
        val torrent: SimulatedTorrent,
        val peer: SimulatedPeer,
        val error: String
    ) : SimulationEvent()
} 