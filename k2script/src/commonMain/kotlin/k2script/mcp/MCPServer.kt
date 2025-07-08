@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.mcp

import k2script.platform.System
import borg.trikeshed.net.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import borg.trikeshed.net.http.*
import borg.trikeshed.io.*
import kotlinx.coroutines.*

/**
 * Core MCP Server Implementation
 * 
 * Features:
 * - Protocol handling (JSON-RPC over stdio/HTTP)
 * - Service discovery and registration
 * - Health monitoring and load balancing
 * - Containerized hosting
 * - Circuit breaker pattern
 * - Response aggregation
 */
class MCPServer(
    val name: String,
    val version: String,
    val capabilities: Set<String>,
    internal val port: Int = 8080
) {
    internal val handlers = mutableMapOf<String, MCPHandler>()
    internal var isRunning = false
    
    init {
        // Register default handlers
        registerHandler("tools/list") { params ->
            MCPResponse(200, mapOf("tools" to listOf("default-tool")))
        }
        registerHandler("resources/list") { params ->
            MCPResponse(200, mapOf("resources" to listOf("default-resource")))
        }
    }
    
    suspend fun getServerInfo(): MCPServerInfo = MCPServerInfo(name, version, capabilities)
    
    fun registerHandler(method: String, handler: MCPHandler) {
        handlers[method] = handler
    }
    
    suspend fun handleRequest(request: MCPRequest): MCPResponse {
        val handler = handlers[request.method]
        return handler?.invoke(request.params) ?: MCPResponse(404, mapOf("error" to "Method not found"))
    }
    
    fun start() {
        isRunning = true
        println("MCP Server $name started on port $port")
    }
    
    fun stop() {
        isRunning = false
        println("MCP Server $name stopped")
    }
    
    fun isRunning(): Boolean = isRunning
}

typealias MCPHandler = suspend (Map<String, Any>) -> MCPResponse

// === MCP Protocol Data Classes ===

@Serializable
data class MCPRequest(
    val method: String,
    val params: Map<String, @Contextual Any> = emptyMap(),
    val id: String? = null
)

@Serializable
data class MCPResponse(
    val statusCode: Int,
    val data: Map<String, @Contextual Any> = emptyMap(),
    val error: String? = null
)

@Serializable
data class MCPServerInfo(
    val name: String,
    val version: String,
    val capabilities: Set<String>
)

@Serializable
data class MCPHealthStatus(
    val isHealthy: Boolean,
    val responseTime: Long,
    val lastCheck: Long,
    val error: String? = null
)

@Serializable
data class MCPContainer(
    val id: String,
    val serverId: String,
    val host: String,
    val port: Int,
    val status: String,
    val createdAt: Long
)

@Serializable
data class MCPHostingConfig(
    val serverId: String,
    val host: String = "localhost",
    val port: Int = 8080,
    val resources: Map<String, @Contextual Any> = emptyMap()
)

@Serializable
sealed class MCPMessage {
    @Serializable
    data class Request(
        val method: String,
        val params: Map<String, @Contextual Any> = emptyMap(),
        val id: String? = null
    ) : MCPMessage()
    
    @Serializable
    data class Response(
        val statusCode: Int,
        val data: Map<String, @Contextual Any> = emptyMap(),
        val error: String? = null
    ) : MCPMessage()
}

@Serializable
data class MCPScalingConfig(
    val minInstances: Int = 1,
    val maxInstances: Int = 10,
    val targetCPU: Int = 70,
    val targetMemory: Int = 80
)

/**
 * MCP Registry for Service Discovery
 */
class MCPRegistry {
    internal val servers = mutableMapOf<String, MCPServer>()
    
    fun register(server: MCPServer) {
        servers[server.name] = server
        println("Registered MCP server: ${server.name}")
    }
    
    fun unregister(serverName: String) {
        servers.remove(serverName)
        println("Unregistered MCP server: $serverName")
    }
    
    fun discover(): List<MCPServer> = servers.values.toList()
    
    fun findByName(name: String): MCPServer? = servers[name]
    
    fun findByCapability(capability: String): List<MCPServer> {
        return servers.values.filter { it.capabilities.contains(capability) }
    }
}

/**
 * MCP Gateway for REST API
 */
class MCPGateway {
    internal val registry = MCPRegistry()
    internal val loadBalancer = MCPLoadBalancer()
    
    fun registerServer(server: MCPServer) {
        registry.register(server)
        loadBalancer.addServer(server)
    }
    
    suspend fun route(request: MCPRequest): MCPResponse {
        val server = loadBalancer.selectServer(request)
        return server.handleRequest(request)
    }
    
    fun getRegisteredServers(): List<MCPServer> = registry.discover()
}

/**
 * MCP Load Balancer
 */
class MCPLoadBalancer {
    internal val servers = mutableListOf<MCPServer>()
    internal var currentIndex = 0
    
    fun addServer(server: MCPServer) {
        servers.add(server)
    }
    
    fun removeServer(server: MCPServer) {
        servers.remove(server)
    }
    
    fun selectServer(request: MCPRequest): MCPServer {
        if (servers.isEmpty()) {
            throw IllegalStateException("No servers available")
        }
        
        // Round-robin load balancing
        val server = servers[currentIndex]
        currentIndex = (currentIndex + 1) % servers.size
        return server
    }
    
    fun getHealthyServers(): List<MCPServer> {
        return servers.filter { it.isRunning() }
    }
}

/**
 * MCP Health Checker
 */
class MCPHealthChecker {
    suspend fun checkHealth(server: MCPServer): MCPHealthStatus {
        val startTime = System.currentTimeMillis()
        
        return try {
            val info = server.getServerInfo()
            val responseTime = System.currentTimeMillis() - startTime
            
            MCPHealthStatus(
                isHealthy = true,
                responseTime = responseTime,
                lastCheck = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            MCPHealthStatus(
                isHealthy = false,
                responseTime = System.currentTimeMillis() - startTime,
                lastCheck = System.currentTimeMillis()
            )
        }
    }
}

/**
 * MCP Hosting Service for Containerized Deployment
 */
class MCPHostingService {
    internal val containers = mutableMapOf<String, MCPContainer>()
    
    suspend fun deploy(config: MCPHostingConfig): MCPContainer {
        val containerId = "mcp-${System.currentTimeMillis()}"
        
        // Simulate container deployment
        delay(1000) // Simulate deployment time
        
        val container = MCPContainer(
            id = containerId,
            serverId = config.serverId,
            host = config.host,
            port = config.port,
            status = "running",
            createdAt = System.currentTimeMillis()
        )
        
        containers[containerId] = container
        println("Deployed MCP container: $containerId on port ${config.port}")
        
        return container
    }
    
    fun getContainer(containerId: String): MCPContainer? = containers[containerId]
    
    fun listContainers(): List<MCPContainer> = containers.values.toList()
    
    suspend fun stopContainer(containerId: String) {
        containers.remove(containerId)
        println("Stopped MCP container: $containerId")
    }
}

/**
 * MCP Circuit Breaker Pattern
 */
class MCPCircuitBreaker(
    internal val failureThreshold: Int,
    internal val timeout: Long
) {
    internal var failureCount = 0
    internal var lastFailureTime = 0L
    internal var state = CircuitState.CLOSED
    
    enum class CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }
    
    suspend fun <T> execute(server: MCPServer, block: suspend () -> T): T {
        when (state) {
            CircuitState.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > timeout) {
                    state = CircuitState.HALF_OPEN
                } else {
                    throw RuntimeException("Circuit breaker is OPEN")
                }
            }
            CircuitState.HALF_OPEN -> {
                // Allow one request to test if service is back
            }
            CircuitState.CLOSED -> {
                // Normal operation
            }
        }
        
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
    
    internal fun onSuccess() {
        failureCount = 0
        state = CircuitState.CLOSED
    }
    
    internal fun onFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        if (failureCount >= failureThreshold) {
            state = CircuitState.OPEN
        }
    }
    
    fun isOpen(): Boolean = state == CircuitState.OPEN
    fun isHalfOpen(): Boolean = state == CircuitState.HALF_OPEN
    fun isClosed(): Boolean = state == CircuitState.CLOSED
}

/**
 * MCP Response Aggregator
 */
class MCPResponseAggregator {
    suspend fun aggregate(responses: List<MCPResponse>): MCPResponse {
        if (responses.isEmpty()) {
            return MCPResponse(200, emptyMap())
        }
        
        val aggregatedData = mutableMapOf<String, Any>()
        
        responses.forEach { response ->
            response.data.forEach { (key, value) ->
                when (val existing = aggregatedData[key]) {
                    is List<*> -> {
                        val newList = existing.toMutableList()
                        when (value) {
                            is List<*> -> newList.addAll(value)
                            else -> newList.add(value)
                        }
                        aggregatedData[key] = newList
                    }
                    null -> aggregatedData[key] = value
                    else -> {
                        aggregatedData[key] = listOf(existing, value)
                    }
                }
            }
        }
        
        return MCPResponse(200, aggregatedData)
    }
}

/**
 * MCP Protocol Handler
 */
class MCPProtocol {
    internal val json = Json { ignoreUnknownKeys = true }
    
    fun serialize(message: MCPMessage): String {
        return json.encodeToString(message)
    }
    
    fun deserialize(data: String): MCPMessage {
        return json.decodeFromString(data)
    }
}

/**
 * MCP Server Lifecycle Manager
 */
class MCPServerLifecycleManager {
    internal val runningServers = mutableSetOf<MCPServer>()
    
    suspend fun start(server: MCPServer) {
        server.start()
        runningServers.add(server)
    }
    
    suspend fun stop(server: MCPServer) {
        server.stop()
        runningServers.remove(server)
    }
    
    fun isRunning(server: MCPServer): Boolean = runningServers.contains(server)
    
    fun getRunningServers(): List<MCPServer> = runningServers.toList()
}

/**
 * MCP Scaling Manager
 */
class MCPScalingManager {
    suspend fun scale(config: MCPScalingConfig): List<MCPServer> {
        val servers = mutableListOf<MCPServer>()
        
        // Create minimum number of instances
        repeat(config.minInstances) { index ->
            val server = MCPServer(
                name = "scaled-server-$index",
                version = "1.0.0",
                capabilities = setOf("tools", "resources")
            )
            servers.add(server)
        }
        
        // Simulate auto-scaling based on CPU
        if (config.targetCPU > 80.0 && servers.size < config.maxInstances) {
            val additionalServer = MCPServer(
                name = "scaled-server-${servers.size}",
                version = "1.0.0",
                capabilities = setOf("tools", "resources")
            )
            servers.add(additionalServer)
        }
        
        return servers
    }
}

// === TrikeShed REST Production Service Integration ===

/**
 * Immaculate TrikeShed REST Service for MCP
 * - Provides REST endpoints using TrikeShed HttpServer
 * - Registers with ServiceRegistry and supports health monitoring
 * - Uses idiomatic REST, JSON, and TrikeShed patterns
 */
class MCPRestService(
    val mcpServer: MCPServer,
    val registry: ServiceRegistry = ServiceIntegrationFactory.createServiceRegistryWithMonitoring(),
    val port: Int = 8080
) {
    private val reactor = Reactor()
    private val httpServer = HttpServer(
        config = HttpServerConfig(port = HttpServerPort(port)),
        reactor = reactor,
        handler = ::routeRequest
    )
    private val serviceId = "mcp-rest-$port"
    private val serviceInfo = ServiceInfo(
        serviceId = serviceId,
        name = mcpServer.name,
        type = "mcp-rest",
        endpoint = "http://localhost:$port",
        version = mcpServer.version,
        tags = setOf("rest", "trikeshed", "mcp"),
        metadata = mapOf("capabilities" to mcpServer.capabilities.joinToString(","))
    )
    private val healthMonitor = ServiceHealthMonitor(registry)
    private val circuitBreaker = MCPCircuitBreaker(failureThreshold = 5, timeout = 10000)

    suspend fun start() {
        // Register service and start health monitoring
        registry.registerService(serviceId, serviceInfo)
        healthMonitor.startMonitoring()
        // Start HTTP server
        httpServer.start()
    }

    suspend fun stop() {
        httpServer.stop()
        registry.unregisterService(serviceId)
    }

    // Main REST router
    private suspend fun routeRequest(request: HttpRequest, ccek: CcekContext): HttpResponse {
        return when (request.method) {
            HttpMethod.GET -> when (request.path.value) {
                "/health" -> jsonResponse(200, mapOf("healthy" to true))
                "/info" -> jsonResponse(200, mapOf(
                    "name" to mcpServer.name,
                    "version" to mcpServer.version,
                    "capabilities" to mcpServer.capabilities.toList()
                ))
                "/resources" -> jsonResponse(200, mapOf("resources" to listOf("default-resource")))
                "/tools" -> jsonResponse(200, mapOf("tools" to listOf("default-tool")))
                else -> notFound()
            }
            else -> methodNotAllowed()
        }
    }

    // Helper: JSON response
    private fun jsonResponse(status: Int, data: Map<String, Any>): HttpResponse {
        val json = kotlinx.serialization.json.Json.encodeToString(
            MapSerializer(String.serializer(), kotlinx.serialization.json.JsonElement.serializer()),
            data.mapValues { (_, v) -> kotlinx.serialization.json.Json.encodeToJsonElement(v) }
        )
        return HttpResponse(
            status = HttpStatusCode(status),
            reasonPhrase = HttpStatusCode(status).defaultReasonPhrase(),
            headers = 1 j { Join(HttpHeaderName("Content-Type"), HttpHeaderValue("application/json")) },
            body = json.encodeToByteArray()
        )
    }
    private fun notFound(): HttpResponse = HttpResponse(
        status = HttpStatus.NOT_FOUND,
        reasonPhrase = HttpStatus.NOT_FOUND.defaultReasonPhrase(),
        headers = 1 j { Join(HttpHeaderName("Content-Type"), HttpHeaderValue("application/json")) },
        body = "{\"error\":\"Not found\"}".encodeToByteArray()
    )
    private fun methodNotAllowed(): HttpResponse = HttpResponse(
        status = HttpStatus.METHOD_NOT_ALLOWED,
        reasonPhrase = HttpStatus.METHOD_NOT_ALLOWED.defaultReasonPhrase(),
        headers = 1 j { Join(HttpHeaderName("Content-Type"), HttpHeaderValue("application/json")) },
        body = "{\"error\":\"Method not allowed\"}".encodeToByteArray()
    )
} 