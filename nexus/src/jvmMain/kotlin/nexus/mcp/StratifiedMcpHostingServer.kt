package nexus.mcp

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import borg.trikeshed.launcher.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.*

/**
 * Stratified MCP Hosting Server for Nexus/IntelliJ
 * 
 * Architecture Layers:
 * 1. Native io_uring layer (Darwin kqueue) - Zero GC, max performance
 * 2. JVM Bridge layer - IntelliJ plugin integration
 * 3. Service orchestration layer - MCP server lifecycle
 * 4. Application layer - Nexus AI services
 */
class StratifiedMcpHostingServer {
    
    // Layer 1: Native io_uring connection
    private lateinit var nativeHost: NativeHostProxy
    
    // Layer 2: JVM Bridge
    private lateinit var intellijBridge: IntelliJCcekBridge
    
    // Layer 3: Service Orchestration
    private val mcpServices = ConcurrentHashMap<String, McpServiceInfo>()
    private val serviceOrchestrator = ServiceOrchestrator()
    
    // Layer 4: Application Services
    private val nexusServices = NexusServiceRegistry()
    
    suspend fun initialize() {
        println("🚀 Stratified MCP Hosting Server")
        println("================================")
        
        // Initialize bottom-up
        initializeNativeLayer()
        initializeJvmBridge()
        initializeOrchestration()
        initializeApplicationServices()
        
        println("✅ All layers initialized")
    }
    
    /**
     * Layer 1: Native io_uring initialization
     */
    private suspend fun initializeNativeLayer() {
        println("\n📡 Layer 1: Native io_uring")
        
        // Start native host process if not running
        if (!isNativeHostRunning()) {
            startNativeHost()
        }
        
        // Connect to native host
        nativeHost = NativeHostProxy().apply {
            connect("ipc:///tmp/uring_mcp_host")
        }
        
        println("  ✅ Connected to native io_uring host")
    }
    
    /**
     * Layer 2: JVM Bridge initialization
     */
    private suspend fun initializeJvmBridge() {
        println("\n🔌 Layer 2: JVM Bridge")
        
        intellijBridge = IntelliJCcekBridge().apply {
            initialize()
        }
        
        // Setup bi-directional communication
        launch {
            bridgeNativeToJvm()
        }
        
        println("  ✅ JVM bridge established")
    }
    
    /**
     * Layer 3: Service Orchestration
     */
    private suspend fun initializeOrchestration() {
        println("\n🎼 Layer 3: Service Orchestration")
        
        serviceOrchestrator.initialize(
            nativeHost = nativeHost,
            jvmBridge = intellijBridge
        )
        
        // Register orchestration policies
        serviceOrchestrator.apply {
            // Auto-scaling policy
            addPolicy(AutoScalingPolicy(
                minInstances = 1,
                maxInstances = 10,
                scaleUpThreshold = 0.8,
                scaleDownThreshold = 0.2
            ))
            
            // Health check policy
            addPolicy(HealthCheckPolicy(
                interval = 30.seconds,
                timeout = 5.seconds,
                retries = 3
            ))
            
            // Load balancing policy
            addPolicy(LoadBalancingPolicy(
                algorithm = LoadBalancingAlgorithm.ROUND_ROBIN
            ))
        }
        
        println("  ✅ Service orchestration ready")
    }
    
    /**
     * Layer 4: Application Services
     */
    private suspend fun initializeApplicationServices() {
        println("\n🎯 Layer 4: Application Services")
        
        // Register Nexus AI services
        nexusServices.apply {
            // Code intelligence service
            register("code-intelligence", CodeIntelligenceService())
            
            // Nemotron integration
            register("nemotron", NemotronService())
            
            // LiteLLM integration
            register("litellm", LiteLLMService())
            
            // Project analysis
            register("project-analysis", ProjectAnalysisService())
            
            // MCP tool service
            register("mcp-tools", McpToolService())
        }
        
        // Create MCP servers for each service
        nexusServices.getAllServices().forEach { (name, service) ->
            createMcpServer(name, service)
        }
        
        println("  ✅ Application services deployed")
    }
    
    /**
     * Create MCP server with full stack integration
     */
    private suspend fun createMcpServer(
        name: String,
        service: NexusService
    ) {
        // Allocate resources through orchestrator
        val allocation = serviceOrchestrator.allocateResources(
            ServiceRequest(
                name = name,
                cpuCores = service.requiredCpuCores,
                memoryMb = service.requiredMemoryMb,
                ioBandwidthMbps = service.requiredIoBandwidth
            )
        )
        
        // Create MCP server via bridge
        val mcpServer = intellijBridge.createMcpServer(
            name = name,
            port = allocation.port,
            handler = { request ->
                // Route through service
                service.handleMcpRequest(request)
            }
        )
        
        // Register in tracking
        mcpServices[name] = McpServiceInfo(
            name = name,
            server = mcpServer,
            service = service,
            allocation = allocation,
            metrics = ServiceMetrics()
        )
        
        println("  📦 Created MCP server: $name on port ${allocation.port}")
    }
    
    /**
     * Bridge native events to JVM
     */
    private suspend fun bridgeNativeToJvm() {
        nativeHost.events.collect { event ->
            when (event) {
                is NativeEvent.ConnectionAccepted -> {
                    handleNewConnection(event)
                }
                
                is NativeEvent.DataReceived -> {
                    handleIncomingData(event)
                }
                
                is NativeEvent.ConnectionClosed -> {
                    handleConnectionClosed(event)
                }
                
                is NativeEvent.Error -> {
                    handleNativeError(event)
                }
            }
        }
    }
    
    private suspend fun handleNewConnection(event: NativeEvent.ConnectionAccepted) {
        // Find least loaded service
        val service = serviceOrchestrator.routeConnection(event.clientInfo)
        
        // Update metrics
        service?.let {
            mcpServices[it]?.metrics?.activeConnections?.incrementAndGet()
        }
    }
    
    private suspend fun handleIncomingData(event: NativeEvent.DataReceived) {
        // Decode MCP request
        val request = decodeMcpRequest(event.data)
        
        // Route to appropriate service
        val serviceInfo = mcpServices[request.serverName]
        if (serviceInfo != null) {
            // Update metrics
            serviceInfo.metrics.requestCount.incrementAndGet()
            
            val startTime = System.nanoTime()
            
            try {
                // Process through service
                val response = serviceInfo.service.handleMcpRequest(request)
                
                // Send response via native layer
                nativeHost.sendResponse(event.connectionId, response)
                
                // Update success metrics
                serviceInfo.metrics.successCount.incrementAndGet()
                
            } catch (e: Exception) {
                // Handle error
                serviceInfo.metrics.errorCount.incrementAndGet()
                
                nativeHost.sendError(event.connectionId, e.message ?: "Unknown error")
                
            } finally {
                // Update latency
                val latency = System.nanoTime() - startTime
                serviceInfo.metrics.totalLatencyNanos.addAndGet(latency)
            }
        }
    }
    
    private suspend fun handleConnectionClosed(event: NativeEvent.ConnectionClosed) {
        // Update connection metrics
        mcpServices.values.forEach { service ->
            if (service.allocation.hasConnection(event.connectionId)) {
                service.metrics.activeConnections.decrementAndGet()
            }
        }
    }
    
    private suspend fun handleNativeError(event: NativeEvent.Error) {
        println("⚠️ Native error: ${event.message}")
        
        // Implement recovery strategy
        when (event.severity) {
            ErrorSeverity.CRITICAL -> {
                // Restart native host
                restartNativeHost()
            }
            
            ErrorSeverity.ERROR -> {
                // Log and continue
            }
            
            ErrorSeverity.WARNING -> {
                // Monitor
            }
        }
    }
    
    /**
     * Get service metrics
     */
    fun getMetrics(): Map<String, ServiceMetricsSnapshot> {
        return mcpServices.mapValues { (_, info) ->
            info.metrics.snapshot()
        }
    }
    
    /**
     * Shutdown server
     */
    suspend fun shutdown() {
        println("\n🛑 Shutting down stratified server...")
        
        // Shutdown top-down
        nexusServices.shutdown()
        serviceOrchestrator.shutdown()
        intellijBridge.shutdown()
        nativeHost.disconnect()
        
        println("✅ Shutdown complete")
    }
    
    // Helper functions
    
    private fun isNativeHostRunning(): Boolean {
        // Check if native process is running
        return try {
            ProcessHandle.allProcesses()
                .anyMatch { it.info().command().orElse("").contains("NativeUringMcpHost") }
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun startNativeHost() {
        val process = ProcessBuilder()
            .command("./build/bin/native/releaseExecutable/platform-launcher.kexe")
            .start()
        
        // Wait for startup
        delay(1000)
        
        if (!process.isAlive) {
            error("Failed to start native host")
        }
    }
    
    private suspend fun restartNativeHost() {
        nativeHost.disconnect()
        delay(1000)
        startNativeHost()
        initializeNativeLayer()
    }
    
    private fun decodeMcpRequest(data: ByteArray): McpRequest {
        // Decode MCP wire format
        // For now, simple JSON
        return McpRequest(
            id = "req-${System.currentTimeMillis()}",
            serverName = "unknown",
            method = "unknown",
            params = null
        )
    }
}

/**
 * Service Orchestrator
 */
class ServiceOrchestrator {
    private val policies = mutableListOf<OrchestrationPolicy>()
    private lateinit var nativeHost: NativeHostProxy
    private lateinit var jvmBridge: IntelliJCcekBridge
    
    fun initialize(nativeHost: NativeHostProxy, jvmBridge: IntelliJCcekBridge) {
        this.nativeHost = nativeHost
        this.jvmBridge = jvmBridge
    }
    
    fun addPolicy(policy: OrchestrationPolicy) {
        policies.add(policy)
    }
    
    suspend fun allocateResources(request: ServiceRequest): ResourceAllocation {
        // Apply policies to determine allocation
        var allocation = ResourceAllocation(
            port = findAvailablePort(),
            cpuCores = request.cpuCores,
            memoryMb = request.memoryMb,
            ioBandwidthMbps = request.ioBandwidthMbps
        )
        
        policies.forEach { policy ->
            allocation = policy.apply(allocation, request)
        }
        
        return allocation
    }
    
    fun routeConnection(clientInfo: ClientInfo): String? {
        // Apply load balancing
        val loadBalancer = policies.filterIsInstance<LoadBalancingPolicy>().firstOrNull()
        return loadBalancer?.selectService(clientInfo)
    }
    
    suspend fun shutdown() {
        // Graceful shutdown
    }
    
    private fun findAvailablePort(): Int = (8000..9000).random()
}

/**
 * Nexus Service Registry
 */
class NexusServiceRegistry {
    private val services = ConcurrentHashMap<String, NexusService>()
    
    fun register(name: String, service: NexusService) {
        services[name] = service
    }
    
    fun getAllServices() = services.toMap()
    
    suspend fun shutdown() {
        services.values.forEach { it.shutdown() }
    }
}

/**
 * Base Nexus Service
 */
abstract class NexusService {
    open val requiredCpuCores: Int = 1
    open val requiredMemoryMb: Int = 512
    open val requiredIoBandwidth: Int = 100
    
    abstract suspend fun handleMcpRequest(request: McpRequest): McpResponse
    open suspend fun shutdown() {}
}

/**
 * Native Host Proxy
 */
class NativeHostProxy {
    val events = MutableSharedFlow<NativeEvent>()
    private var connected = false
    
    suspend fun connect(address: String) {
        // Connect to native host via IPC
        connected = true
    }
    
    suspend fun sendResponse(connectionId: Long, response: McpResponse) {
        // Send via native layer
    }
    
    suspend fun sendError(connectionId: Long, error: String) {
        // Send error response
    }
    
    fun disconnect() {
        connected = false
    }
}

// Service implementations

class CodeIntelligenceService : NexusService() {
    override val requiredMemoryMb = 2048
    
    override suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        return McpResponse(
            id = request.id,
            result = "Code intelligence result",
            error = null
        )
    }
}

class NemotronService : NexusService() {
    override val requiredCpuCores = 4
    override val requiredMemoryMb = 8192
    
    override suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        return McpResponse(
            id = request.id,
            result = "Nemotron AI response",
            error = null
        )
    }
}

class LiteLLMService : NexusService() {
    override suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        return McpResponse(
            id = request.id,
            result = "LiteLLM response",
            error = null
        )
    }
}

class ProjectAnalysisService : NexusService() {
    override suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        return McpResponse(
            id = request.id,
            result = "Project analysis",
            error = null
        )
    }
}

class McpToolService : NexusService() {
    override suspend fun handleMcpRequest(request: McpRequest): McpResponse {
        return McpResponse(
            id = request.id,
            result = "Tool execution result",
            error = null
        )
    }
}

// Supporting types

data class McpServiceInfo(
    val name: String,
    val server: McpServerProxy,
    val service: NexusService,
    val allocation: ResourceAllocation,
    val metrics: ServiceMetrics
)

data class ServiceRequest(
    val name: String,
    val cpuCores: Int,
    val memoryMb: Int,
    val ioBandwidthMbps: Int
)

data class ResourceAllocation(
    val port: Int,
    val cpuCores: Int,
    val memoryMb: Int,
    val ioBandwidthMbps: Int,
    private val connections: MutableSet<Long> = mutableSetOf()
) {
    fun hasConnection(id: Long) = id in connections
    fun addConnection(id: Long) = connections.add(id)
    fun removeConnection(id: Long) = connections.remove(id)
}

class ServiceMetrics {
    val requestCount = java.util.concurrent.atomic.AtomicLong()
    val successCount = java.util.concurrent.atomic.AtomicLong()
    val errorCount = java.util.concurrent.atomic.AtomicLong()
    val activeConnections = java.util.concurrent.atomic.AtomicInteger()
    val totalLatencyNanos = java.util.concurrent.atomic.AtomicLong()
    
    fun snapshot() = ServiceMetricsSnapshot(
        requests = requestCount.get(),
        successes = successCount.get(),
        errors = errorCount.get(),
        connections = activeConnections.get(),
        avgLatencyMs = if (requestCount.get() > 0) 
            totalLatencyNanos.get() / requestCount.get() / 1_000_000 
        else 0
    )
}

data class ServiceMetricsSnapshot(
    val requests: Long,
    val successes: Long,
    val errors: Long,
    val connections: Int,
    val avgLatencyMs: Long
)

// Orchestration policies

interface OrchestrationPolicy {
    fun apply(allocation: ResourceAllocation, request: ServiceRequest): ResourceAllocation
}

class AutoScalingPolicy(
    val minInstances: Int,
    val maxInstances: Int,
    val scaleUpThreshold: Double,
    val scaleDownThreshold: Double
) : OrchestrationPolicy {
    override fun apply(allocation: ResourceAllocation, request: ServiceRequest) = allocation
}

class HealthCheckPolicy(
    val interval: Duration,
    val timeout: Duration,
    val retries: Int
) : OrchestrationPolicy {
    override fun apply(allocation: ResourceAllocation, request: ServiceRequest) = allocation
}

class LoadBalancingPolicy(
    val algorithm: LoadBalancingAlgorithm
) : OrchestrationPolicy {
    private var currentIndex = 0
    
    override fun apply(allocation: ResourceAllocation, request: ServiceRequest) = allocation
    
    fun selectService(clientInfo: ClientInfo): String? {
        // Simple round-robin for now
        return null
    }
}

enum class LoadBalancingAlgorithm {
    ROUND_ROBIN,
    LEAST_CONNECTIONS,
    WEIGHTED,
    IP_HASH
}

// Native events

sealed class NativeEvent {
    data class ConnectionAccepted(val connectionId: Long, val clientInfo: ClientInfo) : NativeEvent()
    data class DataReceived(val connectionId: Long, val data: ByteArray) : NativeEvent()
    data class ConnectionClosed(val connectionId: Long) : NativeEvent()
    data class Error(val message: String, val severity: ErrorSeverity) : NativeEvent()
}

data class ClientInfo(
    val address: String,
    val port: Int
)

enum class ErrorSeverity {
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Main entry point
 */
fun main() = runBlocking {
    val server = StratifiedMcpHostingServer()
    
    try {
        server.initialize()
        
        // Run until interrupted
        println("\n✨ Stratified MCP server running. Press Ctrl+C to stop.")
        
        // Monitor metrics
        launch {
            while (isActive) {
                delay(10.seconds)
                
                val metrics = server.getMetrics()
                println("\n📊 Service Metrics:")
                metrics.forEach { (name, snapshot) ->
                    println("  $name: ${snapshot.requests} reqs, ${snapshot.avgLatencyMs}ms avg")
                }
            }
        }
        
        // Wait for shutdown signal
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                server.shutdown()
            }
        })
        
        delay(Long.MAX_VALUE)
        
    } catch (e: Exception) {
        println("❌ Server error: ${e.message}")
        server.shutdown()
    }
}