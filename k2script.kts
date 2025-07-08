#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
@file:DependsOn("org.jetbrains.kotlin:kotlin-scripting-jvm:2.2.0")
@file:DependsOn("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.2.0")
@file:DependsOn("org.jetbrains.kotlin:kotlin-scripting-dependencies:2.2.0")
@file:DependsOn("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven:2.2.0")

import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.*
import kotlin.script.experimental.dependencies.*
import kotlin.script.experimental.dependencies.maven.*
import kotlin.system.exitProcess

/**
 * K2Script - Unified Kotlin Script Runner
 * 
 * Features:
 * - Direct script execution with dependency resolution
 * - Script installation to destination
 * - Maven dependency management
 * - Kotlin scripting API integration
 */

// === Core Data Types ===
data class MavenCoordinate(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    companion object {
        fun parse(coord: String): MavenCoordinate {
            val parts = coord.split(":")
            require(parts.size == 3) { "Invalid coordinate: $coord" }
            return MavenCoordinate(parts[0], parts[1], parts[2])
        }
    }
    
    val path: String get() = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version.jar"
    val fileName: String get() = "$artifactId-$version.jar"
}

// === Dependency Resolution ===
class DependencyResolver(
    private val cacheDir: File = File(System.getProperty("user.home"), ".k2script/cache")
) {
    init { cacheDir.mkdirs() }
    
    fun resolve(coordinate: String): File {
        val coord = MavenCoordinate.parse(coordinate)
        val jarFile = File(cacheDir, coord.fileName)
        
        if (!jarFile.exists()) {
            println("📦 Downloading: $coordinate")
            val url = URL("https://repo1.maven.org/maven2/${coord.path}")
            url.openStream().use { input ->
                jarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        return jarFile
    }
}

// === Script Parsing ===
data class ParsedScript(
    val dependencies: List<String>,
    val repositories: List<String>,
    val content: String
)

fun parseScript(scriptFile: File): ParsedScript {
    val lines = scriptFile.readLines()
    val dependencies = mutableListOf<String>()
    val repositories = mutableListOf<String>()
    val cleanedLines = mutableListOf<String>()
    
    for (line in lines) {
        when {
            line.trim().startsWith("@file:DependsOn") -> {
                val dep = line.substringAfter('"').substringBefore('"')
                dependencies.add(dep)
            }
            line.trim().startsWith("@file:Repository") -> {
                val repo = line.substringAfter('"').substringBefore('"')
                repositories.add(repo)
            }
            line.trim() == "#!/usr/bin/env kotlin" -> {
                // Skip shebang
            }
            else -> cleanedLines.add(line)
        }
    }
    
    return ParsedScript(dependencies, repositories, cleanedLines.joinToString("\n"))
}

// === Script Execution ===
class ScriptExecutor {
    private val scriptingHost = BasicJvmScriptingHost()
    private val resolver = DependencyResolver()
    
    suspend fun execute(scriptFile: File, args: Array<String>): Int {
        val parsed = parseScript(scriptFile)
        
        // Resolve dependencies
        val jars = parsed.dependencies.map { dep ->
            resolver.resolve(dep)
        }
        
        // Create compilation configuration
        val compilationConfig = createJvmCompilationConfigurationFromTemplate<Any> {
            jvm {
                dependenciesFromClassloader(classLoader = javaClass.classLoader)
                dependenciesFromClasspath(*jars.toTypedArray())
            }
            defaultImports(
                "java.io.*",
                "kotlin.io.*",
                "kotlinx.coroutines.*"
            )
        }
        
        // Create evaluation configuration
        val evaluationConfig = createJvmEvaluationConfigurationFromTemplate<Any> {
            jvm {
                baseClassLoader(Thread.currentThread().contextClassLoader)
            }
            providedProperties("args" to args)
        }
        
        // Execute script
        val result = scriptingHost.eval(
            scriptFile.toScriptSource(),
            compilationConfig,
            evaluationConfig
        )
        
        return when (result) {
            is ResultWithDiagnostics.Success -> 0
            is ResultWithDiagnostics.Failure -> {
                result.reports.forEach { report ->
                    System.err.println("${report.severity}: ${report.message}")
                    report.location?.let { loc ->
                        System.err.println("  at ${loc.start.line}:${loc.start.col}")
                    }
                }
                1
            }
        }
    }
}

// === Installation ===
fun installScript(scriptPath: String, destination: String) {
    val scriptFile = File(scriptPath).absoluteFile
    require(scriptFile.exists()) { "Script not found: $scriptPath" }
    
    val destDir = File(destination).absoluteFile
    destDir.mkdirs()
    
    val scriptName = scriptFile.nameWithoutExtension
    val destScript = File(destDir, scriptName)
    
    // Create wrapper script that uses k2script.kts
    val k2scriptPath = File(System.getProperty("user.dir"), "k2script.kts").absolutePath
    
    destScript.writeText("""#!/bin/bash
# Generated by k2script
exec kotlin "$k2scriptPath" "${scriptFile.absolutePath}" "$@"
""")
    
    destScript.setExecutable(true)
    println("✅ Installed $scriptName to ${destScript.absolutePath}")
    println("   Script: ${scriptFile.absolutePath}")
    println("   Runner: $k2scriptPath")
}

// === Main Entry Point ===
suspend fun main(args: Array<String>) = coroutineScope {
    when {
        args.isEmpty() -> {
            println("K2Script - Kotlin Script Runner")
            println()
            println("Usage:")
            println("  k2script <script.kts> [args...]      Run a Kotlin script")
            println("  k2script --install <dest> <script>   Install script to destination")
            println()
            println("Examples:")
            println("  k2script hello.kts")
            println("  k2script --install ~/.local hello.kts")
        }
        
        args[0] == "--install" -> {
            require(args.size >= 3) { "Usage: k2script --install <destination> <script.kts>" }
            installScript(args[2], args[1])
        }

        args[0] == "--kmp" -> {
            require(args.size >= 4) { "Usage: k2script --kmp <module> <target> <action> [args...]" }
            handleKmpCommand(args.drop(1).toTypedArray())
        }
        
        else -> {
            // Execute script
            val scriptFile = File(args[0])
            require(scriptFile.exists()) { "Script not found: ${args[0]}" }
            
            val executor = ScriptExecutor()
            val exitCode = executor.execute(scriptFile, args.drop(1).toTypedArray())
            exitProcess(exitCode)
        }
    }
}

// Handle KMP commands
suspend fun handleKmpCommand(kmpArgs: Array<String>) {
    val module = kmpArgs[0]
    val target = kmpArgs[1]
    val action = kmpArgs[2]
    val remainingArgs = kmpArgs.drop(3)

    val gradleCommand = when (action) {
        "run" -> {
            when (target) {
                "jvm" -> "./gradlew :$module:run" // Default JVM run task
                "jsBrowser" -> "./gradlew :$module:jsBrowserRun"
                "jsNode" -> "./gradlew :$module:jsNodeRun"
                "macosX64" -> "./gradlew :$module:runDebugExecutableMacosX64"
                "macosArm64" -> "./gradlew :$module:runDebugExecutableMacosArm64"
                "linuxX64" -> "./gradlew :$module:runDebugExecutableLinuxX64"
                "linuxArm64" -> "./gradlew :$module:runDebugExecutableLinuxArm64"
                else -> throw IllegalArgumentException("Unsupported KMP run target: $target")
            }
        }
        "build" -> {
            when (target) {
                "jvm" -> "./gradlew :$module:assembleJvm"
                "js" -> "./gradlew :$module:assembleJs"
                "macosX64" -> "./gradlew :$module:assembleMacosX64"
                "macosArm64" -> "./gradlew :$module:assembleMacosArm64"
                "linuxX64" -> "./gradlew :$module:assembleLinuxX64"
                "linuxArm64" -> "./gradlew :$module:assembleLinuxArm64"
                else -> throw IllegalArgumentException("Unsupported KMP build target: $target")
            }
        }
        "test" -> {
            when (target) {
                "jvm" -> "./gradlew :$module:jvmTest"
                "jsBrowser" -> "./gradlew :$module:jsBrowserTest"
                "jsNode" -> "./gradlew :$module:jsNodeTest"
                "macosX64" -> "./gradlew :$module:macosX64Test"
                "macosArm64" -> "./gradlew :$module:macosArm64Test"
                "linuxX64" -> "./gradlew :$module:linuxX64Test"
                "linuxArm64" -> "./gradlew :$module:linuxArm64Test"
                else -> throw IllegalArgumentException("Unsupported KMP test target: $target")
            }
        }
        else -> throw IllegalArgumentException("Unsupported KMP action: $action. Must be 'run', 'build', or 'test'.")
    }

    println("🚀 Running KMP command: $gradleCommand ${remainingArgs.joinToString(" ")}")
    val process = ProcessBuilder(*gradleCommand.split(" ").toTypedArray(), *remainingArgs.toTypedArray())
        .inheritIO()
        .start()
    val exitCode = process.waitFor()
    exitProcess(exitCode)
}

// Bootstrap
runBlocking {
    main(args)
}