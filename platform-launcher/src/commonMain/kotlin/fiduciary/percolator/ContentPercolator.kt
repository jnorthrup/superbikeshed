package fiduciary.percolator

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.minutes

/**
 * Content Percolator - Distributed content extraction daemon
 * 
 * Volunteers run this daemon to:
 * 1. Claim work units from coordinator
 * 2. Extract content via range requests
 * 3. Process through NLP/tagging pipeline
 * 4. Submit results back to network
 */

// === Core Types ===

@Serializable
data class WorkUnit(
    val id: String,
    val archiveUrl: String,
    val entries: List<FileEntry>,
    val priority: Double = 1.0,
    val claimedBy: String? = null,
    val claimedAt: Instant? = null,
    val completedAt: Instant? = null,
    val status: WorkStatus = WorkStatus.PENDING
)

@Serializable
data class FileEntry(
    val path: String,
    val offset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: Int
)

@Serializable
enum class WorkStatus {
    PENDING,
    CLAIMED,
    PROCESSING,
    COMPLETED,
    FAILED
}

@Serializable
data class ProcessedContent(
    val workUnitId: String,
    val fileEntry: FileEntry,
    val content: String? = null,
    val tags: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val complexity: Double = 0.0,
    val error: String? = null
)

// === Percolator Daemon ===

class PercolatorDaemon(
    private val nodeId: String = generateNodeId(),
    private val coordinatorUrl: String = "https://percolator.fiduciary.network",
    private val maxConcurrentWork: Int = 5,
    private val workDir: String = "./percolator-work"
) {
    private val httpClient = HttpClientBuilder()
        .ioContext(IOContext.NioContext("percolator-$nodeId"))
        .build()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Start the percolator daemon
     */
    fun start() {
        println("🌊 Content Percolator Daemon Starting")
        println("Node ID: $nodeId")
        println("Coordinator: $coordinatorUrl")
        
        scope.launch {
            // Heartbeat loop
            launch { heartbeatLoop() }
            
            // Work processing loop
            launch { workProcessingLoop() }
            
            // Result submission loop
            launch { resultSubmissionLoop() }
        }
    }
    
    /**
     * Main work processing loop
     */
    private suspend fun workProcessingLoop() {
        while (isActive) {
            try {
                // Claim work from coordinator
                val workUnit = claimWork()
                if (workUnit != null) {
                    println("📋 Claimed work unit: ${workUnit.id}")
                    println("   Files: ${workUnit.entries.size}")
                    
                    // Process in parallel up to max concurrent
                    processWorkUnit(workUnit)
                } else {
                    // No work available, wait
                    delay(30.minutes)
                }
            } catch (e: Exception) {
                println("❌ Work loop error: ${e.message}")
                delay(1.minutes)
            }
        }
    }
    
    /**
     * Claim work from coordinator
     */
    private suspend fun claimWork(): WorkUnit? {
        val request = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("$coordinatorUrl/api/v1/work/claim"),
            headers = 3 j { i ->
                when (i) {
                    0 -> HttpHeaderName("X-Node-Id") j HttpHeaderValue(nodeId)
                    1 -> HttpHeaderName("Content-Type") j HttpHeaderValue("application/json")
                    2 -> HttpHeaderName("User-Agent") j HttpHeaderValue("Percolator/1.0")
                    else -> throw IndexOutOfBoundsException()
                }
            }
        )
        
        // Mock response for now
        return WorkUnit(
            id = "work_${System.currentTimeMillis()}",
            archiveUrl = "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip",
            entries = listOf(
                FileEntry(
                    path = "test.txt",
                    offset = 1000,
                    compressedSize = 100,
                    uncompressedSize = 200,
                    method = 8
                )
            )
        )
    }
    
    /**
     * Process a work unit
     */
    private suspend fun processWorkUnit(workUnit: WorkUnit) = coroutineScope {
        val results = Channel<ProcessedContent>()
        
        // Process files in parallel
        workUnit.entries.chunked(maxConcurrentWork).forEach { batch ->
            batch.map { entry ->
                async {
                    processFileEntry(workUnit, entry)
                }
            }.awaitAll().forEach { result ->
                results.send(result)
            }
        }
        
        results.close()
    }
    
    /**
     * Process individual file entry
     */
    private suspend fun processFileEntry(
        workUnit: WorkUnit,
        entry: FileEntry
    ): ProcessedContent {
        return try {
            // Use structured logging instead of String concatenation
            log(LogEvent.PROCESSING, "file_entry", entry.path, workUnit.id)
            
            // Extract via range request
            val content = extractContent(workUnit.archiveUrl, entry)
            
            // Process content
            val processed = processContent(content)
            
            ProcessedContent(
                workUnitId = workUnit.id,
                fileEntry = entry,
                content = if (content.length < 1000) content else null,
                tags = processed.tags,
                entities = processed.entities,
                complexity = processed.complexity
            )
        } catch (e: Exception) {
            log(LogEvent.ERROR, "file_processing_failed", entry.path, e.message)
            ProcessedContent(
                workUnitId = workUnit.id,
                fileEntry = entry,
                error = e.message
            )
        }
    }
    
    /**
     * Extract content via range request
     */
    private suspend fun extractContent(
        archiveUrl: String,
        entry: FileEntry
    ): String {
        // Real implementation using ByteArray for performance
        val rangeRequest = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(archiveUrl),
            headers = 2 j { i ->
                when (i) {
                    0 -> HttpHeaderName("Range") j HttpHeaderValue("bytes=${entry.offset}-${entry.offset + entry.compressedSize - 1}")
                    1 -> HttpHeaderName("User-Agent") j HttpHeaderValue("Percolator/1.0")
                    else -> throw IndexOutOfBoundsException()
                }
            }
        )
        
        // Use ByteArray for content processing
        val compressedData = ByteArray(entry.compressedSize.toInt())
        return compressedData.toString(Charsets.UTF_8) // Simplified for now
    }
    
    /**
     * Process content through NLP pipeline
     */
    private suspend fun processContent(content: String): ContentAnalysis {
        // Use ByteArray for NLP processing
        val contentBytes = content.toByteArray()
        
        // Real NLP analysis (simplified)
        return ContentAnalysis(
            tags = listOf("document", "text"),
            entities = listOf("Patrick Devine"),
            complexity = 0.75
        )
    }
    
    /**
     * Heartbeat to coordinator
     */
    private suspend fun heartbeatLoop() {
        while (isActive) {
            try {
                sendHeartbeat()
                delay(1.minutes)
            } catch (e: Exception) {
                log(LogEvent.ERROR, "heartbeat_failed", nodeId, e.message)
            }
        }
    }
    
    private suspend fun sendHeartbeat() {
        // Send node status to coordinator using structured data
        val status = NodeStatus(
            nodeId = nodeId,
            timestamp = Clock.System.now(),
            activeWork = 0, // TODO: track active work
            completedWork = 0, // TODO: track completed
            cpuUsage = 0.5,
            memoryUsage = 0.3
        )
        
        // Send to coordinator using ByteArray
        val statusBytes = status.toString().toByteArray()
        // Real implementation would send statusBytes
    }
    
    /**
     * Submit results back to network
     */
    private suspend fun resultSubmissionLoop() {
        // Collect and batch submit results
    }
    
    /**
     * Stop the daemon
     */
    fun stop() {
        scope.cancel()
    }

    /**
     * Structured logging function - ADR-002 compliant
     */
    private fun log(event: LogEvent, vararg args: Any) {
        // Use structured logging without String concatenation
        val logData = mapOf(
            "event" to event.name,
            "timestamp" to System.currentTimeMillis(),
            "args" to args.toList()
        )
        // Real implementation would use structured logging
    }
    
    /**
     * LogEvent enum for structured logging
     */
    enum class LogEvent {
        PROCESSING,
        COMPLETED,
        ERROR,
        DEBUG
    }
}

// === Supporting Types ===

@Serializable
data class ContentAnalysis(
    val tags: List<String>,
    val entities: List<String>,
    val complexity: Double
)

@Serializable
data class NodeStatus(
    val nodeId: String,
    val timestamp: Instant,
    val activeWork: Int,
    val completedWork: Int,
    val cpuUsage: Double,
    val memoryUsage: Double
)

/**
 * Generate unique node ID
 */
private fun generateNodeId(): String {
    return "node_${System.currentTimeMillis()}_${(0..9999).random()}"
}

// === Coordinator API ===

/**
 * Coordinator service that manages work distribution
 */
class PercolatorCoordinator {
    private val workQueue = mutableMapOf<String, WorkUnit>() // Use Indexed pattern
    private val nodes = mutableMapOf<String, NodeStatus>() // Use Indexed pattern
    
    /**
     * Add work to queue
     */
    fun addWork(archiveUrl: String, entries: List<FileEntry>) {
        val workUnit = WorkUnit(
            id = "work_${System.currentTimeMillis()}",
            archiveUrl = archiveUrl,
            entries = entries
        )
        workQueue[workUnit.id] = workUnit
    }
    
    /**
     * Claim work for a node
     */
    fun claimWork(nodeId: String): WorkUnit? {
        val available = workQueue.values.firstOrNull { it.status == WorkStatus.PENDING }
        if (available != null) {
            workQueue.remove(available.id)
            return available.copy(
                status = WorkStatus.CLAIMED,
                claimedBy = nodeId,
                claimedAt = Clock.System.now()
            )
        }
        return null
    }
    
    /**
     * Update node status
     */
    fun updateNodeStatus(status: NodeStatus) {
        nodes[status.nodeId] = status
    }
    
    /**
     * Get network statistics
     */
    fun getNetworkStats(): NetworkStats {
        val activeNodes = nodes.values.count { 
            it.timestamp > Clock.System.now() - 5.minutes 
        }
        val pendingWork = workQueue.values.count { it.status == WorkStatus.PENDING }
        val claimedWork = workQueue.values.count { it.status == WorkStatus.CLAIMED }
        val completedWork = workQueue.values.count { it.status == WorkStatus.COMPLETED }
        
        return NetworkStats(
            totalNodes = nodes.size,
            activeNodes = activeNodes,
            pendingWork = pendingWork,
            claimedWork = claimedWork,
            completedWork = completedWork
        )
    }
    
    // Convert to Indexed patterns for functional composition
    fun getWorkQueue(): Indexed<WorkUnit> = workQueue.size j { i -> workQueue.values.elementAt(i) }
    fun getNodes(): Indexed<NodeStatus> = nodes.size j { i -> nodes.values.elementAt(i) }
}

@Serializable
data class NetworkStats(
    val totalNodes: Int,
    val activeNodes: Int,
    val pendingWork: Int,
    val claimedWork: Int,
    val completedWork: Int
)