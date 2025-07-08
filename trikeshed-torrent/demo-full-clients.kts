#!/usr/bin/env kotlin

@file:DependsOn("kotlinx-serialization-json:1.5.1")
@file:DependsOn("kotlinx-coroutines-core:1.7.3")

import borg.trikeshed.torrent.*
import borg.trikeshed.torrent.client.*
import borg.trikeshed.torrent.rpc.*
import borg.trikeshed.services.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/**
 * Demo: Full Client Implementations
 * 
 * This demonstrates:
 * 1. TrikeAriaClient - Programmatic aria2c-compatible client
 * 2. TrikeRpcClient - Universal RPC client with protocol transformation
 * 3. Protocol transformation between different formats
 * 4. Distributed routing and load balancing
 * 5. Real-time progress monitoring
 * 6. Interactive shell capabilities
 */

fun main() = runBlocking {
    println("🚀 Full Client Implementations Demo")
    println("=" * 60)
    
    // Create core components
    val downloader = TrikeDownloader()
    val requestFactory = RequestFactoryServiceImpl(createMockContext())
    val rpcServer = TorrentRpcServer(downloader, requestFactory)
    
    // Create clients
    val ariaClient = TrikeAriaClient("http://localhost:6800")
    val rpcClient = TrikeRpcClient("http://localhost:6800", "aria2c", "native")
    
    // Create transformer
    val transformer = TorrentRpcTransformer(requestFactory, "demo-node")
    
    println("✅ Components initialized")
    println()
    
    // Demo 1: TrikeAriaClient - Programmatic Usage
    println("📥 Demo 1: TrikeAriaClient - Programmatic Usage")
    println("-" * 50)
    
    val httpGid = ariaClient.addUri(
        uris = listOf("https://example.com/file.zip"),
        options = mapOf(
            "header" to listOf("User-Agent: TrikeAriaClient/1.0"),
            "timeout" to 30000L
        )
    )
    println("Added HTTP download via TrikeAriaClient: $httpGid")
    
    val torrentGid = ariaClient.addTorrent(
        torrent = "magnet:?xt=urn:btih:example",
        uris = listOf("udp://tracker.example.com:1337"),
        options = mapOf("piece-length" to 16384)
    )
    println("Added torrent download via TrikeAriaClient: $torrentGid")
    
    // Demo 2: TrikeRpcClient - Protocol Transformation
    println("\n🔄 Demo 2: TrikeRpcClient - Protocol Transformation")
    println("-" * 50)
    
    val transformedHttpGid = rpcClient.addUri(
        uris = listOf("https://example.com/file2.zip"),
        options = mapOf("timeout" to 60000L)
    )
    println("Added HTTP download via TrikeRpcClient (with transformation): $transformedHttpGid")
    
    val transformedTorrentGid = rpcClient.addTorrent(
        torrent = "magnet:?xt=urn:btih:example2",
        uris = listOf("udp://tracker2.example.com:1337")
    )
    println("Added torrent download via TrikeRpcClient (with transformation): $transformedTorrentGid")
    
    // Demo 3: Protocol Transformation
    println("\n🔄 Demo 3: Protocol Transformation")
    println("-" * 50)
    
    // Transform aria2c request to native protocol
    val aria2cRequest = RpcRequest(
        id = "transform-demo",
        method = "aria2.addUri",
        params = mapOf(
            "uris" to listOf("https://example.com/transformed.zip")
        )
    )
    
    val requestBytes = Json.encodeToString(RpcRequest.serializer(), aria2cRequest).encodeToByteArray()
    
    val transformedRequest = transformer.transformRequest(
        sourceProtocol = "aria2c",
        targetProtocol = "native",
        request = requestBytes,
        context = TransformContext(
            sourceProtocol = "aria2c",
            targetProtocol = "native",
            methodMapping = mapOf("aria2.addUri" to "download.add"),
            parameterMapping = mapOf("uris" to "urls")
        )
    )
    
    println("Transformed request size: ${transformedRequest.size} bytes")
    println("Transformation metadata added")
    
    // Demo 4: Distributed Routing
    println("\n🌐 Demo 4: Distributed Routing")
    println("-" * 50)
    
    // Add routes for different methods
    transformer.addRoute(
        method = "aria2.addUri",
        service = "aria2",
        targetNode = "http-node",
        loadBalancing = LoadBalancingStrategy.ROUND_ROBIN,
        failover = listOf("http-backup-1", "http-backup-2")
    )
    
    transformer.addRoute(
        method = "aria2.addTorrent",
        service = "aria2",
        targetNode = "torrent-node",
        loadBalancing = LoadBalancingStrategy.LEAST_CONNECTIONS,
        failover = listOf("torrent-backup-1")
    )
    
    transformer.addRoute(
        method = "aria2.tellStatus",
        service = "aria2",
        targetNode = "status-node",
        loadBalancing = LoadBalancingStrategy.WEIGHTED,
        weights = mapOf("status-node" to 3, "status-backup" to 1)
    )
    
    println("Added routing rules:")
    println("  - HTTP downloads → http-node (round-robin)")
    println("  - Torrent downloads → torrent-node (least-connections)")
    println("  - Status queries → status-node (weighted)")
    
    // Demo 5: Real-Time Progress Monitoring
    println("\n📡 Demo 5: Real-Time Progress Monitoring")
    println("-" * 50)
    
    // Monitor progress with TrikeAriaClient
    println("Monitoring progress with TrikeAriaClient:")
    ariaClient.monitorProgress(httpGid, Duration.ofMillis(500))
        .take(3)
        .collect { status ->
            val completed = status["completedLength"] as? Long ?: 0L
            val total = status["totalLength"] as? Long ?: 0L
            val speed = status["downloadSpeed"] as? Long ?: 0L
            println("  Progress: ${completed}/${total} bytes (${speed} B/s)")
        }
    
    // Monitor progress with TrikeRpcClient
    println("\nMonitoring progress with TrikeRpcClient:")
    rpcClient.monitorProgress(transformedHttpGid, Duration.ofMillis(500))
        .take(3)
        .collect { status ->
            val completed = status["completedLength"] as? Long ?: 0L
            val total = status["totalLength"] as? Long ?: 0L
            val speed = status["downloadSpeed"] as? Long ?: 0L
            println("  Progress: ${completed}/${total} bytes (${speed} B/s)")
        }
    
    // Demo 6: Batch Operations
    println("\n📦 Demo 6: Batch Operations")
    println("-" * 50)
    
    // Batch operations with TrikeAriaClient
    val ariaBatchResults = ariaClient.batch(listOf(
        { ariaClient.addUri(listOf("https://example.com/batch1.zip")) },
        { ariaClient.addUri(listOf("https://example.com/batch2.zip")) },
        { ariaClient.addTorrent("magnet:?xt=urn:btih:batch1") },
        { ariaClient.getGlobalStat() }
    ))
    
    println("TrikeAriaClient batch results:")
    ariaBatchResults.forEachIndexed { index, result ->
        println("  Operation $index: $result")
    }
    
    // Batch operations with TrikeRpcClient
    val rpcBatchResults = rpcClient.batch(listOf(
        { rpcClient.addUri(listOf("https://example.com/batch3.zip")) },
        { rpcClient.addUri(listOf("https://example.com/batch4.zip")) },
        { rpcClient.addTorrent("magnet:?xt=urn:btih:batch2") },
        { rpcClient.execute("aria2.getGlobalStat") }
    ))
    
    println("\nTrikeRpcClient batch results:")
    rpcBatchResults.forEachIndexed { index, result ->
        println("  Operation $index: $result")
    }
    
    // Demo 7: Status Queries
    println("\n📊 Demo 7: Status Queries")
    println("-" * 50)
    
    // Get status with TrikeAriaClient
    val ariaStatus = ariaClient.tellStatus(httpGid, listOf("status", "completedLength", "totalLength", "downloadSpeed"))
    println("TrikeAriaClient status for $httpGid:")
    ariaStatus.forEach { (key, value) ->
        println("  $key: $value")
    }
    
    // Get status with TrikeRpcClient
    val rpcStatus = rpcClient.tellStatus(transformedHttpGid, listOf("status", "completedLength", "totalLength", "downloadSpeed"))
    println("\nTrikeRpcClient status for $transformedHttpGid:")
    rpcStatus.forEach { (key, value) ->
        println("  $key: $value")
    }
    
    // Demo 8: Global Statistics
    println("\n📈 Demo 8: Global Statistics")
    println("-" * 50)
    
    val ariaStats = ariaClient.getGlobalStat()
    println("TrikeAriaClient global stats:")
    ariaStats.forEach { (key, value) ->
        println("  $key: $value")
    }
    
    val rpcStats = rpcClient.execute("aria2.getGlobalStat")
    println("\nTrikeRpcClient global stats:")
    (rpcStats["result"] as? Map<String, Any?>)?.forEach { (key, value) ->
        println("  $key: $value")
    }
    
    // Demo 9: Protocol Handlers
    println("\n🔧 Demo 9: Protocol Handlers")
    println("-" * 50)
    
    // Register custom protocol handler
    val customHandler = object : ProtocolHandler {
        override fun parseRequest(data: ByteArray): TransformedRequest {
            return TransformedRequest(
                id = "custom_${System.currentTimeMillis()}",
                method = "custom.addUri",
                service = "custom",
                parameters = mapOf("url" to data.decodeToString()),
                metadata = mapOf("protocol" to "custom")
            )
        }
        
        override fun serializeRequest(request: TransformedRequest): ByteArray {
            return request.parameters["url"]?.toString()?.encodeToByteArray() ?: ByteArray(0)
        }
        
        override fun parseResponse(data: ByteArray): TransformedResponse {
            return TransformedResponse(
                id = "custom_response",
                result = data.decodeToString(),
                error = null,
                metadata = mapOf("protocol" to "custom")
            )
        }
        
        override fun serializeResponse(response: TransformedResponse): ByteArray {
            return response.result?.toString()?.encodeToByteArray() ?: ByteArray(0)
        }
        
        override fun supportsProtocol(protocol: String): Boolean = protocol == "custom"
    }
    
    transformer.registerProtocol("custom", customHandler)
    println("Registered custom protocol handler")
    
    // Demo 10: Interactive Shell Simulation
    println("\n💻 Demo 10: Interactive Shell Simulation")
    println("-" * 50)
    
    println("Simulating interactive shell commands:")
    
    val shellCommands = listOf(
        "add http https://example.com/shell-demo.zip",
        "status $httpGid",
        "list active",
        "monitor $torrentGid"
    )
    
    shellCommands.forEach { command ->
        println("\nExecuting: $command")
        when {
            command.startsWith("add http ") -> {
                val url = command.substringAfter("add http ")
                val gid = rpcClient.addUri(listOf(url))
                println("  Result: Added download $gid")
            }
            command.startsWith("status ") -> {
                val gid = command.substringAfter("status ")
                val status = rpcClient.tellStatus(gid)
                println("  Result: Status retrieved")
            }
            command.startsWith("list ") -> {
                val type = command.substringAfter("list ")
                val result = rpcClient.execute("aria2.tell${type.capitalize()}")
                println("  Result: Listed ${type} downloads")
            }
            command.startsWith("monitor ") -> {
                val gid = command.substringAfter("monitor ")
                rpcClient.monitorProgress(gid).take(2).collect { status ->
                    println("  Progress update: ${status["completedLength"]}/${status["totalLength"]}")
                }
            }
        }
    }
    
    // Demo 11: Error Handling
    println("\n⚠️ Demo 11: Error Handling")
    println("-" * 50)
    
    try {
        ariaClient.tellStatus("invalid-gid")
    } catch (e: Exception) {
        println("TrikeAriaClient error handling: ${e.message}")
    }
    
    try {
        rpcClient.execute("aria2.invalidMethod")
    } catch (e: Exception) {
        println("TrikeRpcClient error handling: ${e.message}")
    }
    
    // Demo 12: Cleanup
    println("\n🧹 Demo 12: Cleanup")
    println("-" * 50)
    
    // Remove downloads
    ariaClient.remove(httpGid)
    ariaClient.remove(torrentGid)
    rpcClient.execute("aria2.remove", mapOf("gid" to transformedHttpGid))
    rpcClient.execute("aria2.remove", mapOf("gid" to transformedTorrentGid))
    
    println("Removed all test downloads")
    
    println("\n🎉 Full Client Implementations Demo Completed!")
    println("=" * 60)
    println()
    println("Key Features Demonstrated:")
    println("• TrikeAriaClient - Programmatic aria2c compatibility")
    println("• TrikeRpcClient - Universal RPC with protocol transformation")
    println("• Protocol transformation (aria2c ↔ native)")
    println("• Distributed routing and load balancing")
    println("• Real-time progress monitoring")
    println("• Batch operations")
    println("• Interactive shell capabilities")
    println("• Custom protocol handlers")
    println("• Error handling and recovery")
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
private fun String.capitalize(): String = replaceFirstChar { it.uppercase() } 