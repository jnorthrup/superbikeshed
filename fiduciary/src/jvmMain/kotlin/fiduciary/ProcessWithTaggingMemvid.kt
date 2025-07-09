package fiduciary

import fiduciary.curator.*
import fiduciary.pipeline.*
import fiduciary.nlp.*
import fiduciary.attention.*
import fiduciary.memvid.*
import fiduciary.fetch.*
import borg.trikeshed.net.http.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

suspend fun main() {
    // Initialize components
    val ioContext = IOContext.NioContext("tagging-memvid")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    val fetcher = ZipRangeFetcher(httpClient)
    val curator = ZipCurator(fetcher)
    val tagger = StanfordTagger()
    val memvidBridge = createMemvidBridge()
    val pipeline = TaggingPipeline(tagger, memvidBridge)
    
    println("🏷️ Starting Tagging & Memvid Pipeline")
    
    // Load or create index
    val indexFile = File("patrick-devine-index.json")
    val index = if (indexFile.exists()) {
        println("📂 Loading existing index...")
        kotlinx.serialization.json.Json.decodeFromString<CuratedIndex>(indexFile.readText())
    } else {
        println("🔍 Creating new index...")
        curator.indexAllFiles()
    }
    
    // Create processing branches
    val branches = pipeline.createBranches(index)
    println("\n🌿 Processing branches:")
    branches.forEach { (type, branch) ->
        val count = index.entries.count(branch.filter)
        println("  ${branch.name}: $count files (priority ${branch.priority})")
    }
    
    // Process high-priority branch (analysis documents)
    val analysisBranch = branches["analysis"]!!
    val analysisEntries = index.entries.filter(analysisBranch.filter).take(10)
    
    println("\n📝 Processing ${analysisEntries.size} analysis documents...")
    
    // Mock content extraction (replace with actual ZIP extraction)
    val mockContent = analysisEntries.associate { entry ->
        entry.entry.filename to """
        Patrick Devine Analysis - ${entry.entry.filename}
        
        This document analyzes market volatility patterns using attention-weighted mechanisms.
        Key findings indicate superior performance through multi-agent coordination.
        Stochastic processes demonstrate improved risk management capabilities.
        
        Recommendations include implementing real-time tracking systems with consensus protocols.
        """.trimIndent()
    }
    
    // Process through tagging pipeline
    var processedCount = 0
    pipeline.processBatch(analysisEntries, mockContent)
        .collect { taggedDoc ->
            processedCount++
            println("\n✅ Tagged: ${taggedDoc.filename}")
            println("   Words: ${taggedDoc.nlpAnalysis.totalWords}")
            println("   Sentences: ${taggedDoc.nlpAnalysis.totalSentences}")
            println("   Entities: ${taggedDoc.nlpAnalysis.entities.size}")
            println("   Complexity: ${"%.2f".format(taggedDoc.nlpAnalysis.complexity)}")
            
            // Show some tagged sentences
            taggedDoc.taggedSentences.take(2).forEach { sentence ->
                println("   📌 ${sentence.text}")
                sentence.tokens.take(5).forEach { token ->
                    println("      ${token.word}/${token.pos}")
                }
            }
        }
    
    println("\n🎬 Memvid attention tracking active")
    println("📊 Processed $processedCount documents")
    
    // Show Memvid statistics
    val stats = memvidBridge.getStatistics()
    println("\n📈 Memvid Statistics:")
    println("   Total events: ${stats.totalEvents}")
    println("   Document focuses: ${stats.documentFocusCount}")
    println("   Concept extractions: ${stats.conceptExtractionCount}")
    println("   Average attention intensity: ${"%.2f".format(stats.averageIntensity)}")
}

/**
 * Create Memvid bridge instance with real connector
 */
fun createMemvidBridge(): MemvidAttentionBridge {
    // Use real Memvid connector
    val connector = RealMemvidConnector(
        memvidBaseUrl = System.getenv("MEMVID_URL") ?: "http://localhost:8089/memvid",
        apiKey = System.getenv("MEMVID_API_KEY")
    )
    
    val videoEncoder = MemvidVideoEncoder(
        frameWidth = 1280,
        frameHeight = 720,
        frameRate = 30
    )
    
    return object : MemvidAttentionBridge(
        memvidEndpoint = MemvidEndpoint("http://localhost:8089/memvid"),
        couchDB = null // Could add CouchDB for provenance
    ) {
        private var eventCount = 0
        private var focusCount = 0
        private var conceptCount = 0
        private var totalIntensity = 0.0
        private val eventFlow = MutableSharedFlow<AttentionEvent>()
        
        init {
            // Start video encoding pipeline
            GlobalScope.launch {
                videoEncoder.encodeAttentionStream(eventFlow)
                    .collect { frame ->
                        // Send frame to Memvid
                        println("📹 Generated video frame #${frame.frameNumber}")
                    }
            }
        }
        
        override suspend fun recordEvent(event: AttentionEvent) {
            eventCount++
            
            // Send to real Memvid
            val success = connector.registerEvent(event)
            if (success) {
                println("✅ Event sent to Memvid")
            }
            
            // Emit for video encoding
            eventFlow.emit(event)
            
            // Track statistics
            when (event) {
                is AttentionEvent.DocumentFocus -> {
                    focusCount++
                    totalIntensity += event.intensity
                }
                is AttentionEvent.ConceptExtraction -> conceptCount++
                else -> {}
            }
        }
        
        suspend fun getMemory() = connector.getAttentionMemory()
        
        fun getStatistics() = MemvidStats(
            totalEvents = eventCount,
            documentFocusCount = focusCount,
            conceptExtractionCount = conceptCount,
            averageIntensity = if (focusCount > 0) totalIntensity / focusCount else 0.0
        )
    }
}

data class MemvidStats(
    val totalEvents: Int,
    val documentFocusCount: Int,
    val conceptExtractionCount: Int,
    val averageIntensity: Double
)