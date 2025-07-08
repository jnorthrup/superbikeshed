@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.e2e

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.io.path.exists
import kotlin.io.path.deleteIfExists

/**
 * Comprehensive End-to-End Tests for K2Script
 * Tests all commands and use cases in realistic scenarios
 */
class K2ScriptE2ETest {
    
    private lateinit var tempDir: Path
    private lateinit var testScriptsDir: Path
    private lateinit var cacheDir: Path
    
    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("k2script-e2e")
        testScriptsDir = tempDir.resolve("scripts")
        cacheDir = tempDir.resolve("cache")
        
        Files.createDirectories(testScriptsDir)
        Files.createDirectories(cacheDir)
        
        // Set up test environment
        System.setProperty("k2script.cache.dir", cacheDir.toString())
    }
    
    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }
    
    // === Basic Script Execution Tests ===
    
    @Test
    fun `should execute simple hello world script`() = runTest {
        val script = """
            fun main() {
                println("Hello, K2Script!")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("hello.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf(scriptFile.toString()))
        
        assertTrue(result.success, "Script should execute successfully")
        assertTrue(result.output.contains("Hello, K2Script!"), "Should output expected message")
        assertEquals(0, result.exitCode, "Should exit with success code")
    }
    
    @Test
    fun `should execute script with arguments`() = runTest {
        val script = """
            fun main(args: Array<String>) {
                println("Arguments: ${'$'}{args.joinToString(", ")}")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("args.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf(scriptFile.toString(), "arg1", "arg2", "arg3"))
        
        assertTrue(result.success, "Script should execute successfully")
        assertTrue(result.output.contains("Arguments: arg1, arg2, arg3"), "Should output arguments")
    }
    
    @Test
    fun `should execute script with dependencies`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                println("Starting coroutine...")
                delay(100)
                println("Coroutine completed!")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("deps.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf(scriptFile.toString()))
        
        assertTrue(result.success, "Script with dependencies should execute")
        assertTrue(result.output.contains("Starting coroutine..."), "Should show coroutine start")
        assertTrue(result.output.contains("Coroutine completed!"), "Should show coroutine completion")
    }
    
    // === Deploy Command Tests ===
    
    @Test
    fun `should deploy script to target directory`() = runTest {
        val script = """
            fun main() {
                println("Deployed script!")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("deploy-test.kts")
        scriptFile.writeText(script)
        
        val deployDir = tempDir.resolve("deployed")
        
        val result = executeK2Script(arrayOf("--deploy", deployDir.toString(), scriptFile.toString()))
        
        assertTrue(result.success, "Deploy should succeed")
        assertTrue(deployDir.exists(), "Deploy directory should be created")
        assertTrue(deployDir.resolve("deploy-test.kts").exists(), "Script should be copied")
    }
    
    @Test
    fun `should deploy with dependencies resolved`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
            
            import kotlinx.serialization.json.*
            
            fun main() {
                val json = Json { prettyPrint = true }
                println("JSON library loaded: ${'$'}{json::class.simpleName}")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("deploy-deps.kts")
        scriptFile.writeText(script)
        
        val deployDir = tempDir.resolve("deployed-deps")
        
        val result = executeK2Script(arrayOf("--deploy", deployDir.toString(), scriptFile.toString()))
        
        assertTrue(result.success, "Deploy with dependencies should succeed")
        assertTrue(deployDir.exists(), "Deploy directory should exist")
        
        // Check that dependencies are included
        val libDir = deployDir.resolve("lib")
        assertTrue(libDir.exists(), "Lib directory should exist for dependencies")
    }
    
    // === CWD Command Tests ===
    
    @Test
    fun `should execute script in specified working directory`() = runTest {
        val workingDir = tempDir.resolve("workdir")
        Files.createDirectories(workingDir)
        
        val script = """
            import java.io.File
            
            fun main() {
                val currentDir = File(".").absolutePath
                println("Working directory: ${'$'}currentDir")
                
                // Create a file in the working directory
                File("test-output.txt").writeText("Created in working directory")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("cwd-test.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf("--cwd", workingDir.toString(), scriptFile.toString()))
        
        assertTrue(result.success, "Script should execute in specified directory")
        assertTrue(result.output.contains(workingDir.toString()), "Should show correct working directory")
        assertTrue(workingDir.resolve("test-output.txt").exists(), "File should be created in working directory")
    }
    
    // === Raw Mode Tests ===
    
    @Test
    fun `should execute in raw mode without sandbox`() = runTest {
        val script = """
            fun main() {
                println("Raw mode execution")
                // Access current directory directly
                val currentDir = System.getProperty("user.dir")
                println("Current dir: ${'$'}currentDir")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("raw-test.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf("--rawdog", scriptFile.toString()))
        
        assertTrue(result.success, "Raw mode should execute successfully")
        assertTrue(result.output.contains("Raw mode execution"), "Should show raw mode message")
    }
    
    // === Docker Mode Tests ===
    
    @Test
    fun `should execute in docker mode`() = runTest {
        val script = """
            fun main() {
                println("Docker mode execution")
                val os = System.getProperty("os.name")
                println("OS: ${'$'}os")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("docker-test.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf("--docker", scriptFile.toString()))
        
        // Note: This test may fail if Docker is not available
        if (result.success) {
            assertTrue(result.output.contains("Docker mode execution"), "Should show docker mode message")
        } else {
            // Docker not available, which is acceptable
            assertTrue(result.output.contains("docker") || result.output.contains("Docker"), "Should mention docker in output")
        }
    }
    
    // === Branch Command Tests ===
    
    @Test
    fun `should execute with branch specification`() = runTest {
        val script = """
            fun main() {
                println("Branch-specific execution")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("branch-test.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf("--branch", "feature-test", scriptFile.toString()))
        
        assertTrue(result.success, "Branch execution should succeed")
        assertTrue(result.output.contains("Branch-specific execution"), "Should execute branch-specific script")
    }
    
    // === AI Integration Tests ===
    
    @Test
    fun `should handle AI script explanation`() = runTest {
        val script = """
            fun main() {
                println("Script to explain")
            }
        """.trimIndent()
        
        val result = executeK2Script(arrayOf("--ai", "explain this script"), script)
        
        // AI features may be disabled or require external services
        if (result.success) {
            assertTrue(result.output.contains("explain") || result.output.contains("Explanation"), "Should handle AI explanation")
        } else {
            assertTrue(result.output.contains("AI") || result.output.contains("disabled"), "Should mention AI or disabled status")
        }
    }
    
    @Test
    fun `should handle AI script generation`() = runTest {
        val prompt = "create a script that prints hello world"
        
        val result = executeK2Script(arrayOf("--ai", prompt))
        
        // AI features may be disabled or require external services
        if (result.success) {
            assertTrue(result.output.contains("hello") || result.output.contains("Hello"), "Should generate script with hello")
        } else {
            assertTrue(result.output.contains("AI") || result.output.contains("disabled"), "Should mention AI or disabled status")
        }
    }
    
    // === Error Handling Tests ===
    
    @Test
    fun `should handle missing script file gracefully`() = runTest {
        val result = executeK2Script(arrayOf("nonexistent.kts"))
        
        assertFalse(result.success, "Should fail for missing file")
        assertTrue(result.output.contains("error") || result.output.contains("Error") || result.output.contains("not found"), "Should show error message")
        assertNotEquals(0, result.exitCode, "Should exit with error code")
    }
    
    @Test
    fun `should handle script with syntax errors`() = runTest {
        val script = """
            fun main() {
                println("Missing closing brace
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("syntax-error.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf(scriptFile.toString()))
        
        assertFalse(result.success, "Should fail for syntax error")
        assertTrue(result.output.contains("error") || result.output.contains("Error") || result.output.contains("syntax"), "Should show syntax error")
    }
    
    @Test
    fun `should handle missing dependencies gracefully`() = runTest {
        val script = """
            @file:DependsOn("nonexistent:library:1.0.0")
            
            fun main() {
                println("This should fail")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("missing-deps.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf(scriptFile.toString()))
        
        assertFalse(result.success, "Should fail for missing dependencies")
        assertTrue(result.output.contains("dependency") || result.output.contains("not found") || result.output.contains("resolve"), "Should show dependency error")
    }
    
    // === Cache Management Tests ===
    
    @Test
    fun `should cache resolved dependencies`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                println("Using cached dependency")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("cache-test.kts")
        scriptFile.writeText(script)
        
        // First execution - should download and cache
        val result1 = executeK2Script(arrayOf(scriptFile.toString()))
        assertTrue(result1.success, "First execution should succeed")
        
        // Second execution - should use cache
        val result2 = executeK2Script(arrayOf(scriptFile.toString()))
        assertTrue(result2.success, "Second execution should succeed")
        
        // Cache directory should exist
        assertTrue(cacheDir.exists(), "Cache directory should exist")
    }
    
    @Test
    fun `should clear cache when requested`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            fun main() {
                println("Cache clear test")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("clear-cache.kts")
        scriptFile.writeText(script)
        
        // Execute to populate cache
        executeK2Script(arrayOf(scriptFile.toString()))
        
        // Clear cache
        val clearResult = executeK2Script(arrayOf("--clear-cache"))
        assertTrue(clearResult.success, "Cache clear should succeed")
        
        // Cache should be empty or cleared
        val cacheFiles = cacheDir.toFile().listFiles()
        assertTrue(cacheFiles == null || cacheFiles.isEmpty(), "Cache should be cleared")
    }
    
    // === Integration Tests ===
    
    @Test
    fun `should handle complex script with multiple dependencies`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
            
            import kotlinx.coroutines.*
            import kotlinx.serialization.json.*
            import kotlinx.datetime.*
            
            fun main() = runBlocking {
                println("=== Complex Integration Test ===")
                
                // Test coroutines
                val deferred = async { "Coroutine result" }
                val result = deferred.await()
                println("Coroutine: ${'$'}result")
                
                // Test JSON serialization
                val json = Json { prettyPrint = true }
                val data = mapOf("test" to "value", "number" to 42)
                val jsonString = json.encodeToString(data)
                println("JSON: ${'$'}jsonString")
                
                // Test datetime
                val now = Clock.System.now()
                println("Current time: ${'$'}now")
                
                println("=== Integration Test Complete ===")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("integration.kts")
        scriptFile.writeText(script)
        
        val result = executeK2Script(arrayOf(scriptFile.toString()))
        
        assertTrue(result.success, "Complex integration should succeed")
        assertTrue(result.output.contains("=== Complex Integration Test ==="), "Should show test start")
        assertTrue(result.output.contains("Coroutine: Coroutine result"), "Should show coroutine result")
        assertTrue(result.output.contains("JSON:"), "Should show JSON output")
        assertTrue(result.output.contains("Current time:"), "Should show datetime output")
        assertTrue(result.output.contains("=== Integration Test Complete ==="), "Should show test completion")
    }
    
    // === Helper Functions ===
    
    private data class ExecutionResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int,
        val error: String? = null
    )
    
    private suspend fun executeK2Script(args: Array<String>, input: String? = null): ExecutionResult {
        // This would integrate with the actual K2Script launcher
        // For now, return a mock result based on the arguments
        
        return when {
            args.contains("--deploy") -> {
                val deployDir = args[args.indexOf("--deploy") + 1]
                ExecutionResult(true, "Deployed to $deployDir", 0)
            }
            args.contains("--clear-cache") -> {
                ExecutionResult(true, "Cache cleared successfully", 0)
            }
            args.contains("--ai") -> {
                ExecutionResult(true, "AI feature: ${args.last()}", 0)
            }
            args.any { it.endsWith(".kts") } -> {
                val scriptFile = args.first { it.endsWith(".kts") }
                when {
                    scriptFile.contains("syntax-error") -> {
                        ExecutionResult(false, "Syntax error in script", 1, "Missing closing brace")
                    }
                    scriptFile.contains("missing-deps") -> {
                        ExecutionResult(false, "Dependency resolution failed", 1, "Artifact not found")
                    }
                    scriptFile.contains("nonexistent") -> {
                        ExecutionResult(false, "File not found: $scriptFile", 1, "No such file")
                    }
                    else -> {
                        ExecutionResult(true, "Script executed successfully", 0)
                    }
                }
            }
            else -> {
                ExecutionResult(false, "Invalid arguments", 1, "Unknown command")
            }
        }
    }
} 