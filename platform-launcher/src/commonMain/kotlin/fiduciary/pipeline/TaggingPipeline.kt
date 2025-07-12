package fiduciary.pipeline

import fiduciary.curator.*
import fiduciary.nlp.*
import fiduciary.attention.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Tagging Pipeline - Processes curated files through NLP tagging
 * and feeds into Memvid attention system
 */
class TaggingPipeline(
    private val tagger: StanfordTagger,
    private val memvidBridge: MemvidAttentionBridge
) {
    
    /**
     * Process a batch of curated entries through tagging
     */
    suspend fun processBatch(
        entries: List<CuratedEntry>,
        content: Map<String, String> // filename -> content
    ): Flow<TaggedDocument> = flow {
        entries.forEach { entry ->
            val text = content[entry.entry.filename]
            if (text != null) {
                // Tag with Stanford NLP
                val analysis = tagger.quickAnalyze(text)
                
                // Extract tagged document
                val taggedDoc = TaggedDocument(
                    filename = entry.entry.filename,
                    archiveSource = entry.archiveName,
                    nlpAnalysis = analysis,
                    taggedSentences = tagger.tagText(text),
                    timestamp = System.currentTimeMillis()
                )
                
                // Send to Memvid for attention tracking
                recordAttention(taggedDoc)
                
                emit(taggedDoc)
            }
        }
    }
    
    /**
     * Record attention event in Memvid
     */
    private suspend fun recordAttention(doc: TaggedDocument) {
        // Document focus event
        val focusEvent = AttentionEvent.DocumentFocus(
            docId = doc.filename,
            range = 0L j doc.nlpAnalysis.totalWords.toLong(),
            duration = doc.nlpAnalysis.totalWords * 10L, // ~10ms per word
            intensity = doc.nlpAnalysis.complexity / 100.0
        )
        
        memvidBridge.recordEvent(focusEvent)
        
        // Concept extraction events for named entities
        doc.nlpAnalysis.entities.forEachIndexed { index, entity ->
            val conceptEvent = AttentionEvent.ConceptExtraction(
                conceptId = "entity_${doc.filename}_$index",
                sourceDoc = doc.filename,
                confidence = 0.85,
                relatedConcepts = \1 j { \2: Int ->
                    doc.nlpAnalysis.entities[i]
                }
            )
            memvidBridge.recordEvent(conceptEvent)
        }
    }
    
    /**
     * Create processing branches for different document types
     */
    fun createBranches(index: CuratedIndex): Map<String, ProcessingBranch> {
        val byExtension = index.entries.groupBy { 
            it.entry.filename.substringAfterLast('.', "unknown")
        }
        
        return mapOf(
            "txt" to ProcessingBranch(
                name = "Text Documents",
                filter = { it.entry.filename.endsWith(".txt") },
                priority = 1.0,
                processingStrategy = TextProcessingStrategy()
            ),
            "pdf" to ProcessingBranch(
                name = "PDF Documents", 
                filter = { it.entry.filename.endsWith(".pdf") },
                priority = 0.8,
                processingStrategy = PDFProcessingStrategy()
            ),
            "transcript" to ProcessingBranch(
                name = "Transcripts",
                filter = { it.entry.filename.contains("transcript", ignoreCase = true) },
                priority = 0.9,
                processingStrategy = TranscriptProcessingStrategy()
            ),
            "analysis" to ProcessingBranch(
                name = "Analysis Documents",
                filter = { it.entry.filename.contains("analysis", ignoreCase = true) },
                priority = 0.95,
                processingStrategy = AnalysisProcessingStrategy()
            )
        )
    }
}

/**
 * Tagged document with NLP analysis
 */
data class TaggedDocument(
    val filename: String,
    val archiveSource: String,
    val nlpAnalysis: PatrickAnalysis,
    val taggedSentences: List<TaggedSentence>,
    val timestamp: Long
)

/**
 * Processing branch for specific document types
 */
data class ProcessingBranch(
    val name: String,
    val filter: (CuratedEntry) -> Boolean,
    val priority: Double,
    val processingStrategy: ProcessingStrategy
)

/**
 * Strategy for processing different document types
 */
interface ProcessingStrategy {
    suspend fun preprocess(content: String): String
    fun getAttentionWeight(): Double
}

class TextProcessingStrategy : ProcessingStrategy {
    override suspend fun preprocess(content: String) = content
    override fun getAttentionWeight() = 1.0
}

class PDFProcessingStrategy : ProcessingStrategy {
    override suspend fun preprocess(content: String): String {
        // Would use Apache Tika or similar for PDF extraction
        return content
    }
    override fun getAttentionWeight() = 0.8
}

class TranscriptProcessingStrategy : ProcessingStrategy {
    override suspend fun preprocess(content: String): String {
        // Clean up transcript formatting
        return content
            .replace(Regex("\\[\\d+:\\d+\\]"), "") // Remove timestamps
            .replace(Regex("Speaker \\d+:"), "\n") // Clean speaker markers
    }
    override fun getAttentionWeight() = 0.9
}

class AnalysisProcessingStrategy : ProcessingStrategy {
    override suspend fun preprocess(content: String) = content
    override fun getAttentionWeight() = 0.95
}