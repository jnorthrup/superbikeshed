package fiduciary.memvid

import fiduciary.attention.*
import fiduciary.pipeline.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

/**
 * Integrates KMP Memvid with the attention pipeline
 * Stores tagged documents and attention events in video memory
 */
class MemvidAttentionIntegration {
    private val encoder = MemvidEncoder()
    private var currentIndex: MemvidIndex? = null
    
    /**
     * Process tagged documents into Memvid
     */
    suspend fun processTaggedDocuments(
        documents: Flow<TaggedDocument>
    ): MemvidIndex {
        documents.collect { doc ->
            // Add document summary
            encoder.addText(
                text = createDocumentSummary(doc),
                metadata = mapOf(
                    "type" to "document",
                    "filename" to doc.filename,
                    "source" to doc.archiveSource,
                    "timestamp" to doc.timestamp.toString()
                )
            )
            
            // Add individual sentences with tags
            doc.taggedSentences.forEach { sentence ->
                encoder.addText(
                    text = createTaggedSentenceText(sentence),
                    metadata = mapOf(
                        "type" to "sentence",
                        "document" to doc.filename,
                        "tags" to sentence.tokens.map { it.pos }.distinct().joinToString(",")
                    )
                )
            }
            
            // Add extracted entities
            doc.nlpAnalysis.entities.forEach { entity ->
                encoder.addText(
                    text = "Entity: $entity from ${doc.filename}",
                    metadata = mapOf(
                        "type" to "entity",
                        "entity" to entity,
                        "document" to doc.filename
                    )
                )
            }
        }
        
        // Build video and index
        val (videoData, index) = encoder.buildVideo()
        currentIndex = index
        
        println("📹 Built Memvid with ${index.chunks.size} chunks")
        return index
    }
    
    /**
     * Create searchable Memvid from attention events
     */
    suspend fun createAttentionMemvid(
        events: Flow<AttentionEvent>
    ): MemvidIndex {
        val attentionEncoder = MemvidEncoder()
        
        events.collect { event ->
            val text = when (event) {
                is AttentionEvent.DocumentFocus -> {
                    "Document Focus: ${event.docId} " +
                    "Range: ${event.range.a}-${event.range.b} " +
                    "Intensity: ${event.intensity}"
                }
                
                is AttentionEvent.ConceptExtraction -> {
                    "Concept: ${event.conceptId} from ${event.sourceDoc} " +
                    "Confidence: ${event.confidence}"
                }
                
                is AttentionEvent.CorpusScan -> {
                    "Corpus Scan: ${event.corpusId} " +
                    "Progress: ${(event.scannedBytes * 100 / event.totalBytes)}% " +
                    "Score: ${event.attentionScore}"
                }
                
                else -> "Unknown event"
            }
            
            attentionEncoder.addText(
                text = text,
                metadata = mapOf(
                    "eventType" to event.javaClass.simpleName,
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
        }
        
        val (videoData, index) = attentionEncoder.buildVideo()
        return index
    }
    
    /**
     * Search across all Memvid indices
     */
    fun search(query: String): List<MemvidSearchResult> {
        val results = mutableListOf<MemvidSearchResult>()
        
        currentIndex?.let { index ->
            val retriever = MemvidRetriever(index)
            val searchResults = retriever.search(query, topK = 10)
            
            results.addAll(searchResults.map { 
                MemvidSearchResult(
                    source = "attention_pipeline",
                    chunk = it.chunk,
                    score = it.score
                )
            })
        }
        
        return results.sortedByDescending { it.score }
    }
    
    /**
     * Create document summary for Memvid
     */
    private fun createDocumentSummary(doc: TaggedDocument): String {
        return buildString {
            appendLine("Document: ${doc.filename}")
            appendLine("Words: ${doc.nlpAnalysis.totalWords}")
            appendLine("Sentences: ${doc.nlpAnalysis.totalSentences}")
            appendLine("Entities: ${doc.nlpAnalysis.entities.joinToString(", ")}")
            appendLine("Complexity: ${"%.2f".format(doc.nlpAnalysis.complexity)}")
            appendLine("Readability: ${"%.2f".format(doc.nlpAnalysis.readability)}")
        }
    }
    
    /**
     * Create tagged sentence text
     */
    private fun createTaggedSentenceText(sentence: TaggedSentence): String {
        return buildString {
            append(sentence.text)
            append(" [Tags: ")
            append(sentence.tokens.map { "${it.word}/${it.pos}" }.take(5).joinToString(" "))
            append("]")
        }
    }
}

/**
 * Search result with source information
 */
data class MemvidSearchResult(
    val source: String,
    val chunk: ChunkMetadata,
    val score: Float
)

/**
 * Memvid pipeline runner
 */
class MemvidPipelineRunner(
    private val integration: MemvidAttentionIntegration = MemvidAttentionIntegration()
) {
    /**
     * Run complete pipeline with Memvid storage
     */
    suspend fun runPipeline(
        taggedDocuments: Flow<TaggedDocument>,
        attentionEvents: Flow<AttentionEvent>
    ) {
        coroutineScope {
            // Process documents in parallel
            val docJob = launch {
                val docIndex = integration.processTaggedDocuments(taggedDocuments)
                println("📚 Document Memvid: ${docIndex.chunks.size} chunks")
            }
            
            // Process attention events in parallel
            val attentionJob = launch {
                val attentionIndex = integration.createAttentionMemvid(attentionEvents)
                println("👁️ Attention Memvid: ${attentionIndex.chunks.size} chunks")
            }
            
            // Wait for both to complete
            docJob.join()
            attentionJob.join()
        }
        
        // Demonstrate search
        demonstrateSearch()
    }
    
    /**
     * Demonstrate Memvid search capabilities
     */
    private fun demonstrateSearch() {
        val queries = listOf(
            "market volatility",
            "attention mechanisms",
            "Patrick Devine",
            "portfolio management",
            "consensus protocols"
        )
        
        println("\n🔍 Memvid Search Demo:")
        queries.forEach { query ->
            val results = integration.search(query)
            println("\nQuery: '$query'")
            results.take(3).forEach { result ->
                println("  [${result.score}] ${result.chunk.text.take(100)}...")
            }
        }
    }
}