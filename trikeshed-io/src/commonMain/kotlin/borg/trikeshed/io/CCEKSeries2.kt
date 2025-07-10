@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.*
import kotlinx.datetime.*
// Platform-specific concurrent collections required

/**
 * CCEK Series 2: Service Integration
 * 
 * Provides service discovery, registration, and integration capabilities
 * for distributed systems and microservices architectures.
 */

// === SERVICE REGISTRY ===

/**
 * Service registry for discovery and integration
 */
class ServiceRegistry {
    internal val services = mutableMapOf<String, ServiceInfo>()
    internal val serviceChannels = mutableMapOf<String, SendChannel<CCekMessage>>()
    internal val serviceFlows = mutableMapOf<String, MutableSharedFlow<CCekMessage>>()
    
    /**
     * Register a service
     */
    suspend fun registerService(
        serviceId: String,
        serviceInfo: ServiceInfo,
        messageChannel: SendChannel<CCekMessage>? = null
    ) {
        services[serviceId] = serviceInfo
        messageChannel?.let { serviceChannels[serviceId] = it }
        
        // Create flow for this service
        val flow = MutableSharedFlow<CCekMessage>(replay = 0)
        serviceFlows[serviceId] = flow
        
        println("Service registered: $serviceId at ${serviceInfo.endpoint}")
    }
    
    /**
     * Unregister a service
     */
    suspend fun unregisterService(serviceId: String) {
        services.remove(serviceId)
        serviceChannels.remove(serviceId)?.close()
        serviceFlows.remove(serviceId)
        
        println("Service unregistered: $serviceId")
    }
    
    /**
     * Get service information
     */
    fun getService(serviceId: String): ServiceInfo? {
        return services[serviceId]
    }
    
    /**
     * List all services
     */
    fun listServices(): List<ServiceInfo> {
        return services.values.toList()
    }
    
    /**
     * Find services by type
     */
    fun findServicesByType(type: String): List<ServiceInfo> {
        return services.values.filter { it.type == type }
    }
    
    /**
     * Find services by tag
     */
    fun findServicesByTag(tag: String): List<ServiceInfo> {
        return services.values.filter { it.tags.contains(tag) }
    }
    
    /**
     * Send message to service
     */
    suspend fun sendToService(serviceId: String, message: CCekMessage): Boolean {
        val channel = serviceChannels[serviceId]
        return if (channel != null && !channel.isClosedForSend) {
            try {
                channel.send(message)
                true
            } catch (e: Exception) {
                false
            }
        } else false
    }
    
    /**
     * Get service message flow
     */
    fun getServiceFlow(serviceId: String): Flow<CCekMessage>? {
        return serviceFlows[serviceId]?.asSharedFlow()
    }
    
    /**
     * Broadcast message to all services
     */
    suspend fun broadcastToAll(message: CCekMessage): List<String> {
        val successful = mutableListOf<String>()
        
        for ((serviceId, channel) in serviceChannels) {
            if (!channel.isClosedForSend) {
                try {
                    channel.send(message)
                    successful.add(serviceId)
                } catch (e: Exception) {
                    // Service failed to receive message
                }
            }
        }
        
        return successful
    }
}

/**
 * Service information
 */
data class ServiceInfo(
    val serviceId: String,
    val name: String,
    val type: String,
    val endpoint: String,
    val version: String = "1.0.0",
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val health: ServiceHealth = ServiceHealth.HEALTHY,
    val lastSeen: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Service health status
 */
enum class ServiceHealth {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}

// === SERVICE INTEGRATION LAYER ===

/**
 * Service integration layer for protocol stack
 */
class ServiceIntegrationLayer(
    internal val registry: ServiceRegistry
) : ProtocolLayer {
    override val layerId = "service-integration"
    override val priority = 75
    
    override suspend fun processIncoming(message: CCekMessage): CCekMessage? {
        // Route message to appropriate service
        val targetService = registry.getService(message.target)
        return if (targetService != null) {
            // Service exists, forward message
            val success = registry.sendToService(message.target, message)
            if (success) message else null
        } else {
            // Service not found, try to discover it
            discoverAndRoute(message)
        }
    }
    
    override suspend fun processOutgoing(message: CCekMessage): CCekMessage? {
        // For outgoing messages, ensure service is available
        val targetService = registry.getService(message.target)
        return if (targetService != null && targetService.health == ServiceHealth.HEALTHY) {
            message
        } else {
            // Service unavailable, drop message or retry
            null
        }
    }
    
    override fun canHandle(message: CCekMessage): Boolean = true
    
    internal suspend fun discoverAndRoute(message: CCekMessage): CCekMessage? {
        // Try to discover service by name or type
        val discoveredServices = registry.findServicesByType(message.target) +
                               registry.findServicesByTag(message.target)
        
        return if (discoveredServices.isNotEmpty()) {
            // Route to first available service
            val targetService = discoveredServices.first()
            val routedMessage = message.copy(target = targetService.serviceId)
            val success = registry.sendToService(targetService.serviceId, routedMessage)
            if (success) routedMessage else null
        } else null
    }
}

// === SERVICE DISCOVERY ===

/**
 * Service discovery interface
 */
interface ServiceDiscovery {
    suspend fun discoverServices(query: ServiceQuery): List<ServiceInfo>
    suspend fun watchServices(query: ServiceQuery): Flow<List<ServiceInfo>>
}

/**
 * Service query for discovery
 */
data class ServiceQuery(
    val type: String? = null,
    val tags: Set<String> = emptySet(),
    val version: String? = null,
    val health: ServiceHealth? = null
)

/**
 * Local service discovery implementation
 */
class LocalServiceDiscovery(
    internal val registry: ServiceRegistry
) : ServiceDiscovery {
    
    override suspend fun discoverServices(query: ServiceQuery): List<ServiceInfo> {
        var services = registry.listServices()
        
        // Filter by type
        query.type?.let { type ->
            services = services.filter { it.type == type }
        }
        
        // Filter by tags
        if (query.tags.isNotEmpty()) {
            services = services.filter { service ->
                query.tags.any { tag -> service.tags.contains(tag) }
            }
        }
        
        // Filter by version
        query.version?.let { version ->
            services = services.filter { it.version == version }
        }
        
        // Filter by health
        query.health?.let { health ->
            services = services.filter { it.health == health }
        }
        
        return services
    }
    
    override suspend fun watchServices(query: ServiceQuery): Flow<List<ServiceInfo>> {
        return flow {
            // Emit initial results
            emit(discoverServices(query))
            
            // Watch for changes (simplified - in production use proper change detection)
            while (true) {
                delay(5000) // Check every 5 seconds
                emit(discoverServices(query))
            }
        }
    }
}

// === SERVICE HEALTH MONITORING ===

/**
 * Service health monitor
 */
class ServiceHealthMonitor(
    internal val registry: ServiceRegistry
) {
    internal val scope = CoroutineScope(Dispatchers.Default)
    
    fun startMonitoring() {
        scope.launch {
            while (true) {
                checkServiceHealth()
                delay(30000) // Check every 30 seconds
            }
        }
    }
    
    internal suspend fun checkServiceHealth() {
        val services = registry.listServices()
        
        for (service in services) {
            val health = checkServiceHealth(service)
            if (health != service.health) {
                // Update service health
                val updatedService = service.copy(
                    health = health,
                    lastSeen = Clock.System.now().toEpochMilliseconds()
                )
                // Note: In a real implementation, you'd update the registry
                println("Service ${service.serviceId} health changed: ${service.health} -> $health")
            }
        }
    }
    
    internal suspend fun checkServiceHealth(service: ServiceInfo): ServiceHealth {
        return try {
            // Simple health check - in production use proper health check endpoints
            val message = CCekMessage(
                id = Clock.System.now().toEpochMilliseconds(),
                source = "health-monitor",
                target = service.serviceId,
                data = "health-check".encodeToByteArray(),
                compressedSize = 0,
                originalSize = 0,
                compressionRatio = 1.0,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                algorithm = CompressionAlgorithm.NONE
            )
            
            val success = registry.sendToService(service.serviceId, message)
            if (success) ServiceHealth.HEALTHY else ServiceHealth.UNHEALTHY
        } catch (e: Exception) {
            ServiceHealth.UNHEALTHY
        }
    }
}

// === SERVICE LOAD BALANCER ===

/**
 * Service load balancer
 */
class ServiceLoadBalancer(
    internal val registry: ServiceRegistry,
    internal val strategy: LoadBalancingStrategy = LoadBalancingStrategy.ROUND_ROBIN
) {
    internal val serviceIndex = mutableMapOf<String, Int>()
    
    suspend fun selectService(serviceType: String): String? {
        val services = registry.findServicesByType(serviceType)
            .filter { it.health == ServiceHealth.HEALTHY }
        
        return when (strategy) {
            LoadBalancingStrategy.ROUND_ROBIN -> selectRoundRobin(services)
            LoadBalancingStrategy.RANDOM -> selectRandom(services)
            LoadBalancingStrategy.LEAST_CONNECTIONS -> selectLeastConnections(services)
        }
    }
    
    internal fun selectRoundRobin(services: List<ServiceInfo>): String? {
        if (services.isEmpty()) return null
        
        val index = serviceIndex.getOrPut("round-robin") { 0 }
        val selectedService = services[index % services.size]
        serviceIndex["round-robin"] = (index + 1) % services.size
        
        return selectedService.serviceId
    }
    
    internal fun selectRandom(services: List<ServiceInfo>): String? {
        return services.randomOrNull()?.serviceId
    }
    
    internal fun selectLeastConnections(services: List<ServiceInfo>): String? {
        // Simplified - in production track actual connection counts
        return services.firstOrNull()?.serviceId
    }
}

/**
 * Load balancing strategies
 */
enum class LoadBalancingStrategy {
    ROUND_ROBIN,
    RANDOM,
    LEAST_CONNECTIONS
}

// === SERVICE INTEGRATION FACTORY ===

/**
 * Factory for creating service integration components
 */
object ServiceIntegrationFactory {
    
    /**
     * Create a complete service integration stack
     */
    fun createServiceIntegrationStack(
        registry: ServiceRegistry,
        enableHealthMonitoring: Boolean = true,
        enableLoadBalancing: Boolean = true
    ): ProtocolStack {
        val stack = ProtocolStack()
            .addLayer(ServiceIntegrationLayer(registry))
        
        if (enableHealthMonitoring) {
            val healthMonitor = ServiceHealthMonitor(registry)
            healthMonitor.startMonitoring()
        }
        
        return stack
    }
    
    /**
     * Create a service registry with health monitoring
     */
    fun createServiceRegistryWithMonitoring(): ServiceRegistry {
        val registry = ServiceRegistry()
        val healthMonitor = ServiceHealthMonitor(registry)
        healthMonitor.startMonitoring()
        return registry
    }
} 