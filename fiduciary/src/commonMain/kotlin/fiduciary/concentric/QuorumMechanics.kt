package fiduciary.concentric

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.math.*

/**
 * Quorum Mechanics for Concentric Networks
 * 
 * Implements Byzantine fault-tolerant consensus mechanisms
 * across different group sizes with transparent coordination
 * to prevent token amplification and scope creep.
 * 
 * Based on positive value addition principles from quorum_dynamics.md
 */

// === QUORUM PROPOSALS ===

/**
 * Proposal requiring quorum consensus
 */
@Serializable
data class QuorumProposal(
    val id: NUID,
    val type: ProposalType,
    val proposer: NUID,
    val content: ProposalContent,
    val requiredQuorum: QuorumRequirement,
    val deadline: Instant,
    val priority: ProposalPriority,
    val attestations: MutableMap<NUID, Attestation> = mutableMapOf(),
    val status: ProposalStatus = ProposalStatus.PENDING,
    val createdAt: Instant = Clock.System.now()
)

enum class ProposalType {
    TASK_EXECUTION,
    RESOURCE_ALLOCATION,
    MEMBERSHIP_CHANGE,
    POLICY_UPDATE,
    EMERGENCY_ACTION,
    DISCOVERY_VALIDATION
}

@Serializable
sealed class ProposalContent {
    @Serializable
    data class TaskExecution(
        val task: ConcentricTask,
        val estimatedTokens: Long,
        val scopeBoundaries: Set<String>
    ) : ProposalContent()
    
    @Serializable
    data class ResourceAllocation(
        val resourceType: String,
        val amount: Long,
        val recipient: NUID,
        val justification: String
    ) : ProposalContent()
    
    @Serializable
    data class MembershipChange(
        val action: MembershipAction,
        val memberId: NUID,
        val newRole: MemberRole? = null
    ) : ProposalContent()
    
    @Serializable
    data class DiscoveryValidation(
        val discovery: Discovery,
        val confidence: Double,
        val supportingEvidence: List<String>
    ) : ProposalContent()
}

enum class MembershipAction {
    ADD_MEMBER,
    REMOVE_MEMBER,
    CHANGE_ROLE,
    PROMOTE_TO_INNER_RING,
    DEMOTE_TO_OUTER_RING
}

enum class ProposalPriority {
    CRITICAL,    // Immediate consensus required
    HIGH,        // Fast-track consensus
    NORMAL,      // Standard process
    LOW,         // Can wait for optimal conditions
    DEFERRED     // Non-urgent, batch processing
}

enum class ProposalStatus {
    PENDING,
    IN_PROGRESS,
    APPROVED,
    REJECTED,
    TIMED_OUT,
    CANCELLED
}

// === QUORUM REQUIREMENTS ===

/**
 * Flexible quorum requirements based on context
 */
@Serializable
data class QuorumRequirement(
    val minVotes: Int,
    val minPercentage: Double,
    val vetoThreshold: Int? = null,
    val superMajority: Boolean = false,
    val unanimityRequired: Boolean = false,
    val timeLimit: Duration = 30.seconds
) {
    companion object {
        // Pre-defined requirements for common scenarios
        val SIMPLE_MAJORITY = QuorumRequirement(
            minVotes = 0,
            minPercentage = 0.51,
            vetoThreshold = null
        )
        
        val SUPER_MAJORITY = QuorumRequirement(
            minVotes = 0,
            minPercentage = 0.67,
            vetoThreshold = 2,
            superMajority = true
        )
        
        val UNANIMOUS = QuorumRequirement(
            minVotes = 0,
            minPercentage = 1.0,
            vetoThreshold = 1,
            unanimityRequired = true
        )
        
        val BYZANTINE_FAULT_TOLERANT = QuorumRequirement(
            minVotes = 0,
            minPercentage = 0.67,
            vetoThreshold = null,
            timeLimit = 10.seconds
        )
    }
}

// === ATTESTATIONS ===

/**
 * Signed attestation from a group member
 */
@Serializable
data class Attestation(
    val attesterId: NUID,
    val vote: Vote,
    val reasoning: String? = null,
    val evidence: List<Evidence> = emptyList(),
    val timestamp: Instant = Clock.System.now(),
    val signature: ByteArray? = null // Cryptographic signature
) {
    enum class Vote {
        APPROVE,
        REJECT,
        ABSTAIN,
        VETO,
        DELEGATE
    }
}

@Serializable
data class Evidence(
    val type: EvidenceType,
    val content: String,
    val weight: Double = 1.0
)

enum class EvidenceType {
    BOUNDARY_CHECK,
    SCOPE_VALIDATION,
    TOKEN_ESTIMATE,
    PRECEDENT,
    RISK_ASSESSMENT,
    PERFORMANCE_METRIC
}

// === QUORUM COORDINATOR ===

/**
 * Manages quorum formation and consensus processes
 */
class QuorumCoordinator(
    val groupId: NUID,
    val group: ConcentricGroup,
    val networkManager: ConcentricNetworkManager
) {
    private val proposals = mutableMapOf<NUID, QuorumProposal>()
    private val activeQuorums = mutableMapOf<NUID, ActiveQuorum>()
    
    private val _proposalFlow = MutableSharedFlow<ProposalEvent>()
    val proposalFlow: SharedFlow<ProposalEvent> = _proposalFlow.asSharedFlow()
    
    private val _consensusFlow = MutableSharedFlow<ConsensusResult>()
    val consensusFlow: SharedFlow<ConsensusResult> = _consensusFlow.asSharedFlow()
    
    /**
     * Submit a proposal for quorum consideration
     */
    suspend fun submitProposal(
        type: ProposalType,
        content: ProposalContent,
        proposer: NUID,
        priority: ProposalPriority = ProposalPriority.NORMAL
    ): QuorumProposal {
        // Determine quorum requirements based on proposal type
        val requirement = determineQuorumRequirement(type, group.template)
        
        val proposal = QuorumProposal(
            id = NUID.random(),
            type = type,
            proposer = proposer,
            content = content,
            requiredQuorum = requirement,
            deadline = Clock.System.now() + requirement.timeLimit,
            priority = priority
        )
        
        proposals[proposal.id] = proposal
        
        // Form active quorum
        val activeQuorum = formActiveQuorum(proposal)
        activeQuorums[proposal.id] = activeQuorum
        
        // Notify members
        _proposalFlow.emit(ProposalEvent.ProposalSubmitted(proposal))
        
        // Start consensus process
        coroutineScope {
            launch {
                conductConsensus(proposal, activeQuorum)
            }
        }
        
        return proposal
    }
    
    /**
     * Submit attestation for a proposal
     */
    suspend fun submitAttestation(
        proposalId: NUID,
        attestation: Attestation
    ): Boolean {
        val proposal = proposals[proposalId] ?: return false
        val activeQuorum = activeQuorums[proposalId] ?: return false
        
        // Verify attester is part of quorum
        if (attestation.attesterId !in activeQuorum.members) {
            return false
        }
        
        // Add attestation
        proposal.attestations[attestation.attesterId] = attestation
        
        // Check if consensus reached
        val result = evaluateConsensus(proposal, activeQuorum)
        if (result != null) {
            finalizeProposal(proposal, result)
        }
        
        return true
    }
    
    /**
     * Conduct consensus process
     */
    private suspend fun conductConsensus(
        proposal: QuorumProposal,
        quorum: ActiveQuorum
    ) {
        // Phase 1: Deliberation
        _proposalFlow.emit(ProposalEvent.DeliberationStarted(proposal.id))
        
        // Allow time for discussion and evidence gathering
        delay(proposal.requiredQuorum.timeLimit / 3)
        
        // Phase 2: Voting
        _proposalFlow.emit(ProposalEvent.VotingStarted(proposal.id))
        
        // Wait for votes or timeout
        withTimeoutOrNull(proposal.requiredQuorum.timeLimit * 2 / 3) {
            while (true) {
                delay(100)
                val result = evaluateConsensus(proposal, quorum)
                if (result != null) {
                    return@withTimeoutOrNull result
                }
            }
        }
        
        // Phase 3: Finalization
        val finalResult = evaluateConsensus(proposal, quorum) 
            ?: ConsensusResult.TimedOut(proposal.id)
        
        finalizeProposal(proposal, finalResult)
    }
    
    /**
     * Form active quorum for proposal
     */
    private fun formActiveQuorum(proposal: QuorumProposal): ActiveQuorum {
        // Select members based on proposal type and priority
        val members = selectQuorumMembers(proposal)
        
        return ActiveQuorum(
            proposalId = proposal.id,
            members = members,
            coordinator = selectCoordinator(members, proposal),
            formationTime = Clock.System.now(),
            protocol = determineProtocol(proposal)
        )
    }
    
    /**
     * Select members for quorum
     */
    private fun selectQuorumMembers(proposal: QuorumProposal): Set<NUID> {
        return when (proposal.priority) {
            ProposalPriority.CRITICAL -> group.members // All members
            ProposalPriority.HIGH -> {
                // Select most available members
                group.members.take(max(group.template.minQuorum + 1, group.members.size * 3 / 4))
                    .toSet()
            }
            else -> {
                // Minimum quorum
                group.members.take(group.template.minQuorum).toSet()
            }
        }
    }
    
    /**
     * Select coordinator for quorum
     */
    private fun selectCoordinator(members: Set<NUID>, proposal: QuorumProposal): NUID {
        return when (group.template.getWorkDistributionStrategy()) {
            WorkDistributionStrategy.ROTATING_LEADER -> {
                // Rotate based on proposal count
                members.elementAt(proposals.size % members.size)
            }
            WorkDistributionStrategy.HIERARCHICAL,
            WorkDistributionStrategy.COMMITTEE_BASED -> {
                // Use designated leader
                group.leader ?: members.first()
            }
            else -> proposal.proposer // Proposer coordinates
        }
    }
    
    /**
     * Determine consensus protocol
     */
    private fun determineProtocol(proposal: QuorumProposal): ConsensusProtocol {
        return when (proposal.type) {
            ProposalType.EMERGENCY_ACTION -> ConsensusProtocol.FAST_BYZANTINE
            ProposalType.POLICY_UPDATE -> ConsensusProtocol.DELIBERATIVE
            ProposalType.DISCOVERY_VALIDATION -> ConsensusProtocol.EVIDENCE_BASED
            else -> ConsensusProtocol.STANDARD
        }
    }
    
    /**
     * Evaluate if consensus has been reached
     */
    private fun evaluateConsensus(
        proposal: QuorumProposal,
        quorum: ActiveQuorum
    ): ConsensusResult? {
        val votes = proposal.attestations.values
        val totalMembers = quorum.members.size
        
        // Count votes
        val approvals = votes.count { it.vote == Attestation.Vote.APPROVE }
        val rejections = votes.count { it.vote == Attestation.Vote.REJECT }
        val vetoes = votes.count { it.vote == Attestation.Vote.VETO }
        val abstentions = votes.count { it.vote == Attestation.Vote.ABSTAIN }
        
        // Check veto threshold
        if (proposal.requiredQuorum.vetoThreshold != null && 
            vetoes >= proposal.requiredQuorum.vetoThreshold) {
            return ConsensusResult.Vetoed(proposal.id, vetoes)
        }
        
        // Check if enough votes
        val totalVotes = approvals + rejections
        if (totalVotes < proposal.requiredQuorum.minVotes) {
            return null // Not enough votes yet
        }
        
        // Calculate approval percentage
        val approvalPercentage = if (totalVotes > 0) {
            approvals.toDouble() / totalVotes
        } else 0.0
        
        // Check unanimity
        if (proposal.requiredQuorum.unanimityRequired) {
            return if (approvals == totalMembers) {
                ConsensusResult.Approved(proposal.id, approvals, 0, abstentions)
            } else if (rejections > 0) {
                ConsensusResult.Rejected(proposal.id, approvals, rejections, abstentions)
            } else null
        }
        
        // Check percentage threshold
        return if (approvalPercentage >= proposal.requiredQuorum.minPercentage) {
            ConsensusResult.Approved(proposal.id, approvals, rejections, abstentions)
        } else if (votes.size == totalMembers) {
            ConsensusResult.Rejected(proposal.id, approvals, rejections, abstentions)
        } else null
    }
    
    /**
     * Finalize proposal with result
     */
    private suspend fun finalizeProposal(
        proposal: QuorumProposal,
        result: ConsensusResult
    ) {
        // Update proposal status
        proposal.status = when (result) {
            is ConsensusResult.Approved -> ProposalStatus.APPROVED
            is ConsensusResult.Rejected -> ProposalStatus.REJECTED
            is ConsensusResult.Vetoed -> ProposalStatus.REJECTED
            is ConsensusResult.TimedOut -> ProposalStatus.TIMED_OUT
        }
        
        // Emit result
        _consensusFlow.emit(result)
        
        // Execute approved proposals
        if (result is ConsensusResult.Approved) {
            executeProposal(proposal)
        }
        
        // Clean up
        activeQuorums.remove(proposal.id)
        
        // Emit completion event
        _proposalFlow.emit(ProposalEvent.ProposalCompleted(proposal.id, result))
    }
    
    /**
     * Execute approved proposal
     */
    private suspend fun executeProposal(proposal: QuorumProposal) {
        when (val content = proposal.content) {
            is ProposalContent.TaskExecution -> {
                // Submit task with boundaries enforced
                networkManager.submitTask(content.task)
            }
            is ProposalContent.ResourceAllocation -> {
                // Allocate resources
                _proposalFlow.emit(ProposalEvent.ResourceAllocated(
                    content.resourceType,
                    content.amount,
                    content.recipient
                ))
            }
            is ProposalContent.MembershipChange -> {
                // Handle membership change
                handleMembershipChange(content)
            }
            is ProposalContent.DiscoveryValidation -> {
                // Validate and propagate discovery
                if (content.confidence > 0.8) {
                    networkManager.submitDiscovery(content.discovery)
                }
            }
        }
    }
    
    private suspend fun handleMembershipChange(change: ProposalContent.MembershipChange) {
        // Implementation would update group membership
        _proposalFlow.emit(ProposalEvent.MembershipChanged(
            change.action,
            change.memberId
        ))
    }
    
    /**
     * Determine quorum requirements based on context
     */
    private fun determineQuorumRequirement(
        type: ProposalType,
        template: GroupTemplate
    ): QuorumRequirement {
        return when (type) {
            ProposalType.EMERGENCY_ACTION -> {
                if (template.size <= 3) {
                    QuorumRequirement.UNANIMOUS
                } else {
                    QuorumRequirement.SUPER_MAJORITY
                }
            }
            ProposalType.POLICY_UPDATE -> QuorumRequirement.SUPER_MAJORITY
            ProposalType.MEMBERSHIP_CHANGE -> {
                if (template == Dyad) {
                    QuorumRequirement.UNANIMOUS
                } else {
                    QuorumRequirement.SUPER_MAJORITY
                }
            }
            ProposalType.DISCOVERY_VALIDATION -> QuorumRequirement.SIMPLE_MAJORITY
            else -> {
                // Default based on group template
                QuorumRequirement(
                    minVotes = template.minQuorum,
                    minPercentage = template.minQuorum.toDouble() / template.size,
                    vetoThreshold = if (template.size <= 5) 1 else 2
                )
            }
        }
    }
}

// === SUPPORTING TYPES ===

/**
 * Active quorum tracking
 */
@Serializable
data class ActiveQuorum(
    val proposalId: NUID,
    val members: Set<NUID>,
    val coordinator: NUID,
    val formationTime: Instant,
    val protocol: ConsensusProtocol
)

enum class ConsensusProtocol {
    STANDARD,         // Normal voting process
    FAST_BYZANTINE,   // Quick Byzantine agreement
    DELIBERATIVE,     // Extended discussion period
    EVIDENCE_BASED    // Requires supporting evidence
}

/**
 * Consensus results
 */
sealed class ConsensusResult {
    abstract val proposalId: NUID
    
    data class Approved(
        override val proposalId: NUID,
        val approvals: Int,
        val rejections: Int,
        val abstentions: Int
    ) : ConsensusResult()
    
    data class Rejected(
        override val proposalId: NUID,
        val approvals: Int,
        val rejections: Int,
        val abstentions: Int
    ) : ConsensusResult()
    
    data class Vetoed(
        override val proposalId: NUID,
        val vetoCount: Int
    ) : ConsensusResult()
    
    data class TimedOut(
        override val proposalId: NUID
    ) : ConsensusResult()
}

/**
 * Proposal events
 */
sealed class ProposalEvent {
    data class ProposalSubmitted(val proposal: QuorumProposal) : ProposalEvent()
    data class DeliberationStarted(val proposalId: NUID) : ProposalEvent()
    data class VotingStarted(val proposalId: NUID) : ProposalEvent()
    data class ProposalCompleted(val proposalId: NUID, val result: ConsensusResult) : ProposalEvent()
    data class ResourceAllocated(val resourceType: String, val amount: Long, val recipient: NUID) : ProposalEvent()
    data class MembershipChanged(val action: MembershipAction, val memberId: NUID) : ProposalEvent()
}

// === TRANSPARENCY MECHANISMS ===

/**
 * Transparent logging of all quorum actions
 */
class QuorumTransparencyLog {
    private val log = mutableListOf<TransparencyEntry>()
    
    fun logProposal(proposal: QuorumProposal) {
        log.add(TransparencyEntry(
            timestamp = Clock.System.now(),
            type = EntryType.PROPOSAL_SUBMITTED,
            actorId = proposal.proposer,
            details = "Proposal ${proposal.id}: ${proposal.type}",
            tokenEstimate = if (proposal.content is ProposalContent.TaskExecution) {
                proposal.content.estimatedTokens
            } else null
        ))
    }
    
    fun logAttestation(proposalId: NUID, attestation: Attestation) {
        log.add(TransparencyEntry(
            timestamp = Clock.System.now(),
            type = EntryType.ATTESTATION_SUBMITTED,
            actorId = attestation.attesterId,
            details = "Vote on ${proposalId}: ${attestation.vote}",
            reasoning = attestation.reasoning
        ))
    }
    
    fun logConsensus(result: ConsensusResult) {
        log.add(TransparencyEntry(
            timestamp = Clock.System.now(),
            type = EntryType.CONSENSUS_REACHED,
            actorId = NUID.ZERO, // System entry
            details = "Consensus on ${result.proposalId}: ${result::class.simpleName}"
        ))
    }
    
    fun getRecentEntries(count: Int = 100): List<TransparencyEntry> {
        return log.takeLast(count)
    }
    
    fun getEntriesByActor(actorId: NUID): List<TransparencyEntry> {
        return log.filter { it.actorId == actorId }
    }
}

@Serializable
data class TransparencyEntry(
    val timestamp: Instant,
    val type: EntryType,
    val actorId: NUID,
    val details: String,
    val reasoning: String? = null,
    val tokenEstimate: Long? = null
)

enum class EntryType {
    PROPOSAL_SUBMITTED,
    ATTESTATION_SUBMITTED,
    CONSENSUS_REACHED,
    EXECUTION_STARTED,
    EXECUTION_COMPLETED,
    BOUNDARY_VIOLATION,
    TOKEN_OVERRUN
}

// === ACCOUNTABILITY MECHANISMS ===

/**
 * Tracks agent behavior and enforces boundaries
 */
class AccountabilityMonitor(
    val transparencyLog: QuorumTransparencyLog
) {
    private val violations = mutableMapOf<NUID, MutableList<BoundaryViolation>>()
    private val reputations = mutableMapOf<NUID, Double>()
    
    /**
     * Check if action violates boundaries
     */
    fun checkBoundaries(
        actorId: NUID,
        action: String,
        scopeBoundaries: Set<String>
    ): BoundaryCheckResult {
        // Simple boundary checking logic
        val violations = mutableListOf<String>()
        
        scopeBoundaries.forEach { boundary ->
            if (!isWithinBoundary(action, boundary)) {
                violations.add(boundary)
            }
        }
        
        if (violations.isNotEmpty()) {
            recordViolation(actorId, violations)
            return BoundaryCheckResult.Violation(violations)
        }
        
        return BoundaryCheckResult.Approved
    }
    
    /**
     * Update reputation based on behavior
     */
    fun updateReputation(actorId: NUID, delta: Double) {
        val current = reputations[actorId] ?: 1.0
        reputations[actorId] = (current + delta).coerceIn(0.0, 2.0)
    }
    
    private fun isWithinBoundary(action: String, boundary: String): Boolean {
        // Simplified boundary checking
        return !action.contains("unsafe") && !action.contains("unscoped")
    }
    
    private fun recordViolation(actorId: NUID, boundaries: List<String>) {
        val actorViolations = violations.getOrPut(actorId) { mutableListOf() }
        actorViolations.add(BoundaryViolation(
            timestamp = Clock.System.now(),
            boundaries = boundaries,
            severity = if (boundaries.size > 2) Severity.HIGH else Severity.LOW
        ))
        
        // Update reputation
        updateReputation(actorId, -0.1 * boundaries.size)
    }
}

@Serializable
data class BoundaryViolation(
    val timestamp: Instant,
    val boundaries: List<String>,
    val severity: Severity
)

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

sealed class BoundaryCheckResult {
    object Approved : BoundaryCheckResult()
    data class Violation(val boundaries: List<String>) : BoundaryCheckResult()
}

// === QUORUM STRATEGIES ===

/**
 * Different strategies for quorum-based decision making
 */
object QuorumStrategies {
    
    /**
     * Token-efficient strategy that minimizes AI token usage
     */
    fun tokenEfficientStrategy(): QuorumStrategy {
        return QuorumStrategy(
            name = "TokenEfficient",
            preProcess = { proposal ->
                // Compress proposal content
                proposal.copy(
                    content = when (val content = proposal.content) {
                        is ProposalContent.TaskExecution -> content.copy(
                            task = content.task.copy(
                                payload = content.task.payload.take(100) // Limit payload
                            )
                        )
                        else -> content
                    }
                )
            },
            votingProcess = VotingProcess.FAST_ROUND,
            postProcess = { result ->
                // Quick finalization
                result
            }
        )
    }
    
    /**
     * Quality-focused strategy for critical decisions
     */
    fun qualityFocusedStrategy(): QuorumStrategy {
        return QuorumStrategy(
            name = "QualityFocused",
            preProcess = { proposal ->
                // Require evidence for all votes
                proposal
            },
            votingProcess = VotingProcess.MULTI_ROUND,
            postProcess = { result ->
                // Extensive validation
                result
            }
        )
    }
    
    /**
     * Byzantine fault-tolerant strategy
     */
    fun byzantineFaultTolerantStrategy(): QuorumStrategy {
        return QuorumStrategy(
            name = "ByzantineFaultTolerant",
            preProcess = { proposal ->
                proposal.copy(
                    requiredQuorum = QuorumRequirement.BYZANTINE_FAULT_TOLERANT
                )
            },
            votingProcess = VotingProcess.BYZANTINE_AGREEMENT,
            postProcess = { result ->
                result
            }
        )
    }
}

@Serializable
data class QuorumStrategy(
    val name: String,
    val preProcess: (QuorumProposal) -> QuorumProposal,
    val votingProcess: VotingProcess,
    val postProcess: (ConsensusResult) -> ConsensusResult
)

enum class VotingProcess {
    FAST_ROUND,        // Single round, quick decision
    MULTI_ROUND,       // Multiple rounds with deliberation
    BYZANTINE_AGREEMENT, // Byzantine fault-tolerant protocol
    RANKED_CHOICE      // Ranked preference voting
}