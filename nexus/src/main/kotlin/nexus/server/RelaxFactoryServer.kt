package nexus.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import nexus.api.CouchDbApi
import nexus.bridge.IpfsBridge
import nexus.core.DefaultNexusAgent
import nexus.core.LogLevel
import nexus.core.TestIpfsPubSubService

/**
 * RelaxFactory Server - The main server implementation.
 * 
 * This server combines:
 * - QUIC server for HTTP/3 connections
 * - CouchDB-compatible API layer
 * - IPFS bridge for decentralized storage
 * - Nexus agent for distributed coordination
 * 
 * The server uses the generated DSL for configuration and demonstrates
 * the end-to-end vision from the v2reboot.md roadmap.
 */
class RelaxFactoryServer(
    private val agent: DefaultNexusAgent,
    private val scope: CoroutineScope,
    private val config: RelaxFactoryConfig
) {
    private val quicServer = QuicServer(agent, scope, config.quicConfig)
    private val ipfsBridge = IpfsBridge(agent, agent.ipfsPubSubService)
    private val couchDbApi = CouchDbApi(agent, ipfsBridge)
    
    private var isRunning = false
    
    /**
     * Starts the RelaxFactory server
     */
    suspend fun start() {
        if (isRunning) {
            throw IllegalStateException("Server is already running")
        }
        
        agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
            println("=== Starting RelaxFactory Server ===")
            println("Node ID: ${agent.nodeId}")
            println("Network: ${agent.networkId}")
            println("QUIC: ${config.quicConfig.host}:${config.quicConfig.port}")
            println("Capabilities: ${agent.capabilities.joinToString(", ")}")
            println()
        }
        
        try {
            // Start the Nexus agent
            agent.start()
            
            // Start the QUIC server
            quicServer.start()
            
            // Start request handling
            scope.launch {
                handleIncomingRequests()
            }
            
            // Start health monitoring
            if (agent.capabilities.contains(nexus.core.AgentCapability.TELEMETRY)) {
                scope.launch {
                    startHealthMonitoring()
                }
            }
            
            isRunning = true
            
            agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
                println("RelaxFactory server started successfully")
                println("CouchDB API available at: https://${config.quicConfig.host}:${config.quicConfig.port}")
                println("IPFS PubSub topics: ${agent.gossipTopics.joinToString(", ")}")
                println()
            }
            
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("Failed to start RelaxFactory server: ${e.message}")
            }
            throw e
        }
    }
    
    /**
     * Stops the RelaxFactory server
     */
    suspend fun stop() {
        if (!isRunning) {
            return
        }
        
        agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
            println("=== Stopping RelaxFactory Server ===")
        }
        
        try {
            // Stop the QUIC server
            quicServer.stop()
            
            // Stop the Nexus agent
            agent.stop()
            
            isRunning = false
            
            agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
                println("RelaxFactory server stopped successfully")
            }
            
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("Error stopping RelaxFactory server: ${e.message}")
            }
            throw e
        }
    }
    
    /**
     * Checks if the server is currently running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Handles incoming QUIC streams and routes them to the CouchDB API
     */
    private suspend fun handleIncomingRequests() {
        quicServer.connections
            .onEach { connection ->
                agent.logLevel.takeIf { it <= LogLevel.DEBUG }?.let {
                    println("New QUIC connection: ${connection.remoteAddress}")
                }
            }
            .collect { connection ->
                scope.launch {
                    connection.streams.collect { stream ->
                        if (stream.direction == StreamDirection.INBOUND) {
                            handleRequest(stream)
                        }
                    }
                }
            }
    }
    
    /**
     * Handles a single HTTP request
     */
    private suspend fun handleRequest(stream: QuicStream) {
        try {
            val response = couchDbApi.handleRequest(stream)
            
            // Send response back to client
            sendResponse(stream, response)
            
            agent.logLevel.takeIf { it <= LogLevel.DEBUG }?.let {
                println("Handled request: ${stream.id} -> ${response.status}")
            }
            
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("Error handling request ${stream.id}: ${e.message}")
            }
            
            // Send error response
            val errorResponse = CouchDbResponse(
                status = 500,
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"error":"Internal Server Error","reason":"${e.message}"}"""
            )
            sendResponse(stream, errorResponse)
        }
    }
    
    /**
     * Sends an HTTP response back to the client
     */
    private suspend fun sendResponse(stream: QuicStream, response: CouchDbResponse) {
        // In a real implementation, this would serialize the response
        // and send it back through the QUIC stream
        agent.logLevel.takeIf { it <= LogLevel.DEBUG }?.let {
            println("Sending response: ${response.status} ${response.headers["Content-Type"]}")
        }
    }
    
    /**
     * Starts health monitoring and telemetry
     */
    private suspend fun startHealthMonitoring() {
        while (isRunning) {
            try {
                // Publish health status
                val healthPayload = nexus.core._i(
                    "node_id" j agent.nodeId,
                    "status" j "healthy",
                    "timestamp" j System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString(),
                    "connections" j quicServer.connections.toString(),
                    "databases" j "0", // Would count actual databases
                    "documents" j "0"  // Would count actual documents
                )
                
                agent.gossipAbout("nexus/health", healthPayload)
                
                // Wait for next health check
                kotlinx.coroutines.delay(agent.heartbeatIntervalMs)
                
            } catch (e: Exception) {
                agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                    println("Health monitoring error: ${e.message}")
                }
            }
        }
    }
}

/**
 * Configuration for the RelaxFactory server
 */
@GenerateDsl
data class RelaxFactoryConfig(
    val quicConfig: QuicServerConfig = QuicServerConfig(),
    val enableHealthMonitoring: Boolean = true,
    val enableMetrics: Boolean = true,
    val enableLogging: Boolean = true,
    val maxRequestSize: Int = 10 * 1024 * 1024, // 10MB
    val requestTimeoutMs: Long = 30000,
    val enableCors: Boolean = true,
    val corsOrigins: List<String> = listOf("*"),
    val enableRateLimiting: Boolean = false,
    val rateLimitRequestsPerMinute: Int = 1000,
    val enableCompression: Boolean = true,
    val compressionLevel: Int = 6,
    val enableTls: Boolean = true,
    val certificatePath: String? = null,
    val privateKeyPath: String? = null,
    val enableAuthentication: Boolean = false,
    val authToken: String? = null,
    val enableAuditLogging: Boolean = false,
    val auditLogPath: String? = null,
)

/**
 * Main function demonstrating the end-to-end RelaxFactory server
 * using the generated DSL for configuration.
 */
suspend fun main() {
    println("=== RelaxFactory Server Demo ===")
    println()
    
    // Create a test IPFS service
    val testIpfsService = TestIpfsPubSubService()
    
    // Configure the server using the generated DSL
    val agent = defaultNexusAgent {
        ipfsPubSubService(testIpfsService)
        nodeId("relaxfactory-demo-001")
        networkId("nexus-demo")
        
        // Network settings
        heartbeatIntervalMs(15000)
        maxConcurrentTasks(20)
        logLevel(LogLevel.INFO)
        
        // Capabilities
        capability(nexus.core.AgentCapability.GOSSIP)
        capability(nexus.core.AgentCapability.TELEMETRY)
        capability(nexus.core.AgentCapability.TASK_EXECUTION)
        capability(nexus.core.AgentCapability.AI_REASONING)
        
        // Gossip topics
        gossipTopic("nexus/health")
        gossipTopic("nexus/document-index")
        gossipTopic("nexus/database-index")
        gossipTopic("nexus/telemetry")
        
        // Security
        enableEncryption(true)
        authToken("demo-token-123")
        
        // Monitoring
        enableMetrics(true)
        metricsIntervalMs(10000)
    }
    
    // Configure the server
    val serverConfig = relaxFactoryConfig {
        quicConfig {
            host("0.0.0.0")
            port(8080)
            maxConnections(1000)
            enableTls(true)
            alpnProtocols("h3", "h3-29")
        }
        enableHealthMonitoring(true)
        enableMetrics(true)
        enableLogging(true)
        maxRequestSize(10485760) // 10MB
        requestTimeoutMs(30000)
        enableCors(true)
        enableCompression(true)
        compressionLevel(6)
    }
    
    // Create and start the server
    val server = RelaxFactoryServer(
        agent = agent,
        scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        config = serverConfig
    )
    
    try {
        // Start the server
        server.start()
        
        println("Server is running. Press Ctrl+C to stop.")
        
        // Keep the server running
        kotlinx.coroutines.delay(Long.MAX_VALUE)
        
    } catch (e: Exception) {
        println("Server error: ${e.message}")
    } finally {
        // Stop the server
        server.stop()
        println("Server stopped.")
    }
} 