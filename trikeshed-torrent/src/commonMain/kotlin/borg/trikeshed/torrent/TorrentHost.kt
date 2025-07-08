@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Simple torrent host that can serve files
 */
class TorrentHost(
    internal val port: Int = 6881,
    internal val uploadDir: String = "./uploads",
    internal val downloadDir: String = "./downloads"
) {
    
    internal var isRunning = false
    internal val activeTorrents = mutableMapOf<String, TorrentInfo>()
    internal val peerConnections = mutableMapOf<String, PeerConnection>()
    
    data class TorrentInfo(
        val infoHash: InfoHash,
        val name: String,
        val files: List<TorrentFile>,
        val pieceSize: Int,
        val pieces: List<PieceHash>,
        val totalSize: Long
    )
    
    data class TorrentFile(
        val path: String,
        val size: Long,
        val offset: Long
    )
    
    data class PeerConnection(
        val address: String,
        val port: Int,
        val infoHash: InfoHash,
        val uploadSpeed: Long = 0,
        val downloadSpeed: Long = 0
    )
    
    /**
     * Start the torrent host
     */
    suspend fun start() {
        if (isRunning) return
        
        println("Starting TorrentHost on port $port")
        println("Upload directory: $uploadDir")
        println("Download directory: $downloadDir")
        
        isRunning = true
        
        // Start listening for peer connections
        launch {
            listenForPeers()
        }
        
        // Start DHT integration
        launch {
            startDHT()
        }
    }
    
    /**
     * Stop the torrent host
     */
    suspend fun stop() {
        if (!isRunning) return
        
        println("Stopping TorrentHost")
        isRunning = false
        
        // Close all peer connections
        peerConnections.values.forEach { peer ->
            // Close peer connection
        }
        peerConnections.clear()
    }
    
    /**
     * Add a torrent to host
     */
    suspend fun addTorrent(torrentInfo: TorrentInfo) {
        activeTorrents[torrentInfo.infoHash.contentToString()] = torrentInfo
        println("Added torrent: ${torrentInfo.name}")
        
        // Announce to DHT
        announceToDHT(torrentInfo.infoHash)
    }
    
    /**
     * Remove a torrent from hosting
     */
    suspend fun removeTorrent(infoHash: InfoHash) {
        val hashString = infoHash.contentToString()
        activeTorrents.remove(hashString)
        println("Removed torrent: $hashString")
    }
    
    /**
     * Get statistics about the host
     */
    fun getStats(): HostStats {
        return HostStats(
            isRunning = isRunning,
            port = port,
            activeTorrents = activeTorrents.size,
            connectedPeers = peerConnections.size,
            totalUploadSpeed = peerConnections.values.sumOf { it.uploadSpeed },
            totalDownloadSpeed = peerConnections.values.sumOf { it.downloadSpeed }
        )
    }
    
    data class HostStats(
        val isRunning: Boolean,
        val port: Int,
        val activeTorrents: Int,
        val connectedPeers: Int,
        val totalUploadSpeed: Long,
        val totalDownloadSpeed: Long
    )
    
    internal suspend fun listenForPeers() {
        // Simulate peer listening
        while (isRunning) {
            delay(1000)
            // In real implementation: accept incoming peer connections
        }
    }
    
    internal suspend fun startDHT() {
        // Simulate DHT startup
        println("Starting DHT integration")
        delay(100)
        println("DHT started successfully")
    }
    
    internal suspend fun announceToDHT(infoHash: InfoHash) {
        // Simulate DHT announcement
        println("Announcing ${infoHash.contentToString()} to DHT")
        delay(100)
    }
} 