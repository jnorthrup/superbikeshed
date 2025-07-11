package fiduciary.clean

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Standalone Fiduciary Percolator Demo
 * 
 * Demonstrates the core percolator functionality without external dependencies.
 * Shows the complete pipeline: Ingest → Transform → Store → Emit
 */

// Core Percolator Data Types with type-safe CoreTypes
data class FiduciaryData(
    val id: String, // Simplified for demo
    val source: String,
    val content: Map<String, Any>,
    val stage: String = "raw",
    val timestamp: Long = System.currentTimeMillis()
)

// Percolation Stages
sealed class PercolationStage {
    object Ingest : PercolationStage()
    object Normalize : PercolationStage() 
    object Enrich : PercolationStage()
    object Classify : PercolationStage()
    object Store : PercolationStage()
    object Emit : PercolationStage()
}

// Simplified audit logging data class
@Serializable
data class AuditLogEntry(
    val id: String,
    val operation: String,
    val documentId: String,
    val userId: String? = null,
    val timestamp: Instant = Clock.System.now(),
    val metadata: Map<String, String> = emptyMap(),
    val success: Boolean = true,
    val error: String? = null
)

// Simplified document storage
@Serializable
data class FiduciaryDocument(
    val id: String,
    val type: String,
    val timestamp: Instant,
    val status: String,
    val data: Map<String, Any>,
    val metadata: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList()
)

// The Core Percolator Engine
object FiduciaryPercolator : CoroutineContext.Element, CoroutineContext.Key<FiduciaryPercolator> {
    override val key: CoroutineContext.Key<*> get() = FiduciaryPercolator
    
    private val ingestChannel = Channel<FiduciaryData>(capacity = 1000)
    private val transformChannel = Channel<FiduciaryData>(capacity = 1000) 
    private val storeChannel = Channel<FiduciaryData>(capacity = 1000)
    private val emitChannel = Channel<FiduciaryData>(capacity = 1000)
    
    private val storage = mutableMapOf<String, FiduciaryData>()
    private val percolationFlow = MutableSharedFlow<FiduciaryData>(replay = 100)

    // Simplified storage with in-memory documents
    private val batchBuffer = mutableListOf<FiduciaryDocument>()
    private val auditBuffer = mutableListOf<AuditLogEntry>()
    private val batchSize = 5
    private val batchDelayMs = 200L
    private var batchJob: Job? = null

    suspend fun startPercolation(scope: CoroutineScope) {
        println("🔥 Starting Standalone Fiduciary Percolator Engine")
        
        // Stage 1: Ingestion Pipeline
        scope.launch {
            ingestChannel.consumeAsFlow().collect { data ->
                println("📥 INGEST: ${data.id} from ${data.source}")
                val normalized = normalizeData(data)
                transformChannel.send(normalized)
            }
        }
        
        // Stage 2: Transformation Pipeline  
        scope.launch {
            transformChannel.consumeAsFlow().collect { data ->
                println("🔄 TRANSFORM: ${data.id} → ${data.stage}")
                val enriched = enrichData(data)
                val classified = classifyData(enriched)
                storeChannel.send(classified)
            }
        }
        
        // Stage 3: Storage Pipeline (simplified, in-memory)
        scope.launch {
            storeChannel.consumeAsFlow().collect { data ->
                val doc = FiduciaryDocument(
                    id = data.id,
                    type = data.stage,
                    timestamp = Clock.System.now(),
                    status = "active",
                    data = data.content + mapOf("schema_version" to 2),
                    metadata = mapOf("source" to data.source),
                    tags = listOf("fiduciary")
                )
                batchBuffer.add(doc)
                
                // Create audit log entry
                val auditEntry = AuditLogEntry(
                    id = "audit_${System.currentTimeMillis()}_${data.id}",
                    operation = "INGEST",
                    documentId = data.id,
                    userId = System.getenv("USER") ?: "system",
                    metadata = mapOf(
                        "source" to data.source,
                        "stage" to data.stage,
                        "schema_version" to "2"
                    )
                )
                auditBuffer.add(auditEntry)
                
                if (batchBuffer.size >= batchSize) {
                    flushBatch()
                } else if (batchJob == null || batchJob?.isActive == false) {
                    batchJob = scope.launch {
                        delay(batchDelayMs)
                        flushBatch()
                    }
                }
            }
        }
        
        // Stage 4: Emission Pipeline
        scope.launch {
            emitChannel.consumeAsFlow().collect { data ->
                println("📡 EMIT: ${data.id} → downstream systems")
            }
        }
        println("✅ Percolator pipelines active")
    }
    
    suspend fun ingest(data: FiduciaryData) {
        ingestChannel.send(data)
    }
    
    private fun normalizeData(data: FiduciaryData): FiduciaryData {
        return data.copy(
            stage = "normalized",
            content = data.content + mapOf(
                "normalized_at" to System.currentTimeMillis(),
                "fiduciary_version" to "1.0.0"
            )
        )
    }
    
    private fun enrichData(data: FiduciaryData): FiduciaryData {
        return data.copy(
            stage = "enriched", 
            content = data.content + mapOf(
                "enriched_at" to System.currentTimeMillis(),
                "risk_score" to calculateRiskScore(data),
                "asset_type" to determineAssetType(data),
                "location" to determineLocation(data)
            )
        )
    }
    
    private fun classifyData(data: FiduciaryData): FiduciaryData {
        return data.copy(
            stage = "classified",
            content = data.content + mapOf(
                "classified_at" to System.currentTimeMillis(),
                "security_classification" to determineSecurityClassification(data),
                "priority" to determinePriority(data),
                "tags" to generateTags(data)
            )
        )
    }
    
    // Helper functions for data enrichment and classification
    private fun calculateRiskScore(data: FiduciaryData): Double {
        return when (data.source) {
            "network_scanner" -> 0.3
            "patrick_devine" -> 0.1
            else -> 0.5
        }
    }
    
    private fun determineAssetType(data: FiduciaryData): String {
        return when {
            data.content.containsKey("protocol") -> "network_service"
            data.content.containsKey("certificate") -> "ssl_certificate"
            data.content.containsKey("banner") -> "ssh_service"
            else -> "unknown"
        }
    }
    
    private fun determineLocation(data: FiduciaryData): String {
        return when (data.source) {
            "network_scanner" -> "network_edge"
            "patrick_devine" -> "archive"
            else -> "unknown"
        }
    }
    
    private fun determineSecurityClassification(data: FiduciaryData): String {
        return when {
            data.content["protocol"] == "HTTPS" -> "encrypted"
            data.content["protocol"] == "SSH" -> "secure"
            data.content["protocol"] == "HTTP" -> "unencrypted"
            else -> "unknown"
        }
    }
    
    private fun determinePriority(data: FiduciaryData): String {
        return when {
            data.content["status"] == "open" -> "high"
            data.content["status"] == "closed" -> "low"
            else -> "medium"
        }
    }
    
    private fun generateTags(data: FiduciaryData): List<String> {
        val tags = mutableListOf<String>()
        tags.add(data.source)
        tags.add(data.content["protocol"]?.toString() ?: "unknown_protocol")
        tags.add(data.content["status"]?.toString() ?: "unknown_status")
        return tags
    }
    
    fun getStorage(): Map<String, FiduciaryData> = storage.toMap()
    fun getPercolationFlow(): SharedFlow<FiduciaryData> = percolationFlow.asSharedFlow()
    
    private suspend fun flushBatch() {
        if (batchBuffer.isEmpty()) return
        val docs = batchBuffer.toList()
        val auditEntries = auditBuffer.toList()
        batchBuffer.clear()
        auditBuffer.clear()
        
        try {
            // Simulate storage
            docs.forEach { doc ->
                storage[doc.id] = FiduciaryData(
                    id = doc.id,
                    source = doc.metadata["source"] ?: "unknown",
                    content = doc.data,
                    stage = doc.type,
                    timestamp = doc.timestamp.toEpochMilliseconds()
                )
            }
            
            // Emit metrics and pop events
            docs.forEach { doc ->
                println("🍿 Pop! Document ${doc.id} scanned in.")
                emitMetric("document.ingested", 1.0, mapOf("type" to doc.type, "source" to doc.metadata["source"]))
            }
            
            emitMetric("batch.stored", 1.0, mapOf(
                "total" to docs.size.toString(),
                "successful" to docs.size.toString(),
                "failed" to "0"
            ))
            
            println("✅ Batch stored: ${docs.size}/${docs.size} successful, 0 failed")
            println("📝 Audit logs stored: ${auditEntries.size} entries")
            
        } catch (e: Exception) {
            println("❌ Error storing batch: ${e.message}")
            emitMetric("batch.error", 1.0, mapOf("error" to e.message ?: "unknown"))
        }
    }
    
    private fun emitMetric(name: String, value: Double, labels: Map<String, String> = emptyMap()) {
        val labelString = if (labels.isNotEmpty()) {
            "{" + labels.entries.joinToString(",") { "${it.key}=\"${it.value}\"" } + "}"
        } else ""
        println("📊 METRIC: $name$labelString $value")
    }
    
    private fun generateDemoData(): List<FiduciaryData> {
        return listOf(
            FiduciaryData(
                id = "scan_001",
                source = "network_scanner",
                content = mapOf(
                    "protocol" to "HTTP",
                    "port" to 80,
                    "status" to "open",
                    "response_time" to 45,
                    "headers" to mapOf("Server" to "nginx/1.18.0")
                )
            ),
            FiduciaryData(
                id = "scan_002",
                source = "network_scanner",
                content = mapOf(
                    "protocol" to "HTTPS", 
                    "port" to 443,
                    "status" to "open",
                    "response_time" to 120,
                    "certificate" to mapOf("issuer" to "Let's Encrypt")
                )
            ),
            FiduciaryData(
                id = "scan_003",
                source = "network_scanner", 
                content = mapOf(
                    "protocol" to "SSH",
                    "port" to 22,
                    "status" to "open", 
                    "banner" to "OpenSSH_8.2p1 Ubuntu-4ubuntu0.5",
                    "key_exchange" to "curve25519-sha256"
                )
            ),
            FiduciaryData(
                id = "scan_004",
                source = "network_scanner",
                content = mapOf(
                    "protocol" to "QUIC",
                    "port" to 443,
                    "status" to "open",
                    "alpn" to "h3",
                    "streams" to 100
                )
            )
        )
    }
}

// Main function to demonstrate the percolator
suspend fun main() = coroutineScope {
    println("🚀 Starting Fiduciary Percolator Demo")
    
    val percolator = FiduciaryPercolator
    percolator.startPercolation(this)
    
    // Wait a moment for pipelines to start
    delay(1.seconds)
    
    // Generate and ingest demo data
    val demoData = percolator.generateDemoData()
    println("\n📥 Ingesting ${demoData.size} demo documents...")
    
    demoData.forEach { data ->
        percolator.ingest(data)
        delay(100) // Small delay between ingests
    }
    
    // Wait for processing to complete
    delay(3.seconds)
    
    // Show results
    println("\n📊 Percolator Results:")
    val storage = percolator.getStorage()
    storage.forEach { (id, data) ->
        println("  📄 $id: ${data.stage} (${data.content["security_classification"]} priority)")
    }
    
    println("\n✅ Percolator demo completed successfully!")
    println("🍿 Total documents processed: ${storage.size}")
}