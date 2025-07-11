package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Patrick Devine Text Processing Demo
 * 
 * Demonstrates processing patrick0720.txt through the complete percolation system
 * with CouchDB integration, graph analysis, LDA topic modeling, word clouds, and Stanford graphs.
 */

object PatrickDevineTextDemo {
    
    suspend fun runCompleteTextProcessingDemo() {
        println("🌊 Patrick Devine Text Processing Demo")
        println("=" * 60)
        
        // Process patrick0720.txt through all completion levels
        val filePath = "patrick0720.txt"
        val finalResult = CouchDBTextProcessor.processTextFile(filePath)
        
        // Display comprehensive results
        displayTextProcessingResults(finalResult)
        
        // Show CouchDB integration
        displayCouchDBIntegration()
        
        // Show graph analysis
        displayGraphAnalysis(finalResult)
        
        // Show LDA topic modeling
        displayLDATopicModeling(finalResult)
        
        // Show word cloud data
        displayWordCloudData(finalResult)
        
        // Show Stanford graph
        displayStanfordGraph(finalResult)
        
        // Show percolation metrics
        displayPercolationMetrics()
    }
    
    private fun displayTextProcessingResults(result: ProcessedText) {
        println("\n📊 TEXT PROCESSING RESULTS")
        println("=" * 40)
        println("Document ID: ${result.documentId}")
        println("Processing Level: ${result.level.description}")
        println("Processing Time: ${result.processingTime}ms")
        println("Confidence: ${"%.2f".format(result.confidence)}")
        println("Tokens Processed: ${result.tokens.size}")
        println("Sentences: ${result.sentences.size}")
        println("Paragraphs: ${result.paragraphs.size}")
        
        println("\n📝 SAMPLE TOKENS:")
        println("-" * 20)
        result.tokens.take(20).forEachIndexed { index, token ->
            print("$token ")
            if ((index + 1) % 10 == 0) println()
        }
        println()
        
        println("\n📊 WORD FREQUENCY (Top 10):")
        println("-" * 30)
        result.wordFrequency.entries
            .sortedByDescending { it.value }
            .take(10)
            .forEach { (word, freq) ->
                println("  $word: $freq")
            }
    }
    
    private fun displayCouchDBIntegration() {
        println("\n🗄️ COUCHDB INTEGRATION")
        println("=" * 30)
        
        // Show databases
        val databases = listOf("fiduciary", "patrick_devine_agent", "channelized_data", "percolator_state")
        println("Available Databases:")
        databases.forEach { db ->
            println("  • $db")
        }
        
        // Show document storage
        println("\nDocument Storage:")
        println("  • Text processing results stored in 'patrick_devine_agent' database")
        println("  • Each completion level creates a separate document")
        println("  • Documents include metadata, processing time, and confidence scores")
        println("  • CouchDB provides full-text search and querying capabilities")
    }
    
    private fun displayGraphAnalysis(result: ProcessedText) {
        println("\n🕸️ GRAPH ANALYSIS")
        println("=" * 25)
        
        result.graphData?.let { graph ->
            println("Graph Statistics:")
            println("  • Nodes: ${graph.nodes.size}")
            println("  • Edges: ${graph.edges.size}")
            println("  • Communities: ${graph.communities.size}")
            
            println("\nTop Nodes by Centrality:")
            println("-" * 30)
            graph.centrality.entries
                .sortedByDescending { it.value }
                .take(10)
                .forEach { (node, centrality) ->
                    println("  $node: ${"%.3f".format(centrality)}")
                }
            
            println("\nCommunities Detected:")
            println("-" * 25)
            graph.communities.forEachIndexed { index, community ->
                println("  Community ${index + 1}: ${community.joinToString(", ")}")
            }
            
            println("\nSample Edges:")
            println("-" * 15)
            graph.edges.take(10).forEach { edge ->
                println("  ${edge.source} -> ${edge.target} (${edge.type})")
            }
        } ?: println("No graph data available")
    }
    
    private fun displayLDATopicModeling(result: ProcessedText) {
        println("\n📈 LDA TOPIC MODELING")
        println("=" * 30)
        
        if (result.topics.isNotEmpty()) {
            println("Topics Identified:")
            result.topics.forEach { topic ->
                println("\nTopic ${topic.topicId} (Weight: ${"%.2f".format(topic.weight)}, Coherence: ${"%.2f".format(topic.coherence)}):")
                println("  Top Words:")
                topic.words.take(5).forEach { (word, weight) ->
                    println("    • $word: ${"%.3f".format(weight)}")
                }
            }
            
            println("\nTopic Distribution:")
            val totalWeight = result.topics.sumOf { it.weight }
            result.topics.forEach { topic ->
                val percentage = (topic.weight / totalWeight) * 100
                println("  Topic ${topic.topicId}: ${"%.1f".format(percentage)}%")
            }
        } else {
            println("No topics identified")
        }
    }
    
    private fun displayWordCloudData(result: ProcessedText) {
        println("\n☁️ WORD CLOUD DATA")
        println("=" * 25)
        
        result.wordCloud?.let { cloud ->
            println("Word Cloud Configuration:")
            println("  • Dimensions: ${cloud.dimensions.first} x ${cloud.dimensions.second}")
            println("  • Color Scheme: ${cloud.colorScheme}")
            println("  • Total Words: ${cloud.words.size}")
            
            println("\nTop Words by Frequency:")
            println("-" * 30)
            cloud.words
                .sortedByDescending { it.frequency }
                .take(15)
                .forEach { word ->
                    println("  ${word.word}: ${word.frequency} (size: ${"%.1f".format(word.size)})")
                }
            
            println("\nColor Distribution:")
            println("-" * 25)
            cloud.words.groupBy { it.color }.forEach { (color, words) ->
                println("  $color: ${words.size} words")
            }
        } ?: println("No word cloud data available")
    }
    
    private fun displayStanfordGraph(result: ProcessedText) {
        println("\n🎓 STANFORD DEPENDENCY GRAPH")
        println("=" * 35)
        
        result.stanfordGraph?.let { stanford ->
            println("Dependency Analysis:")
            println("  • Total Dependencies: ${stanford.dependencies.size}")
            println("  • Named Entities: ${stanford.namedEntities.size}")
            println("  • Coreferences: ${stanford.coreferences.size}")
            
            println("\nSample Dependencies:")
            println("-" * 25)
            stanford.dependencies.take(10).forEach { dep ->
                println("  ${dep.governor} --${dep.relation}--> ${dep.dependent}")
            }
            
            println("\nNamed Entities:")
            println("-" * 20)
            stanford.namedEntities.forEach { entity ->
                println("  ${entity.text} (${entity.type}) at positions ${entity.startIndex}-${entity.endIndex}")
            }
            
            println("\nCoreference Chains:")
            println("-" * 25)
            stanford.coreferences.forEach { coref ->
                println("  Representative: '${coref.representative}'")
                println("    Mentions: ${coref.mentions.joinToString(", ")}")
                println("    Type: ${coref.type}")
            }
            
            println("\nParse Tree Structure:")
            println("-" * 25)
            displayParseTree(stanford.parseTree, 0)
        } ?: println("No Stanford graph data available")
    }
    
    private fun displayParseTree(node: ParseTreeNode, depth: Int) {
        val indent = "  ".repeat(depth)
        println("$indent${node.word} (${node.pos})")
        node.children.forEach { child ->
            displayParseTree(child, depth + 1)
        }
    }
    
    private fun displayPercolationMetrics() {
        println("\n📊 PERCOLATION METRICS")
        println("=" * 30)
        
        // Get metrics from the percolator
        val metrics = MonitorPercolatorKey.getMetricsFlow().value
        println("Events Processed: ${metrics.eventsProcessed}")
        println("Transformations: ${metrics.transformations}")
        println("Errors: ${metrics.errors}")
        
        println("\nTransformation Breakdown:")
        println("-" * 25)
        metrics.transformations.forEach { (stage, count) ->
            println("  $stage: $count")
        }
        
        // Get stage progression
        val stages = PercolatorCompletionEngine.getStageFlow().value
        println("\nStage Progression:")
        println("-" * 20)
        stages.forEach { (level, stage) ->
            val status = when (stage.status) {
                StageStatus.COMPLETED -> "✅"
                StageStatus.PROCESSING -> "🔄"
                StageStatus.FAILED -> "❌"
                StageStatus.PENDING -> "⏳"
                StageStatus.SKIPPED -> "⏭️"
            }
            println("  $status ${level.description}")
        }
    }
    
    suspend fun runProgressiveTextDemo() {
        println("\n🚀 PROGRESSIVE TEXT PROCESSING DEMO")
        println("=" * 45)
        
        val filePath = "patrick0720.txt"
        val document = TextDocument(
            id = "patrick0720_${System.currentTimeMillis()}",
            filename = "patrick0720.txt",
            content = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath))
        )
        
        // Process through each level individually
        for (level in CompletionLevel.values()) {
            println("\n📊 Processing Level ${level.level}: ${level.description}")
            println("-" * 50)
            
            val result = CouchDBTextProcessor.processTextThroughLevels(document, level)
            
            println("✅ Level ${level.level} completed:")
            println("   Confidence: ${"%.2f".format(result.confidence)}")
            println("   Processing Time: ${result.processingTime}ms")
            println("   Tokens: ${result.tokens.size}")
            
            // Show level-specific insights
            displayLevelSpecificInsights(level, result)
        }
    }
    
    private fun displayLevelSpecificInsights(level: CompletionLevel, result: ProcessedText) {
        when (level) {
            CompletionLevel.RAW -> {
                println("   Raw Text Stats:")
                println("     • Characters: ${result.tokens.joinToString("").length}")
                println("     • Sentences: ${result.sentences.size}")
                println("     • Paragraphs: ${result.paragraphs.size}")
            }
            CompletionLevel.VALIDATED -> {
                println("   Validation Results:")
                println("     • Quality Score: ${result.metadata["quality_score"]}")
                println("     • Vocabulary Diversity: ${result.metadata["vocabulary_diversity"]}")
            }
            CompletionLevel.ENRICHED -> {
                println("   Enrichment Results:")
                println("     • Unique Words: ${result.wordFrequency.size}")
                println("     • Bigrams: ${result.bigrams.size}")
                println("     • Entities: ${result.entities.size}")
            }
            CompletionLevel.ANALYZED -> {
                println("   Analysis Results:")
                println("     • Topics Identified: ${result.topics.size}")
                println("     • Patterns Detected: ${result.metadata["patterns_detected"]}")
            }
            CompletionLevel.CLASSIFIED -> {
                println("   Classification Results:")
                println("     • Graph Nodes: ${result.graphData?.nodes?.size}")
                println("     • Graph Edges: ${result.graphData?.edges?.size}")
                println("     • Classification: ${result.metadata["classification"]}")
            }
            CompletionLevel.CONSENSUS -> {
                println("   Consensus Results:")
                println("     • Word Cloud Words: ${result.wordCloud?.words?.size}")
                println("     • Consensus Reached: ${result.metadata["consensus_reached"]}")
            }
            CompletionLevel.SYNTHESIZED -> {
                println("   Synthesis Results:")
                println("     • Stanford Dependencies: ${result.stanfordGraph?.dependencies?.size}")
                println("     • Named Entities: ${result.stanfordGraph?.namedEntities?.size}")
                println("     • Synthesis Complete: ${result.metadata["synthesis_complete"]}")
            }
        }
    }
    
    suspend fun runRealTimeTextDemo() {
        println("\n⚡ REAL-TIME TEXT PROCESSING DEMO")
        println("=" * 40)
        
        // Collect processing events in real-time
        val processingJob = CoroutineScope(Dispatchers.Default).launch {
            CouchDBTextProcessor.getTextProcessingFlow()
                .collect { result ->
                    println("🎯 Text Level ${result.level.level} completed: ${result.level.description}")
                    println("   Document: ${result.documentId}")
                    println("   Confidence: ${"%.2f".format(result.confidence)}")
                    println("   Time: ${result.processingTime}ms")
                    println("   Tokens: ${result.tokens.size}")
                    println()
                }
        }
        
        // Process multiple files
        val files = listOf("patrick0720.txt")
        
        files.forEachIndexed { index, filePath ->
            println("🚀 Processing file ${index + 1}: $filePath")
            try {
                CouchDBTextProcessor.processTextFile(filePath)
                kotlinx.coroutines.delay(1000) // Wait between files
            } catch (e: Exception) {
                println("❌ Error processing $filePath: ${e.message}")
            }
        }
        
        processingJob.cancel()
    }
}

// Main demo runner
suspend fun main() {
    println("🌊 Patrick Devine Text Processing Demo")
    println("=" * 50)
    
    // Run the complete text processing demo
    PatrickDevineTextDemo.runCompleteTextProcessingDemo()
    
    // Run progressive demo
    PatrickDevineTextDemo.runProgressiveTextDemo()
    
    // Run real-time demo
    PatrickDevineTextDemo.runRealTimeTextDemo()
    
    println("\n✅ Patrick Devine text processing demo completed successfully!")
    println("The percolation system has processed patrick0720.txt with:")
    println("  • Graph analysis and visualization")
    println("  • LDA topic modeling")
    println("  • Word cloud generation")
    println("  • Stanford dependency graphs")
    println("  • CouchDB storage and integration")
} 