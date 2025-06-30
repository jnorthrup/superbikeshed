package nexus.core.dgm.services

import nexus.core.dgm.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import java.io.File

class JvmExternalSolutionProposerTest {

    private lateinit var proposerService: JvmExternalSolutionProposer
    private val processExecutionService: ProcessExecutionService = JvmProcessExecutionService()
    private val testContext = EmptyCoroutineContext
    private lateinit var mockScriptFile: File

    @BeforeTest
    fun setup() {
        // Create a path to the mock script.
        // Assuming the resources are copied to the build output directory.
        // The path might need adjustment based on the actual test execution environment/CWD.
        val scriptResourcePath = "test_py_scripts/mock_solution_proposer.py"
        val classLoader = Thread.currentThread().contextClassLoader
        val resourceUrl = classLoader.getResource(scriptResourcePath)
        assertNotNull(resourceUrl, "Mock Python script not found in resources: $scriptResourcePath. Check build process and CWD.")
        mockScriptFile = File(resourceUrl.toURI())
        assertTrue(mockScriptFile.exists() && mockScriptFile.canExecute(), "Mock script must exist and be executable. Path: ${mockScriptFile.absolutePath}")

        proposerService = JvmExternalSolutionProposer(processExecutionService, mockScriptFile.absolutePath)
    }

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


    @Test
    fun proposeSolutionSuccessfulExecution() = runTest {
        val pythonInterpreter = getPythonInterpreter() // Ensure python is available for the test to run the script
        proposerService = JvmExternalSolutionProposer(processExecutionService, "$pythonInterpreter ${mockScriptFile.absolutePath}")


        val task = DgmTaskForPython(
            taskId = "task_prop_001",
            parentEntryId = "parent_abc",
            benchmarkId = "bench_xyz",
            prompt = "Please refactor this code.",
            codeFiles = listOf(DgmTaskForPythonFile("src/main.kt", "fun main() { println(\"old\") }")),
            langchainAgentConfig = LangchainAgentConfigForPython("test-agent", listOf("tool1"))
        )

        val result = proposerService.propose(task, testContext)

        assertEquals("SUCCESS", result.status, "Status should be SUCCESS. Error: ${result.errorMessage}")
        assertNull(result.errorMessage, "Error message should be null on success.")
        assertNotNull(result.llmOutputLog)
        assertTrue(result.llmOutputLog!!.contains("Mock LLM log for task task_prop_001"))

        assertEquals(2, result.changedFiles.size)
        val firstChangedFile = result.changedFiles.find { it.filePath == "src/main.kt" }
        assertNotNull(firstChangedFile)
        assertTrue(firstChangedFile.content.startsWith("// Mock change by solution_proposer.py"))
        assertTrue(firstChangedFile.content.contains("fun main() { println(\"old\") }"))

        val newFile = result.changedFiles.find { it.filePath == "new_file_from_proposer.kt" }
        assertNotNull(newFile)
        assertEquals("fun addedByProposer() = println(\"Hello from mock proposer!\")", newFile.content)
    }

    @Test
    fun proposeSolutionPythonScriptError() = runTest {
        val pythonInterpreter = getPythonInterpreter()
        proposerService = JvmExternalSolutionProposer(processExecutionService, "$pythonInterpreter ${mockScriptFile.absolutePath}")

        val task = DgmTaskForPython(
            taskId = "task_prop_err_002",
            parentEntryId = "parent_def",
            benchmarkId = "bench_123",
            prompt = "error_please trigger script error", // This will make the mock script return an error
            codeFiles = listOf(DgmTaskForPythonFile("src/error.kt", "content")),
            langchainAgentConfig = null
        )

        val result = proposerService.propose(task, testContext)

        assertEquals("ERROR", result.status)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Simulated error from mock_solution_proposer.py"))
        assertNotNull(result.llmOutputLog)
        assertTrue(result.llmOutputLog!!.contains("Simulating an error based on prompt"))
        assertTrue(result.changedFiles.isEmpty())
    }

    @Test
    fun proposeSolutionNoChangeFromScript() = runTest {
        val pythonInterpreter = getPythonInterpreter()
        proposerService = JvmExternalSolutionProposer(processExecutionService, "$pythonInterpreter ${mockScriptFile.absolutePath}")

        val task = DgmTaskForPython(
            taskId = "task_prop_no_change_003",
            parentEntryId = "parent_ghi",
            benchmarkId = "bench_456",
            prompt = "no_change_please make no changes",
            codeFiles = listOf(DgmTaskForPythonFile("src/nochange.kt", "original content")),
            langchainAgentConfig = null
        )

        val result = proposerService.propose(task, testContext)

        assertEquals("SUCCESS", result.status) // Script itself ran successfully
        assertNull(result.errorMessage)
        assertNotNull(result.llmOutputLog)
        assertTrue(result.llmOutputLog!!.contains("Simulating a no-change scenario"))
        assertTrue(result.changedFiles.isEmpty(), "Changed files should be empty for no-change scenario.")
    }


    @Test
    fun proposeSolutionHandlesJsonDeserializationErrorFromScript() = runTest {
        // This test requires the script to output malformed JSON.
        // For simplicity, we'll test the JvmExternalSolutionProposer's error handling for ProcessExecutionService errors
        // which is more robust to test than making the python script conditionally malform JSON.
        // We'll assume the script path is invalid to trigger a ProcessExecutionService error.

        val invalidScriptPath = "/path/to/nonexistent/script.py"
         // Create a temporary file that is not a valid python script
        val tempScriptDir = File(System.getProperty("java.io.tmpdir"), "dgm_prop_test_${System.currentTimeMillis()}")
        tempScriptDir.mkdirs()
        val tempMalformedScript = File(tempScriptDir, "malformed_script.py")
        tempMalformedScript.writeText("print('This is not JSON!')")
        tempMalformedScript.setExecutable(true)


        val pythonInterpreter = getPythonInterpreter()
        // Use the real JvmProcessExecutionService
        proposerService = JvmExternalSolutionProposer(JvmProcessExecutionService(), "$pythonInterpreter ${tempMalformedScript.absolutePath}")


        val task = DgmTaskForPython(
            taskId = "task_json_err",
            parentEntryId = "parent",
            benchmarkId = "bench",
            prompt = "any",
            codeFiles = emptyList(),
            langchainAgentConfig = null
        )

        val result = proposerService.propose(task, testContext)

        assertEquals("ERROR", result.status)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Failed to deserialize response from JSON") || result.errorMessage!!.contains("Error executing Python solution proposer"), "Error message mismatch: ${result.errorMessage}")
        // Depending on how ProcessExecutionService reports the error vs. JSON deserialization error

        tempMalformedScript.delete()
        tempScriptDir.delete()
    }
}
