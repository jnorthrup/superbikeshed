package fiduciary.agents

import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlin.collections.*

/**
 * Multi-agent system for fiduciary attention with semantic teams
 * Uses FSM codec for synchronous deterministic decision management
 */
class FiduciaryAgentSystem {
    
    private val leader = AgentLeader()
    private val teams = mutableMapOf<TeamId, AgentTeam>()
    private val codecRegistry = CodecRegistry()
    
    /**
     * Agent with semantic traits, capabilities, and priority rank
     */
    data class Agent(
        val id: AgentId,
        val teamId: TeamId,
        val semanticTraits: Set<SemanticTrait>,
        val capabilities: Set<Capability>,
        val priorityRank: Int, // C+C priority (rank)
        val fsmState: FSMState = FSMState.IDLE,
        val registeredCodecs: Set<CodecId> = emptySet()
    )
    
    /**
     * Semantic traits that define agent behavior patterns
     */
    enum class SemanticTrait {
        ANALYTICAL,      // Data analysis and pattern recognition
        CREATIVE,        // Novel solution generation
        CRITICAL,        // Risk assessment and validation
        COLLABORATIVE,   // Team coordination and synthesis
        SPECIALIZED,     // Domain-specific expertise
        ADAPTIVE,        // Dynamic response to changing contexts
        CONSERVATIVE,    // Stability and compliance focus
        INNOVATIVE       // Experimental and forward-thinking
    }
    
    /**
     * Agent capabilities for task execution
     */
    enum class Capability {
        DOCUMENT_PARSING,
        CONCEPT_EXTRACTION,
        ATTENTION_FILTERING,
        PATTERN_RECOGNITION,
        RISK_ASSESSMENT,
        OPTIMIZATION,
        VALIDATION,
        SYNTHESIS,
        COORDINATION,
        DECISION_MAKING
    }
    
    /**
     * FSM states for agent behavior
     */
    enum class FSMState {
        IDLE,
        ANALYZING,
        COLLABORATING,
        DECIDING,
        EXECUTING,
        VALIDATING,
        SYNTHESIZING,
        ERROR
    }
    
    /**
     * FSM transitions with deterministic outcomes
     */
    data class FSMTransition(
        val fromState: FSMState,
        val trigger: String,
        val toState: FSMState,
        val action: String,
        val priority: Int
    )
    
    /**
     * Agent team with coordinated behavior
     */
    data class AgentTeam(
        val id: TeamId,
        val name: String,
        val agents: MutableList<Agent> = mutableListOf(),
        val teamTraits: Set<SemanticTrait>,
        val coordinationPattern: CoordinationPattern
    )
    
    /**
     * Team coordination patterns
     */
    enum class CoordinationPattern {
        HIERARCHICAL,    // Leader-follower with clear chain of command
        COLLABORATIVE,   // Equal participation with consensus
        SPECIALIZED,     // Role-based with handoffs
        ADAPTIVE,        // Dynamic role assignment based on context
        VALIDATION       // Multi-stage validation with different perspectives
    }
    
    /**
     * Codec for FSM management and message reduction
     */
    data class Codec(
        val id: CodecId,
        val name: String,
        val transitions: List<FSMTransition>,
        val messageProtocol: MessageProtocol,
        val decisionMatrix: DecisionMatrix
    )
    
    /**
     * Message protocol for efficient communication
     */
    data class MessageProtocol(
        val messageTypes: Set<String>,
        val compressionRules: Map<String, String>,
        val priorityMapping: Map<Int, String>
    )
    
    /**
     * Decision matrix for deterministic outcomes
     */
    data class DecisionMatrix(
        val inputs: Set<String>,
        val outputs: Set<String>,
        val rules: Map<String, String>
    )
    
    /**
     * Agent leader managing codec decisions synchronously
     */
    class AgentLeader {
        private val registeredAgents = mutableMapOf<AgentId, Agent>()
        private val activeCodecs = mutableMapOf<CodecId, Codec>()
        private val decisionQueue = mutableListOf<DecisionRequest>()
        
        /**
         * Register agent with leader
         */
        fun registerAgent(agent: Agent) {
            registeredAgents[agent.id] = agent
            // Register agent's codecs
            agent.registeredCodecs.forEach { codecId ->
                // Codec registration logic
            }
        }
        
        /**
         * Process decision request synchronously and deterministically
         */
        suspend fun processDecision(request: DecisionRequest): DecisionResponse {
            val agent = registeredAgents[request.agentId] ?: 
                throw IllegalArgumentException("Agent not registered")
            
            val codec = activeCodecs[request.codecId] ?:
                throw IllegalArgumentException("Codec not found")
            
            // Apply decision matrix rules deterministically
            val decision = applyDecisionMatrix(codec.decisionMatrix, request.inputs)
            
            // Update agent FSM state
            val newState = applyFSMTransition(codec, agent.fsmState, request.trigger)
            
            return DecisionResponse(
                agentId = request.agentId,
                decision = decision,
                newState = newState,
                timestamp = Clock.System.now()
            )
        }
        
        private fun applyDecisionMatrix(matrix: DecisionMatrix, inputs: Map<String, Any>): String {
            // Deterministic decision logic based on matrix rules
            val inputKey = inputs.entries.joinToString("|") { "${it.key}=${it.value}" }
            return matrix.rules[inputKey] ?: "DEFAULT"
        }
        
        private fun applyFSMTransition(codec: Codec, currentState: FSMState, trigger: String): FSMState {
            val transition = codec.transitions.find { 
                it.fromState == currentState && it.trigger == trigger 
            }
            return transition?.toState ?: currentState
        }
    }
    
    /**
     * Codec registry for managing available codecs
     */
    class CodecRegistry {
        private val codecs = mutableMapOf<CodecId, Codec>()
        
        fun registerCodec(codec: Codec) {
            codecs[codec.id] = codec
        }
        
        fun getCodec(id: CodecId): Codec? = codecs[id]
        
        fun createDefaultCodec(): Codec {
            return Codec(
                id = CodecId("default"),
                name = "Default Fiduciary Codec",
                transitions = listOf(
                    FSMTransition(FSMState.IDLE, "ANALYZE", FSMState.ANALYZING, "START_ANALYSIS", 1),
                    FSMTransition(FSMState.ANALYZING, "COLLABORATE", FSMState.COLLABORATING, "SHARE_FINDINGS", 2),
                    FSMTransition(FSMState.COLLABORATING, "DECIDE", FSMState.DECIDING, "MAKE_DECISION", 3),
                    FSMTransition(FSMState.DECIDING, "EXECUTE", FSMState.EXECUTING, "IMPLEMENT_DECISION", 4),
                    FSMTransition(FSMState.EXECUTING, "VALIDATE", FSMState.VALIDATING, "CHECK_RESULTS", 5),
                    FSMTransition(FSMState.VALIDATING, "SYNTHESIZE", FSMState.SYNTHESIZING, "INTEGRATE_FINDINGS", 6),
                    FSMTransition(FSMState.SYNTHESIZING, "COMPLETE", FSMState.IDLE, "RESET_STATE", 7)
                ),
                messageProtocol = MessageProtocol(
                    messageTypes = setOf("ANALYZE", "COLLABORATE", "DECIDE", "EXECUTE", "VALIDATE", "SYNTHESIZE"),
                    compressionRules = mapOf(
                        "ANALYZE" to "A",
                        "COLLABORATE" to "C", 
                        "DECIDE" to "D",
                        "EXECUTE" to "E",
                        "VALIDATE" to "V",
                        "SYNTHESIZE" to "S"
                    ),
                    priorityMapping = mapOf(
                        1 to "LOW",
                        2 to "MEDIUM", 
                        3 to "HIGH",
                        4 to "CRITICAL"
                    )
                ),
                decisionMatrix = DecisionMatrix(
                    inputs = setOf("RISK_LEVEL", "COMPLEXITY", "URGENCY", "RESOURCES"),
                    outputs = setOf("PROCEED", "REVIEW", "ESCALATE", "ABORT"),
                    rules = mapOf(
                        "RISK_LEVEL=LOW|COMPLEXITY=LOW|URGENCY=LOW|RESOURCES=AVAILABLE" to "PROCEED",
                        "RISK_LEVEL=HIGH|COMPLEXITY=HIGH|URGENCY=HIGH|RESOURCES=AVAILABLE" to "ESCALATE",
                        "RISK_LEVEL=MEDIUM|COMPLEXITY=MEDIUM|URGENCY=MEDIUM|RESOURCES=AVAILABLE" to "REVIEW"
                    )
                )
            )
        }
    }
    
    /**
     * Decision request for synchronous processing
     */
    data class DecisionRequest(
        val agentId: AgentId,
        val codecId: CodecId,
        val trigger: String,
        val inputs: Map<String, Any>,
        val priority: Int
    )
    
    /**
     * Decision response with deterministic outcome
     */
    data class DecisionResponse(
        val agentId: AgentId,
        val decision: String,
        val newState: FSMState,
        val timestamp: Instant
    )
    
    /**
     * Create and register a team of agents
     */
    fun createTeam(
        teamId: TeamId,
        name: String,
        teamTraits: Set<SemanticTrait>,
        coordinationPattern: CoordinationPattern
    ): AgentTeam {
        val team = AgentTeam(teamId, name, teamTraits = teamTraits, coordinationPattern = coordinationPattern)
        teams[teamId] = team
        return team
    }
    
    /**
     * Add agent to team and register with leader
     */
    fun addAgentToTeam(teamId: TeamId, agent: Agent) {
        val team = teams[teamId] ?: throw IllegalArgumentException("Team not found")
        team.agents.add(agent)
        leader.registerAgent(agent)
    }
    
    /**
     * Process team decision with synchronous coordination
     */
    suspend fun processTeamDecision(
        teamId: TeamId,
        request: DecisionRequest
    ): List<DecisionResponse> {
        val team = teams[teamId] ?: throw IllegalArgumentException("Team not found")
        
        return when (team.coordinationPattern) {
            CoordinationPattern.HIERARCHICAL -> processHierarchicalDecision(team, request)
            CoordinationPattern.COLLABORATIVE -> processCollaborativeDecision(team, request)
            CoordinationPattern.SPECIALIZED -> processSpecializedDecision(team, request)
            CoordinationPattern.ADAPTIVE -> processAdaptiveDecision(team, request)
            CoordinationPattern.VALIDATION -> processValidationDecision(team, request)
        }
    }
    
    private suspend fun processHierarchicalDecision(
        team: AgentTeam, 
        request: DecisionRequest
    ): List<DecisionResponse> {
        // Sort agents by priority rank and process sequentially
        val sortedAgents = team.agents.sortedBy { it.priorityRank }
        return sortedAgents.map { agent ->
            leader.processDecision(request.copy(agentId = agent.id))
        }
    }
    
    private suspend fun processCollaborativeDecision(
        team: AgentTeam,
        request: DecisionRequest
    ): List<DecisionResponse> {
        // Process all agents in parallel and synthesize results
        return team.agents.map { agent ->
            leader.processDecision(request.copy(agentId = agent.id))
        }
    }
    
    private suspend fun processSpecializedDecision(
        team: AgentTeam,
        request: DecisionRequest
    ): List<DecisionResponse> {
        // Route to agents with matching capabilities
        val relevantAgents = team.agents.filter { agent ->
            agent.capabilities.any { it.name.contains(request.trigger, ignoreCase = true) }
        }
        return relevantAgents.map { agent ->
            leader.processDecision(request.copy(agentId = agent.id))
        }
    }
    
    private suspend fun processAdaptiveDecision(
        team: AgentTeam,
        request: DecisionRequest
    ): List<DecisionResponse> {
        // Dynamically select agents based on context and current state
        val adaptiveAgents = team.agents.filter { agent ->
            agent.semanticTraits.contains(SemanticTrait.ADAPTIVE) ||
            agent.fsmState == FSMState.IDLE
        }
        return adaptiveAgents.map { agent ->
            leader.processDecision(request.copy(agentId = agent.id))
        }
    }
    
    private suspend fun processValidationDecision(
        team: AgentTeam,
        request: DecisionRequest
    ): List<DecisionResponse> {
        // Multi-stage validation with different agent perspectives
        val criticalAgents = team.agents.filter { agent ->
            agent.semanticTraits.contains(SemanticTrait.CRITICAL)
        }
        val analyticalAgents = team.agents.filter { agent ->
            agent.semanticTraits.contains(SemanticTrait.ANALYTICAL)
        }
        
        val criticalResults = criticalAgents.map { agent ->
            leader.processDecision(request.copy(agentId = agent.id))
        }
        val analyticalResults = analyticalAgents.map { agent ->
            leader.processDecision(request.copy(agentId = agent.id))
        }
        
        return criticalResults + analyticalResults
    }
    
    /**
     * Get system statistics
     */
    fun getSystemStats(): SystemStats {
        return SystemStats(
            totalTeams = teams.size,
            totalAgents = teams.values.sumOf { it.agents.size },
            activeCodecs = leader.activeCodecs.size,
            registeredAgents = leader.registeredAgents.size
        )
    }
    
    data class SystemStats(
        val totalTeams: Int,
        val totalAgents: Int,
        val activeCodecs: Int,
        val registeredAgents: Int
    )
}

// Type aliases for clarity
typealias AgentId = String
typealias TeamId = String
typealias CodecId = String 