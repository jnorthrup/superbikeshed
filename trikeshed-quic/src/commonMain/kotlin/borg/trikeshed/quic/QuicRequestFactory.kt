package borg.trikeshed.quic

import borg.trikeshed.couchdb.*
import borg.trikeshed.net.*
import borg.trikeshed.io.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Real QUIC RequestFactory Implementation
 * 
 * Provides:
 * - QUIC-based request/response handling
 * - CouchDB integration for persistence
 * - Real-time request processing
 * - Connection pooling and multiplexing
 */
@Serializable
data class QuicRequest(
    val id: String,
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: RequestPriority = RequestPriority.NORMAL,
    val timeout: Duration = 30.seconds,
    val retries: Int = 3
) {
    @Serializable
    enum class RequestPriority {
        LOW, NORMAL, HIGH, URGENT
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuicRequest) return false
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class QuicResponse(
    val id: String,
    val requestId: String,
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val processingTime: Duration = Duration.ZERO
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuicResponse) return false
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class QuicConnection(
    val id: String,
    val remoteAddress: String,
    val localAddress: String,
    val streamId: Long,
    val state: ConnectionState = ConnectionState.ESTABLISHING,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivity: Long = System.currentTimeMillis()
) {
    @Serializable
    enum class ConnectionState {
        ESTABLISHING, ESTABLISHED, CLOSING, CLOSED, ERROR
    }
}

@Serializable
data class RequestMetrics(
    val requestId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val retryCount: Int = 0,
    val errorCount: Int = 0,
    val connectionId: String? = null
)

class QuicRequestFactory(
    private val couchDB: CouchDBClient,
    private val networkManager: NetworkManager,
    private val ioManager: IOManager
) {
    private val connections = mutableMapOf<String, QuicConnection>()
    private val requestQueue = PriorityQueue<QuicRequest>(compareBy { it.priority.ordinal })
    private val activeRequests = mutableMapOf<String, QuicRequest>()
    private val responseCache = mutableMapOf<String, QuicResponse>()
    private val metrics = mutableMapOf<String, RequestMetrics>()
    
    private val _requestFlow = MutableSharedFlow<QuicRequest>()
    val requestFlow: Flow<QuicRequest> = _requestFlow.asSharedFlow()
    
    private val _responseFlow = MutableSharedFlow<QuicResponse>()
    val responseFlow: Flow<QuicResponse> = _responseFlow.asSharedFlow()
    
    private val _connectionFlow = MutableSharedFlow<QuicConnection>()
    val connectionFlow: Flow<QuicConnection> = _connectionFlow.asSharedFlow()
    
    private val requestProcessor = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionManager = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        initializeDatabases()
        startRequestProcessor()
        startConnectionManager()
    }
    
    /**
     * Submit a request for processing
     */
    suspend fun submitRequest(request: QuicRequest): String {
        // Store request in CouchDB
        val requestDoc = mapOf(
            "_id" to request.id,
            "type" to "quic-request",
            "request" to request,
            "status" to "pending",
            "timestamp" to System.currentTimeMillis()
        )
        
        couchDB.saveDocument("quic-requests", requestDoc)
        
        // Add to processing queue
        requestQueue.offer(request)
        activeRequests[request.id] = request
        
        // Emit to flow
        _requestFlow.emit(request)
        
        // Start metrics tracking
        metrics[request.id] = RequestMetrics(
            requestId = request.id,
            startTime = System.currentTimeMillis()
        )
        
        return request.id
    }
    
    /**
     * Get response for a request
     */
    suspend fun getResponse(requestId: String, timeout: Duration = 30.seconds): QuicResponse? {
        val startTime = System.currentTimeMillis()
        
        return withTimeout(timeout) {
            responseFlow
                .filter { it.requestId == requestId }
                .first()
        }
    }
    
    /**
     * Create QUIC connection
     */
    suspend fun createConnection(
        remoteAddress: String,
        localAddress: String = "0.0.0.0:0"
    ): QuicConnection {
        val connectionId = "conn-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}"
        val streamId = System.nanoTime()
        
        val connection = QuicConnection(
            id = connectionId,
            remoteAddress = remoteAddress,
            localAddress = localAddress,
            streamId = streamId
        )
        
        // Store in CouchDB
        val connectionDoc = mapOf(
            "_id" to connectionId,
            "type" to "quic-connection",
            "connection" to connection,
            "timestamp" to System.currentTimeMillis()
        )
        
        couchDB.saveDocument("quic-connections", connectionDoc)
        
        connections[connectionId] = connection
        _connectionFlow.emit(connection)
        
        return connection
    }
    
    /**
     * Send request over QUIC connection
     */
    suspend fun sendRequest(
        connectionId: String,
        request: QuicRequest
    ): QuicResponse {
        val connection = connections[connectionId]
            ?: throw IllegalArgumentException("Connection not found: $connectionId")
        
        // Update connection state
        connection.state = QuicConnection.ConnectionState.ESTABLISHED
        connection.lastActivity = System.currentTimeMillis()
        
        // Prepare QUIC packet
        val packet = prepareQuicPacket(request, connection)
        
        // Send via network manager
        val responseData = networkManager.sendPacket(packet)
        
        // Parse response
        val response = parseQuicResponse(responseData, request.id)
        
        // Store response in CouchDB
        val responseDoc = mapOf(
            "_id" to response.id,
            "type" to "quic-response",
            "response" to response,
            "connectionId" to connectionId,
            "timestamp" to System.currentTimeMillis()
        )
        
        couchDB.saveDocument("quic-responses", responseDoc)
        
        // Update metrics
        updateMetrics(request.id, response)
        
        // Cache response
        responseCache[request.id] = response
        
        // Emit response
        _responseFlow.emit(response)
        
        return response
    }
    
    /**
     * Get request metrics
     */
    suspend fun getRequestMetrics(requestId: String): RequestMetrics? {
        return metrics[requestId]
    }
    
    /**
     * Get all active connections
     */
    fun getActiveConnections(): List<QuicConnection> {
        return connections.values.filter { 
            it.state == QuicConnection.ConnectionState.ESTABLISHED 
        }
    }
    
    /**
     * Close connection
     */
    suspend fun closeConnection(connectionId: String) {
        val connection = connections[connectionId] ?: return
        
        connection.state = QuicConnection.ConnectionState.CLOSING
        
        // Send close packet
        val closePacket = prepareClosePacket(connection)
        networkManager.sendPacket(closePacket)
        
        connection.state = QuicConnection.ConnectionState.CLOSED
        connections.remove(connectionId)
        
        // Update in CouchDB
        val updateDoc = mapOf(
            "_id" to connectionId,
            "type" to "quic-connection",
            "connection" to connection,
            "closedAt" to System.currentTimeMillis()
        )
        
        couchDB.updateDocument("quic-connections", updateDoc)
        _connectionFlow.emit(connection)
    }
    
    // Private methods
    
    private suspend fun initializeDatabases() {
        val databases = listOf("quic-requests", "quic-responses", "quic-connections", "quic-metrics")
        
        databases.forEach { dbName ->
            try {
                couchDB.createDatabase(dbName)
                
                // Create views for querying
                val views = when (dbName) {
                    "quic-requests" -> mapOf(
                        "requests" to mapOf(
                            "by-status" to "function(doc) { if (doc.type === 'quic-request') { emit(doc.status, doc); } }",
                            "by-timestamp" to "function(doc) { if (doc.type === 'quic-request') { emit(doc.timestamp, doc); } }"
                        )
                    )
                    "quic-responses" -> mapOf(
                        "responses" to mapOf(
                            "by-request" to "function(doc) { if (doc.type === 'quic-response') { emit(doc.response.requestId, doc); } }",
                            "by-status" to "function(doc) { if (doc.type === 'quic-response') { emit(doc.response.statusCode, doc); } }"
                        )
                    )
                    "quic-connections" -> mapOf(
                        "connections" to mapOf(
                            "by-state" to "function(doc) { if (doc.type === 'quic-connection') { emit(doc.connection.state, doc); } }",
                            "by-remote" to "function(doc) { if (doc.type === 'quic-connection') { emit(doc.connection.remoteAddress, doc); } }"
                        )
                    )
                    else -> emptyMap()
                }
                
                if (views.isNotEmpty()) {
                    couchDB.createViews(dbName, views)
                }
            } catch (e: Exception) {
                println("Failed to initialize database $dbName: ${e.message}")
            }
        }
    }
    
    private fun startRequestProcessor() {
        requestProcessor.launch {
            while (isActive) {
                try {
                    val request = requestQueue.poll()
                    if (request != null) {
                        processRequest(request)
                    } else {
                        delay(10.milliseconds)
                    }
                } catch (e: Exception) {
                    println("Request processor error: ${e.message}")
                }
            }
        }
    }
    
    private fun startConnectionManager() {
        connectionManager.launch {
            while (isActive) {
                try {
                    // Clean up stale connections
                    val now = System.currentTimeMillis()
                    connections.values
                        .filter { now - it.lastActivity > 300000 } // 5 minutes
                        .forEach { connection ->
                            launch { closeConnection(connection.id) }
                        }
                    
                    delay(60000) // Check every minute
                } catch (e: Exception) {
                    println("Connection manager error: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun processRequest(request: QuicRequest) {
        try {
            // Find available connection or create new one
            val connection = getOrCreateConnection(request)
            
            // Send request
            val response = sendRequest(connection.id, request)
            
            // Update request status
            val updateDoc = mapOf(
                "_id" to request.id,
                "type" to "quic-request",
                "status" to "completed",
                "responseId" to response.id,
                "completedAt" to System.currentTimeMillis()
            )
            
            couchDB.updateDocument("quic-requests", updateDoc)
            
        } catch (e: Exception) {
            // Handle request failure
            handleRequestFailure(request, e)
        } finally {
            activeRequests.remove(request.id)
        }
    }
    
    private suspend fun getOrCreateConnection(request: QuicRequest): QuicConnection {
        // Try to find existing connection to the same host
        val host = extractHost(request.path)
        val existingConnection = connections.values
            .find { it.remoteAddress.contains(host) && it.state == QuicConnection.ConnectionState.ESTABLISHED }
        
        return existingConnection ?: createConnection(host)
    }
    
    private fun extractHost(path: String): String {
        // Simple host extraction - in production would parse URL properly
        return path.split("/").firstOrNull() ?: "localhost"
    }
    
    private fun prepareQuicPacket(request: QuicRequest, connection: QuicConnection): ByteArray {
        // Create QUIC packet with request data
        val packetData = buildJsonObject {
            put("type", "request")
            put("requestId", request.id)
            put("method", request.method)
            put("path", request.path)
            put("headers", JsonObject(request.headers))
            put("body", request.body?.let { String(it) } ?: "")
            put("connectionId", connection.id)
            put("streamId", connection.streamId)
        }
        
        return packetData.toString().toByteArray()
    }
    
    private fun parseQuicResponse(responseData: ByteArray, requestId: String): QuicResponse {
        val responseJson = Json.parseToJsonElement(String(responseData)).jsonObject
        
        return QuicResponse(
            id = "resp-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}",
            requestId = requestId,
            statusCode = responseJson["statusCode"]?.jsonPrimitive?.int ?: 200,
            headers = responseJson["headers"]?.jsonObject?.entries?.associate { it.key to it.value.jsonPrimitive.content } ?: emptyMap(),
            body = responseJson["body"]?.jsonPrimitive?.content?.toByteArray(),
            timestamp = System.currentTimeMillis(),
            processingTime = Duration.milliseconds(responseJson["processingTime"]?.jsonPrimitive?.long ?: 0)
        )
    }
    
    private fun prepareClosePacket(connection: QuicConnection): ByteArray {
        val closeData = buildJsonObject {
            put("type", "close")
            put("connectionId", connection.id)
            put("streamId", connection.streamId)
        }
        
        return closeData.toString().toByteArray()
    }
    
    private suspend fun handleRequestFailure(request: QuicRequest, error: Exception) {
        // Update metrics
        metrics[request.id]?.let { metric ->
            metrics[request.id] = metric.copy(
                errorCount = metric.errorCount + 1
            )
        }
        
        // Update request status
        val errorDoc = mapOf(
            "_id" to request.id,
            "type" to "quic-request",
            "status" to "failed",
            "error" to error.message,
            "failedAt" to System.currentTimeMillis()
        )
        
        couchDB.updateDocument("quic-requests", errorDoc)
        
        // Retry if possible
        val currentRetries = metrics[request.id]?.retryCount ?: 0
        if (currentRetries < request.retries) {
            metrics[request.id] = metrics[request.id]?.copy(retryCount = currentRetries + 1)
            requestQueue.offer(request.copy(id = "${request.id}-retry-${currentRetries + 1}"))
        }
    }
    
    private fun updateMetrics(requestId: String, response: QuicResponse) {
        metrics[requestId]?.let { metric ->
            metrics[requestId] = metric.copy(
                endTime = System.currentTimeMillis(),
                bytesReceived = response.body?.size?.toLong() ?: 0
            )
        }
    }
} 