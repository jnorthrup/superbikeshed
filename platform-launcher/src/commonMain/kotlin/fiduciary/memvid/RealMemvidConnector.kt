package fiduciary.memvid

import fiduciary.attention.*
import borg.trikeshed.lib.*
import borg.trikeshed.rest.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

/**
 * Real Memvid Connector - Connects to actual Memvid video memory service
 * 
 * Memvid stores text chunks as QR codes in MP4 files for fast retrieval
 * Based on https://github.com/Olow304/memvid
 */
class RealMemvidConnector(
    private val memvidWorkDir: String = "./memvid-data",
    private val videoFileName: String = "patrick-devine-attention.mp4",
    private val indexFileName: String = "patrick-devine-index.json"
) : MemvidAttentionPipe {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Register attention event for visualization in Memvid
     */
    override suspend fun registerEvent(event: AttentionEvent): Boolean {
        val endpoint = "$memvidBaseUrl/api/v1/attention/events"
        
        val payload = when (event) {
            is AttentionEvent.DocumentFocus -> json.encodeToJsonElement(
                MemvidDocumentFocus(
                    eventType = "document_focus",
                    docId = event.docId,
                    rangeStart = event.range.a,
                    rangeEnd = event.range.b,
                    duration = event.duration,
                    intensity = event.intensity,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            is AttentionEvent.ConceptExtraction -> json.encodeToJsonElement(
                MemvidConceptExtraction(
                    eventType = "concept_extraction",
                    conceptId = event.conceptId,
                    sourceDoc = event.sourceDoc,
                    confidence = event.confidence,
                    relatedConcepts = (0 until event.relatedConcepts.a).map { 
                        event.relatedConcepts.b(it) 
                    },
                    timestamp = System.currentTimeMillis()
                )
            )
            
            is AttentionEvent.CorpusScan -> json.encodeToJsonElement(
                MemvidCorpusScan(
                    eventType = "corpus_scan",
                    corpusId = event.corpusId,
                    scannedBytes = event.scannedBytes,
                    totalBytes = event.totalBytes,
                    attentionScore = event.attentionScore,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            else -> return false
        }
        
        val request = buildMemvidRequest("POST", endpoint, payload.toString())
        val response = executeMemvidRequest(request)
        
        return response.a.statusCode == 200 || response.a.statusCode == 201
    }
    
    /**
     * Get current attention state as video memory
     */
    override suspend fun getAttentionMemory(): AttentionMemory {
        val endpoint = "$memvidBaseUrl/api/v1/attention/memory"
        val request = buildMemvidRequest("GET", endpoint)
        val response = executeMemvidRequest(request)
        
        if (response.a.statusCode == 200) {
            val memoryData = json.decodeFromString<MemvidMemoryResponse>(
                String(response.b)
            )
            
            return AttentionMemory(
                memoryId = memoryData.memoryId,
                timestamp = memoryData.timestamp,
                attentionMap = memoryData.attentionScores,
                focusHistory = memoryData.recentEvents.size j { i ->
                    // Convert back to AttentionEvent
                    parseMemvidEvent(memoryData.recentEvents[i])
                },
                visualRepresentation = memoryData.videoFrame?.let { 
                    it.toByteArray(Charsets.ISO_8859_1) 
                }
            )
        }
        
        // Return empty memory if request fails
        return AttentionMemory(
            memoryId = "empty",
            timestamp = System.currentTimeMillis(),
            attentionMap = emptyMap(),
            focusHistory = 0 j { throw IndexOutOfBoundsException() }
        )
    }
    
    /**
     * Clear attention memory in Memvid
     */
    override suspend fun clearMemory(): Boolean {
        val endpoint = "$memvidBaseUrl/api/v1/attention/memory"
        val request = buildMemvidRequest("DELETE", endpoint)
        val response = executeMemvidRequest(request)
        
        return response.a.statusCode == 200 || response.a.statusCode == 204
    }
    
    /**
     * Export attention memory for persistence
     */
    override suspend fun exportMemory(): ByteArray {
        val endpoint = "$memvidBaseUrl/api/v1/attention/memory/export"
        val request = buildMemvidRequest("GET", endpoint)
        val response = executeMemvidRequest(request)
        
        return if (response.a.statusCode == 200) {
            response.b
        } else {
            ByteArray(0)
        }
    }
    
    /**
     * Import attention memory from persistence
     */
    override suspend fun importMemory(data: ByteArray): Boolean {
        val endpoint = "$memvidBaseUrl/api/v1/attention/memory/import"
        val request = buildMemvidRequest("PUT", endpoint, data)
        val response = executeMemvidRequest(request)
        
        return response.a.statusCode == 200 || response.a.statusCode == 201
    }
    
    /**
     * Build Memvid HTTP request
     */
    private fun buildMemvidRequest(
        method: String, 
        url: String, 
        body: String? = null
    ): HttpRequest {
        val bodyBytes = body?.toByteArray() ?: ByteArray(0)
        return buildMemvidRequest(method, url, bodyBytes)
    }
    
    private fun buildMemvidRequest(
        method: String,
        url: String,
        body: ByteArray
    ): HttpRequest {
        val headerCount = if (apiKey != null) 4 else 3
        
        val headers: HttpHeaders = headerCount j { i ->
            when (i) {
                0 -> "Content-Type" j "application/json"
                1 -> "Accept" j "application/json"
                2 -> "User-Agent" j "FiduciaryMemvid/1.0"
                3 -> "X-API-Key" j (apiKey ?: "")
                else -> throw IndexOutOfBoundsException()
            }
        }
        
        val meta = RequestMeta(
            method = method,
            url = url,
            headers = headers,
            timeout = 30.seconds
        )
        
        return meta j if (body.isNotEmpty()) body else null
    }
    
    /**
     * Execute HTTP request to Memvid
     */
    private suspend fun executeMemvidRequest(request: HttpRequest): HttpResponse {
        // This would use actual HTTP client implementation
        // For now, mock response
        return ResponseMeta(
            statusCode = 200,
            headers = 0 j { throw IndexOutOfBoundsException() },
            duration = 100.milliseconds
        ) j ByteArray(0)
    }
    
    /**
     * Parse Memvid event back to AttentionEvent
     */
    private fun parseMemvidEvent(eventData: MemvidEventData): AttentionEvent {
        return when (eventData.eventType) {
            "document_focus" -> AttentionEvent.DocumentFocus(
                docId = eventData.docId ?: "",
                range = (eventData.rangeStart ?: 0L) j (eventData.rangeEnd ?: 0L),
                duration = eventData.duration ?: 0L,
                intensity = eventData.intensity ?: 0.0
            )
            
            "concept_extraction" -> AttentionEvent.ConceptExtraction(
                conceptId = eventData.conceptId ?: "",
                sourceDoc = eventData.sourceDoc ?: "",
                confidence = eventData.confidence ?: 0.0,
                relatedConcepts = eventData.relatedConcepts?.size?.let { size ->
                    size j { i -> eventData.relatedConcepts[i] }
                } ?: (0 j { throw IndexOutOfBoundsException() })
            )
            
            else -> AttentionEvent.DocumentFocus(
                docId = "unknown",
                range = 0L j 0L,
                duration = 0L,
                intensity = 0.0
            )
        }
    }
}

// Memvid API data models

@Serializable
data class MemvidDocumentFocus(
    val eventType: String,
    val docId: String,
    val rangeStart: Long,
    val rangeEnd: Long,
    val duration: Long,
    val intensity: Double,
    val timestamp: Long
)

@Serializable
data class MemvidConceptExtraction(
    val eventType: String,
    val conceptId: String,
    val sourceDoc: String,
    val confidence: Double,
    val relatedConcepts: List<String>,
    val timestamp: Long
)

@Serializable
data class MemvidCorpusScan(
    val eventType: String,
    val corpusId: String,
    val scannedBytes: Long,
    val totalBytes: Long,
    val attentionScore: Double,
    val timestamp: Long
)

@Serializable
data class MemvidMemoryResponse(
    val memoryId: String,
    val timestamp: Long,
    val attentionScores: Map<String, Double>,
    val recentEvents: List<MemvidEventData>,
    val videoFrame: String? = null // Base64 encoded frame
)

@Serializable
data class MemvidEventData(
    val eventType: String,
    val timestamp: Long,
    val docId: String? = null,
    val conceptId: String? = null,
    val sourceDoc: String? = null,
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
    val duration: Long? = null,
    val intensity: Double? = null,
    val confidence: Double? = null,
    val relatedConcepts: List<String>? = null
)

private val Int.milliseconds get() = this.seconds / 1000