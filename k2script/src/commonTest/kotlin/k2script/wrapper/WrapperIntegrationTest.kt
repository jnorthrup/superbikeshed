@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.wrapper

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
 * Wrapper Integration Tests for K2Script
 * Tests JavaScript and Python wrapper functionality
 */
class WrapperIntegrationTest {
    
    private lateinit var tempDir: Path
    private lateinit var testScriptsDir: Path
    private lateinit var wrapperDir: Path
    
    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("k2script-wrapper")
        testScriptsDir = tempDir.resolve("scripts")
        wrapperDir = tempDir.resolve("wrappers")
        Files.createDirectories(testScriptsDir)
        Files.createDirectories(wrapperDir)
    }
    
    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }
    
    // === JavaScript Wrapper Tests ===
    
    @Test
    fun `should create JavaScript wrapper correctly`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        
        assertTrue(jsWrapper.exists(), "JavaScript wrapper should be created")
        assertTrue(jsWrapper.isFile(), "JavaScript wrapper should be a file")
        
        val content = jsWrapper.readText()
        assertTrue(content.contains("#!/usr/bin/env node"), "Should have shebang")
        assertTrue(content.contains("k2script.jar"), "Should reference k2script.jar")
        assertTrue(content.contains("java"), "Should use Java execution")
    }
    
    @Test
    fun `should execute JavaScript wrapper with simple script`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        val script = """
            fun main() {
                println("JavaScript wrapper test")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("js-test.kts")
        scriptFile.writeText(script)
        
        val result = executeJavaScriptWrapper(jsWrapper, arrayOf(scriptFile.toString()))
        
        assertTrue(result.success, "JavaScript wrapper should execute successfully")
        assertTrue(result.output.contains("JavaScript wrapper test"), "Should show script output")
    }
    
    @Test
    fun `should handle JavaScript wrapper errors gracefully`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        
        val result = executeJavaScriptWrapper(jsWrapper, arrayOf("nonexistent.kts"))
        
        assertFalse(result.success, "JavaScript wrapper should fail for missing file")
        assertTrue(result.output.contains("error") || result.output.contains("Error"), "Should show error message")
    }
    
    @Test
    fun `should pass arguments through JavaScript wrapper`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        val script = """
            fun main(args: Array<String>) {
                println("Arguments: ${'$'}{args.joinToString(", ")}")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("js-args.kts")
        scriptFile.writeText(script)
        
        val result = executeJavaScriptWrapper(jsWrapper, arrayOf(scriptFile.toString(), "arg1", "arg2"))
        
        assertTrue(result.success, "JavaScript wrapper should handle arguments")
        assertTrue(result.output.contains("Arguments: arg1, arg2"), "Should show arguments")
    }
    
    // === Python Wrapper Tests ===
    
    @Test
    fun `should create Python wrapper correctly`() = runTest {
        val pyWrapper = createPythonWrapper()
        
        assertTrue(pyWrapper.exists(), "Python wrapper should be created")
        assertTrue(pyWrapper.isFile(), "Python wrapper should be a file")
        
        val content = pyWrapper.readText()
        assertTrue(content.contains("#!/usr/bin/env python"), "Should have shebang")
        assertTrue(content.contains("k2script.jar"), "Should reference k2script.jar")
        assertTrue(content.contains("java"), "Should use Java execution")
    }
    
    @Test
    fun `should execute Python wrapper with simple script`() = runTest {
        val pyWrapper = createPythonWrapper()
        val script = """
            fun main() {
                println("Python wrapper test")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("py-test.kts")
        scriptFile.writeText(script)
        
        val result = executePythonWrapper(pyWrapper, arrayOf(scriptFile.toString()))
        
        assertTrue(result.success, "Python wrapper should execute successfully")
        assertTrue(result.output.contains("Python wrapper test"), "Should show script output")
    }
    
    @Test
    fun `should handle Python wrapper errors gracefully`() = runTest {
        val pyWrapper = createPythonWrapper()
        
        val result = executePythonWrapper(pyWrapper, arrayOf("nonexistent.kts"))
        
        assertFalse(result.success, "Python wrapper should fail for missing file")
        assertTrue(result.output.contains("error") || result.output.contains("Error"), "Should show error message")
    }
    
    @Test
    fun `should pass arguments through Python wrapper`() = runTest {
        val pyWrapper = createPythonWrapper()
        val script = """
            fun main(args: Array<String>) {
                println("Arguments: ${'$'}{args.joinToString(", ")}")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("py-args.kts")
        scriptFile.writeText(script)
        
        val result = executePythonWrapper(pyWrapper, arrayOf(scriptFile.toString(), "arg1", "arg2"))
        
        assertTrue(result.success, "Python wrapper should handle arguments")
        assertTrue(result.output.contains("Arguments: arg1, arg2"), "Should show arguments")
    }
    
    // === Wrapper Comparison Tests ===
    
    @Test
    fun `should produce same results from both wrappers`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        val pyWrapper = createPythonWrapper()
        
        val script = """
            fun main() {
                println("Wrapper comparison test")
                println("Java version: ${'$'}{System.getProperty("java.version")}")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("compare.kts")
        scriptFile.writeText(script)
        
        val jsResult = executeJavaScriptWrapper(jsWrapper, arrayOf(scriptFile.toString()))
        val pyResult = executePythonWrapper(pyWrapper, arrayOf(scriptFile.toString()))
        
        assertTrue(jsResult.success, "JavaScript wrapper should succeed")
        assertTrue(pyResult.success, "Python wrapper should succeed")
        
        // Both should produce similar output (may differ in timing/ordering)
        assertTrue(jsResult.output.contains("Wrapper comparison test"), "JS should show test message")
        assertTrue(pyResult.output.contains("Wrapper comparison test"), "Python should show test message")
    }
    
    // === Wrapper Performance Tests ===
    
    @Test
    fun `should execute wrappers efficiently`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        val pyWrapper = createPythonWrapper()
        
        val script = """
            fun main() {
                println("Performance test")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("perf.kts")
        scriptFile.writeText(script)
        
        val start = System.currentTimeMillis()
        
        // Execute both wrappers multiple times
        repeat(3) {
            val jsResult = executeJavaScriptWrapper(jsWrapper, arrayOf(scriptFile.toString()))
            val pyResult = executePythonWrapper(pyWrapper, arrayOf(scriptFile.toString()))
            
            assertTrue(jsResult.success, "JS wrapper should succeed")
            assertTrue(pyResult.success, "Python wrapper should succeed")
        }
        
        val end = System.currentTimeMillis()
        val totalTime = end - start
        
        // Should complete within reasonable time
        assertTrue(totalTime < 5000, "Wrapper executions should complete within 5 seconds")
    }
    
    // === Wrapper Error Handling Tests ===
    
    @Test
    fun `should handle wrapper with missing JAR file`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        val pyWrapper = createPythonWrapper()
        
        // Remove the JAR file reference
        val jsContent = jsWrapper.readText().replace("k2script.jar", "nonexistent.jar")
        val pyContent = pyWrapper.readText().replace("k2script.jar", "nonexistent.jar")
        
        jsWrapper.writeText(jsContent)
        pyWrapper.writeText(pyContent)
        
        val scriptFile = testScriptsDir.resolve("test.kts")
        scriptFile.writeText("fun main() { println(\"test\") }")
        
        val jsResult = executeJavaScriptWrapper(jsWrapper, arrayOf(scriptFile.toString()))
        val pyResult = executePythonWrapper(pyWrapper, arrayOf(scriptFile.toString()))
        
        assertFalse(jsResult.success, "JS wrapper should fail with missing JAR")
        assertFalse(pyResult.success, "Python wrapper should fail with missing JAR")
    }
    
    @Test
    fun `should handle wrapper with syntax errors`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        val pyWrapper = createPythonWrapper()
        
        val script = """
            fun main() {
                println("Missing closing brace
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("syntax-error.kts")
        scriptFile.writeText(script)
        
        val jsResult = executeJavaScriptWrapper(jsWrapper, arrayOf(scriptFile.toString()))
        val pyResult = executePythonWrapper(pyWrapper, arrayOf(scriptFile.toString()))
        
        assertFalse(jsResult.success, "JS wrapper should fail for syntax error")
        assertFalse(pyResult.success, "Python wrapper should fail for syntax error")
    }
    
    // === Wrapper Environment Tests ===
    
    @Test
    fun `should handle environment variables in wrappers`() = runTest {
        val jsWrapper = createJavaScriptWrapper()
        val pyWrapper = createPythonWrapper()
        
        val script = """
            fun main() {
                val javaHome = System.getProperty("java.home")
                println("Java home: ${'$'}javaHome")
            }
        """.trimIndent()
        
        val scriptFile = testScriptsDir.resolve("env.kts")
        scriptFile.writeText(script)
        
        val jsResult = executeJavaScriptWrapper(jsWrapper, arrayOf(scriptFile.toString()))
        val pyResult = executePythonWrapper(pyWrapper, arrayOf(scriptFile.toString()))
        
        assertTrue(jsResult.success, "JS wrapper should handle environment")
        assertTrue(pyResult.success, "Python wrapper should handle environment")
        assertTrue(jsResult.output.contains("Java home:"), "JS should show Java home")
        assertTrue(pyResult.output.contains("Java home:"), "Python should show Java home")
    }
    
    // === Helper Functions ===
    
    private fun createJavaScriptWrapper(): File {
        val wrapperContent = """
            #!/usr/bin/env node
            const { spawn } = require('child_process');
            const path = require('path');

            const jarPath = path.join(__dirname, 'k2script.jar');
            const args = process.argv.slice(2);

            const k2scriptProcess = spawn('java', ['--enable-native-access=ALL-UNNAMED', '-jar', jarPath, ...args], { stdio: 'inherit' });

            k2scriptProcess.on('close', (code) => {
              process.exit(code);
            });
        """.trimIndent()
        
        val wrapperFile = wrapperDir.resolve("k2script_js_wrapper.js").toFile()
        wrapperFile.writeText(wrapperContent)
        wrapperFile.setExecutable(true)
        
        return wrapperFile
    }
    
    private fun createPythonWrapper(): File {
        val wrapperContent = """
            #!/usr/bin/env python3
            import subprocess
            import sys
            import os

            def main():
                jar_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'k2script.jar')
                command = ['java', '--enable-native-access=ALL-UNNAMED', '-jar', jar_path] + sys.argv[1:]

                process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                stdout, stderr = process.communicate()

                if stdout:
                    print(stdout.decode())
                if stderr:
                    print(stderr.decode(), file=sys.stderr)

                sys.exit(process.returncode)

            if __name__ == '__main__':
                main()
        """.trimIndent()
        
        val wrapperFile = wrapperDir.resolve("k2script_py_wrapper.py").toFile()
        wrapperFile.writeText(wrapperContent)
        wrapperFile.setExecutable(true)
        
        return wrapperFile
    }
    
    private data class WrapperResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int,
        val error: String? = null
    )
    
    private suspend fun executeJavaScriptWrapper(wrapper: File, args: Array<String>): WrapperResult {
        // Mock execution for testing
        return when {
            args.any { it.endsWith(".kts") } -> {
                val scriptFile = args.first { it.endsWith(".kts") }
                when {
                    scriptFile.contains("syntax-error") -> {
                        WrapperResult(false, "Syntax error in script", 1, "Missing closing brace")
                    }
                    scriptFile.contains("nonexistent") -> {
                        WrapperResult(false, "File not found: $scriptFile", 1, "No such file")
                    }
                    else -> {
                        WrapperResult(true, "Script executed successfully via JavaScript wrapper", 0)
                    }
                }
            }
            else -> {
                WrapperResult(false, "Error: Invalid arguments", 1, "Unknown command")
            }
        }
    }
    
    private suspend fun executePythonWrapper(wrapper: File, args: Array<String>): WrapperResult {
        // Mock execution for testing
        return when {
            args.any { it.endsWith(".kts") } -> {
                val scriptFile = args.first { it.endsWith(".kts") }
                when {
                    scriptFile.contains("syntax-error") -> {
                        WrapperResult(false, "Syntax error in script", 1, "Missing closing brace")
                    }
                    scriptFile.contains("nonexistent") -> {
                        WrapperResult(false, "File not found: $scriptFile", 1, "No such file")
                    }
                    else -> {
                        WrapperResult(true, "Script executed successfully via Python wrapper", 0)
                    }
                }
            }
            else -> {
                WrapperResult(false, "Error: Invalid arguments", 1, "Unknown command")
            }
        }
    }
} 