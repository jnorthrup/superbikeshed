package borg.trikeshed.couchdb

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * CouchDB CCEK (CoroutineContextElementKey) Implementation
 * 
 * This ties all CouchDB functionality to CoroutineContext elements,
 * allowing dependency injection without module dependencies.
 */

// Network layer functions as context element
interface NetworkService : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<NetworkService>
    override val key: CoroutineContext.Key<*> get() = Key
    
    suspend fun startServer(port: Int): NetworkServer
    suspend fun handleRequest(request: HttpRequest): HttpResponse
}

// Blob storage functions as context element  
interface BlobService : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BlobService>
    override val key: CoroutineContext.Key<*> get() = Key
    
    suspend fun store(id: String, data: ByteArray): Boolean
    suspend fun retrieve(id: String): ByteArray?
    suspend fun delete(id: String): Boolean
    suspend fun exists(id: String): Boolean
}

// Channel service functions as context element
interface ChannelService : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ChannelService>
    override val key: CoroutineContext.Key<*> get() = Key
    
    suspend fun createChannel(name: String): Channel
    suspend fun send(channel: Channel, data: Any)
    suspend fun receive(channel: Channel): Any?
}

// Storage engine functions as context element
interface StorageEngine : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<StorageEngine>
    override val key: CoroutineContext.Key<*> get() = Key
    
    suspend fun put(key: String, value: String): Boolean
    suspend fun get(key: String): String?
    suspend fun delete(key: String): Boolean
    suspend fun scan(prefix: String): List<Pair<String, String>>
}

// The main CouchDB service that uses CCEK for all dependencies
class CouchDBServiceCCEK(
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    
    // Extract services from context
    private val networkService = coroutineContext[NetworkService] 
        ?: error("NetworkService not found in context")
    private val blobService = coroutineContext[BlobService]
        ?: error("BlobService not found in context")
    private val channelService = coroutineContext[ChannelService]
        ?: error("ChannelService not found in context")
    private val storageEngine = coroutineContext[StorageEngine]
        ?: error("StorageEngine not found in context")
    
    suspend fun start(port: Int = 5984) {
        println("🚀 CouchDB CCEK Service Starting")
        println("   📍 Port: $port")
        println("   🔧 Using CoroutineContext dependency injection")
        
        // Start network server using injected service
        val server = networkService.startServer(port)
        
        // Create channels for request/response
        val requestChannel = channelService.createChannel("requests")
        val responseChannel = channelService.createChannel("responses")
        
        // Launch request processor
        launch {
            while (isActive) {
                val request = channelService.receive(requestChannel) as? HttpRequest
                if (request != null) {
                    val response = processRequest(request)
                    channelService.send(responseChannel, response)
                }
            }
        }
        
        println("✅ CouchDB CCEK Service Started")
    }
    
    private suspend fun processRequest(request: HttpRequest): HttpResponse {
        return when {
            request.path == "/" -> {
                HttpResponse(200, "OK", """{"couchdb":"Welcome","version":"3.0.0-ccek"}""")
            }
            request.path == "/_all_dbs" -> {
                val dbs = storageEngine.scan("db:").map { it.first.removePrefix("db:") }
                HttpResponse(200, "OK", dbs.joinToString(",", "[", "]") { "\"$it\"" })
            }
            request.path.startsWith("/") && request.method == "PUT" -> {
                val dbName = request.path.trim('/')
                storageEngine.put("db:$dbName", "{}")
                HttpResponse(201, "Created", """{"ok":true}""")
            }
            else -> {
                HttpResponse(404, "Not Found", """{"error":"not_found"}""")
            }
        }
    }
}

// Simple HTTP types
data class HttpRequest(val method: String, val path: String, val body: String? = null)
data class HttpResponse(val status: Int, val statusText: String, val body: String)
data class Channel(val name: String)
interface NetworkServer { val port: Int }

// Concrete implementations that can be injected
class SimpleNetworkService : NetworkService {
    override suspend fun startServer(port: Int) = object : NetworkServer {
        override val port = port
    }
    
    override suspend fun handleRequest(request: HttpRequest) = 
        HttpResponse(200, "OK", "Simple response")
}

class MemoryBlobService : BlobService {
    private val storage = mutableMapOf<String, ByteArray>()
    
    override suspend fun store(id: String, data: ByteArray) = 
        storage.put(id, data) != null
    
    override suspend fun retrieve(id: String) = storage[id]
    
    override suspend fun delete(id: String) = storage.remove(id) != null
    
    override suspend fun exists(id: String) = id in storage
}

class SimpleChannelService : ChannelService {
    private val channels = mutableMapOf<String, MutableList<Any>>()
    
    override suspend fun createChannel(name: String) = Channel(name).also {
        channels[name] = mutableListOf()
    }
    
    override suspend fun send(channel: Channel, data: Any) {
        channels[channel.name]?.add(data)
    }
    
    override suspend fun receive(channel: Channel): Any? {
        return channels[channel.name]?.removeFirstOrNull()
    }
}

class MemoryStorageEngine : StorageEngine {
    private val data = mutableMapOf<String, String>()
    
    override suspend fun put(key: String, value: String) = 
        data.put(key, value) != null
    
    override suspend fun get(key: String) = data[key]
    
    override suspend fun delete(key: String) = data.remove(key) != null
    
    override suspend fun scan(prefix: String) = 
        data.entries.filter { it.key.startsWith(prefix) }
            .map { it.key to it.value }
}

// Builder function that creates a fully configured CouchDB service
suspend fun launchCouchDBWithCCEK(
    port: Int = 5984,
    networkService: NetworkService = SimpleNetworkService(),
    blobService: BlobService = MemoryBlobService(),
    channelService: ChannelService = SimpleChannelService(),
    storageEngine: StorageEngine = MemoryStorageEngine()
): CouchDBServiceCCEK {
    
    // Build the context with all required services
    val context = coroutineContext +
        networkService +
        blobService +
        channelService +
        storageEngine
    
    // Create and start the service
    val service = CouchDBServiceCCEK(context)
    service.start(port)
    
    return service
}

// Extension to make context building easier
operator fun CoroutineContext.plus(service: NetworkService): CoroutineContext = this + service
operator fun CoroutineContext.plus(service: BlobService): CoroutineContext = this + service  
operator fun CoroutineContext.plus(service: ChannelService): CoroutineContext = this + service
operator fun CoroutineContext.plus(service: StorageEngine): CoroutineContext = this + service