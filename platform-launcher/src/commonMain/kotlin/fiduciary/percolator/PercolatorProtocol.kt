package fiduciary.percolator

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant

/**
 * Percolator Network Protocol
 * 
 * Defines the protocol for distributed content extraction
 */

// === Protocol Messages ===

@Serializable
sealed class PercolatorMessage {
    abstract val messageId: String
    abstract val timestamp: Instant
}

// Node -> Coordinator Messages

@Serializable
data class RegisterNode(
    override val messageId: String,
    override val timestamp: Instant,
    val nodeId: String,
    val capabilities: NodeCapabilities
) : PercolatorMessage()

@Serializable
data class ClaimWorkRequest(
    override val messageId: String,
    override val timestamp: Instant,
    val nodeId: String,
    val maxWork: Int = 1
) : PercolatorMessage()

@Serializable
data class WorkProgress(
    override val messageId: String,
    override val timestamp: Instant,
    val nodeId: String,
    val workUnitId: String,
    val filesProcessed: Int,
    val totalFiles: Int,
    val estimatedCompletion: Instant?
) : PercolatorMessage()

@Serializable
data class WorkResult(
    override val messageId: String,
    override val timestamp: Instant,
    val nodeId: String,
    val workUnitId: String,
    val results: List<ProcessedContent>,
    val stats: ProcessingStats
) : PercolatorMessage()

// Coordinator -> Node Messages

@Serializable
data class WorkAssignment(
    override val messageId: String,
    override val timestamp: Instant,
    val workUnit: WorkUnit,
    val deadline: Instant
) : PercolatorMessage()

@Serializable
data class WorkAcknowledgment(
    override val messageId: String,
    override val timestamp: Instant,
    val workUnitId: String,
    val accepted: Boolean,
    val reason: String? = null
) : PercolatorMessage()

// === Capabilities ===

@Serializable
data class NodeCapabilities(
    val maxConcurrentWork: Int,
    val supportedFormats: List<String> = listOf("zip", "tar", "gz"),
    val hasOCR: Boolean = false,
    val hasAudioTranscription: Boolean = false,
    val maxBandwidthMbps: Int = 100,
    val processingPower: ProcessingPower = ProcessingPower.MEDIUM
)

@Serializable
enum class ProcessingPower {
    LOW,    // Raspberry Pi, old laptop
    MEDIUM, // Modern laptop
    HIGH    // Desktop with GPU
}

// === Processing Stats ===

@Serializable
data class ProcessingStats(
    val startTime: Instant,
    val endTime: Instant,
    val filesProcessed: Int,
    val bytesDownloaded: Long,
    val bytesProcessed: Long,
    val errorsEncountered: Int,
    val averageProcessingTimeMs: Long
)

// === Work Distribution Strategy ===

/**
 * Strategy for distributing work across nodes
 */
interface WorkDistributionStrategy {
    fun assignWork(
        availableWork: List<WorkUnit>,
        activeNodes: List<NodeInfo>
    ): List<WorkAssignment>
}

/**
 * Simple round-robin distribution
 */
class RoundRobinDistribution : WorkDistributionStrategy {
    private var lastNodeIndex = 0
    
    override fun assignWork(
        availableWork: List<WorkUnit>,
        activeNodes: List<NodeInfo>
    ): List<WorkAssignment> {
        if (activeNodes.isEmpty() || availableWork.isEmpty()) {
            return emptyList()
        }
        
        val assignments = mutableListOf<WorkAssignment>()
        
        availableWork.forEach { work ->
            val node = activeNodes[lastNodeIndex % activeNodes.size]
            lastNodeIndex++
            
            if (node.canAcceptWork()) {
                assignments.add(WorkAssignment(
                    messageId = generateMessageId(),
                    timestamp = kotlinx.datetime.Clock.System.now(),
                    workUnit = work,
                    deadline = kotlinx.datetime.Clock.System.now() + 
                              kotlin.time.Duration.hours(1)
                ))
            }
        }
        
        return assignments
    }
}

/**
 * Capability-based distribution
 */
class CapabilityBasedDistribution : WorkDistributionStrategy {
    override fun assignWork(
        availableWork: List<WorkUnit>,
        activeNodes: List<NodeInfo>
    ): List<WorkAssignment> {
        val assignments = mutableListOf<WorkAssignment>()
        
        // Sort work by complexity
        val sortedWork = availableWork.sortedByDescending { 
            it.entries.sumOf { it.uncompressedSize } 
        }
        
        // Assign complex work to powerful nodes
        sortedWork.forEach { work ->
            val bestNode = activeNodes
                .filter { it.canAcceptWork() }
                .maxByOrNull { 
                    when (it.capabilities.processingPower) {
                        ProcessingPower.HIGH -> 3
                        ProcessingPower.MEDIUM -> 2
                        ProcessingPower.LOW -> 1
                    }
                }
            
            if (bestNode != null) {
                assignments.add(WorkAssignment(
                    messageId = generateMessageId(),
                    timestamp = kotlinx.datetime.Clock.System.now(),
                    workUnit = work,
                    deadline = calculateDeadline(work, bestNode)
                ))
            }
        }
        
        return assignments
    }
    
    private fun calculateDeadline(
        work: WorkUnit,
        node: NodeInfo
    ): Instant {
        val complexity = work.entries.sumOf { it.uncompressedSize }
        val processingTime = when (node.capabilities.processingPower) {
            ProcessingPower.HIGH -> 30
            ProcessingPower.MEDIUM -> 60
            ProcessingPower.LOW -> 120
        }
        
        return kotlinx.datetime.Clock.System.now() + 
               kotlin.time.Duration.minutes(processingTime)
    }
}

// === Node Management ===

@Serializable
data class NodeInfo(
    val nodeId: String,
    val capabilities: NodeCapabilities,
    val status: NodeStatus,
    val lastSeen: Instant,
    val activeWork: List<String> = emptyList(),
    val completedWork: Int = 0,
    val reputation: Double = 1.0
)

@Serializable
enum class NodeStatus {
    ONLINE,
    BUSY,
    OFFLINE,
    BANNED
}

fun NodeInfo.canAcceptWork(): Boolean {
    return status == NodeStatus.ONLINE && 
           activeWork.size < capabilities.maxConcurrentWork
}

// === Utility Functions ===

fun generateMessageId(): String {
    return "msg_${System.currentTimeMillis()}_${(0..9999).random()}"
}

/**
 * Calculate reward for completed work
 */
fun calculateReward(
    stats: ProcessingStats,
    quality: Double = 1.0
): RewardPoints {
    val baseReward = stats.filesProcessed * 10
    val efficiencyBonus = if (stats.averageProcessingTimeMs < 1000) 50 else 0
    val qualityMultiplier = quality
    
    return RewardPoints(
        points = ((baseReward + efficiencyBonus) * qualityMultiplier).toInt(),
        reason = "Processed ${stats.filesProcessed} files"
    )
}

@Serializable
data class RewardPoints(
    val points: Int,
    val reason: String
)