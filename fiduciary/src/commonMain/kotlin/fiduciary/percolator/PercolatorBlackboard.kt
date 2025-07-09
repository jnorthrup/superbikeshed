package fiduciary.percolator

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.minutes

/**
 * Percolator Blackboard - Content-addressed attention system
 * 
 * When attention is needed, each node waiting in the percolator
 * receives a debenture (promise of future value) that can be
 * redeemed when they contribute to resolving the attention request.
 */

// === Debenture System ===

@Serializable
data class AttentionDebenture(
    val id: String = generateDebentureId(),
    val contentHash: String, // Content-addressed identifier
    val nodeId: String,
    val issuedAt: Instant = Clock.System.now(),
    val expiresAt: Instant,
    val baseValue: Double,
    val multiplier: Double = 1.0,
    val attentionType: AttentionType,
    val redeemableAfter: Instant? = null,
    val redeemedAt: Instant? = null,
    val status: DebentureStatus = DebentureStatus.ISSUED
)

@Serializable
enum class AttentionType {
    EXTRACTION,      // Need help extracting content
    TRANSCRIPTION,   // Audio/video transcription needed
    TRANSLATION,     // Language translation required
    ANALYSIS,        // Deep analysis requested
    VERIFICATION,    // Fact checking/verification
    SYNTHESIS,       // Combine multiple sources
    EMERGENCY        // Urgent attention needed
}

@Serializable
enum class DebentureStatus {
    ISSUED,
    ACTIVE,
    REDEEMABLE,
    REDEEMED,
    EXPIRED,
    CANCELLED
}

// === Blackboard System ===

/**
 * Content-addressed blackboard for sticky attention requests
 */
class PercolatorBlackboard {
    // Content hash -> Sticky note
    private val stickyNotes = mutableMapOf<String, BlackboardSticky>()
    
    // Node -> List of debentures
    private val nodeDebentures = mutableMapOf<String, MutableList<AttentionDebenture>>()
    
    // Active attention requests
    private val attentionQueue = mutableListOf<AttentionRequest>()
    
    /**
     * Post attention request to blackboard
     */
    fun postAttention(
        content: String,
        type: AttentionType,
        priority: Double = 1.0,
        metadata: Map<String, String> = emptyMap()
    ): BlackboardSticky {
        val contentHash = calculateContentHash(content)
        
        val sticky = BlackboardSticky(
            contentHash = contentHash,
            content = content,
            type = type,
            priority = priority,
            metadata = metadata,
            postedAt = Clock.System.now(),
            claimedBy = mutableListOf(),
            solutions = mutableListOf()
        )
        
        stickyNotes[contentHash] = sticky
        
        // Create attention request
        val request = AttentionRequest(
            id = generateRequestId(),
            sticky = sticky,
            debentures = mutableListOf()
        )
        
        attentionQueue.add(request)
        
        // Issue debentures to waiting nodes
        issueDebentures(request)
        
        return sticky
    }
    
    /**
     * Issue debentures to nodes waiting in percolator
     */
    private fun issueDebentures(request: AttentionRequest) {
        val waitingNodes = getWaitingNodes()
        
        waitingNodes.forEach { nodeId ->
            val debenture = AttentionDebenture(
                contentHash = request.sticky.contentHash,
                nodeId = nodeId,
                expiresAt = Clock.System.now() + 24.hours,
                baseValue = calculateBaseValue(request.sticky),
                attentionType = request.sticky.type
            )
            
            // Add to node's debentures
            nodeDebentures.getOrPut(nodeId) { mutableListOf() }.add(debenture)
            
            // Track in request
            request.debentures.add(debenture)
        }
    }
    
    /**
     * Node claims attention work
     */
    fun claimAttention(
        nodeId: String,
        contentHash: String
    ): ClaimResult {
        val sticky = stickyNotes[contentHash] 
            ?: return ClaimResult.NotFound
        
        // Check if node has debenture
        val debenture = nodeDebentures[nodeId]?.find { 
            it.contentHash == contentHash && 
            it.status == DebentureStatus.ISSUED 
        } ?: return ClaimResult.NoDebenture
        
        // Mark as claimed
        sticky.claimedBy.add(NodeClaim(
            nodeId = nodeId,
            claimedAt = Clock.System.now(),
            debentureId = debenture.id
        ))
        
        // Activate debenture
        val updatedDebenture = debenture.copy(
            status = DebentureStatus.ACTIVE
        )
        updateDebenture(nodeId, updatedDebenture)
        
        return ClaimResult.Success(sticky, updatedDebenture)
    }
    
    /**
     * Submit solution for attention request
     */
    fun submitSolution(
        nodeId: String,
        contentHash: String,
        solution: String,
        metadata: Map<String, String> = emptyMap()
    ): SubmissionResult {
        val sticky = stickyNotes[contentHash]
            ?: return SubmissionResult.NotFound
        
        // Verify node claimed this work
        val claim = sticky.claimedBy.find { it.nodeId == nodeId }
            ?: return SubmissionResult.NotClaimed
        
        // Add solution
        val solutionEntry = Solution(
            nodeId = nodeId,
            content = solution,
            metadata = metadata,
            submittedAt = Clock.System.now(),
            quality = 0.0 // To be evaluated
        )
        
        sticky.solutions.add(solutionEntry)
        
        // Mark debenture as redeemable
        val debenture = nodeDebentures[nodeId]?.find {
            it.id == claim.debentureId
        }
        
        if (debenture != null) {
            val redeemableDebenture = debenture.copy(
                status = DebentureStatus.REDEEMABLE,
                redeemableAfter = Clock.System.now() + 10.minutes
            )
            updateDebenture(nodeId, redeemableDebenture)
        }
        
        return SubmissionResult.Success(solutionEntry)
    }
    
    /**
     * Redeem debenture for value
     */
    fun redeemDebenture(
        nodeId: String,
        debentureId: String
    ): RedemptionResult {
        val debentures = nodeDebentures[nodeId] 
            ?: return RedemptionResult.NotFound
        
        val debenture = debentures.find { it.id == debentureId }
            ?: return RedemptionResult.NotFound
        
        if (debenture.status != DebentureStatus.REDEEMABLE) {
            return RedemptionResult.NotRedeemable
        }
        
        if (debenture.redeemableAfter != null && 
            Clock.System.now() < debenture.redeemableAfter) {
            return RedemptionResult.TooEarly
        }
        
        // Calculate final value based on solution quality
        val sticky = stickyNotes[debenture.contentHash]
        val solution = sticky?.solutions?.find { it.nodeId == nodeId }
        val qualityMultiplier = solution?.quality ?: 1.0
        
        val finalValue = debenture.baseValue * 
                        debenture.multiplier * 
                        qualityMultiplier
        
        // Mark as redeemed
        val redeemedDebenture = debenture.copy(
            status = DebentureStatus.REDEEMED,
            redeemedAt = Clock.System.now()
        )
        updateDebenture(nodeId, redeemedDebenture)
        
        return RedemptionResult.Success(finalValue)
    }
    
    /**
     * Get sticky notes by type
     */
    fun getStickyByType(type: AttentionType): List<BlackboardSticky> {
        return stickyNotes.values.filter { it.type == type }
    }
    
    /**
     * Get node's active debentures
     */
    fun getNodeDebentures(nodeId: String): List<AttentionDebenture> {
        return nodeDebentures[nodeId] ?: emptyList()
    }
    
    // === Private Helpers ===
    
    private fun calculateContentHash(content: String): String {
        // Simple hash for now
        return "hash_${content.hashCode()}"
    }
    
    private fun calculateBaseValue(sticky: BlackboardSticky): Double {
        return when (sticky.type) {
            AttentionType.EMERGENCY -> 100.0
            AttentionType.SYNTHESIS -> 50.0
            AttentionType.ANALYSIS -> 30.0
            AttentionType.VERIFICATION -> 25.0
            AttentionType.TRANSLATION -> 20.0
            AttentionType.TRANSCRIPTION -> 15.0
            AttentionType.EXTRACTION -> 10.0
        } * sticky.priority
    }
    
    private fun getWaitingNodes(): List<String> {
        // Get nodes that are waiting for work
        // This would connect to the percolator coordinator
        return listOf("node_1", "node_2", "node_3") // Mock for now
    }
    
    private fun updateDebenture(nodeId: String, debenture: AttentionDebenture) {
        val debentures = nodeDebentures[nodeId] ?: return
        val index = debentures.indexOfFirst { it.id == debenture.id }
        if (index >= 0) {
            debentures[index] = debenture
        }
    }
}

// === Data Classes ===

@Serializable
data class BlackboardSticky(
    val contentHash: String,
    val content: String,
    val type: AttentionType,
    val priority: Double,
    val metadata: Map<String, String>,
    val postedAt: Instant,
    val claimedBy: MutableList<NodeClaim>,
    val solutions: MutableList<Solution>,
    val status: StickyStatus = StickyStatus.OPEN
)

@Serializable
enum class StickyStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    EXPIRED
}

@Serializable
data class NodeClaim(
    val nodeId: String,
    val claimedAt: Instant,
    val debentureId: String
)

@Serializable
data class Solution(
    val nodeId: String,
    val content: String,
    val metadata: Map<String, String>,
    val submittedAt: Instant,
    val quality: Double
)

@Serializable
data class AttentionRequest(
    val id: String,
    val sticky: BlackboardSticky,
    val debentures: MutableList<AttentionDebenture>
)

// === Result Types ===

sealed class ClaimResult {
    object NotFound : ClaimResult()
    object NoDebenture : ClaimResult()
    data class Success(
        val sticky: BlackboardSticky,
        val debenture: AttentionDebenture
    ) : ClaimResult()
}

sealed class SubmissionResult {
    object NotFound : SubmissionResult()
    object NotClaimed : SubmissionResult()
    data class Success(val solution: Solution) : SubmissionResult()
}

sealed class RedemptionResult {
    object NotFound : RedemptionResult()
    object NotRedeemable : RedemptionResult()
    object TooEarly : RedemptionResult()
    data class Success(val value: Double) : RedemptionResult()
}

// === Utility Functions ===

private fun generateDebentureId(): String {
    return "deb_${System.currentTimeMillis()}_${(0..9999).random()}"
}

private fun generateRequestId(): String {
    return "req_${System.currentTimeMillis()}_${(0..9999).random()}"
}

// === Extension Functions ===

private val Int.hours get() = kotlin.time.Duration.hours(this)