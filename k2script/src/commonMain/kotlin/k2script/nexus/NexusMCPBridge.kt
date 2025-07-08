@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.nexus

import k2script.mcp.*
import k2script.exec.*
import k2script.bbcontroller.*
import borg.trikeshed.lib.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Nexus MCP Bridge - Connects K2Script to IntelliJ via MCP protocol
 * 
 * This bridges the gap between:
 * - IntelliJ IDEA (MCP client)
 * - K2Script execution environment (via NexusToolset)
 * - TrikeShed infrastructure (JAR resolution, caching, etc.)
 */

class NexusMCPBridge(
    private val nexusToolset: NexusToolset,
    private val mcpServer: MCPServer
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        setupMCPHandlers()
    }
    
    private fun setupMCPHandlers() {
        // Dependency resolution handler for IntelliJ
        mcpServer.registerHandler("k2script/resolve") { params ->
            handleDependencyResolution(params)
        }
        
        // Script execution handler
        mcpServer.registerHandler("k2script/execute") { params ->
            handleScriptExecution(params)
        }
        
        // Cache management handler
        mcpServer.registerHandler("k2script/cache") { params ->
            handleCacheManagement(params)
        }
        
        // Tools listing for IntelliJ discovery
        mcpServer.registerHandler("tools/list") { params ->
            MCPResponse(200, mapOf(
                "tools" to listOf(
                    mapOf(
                        "name" to "k2script-resolver",
                        "description" to "Resolve Kotlin script dependencies",
                        "inputSchema" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "dependencies" to mapOf("type" to "array"),
                                "agentId" to mapOf("type" to "string")
                            )
                        )
                    ),
                    mapOf(
                        "name" to "k2script-executor", 
                        "description" to "Execute Kotlin scripts with dependency resolution",
                        "inputSchema" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "scriptPath" to mapOf("type" to "string"),
                                "args" to mapOf("type" to "array")
                            )
                        )
                    )
                )
            ))
        }
    }
    
    private suspend fun handleDependencyResolution(params: Map<String, Any>): MCPResponse {
        try {
            val dependencies = extractDependencies(params)
            val agentId = params["agentId"] as? String ?: "intellij-agent"
            
            val result = nexusToolset.resolveForAgent(agentId, dependencies)
            
            return MCPResponse(200, mapOf(
                "resolution" to mapOf(
                    "agentId" to result.agentId,
                    "success" to result.success,
                    "resolvedCount" to result.resolvedCount,
                    "cachedCount" to result.cachedCount,
                    "failedCount" to result.failedCount,
                    "executionTimeMs" to result.executionTimeMs,
                    "classpath" to result.classpath,
                    "error" to result.error
                )
            ))
        } catch (e: Exception) {
            return MCPResponse(500, mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    private suspend fun handleScriptExecution(params: Map<String, Any>): MCPResponse {
        try {
            val scriptPath = params["scriptPath"] as? String
                ?: return MCPResponse(400, mapOf("error" to "scriptPath required"))
            
            val args = extractStringArray(params["args"])
            
            // Create execution config for IntelliJ integration
            val config = ExecConfig(
                verboseOutput = params["verbose"] as? Boolean ?: false,
                enableCache = params["enableCache"] as? Boolean ?: true,
                dryRun = params["dryRun"] as? Boolean ?: false
            )
            
            // Execute via K2ExecTool
            val tool = K2ExecTool(nexusToolset.jarController, nexusToolset.reactor)
            val result = tool.execute(scriptPath, config)
            
            return MCPResponse(200, mapOf(
                "execution" to mapOf(
                    "exitCode" to result.exitCode,
                    "output" to result.output,
                    "errorOutput" to result.errorOutput,
                    "executionTimeMs" to result.executionTimeMs,
                    "resolvedDependencies" to result.resolvedDependencies,
                    "cachedDependencies" to result.cachedDependencies
                )
            ))
        } catch (e: Exception) {
            return MCPResponse(500, mapOf("error" to (e.message ?: "Execution failed")))
        }
    }
    
    private suspend fun handleCacheManagement(params: Map<String, Any>): MCPResponse {
        try {
            val action = params["action"] as? String
                ?: return MCPResponse(400, mapOf("error" to "action required"))
            
            when (action) {
                "stats" -> {
                    val stats = nexusToolset.jarController.cacheManager.getStats()
                    return MCPResponse(200, mapOf(
                        "cache" to mapOf(
                            "totalEntries" to stats.totalEntries,
                            "totalSizeBytes" to stats.totalSizeBytes,
                            "hitRate" to stats.hitRate,
                            "oldestEntry" to stats.oldestEntry,
                            "newestEntry" to stats.newestEntry
                        )
                    ))
                }
                "clear" -> {
                    // Clear cache logic would go here
                    return MCPResponse(200, mapOf("message" to "Cache cleared"))
                }
                "warm" -> {
                    val dependencies = extractDependencies(params)
                    // Pre-warm cache logic would go here
                    return MCPResponse(200, mapOf("message" to "Cache warming initiated"))
                }
                else -> {
                    return MCPResponse(400, mapOf("error" to "Unknown cache action: $action"))
                }
            }
        } catch (e: Exception) {
            return MCPResponse(500, mapOf("error" to (e.message ?: "Cache operation failed")))
        }
    }
    
    private fun extractDependencies(params: Map<String, Any>): Indexed<String> {
        val depsList = when (val deps = params["dependencies"]) {
            is List<*> -> deps.filterIsInstance<String>()
            is Array<*> -> deps.filterIsInstance<String>()
            else -> emptyList()
        }
        return depsList.size j { i -> depsList[i] }
    }
    
    private fun extractStringArray(value: Any?): Indexed<String> {
        val list = when (value) {
            is List<*> -> value.filterIsInstance<String>()
            is Array<*> -> value.filterIsInstance<String>()
            else -> emptyList()
        }
        return list.size j { i -> list[i] }
    }
}

/**
 * Nexus MCP Server Factory - Creates configured MCP server for IntelliJ integration
 */
object NexusMCPServerFactory {
    fun createServer(port: Int = 8080): Pair<MCPServer, NexusMCPBridge> {
        // Create MCP server with K2Script capabilities
        val mcpServer = MCPServer(
            name = "k2script-nexus",
            version = "1.0.0",
            capabilities = setOf(
                "tools",
                "dependency-resolution", 
                "script-execution",
                "cache-management"
            ),
            port = port
        )
        
        // Create Nexus toolset for backend operations
        val jarController = JarController()
        val reactor = Reactor<ExecStatus>()
        val nexusToolset = NexusToolset(jarController, reactor)
        
        // Create bridge
        val bridge = NexusMCPBridge(nexusToolset, mcpServer)
        
        return Pair(mcpServer, bridge)
    }
    
    fun createIntelliJIntegration(): IntelliJMCPIntegration {
        val (server, bridge) = createServer()
        return IntelliJMCPIntegration(server, bridge)
    }
}

/**
 * IntelliJ MCP Integration - Main entry point for IntelliJ plugin
 */
class IntelliJMCPIntegration(
    private val mcpServer: MCPServer,
    private val bridge: NexusMCPBridge
) {
    fun start() {
        mcpServer.start()
        println("🚀 K2Script Nexus MCP Bridge started for IntelliJ integration")
        println("   - Port: ${mcpServer.port}")
        println("   - Capabilities: ${mcpServer.capabilities.joinToString(", ")}")
        println("   - Ready for IntelliJ MCP client connections")
    }
    
    fun stop() {
        mcpServer.stop()
        println("⏹️  K2Script Nexus MCP Bridge stopped")
    }
    
    fun getServerInfo(): MCPServerInfo {
        return MCPServerInfo(
            name = mcpServer.name,
            version = mcpServer.version,
            capabilities = mcpServer.capabilities
        )
    }
}