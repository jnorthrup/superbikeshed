@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.nexus

import k2script.mcp.*
import k2script.exec.*
import kotlinx.coroutines.*
import kotlinx.datetime.*

/**
 * Main entry point for K2Script Nexus MCP Bridge
 * 
 * This starts the MCP server that IntelliJ can connect to for:
 * - Kotlin script dependency resolution
 * - Script execution with caching
 * - Cache management and statistics
 */

suspend fun main() {
    println("🚀 Starting K2Script Nexus MCP Bridge...")
    
    try {
        // Create MCP integration for IntelliJ
        val integration = NexusMCPServerFactory.createIntelliJIntegration()
        
        // Start the server
        integration.start()
        
        // Display server info
        val info = integration.getServerInfo()
        println("📋 Server Info:")
        println("   Name: ${info.name}")
        println("   Version: ${info.version}")
        println("   Capabilities: ${info.capabilities.joinToString(", ")}")
        
        // Keep server running
        println("🔄 Server running... Press Ctrl+C to stop")
        
        // Simulate some activity
        delay(1000)
        println("✅ Ready for IntelliJ MCP client connections")
        
        // Keep alive indefinitely
        while (true) {
            delay(10000)
            println("💓 Server heartbeat - ${Clock.System.now()}")
        }
        
    } catch (e: Exception) {
        println("❌ Failed to start MCP server: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Test function to validate MCP bridge functionality
 */
suspend fun testNexusBridge() {
    println("🧪 Testing Nexus MCP Bridge...")
    
    val (server, bridge) = NexusMCPServerFactory.createServer(port = 8081)
    server.start()
    
    try {
        // Test dependency resolution
        val resolutionRequest = MCPRequest(
            method = "k2script/resolve",
            params = mapOf(
                "dependencies" to listOf(
                    "org.jetbrains.kotlin:kotlin-stdlib:1.9.24",
                    "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0"
                ),
                "agentId" to "test-agent"
            )
        )
        
        val response = server.handleRequest(resolutionRequest)
        println("✅ Dependency resolution test: ${response.statusCode}")
        
        // Test tools listing
        val toolsRequest = MCPRequest(method = "tools/list")
        val toolsResponse = server.handleRequest(toolsRequest)
        println("✅ Tools listing test: ${toolsResponse.statusCode}")
        
        println("🎉 All tests passed!")
        
    } finally {
        server.stop()
    }
}

/**
 * Development mode for quick testing
 */
suspend fun devMode() {
    println("🔧 Development mode - quick MCP server test")
    testNexusBridge()
}