#!/usr/bin/env kotlin

@file:DependsOn("kotlinx-coroutines-core:1.7.3")
@file:DependsOn("kotlinx-serialization-json:1.6.0")

import borg.trikeshed.torrent.batch.*
import borg.trikeshed.torrent.client.TrikeAriaClient
import borg.trikeshed.torrent.client.TrikeRpcClient
import borg.trikeshed.torrent.rpc.TorrentRpcServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
    println("🚀 TrikeAria Batch Control & Media Player Demo")
    println("=" * 50)
    
    // Initialize RPC server and client
    val rpcServer = TorrentRpcServer()
    val rpcClient = TrikeRpcClient("http://localhost:6800/jsonrpc")
    val ariaClient = TrikeAriaClient(rpcClient)
    
    println("\n📦 Creating Media Batches")
    println("-" * 30)
    
    // Create different types of batches
    val movieBatch = ariaClient.createBatch(
        name = "Movie Collection",
        maxConcurrent = 2,
        priority = "HIGH",
        mediaPlayerIntegration = true
    )
    println("✅ Created movie batch: $movieBatch")
    
    val musicBatch = ariaClient.createBatch(
        name = "Music Albums",
        maxConcurrent = 3,
        priority = "NORMAL",
        mediaPlayerIntegration = true
    )
    println("✅ Created music batch: $musicBatch")
    
    val documentBatch = ariaClient.createBatch(
        name = "Documents",
        maxConcurrent = 1,
        priority = "LOW",
        mediaPlayerIntegration = false
    )
    println("✅ Created document batch: $documentBatch")
    
    println("\n🎬 Adding Content to Movie Batch")
    println("-" * 30)
    
    // Add movies to batch
    val movieUrls = listOf(
        "magnet:?xt=urn:btih:movie1_hash&dn=The+Matrix",
        "magnet:?xt=urn:btih:movie2_hash&dn=Inception",
        "magnet:?xt=urn:btih:movie3_hash&dn=Interstellar"
    )
    
    movieUrls.forEach { url ->
        val torrentId = ariaClient.addTorrentToBatch(movieBatch, url)
        println("➕ Added movie torrent: $torrentId")
    }
    
    println("\n🎵 Adding Content to Music Batch")
    println("-" * 30)
    
    // Add music to batch
    val musicUrls = listOf(
        "magnet:?xt=urn:btih:music1_hash&dn=Album+1",
        "magnet:?xt=urn:btih:music2_hash&dn=Album+2",
        "magnet:?xt=urn:btih:music3_hash&dn=Album+3"
    )
    
    musicUrls.forEach { url ->
        val torrentId = ariaClient.addTorrentToBatch(musicBatch, url)
        println("➕ Added music torrent: $torrentId")
    }
    
    println("\n📄 Adding Content to Document Batch")
    println("-" * 30)
    
    // Add documents to batch
    val docUrls = listOf(
        "magnet:?xt=urn:btih:doc1_hash&dn=Manual.pdf",
        "magnet:?xt=urn:btih:doc2_hash&dn=Guide.pdf"
    )
    
    docUrls.forEach { url ->
        val torrentId = ariaClient.addTorrentToBatch(documentBatch, url)
        println("➕ Added document torrent: $torrentId")
    }
    
    println("\n🎯 Starting Batches with Priority Control")
    println("-" * 30)
    
    // Start batches in priority order
    ariaClient.startBatch(movieBatch)
    println("▶️ Started movie batch (HIGH priority)")
    
    ariaClient.startBatch(musicBatch)
    println("▶️ Started music batch (NORMAL priority)")
    
    ariaClient.startBatch(documentBatch)
    println("▶️ Started document batch (LOW priority)")
    
    println("\n📊 Batch Status Overview")
    println("-" * 30)
    
    val allBatches = ariaClient.getAllBatchStatuses()
    allBatches.forEach { batch ->
        println("📦 ${batch["name"]}: ${batch["status"]} (${batch["torrentCount"]} torrents, ${(batch["totalProgress"] as Double * 100).toInt()}% complete)")
    }
    
    println("\n🎮 Media Player Integration Demo")
    println("-" * 30)
    
    // Set up media player playlists
    val movieTorrentIds = movieUrls.map { "hash_${it.hashCode()}" }
    ariaClient.setMediaPlaylist(movieBatch, movieTorrentIds)
    println("📋 Set movie playlist: ${movieTorrentIds.size} items")
    
    val musicTorrentIds = musicUrls.map { "hash_${it.hashCode()}" }
    ariaClient.setMediaPlaylist(musicBatch, musicTorrentIds)
    println("📋 Set music playlist: ${musicTorrentIds.size} items")
    
    // Control media players
    ariaClient.playMedia(movieBatch)
    println("▶️ Started movie playback")
    
    delay(2.seconds)
    
    ariaClient.pauseMedia(movieBatch)
    println("⏸️ Paused movie playback")
    
    ariaClient.playMedia(musicBatch)
    println("▶️ Started music playback")
    
    delay(1.seconds)
    
    ariaClient.nextMedia(musicBatch)
    println("⏭️ Next track in music playlist")
    
    println("\n🎛️ Batch Priority Management")
    println("-" * 30)
    
    // Change priorities dynamically
    ariaClient.setBatchPriority(documentBatch, "HIGH")
    println("⬆️ Elevated document batch to HIGH priority")
    
    ariaClient.setBatchPriority(movieBatch, "NORMAL")
    println("⬇️ Lowered movie batch to NORMAL priority")
    
    println("\n📋 Batch Reordering")
    println("-" * 30)
    
    ariaClient.reorderBatch(musicBatch, 0)
    println("🔄 Moved music batch to front of queue")
    
    ariaClient.reorderBatch(documentBatch, 1)
    println("🔄 Moved document batch to second position")
    
    println("\n⏸️ Batch Control Operations")
    println("-" * 30)
    
    ariaClient.pauseBatch(musicBatch)
    println("⏸️ Paused music batch")
    
    delay(1.seconds)
    
    ariaClient.resumeBatch(musicBatch)
    println("▶️ Resumed music batch")
    
    println("\n📈 Real-time Batch Monitoring")
    println("-" * 30)
    
    // Monitor a batch for a short period
    val monitoringJob = launch {
        var count = 0
        while (count < 5) {
            val status = ariaClient.getBatchStatus(movieBatch)
            if (status != null) {
                val progress = (status["totalProgress"] as Double * 100).toInt()
                val speed = status["downloadSpeed"] as Double
                println("📊 Movie batch: ${progress}% complete, ${String.format("%.2f", speed)} MB/s")
            }
            delay(1.seconds)
            count++
        }
    }
    
    monitoringJob.join()
    
    println("\n🎯 Convenience Batch Operations")
    println("-" * 30)
    
    // Use convenience methods
    val quickBatch = ariaClient.batchDownload(
        torrentUrls = listOf(
            "magnet:?xt=urn:btih:quick1_hash&dn=Quick+Download+1",
            "magnet:?xt=urn:btih:quick2_hash&dn=Quick+Download+2"
        ),
        batchName = "Quick Downloads"
    )
    println("⚡ Created quick batch: $quickBatch")
    
    println("\n🧹 Cleanup")
    println("-" * 30)
    
    // Clean up batches
    ariaClient.removeBatch(quickBatch)
    println("🗑️ Removed quick batch")
    
    ariaClient.removeBatch(documentBatch)
    println("🗑️ Removed document batch")
    
    ariaClient.removeBatch(musicBatch)
    println("🗑️ Removed music batch")
    
    ariaClient.removeBatch(movieBatch)
    println("🗑️ Removed movie batch")
    
    println("\n✅ Demo completed successfully!")
    println("\n🎉 Key Features Demonstrated:")
    println("• Batch creation with different priorities and media player integration")
    println("• Torrent addition to batches")
    println("• Priority-based batch ordering and management")
    println("• Media player control (play, pause, next, previous, seek)")
    println("• Playlist management for media batches")
    println("• Real-time batch monitoring")
    println("• Batch reordering and priority changes")
    println("• Convenience methods for quick batch operations")
    println("• Proper cleanup and resource management")
} 