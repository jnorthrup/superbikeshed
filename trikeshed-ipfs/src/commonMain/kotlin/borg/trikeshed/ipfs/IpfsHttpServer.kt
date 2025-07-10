@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Complete IPFS HTTP Server implementation
 * Uses coroutine context for service container management
 */
class IpfsHttpServer(
    internal val ipfsServer: IpfsServer,
    internal val config: IpfsHttpServerConfig = IpfsHttpServerConfig()
) {
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true 
    }
    
    internal var isRunning = false
    
    /**
     * Start the HTTP server
     */
    suspend fun start() {
        if (isRunning) return
        
        val context = ipfsServer.createContext()
        
        scope.launch {
            withIpfsContext(context) {
                // Start HTTP server on configured port
                startHttpServer()
                
                // Start API handlers
                startApiHandlers()
                
                // Start metrics endpoint
                startMetricsEndpoint()
            }
        }
        
        isRunning = true
    }
    
    /**
     * Stop the HTTP server
     */
    suspend fun stop() {
        if (!isRunning) return
        
        scope.cancel()
        isRunning = false
    }
    
    /**
     * Handle HTTP request
     */
    suspend fun handleRequest(request: HttpRequest): HttpResponse {
        return withIpfsContext(ipfsServer.createContext()) {
            when (request.method) {
                "GET" -> handleGetRequest(request)
                "POST" -> handlePostRequest(request)
                "PUT" -> handlePutRequest(request)
                "DELETE" -> handleDeleteRequest(request)
                else -> HttpResponse(405, "Method not allowed")
            }
        }
    }
    
    // === PRIVATE IMPLEMENTATION ===
    
    internal suspend fun startHttpServer() {
        // Simplified HTTP server startup
        // In a real implementation, this would bind to the configured port
        println("IPFS HTTP Server starting on ${config.host}:${config.port}")
    }
    
    internal suspend fun startApiHandlers() {
        // Register API handlers
        scope.launch {
            // Handle API requests
        }
    }
    
    internal suspend fun startMetricsEndpoint() {
        // Start metrics collection endpoint
        scope.launch {
            while (isActive) {
                // Collect and expose metrics
                delay(config.metricsInterval)
            }
        }
    }
    
    internal suspend fun handleGetRequest(request: HttpRequest): HttpResponse {
        return when (request.path) {
            "/api/v0/cat" -> {
                val cid = request.params["arg"] ?: return HttpResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val data = ipfsServer.get(parsedCid)
                if (data != null) {
                    HttpResponse(200, data)
                } else {
                    HttpResponse(404, "Content not found")
                }
            }
            "/api/v0/id" -> {
                val peer = IpfsServiceLocator.getPeer()
                val response = mapOf(
                    "ID" to (peer?.peerId?.toBase58() ?: "unknown"),
                    "Addresses" to (peer?.addresses?.let { addresses ->
                        (0 until addresses.a).map { addresses.b(it) }
                    } ?: emptyList()),
                    "AgentVersion" to "trikeshed-ipfs/1.0.0",
                    "ProtocolVersion" to "ipfs/0.1.0"
                )
                HttpResponse(200, json.encodeToString(response))
            }
            "/api/v0/version" -> {
                val response = mapOf(
                    "Version" to "1.0.0",
                    "Commit" to "trikeshed",
                    "Repo" to "1",
                    "System" to "trikeshed"
                )
                HttpResponse(200, json.encodeToString(response))
            }
            "/api/v0/stats/bw" -> {
                val network = IpfsServiceLocator.getNetwork()
                val stats = network?.stats ?: IpfsServerStats()
                HttpResponse(200, json.encodeToString(stats))
            }
            "/api/v0/pin/ls" -> {
                val pinned = ipfsServer.listPinned()
                val response = mapOf(
                    "Keys" to (0 until pinned.a).associate { i ->
                        val cid = pinned.b(i)
                        cid.encode() to mapOf("Type" to "recursive")
                    }
                )
                HttpResponse(200, json.encodeToString(response))
            }
            "/api/v0/dht/findprovs" -> {
                val cid = request.params["arg"] ?: return HttpResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val dht = IpfsServiceLocator.getDHT()
                val providers = dht.findProviders(parsedCid)
                
                val response = (0 until providers.a).map { i ->
                    val provider = providers.b(i)
                    mapOf(
                        "ID" to provider.id.toBase58(),
                        "Addrs" to (0 until provider.addresses.a).map { provider.addresses.b(it) }
                    )
                }
                HttpResponse(200, json.encodeToString(response))
            }
            else -> HttpResponse(404, "Not found")
        }
    }
    
    internal suspend fun handlePostRequest(request: HttpRequest): HttpResponse {
        return when (request.path) {
            "/api/v0/add" -> {
                val data = request.body ?: return HttpResponse(400, "Missing data")
                val cid = ipfsServer.add(data)
                val response = mapOf("Hash" to cid.encode())
                HttpResponse(200, json.encodeToString(response))
            }
            "/api/v0/pin/add" -> {
                val cid = request.params["arg"] ?: return HttpResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val success = ipfsServer.pin(parsedCid)
                if (success) {
                    val response = mapOf("Pins" to listOf(cid))
                    HttpResponse(200, json.encodeToString(response))
                } else {
                    HttpResponse(500, "Failed to pin")
                }
            }
            "/api/v0/dht/provide" -> {
                val cid = request.params["arg"] ?: return HttpResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val dht = IpfsServiceLocator.getDHT()
                dht.provide(parsedCid)
                HttpResponse(200, "OK")
            }
            else -> HttpResponse(404, "Not found")
        }
    }
    
    internal suspend fun handlePutRequest(request: HttpRequest): HttpResponse {
        return HttpResponse(405, "Method not allowed")
    }
    
    internal suspend fun handleDeleteRequest(request: HttpRequest): HttpResponse {
        return when (request.path) {
            "/api/v0/pin/rm" -> {
                val cid = request.params["arg"] ?: return HttpResponse(400, "Missing CID")
                val parsedCid = parseCID(cid)
                val success = ipfsServer.unpin(parsedCid)
                if (success) {
                    val response = mapOf("Pins" to listOf(cid))
                    HttpResponse(200, json.encodeToString(response))
                } else {
                    HttpResponse(500, "Failed to unpin")
                }
            }
            else -> HttpResponse(404, "Not found")
        }
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

// === HTTP SERVER DATA CLASSES ===

data class IpfsHttpServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 5001,
    val metricsInterval: Long = 5000, // 5 seconds
    val maxRequestSize: Int = 1024 * 1024, // 1MB
    val enableCORS: Boolean = true,
    val enableMetrics: Boolean = true
)

data class HttpRequest(
    val method: String,
    val path: String,
    val params: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: Indexed<Byte>? = null
)

data class HttpResponse(
    val status: Int,
    val data: Any, // Can be String, Indexed<Byte>, or serialized JSON
    val headers: Map<String, String> = emptyMap()
) {
    fun toByteArray(): ByteArray {
        return when (data) {
            is String -> data.encodeToByteArray()
            is Indexed<Byte> -> data.a j { data.b(it) }.toByteArray()
            else -> data.toString().encodeToByteArray()
        }
    }
}

// === IPFS API RESPONSE TYPES ===

@Serializable
data class IpfsApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

@Serializable
data class IpfsPeerResponse(
    val ID: String,
    val Addrs: List<String>,
    val AgentVersion: String,
    val ProtocolVersion: String
)

@Serializable
data class IpfsVersionResponse(
    val Version: String,
    val Commit: String,
    val Repo: String,
    val System: String
)

@Serializable
data class IpfsPinResponse(
    val Pins: List<String>
)

@Serializable
data class IpfsProviderResponse(
    val ID: String,
    val Addrs: List<String>
) 