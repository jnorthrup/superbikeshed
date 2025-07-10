@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled // Using Disabled for tests requiring external services or specific setup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.coroutines.runBlocking

class AiFeaturesTest {

    // Helper function to run K2script.main and capture output
    internal fun runK2script(args: Array<String>, inputString: String? = null): String {
        val originalIn = System.`in`
        val originalOut = System.out
        val originalErr = System.err

        val outContent = ByteArrayOutputStream()
        val errContent = ByteArrayOutputStream()
        val printStreamOut = PrintStream(outContent)
        val printStreamErr = PrintStream(errContent)

        try {
            inputString?.let {
                System.setIn(ByteArrayInputStream(it.toByteArray()))
            }
            System.setOut(printStreamOut)
            System.setErr(printStreamErr)

            // K2script.main is a suspend function, so it needs to be called within runBlocking
            runBlocking {
                K2script.main(args)
            }

        } catch (e: Exception) {
            // Catch exceptions that might cause premature exit, like exitProcess
             if (e.message?.contains("exitProcess") == true || e is SecurityException) {
                // Expected if exitProcess is called, or security manager prevents exit
                System.err.println("Caught exitProcess call: ${e.message}")
            } else {
                // Rethrow if it's not an exitProcess related exception
                // e.printStackTrace(printStreamErr) // Print stack trace to captured error stream
                throw e
            }
        } finally {
            System.setIn(originalIn)
            System.setOut(originalOut)
            System.setErr(originalErr)

            // Print captured streams for debugging during test development
            // println("STDOUT:\n" + outContent.toString())
            // println("STDERR:\n" + errContent.toString())
        }
        // For AI tests, we are often interested in stdout. Stderr might contain service logs.
        return outContent.toString().trim() + "\n" + errContent.toString().trim()
    }

    @Test
    @Disabled("Requires a running LiteLLM service and valid API keys")
    fun `test AI script explanation`() {
        val scriptToExplain = "println(\"Hello, AI!\")"
        val args = arrayOf("--ai", "explain this script")

        // Mocking System.in for piped input
        val output = runK2script(args, scriptToExplain)

        assertTrue(output.contains("Script Explanation:"), "Output should contain script explanation header")
        // Further assertions would depend on the actual LLM response,
        // making it hard to make a stable unit test.
        // For now, we check if the process runs and prints something indicative.
        // A more robust test might check for keywords or structure if the LLM is somewhat predictable.
        System.out.println("Output for 'test AI script explanation':\n$output")
    }

    @Test
    @Disabled("Requires a running LiteLLM service and valid API keys")
    fun `test AI script generation`() {
        val prompt = "create a kotlin script that prints numbers from 1 to 3"
        val args = arrayOf("--ai", prompt)

        val output = runK2script(args)

        assertTrue(output.contains("Generated Script:"), "Output should contain generated script header")
        // Similar to explanation, concrete assertions on generated code are difficult.
        // We check for the header.
        // A more advanced test could try to compile and run the generated script.
        System.out.println("Output for 'test AI script generation':\n$output")
    }

    @Test
    fun `test AI flag without prompt`() {
        val args = arrayOf("--ai")
        val output = runK2script(args)
        assertTrue(output.contains("Error: No prompt provided for --ai flag."), "Should show error for missing prompt")
    }

    @Test
    fun `test AI explain without piped script`() {
        val args = arrayOf("--ai", "explain this script")
        // No input piped
        val output = runK2script(args)
        assertTrue(output.contains("Error: No script content piped for explanation."), "Should show error for missing piped script")
    }
}
