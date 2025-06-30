package nexus.core.dgm.services

import nexus.core.dgm.*
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest

// Test-specific actual implementation for ExternalSolutionProposer
internal actual interface ExternalSolutionProposer {
    actual suspend fun propose(request: DgmTaskForPython, context: CCEKContext): ProposedChangesFromPython {
        // Default stub for commonTest, will be overridden by MockExternalSolutionProposer
        println("Warning: Default stub for ExternalSolutionProposer.propose called in commonTest")
        return ProposedChangesFromPython(
            request.taskId, emptyList(), "Default stub response", "SUCCESS", null
        )
    }
}

class MockExternalSolutionProposer : ExternalSolutionProposer {
    var lastRequest: DgmTaskForPython? = null
    var responseToReturn: ProposedChangesFromPython? = null
    var executionShouldThrow: Exception? = null

    override suspend fun propose(request: DgmTaskForPython, context: CCEKContext): ProposedChangesFromPython {
        lastRequest = request
        executionShouldThrow?.let { throw it }
        return responseToReturn ?: ProposedChangesFromPython(
            request.taskId, emptyList(), null, "ERROR", "Mock response not set"
        )
    }

    fun reset() {
        lastRequest = null
        responseToReturn = null
        executionShouldThrow = null
    }
}

class RemoteSolutionProposerServiceTest {

    private lateinit var proposerService: RemoteSolutionProposerService
    private val mockProposer = MockExternalSolutionProposer()
    private val testContext = EmptyCoroutineContext

    @BeforeTest
    fun setup() {
        mockProposer.reset()
        proposerService = RemoteSolutionProposerService(mockProposer)
    }

    private fun createDummyDgmTask(taskIdStr: String = "task_test_123"): DgmTask {
        return DgmTask(
            Join(TaskId(taskIdStr), ArchiveEntryId("parent_abc")),
            Join(BenchmarkId("benchmark_def"), Series.of("param1", "param2"))
        )
    }

    private fun createDummyCodeSnapshot(): CodeSnapshot {
        return Series.of(
            Join<FilePath, FileContent>("src/FileA.kt", "package com.example\n\nfun greet() = \"Hello\""),
            Join<FilePath, FileContent>("README.md", "# My Project")
        )
    }

    @Test
    fun proposeSolutionConstructsRequestCorrectly() = runTest {
        val task = createDummyDgmTask()
        val parentCode = createDummyCodeSnapshot()
        val expectedPromptContains = "Improve code for task ${task.a.a}" // From RemoteSolutionProposerService logic

        mockProposer.responseToReturn = ProposedChangesFromPython(
            task.a.a, emptyList(), "Success", "SUCCESS", null
        )

        proposerService.proposeSolution(task, parentCode, testContext)

        assertNotNull(mockProposer.lastRequest)
        val pyRequest = mockProposer.lastRequest!!
        assertEquals(task.a.a, pyRequest.taskId)
        assertEquals(task.a.b, pyRequest.parentEntryId)
        assertEquals(task.b.a, pyRequest.benchmarkId)
        assertTrue(pyRequest.prompt.contains(expectedPromptContains), "Prompt content mismatch")

        assertEquals(parentCode.size, pyRequest.codeFiles.size)
        assertEquals(parentCode.first().a, pyRequest.codeFiles.first().filePath)
        assertEquals(parentCode.first().b, pyRequest.codeFiles.first().content)

        assertNotNull(pyRequest.langchainAgentConfig) // Default config is applied
        assertEquals("code-refactor-agent", pyRequest.langchainAgentConfig?.agentName)
    }

    @Test
    fun proposeSolutionConvertsSuccessfulPythonResult() = runTest {
        val task = createDummyDgmTask()
        val parentCode = createDummyCodeSnapshot()
        val pythonResult = ProposedChangesFromPython(
            taskId = task.a.a,
            changedFiles = listOf(
                ProposedChangeFileFromPython("src/FileA.kt", "package com.example\n\nfun greet() = \"Hello World\" // Updated"),
                ProposedChangeFileFromPython("NEW_FILE.md", "## New Documentation")
            ),
            llmOutputLog = "LLM successfully proposed changes.",
            status = "SUCCESS",
            errorMessage = null
        )
        mockProposer.responseToReturn = pythonResult

        val improvementCandidate = proposerService.proposeSolution(task, parentCode, testContext)

        assertEquals(task, improvementCandidate.a.a) // Original DgmTask

        val proposedSnapshot = improvementCandidate.a.b
        assertEquals(2, proposedSnapshot.size)
        assertTrue(proposedSnapshot.any { it.a == "src/FileA.kt" && it.b.contains("Hello World") })
        assertTrue(proposedSnapshot.any { it.a == "NEW_FILE.md" && it.b.contains("New Documentation") })

        assertEquals(pythonResult.langchainAgentConfig?.agentName ?: "unknown_python_agent", improvementCandidate.b.a) // ProposerId
        assertEquals(Series.of("LLM successfully proposed changes."), improvementCandidate.b.b) // Rationale
    }

    @Test
    fun proposeSolutionConvertsErrorPythonResult() = runTest {
        val task = createDummyDgmTask()
        val parentCode = createDummyCodeSnapshot()
        val pythonResult = ProposedChangesFromPython(
            taskId = task.a.a,
            changedFiles = emptyList(),
            llmOutputLog = "LLM failed to generate a response.",
            status = "ERROR",
            errorMessage = "Simulated LLM API error: Quota exceeded."
        )
        mockProposer.responseToReturn = pythonResult

        val improvementCandidate = proposerService.proposeSolution(task, parentCode, testContext)

        assertEquals(task, improvementCandidate.a.a)
        assertTrue(improvementCandidate.a.b.isEmpty(), "Proposed snapshot should be empty on error.")
        assertEquals("llm_error_handler", improvementCandidate.b.a, "ProposerId should indicate error.")

        val rationale = improvementCandidate.b.b
        assertTrue(rationale.any { it.contains("Proposal failed: Simulated LLM API error: Quota exceeded.") })
        assertTrue(rationale.any { it.contains("LLM failed to generate a response.") })
    }

    @Test
    fun proposeSolutionHandlesEmptyCodeSnapshotInput() = runTest {
        val task = createDummyDgmTask()
        val emptyParentCode = Series.empty<Join<FilePath, FileContent>>()

        mockProposer.responseToReturn = ProposedChangesFromPython(
            task.a.a,
            listOf(ProposedChangeFileFromPython("generated.kt", "fun new() = {}")),
            "Generated from empty.", "SUCCESS", null
        )

        val candidate = proposerService.proposeSolution(task, emptyParentCode, testContext)
        assertNotNull(mockProposer.lastRequest)
        assertTrue(mockProposer.lastRequest!!.codeFiles.isEmpty())

        assertEquals(1, candidate.a.b.size) // One new file proposed
        assertEquals("generated.kt", candidate.a.b.first().a)
    }
}
