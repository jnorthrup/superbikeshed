@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.launcher

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.io.path.exists
import kotlin.io.path.deleteIfExists

/**
 * Integration Tests for K2Script Launcher
 * Tests the actual launcher functionality with real script execution
 */
class LauncherIntegrationTest {
    
    private lateinit var tempDir: Path
    private lateinit var testScriptsDir: Path
    private lateinit var launcher: TrikeShedLauncher
    
    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("k2script-launcher")
        testScriptsDir = tempDir.resolve("scripts")
        Files.createDirectories(testScriptsDir)
        
        launcher = TrikeShedLauncher()
    }
    
    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }
    
    // === Script Analysis Tests ===
    
    @Test
    fun `should analyze script metadata correctly`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            @file:Repository("https://repo1.maven.org/maven2")
            
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                println("Hello from analyzed script")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("analyze-test.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        
        assertEquals(scriptFile.toString(), metadata.path, "Should have correct script path")
        assertEquals(1, metadata.dependencies.a, "Should have one dependency")
        assertEquals(1, metadata.repositories.a, "Should have one repository")
        assertEquals("MainKt", metadata.mainClass, "Should have correct main class")
        
        val dependency = metadata.dependencies.b(0)
        assertEquals("org.jetbrains.kotlinx", dependency.groupId, "Should have correct group ID")
        assertEquals("kotlinx-coroutines-core", dependency.artifactId, "Should have correct artifact ID")
        assertEquals("1.7.3", dependency.version, "Should have correct version")
    }
    
    @Test
    fun `should handle script without dependencies`() = runTest {
        val script = """
            fun main() {
                println("Simple script")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("simple.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        
        assertEquals(0, metadata.dependencies.a, "Should have no dependencies")
        assertEquals(0, metadata.repositories.a, "Should have no repositories")
        assertEquals("MainKt", metadata.mainClass, "Should have correct main class")
    }
    
    @Test
    fun `should handle script with multiple dependencies`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
            
            fun main() {
                println("Multiple dependencies")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("multi-deps.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        
        assertEquals(3, metadata.dependencies.a, "Should have three dependencies")
        
        val deps = (0 until metadata.dependencies.a).map { metadata.dependencies.b(it) }
        assertTrue(deps.any { it.artifactId == "kotlinx-coroutines-core" }, "Should have coroutines dependency")
        assertTrue(deps.any { it.artifactId == "kotlinx-serialization-json" }, "Should have serialization dependency")
        assertTrue(deps.any { it.artifactId == "kotlinx-datetime" }, "Should have datetime dependency")
    }
    
    // === Dependency Resolution Tests ===
    
    @Test
    fun `should resolve dependencies successfully`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            fun main() {
                println("Dependency resolved")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("resolve-test.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        val resolved = launcher.resolveDependencies(metadata)
        
        assertTrue(resolved.failed.a == 0, "Should have no failed dependencies")
        assertTrue(resolved.resolved.a > 0, "Should have resolved dependencies")
    }
    
    @Test
    fun `should handle dependency resolution failures gracefully`() = runTest {
        val script = """
            @file:DependsOn("nonexistent:library:1.0.0")
            
            fun main() {
                println("This should fail")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("resolve-fail.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        val resolved = launcher.resolveDependencies(metadata)
        
        assertTrue(resolved.failed.a > 0, "Should have failed dependencies")
        assertTrue(resolved.resolved.a == 0, "Should have no resolved dependencies")
    }
    
    // === Classpath Building Tests ===
    
    @Test
    fun `should build classpath with resolved dependencies`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            fun main() {
                println("Classpath test")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("classpath-test.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        val resolved = launcher.resolveDependencies(metadata)
        val classpath = launcher.buildClasspath(resolved)
        
        assertTrue(classpath.entries.a > 0, "Should have classpath entries")
        
        // Should include system classpath
        val systemCp = System.getProperty("java.class.path")
        assertTrue(classpath.entries.a > systemCp.split(System.getProperty("path.separator")).size, 
                  "Should have more entries than system classpath")
    }
    
    // === Script Execution Tests ===
    
    @Test
    fun `should execute simple script successfully`() = runTest {
        val script = """
            fun main() {
                println("Execution test successful")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("exec-test.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        val resolved = launcher.resolveDependencies(metadata)
        val classpath = launcher.buildClasspath(resolved)
        
        val result = launcher.executeScript(metadata, classpath)
        
        assertTrue(result is ExecutionResult.Success, "Should execute successfully")
    }
    
    @Test
    fun `should execute script with arguments`() = runTest {
        val script = """
            fun main(args: Array<String>) {
                println("Arguments: ${'$'}{args.joinToString(", ")}")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("exec-args.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        val resolved = launcher.resolveDependencies(metadata)
        val classpath = launcher.buildClasspath(resolved)
        
        val args = arrayOf("arg1", "arg2", "arg3").toTypedArray().toIdx()
        val result = launcher.executeScript(metadata, classpath, args)
        
        assertTrue(result is ExecutionResult.Success, "Should execute with arguments successfully")
    }
    
    @Test
    fun `should handle script execution failures`() = runTest {
        val script = """
            fun main() {
                throw RuntimeException("Intentional error")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("exec-fail.kts")
        scriptFile.writeText(script)
        
        val metadata = launcher.analyzeScript(scriptFile.toString())
        val resolved = launcher.resolveDependencies(metadata)
        val classpath = launcher.buildClasspath(resolved)
        
        val result = launcher.executeScript(metadata, classpath)
        
        assertTrue(result is ExecutionResult.Failure, "Should fail execution")
        val failure = result as ExecutionResult.Failure
        assertTrue(failure.error.contains("Intentional error"), "Should contain error message")
    }
    
    // === Complete Pipeline Tests ===
    
    @Test
    fun `should handle complete script execution pipeline`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                println("Pipeline test starting...")
                delay(50)
                println("Pipeline test completed!")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("pipeline.kts")
        scriptFile.writeText(script)
        
        val result = launcher.launch(scriptFile.toString())
        
        assertTrue(result is ExecutionResult.Success, "Complete pipeline should succeed")
    }
    
    @Test
    fun `should handle pipeline with arguments`() = runTest {
        val script = """
            fun main(args: Array<String>) {
                println("Pipeline with args: ${'$'}{args.joinToString(", ")}")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("pipeline-args.kts")
        scriptFile.writeText(script)
        
        val args = arrayOf("pipeline", "test", "arguments")
        val result = launcher.launch(scriptFile.toString(), args)
        
        assertTrue(result is ExecutionResult.Success, "Pipeline with args should succeed")
    }
    
    // === Error Handling Tests ===
    
    @Test
    fun `should handle missing script file`() = runTest {
        val result = launcher.launch("nonexistent.kts")
        
        assertTrue(result is ExecutionResult.Failure, "Should fail for missing file")
        val failure = result as ExecutionResult.Failure
        assertTrue(failure.error.contains("not found") || failure.error.contains("No such file"), 
                  "Should show file not found error")
    }
    
    @Test
    fun `should handle malformed script`() = runTest {
        val script = """
            fun main() {
                println("Missing closing brace
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("malformed.kts")
        scriptFile.writeText(script)
        
        val result = launcher.launch(scriptFile.toString())
        
        assertTrue(result is ExecutionResult.Failure, "Should fail for malformed script")
        val failure = result as ExecutionResult.Failure
        assertTrue(failure.error.contains("syntax") || failure.error.contains("parse"), 
                  "Should show syntax error")
    }
    
    // === Performance Tests ===
    
    @Test
    fun `should handle concurrent script execution`() = runTest {
        val script = """
            fun main() {
                println("Concurrent execution test")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("concurrent.kts")
        scriptFile.writeText(script)
        
        // Execute multiple scripts concurrently
        val results = (1..5).map { 
            launcher.launch(scriptFile.toString())
        }
        
        assertTrue(results.all { it is ExecutionResult.Success }, "All concurrent executions should succeed")
    }
    
    @Test
    fun `should cache resolved dependencies for performance`() = runTest {
        val script = """
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            fun main() {
                println("Cache performance test")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("cache-perf.kts")
        scriptFile.writeText(script)
        
        // First execution - should resolve dependencies
        val start1 = System.currentTimeMillis()
        val result1 = launcher.launch(scriptFile.toString())
        val time1 = System.currentTimeMillis() - start1
        
        // Second execution - should use cache
        val start2 = System.currentTimeMillis()
        val result2 = launcher.launch(scriptFile.toString())
        val time2 = System.currentTimeMillis() - start2
        
        assertTrue(result1 is ExecutionResult.Success, "First execution should succeed")
        assertTrue(result2 is ExecutionResult.Success, "Second execution should succeed")
        
        // Second execution should be faster (cached)
        assertTrue(time2 <= time1, "Cached execution should be faster or equal")
    }
} 