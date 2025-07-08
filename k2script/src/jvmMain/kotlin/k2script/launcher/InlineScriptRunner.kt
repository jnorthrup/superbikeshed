package k2script.launcher

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths
import javax.script.ScriptEngineManager
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.*

/**
 * Inline Kotlin Script Runner
 * 
 * Downloads dependencies directly without Maven/Gradle
 * Uses Kotlin scripting API for compilation and execution
 */
class InlineScriptRunner {
    
    private val cacheDir = Paths.get(System.getProperty("user.home"), ".k2script", "deps")
    private val mavenCentral = "https://repo1.maven.org/maven2"
    
    init {
        Files.createDirectories(cacheDir)
    }
    
    // Convert dependency coordinates to URL
    private fun dependencyToUrl(dependency: String): String {
        val parts = dependency.split(":")
        if (parts.size < 3) throw IllegalArgumentException("Invalid dependency: $dependency")
        
        val (group, artifact, version) = parts
        val groupPath = group.replace('.', '/')
        
        return "$mavenCentral/$groupPath/$artifact/$version/$artifact-$version.jar"
    }
    
    // Download dependency if not cached
    private suspend fun downloadDependency(dependency: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val parts = dependency.split(":")
            if (parts.size < 3) return@withContext Result.failure(IllegalArgumentException("Invalid dependency: $dependency"))
            
            val (group, artifact, version) = parts
            val jarName = "$artifact-$version.jar"
            val cachedFile = cacheDir.resolve(jarName).toFile()
            
            if (cachedFile.exists()) {
                return@withContext Result.success(cachedFile)
            }
            
            val url = dependencyToUrl(dependency)
            println("Downloading: $dependency")
            
            URL(url).openStream().use { input ->
                cachedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Result.success(cachedFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Download all dependencies
    private suspend fun downloadAllDependencies(dependencies: Indexed<String>): Result<Indexed<File>> = coroutineScope {
        val results = (0 until dependencies.a).map { i ->
            async { downloadDependency(dependencies.b(i)) }
        }.awaitAll()
        
        val failures = results.filter { it.isFailure }
        if (failures.isNotEmpty()) {
            return@coroutineScope Result.failure(
                Exception("Failed to download ${failures.size} dependencies")
            )
        }
        
        val jars = results.map { it.getOrThrow() }
        Result.success(jars.toIndexed())
    }
    
    // Create script compilation configuration
    private fun createCompilationConfig(
        classpath: List<File>,
        compilerOpts: List<String>,
        hasSerializableDependency: Boolean
    ): ScriptCompilationConfiguration {
        return createJvmCompilationConfigurationFromTemplate<Any> {
            jvm {
                dependenciesFromClasspath(*classpath.toTypedArray())
            }
            
            // Add serialization plugin if needed
            if (hasSerializableDependency) {
                val pluginPath = findSerializationPlugin()
                if (pluginPath != null) {
                    compilerOptions.append("-Xplugin=$pluginPath")
                }
            }
            
            compilerOptions(compilerOpts)
            ide {
                acceptedLocations(ScriptAcceptedLocation.Everywhere)
            }
        }
    }
    
    private fun findSerializationPlugin(): String? {
        // Look for serialization plugin in Kotlin installation
        val kotlinHome = System.getenv("KOTLIN_HOME") ?: "/usr/local/lib/kotlin"
        val pluginPath = "$kotlinHome/lib/kotlinx-serialization-compiler-plugin.jar"
        return if (File(pluginPath).exists()) pluginPath else null
    }
    
    // Create script evaluation configuration
    private fun createEvaluationConfig(
        classpath: List<File>,
        args: Array<String>
    ): ScriptEvaluationConfiguration {
        return createJvmEvaluationConfigurationFromTemplate<Any> {
            jvm {
                updateClasspath(classpath)
            }
            constructorArgs(*args)
            enableScriptsInstancesSharing()
        }
    }
    
    suspend fun runScript(
        scriptFile: File,
        args: Array<String> = emptyArray()
    ): Result<Any?> = withContext(Dispatchers.IO) {
        try {
            // Parse annotations
            val launcher = MavenScriptLauncher()
            val annotations = launcher.parseAnnotations(scriptFile)
            
            // Download dependencies
            println("Resolving dependencies...")
            val jarsResult = downloadAllDependencies(annotations.dependencies)
            if (jarsResult.isFailure) {
                return@withContext Result.failure(jarsResult.exceptionOrNull()!!)
            }
            
            val jars = jarsResult.getOrThrow().toList()
            
            // Add Kotlin runtime JARs
            val kotlinJars = listOf(
                "kotlin-stdlib",
                "kotlin-script-runtime",
                "kotlinx-coroutines-core"
            ).mapNotNull { name ->
                this::class.java.classLoader.getResource("$name.jar")?.file?.let { File(it) }
            }
            
            val allJars = jars + kotlinJars
            
            // Check if we need serialization plugin
            val needsSerializationPlugin = annotations.dependencies.toList().any { dep ->
                dep.contains("kotlinx-serialization")
            }
            
            // Create configurations
            val compilationConfig = createCompilationConfig(
                allJars, 
                annotations.compilerOpts.toList(),
                needsSerializationPlugin
            )
            val evaluationConfig = createEvaluationConfig(allJars, args)
            
            // Load and run script
            println("Running script: ${scriptFile.name}")
            val scriptSource = scriptFile.toScriptSource()
            
            val result = BasicJvmScriptingHost().eval(
                scriptSource,
                compilationConfig,
                evaluationConfig
            )
            
            when (result) {
                is ResultValue.Value -> Result.success(result.value)
                is ResultValue.Error -> {
                    val error = result.error
                    when (error) {
                        is ScriptDiagnostic -> {
                            println("Script error: ${error.message}")
                            error.exception?.printStackTrace()
                        }
                        else -> {
                            println("Evaluation error: $error")
                        }
                    }
                    Result.failure(Exception(error.toString()))
                }
                is ResultValue.Unit -> Result.success(Unit)
                else -> Result.success(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

// Simple CLI for the inline runner
suspend fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("""
            K2Script - Kotlin Script Runner
            
            Usage: k2script <script.kts> [args...]
            
            Supported annotations:
              @file:DependsOn("group:artifact:version")
              @file:Repository("url") 
              @file:Include("path")
              @file:CompilerOpts("options")
              @file:KotlinOpts("options")
        """.trimIndent())
        return
    }
    
    val scriptFile = File(args[0])
    if (!scriptFile.exists()) {
        println("Error: Script file not found: ${args[0]}")
        return
    }
    
    val scriptArgs = args.drop(1).toTypedArray()
    val runner = InlineScriptRunner()
    
    runner.runScript(scriptFile, scriptArgs).fold(
        onSuccess = { 
            // Script completed successfully
        },
        onFailure = { error ->
            println("Script execution failed: ${error.message}")
            error.printStackTrace()
        }
    )
}

// Extension functions
private fun <T> List<T>.toIndexed(): Indexed<T> = this.size j { i: Int -> this[i] }
private fun <T> Indexed<T>.toList(): List<T> = (0 until this.a).map { i -> this.b(i) }