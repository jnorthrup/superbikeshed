package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Standalone Fiduciary Percolator Demo
 * 
 * Demonstrates the core percolator functionality without external dependencies.
 * Shows the complete pipeline: Ingest → Transform → Store → Emit
 */

// Core Percolator Data Types
data class FiduciaryData(
    val id: String,
    val source: String,
    val content: Map<String, Any>,
    val stage: String = "raw",
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Double = 0.0,
    val metadata: Map<String, Any> = emptyMap()
)

data class PercolatorResult(
    val documentId: String,
    val classification: String,
    val confidence: Double,
    val processingTime: Long,
    val metadata: Map<String, Any>
)

// Simple in-memory storage for demo
class InMemoryStorage {
    private val documents = mutableMapOf<String, FiduciaryData>()
    private val results = mutableListOf<PercolatorResult>()
    
    suspend fun storeDocument(doc: FiduciaryData) {
        documents[doc.id] = doc
        println("📄 Stored document: ${doc.id} (${doc.stage})")
    }
    
    suspend fun storeResult(result: PercolatorResult) {
        results.add(result)
        println("✅ Stored result: ${result.documentId} -> ${result.classification} (${result.confidence})")
    }
    
    fun getDocuments(): List<FiduciaryData> = documents.values.toList()
    fun getResults(): List<PercolatorResult> = results.toList()
}

// Expert System Assertions
class ExpertSystemAssertions {
    private val rules = mutableListOf<AssertionRule>()
    
    data class AssertionRule(
        val name: String,
        val condition: (FiduciaryData) -> Boolean,
        val action: (FiduciaryData) -> PercolatorResult
    )
    
    fun addRule(rule: AssertionRule) {
        rules.add(rule)
        println("🧠 Added expert rule: ${rule.name}")
    }
    
    suspend fun evaluateDocument(doc: FiduciaryData): PercolatorResult? {
        for (rule in rules) {
            if (rule.condition(doc)) {
                val result = rule.action(doc)
                println("🎯 Rule '${rule.name}' triggered for ${doc.id}")
                return result
            }
        }
        return null
    }

    fun getRuleCount(): Int = rules.size
}

// Main Percolator Engine
class StandalonePercolator(
    private val storage: InMemoryStorage,
    private val expertSystem: ExpertSystemAssertions
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val ingestChannel = Channel<FiduciaryData>(Channel.UNLIMITED)
    private val transformChannel = Channel<FiduciaryData>(Channel.UNLIMITED)
    private val emitChannel = Channel<PercolatorResult>(Channel.UNLIMITED)
    
    init {
        setupPipeline()
        setupExpertRules()
    }
    
    private fun setupPipeline() {
        // Ingest → Transform pipeline
        scope.launch {
            for (doc in ingestChannel) {
                val transformed = transformDocument(doc)
                transformChannel.send(transformed)
            }
        }
        
        // Transform → Store → Emit pipeline
        scope.launch {
            for (doc in transformChannel) {
                val startTime = System.currentTimeMillis()
                
                // Store the document
                storage.storeDocument(doc)
                
                // Apply expert system rules
                val result = expertSystem.evaluateDocument(doc) ?: 
                    PercolatorResult(
                        documentId = doc.id,
                        classification = "unknown",
                        confidence = 0.0,
                        processingTime = System.currentTimeMillis() - startTime,
                        metadata = doc.metadata
                    )
                
                // Store and emit result
                storage.storeResult(result)
                emitChannel.send(result)
                
                println("🍿 Pop! Document ${doc.id} processed in ${result.processingTime}ms")
            }
        }
    }
    
    private fun setupExpertRules() {
        // Rule 1: High confidence encrypted traffic
        expertSystem.addRule(
            ExpertSystemAssertions.AssertionRule(
                name = "Encrypted Traffic Detection",
                condition = { doc ->
                    doc.content["encrypted"] == true && 
                    doc.content["confidence"]?.toString()?.toDoubleOrNull() ?: 0.0 > 0.8
                },
                action = { doc ->
                    PercolatorResult(
                        documentId = doc.id,
                        classification = "encrypted_priority",
                        confidence = 0.95,
                        processingTime = 0,
                        metadata = doc.metadata + mapOf("rule" to "encrypted_traffic")
                    )
                }
            )
        )
        
        // Rule 2: Unencrypted sensitive data
        expertSystem.addRule(
            ExpertSystemAssertions.AssertionRule(
                name = "Unencrypted Sensitive Data",
                condition = { doc ->
                    doc.content["encrypted"] == false && 
                    doc.content["sensitive"] == true
                },
                action = { doc ->
                    PercolatorResult(
                        documentId = doc.id,
                        classification = "unencrypted_priority",
                        confidence = 0.9,
                        processingTime = 0,
                        metadata = doc.metadata + mapOf("rule" to "unencrypted_sensitive")
                    )
                }
            )
        )
        
        // Rule 3: Secure communication patterns
        expertSystem.addRule(
            ExpertSystemAssertions.AssertionRule(
                name = "Secure Communication",
                condition = { doc ->
                    doc.content["protocol"] == "TLS" || 
                    doc.content["protocol"] == "SSH" ||
                    doc.content["secure"] == true
                },
                action = { doc ->
                    PercolatorResult(
                        documentId = doc.id,
                        classification = "secure_priority",
                        confidence = 0.85,
                        processingTime = 0,
                        metadata = doc.metadata + mapOf("rule" to "secure_communication")
                    )
                }
            )
        )
    }
    
    private suspend fun transformDocument(doc: FiduciaryData): FiduciaryData {
        // Simulate document transformation
        delay(10) // Simulate processing time
        
        val transformed = doc.copy(
            stage = "transformed",
            metadata = doc.metadata + mapOf(
                "transformed_at" to Instant.now().toString(),
                "processor" to "standalone_percolator"
            )
        )
        
        println("🔄 TRANSFORM: ${doc.id} → ${transformed.stage}")
        return transformed
    }
    
    suspend fun ingestDocument(doc: FiduciaryData) {
        ingestChannel.send(doc)
        println("📥 INGEST: ${doc.id} from ${doc.source}")
    }
    
    fun getResults(): Flow<PercolatorResult> = emitChannel.receiveAsFlow()
    
    suspend fun shutdown() {
        ingestChannel.close()
        transformChannel.close()
        emitChannel.close()
        scope.cancel()
    }
}

// Demo runner
fun main() = runBlocking {
    println("🚀 Starting Standalone Fiduciary Percolator Demo")
    
    val storage = InMemoryStorage()
    val expertSystem = ExpertSystemAssertions()
    val percolator = StandalonePercolator(storage, expertSystem)
    
    println("🔥 Starting Standalone Fiduciary Percolator Engine")
    println("✅ Percolator pipelines active")
    println("🧠 Expert system rules loaded")
    
    // Create demo documents
    val demoDocuments = listOf(
        FiduciaryData(
            id = "scan_001",
            source = "network_scanner",
            content = mapOf(
                "encrypted" to false,
                "sensitive" to true,
                "protocol" to "HTTP",
                "confidence" to 0.85
            )
        ),
        FiduciaryData(
            id = "scan_002",
            source = "network_scanner",
            content = mapOf(
                "encrypted" to true,
                "protocol" to "TLS",
                "confidence" to 0.92
            )
        ),
        FiduciaryData(
            id = "scan_003",
            source = "network_scanner",
            content = mapOf(
                "encrypted" to true,
                "protocol" to "SSH",
                "secure" to true,
                "confidence" to 0.88
            )
        ),
        FiduciaryData(
            id = "scan_004",
            source = "network_scanner",
            content = mapOf(
                "encrypted" to false,
                "protocol" to "FTP",
                "confidence" to 0.45
            )
        )
    )
    
    println("\n📥 Ingesting ${demoDocuments.size} demo documents...")
    
    // Ingest documents
    demoDocuments.forEach { doc ->
        percolator.ingestDocument(doc)
        delay(50) // Small delay between ingestions
    }
    
    // Wait for processing to complete
    delay(1000)
    
    // Display results
    println("\n📊 Percolator Results:")
    storage.getResults().forEach { result ->
        println("  📄 ${result.documentId}: ${result.classification} (${result.confidence})")
    }
    
    println("\n✅ Standalone percolator demo completed successfully!")
    println("🍿 Total documents processed: ${storage.getResults().size}")
    println("🧠 Expert system rules applied: ${expertSystem.getRuleCount()}")
    
    percolator.shutdown()
} 