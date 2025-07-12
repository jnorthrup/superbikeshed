@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.simulation

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

/**
 * Consolidated Torrent Simulation - Self-Contained Network-Free Implementation
 * 
 * Complete torrent ecosystem simulation using only coroutine contexts and channels:
 * - No external network dependencies
 * - All operations use coroutine context wiring
 * - Self-contained peer discovery, DHT, tracker, and file operations
 * - Channelized communication between all components
 */
class ConsolidatedTorrentSimulation(
    internal val context: CoroutineContext = Dispatchers.IO,
    internal val simulationId: String = "consolidated_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
) {
    
    // Core simulation state - all in memory
    internal val torrents = mutableMapOf<InfoHash, SimulatedTorrent>()
    internal val peers = mutableMapOf<String, SimulatedPeer>()
    internal val pieces = mutableMapOf<InfoHash, MutableMap<Int, ByteIndexed>>()
    internal val peerConnections = mutableMapOf<String, MutableSet<String>>()
    
    // Communication channels - no external network
    internal val simulationChannel = Channel<SimulationEvent>(capacity = 1000)
    internal val dhtChannel = Channel<DHTEvent>(capacity = 500)
    internal val trackerChannel = Channel<TrackerEvent>(capacity = 500)
    internal val peerChannel = Channel<PeerEvent>(capacity = 500)
    internal val fileChannel = Channel<FileEvent>(capacity = 500)
    
    // Simulation coordinator
    internal val simulationScope = CoroutineScope(context + CoroutineName("consolidated-sim-$simulationId"))
    internal var isRunning = false
    
    /**
     * Start consolidated simulation
     */
    suspend fun start() = withContext(context) {
        if (isRunning) return@withContext
        
        isRunning = true
        println("🚀 Starting Consolidated Torrent Simulation: $simulationId")
        
        // Start all simulation components
        simulationScope.launch { coordinateSimulation() }
        simulationScope.launch { processDHTEvents() }
        simulationScope.launch { processTrackerEvents() }
        simulationScope.launch { processPeerEvents() }
        simulationScope.launch { processFileEvents() }
        
        println("✅ Consolidated simulation started")
    }
    
    /**
     * Create torrent with full simulation
     */
    suspend fun createTorrent(
        name: String,
        totalSize: Long,
        pieceSize: Int = 16384,
        files: List<SimulatedFile> = emptyList()
    ): SimulatedTorrent = withContext(context) {
        val infoHash = generateInfoHash(name)
        val totalPieces = (totalSize / pieceSize).toInt() + 1
        
        val torrent = SimulatedTorrent(
            infoHash = infoHash,
            name = name,
            totalSize = totalSize,
            pieceSize = pieceSize,
            files = files,
            totalPieces = totalPieces,
            createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        torrents[infoHash] = torrent
        pieces[infoHash] = mutableMapOf()
        
        // Announce to all simulated networks
        dhtChannel.send(DHTEvent.TorrentAnnounced(infoHash, name))
        trackerChannel.send(TrackerEvent.TorrentAnnounced(infoHash, name))
        
        simulationChannel.send(SimulationEvent.TorrentCreated(torrent))
        
        println("📦 Created torrent: $name (${totalPieces} pieces, ${totalSize} bytes)")
        return@withContext torrent
    }
    
    /**
     * Add peer to simulation
     */
    suspend fun addPeer(
        torrent: SimulatedTorrent,
        address: String,
        port: Int,
        hasPieces: Set<Int> = emptySet()
    ): SimulatedPeer = withContext(context) {
        val peerId = generatePeerId()
        val peerKey = "$address:$port"
        
        val peer = SimulatedPeer(
            peerId = peerId,
            address = address,
            port = port,
            torrent = torrent,
            hasPieces = hasPieces.toMutableSet(),
            downloadSpeed = (1024..10240).random().toLong(),
            uploadSpeed = (512..5120).random().toLong(),
            joinedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        
        peers[peerKey] = peer
        
        // Connect to other peers in same torrent
        val otherPeers = peers.values.filter { 
            it.torrent.infoHash == torrent.infoHash && it != peer 
        }
        peerConnections[peerKey] = otherPeers.map { "${it.address}:${it.port}" }.toMutableSet()
        
        // Notify all systems
        dhtChannel.send(DHTEvent.PeerAdded(torrent.infoHash, peerId))
        trackerChannel.send(TrackerEvent.PeerAdded(torrent.infoHash, peerId))
        peerChannel.send(PeerEvent.PeerJoined(peer))
        
        println("👤 Added peer: $address:$port (${hasPieces.size} pieces)")
        return@withContext peer
    }
    
    /**
     * Simulate piece download between peers
     */
    suspend fun downloadPiece(
        torrent: SimulatedTorrent,
        fromPeer: SimulatedPeer,
        toPeer: SimulatedPeer,
        pieceIndex: Int
    ): Boolean = withContext(context) {
        if (!fromPeer.hasPieces.contains(pieceIndex)) {
            return@withContext false
        }
        
        // Simulate network delay
        delay((50..200).random().toLong())
        
        // Generate piece data
        val pieceData = generatePieceData(torrent.pieceSize)
        
        // Store piece
        pieces.getOrPut(torrent.infoHash) { mutableMapOf() }[pieceIndex] = pieceData
        
        // Update peer stats
        toPeer.downloadedPieces.add(pieceIndex)
        fromPeer.uploadedPieces.add(pieceIndex)
        toPeer.lastActivity = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        fromPeer.lastActivity = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        // Notify systems
        fileChannel.send(FileEvent.PieceStored(torrent.infoHash, pieceIndex, pieceData.a))
        peerChannel.send(PeerEvent.PieceTransferred(fromPeer, toPeer, pieceIndex))
        
        // Update torrent progress
        val downloadedPieces = pieces[torrent.infoHash]?.size ?: 0
        trackerChannel.send(TrackerEvent.ProgressUpdated(torrent.infoHash, downloadedPieces, downloadedPieces * torrent.pieceSize.toLong()))
        
        println("📦 Piece $pieceIndex: ${fromPeer.address} → ${toPeer.address}")
        return@withContext true
    }
    
    /**
     * Simulate complete torrent download
     */
    suspend fun simulateCompleteDownload(
        torrent: SimulatedTorrent,
        downloaderPeer: SimulatedPeer
    ) = withContext(context) {
        println("🔄 Starting complete download: ${torrent.name}")
        
        val neededPieces = (0 until torrent.totalPieces).toMutableSet()
        val availablePeers = peers.values.filter { it.torrent.infoHash == torrent.infoHash }
        
        while (neededPieces.isNotEmpty() && availablePeers.isNotEmpty()) {
            val pieceIndex = neededPieces.random()
            val peerWithPiece = availablePeers.find { it.hasPieces.contains(pieceIndex) }
            
            if (peerWithPiece != null) {
                downloadPiece(torrent, peerWithPiece, downloaderPeer, pieceIndex)
                neededPieces.remove(pieceIndex)
                delay((20..100).random().toLong())
            } else {
                delay(500) // Wait for more peers
            }
        }
        
        if (neededPieces.isEmpty()) {
            simulationChannel.send(SimulationEvent.DownloadCompleted(torrent, downloaderPeer))
            println("🎉 Download completed: ${torrent.name}")
        } else {
            println("⚠️ Download incomplete: ${neededPieces.size} pieces missing")
        }
    }
    
    /**
     * Get peers for torrent
     */
    suspend fun getPeersForTorrent(infoHash: InfoHash): List<SimulatedPeer> = withContext(context) {
        return@withContext peers.values.filter { it.torrent.infoHash == infoHash }
    }
    
    /**
     * Get torrent progress
     */
    suspend fun getTorrentProgress(infoHash: InfoHash): TorrentProgress = withContext(context) {
        val torrent = torrents[infoHash] ?: return@withContext TorrentProgress(0, 0, 0, 0.0)
        val downloadedPieces = pieces[infoHash]?.size ?: 0
        val downloadedBytes = downloadedPieces * torrent.pieceSize.toLong()
        
        return TorrentProgress(
            downloadedPieces = downloadedPieces,
            totalPieces = torrent.totalPieces,
            downloadedBytes = downloadedBytes,
            completionPercentage = (downloadedPieces.toDouble() / torrent.totalPieces) * 100.0
        )
    }
    
    /**
     * Get simulation statistics
     */
    suspend fun getSimulationStats(): SimulationStats = withContext(context) {
        val totalPiecesDownloaded = pieces.values.sumOf { it.size }
        val totalDataTransferred = pieces.values.sumOf { pieceMap ->
            pieceMap.values.sumOf { it.a }
        }
        
        SimulationStats(
            totalTorrents = torrents.size,
            totalPeers = peers.size,
            activeDownloads = torrents.count { it.value.isActive },
            totalPiecesDownloaded = totalPiecesDownloaded,
            totalDataTransferred = totalDataTransferred.toLong(),
            simulationUptime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - torrents.values.firstOrNull()?.createdAt ?: 0
        )
    }
    
    // Event processors - all self-contained
    internal suspend fun coordinateSimulation() = withContext(context) {
        for (event in simulationChannel) {
            when (event) {
                is SimulationEvent.TorrentCreated -> {
                    println("📦 Torrent created: ${event.torrent.name}")
                }
                is SimulationEvent.DownloadCompleted -> {
                    println("🎉 Download completed: ${event.torrent.name}")
                }
            }
        }
    }
    
    internal suspend fun processDHTEvents() = withContext(context) {
        for (event in dhtChannel) {
            when (event) {
                is DHTEvent.TorrentAnnounced -> {
                    delay((10..50).random().toLong())
                    println("🌐 DHT: Torrent announced - ${event.name}")
                }
                is DHTEvent.PeerAdded -> {
                    delay((5..20).random().toLong())
                    println("👤 DHT: Peer added")
                }
            }
        }
    }
    
    internal suspend fun processTrackerEvents() = withContext(context) {
        for (event in trackerChannel) {
            when (event) {
                is TrackerEvent.TorrentAnnounced -> {
                    delay((10..50).random().toLong())
                    println("📡 Tracker: Torrent announced - ${event.name}")
                }
                is TrackerEvent.PeerAdded -> {
                    delay((5..20).random().toLong())
                    println("👤 Tracker: Peer added")
                }
                is TrackerEvent.ProgressUpdated -> {
                    println("📊 Tracker: Progress updated - ${event.downloadedPieces} pieces")
                }
            }
        }
    }
    
    internal suspend fun processPeerEvents() = withContext(context) {
        for (event in peerChannel) {
            when (event) {
                is PeerEvent.PeerJoined -> {
                    println("👤 Peer joined: ${event.peer.address}:${event.peer.port}")
                }
                is PeerEvent.PieceTransferred -> {
                    println("📦 Piece transferred: ${event.pieceIndex}")
                }
            }
        }
    }
    
    internal suspend fun processFileEvents() = withContext(context) {
        for (event in fileChannel) {
            when (event) {
                is FileEvent.PieceStored -> {
                    println("💾 Piece stored: ${event.pieceIndex} (${event.size} bytes)")
                }
            }
        }
    }
    
    /**
     * Stop simulation
     */
    suspend fun stop() = withContext(context) {
        if (!isRunning) return@withContext
        
        isRunning = false
        println("🛑 Stopping Consolidated Simulation")
        
        simulationScope.cancel()
        simulationChannel.close()
        dhtChannel.close()
        trackerChannel.close()
        peerChannel.close()
        fileChannel.close()
        
        println("✅ Consolidated simulation stopped")
    }
    
    // Helper functions - all self-contained
    internal fun generateInfoHash(name: String): InfoHash {
        return 20 j { i -> (name.hashCode() + i).toByte() }
    }
    
    internal fun generatePeerId(): PeerId {
        return 20 j { i -> (i * 7).toByte() }
    }
    
    internal fun generatePieceData(size: Int): ByteIndexed {
        return size j { i -> (i % 256).toByte() }
    }
}

// Consolidated data classes
data class SimulatedTorrent(
    val infoHash: InfoHash,
    val name: String,
    val totalSize: Long,
    val pieceSize: Int,
    val files: List<SimulatedFile>,
    val totalPieces: Int,
    val createdAt: Long,
    val isActive: Boolean = true
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

data class TorrentProgress(
    val downloadedPieces: Int,
    val totalPieces: Int,
    val downloadedBytes: Long,
    val completionPercentage: Double
)

data class SimulationStats(
    val totalTorrents: Int,
    val totalPeers: Int,
    val activeDownloads: Int,
    val totalPiecesDownloaded: Int,
    val totalDataTransferred: Long,
    val simulationUptime: Long
)

// Consolidated events - all self-contained
sealed class SimulationEvent {
    data class TorrentCreated(val torrent: SimulatedTorrent) : SimulationEvent()
    data class DownloadCompleted(val torrent: SimulatedTorrent, val peer: SimulatedPeer) : SimulationEvent()
}

sealed class DHTEvent {
    data class TorrentAnnounced(val infoHash: InfoHash, val name: String) : DHTEvent()
    data class PeerAdded(val infoHash: InfoHash, val peerId: PeerId) : DHTEvent()
}

sealed class TrackerEvent {
    data class TorrentAnnounced(val infoHash: InfoHash, val name: String) : TrackerEvent()
    data class PeerAdded(val infoHash: InfoHash, val peerId: PeerId) : TrackerEvent()
    data class ProgressUpdated(val infoHash: InfoHash, val downloadedPieces: Int, val downloadedBytes: Long) : TrackerEvent()
}

sealed class PeerEvent {
    data class PeerJoined(val peer: SimulatedPeer) : PeerEvent()
    data class PieceTransferred(val fromPeer: SimulatedPeer, val toPeer: SimulatedPeer, val pieceIndex: Int) : PeerEvent()
}

sealed class FileEvent {
    data class PieceStored(val infoHash: InfoHash, val pieceIndex: Int, val size: Int) : FileEvent()
} 