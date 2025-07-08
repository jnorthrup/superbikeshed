@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent

import kotlinx.coroutines.*

/**
 * Demonstration of torrent hosting functionality
 */
object TorrentDemo {
    
    suspend fun runDemo() {
        println("=== Torrent Hosting Demo ===")
        
        // Create a torrent host
        val host = TorrentHost(
            port = 6881,
            uploadDir = "./uploads",
            downloadDir = "./downloads"
        )
        
        // Start the host
        println("Starting torrent host...")
        host.start()
        
        // Create some sample torrents
        val sampleTorrents = listOf(
            TorrentHost.TorrentInfo(
                infoHash = "sample-movie-123".toByteArray(),
                name = "Sample Movie",
                files = listOf(
                    TorrentHost.TorrentFile("movie.mp4", 1024 * 1024 * 100, 0)
                ),
                pieceSize = 16384,
                pieces = List(100) { "piece$it".toByteArray() },
                totalSize = 1024 * 1024 * 100
            ),
            TorrentHost.TorrentInfo(
                infoHash = "sample-archive-456".toByteArray(),
                name = "Sample Archive",
                files = listOf(
                    TorrentHost.TorrentFile("archive.zip", 1024 * 1024 * 50, 0)
                ),
                pieceSize = 16384,
                pieces = List(50) { "piece$it".toByteArray() },
                totalSize = 1024 * 1024 * 50
            )
        )
        
        // Add torrents to host
        sampleTorrents.forEach { torrent ->
            host.addTorrent(torrent)
            println("Added torrent: ${torrent.name}")
        }
        
        // Show stats
        repeat(5) {
            val stats = host.getStats()
            println("Host Stats: ${stats.activeTorrents} torrents, ${stats.connectedPeers} peers")
            delay(2000)
        }
        
        // Remove one torrent
        host.removeTorrent(sampleTorrents[0].infoHash)
        println("Removed torrent: ${sampleTorrents[0].name}")
        
        // Show final stats
        val finalStats = host.getStats()
        println("Final Stats: ${finalStats.activeTorrents} torrents")
        
        // Stop the host
        host.stop()
        println("Torrent host stopped")
    }
    
    /**
     * Run a simple hosting scenario
     */
    suspend fun runSimpleHosting() {
        println("=== Simple Torrent Hosting ===")
        
        val host = TorrentHost(port = 6882)
        
        coroutineScope {
            // Start host in background
            launch {
                host.start()
            }
            
            delay(1000) // Wait for host to start
            
            // Add a torrent
            val torrent = TorrentHost.TorrentInfo(
                infoHash = "demo-file-789".toByteArray(),
                name = "Demo File",
                files = listOf(
                    TorrentHost.TorrentFile("demo.txt", 1024, 0)
                ),
                pieceSize = 16384,
                pieces = listOf("demo-piece".toByteArray()),
                totalSize = 1024
            )
            
            host.addTorrent(torrent)
            println("Hosting: ${torrent.name}")
            
            // Let it run for a bit
            delay(5000)
            
            // Stop
            host.stop()
        }
    }
} 