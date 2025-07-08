package com.superbikeshed.mcp.server

import com.superbikeshed.mcp.trikeshed.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging

internal val logger = KotlinLogging.logger {}

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: Map<String, String>? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: Map<String, String>? = null
)

abstract class McpServer(
    internal val name: String,
    internal val port: Int
) {
    internal val reactor = Reactor()
    internal val networkManager = NetworkManager()
    internal val serviceRegistry = ServiceRegistry()
    
    suspend fun start() {
        logger.info { "Starting $name MCP server on port $port" }
        
        // Initialize trikeshed components
        reactor.start()
        serviceRegistry.register(name, this)
        networkManager.bind(port)
        
        // Start processing loop
        launch {
            processMcpRequests()
        }
        
        logger.info { "$name MCP server ready on port $port" }
    }
    
    internal suspend fun processMcpRequests() {
        while (true) {
            try {
                val request = networkManager.receive<McpRequest>()
                val response = handleMcpRequest(request)
                networkManager.send(response)
            } catch (e: Exception) {
                logger.error(e) { "Error processing MCP request" }
                val errorResponse = McpResponse(
                    id = "error",
                    error = McpError(500, "Internal server error: ${e.message}")
                )
                networkManager.send(errorResponse)
            }
        }
    }
    
    internal abstract suspend fun handleMcpRequest(request: McpRequest): McpResponse
    
    internal fun createSuccessResponse(id: String, result: Map<String, String>): McpResponse {
        return McpResponse(id = id, result = result)
    }
    
    internal fun createErrorResponse(id: String, code: Int, message: String): McpResponse {
        return McpResponse(
            id = id,
            error = McpError(code, message)
        )
    }
    
    fun getName(): String = name
    fun getPort(): Int = port
} 