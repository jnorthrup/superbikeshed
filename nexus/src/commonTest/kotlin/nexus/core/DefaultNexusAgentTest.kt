package nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlin.coroutines.CoroutineContext

// TrikeShed core types and helpers
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j // For Join infix constructor
import borg.trikeshed.lib._i // For creating series easily
import borg.trikeshed.lib.materialize
import borg.trikeshed.lib.TensorSeries // For creating series from list


// TrikeShed Assertions
import borg.trikeshed.lib.shouldHaveSize
import borg.trikeshed.lib.shouldBe
import borg.trikeshed.lib.elementAtShouldBe

// Types from the nexus.core module (main source set)
import nexus.core.DefaultNexusAgent
import nexus.core.EnvironmentContext
import nexus.core.Capability
import nexus.core.Request
import nexus.core.Response
import nexus.core.Problem
import nexus.core.ProjectContext
import nexus.core.Solution
import nexus.core.Action
import nexus.core.Outcome
import nexus.core.ScoredSuggestion
import nexus.core.Workflow
import nexus.core.PredictedAction
import nexus.core.Feedback
import nexus.core.LearningUpdate
import nexus.core.GossipPayload
import nexus.core.SerializableKeyValuePair // Import for deserialization in test

// kotlinx.serialization for test deserialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString


// IPFS PubSub Service import for mocking
import borg.ipfs.IpfsPubSubService

// Mock IpfsPubSubService for testing
class TestIpfsPubSubService : IpfsPubSubService {
    val publications = mutableListOf<Pair<String, String>>()
    var subscribedTopics = mutableMapOf<String, (String) -> Unit>()

    override suspend fun publish(topic: String, data: String) {
        publications.add(topic to data)
        println("TestIpfsPubSubService: Published to $topic: $data")
    }

    override suspend fun subscribe(topic: String, handler: (String) -> Unit): Job? {
        subscribedTopics[topic] = handler
        println("TestIpfsPubSubService: Subscribed to $topic")
        return Job().apply { complete() } // Return a completed job for simplicity in mock
    }

    override fun unsubscribe(topic: String) {
        subscribedTopics.remove(topic)
        println("TestIpfsPubSubService: Unsubscribed from $topic")
    }

    fun clearPublications() {
        publications.clear()
    }
}

class DefaultNexusAgentTest {

    private fun createAgent(
        testCoroutineContext: CoroutineContext = Dispatchers.Unconfined // Use Unconfined for immediate execution in tests
    ): Pair<DefaultNexusAgent, TestIpfsPubSubService> {
        val testIpfsService = TestIpfsPubSubService()
        val agent = DefaultNexusAgent(
            parentCoroutineContext = testCoroutineContext,
            ipfsPubSubService = testIpfsService
        )
        return agent to testIpfsService
    }

    @Test
    fun `scanEnvironment should return expected context`() = runBlocking {
        val (agent, _) = createAgent()
        val expectedContext: EnvironmentContext = _i(
            "OS" j "Linux",
            "KotlinVersion" j "1.9.22",
            "Cores" j "8"
        )
        val actualContext = agent.scanEnvironment()
        actualContext.shouldHaveSize(3)
        actualContext.shouldBe(expectedContext)
    }

    @Test
    fun `discoverCapabilities should return expected capabilities`() = runBlocking {
        val (agent, _) = createAgent()
        val expectedCapabilities: Indexed<Capability> = _i(
            "RunCommand" j _i("command:String", "timeout:Int (ms)"),
            "ReadFile" j _i("filePath:String"),
            "WriteFile" j _i("filePath:String", "content:String")
        )
        val actualCapabilities = agent.discoverCapabilities()
        actualCapabilities.shouldHaveSize(3)
        actualCapabilities.shouldBe(expectedCapabilities) { cap1, cap2 ->
            if (cap1.a != cap2.a) return@shouldBe false
            if (cap1.b.size != cap2.b.size) return@shouldBe false
            var paramsMatch = true
            for (i in 0 until cap1.b.size) {
                if (cap1.b.get(i) != cap2.b.get(i)) {
                    paramsMatch = false
                    break
                }
            }
            paramsMatch
        }
    }

    @Test
    fun `processRequest should return expected response`() = runBlocking {
        val (agent, _) = createAgent()
        val testRequest: Request = _i("Test problem: App crashes on startup", "Provide diagnostic steps.")
        val requestContent = testRequest.materialize().joinToString(separator = "; ") { it }
        val expectedResponse: Response = _i(
            "Agent [${agent.agentId}] received problem: \"$requestContent\"",
            "DefaultNexusAgent is processing this.",
            "Solution is pending implementation."
        )
        val actualResponse = agent.processRequest(testRequest)
        actualResponse.shouldHaveSize(3)
        actualResponse.shouldBe(expectedResponse)
    }

    @Test
    fun `start should activate agent`() = runBlocking {
        val (agent, _) = createAgent()
        assertFalse(agent.isActive, "Agent should not be active before start")
        agent.start()
        assertTrue(agent.isActive, "Agent should be active after start")
        agent.stop()
    }

    @Test
    fun `stop should deactivate agent`() = runBlocking {
        val (agent, _) = createAgent()
        agent.start()
        assertTrue(agent.isActive, "Agent should be active after start")
        agent.stop()
        assertFalse(agent.isActive, "Agent should not be active after stop")
    }

    @Test
    fun `start can optionally publish a lifecycle message`() = runBlocking {
        val (agent, _) = createAgent()
        agent.start()
        assertTrue(agent.isActive, "Agent should be active after start.")
        agent.stop()
    }

    @Test
    fun `generateSolutions should return sample solutions`() = runBlocking {
        val (agent, _) = createAgent()
        val problem: Problem = _i("High CPU usage", "Application becomes unresponsive.")
        val context: ProjectContext = _i("OS" j "Ubuntu 22.04", "AppVersion" j "1.2.3")
        val problemDescription = problem.materialize().joinToString("\n") { it }
        val contextSummary = context.materialize().joinToString("; ") { "${it.a}=${it.b}" }
        val expectedSol1: Solution = _i(
            "Solution Alpha for problem: '$problemDescription'",
            "Based on context: '$contextSummary'",
            "Step 1: Analyze requirements.",
            "Step 2: Implement feature X.",
            "Step 3: Write unit tests for X."
        )
        val expectedSol2: Solution = _i(
            "Solution Beta (alternative) for problem: '$problemDescription'",
            "Considering context: '$contextSummary'",
            "Step 1: Refactor module Y.",
            "Step 2: Add new API endpoint.",
            "Step 3: Document changes."
        )
        val expectedSolutions: Indexed<Solution> = indexOf(expectedSol1, expectedSol2)
        val actualSolutions = agent.generateSolutions(problem, context)
        actualSolutions.shouldHaveSize(2)
        actualSolutions.shouldBe(expectedSolutions) { solA, solB ->
            if (solA.size != solB.size) return@shouldBe false
            var stepsMatch = true
            for (i in 0 until solA.size) {
                if (solA.get(i) != solB.get(i)) {
                    stepsMatch = false
                    break
                }
            }
            stepsMatch
        }
    }

    @Test
    fun `executeAction should return sample outcome`() = runBlocking {
        val (agent, _) = createAgent()
        val action: Action = "RunDiagnostics" j _i("target=CPU", "level=Detailed")
        val actionName = action.a
        val argsString = action.b.materialize().joinToString(", ") { it }
        val actualOutcome = agent.executeAction(action)
        actualOutcome.shouldHaveSize(4)
        actualOutcome.elementAtShouldBe(0, "Executed action: '$actionName'")
        actualOutcome.elementAtShouldBe(1, "Arguments: [$argsString]")
        actualOutcome.elementAtShouldBe(2, "Result: Success (placeholder outcome)")
        assertTrue(actualOutcome.elementAt(3).startsWith("Timestamp: "), "Timestamp format incorrect or missing")
    }

    @Test
    fun `getSuggestions should return predefined suggestions`() = runBlocking {
        val (agent, _) = createAgent()
        val expectedSuggestions: Indexed<ScoredSuggestion> = TensorSeries.fromList(
            listOf(
                0.5 j "Suggestion for pattern: (code_smell - long_method - class_X)",
                0.6 j "Suggestion for pattern: (performance_issue - database_query - entity_Y)",
                0.7 j "Suggestion for pattern: (refactor_opportunity - duplicate_code - module_Z)"
            )
        )
        val actualSuggestions = agent.getSuggestions()
        actualSuggestions.shouldHaveSize(3)
        actualSuggestions.shouldBe(expectedSuggestions) { suggA, suggB ->
            (suggA.a == suggB.a) && (suggA.b == suggB.b)
        }
    }

    @Test
    fun `observeChanges should emit sample changes`() = runBlocking {
        val (agent, _) = createAgent()
        val expectedChanges = listOf(
            "FileSystem" j "FileA.kt updated",
            "VCS" j "New commit detected",
            "Environment" j "ToolX version changed to 2.0",
            "UserActivity" j "User idle for 5 minutes"
        )
        val actualChanges = agent.observeChanges().toList()
        assertEquals(expectedChanges.size, actualChanges.size)
        assertEquals(expectedChanges, actualChanges)
    }

    @Test
    fun `orchestrateWorkflow should execute actions and collect outcomes`() = runBlocking {
        val (agent, _) = createAgent()
        val action1: Action = "SetupEnv" j _i("config=A")
        val action2: Action = "RunBuild" j _i("target=all")
        val workflow: Workflow = indexOf(action1, action2)

        val workflowOutcome = agent.orchestrateWorkflow(workflow)

        workflowOutcome.a.shouldBe(workflow) { wfA, wfB ->
            if (wfA.size != wfB.size) return@shouldBe false
            var actionsMatch = true
            for(i in 0 until wfA.size) {
                val actA = wfA.get(i)
                val actB = wfB.get(i)
                if (actA.a != actB.a || actA.b.size != actB.b.size) {
                    actionsMatch = false; break
                }
                for (j in 0 until actA.b.size) {
                    if (actA.b.get(j) != actB.b.get(j)) {
                        actionsMatch = false; break
                    }
                }
                if (!actionsMatch) break
            }
            actionsMatch
        }

        val outcomesSeries = workflowOutcome.b
        outcomesSeries.shouldHaveSize(2)

        val outcome1 = outcomesSeries.elementAt(0)
        outcome1.shouldHaveSize(4)
        assertEquals("Executed action: '${action1.a}'", outcome1.elementAt(0))
        assertEquals("Arguments: [${action1.b.materialize().joinToString(", ") {it}}]", outcome1.elementAt(1))

        val outcome2 = outcomesSeries.elementAt(1)
        outcome2.shouldHaveSize(4)
        assertEquals("Executed action: '${action2.a}'", outcome2.elementAt(0))
        assertEquals("Arguments: [${action2.b.materialize().joinToString(", ") {it}}]", outcome2.elementAt(1))
    }

    @Test
    fun `predictNextActions should return predefined actions with scores`() = runBlocking {
        val (agent, _) = createAgent()

        val expectedAction1: Action = "CommitChanges" j _i("message:Finalize feature X", "push:true")
        val expectedAction2: Action = "RunTests" j _i("scope:unit", "module:featureX")
        val expectedAction3: Action = "RequestReview" j _i("reviewer:@teamLead", "crNumber:123")

        val expectedPredictions: Indexed<PredictedAction> = TensorSeries.fromList(listOf(
            expectedAction1 j 0.75,
            expectedAction2 j 0.60,
            expectedAction3 j 0.85
        ))

        val actualPredictions = agent.predictNextActions()
        actualPredictions.shouldHaveSize(3)
        actualPredictions.shouldBe(expectedPredictions) { predA, predB ->
            val actionA = predA.a
            val actionB = predB.a
            val confidenceA = predA.b
            val confidenceB = predB.b
            if (confidenceA != confidenceB) return@shouldBe false
            if (actionA.a != actionB.a) return@shouldBe false
            if (actionA.b.size != actionB.b.size) return@shouldBe false
            var argsMatch = true
            for (i in 0 until actionA.b.size) {
                if (actionA.b.get(i) != actionB.b.get(i)) {
                    argsMatch = false
                    break
                }
            }
            argsMatch
        }
    }

    @Test
    fun `evolveSolution should append feedback to solution`() = runBlocking {
        val (agent, _) = createAgent()
        val initialSolution: Solution = _i("Step 1: Initial code", "Step 2: Basic test")
        val feedback: Feedback = "ReviewComment" j _i("Add more error handling", "Improve test coverage")

        val evolvedSolution = agent.evolveSolution(initialSolution, feedback)

        evolvedSolution.shouldHaveSize(initialSolution.size + 1)
        assertEquals(initialSolution.elementAt(0), evolvedSolution.elementAt(0))
        assertEquals(initialSolution.elementAt(1), evolvedSolution.elementAt(1))

        val feedbackDesc = "${feedback.a}: ${feedback.b.materialize().joinToString(", ", limit = 2)}"
        assertEquals("EVOLVED with feedback ($feedbackDesc)", evolvedSolution.elementAt(initialSolution.size))
    }

    @Test
    fun `learnFromOutcome should return learning update`() = runBlocking {
        val (agent, _) = createAgent()
        val outcome: Outcome = _i("Action executed successfully", "Output: Value=42", "Log: Processing complete.")

        val learningUpdate = agent.learnFromOutcome(outcome)

        val outcomeSummary = outcome.materialize().joinToString(", ", limit = 3) { it.take(50) }
        val expectedLearningUpdate: LearningUpdate = "OutcomeProcessed" j "Learned from outcome: $outcomeSummary"

        learningUpdate.shouldBe(expectedLearningUpdate)
    }

    @Test
    fun `adaptToEnvironment should return agent configuration`() = runBlocking {
        val (agent, _) = createAgent()
        val envContext: EnvironmentContext = _i(
            "OS" j "Windows 11",
            "KotlinVersion" j "1.9.23",
            "Network" j "Available"
        )

        val agentConfig = agent.adaptToEnvironment(envContext)

        val expectedConfigSettings = mutableListOf<Join<String, String>>(
            "AdaptedConfigForOS" j "Windows 11",
            "KotlinTargetVersion" j "1.9.23",
            "DefaultAdaptation" j "EnabledFor-${agent.agentId}"
        )

        agentConfig.shouldHaveSize(expectedConfigSettings.size + 1)
        assertTrue(agentConfig.materialize().containsAll(expectedConfigSettings))
        assertTrue(agentConfig.materialize().any { it.a == "Timestamp" })
    }

    @Test
    fun `gossipAbout should publish serialized payload`() = runBlocking {
        val (agent, testIpfsService) = createAgent()
        val topic = "nexus/test_gossip"
        val payload: GossipPayload = _i(
            "data_point_1" j "value_alpha",
            "metric_A" j "123.45"
        )

        agent.gossipAbout(topic, payload)

        assertEquals(1, testIpfsService.publications.size)
        val publication = testIpfsService.publications.first()
        assertEquals(topic, publication.first)

        // Deserialize and verify payload content
        // This uses the SerializableKeyValuePair defined in DefaultNexusAgent.kt for structure.
        val expectedDeserialized = payload.materialize().map { SerializableKeyValuePair(it.a, it.b) }
        val actualDeserialized = Json.decodeFromString<List<SerializableKeyValuePair>>(publication.second)

        assertEquals(expectedDeserialized.size, actualDeserialized.size)
        expectedDeserialized.forEach { expectedPair ->
            assertTrue(actualDeserialized.contains(expectedPair), "Deserialized payload missing: $expectedPair")
        }
    }

    // --- Tests for K2SCRIPT_EXECUTE ---

    // TODO: Refactor DefaultNexusAgent to allow injection of a ProcessExecutor service for easier mocking of k2script execution.
    // For now, these tests might attempt to run the actual 'k2script' command if it's in PATH.
    // If 'k2script' is not in PATH, these tests will verify error handling for that scenario.

    @Test
    fun `executeAction K2SCRIPT_EXECUTE with valid script path and no args`() = runBlocking {
        val (agent, _) = createAgent()
        // This test assumes 'k2script' might not be found, or if found, 'non_existent_script.kts' won't exist.
        // We are testing the agent's behavior in calling the command.
        val scriptPath = "non_existent_script.kts"
        val action = ActionNames.K2SCRIPT_EXECUTE j _i(scriptPath)

        val outcome = agent.executeAction(action).materialize()

        val outcomeString = outcome.joinToString("\n")

        assertTrue(outcomeString.contains("K2Script execution finished for script: $scriptPath") || outcomeString.contains("Error executing K2Script"),
            "Outcome should indicate execution attempt for $scriptPath. Actual: $outcomeString")

        if (outcomeString.contains("Error executing K2Script: Cannot run program \"k2script\"")) {
            // This means k2script is not in PATH, which is a valid test scenario for command invocation itself
            println("Test Info: k2script command not found in PATH. Agent correctly tried to execute it.")
            assertTrue(outcomeString.contains("Result: Failure (Exception)"))
        } else if (outcomeString.contains("Exit Code: 0")) {
            println("Warning: Test executed with a real 'k2script' and found '$scriptPath' which unexpectedly succeeded.")
             assertTrue(outcomeString.contains("Result: Success"))
        }
         else {
            // k2script was found, but the script itself failed (e.g., file not found by k2script)
            assertTrue(outcomeString.contains("Result: Failure"), "Expected failure for non-existent script if k2script runs. Actual: $outcomeString")
        }
    }

    @Test
    fun `executeAction K2SCRIPT_EXECUTE with script path and args`() = runBlocking {
        val (agent, _) = createAgent()
        val scriptPath = "another_non_existent_script.kts"
        val scriptArgs = listOf("arg1", "--option", "value")
        val action = ActionNames.K2SCRIPT_EXECUTE j _i(scriptPath, *scriptArgs.toTypedArray())

        val outcome = agent.executeAction(action).materialize()
        val outcomeString = outcome.joinToString("\n")

        assertTrue(outcomeString.contains("K2Script execution finished for script: $scriptPath") || outcomeString.contains("Error executing K2Script"),
             "Outcome should indicate execution attempt. Actual: $outcomeString")

        if (outcomeString.contains("Error executing K2Script: Cannot run program \"k2script\"")) {
            println("Test Info: k2script command not found in PATH.")
            assertTrue(outcomeString.contains("Result: Failure (Exception)"))
        } else {
            // k2script found, script failed.
            assertTrue(outcomeString.contains("Args: ${scriptArgs.joinToString(" ")}"), "Args should be logged in outcome. Actual: $outcomeString")
            assertTrue(outcomeString.contains("Result: Failure"), "Expected failure for non-existent script. Actual: $outcomeString")
        }
    }

    @Test
    fun `executeAction K2SCRIPT_EXECUTE with no script path`() = runBlocking {
        val (agent, _) = createAgent()
        val action = ActionNames.K2SCRIPT_EXECUTE j _i() // Empty series for arguments

        val outcome = agent.executeAction(action).materialize()
        val outcomeString = outcome.joinToString("\n")

        assertTrue(outcomeString.contains("Error: K2SCRIPT_EXECUTE action requires at least a script path."))
        assertTrue(outcomeString.contains("Result: Failure"))
    }

    // Note: The existing `executeAction should return sample outcome` test covers the default handler path.
    // My new tests cover the K2SCRIPT_EXECUTE path.
}
