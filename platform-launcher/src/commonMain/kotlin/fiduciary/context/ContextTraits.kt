package fiduciary.context

import fiduciary.hexagon.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

/**
 * Context API Traits - Deliberate interfaces for component integration
 * Each component hooks up to these context APIs rather than direct coupling
 */

// === STORAGE CONTEXT TRAIT ===

/**
 * Storage context - provides storage operations to any component
 */
interface StorageContext {
    suspend fun store(data: StorageData): StorageResult
    suspend fun retrieve(id: String): StorageData?
    suspend fun query(query: StorageQuery): List<StorageData>
    suspend fun onChange(handler: (StorageChange) -> Unit)
}

@Serializable
data class StorageData(
    val id: String,
    val content: ByteArray,
    val metadata: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as StorageData
        return id == other.id && content.contentEquals(other.content) && 
               metadata == other.metadata && timestamp == other.timestamp
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

@Serializable
data class StorageResult(
    val id: String,
    val success: Boolean,
    val documentId: String? = null,
    val error: String? = null
)

@Serializable
data class StorageQuery(
    val selector: Map<String, String>,
    val limit: Int = 100
)

// === ROUTING CONTEXT TRAIT ===

/**
 * Routing context - provides routing decisions to any component
 */
interface RoutingContext {
    suspend fun route(data: RoutingData): RoutingResult
    suspend fun addRule(rule: RoutingRule)
    suspend fun onRoute(handler: (RoutingResult) -> Unit)
}

@Serializable
data class RoutingData(
    val id: String,
    val content: ByteArray,
    val contentType: String,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as RoutingData
        return id == other.id && content.contentEquals(other.content) && 
               contentType == other.contentType && metadata == other.metadata
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

@Serializable
data class RoutingResult(
    val id: String,
    val decision: RoutingDecision,
    val destinations: Set<String>,
    val metadata: Map<String, String> = emptyMap()
)

// === ATTENTION CONTEXT TRAIT ===

/**
 * Attention context - provides attention tracking to any component
 */
interface AttentionContext {
    suspend fun track(data: AttentionData): AttentionResult
    suspend fun getMetrics(entityId: String): AttentionMetrics
    suspend fun onAttention(handler: (AttentionResult) -> Unit)
}

@Serializable
data class AttentionData(
    val entityId: String,
    val type: AttentionType,
    val intensity: Double,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class AttentionResult(
    val entityId: String,
    val tracked: Boolean,
    val intensity: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AttentionMetrics(
    val entityId: String,
    val totalEvents: Int,
    val averageIntensity: Double,
    val peakIntensity: Double
)

// === CRDT CONTEXT TRAIT ===

/**
 * CRDT context - provides distributed state management to any component
 */
interface CRDTContext {
    suspend fun apply(operation: CRDTOperation): CRDTResult
    suspend fun get(entityId: String): CRDTState
    suspend fun sync(data: CRDTSyncData): CRDTSyncResult
    suspend fun onUpdate(handler: (CRDTResult) -> Unit)
}

@Serializable
data class CRDTOperation(
    val entityId: String,
    val operation: CRDTOperationType,
    val value: String,
    val timestamp: Long = System.currentTimeMillis(),
    val vectorClock: Map<String, Long> = emptyMap()
)

@Serializable
enum class CRDTOperationType { ADD, REMOVE, UPDATE }

@Serializable
data class CRDTResult(
    val entityId: String,
    val success: Boolean,
    val newState: Set<String>,
    val conflicts: List<String> = emptyList()
)

@Serializable
data class CRDTState(
    val entityId: String,
    val values: Set<String>,
    val vectorClock: Map<String, Long>
)

@Serializable
data class CRDTSyncData(
    val entityId: String,
    val operations: List<CRDTOperation>
)

@Serializable
data class CRDTSyncResult(
    val entityId: String,
    val synced: Boolean,
    val conflictsResolved: Int
)

// === ANALYTICS CONTEXT TRAIT ===

/**
 * Analytics context - provides analytics and metrics to any component
 */
interface AnalyticsContext {
    suspend fun track(event: AnalyticsEvent): AnalyticsResult
    suspend fun query(query: AnalyticsQuery): AnalyticsQueryResult
    suspend fun onMetric(handler: (AnalyticsEvent) -> Unit)
}

@Serializable
data class AnalyticsEvent(
    val type: String,
    val entityId: String,
    val data: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AnalyticsResult(
    val tracked: Boolean,
    val eventId: String
)

@Serializable
data class AnalyticsQueryResult(
    val results: List<Map<String, String>>,
    val totalCount: Int
)

// === INGESTION CONTEXT TRAIT ===

/**
 * Ingestion context - provides data ingestion to any component
 */
interface IngestionContext {
    suspend fun ingest(data: IngestionData): IngestionResult
    suspend fun batch(data: List<IngestionData>): List<IngestionResult>
    suspend fun onIngest(handler: (IngestionResult) -> Unit)
}

@Serializable
data class IngestionData(
    val content: ByteArray,
    val contentType: String,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as IngestionData
        return content.contentEquals(other.content) && 
               contentType == other.contentType && metadata == other.metadata
    }
    
    override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

@Serializable
data class IngestionResult(
    val id: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// === CONTEXT REGISTRY ===

/**
 * Context registry - central place where components register their contexts
 */
object ContextRegistry {
    private val contexts = mutableMapOf<String, Any>()
    
    fun <T> register(key: String, context: T) {
        contexts[key] = context as Any
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        return contexts[key] as? T
    }
    
    fun <T> require(key: String): T {
        return get<T>(key) ?: throw IllegalStateException("Context not found: $key")
    }
    
    fun clear() {
        contexts.clear()
    }
    
    fun getRegisteredContexts(): Set<String> {
        return contexts.keys.toSet()
    }
}

// === CONTEXT KEYS ===

/**
 * Well-known context keys for component integration
 */
object ContextKeys {
    const val STORAGE = "storage"
    const val ROUTING = "routing"
    const val ATTENTION = "attention"
    const val CRDT = "crdt"
    const val ANALYTICS = "analytics"
    const val INGESTION = "ingestion"
}

// === CONTEXT AWARE TRAIT ===

/**
 * Base trait for components that use context APIs
 */
interface ContextAware {
    val contextKeys: Set<String>
    
    fun initializeContexts() {
        contextKeys.forEach { key ->
            val context = ContextRegistry.get<Any>(key)
            if (context == null) {
                throw IllegalStateException("Required context not available: $key")
            }
        }
    }
}

// === CONTEXT PROVIDER TRAIT ===

/**
 * Base trait for components that provide context APIs
 */
interface ContextProvider {
    val providedContexts: Map<String, Any>
    
    fun registerContexts() {
        providedContexts.forEach { (key, context) ->
            ContextRegistry.register(key, context)
        }
    }
}