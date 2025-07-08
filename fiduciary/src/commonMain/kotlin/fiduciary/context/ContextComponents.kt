package fiduciary.context

import fiduciary.hexagon.*
import fiduciary.protocol.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * Components refactored to use deliberate context APIs
 * Each component hooks up to specific context traits instead of direct coupling
 */

// === STORAGE COMPONENT WITH CONTEXT ===

/**
 * Storage component that provides StorageContext and uses other contexts
 */
class StorageComponent(
    private val databaseName: String
) : ContextProvider, ContextAware {
    
    private val couchAPI = CouchDBAPI(databaseName)
    private val mutex = Mutex()
    private val storedData = mutableMapOf<String, StorageData>()
    private val changeHandlers = mutableListOf<(StorageChange) -> Unit>()
    
    override val contextKeys = setOf(ContextKeys.ANALYTICS)
    override val providedContexts = mapOf(
        ContextKeys.STORAGE to StorageContextImpl()
    )
    
    private inner class StorageContextImpl : StorageContext {
        override suspend fun store(data: StorageData): StorageResult {
            return mutex.withLock {
                try {
                    val documentData = JsonObject(mapOf(
                        "id" to JsonPrimitive(data.id),
                        "content" to JsonPrimitive(data.content.decodeToString()),
                        "metadata" to JsonObject(data.metadata.mapValues { JsonPrimitive(it.value) }),
                        "timestamp" to JsonPrimitive(data.timestamp)
                    ))
                    
                    val document = CouchDocument(
                        id = data.id,
                        revision = "1-initial",
                        data = documentData
                    )
                    
                    val created = couchAPI.createDocument(document)
                    storedData[data.id] = data
                    
                    // Use analytics context to track storage event
                    val analyticsContext = ContextRegistry.get<AnalyticsContext>(ContextKeys.ANALYTICS)
                    analyticsContext?.track(AnalyticsEvent(
                        type = "storage.store",
                        entityId = data.id,
                        data = mapOf("documentId" to created.id)
                    ))
                    
                    // Emit change event
                    val change = StorageChange(
                        blobId = data.id,
                        documentId = created.id,
                        changeType = ChangeType.CREATED
                    )
                    changeHandlers.forEach { it(change) }
                    
                    StorageResult(
                        id = data.id,
                        success = true,
                        documentId = created.id
                    )
                } catch (e: Exception) {
                    StorageResult(
                        id = data.id,
                        success = false,
                        error = e.message
                    )
                }
            }
        }
        
        override suspend fun retrieve(id: String): StorageData? {
            return mutex.withLock {
                storedData[id]
            }
        }
        
        override suspend fun query(query: StorageQuery): List<StorageData> {
            return mutex.withLock {
                storedData.values.filter { data ->
                    query.selector.all { (key, value) ->
                        data.metadata[key] == value
                    }
                }.take(query.limit)
            }
        }
        
        override suspend fun onChange(handler: (StorageChange) -> Unit) {
            changeHandlers.add(handler)
        }
    }
}

// === ROUTING COMPONENT WITH CONTEXT ===

/**
 * Routing component that provides RoutingContext and uses other contexts
 */
class RoutingComponent : ContextProvider, ContextAware {
    
    private val mutex = Mutex()
    private val routingRules = mutableListOf<RoutingRule>()
    private val routeHandlers = mutableListOf<(RoutingResult) -> Unit>()
    
    override val contextKeys = setOf(ContextKeys.STORAGE, ContextKeys.ATTENTION, ContextKeys.ANALYTICS)
    override val providedContexts = mapOf(
        ContextKeys.ROUTING to RoutingContextImpl()
    )
    
    private inner class RoutingContextImpl : RoutingContext {
        override suspend fun route(data: RoutingData): RoutingResult {
            return mutex.withLock {
                // Convert to blob for rule matching
                val blob = IngestedBlob(
                    id = data.id,
                    data = data.content,
                    contentType = data.contentType,
                    status = BlobStatus.INGESTED,
                    metadata = data.metadata
                )
                
                // Find matching rule
                val matchingRule = routingRules.find { rule ->
                    rule.condition(blob)
                }
                
                val decision = matchingRule?.decision ?: RoutingDecision.STORE
                val destinations = matchingRule?.destinations?.ifEmpty { 
                    getDefaultDestinations(decision) 
                } ?: getDefaultDestinations(decision)
                
                val result = RoutingResult(
                    id = data.id,
                    decision = decision,
                    destinations = destinations,
                    metadata = data.metadata
                )
                
                // Use context APIs to handle routing decision
                when (decision) {
                    RoutingDecision.STORE -> {
                        val storageContext = ContextRegistry.get<StorageContext>(ContextKeys.STORAGE)
                        storageContext?.store(StorageData(
                            id = data.id,
                            content = data.content,
                            metadata = data.metadata + mapOf("routing" to "store")
                        ))
                    }
                    RoutingDecision.ATTENTION -> {
                        val attentionContext = ContextRegistry.get<AttentionContext>(ContextKeys.ATTENTION)
                        attentionContext?.track(AttentionData(
                            entityId = data.id,
                            type = AttentionType.FOCUS,
                            intensity = 0.8,
                            metadata = data.metadata
                        ))
                        
                        // Also store
                        val storageContext = ContextRegistry.get<StorageContext>(ContextKeys.STORAGE)
                        storageContext?.store(StorageData(
                            id = data.id,
                            content = data.content,
                            metadata = data.metadata + mapOf("routing" to "attention")
                        ))
                    }
                    RoutingDecision.BATCH -> {
                        val crdtContext = ContextRegistry.get<CRDTContext>(ContextKeys.CRDT)
                        crdtContext?.apply(CRDTOperation(
                            entityId = data.id,
                            operation = CRDTOperationType.ADD,
                            value = "batch-${data.id}"
                        ))
                    }
                    else -> {
                        // Default to storage
                        val storageContext = ContextRegistry.get<StorageContext>(ContextKeys.STORAGE)
                        storageContext?.store(StorageData(
                            id = data.id,
                            content = data.content,
                            metadata = data.metadata + mapOf("routing" to "default")
                        ))
                    }
                }
                
                // Track routing event
                val analyticsContext = ContextRegistry.get<AnalyticsContext>(ContextKeys.ANALYTICS)
                analyticsContext?.track(AnalyticsEvent(
                    type = "routing.route",
                    entityId = data.id,
                    data = mapOf("decision" to decision.name)
                ))
                
                // Emit to handlers
                routeHandlers.forEach { it(result) }
                
                result
            }
        }
        
        override suspend fun addRule(rule: RoutingRule) {
            mutex.withLock {
                routingRules.add(rule)
                routingRules.sortByDescending { it.priority }
            }
        }
        
        override suspend fun onRoute(handler: (RoutingResult) -> Unit) {
            routeHandlers.add(handler)
        }
        
        private fun getDefaultDestinations(decision: RoutingDecision): Set<String> {
            return when (decision) {
                RoutingDecision.STORE -> setOf("storage")
                RoutingDecision.ATTENTION -> setOf("attention", "storage")
                RoutingDecision.BATCH -> setOf("crdt")
                RoutingDecision.PRIORITY -> setOf("attention", "storage")
                RoutingDecision.DISCARD -> setOf("discard")
            }
        }
    }
}

// === ATTENTION COMPONENT WITH CONTEXT ===

/**
 * Attention component that provides AttentionContext and uses other contexts
 */
class AttentionComponent : ContextProvider, ContextAware {
    
    private val attentionProtocol = AttentionProtocol()
    private val mutex = Mutex()
    private val attentionEvents = mutableListOf<AttentionResult>()
    private val attentionHandlers = mutableListOf<(AttentionResult) -> Unit>()
    
    override val contextKeys = setOf(ContextKeys.ANALYTICS)
    override val providedContexts = mapOf(
        ContextKeys.ATTENTION to AttentionContextImpl()
    )
    
    private inner class AttentionContextImpl : AttentionContext {
        override suspend fun track(data: AttentionData): AttentionResult {
            return mutex.withLock {
                val event = AttentionEvent(
                    id = "attention-${data.entityId}",
                    entityId = data.entityId,
                    type = data.type,
                    intensity = data.intensity,
                    timestamp = System.currentTimeMillis()
                )
                
                attentionProtocol.recordAttention(event)
                
                val result = AttentionResult(
                    entityId = data.entityId,
                    tracked = true,
                    intensity = data.intensity
                )
                
                attentionEvents.add(result)
                
                // Use analytics context to track attention event
                val analyticsContext = ContextRegistry.get<AnalyticsContext>(ContextKeys.ANALYTICS)
                analyticsContext?.track(AnalyticsEvent(
                    type = "attention.track",
                    entityId = data.entityId,
                    data = mapOf("intensity" to data.intensity.toString())
                ))
                
                // Emit to handlers
                attentionHandlers.forEach { it(result) }
                
                result
            }
        }
        
        override suspend fun getMetrics(entityId: String): AttentionMetrics {
            return mutex.withLock {
                val entityEvents = attentionEvents.filter { it.entityId == entityId }
                AttentionMetrics(
                    entityId = entityId,
                    totalEvents = entityEvents.size,
                    averageIntensity = entityEvents.map { it.intensity }.average().takeIf { !it.isNaN() } ?: 0.0,
                    peakIntensity = entityEvents.maxOfOrNull { it.intensity } ?: 0.0
                )
            }
        }
        
        override suspend fun onAttention(handler: (AttentionResult) -> Unit) {
            attentionHandlers.add(handler)
        }
    }
}

// === INGESTION COMPONENT WITH CONTEXT ===

/**
 * Ingestion component that provides IngestionContext and uses other contexts
 */
class IngestionComponent : ContextProvider, ContextAware {
    
    private val mutex = Mutex()
    private val ingestedData = mutableMapOf<String, IngestionResult>()
    private val ingestHandlers = mutableListOf<(IngestionResult) -> Unit>()
    
    override val contextKeys = setOf(ContextKeys.ROUTING, ContextKeys.ANALYTICS)
    override val providedContexts = mapOf(
        ContextKeys.INGESTION to IngestionContextImpl()
    )
    
    private inner class IngestionContextImpl : IngestionContext {
        override suspend fun ingest(data: IngestionData): IngestionResult {
            return mutex.withLock {
                val id = "blob-${System.currentTimeMillis()}-${Random.nextInt(1000)}"
                
                val result = IngestionResult(
                    id = id,
                    success = true
                )
                
                ingestedData[id] = result
                
                // Use routing context to route the ingested data
                val routingContext = ContextRegistry.get<RoutingContext>(ContextKeys.ROUTING)
                routingContext?.route(RoutingData(
                    id = id,
                    content = data.content,
                    contentType = data.contentType,
                    metadata = data.metadata
                ))
                
                // Track ingestion event
                val analyticsContext = ContextRegistry.get<AnalyticsContext>(ContextKeys.ANALYTICS)
                analyticsContext?.track(AnalyticsEvent(
                    type = "ingestion.ingest",
                    entityId = id,
                    data = mapOf("contentType" to data.contentType)
                ))
                
                // Emit to handlers
                ingestHandlers.forEach { it(result) }
                
                result
            }
        }
        
        override suspend fun batch(data: List<IngestionData>): List<IngestionResult> {
            return data.map { ingest(it) }
        }
        
        override suspend fun onIngest(handler: (IngestionResult) -> Unit) {
            ingestHandlers.add(handler)
        }
    }
}

// === ANALYTICS COMPONENT WITH CONTEXT ===

/**
 * Analytics component that provides AnalyticsContext
 */
class AnalyticsComponent : ContextProvider {
    
    private val mutex = Mutex()
    private val events = mutableListOf<AnalyticsEvent>()
    private val metricHandlers = mutableListOf<(AnalyticsEvent) -> Unit>()
    
    override val providedContexts = mapOf(
        ContextKeys.ANALYTICS to AnalyticsContextImpl()
    )
    
    private inner class AnalyticsContextImpl : AnalyticsContext {
        override suspend fun track(event: AnalyticsEvent): AnalyticsResult {
            return mutex.withLock {
                events.add(event)
                
                // Emit to handlers
                metricHandlers.forEach { it(event) }
                
                AnalyticsResult(
                    tracked = true,
                    eventId = "${event.type}-${event.timestamp}"
                )
            }
        }
        
        override suspend fun query(query: AnalyticsQuery): AnalyticsQueryResult {
            return mutex.withLock {
                val filteredEvents = events.filter { event ->
                    query.filters.all { (key, value) ->
                        when (key) {
                            "type" -> event.type == value
                            "entityId" -> event.entityId == value
                            else -> event.data[key] == value
                        }
                    }
                }
                
                val results = filteredEvents.take(query.limit).map { event ->
                    mapOf(
                        "type" to event.type,
                        "entityId" to event.entityId,
                        "timestamp" to event.timestamp.toString()
                    ) + event.data
                }
                
                AnalyticsQueryResult(
                    results = results,
                    totalCount = filteredEvents.size
                )
            }
        }
        
        override suspend fun onMetric(handler: (AnalyticsEvent) -> Unit) {
            metricHandlers.add(handler)
        }
    }
}

// === CRDT COMPONENT WITH CONTEXT ===

/**
 * CRDT component that provides CRDTContext and uses other contexts
 */
class CRDTComponent : ContextProvider, ContextAware {
    
    private val crdtProtocol = CRDTProtocol<String>()
    private val mutex = Mutex()
    private val updateHandlers = mutableListOf<(CRDTResult) -> Unit>()
    
    override val contextKeys = setOf(ContextKeys.STORAGE, ContextKeys.ANALYTICS)
    override val providedContexts = mapOf(
        ContextKeys.CRDT to CRDTContextImpl()
    )
    
    private inner class CRDTContextImpl : CRDTContext {
        override suspend fun apply(operation: CRDTOperation): CRDTResult {
            return mutex.withLock {
                val result = when (operation.operation) {
                    CRDTOperationType.ADD -> {
                        crdtProtocol.add(operation.entityId, operation.value)
                        CRDTResult(
                            entityId = operation.entityId,
                            success = true,
                            newState = crdtProtocol.get(operation.entityId)
                        )
                    }
                    CRDTOperationType.REMOVE -> {
                        crdtProtocol.remove(operation.entityId, operation.value)
                        CRDTResult(
                            entityId = operation.entityId,
                            success = true,
                            newState = crdtProtocol.get(operation.entityId)
                        )
                    }
                    CRDTOperationType.UPDATE -> {
                        crdtProtocol.remove(operation.entityId, operation.value)
                        crdtProtocol.add(operation.entityId, operation.value)
                        CRDTResult(
                            entityId = operation.entityId,
                            success = true,
                            newState = crdtProtocol.get(operation.entityId)
                        )
                    }
                }
                
                // Use storage context to persist CRDT state
                val storageContext = ContextRegistry.get<StorageContext>(ContextKeys.STORAGE)
                storageContext?.store(StorageData(
                    id = "crdt-${operation.entityId}",
                    content = result.newState.joinToString(",").toByteArray(),
                    metadata = mapOf("type" to "crdt-state", "operation" to operation.operation.name)
                ))
                
                // Track CRDT operation
                val analyticsContext = ContextRegistry.get<AnalyticsContext>(ContextKeys.ANALYTICS)
                analyticsContext?.track(AnalyticsEvent(
                    type = "crdt.apply",
                    entityId = operation.entityId,
                    data = mapOf("operation" to operation.operation.name)
                ))
                
                // Emit to handlers
                updateHandlers.forEach { it(result) }
                
                result
            }
        }
        
        override suspend fun get(entityId: String): CRDTState {
            return mutex.withLock {
                CRDTState(
                    entityId = entityId,
                    values = crdtProtocol.get(entityId),
                    vectorClock = emptyMap() // Simplified
                )
            }
        }
        
        override suspend fun sync(data: CRDTSyncData): CRDTSyncResult {
            return mutex.withLock {
                var conflicts = 0
                data.operations.forEach { operation ->
                    val result = apply(operation)
                    if (result.conflicts.isNotEmpty()) {
                        conflicts += result.conflicts.size
                    }
                }
                
                CRDTSyncResult(
                    entityId = data.entityId,
                    synced = true,
                    conflictsResolved = conflicts
                )
            }
        }
        
        override suspend fun onUpdate(handler: (CRDTResult) -> Unit) {
            updateHandlers.add(handler)
        }
    }
}