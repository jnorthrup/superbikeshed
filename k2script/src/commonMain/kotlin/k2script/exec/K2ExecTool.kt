package k2script.exec

import k2script.bbcontroller.*
import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import borg.trikeshed.services.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*

/**
 * K2Script Execution Tool
 * 
 * Clean exec tool with extra capabilities for nexus to borrow.
 * Integrates BBCursor JAR controller for intelligent dependency management.
 */

data class ExecConfig(
    val enableCache: Boolean = true,
    val bandwidthProfile: BandwidthProfile? = null,
    val verboseOutput: Boolean = false,
    val dryRun: Boolean = false,
    val workingDirectory: String = System.getProperty("user.dir"),
    val jvmArgs: Indexed<String> = emptyArray<String>().toIdx(),
    val scriptArgs: Indexed<String> = emptyArray<String>().toIdx()
)

data class ExecResult(
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
    val executionTimeMs: Long,
    val resolvedDependencies: Int,
    val cachedDependencies: Int
)

sealed class ExecStatus {
    object Analyzing : ExecStatus()
    object ResolvingDependencies : ExecStatus()
    object BuildingClasspath : ExecStatus()
    object Executing : ExecStatus()
    data class Completed(val result: ExecResult) : ExecStatus()
    data class Failed(val error: String, val cause: Throwable? = null) : ExecStatus()
}

class K2ExecTool(
    private val jarController: JarController = JarController(),
    private val reactor: Reactor = Reactor(),
    private val serviceManager: ServiceManager = ServiceManager(reactor)
) {
    
    suspend fun execute(scriptPath: String, config: ExecConfig = ExecConfig()): ExecResult {
        val startTime = System.currentTimeMillis()
        
        if (config.verboseOutput) {
            println("🚀 K2Script Execution Tool")
            println("📄 Script: $scriptPath")
            println("🔧 Config: cache=${config.enableCache}, dryRun=${config.dryRun}")
        }
        
        try {
            // 1. Analyze script annotations
            if (config.verboseOutput) println("🔍 Analyzing script annotations...")
            val annotations = jarController.analyzeScript(scriptPath)
            
            if (config.verboseOutput) {
                println("   📦 Dependencies: ${annotations.dependencies.a}")
                println("   🏛️  Repositories: ${annotations.repositories.a}")
            }
            
            // 2. Resolve dependencies with intelligent caching
            if (config.verboseOutput) println("🌐 Resolving dependencies...")
            val resolution = jarController.resolveDependencies(annotations)
            
            if (config.verboseOutput) {
                println("   ✅ Resolved: ${resolution.resolved.a}")
                println("   💾 Cached: ${resolution.cached.a}")
                println("   ❌ Failed: ${resolution.failed.a}")
            }
            
            // 3. Build classpath
            if (config.verboseOutput) println("🔗 Building classpath...")
            val classpath = jarController.createClasspath(resolution)
            
            // 4. Execute script (or dry run)
            val executionResult = if (config.dryRun) {
                if (config.verboseOutput) println("🧪 Dry run - not executing")
                ExecResult(
                    exitCode = 0,
                    output = "Dry run completed successfully",
                    errorOutput = "",
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    resolvedDependencies = resolution.resolved.a,
                    cachedDependencies = resolution.cached.a
                )
            } else {
                if (config.verboseOutput) println("⚡ Executing script...")
                executeScript(scriptPath, classpath, config, startTime, resolution)
            }
            
            if (config.verboseOutput) {
                println("✅ Execution completed in ${executionResult.executionTimeMs}ms")
                println("📊 Cache hit rate: ${(resolution.stats.hitRate * 100).toInt()}%")
            }
            
            return executionResult
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            
            if (config.verboseOutput) {
                println("❌ Execution failed: ${e.message}")
                e.printStackTrace()
            }
            
            return ExecResult(
                exitCode = 1,
                output = "",
                errorOutput = e.message ?: "Unknown error",
                executionTimeMs = duration,
                resolvedDependencies = 0,
                cachedDependencies = 0
            )
        }
    }
    
    private suspend fun executeScript(
        scriptPath: String,
        classpath: String,
        config: ExecConfig,
        startTime: Long,
        resolution: JarResolutionResult
    ): ExecResult {
        
        // Build command to execute Kotlin script
        val command = buildExecutionCommand(scriptPath, classpath, config)
        
        if (config.verboseOutput) {
            println("🔧 Command: ${command.take(3).joinToString(" ")}...")
        }
        
        // Execute using ProcessBuilder for better control
        val processBuilder = ProcessBuilder(command)
            .directory(java.io.File(config.workingDirectory))
            .redirectErrorStream(false)
        
        val process = processBuilder.start()
        
        // Capture output and error streams
        val outputDeferred = reactor.spawn {
            process.inputStream.bufferedReader().readText()
        }
        
        val errorDeferred = reactor.spawn {
            process.errorStream.bufferedReader().readText()
        }
        
        // Wait for completion
        val exitCode = process.waitFor()
        
        val output = outputDeferred.await()
        val errorOutput = errorDeferred.await()
        
        return ExecResult(
            exitCode = exitCode,
            output = output,
            errorOutput = errorOutput,
            executionTimeMs = System.currentTimeMillis() - startTime,
            resolvedDependencies = resolution.resolved.a,
            cachedDependencies = resolution.cached.a
        )
    }
    
    private fun buildExecutionCommand(
        scriptPath: String,
        classpath: String,
        config: ExecConfig
    ): List<String> {
        val command = mutableListOf<String>()
        
        // Add kotlin command
        command.add("kotlin")
        
        // Add JVM args
        for (i in 0 until config.jvmArgs.a) {
            command.add(config.jvmArgs.b(i))
        }
        
        // Add classpath if not empty
        if (classpath.isNotEmpty()) {
            command.add("-classpath")
            command.add(classpath)
        }
        
        // Add script path
        command.add(scriptPath)
        
        // Add script args
        for (i in 0 until config.scriptArgs.a) {
            command.add(config.scriptArgs.b(i))
        }
        
        return command
    }
    
    // === Nexus Integration Interface ===
    
    /**
     * Extra tools for nexus to borrow
     */
    fun getToolsForNexus(): NexusToolset {
        return NexusToolset(
            jarController = jarController,
            reactor = reactor,
            serviceManager = serviceManager
        )
    }
    
    /**
     * Quick dependency resolution for nexus agents
     */
    suspend fun quickResolve(coordinates: Indexed<String>): JarResolutionResult {
        // Convert string coordinates to dependency annotations
        val dependencies = coordinates.a j { i ->
            DependencyAnnotation(
                coordinate = coordinates.b(i),
                repository = null
            )
        }
        
        val annotations = AnnotationSet(
            dependencies = dependencies,
            repositories = emptyArray<RepositoryAnnotation>().toIdx(),
            sourceFile = "nexus-request"
        )
        
        return jarController.resolveDependencies(annotations)
    }
    
    /**
     * Get cache statistics for nexus monitoring
     */
    fun getCacheStats(): CacheStats {
        return jarController.cacheManager.getStats()
    }
    
    /**
     * Pre-warm cache with common dependencies
     */
    suspend fun preWarmCache(commonDependencies: Indexed<String>) {
        println("🔥 Pre-warming cache with ${commonDependencies.a} dependencies...")
        
        val tasks = commonDependencies.a j { i ->
            reactor.spawn {
                try {
                    quickResolve(arrayOf(commonDependencies.b(i)).toIdx())
                    println("💾 Cached: ${commonDependencies.b(i)}")
                } catch (e: Exception) {
                    println("⚠️  Failed to cache: ${commonDependencies.b(i)}")
                }
            }
        }
        
        // Wait for all pre-warming tasks
        for (i in 0 until tasks.a) {
            tasks.b(i).await()
        }
        
        val stats = getCacheStats()
        println("🔥 Cache pre-warming complete: ${stats.totalEntries} entries, ${stats.totalSizeBytes / 1024 / 1024}MB")
    }
}

// === Nexus Integration Types ===

data class NexusToolset(
    val jarController: JarController,
    val reactor: Reactor,
    val serviceManager: ServiceManager
) {
    /**
     * Quick JAR resolution for nexus agents
     */
    suspend fun resolveForAgent(agentId: String, dependencies: Indexed<String>): AgentResolutionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            val coordinates = dependencies.a j { i -> dependencies.b(i) }
            val tool = K2ExecTool(jarController, reactor, serviceManager)
            val result = tool.quickResolve(coordinates)
            
            return AgentResolutionResult(
                agentId = agentId,
                success = true,
                resolvedCount = result.resolved.a,
                cachedCount = result.cached.a,
                failedCount = result.failed.a,
                executionTimeMs = System.currentTimeMillis() - startTime,
                classpath = jarController.createClasspath(result),
                error = null
            )
            
        } catch (e: Exception) {
            return AgentResolutionResult(
                agentId = agentId,
                success = false,
                resolvedCount = 0,
                cachedCount = 0,
                failedCount = dependencies.a,
                executionTimeMs = System.currentTimeMillis() - startTime,
                classpath = "",
                error = e.message
            )
        }
    }
    
    /**
     * Batch resolution for multiple agents
     */
    suspend fun resolveBatch(requests: Indexed<AgentRequest>): Indexed<AgentResolutionResult> {
        val tasks = requests.a j { i ->
            val request = requests.b(i)
            reactor.spawn {
                resolveForAgent(request.agentId, request.dependencies)
            }
        }
        
        val results = mutableListOf<AgentResolutionResult>()
        for (i in 0 until tasks.a) {
            results.add(tasks.b(i).await())
        }
        
        return results.toTypedArray().toIdx()
    }
}

data class AgentRequest(
    val agentId: String,
    val dependencies: Indexed<String>,
    val priority: Int = 50
)

data class AgentResolutionResult(
    val agentId: String,
    val success: Boolean,
    val resolvedCount: Int,
    val cachedCount: Int,
    val failedCount: Int,
    val executionTimeMs: Long,
    val classpath: String,
    val error: String?
)

// === Command Line Interface ===

object K2ExecCLI {
    
    suspend fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }
        
        val scriptPath = args[0]
        val scriptArgs = args.drop(1).toTypedArray().toIdx()
        
        // Parse CLI flags (simplified)
        val config = ExecConfig(
            verboseOutput = args.contains("--verbose") || args.contains("-v"),
            dryRun = args.contains("--dry-run"),
            enableCache = !args.contains("--no-cache"),
            scriptArgs = scriptArgs
        )
        
        val tool = K2ExecTool()
        val result = tool.execute(scriptPath, config)
        
        if (result.exitCode != 0) {
            System.err.println(result.errorOutput)
        } else {
            println(result.output)
        }
        
        kotlin.system.exitProcess(result.exitCode)
    }
    
    private fun printUsage() {
        println("""
            K2Script Execution Tool
            
            Usage: k2exec <script.kts> [args...] [options]
            
            Options:
              --verbose, -v    Verbose output
              --dry-run        Analyze dependencies without execution
              --no-cache       Disable dependency caching
              
            Features:
            🧠 Intelligent bandwidth management
            💾 Perfect caching with LRU eviction
            🔍 BBCursive annotation parsing
            🚀 Reactor-based parallel resolution
            🔧 Nexus integration tools
            
            Examples:
              k2exec script.kts arg1 arg2
              k2exec --verbose --dry-run script.kts
              k2exec --no-cache script.kts
        """.trimIndent())
    }
}

// === Common Dependencies for Pre-warming ===

object CommonDependencies {
    val KOTLIN_STDLIB = arrayOf(
        "org.jetbrains.kotlin:kotlin-stdlib:1.9.24",
        "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24"
    ).toIdx()
    
    val COROUTINES = arrayOf(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0"
    ).toIdx()
    
    val SERIALIZATION = arrayOf(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3",
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3"
    ).toIdx()
    
    val HTTP_CLIENTS = arrayOf(
        "com.squareup.okhttp3:okhttp:4.12.0",
        "io.ktor:ktor-client-core:2.3.12",
        "io.ktor:ktor-client-cio:2.3.12"
    ).toIdx()
    
    val ALL_COMMON: Indexed<String> get() {
        val all = mutableListOf<String>()
        all.addAll(KOTLIN_STDLIB.play)
        all.addAll(COROUTINES.play)
        all.addAll(SERIALIZATION.play)
        all.addAll(HTTP_CLIENTS.play)
        return all.toTypedArray().toIdx()
    }
}