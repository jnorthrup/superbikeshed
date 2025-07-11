package fiduciary.hexagon

import borg.trikeshed.lib.*
import fiduciary.protocol.*
import kotlinx.serialization.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * Ingestion Pipeline Components
 * 
 * From Fiduciary Omnibus Architecture updated diagram:
 * - Ingester processes IngestedBlob Flow
 * - Router handles RoutedBlob Flow
 * - CouchDB Storage provides ChangeFeed/Query
 * - Dashboard/Analytics processes user queries
 * - Attention tracks attention events
 * - CRDT syncs with storage
 * - CRDT Stream distributes updates
 */

// === BLOB TYPES ===

/**
 * Ingestable blob for processing
 */
@Serializable
data class IngestableBlob(
    val data: ByteArray,
    val contentType: String,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as IngestableBlob
        
        if (!data.contentEquals(other.data)) return false
        if (contentType != other.contentType) return false
        if (metadata != other.metadata) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Ingested blob with ID and status
 */
@Serializable
data class IngestedBlob(
    val id: String,
    val data: ByteArray,
    val contentType: String,
    val status: BlobStatus,
    val metadata: Map<String, String> = emptyMap(),
    val ingestedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as IngestedBlob
        
        if (id != other.id) return false
        if (!data.contentEquals(other.data)) return false
        if (contentType != other.contentType) return false
        if (status != other.status) return false
        if (metadata != other.metadata) return false
        if (ingestedAt != other.ingestedAt) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + ingestedAt.hashCode()
        return result
    }
}

@Serializable
enum class BlobStatus {
    PENDING,
    INGESTED,
    ROUTED,
    STORED,
    FAILED
}

/**
 * Routed blob with routing decision
 */
@Serializable
data class RoutedBlob(
    val blobId: String,
    val decision: RoutingDecision,
    val destinations: Set<String>,
    val metadata: Map<String, String> = emptyMap(),
    val routedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class RoutingDecision {
    STORE,
    ATTENTION,
    DISCARD,
    BATCH,
    PRIORITY
}

/**
 * Stored blob with document reference
 */
@Serializable
data class StoredBlob(
    val blobId: String,
    val documentId: String,
    val status: StorageStatus,
    val storedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class StorageStatus {
    STORING,
    STORED,
    FAILED
}

// === ROUTING SYSTEM ===

/**
 * Routing rule for blob processing
 */
@Serializable
data class RoutingRule(
    val name: String,
    val condition: (IngestedBlob) -> Boolean,
    val decision: RoutingDecision,
    val priority: Int = 0,
    val destinations: Set<String> = emptySet()
)

// === CHANGE TRACKING ===

/**
 * Storage change event
 */
@Serializable
data class StorageChange(
    val blobId: String,
    val documentId: String,
    val changeType: ChangeType,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class ChangeType {
    CREATED,
    UPDATED,
    DELETED
}

// === ANALYTICS SYSTEM ===

/**
 * Analytics query
 */
@Serializable
data class AnalyticsQuery(
    val type: QueryType,
    val filters: Map<String, String> = emptyMap(),
    val timeRange: TimeRange = TimeRange.ALL_TIME,
    val limit: Int = 100
)

@Serializable
enum class QueryType {
    BLOB_COUNT,
    STORAGE_USAGE,
    ATTENTION_METRICS,
    INGESTION_RATE,
    ERROR_RATE
}

@Serializable
enum class TimeRange {
    LAST_HOUR,
    LAST_24_HOURS,
    LAST_WEEK,
    LAST_MONTH,
    ALL_TIME
}

/**
 * Analytics result
 */
@Serializable
data class AnalyticsResult(
    val queryType: QueryType,
    val data: Map<String, JsonElement>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * User query
 */
@Serializable
data class UserQuery(
    val userId: String,
    val query: String,
    val context: QueryContext
)

@Serializable
enum class QueryContext {
    DASHBOARD,
    API,
    SEARCH,
    ANALYTICS
}

/**
 * User query response
 */
@Serializable
data class UserQueryResponse(
    val userId: String,
    val status: QueryStatus,
    val results: List<JsonElement>,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class QueryStatus {
    PENDING,
    PROCESSED,
    FAILED
}

// === CRDT SYSTEM ===

/**
 * CRDT data for synchronization
 */
@Serializable
data class CRDTData(
    val entityId: String,
    val operation: CRDTOperation,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class CRDTOperation {
    ADD,
    REMOVE,
    UPDATE
}

/**
 * CRDT update for streaming
 */
@Serializable
data class CRDTUpdate(
    val entityId: String,
    val operation: CRDTOperation,
    val value: String,
    val timestamp: Long,
    val vectorClock: Map<String, Long> = emptyMap()
)

// === COMPONENT IMPLEMENTATIONS ===

/**
 * Ingester - Processes ingested blobs
 */
class IngesterHexagon {
    private val mutex = Mutex()
    private val processedBlobs = mutableMapOf<String, IngestedBlob>()
    private var connectedRouter: RouterHexagon? = null
    
    /**
     * Tether to router - real dependency injection
     */
    fun connectRouter(router: RouterHexagon) {
        connectedRouter = router
    }
    
    /**
     * Ingest single blob - calls router directly
     */
    suspend fun ingest(data: ByteArray, contentType: String): IngestedBlob {
        mutex.withLock {
            val id = "blob-${System.currentTimeMillis()}-${Random.nextInt(1000)}"
            val blob = IngestedBlob(
                id = id,
                data = data,
                contentType = contentType,
                status = BlobStatus.INGESTED
            )
            
            processedBlobs[id] = blob
            
            // REAL TETHER: Call router directly after ingestion
            connectedRouter?.let { router ->
                val routedBlob = router.route(blob)
                // Router will handle the next call in chain
            }
            
            return blob
        }
    }
    
    /**
     * Ingest batch of blobs
     */
    suspend fun ingestBatch(blobs: List<IngestableBlob>): List<IngestedBlob> {
        return blobs.map { ingestable ->
            ingest(ingestable.data, ingestable.contentType)
        }
    }
    
    /**
     * Get ingested blob by ID
     */
    suspend fun getIngestedBlob(id: String): IngestedBlob? {
        mutex.withLock {
            return processedBlobs[id]
        }
    }
    
    /**
     * Get all ingested blobs
     */
    suspend fun getAllIngestedBlobs(): List<IngestedBlob> {
        mutex.withLock {
            return processedBlobs.values.toList()
        }
    }
}

/**
 * Router - Routes blobs to appropriate destinations
 */
class RouterHexagon {
    private val mutex = Mutex()
    private val routingRules = mutableListOf<RoutingRule>()
    private val routedBlobs = mutableMapOf<String, RoutedBlob>()
    private var connectedStorage: CouchDBStorageHexagon? = null
    private var connectedAttention: AttentionHexagon? = null
    private var connectedCRDT: CRDTStreamHexagon? = null
    
    /**
     * Tether to downstream components - real dependencies
     */
    fun connectStorage(storage: CouchDBStorageHexagon) {
        connectedStorage = storage
    }
    
    fun connectAttention(attention: AttentionHexagon) {
        connectedAttention = attention
    }
    
    fun connectCRDTStream(crdtStream: CRDTStreamHexagon) {
        connectedCRDT = crdtStream
    }
    
    /**
     * Add routing rule
     */
    suspend fun addRoutingRule(rule: RoutingRule) {
        mutex.withLock {
            routingRules.add(rule)
            routingRules.sortByDescending { it.priority }
        }
    }
    
    /**
     * Route blob based on rules - calls next components directly
     */
    suspend fun route(blob: IngestedBlob): RoutedBlob {
        mutex.withLock {
            // Find first matching rule
            val matchingRule = routingRules.find { rule ->
                rule.condition(blob)
            }
            
            val decision = matchingRule?.decision ?: RoutingDecision.STORE
            val destinations = matchingRule?.destinations?.ifEmpty { 
                getDefaultDestinations(decision) 
            } ?: getDefaultDestinations(decision)
            
            val routed = RoutedBlob(
                blobId = blob.id,
                decision = decision,
                destinations = destinations,
                metadata = blob.metadata
            )
            
            routedBlobs[blob.id] = routed
            
            // REAL TETHER: Call next components based on routing decision
            when (decision) {
                RoutingDecision.STORE -> {
                    connectedStorage?.let { storage ->
                        val stored = storage.store(routed)
                        // Storage will handle next calls
                    }
                }
                RoutingDecision.ATTENTION -> {
                    connectedAttention?.let { attention ->
                        attention.processAttention(routed)
                        // Attention will handle next calls
                    }
                    // Also store if storage connected
                    connectedStorage?.let { storage ->
                        storage.store(routed)
                    }
                }
                RoutingDecision.BATCH -> {
                    // Batch processing - send to CRDT stream
                    connectedCRDT?.let { crdt ->
                        crdt.publishUpdate(CRDTUpdate(
                            entityId = routed.blobId,
                            operation = CRDTOperation.ADD,
                            value = "batch-${routed.blobId}",
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
                else -> {
                    // Default to storage
                    connectedStorage?.let { storage ->
                        storage.store(routed)
                    }
                }
            }
            
            return routed
        }
    }
    
    /**
     * Get routed blob by ID
     */
    suspend fun getRoutedBlob(blobId: String): RoutedBlob? {
        mutex.withLock {
            return routedBlobs[blobId]
        }
    }
    
    private fun getDefaultDestinations(decision: RoutingDecision): Set<String> {
        return when (decision) {
            RoutingDecision.STORE -> setOf("storage")
            RoutingDecision.ATTENTION -> setOf("attention")
            RoutingDecision.BATCH -> setOf("batch")
            RoutingDecision.PRIORITY -> setOf("priority")
            RoutingDecision.DISCARD -> setOf("discard")
        }
    }
}

/**
 * CouchDB Storage - Stores blobs and provides change feeds
 */
class CouchDBStorageHexagon(
    private val databaseName: String
) {
    private val couchAPI = CouchDBAPI(databaseName)
    private val mutex = Mutex()
    private val storedBlobs = mutableMapOf<String, StoredBlob>()
    private val changeListeners = mutableListOf<(StorageChange) -> Unit>()
    private var connectedCRDT: CRDTHexagon? = null
    private var connectedDashboard: DashboardAnalyticsHexagon? = null
    
    /**
     * Tether to downstream components
     */
    fun connectCRDT(crdt: CRDTHexagon) {
        connectedCRDT = crdt
    }
    
    fun connectDashboard(dashboard: DashboardAnalyticsHexagon) {
        connectedDashboard = dashboard
    }
    
    /**
     * Store routed blob - calls next components directly
     */
    suspend fun store(routedBlob: RoutedBlob): StoredBlob {
        mutex.withLock {
            val documentData = JsonObject(mapOf(
                "blobId" to JsonPrimitive(routedBlob.blobId),
                "decision" to JsonPrimitive(routedBlob.decision.name),
                "destinations" to JsonArray(routedBlob.destinations.map { JsonPrimitive(it) }),
                "metadata" to JsonObject(routedBlob.metadata.mapValues { JsonPrimitive(it.value) }),
                "routedAt" to JsonPrimitive(routedBlob.routedAt),
                "storedAt" to JsonPrimitive(System.currentTimeMillis())
            ))
            
            val document = CouchDocument(
                id = "stored-${routedBlob.blobId}",
                revision = "1-initial",
                data = documentData
            )
            
            val created = couchAPI.createDocument(document)
            
            val stored = StoredBlob(
                blobId = routedBlob.blobId,
                documentId = created.id,
                status = StorageStatus.STORED
            )
            
            storedBlobs[routedBlob.blobId] = stored
            
            // REAL TETHER: Call CRDT to sync storage change
            connectedCRDT?.let { crdt ->
                crdt.applyCRDTOperation(CRDTData(
                    entityId = routedBlob.blobId,
                    operation = CRDTOperation.ADD,
                    value = "stored-${routedBlob.blobId}",
                    timestamp = System.currentTimeMillis()
                ))
            }
            
            // Emit change event
            val change = StorageChange(
                blobId = routedBlob.blobId,
                documentId = created.id,
                changeType = ChangeType.CREATED
            )
            emitChange(change)
            
            // REAL TETHER: Notify dashboard of storage change
            connectedDashboard?.let { dashboard ->
                dashboard.notifyStorageChange(change)
            }
            
            return stored
        }
    }
    
    /**
     * Get document by ID
     */
    suspend fun getDocument(id: String): CouchDocument {
        return couchAPI.getDocument(id)
    }
    
    /**
     * Query documents
     */
    suspend fun query(query: CouchQuery): List<CouchDocument> {
        return couchAPI.query(query)
    }
    
    /**
     * Subscribe to change feed
     */
    suspend fun subscribeToChanges(listener: (StorageChange) -> Unit) {
        mutex.withLock {
            changeListeners.add(listener)
        }
    }
    
    /**
     * Get stored blob by ID
     */
    suspend fun getStoredBlob(blobId: String): StoredBlob? {
        mutex.withLock {
            return storedBlobs[blobId]
        }
    }
    
    private fun emitChange(change: StorageChange) {
        changeListeners.forEach { listener ->
            try {
                listener(change)
            } catch (e: Exception) {
                // Log error but don't fail
            }
        }
    }
}

/**
 * Dashboard Analytics - Processes queries and analytics
 */
class DashboardAnalyticsHexagon {
    private val mutex = Mutex()
    private val queryHistory = mutableListOf<AnalyticsQuery>()
    private val userQueries = mutableListOf<UserQuery>()
    private val storageChanges = mutableListOf<StorageChange>()
    private val attentionEvents = mutableListOf<AttentionEvent>()
    
    /**
     * Receive storage change notification - called by Storage
     */
    suspend fun notifyStorageChange(change: StorageChange) {
        mutex.withLock {
            storageChanges.add(change)
            
            // Auto-generate analytics based on storage changes
            if (storageChanges.size % 10 == 0) {
                val analyticsQuery = AnalyticsQuery(
                    type = QueryType.STORAGE_USAGE,
                    timeRange = TimeRange.LAST_HOUR
                )
                processQuery(analyticsQuery)
            }
        }
    }
    
    /**
     * Receive attention event notification - called by Attention
     */
    suspend fun notifyAttentionEvent(event: AttentionEvent) {
        mutex.withLock {
            attentionEvents.add(event)
            
            // Auto-generate attention metrics
            if (attentionEvents.size % 5 == 0) {
                val analyticsQuery = AnalyticsQuery(
                    type = QueryType.ATTENTION_METRICS,
                    timeRange = TimeRange.LAST_24_HOURS
                )
                processQuery(analyticsQuery)
            }
        }
    }
    
    /**
     * Process analytics query
     */
    suspend fun processQuery(query: AnalyticsQuery): AnalyticsResult {
        mutex.withLock {
            queryHistory.add(query)
            
            val data = when (query.type) {
                QueryType.BLOB_COUNT -> {
                    val count = calculateBlobCount(query.filters, query.timeRange)
                    mapOf("count" to JsonPrimitive(count))
                }
                QueryType.STORAGE_USAGE -> {
                    val usage = calculateStorageUsage(query.timeRange)
                    mapOf(
                        "totalSize" to JsonPrimitive(usage.totalSize),
                        "documentCount" to JsonPrimitive(usage.documentCount)
                    )
                }
                QueryType.ATTENTION_METRICS -> {
                    val metrics = calculateAttentionMetrics(query.timeRange)
                    mapOf(
                        "averageAttention" to JsonPrimitive(metrics.average),
                        "peakAttention" to JsonPrimitive(metrics.peak)
                    )
                }
                QueryType.INGESTION_RATE -> {
                    val rate = calculateIngestionRate(query.timeRange)
                    mapOf("rate" to JsonPrimitive(rate))
                }
                QueryType.ERROR_RATE -> {
                    val errorRate = calculateErrorRate(query.timeRange)
                    mapOf("errorRate" to JsonPrimitive(errorRate))
                }
            }
            
            return AnalyticsResult(
                queryType = query.type,
                data = data
            )
        }
    }
    
    /**
     * Handle user query
     */
    suspend fun handleUserQuery(userQuery: UserQuery): UserQueryResponse {
        mutex.withLock {
            userQueries.add(userQuery)
            
            // Simple query processing
            val results = when {
                userQuery.query.contains("blobs") -> {
                    listOf(JsonObject(mapOf(
                        "type" to JsonPrimitive("blob"),
                        "count" to JsonPrimitive(42)
                    )))
                }
                userQuery.query.contains("today") -> {
                    listOf(JsonObject(mapOf(
                        "timeRange" to JsonPrimitive("today"),
                        "results" to JsonPrimitive(10)
                    )))
                }
                else -> {
                    listOf(JsonObject(mapOf(
                        "message" to JsonPrimitive("Query processed"),
                        "query" to JsonPrimitive(userQuery.query)
                    )))
                }
            }
            
            return UserQueryResponse(
                userId = userQuery.userId,
                status = QueryStatus.PROCESSED,
                results = results
            )
        }
    }
    
    /**
     * Get query history
     */
    suspend fun getQueryHistory(): List<AnalyticsQuery> {
        mutex.withLock {
            return queryHistory.toList()
        }
    }
    
    // Helper methods for calculations
    private fun calculateBlobCount(filters: Map<String, String>, timeRange: TimeRange): Int {
        // Mock implementation
        return when (timeRange) {
            TimeRange.LAST_HOUR -> 10
            TimeRange.LAST_24_HOURS -> 100
            TimeRange.LAST_WEEK -> 500
            TimeRange.LAST_MONTH -> 2000
            TimeRange.ALL_TIME -> 10000
        }
    }
    
    private fun calculateStorageUsage(timeRange: TimeRange): StorageUsage {
        return StorageUsage(
            totalSize = 1024 * 1024 * 100, // 100MB
            documentCount = 1000
        )
    }
    
    private fun calculateAttentionMetrics(timeRange: TimeRange): AttentionMetrics {
        return AttentionMetrics(
            average = 0.75,
            peak = 0.95
        )
    }
    
    private fun calculateIngestionRate(timeRange: TimeRange): Double {
        return 5.2 // blobs per second
    }
    
    private fun calculateErrorRate(timeRange: TimeRange): Double {
        return 0.02 // 2% error rate
    }
}

/**
 * Supporting data classes
 */
data class StorageUsage(
    val totalSize: Long,
    val documentCount: Int
)

data class AttentionMetrics(
    val average: Double,
    val peak: Double
)

/**
 * Attention - Tracks attention events
 */
class AttentionHexagon {
    private val attentionProtocol = AttentionProtocol()
    private val mutex = Mutex()
    private val attentionListeners = mutableListOf<(AttentionEvent) -> Unit>()
    private var connectedDashboard: DashboardAnalyticsHexagon? = null
    
    /**
     * Tether to dashboard
     */
    fun connectDashboard(dashboard: DashboardAnalyticsHexagon) {
        connectedDashboard = dashboard
    }
    
    /**
     * Process attention for routed blob - calls dashboard directly
     */
    suspend fun processAttention(routedBlob: RoutedBlob) {
        val intensity = calculateAttentionIntensity(routedBlob)
        
        val event = AttentionEvent(
            id = "attention-${routedBlob.blobId}",
            entityId = routedBlob.blobId,
            type = AttentionType.FOCUS,
            intensity = intensity,
            timestamp = System.currentTimeMillis()
        )
        
        attentionProtocol.recordAttention(event)
        
        // REAL TETHER: Call dashboard directly with attention event
        connectedDashboard?.let { dashboard ->
            dashboard.notifyAttentionEvent(event)
        }
        
        // Emit to listeners
        emitAttentionEvent(event)
    }
    
    /**
     * Get attention events for entity
     */
    suspend fun getAttentionEvents(entityId: String): List<AttentionEvent> {
        return attentionProtocol.getAttentionEvents(entityId)
    }
    
    /**
     * Subscribe to attention events
     */
    suspend fun subscribeToAttentionEvents(listener: (AttentionEvent) -> Unit) {
        mutex.withLock {
            attentionListeners.add(listener)
        }
    }
    
    private fun calculateAttentionIntensity(routedBlob: RoutedBlob): Double {
        // Mock calculation based on routing metadata
        return when (routedBlob.decision) {
            RoutingDecision.ATTENTION -> 0.9
            RoutingDecision.PRIORITY -> 0.7
            RoutingDecision.STORE -> 0.5
            else -> 0.3
        }
    }
    
    private fun emitAttentionEvent(event: AttentionEvent) {
        attentionListeners.forEach { listener ->
            try {
                listener(event)
            } catch (e: Exception) {
                // Log error but don't fail
            }
        }
    }
}

/**
 * CRDT - Syncs with storage
 */
class CRDTHexagon {
    private val crdtProtocol = CRDTProtocol<String>()
    private val mutex = Mutex()
    private var connectedStorage: CouchDBStorageHexagon? = null
    
    /**
     * Connect to storage hexagon
     */
    suspend fun connectToStorage(storage: CouchDBStorageHexagon) {
        mutex.withLock {
            connectedStorage = storage
        }
    }
    
    /**
     * Apply CRDT operation
     */
    suspend fun applyCRDTOperation(crdtData: CRDTData) {
        when (crdtData.operation) {
            CRDTOperation.ADD -> {
                crdtProtocol.add(crdtData.entityId, crdtData.value)
            }
            CRDTOperation.REMOVE -> {
                crdtProtocol.remove(crdtData.entityId, crdtData.value)
            }
            CRDTOperation.UPDATE -> {
                // Update is remove + add
                crdtProtocol.remove(crdtData.entityId, crdtData.value)
                crdtProtocol.add(crdtData.entityId, crdtData.value)
            }
        }
        
        // Sync to storage if connected
        connectedStorage?.let { storage ->
            syncToStorage(storage, crdtData)
        }
    }
    
    /**
     * Get CRDT data for entity
     */
    suspend fun getCRDTData(entityId: String): Set<String> {
        return crdtProtocol.get(entityId)
    }
    
    private suspend fun syncToStorage(storage: CouchDBStorageHexagon, crdtData: CRDTData) {
        try {
            val documentData = JsonObject(mapOf(
                "entityId" to JsonPrimitive(crdtData.entityId),
                "operation" to JsonPrimitive(crdtData.operation.name),
                "value" to JsonPrimitive(crdtData.value),
                "timestamp" to JsonPrimitive(crdtData.timestamp)
            ))
            
            val document = CouchDocument(
                id = crdtData.entityId,
                revision = "1-initial",
                data = documentData
            )
            
            storage.store(RoutedBlob(
                blobId = crdtData.entityId,
                decision = RoutingDecision.STORE,
                destinations = setOf("storage")
            ))
        } catch (e: Exception) {
            // Log error but don't fail CRDT operation
        }
    }
}

/**
 * CRDT Stream - Distributes CRDT updates
 */
class CRDTStreamHexagon {
    private val mutex = Mutex()
    private val updateListeners = mutableListOf<(CRDTUpdate) -> Unit>()
    private var connectedCRDT: CRDTHexagon? = null
    private var connectedStorage: CouchDBStorageHexagon? = null
    private var connectedDashboard: DashboardAnalyticsHexagon? = null
    
    /**
     * Tether to downstream components
     */
    fun connectCRDT(crdt: CRDTHexagon) {
        connectedCRDT = crdt
    }
    
    fun connectStorage(storage: CouchDBStorageHexagon) {
        connectedStorage = storage
    }
    
    fun connectDashboard(dashboard: DashboardAnalyticsHexagon) {
        connectedDashboard = dashboard
    }
    
    /**
     * Publish CRDT update - calls connected components directly
     */
    suspend fun publishUpdate(update: CRDTUpdate) {
        mutex.withLock {
            // Emit to all listeners
            updateListeners.forEach { listener ->
                try {
                    listener(update)
                } catch (e: Exception) {
                    // Log error but continue
                }
            }
            
            // REAL TETHER: Call connected components directly
            connectedCRDT?.let { crdt ->
                crdt.applyCRDTOperation(CRDTData(
                    entityId = update.entityId,
                    operation = update.operation,
                    value = update.value,
                    timestamp = update.timestamp
                ))
            }
            
            // Storage gets a routed blob for CRDT updates
            connectedStorage?.let { storage ->
                storage.store(RoutedBlob(
                    blobId = update.entityId,
                    decision = RoutingDecision.STORE,
                    destinations = setOf("storage"),
                    metadata = mapOf("source" to "crdt-stream")
                ))
            }
            
            // Dashboard gets notified of CRDT activity
            connectedDashboard?.let { dashboard ->
                dashboard.notifyStorageChange(StorageChange(
                    blobId = update.entityId,
                    documentId = "crdt-${update.entityId}",
                    changeType = ChangeType.UPDATED
                ))
            }
        }
    }
    
    /**
     * Subscribe to updates
     */
    suspend fun subscribeToUpdates(listener: (CRDTUpdate) -> Unit) {
        mutex.withLock {
            updateListeners.add(listener)
        }
    }
    
    /**
     * Add destination
     */
    suspend fun addDestination(name: String, destination: Any) {
        mutex.withLock {
            destinations[name] = destination
        }
    }
    
    /**
     * Get active destinations
     */
    suspend fun getActiveDestinations(): Set<String> {
        mutex.withLock {
            return destinations.keys.toSet()
        }
    }
}