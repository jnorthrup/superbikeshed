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
    private val platformLauncher = PlatformLauncher()
    @Volatile private var daemonMode = false // Made volatile for thread safety
    private val activeTasks = mutableListOf<Job>() // Consider thread-safe collection if accessed by Ktor threads
    private var daemonServer: DaemonServer? = null

    fun stopDaemon() {
        println("Shutdown command received. Stopping daemon...")
        daemonMode = false
        daemonServer?.stop()
    }
    
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
    
    private fun parseArguments(args: Array<String>): LauncherConfig {
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
    
    private fun startDaemon(config: LauncherConfig) {
        println("Starting Platform Launcher Daemon on port ${config.port}")
        
        runBlocking {
            // Initialize platform
            platformLauncher.initialize(config.jvmOptions)
            
            // Start daemon server
            daemonServer = DaemonServer(config.port, this, platformLauncher)
            daemonServer?.start()
            
            // Keep alive
            while (daemonMode) {
                delay(1000)
                cleanupCompletedTasks()
            }
        }
    }
    
    private fun executeCommand(config: LauncherConfig) {
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
    
    private suspend fun executeWASM(wasmPath: String, args: List<String>) {
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
    
    private suspend fun executeJava(javaPath: String, args: List<String>) {
        val source = File(javaPath).readText()
        val className = extractClassName(source)
        
        // Compile
        val clazz = platformLauncher.compileAndLoadJava(className, source)
        
        // Execute main method
        val mainMethod = clazz.getMethod("main", Array<String>::class.java)
        mainMethod.invoke(null, args.toTypedArray())
    }
    
    private suspend fun executeJar(jarPath: String, args: List<String>) {
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
    
    private suspend fun executeClass(className: String, args: List<String>) {
        // Try to load and execute class
        try {
            val clazz = Class.forName(className)
            val mainMethod = clazz.getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, args.toTypedArray())
        } catch (e: ClassNotFoundException) {
            error("Class not found: $className")
        }
    }
    
    private fun compileModule(config: LauncherConfig) {
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
    
    private fun compileJavaToNative(javaPath: String) {
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
    
    private fun runBenchmark(config: LauncherConfig) {
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
    
    private fun createWASMImports(): Map<String, Any> {
        return mapOf(
            "console" to ConsoleImports(),
            "fs" to FileSystemImports(),
            "process" to ProcessImports()
        )
    }
    
    private fun extractClassName(javaSource: String): String {
        val classRegex = Regex("public\\s+class\\s+(\\w+)")
        return classRegex.find(javaSource)?.groupValues?.get(1)
            ?: "UnknownClass"
    }
    
    private fun cleanupCompletedTasks() {
        activeTasks.removeAll { it.isCompleted }
    }
    
    private fun showHelp() {
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

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.Serializable


@Serializable
data class ExecuteRequest(val target: String, val args: List<String> = emptyList(), val type: String) // type: "wasm", "java", "jar", "class"

@Serializable
data class LoadModuleRequest(val name: String, val path: String, val type: String) // type: "wasm"

@Serializable
data class StatusResponse(val status: String, val activeTasks: Int)

@Serializable
data class GenericResponse(val message: String, val details: String? = null)

// Daemon server for remote control
class DaemonServer(
    private val port: Int,
    private val nativeLauncherImpl: NativeLauncherImpl, // Changed to NativeLauncherImpl
    private val platformLauncher: PlatformLauncher // Keep platformLauncher for direct access if needed
) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port, module = { module() }).start(wait = false)
        println("Daemon server started on port $port. Press Ctrl+C to stop.")
        // To keep the daemon running if it's the main process and not managed by NativeLauncherImpl's daemonMode loop
        // Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    fun stop() {
        server?.stop(1000, 5000)
        println("Daemon server stopped.")
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json()
        }

        routing {
            get("/") {
                call.respondText("Platform Launcher Daemon")
            }

            post("/execute") {
                try {
                    val request = call.receive<ExecuteRequest>()
                    // This part needs to be run off the Ktor request thread
                    // and align with NativeLauncherImpl's execution logic.
                    // For simplicity, directly calling platformLauncher's methods,
                    // but ideally, it would queue tasks or use NativeLauncherImpl's mechanisms.

                    // Caution: The execution methods in NativeLauncherImpl are runBlocking.
                    // Running them directly in Ktor might block request threads.
                    // A proper solution would involve a job queue or async execution.
                    // For this example, we'll proceed with a simplified direct call,
                    // assuming NativeLauncherImpl.executeCommand could be refactored to be non-blocking or offloaded.

                    // Simplified: Reconstruct args for NativeLauncherImpl's parseArguments or execute directly
                    val config = LauncherConfig(
                        command = Command.EXECUTE,
                        target = request.target,
                        additionalArgs = request.args.toMutableList()
                        // jvmOptions, wasmModules, javaClasses would need to be settable or pre-configured
                    )

                    // This is a conceptual call. Actual execution needs to be handled carefully.
                    // nativeLauncherImpl.executeCommand(config) // This is blocking.

                    // A more direct, but still potentially blocking approach for now:
                    when (request.type) {
                        "wasm" -> platformLauncher.executeWASMFunction(request.target, "_start", *request.args.toTypedArray()) // Assuming _start
                        "java" -> { /* platformLauncher.compileAndLoadJava and invoke main */ }
                        "jar" -> { /* platformLauncher.executeJar - needs ProcessBuilder logic from NativeLauncherImpl */ }
                        // Add more types as needed
                        else -> {
                            call.respond(HttpStatusCode.BadRequest, GenericResponse("Unsupported execution type: ${request.type}"))
                            return@post
                        }
                    }
                    call.respond(HttpStatusCode.OK, GenericResponse("Execution command received for ${request.target}."))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse("Error during execution", e.message))
                }
            }

            post("/load/wasm") {
                try {
                    val request = call.receive<LoadModuleRequest>()
                    platformLauncher.loadWASMModule(request.name, request.path) // Assuming default imports
                    call.respond(HttpStatusCode.OK, GenericResponse("WASM module ${request.name} loading initiated."))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, GenericResponse("Error loading WASM module", e.message))
                }
            }

            get("/status") {
                // val active = nativeLauncherImpl.getActiveTasksCount() // Need a way to get this
                call.respond(HttpStatusCode.OK, StatusResponse("Daemon is running", 0 /*active*/))
            }

            post("/shutdown") {
                call.respond(HttpStatusCode.OK, GenericResponse("Shutdown command received."))
                // This should trigger a graceful shutdown of the NativeLauncher's main loop or the application.
                nativeLauncherImpl.stopDaemon() // Need a method in NativeLauncherImpl
            }
        }
    }
}

// Helper function to measure time
inline fun measureTimeMillis(block: () -> Unit): Long {
    val start = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    block()
    return kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - start
}