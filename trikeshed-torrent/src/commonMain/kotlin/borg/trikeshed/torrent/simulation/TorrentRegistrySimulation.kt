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
 * Torrent Registry Simulation
 * 
 * Simulates torrent and peer registry management:
 * - Torrent registration and lookup
 * - Peer management per torrent
 * - Statistics tracking
 * - All operations use coroutine context wiring
 */
class TorrentRegistrySimulation(
    internal val context: CoroutineContext = Dispatchers.IO
) {
    
    // Registry state
    internal val torrents = mutableMapOf<InfoHash, SimulatedTorrent>()
    internal val torrentPeers = mutableMapOf<InfoHash, MutableSet<SimulatedPeer>>()
    internal val torrentStats = mutableMapOf<InfoHash, TorrentStatistics>()
    
    // Communication channels
    internal val registryChannel = Channel<RegistryEvent>(capacity = 1000)
    internal val registryScope = CoroutineScope(context + CoroutineName("registry-sim"))
    
    /**
     * Start torrent registry simulation
     */
    suspend fun start() = withContext(context) {
        println("📋 Starting Torrent Registry Simulation")
        
        // Start registry event processor
        registryScope.launch {
            processRegistryEvents()
        }
        
        println("✅ Torrent Registry Simulation started")
    }
    
    /**
     * Register a torrent
     */
    suspend fun registerTorrent(torrent: SimulatedTorrent) = withContext(context) {
        torrents[torrent.infoHash] = torrent
        
        // Initialize statistics
        torrentStats[torrent.infoHash] = TorrentStatistics(
            infoHash = torrent.infoHash,
            totalPeers = 0,
            activePeers = 0,
            totalPieces = torrent.totalPieces,
            downloadedPieces = 0,
            totalSize = torrent.totalSize,
            downloadedBytes = 0,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
        
        registryChannel.send(RegistryEvent.TorrentRegistered(torrent))
        
        println("📋 Registered torrent: ${torrent.name} (${torrent.totalPieces} pieces)")
    }
    
    /**
     * Add a peer to a torrent
     */
    suspend fun addPeer(infoHash: InfoHash, peer: SimulatedPeer) = withContext(context) {
        torrentPeers.getOrPut(infoHash) { mutableSetOf() }.add(peer)
        
        // Update statistics
        torrentStats[infoHash]?.let { stats ->
            torrentStats[infoHash] = stats.copy(
                totalPeers = torrentPeers[infoHash]?.size ?: 0,
                activePeers = torrentPeers[infoHash]?.count { it.lastActivity > System.currentTimeMillis() - 300000 } ?: 0, // 5 minutes
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        registryChannel.send(RegistryEvent.PeerAdded(infoHash, peer))
        
        println("👤 Added peer to torrent: ${peer.address}:${peer.port}")
    }
    
    /**
     * Remove a peer from a torrent
     */
    suspend fun removePeer(infoHash: InfoHash, peer: SimulatedPeer) = withContext(context) {
        torrentPeers[infoHash]?.remove(peer)
        
        // Update statistics
        torrentStats[infoHash]?.let { stats ->
            torrentStats[infoHash] = stats.copy(
                totalPeers = torrentPeers[infoHash]?.size ?: 0,
                activePeers = torrentPeers[infoHash]?.count { it.lastActivity > System.currentTimeMillis() - 300000 } ?: 0,
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        registryChannel.send(RegistryEvent.PeerRemoved(infoHash, peer))
        
        println("👋 Removed peer from torrent: ${peer.address}:${peer.port}")
    }
    
    /**
     * Update torrent progress
     */
    suspend fun updateProgress(
        infoHash: InfoHash,
        downloadedPieces: Int,
        downloadedBytes: Long
    ) = withContext(context) {
        torrentStats[infoHash]?.let { stats ->
            torrentStats[infoHash] = stats.copy(
                downloadedPieces = downloadedPieces,
                downloadedBytes = downloadedBytes,
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        registryChannel.send(RegistryEvent.ProgressUpdated(infoHash, downloadedPieces, downloadedBytes))
        
        println("📊 Updated progress: $downloadedPieces pieces, $downloadedBytes bytes")
    }
    
    /**
     * Get torrent by info hash
     */
    suspend fun getTorrent(infoHash: InfoHash): SimulatedTorrent? = withContext(context) {
        return@withContext torrents[infoHash]
    }
    
    /**
     * Get peers for a torrent
     */
    suspend fun getPeersForTorrent(infoHash: InfoHash): List<SimulatedPeer> = withContext(context) {
        return@withContext torrentPeers[infoHash]?.toList() ?: emptyList()
    }
    
    /**
     * Get torrent statistics
     */
    suspend fun getTorrentStats(infoHash: InfoHash): TorrentStatistics? = withContext(context) {
        return@withContext torrentStats[infoHash]
    }
    
    /**
     * Get all torrents
     */
    suspend fun getAllTorrents(): List<SimulatedTorrent> = withContext(context) {
        return@withContext torrents.values.toList()
    }
    
    /**
     * Search torrents by name
     */
    suspend fun searchTorrents(query: String): List<SimulatedTorrent> = withContext(context) {
        return@withContext torrents.values.filter { 
            it.name.contains(query, ignoreCase = true) 
        }
    }
    
    /**
     * Get torrents by status
     */
    suspend fun getTorrentsByStatus(isActive: Boolean): List<SimulatedTorrent> = withContext(context) {
        return@withContext torrents.values.filter { it.isActive == isActive }
    }
    
    /**
     * Process registry events
     */
    internal suspend fun processRegistryEvents() = withContext(context) {
        try {
            for (event in registryChannel) {
                when (event) {
                    is RegistryEvent.TorrentRegistered -> {
                        handleTorrentRegistered(event)
                    }
                    is RegistryEvent.PeerAdded -> {
                        handlePeerAdded(event)
                    }
                    is RegistryEvent.PeerRemoved -> {
                        handlePeerRemoved(event)
                    }
                    is RegistryEvent.ProgressUpdated -> {
                        handleProgressUpdated(event)
                    }
                    is RegistryEvent.TorrentCompleted -> {
                        handleTorrentCompleted(event)
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Registry event processing error: ${e.message}")
        }
    }
    
    // Event handlers
    internal suspend fun handleTorrentRegistered(event: RegistryEvent.TorrentRegistered) = withContext(context) {
        println("📋 Registry: Torrent registered - ${event.torrent.name}")
    }
    
    internal suspend fun handlePeerAdded(event: RegistryEvent.PeerAdded) = withContext(context) {
        println("👤 Registry: Peer added to torrent")
    }
    
    internal suspend fun handlePeerRemoved(event: RegistryEvent.PeerRemoved) = withContext(context) {
        println("👋 Registry: Peer removed from torrent")
    }
    
    internal suspend fun handleProgressUpdated(event: RegistryEvent.ProgressUpdated) = withContext(context) {
        println("📊 Registry: Progress updated for torrent")
    }
    
    internal suspend fun handleTorrentCompleted(event: RegistryEvent.TorrentCompleted) = withContext(context) {
        println("🎉 Registry: Torrent completed - ${event.torrent.name}")
    }
    
    /**
     * Get registry statistics
     */
    suspend fun getRegistryStats(): RegistryStats = withContext(context) {
        val totalTorrents = torrents.size
        val activeTorrents = torrents.count { it.value.isActive }
        val totalPeers = torrentPeers.values.sumOf { it.size }
        val totalPieces = torrents.values.sumOf { it.totalPieces }
        val downloadedPieces = torrentStats.values.sumOf { it.downloadedPieces }
        
        RegistryStats(
            totalTorrents = totalTorrents,
            activeTorrents = activeTorrents,
            totalPeers = totalPeers,
            totalPieces = totalPieces,
            downloadedPieces = downloadedPieces,
            averagePeersPerTorrent = if (totalTorrents > 0) totalPeers.toDouble() / totalTorrents else 0.0
        )
    }
    
    /**
     * Stop torrent registry simulation
     */
    suspend fun stop() = withContext(context) {
        println("🛑 Stopping Torrent Registry Simulation")
        registryScope.cancel()
        registryChannel.close()
    }
}

// Registry data classes
data class TorrentStatistics(
    val infoHash: InfoHash,
    val totalPeers: Int,
    val activePeers: Int,
    val totalPieces: Int,
    val downloadedPieces: Int,
    val totalSize: Long,
    val downloadedBytes: Long,
    val createdAt: Long,
    val lastUpdated: Long
)

data class RegistryStats(
    val totalTorrents: Int,
    val activeTorrents: Int,
    val totalPeers: Int,
    val totalPieces: Int,
    val downloadedPieces: Int,
    val averagePeersPerTorrent: Double
)

// Registry events
sealed class RegistryEvent {
    data class TorrentRegistered(val torrent: SimulatedTorrent) : RegistryEvent()
    data class PeerAdded(val infoHash: InfoHash, val peer: SimulatedPeer) : RegistryEvent()
    data class PeerRemoved(val infoHash: InfoHash, val peer: SimulatedPeer) : RegistryEvent()
    data class ProgressUpdated(val infoHash: InfoHash, val downloadedPieces: Int, val downloadedBytes: Long) : RegistryEvent()
    data class TorrentCompleted(val torrent: SimulatedTorrent) : RegistryEvent()
} 