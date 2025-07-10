@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.jvm

import k2script.lib.*
import k2script.core.*
import kotlinx.coroutines.*
import java.io.File
import java.lang.ProcessBuilder
import kotlin.concurrent.thread

/**
 * JVM Script Runner - Executes Kotlin scripts as separate processes
 * This provides a more isolated execution environment
 */
class JvmScriptRunner(
    internal val kotlinHome: String = System.getenv("KOTLIN_HOME") ?: findKotlinHome(),
    internal val javaHome: String = System.getProperty("java.home")
) {
    
    internal val kotlinc = "$kotlinHome/bin/kotlinc"
    internal val kotlin = "$kotlinHome/bin/kotlin"
    
    /**
     * Run a Kotlin script file directly using kotlin command
     */
    suspend fun runScript(
        scriptFile: File,
        args: Array<String> = emptyArray(),
        classpath: List<String> = emptyList(),
        systemProperties: Map<String, String> = emptyMap()
    ): ScriptResult = withContext(Dispatchers.IO) {
        try {
            val command = buildRunCommand(scriptFile, args, classpath, systemProperties)
            val process = ProcessBuilder(command)
                .directory(scriptFile.parentFile)
                .start()
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            // Capture output streams
            val outputReader = thread {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lines().forEach { line ->
                        output.appendLine(line)
                        println(line) // Echo to console
                    }
                }
            }
            
            val errorReader = thread {
                process.errorStream.bufferedReader().use { reader ->
                    reader.lines().forEach { line ->
                        error.appendLine(line)
                        System.err.println(line) // Echo to console
                    }
                }
            }
            
            val exitCode = process.waitFor()
            outputReader.join()
            errorReader.join()
            
            if (exitCode == 0) {
                ScriptResult.Success(output.toString(), exitCode)
            } else {
                ScriptResult.Failure(error.toString(), exitCode)
            }
        } catch (e: Exception) {
            ScriptResult.Failure(
                error = "Failed to run script: ${e.message}",
                exitCode = -1,
                cause = e
            )
        }
    }
    
    /**
     * Compile a Kotlin script to a JAR
     */
    suspend fun compileToJar(
        scriptFile: File,
        outputJar: File,
        classpath: List<String> = emptyList(),
        includeRuntime: Boolean = true
    ): CompilationResult = withContext(Dispatchers.IO) {
        try {
            val command = buildCompileCommand(scriptFile, outputJar, classpath, includeRuntime)
            val process = ProcessBuilder(command)
                .directory(scriptFile.parentFile)
                .start()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                CompilationResult.Success(outputJar, output)
            } else {
                CompilationResult.Failure(error, exitCode)
            }
        } catch (e: Exception) {
            CompilationResult.Failure(
                error = "Compilation failed: ${e.message}",
                exitCode = -1
            )
        }
    }
    
    /**
     * Run a compiled JAR file
     */
    suspend fun runJar(
        jarFile: File,
        mainClass: String,
        args: Array<String> = emptyArray(),
        classpath: List<String> = emptyList(),
        systemProperties: Map<String, String> = emptyMap()
    ): ScriptResult = withContext(Dispatchers.IO) {
        try {
            val command = buildJarRunCommand(jarFile, mainClass, args, classpath, systemProperties)
            val process = ProcessBuilder(command)
                .start()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                ScriptResult.Success(output, exitCode)
            } else {
                ScriptResult.Failure(error, exitCode)
            }
        } catch (e: Exception) {
            ScriptResult.Failure(
                error = "Failed to run JAR: ${e.message}",
                exitCode = -1,
                cause = e
            )
        }
    }
    
    internal fun buildRunCommand(
        scriptFile: File,
        args: Array<String>,
        classpath: List<String>,
        systemProperties: Map<String, String>
    ): List<String> {
        val command = mutableListOf(kotlin)
        
        // Add classpath
        if (classpath.isNotEmpty()) {
            command.add("-cp")
            command.add(classpath.joinToString(File.pathSeparator))
        }
        
        // Add system properties
        systemProperties.forEach { (key, value) ->
            command.add("-D$key=$value")
        }
        
        // Add script file
        command.add(scriptFile.absolutePath)
        
        // Add script arguments
        command.addAll(args)
        
        return command
    }
    
    internal fun buildCompileCommand(
        scriptFile: File,
        outputJar: File,
        classpath: List<String>,
        includeRuntime: Boolean
    ): List<String> {
        val command = mutableListOf(kotlinc)
        
        // Add classpath
        if (classpath.isNotEmpty()) {
            command.add("-cp")
            command.add(classpath.joinToString(File.pathSeparator))
        }
        
        // Include Kotlin runtime
        if (includeRuntime) {
            command.add("-include-runtime")
        }
        
        // Script source
        command.add("-script")
        command.add(scriptFile.absolutePath)
        
        // Output JAR
        command.add("-d")
        command.add(outputJar.absolutePath)
        
        return command
    }
    
    internal fun buildJarRunCommand(
        jarFile: File,
        mainClass: String,
        args: Array<String>,
        classpath: List<String>,
        systemProperties: Map<String, String>
    ): List<String> {
        val java = "$javaHome/bin/java"
        val command = mutableListOf(java)
        
        // Add system properties
        systemProperties.forEach { (key, value) ->
            command.add("-D$key=$value")
        }
        
        // Build full classpath including the JAR
        val fullClasspath = (classpath + listOf(jarFile.absolutePath))
            .joinToString(File.pathSeparator)
        
        command.add("-cp")
        command.add(fullClasspath)
        
        // Main class
        command.add(mainClass)
        
        // Arguments
        command.addAll(args)
        
        return command
    }
    
    companion object {
        internal fun findKotlinHome(): String {
            // Try to find Kotlin installation
            val paths = listOf(
                "/usr/local/kotlin",
                "/opt/kotlin",
                System.getProperty("user.home") + "/.sdkman/candidates/kotlin/current"
            )
            
            return paths.firstOrNull { File(it).exists() }
                ?: throw IllegalStateException("KOTLIN_HOME not set and Kotlin installation not found")
        }
    }
}

/**
 * Compilation result
 */
sealed class CompilationResult {
    data class Success(val jarFile: File, val output: String) : CompilationResult()
    data class Failure(val error: String, val exitCode: Int) : CompilationResult()
}

