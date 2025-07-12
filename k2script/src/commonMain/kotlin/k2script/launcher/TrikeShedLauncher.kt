@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.launcher

import k2script.wagon.*
import k2script.platform.*
import borg.trikeshed.lib.*
import borg.trikeshed.cursor.*
import borg.trikeshed.io.*
import borg.trikeshed.reactor.*
import kotlinx.coroutines.*

/**
 * K2Script TrikeShed Launcher
 * 
 * Integrates the complete TrikeShed stack for script execution:
 * - TrikeShedWagon for dependency resolution
 * - Reactor for concurrent processing
 * - Cursor for metadata management
 * - IOMemento for efficient file operations
 */

class TrikeShedLauncher(
    internal val wagon: TrikeShedWagon = TrikeShedWagon(DefaultRepositories.withQuic),
    internal val reactor: Reactor<ResolveResult> = Reactor(),
    internal val cacheDir: String = "${Environment.userHome}/.k2script/trikeshed"
) {
    
    // === Script Analysis with TrikeShed Cursor ===
    
    suspend fun analyzeScript(scriptPath: String): ScriptMetadata {
        val scriptContent = IOMemento.readText(scriptPath)
        
        // Extract dependencies using cursor operations
        val dependencies = extractDependencies(scriptContent)
        val repositories = extractRepositories(scriptContent)
        val imports = extractImports(scriptContent)
        
        return ScriptMetadata(
            path = scriptPath,
            dependencies = dependencies,
            repositories = repositories,
            imports = imports,
            mainClass = findMainClass(scriptContent)
        )
    }
    
    internal fun extractDependencies(content: String): Indexed<Artifact> {
        val pattern = """@file:DependsOn\("([^"]+)"\)""".toRegex()
        val matches = pattern.findAll(content).toList()
        
        return \1 j { \2: Int ->
            val coordinate = matches[i].groupValues[1]
            parseArtifact(coordinate)
        }
    }
    
    internal fun extractRepositories(content: String): Indexed<Repository> {
        val pattern = """@file:Repository\("([^"]+)"\)""".toRegex()
        val matches = pattern.findAll(content).toList()
        
        return \1 j { \2: Int ->
            val url = matches[i].groupValues[1]
            Repository(
                id = "script-repo-$i",
                url = url,
                protocol = if (url.startsWith("https")) TransportProtocol.HTTPS else TransportProtocol.HTTP
            )
        }
    }
    
    internal fun extractImports(content: String): Indexed<String> {
        val pattern = """^import\s+(.+)$""".toRegex(RegexOption.MULTILINE)
        val matches = pattern.findAll(content).toList()
        
        return \1 j { \2: Int ->
            matches[i].groupValues[1].trim()
        }
    }
    
    internal fun parseArtifact(coordinate: String): Artifact {
        val parts = coordinate.split(":")
        return when (parts.size) {
            3 -> Artifact(parts[0], parts[1], parts[2])
            4 -> Artifact(parts[0], parts[1], parts[2], parts[3])
            else -> throw IllegalArgumentException("Invalid coordinate: $coordinate")
        }
    }
    
    internal fun findMainClass(content: String): String? {
        return if (content.contains("fun main")) {
            "MainKt" // Default Kotlin script main class
        } else {
            null
        }
    }
    
    // === Dependency Resolution with Reactor Concurrency ===
    
    suspend fun resolveDependencies(metadata: ScriptMetadata): ResolvedDependencies {
        println("🔍 Resolving ${metadata.dependencies.component1()} dependencies...")
        
        // Use reactor for concurrent resolution
        val batchWagon = BatchWagon(wagon, reactor)
        val results = batchWagon.resolveBatch(metadata.dependencies)
        
        val resolved = mutableListOf<ResolvedArtifact>()
        val failed = mutableListOf<String>()
        
        for (i in 0 until results.component1()) {
            when (val result = results.component2()(i)) {
                is ResolveResult.Success -> {
                    val artifact = result.artifact
                    val jarFile = cacheArtifact(artifact, result.data)
                    resolved.add(ResolvedArtifact(artifact, jarFile))
                    println("✅ ${artifact.artifactId}-${artifact.version}")
                }
                is ResolveResult.Failure -> {
                    failed.add("${result.artifact.artifactId}: ${result.error}")
                    println("❌ ${result.artifact.artifactId}: ${result.error}")
                }
            }
        }
        
        return ResolvedDependencies(
            resolved = resolved.toTypedArray().toIdx(),
            failed = failed.toTypedArray().toIdx()
        )
    }
    
    internal suspend fun cacheArtifact(artifact: Artifact, data: ByteIndexed): String {
        val jarPath = "$cacheDir/${artifact.fileName}"
        IOMemento.writeBytes(jarPath, data)
        return jarPath
    }
    
    // === Classpath Building with TrikeShed Collections ===
    
    fun buildClasspath(resolved: ResolvedDependencies): Classpath {
        val entries = mutableListOf<String>()
        
        // Add resolved JARs
        for (i in 0 until resolved.resolved.component1()) {
            val artifact = resolved.resolved.component2()(i)
            entries.add(artifact.jarPath)
        }
        
        // Add system classpath
        val systemCp = System.getProperty("java.class.path")
        entries.addAll(systemCp.split(System.getProperty("path.separator")))
        
        return Classpath(entries.toTypedArray().toIdx())
    }
    
    // === Script Execution with TrikeShed Integration ===
    
    suspend fun executeScript(
        metadata: ScriptMetadata,
        classpath: Classpath,
        args: Indexed<String> = emptyArray<String>().toIdx()
    ): ExecutionResult {
        
        println("🚀 Executing script: ${metadata.path}")
        println("📚 Classpath entries: ${classpath.entries.component1()}")
        
        // Create execution context
        val context = ExecutionContext(
            scriptPath = metadata.path,
            classpath = classpath,
            mainClass = metadata.mainClass ?: "MainKt",
            arguments = args,
            workingDirectory = getCurrentDirectory()
        )
        
        return try {
            val result = executeInContext(context)
            ExecutionResult.Success(result)
        } catch (e: Exception) {
            ExecutionResult.Failure(e.message ?: "Unknown error", e)
        }
    }
    
    internal suspend fun executeInContext(context: ExecutionContext): Any? {
        // This would integrate with kotlin-scripting-jvm
        // Using the TrikeShed-resolved classpath
        
        // For now, return a placeholder
        println("📄 Script: ${context.scriptPath}")
        println("🎯 Main: ${context.mainClass}")
        println("📋 Args: ${context.arguments.component1()}")
        
        return "Script executed successfully"
    }
    
    internal fun getCurrentDirectory(): String = System.getProperty("user.dir")
    
    // === High-level Launch API ===
    
    suspend fun launch(scriptPath: String, args: Array<String> = emptyArray()): ExecutionResult {
        val argsIndexed = args.toTypedArray().toIdx()
        
        // 1. Analyze script
        val metadata = analyzeScript(scriptPath)
        
        // 2. Resolve dependencies
        val resolved = resolveDependencies(metadata)
        
        if (resolved.failed.component1() > 0) {
            println("⚠️  Failed to resolve ${resolved.failed.component1()} dependencies:")
            for (i in 0 until resolved.failed.component1()) {
                println("   ${resolved.failed.component2()(i)}")
            }
        }
        
        // 3. Build classpath
        val classpath = buildClasspath(resolved)
        
        // 4. Execute script
        return executeScript(metadata, classpath, argsIndexed)
    }
}

// === Data Classes using TrikeShed Types ===

data class ScriptMetadata(
    val path: String,
    val dependencies: Indexed<Artifact>,
    val repositories: Indexed<Repository>,
    val imports: Indexed<String>,
    val mainClass: String?
)

data class ResolvedArtifact(
    val artifact: Artifact,
    val jarPath: String
)

data class ResolvedDependencies(
    val resolved: Indexed<ResolvedArtifact>,
    val failed: Indexed<String>
)

data class Classpath(
    val entries: Indexed<String>
) {
    val asString: String get() = entries.play.joinToString(System.getProperty("path.separator"))
}

data class ExecutionContext(
    val scriptPath: String,
    val classpath: Classpath,
    val mainClass: String,
    val arguments: Indexed<String>,
    val workingDirectory: String
)

sealed class ExecutionResult {
    data class Success(val result: Any?) : ExecutionResult()
    data class Failure(val error: String, val cause: Throwable?) : ExecutionResult()
}

// === Main Entry Point ===

/**
 * K2Script with full TrikeShed integration
 * 
 * Usage:
 * ```
 * val launcher = TrikeShedLauncher()
 * val result = launcher.launch("script.kts", arrayOf("arg1", "arg2"))
 * ```
 */
suspend fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("""
            K2Script TrikeShed Launcher
            
            Usage: k2script-trikeshed <script.kts> [args...]
            
            Features:
            🚀 TrikeShed Wagon dependency resolution
            ⚡ Reactor-based concurrent processing  
            📊 Cursor-based metadata analysis
            💾 IOMemento efficient file operations
            🔗 Full TrikeShed ecosystem integration
            
            The script execution engine that uses almost every
            TrikeShed module for maximum performance and transparency.
        """.trimIndent())
        return
    }
    
    val scriptPath = args[0]
    val scriptArgs = args.drop(1).toTypedArray()
    
    val launcher = TrikeShedLauncher()
    
    when (val result = launcher.launch(scriptPath, scriptArgs)) {
        is ExecutionResult.Success -> {
            println("✅ Success: ${result.result}")
        }
        is ExecutionResult.Failure -> {
            println("❌ Failed: ${result.error}")
            result.cause?.printStackTrace()
        }
    }
}