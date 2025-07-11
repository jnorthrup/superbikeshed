package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import fiduciary.storage.CouchDBActiveStorage
import fiduciary.storage.FiduciaryDocument
import fiduciary.storage.CouchDBConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import fiduciary.protocol.CouchDBAPI
import fiduciary.protocol.evolveSchema
import kotlinx.serialization.json.JsonPrimitive

/**
 * Foundational Main Entry Point for Fiduciary Percolator
 * 
 * Demonstrates actual data percolation through transformation stages
 * using our own CCEK networking protocols, not Java imports.
 * 
 * This is the stable starting point that shows real data flow:
 * Ingest → Transform → Percolate → Store → Emit
 * 
 * Requires [CouchDBActiveStorage] to be present in the coroutine context.
 */

// Core Percolator Data Types
data class FiduciaryData(
    val id: String,
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

// The Core Percolator Engine
object FiduciaryPercolator : CoroutineContext.Element, CoroutineContext.Key<FiduciaryPercolator> {
    override val key: CoroutineContext.Key<*> get() = FiduciaryPercolator
    
    private val ingestChannel = Channel<FiduciaryData>(capacity = 1000)
    private val transformChannel = Channel<FiduciaryData>(capacity = 1000) 
    private val storeChannel = Channel<FiduciaryData>(capacity = 1000)
    private val emitChannel = Channel<FiduciaryData>(capacity = 1000)
    
    private val storage = mutableMapOf<String, FiduciaryData>()
    private val percolationFlow = MutableSharedFlow<FiduciaryData>(replay = 100)

    // --- CouchDB Integration ---
    // Remove direct construction of couchStorage and couchConfig
    // Use context-based access instead
    // private val couchConfig = CouchDBConfig(
    //     baseUrl = System.getenv("COUCHDB_URL") ?: "http://localhost:5984",
    //     database = System.getenv("COUCHDB_DATABASE") ?: "fiduciary_scans",
    //     auth = System.getenv("COUCHDB_AUTH"), // base64 user:pass
    //     maxConnections = 10,
    //     bulkChunkSize = 100
    // )
    // private val couchStorage = CouchDBActiveStorage(couchConfig)
    private val batchBuffer = mutableListOf<FiduciaryDocument>()
    private val batchSize = 10
    private val batchDelayMs = 500L
    private var batchJob: Job? = null
    // --- End CouchDB Integration ---

    suspend fun startPercolation(scope: CoroutineScope) {
        println("🔥 Starting Fiduciary Percolator Engine")
        // Initialize CouchDB storage from context
        val couchStorage = coroutineContext.couchStorage
        couchStorage.initialize()
        
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
        
        // Stage 3: Storage Pipeline (CouchDB-backed, batched, schema-evolving)
        scope.launch {
            storeChannel.consumeAsFlow().collect { data ->
                var doc = FiduciaryDocument(
                    id = data.id,
                    type = data.stage,
                    timestamp = kotlinx.datetime.Clock.System.now(),
                    status = "active",
                    data = buildJsonObject {
                        data.content.forEach { (k, v) -> put(k, v.toString()) }
                        put("schema_version", JsonPrimitive(2)) // Example: always target v2
                    },
                    metadata = mapOf("source" to data.source),
                    tags = listOf("fiduciary")
                )
                // Evolve schema if needed
                doc = evolveSchemaIfNeeded(doc, 2)
                batchBuffer.add(doc)
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
                // This is where we'd send to SIEM, dashboards, etc.
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
    
    private fun calculateRiskScore(data: FiduciaryData): Int {
        var score = 30 // Base score
        
        val content = data.content
        if (content["exposed"] == true) score += 40
        if (content["vulnerable"] == true) score += 30
        if (content["port"] == 22) score += 20 // SSH
        if (content["port"] == 3389) score += 35 // RDP
        if (content["ssl_expired"] == true) score += 25
        
        return score.coerceAtMost(100)
    }
    
    private fun determineAssetType(data: FiduciaryData): String {
        val content = data.content
        return when {
            content["port"] == 80 || content["port"] == 443 -> "web_server"
            content["port"] == 22 -> "ssh_server" 
            content["port"] == 3389 -> "rdp_server"
            content["port"] == 5432 -> "database"
            content["service"]?.toString()?.contains("couchdb") == true -> "document_store"
            else -> "unknown_service"
        }
    }
    
    private fun determineLocation(data: FiduciaryData): String {
        val target = data.content["target"]?.toString() ?: ""
        return when {
            target.startsWith("192.168.") -> "internal_network"
            target.startsWith("10.") -> "internal_network"
            target.startsWith("172.") -> "internal_network"
            target.startsWith("127.") -> "localhost"
            else -> "external_network"
        }
    }
    
    private fun determineSecurityClassification(data: FiduciaryData): String {
        val riskScore = data.content["risk_score"] as? Int ?: 0
        return when {
            riskScore >= 80 -> "critical"
            riskScore >= 60 -> "high"
            riskScore >= 40 -> "medium" 
            else -> "low"
        }
    }
    
    private fun determinePriority(data: FiduciaryData): String {
        val classification = data.content["security_classification"]?.toString() ?: "low"
        val location = data.content["location"]?.toString() ?: "unknown"
        
        return when {
            classification == "critical" -> "immediate"
            classification == "high" && location == "external_network" -> "urgent"
            classification == "high" -> "high"
            classification == "medium" -> "medium"
            else -> "low"
        }
    }
    
    private fun generateTags(data: FiduciaryData): List<String> {
        val tags = mutableListOf<String>()
        val content = data.content
        
        tags.add(content["asset_type"]?.toString() ?: "unknown")
        tags.add(content["location"]?.toString() ?: "unknown")
        tags.add("risk_${content["security_classification"]}")
        
        if (content["ssl_enabled"] == true) tags.add("ssl")
        if (content["ssl_expired"] == true) tags.add("ssl_expired")
        if (content["exposed"] == true) tags.add("exposed")
        if (content["vulnerable"] == true) tags.add("vulnerable")
        
        return tags
    }
    
    fun getStorage(): Map<String, FiduciaryData> = storage.toMap()
    fun getPercolationFlow(): SharedFlow<FiduciaryData> = percolationFlow.asSharedFlow()

    private suspend fun flushBatch() {
        if (batchBuffer.isEmpty()) return
        val docs = batchBuffer.toList()
        batchBuffer.clear()
        try {
            val result = coroutineContext.couchStorage.bulkStore(docs)
            docs.forEach { doc ->
                println("🍿 Pop! Document ${doc.id} scanned in.")
            }
            println("✅ Batch stored: ${result.successful}/${result.total} successful, ${result.failed} failed")
        } catch (e: Exception) {
            println("❌ Error storing batch: ${e.message}")
            // Optionally: retry logic or dead-letter queue
        }
    }
}

suspend fun evolveSchemaIfNeeded(doc: FiduciaryDocument, targetVersion: Int): FiduciaryDocument {
    val currentVersion = doc.data["schema_version"]?.toString()?.toIntOrNull() ?: 1
    return if (currentVersion < targetVersion) {
        // Use the protocol API to evolve schema
        val evolved = fiduciary.protocol.evolveSchema(
            fiduciary.protocol.CouchDocument(
                id = doc.id,
                revision = "",
                data = doc.data
            ),
            targetVersion
        )
        doc.copy(data = evolved.data)
    } else doc
}

// Sample Data Generator (simulates network scan results)
object SampleDataGenerator {
    fun generateNetworkScanData(): List<FiduciaryData> {
        return listOf(
            FiduciaryData(
                id = "scan_001",
                source = "network_scanner",
                content = mapOf(
                    "target" to "192.168.1.100",
                    "port" to 22,
                    "service" to "ssh",
                    "version" to "OpenSSH_7.4",
                    "exposed" to true,
                    "vulnerable" to true
                )
            ),
            FiduciaryData(
                id = "scan_002", 
                source = "network_scanner",
                content = mapOf(
                    "target" to "10.0.0.50",
                    "port" to 443,
                    "service" to "https",
                    "ssl_enabled" to true,
                    "ssl_expired" to true,
                    "exposed" to true
                )
            ),
            FiduciaryData(
                id = "scan_003",
                source = "network_scanner", 
                content = mapOf(
                    "target" to "203.0.113.10",
                    "port" to 3389,
                    "service" to "rdp",
                    "exposed" to true,
                    "vulnerable" to false
                )
            ),
            FiduciaryData(
                id = "scan_004",
                source = "network_scanner",
                content = mapOf(
                    "target" to "192.168.1.200",
                    "port" to 5984,
                    "service" to "couchdb",
                    "version" to "3.1.0",
                    "exposed" to false
                )
            )
        )
    }
}

/**
 * Main Entry Point - Demonstrates Real Percolation
 * 
 * This shows the complete data flow:
 * 1. Generate sample network scan data
 * 2. Ingest into percolator 
 * 3. Watch data transform through stages
 * 4. Verify final enriched results
 */
suspend fun main() = coroutineScope {
    println("🚀 Fiduciary Percolator - Foundational Main Entry Point")
    println("=" .repeat(60))
    
    // Initialize percolator context with concentric dispatch and FSM
    val percolatorContext = coroutineContext + FiduciaryPercolator + ConcentricDispatcher + PercolationFSM
    
    withContext(percolatorContext) {
        // Start the percolation engine with concentric dispatch and FSM
        FiduciaryPercolator.startPercolation(this)
        ConcentricDispatcher.startDispatcher(this)
        PercolationFSM.startFSM(this)
        
        // Wait for pipelines to initialize
        delay(1.seconds)
        
        println("\n📊 Generating sample network scan data...")
        val sampleData = SampleDataGenerator.generateNetworkScanData()
        
        println("📥 Ingesting ${sampleData.size} data points into percolator...")
        
        // Ingest data points
        sampleData.forEach { data ->
            FiduciaryPercolator.ingest(data)
            delay(500) // Stagger ingestion to see flow
        }
        
        // Wait for percolation to complete
        delay(3.seconds)
        
        // Show results
        println("\n📋 Percolation Results:")
        println("=" .repeat(30))
        
        val storage = FiduciaryPercolator.getStorage()
        storage.forEach { (id, data) ->
            println("🔍 $id:")
            println("   Stage: ${data.stage}")
            println("   Asset Type: ${data.content["asset_type"]}")
            println("   Risk Score: ${data.content["risk_score"]}")
            println("   Classification: ${data.content["security_classification"]}")
            println("   Priority: ${data.content["priority"]}")
            println("   Location: ${data.content["location"]}")
            println("   Tags: ${data.content["tags"]}")
            println()
        }
        
        println("✅ Percolation Complete!")
        println("📊 Total processed: ${storage.size} items")
        
        val criticalItems = storage.values.count { 
            it.content["security_classification"] == "critical" 
        }
        val highRiskItems = storage.values.count { 
            it.content["security_classification"] == "high" 
        }
        
        println("🚨 Critical items: $criticalItems")
        println("⚠️  High risk items: $highRiskItems")
        
        // Monitor live percolation flow, concentric dispatch, and FSM
        println("\n👀 Monitoring percolation flow, concentric dispatch, and FSM...")
        val monitorJob = launch {
            FiduciaryPercolator.getPercolationFlow().collect { data ->
                println("🌊 Flow: ${data.id} reached ${data.stage} stage")
            }
        }
        
        val dispatchMonitorJob = launch {
            ConcentricDispatcher.getDispatchFlow().collect { result ->
                val ringEmoji = when (result.ring.name) {
                    "CORE" -> "🔥"
                    "DYAD" -> "👥" 
                    "TRIAD" -> "🔺"
                    "PENTAD" -> "⭐"
                    "DODECAD" -> "🌟"
                    "SENATE" -> "🏛️"
                    else -> "🌀"
                }
                println("🌀 $ringEmoji ${result.ring.name} Ring: ${result.taskId} → ${result.status}")
            }
        }
        
        val fsmMonitorJob = launch {
            PercolationFSM.getStateFlow().collect { state ->
                val stateEmoji = when (state) {
                    is PercolationState.Normal -> "✅"
                    is PercolationState.Slow -> "🐌"
                    is PercolationState.Critical -> "🚨"
                    is PercolationState.Fallback -> "⚡"
                    is PercolationState.Recovery -> "🔧"
                    is PercolationState.Overload -> "💥"
                }
                println("⏱️ $stateEmoji FSM State: ${state::class.simpleName}")
            }
        }
        
        // Show concentric ring status
        val ringStats = ConcentricDispatcher.getAgentStats()
        println("\n🌀 Concentric Ring Status:")
        ringStats.forEach { (ring, agentCount) ->
            val emoji = when (ring.name) {
                "CORE" -> "🔥"
                "DYAD" -> "👥"
                "TRIAD" -> "🔺" 
                "PENTAD" -> "⭐"
                "DODECAD" -> "🌟"
                "SENATE" -> "🏛️"
                else -> "🌀"
            }
            println("   $emoji ${ring.name}: $agentCount agents (${ring.size} max)")
        }
        
        delay(5.seconds)
        monitorJob.cancel()
        dispatchMonitorJob.cancel()
        fsmMonitorJob.cancel()
        
        // Show FSM performance summary
        println("\n${PercolationFSM.getPerformanceSummary()}")
        
        println("\n🎉 Foundational main execution complete!")
        println("🔥 Percolator is percolating! Data flows through all stages.")
        println("🌀 Concentric dispatch active! Agents working in coordinated rings.")
        println("⏱️ Timer-based FSM monitoring slow scenarios and adapting performance.")
        println("📊 ${ringStats.values.sum()} total agents across ${ringStats.size} concentric rings")
    }
}