package nexus.mcp

import kotlinx.coroutines.*
import borg.trikeshed.launcher.*

/**
 * Test the MCP system components
 */
object TestMcpSystem {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("🧪 Testing MCP System Components")
        println("================================")
        
        when (args.firstOrNull()) {
            "bridge" -> testIntelliJBridge()
            "server" -> testStratifiedServer()
            "naming" -> testNamingService()
            else -> {
                println("Usage: TestMcpSystem [bridge|server|naming]")
                println("")
                println("Available tests:")
                println("  bridge - Test IntelliJ CCEK Bridge")
                println("  server - Test Stratified MCP Server") 
                println("  naming - Test io_uring Naming Service")
            }
        }
    }
    
    private suspend fun testIntelliJBridge() {
        println("\n📋 Testing IntelliJ CCEK Bridge")
        println("-------------------------------")
        
        val bridge = IntelliJCcekBridge()
        
        try {
            // Initialize bridge (without native host)
            println("1. Initializing bridge...")
            bridge.initialize()
            println("   ✅ Bridge initialized")
            
            // Create test MCP server
            println("\n2. Creating test MCP server...")
            val testServer = bridge.createMcpServer(
                name = "test-server",
                port = 8888
            ) { request ->
                println("   📥 Received: ${request.method}")
                McpResponse(
                    id = request.id,
                    result = when (request.method) {
                        "ping" -> "pong"
                        "echo" -> request.params
                        else -> "unknown method"
                    },
                    error = null
                )
            }
            
            println("   ✅ Server created on port ${testServer.port}")
            
            // Simulate requests
            println("\n3. Testing request handling...")
            
            val pingRequest = McpRequest("1", "test-server", "ping", null)
            val pingResponse = testServer.handleRequest(pingRequest)
            println("   Ping: ${pingResponse.result}")
            
            val echoRequest = McpRequest("2", "test-server", "echo", "Hello MCP!")
            val echoResponse = testServer.handleRequest(echoRequest)
            println("   Echo: ${echoResponse.result}")
            
            // Test multiple servers
            println("\n4. Creating multiple servers...")
            val servers = listOf("auth", "data", "compute").map { name ->
                bridge.createMcpServer(name, 0) { request ->
                    McpResponse(request.id, "$name processed ${request.method}", null)
                }
            }
            
            println("   ✅ Created ${servers.size} servers")
            
            // Cleanup
            println("\n5. Cleanup...")
            bridge.shutdown()
            println("   ✅ Bridge shutdown")
            
        } catch (e: Exception) {
            println("   ❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private suspend fun testStratifiedServer() {
        println("\n📋 Testing Stratified MCP Server")
        println("--------------------------------")
        
        // Mock native host for testing
        val mockNativeHost = MockNativeHostProxy()
        
        try {
            println("1. Creating stratified server...")
            val server = TestableStratifiedServer(mockNativeHost)
            
            println("\n2. Initializing layers...")
            server.initializeWithMocks()
            
            println("\n3. Testing service registration...")
            val services = server.getRegisteredServices()
            services.forEach { (name, info) ->
                println("   - $name: ${info.allocation.cpuCores} cores, ${info.allocation.memoryMb}MB")
            }
            
            println("\n4. Simulating client requests...")
            
            // Simulate connection
            mockNativeHost.simulateConnection("client-1", "127.0.0.1", 12345)
            delay(100)
            
            // Simulate MCP request
            val testRequest = """{"id": "test-1", "serverName": "code-intelligence", "method": "analyze", "params": null}"""
            mockNativeHost.simulateData("client-1", testRequest.toByteArray())
            delay(100)
            
            // Check metrics
            println("\n5. Service metrics:")
            val metrics = server.getMetrics()
            metrics.forEach { (name, snapshot) ->
                println("   - $name: ${snapshot.requests} requests, ${snapshot.avgLatencyMs}ms avg")
            }
            
            // Shutdown
            println("\n6. Shutting down...")
            server.shutdown()
            println("   ✅ Server shutdown complete")
            
        } catch (e: Exception) {
            println("   ❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private suspend fun testNamingService() {
        println("\n📋 Testing io_uring Naming Service")
        println("----------------------------------")
        
        try {
            // Initialize naming service
            println("1. Initializing naming service...")
            System.setProperty("javax.naming.factory.initial", 
                "borg.trikeshed.launcher.UringNamingService")
            
            val env = java.util.Hashtable<String, String>()
            env["java.naming.provider.url"] = "uring://localhost"
            
            val ctx = javax.naming.InitialContext(env)
            println("   ✅ Context created")
            
            // Test operations
            println("\n2. Testing bind/lookup...")
            
            ctx.bind("test/service1", TestService("Service 1"))
            println("   ✅ Bound test/service1")
            
            val retrieved = ctx.lookup("test/service1") as TestService
            println("   ✅ Retrieved: ${retrieved.name}")
            
            // Test subcontext
            println("\n3. Testing subcontexts...")
            val subCtx = ctx.createSubcontext("test/sub")
            subCtx.bind("service2", TestService("Service 2"))
            println("   ✅ Created subcontext and bound service")
            
            // List bindings
            println("\n4. Listing bindings...")
            val bindings = ctx.listBindings("test")
            while (bindings.hasMore()) {
                val binding = bindings.next()
                println("   - ${binding.name}: ${binding.`object`}")
            }
            
            // Performance test
            println("\n5. Performance test...")
            val startTime = System.currentTimeMillis()
            val iterations = 1000
            
            repeat(iterations) { i ->
                ctx.bind("perf/item$i", "Value $i")
            }
            
            val bindTime = System.currentTimeMillis() - startTime
            println("   Bound $iterations items in ${bindTime}ms (${bindTime.toDouble() / iterations}ms per op)")
            
            val lookupStart = System.currentTimeMillis()
            repeat(iterations) { i ->
                ctx.lookup("perf/item$i")
            }
            
            val lookupTime = System.currentTimeMillis() - lookupStart
            println("   Looked up $iterations items in ${lookupTime}ms (${lookupTime.toDouble() / iterations}ms per op)")
            
            // Cleanup
            println("\n6. Cleanup...")
            ctx.close()
            println("   ✅ Context closed")
            
        } catch (e: Exception) {
            println("   ❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }
}

// Test helpers

data class TestService(val name: String) {
    override fun toString() = "TestService[$name]"
}

class MockNativeHostProxy : NativeHostProxy() {
    private val connections = mutableMapOf<String, ClientInfo>()
    
    fun simulateConnection(id: String, address: String, port: Int) {
        val clientInfo = ClientInfo(address, port)
        connections[id] = clientInfo
        
        runBlocking {
            events.emit(NativeEvent.ConnectionAccepted(id.hashCode().toLong(), clientInfo))
        }
    }
    
    fun simulateData(connectionId: String, data: ByteArray) {
        runBlocking {
            events.emit(NativeEvent.DataReceived(connectionId.hashCode().toLong(), data))
        }
    }
    
    override suspend fun sendResponse(connectionId: Long, response: McpResponse) {
        println("   📤 Mock sending response: ${response.result}")
    }
}

class TestableStratifiedServer(
    private val mockNativeHost: NativeHostProxy
) : StratifiedMcpHostingServer() {
    
    suspend fun initializeWithMocks() {
        // Override to use mock native host
        val field = StratifiedMcpHostingServer::class.java.getDeclaredField("nativeHost")
        field.isAccessible = true
        field.set(this, mockNativeHost)
        
        // Initialize other layers normally
        initializeJvmBridge()
        initializeOrchestration()
        initializeApplicationServices()
        
        // Start bridge
        launch { bridgeNativeToJvm() }
    }
    
    fun getRegisteredServices() = mcpServices.toMap()
}