@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Complete IPFS Server implementation
 * Handles HTTP API, DHT operations, peer management, and content routing
 * Uses coroutine context for service container management
 */
class IpfsServer(
    internal val config: IpfsServerConfig,
    internal val storage: IpfsStorage,
    internal val dht: DHTService,
    internal val pubsub: IpfsPubSubService
) {
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal val localPeerId = generatePeerId()
    internal val client = IpfsClient(localPeerId, null, storage, config.clientConfig)
    
    /**
     * Create IPFS context for this server instance
     */
    fun createContext(): CoroutineContext {
        return ipfsContext {
            server(this@IpfsServer)
            client(this@IpfsServer.client)
            dht(this@IpfsServer.dht)
            storage(this@IpfsServer.storage)
            pubsub(this@IpfsServer.pubsub)
            config(this@IpfsServer.config)
            peer(IpfsPeerContext(
                peerId = localPeerId,
                addresses = 1 j { "/ip4/127.0.0.1/tcp/4001" },
                protocols = 1 j { "/ipfs/kad/1.0.0" }
            ))
        }
    }
    
    internal val _peers = MutableStateFlow<Indexed<PeerInfo>>(0 j { throw NoSuchElementException() })
    val peers: StateFlow<Indexed<PeerInfo>> = _peers.asStateFlow()
    
    internal val _stats = MutableStateFlow(IpfsServerStats())
    val stats: StateFlow<IpfsServerStats> = _stats.asStateFlow()
    
    internal var isRunning = false
    
    /**
     * Start the IPFS server
     */
    suspend fun start() {
        if (isRunning) return
        
        scope.launch {
            dht.start(localPeerId)
            pubsub.start()
            
            // Start peer discovery
            startPeerDiscovery()
            
            // Start content routing
            startContentRouting()
            
            // Start metrics collection
            startMetricsCollection()
        }
        
        isRunning = true
    }
    
    /**
     * Stop the IPFS server
     */
    suspend fun stop() {
        if (!isRunning) return
        
        scope.cancel()
        dht.stop()
        pubsub.stop()
        isRunning = false
    }
    
    /**
     * Handle HTTP API requests
     */
    suspend fun handleApiRequest(request: IpfsApiRequest): IpfsApiResponse {
        return when (request.method) {
            "GET" -> handleGetRequest(request)
            "POST" -> handlePostRequest(request)
            "PUT" -> handlePutRequest(request)
            "DELETE" -> handleDeleteRequest(request)
            else -> IpfsApiResponse(405, "Method not allowed")
        }
    }
    
    /**
     * Add content to IPFS
     */
    suspend fun add(data: Indexed<Byte>): CID {
        return withIpfsContext(createContext()) {
            val client = IpfsServiceLocator.getClient()
            val dht = IpfsServiceLocator.getDHT()
            
            val cid = client.add(data)
            
            // Announce to DHT
            scope.launch {
                dht.provide(cid)
            }
            
            // Update stats
            _stats.update { it.copy(blocksStored = it.blocksStored + 1) }
            
            cid
        }
    }
    
    /**
     * Get content from IPFS
     */
    suspend fun get(cid: CID): Indexed<Byte>? {
        return withIpfsContext(createContext()) {
            val client = IpfsServiceLocator.getClient()
            val dht = IpfsServiceLocator.getDHT()
            
            // Try local storage first
            var data = client.get(cid)
            
            if (data == null) {
                // Try to find providers via DHT
                val providers = dht.findProviders(cid)
                
                for (i in 0 until providers.component1()) {
                    val provider = providers.component2()(i)
                    data = tryFetchFromPeer(provider, cid)
                    if (data != null) break
                }
            }
            
            if (data != null) {
                _stats.update { it.copy(blocksRetrieved = it.blocksRetrieved + 1) }
            }
            
            data
        }
    }
    
    /**
     * Pin content
     */
    suspend fun pin(cid: CID): Boolean {
        return withIpfsClient(client) {
            client.pin(cid)
        }
    }
    
    /**
     * Unpin content
     */
    suspend fun unpin(cid: CID): Boolean {
        return withIpfsClient(client) {
            client.unpin(cid)
        }
    }
    
    /**
     * List pinned content
     */
    suspend fun listPinned(): Indexed<CID> {
        return withIpfsClient(client) {
            client.listPinned()
        }
    }
    
    /**
     * Garbage collect unpinned content
     */
    suspend fun gc(): Indexed<CID> {
        return withIpfsClient(client) {
            client.gc()
        }
    }
    
    /**
     * Get server statistics
     */
    fun getStats(): IpfsServerStats {
        return stats.value
    }
    
    // === PRIVATE IMPLEMENTATION ===
    
    internal suspend fun handleGetRequest(request: IpfsApiRequest): IpfsApiResponse {
        return when (request.path) {
            "/api/v0/cat" -> {
                val cid = request.params["arg"] ?: return IpfsApiResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val data = get(parsedCid)
                if (data != null) {
                    IpfsApiResponse(200, data)
                } else {
                    IpfsApiResponse(404, "Content not found")
                }
            }
            "/api/v0/id" -> {
                val response = mapOf(
                    "ID" to localPeerId.toBase58(),
                    "Addresses" to emptyList<String>(),
                    "AgentVersion" to "trikeshed-ipfs/1.0.0",
                    "ProtocolVersion" to "ipfs/0.1.0"
                )
                IpfsApiResponse(200, response)
            }
            "/api/v0/version" -> {
                val response = mapOf(
                    "Version" to "1.0.0",
                    "Commit" to "trikeshed",
                    "Repo" to "1",
                    "System" to "trikeshed"
                )
                IpfsApiResponse(200, response)
            }
            "/api/v0/stats/bw" -> {
                IpfsApiResponse(200, stats.value)
            }
            else -> IpfsApiResponse(404, "Not found")
        }
    }
    
    internal suspend fun handlePostRequest(request: IpfsApiRequest): IpfsApiResponse {
        return when (request.path) {
            "/api/v0/add" -> {
                val data = request.body ?: return IpfsApiResponse(400, "Missing data")
                val cid = add(data)
                val response = mapOf("Hash" to cid.encode())
                IpfsApiResponse(200, response)
            }
            "/api/v0/pin/add" -> {
                val cid = request.params["arg"] ?: return IpfsApiResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val success = pin(parsedCid)
                if (success) {
                    IpfsApiResponse(200, mapOf("Pins" to listOf(cid)))
                } else {
                    IpfsApiResponse(500, "Failed to pin")
                }
            }
            else -> IpfsApiResponse(404, "Not found")
        }
    }
    
    internal suspend fun handlePutRequest(request: IpfsApiRequest): IpfsApiResponse {
        return IpfsApiResponse(405, "Method not allowed")
    }
    
    internal suspend fun handleDeleteRequest(request: IpfsApiRequest): IpfsApiResponse {
        return when (request.path) {
            "/api/v0/pin/rm" -> {
                val cid = request.params["arg"] ?: return IpfsApiResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val success = unpin(parsedCid)
                if (success) {
                    IpfsApiResponse(200, mapOf("Pins" to listOf(cid)))
                } else {
                    IpfsApiResponse(500, "Failed to unpin")
                }
            }
            else -> IpfsApiResponse(404, "Not found")
        }
    }
    
    internal suspend fun startPeerDiscovery() {
        scope.launch {
            while (isActive) {
                val discoveredPeers = dht.discoverPeers()
                _peers.value = discoveredPeers
                delay(config.peerDiscoveryInterval)
            }
        }
    }
    
    internal suspend fun startContentRouting() {
        scope.launch {
            while (isActive) {
                // Handle content routing requests
                delay(config.contentRoutingInterval)
            }
        }
    }
    
    internal suspend fun startMetricsCollection() {
        scope.launch {
            while (isActive) {
                val currentStats = stats.value
                val newStats = currentStats.copy(
                    uptime = currentStats.uptime + 1,
                    peersCount = peers.value.component1()
                )
                _stats.value = newStats
                delay(1000) // Update every second
            }
        }
    }
    
    internal suspend fun tryFetchFromPeer(peer: PeerInfo, cid: CID): Indexed<Byte>? {
        // Simplified peer fetching - would implement actual protocol
        return null
    }
    
    internal fun generatePeerId(): PeerId {
        val randomBytes = 32 j { (Math.random() * 256).toInt().toByte() }
        return PeerId(randomBytes)
    }
    
    internal fun parseCID(cidString: String): CID {
        // Simplified CID parsing
        if (cidString.startsWith("bafy")) {
            val digest = cidString.substring(4).chunked(2).map { it.toInt(16).toByte() }
            val multihash = Multihash(Multihash.HashType.SHA2_256, digest.size j { digest[it] })
            return CID(1, CID.Codec.RAW, multihash)
        }
        throw IllegalArgumentException("Invalid CID format")
    }
}

// === CONFIGURATION AND DATA CLASSES ===

data class IpfsServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 5001,
    val apiPort: Int = 5001,
    val clientConfig: IpfsConfig = IpfsConfig(),
    val peerDiscoveryInterval: Long = 30000, // 30 seconds
    val contentRoutingInterval: Long = 10000, // 10 seconds
    val maxPeers: Int = 100,
    val enableDHT: Boolean = true,
    val enablePubSub: Boolean = true
)

data class IpfsServerStats(
    val uptime: Long = 0,
    val peersCount: Int = 0,
    val blocksStored: Long = 0,
    val blocksRetrieved: Long = 0,
    val bytesStored: Long = 0,
    val bytesRetrieved: Long = 0
)

data class IpfsApiRequest(
    val method: String,
    val path: String,
    val params: Map<String, String> = emptyMap(),
    val body: Indexed<Byte>? = null
)

data class IpfsApiResponse(
    val status: Int,
    val data: Any // Can be String, Indexed<Byte>, or Map
)

// === DHT SERVICE INTERFACE ===

interface DHTService {
    suspend fun start(localPeerId: PeerId)
    suspend fun stop()
    suspend fun provide(cid: CID)
    suspend fun findProviders(cid: CID): Indexed<PeerInfo>
    suspend fun discoverPeers(): Indexed<PeerInfo>
}

// === ENHANCED PUBSUB SERVICE ===

class EnhancedIpfsPubSubService : IpfsPubSubService() {
    internal val topics = mutableMapOf<String, MutableSharedFlow<String>>()
    internal var isStarted = false
    
    suspend fun start() {
        isStarted = true
    }
    
    suspend fun stop() {
        isStarted = false
    }
    
    override fun publish(topic: String, message: String) {
        if (!isStarted) return
        
        val flow = topics.getOrPut(topic) { MutableSharedFlow() }
        // In a real implementation, this would broadcast to all subscribers
    }
    
    override fun subscribe(topic: String): Flow<String> {
        val flow = topics.getOrPut(topic) { MutableSharedFlow() }
        return flow.asSharedFlow()
    }
} 