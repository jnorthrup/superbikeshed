package fiduciary.concentric

import borg.trikeshed.dht.kademlia.id.NUID
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.*

/**
 * NUID-Based Concentric Network Topology
 * 
 * Implements the concentric network where NUID proximity determines semantic scope.
 * Larger NUIDs attract broader agent scope, smaller subnets created for specific semantics.
 */

// ============================================================================
// NUID-Based Ring Definitions
// ============================================================================

@Serializable
enum class ConcentricRing(
    val level: Int,
    val groupSize: Int,
    val quorumSize: Int,
    val maxBandwidth: Long,
    val priority: StreamPriority,
    val nuidScope: Int  // Hop-based visibility scope
) {
    CORE(0, 1, 1, Long.MAX_VALUE, StreamPriority.URGENT, 1),
    DYAD(1, 2, 2, 100_000_000L, StreamPriority.HIGH, 2),
    TRIAD(2, 3, 2, 50_000_000L, StreamPriority.HIGH, 4),
    PENTAD(3, 5, 3, 25_000_000L, StreamPriority.NORMAL, 8),
    DODECAD(4, 12, 7, 10_000_000L, StreamPriority.NORMAL, 16),
    SENATE(5, 24, 13, 5_000_000L, StreamPriority.LOW, 32),
    CONGRESS(6, 100, 51, 2_000_000L, StreamPriority.BACKGROUND, 64);
    
    fun getTrustLevel(): TrustLevel {
        return when (level) {
            0 -> TrustLevel.FULL      // Smallest NUID, highest trust
            1, 2 -> TrustLevel.HIGH   // Focused semantic subnets
            3, 4 -> TrustLevel.MEDIUM // Moderate semantic scope
            5 -> TrustLevel.LOW       // Broad semantic scope
            6 -> TrustLevel.MINIMAL   // Largest NUID, widest scope
            else -> TrustLevel.MINIMAL
        }
    }
}

@Serializable
enum class TrustLevel {
    FULL,      // CORE only - smallest NUID scope
    HIGH,      // DYAD, TRIAD - focused semantic scope
    MEDIUM,    // PENTAD, DODECAD - moderate semantic scope
    LOW,       // SENATE - broad semantic scope
    MINIMAL    // CONGRESS - widest NUID scope
}

@Serializable
enum class StreamPriority {
    URGENT, HIGH, NORMAL, LOW, BACKGROUND
}

// ============================================================================
// NUID-Based Agent
// ============================================================================

@Serializable
data class NUIDAgent(
    val id: NUID,
    val ring: ConcentricRing,
    val capabilities: Set<AgentCapability>,
    val semanticTags: Set<String>,
    val endpoint: String? = null,
    val lastSeen: Instant = Clock.System.now(),
    val workload: Double = 0.0
) {
    fun canSee(other: NUIDAgent): Boolean {
        val distance = calculateNUIDDistance(id, other.id)
        return distance <= ring.nuidScope
    }
    
    fun getSemanticScope(): Int = ring.nuidScope
}

@Serializable
enum class AgentCapability {
    CONSENSUS_BUILDING,
    SECURITY_ENFORCEMENT,
    QUORUM_FORMATION,
    TASK_COORDINATION,
    NLP_PROCESSING,
    ENTITY_EXTRACTION,
    CONTENT_INGESTION,
    TRANSFORMATION,
    STORAGE_MANAGEMENT,
    ARCHIVE_PROCESSING,
    RETRIEVAL_OPTIMIZATION,
    INDEXING_OPERATIONS,
    DISTRIBUTION_COORDINATION,
    REPLICATION_MANAGEMENT,
    SCALING_OPERATIONS,
    MASS_DISTRIBUTION,
    GLOBAL_SYNC,
    BULK_OPERATIONS
}

// ============================================================================
// NUID-Based Topology Manager
// ============================================================================

class NUIDConcentricTopology {
    private val agents = mutableMapOf<NUID, NUIDAgent>()
    private val semanticSubnets = mutableMapOf<String, MutableSet<NUID>>()
    private val ringAgents = ConcentricRing.values().associateWith { mutableSetOf<NUID>() }
    
    private val _agentEvents = MutableSharedFlow<AgentEvent>()
    val agentEvents: SharedFlow<AgentEvent> = _agentEvents.asSharedFlow()
    
    // ========================================================================
    // NUID Distance Calculations
    // ========================================================================
    
    fun calculateNUIDDistance(nuid1: NUID, nuid2: NUID): Int {
        val distanceNUID = nuid1.distanceTo(nuid2)
        return distanceNUID.bytes.play.sumOf { it.toInt() and 0xFF }
    }
    
    fun getVisibleAgents(agentNUID: NUID, scope: Int): List<NUIDAgent> {
        val agent = agents[agentNUID] ?: return emptyList()
        return agents.values.filter { otherAgent ->
            calculateNUIDDistance(agentNUID, otherAgent.id) <= scope
        }
    }
    
    fun getAgentsInSemanticScope(agentNUID: NUID): List<NUIDAgent> {
        val agent = agents[agentNUID] ?: return emptyList()
        return getVisibleAgents(agentNUID, agent.getSemanticScope())
    }
    
    // ========================================================================
    // Semantic Subnet Management
    // ========================================================================
    
    fun createSemanticSubnet(semanticTag: String, targetAgents: List<NUID>): NUID {
        // Create a focused NUID for this semantic subnet
        val subnetNUID = NUID.fromSemantic(semanticTag)
        
        // Register agents in this semantic subnet
        semanticSubnets.getOrPut(semanticTag) { mutableSetOf() }.addAll(targetAgents)
        
        // Notify agents about the new semantic subnet
        targetAgents.forEach { agentId ->
            agents[agentId]?.let { agent ->
                _agentEvents.tryEmit(AgentEvent.SemanticSubnetCreated(semanticTag, subnetNUID, agent))
            }
        }
        
        return subnetNUID
    }
    
    fun findAgentsBySemantic(semanticTag: String): List<NUIDAgent> {
        val agentIds = semanticSubnets[semanticTag] ?: return emptyList()
        return agentIds.mapNotNull { agents[it] }
    }
    
    // ========================================================================
    // Agent Management
    // ========================================================================
    
    suspend fun registerAgent(agent: NUIDAgent) {
        agents[agent.id] = agent
        ringAgents[agent.ring]?.add(agent.id)
        
        // Add agent to semantic subnets based on tags
        agent.semanticTags.forEach { tag ->
            semanticSubnets.getOrPut(tag) { mutableSetOf() }.add(agent.id)
        }
        
        _agentEvents.emit(AgentEvent.AgentRegistered(agent))
    }
    
    suspend fun unregisterAgent(agentId: NUID) {
        val agent = agents.remove(agentId) ?: return
        ringAgents[agent.ring]?.remove(agentId)
        
        // Remove from semantic subnets
        agent.semanticTags.forEach { tag ->
            semanticSubnets[tag]?.remove(agentId)
        }
        
        _agentEvents.emit(AgentEvent.AgentUnregistered(agent))
    }
    
    fun getAgentsByRing(ring: ConcentricRing): List<NUIDAgent> {
        val agentIds = ringAgents[ring] ?: return emptyList()
        return agentIds.mapNotNull { agents[it] }
    }
    
    fun getAgentsByCapability(capability: AgentCapability): List<NUIDAgent> {
        return agents.values.filter { capability in it.capabilities }
    }
    
    // ========================================================================
    // Ring-Based Operations
    // ========================================================================
    
    fun findOptimalRing(task: ConcentricTask): ConcentricRing {
        return when {
            task.priority == TaskPriority.CRITICAL -> ConcentricRing.CORE
            task.complexity <= ComplexityLevel.SIMPLE -> ConcentricRing.DYAD
            task.complexity <= ComplexityLevel.MODERATE -> ConcentricRing.TRIAD
            task.complexity <= ComplexityLevel.COMPLEX -> ConcentricRing.PENTAD
            task.complexity <= ComplexityLevel.DISTRIBUTED -> ConcentricRing.DODECAD
            task.complexity <= ComplexityLevel.MASSIVE -> ConcentricRing.SENATE
            else -> ConcentricRing.CONGRESS
        }
    }
    
    suspend fun routeTaskToRing(task: ConcentricTask, ring: ConcentricRing): List<NUIDAgent> {
        val ringAgents = getAgentsByRing(ring)
        val capableAgents = ringAgents.filter { agent ->
            task.requiredCapabilities.all { it in agent.capabilities }
        }
        
        // Select agents based on workload and NUID proximity
        return selectOptimalAgents(capableAgents, task.requiredAgents)
    }
    
    private fun selectOptimalAgents(candidates: List<NUIDAgent>, count: Int): List<NUIDAgent> {
        return candidates
            .sortedBy { it.workload }  // Prefer less loaded agents
            .take(count)
    }
    
    // ========================================================================
    // Topology Statistics
    // ========================================================================
    
    fun getTopologyStats(): TopologyStats {
        val totalAgents = agents.size
        val ringStats = ConcentricRing.values().associateWith { ring ->
            RingStats(
                ring = ring,
                agentCount = getAgentsByRing(ring).size,
                avgWorkload = getAgentsByRing(ring).map { it.workload }.average(),
                semanticSubnetCount = semanticSubnets.count { (_, agentIds) ->
                    agentIds.any { agentId -> agents[agentId]?.ring == ring }
                }
            )
        }
        
        return TopologyStats(
            totalAgents = totalAgents,
            ringStats = ringStats,
            semanticSubnetCount = semanticSubnets.size,
            avgNUIDDistance = calculateAverageNUIDDistance()
        )
    }
    
    private fun calculateAverageNUIDDistance(): Double {
        if (agents.size < 2) return 0.0
        
        val distances = mutableListOf<Int>()
        val agentList = agents.values.toList()
        
        for (i in 0 until agentList.size - 1) {
            for (j in i + 1 until agentList.size) {
                val distance = calculateNUIDDistance(agentList[i].id, agentList[j].id)
                distances.add(distance)
            }
        }
        
        return distances.average()
    }
}

// ============================================================================
// Supporting Data Classes
// ============================================================================

@Serializable
data class ConcentricTask(
    val id: String,
    val type: TaskType,
    val requiredCapabilities: Set<AgentCapability>,
    val priority: TaskPriority,
    val complexity: ComplexityLevel,
    val requiredAgents: Int = 1,
    val semanticTags: Set<String> = emptySet()
)

@Serializable
enum class TaskType {
    CONSENSUS, VALIDATION, ANALYSIS, INGESTION, DISTRIBUTION, SYNC
}

@Serializable
enum class TaskPriority { CRITICAL, HIGH, NORMAL, LOW, BACKGROUND }

@Serializable
enum class ComplexityLevel { SIMPLE, MODERATE, COMPLEX, DISTRIBUTED, MASSIVE }

@Serializable
sealed class AgentEvent {
    data class AgentRegistered(val agent: NUIDAgent) : AgentEvent()
    data class AgentUnregistered(val agent: NUIDAgent) : AgentEvent()
    data class SemanticSubnetCreated(val semanticTag: String, val subnetNUID: NUID, val agent: NUIDAgent) : AgentEvent()
}

@Serializable
data class TopologyStats(
    val totalAgents: Int,
    val ringStats: Map<ConcentricRing, RingStats>,
    val semanticSubnetCount: Int,
    val avgNUIDDistance: Double
)

@Serializable
data class RingStats(
    val ring: ConcentricRing,
    val agentCount: Int,
    val avgWorkload: Double,
    val semanticSubnetCount: Int
)

// ============================================================================
// NUID Extensions
// ============================================================================

fun NUID.fromSemantic(semantic: String): NUID {
    // Create a deterministic NUID from semantic string
    val hash = semantic.hashCode().toByteArray()
    val paddedHash = ByteArray(32) { if (it < hash.size) hash[it] else 0 }
    return NUID.fromBytes(paddedHash)
} 