@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * IPFS Coroutine Context Element Keys (CCEK)
 * Following the TrikeShed pattern for service containers
 */

// === IPFS Service Context Elements ===

/**
 * IPFS Client context element - provides IPFS client operations
 */
data class IpfsClientContext(
    val client: IpfsClient
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsClientContext>
}

/**
 * IPFS Server context element - provides IPFS server operations
 */
data class IpfsServerContext(
    val server: IpfsServer
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsServerContext>
}

/**
 * DHT Service context element - provides DHT operations
 */
data class DHTServiceContext(
    val dht: DHTService
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<DHTServiceContext>
}

/**
 * IPFS Storage context element - provides storage operations
 */
data class IpfsStorageContext(
    val storage: IpfsStorage
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsStorageContext>
}

/**
 * IPFS PubSub context element - provides pubsub operations
 */
data class IpfsPubSubContext(
    val pubsub: IpfsPubSubService
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsPubSubContext>
}

/**
 * IPFS Configuration context element - provides configuration
 */
data class IpfsConfigContext(
    val config: IpfsServerConfig
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsConfigContext>
}

/**
 * IPFS Peer context element - provides peer information
 */
data class IpfsPeerContext(
    val peerId: PeerId,
    val addresses: Indexed<String>,
    val protocols: Indexed<String>
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsPeerContext>
}

/**
 * IPFS Content context element - provides content routing information
 */
data class IpfsContentContext(
    val cid: CID,
    val providers: Indexed<PeerInfo>,
    val isPinned: Boolean = false
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsContentContext>
}

/**
 * IPFS Network context element - provides network statistics
 */
data class IpfsNetworkContext(
    val stats: IpfsServerStats,
    val peers: Indexed<PeerInfo>
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IpfsNetworkContext>
}

// === IPFS Service Locator ===

/**
 * IPFS Service Locator - provides IPFS services through coroutine context
 */
object IpfsServiceLocator {
    
    /**
     * Get IPFS client from context
     */
    suspend fun getClient(): IpfsClient {
        val context = coroutineContext[IpfsClientContext]
        return context?.client ?: throw IllegalStateException("IpfsClientContext not found in coroutine context")
    }
    
    /**
     * Get IPFS server from context
     */
    suspend fun getServer(): IpfsServer {
        val context = coroutineContext[IpfsServerContext]
        return context?.server ?: throw IllegalStateException("IpfsServerContext not found in coroutine context")
    }
    
    /**
     * Get DHT service from context
     */
    suspend fun getDHT(): DHTService {
        val context = coroutineContext[DHTServiceContext]
        return context?.dht ?: throw IllegalStateException("DHTServiceContext not found in coroutine context")
    }
    
    /**
     * Get IPFS storage from context
     */
    suspend fun getStorage(): IpfsStorage {
        val context = coroutineContext[IpfsStorageContext]
        return context?.storage ?: throw IllegalStateException("IpfsStorageContext not found in coroutine context")
    }
    
    /**
     * Get IPFS pubsub from context
     */
    suspend fun getPubSub(): IpfsPubSubService {
        val context = coroutineContext[IpfsPubSubContext]
        return context?.pubsub ?: throw IllegalStateException("IpfsPubSubContext not found in coroutine context")
    }
    
    /**
     * Get IPFS configuration from context
     */
    suspend fun getConfig(): IpfsServerConfig {
        val context = coroutineContext[IpfsConfigContext]
        return context?.config ?: throw IllegalStateException("IpfsConfigContext not found in coroutine context")
    }
    
    /**
     * Get current peer from context
     */
    suspend fun getPeer(): IpfsPeerContext? {
        return coroutineContext[IpfsPeerContext]
    }
    
    /**
     * Get current content from context
     */
    suspend fun getContent(): IpfsContentContext? {
        return coroutineContext[IpfsContentContext]
    }
    
    /**
     * Get network information from context
     */
    suspend fun getNetwork(): IpfsNetworkContext? {
        return coroutineContext[IpfsNetworkContext]
    }
}

// === IPFS Context DSL ===

/**
 * DSL for creating IPFS context loadouts
 */
class IpfsContextBuilder {
    internal var client: IpfsClient? = null
    internal var server: IpfsServer? = null
    internal var dht: DHTService? = null
    internal var storage: IpfsStorage? = null
    internal var pubsub: IpfsPubSubService? = null
    internal var config: IpfsServerConfig? = null
    internal var peer: IpfsPeerContext? = null
    internal var content: IpfsContentContext? = null
    internal var network: IpfsNetworkContext? = null
    
    fun client(client: IpfsClient) = apply { this.client = client }
    fun server(server: IpfsServer) = apply { this.server = server }
    fun dht(dht: DHTService) = apply { this.dht = dht }
    fun storage(storage: IpfsStorage) = apply { this.storage = storage }
    fun pubsub(pubsub: IpfsPubSubService) = apply { this.pubsub = pubsub }
    fun config(config: IpfsServerConfig) = apply { this.config = config }
    fun peer(peer: IpfsPeerContext) = apply { this.peer = peer }
    fun content(content: IpfsContentContext) = apply { this.content = content }
    fun network(network: IpfsNetworkContext) = apply { this.network = network }
    
    fun build(): CoroutineContext {
        val elements = mutableListOf<CoroutineContext.Element>()
        
        client?.let { elements.add(IpfsClientContext(it)) }
        server?.let { elements.add(IpfsServerContext(it)) }
        dht?.let { elements.add(DHTServiceContext(it)) }
        storage?.let { elements.add(IpfsStorageContext(it)) }
        pubsub?.let { elements.add(IpfsPubSubContext(it)) }
        config?.let { elements.add(IpfsConfigContext(it)) }
        peer?.let { elements.add(it) }
        content?.let { elements.add(it) }
        network?.let { elements.add(it) }
        
        return elements.fold(EmptyCoroutineContext) { acc, element -> acc + element }
    }
}

/**
 * DSL function for building IPFS contexts
 */
fun ipfsContext(block: IpfsContextBuilder.() -> Unit): CoroutineContext {
    return IpfsContextBuilder().apply(block).build()
}

// === IPFS Context Extensions ===

/**
 * Extension functions for easy context access
 */
val CoroutineContext.ipfsClient: IpfsClient?
    get() = this[IpfsClientContext]?.client

val CoroutineContext.ipfsServer: IpfsServer?
    get() = this[IpfsServerContext]?.server

val CoroutineContext.dhtService: DHTService?
    get() = this[DHTServiceContext]?.dht

val CoroutineContext.ipfsStorage: IpfsStorage?
    get() = this[IpfsStorageContext]?.storage

val CoroutineContext.ipfsPubSub: IpfsPubSubService?
    get() = this[IpfsPubSubContext]?.pubsub

val CoroutineContext.ipfsConfig: IpfsServerConfig?
    get() = this[IpfsConfigContext]?.config

val CoroutineContext.ipfsPeer: IpfsPeerContext?
    get() = this[IpfsPeerContext]

val CoroutineContext.ipfsContent: IpfsContentContext?
    get() = this[IpfsContentContext]

val CoroutineContext.ipfsNetwork: IpfsNetworkContext?
    get() = this[IpfsNetworkContext]

// === IPFS Context Scoped Operations ===

/**
 * Execute operations with IPFS client context
 */
suspend fun <T> withIpfsClient(
    client: IpfsClient,
    block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    withContext(IpfsClientContext(client)) {
        block()
    }
}

/**
 * Execute operations with IPFS server context
 */
suspend fun <T> withIpfsServer(
    server: IpfsServer,
    block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    withContext(IpfsServerContext(server)) {
        block()
    }
}

/**
 * Execute operations with DHT service context
 */
suspend fun <T> withDHTService(
    dht: DHTService,
    block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    withContext(DHTServiceContext(dht)) {
        block()
    }
}

/**
 * Execute operations with IPFS storage context
 */
suspend fun <T> withIpfsStorage(
    storage: IpfsStorage,
    block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    withContext(IpfsStorageContext(storage)) {
        block()
    }
}

/**
 * Execute operations with IPFS pubsub context
 */
suspend fun <T> withIpfsPubSub(
    pubsub: IpfsPubSubService,
    block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    withContext(IpfsPubSubContext(pubsub)) {
        block()
    }
}

/**
 * Execute operations with complete IPFS context
 */
suspend fun <T> withIpfsContext(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = coroutineScope {
    withContext(context) {
        block()
    }
} 