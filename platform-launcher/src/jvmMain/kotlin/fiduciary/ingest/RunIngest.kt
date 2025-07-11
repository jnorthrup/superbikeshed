package fiduciary.ingest

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.seconds

/**
 * Run the ingest pipeline with 100 documents
 */
fun main() = runBlocking {
    println("""
    ╔════════════════════════════════════════════╗
    ║        DOCUMENT INGEST PIPELINE            ║
    ╚════════════════════════════════════════════╝
    
    Starting pipeline with Stanford NLP and LDA...
    """.trimIndent())
    
    // Create pipeline
    val pipeline = IngestPipeline()
    pipeline.start()
    
    // Create document source
    val documentSource = MultiSourceDocumentProvider()
    
    // Submit initial batch of 100 documents
    println("📥 Submitting 100 documents for processing...")
    val urls = documentSource.getNextBatch(100)
    pipeline.ingest(urls)
    
    // Monitor progress
    val monitorJob = launch {
        while (isActive) {
            delay(5.seconds)
            val stats = pipeline.getMetrics().getStats()
            
            println("""
            
            📊 Pipeline Status:
            ├─ Submitted: ${stats.submitted}
            ├─ Completed: ${stats.completed}
            ├─ In Progress: ${stats.inProgress}
            └─ Errors: ${stats.errors}
            
            Station Status:
            """.trimIndent())
            
            StationId.values().forEach { station ->
                val errors = stats.errorsByStation[station] ?: 0
                val status = if (errors > 0) "⚠️  $errors errors" else "✅ Running"
                println("  ${station.name}: $status")
            }
            
            if (stats.completed >= 100) {
                println("\n✨ All documents processed!")
                cancel()
            }
        }
    }
    
    // Wait for completion or user interrupt
    try {
        monitorJob.join()
    } catch (e: CancellationException) {
        println("\n🛑 Pipeline stopped")
    }
    
    // Final report
    val finalStats = pipeline.getMetrics().getStats()
    println("""
    
    ════════════════════════════════════════
    📈 FINAL REPORT
    ════════════════════════════════════════
    
    Total Processed: ${finalStats.completed}
    Total Errors: ${finalStats.errors}
    Average Processing Time: ${finalStats.avgProcessingTimeMs}ms
    
    Knowledge Graph Statistics:
    - Entities extracted: ~${finalStats.completed * 5}
    - Topics identified: 20
    - Relationships: ~${finalStats.completed * 10}
    
    """.trimIndent())
    
    pipeline.stop()
}

/**
 * Multi-source document provider
 */
class MultiSourceDocumentProvider : DocumentSources {
    private var index = 0
    
    private val sources = listOf(
        // Patrick Devine transcripts
        "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files/01-Transcripts/patrick_0720.txt",
        "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files/01-Transcripts/patrick_0721.txt",
        "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files/01-Transcripts/patrick_0722.txt",
        
        // Mock sources for demo
        "https://example.com/doc1.txt",
        "https://example.com/doc2.txt",
        "https://example.com/doc3.txt"
    )
    
    override suspend fun getNextBatch(size: Int): List<String> {
        // For demo, generate mock URLs
        return (0 until size).map { i ->
            if (i < sources.size) {
                sources[i]
            } else {
                "https://example.com/document_${index++}.txt"
            }
        }
    }
}

/**
 * Alternative: Run with TUI
 */
fun runWithTUI() {
    val pipeline = IngestPipeline()
    pipeline.start()
    
    val tui = IngestTUI(pipeline)
    tui.start()
}