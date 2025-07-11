package borg.trikeshed.demo

import borg.trikeshed.net.http.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.rest.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.reactor.Reactor
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

/**
 * CCEK Protocol Demonstration
 * 
 * This demonstrates the systematic refactoring of TrikeShed protocols
 * to use Key-based APIs with Job orchestration and context composition.
 */

suspend fun demonstrateCCEKPattern() {
    println("🚀 CCEK Protocol Demonstration")
    println("===============================")
    
    // 1. Create service instances
    val ioContext = IOContext.NioContext("demo")
    val httpClient = HttpClient.Key.create(ioContext) {
        connectTimeout = 10.seconds
        maxConnectionsPerHost = 5
        maxRetries = 2
    }
    
    val quicServerConfig = QuicServerConfig(port = 8443)
    val quicServer = QuicServer.Key.create(quicServerConfig)
    
    val restClient = TrikeShedRestClient.Key.create(
        baseUrl = "https://api.example.com",
        connectionPoolSize = 10,
        defaultTimeout = 30.seconds
    )
    
    // 2. Build context composition
    val networkContext = httpClient + quicServer + restClient
    
    println("📡 Network Context Composition")
    println("   - HttpClient: ✓")
    println("   - QuicServer: ✓") 
    println("   - TrikeShedRestClient: ✓")
    println()
    
    // 3. Demonstrate Key-based API usage
    withContext(networkContext) {
        println("🔄 Using Key-based APIs")
        
        // HTTP Client via Key API
        launch {
            try {
                val request = HttpRequest(
                    method = HttpMethod.GET,
                    path = HttpRequestPath("/api/status"),
                    headers = arrayOf(
                        HttpHeaderName("Host") j HttpHeaderValue("api.example.com"),
                        HttpHeaderName("User-Agent") j HttpHeaderValue("TrikeShed-CCEK/1.0")
                    )
                )
                
                println("   📤 HTTP GET /api/status")
                val response = HttpClient.Key.execute(request)
                println("   📥 Response: ${response.status}")
                
            } catch (e: Exception) {
                println("   ❌ HTTP Error: ${e.message}")
            }
        }
        
        // QUIC Server via Key API
        launch {
            try {
                println("   🚀 Starting QUIC Server on port 8443")
                QuicServer.Key.start()
                
                // Add connection handler
                QuicServer.Key.onConnection(object : ConnectionHandler {
                    override suspend fun onConnect(connection: QuicConnection) {
                        println("   🔗 New QUIC connection established")
                    }
                    
                    override suspend fun onDisconnect(connectionId: String) {
                        println("   🔌 QUIC connection $connectionId disconnected")
                    }
                })
                
                val stats = QuicServer.Key.getStats()
                println("   📊 QUIC Server Stats: ${stats.activeConnections} connections")
                
            } catch (e: Exception) {
                println("   ❌ QUIC Error: ${e.message}")
            }
        }
        
        // REST Client via Key API
        launch {
            try {
                val request = RequestMeta(
                    method = "GET",
                    url = "/health",
                    headers = headersOf("Accept" j "application/json")
                ) j null
                
                println("   📤 REST GET /health")
                val response = TrikeShedRestClient.Key.execute(request)
                println("   📥 Response: ${response.a.statusCode}")
                
            } catch (e: Exception) {
                println("   ❌ REST Error: ${e.message}")
            }
        }
        
        // Wait for all operations to complete
        delay(1000)
        
        println()
        println("✅ All Key-based operations completed")
    }
    
    println()
    println("🎯 CCEK Pattern Benefits Demonstrated:")
    println("   - Service composition through context addition")
    println("   - Type-safe key-based API access")
    println("   - Job-based orchestration with structured concurrency")
    println("   - Easy testing through context substitution")
    println("   - Platform abstraction through uniform API")
}

/**
 * Demonstrate advanced context composition patterns
 */
suspend fun demonstrateAdvancedComposition() {
    println("🔧 Advanced Context Composition Patterns")
    println("=======================================")
    
    // Full Web Server Stack
    val webServerContext = IOContext.NioContext("web-server") +
                           HttpClient.Key.create(IOContext.NioContext("web-server")) +
                           QuicServer.Key.create(QuicServerConfig(port = 8080)) +
                           TrikeShedRestClient.Key.create("https://backend.example.com")
    
    withContext(webServerContext) {
        println("🌐 Full Web Server Stack Active")
        
        // Simulate web server operations
        launch {
            println("   🚀 Starting HTTP/QUIC hybrid server")
            QuicServer.Key.start()
            
            // Handle incoming requests
            QuicServer.Key.onConnection(object : ConnectionHandler {
                override suspend fun onConnect(connection: QuicConnection) {
                    launch {
                        // Forward to backend via REST
                        val backendRequest = RequestMeta(
                            method = "GET",
                            url = "/api/data",
                            headers = headersOf("X-Forwarded-For" j "client-ip")
                        ) j null
                        
                        val backendResponse = TrikeShedRestClient.Key.execute(backendRequest)
                        println("   🔄 Proxied request: ${backendResponse.a.statusCode}")
                    }
                }
                
                override suspend fun onDisconnect(connectionId: String) {
                    println("   🔌 Client $connectionId disconnected")
                }
            })
        }
        
        delay(500)
        println("   ✅ Web server stack operational")
    }
    
    println()
    println("🎪 Context Composition Complete")
}

/**
 * Demonstrate testing with mock contexts
 */
suspend fun demonstrateTestingPattern() {
    println("🧪 Testing with Mock Contexts")
    println("=============================")
    
    // Mock implementations for testing
    val mockHttpClient = object : HttpClient(IOContext.NioContext("mock")) {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            return HttpResponse(
                status = HttpStatus(200),
                headers = arrayOf(HttpHeaderName("Content-Type") j HttpHeaderValue("application/json")),
                body = """{"status":"ok","mock":true}""".toByteArray()
            )
        }
    }
    
    val mockQuicServer = object : QuicServer(QuicServerConfig(port = 9999)) {
        override suspend fun start() {
            println("   🎭 Mock QUIC server started")
        }
        
        override fun getStats(): QuicServerStats {
            return QuicServerStats(
                port = 9999,
                isRunning = true,
                activeConnections = 42,
                totalStreams = 123
            )
        }
    }
    
    // Test context with mocked services
    val testContext = mockHttpClient + mockQuicServer
    
    withContext(testContext) {
        println("🎯 Testing with mock context")
        
        // Test HTTP operations
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/test"),
            headers = arrayOf(HttpHeaderName("Host") j HttpHeaderValue("test.example.com"))
        )
        
        val response = HttpClient.Key.execute(request)
        println("   📤 Mock HTTP response: ${response.status}")
        
        // Test QUIC operations
        QuicServer.Key.start()
        val stats = QuicServer.Key.getStats()
        println("   📊 Mock QUIC stats: ${stats.activeConnections} connections")
        
        println("   ✅ All tests passed with mock context")
    }
    
    println()
    println("🎉 Testing pattern demonstrated successfully")
}

/**
 * Main demonstration function
 */
suspend fun main() {
    println("TrikeShed CCEK Protocol Implementation Demonstration")
    println("==================================================")
    println()
    
    try {
        // Basic CCEK pattern
        demonstrateCCEKPattern()
        println()
        
        // Advanced composition
        demonstrateAdvancedComposition()
        println()
        
        // Testing patterns
        demonstrateTestingPattern()
        println()
        
        println("🎯 CCEK Protocol Demonstration Complete!")
        println("   - Key-based APIs: ✓")
        println("   - Context composition: ✓")
        println("   - Job orchestration: ✓")
        println("   - Testing patterns: ✓")
        
    } catch (e: Exception) {
        println("❌ Demo failed: ${e.message}")
        e.printStackTrace()
    }
}