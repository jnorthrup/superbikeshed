package nexus.core

// Imports from NexusTypes.kt for return types and parameters
import nexus.core.Action
import nexus.core.AgentConfiguration
import nexus.core.Capability
import nexus.core.Change
import nexus.core.EnvironmentContext
import nexus.core.Feedback
import nexus.core.LearningUpdate
import nexus.core.Outcome
import nexus.core.Pattern
import nexus.core.PredictedAction
import nexus.core.Problem
import nexus.core.ProjectContext
import nexus.core.Request
import nexus.core.Response
import nexus.core.ScoredSuggestion
import nexus.core.Solution
import nexus.core.Workflow
import nexus.core.WorkflowOutcome
import nexus.core.GossipPayload


// Import for NexusAgent interface
import nexus.core.NexusAgent

// External and TrikeShed imports
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.TensorSeries
import borg.trikeshed.lib.j // For Join infix constructor
import borg.trikeshed.lib.seriesOf // Convenient way to create Series
import borg.trikeshed.lib.materialize // For Series.▶ syntax (if needed, or use .map directly)
import borg.trikeshed.lib.emptySeries // For creating empty series

// Orchestration imports
import borg.trikeshed.orchestration.BaseOrchestrationAgent

// IPFS PubSub Service import
import borg.ipfs.IpfsPubSubService

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import java.util.UUID // For generating a default agentId if not provided

// kotlinx.serialization imports
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

// For K2Script Execution & IntelliJ Enumeration
import java.io.File
import java.util.concurrent.TimeUnit
import nexus.core.ActionNames // Import the new ActionNames
// import nexus.intellij.project_model.IntelliJProjectDetails // TODO: Import from shared module when available
// import kotlinx.serialization.json.Json // TODO: Import for deserialization when model is shared
// Assuming BaseOrchestrationAgent provides a 'json' instance of kotlinx.serialization.json.Json
// If not, DefaultNexusAgent would need to instantiate its own or have it passed in.
// For now, let's assume 'this.json' is available from BaseOrchestrationAgent.

@Serializable
data class SerializableKeyValuePair(val key: String, val value: String)

class DefaultNexusAgent(
    givenAgentId: String? = null,
    parentCoroutineContext: CoroutineContext = Dispatchers.Default,
    ipfsPubSubService: IpfsPubSubService // Expecting an instance to be passed in
) : BaseOrchestrationAgent(
    agentId = givenAgentId ?: "DefaultNexusAgent-${UUID.randomUUID()}",
    parentCoroutineContext = parentCoroutineContext,
    ipfsPubSubService = ipfsPubSubService
), NexusAgent {

    // Predefined patterns for getSuggestions
    private val predefinedPatterns: Series<Pattern> = seriesOf(
        seriesOf("code_smell", "long_method", "class_X"),
        seriesOf("performance_issue", "database_query", "entity_Y"),
        seriesOf("refactor_opportunity", "duplicate_code", "module_Z")
    )

    override suspend fun start() {
        println("DefaultNexusAgent [${this.agentId}] started.")
    }

    // --- NexusAgent Interface Implementation ---

    override suspend fun scanEnvironment(): EnvironmentContext {
        return seriesOf(
            "OS" j "Linux",
            "KotlinVersion" j "1.9.22",
            "Cores" j "8"
        )
    }

    override suspend fun discoverCapabilities(): Series<Capability> {
        return seriesOf(
            "RunCommand" j seriesOf("command:String", "timeout:Int (ms)"),
            "ReadFile" j seriesOf("filePath:String"),
            "WriteFile" j seriesOf("filePath:String", "content:String")
        )
    }

    override suspend fun synthesizeContext(): ProjectContext {
        return seriesOf(
            "CurrentProject" j "NexusCoreRefactoring",
            "User" j "DevAgent"
        )
    }

    override suspend fun generateSolutions(problem: Problem, context: ProjectContext): Series<Solution> {
        val problemDescription = problem.materialize().joinToString("\n") { it }
        val contextSummary = context.materialize().joinToString("; ") { "${it.a}=${it.b}" }

        val sol1: Solution = seriesOf(
            "Solution Alpha for problem: '$problemDescription'",
            "Based on context: '$contextSummary'",
            "Step 1: Analyze requirements.",
            "Step 2: Implement feature X.",
            "Step 3: Write unit tests for X."
        )
        val sol2: Solution = seriesOf(
            "Solution Beta (alternative) for problem: '$problemDescription'",
            "Considering context: '$contextSummary'",
            "Step 1: Refactor module Y.",
            "Step 2: Add new API endpoint.",
            "Step 3: Document changes."
        )
        return seriesOf(sol1, sol2)
    }

    override suspend fun evolveSolution(solution: Solution, feedback: Feedback): Solution {
        val originalSolutionMaterialized = solution.materialize()
        val feedbackDesc = "${feedback.a}: ${feedback.b.materialize().joinToString(", ", limit = 2)}"

        val newSolutionSteps = originalSolutionMaterialized.toMutableList()
        newSolutionSteps.add("EVOLVED with feedback ($feedbackDesc)")

        return TensorSeries.fromList(newSolutionSteps)
    }

    override suspend fun learnFromOutcome(outcome: Outcome): LearningUpdate {
        val outcomeSummary = outcome.materialize().joinToString(", ", limit = 3) { it.take(50) }
        return "OutcomeProcessed" j "Learned from outcome: $outcomeSummary"
    }

    override suspend fun executeAction(action: Action): Outcome {
        val actionName = action.a
        val args = action.b.materialize() // Get args as List<String>

        println("Agent [${this.agentId}] executing action: $actionName with args [${args.joinToString(", ")}]")

        return when (actionName) {
            ActionNames.K2SCRIPT_EXECUTE -> {
                // TODO: Make k2scriptCommandPath configurable (e.g., via AgentConfiguration or environment variable)
                val k2scriptCommandPath = "k2script" // Assuming k2script is in PATH or provide full path

                if (args.isEmpty()) {
                    return seriesOf(
                        "Error: K2SCRIPT_EXECUTE action requires at least a script path.",
                        "Result: Failure",
                        "Timestamp: ${System.currentTimeMillis()}"
                    )
                }
                val scriptPath = args[0]
                val scriptArgs = args.drop(1)

                try {
                    val command = mutableListOf<String>()
                    command.add(k2scriptCommandPath)
                    command.add(scriptPath)
                    command.addAll(scriptArgs)

                    // TODO: Make working directory configurable
                    val processBuilder = ProcessBuilder(command)
                        .directory(File(".")) // Execute in the current working directory of the Nexus agent
                        .redirectErrorStream(true) // Merge stdout and stderr

                    val process = processBuilder.start()

                    // TODO: Make timeout configurable
                    val timeoutSeconds = 60L
                    val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

                    if (!exited) {
                        process.destroyForcibly()
                        return seriesOf(
                            "K2Script execution timed out after $timeoutSeconds seconds.",
                            "Script: $scriptPath",
                            "Args: ${scriptArgs.joinToString(" ")}",
                            "Result: Failure (Timeout)",
                            "Timestamp: ${System.currentTimeMillis()}"
                        )
                    }

                    val exitCode = process.exitValue()
                    val output = process.inputStream.bufferedReader().readText()

                    seriesOf(
                        "K2Script execution finished for script: $scriptPath",
                        "Args: ${scriptArgs.joinToString(" ")}",
                        "Exit Code: $exitCode",
                        "Output:\n$output",
                        "Result: ${if (exitCode == 0) "Success" else "Failure"}",
                        "Timestamp: ${System.currentTimeMillis()}"
                    )
                } catch (e: Exception) {
                    seriesOf(
                        "Error executing K2Script: ${e.message}",
                        "Script: $scriptPath",
                        "Args: ${scriptArgs.joinToString(" ")}",
                        "Result: Failure (Exception)",
                        "Timestamp: ${System.currentTimeMillis()}"
                    )
                }
            }
            else -> {
                // Default handling for other actions
                val argsString = args.joinToString(", ")
                seriesOf(
                    "Executed action: '$actionName'",
                    "Arguments: [$argsString]",
                    "Result: Success (Default Handler)",
                    "Timestamp: ${System.currentTimeMillis()}"
                )
            }
        }
    }

    override suspend fun orchestrateWorkflow(workflow: Workflow): WorkflowOutcome {
        val collectedOutcomes = mutableListOf<Outcome>()
        workflow.materialize().forEach { action ->
            val outcome = executeAction(action)
            collectedOutcomes.add(outcome)
        }

        val outcomesSeries: Series<Outcome> = if (collectedOutcomes.isEmpty()) {
            emptySeries()
        } else {
            TensorSeries.fromList(collectedOutcomes)
        }
        return workflow j outcomesSeries
    }

    override suspend fun adaptToEnvironment(envContext: EnvironmentContext): AgentConfiguration {
        val newConfigSettings = mutableListOf<Join<String, String>>()
        envContext.materialize().forEach { envVar ->
            if (envVar.a == "OS") {
                newConfigSettings.add("AdaptedConfigForOS" j envVar.b)
            }
            if (envVar.a == "KotlinVersion") {
                newConfigSettings.add("KotlinTargetVersion" j envVar.b)
            }
        }
        newConfigSettings.add("DefaultAdaptation" j "EnabledFor-${this.agentId}")
        newConfigSettings.add("Timestamp" j System.currentTimeMillis().toString())

        return if (newConfigSettings.isEmpty()) {
            emptySeries()
        } else {
            TensorSeries.fromList(newConfigSettings)
        }
    }

    override suspend fun processRequest(request: Request): Response {
        val requestContent = request.materialize().joinToString(separator = "; ") { it }
        println("Agent [${this.agentId}] processing request: $requestContent")

        return seriesOf(
            "Agent [${this.agentId}] received problem: \"$requestContent\"",
            "DefaultNexusAgent is processing this.",
            "Solution is pending implementation."
        )
    }

    override suspend fun solveProblem(problem: Problem): Series<Solution> {
        val currentContext = synthesizeContext()
        return generateSolutions(problem, currentContext)
    }

    override suspend fun getSuggestions(): Series<ScoredSuggestion> {
        val suggestions = mutableListOf<ScoredSuggestion>()
        this.predefinedPatterns.materialize().forEachIndexed { index, pattern ->
            val patternDesc = pattern.materialize().joinToString(" - ") { it }
            val score = (index * 0.1) + 0.5
            suggestions.add(score j "Suggestion for pattern: ($patternDesc)")
        }
        if (suggestions.isEmpty()) {
            return emptySeries()
        }
        return TensorSeries.fromList(suggestions)
    }

    override suspend fun predictNextActions(): Series<PredictedAction> {
        val action1: Action = "CommitChanges" j seriesOf("message:Finalize feature X", "push:true")
        val action2: Action = "RunTests" j seriesOf("scope:unit", "module:featureX")
        val action3: Action = "RequestReview" j seriesOf("reviewer:@teamLead", "crNumber:123")

        val predictedActionsList = listOf(
            action1 j 0.75,
            action2 j 0.60,
            action3 j 0.85
        )

        return if (predictedActionsList.isEmpty()) {
            emptySeries()
        } else {
            TensorSeries.fromList(predictedActionsList)
        }
    }

    override fun observeChanges(): Flow<Change> = flow {
        emit("FileSystem" j "FileA.kt updated")
        delay(100)
        emit("VCS" j "New commit detected")
        delay(100)
        emit("Environment" j "ToolX version changed to 2.0")
        delay(100)
        emit("UserActivity" j "User idle for 5 minutes")
    }

    override suspend fun gossipAbout(topic: String, payload: GossipPayload) {
        // Convert Series<Join<String, String>> to List<SerializableKeyValuePair> for JSON serialization
        val serializablePayload = payload.materialize().map { SerializableKeyValuePair(it.a, it.b) }

        // Assuming 'this.json' is the Json instance from BaseOrchestrationAgent
        val messageJson = this.json.encodeToString(serializablePayload)

        this.ipfsPubSubService.publish(topic, messageJson) // Use the inherited ipfsPubSubService
        println("Agent ${this.agentId} gossipped to $topic: $messageJson")
    }
}
