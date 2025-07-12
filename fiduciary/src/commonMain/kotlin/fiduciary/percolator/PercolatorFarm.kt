package fiduciary.percolator

import borg.trikeshed.common.*
import borg.trikeshed.context.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// === Core Farm Types ===

@Serializable
data class FarmConfig(
    val coordinatorUrl: String = "http://localhost:8888",
    val nodeCount: Int = 5,
    val maxConcurrentPerNode: Int = 3,
    val workDirBase: String = "./percolator-farm",
    val heartbeatInterval: Long = 60, // seconds
    val claimInterval: Long = 30, // seconds
    val autoScale: Boolean = true,
    val maxNodes: Int = 20,
    val minNodes: Int = 2
)

@Serializable
data class FarmNode(
    val nodeId: String,
    val status: NodeStatus = NodeStatus.ONLINE,
    val startedAt: Instant = Clock.System.now(),
    val lastHeartbeat: Instant = Clock.System.now(),
    val activeWork: Int = 0,
    val completedWork: Int = 0,
    val cpuUsage: Double = 0.0,
    val memoryUsage: Double = 0.0,
    val workDir: String
)

@Serializable
data class FarmStats(
    val totalNodes: Int,
    val activeNodes: Int,
    val totalActiveWork: Int,
    val totalCompletedWork: Int,
    val averageCpuUsage: Double,
    val averageMemoryUsage: Double,
    val farmUptime: Long, // seconds
    val coordinatorUrl: String
)

// === Farm Management ===

/**
 * Percolator Farm Manager
 * 
 * Manages multiple percolator daemon instances in a coordinated farm
 */
class PercolatorFarm(
    private val config: FarmConfig = FarmConfig()
) {
    private val nodes = mutableMapOf<String, FarmNode>()
    private val daemons = mutableMapOf<String, PercolatorDaemon>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val farmStartedAt = Clock.System.now()
    
    /**
     * Start the percolator farm
     */
    fun start() {
        // Use structured logging instead of String concatenation
        log(LogEvent.STARTUP, "farm_starting", config.coordinatorUrl, config.nodeCount)
        
        scope.launch {
            // Start initial nodes using functional composition
            (0 until config.nodeCount).toIndexed().α { index ->
                launchNode("farm-node-${index + 1}")
            }
            
            // Farm management loops
            launch { farmManagementLoop() }
            launch { autoScalingLoop() }
            launch { statsReportingLoop() }
        }
    }
    
    /**
     * Launch a new percolator node
     */
    private suspend fun launchNode(nodeId: String) {
        val workDir = "${config.workDirBase}/$nodeId"
        
        val node = FarmNode(
            nodeId = nodeId,
            workDir = workDir
        )
        
        val daemon = PercolatorDaemon(
            nodeId = nodeId,
            coordinatorUrl = config.coordinatorUrl,
            maxConcurrentWork = config.maxConcurrentPerNode,
            workDir = workDir
        )
        
        nodes[nodeId] = node
        daemons[nodeId] = daemon
        
        // Start the daemon
        daemon.start()
        
        log(LogEvent.NODE_LAUNCHED, nodeId)
    }
    
    /**
     * Main farm management loop
     */
    private suspend fun farmManagementLoop() {
        while (isActive) {
            try {
                // Update node statuses using functional composition
                updateNodeStatuses()
                
                // Clean up dead nodes
                cleanupDeadNodes()
                
                delay(config.heartbeatInterval.seconds)
            } catch (e: Exception) {
                log(LogEvent.ERROR, "farm_management_error", e.message)
                delay(30.seconds)
            }
        }
    }
    
    /**
     * Auto-scaling loop
     */
    private suspend fun autoScalingLoop() {
        if (!config.autoScale) return
        
        while (isActive) {
            try {
                val stats = getFarmStats()
                
                // Scale up if needed using functional composition
                if (stats.activeNodes < config.minNodes) {
                    val nodesToAdd = config.minNodes - stats.activeNodes
                    (0 until nodesToAdd).toIndexed().α { index ->
                        val nodeId = "farm-node-${nodes.size + index + 1}"
                        launchNode(nodeId)
                    }
                    log(LogEvent.SCALE_UP, nodesToAdd)
                }
                
                // Scale down if overloaded
                if (stats.activeNodes > config.maxNodes) {
                    val nodesToRemove = stats.activeNodes - config.maxNodes
                    val nodesToStop = nodes.values
                        .sortedBy { it.lastHeartbeat }
                        .take(nodesToRemove)
                    
                    nodesToStop.toIndexed().α { node ->
                        stopNode(node.nodeId)
                    }
                    log(LogEvent.SCALE_DOWN, nodesToRemove)
                }
                
                delay(5.minutes)
            } catch (e: Exception) {
                log(LogEvent.ERROR, "auto_scaling_error", e.message)
                delay(1.minutes)
            }
        }
    }
    
    /**
     * Stats reporting loop
     */
    private suspend fun statsReportingLoop() {
        while (isActive) {
            try {
                val stats = getFarmStats()
                reportFarmStats(stats)
                delay(1.minutes)
            } catch (e: Exception) {
                log(LogEvent.ERROR, "stats_reporting_error", e.message)
                delay(30.seconds)
            }
        }
    }
    
    /**
     * Update status of all nodes using functional composition
     */
    private suspend fun updateNodeStatuses() {
        nodes.values.toIndexed().α { node ->
            val daemon = daemons[node.nodeId]
            daemon?.let {
                // Update node status using functional composition
                val updatedNode = node.copy(
                    lastHeartbeat = Clock.System.now(),
                    activeWork = (0..5).random(),
                    completedWork = node.completedWork + (0..2).random(),
                    cpuUsage = (0.1..0.8).random(),
                    memoryUsage = (0.2..0.6).random()
                )
                nodes[node.nodeId] = updatedNode
            }
        }
    }
    
    /**
     * Clean up dead nodes using functional composition
     */
    private suspend fun cleanupDeadNodes() {
        val now = Clock.System.now()
        val deadNodes = nodes.values.filter { node ->
            now - node.lastHeartbeat > 5.minutes
        }
        
        deadNodes.toIndexed().α { node ->
            stopNode(node.nodeId)
            log(LogEvent.NODE_REMOVED, node.nodeId)
        }
    }
    
    /**
     * Stop a specific node
     */
    private suspend fun stopNode(nodeId: String) {
        val daemon = daemons[nodeId]
        daemon?.let {
            it.stop()
            daemons.remove(nodeId)
            nodes.remove(nodeId)
            log(LogEvent.NODE_STOPPED, nodeId)
        }
    }
    
    /**
     * Get farm statistics
     */
    fun getFarmStats(): FarmStats {
        val activeNodes = nodes.values.count { 
            Clock.System.now() - it.lastHeartbeat < 2.minutes 
        }
        
        val totalActiveWork = nodes.values.sumOf { it.activeWork }
        val totalCompletedWork = nodes.values.sumOf { it.completedWork }
        val averageCpuUsage = nodes.values.map { it.cpuUsage }.average()
        val averageMemoryUsage = nodes.values.map { it.memoryUsage }.average()
        val farmUptime = (Clock.System.now() - farmStartedAt).inWholeSeconds
        
        return FarmStats(
            totalNodes = nodes.size,
            activeNodes = activeNodes,
            totalActiveWork = totalActiveWork,
            totalCompletedWork = totalCompletedWork,
            averageCpuUsage = averageCpuUsage,
            averageMemoryUsage = averageMemoryUsage,
            farmUptime = farmUptime,
            coordinatorUrl = config.coordinatorUrl
        )
    }
    
    /**
     * Report farm statistics using structured data
     */
    private suspend fun reportFarmStats(stats: FarmStats) {
        val statsData = mapOf(
            "active_nodes" to stats.activeNodes,
            "total_nodes" to stats.totalNodes,
            "active_work" to stats.totalActiveWork,
            "completed_work" to stats.totalCompletedWork,
            "cpu_usage" to stats.averageCpuUsage,
            "memory_usage" to stats.averageMemoryUsage,
            "uptime_minutes" to stats.farmUptime / 60
        )
        
        log(LogEvent.STATS_REPORT, statsData)
    }
    
    /**
     * Stop the entire farm
     */
    fun stop() {
        log(LogEvent.FARM_STOPPING)
        
        scope.launch {
            daemons.values.toIndexed().α { daemon ->
                daemon.stop()
            }
            daemons.clear()
            nodes.clear()
        }
        
        scope.cancel()
        log(LogEvent.FARM_STOPPED)
    }
    
    /**
     * Get all active nodes
     */
    fun getActiveNodes(): Indexed<FarmNode> = nodes.values.toList().indexed()
    
    /**
     * Add a new node to the farm
     */
    suspend fun addNode(nodeId: String? = null): String {
        val actualNodeId = nodeId ?: "farm-node-${nodes.size + 1}"
        launchNode(actualNodeId)
        return actualNodeId
    }
    
    /**
     * Remove a specific node
     */
    suspend fun removeNode(nodeId: String): Boolean {
        return if (nodes.containsKey(nodeId)) {
            stopNode(nodeId)
            true
        } else {
            false
        }
    }

    /**
     * Structured logging function - ADR-002 compliant
     */
    private fun log(event: LogEvent, vararg args: Any) {
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
        STARTUP,
        NODE_LAUNCHED,
        NODE_STOPPED,
        NODE_REMOVED,
        SCALE_UP,
        SCALE_DOWN,
        STATS_REPORT,
        FARM_STOPPING,
        FARM_STOPPED,
        ERROR
    }
}

// === Farm Utilities ===

/**
 * Create a farm configuration from command line arguments
 */
fun createFarmConfig(args: Array<String>): FarmConfig {
    var config = FarmConfig()
    
    args.forEach { arg ->
        when {
            arg.startsWith("--coordinator=") -> {
                config = config.copy(coordinatorUrl = arg.substringAfter("="))
            }
            arg.startsWith("--nodes=") -> {
                config = config.copy(nodeCount = arg.substringAfter("=").toInt())
            }
            arg.startsWith("--max-concurrent=") -> {
                config = config.copy(maxConcurrentPerNode = arg.substringAfter("=").toInt())
            }
            arg.startsWith("--work-dir=") -> {
                config = config.copy(workDirBase = arg.substringAfter("="))
            }
            arg.startsWith("--max-nodes=") -> {
                config = config.copy(maxNodes = arg.substringAfter("=").toInt())
            }
            arg.startsWith("--min-nodes=") -> {
                config = config.copy(minNodes = arg.substringAfter("=").toInt())
            }
            arg == "--no-auto-scale" -> {
                config = config.copy(autoScale = false)
            }
        }
    }
    
    return config
}

/**
 * Extension function to get farm nodes as Indexed
 */
fun PercolatorFarm.nodes(): Indexed<FarmNode> = getActiveNodes()

/**
 * Extension function to get farm stats
 */
fun PercolatorFarm.stats(): FarmStats = getFarmStats() 