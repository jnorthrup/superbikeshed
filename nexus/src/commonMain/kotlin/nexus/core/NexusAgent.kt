package nexus.core

// Imports from the NEW NexusTypes.kt
import nexus.core.Action
import nexus.core.AgentConfiguration
import nexus.core.Capability
import nexus.core.Change
import nexus.core.EnvironmentContext
import nexus.core.Feedback
import nexus.core.LearningUpdate
import nexus.core.Outcome
import nexus.core.WorkflowOutcome // Import WorkflowOutcome
import nexus.core.PredictedAction
import nexus.core.Problem
import nexus.core.ProjectContext
import nexus.core.Request
import nexus.core.Response
import nexus.core.ScoredSuggestion
import nexus.core.Solution
import nexus.core.Workflow
import nexus.core.GossipPayload // Import GossipPayload
// External and TrikeShed imports
import borg.trikeshed.lib.Series
import kotlinx.coroutines.flow.Flow

interface NexusAgent {
    suspend fun scanEnvironment(): EnvironmentContext
    suspend fun discoverCapabilities(): Series<Capability>
    suspend fun synthesizeContext(): ProjectContext

    suspend fun generateSolutions(problem: Problem, context: ProjectContext): Series<Solution>
    suspend fun evolveSolution(solution: Solution, feedback: Feedback): Solution // Or Series<Solution>
    suspend fun learnFromOutcome(outcome: Outcome): LearningUpdate // Or modify internal state

    suspend fun executeAction(action: Action): Outcome
    suspend fun orchestrateWorkflow(workflow: Workflow): WorkflowOutcome
    suspend fun adaptToEnvironment(envContext: EnvironmentContext): AgentConfiguration

    // Functions derived from the original NexusAgent.kt extension functions (simplified for interface)
    suspend fun processRequest(request: Request): Response
    suspend fun solveProblem(problem: Problem): Series<Solution>
    // executeAction is already above
    // orchestrateWorkflow is already above
    suspend fun getSuggestions(): Series<ScoredSuggestion>
    suspend fun predictNextActions(): Series<PredictedAction>
    fun observeChanges(): Flow<Change>

    suspend fun gossipAbout(topic: String, payload: GossipPayload)
}
