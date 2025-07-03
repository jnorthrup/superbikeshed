package nexus.core.dgm.services

import nexus.core.dgm.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest

// Test-specific actual implementation for ExternalBenchmarkExecutor
internal actual interface ExternalBenchmarkExecutor {
    // Companion object for actual interface is not how Key is typically accessed.
    // The Key is on the expect interface. This actual just provides the method.
    actual suspend fun execute(request: BenchmarkInvocation, context: CCEKContext): BenchmarkResultFromPython {
        // This is the default stub implementation for the actual interface in commonTest
        // It will be overridden by MockExternalBenchmarkExecutor's behavior if used.
        println("Warning: Default stub for ExternalBenchmarkExecutor.execute called in commonTest")
        return BenchmarkResultFromPython(
            request.benchmarkId, "PASSED", emptyList(), "Default stub response", null
        )
    }
}

class MockExternalBenchmarkExecutor : ExternalBenchmarkExecutor {
    var lastRequest: BenchmarkInvocation? = null
    var responseToReturn: BenchmarkResultFromPython? = null
    var executionShouldThrow: Exception? = null

    override suspend fun execute(request: BenchmarkInvocation, context: CCEKContext): BenchmarkResultFromPython {
        lastRequest = request
        executionShouldThrow?.let { throw it }
        return responseToReturn ?: BenchmarkResultFromPython(
            request.benchmarkId, "ERROR", emptyList(), null, "Mock response not set"
        )
    }

    fun reset() {
        lastRequest = null
        responseToReturn = null
        executionShouldThrow = null
    }
}

class RemoteBenchmarkValidationServiceTest {

    private lateinit var validationService: RemoteBenchmarkValidationService
    private val mockExecutor = MockExternalBenchmarkExecutor()
    private val testContext = EmptyCoroutineContext

    @BeforeTest
    fun setup() {
        mockExecutor.reset()
        validationService = RemoteBenchmarkValidationService(mockExecutor)
    }

    private fun createDummyImprovementCandidate(): ImprovementCandidate {
        val task = DgmTask(
            Join(TaskId("task1"), ArchiveEntryId("parent1")),
            Join(BenchmarkId("bench1"), Indexed.empty<String>())
        )
        val codeSnapshot = Indexed.of(Join<FilePath, FileContent>("file.kt", "dummy content"))
        return ImprovementCandidate(
            Join(task, codeSnapshot),
            Join(ProposerId("proposer1"), Indexed.of("rationale"))
        )
    }

    @Test
    fun validateSolutionConstructsRequestCorrectly() = runTest {
        val candidate = createDummyImprovementCandidate()
        val workspacePath = "/test/workspace"
        val benchmarkId = "test_benchmark_001"
        val timeoutFromInvocation = 300 // Default in RemoteBenchmarkValidationService

        mockExecutor.responseToReturn = BenchmarkResultFromPython(
            benchmarkId, "PASSED", emptyList(), "Success", null
        )

        validationService.validateSolution(candidate, workspacePath, benchmarkId, testContext)

        assertNotNull(mockExecutor.lastRequest)
        assertEquals(benchmarkId, mockExecutor.lastRequest?.benchmarkId)
        assertEquals(workspacePath, mockExecutor.lastRequest?.workspacePath)
        assertEquals(timeoutFromInvocation, mockExecutor.lastRequest?.timeoutSeconds)
    }

    @Test
    fun validateSolutionConvertsSuccessfulPythonResult() = runTest {
        val benchmarkId = "success_test"
        val pythonResult = BenchmarkResultFromPython(
            benchmarkId = benchmarkId,
            status = "PASSED",
            scores = listOf(
                BenchmarkScoreFromPython("accuracy", 0.95),
                BenchmarkScoreFromPython("time_ms", 1200.0)
            ),
            logOutput = "All tests passed.",
            errorDetails = null
        )
        mockExecutor.responseToReturn = pythonResult

        val candidate = createDummyImprovementCandidate()
        val validationResult = validationService.validateSolution(candidate, "/path", benchmarkId, testContext)

        assertEquals("PASSED", validationResult.a.a) // Status
        assertEquals("All tests passed.", validationResult.b) // LogOutput

        val scores = validationResult.a.b
        assertEquals(2, scores.size)
        assertTrue(scores.any { it.a == "accuracy" && it.b == 0.95 })
        assertTrue(scores.any { it.a == "time_ms" && it.b == 1200.0 })
    }

    @Test
    fun validateSolutionConvertsFailedPythonResult() = runTest {
        val benchmarkId = "failure_test"
        val pythonResult = BenchmarkResultFromPython(
            benchmarkId = benchmarkId,
            status = "FAILED_TESTS",
            scores = listOf(BenchmarkScoreFromPython("accuracy", 0.50)),
            logOutput = "Some tests failed.",
            errorDetails = "AssertionError in test_example"
        )
        mockExecutor.responseToReturn = pythonResult

        val candidate = createDummyImprovementCandidate()
        val validationResult = validationService.validateSolution(candidate, "/path", benchmarkId, testContext)

        assertEquals("FAILED_TESTS", validationResult.a.a) // Status
        // Log output prioritizes logOutput, then errorDetails if logOutput is null
        assertEquals("Some tests failed.", validationResult.b)

        val scores = validationResult.a.b
        assertEquals(1, scores.size)
        assertTrue(scores.any { it.a == "accuracy" && it.b == 0.50 })
    }

    @Test
    fun validateSolutionHandlesErrorInPythonResult() = runTest {
        val benchmarkId = "error_test"
         val pythonResult = BenchmarkResultFromPython(
            benchmarkId = benchmarkId,
            status = "ERROR",
            scores = emptyList(),
            logOutput = null, // Important: logOutput is null
            errorDetails = "Python script crashed due to MemoryError"
        )
        mockExecutor.responseToReturn = pythonResult

        val candidate = createDummyImprovementCandidate()
        val validationResult = validationService.validateSolution(candidate, "/path", benchmarkId, testContext)

        assertEquals("ERROR", validationResult.a.a)
        assertEquals("Python script crashed due to MemoryError", validationResult.b) // LogOutput should be errorDetails
        assertTrue(validationResult.a.b.isEmpty()) // No scores
    }


    @Test
    fun validateSolutionHandlesExecutorException() = runTest {
        // This test is more about how JvmExternalBenchmarkExecutor (if it were real) would populate ProcessResult
        // For the mock, we simulate an exception being caught by RemoteBenchmarkValidationService if executor throws.
        // However, the current RemoteBenchmarkValidationService doesn't catch exceptions from executor.execute,
        // the executor itself (like JvmExternalBenchmarkExecutor) is expected to catch and return an ERROR ProcessResult.
        // So, we test that RemoteBenchmarkValidationService correctly converts an ERROR *BenchmarkResultFromPython*.

        val benchmarkId = "exception_scenario"
        mockExecutor.responseToReturn = BenchmarkResultFromPython(
            benchmarkId = benchmarkId,
            status = "ERROR", // This is what a real executor would set if it caught an internal exception
            scores = emptyList(),
            logOutput = "Executor failed to run benchmark.",
            errorDetails = "Underlying process error: File not found"
        )

        val candidate = createDummyImprovementCandidate()
        val validationResult = validationService.validateSolution(candidate, "/path", benchmarkId, testContext)

        assertEquals("ERROR", validationResult.a.a)
        assertEquals("Executor failed to run benchmark.", validationResult.b)
    }
}
