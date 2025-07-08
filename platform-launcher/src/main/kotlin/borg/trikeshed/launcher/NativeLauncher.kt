@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")
package borg.trikeshed.launcher

import kotlinx.coroutines.*
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Native Launcher - Entry point for native image builds
 * 
 * This launcher can be compiled to a native executable using GraalVM native-image
 * and provides a lightweight entry point that can spawn JVM instances and manage
 * WASM execution without requiring a full JVM at startup.
 */
object NativeLauncher {
    
    @JvmStatic
    fun main(args: Array<String>) {
        val launcher = NativeLauncherImpl()
        launcher.run(args)
    }
}

class NativeLauncherImpl {
    internal val platformLauncher = PlatformLauncher()
    internal var daemonMode = false
    internal val activeTasks = mutableListOf<Job>()
    
    fun run(args: Array<String>) {
        // Parse command line arguments
        val config = parseArguments(args)
        
        when (config.command) {
            Command.DAEMON -> startDaemon(config)
            Command.EXECUTE -> executeCommand(config)
            Command.COMPILE -> compileModule(config)
            Command.BENCHMARK -> runBenchmark(config)
            else -> showHelp()
        }
    }
    
    internal fun parseArguments(args: Array<String>): LauncherConfig {
        val config = LauncherConfig()
        var i = 0
        
        while (i < args.size) {
            when (val arg = args[i]) {
                "--daemon", "-d" -> {
                    config.command = Command.DAEMON
                    daemonMode = true
                }
                "--execute", "-e" -> {
                    config.command = Command.EXECUTE
                    config.target = args.getOrNull(++i) ?: error("Missing target for execute")
                }
                "--compile", "-c" -> {
                    config.command = Command.COMPILE
                    config.target = args.getOrNull(++i) ?: error("Missing target for compile")
                }
                "--benchmark", "-b" -> {
                    config.command = Command.BENCHMARK
                }
                "--jvm-options" -> {
                    config.jvmOptions = args.getOrNull(++i)?.split(",") ?: emptyList()
                }
                "--wasm" -> {
                    config.wasmModules.add(args.getOrNull(++i) ?: error("Missing WASM module"))
                }
                "--java" -> {
                    config.javaClasses.add(args.getOrNull(++i) ?: error("Missing Java class"))
                }
                "--port" -> {
                    config.port = args.getOrNull(++i)?.toIntOrNull() ?: 8080
                }
                "--help", "-h" -> {
                    showHelp()
                    exitProcess(0)
                }
                else -> {
                    if (arg.startsWith("-")) {
                        error("Unknown option: $arg")
                    }
                    config.additionalArgs.add(arg)
                }
            }
            i++
        }
        
        return config
    }
    
    internal fun startDaemon(config: LauncherConfig) {
        println("Starting Platform Launcher Daemon on port ${config.port}")
        
        runBlocking {
            // Initialize platform
            platformLauncher.initialize(config.jvmOptions)
            
            // Start daemon server
            val server = DaemonServer(config.port, platformLauncher)
            server.start()
            
            // Keep alive
            while (daemonMode) {
                delay(1000)
                cleanupCompletedTasks()
            }
        }
    }
    
    internal fun executeCommand(config: LauncherConfig) {
        runBlocking {
            platformLauncher.initialize(config.jvmOptions)
            
            when {
                config.target?.endsWith(".wasm") == true -> {
                    // Execute WASM module
                    executeWASM(config.target!!, config.additionalArgs)
                }
                config.target?.endsWith(".java") == true -> {
                    // Compile and execute Java
                    executeJava(config.target!!, config.additionalArgs)
                }
                config.target?.endsWith(".jar") == true -> {
                    // Execute JAR
                    executeJar(config.target!!, config.additionalArgs)
                }
                else -> {
                    // Try to execute as class name
                    executeClass(config.target ?: "Main", config.additionalArgs)
                }
            }
        }
    }
    
    internal suspend fun executeWASM(wasmPath: String, args: List<String>) {
        val moduleName = File(wasmPath).nameWithoutExtension
        
        // Load module
        platformLauncher.loadWASMModule(moduleName, wasmPath, createWASMImports())
        
        // Execute main function
        val result = platformLauncher.executeWASMFunction(
            moduleName,
            "_start",
            *args.toTypedArray()
        )
        
        println("WASM execution result: $result")
    }
    
    internal suspend fun executeJava(javaPath: String, args: List<String>) {
        val source = File(javaPath).readText()
        val className = extractClassName(source)
        
        // Compile
        val clazz = platformLauncher.compileAndLoadJava(className, source)
        
        // Execute main method
        val mainMethod = clazz.getMethod("main", Array<String>::class.java)
        mainMethod.invoke(null, args.toTypedArray())
    }
    
    internal suspend fun executeJar(jarPath: String, args: List<String>) {
        // Create child process for JAR execution
        val pb = ProcessBuilder(
            "java",
            "-jar",
            jarPath,
            *args.toTypedArray()
        )
        
        pb.inheritIO()
        val process = pb.start()
        process.waitFor()
    }
    
    internal suspend fun executeClass(className: String, args: List<String>) {
        // Try to load and execute class
        try {
            val clazz = Class.forName(className)
            val mainMethod = clazz.getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, args.toTypedArray())
        } catch (e: ClassNotFoundException) {
            error("Class not found: $className")
        }
    }
    
    internal fun compileModule(config: LauncherConfig) {
        when {
            config.target?.endsWith(".wasm") == true -> {
                println("WASM compilation not implemented yet")
            }
            config.target?.endsWith(".java") == true -> {
                compileJavaToNative(config.target!!)
            }
            else -> {
                error("Unknown compilation target: ${config.target}")
            }
        }
    }
    
    internal fun compileJavaToNative(javaPath: String) {
        println("Compiling $javaPath to native...")
        
        // Would invoke native-image with appropriate options
        val pb = ProcessBuilder(
            "native-image",
            "-cp", System.getProperty("java.class.path"),
            javaPath
        )
        
        pb.inheritIO()
        val process = pb.start()
        val exitCode = process.waitFor()
        
        if (exitCode == 0) {
            println("Compilation successful")
        } else {
            error("Compilation failed with exit code: $exitCode")
        }
    }
    
    internal fun runBenchmark(config: LauncherConfig) {
        println("Running Platform Launcher Benchmark")
        
        runBlocking {
            platformLauncher.initialize(config.jvmOptions)
            
            // Benchmark JVM startup
            val jvmStartTime = measureTimeMillis {
                platformLauncher.compileAndLoadJava(
                    "BenchmarkTest",
                    """
                    public class BenchmarkTest {
                        public static void main(String[] args) {
                            System.out.println("Benchmark JVM started");
                        }
                    }
                    """.trimIndent()
                )
            }
            println("JVM startup time: ${jvmStartTime}ms")
            
            // Benchmark WASM loading
            val wasmLoadTime = measureTimeMillis {
                // Would load a test WASM module
            }
            println("WASM load time: ${wasmLoadTime}ms")
            
            // Benchmark interop
            val interopTime = measureTimeMillis {
                repeat(1000) {
                    // Would perform JVM-WASM interop calls
                }
            }
            println("Interop time (1000 calls): ${interopTime}ms")
        }
    }
    
    internal fun createWASMImports(): Map<String, Any> {
        return mapOf(
            "console" to ConsoleImports(),
            "fs" to FileSystemImports(),
            "process" to ProcessImports()
        )
    }
    
    internal fun extractClassName(javaSource: String): String {
        val classRegex = Regex("public\\s+class\\s+(\\w+)")
        return classRegex.find(javaSource)?.groupValues?.get(1)
            ?: "UnknownClass"
    }
    
    internal fun cleanupCompletedTasks() {
        activeTasks.removeAll { it.isCompleted }
    }
    
    internal fun showHelp() {
        println("""
            Platform Launcher - Dynamic JVM and WASM execution
            
            Usage: platform-launcher [options] [target]
            
            Commands:
              --daemon, -d          Start in daemon mode
              --execute, -e <file>  Execute a file (.wasm, .java, .jar, or class name)
              --compile, -c <file>  Compile to native
              --benchmark, -b       Run performance benchmarks
              --help, -h           Show this help
            
            Options:
              --jvm-options <opts>  Comma-separated JVM options
              --wasm <module>       Load additional WASM module
              --java <class>        Load additional Java class
              --port <port>         Daemon port (default: 8080)
            
            Examples:
              platform-launcher -e example.wasm
              platform-launcher -e Main.java arg1 arg2
              platform-launcher -d --port 9090
              platform-launcher -c MyApp.java
        """.trimIndent())
    }
}

// Configuration data classes
data class LauncherConfig(
    var command: Command = Command.HELP,
    var target: String? = null,
    var jvmOptions: List<String> = emptyList(),
    val wasmModules: MutableList<String> = mutableListOf(),
    val javaClasses: MutableList<String> = mutableListOf(),
    var port: Int = 8080,
    val additionalArgs: MutableList<String> = mutableListOf()
)

enum class Command {
    DAEMON,
    EXECUTE,
    COMPILE,
    BENCHMARK,
    HELP
}

// WASM import implementations
class ConsoleImports {
    fun log(message: String) = println("[WASM] $message")
    fun error(message: String) = System.err.println("[WASM ERROR] $message")
}

class FileSystemImports {
    fun readFile(path: String): ByteArray = File(path).readBytes()
    fun writeFile(path: String, data: ByteArray) = File(path).writeBytes(data)
    fun exists(path: String): Boolean = File(path).exists()
}

class ProcessImports {
    fun exit(code: Int) = exitProcess(code)
    fun env(name: String): String? = System.getenv(name)
    fun args(): Array<String> = emptyArray() // Would be set from context
}

// Daemon server for remote control
class DaemonServer(
    internal val port: Int,
    internal val launcher: PlatformLauncher
) {
    fun start() {
        // Simple HTTP server for daemon control
        println("Daemon server started on port $port")
        
        // Would implement actual server using Ktor or similar
        // Endpoints:
        // POST /execute - Execute WASM or Java
        // POST /load - Load module
        // GET /status - Get daemon status
        // POST /shutdown - Shutdown daemon
    }
}

// Helper function to measure time
inline fun measureTimeMillis(block: () -> Unit): Long {
    val start = System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    block()
    return System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - start
}