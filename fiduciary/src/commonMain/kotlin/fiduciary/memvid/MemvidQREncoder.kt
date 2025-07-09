package fiduciary.memvid

import fiduciary.attention.*
import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.BufferedWriter

/**
 * Memvid QR Encoder - Encodes attention events as text chunks for Memvid
 * 
 * Memvid stores text as QR codes in MP4 files, so we convert attention
 * events to searchable text chunks
 */
class MemvidQREncoder(
    private val workDir: String = "./memvid-data"
) {
    init {
        File(workDir).mkdirs()
    }
    
    private val chunks = mutableListOf<MemvidChunk>()
    private val json = Json { prettyPrint = true }
    
    /**
     * Add attention event as text chunk
     */
    fun addAttentionEvent(event: AttentionEvent) {
        val chunk = when (event) {
            is AttentionEvent.DocumentFocus -> createDocumentFocusChunk(event)
            is AttentionEvent.ConceptExtraction -> createConceptExtractionChunk(event)
            is AttentionEvent.CorpusScan -> createCorpusScanChunk(event)
            else -> null
        }
        
        chunk?.let { chunks.add(it) }
    }
    
    /**
     * Create chunk for document focus event
     */
    private fun createDocumentFocusChunk(event: AttentionEvent.DocumentFocus): MemvidChunk {
        val text = buildString {
            appendLine("Document Focus: ${event.docId}")
            appendLine("Range: ${event.range.a}-${event.range.b}")
            appendLine("Duration: ${event.duration}ms")
            appendLine("Intensity: ${event.intensity}")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
        }
        
        return MemvidChunk(
            id = "doc_focus_${event.docId}_${System.currentTimeMillis()}",
            text = text,
            metadata = mapOf(
                "type" to "document_focus",
                "docId" to event.docId,
                "intensity" to event.intensity.toString()
            ),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Create chunk for concept extraction event
     */
    private fun createConceptExtractionChunk(event: AttentionEvent.ConceptExtraction): MemvidChunk {
        val relatedConcepts = (0 until event.relatedConcepts.a).map { 
            event.relatedConcepts.b(it) 
        }
        
        val text = buildString {
            appendLine("Concept Extraction: ${event.conceptId}")
            appendLine("Source: ${event.sourceDoc}")
            appendLine("Confidence: ${event.confidence}")
            appendLine("Related Concepts: ${relatedConcepts.joinToString(", ")}")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
        }
        
        return MemvidChunk(
            id = "concept_${event.conceptId}_${System.currentTimeMillis()}",
            text = text,
            metadata = mapOf(
                "type" to "concept_extraction",
                "conceptId" to event.conceptId,
                "sourceDoc" to event.sourceDoc,
                "confidence" to event.confidence.toString(),
                "relatedConcepts" to relatedConcepts.joinToString(",")
            ),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Create chunk for corpus scan event
     */
    private fun createCorpusScanChunk(event: AttentionEvent.CorpusScan): MemvidChunk {
        val progress = (event.scannedBytes.toDouble() / event.totalBytes * 100).toInt()
        
        val text = buildString {
            appendLine("Corpus Scan: ${event.corpusId}")
            appendLine("Progress: $progress% (${event.scannedBytes}/${event.totalBytes} bytes)")
            appendLine("Attention Score: ${event.attentionScore}")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
        }
        
        return MemvidChunk(
            id = "corpus_scan_${event.corpusId}_${System.currentTimeMillis()}",
            text = text,
            metadata = mapOf(
                "type" to "corpus_scan",
                "corpusId" to event.corpusId,
                "progress" to progress.toString(),
                "attentionScore" to event.attentionScore.toString()
            ),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Write chunks to file for Memvid processing
     */
    fun writeChunksFile(): String {
        val chunksFile = File(workDir, "attention_chunks.jsonl")
        
        chunksFile.bufferedWriter().use { writer ->
            chunks.forEach { chunk ->
                val jsonLine = json.encodeToString(MemvidChunk.serializer(), chunk)
                writer.write(jsonLine)
                writer.newLine()
            }
        }
        
        return chunksFile.absolutePath
    }
    
    /**
     * Create Python script to build Memvid video
     */
    fun createMemvidScript(videoPath: String, indexPath: String): String {
        val scriptFile = File(workDir, "build_memvid.py")
        
        scriptFile.writeText("""
import json
from memvid import MemvidEncoder

# Load chunks
chunks = []
with open('${writeChunksFile()}', 'r') as f:
    for line in f:
        chunk = json.loads(line)
        chunks.append(chunk['text'])

# Create Memvid encoder
encoder = MemvidEncoder()

# Add all chunks
encoder.add_chunks(chunks)

# Build video
encoder.build_video('$videoPath', '$indexPath')

print(f"Built Memvid video: {videoPath}")
print(f"Index saved to: {indexPath}")
        """.trimIndent())
        
        return scriptFile.absolutePath
    }
    
    /**
     * Get current chunks for inspection
     */
    fun getChunks(): List<MemvidChunk> = chunks.toList()
    
    /**
     * Clear chunks
     */
    fun clearChunks() = chunks.clear()
}

/**
 * Memvid chunk data
 */
@Serializable
data class MemvidChunk(
    val id: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long
)

/**
 * Memvid search interface
 */
class MemvidSearcher(
    private val videoPath: String,
    private val indexPath: String
) {
    /**
     * Create Python script for searching Memvid
     */
    fun createSearchScript(query: String): String {
        val scriptContent = """
from memvid import MemvidChat

# Initialize Memvid chat
chat = MemvidChat('$videoPath', '$indexPath')

# Search for: $query
response = chat.chat('$query')

print("Query:", '$query')
print("Response:", response)
        """.trimIndent()
        
        return scriptContent
    }
}