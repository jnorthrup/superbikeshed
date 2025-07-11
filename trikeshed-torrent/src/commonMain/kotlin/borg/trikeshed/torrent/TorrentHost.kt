/*
/*
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import borg.trikeshed.lib.*
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Simple torrent host that can serve files
 */
class TorrentHost(
    internal val port: Int = 6881,
    internal val uploadDir: String = ".",
    internal val downloadDir: String = "."
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TorrentHost>
    override val key: CoroutineContext.Key<*> get() = Key
    
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
} */ */

// CCEK Key-based API extensions for TorrentHost
/**
 * Start torrent host using TorrentHost from context
 */
suspend fun TorrentHost.Key.start(): TorrentHost {
    val host = coroutineContext[this] 
        ?: throw IllegalStateException("TorrentHost not found in context")
    host.start()
    return host
}

/**
 * Add torrent using TorrentHost from context
 */
suspend fun TorrentHost.Key.addTorrent(magnetUri: String): String {
    val host = coroutineContext[this] 
        ?: throw IllegalStateException("TorrentHost not found in context")
    return host.addTorrent(magnetUri)
}

/**
 * Download torrent using TorrentHost from context
 */
suspend fun TorrentHost.Key.download(magnetUri: String): String {
    val host = coroutineContext[this] 
        ?: throw IllegalStateException("TorrentHost not found in context")
    return host.downloadTorrent(magnetUri)
}

/**
 * Create torrent host in context
 */
fun TorrentHost.Key.create(
    port: Int = 6881,
    uploadDir: String = ".",
    downloadDir: String = ".",
    configure: TorrentHost.() -> Unit = {}
): TorrentHost {
    return TorrentHost(port, uploadDir, downloadDir).apply(configure)
} 