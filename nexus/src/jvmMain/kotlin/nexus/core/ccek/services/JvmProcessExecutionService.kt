package nexus.core.ccek.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

actual interface ProcessExecutionService {
    actual suspend fun executeProcess(
        command: List<String>,
        workingDirectory: String?,
        timeoutMillis: Long,
        environmentVars: Map<String, String>,
        context: CCEKContext
    ): ProcessResult {
        return withContext(Dispatchers.IO) { // Ensure non-blocking IO operations
            try {
                val processBuilder = ProcessBuilder(command)

                workingDirectory?.let {
                    val dir = File(it)
                    if (dir.exists() && dir.isDirectory) {
                        processBuilder.directory(dir)
                    } else {
                        return@withContext ProcessResult(-1, "", "Working directory '$it' does not exist or is not a directory.", false, "Invalid working directory")
                    }
                }

                processBuilder.environment().putAll(environmentVars)

                var stdout = ""
                var stderr = ""
                var exitCode = -1 // Default for error or timeout
                var timedOut = false
                var exceptionMessage: String? = null

                val process = withTimeoutOrNull(timeoutMillis) {
                    try {
                        val p = processBuilder.start()
                        // Read streams concurrently or ensure they don't block each other
                        // For simplicity, using separate threads for stream reading might be an option
                        // but can be complex. Using built-in methods for now.

                        // It's crucial to consume both streams to prevent process deadlock
                        val stdoutReader = p.inputStream.bufferedReader()
                        val stderrReader = p.errorStream.bufferedReader()

                        val stdoutFuture = kotlinx.coroutines.async { stdoutReader.readText() }
                        val stderrFuture = kotlinx.coroutines.async { stderrReader.readText() }

                        p.waitFor(timeoutMillis, TimeUnit.MILLISECONDS) // Re-check timeout inside the block

                        if (p.isAlive) {
                            p.destroyForcibly()
                            timedOut = true
                            stderr = "Process timed out after $timeoutMillis ms and was forcibly destroyed."
                        } else {
                            exitCode = p.exitValue()
                        }

                        stdout = stdoutFuture.await()
                        stderr = stderrFuture.await() + (if(timedOut) stderr else "")


                    } catch (e: Exception) {
                        exceptionMessage = e.message ?: e.javaClass.simpleName
                        stderr += "\nException during process execution: ${e.message}"
                        // exitCode remains -1 or some other error indicator
                    }
                }

                if (process == null) { // This means withTimeoutOrNull itself timed out
                    timedOut = true
                    stderr = "Process timed out after $timeoutMillis ms (outer timeout)."
                    // Attempt to clean up the process if ProcessBuilder started it and it's findable, though harder here.
                }

                ProcessResult(exitCode, stdout, stderr, timedOut, exceptionMessage)

            } catch (e: Exception) { // Catch exceptions from ProcessBuilder setup etc.
                 ProcessResult(-1, "", e.message ?: e.javaClass.simpleName, false, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    actual companion object Key : CoroutineContext.Key<ProcessExecutionService> {
        // For JVM, we can provide a default instance if desired, or expect it to be injected.
        // For now, just the Key. The actual service instance would be created and put into context elsewhere.
    }
}

// A concrete class that can be instantiated and placed into CCEKContext
class JvmProcessExecutionService : ProcessExecutionService {
    // Delegate to the actual implementation provided in the actual interface companion
    // This structure is a bit specific due to the 'actual interface' pattern.
    // A more common pattern would be 'expect class' and 'actual class'.
    // Given the current 'expect interface', we make this concrete class use the actual logic.

    private val actualImpl = object : ProcessExecutionService {} // Create an anonymous object that gets the actual methods

    override suspend fun executeProcess(
        command: List<String>,
        workingDirectory: String?,
        timeoutMillis: Long,
        environmentVars: Map<String, String>,
        context: CCEKContext
    ): ProcessResult {
        return actualImpl.executeProcess(command, workingDirectory, timeoutMillis, environmentVars, context)
    }
}
