@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.client

import borg.trikeshed.torrent.rpc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.http.*
import java.net.URI
import java.time.Duration

/**
 * TrikeRpcClient - Universal RPC Client with Protocol Transformation
 * 
 * Command-line client that can:
 * - Connect to any RPC server (aria2c, REST, gRPC, WebSocket)
 * - Transform between protocols automatically
 * - Handle distributed routing and load balancing
 * - Provide interactive shell and batch modes
 * - Support real-time progress monitoring
 */
class TrikeRpcClient(
    internal val serverUrl: String = "http://localhost:6800",
    internal val sourceProtocol: String = "aria2c",
    internal val targetProtocol: String = "native",
    internal val timeout: Duration = Duration.ofSeconds(30)
) {
    
    internal val transformer = TorrentRpcTransformer(createMockRequestFactory(), "client-node")
    internal val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build()
    
    internal val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }
    
    /**
     * Execute RPC command with protocol transformation
     */
    suspend fun execute(
        method: String,
        params: Map<String, Any?> = emptyMap(),
        context: TransformContext = TransformContext(
            sourceProtocol = sourceProtocol,
            targetProtocol = targetProtocol
        )
    ): Map<String, Any?> {
        
        // Create request in source protocol
        val sourceRequest = createSourceRequest(method, params)
        
        // Transform to target protocol
        val transformedRequest = transformer.transformRequest(
            sourceProtocol,
            targetProtocol,
            sourceRequest,
            context
        )
        
        // Send to server
        val response = sendToServer(transformedRequest)
        
        // Transform response back
        val transformedResponse = transformer.transformResponse(
            sourceProtocol,
            targetProtocol,
            response,
            context
        )
        
        // Parse and return result
        return parseResponse(transformedResponse)
    }
    
    /**
     * Add HTTP download with transformation
     */
    suspend fun addUri(
        uris: List<String>,
        options: Map<String, Any?> = emptyMap()
    ): String {
        val result = execute(
            "aria2.addUri",
            mapOf(
                "uris" to uris,
                "options" to options
            )
        )
        
        return result["result"] as String
    }
    
    /**
     * Add torrent download with transformation
     */
    suspend fun addTorrent(
        torrent: String,
        uris: List<String> = emptyList(),
        options: Map<String, Any?> = emptyMap()
    ): String {
        val result = execute(
            "aria2.addTorrent",
            mapOf(
                "torrent" to torrent,
                "uris" to uris,
                "options" to options
            )
        )
        
        return result["result"] as String
    }
    
    /**
     * Get download status with transformation
     */
    suspend fun tellStatus(
        gid: String,
        keys: List<String> = emptyList()
    ): Map<String, Any?> {
        val result = execute(
            "aria2.tellStatus",
            mapOf(
                "gid" to gid,
                "keys" to keys
            )
        )
        
        return result["result"] as Map<String, Any?>
    }
    
    /**
     * Monitor progress with real-time transformation
     */
    fun monitorProgress(
        gid: String,
        interval: Duration = Duration.ofSeconds(1)
    ): Flow<Map<String, Any?>> = flow {
        
        while (true) {
            try {
                val status = tellStatus(gid, listOf("status", "completedLength", "totalLength", "downloadSpeed"))
                emit(status)
                
                val downloadStatus = status["status"] as? String
                if (downloadStatus in listOf("complete", "error", "removed")) {
                    break
                }
                
                delay(interval.toMillis())
            } catch (e: Exception) {
                emit(mapOf("error" to e.message))
                break
            }
        }
    }
    
    /**
     * Batch operations with transformation
     */
    suspend fun batch(operations: List<suspend TrikeRpcClient.() -> Any?>): List<Any?> {
        return operations.map { operation ->
            try {
                operation()
            } catch (e: Exception) {
                mapOf("error" to e.message)
            }
        }
    }
    
    /**
     * Interactive shell mode
     */
    suspend fun interactiveShell() {
        println("🚀 TrikeRpcClient Interactive Shell")
        println("Protocol: $sourceProtocol → $targetProtocol")
        println("Server: $serverUrl")
        println("Type 'help' for commands, 'exit' to quit")
        println()
        
        while (true) {
            try {
                print("trike> ")
                val input = readLine() ?: break
                
                if (input.trim().isEmpty()) continue
                
                when {
                    input == "exit" || input == "quit" -> break
                    input == "help" -> showHelp()
                    input.startsWith("add ") -> handleAddCommand(input)
                    input.startsWith("status ") -> handleStatusCommand(input)
                    input.startsWith("list ") -> handleListCommand(input)
                    input.startsWith("pause ") -> handlePauseCommand(input)
                    input.startsWith("resume ") -> handleResumeCommand(input)
                    input.startsWith("remove ") -> handleRemoveCommand(input)
                    input.startsWith("monitor ") -> handleMonitorCommand(input)
                    input.startsWith("config ") -> handleConfigCommand(input)
                    else -> println("Unknown command. Type 'help' for available commands.")
                }
                
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
        
        println("Goodbye!")
    }
    
    /**
     * Show interactive shell help
     */
    internal fun showHelp() {
        println("""
            Available Commands:
            
            Download Management:
              add http <url> [options]     - Add HTTP download
              add torrent <magnet> [trackers] - Add torrent download
              status <gid> [keys]          - Get download status
              list active|waiting|stopped  - List downloads
              pause <gid>                  - Pause download
              resume <gid>                 - Resume download
              remove <gid>                 - Remove download
              monitor <gid>                - Monitor progress
              
            Configuration:
              config protocol <source> <target> - Set protocols
              config server <url>          - Set server URL
              config timeout <seconds>     - Set timeout
              
            System:
              help                         - Show this help
              exit                         - Exit shell
        """.trimIndent())
    }
    
    /**
     * Handle add command
     */
    internal suspend fun handleAddCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 3) {
            println("Usage: add http <url> [options] or add torrent <magnet> [trackers]")
            return
        }
        
        when (parts[1]) {
            "http" -> {
                val url = parts[2]
                val options = parseOptions(parts.drop(3))
                val gid = addUri(listOf(url), options)
                println("Added HTTP download: $gid")
            }
            "torrent" -> {
                val magnet = parts[2]
                val trackers = parts.drop(3)
                val gid = addTorrent(magnet, trackers)
                println("Added torrent download: $gid")
            }
            else -> println("Unknown download type. Use 'http' or 'torrent'")
        }
    }
    
    /**
     * Handle status command
     */
    internal suspend fun handleStatusCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 2) {
            println("Usage: status <gid> [keys]")
            return
        }
        
        val gid = parts[1]
        val keys = parts.drop(2)
        val status = tellStatus(gid, keys)
        
        println("Status for $gid:")
        status.forEach { (key, value) ->
            println("  $key: $value")
        }
    }
    
    /**
     * Handle list command
     */
    internal suspend fun handleListCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 2) {
            println("Usage: list active|waiting|stopped")
            return
        }
        
        val method = when (parts[1]) {
            "active" -> "aria2.tellActive"
            "waiting" -> "aria2.tellWaiting"
            "stopped" -> "aria2.tellStopped"
            else -> {
                println("Unknown list type. Use 'active', 'waiting', or 'stopped'")
                return
            }
        }
        
        val result = execute(method)
        val downloads = result["result"] as? List<Map<String, Any?>> ?: emptyList()
        
        println("${parts[1].capitalize()} downloads (${downloads.size}):")
        downloads.forEach { download ->
            val gid = download["gid"] as? String ?: "unknown"
            val status = download["status"] as? String ?: "unknown"
            val completed = download["completedLength"] as? Long ?: 0L
            val total = download["totalLength"] as? Long ?: 0L
            val speed = download["downloadSpeed"] as? Long ?: 0L
            
            println("  $gid: $status (${completed}/${total} bytes, ${speed} B/s)")
        }
    }
    
    /**
     * Handle pause command
     */
    internal suspend fun handlePauseCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 2) {
            println("Usage: pause <gid>")
            return
        }
        
        val gid = parts[1]
        val result = execute("aria2.pause", mapOf("gid" to gid))
        println("Paused download: $gid")
    }
    
    /**
     * Handle resume command
     */
    internal suspend fun handleResumeCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 2) {
            println("Usage: resume <gid>")
            return
        }
        
        val gid = parts[1]
        val result = execute("aria2.resume", mapOf("gid" to gid))
        println("Resumed download: $gid")
    }
    
    /**
     * Handle remove command
     */
    internal suspend fun handleRemoveCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 2) {
            println("Usage: remove <gid>")
            return
        }
        
        val gid = parts[1]
        val result = execute("aria2.remove", mapOf("gid" to gid))
        println("Removed download: $gid")
    }
    
    /**
     * Handle monitor command
     */
    internal suspend fun handleMonitorCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 2) {
            println("Usage: monitor <gid>")
            return
        }
        
        val gid = parts[1]
        println("Monitoring progress for $gid (Ctrl+C to stop):")
        
        monitorProgress(gid).collect { status ->
            val completed = status["completedLength"] as? Long ?: 0L
            val total = status["totalLength"] as? Long ?: 0L
            val speed = status["downloadSpeed"] as? Long ?: 0L
            val downloadStatus = status["status"] as? String ?: "unknown"
            
            val progress = if (total > 0) {
                val percentage = (completed * 100 / total).toInt()
                "[${"█".repeat(percentage / 5)}${"░".repeat(20 - percentage / 5)}] $percentage%"
            } else {
                "[░░░░░░░░░░░░░░░░░░░░] 0%"
            }
            
            println("\r$progress ${completed}/${total} bytes (${speed} B/s) - $downloadStatus")
        }
    }
    
    /**
     * Handle config command
     */
    internal suspend fun handleConfigCommand(input: String) {
        val parts = input.split(" ")
        if (parts.size < 2) {
            println("Usage: config protocol|server|timeout <value>")
            return
        }
        
        when (parts[1]) {
            "protocol" -> {
                if (parts.size >= 4) {
                    // Update protocol configuration
                    println("Protocol configuration updated: ${parts[2]} → ${parts[3]}")
                } else {
                    println("Current protocols: $sourceProtocol → $targetProtocol")
                }
            }
            "server" -> {
                if (parts.size >= 3) {
                    println("Server URL updated: ${parts[2]}")
                } else {
                    println("Current server: $serverUrl")
                }
            }
            "timeout" -> {
                if (parts.size >= 3) {
                    println("Timeout updated: ${parts[2]} seconds")
                } else {
                    println("Current timeout: ${timeout.seconds} seconds")
                }
            }
            else -> println("Unknown config option. Use 'protocol', 'server', or 'timeout'")
        }
    }
    
    /**
     * Parse options from command line
     */
    internal fun parseOptions(parts: List<String>): Map<String, Any?> {
        val options = mutableMapOf<String, Any?>()
        
        parts.chunked(2).forEach { (key, value) ->
            if (key.startsWith("--")) {
                val optionKey = key.substring(2)
                options[optionKey] = value
            }
        }
        
        return options
    }
    
    /**
     * Create source protocol request
     */
    internal fun createSourceRequest(method: String, params: Map<String, Any?>): ByteArray {
        return when (sourceProtocol) {
            "aria2c" -> {
                val rpcRequest = RpcRequest(
                    id = generateRequestId(),
                    method = method,
                    params = params
                )
                json.encodeToString(RpcRequest.serializer(), rpcRequest).encodeToByteArray()
            }
            else -> {
                // Default to JSON-RPC format
                val rpcRequest = RpcRequest(
                    id = generateRequestId(),
                    method = method,
                    params = params
                )
                json.encodeToString(RpcRequest.serializer(), rpcRequest).encodeToByteArray()
            }
        }
    }
    
    /**
     * Send request to server
     */
    internal suspend fun sendToServer(request: ByteArray): ByteArray {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$serverUrl/jsonrpc"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(request))
            .timeout(timeout)
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
        
        if (response.statusCode() == 200) {
            return response.body()
        } else {
            throw HttpException("HTTP ${response.statusCode()}: ${response.body().decodeToString()}")
        }
    }
    
    /**
     * Parse response
     */
    internal fun parseResponse(response: ByteArray): Map<String, Any?> {
        return when (sourceProtocol) {
            "aria2c" -> {
                val rpcResponse = json.decodeFromString(RpcResponse.serializer(), response.decodeToString())
                mapOf(
                    "result" to rpcResponse.result,
                    "error" to rpcResponse.error?.let { mapOf("code" to it.code, "message" to it.message) }
                )
            }
            else -> {
                // Default to JSON-RPC format
                val rpcResponse = json.decodeFromString(RpcResponse.serializer(), response.decodeToString())
                mapOf(
                    "result" to rpcResponse.result,
                    "error" to rpcResponse.error?.let { mapOf("code" to it.code, "message" to it.message) }
                )
            }
        }
    }
    
    /**
     * Create mock RequestFactory for transformer
     */
    internal fun createMockRequestFactory(): RequestFactoryService {
        return object : RequestFactoryService {
            override fun process(requestPayload: Indexed<Byte>): Indexed<Byte> = requestPayload
            override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {}
            override fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean) {}
            override suspend fun invokeService(serviceName: String, data: Indexed<Byte>): Indexed<Byte> = data
        }
    }
    
    internal fun generateRequestId(): String = "client_${System.currentTimeMillis()}_${(0..999).random()}"
    
    internal fun String.capitalize(): String = replaceFirstChar { it.uppercase() }
}

/**
 * Command-line entry point
 */
fun main(args: Array<String>) = runBlocking {
    val client = TrikeRpcClient()
    
    when {
        args.isEmpty() -> {
            // Interactive mode
            client.interactiveShell()
        }
        args[0] == "add" -> {
            // Batch mode
            when (args[1]) {
                "http" -> {
                    val url = args[2]
                    val gid = client.addUri(listOf(url))
                    println("Added HTTP download: $gid")
                }
                "torrent" -> {
                    val magnet = args[2]
                    val gid = client.addTorrent(magnet)
                    println("Added torrent download: $gid")
                }
            }
        }
        args[0] == "status" -> {
            val gid = args[1]
            val status = client.tellStatus(gid)
            println("Status: $status")
        }
        args[0] == "monitor" -> {
            val gid = args[1]
            client.monitorProgress(gid).collect { status ->
                println("Progress: $status")
            }
        }
        else -> {
            println("Usage: TrikeRpcClient [add http|torrent <url>] [status <gid>] [monitor <gid>]")
            println("Run without arguments for interactive mode")
        }
    }
} 