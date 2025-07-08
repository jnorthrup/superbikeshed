#!/usr/bin/env kotlin

@file:DependsOn("kotlinx-serialization-json:1.5.1")
@file:DependsOn("kotlinx-coroutines-core:1.7.3")

import borg.trikeshed.torrent.*
import borg.trikeshed.torrent.rpc.*
import borg.trikeshed.services.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * Demo: aria2c-style RPC Server with Distributed Objects
 * 
 * This demonstrates:
 * 1. RPC server with aria2c-compatible API
 * 2. Distributed objects using RequestFactory pattern
 * 3. Multi-node coordination
 * 4. Real-time progress tracking
 */

fun main() = runBlocking {
    println("🚀 Starting TrikeDownloader RPC Server Demo")
    println("=" * 50)
    
    // Create core components
    val downloader = TrikeDownloader()
    val requestFactory = RequestFactoryServiceImpl(createMockContext())
    
    // Create distributed objects
    val registry = DistributedDownloadRegistry(requestFactory, "node-1")
    val tracker = DistributedProgressTracker(requestFactory, "node-1")
    val sessionManager = DistributedSessionManager(requestFactory, "node-1")
    val coordinator = DistributedNodeCoordinator(requestFactory, "node-1", registry, tracker, sessionManager)
    
    // Create RPC server
    val rpcServer = TorrentRpcServer(downloader, requestFactory)
    
    // Register node capabilities
    coordinator.registerNode(setOf("http", "torrent", "ftp"))
    
    println("✅ Components initialized")
    println()
    
    // Demo 1: HTTP Download via RPC
    println("📥 Demo 1: HTTP Download via RPC")
    println("-" * 30)
    
    val httpRequest = RpcRequest(
        id = "req-1",
        method = "aria2.addUri",
        params = mapOf(
            "uris" to listOf("https://example.com/file.zip"),
            "options" to mapOf(
                "header" to listOf("User-Agent: TrikeDownloader/1.0"),
                "timeout" to 30000L
            )
        )
    )
    
    val httpResponse = rpcServer.handleRpcRequest(httpRequest)
    println("HTTP Download Response: ${httpResponse.result}")
    
    // Demo 2: Torrent Download via RPC
    println("\n📥 Demo 2: Torrent Download via RPC")
    println("-" * 30)
    
    val torrentRequest = RpcRequest(
        id = "req-2",
        method = "aria2.addTorrent",
        params = mapOf(
            "torrent" to "magnet:?xt=urn:btih:example",
            "uris" to listOf("udp://tracker.example.com:1337"),
            "options" to mapOf(
                "piece-length" to 16384
            )
        )
    )
    
    val torrentResponse = rpcServer.handleRpcRequest(torrentRequest)
    println("Torrent Download Response: ${torrentResponse.result}")
    
    // Demo 3: Status Queries
    println("\n📊 Demo 3: Status Queries")
    println("-" * 30)
    
    val statusRequest = RpcRequest(
        id = "req-3",
        method = "aria2.tellStatus",
        params = mapOf(
            "gid" to httpResponse.result,
            "keys" to listOf("status", "completedLength", "totalLength", "downloadSpeed")
        )
    )
    
    val statusResponse = rpcServer.handleRpcRequest(statusRequest)
    println("Status Response: ${statusResponse.result}")
    
    // Demo 4: Global Statistics
    println("\n📈 Demo 4: Global Statistics")
    println("-" * 30)
    
    val globalStatRequest = RpcRequest(
        id = "req-4",
        method = "aria2.getGlobalStat"
    )
    
    val globalStatResponse = rpcServer.handleRpcRequest(globalStatRequest)
    println("Global Stats: ${globalStatResponse.result}")
    
    // Demo 5: Distributed Session Management
    println("\n🔄 Demo 5: Distributed Session Management")
    println("-" * 30)
    
    val sessionId = "demo-session-${System.currentTimeMillis()}"
    val session = registry.createSession(sessionId, mapOf("demo" to "true"))
    println("Created session: ${session.sessionId}")
    
    // Add download to distributed registry
    val downloadId = httpResponse.result as String
    val task = DownloadTask.HttpDownload(
        id = downloadId,
        url = "https://example.com/file.zip",
        method = "GET"
    )
    
    registry.addDownload(sessionId, downloadId, task)
    println("Added download to distributed registry")
    
    // Update progress in distributed tracker
    val progress = DownloadProgress(
        id = downloadId,
        status = DownloadStatus.DOWNLOADING,
        downloadedBytes = 1024 * 1024L,
        totalBytes = 10 * 1024 * 1024L,
        downloadSpeed = 1024 * 100L
    )
    
    tracker.updateProgress(downloadId, progress)
    println("Updated progress in distributed tracker")
    
    // Demo 6: Multi-Node Coordination
    println("\n🌐 Demo 6: Multi-Node Coordination")
    println("-" * 30)
    
    // Simulate multiple nodes
    val node2Coordinator = DistributedNodeCoordinator(
        requestFactory, "node-2",
        DistributedDownloadRegistry(requestFactory, "node-2"),
        DistributedProgressTracker(requestFactory, "node-2"),
        DistributedSessionManager(requestFactory, "node-2")
    )
    
    node2Coordinator.registerNode(setOf("http", "torrent"))
    println("Registered node-2 with HTTP and torrent capabilities")
    
    val distributedNodes = coordinator.listNodes()
    println("Connected nodes: $distributedNodes")
    
    // Distribute download across nodes
    val torrentTask = DownloadTask.TorrentDownload(
        id = "torrent-1",
        url = "magnet:?xt=urn:btih:example",
        trackers = listOf("udp://tracker.example.com:1337")
    )
    
    val targetNodes = coordinator.distributeDownload(sessionId, "torrent-1", torrentTask)
    println("Distributed torrent download to nodes: $targetNodes")
    
    // Demo 7: Session Persistence
    println("\n💾 Demo 7: Session Persistence")
    println("-" * 30)
    
    val saved = sessionManager.saveSession(session)
    println("Session saved: $saved")
    
    val loaded = sessionManager.loadSession(sessionId)
    println("Session loaded: ${loaded?.sessionId}")
    
    val allSessions = sessionManager.listSessions()
    println("Total saved sessions: ${allSessions.size}")
    
    // Demo 8: RPC Method Validation
    println("\n🔒 Demo 8: RPC Method Validation")
    println("-" * 30)
    
    val invalidRequest = RpcRequest(
        id = "req-invalid",
        method = "aria2.invalidMethod"
    )
    
    val invalidResponse = rpcServer.handleRpcRequest(invalidRequest)
    println("Invalid method response: ${invalidResponse.error}")
    
    // Demo 9: Progress Subscription
    println("\n📡 Demo 9: Progress Subscription")
    println("-" * 30)
    
    // Subscribe to progress updates
    val progressFlow = tracker.subscribeToProgress(downloadId)
    
    // Launch progress updates
    launch {
        repeat(5) { i ->
            delay(500)
            val newProgress = progress.copy(
                downloadedBytes = progress.downloadedBytes + (1024 * 1024L * (i + 1)),
                downloadSpeed = 1024 * 100L + (1024 * 10L * i)
            )
            tracker.updateProgress(downloadId, newProgress)
        }
    }
    
    // Collect progress updates
    progressFlow.take(5).collect { progress ->
        println("Progress update: ${progress.downloadedBytes}/${progress.totalBytes} bytes (${progress.downloadSpeed} B/s)")
    }
    
    // Demo 10: Cleanup
    println("\n🧹 Demo 10: Cleanup")
    println("-" * 30)
    
    // Pause all downloads
    val pauseAllRequest = RpcRequest(
        id = "req-pause",
        method = "aria2.pauseAll"
    )
    
    val pauseAllResponse = rpcServer.handleRpcRequest(pauseAllRequest)
    println("Pause all response: ${pauseAllResponse.result}")
    
    // Remove downloads
    val removeRequest = RpcRequest(
        id = "req-remove",
        method = "aria2.remove",
        params = mapOf("gid" to downloadId)
    )
    
    val removeResponse = rpcServer.handleRpcRequest(removeRequest)
    println("Remove download response: ${removeResponse.result}")
    
    // Cleanup distributed objects
    tracker.unsubscribeFromProgress(downloadId)
    registry.deleteSession(sessionId)
    sessionManager.deleteSession(sessionId)
    coordinator.unregisterNode()
    
    println("✅ Cleanup completed")
    
    println("\n🎉 RPC Server Demo Completed!")
    println("=" * 50)
    println()
    println("Key Features Demonstrated:")
    println("• aria2c-compatible JSON-RPC API")
    println("• Distributed object management")
    println("• Multi-node coordination")
    println("• Real-time progress tracking")
    println("• Session persistence")
    println("• Request validation")
    println("• Graceful cleanup")
}

// Helper function to create mock context
private fun createMockContext(): HttpServerContext {
    return object : HttpServerContext {
        override val ioModel: String = "mock"
        override fun registerPacker(size: Int): Int = size
    }
}

// Helper extension
private operator fun String.times(n: Int): String = repeat(n) 