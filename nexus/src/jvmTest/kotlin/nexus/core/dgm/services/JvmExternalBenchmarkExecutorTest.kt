package nexus.core.dgm.services

import nexus.core.dgm.BenchmarkId
import nexus.core.dgm.FilePath
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import java.io.File

class JvmExternalBenchmarkExecutorTest {

    private lateinit var benchmarkExecutor: JvmExternalBenchmarkExecutor
    private val processExecutionService: ProcessExecutionService = JvmProcessExecutionService()
    private val testContext = EmptyCoroutineContext
    private lateinit var mockScriptFile: File
    private lateinit var tempWorkspaceDir: File


    private fun getPythonInterpreter(): String {
        try {
            val process = ProcessBuilder("python3", "--version").start()
            if (process.waitFor() == 0) return "python3"
        } catch (e: Exception) { /* ignore */ }
        try {
            val process = ProcessBuilder("python", "--version").start()
            if (process.waitFor() == 0) return "python"
        } catch (e: Exception) { /* ignore */ }
        fail("Python interpreter (python3 or python) not found on system PATH.")
    }

    @BeforeTest
    fun setup() {
        val scriptResourcePath = "test_py_scripts/mock_benchmark_runner.py"
        val classLoader = Thread.currentThread().contextClassLoader
        val resourceUrl = classLoader.getResource(scriptResourcePath)
        assertNotNull(resourceUrl, "Mock Python script not found in resources: $scriptResourcePath.")
        mockScriptFile = File(resourceUrl.toURI())
        assertTrue(mockScriptFile.exists(), "Mock script must exist. Path: ${mockScriptFile.absolutePath}")
        // Execution flag set by ProcessBuilder when calling python interpreter

        val pythonInterpreter = getPythonInterpreter()
        benchmarkExecutor = JvmExternalBenchmarkExecutor(processExecutionService, "$pythonInterpreter ${mockScriptFile.absolutePath}")

        // Setup a temporary workspace directory for tests that need it
        tempWorkspaceDir = File(System.getProperty("java.io.tmpdir"), "dgm_benchmark_ws_test_${System.currentTimeMillis()}")
        tempWorkspaceDir.mkdirs()
        assertTrue(tempWorkspaceDir.exists() && tempWorkspaceDir.isDirectory)
    }

    @AfterTest
    fun tearDown() {
        // Clean up the temporary workspace directory
        if (::tempWorkspaceDir.isInitialized && tempWorkspaceDir.exists()) {
            tempWorkspaceDir.deleteRecursively()
        }
    }

    @Test
    fun executeBenchmarkSuccessful() = runTest {
        val request = BenchmarkInvocation(
            benchmarkId = "bench_success_001",
            workspacePath = tempWorkspaceDir.absolutePath,
            timeoutSeconds = 60
        )

        val result = benchmarkExecutor.execute(request, testContext)

        assertEquals("PASSED", result.status, "Status should be PASSED. Error: ${result.errorDetails} Log: ${result.logOutput}")
        assertNull(result.errorDetails)
        assertNotNull(result.logOutput)
        assertTrue(result.logOutput!!.contains("Mock benchmark run for bench_success_001 in workspace ${tempWorkspaceDir.absolutePath} completed successfully."))

        assertEquals(2, result.scores.size)
        val scoresMap = result.scores.associate { it.metricName to it.value }
        assertEquals(1.0, scoresMap["pass_rate"])
        assertEquals(123.45, scoresMap["execution_time_ms"])
    }

    @Test
    fun executeBenchmarkPythonScriptError() = runTest {
        val request = BenchmarkInvocation(
            benchmarkId = "bench_error_please_002", // Triggers error in mock script
            workspacePath = tempWorkspaceDir.absolutePath,
            timeoutSeconds = 60
        )

        val result = benchmarkExecutor.execute(request, testContext)

        assertEquals("ERROR", result.status)
        assertNotNull(result.errorDetails)
        assertTrue(result.errorDetails!!.contains("Simulated error from mock_benchmark_runner.py due to benchmarkId"))
        assertNotNull(result.logOutput) // Log output might still contain info
        assertTrue(result.logOutput!!.contains("Simulating a benchmark script error."))
        assertTrue(result.scores.isEmpty())
    }

    @Test
    fun executeBenchmarkTriggeredFailureFromFile() = runTest {
        // Create the trigger file in the workspace
        val triggerFile = File(tempWorkspaceDir, "trigger_failure.txt")
        triggerFile.writeText("This file causes the mock benchmark to fail.")
        assertTrue(triggerFile.exists())

        val request = BenchmarkInvocation(
            benchmarkId = "bench_trigger_fail_003",
            workspacePath = tempWorkspaceDir.absolutePath,
            timeoutSeconds = 60
        )

        val result = benchmarkExecutor.execute(request, testContext)

        assertEquals("FAILED_TESTS", result.status)
        assertNotNull(result.errorDetails)
        assertTrue(result.errorDetails!!.contains("Trigger file indicated failure."))
        assertNotNull(result.logOutput)
        assertTrue(result.logOutput!!.contains("Benchmark failed because trigger file"))

        // Cleanup the trigger file
        triggerFile.delete()
    }

    @Test
    fun executeBenchmarkInvalidWorkspacePath() = runTest {
        val invalidWorkspacePath = "/path/to/some/nonexistent/dir/${System.currentTimeMillis()}"
        val request = BenchmarkInvocation(
            benchmarkId = "bench_invalid_ws_004",
            workspacePath = invalidWorkspacePath,
            timeoutSeconds = 60
        )

        val result = benchmarkExecutor.execute(request, testContext)

        assertEquals("ERROR", result.status)
        assertNotNull(result.errorDetails)
        assertTrue(
            result.errorDetails!!.contains("Workspace directory '$invalidWorkspacePath' does not exist or is not a directory."),
            "Error message mismatch: ${result.errorDetails}"
        )
    }

    @Test
    fun executeBenchmarkScriptReturnsMalformedJson() = runTest {
        // To test this, we need a script that outputs malformed JSON.
        // We'll create a temporary script for this purpose.
        val tempScriptDir = File(System.getProperty("java.io.tmpdir"), "dgm_bench_json_err_${System.currentTimeMillis()}")
        tempScriptDir.mkdirs()
        val malformedScript = File(tempScriptDir, "malformed_benchmark_script.py")
        malformedScript.writeText("""
            import sys
            print("{'this is not valid json': ") # Intentionally malformed
            sys.exit(0) # Script itself exits successfully
        """.trimIndent())

        val pythonInterpreter = getPythonInterpreter()
        val tempExecutor = JvmExternalBenchmarkExecutor(processExecutionService, "$pythonInterpreter ${malformedScript.absolutePath}")

        val request = BenchmarkInvocation(
            benchmarkId = "bench_malformed_json_005",
            workspacePath = tempWorkspaceDir.absolutePath, // A valid workspace
            timeoutSeconds = 10
        )

        val result = tempExecutor.execute(request, testContext)

        assertEquals("ERROR", result.status)
        assertNotNull(result.errorDetails)
        assertTrue(
            result.errorDetails!!.contains("Failed to deserialize benchmark response from JSON"),
            "Error message mismatch: ${result.errorDetails}"
        )
        assertTrue(
            result.errorDetails!!.contains("Raw output: {'this is not valid json': "),
            "Raw output missing: ${result.errorDetails}"
        )

        malformedScript.delete()
        tempScriptDir.delete()
    }
}
