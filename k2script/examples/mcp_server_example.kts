#!/usr/bin/env k2script

// MCP Server Example - Demonstrates MCP server hosting and management
// This script shows how to create, deploy, and manage MCP servers

@file:Import("k2script.mcp.*")

println("=== MCP Server Hosting Example ===")
println()

// Create MCP servers with different capabilities
val toolsServer = MCPServer(
    name = "tools-server",
    version = "1.0.0",
    capabilities = setOf("tools", "filesystem")
)

val resourcesServer = MCPServer(
    name = "resources-server", 
    version = "1.0.0",
    capabilities = setOf("resources", "databases")
)

val promptsServer = MCPServer(
    name = "prompts-server",
    version = "1.0.0", 
    capabilities = setOf("prompts", "templates")
)

// Register custom handlers for tools server
toolsServer.registerHandler("tools/list") { params ->
    val filter = params["filter"] as? String ?: "all"
    val tools = when (filter) {
        "active" -> listOf("file-reader", "file-writer", "calculator")
        "system" -> listOf("process-manager", "network-monitor")
        else -> listOf("file-reader", "file-writer", "calculator", "process-manager", "network-monitor")
    }
    MCPResponse(200, mapOf("tools" to tools))
}

toolsServer.registerHandler("tools/execute") { params ->
    val toolName = params["tool"] as? String ?: "unknown"
    val result = when (toolName) {
        "calculator" -> "42"
        "file-reader" -> "file contents"
        else -> "unknown tool"
    }
    MCPResponse(200, mapOf("result" to result))
}

// Register custom handlers for resources server
resourcesServer.registerHandler("resources/list") { params ->
    val resources = listOf(
        mapOf("name" to "database-1", "type" to "postgresql", "status" to "active"),
        mapOf("name" to "cache-1", "type" to "redis", "status" to "active"),
        mapOf("name" to "storage-1", "type" to "s3", "status" to "active")
    )
    MCPResponse(200, mapOf("resources" to resources))
}

// Create registry and register servers
val registry = MCPRegistry()
registry.register(toolsServer)
registry.register(resourcesServer)
registry.register(promptsServer)

println("Registered MCP servers:")
registry.discover().forEach { server ->
    println("  - ${server.name} (${server.version})")
    println("    Capabilities: ${server.capabilities.joinToString(", ")}")
}
println()

// Create gateway for routing requests
val gateway = MCPGateway()
gateway.registerServer(toolsServer)
gateway.registerServer(resourcesServer)
gateway.registerServer(promptsServer)

// Test routing requests through gateway
println("Testing request routing:")
val requests = listOf(
    MCPRequest("tools/list", mapOf("filter" to "active")),
    MCPRequest("tools/execute", mapOf("tool" to "calculator")),
    MCPRequest("resources/list", emptyMap())
)

requests.forEach { request ->
    val response = gateway.route(request)
    println("  ${request.method}: ${response.status} - ${response.data}")
}
println()

// Test health checking
println("Health check results:")
val healthChecker = MCPHealthChecker()
registry.discover().forEach { server ->
    val health = healthChecker.checkHealth(server)
    val status = if (health.isHealthy) "HEALTHY" else "UNHEALTHY"
    println("  ${server.name}: $status (${health.responseTime}ms)")
}
println()

// Test load balancing
println("Load balancing test:")
val loadBalancer = MCPLoadBalancer()
loadBalancer.addServer(toolsServer)
loadBalancer.addServer(resourcesServer)
loadBalancer.addServer(promptsServer)

val request = MCPRequest("tools/list", emptyMap())
repeat(10) { index ->
    val selectedServer = loadBalancer.selectServer(request)
    println("  Request $index: routed to ${selectedServer.name}")
}
println()

// Test circuit breaker pattern
println("Circuit breaker test:")
val circuitBreaker = MCPCircuitBreaker(failureThreshold = 3, timeout = 5000L)

try {
    circuitBreaker.execute(toolsServer) {
        // Simulate successful operation
        "success"
    }
    println("  Circuit breaker: CLOSED (normal operation)")
} catch (e: Exception) {
    println("  Circuit breaker: ${e.message}")
}

// Test hosting service
println("Container hosting test:")
val hostingService = MCPHostingService()
val config = MCPHostingConfig(
    image = "mcp-server:latest",
    port = 8080,
    environment = mapOf(
        "MCP_CAPABILITIES" to "tools,resources,prompts",
        "MCP_LOG_LEVEL" to "INFO"
    )
)

val container = hostingService.deploy(config)
println("  Deployed container: ${container.id}")
println("  Status: ${container.status}")
println("  Port: ${container.port}")
println("  Healthy: ${container.isHealthy}")
println()

// Test response aggregation
println("Response aggregation test:")
val aggregator = MCPResponseAggregator()
val responses = listOf(
    MCPResponse(200, mapOf("tools" to listOf("tool1", "tool2"))),
    MCPResponse(200, mapOf("tools" to listOf("tool3", "tool4"))),
    MCPResponse(200, mapOf("resources" to listOf("resource1")))
)

val aggregated = aggregator.aggregate(responses)
println("  Aggregated response: ${aggregated.data}")
println()

// Test scaling
println("Auto-scaling test:")
val scalingManager = MCPScalingManager()
val scalingConfig = MCPScalingConfig(
    minInstances = 2,
    maxInstances = 5,
    targetCPU = 85.0  // High CPU should trigger scaling
)

val scaledServers = scalingManager.scale(scalingConfig)
println("  Scaled to ${scaledServers.size} instances:")
scaledServers.forEach { server ->
    println("    - ${server.name}")
    registry.register(server)
}
println()

// Test lifecycle management
println("Lifecycle management test:")
val lifecycleManager = MCPServerLifecycleManager()

// Start servers
registry.discover().take(2).forEach { server ->
    lifecycleManager.start(server)
    println("  Started: ${server.name}")
}

// Check running servers
val runningServers = lifecycleManager.getRunningServers()
println("  Running servers: ${runningServers.size}")

// Stop servers
runningServers.forEach { server ->
    lifecycleManager.stop(server)
    println("  Stopped: ${server.name}")
}
println()

println("=== MCP Server Example Completed ===")
println()
println("Next steps:")
println("  1. Use 'k2script --mcp list' to see registered servers")
println("  2. Use 'k2script --mcp health' to check server health")
println("  3. Use 'k2script --mcp route tools/list' to test routing")
println("  4. Use 'k2script --mcp deploy' to deploy containers") 