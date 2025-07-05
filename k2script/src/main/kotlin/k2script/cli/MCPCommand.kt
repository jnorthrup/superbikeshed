package k2script.cli

import k2script.mcp.*
import kotlinx.coroutines.runBlocking

/**
 * MCP Server Management CLI Command
 * 
 * Usage:
 *   k2script --mcp start <server-name> [options]
 *   k2script --mcp stop <server-name>
 *   k2script --mcp list
 *   k2script --mcp deploy <config-file>
 *   k2script --mcp health
 *   k2script --mcp scale <config>
 */
object MCPCommand {
    
    private val registry = MCPRegistry()
    private val gateway = MCPGateway()
    private val hostingService = MCPHostingService()
    private val lifecycleManager = MCPServerLifecycleManager()
    private val healthChecker = MCPHealthChecker()
    private val scalingManager = MCPScalingManager()
    
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            showHelp()
            return
        }
        
        val command = args[0]
        
        runBlocking {
            when (command) {
                "start" -> handleStart(args.drop(1).toTypedArray())
                "stop" -> handleStop(args.drop(1).toTypedArray())
                "list" -> handleList()
                "deploy" -> handleDeploy(args.drop(1).toTypedArray())
                "health" -> handleHealth()
                "scale" -> handleScale(args.drop(1).toTypedArray())
                "register" -> handleRegister(args.drop(1).toTypedArray())
                "route" -> handleRoute(args.drop(1).toTypedArray())
                "help" -> showHelp()
                else -> {
                    println("Unknown command: $command")
                    showHelp()
                }
            }
        }
    }
    
    private suspend fun handleStart(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Server name required")
            return
        }
        
        val serverName = args[0]
        val server = registry.findByName(serverName)
        
        if (server == null) {
            println("Error: Server '$serverName' not found")
            return
        }
        
        lifecycleManager.start(server)
        println("Started MCP server: $serverName")
    }
    
    private suspend fun handleStop(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Server name required")
            return
        }
        
        val serverName = args[0]
        val server = registry.findByName(serverName)
        
        if (server == null) {
            println("Error: Server '$serverName' not found")
            return
        }
        
        lifecycleManager.stop(server)
        println("Stopped MCP server: $serverName")
    }
    
    private fun handleList() {
        val servers = registry.discover()
        
        if (servers.isEmpty()) {
            println("No MCP servers registered")
            return
        }
        
        println("Registered MCP Servers:")
        println("========================")
        
        servers.forEach { server ->
            val status = if (lifecycleManager.isRunning(server)) "RUNNING" else "STOPPED"
            println("${server.name} (${server.version}) - $status")
            println("  Capabilities: ${server.capabilities.joinToString(", ")}")
            println()
        }
    }
    
    private suspend fun handleDeploy(args: Array<String>) {
        if (args.isEmpty()) {
            println("Error: Configuration required")
            return
        }
        
        val config = MCPHostingConfig(
            image = "mcp-server:latest",
            port = 8080,
            environment = mapOf("MCP_CAPABILITIES" to "tools,resources")
        )
        
        val container = hostingService.deploy(config)
        println("Deployed MCP container: ${container.id}")
        println("  Status: ${container.status}")
        println("  Port: ${container.port}")
        println("  Healthy: ${container.isHealthy}")
    }
    
    private suspend fun handleHealth() {
        val servers = registry.discover()
        
        if (servers.isEmpty()) {
            println("No MCP servers to check")
            return
        }
        
        println("MCP Server Health Status:")
        println("=========================")
        
        servers.forEach { server ->
            val health = healthChecker.checkHealth(server)
            val status = if (health.isHealthy) "HEALTHY" else "UNHEALTHY"
            
            println("${server.name}: $status")
            println("  Response Time: ${health.responseTime}ms")
            println("  Last Check: ${health.lastCheck}")
            println()
        }
    }
    
    private suspend fun handleScale(args: Array<String>) {
        val config = MCPScalingConfig(
            minInstances = 2,
            maxInstances = 5,
            targetCPU = 70.0
        )
        
        val scaledServers = scalingManager.scale(config)
        println("Scaled MCP servers: ${scaledServers.size} instances")
        
        scaledServers.forEach { server ->
            registry.register(server)
            println("  - ${server.name}")
        }
    }
    
    private fun handleRegister(args: Array<String>) {
        if (args.size < 3) {
            println("Error: Usage: register <name> <version> <capabilities...>")
            return
        }
        
        val name = args[0]
        val version = args[1]
        val capabilities = args.drop(2).toSet()
        
        val server = MCPServer(name, version, capabilities)
        registry.register(server)
        gateway.registerServer(server)
        
        println("Registered MCP server: $name")
    }
    
    private suspend fun handleRoute(args: Array<String>) {
        if (args.size < 2) {
            println("Error: Usage: route <method> <params...>")
            return
        }
        
        val method = args[0]
        val params = args.drop(1).associate { it.split("=").let { parts -> parts[0] to parts.getOrNull(1) ?: "" } }
        
        val request = MCPRequest(method, params)
        val response = gateway.route(request)
        
        println("Response: ${response.status}")
        println("Data: ${response.data}")
    }
    
    private fun showHelp() {
        println("""
            MCP Server Management Commands:
            ===============================
            
            start <server-name>          Start an MCP server
            stop <server-name>           Stop an MCP server
            list                         List all registered servers
            deploy <config>              Deploy a new MCP container
            health                       Check health of all servers
            scale <config>               Scale servers based on config
            register <name> <version> <capabilities...>  Register a new server
            route <method> <params...>   Route a request through gateway
            help                         Show this help
            
            Examples:
              k2script --mcp register my-server 1.0.0 tools resources
              k2script --mcp start my-server
              k2script --mcp route tools/list filter=active
              k2script --mcp health
        """.trimIndent())
    }
} 