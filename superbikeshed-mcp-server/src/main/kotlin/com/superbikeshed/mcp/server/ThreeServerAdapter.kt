package com.superbikeshed.mcp.server

import com.superbikeshed.mcp.three.*
import com.superbikeshed.mcp.trikeshed.*
import kotlinx.coroutines.*
import mu.KotlinLogging

internal val logger = KotlinLogging.logger {}

class ThreeServerAdapter(
    internal val threeServer: ThreeServer,
    name: String,
    port: Int
) : McpServer(name, port) {
    
    override suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        logger.info { "Handling MCP request: ${request.method} on ${getName()}" }
        
        // Convert MCP request to ThreeRequest
        val threeRequest = ThreeRequest(
            id = request.id,
            method = request.method,
            params = request.params
        )
        
        // Delegate to three server
        val threeResponse = threeServer.handleRequest(threeRequest)
        
        // Convert ThreeResponse back to McpResponse
        return if (threeResponse.error != null) {
            createErrorResponse(
                id = threeResponse.id,
                code = 400,
                message = threeResponse.error
            )
        } else {
            createSuccessResponse(
                id = threeResponse.id,
                result = threeResponse.result
            )
        }
    }
}

// Factory for creating MCP servers from three servers
object ThreeServerFactory {
    
    fun createPsiMcpServer(): ThreeServerAdapter {
        val threeServer = PsiMcpServer()
        return ThreeServerAdapter(threeServer, "PSI-MCP-Server", 3001)
    }
    
    fun createAnalysisMcpServer(): ThreeServerAdapter {
        val threeServer = AnalysisMcpServer()
        return ThreeServerAdapter(threeServer, "Analysis-MCP-Server", 3002)
    }
    
    fun createRefactorMcpServer(): ThreeServerAdapter {
        val threeServer = RefactorMcpServer()
        return ThreeServerAdapter(threeServer, "Refactor-MCP-Server", 3003)
    }
} 