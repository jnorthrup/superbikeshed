@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.mcp

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * TDD Tests for MCP Server and Hosting Services
 * 
 * Architecture:
 * - MCPServer: Core MCP protocol implementation
 * - MCPRegistry: Service discovery and registration
 * - MCPGateway: REST API gateway for MCP operations
 * - MCPHostingService: Containerized hosting management
 * - MCPLoadBalancer: Request distribution and health checks
 */
class MCPServerTest {
    
    @Test
    fun `should create MCP server with basic protocol support`() = runTest {
        // Given
        val server = MCPServer(
            name = "test-server",
            version = "1.0.0",
            capabilities = setOf("tools", "resources", "prompts")
        )
        
        // When
        val info = server.getServerInfo()
        
        // Then
        assertEquals("test-server", info.name)
        assertEquals("1.0.0", info.version)
        assertTrue(info.capabilities.contains("tools"))
        assertTrue(info.capabilities.contains("resources"))
        assertTrue(info.capabilities.contains("prompts"))
    }
    
    @Test
    fun `should register and discover MCP servers`() = runTest {
        // Given
        val registry = MCPRegistry()
        val server1 = MCPServer("server1", "1.0.0", setOf("tools"))
        val server2 = MCPServer("server2", "1.0.0", setOf("resources"))
        
        // When
        registry.register(server1)
        registry.register(server2)
        val discovered = registry.discover()
        
        // Then
        assertEquals(2, discovered.size)
        assertTrue(discovered.any { it.name == "server1" })
        assertTrue(discovered.any { it.name == "server2" })
    }
    
    @Test
    fun `should route requests through MCP gateway`() = runTest {
        // Given
        val gateway = MCPGateway()
        val server = MCPServer("test-server", "1.0.0", setOf("tools"))
        gateway.registerServer(server)
        
        // When
        val request = MCPRequest(
            method = "tools/list",
            params = mapOf("filter" to "active")
        )
        val response = gateway.route(request)
        
        // Then
        assertNotNull(response)
        assertEquals(200, response.status)
    }
    
    @Test
    fun `should handle server health checks`() = runTest {
        // Given
        val healthChecker = MCPHealthChecker()
        val server = MCPServer("test-server", "1.0.0", setOf("tools"))
        
        // When
        val health = healthChecker.checkHealth(server)
        
        // Then
        assertTrue(health.isHealthy)
        assertTrue(health.responseTime > 0)
        assertNotNull(health.lastCheck)
    }
    
    @Test
    fun `should load balance requests across multiple servers`() = runTest {
        // Given
        val loadBalancer = MCPLoadBalancer()
        val server1 = MCPServer("server1", "1.0.0", setOf("tools"))
        val server2 = MCPServer("server2", "1.0.0", setOf("tools"))
        loadBalancer.addServer(server1)
        loadBalancer.addServer(server2)
        
        // When
        val request = MCPRequest("tools/list", emptyMap())
        val responses = mutableListOf<MCPServer>()
        
        repeat(10) {
            val selectedServer = loadBalancer.selectServer(request)
            responses.add(selectedServer)
        }
        
        // Then
        assertEquals(10, responses.size)
        // Should distribute requests (not perfect but should have both servers)
        val server1Count = responses.count { it.name == "server1" }
        val server2Count = responses.count { it.name == "server2" }
        assertTrue(server1Count > 0)
        assertTrue(server2Count > 0)
    }
    
    @Test
    fun `should host MCP servers in containers`() = runTest {
        // Given
        val hostingService = MCPHostingService()
        val config = MCPHostingConfig(
            image = "mcp-server:latest",
            port = 8080,
            environment = mapOf("MCP_CAPABILITIES" to "tools,resources")
        )
        
        // When
        val container = hostingService.deploy(config)
        
        // Then
        assertNotNull(container)
        assertEquals("running", container.status)
        assertEquals(8080, container.port)
        assertTrue(container.isHealthy)
    }
    
    @Test
    fun `should handle circuit breaker pattern`() = runTest {
        // Given
        val circuitBreaker = MCPCircuitBreaker(
            failureThreshold = 3,
            timeout = 5000L
        )
        val failingServer = MCPServer("failing-server", "1.0.0", setOf("tools"))
        
        // When - Simulate failures
        repeat(3) {
            try {
                circuitBreaker.execute(failingServer) {
                    throw RuntimeException("Server error")
                }
            } catch (e: Exception) {
                // Expected
            }
        }
        
        // Then - Circuit should be open
        assertTrue(circuitBreaker.isOpen())
        
        // When - Wait for timeout and try again
        kotlinx.coroutines.delay(6000)
        
        // Then - Circuit should be half-open
        assertTrue(circuitBreaker.isHalfOpen())
    }
    
    @Test
    fun `should aggregate responses from multiple servers`() = runTest {
        // Given
        val aggregator = MCPResponseAggregator()
        val server1 = MCPServer("server1", "1.0.0", setOf("tools"))
        val server2 = MCPServer("server2", "1.0.0", setOf("tools"))
        
        val responses = listOf(
            MCPResponse(200, mapOf("tools" to listOf("tool1", "tool2"))),
            MCPResponse(200, mapOf("tools" to listOf("tool3", "tool4")))
        )
        
        // When
        val aggregated = aggregator.aggregate(responses)
        
        // Then
        assertEquals(200, aggregated.status)
        val tools = aggregated.data["tools"] as? List<*>
        assertNotNull(tools)
        assertEquals(4, tools.size)
        assertTrue(tools.contains("tool1"))
        assertTrue(tools.contains("tool2"))
        assertTrue(tools.contains("tool3"))
        assertTrue(tools.contains("tool4"))
    }
    
    @Test
    fun `should handle MCP protocol messages`() = runTest {
        // Given
        val protocol = MCPProtocol()
        val message = MCPMessage(
            id = "msg-1",
            method = "tools/list",
            params = mapOf("filter" to "active")
        )
        
        // When
        val serialized = protocol.serialize(message)
        val deserialized = protocol.deserialize(serialized)
        
        // Then
        assertEquals(message.id, deserialized.id)
        assertEquals(message.method, deserialized.method)
        assertEquals(message.params, deserialized.params)
    }
    
    @Test
    fun `should manage server lifecycle`() = runTest {
        // Given
        val lifecycleManager = MCPServerLifecycleManager()
        val server = MCPServer("test-server", "1.0.0", setOf("tools"))
        
        // When
        lifecycleManager.start(server)
        
        // Then
        assertTrue(lifecycleManager.isRunning(server))
        
        // When
        lifecycleManager.stop(server)
        
        // Then
        assertFalse(lifecycleManager.isRunning(server))
    }
    
    @Test
    fun `should handle server scaling`() = runTest {
        // Given
        val scalingManager = MCPScalingManager()
        val config = MCPScalingConfig(
            minInstances = 2,
            maxInstances = 5,
            targetCPU = 70.0
        )
        
        // When
        val scaledServers = scalingManager.scale(config)
        
        // Then
        assertTrue(scaledServers.size >= config.minInstances)
        assertTrue(scaledServers.size <= config.maxInstances)
    }
}

// === MCP Data Classes ===

data class MCPServer(
    val name: String,
    val version: String,
    val capabilities: Set<String>
) {
    suspend fun getServerInfo(): MCPServerInfo = MCPServerInfo(name, version, capabilities)
}

data class MCPServerInfo(
    val name: String,
    val version: String,
    val capabilities: Set<String>
)

data class MCPRequest(
    val method: String,
    val params: Map<String, Any>
)

data class MCPResponse(
    val status: Int,
    val data: Map<String, Any>
)

data class MCPMessage(
    val id: String,
    val method: String,
    val params: Map<String, Any>
)

data class MCPHealthStatus(
    val isHealthy: Boolean,
    val responseTime: Long,
    val lastCheck: Long
)

data class MCPContainer(
    val id: String,
    val status: String,
    val port: Int,
    val isHealthy: Boolean
)

data class MCPHostingConfig(
    val image: String,
    val port: Int,
    val environment: Map<String, String>
)

data class MCPScalingConfig(
    val minInstances: Int,
    val maxInstances: Int,
    val targetCPU: Double
) 