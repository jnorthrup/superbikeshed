#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

import kotlinx.coroutines.*
import borg.trikeshed.torrent.*

/**
 * Demonstration of TrikeDownloader with both aria2c and curl personas
 */
suspend fun main() {
    println("🚀 TrikeDownloader Demo - aria2c + curl combined")
    println("=" * 60)
    
    val downloader = TrikeDownloader(
        maxConcurrentDownloads = 3,
        downloadDir = "./demo-downloads",
        tempDir = "./demo-temp"
    )
    
    try {
        // Start the downloader
        downloader.start()
        
        // Demo 1: HTTP downloads (curl-like)
        println("\n📡 Demo 1: HTTP Downloads (curl-like)")
        println("-" * 40)
        
        val httpId1 = downloader.addHttpDownload(
            url = "https://httpbin.org/json",
            outputPath = "./demo-downloads/sample.json"
        )
        
        val httpId2 = downloader.addHttpDownload(
            url = "https://httpbin.org/bytes/1024",
            outputPath = "./demo-downloads/sample.bin"
        )
        
        println("✅ Added HTTP downloads: $httpId1, $httpId2")
        
        // Demo 2: Torrent downloads (aria2c-like)
        println("\n🔗 Demo 2: Torrent Downloads (aria2c-like)")
        println("-" * 40)
        
        val torrentId1 = downloader.addTorrentDownload(
            url = "magnet:?xt=urn:btih:demo123456789",
            outputPath = "./demo-downloads/demo-torrent",
            maxPeers = 20
        )
        
        val torrentId2 = downloader.addTorrentDownload(
            url = "https://example.com/sample.torrent",
            outputPath = "./demo-downloads/sample-torrent",
            trackers = listOf("udp://tracker.opentrackr.org:1337")
        )
        
        println("✅ Added torrent downloads: $torrentId1, $torrentId2")
        
        // Demo 3: Progress monitoring
        println("\n📊 Demo 3: Progress Monitoring")
        println("-" * 40)
        
        repeat(5) { iteration ->
            delay(2000)
            
            val stats = downloader.getStats()
            println("📈 Progress Update #${iteration + 1}:")
            println("  Active downloads: ${stats.activeDownloads}")
            println("  Total downloaded: ${stats.totalDownloadedBytes / 1024}KB")
            println("  Completed: ${stats.completedDownloads}")
            
            // Show individual progress
            val downloads = downloader.listDownloads()
            downloads.forEach { progress ->
                val percentage = if (progress.totalBytes > 0) {
                    (progress.downloadedBytes * 100 / progress.totalBytes)
                } else 0
                
                val speed = when {
                    progress.speed > 1024 * 1024 -> "${progress.speed / (1024 * 1024)}MB/s"
                    progress.speed > 1024 -> "${progress.speed / 1024}KB/s"
                    else -> "${progress.speed}B/s"
                }
                
                println("  [${progress.id}] $percentage% - $speed (${progress.connections} conn)")
            }
            println()
        }
        
        // Demo 4: Download management
        println("\n🎛️ Demo 4: Download Management")
        println("-" * 40)
        
        // Pause a download
        downloader.pauseDownload(httpId1)
        println("⏸️ Paused download: $httpId1")
        
        delay(1000)
        
        // Resume the download
        downloader.resumeDownload(httpId1)
        println("▶️ Resumed download: $httpId1")
        
        delay(1000)
        
        // Cancel a download
        downloader.cancelDownload(torrentId2)
        println("❌ Cancelled download: $torrentId2")
        
        // Final stats
        delay(2000)
        val finalStats = downloader.getStats()
        println("\n📊 Final Statistics:")
        println("  Total downloads: ${finalStats.totalDownloads}")
        println("  Active: ${finalStats.activeDownloads}")
        println("  Paused: ${finalStats.pausedDownloads}")
        println("  Completed: ${finalStats.completedDownloads}")
        println("  Failed: ${finalStats.failedDownloads}")
        println("  Total downloaded: ${finalStats.totalDownloadedBytes / 1024}KB")
        
    } finally {
        downloader.stop()
        println("\n🛑 Demo completed!")
    }
}

// Run the demo
runBlocking {
    main()
} 