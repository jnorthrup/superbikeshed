package fiduciary

import fiduciary.curator.*
import fiduciary.pipeline.*
import fiduciary.memvid.*
import fiduciary.nlp.*
import fiduciary.attention.*
import fiduciary.fetch.*
import borg.trikeshed.net.http.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.io.File

suspend fun main() {
    println("🚀 Running Complete Memvid Pipeline")
    println("=" * 50)
    
    // Initialize components
    val ioContext = IOContext.NioContext("complete-pipeline")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    // Core components
    val fetcher = ZipRangeFetcher(httpClient)
    val curator = ZipCurator(fetcher)
    val tagger = StanfordTagger()
    val memvidIntegration = MemvidAttentionIntegration()
    val pipelineRunner = MemvidPipelineRunner(memvidIntegration)
    
    // Create flows for documents and events
    val taggedDocumentFlow = MutableSharedFlow<TaggedDocument>()
    val attentionEventFlow = MutableSharedFlow<AttentionEvent>()
    
    // Start Memvid pipeline runner
    val pipelineJob = GlobalScope.launch {
        pipelineRunner.runPipeline(taggedDocumentFlow, attentionEventFlow)
    }
    
    // Process patrick0720.txt
    val patrick0720 = File("patrick0720.txt")
    if (patrick0720.exists()) {
        println("\n📄 Processing patrick0720.txt")
        
        val content = patrick0720.readText()
        val analysis = tagger.quickAnalyze(content)
        val taggedSentences = tagger.tagText(content)
        
        val taggedDoc = TaggedDocument(
            filename = "patrick0720.txt",
            archiveSource = "local",
            nlpAnalysis = analysis,
            taggedSentences = taggedSentences,
            timestamp = System.currentTimeMillis()
        )
        
        // Emit document
        taggedDocumentFlow.emit(taggedDoc)
        
        // Emit attention events
        attentionEventFlow.emit(AttentionEvent.DocumentFocus(
            docId = "patrick0720.txt",
            range = 0L j content.length.toLong(),
            duration = content.length * 10L,
            intensity = 0.95
        ))
        
        // Emit concept extractions for entities
        analysis.entities.forEach { entity ->
            attentionEventFlow.emit(AttentionEvent.ConceptExtraction(
                conceptId = "entity_$entity",
                sourceDoc = "patrick0720.txt",
                confidence = 0.85,
                relatedConcepts = \1 j { \2: Int -> analysis.entities[i] }
            ))
        }
    }
    
    // Process curated index if available
    val indexFile = File("patrick-devine-index.json")
    if (indexFile.exists()) {
        println("\n📚 Processing curated index")
        
        val index = Json.decodeFromString<CuratedIndex>(indexFile.readText())
        val analysisFiles = index.entries
            .filter { it.entry.filename.contains("analysis", ignoreCase = true) }
            .take(5)
        
        println("Found ${analysisFiles.size} analysis files to process")
        
        analysisFiles.forEach { entry ->
            // Mock content for demo
            val mockContent = """
            Analysis Document: ${entry.entry.filename}
            This document contains fiduciary analysis with attention mechanisms.
            Market volatility patterns are examined using stochastic models.
            """.trimIndent()
            
            val analysis = tagger.quickAnalyze(mockContent)
            val taggedSentences = tagger.tagText(mockContent)
            
            val taggedDoc = TaggedDocument(
                filename = entry.entry.filename,
                archiveSource = entry.archiveName,
                nlpAnalysis = analysis,
                taggedSentences = taggedSentences,
                timestamp = System.currentTimeMillis()
            )
            
            taggedDocumentFlow.emit(taggedDoc)
            
            // Corpus scan event
            attentionEventFlow.emit(AttentionEvent.CorpusScan(
                corpusId = entry.archiveName,
                scannedBytes = entry.entry.compressedSize,
                totalBytes = index.totalCompressedSize,
                attentionScore = 0.75
            ))
        }
    }
    
    // Wait a bit for processing
    delay(2000)
    
    // Complete flows
    println("\n✅ Completing flows...")
    
    // Close flows to signal completion
    taggedDocumentFlow.tryEmit(TaggedDocument(
        filename = "_END_",
        archiveSource = "_END_",
        nlpAnalysis = PatrickAnalysis(0, 0, 0, 0.0, 0.0, emptyList()),
        taggedSentences = emptyList(),
        timestamp = 0
    ))
    
    attentionEventFlow.tryEmit(AttentionEvent.DocumentFocus(
        docId = "_END_",
        range = 0L j 0L,
        duration = 0,
        intensity = 0.0
    ))
    
    // Wait for pipeline to complete
    pipelineJob.join()
    
    println("\n🎉 Pipeline complete!")
    
    // Final search demo
    println("\n🔎 Final Memvid Search:")
    val searchQueries = listOf(
        "fiduciary responsibility",
        "attention mechanisms", 
        "patrick devine analysis",
        "market volatility"
    )
    
    searchQueries.forEach { query ->
        println("\nSearching for: '$query'")
        val results = memvidIntegration.search(query)
        if (results.isNotEmpty()) {
            results.take(2).forEach { result ->
                println("  Score: ${"%.3f".format(result.score)}")
                println("  Text: ${result.chunk.text.take(100)}...")
            }
        } else {
            println("  No results found")
        }
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)