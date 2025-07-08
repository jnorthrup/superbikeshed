package com.v2superbikeshed.nexus.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.v2superbikeshed.nexus.rpc.*
import com.v2superbikeshed.nexus.service.IntelliJAccessService
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.PrintWriter

/**
 * Simple MCP Server that works with existing infrastructure
 * Uses standard sockets for now, QUIC channels can be added later
 */
@Service(Service.Level.PROJECT)
class SimpleMcpServer(internal val project: Project) {
    
    companion object {
        const val MCP_PORT = 63344
    }
    
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
    internal var serverSocket: ServerSocket? = null
    internal var isRunning = false
    internal val connections = mutableListOf<Socket>()
    
    // Nodes
    internal lateinit var psiNode: PsiNode
    internal lateinit var analysisNode: AnalysisNode
    internal lateinit var refactoringNode: RefactoringNode
    
    fun start() {
        if (isRunning) return
        
        // Initialize nodes
        val intelliJAccess = IntelliJAccessService.getInstance(project)
        psiNode = PsiNode(intelliJAccess)
        analysisNode = AnalysisNode(intelliJAccess)
        refactoringNode = RefactoringNode(intelliJAccess)
        
        isRunning = true
        
        GlobalScope.launch {
            try {
                serverSocket = ServerSocket(MCP_PORT)
                println("MCP Server listening on port $MCP_PORT")
                
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    connections.add(clientSocket)
                    
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    println("MCP Server error: ${e.message}")
                }
            }
        }
    }
    
    internal suspend fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(socket.getInputStream().reader())
            val writer = PrintWriter(socket.getOutputStream(), true)
            
            // Send server info
            val serverInfo = json.encodeToString(McpResponse(
                jsonrpc = "2.0",
                result = JsonObject(mapOf(
                    "serverInfo" to JsonObject(mapOf(
                        "name" to JsonPrimitive("v2superbikeshed-mcp"),
                        "version" to JsonPrimitive("1.0.0")
                    ))
                ))
            ))
            writer.println(serverInfo)
            
            // Read requests
            while (isRunning && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                
                try {
                    val request = json.decodeFromString<McpRequest>(line)
                    val response = handleRequest(request)
                    writer.println(json.encodeToString(response))
                } catch (e: Exception) {
                    val errorResponse = McpResponse(
                        jsonrpc = "2.0",
                        error = McpError(-32603, "Internal error: ${e.message}")
                    )
                    writer.println(json.encodeToString(errorResponse))
                }
            }
        } catch (e: Exception) {
            println("Client handler error: ${e.message}")
        } finally {
            connections.remove(socket)
            socket.close()
        }
    }
    
    internal suspend fun handleRequest(request: McpRequest): McpResponse {
        return when {
            request.method == "initialize" -> handleInitialize(request)
            request.method == "tools/list" -> handleToolsList(request)
            request.method.startsWith("psi-node.") -> handlePsiRequest(request)
            request.method.startsWith("analysis-node.") -> handleAnalysisRequest(request)
            request.method.startsWith("refactoring-node.") -> handleRefactoringRequest(request)
            else -> McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32601, "Method not found: ${request.method}")
            )
        }
    }
    
    internal fun handleInitialize(request: McpRequest): McpResponse {
        return McpResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonObject(mapOf(
                "protocolVersion" to JsonPrimitive("2024-11-05"),
                "capabilities" to JsonObject(mapOf(
                    "tools" to JsonObject(mapOf("listChanged" to JsonPrimitive(true))),
                    "resources" to JsonObject(mapOf("listChanged" to JsonPrimitive(true)))
                )),
                "serverInfo" to JsonObject(mapOf(
                    "name" to JsonPrimitive("v2superbikeshed-mcp"),
                    "version" to JsonPrimitive("1.0.0")
                ))
            ))
        )
    }
    
    internal fun handleToolsList(request: McpRequest): McpResponse {
        val tools = listOf(
            "psi-node.find_symbols",
            "psi-node.rename_symbols", 
            "psi-node.get_ast",
            "analysis-node.analyze_code",
            "analysis-node.find_issues",
            "refactoring-node.rename",
            "refactoring-node.move"
        ).map { tool ->
            JsonObject(mapOf(
                "name" to JsonPrimitive(tool),
                "description" to JsonPrimitive("Tool: $tool")
            ))
        }
        
        return McpResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonObject(mapOf("tools" to JsonArray(tools)))
        )
    }
    
    internal suspend fun handlePsiRequest(request: McpRequest): McpResponse {
        val method = request.method.removePrefix("psi-node.")
        val params = request.params?.toMap() ?: emptyMap()
        
        return try {
            val result = psiNode.handleRequest(method, params)
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
    
    internal suspend fun handleAnalysisRequest(request: McpRequest): McpResponse {
        val method = request.method.removePrefix("analysis-node.")
        val params = request.params?.toMap() ?: emptyMap()
        
        return try {
            val result = analysisNode.handleRequest(method, params)
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
    
    internal suspend fun handleRefactoringRequest(request: McpRequest): McpResponse {
        val method = request.method.removePrefix("refactoring-node.")
        val params = request.params?.toMap() ?: emptyMap()
        
        return try {
            val result = refactoringNode.handleRequest(method, params)
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = json.encodeToJsonElement(result)
            )
        } catch (e: Exception) {
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, e.message ?: "Refactoring operation failed")
            )
        }
    }
    
    fun stop() {
        isRunning = false
        connections.forEach { it.close() }
        connections.clear()
        serverSocket?.close()
        serverSocket = null
    }
}

// Extension function to convert JsonObject to Map
internal fun JsonObject.toMap(): Map<String, Any?> {
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