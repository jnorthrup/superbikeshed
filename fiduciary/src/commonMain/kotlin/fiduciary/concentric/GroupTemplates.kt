package fiduciary.concentric

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.math.*

/**
 * Group Templates for Concentric Networks
 * 
 * Defines specific group sizes (2, 3, 5, 24, etc.) with their
 * collaboration mechanics, quorum rules, and work distribution patterns.
 * 
 * Each group size has specific properties optimized for different
 * types of collaborative work in the Patrick Devine benchmark system.
 */

// === GROUP TEMPLATE DEFINITIONS ===

/**
 * Base template for all group configurations
 */
@Serializable
sealed class GroupTemplate {
    abstract val size: Int
    abstract val name: String
    abstract val minQuorum: Int
    abstract val description: String
    abstract val optimalTaskTypes: Set<TaskType>
    
    /**
     * Calculate if group has quorum based on active members
     */
    fun hasQuorum(activeMembers: Int): Boolean = activeMembers >= minQuorum
    
    /**
     * Get work distribution strategy for this group size
     */
    abstract fun getWorkDistributionStrategy(): WorkDistributionStrategy
}

// === SPECIFIC GROUP TEMPLATES ===

/**
 * Dyad - 2 members
 * Perfect for validation, cross-checking, and peer review
 */
@Serializable
data object Dyad : GroupTemplate() {
    override val size = 2
    override val name = "Dyad"
    override val minQuorum = 2 // Unanimous
    override val description = "Two-member group for validation and peer review"
    override val optimalTaskTypes = setOf(
        TaskType.VALIDATION,
        TaskType.CROSS_REFERENCE
    )
    
    override fun getWorkDistributionStrategy() = WorkDistributionStrategy.PEER_VALIDATION
}

/**
 * Triad - 3 members
 * Ideal for tie-breaking, analysis tasks, and Byzantine fault tolerance
 */
@Serializable
data object Triad : GroupTemplate() {
    override val size = 3
    override val name = "Triad"
    override val minQuorum = 2 // Simple majority
    override val description = "Three-member group with Byzantine fault tolerance"
    override val optimalTaskTypes = setOf(
        TaskType.NLP_ANALYSIS,
        TaskType.TOPIC_EXTRACTION,
        TaskType.ANOMALY_DETECTION
    )
    
    override fun getWorkDistributionStrategy() = WorkDistributionStrategy.ROTATING_LEADER
}

/**
 * Pentad - 5 members
 * Good for complex analysis with higher fault tolerance
 */
@Serializable
data object Pentad : GroupTemplate() {
    override val size = 5
    override val name = "Pentad"
    override val minQuorum = 3 // Simple majority
    override val description = "Five-member group for complex collaborative tasks"
    override val optimalTaskTypes = setOf(
        TaskType.CONTENT_INGESTION,
        TaskType.TRANSCRIPTION,
        TaskType.AGGREGATION
    )
    
    override fun getWorkDistributionStrategy() = WorkDistributionStrategy.LOAD_BALANCED
}

/**
 * Octad - 8 members
 * Efficient for parallel processing with sub-group formation
 */
@Serializable
data object Octad : GroupTemplate() {
    override val size = 8
    override val name = "Octad"
    override val minQuorum = 5 // >50%
    override val description = "Eight-member group allowing 2x2x2 or 4x2 sub-grouping"
    override val optimalTaskTypes = setOf(
        TaskType.CONTENT_INGESTION,
        TaskType.NLP_ANALYSIS
    )
    
    override fun getWorkDistributionStrategy() = WorkDistributionStrategy.HIERARCHICAL
}

/**
 * Dodecad - 12 members
 * Allows for sophisticated sub-grouping (3x4, 2x6, etc.)
 */
@Serializable
data object Dodecad : GroupTemplate() {
    override val size = 12
    override val name = "Dodecad"
    override val minQuorum = 7 // >50%
    override val description = "Twelve-member group with flexible sub-grouping"
    override val optimalTaskTypes = setOf(
        TaskType.CONSENSUS,
        TaskType.AGGREGATION
    )
    
    override fun getWorkDistributionStrategy() = WorkDistributionStrategy.SUBGROUP_BASED
}

/**
 * Senate - 24 members
 * Large deliberative body for major decisions and consensus
 */
@Serializable
data object Senate : GroupTemplate() {
    override val size = 24
    override val name = "Senate"
    override val minQuorum = 13 // >50%
    override val description = "Twenty-four member deliberative body"
    override val optimalTaskTypes = setOf(
        TaskType.CONSENSUS,
        TaskType.VALIDATION
    )
    
    override fun getWorkDistributionStrategy() = WorkDistributionStrategy.COMMITTEE_BASED
}

/**
 * Custom group template for arbitrary sizes
 */
@Serializable
data class CustomGroup(
    override val size: Int,
    override val name: String,
    override val minQuorum: Int,
    override val description: String,
    override val optimalTaskTypes: Set<TaskType> = emptySet()
) : GroupTemplate() {
    init {
        require(size > 0) { "Group size must be positive" }
        require(minQuorum > 0 && minQuorum <= size) { 
            "Quorum must be between 1 and group size" 
        }
    }
    
    override fun getWorkDistributionStrategy(): WorkDistributionStrategy {
        return when {
            size <= 3 -> WorkDistributionStrategy.PEER_VALIDATION
            size <= 8 -> WorkDistributionStrategy.LOAD_BALANCED
            size <= 16 -> WorkDistributionStrategy.HIERARCHICAL
            else -> WorkDistributionStrategy.COMMITTEE_BASED
        }
    }
}

// === WORK DISTRIBUTION STRATEGIES ===

enum class WorkDistributionStrategy {
    PEER_VALIDATION,    // Each member validates others' work
    ROTATING_LEADER,    // Leadership rotates among members
    LOAD_BALANCED,      // Work distributed by current load
    HIERARCHICAL,       // Tree-based distribution
    SUBGROUP_BASED,     // Divide into sub-groups
    COMMITTEE_BASED     // Committee structure with specialization
}

// === GROUP FORMATION ===

/**
 * Group instance with actual members
 */
@Serializable
data class ConcentricGroup(
    val id: NUID,
    val template: GroupTemplate,
    val members: Set<NUID>,
    val leader: NUID? = null,
    val formedAt: Instant = Clock.System.now(),
    val purpose: GroupPurpose
) {
    init {
        require(members.size <= template.size) {
            "Group has more members than template allows"
        }
    }
    
    fun isFull(): Boolean = members.size == template.size
    fun hasQuorum(): Boolean = template.hasQuorum(members.size)
    fun canAddMember(): Boolean = members.size < template.size
}

enum class GroupPurpose {
    TASK_PROCESSING,
    CONSENSUS_BUILDING,
    VALIDATION,
    RESEARCH,
    COORDINATION
}

// === GROUP MANAGEMENT ===

/**
 * Manages group formation and membership
 */
class GroupManager {
    private val groups = mutableMapOf<NUID, ConcentricGroup>()
    private val memberToGroups = mutableMapOf<NUID, MutableSet<NUID>>()
    
    /**
     * Form a new group with given template
     */
    fun formGroup(
        template: GroupTemplate,
        purpose: GroupPurpose,
        initialMembers: Set<NUID> = emptySet()
    ): ConcentricGroup {
        require(initialMembers.size <= template.size) {
            "Too many initial members for template"
        }
        
        val group = ConcentricGroup(
            id = NUID.random(),
            template = template,
            members = initialMembers,
            leader = selectLeader(template, initialMembers),
            purpose = purpose
        )
        
        groups[group.id] = group
        
        // Update member mappings
        initialMembers.forEach { member ->
            memberToGroups.getOrPut(member) { mutableSetOf() }.add(group.id)
        }
        
        return group
    }
    
    /**
     * Add member to existing group
     */
    fun addMember(groupId: NUID, memberId: NUID): Boolean {
        val group = groups[groupId] ?: return false
        
        if (!group.canAddMember() || memberId in group.members) {
            return false
        }
        
        // Create updated group
        val updatedGroup = group.copy(
            members = group.members + memberId
        )
        
        groups[groupId] = updatedGroup
        memberToGroups.getOrPut(memberId) { mutableSetOf() }.add(groupId)
        
        return true
    }
    
    /**
     * Remove member from group
     */
    fun removeMember(groupId: NUID, memberId: NUID): Boolean {
        val group = groups[groupId] ?: return false
        
        if (memberId !in group.members) {
            return false
        }
        
        val updatedMembers = group.members - memberId
        
        // Check if group still viable
        if (updatedMembers.isEmpty()) {
            disbandGroup(groupId)
            return true
        }
        
        // Update group
        val updatedGroup = group.copy(
            members = updatedMembers,
            leader = if (group.leader == memberId) {
                selectLeader(group.template, updatedMembers)
            } else group.leader
        )
        
        groups[groupId] = updatedGroup
        memberToGroups[memberId]?.remove(groupId)
        
        return true
    }
    
    /**
     * Disband a group
     */
    fun disbandGroup(groupId: NUID) {
        val group = groups.remove(groupId) ?: return
        
        // Clean up member mappings
        group.members.forEach { member ->
            memberToGroups[member]?.remove(groupId)
        }
    }
    
    /**
     * Find groups by member
     */
    fun getGroupsForMember(memberId: NUID): Set<ConcentricGroup> {
        val groupIds = memberToGroups[memberId] ?: return emptySet()
        return groupIds.mapNotNull { groups[it] }.toSet()
    }
    
    /**
     * Find groups by template type
     */
    fun getGroupsByTemplate(template: GroupTemplate): List<ConcentricGroup> {
        return groups.values.filter { it.template == template }
    }
    
    /**
     * Optimal group formation for task
     */
    fun formOptimalGroup(
        task: ConcentricTask,
        availableAgents: Set<NUID>
    ): ConcentricGroup? {
        // Select template based on task type
        val template = selectTemplateForTask(task)
        
        // Find agents with required capabilities
        val qualifiedAgents = availableAgents.take(template.size).toSet()
        
        if (qualifiedAgents.size < template.minQuorum) {
            return null // Not enough agents
        }
        
        return formGroup(
            template = template,
            purpose = GroupPurpose.TASK_PROCESSING,
            initialMembers = qualifiedAgents
        )
    }
    
    // Private helper methods
    
    private fun selectLeader(template: GroupTemplate, members: Set<NUID>): NUID? {
        if (members.isEmpty()) return null
        
        return when (template.getWorkDistributionStrategy()) {
            WorkDistributionStrategy.ROTATING_LEADER,
            WorkDistributionStrategy.HIERARCHICAL,
            WorkDistributionStrategy.COMMITTEE_BASED -> members.first() // Could be more sophisticated
            else -> null // No designated leader
        }
    }
    
    private fun selectTemplateForTask(task: ConcentricTask): GroupTemplate {
        // Find templates that list this task type as optimal
        val optimalTemplates = listOf(
            Dyad, Triad, Pentad, Octad, Dodecad, Senate
        ).filter { task.type in it.optimalTaskTypes }
        
        // Default selection based on task priority and type
        return optimalTemplates.firstOrNull() ?: when (task.priority) {
            TaskPriority.CRITICAL -> Triad // Fast decision with fault tolerance
            TaskPriority.HIGH -> Pentad
            TaskPriority.NORMAL -> Octad
            else -> Dodecad
        }
    }
}

// === GROUP COLLABORATION PATTERNS ===

/**
 * Collaboration coordinator for group work
 */
class GroupCollaborationCoordinator(
    val group: ConcentricGroup,
    val networkManager: ConcentricNetworkManager
) {
    private val memberStates = mutableMapOf<NUID, MemberState>()
    private val taskAssignments = mutableMapOf<NUID, MutableSet<NUID>>() // task -> members
    
    private val _collaborationFlow = MutableSharedFlow<CollaborationEvent>()
    val collaborationFlow: SharedFlow<CollaborationEvent> = _collaborationFlow.asSharedFlow()
    
    /**
     * Distribute work according to group's strategy
     */
    suspend fun distributeWork(tasks: List<ConcentricTask>) {
        when (group.template.getWorkDistributionStrategy()) {
            WorkDistributionStrategy.PEER_VALIDATION -> distributePeerValidation(tasks)
            WorkDistributionStrategy.ROTATING_LEADER -> distributeRotatingLeader(tasks)
            WorkDistributionStrategy.LOAD_BALANCED -> distributeLoadBalanced(tasks)
            WorkDistributionStrategy.HIERARCHICAL -> distributeHierarchical(tasks)
            WorkDistributionStrategy.SUBGROUP_BASED -> distributeSubgroup(tasks)
            WorkDistributionStrategy.COMMITTEE_BASED -> distributeCommittee(tasks)
        }
    }
    
    private suspend fun distributePeerValidation(tasks: List<ConcentricTask>) {
        // Each task assigned to one member, validated by another
        require(group.members.size >= 2) { "Peer validation needs at least 2 members" }
        
        val memberList = group.members.toList()
        tasks.forEachIndexed { index, task ->
            val primary = memberList[index % memberList.size]
            val validator = memberList[(index + 1) % memberList.size]
            
            assignTask(task, setOf(primary, validator))
            
            _collaborationFlow.emit(
                CollaborationEvent.TaskAssigned(
                    taskId = task.id,
                    assignedTo = setOf(primary, validator),
                    role = mapOf(
                        primary to MemberRole.PRIMARY,
                        validator to MemberRole.VALIDATOR
                    )
                )
            )
        }
    }
    
    private suspend fun distributeRotatingLeader(tasks: List<ConcentricTask>) {
        // Leader assigns work, leadership rotates per batch
        val memberList = group.members.toList()
        var leaderIndex = 0
        
        tasks.chunked(max(1, tasks.size / group.members.size)).forEach { batch ->
            val leader = memberList[leaderIndex % memberList.size]
            val workers = memberList - leader
            
            batch.forEachIndexed { index, task ->
                val assignedWorker = workers[index % workers.size]
                assignTask(task, setOf(leader, assignedWorker))
            }
            
            leaderIndex++
        }
    }
    
    private suspend fun distributeLoadBalanced(tasks: List<ConcentricTask>) {
        // Assign based on current load
        val loads = group.members.associateWith { member ->
            memberStates[member]?.currentLoad ?: 0
        }.toMutableMap()
        
        tasks.forEach { task ->
            // Find member with lowest load
            val assignee = loads.minBy { it.value }.key
            
            assignTask(task, setOf(assignee))
            loads[assignee] = loads[assignee]!! + 1
        }
    }
    
    private suspend fun distributeHierarchical(tasks: List<ConcentricTask>) {
        // Tree-based distribution
        val memberList = group.members.toList()
        val root = memberList.firstOrNull() ?: return
        
        // Build tree structure (binary for simplicity)
        val tree = buildHierarchicalTree(memberList)
        
        // Distribute from root down
        distributeTreeTasks(tasks, tree, root)
    }
    
    private suspend fun distributeSubgroup(tasks: List<ConcentricTask>) {
        // Divide into optimal sub-groups
        val subgroupSize = when (group.template.size) {
            12 -> 3 // 4 groups of 3
            8 -> 2  // 4 groups of 2
            else -> 2
        }
        
        val subgroups = group.members.chunked(subgroupSize)
        
        tasks.forEachIndexed { index, task ->
            val subgroup = subgroups[index % subgroups.size]
            assignTask(task, subgroup.toSet())
        }
    }
    
    private suspend fun distributeCommittee(tasks: List<ConcentricTask>) {
        // Committee-based with specialization
        val committees = formCommittees(group.members, tasks)
        
        tasks.forEach { task ->
            val committee = selectCommitteeForTask(committees, task)
            assignTask(task, committee)
        }
    }
    
    // Helper methods
    
    private suspend fun assignTask(task: ConcentricTask, members: Set<NUID>) {
        taskAssignments.getOrPut(task.id) { mutableSetOf() }.addAll(members)
        
        members.forEach { member ->
            updateMemberState(member) { state ->
                state.copy(
                    currentLoad = state.currentLoad + 1,
                    assignedTasks = state.assignedTasks + task.id
                )
            }
        }
    }
    
    private fun updateMemberState(
        memberId: NUID,
        update: (MemberState) -> MemberState
    ) {
        val current = memberStates[memberId] ?: MemberState(memberId)
        memberStates[memberId] = update(current)
    }
    
    private fun buildHierarchicalTree(members: List<NUID>): Map<NUID, List<NUID>> {
        val tree = mutableMapOf<NUID, MutableList<NUID>>()
        
        // Simple binary tree
        for (i in members.indices) {
            val leftChild = 2 * i + 1
            val rightChild = 2 * i + 2
            
            if (leftChild < members.size) {
                tree.getOrPut(members[i]) { mutableListOf() }.add(members[leftChild])
            }
            if (rightChild < members.size) {
                tree.getOrPut(members[i]) { mutableListOf() }.add(members[rightChild])
            }
        }
        
        return tree
    }
    
    private suspend fun distributeTreeTasks(
        tasks: List<ConcentricTask>,
        tree: Map<NUID, List<NUID>>,
        node: NUID
    ) {
        val children = tree[node] ?: emptyList()
        if (children.isEmpty()) {
            // Leaf node - assign tasks
            val myTasks = tasks.take(max(1, tasks.size / group.members.size))
            myTasks.forEach { task ->
                assignTask(task, setOf(node))
            }
        } else {
            // Distribute to children
            val chunks = tasks.chunked(max(1, tasks.size / children.size))
            children.forEachIndexed { index, child ->
                if (index < chunks.size) {
                    distributeTreeTasks(chunks[index], tree, child)
                }
            }
        }
    }
    
    private fun formCommittees(
        members: Set<NUID>,
        tasks: List<ConcentricTask>
    ): Map<TaskType, Set<NUID>> {
        // Form committees based on task types
        val taskTypes = tasks.map { it.type }.distinct()
        val committeeSize = max(2, members.size / taskTypes.size)
        
        val memberList = members.toList()
        return taskTypes.mapIndexed { index, taskType ->
            val start = index * committeeSize
            val end = minOf(start + committeeSize, memberList.size)
            taskType to memberList.subList(start, end).toSet()
        }.toMap()
    }
    
    private fun selectCommitteeForTask(
        committees: Map<TaskType, Set<NUID>>,
        task: ConcentricTask
    ): Set<NUID> {
        return committees[task.type] ?: committees.values.firstOrNull() ?: emptySet()
    }
}

// === SUPPORTING TYPES ===

/**
 * Member state within a group
 */
@Serializable
data class MemberState(
    val memberId: NUID,
    val currentLoad: Int = 0,
    val assignedTasks: Set<NUID> = emptySet(),
    val role: MemberRole = MemberRole.MEMBER,
    val lastActive: Instant = Clock.System.now()
)

enum class MemberRole {
    LEADER,
    PRIMARY,
    VALIDATOR,
    MEMBER,
    OBSERVER
}

/**
 * Collaboration events
 */
sealed class CollaborationEvent {
    data class TaskAssigned(
        val taskId: NUID,
        val assignedTo: Set<NUID>,
        val role: Map<NUID, MemberRole>
    ) : CollaborationEvent()
    
    data class TaskCompleted(
        val taskId: NUID,
        val completedBy: NUID,
        val result: ByteArray
    ) : CollaborationEvent()
    
    data class ValidationRequired(
        val taskId: NUID,
        val validator: NUID,
        val primary: NUID
    ) : CollaborationEvent()
    
    data class ConsensusReached(
        val taskId: NUID,
        val decision: QuorumDecision
    ) : CollaborationEvent()
}