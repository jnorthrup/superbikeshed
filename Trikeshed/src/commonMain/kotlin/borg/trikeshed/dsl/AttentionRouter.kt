package borg.trikeshed.dsl

import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import borg.trikeshed.distributed.*
import borg.trikeshed.ipfs.*
import borg.trikeshed.couchdb.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.jetsam.*
import borg.trikeshed.cursor.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.*

/**
 * Stackable Bubbling Router DSL
 * Extends LLM attention through main() parameters to attention-framed data collection telemetry services
 * Provides test-coverage-like reach to all TrikeShed code
 */

// Attention frame for telemetry collection
@DslMarker
annotation class AttentionDsl

@AttentionDsl
data class AttentionFrame(
    val source: String,
    val target: String,
    val weight: Double = 1.0,
    val metadata: JsonObject = JsonObject(emptyMap())
)

// Telemetry event
data class TelemetryEvent(
    val timestamp: Long,
    val component: String,
    val operation: String,
    val frames: Indexed<AttentionFrame>,
    val data: JsonElement = JsonNull
)

// Router configuration
@AttentionDsl
class RouterConfig {
    var enableQuic = true
    var enableIpfs = true
    var enableCouchDb = true
    var enableGossip = true
    var enableCursor = true
    var enableReactor = true
    var telemetryEndpoint: String? = null
    
    internal val routes = mutableListOf<Route>()
    internal val middlewares = mutableListOf<Middleware>()
    internal val telemetryHandlers = mutableListOf<TelemetryHandler>()
    
    fun route(pattern: String, handler: suspend AttentionContext.() -> Unit) {
        routes.add(Route(pattern, handler))
    }
    
    fun middleware(handler: suspend (AttentionContext, suspend () -> Unit) -> Unit) {
        middlewares.add(Middleware(handler))
    }
    
    fun telemetry(handler: suspend (TelemetryEvent) -> Unit) {
        telemetryHandlers.add(TelemetryHandler(handler))
    }
}

// Route definition
data class Route(
    val pattern: String,
    val handler: suspend AttentionContext.() -> Unit
)

// Middleware definition
data class Middleware(
    val handler: suspend (AttentionContext, suspend () -> Unit) -> Unit
)

// Telemetry handler
data class TelemetryHandler(
    val handler: suspend (TelemetryEvent) -> Unit
)

// Attention context for route handlers
@AttentionDsl
class AttentionContext(
    val coroutineContext: CoroutineContext,
    private val telemetryCollector: TelemetryCollector
) {
    private val frames = mutableListOf<AttentionFrame>()
    
    // Frame DSL
    fun frame(source: String, target: String, block: AttentionFrame.() -> Unit = {}) {
        val frame = AttentionFrame(source, target).apply(block)
        frames.add(frame)
    }
    
    // Component access
    val quic: QuicEngine? get() = coroutineContext[DistributedStorageCCEK.QUIC_ENGINE]?.engine
    val ipfs: IpfsClient? get() = coroutineContext[DistributedStorageCCEK.IPFS_CLIENT]?.client
    val couch: CouchClient? get() = coroutineContext[DistributedStorageCCEK.COUCH_CLIENT]?.client
    val storage = DistributedStorage(coroutineContext)
    
    // Production operations
    
    suspend fun processRequest(request: HttpRequest): HttpResponse {
        frame("router", "request:${request.path}")
        
        return when {
            request.path.startsWith("/api/") -> handleApi(request)
            request.path.startsWith("/ipfs/") -> handleIpfs(request)
            request.path.startsWith("/couch/") -> handleCouch(request)
            request.path.startsWith("/gossip/") -> handleGossip(request)
            else -> HttpResponse(404, "Not Found")
        }
    }
    
    private suspend fun handleApi(request: HttpRequest): HttpResponse {
        frame("api", request.path)
        
        val apiPath = request.path.removePrefix("/api/")
        return when (apiPath) {
            "status" -> {
                val status = buildJsonObject {
                    put("quic", quic != null)
                    put("ipfs", ipfs != null)
                    put("couch", couch != null)
                    put("timestamp", System.currentTimeMillis())
                }
                HttpResponse(200, "OK", status.toString(), "application/json")
            }
            else -> HttpResponse(404, "API endpoint not found")
        }
    }
    
    private suspend fun handleIpfs(request: HttpRequest): HttpResponse {
        frame("ipfs", request.path)
        
        val ipfsPath = request.path.removePrefix("/ipfs/")
        val client = ipfs ?: return HttpResponse(503, "IPFS not available")
        
        return when (request.method) {
            "GET" -> {
                // Get content by CID
                val cid = CID(
                    version = 1,
                    codec = CID.Codec.RAW,
                    multihash = Multihash.decode(parseBase58(ipfsPath))
                )
                val data = client.get(cid)
                if (data != null) {
                    val bytes = ByteArray(data.a) { data.b(it) }
                    HttpResponse(200, "OK", bytes.decodeToString())
                } else {
                    HttpResponse(404, "Content not found")
                }
            }
            "POST" -> {
                // Add content
                val data = request.body.toByteArray()
                val indexed = data.size j { data[it] }
                val cid = client.add(indexed)
                HttpResponse(200, "OK", cid.encode())
            }
            else -> HttpResponse(405, "Method not allowed")
        }
    }
    
    private suspend fun handleCouch(request: HttpRequest): HttpResponse {
        frame("couch", request.path)
        
        val couchPath = request.path.removePrefix("/couch/")
        val client = couch ?: return HttpResponse(503, "CouchDB not available")
        
        val parts = couchPath.split('/')
        if (parts.isEmpty()) return HttpResponse(400, "Invalid path")
        
        val db = parts[0]
        val docId = parts.getOrNull(1)
        
        return when (request.method) {
            "GET" -> {
                if (docId != null) {
                    try {
                        val doc = client.getDocument(db, docId)
                        HttpResponse(200, "OK", doc.toJson().toString(), "application/json")
                    } catch (e: Exception) {
                        HttpResponse(404, "Document not found")
                    }
                } else {
                    HttpResponse(400, "Document ID required")
                }
            }
            "PUT" -> {
                if (docId != null) {
                    val doc = CouchDocument(
                        id = docId,
                        data = Json.parseToJsonElement(request.body).jsonObject
                    )
                    val response = client.createDocument(db, doc)
                    HttpResponse(200, "OK", Json.encodeToString(response))
                } else {
                    HttpResponse(400, "Document ID required")
                }
            }
            else -> HttpResponse(405, "Method not allowed")
        }
    }
    
    private suspend fun handleGossip(request: HttpRequest): HttpResponse {
        frame("gossip", request.path)
        
        return when (request.method) {
            "GET" -> {
                val jetsam = JetsamGossipManager.gatherJetsam()
                HttpResponse(200, "OK", Json.encodeToString(jetsam), "application/json")
            }
            "POST" -> {
                val json = Json.parseToJsonElement(request.body).jsonObject
                val key = json["key"]?.jsonPrimitive?.content ?: return HttpResponse(400, "Key required")
                val value = json["value"] ?: JsonNull
                JetsamGossipManager.addEntry(key, value)
                HttpResponse(200, "OK")
            }
            else -> HttpResponse(405, "Method not allowed")
        }
    }
    
    // Emit telemetry
    private suspend fun emitTelemetry(component: String, operation: String, data: JsonElement) {
        val event = TelemetryEvent(
            timestamp = System.currentTimeMillis(),
            component = component,
            operation = operation,
            frames = frames.size j { frames[it] },
            data = data
        )
        telemetryCollector.emit(event)
    }
}

// Telemetry collector
class TelemetryCollector {
    private val events = mutableListOf<TelemetryEvent>()
    private val handlers = mutableListOf<TelemetryHandler>()
    
    fun addHandler(handler: TelemetryHandler) {
        handlers.add(handler)
    }
    
    suspend fun emit(event: TelemetryEvent) {
        events.add(event)
        handlers.forEach { handler ->
            handler.handler(event)
        }
    }
    
    fun getEvents(): Indexed<TelemetryEvent> = events.size j { events[it] }
}

/**
 * Main attention router builder
 */
class AttentionRouter {
    private val config = RouterConfig()
    private val telemetryCollector = TelemetryCollector()
    
    /**
     * Configure the router using DSL
     */
    fun configure(block: RouterConfig.() -> Unit) {
        config.apply(block)
        
        // Add telemetry handlers
        config.telemetryHandlers.forEach {
            telemetryCollector.addHandler(it)
        }
    }
    
    /**
     * Run the router with main() parameters
     */
    suspend fun run(args: Array<String>): Int = coroutineScope {
        // Parse main() arguments as attention parameters
        val params = parseMainArgs(args)
        
        // Initialize distributed storage if enabled
        val context = if (config.enableQuic || config.enableIpfs || config.enableCouchDb) {
            val storage = DistributedStorage()
            val nodeId = "node_${System.currentTimeMillis()}"
            storage.initialize(
                peerId = PeerId(nodeId.toByteArray().let { it.size j { i -> it[i] } })
            )
        } else {
            EmptyCoroutineContext
        }
        
        // Create attention context
        val attentionContext = AttentionContext(context, telemetryCollector)
        
        // Apply middlewares
        val finalHandler: suspend () -> Unit = {
            // Match and execute routes
            val route = config.routes.find { it.pattern == params["route"] || it.pattern == "*" }
            route?.handler?.invoke(attentionContext)
        }
        
        // Build middleware chain
        val chain = config.middlewares.foldRight(finalHandler) { middleware, next ->
            { middleware.handler(attentionContext, next) }
        }
        
        // Execute chain
        chain()
        
        // Return success
        0
    }
    
    private fun parseMainArgs(args: Array<String>): Map<String, String> {
        val params = mutableMapOf<String, String>()
        
        for (i in args.indices) {
            when {
                args[i].startsWith("--") -> {
                    val key = args[i].removePrefix("--")
                    val value = if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                        args[i + 1]
                    } else {
                        "true"
                    }
                    params[key] = value
                }
            }
        }
        
        return params
    }
}

/**
 * DSL entry point
 */
fun attentionRouter(block: RouterConfig.() -> Unit): AttentionRouter {
    return AttentionRouter().apply {
        configure(block)
    }
}

// Helper functions

private fun <T> emptyIndex(): Indexed<T> = 0 j { throw NoSuchElementException() }

private fun parseBase58(encoded: String): Indexed<Byte> {
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    var num = 0L
    
    for (char in encoded) {
        val digit = alphabet.indexOf(char)
        if (digit < 0) throw IllegalArgumentException("Invalid base58 character: $char")
        num = num * 58 + digit
    }
    
    // Convert to bytes
    val bytes = mutableListOf<Byte>()
    while (num > 0) {
        bytes.add(0, (num and 0xFF).toByte())
        num = num shr 8
    }
    
    // Add leading zeros
    for (char in encoded) {
        if (char == '1') bytes.add(0, 0)
        else break
    }
    
    return bytes.size j { bytes[it] }
}