package com.v2superbikeshed.nexus.mcp

import borg.trikeshed.lib.*
import borg.trikeshed.reactor.quic.*
import borg.trikeshed.net.quic.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
// Simple logging for now
internal fun logInfo(message: () -> String) = println("[INFO] ${message()}")
internal fun logError(message: () -> String) = println("[ERROR] ${message()}")
internal fun logDebug(message: () -> String) = println("[DEBUG] ${message()}")
import com.v2superbikeshed.nexus.rpc.*
import com.v2superbikeshed.nexus.service.IntelliJAccessService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import java.net.InetSocketAddress

/**
 * MCP Server implementation using TrikeShed QUIC channels
 * Multiple channelized servers for different node types
 */
@Service(Service.Level.PROJECT)
class QuicMcpServer(internal val project: Project) {
    
    companion object {
        
        
        // Channel ports for different services
        const val PSI_CHANNEL_PORT = 63344
        const val ANALYSIS_CHANNEL_PORT = 63345
        const val REFACTOR_CHANNEL_PORT = 63346
        const val CONTROL_CHANNEL_PORT = 63347
    }
    
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Channelized servers
    internal val psiServer = createQuicServer("psi", PSI_CHANNEL_PORT)
    internal val analysisServer = createQuicServer("analysis", ANALYSIS_CHANNEL_PORT)
    internal val refactorServer = createQuicServer("refactor", REFACTOR_CHANNEL_PORT)
    internal val controlServer = createQuicServer("control", CONTROL_CHANNEL_PORT)
    
    // Active connections tracked as Indexed collections
    internal val activeConnections: MutableMap<String, Indexed<QuicConnection>> = mutableMapOf()
    
    /**
     * Start all channelized servers
     */
    suspend fun startAll() = coroutineScope {
        launch { startPsiChannel() }
        launch { startAnalysisChannel() }
        launch { startRefactorChannel() }
        launch { startControlChannel() }
        
        logInfo { "QUIC MCP servers started on channels: PSI($PSI_CHANNEL_PORT), Analysis($ANALYSIS_CHANNEL_PORT), Refactor($REFACTOR_CHANNEL_PORT), Control($CONTROL_CHANNEL_PORT)" }
    }
    
    /**
     * Create a QUIC server for a specific channel
     */
    internal fun createQuicServer(name: String, port: Int): QuicServer {
        val config = QuicServerConfig(
            host = "localhost",
            port = port,
            mtu = 1500,
            onStreamHandler = { stream ->
                handleStream(name, stream)
            }
        )
        return QuicServer(config)
    }
    
    /**
     * Start PSI channel server
     */
    internal suspend fun startPsiChannel() {
        val psiNode = PsiNode(IntelliJAccessService.getInstance(project))
        
        psiServer.start { connection ->
            handleChannelConnection("psi", connection) { request ->
                processPsiRequest(psiNode, request)
            }
        }
    }
    
    /**
     * Start Analysis channel server
     */
    internal suspend fun startAnalysisChannel() {
        val analysisNode = AnalysisNode(IntelliJAccessService.getInstance(project))
        
        analysisServer.start { connection ->
            handleChannelConnection("analysis", connection) { request ->
                processAnalysisRequest(analysisNode, request)
            }
        }
    }
    
    /**
     * Start Refactor channel server
     */
    internal suspend fun startRefactorChannel() {
        val refactorNode = RefactoringNode(IntelliJAccessService.getInstance(project))
        
        refactorServer.start { connection ->
            handleChannelConnection("refactor", connection) { request ->
                processRefactorRequest(refactorNode, request)
            }
        }
    }
    
    /**
     * Start Control channel server for meta operations
     */
    internal suspend fun startControlChannel() {
        controlServer.start { connection ->
            handleChannelConnection("control", connection) { request ->
                processControlRequest(request)
            }
        }
    }
    
    /**
     * Handle incoming QUIC connection on a channel
     */
    internal suspend fun handleChannelConnection(
        channelName: String,
        connection: QuicConnection,
        processor: suspend (McpRequest) -> McpResponse
    ) = coroutineScope {
        // Track connection
        val connectionsList = mutableListOf<QuicConnection>()
        val connections = activeConnections.getOrPut(channelName) { 
            \1 j { \2: Int -> connectionsList[i] }
        }
        connectionsList.add(connection)
        
        // Create bidirectional stream
        val stream = connection.createStream()
        
        // Process messages on this stream
        launch {
            while (true) {
                try {
                    val data = stream.readAll()
                    val message = String(data.toArray())
                    val request = json.decodeFromString<McpRequest>(message)
                    
                    logInfo { "$channelName channel received: ${request.method}" }
                    
                    val response = processor(request)
                    val responseJson = json.encodeToString(response)
                    stream.write(s_(responseJson.toByteArray()))
                    
                } catch (e: Exception) {
                    logError { "Error in $channelName channel: ${e.message}" }
                    break
                }
            }
        }
    }
    
    /**
     * Handle stream for a specific service
     */
    internal fun handleStream(serviceName: String, stream: QuicStream) {
        GlobalScope.launch {
            try {
                while (true) {
                    val data = stream.readAll()
                    val request = String(data.toArray())
                    logInfo { "$serviceName received stream data: $request" }
                    
                    // Echo response for now
                    val response = """{"service":"$serviceName","echo":"$request"}"""
                    stream.write(s_(response.toByteArray()))
                }
            } catch (e: Exception) {
                logError { "Stream error in $serviceName: ${e.message}" }
                stream.close()
            }
        }
    }
    
    /**
     * Process PSI channel requests
     */
    internal suspend fun processPsiRequest(node: PsiNode, request: McpRequest): McpResponse {
        return try {
            val params = request.params?.toMap() ?: emptyMap()
            val result = node.handleRequest(request.method, params)
            
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = json.encodeToJsonElement(result)
            )
        } catch (e: Exception) {
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, e.message ?: "PSI operation failed")
            )
        }
    }
    
    /**
     * Process Analysis channel requests
     */
    internal suspend fun processAnalysisRequest(node: AnalysisNode, request: McpRequest): McpResponse {
        return try {
            val params = request.params?.toMap() ?: emptyMap()
            val result = node.handleRequest(request.method, params)
            
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = json.encodeToJsonElement(result)
            )
        } catch (e: Exception) {
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, e.message ?: "Analysis operation failed")
            )
        }
    }
    
    /**
     * Process Refactor channel requests
     */
    internal suspend fun processRefactorRequest(node: RefactoringNode, request: McpRequest): McpResponse {
        return try {
            val params = request.params?.toMap() ?: emptyMap()
            val result = node.handleRequest(request.method, params)
            
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = json.encodeToJsonElement(result)
            )
        } catch (e: Exception) {
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, e.message ?: "Refactor operation failed")
            )
        }
    }
    
    /**
     * Process Control channel requests
     */
    internal suspend fun processControlRequest(request: McpRequest): McpResponse {
        return when (request.method) {
            "server.info" -> McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonObject(mapOf(
                    "name" to JsonPrimitive("v2superbikeshed-quic-mcp"),
                    "version" to JsonPrimitive("1.0.0"),
                    "channels" to JsonArray(listOf(
                        JsonObject(mapOf(
                            "name" to JsonPrimitive("psi"),
                            "port" to JsonPrimitive(PSI_CHANNEL_PORT),
                            "status" to JsonPrimitive("active")
                        )),
                        JsonObject(mapOf(
                            "name" to JsonPrimitive("analysis"),
                            "port" to JsonPrimitive(ANALYSIS_CHANNEL_PORT),
                            "status" to JsonPrimitive("active")
                        )),
                        JsonObject(mapOf(
                            "name" to JsonPrimitive("refactor"),
                            "port" to JsonPrimitive(REFACTOR_CHANNEL_PORT),
                            "status" to JsonPrimitive("active")
                        ))
                    ))
                ))
            )
            
            "channel.list" -> McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonObject(mapOf(
                    "channels" to JsonArray(activeConnections.keys.map { JsonPrimitive(it) }),
                    "connections" to JsonObject(
                        activeConnections.mapValues { (_, connections) ->
                            JsonPrimitive((connections as List<*>).size)
                        }
                    )
                ))
            )
            
            else -> McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32601, "Unknown control method: ${request.method}")
            )
        }
    }
    
    /**
     * Stop all servers
     */
    fun stopAll() {
        // QUIC servers handle their own cleanup
        activeConnections.clear()
        logInfo { "All QUIC MCP servers stopped" }
    }
}

/**
 * QUIC Server configuration
 */
data class QuicServerConfig(
    val host: String,
    val port: Int,
    val mtu: Int = 1500,
    val onStreamHandler: (QuicStream) -> Unit
)

/**
 * Extension to start QUIC server with connection handler
 */
suspend fun QuicServer.start(onConnection: suspend (QuicConnection) -> Unit) = coroutineScope {
    // This would integrate with the actual QUIC implementation
    start()
}

/**
 * Extension to convert JsonObject to Map
 */
fun JsonObject.toMap(): Map<String, Any?> {
    return entries.associate { (key, element) ->
        key to when (element) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.booleanOrNull
                element.longOrNull != null -> element.longOrNull
                element.doubleOrNull != null -> element.doubleOrNull
                else -> element.content
            }
            is JsonArray -> element.map { it.jsonPrimitive.content }
            is JsonObject -> element.toMap()
            is JsonNull -> null
        }
    }
}