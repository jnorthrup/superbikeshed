package com.superbikeshed.mcp.three

import com.superbikeshed.mcp.trikeshed.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging

internal val logger = KotlinLogging.logger {}

@Serializable
data class ThreeRequest(
    val id: String,
    val method: String,
    val params: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ThreeResponse(
    val id: String,
    val result: Map<String, String>,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

abstract class ThreeServer(
    internal val name: String,
    internal val capabilities: List<String>
) {
    internal val reactor = Reactor()
    internal val serviceRegistry = ServiceRegistry()
    internal val networkManager = NetworkManager()
    
    suspend fun start(port: Int) {
        logger.info { "Starting $name server on port $port" }
        
        // Initialize trikeshed components
        reactor.start()
        serviceRegistry.register(name, this)
        networkManager.bind(port)
        
        // Start processing loop
        launch {
            processRequests()
        }
        
        logger.info { "$name server ready on port $port" }
    }
    
    internal suspend fun processRequests() {
        while (true) {
            val request = networkManager.receive<ThreeRequest>()
            val response = handleRequest(request)
            networkManager.send(response)
        }
    }
    
    internal abstract suspend fun handleRequest(request: ThreeRequest): ThreeResponse
    
    internal fun createResponse(id: String, result: Map<String, String>): ThreeResponse {
        return ThreeResponse(id, result)
    }
    
    internal fun createErrorResponse(id: String, error: String): ThreeResponse {
        return ThreeResponse(id, emptyMap(), error)
    }
    
    fun getCapabilities(): List<String> = capabilities
} 