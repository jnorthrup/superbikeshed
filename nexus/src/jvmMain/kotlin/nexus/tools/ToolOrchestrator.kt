package nexus.tools

import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Tool Orchestration Framework for Nexus
 * 
 * Discovers and executes development tools in the environment
 */
class ToolOrchestrator(private val workingDir: File = File(".")) {
    
    data class ToolInfo(
        val name: String,
        val path: String,
        val version: String?,
        val capabilities: Set<ToolCapability>
    )
    
    enum class ToolCapability {
        BUILD,
        TEST,
        LINT,
        FORMAT,
        ANALYZE,
        DEPLOY,
        PACKAGE
    }
    
    data class ToolExecutionResult(
        val success: Boolean,
        val output: String,
        val errorOutput: String,
        val exitCode: Int,
        val duration: Long
    )
    
    private val discoveredTools = mutableMapOf<String, ToolInfo>()
    
    suspend fun discoverTools(): Map<String, ToolInfo> = withContext(Dispatchers.IO) {
        println("Discovering available tools...")
        
        val tools = mutableMapOf<String, ToolInfo>()
        
        // Check for common development tools
        checkForTool("gradle", "gradle", "--version", ToolCapability.BUILD)?.let { tools["gradle"] = it }
        checkForTool("maven", "mvn", "--version", ToolCapability.BUILD)?.let { tools["maven"] = it }
        checkForTool("kotlin", "kotlinc", "-version", ToolCapability.BUILD)?.let { tools["kotlin"] = it }
        checkForTool("java", "java", "-version", ToolCapability.BUILD)?.let { tools["java"] = it }
        checkForTool("git", "git", "--version", ToolCapability.PACKAGE)?.let { tools["git"] = it }
        checkForTool("docker", "docker", "--version", ToolCapability.DEPLOY)?.let { tools["docker"] = it }
        checkForTool("k2script", "k2script", "--help", ToolCapability.BUILD)?.let { tools["k2script"] = it }
        
        // Check for project-specific tools
        if (File(workingDir, "build.gradle.kts").exists()) {
            checkForTool("gradle-wrapper", "./gradlew", "--version", ToolCapability.BUILD)?.let { tools["gradle-wrapper"] = it }
        }
        
        discoveredTools.clear()
        discoveredTools.putAll(tools)
        
        println("Discovered ${tools.size} tools")
        tools
    }
    
    private suspend fun checkForTool(name: String, command: String, versionArg: String, capability: ToolCapability): ToolInfo? {
        return try {
            val result = executeCommand(listOf(command, versionArg))
            if (result.success) {
                val version = extractVersion(result.output)
                ToolInfo(name, command, version, setOf(capability))
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractVersion(output: String): String? {
        // Simple version extraction - could be made more sophisticated
        val versionPattern = Regex("""(\d+\.\d+\.\d+)""")
        return versionPattern.find(output)?.groupValues?.get(1)
    }
    
    suspend fun executeTool(toolName: String, args: List<String>): ToolExecutionResult {
        val tool = discoveredTools[toolName] ?: throw IllegalArgumentException("Tool not found: $toolName")
        
        println("Executing $toolName with args: ${args.joinToString(" ")}")
        
        val command = listOf(tool.path) + args
        return executeCommand(command)
    }
    
    suspend fun executeCommand(command: List<String>): ToolExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(workingDir)
            
            val process = processBuilder.start()
            
            // Capture output
            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            
            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startTime
            
            ToolExecutionResult(
                success = exitCode == 0,
                output = output,
                errorOutput = errorOutput,
                exitCode = exitCode,
                duration = duration
            )
        } catch (e: IOException) {
            ToolExecutionResult(
                success = false,
                output = "",
                errorOutput = e.message ?: "Unknown error",
                exitCode = -1,
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    fun getAvailableTools(): Map<String, ToolInfo> = discoveredTools.toMap()
    
    fun hasTool(name: String): Boolean = discoveredTools.containsKey(name)
    
    fun getToolCapabilities(name: String): Set<ToolCapability>? = discoveredTools[name]?.capabilities
} 