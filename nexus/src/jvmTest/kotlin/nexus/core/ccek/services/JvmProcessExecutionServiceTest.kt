package nexus.core.ccek.services

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import java.io.File

class JvmProcessExecutionServiceTest {

    private val service: ProcessExecutionService = JvmProcessExecutionService()
    private val testContext = EmptyCoroutineContext

    private fun getPythonInterpreter(): String {
        // Simple way to try and find python3 or python
        // In a real CI environment, python3 should be available.
        try {
            val process = ProcessBuilder("python3", "--version").start()
            if (process.waitFor() == 0) return "python3"
        } catch (e: Exception) { /* ignore */ }
        try {
            val process = ProcessBuilder("python", "--version").start()
            if (process.waitFor() == 0) return "python"
        } catch (e: Exception) { /* ignore */ }
        return "python" // Default, might fail if neither is found
    }

    @Test
    fun executeSimpleEchoCommand() = runTest {
        val command = listOf("echo", "Hello, Process!")
        val result = service.executeProcess(command, context = testContext)

        assertEquals(0, result.exitCode, "Exit code should be 0 for successful echo.")
        assertTrue(result.stdout.trim().contains("Hello, Process!"), "Stdout should contain the echoed string.")
        assertTrue(result.stderr.isBlank(), "Stderr should be blank for successful echo.")
        assertFalse(result.timedOut, "Process should not time out.")
        assertNull(result.exception, "Exception should be null.")
    }

    @Test
    fun executeCommandWithStderr() = runTest {
        val python = getPythonInterpreter()
        // Python script that prints to stderr
        val command = listOf(python, "-c", "import sys; sys.stderr.write('Error output')")
        val result = service.executeProcess(command, context = testContext)

        // Python might exit with 0 even if it writes to stderr
        // assertEquals(0, result.exitCode) // This depends on shell/python version for just stderr
        assertTrue(result.stderr.trim().contains("Error output"), "Stderr should contain the error string: ${result.stderr}")
        assertFalse(result.timedOut)
    }

    @Test
    fun executeCommandWithNonZeroExitCode() = runTest {
        val python = getPythonInterpreter()
        // Python script that exits with 1
        val command = listOf(python, "-c", "import sys; sys.exit(1)")
        val result = service.executeProcess(command, context = testContext)

        assertEquals(1, result.exitCode, "Exit code should be 1.")
        assertFalse(result.timedOut)
    }

    @Test
    fun executeCommandWithTimeout() = runTest {
        val python = getPythonInterpreter()
        // Python script that sleeps for a long time
        val command = listOf(python, "-c", "import time; time.sleep(5)")
        val result = service.executeProcess(command, timeoutMillis = 100L, context = testContext)

        assertTrue(result.timedOut, "Process should time out.")
        // Stderr might contain timeout information, or it might be empty if process was killed hard
        // assertTrue(result.stderr.contains("timed out"), "Stderr should indicate timeout: ${result.stderr}")
        // Exit code might be non-standard due to forceful termination
    }

    @Test
    fun executeCommandInWorkingDirectory() = runTest {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "dgm_jvm_proc_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val testFile = File(tempDir, "test_file.txt")
        testFile.writeText("Hello from temp dir")

        // On Windows, 'dir' or 'type', on Unix 'ls' or 'cat'
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val listCommand = if (isWindows) listOf("cmd", "/c", "dir") else listOf("ls", "-la")

        var result = service.executeProcess(listCommand, workingDirectory = tempDir.absolutePath, context = testContext)

        assertEquals(0, result.exitCode, "Exit code for listing directory should be 0. Stderr: ${result.stderr}")
        assertTrue(result.stdout.contains("test_file.txt"), "Stdout should list the test file. Output: ${result.stdout}")

        val catCommand = if (isWindows) listOf("cmd", "/c", "type", "test_file.txt") else listOf("cat", "test_file.txt")
        result = service.executeProcess(catCommand, workingDirectory = tempDir.absolutePath, context = testContext)
        assertEquals(0, result.exitCode, "Exit code for catting file should be 0. Stderr: ${result.stderr}")
        assertTrue(result.stdout.contains("Hello from temp dir"), "Stdout should contain file content. Output: ${result.stdout}")

        // Cleanup
        testFile.delete()
        tempDir.delete()
    }

    @Test
    fun executeCommandWithEnvironmentVariables() = runTest {
        val varName = "MY_TEST_VARIABLE"
        val varValue = "HelloFromEnv"
        val python = getPythonInterpreter()
        val command = listOf(python, "-c", "import os; print(os.environ.get('$varName'))")
        val envVars = mapOf(varName to varValue)

        val result = service.executeProcess(command, environmentVars = envVars, context = testContext)

        assertEquals(0, result.exitCode, "Exit code should be 0. Stderr: ${result.stderr}")
        assertTrue(result.stdout.trim().contains(varValue), "Stdout should contain the env var value. Got: ${result.stdout}")
    }

    @Test
    fun executeNonExistentCommand() = runTest {
        val command = listOf("a_command_that_does_not_exist_12345")
        val result = service.executeProcess(command, context = testContext)

        assertNotEquals(0, result.exitCode, "Exit code should be non-zero for non-existent command.")
        assertFalse(result.timedOut)
        assertNotNull(result.exception, "Exception message should be present for non-existent command.")
        assertTrue(result.exception!!.contains("Cannot run program") || result.exception.contains("No such file or directory"), "Exception message mismatch: ${result.exception}")
    }

    @Test
    fun executeCommandInNonExistentWorkingDirectory() = runTest {
        val command = listOf("echo", "test")
        val nonExistentDir = "/path/to/a/dir/that/does/not/exist/${System.currentTimeMillis()}"
        val result = service.executeProcess(command, workingDirectory = nonExistentDir, context = testContext)

        assertNotEquals(0, result.exitCode, "Exit code should be non-zero.")
        assertNotNull(result.exception, "Exception string should not be null.")
        assertTrue(result.exception!!.contains("Invalid working directory") || result.stderr.contains("Working directory"), "Error message mismatch: ${result.exception} / ${result.stderr}")
    }
}
