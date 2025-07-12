package fiduciary.attention

import borg.trikeshed.lib.*
import borg.trikeshed.attention.NormalizedAttention
import borg.trikeshed.attention.ContextualAttention
import borg.trikeshed.io.IOContext
import fiduciary.metaverse.CouchDBService
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock

/**
 * Memvid Attention Bridge
 * 
 * Connects fiduciary attention system with memvid video-memory side-pipe.
 * Works forward from fiduciary attention and backward from memvid requirements.
 * 
 * Memvid provides:
 * - Video memory as side-pipe for attention tracking
 * - Visual representation of attention flows
 * - Memory persistence across sessions
 * - Real-time attention visualization
 */

// === Forward from Fiduciary Attention ===

/**
 * Attention events that can be visualized via memvid
 */
sealed interface AttentionEvent {
    data class DocumentFocus(
        val docId: String,
        val range: Twin<Long>,
        val duration: Long, // milliseconds
        val intensity: Double // 0.0 to 1.0
    ) : AttentionEvent
    
    data class CorpusScan(
        val corpusId: String,
        val scannedBytes: Long,
        val totalBytes: Long,
        val attentionScore: Double
    ) : AttentionEvent
    
    data class ConceptExtraction(
        val conceptId: String,
        val sourceDoc: String,
        val confidence: Double,
        val relatedConcepts: Indexed<String>
    ) : AttentionEvent
    
    data class FiduciaryAction(
        val actionType: String,
        val targetId: String,
        val obligation: String,
        val timestamp: Long
    ) : AttentionEvent
}

/**
 * Attention stream that feeds memvid visualization
 */
data class AttentionStream(
    val streamId: String,
    val events: Indexed<AttentionEvent>,
    val metadata: Map<String, Any> = emptyMap()
)

// === Backward from Memvid Requirements ===

/**
 * Memvid integration interface
 * Provides video-memory side-pipe for attention tracking
 */
interface MemvidAttentionPipe {
    /**
     * Register attention event for visualization
     */
    suspend fun registerEvent(event: AttentionEvent): Boolean
    
    /**
     * Get current attention state as video memory
     */
    suspend fun getAttentionMemory(): AttentionMemory
    
    /**
     * Clear attention memory
     */
    suspend fun clearMemory(): Boolean
    
    /**
     * Export attention memory for persistence
     */
    suspend fun exportMemory(): ByteArray
    
    /**
     * Import attention memory from persistence
     */
    suspend fun importMemory(data: ByteArray): Boolean
}

/**
 * Attention memory structure for memvid
 */
data class AttentionMemory(
    val memoryId: String,
    val timestamp: Long,
    val attentionMap: Map<String, Double>, // docId -> attention score
    val focusHistory: Indexed<AttentionEvent>,
    val visualRepresentation: ByteArray? = null // Optional video data
)

// === Provenance Event Document for CouchDB ===
@Serializable
data class ProvenanceEventDoc(
    val type: String = "provenance_event",
    val event_id: String,
    val timestamp: Long,
    val source_file: String? = null,
    val parent_file: String? = null,
    val entity_id: String? = null,
    val action_type: String,
    val details: String? = null,
    val links: List<String> = emptyList()
)

// === CouchDB Provenance Writer ===
suspend fun writeProvenanceEvent(
    couchDbService: CouchDBService,
    dbName: String,
    event: ProvenanceEventDoc
) {
    val doc = borg.trikeshed.couchdb.CouchDocument(
        id = event.event_id,
        data = kotlinx.serialization.json.Json.encodeToJsonElement(ProvenanceEventDoc.serializer(), event).jsonObject
    )
    couchDbService.saveDocument(dbName, doc)
}

// === Bridge Implementation ===

/**
 * Bridge between fiduciary attention and memvid
 */
class FiduciaryMemvidBridge(
    internal val memvidPipe: MemvidAttentionPipe,
    internal val couchDbService: CouchDBService? = null, // Optional for provenance
    internal val provenanceDb: String = "provenance"
) {
    
    /**
     * Convert fiduciary attention to memvid event
     */
    fun attentionToEvent(attention: FiduciaryAttention): AttentionEvent {
        return when (attention.document) {
            is DocumentAttention -> AttentionEvent.DocumentFocus(
                docId = attention.document.doc.component1().toString(),
                range = attention.document.doc.component2(),
                duration = 1000L, // Default 1 second
                intensity = calculateIntensity(attention)
            )
            is CorpusAttention -> AttentionEvent.CorpusScan(
                corpusId = attention.document.corpus.component1(),
                scannedBytes = attention.document.corpus.component2().component1(),
                totalBytes = attention.document.corpus.component2().component2(),
                attentionScore = calculateIntensity(attention)
            )
            else -> AttentionEvent.FiduciaryAction(
                actionType = "unknown",
                targetId = "unknown",
                obligation = "fiduciary_attention",
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Process attention through memvid pipe
     */
    suspend fun processAttention(attention: FiduciaryAttention): Boolean {
        val event = attentionToEvent(attention)
        // Write provenance if CouchDB is available
        couchDbService?.let {
            val provDoc = ProvenanceEventDoc(
                event_id = "event-${System.currentTimeMillis()}",
                timestamp = Clock.System.now().toEpochMilliseconds(),
                source_file = attention.context.metadata.getProperty("source_file", ""),
                parent_file = attention.context.metadata.getProperty("parent_file", ""),
                entity_id = attention.context.metadata.getProperty("entity_id", ""),
                action_type = when (event) {
                    is AttentionEvent.DocumentFocus -> "document_focus"
                    is AttentionEvent.CorpusScan -> "corpus_scan"
                    is AttentionEvent.ConceptExtraction -> "concept_extraction"
                    is AttentionEvent.FiduciaryAction -> event.actionType
                },
                details = event.toString(),
                links = emptyList() // Extend as needed
            )
            writeProvenanceEvent(it, provenanceDb, provDoc)
        }
        return memvidPipe.registerEvent(event)
    }
    
    /**
     * Get attention memory from memvid
     */
    suspend fun getAttentionMemory(): AttentionMemory {
        return memvidPipe.getAttentionMemory()
    }
    
    /**
     * Calculate attention intensity from fiduciary context
     */
    internal fun calculateIntensity(attention: FiduciaryAttention): Double {
        // Extract intensity from metadata or context
        val metadata = attention.context.metadata
        return metadata.getProperty("intensity", "1.0").toDoubleOrNull() ?: 1.0
    }
}

// === Integration with Existing Fiduciary Attention ===

/**
 * Extend FiduciaryAttentionContext with memvid integration
 */
suspend fun FiduciaryAttentionContext.processWithMemvid(
    memvidBridge: FiduciaryMemvidBridge
): Boolean {
    val fiduciaryAttention = FiduciaryAttention(
        document = DocumentAttention(0L j 1000L, "application/pdf"),
        context = this
    )
    
    return memvidBridge.processAttention(fiduciaryAttention)
}

/**
 * Create attention stream from fiduciary operations
 */
suspend fun createAttentionStream(
    operations: Indexed<FiduciaryAttention>,
    memvidBridge: FiduciaryMemvidBridge
): AttentionStream {
    val events = operations.α { attention ->
        memvidBridge.attentionToEvent(attention)
    }
    
    return AttentionStream(
        streamId = "fiduciary-${System.currentTimeMillis()}",
        events = events,
        metadata = mapOf("source" to "fiduciary_attention")
    )
}

// === TDD Test Structure ===

/**
 * TDD test for memvid attention bridge
 */
@kotlin.test.Test
fun testMemvidAttentionBridge() {
    // TODO: Implement TDD test for memvid integration
    // 1. Test attention event conversion
    // 2. Test memvid pipe integration
    // 3. Test attention memory persistence
    // 4. Test visual representation generation
}

/**
 * TDD test for attention stream creation
 */
@kotlin.test.Test
fun testAttentionStreamCreation() {
    // TODO: Implement TDD test for attention stream
    // 1. Test stream creation from fiduciary operations
    // 2. Test event ordering and timing
    // 3. Test metadata preservation
    // 4. Test stream persistence
} 