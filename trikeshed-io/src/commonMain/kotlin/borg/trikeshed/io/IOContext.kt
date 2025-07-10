@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import borg.trikeshed.lib.Indexed

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

// Taxonomical type hierarchy for IO capabilities
sealed class IOCapability {
    // TCP capabilities
    sealed class TCP : IOCapability() {
        object NonBlocking : TCP()
        object NoDelay : TCP()
        object KeepAlive : TCP()
        object ReuseAddr : TCP()
        object FastOpen : TCP()
    }
    
    // UDP capabilities
    sealed class UDP : IOCapability() {
        object Datagram : UDP()
        object Multicast : UDP()
        object Broadcast : UDP()
        object Connected : UDP()
        object Unconnected : UDP()
        object GSO : UDP()  // Generic Segmentation Offload
        object GRO : UDP()  // Generic Receive Offload
    }
    
    // Kernel capabilities
    sealed class Kernel : IOCapability() {
        object ZeroCopy : Kernel()
        object BatchSubmission : Kernel()
        object KernelPolling : Kernel()
        object EdgeTriggered : Kernel()
        object OneShot : Kernel()
        object Exclusive : Kernel()
        object MultiShotAccept : Kernel()
        object FixedBuffers : Kernel()
        object LinkedOperations : Kernel()
        object RecvMMsg : Kernel()
        object SendMMsg : Kernel()
    }
    
    // Channel capabilities
    sealed class Channel : IOCapability() {
        object Multiplexing : Channel()
        object ScatterGather : Channel()
        object ChannelTransfer : Channel()
        object DirectBuffers : Channel()
    }
}

typealias ContextId = String
typealias ServiceId = String
typealias CapabilitySet = Indexed<IOCapability>

/**
 * Normalized IO Context system for Trikeshed
 * 
 * One context can enable multiple services through capability-based sharing
 * Examples:
 * - UringContext can serve both TorrentKettle and RemoteFileAttention
 * - NioContext can handle HTTP/2, WebSockets, and BitTorrent simultaneously
 */
sealed class IOContext {
    abstract val id: ContextId
    abstract val capabilities: CapabilitySet
    abstract val coroutineContext: CoroutineContext
    abstract val scope: CoroutineScope
    
    /**
     * Platform-specific IO contexts with normalized interfaces
     */
    data class UringContext(
        override val id: ContextId,
        val ringSize: Int = 256,
        val sqPollThread: Boolean = true,
        val ioPollCpu: Int? = null
    ) : IOContext() {
        internal val capabilityArray: Array<IOCapability> = arrayOf(
            IOCapability.Kernel.ZeroCopy,
            IOCapability.Kernel.BatchSubmission,
            IOCapability.Kernel.KernelPolling,
            IOCapability.Kernel.MultiShotAccept,
            IOCapability.Kernel.FixedBuffers,
            IOCapability.Kernel.LinkedOperations,
            IOCapability.UDP.GSO,
            IOCapability.UDP.GRO,
            IOCapability.Kernel.RecvMMsg,
            IOCapability.Kernel.SendMMsg,
            IOCapability.TCP.NonBlocking,
            IOCapability.UDP.Datagram,
            IOCapability.UDP.Multicast
        )
        
        override val capabilities: CapabilitySet = 
            capabilityArray.size j { i: Int -> capabilityArray[i] }
        
        override val coroutineContext: CoroutineContext = 
            /* Dispatchers.IO + */ CoroutineName("uring-$id") // TODO: Platform-specific dispatcher
            
        override val scope: CoroutineScope = CoroutineScope(coroutineContext)
    }
    
    data class NioContext(
        override val id: ContextId,
        val selectorThreads: Int = 2,
        val directBuffers: Boolean = true,
        val tcpNoDelay: Boolean = true
    ) : IOContext() {
        internal val capabilityArray: Array<IOCapability> = arrayOf(
            IOCapability.TCP.NonBlocking,
            IOCapability.Channel.Multiplexing,
            IOCapability.Channel.ScatterGather,
            IOCapability.Channel.ChannelTransfer,
            IOCapability.UDP.Datagram,
            IOCapability.UDP.Multicast,
            IOCapability.UDP.Connected,
            IOCapability.UDP.Unconnected,
            IOCapability.Channel.DirectBuffers
        )
        
        override val capabilities: CapabilitySet = 
            capabilityArray.size j { i: Int -> capabilityArray[i] }
        
        override val coroutineContext: CoroutineContext = 
            /* Dispatchers.IO + */ CoroutineName("nio-$id") // TODO: Platform-specific dispatcher
            
        override val scope: CoroutineScope = CoroutineScope(coroutineContext)
    }
    
    data class KqueueContext(
        override val id: ContextId,
        val maxEvents: Int = 1024,
        val edgeTriggered: Boolean = true
    ) : IOContext() {
        internal val capabilityArray: Array<IOCapability> = arrayOf(
            IOCapability.Kernel.EdgeTriggered,
            IOCapability.Kernel.BatchSubmission,
            IOCapability.UDP.Datagram,
            IOCapability.UDP.Multicast,
            IOCapability.TCP.NonBlocking
        )
        
        override val capabilities: CapabilitySet = 
            capabilityArray.size j { i: Int -> capabilityArray[i] }
        
        override val coroutineContext: CoroutineContext = 
            /* Dispatchers.IO + */ CoroutineName("kqueue-$id") // TODO: Platform-specific dispatcher
            
        override val scope: CoroutineScope = CoroutineScope(coroutineContext)
    }
    
    data class EpollContext(
        override val id: ContextId,
        val maxEvents: Int = 1024,
        val edgeTriggered: Boolean = true,
        val oneShot: Boolean = false
    ) : IOContext() {
        internal val capabilityArray: Array<IOCapability> = arrayOf(
            IOCapability.Kernel.EdgeTriggered,
            IOCapability.Kernel.OneShot,
            IOCapability.Kernel.Exclusive,
            IOCapability.UDP.Datagram,
            IOCapability.UDP.Multicast,
            IOCapability.Kernel.RecvMMsg,
            IOCapability.Kernel.SendMMsg
        )
        
        override val capabilities: CapabilitySet = 
            capabilityArray.size j { i: Int -> capabilityArray[i] }
        
        override val coroutineContext: CoroutineContext = 
            /* Dispatchers.IO + */ CoroutineName("epoll-$id") // TODO: Platform-specific dispatcher
            
        override val scope: CoroutineScope = CoroutineScope(coroutineContext)
    }
}

/**
 * Service registration for context sharing
 */
data class ServiceRegistration(
    val serviceId: ServiceId,
    val requiredCapabilities: CapabilitySet,
    val preferredContext: IOContext? = null
)

/**
 * IO Context Manager - Manages context sharing across services
 */
class IOContextManager {
    internal val contexts: MutableMap<ContextId, IOContext> = mutableMapOf()
    internal val services: MutableMap<ServiceId, ServiceRegistration> = mutableMapOf()
    internal val assignments: MutableMap<ServiceId, ContextId> = mutableMapOf()
    
    /**
     * Register a service with its capability requirements
     */
    fun registerService(
        serviceId: ServiceId,
        requiredCapabilities: CapabilitySet,
        preferredContext: IOContext? = null
    ) {
        services[serviceId] = ServiceRegistration(
            serviceId, 
            requiredCapabilities, 
            preferredContext
        )
        
        // Try to assign to existing context or create new one
        val context = preferredContext ?: findCompatibleContext(requiredCapabilities)
        assignServiceToContext(serviceId, context)
    }
    
    /**
     * Find or create a context that satisfies capability requirements
     */
    internal fun findCompatibleContext(required: CapabilitySet): IOContext {
        // Check existing contexts
        for ((_, context) in contexts) {
            if (hasAllCapabilities(context.capabilities, required)) {
                return context
            }
        }
        
        // Create new context based on requirements
        return createOptimalContext(required)
    }
    
    /**
     * Check if context has all required capabilities
     */
    internal fun hasAllCapabilities(
        available: CapabilitySet, 
        required: CapabilitySet
    ): Boolean {
        val availableSet: Set<IOCapability> = (0 until available.a.toInt()).map { 
            available.b(it) 
        }.toSet()
        
        for (i in 0 until required.a.toInt()) {
            if (!availableSet.contains(required.b(i))) {
                return false
            }
        }
        return true
    }
    
    /**
     * Create optimal context based on capability requirements
     */
    internal fun createOptimalContext(required: CapabilitySet): IOContext {
        val requiredSet: Set<IOCapability> = (0 until required.a.toInt()).map { 
            required.b(it) 
        }.toSet()
        
        return when {
            requiredSet.contains(IOCapability.Kernel.ZeroCopy) || 
            requiredSet.contains(IOCapability.Kernel.LinkedOperations) -> {
                IOContext.UringContext(
                    id = "uring-${0L}", // TODO: Platform-specific nanoTime
                    ringSize = 512
                )
            }
            requiredSet.contains(IOCapability.Kernel.EdgeTriggered) && isMacOS() -> {
                IOContext.KqueueContext(
                    id = "kqueue-${0L}" // TODO: Platform-specific nanoTime
                )
            }
            requiredSet.contains(IOCapability.Kernel.EdgeTriggered) && isLinux() -> {
                IOContext.EpollContext(
                    id = "epoll-${0L}" // TODO: Platform-specific nanoTime
                )
            }
            else -> {
                IOContext.NioContext(
                    id = "nio-${0L}" // TODO: Platform-specific nanoTime
                )
            }
        }
    }
    
    /**
     * Assign service to context
     */
    internal fun assignServiceToContext(serviceId: ServiceId, context: IOContext) {
        contexts[context.id] = context
        assignments[serviceId] = context.id
    }
    
    /**
     * Get context for a service
     */
    fun getContext(serviceId: ServiceId): IOContext? {
        val contextId = assignments[serviceId] ?: return null
        return contexts[contextId]
    }
    
    /**
     * Share context between services if compatible
     */
    fun shareContext(service1: ServiceId, service2: ServiceId): Boolean {
        val context1 = getContext(service1) ?: return false
        val registration2 = services[service2] ?: return false
        
        if (hasAllCapabilities(context1.capabilities, registration2.requiredCapabilities)) {
            assignments[service2] = context1.id
            return true
        }
        return false
    }
    
    internal fun isLinux(): Boolean = 
        borg.trikeshed.lib.platform.PlatformDetection.getPlatformInfo().os == borg.trikeshed.lib.platform.OperatingSystem.LINUX
        
    internal fun isMacOS(): Boolean = 
        borg.trikeshed.lib.platform.PlatformDetection.getPlatformInfo().os == borg.trikeshed.lib.platform.OperatingSystem.MACOS
}

/**
 * Global IO context manager instance
 */
val ioContextManager = IOContextManager()