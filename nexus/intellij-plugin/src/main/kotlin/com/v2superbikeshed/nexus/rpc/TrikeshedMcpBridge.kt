package com.v2superbikeshed.nexus.rpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import com.superbikeshed.mcp.trikeshed.CouchDbMcpAdapter
import com.superbikeshed.mcp.trikeshed.MockCouchClient
import com.superbikeshed.mcp.trikeshed.TrikeshedMcpServer
import com.superbikeshed.mcp.trikeshed.TrikeshedServiceRegistry
import com.superbikeshed.mcp.trikeshed.TrikeshedReactor
import borg.trikeshed.net.quic.QuicEngine
import borg.trikeshed.net.quic.QuicConnectionState
import borg.trikeshed.net.quic.QuicEngine.Role
import borg.trikeshed.lib.toIndexed
import com.superbikeshed.mcp.trikeshed.QuicMcpAdapter

/**
 * Bridge that allows TrikeShed RequestFactory nodes to be accessed via MCP protocol
 * and MCP servers to be accessed via RequestFactory RPC.
 * 
 * This provides bidirectional compatibility between:
 * - MCP JSON-RPC protocol
 * - TrikeShed RequestFactory RPC
 */
@Service(Service.Level.PROJECT)
class TrikeshedMcpBridge(internal val project: Project) {
    
    internal val requestFactory = TrikeshedRequestFactory(project)
    internal val mcpAdapters = ConcurrentHashMap<String, McpToNodeAdapter>()
    internal val nodeAdapters = ConcurrentHashMap<String, NodeToMcpAdapter>()
    
    /**
     * Register a TrikeShed node to be accessible via MCP protocol
     */
    fun exposeNodeAsMcp(node: TrikeshedNode): McpServerInfo {
        val adapter = NodeToMcpAdapter(node)
        nodeAdapters[node.nodeId] = adapter
        
        return McpServerInfo(
            name = node.nodeId,
            version = "1.0.0",
            capabilities = node.capabilities
        )
    }
    
    /**
     * Register an MCP server to be accessible via RequestFactory RPC
     */
    fun exposeMcpAsNode(mcpServer: McpServerInterface): TrikeshedNode {
        val adapter = McpToNodeAdapter(mcpServer)
        mcpAdapters[mcpServer.name] = adapter
        requestFactory.registerNode(mcpServer.name, adapter)
        
        return adapter
    }
    
    /**
     * Handle MCP protocol request and route to appropriate TrikeShed node
     */
    suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        // Extract the target node from the method (e.g., "psi-node.find_symbols")
        val parts = request.method.split(".")
        if (parts.size < 2) {
            return McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32601, "Method must be in format: nodeId.method")
            )
        }
        
        val nodeId = parts[0]
        val method = parts.drop(1).joinToString(".")
        
        val adapter = nodeAdapters[nodeId]
        if (adapter == null) {
            return McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32601, "Node not found: $nodeId")
            )
        }
        
        return adapter.handleMcpRequest(request.copy(method = method))
    }
    
    /**
     * Get RequestFactory instance for making RPC calls
     */
    fun getRequestFactory(): TrikeshedRequestFactory = requestFactory
}

/**
 * MCP protocol data classes
 */
@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class McpServerInfo(
    val name: String,
    val version: String,
    val capabilities: Set<String>
)

/**
 * Interface for MCP servers
 */
interface McpServerInterface {
    val name: String
    val version: String
    val capabilities: Set<String>
    
    suspend fun handleRequest(request: McpRequest): McpResponse
}

/**
 * Adapter that makes a TrikeShed node accessible via MCP protocol
 */
class NodeToMcpAdapter(internal val node: TrikeshedNode) : McpServerInterface {
    
    override val name: String = node.nodeId
    override val version: String = "1.0.0"
    override val capabilities: Set<String> = node.capabilities
    
    internal val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun handleRequest(request: McpRequest): McpResponse {
        return try {
            // Convert MCP params to Map
            val params = request.params?.let { jsonObject ->
                jsonObject.entries.associate { (key, value) ->
                    key to when (value) {
                        is JsonPrimitive -> when {
                            value.isString -> value.content
                            value.booleanOrNull != null -> value.booleanOrNull
                            value.longOrNull != null -> value.longOrNull
                            value.doubleOrNull != null -> value.doubleOrNull
                            else -> value.content
                        }
                        is JsonArray -> value.map { element ->
                            if (element is JsonPrimitive) element.content else element.toString()
                        }
                        is JsonObject -> value.toString()
                        else -> null
                    }
                }
            } ?: emptyMap()
            
            // Call the node
            val result = node.handleRequest(request.method, params)
            
            // Convert result back to JSON
            val jsonResult = when (result) {
                null -> JsonNull
                is String -> JsonPrimitive(result)
                is Number -> JsonPrimitive(result)
                is Boolean -> JsonPrimitive(result)
                is List<*> -> JsonArray(result.map { item ->
                    when (item) {
                        is String -> JsonPrimitive(item)
                        is Number -> JsonPrimitive(item)
                        is Boolean -> JsonPrimitive(item)
                        else -> json.encodeToJsonElement(item)
                    }
                })
                else -> json.encodeToJsonElement(result)
            }
            
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = jsonResult
            )
            
        } catch (e: Exception) {
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(
                    code = -32603,
                    message = e.message ?: "Internal error",
                    data = JsonPrimitive(e.stackTraceToString())
                )
            )
        }
    }
    
    suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        return handleRequest(request)
    }
}

/**
 * Adapter that makes an MCP server accessible as a TrikeShed node
 */
class McpToNodeAdapter(
    internal val mcpServer: McpServerInterface
) : TrikeshedNode(
    nodeId = mcpServer.name,
    capabilities = mcpServer.capabilities
) {
    
    internal val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun handleRequest(method: String, params: Map<String, Any?>): Any? {
        // Convert params to JSON
        val jsonParams = JsonObject(
            params.mapValues { (_, value) ->
                when (value) {
                    null -> JsonNull
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is List<*> -> JsonArray(value.map { item ->
                        when (item) {
                            is String -> JsonPrimitive(item)
                            is Number -> JsonPrimitive(item)
                            is Boolean -> JsonPrimitive(item)
                            else -> json.encodeToJsonElement(item ?: JsonNull)
                        }
                    })
                    else -> json.encodeToJsonElement(value)
                }
            }
        )
        
        // Create MCP request
        val mcpRequest = McpRequest(
            id = JsonPrimitive(System.currentTimeMillis().toString()),
            method = method,
            params = jsonParams
        )
        
        // Call MCP server
        val mcpResponse = mcpServer.handleRequest(mcpRequest)
        
        // Handle response
        if (mcpResponse.error != null) {
            throw RuntimeException("MCP Error ${mcpResponse.error.code}: ${mcpResponse.error.message}")
        }
        
        // Convert result back from JSON
        return when (val result = mcpResponse.result) {
            null, is JsonNull -> null
            is JsonPrimitive -> when {
                result.isString -> result.content
                result.booleanOrNull != null -> result.booleanOrNull
                result.longOrNull != null -> result.longOrNull
                result.doubleOrNull != null -> result.doubleOrNull
                else -> result.content
            }
            is JsonArray -> result.map { element ->
                when (element) {
                    is JsonPrimitive -> when {
                        element.isString -> element.content
                        element.booleanOrNull != null -> element.booleanOrNull
                        element.longOrNull != null -> element.longOrNull
                        element.doubleOrNull != null -> element.doubleOrNull
                        else -> element.content
                    }
                    else -> json.decodeFromJsonElement<Any>(element)
                }
            }
            is JsonObject -> json.decodeFromJsonElement<Map<String, Any?>>(result)
            else -> json.decodeFromJsonElement<Any>(result)
        }
    }
}

/**
 * Ktor routing extension for MCP protocol endpoints
 */
fun Route.mcpBridgeRoutes(bridge: TrikeshedMcpBridge) {
    
    // MCP JSON-RPC endpoint
    post("/mcp/rpc") {
        val request = call.receive<McpRequest>()
        val response = bridge.handleMcpRequest(request)
        call.respond(response)
    }
    
    // MCP WebSocket endpoint
    webSocket("/mcp/ws") {
        // Send capabilities on connect
        val capabilities = McpResponse(
            jsonrpc = "2.0",
            result = JsonObject(mapOf(
                "capabilities" to JsonArray(listOf(
                    JsonPrimitive("psi-node"),
                    JsonPrimitive("analysis-node"),
                    JsonPrimitive("refactoring-node")
                ))
            ))
        )
        send(Frame.Text(Json.encodeToString(capabilities)))
        
        // Handle requests
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    val text = frame.readText()
                    val request = Json.decodeFromString<McpRequest>(text)
                    val response = bridge.handleMcpRequest(request)
                    send(Frame.Text(Json.encodeToString(response)))
                }
                else -> {}
            }
        }
    }
    
    // List available nodes
    get("/mcp/nodes") {
        val nodes = bridge.getRequestFactory().let { factory ->
            // Get registered nodes info
            mapOf(
                "nodes" to listOf(
                    mapOf(
                        "id" to "psi-node",
                        "capabilities" to listOf("find_symbols", "rename_symbols", "get_ast", "modify_ast")
                    ),
                    mapOf(
                        "id" to "analysis-node", 
                        "capabilities" to listOf("analyze_code", "find_issues", "suggest_fixes")
                    ),
                    mapOf(
                        "id" to "refactoring-node",
                        "capabilities" to listOf("rename", "move", "extract", "inline")
                    )
                )
            )
        }
        call.respond(nodes)
    }
}

/**
 * Example: Creating a unified service that works with both protocols
 */
class UnifiedService(
    internal val bridge: TrikeshedMcpBridge,
    internal val intelliJAccess: IntelliJAccessService
) {
    
    fun initialize() {
        // Create nodes
        val psiNode = PsiNode(intelliJAccess)
        val analysisNode = AnalysisNode(intelliJAccess)
        val refactoringNode = RefactoringNode(intelliJAccess)
        
        // Register nodes with RequestFactory
        val factory = bridge.getRequestFactory()
        factory.registerNode(psiNode.nodeId, psiNode)
        factory.registerNode(analysisNode.nodeId, analysisNode)
        factory.registerNode(refactoringNode.nodeId, refactoringNode)
        
        // Also expose nodes via MCP protocol
        bridge.exposeNodeAsMcp(psiNode)
        bridge.exposeNodeAsMcp(analysisNode)
        bridge.exposeNodeAsMcp(refactoringNode)

        // Expose CouchDB MCP Server
        val serviceRegistry = TrikeshedServiceRegistry()
        val reactor = TrikeshedReactor()
        val mockCouchClient = MockCouchClient()

        val couchDbAdapter = CouchDbMcpAdapter(
            name = "couchdb-mcp-server",
            version = "1.0.0",
            capabilities = setOf("get", "put", "listDatabases", "createDatabase", "deleteDatabase"),
            couchClient = mockCouchClient
        )
        bridge.exposeMcpAsNode(couchDbAdapter)

        // Expose QUIC MCP Server
        val quicEngine = QuicEngine(
            role = Role.SERVER,
            initialState = QuicConnectionState(
                localConnectionId = borg.trikeshed.net.quic.ConnectionId(byteArrayOf(1,2,3,4).toIndexed()),
                remoteConnectionId = borg.trikeshed.net.quic.ConnectionId(byteArrayOf(5,6,7,8).toIndexed())
            ),
            port = 8443, // Default QUIC port
            privateKey = byteArrayOf(0,0,0,0).toIndexed() // Dummy internal key
        )

        val quicAdapter = QuicMcpAdapter(
            name = "quic-mcp-server",
            version = "1.0.0",
            capabilities = setOf("connect", "createStream", "sendData", "closeConnection"),
            quicEngine = quicEngine
        )
        bridge.exposeMcpAsNode(quicAdapter)

        // Expose QUIC MCP Server
        val quicEngine = QuicEngine(
            role = Role.SERVER,
            initialState = QuicConnectionState(
                localConnectionId = borg.trikeshed.net.quic.ConnectionId(byteArrayOf(1,2,3,4).toIndexed()),
                remoteConnectionId = borg.trikeshed.net.quic.ConnectionId(byteArrayOf(5,6,7,8).toIndexed())
            ),
            port = 8443, // Default QUIC port
            privateKey = byteArrayOf(0,0,0,0).toIndexed() // Dummy internal key
        )

        val quicAdapter = QuicMcpAdapter(
            name = "quic-mcp-server",
            version = "1.0.0",
            capabilities = setOf("connect", "createStream", "sendData", "closeConnection"),
            quicEngine = quicEngine
        )
        bridge.exposeMcpAsNode(quicAdapter)
    }
    
    /**
     * Example: Use RequestFactory RPC to call a node
     */
    suspend fun findSymbolsViaRpc(pattern: String): List<SymbolInfo> {
        val context = bridge.getRequestFactory().createRequestContext()
        
        var result: List<SymbolInfo>? = null
        
        context.invoke(
            serviceId = "psi-node",
            method = "find_symbols",
            params = mapOf("pattern" to pattern),
            receiver = object : Receiver<List<SymbolInfo>> {
                override fun onSuccess(response: List<SymbolInfo>) {
                    result = response
                }
                
                override fun onFailure(error: ServerFailure) {
                    throw RuntimeException("RPC failed: ${error.message}")
                }
            }
        )
        
        context.fire()
        
        return result ?: emptyList()
    }
    
    /**
     * Example: Handle MCP request
     */
    suspend fun handleMcpExample() {
        val mcpRequest = McpRequest(
            id = JsonPrimitive("123"),
            method = "psi-node.find_symbols",
            params = JsonObject(mapOf(
                "pattern" to JsonPrimitive("*Series")
            ))
        )
        
        val response = bridge.handleMcpRequest(mcpRequest)
        println("MCP Response: $response")
    }
}