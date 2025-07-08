#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

import kotlinx.coroutines.*
import borg.trikeshed.torrent.*

/**
 * Simple demonstration of torrent hosting
 */
suspend fun main() {
    println("🚀 Starting Torrent Hosting Demo")
    
    val host = TorrentHost(
        port = 6881,
        uploadDir = "./uploads",
        downloadDir = "./downloads"
    )
    
    try {
        // Start the host
        host.start()
        println("✅ Torrent host started on port 6881")
        
        // Create a sample torrent
        val sampleTorrent = TorrentHost.TorrentInfo(
            infoHash = "demo-sample-123".toByteArray(),
            name = "Demo Sample File",
            files = listOf(
                TorrentHost.TorrentFile("sample.txt", 1024 * 1024, 0)
            ),
            pieceSize = 16384,
            pieces = List(64) { "piece$it".toByteArray() },
            totalSize = 1024 * 1024
        )
        
        // Add the torrent
        host.addTorrent(sampleTorrent)
        println("📁 Added torrent: ${sampleTorrent.name}")
        
        // Show stats for a few seconds
        repeat(3) {
            val stats = host.getStats()
            println("📊 Stats: ${stats.activeTorrents} torrents, ${stats.connectedPeers} peers")
            delay(2000)
        }
        
        // Remove the torrent
        host.removeTorrent(sampleTorrent.infoHash)
        println("🗑️ Removed torrent: ${sampleTorrent.name}")
        
        val finalStats = host.getStats()
        println("📊 Final stats: ${finalStats.activeTorrents} torrents")
        
    } finally {
        // Stop the host
        host.stop()
        println("🛑 Torrent host stopped")
    }
    
    println("✅ Demo completed successfully!")
}

// Run the demo
runBlocking {
    main()
} 