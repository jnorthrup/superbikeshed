package borg.nexus.testing

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.io.File

/**
 * POLYGLOT TEST RUNNER - Unified Multi-Language Testing Framework
 * 
 * This integrates the beneficial patterns from DGM (Darwin Godel Machine):
 * - Unified testing harness for multiple programming languages
 * - Polyglot test execution with language-specific commands
 * - Asynchronous test execution with coroutines
 * - Detailed test reporting with output capture
 */

// ═══════════════════════════════════════════════════════════════════════════════
// POLYGLOT TEST RUNNER CORE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Supported programming languages
 */
enum class Language(val displayName: String, val testCommands: Indexed<List<String>>) {
    PYTHON("Python", listOf(
        listOf("pytest", "-rA", "--tb=long")
    ).toIndexed()),
    RUST("Rust", listOf(
        listOf("cargo", "test", "--", "--include-ignored")
    ).toIndexed()),
    GO("Go", listOf(
        listOf("go", "test", "./...")
    ).toIndexed()),
    JAVASCRIPT("JavaScript", listOf(
        listOf("sh", "-c", "set -e"),
        listOf("sh", "-c", "[ ! -e node_modules ] && ln -s /npm-install/node_modules ."),
        listOf("npm", "run", "test")
    ).toIndexed()),
    CPP("C++", listOf(
        listOf("sh", "-c", "set -e"),
        listOf("sh", "-c", "[ ! -d \"build\" ] && mkdir build"),
        listOf("sh", "-c", "cd build"),
        listOf("cmake", ".."),
        listOf("make")
    ).toIndexed()),
    JAVA("Java", listOf(
        listOf("./gradlew", "test")
    ).toIndexed()),
    KOTLIN("Kotlin", listOf(
        listOf("./gradlew", "clean", "test", "--rerun-tasks")
    ).toIndexed())
}

/**
 * Test execution result
 */
data class TestResult(
    val language: Language,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
)

/**
 * Polyglot test runner for executing tests in multiple languages
 */
class PolyglotTestRunner(
    private val workingDirectory: File,
    private val language: Language
) {

    /**
     * Execute tests asynchronously
     */
    suspend fun executeTests(): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var success = false
        var exitCode = -1
        var stdout = ""
        var stderr = ""

        try {
            for (command in language.testCommands.play) {
                val processBuilder = ProcessBuilder(command)
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                
                val process = processBuilder.start()
                
                // Capture output
                val processStdout = async { process.inputStream.bufferedReader().readText() }
                
                exitCode = process.waitFor()
                stdout += processStdout.await()
                
                if (exitCode != 0) {
                    stderr = "Test command failed: ${command.joinToString(" ")}"
                    break // Stop on first failure
                }
            }
            
            success = exitCode == 0
            
        } catch (e: Exception) {
            stderr = e.message ?: "An unexpected error occurred."
            success = false
        }
        
        val durationMs = System.currentTimeMillis() - startTime
        
        TestResult(
            language = language,
            success = success,
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            durationMs = durationMs
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UNIFIED TEST HARNESS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Unified test harness for running tests across all supported languages
 */
object UnifiedTestHarness {
    
    /**
     * Run tests for a specific language
     */
    suspend fun runTestsForLanguage(
        language: Language,
        workingDirectory: File
    ): TestResult {
        val runner = PolyglotTestRunner(workingDirectory, language)
        return runner.executeTests()
    }
    
    /**
     * Run tests for all supported languages in parallel
     */
    suspend fun runAllTests(
        workingDirectory: File
    ): Indexed<TestResult> = coroutineScope {
        val results = Language.values().map { language ->
            async {
                runTestsForLanguage(language, workingDirectory)
            }
        }.map { it.await() }
        
        return@coroutineScope results.toIndexed()
    }
    
    /**
     * Analyze test results using Series operations
     */
    fun analyzeTestResults(results: Indexed<TestResult>): Indexed<String> {
        val analysis = mutableListOf<String>()
        
        val totalTests = results.size
        val passedTests = results.play.count { it.success }
        val failedTests = totalTests - passedTests
        
        analysis.add("Total tests run: $totalTests")
        analysis.add("Passed: $passedTests")
        analysis.add("Failed: $failedTests")
        
        if (failedTests > 0) {
            analysis.add("\nFailed tests:")
            results.play.filter { !it.success }.forEach { result ->
                analysis.add("  - ${result.language.displayName}: exit code ${result.exitCode}")
                analysis.add("    Stdout: ${result.stdout.take(100)}...")
                analysis.add("    Stderr: ${result.stderr.take(100)}...")
            }
        }
        
        return analysis.toIndexed()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
* Convert List to Indexed
*/
fun <T> List<T>.toIndexed(): Indexed<T> = this.size j { i -> this[i] } 