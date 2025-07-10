@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.launcher

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.net.quic.*
import borg.trikeshed.lib.*
import fiduciary.concentric.*
import fiduciary.fetch.ZipRangeFetcher
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * QUIC Concentric Agent for subnet ingestion
 * 
 * Implements agent behavior within concentric ring topology for
 * distributed content ingestion, particularly for Patrick Devine archives.
 */
class QuicConcentricAgent(
    val agentId: NUID,
    val ring: ConcentricRing,
    val capabilities: Set<AgentCapability>,
    private val server: UringCouchDBServer,
    private val quicEndpoint: QuicEndpoint
) : CoroutineScope {
    
    override val coroutineContext: CoroutineContext = 
        SupervisorJob() + Dispatchers.IO + CoroutineName("Agent-${agentId.toShortString()}")
    
    // Agent state
    private val workQueue = ConcentricWorkQueue(agentId, ring)
    private val activeConnections = ConcurrentHashMap<NUID, QuicConnection>()
    private val peerAgents = ConcurrentHashMap<NUID, ConcentricAgent>()
    
    // Channels for agent communication
    private val taskChannel = Channel<ConcentricTask>(Channel.UNLIMITED)
    private val discoveryChannel = Channel<Discovery>(Channel.UNLIMITED)
    private val stealRequestChannel = Channel<WorkStealRequest>(Channel.UNLIMITED)
    
    // Metrics
    private val metrics = AgentMetrics()
    
    // Work stealing coordinator
    private val workStealingCoordinator = WorkStealingCoordinator(agentId, server)
    
    /**
     * Initialize the agent
     */
    suspend fun initialize() {
        // Start task processor
        launch { processTasksLoop() }
        
        // Start discovery processor
        launch { processDiscoveriesLoop() }
        
        // Start work stealing if enabled
        if (ring.level > 0) { // Not core ring
            launch { workStealingLoop() }
        }
        
        // Connect to peers in same ring
        connectToPeers()
        
        println("🤖 Agent ${agentId.toShortString()} initialized in ${ring.name} ring")
    }
    
    /**
     * Submit a task to this agent
     */
    suspend fun submitTask(task: ConcentricTask): Boolean {
        if (!hasCapabilities(task.requiredCapabilities)) {
            return false
        }
        
        metrics.tasksReceived.incrementAndGet()
        workQueue.push(task)
        taskChannel.send(task)
        
        return true
    }
    
    /**
     * Process tasks from the work queue
     */
    private suspend fun processTasksLoop() {
        for (task in taskChannel) {
            try {
                val startTime = System.currentTimeMillis()
                
                when (task.type) {
                    TaskType.CONTENT_INGESTION -> processIngestionTask(task)
                    TaskType.TRANSCRIPTION -> processTranscriptionTask(task)
                    TaskType.NLP_ANALYSIS -> processNlpTask(task)
                    TaskType.TOPIC_EXTRACTION -> processTopicExtractionTask(task)
                    TaskType.ANOMALY_DETECTION -> processAnomalyDetectionTask(task)
                    TaskType.CROSS_REFERENCE -> processCrossReferenceTask(task)
                    TaskType.VALIDATION -> processValidationTask(task)
                    TaskType.AGGREGATION -> processAggregationTask(task)
                    TaskType.CONSENSUS -> processConsensusTask(task)
                }
                
                metrics.tasksCompleted.incrementAndGet()
                metrics.totalProcessingTime.addAndGet(System.currentTimeMillis() - startTime)
                
            } catch (e: Exception) {
                println("❌ Agent ${agentId.toShortString()} failed to process task ${task.id}: ${e.message}")
                metrics.tasksFailed.incrementAndGet()
            }
        }
    }
    
    /**
     * Process content ingestion tasks (e.g., Patrick Devine archives)
     */
    private suspend fun processIngestionTask(task: ConcentricTask) {
        val payload = task.payload.decodeToString()
        val config = Json.parseToJsonElement(payload).jsonObject
        
        val url = config["url"]?.jsonPrimitive?.content ?: return
        val action = config["action"]?.jsonPrimitive?.content ?: "extract_metadata"
        
        when (action) {
            "extract_metadata" -> {
                // Extract ZIP metadata using range requests
                val metadata = extractZipMetadata(url)
                
                // Store in CouchDB
                val dbName = "patrick_devine_archives"
                server.handleRestRequest("PUT", "/$dbName", null)
                
                val docId = url.substringAfterLast("/").replace(".zip", "_metadata")
                val metadataDoc = buildJsonObject {
                    put("_id", docId)
                    put("type", "archive_metadata")
                    put("url", url)
                    put("extracted_at", Clock.System.now().toString())
                    put("extracted_by", agentId.toString())
                    put("entries", metadata)
                }.toString()
                
                server.handleRestRequest("PUT", "/$dbName/$docId", metadataDoc)
                
                // Create discovery
                val discovery = Discovery(
                    id = NUID.random(),
                    taskId = task.id,
                    agentId = agentId,
                    type = DiscoveryType.PATTERN,
                    content = "Extracted metadata for archive: $url",
                    confidence = 1.0,
                    importance = DiscoveryImportance.MEDIUM
                )
                
                discoveryChannel.send(discovery)
            }
            
            "process_entry" -> {
                // Process specific entry from archive
                val entryName = config["entry"]?.jsonPrimitive?.content ?: return
                processArchiveEntry(url, entryName)
            }
        }
    }
    
    /**
     * Extract ZIP metadata using HTTP range requests
     */
    private suspend fun extractZipMetadata(url: String): JsonArray {
        // In real implementation, would use ZipRangeFetcher
        // For now, return sample metadata
        return buildJsonArray {
            add(buildJsonObject {
                put("name", "sample_entry.mp3")
                put("offset", 0)
                put("size", 1234567)
                put("compressed_size", 1000000)
            })
        }
    }
    
    /**
     * Process specific entry from archive
     */
    private suspend fun processArchiveEntry(url: String, entryName: String) {
        // Extract specific file using range requests
        println("📄 Processing entry $entryName from $url")
        
        // Create subtasks for transcription, analysis, etc.
        if (entryName.endsWith(".mp3") || entryName.endsWith(".wav")) {
            val transcriptionTask = ConcentricTask(
                id = NUID.random(),
                type = TaskType.TRANSCRIPTION,
                payload = Json.encodeToString(buildJsonObject {
                    put("source", url)
                    put("entry", entryName)
                }).toByteArray(),
                requiredCapabilities = setOf(AgentCapability.TRANSCRIPTION),
                priority = TaskPriority.NORMAL,
                submittedBy = agentId
            )
            
            // Submit to appropriate agent
            server.submitTaskToAgent(this.toConcentricAgent(), transcriptionTask)
        }
    }
    
    private suspend fun processTranscriptionTask(task: ConcentricTask) {
        println("🎤 Processing transcription task")
        // Would use Whisper.cpp or similar
        delay(100) // Simulate processing
        
        // Store result
        val transcription = "Sample transcription of audio content"
        storeTranscriptionResult(task.id, transcription)
    }
    
    private suspend fun processNlpTask(task: ConcentricTask) {
        println("🧠 Processing NLP analysis task")
        val text = task.payload.decodeToString()
        
        // Simulate NLP processing
        delay(50)
        
        // Create discovery for interesting patterns
        if (text.contains("important", ignoreCase = true)) {
            val discovery = Discovery(
                id = NUID.random(),
                taskId = task.id,
                agentId = agentId,
                type = DiscoveryType.INSIGHT,
                content = "Found important content in text",
                confidence = 0.85,
                importance = DiscoveryImportance.HIGH
            )
            discoveryChannel.send(discovery)
        }
    }
    
    private suspend fun processTopicExtractionTask(task: ConcentricTask) {
        println("🏷️ Extracting topics")
        delay(75)
        
        // Store extracted topics
        val topics = listOf("technology", "communication", "history")
        storeTopicsResult(task.id, topics)
    }
    
    private suspend fun processAnomalyDetectionTask(task: ConcentricTask) {
        println("🔍 Detecting anomalies")
        delay(100)
        
        // Check for anomalies
        if (Math.random() > 0.8) {
            val discovery = Discovery(
                id = NUID.random(),
                taskId = task.id,
                agentId = agentId,
                type = DiscoveryType.ANOMALY,
                content = "Detected unusual pattern in data",
                confidence = 0.75,
                importance = DiscoveryImportance.HIGH
            )
            discoveryChannel.send(discovery)
        }
    }
    
    private suspend fun processCrossReferenceTask(task: ConcentricTask) {
        println("🔗 Cross-referencing content")
        delay(50)
    }
    
    private suspend fun processValidationTask(task: ConcentricTask) {
        println("✅ Validating content")
        delay(25)
    }
    
    private suspend fun processAggregationTask(task: ConcentricTask) {
        println("📊 Aggregating results")
        delay(50)
    }
    
    private suspend fun processConsensusTask(task: ConcentricTask) {
        println("🤝 Building consensus")
        
        // For core ring agents
        if (ring.level == 0) {
            // Collect votes from other agents
            val votes = collectVotes(task.id)
            
            val decision = QuorumDecision(
                ringLevel = ring.level,
                taskId = task.id,
                votes = votes
            )
            
            if (decision.hasQuorum()) {
                println("✅ Consensus reached for task ${task.id}")
            }
        }
    }
    
    /**
     * Process discoveries and propagate important ones
     */
    private suspend fun processDiscoveriesLoop() {
        for (discovery in discoveryChannel) {
            // Submit to server
            server.submitDiscovery(discovery)
            
            // Propagate to peers if important
            if (discovery.importance >= DiscoveryImportance.HIGH) {
                propagateToPeers(discovery)
            }
        }
    }
    
    /**
     * Work stealing loop for load balancing
     */
    private suspend fun workStealingLoop() {
        while (isActive) {
            if (workQueue.size() == 0) {
                // Try to steal work
                val stolenTask = workStealingCoordinator.attemptWorkStealing()
                if (stolenTask != null) {
                    submitTask(stolenTask)
                }
            }
            
            delay(100) // Check periodically
        }
    }
    
    /**
     * Connect to peer agents in the same ring
     */
    private suspend fun connectToPeers() {
        // In real implementation, would discover peers via DHT or registry
        println("🔗 Connecting to peers in ${ring.name} ring")
    }
    
    /**
     * Propagate discovery to peer agents
     */
    private suspend fun propagateToPeers(discovery: Discovery) {
        peerAgents.values.forEach { peer ->
            // Would send via QUIC
            println("📢 Propagating discovery ${discovery.id} to peer ${peer.id.toShortString()}")
        }
    }
    
    /**
     * Collect votes from peer agents
     */
    private suspend fun collectVotes(taskId: NUID): Map<NUID, Vote> {
        val votes = mutableMapOf<NUID, Vote>()
        
        // Simulate vote collection
        peerAgents.keys.forEach { peerId ->
            votes[peerId] = when {
                Math.random() > 0.2 -> Vote.APPROVE
                Math.random() > 0.5 -> Vote.REJECT
                else -> Vote.ABSTAIN
            }
        }
        
        return votes
    }
    
    /**
     * Store results in CouchDB
     */
    private suspend fun storeTranscriptionResult(taskId: NUID, transcription: String) {
        val dbName = "transcriptions"
        server.handleRestRequest("PUT", "/$dbName", null)
        
        val doc = buildJsonObject {
            put("_id", taskId.toString())
            put("type", "transcription")
            put("content", transcription)
            put("processed_by", agentId.toString())
            put("processed_at", Clock.System.now().toString())
        }.toString()
        
        server.handleRestRequest("PUT", "/$dbName/${taskId}", doc)
    }
    
    private suspend fun storeTopicsResult(taskId: NUID, topics: List<String>) {
        val dbName = "topics"
        server.handleRestRequest("PUT", "/$dbName", null)
        
        val doc = buildJsonObject {
            put("_id", taskId.toString())
            put("type", "topic_extraction")
            put("topics", JsonArray(topics.map { JsonPrimitive(it) }))
            put("processed_by", agentId.toString())
            put("processed_at", Clock.System.now().toString())
        }.toString()
        
        server.handleRestRequest("PUT", "/$dbName/${taskId}", doc)
    }
    
    /**
     * Check if agent has required capabilities
     */
    private fun hasCapabilities(required: Set<AgentCapability>): Boolean {
        return capabilities.containsAll(required)
    }
    
    /**
     * Convert to ConcentricAgent for server API
     */
    fun toConcentricAgent(): ConcentricAgent {
        return ConcentricAgent(
            id = agentId,
            ring = ring,
            capabilities = capabilities,
            quicEndpoint = quicEndpoint
        )
    }
    
    /**
     * Get agent metrics
     */
    fun getMetrics(): AgentMetrics {
        val avgTime = if (metrics.tasksCompleted.get() > 0) {
            metrics.totalProcessingTime.get() / metrics.tasksCompleted.get()
        } else {
            0L
        }
        
        return AgentMetrics(
            agentId = agentId,
            tasksReceived = metrics.tasksReceived.get().toInt(),
            tasksCompleted = metrics.tasksCompleted.get().toInt(),
            averageProcessingTimeMs = avgTime,
            lastActive = Clock.System.now()
        )
    }
    
    /**
     * Shutdown the agent
     */
    fun shutdown() {
        cancel()
        activeConnections.values.forEach { it.close() }
        println("🛑 Agent ${agentId.toShortString()} shutdown")
    }
}

/**
 * Agent metrics tracking
 */
private class AgentMetrics {
    val tasksReceived = AtomicLong(0)
    val tasksCompleted = AtomicLong(0)
    val tasksFailed = AtomicLong(0)
    val totalProcessingTime = AtomicLong(0)
}

/**
 * Work steal request
 */
data class WorkStealRequest(
    val requestingAgent: NUID,
    val targetAgent: NUID,
    val timestamp: Instant = Clock.System.now()
)

/**
 * Extension to convert NUID to short string for logging
 */
fun NUID.toShortString(): String {
    return this.toString().take(8)
}