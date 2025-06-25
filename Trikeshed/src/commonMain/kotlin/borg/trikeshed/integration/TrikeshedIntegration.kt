package borg.trikeshed.integration

import borg.trikeshed.lib.*
import borg.trikeshed.couchdb.*
import borg.trikeshed.ipfs.*
import borg.trikeshed.net.http.*
import borg.trikeshed.net.quic.*
import borg.trikeshed.reactor.*
import kotlinx.serialization.json.*
import kotlin.random.Random

// Platform-agnostic time function - defined in IntegrationTypes.kt

/**
 * Trikeshed Integration System
 * 
 * Comprehensive integration of all Trikeshed subsystems:
 * - CouchDB for document storage
 * - IPFS for distributed content addressing
 * - QUIC for high-performance networking
 * - HTTP server for REST API
 * - Reactor pattern for event-driven architecture
 */

// === TRIKESHED INTEGRATION TAXONOMICAL TYPEALIASES ===

// Integration Configuration Types - use imported types from IntegrationTypes.kt
typealias IntegrationConfig = Map<String, Any>

// Integration Event Types - use imported types from IntegrationTypes.kt  
typealias IntegrationEventType = EventType
typealias IntegrationEventData = EventData
typealias IntegrationEventId = EventId
typealias IntegrationEventTimestamp = EventTimestamp

// Integration Result Types - use imported types from IntegrationTypes.kt  
typealias TrikeshedIntegrationResult = Either<IntegrationError, IntegrationSuccess>
typealias IntegrationDocumentResult = Either<IntegrationError, IntegrationDocument>

// === TRIKESHED INTEGRATION IMPLEMENTATION ===

class TrikeshedIntegration(
    private val config: Map<String, Any> = emptyMap()
) {
    private val couchUrl: IntegrationUrl = config["couch_url"] as? String ?: "http://localhost:5984"
    private val quicPort: IntegrationPort = config["quic_port"] as? Int ?: 4433
    private val databaseName: IntegrationDatabaseName = config["database_name"] as? String ?: "trikeshed_integration"
    
    // Core components
    private lateinit var couchClient: CouchClient
    private lateinit var ipfsClient: IpfsClient
    private lateinit var httpServer: HttpQuicServer
    private lateinit var quicServer: QuicServer
    private lateinit var quicEngine: QuicEngine
    
    // Reactor network
    private val network = ReactorNetwork()
    private lateinit var integrationReactor: Reactor<IntegrationEvent>
    
    // Storage
    private val ipfsStorage = IpfsStorage()
    
    /**
     * Initialize the complete Trikeshed integration system
     */
    suspend fun initialize(): TrikeshedIntegrationResult {
        return try {
            // Initialize QUIC engine
            quicEngine = QuicEngine(
                role = QuicEngine.Role.SERVER,
                initialState = QuicConnectionState(
                    localConnectionId = ConnectionId(8 j { _: Int -> 0.toByte() }),
                    remoteConnectionId = ConnectionId(8 j { _: Int -> 0.toByte() })
                ),
                port = quicPort,
                privateKey = 32 j { _: Int -> 0.toByte() }
            )
            
            // Initialize CouchDB client
            couchClient = CouchClient(couchUrl)
            if (!couchClient.authenticate("admin", "password")) {
                return Either.Left(IntegrationError("Failed to authenticate with CouchDB"))
            }
            
            // Create database
            val dbResult = couchClient.createDatabase(databaseName)
            when (dbResult) {
                is CouchResult.Success -> println("Database created: $databaseName")
                is CouchResult.Error -> println("Database creation warning: ${dbResult.message}")
            }
            
            // Initialize IPFS client
            val peerId = PeerId(32 j { it: Int -> (it % 256).toByte() })
            val ipfsConfig = IpfsConfig()
            ipfsClient = IpfsClient(peerId, quicEngine, ipfsStorage, ipfsConfig)
            
            // Initialize QUIC server
            quicServer = QuicServer(quicEngine, quicPort)
            
            // Initialize HTTP QUIC server
            httpServer = HttpQuicServer(quicEngine, quicPort)
            setupHttpRoutes()
            
            // Initialize reactor
            initializeReactor()
            
            println("Trikeshed Integration initialized successfully")
            Either.Right(IntegrationSuccess("Initialization completed"))
            
        } catch (e: Exception) {
            Either.Left(IntegrationError("Initialization failed: ${e.message}"))
        }
    }
    
    /**
     * Initialize the integration reactor
     */
    private fun initializeReactor() {
        integrationReactor = Reactor<IntegrationEvent>("trikeshed-integration")
        
        // Handle document events
        integrationReactor.on("document_created") { event ->
            handleDocumentCreated(event.data)
        }
        
        integrationReactor.on("document_updated") { event ->
            handleDocumentUpdated(event.data)
        }
        
        integrationReactor.on("document_deleted") { event ->
            handleDocumentDeleted(event.data)
        }
        
        // Handle IPFS events
        integrationReactor.on("ipfs_content_added") { event ->
            handleIpfsContentAdded(event.data)
        }
        
        integrationReactor.on("ipfs_content_retrieved") { event ->
            handleIpfsContentRetrieved(event.data)
        }
        
        // Add to network
        network.addReactor("integration", integrationReactor)
    }
    
    /**
     * Setup HTTP routes for the integration API
     */
    private fun setupHttpRoutes() {
        // Health check
        httpServer.route("/health") { request ->
            val health = mapOf(
                "status" to "healthy",
                "couchdb" to true,
                "ipfs" to true,
                "quic" to true,
                "reactor" to network.isActive(),
                "timestamp" to getCurrentTimeMillis()
            )
            HttpResponse(
                status = 200,
                headers = mapOf("content-type" to "application/json"),
                body = health.toJson()
            )
        }
        
        // Document operations
        httpServer.route("/documents") { request ->
            when (request.method) {
                "POST" -> {
                    val result = createDocument(request.body)
                    when (result) {
                        is Either.Right -> HttpResponse(
                            status = 201,
                            headers = mapOf("content-type" to "application/json"),
                            body = result.value.toJson()
                        )
                        is Either.Left -> HttpResponse(
                            status = 500,
                            headers = mapOf("content-type" to "application/json"),
                            body = """{"error":"${result.value.message}"}"""
                        )
                    }
                }
                "GET" -> {
                    val id = request.queryParams["id"]
                    if (id != null) {
                        val result = getDocument(id)
                        when (result) {
                            is Either.Right -> HttpResponse(
                                status = 200,
                                headers = mapOf("content-type" to "application/json"),
                                body = result.value.toJson()
                            )
                            is Either.Left -> HttpResponse(
                                status = 404,
                                headers = mapOf("content-type" to "application/json"),
                                body = """{"error":"${result.value.message}"}"""
                            )
                        }
                    } else {
                        HttpResponse(400, mapOf("content-type" to "application/json"), """{"error":"Missing id parameter"}""")
                    }
                }
                else -> HttpResponse(405, mapOf("content-type" to "application/json"), """{"error":"Method not allowed"}""")
            }
        }
        
        // IPFS operations
        httpServer.route("/ipfs") { request ->
            when (request.method) {
                "POST" -> {
                    val result = storeInIpfs(request.body)
                    when (result) {
                        is Either.Right -> HttpResponse(
                            status = 201,
                            headers = mapOf("content-type" to "application/json"),
                            body = """{"hash":"${result.value.hash}"}"""
                        )
                        is Either.Left -> HttpResponse(
                            status = 500,
                            headers = mapOf("content-type" to "application/json"),
                            body = """{"error":"${result.value.message}"}"""
                        )
                    }
                }
                "GET" -> {
                    val hash = request.queryParams["hash"]
                    if (hash != null) {
                        val result = retrieveFromIpfs(hash)
                        when (result) {
                            is Either.Right -> HttpResponse(
                                status = 200,
                                headers = mapOf("content-type" to "application/json"),
                                body = result.value.content ?: ""
                            )
                            is Either.Left -> HttpResponse(
                                status = 404,
                                headers = mapOf("content-type" to "application/json"),
                                body = """{"error":"${result.value.message}"}"""
                            )
                        }
                    } else {
                        HttpResponse(400, mapOf("content-type" to "application/json"), """{"error":"Missing hash parameter"}""")
                    }
                }
                else -> HttpResponse(405, mapOf("content-type" to "application/json"), """{"error":"Method not allowed"}""")
            }
        }
        
        // Reactor events
        httpServer.route("/events") { request ->
            when (request.method) {
                "POST" -> {
                    val event = parseEvent(request.body)
                    network.emit("integration", event)
                    HttpResponse(200, mapOf("content-type" to "application/json"), """{"status":"event_emitted"}""")
                }
                "GET" -> {
                    val stats = network.getStats()
                    HttpResponse(200, mapOf("content-type" to "application/json"), stats.toJson())
                }
                else -> HttpResponse(405, mapOf("content-type" to "application/json"), """{"error":"Method not allowed"}""")
            }
        }
    }
    
    /**
     * Create a document with IPFS integration
     */
    private suspend fun createDocument(content: String): IntegrationDocumentResult {
        return try {
            // Create CouchDB document
            val document = CouchDocument(
                data = buildJsonObject { 
                    put("content", content)
                    put("created_at", getCurrentTimeMillis())
                }
            )
            
            val couchResult = couchClient.createDocument(databaseName, document)
            when (couchResult) {
                is Either.Right -> {
                    val doc = couchResult.value
                    val docId = doc.id ?: return Either.Left(IntegrationError("No document ID returned"))
                    
                    // Store content in IPFS
                    val contentBytes = content.encodeToByteArray()
                    val indexedContent = contentBytes.size j { contentBytes[it] }
                    val ipfsResult = ipfsClient.store(indexedContent)
                    
                    // Update document with IPFS hash
                    val updatedDoc = doc.copy(
                        data = buildJsonObject {
                            doc.data.forEach { (key, value) -> put(key, value) }
                            put("ipfs_hash", ipfsResult.hash)
                        }
                    )
                    couchClient.updateDocument(databaseName, updatedDoc)
                    
                    // Emit integration event
                    val event = IntegrationEvent(
                        type = "document_created",
                        data = mapOf(
                            "document_id" to docId,
                            "ipfs_hash" to ipfsResult.hash,
                            "content_length" to content.length
                        )
                    )
                    network.emit("integration", event)
                    
                    Either.Right(
                        IntegrationDocument(
                            id = docId,
                            content = content,
                            ipfsHash = ipfsResult.hash,
                            metadata = mapOf(
                                "created_at" to getCurrentTimeMillis(),
                                "content_length" to content.length
                            )
                        )
                    )
                }
                is Either.Left -> Either.Left(IntegrationError(couchResult.value))
            }
        } catch (e: Exception) {
            Either.Left(IntegrationError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Get a document with IPFS verification
     */
    private suspend fun getDocument(id: String): IntegrationDocumentResult {
        return try {
            val couchResult = couchClient.getDocument(databaseName, id)
            when (couchResult) {
                is Either.Right -> {
                    val doc = couchResult.value
                    val content = doc.data["content"]?.jsonPrimitive?.content ?: return Either.Left(IntegrationError("No content found"))
                    val ipfsHash = doc.data["ipfs_hash"]?.jsonPrimitive?.content
                    
                    // Verify with IPFS if hash exists
                    if (ipfsHash != null) {
                        val ipfsContent = ipfsClient.retrieve(ipfsHash)
                        if (ipfsContent != null) {
                            val contentBytes = content.encodeToByteArray()
                            val ipfsBytes = ipfsContent.content.toByteArray()
                            if (contentBytes.contentEquals(ipfsBytes)) {
                                Either.Right(
                                    IntegrationDocument(
                                        id = id,
                                        content = content,
                                        ipfsHash = ipfsHash,
                                        metadata = mapOf(
                                            "retrieved_at" to getCurrentTimeMillis(),
                                            "verified" to true
                                        )
                                    )
                                )
                            } else {
                                Either.Left(IntegrationError("Content verification failed"))
                            }
                        } else {
                            Either.Left(IntegrationError("IPFS content not found"))
                        }
                    } else {
                        // No IPFS hash, return content directly
                        Either.Right(
                            IntegrationDocument(
                                id = id,
                                content = content,
                                metadata = mapOf(
                                    "retrieved_at" to getCurrentTimeMillis(),
                                    "verified" to false
                                )
                            )
                        )
                    }
                }
                is Either.Left -> Either.Left(IntegrationError(couchResult.value))
            }
        } catch (e: Exception) {
            Either.Left(IntegrationError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Store content directly in IPFS
     */
    private suspend fun storeInIpfs(content: String): TrikeshedIntegrationResult {
        return try {
            val contentBytes = content.encodeToByteArray()
            val indexedContent = contentBytes.size j { contentBytes[it] }
            val result = ipfsClient.store(indexedContent)
            
            Either.Right(IntegrationSuccess(hash = result.hash))
        } catch (e: Exception) {
            Either.Left(IntegrationError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Retrieve content from IPFS
     */
    private suspend fun retrieveFromIpfs(hash: String): TrikeshedIntegrationResult {
        return try {
            val result = ipfsClient.retrieve(hash)
            if (result != null) {
                val content = result.content.toByteArray().decodeToString()
                Either.Right(IntegrationSuccess(content = content))
            } else {
                Either.Left(IntegrationError("Content not found"))
            }
        } catch (e: Exception) {
            Either.Left(IntegrationError(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Parse event from request body
     */
    private fun parseEvent(body: String): IntegrationEvent {
        return IntegrationEvent(
            type = "custom_event",
            data = mapOf("body" to body)
        )
    }
    
    /**
     * Handle document created events
     */
    private fun handleDocumentCreated(data: IntegrationEventData) {
        println("Document created: $data")
    }
    
    /**
     * Handle document updated events
     */
    private fun handleDocumentUpdated(data: IntegrationEventData) {
        println("Document updated: $data")
    }
    
    /**
     * Handle document deleted events
     */
    private fun handleDocumentDeleted(data: IntegrationEventData) {
        println("Document deleted: $data")
    }
    
    /**
     * Handle IPFS content added events
     */
    private fun handleIpfsContentAdded(data: IntegrationEventData) {
        println("IPFS content added: $data")
    }
    
    /**
     * Handle IPFS content retrieved events
     */
    private fun handleIpfsContentRetrieved(data: IntegrationEventData) {
        println("IPFS content retrieved: $data")
    }
    
    /**
     * Start the integration system
     */
    suspend fun start(): TrikeshedIntegrationResult {
        return try {
            quicServer.start()
            httpServer.start()
            network.start()
            println("Trikeshed Integration started")
            Either.Right(IntegrationSuccess("System started"))
        } catch (e: Exception) {
            Either.Left(IntegrationError("Failed to start: ${e.message}"))
        }
    }
    
    /**
     * Stop the integration system
     */
    suspend fun stop(): TrikeshedIntegrationResult {
        return try {
            network.stop()
            httpServer.stop()
            quicServer.stop()
            println("Trikeshed Integration stopped")
            Either.Right(IntegrationSuccess("System stopped"))
        } catch (e: Exception) {
            Either.Left(IntegrationError("Failed to stop: ${e.message}"))
        }
    }
}

// === INTEGRATION DATA TYPES ===

data class IntegrationDocument(
    val id: String,
    val content: String,
    val ipfsHash: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    fun toJson(): String {
        return """{"id":"$id","content":"$content","ipfs_hash":"${ipfsHash ?: ""}","metadata":${(metadata as Map<String, Any?>).toJson()}}"""
    }
}

data class IntegrationEvent(
    val type: EventType,
    val data: EventData,
    val id: EventId = generateEventId(),
    val timestamp: EventTimestamp = getCurrentTimeMillis()
)


data class IntegrationSuccess(
    val message: String? = null,
    val hash: String? = null,
    val content: String? = null
)

// === UTILITY FUNCTIONS ===

private fun generateEventId(): EventId = "event_${getCurrentTimeMillis()}_${(Random.nextDouble() * 1000).toInt()}"

private fun Map<String, Any>.toJson(): String {
    return toString() // Simplified JSON conversion
} 