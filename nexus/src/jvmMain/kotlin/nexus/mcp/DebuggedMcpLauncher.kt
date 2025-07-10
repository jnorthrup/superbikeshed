package nexus.mcp

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.*
import kotlin.concurrent.thread

/**
 * Debugged MCP Launcher with proper error handling and diagnostics
 */
object DebuggedMcpLauncher {
    
    private val logger = McpLogger("Launcher")
    
    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("🚀 Starting Debugged MCP Launcher")
        
        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            logger.info("Shutdown hook triggered")
            cleanup()
        })
        
        try {
            // Pre-flight checks
            performPreflightChecks()
            
            // Launch components
            runBlocking {
                launchWithDebug()
            }
            
        } catch (e: Exception) {
            logger.error("Fatal error during launch", e)
            System.exit(1)
        }
    }
    
    private fun performPreflightChecks() {
        logger.section("Pre-flight Checks")
        
        // Check Java version
        val javaVersion = System.getProperty("java.version")
        logger.check("Java version: $javaVersion", javaVersion.startsWith("17") || javaVersion.startsWith("21"))
        
        // Check native binary exists
        val nativeBinary = findNativeBinary()
        logger.check("Native binary: ${nativeBinary?.absolutePath ?: "NOT FOUND"}", nativeBinary != null)
        
        // Check shared memory availability
        val shmAvailable = checkSharedMemory()
        logger.check("Shared memory available", shmAvailable)
        
        // Check ports
        val portsAvailable = checkPorts(8000..8010)
        logger.check("Ports 8000-8010 available", portsAvailable)
        
        // Check environment
        val intellijHome = System.getenv("INTELLIJ_HOME")
        logger.check("INTELLIJ_HOME: ${intellijHome ?: "NOT SET"}", intellijHome != null)
        
        if (!nativeBinary?.exists()!!) {
            throw IllegalStateException("Native binary not found. Run: ./gradlew :platform-launcher:nativeBinaries")
        }
    }
    
    private suspend fun launchWithDebug() = coroutineScope {
        logger.section("Component Launch")
        
        // Step 1: Launch native host
        val nativeProcess = launchNativeHost()
        
        // Step 2: Wait for native host to be ready
        waitForNativeHost()
        
        // Step 3: Initialize JVM bridge
        val bridge = initializeJvmBridge()
        
        // Step 4: Launch stratified server
        val server = launchStratifiedServer(bridge)
        
        // Step 5: Verify all components
        verifySystemHealth(nativeProcess, bridge, server)
        
        logger.success("All components launched successfully!")
        
        // Keep running
        awaitCancellation()
    }
    
    private suspend fun launchNativeHost(): Process {
        logger.step("Launching native host...")
        
        val nativeBinary = findNativeBinary()!!
        val logFile = File("native-host.log")
        
        val processBuilder = ProcessBuilder().apply {
            command(nativeBinary.absolutePath)
            environment()["RUST_LOG"] = "debug"
            environment()["URING_MCP_DEBUG"] = "1"
            redirectError(logFile)
            redirectOutput(logFile)
        }
        
        logger.debug("Command: ${processBuilder.command().joinToString(" ")}")
        
        val process = processBuilder.start()
        
        // Monitor process
        launch {
            delay(100) // Give it time to start
            if (!process.isAlive) {
                val exitCode = process.exitValue()
                logger.error("Native host exited immediately with code: $exitCode")
                logger.error("Check native-host.log for details")
                throw RuntimeException("Native host failed to start")
            }
        }
        
        logger.success("Native host PID: ${process.pid()}")
        return process
    }
    
    private suspend fun waitForNativeHost() {
        logger.step("Waiting for native host...")
        
        val maxAttempts = 30
        var attempts = 0
        
        while (attempts < maxAttempts) {
            if (isNativeHostReady()) {
                logger.success("Native host is ready")
                return
            }
            
            delay(1000)
            attempts++
            
            if (attempts % 5 == 0) {
                logger.debug("Still waiting... ($attempts/$maxAttempts)")
            }
        }
        
        throw TimeoutException("Native host did not become ready in ${maxAttempts} seconds")
    }
    
    private fun isNativeHostReady(): Boolean {
        return try {
            // Check if shared memory is created
            val shmPath = Paths.get("/dev/shm/uring_mcp_ccek")
            if (!Files.exists(shmPath)) {
                logger.debug("Shared memory not yet created")
                return false
            }
            
            // Check if control socket is listening
            val socket = java.net.Socket()
            socket.use {
                it.connect(java.net.InetSocketAddress("localhost", 9876), 100)
                true
            }
        } catch (e: Exception) {
            logger.debug("Native host not ready: ${e.message}")
            false
        }
    }
    
    private suspend fun initializeJvmBridge(): IntelliJCcekBridge {
        logger.step("Initializing JVM bridge...")
        
        return try {
            val bridge = IntelliJCcekBridge().apply {
                initialize()
            }
            
            // Test bridge communication
            val testServer = bridge.createMcpServer("test", 0) { request ->
                McpResponse(request.id, "pong", null)
            }
            
            logger.success("JVM bridge initialized, test server on port ${testServer.port}")
            bridge
            
        } catch (e: Exception) {
            logger.error("Failed to initialize JVM bridge", e)
            throw e
        }
    }
    
    private suspend fun launchStratifiedServer(bridge: IntelliJCcekBridge): StratifiedMcpHostingServer {
        logger.step("Launching stratified server...")
        
        val server = StratifiedMcpHostingServer()
        
        try {
            server.initialize()
            
            // Get initial metrics
            val metrics = server.getMetrics()
            logger.info("Initial services: ${metrics.keys.joinToString(", ")}")
            
            return server
            
        } catch (e: Exception) {
            logger.error("Failed to launch stratified server", e)
            throw e
        }
    }
    
    private suspend fun verifySystemHealth(
        nativeProcess: Process,
        bridge: IntelliJCcekBridge,
        server: StratifiedMcpHostingServer
    ) {
        logger.section("System Health Verification")
        
        // Check native process
        logger.check("Native process alive", nativeProcess.isAlive)
        
        // Check shared memory
        val shmSize = Files.size(Paths.get("/dev/shm/uring_mcp_ccek"))
        logger.check("Shared memory size: ${shmSize / 1024 / 1024}MB", shmSize > 0)
        
        // Check services
        val metrics = server.getMetrics()
        logger.check("Services running: ${metrics.size}", metrics.isNotEmpty())
        
        // Perform end-to-end test
        val testPassed = performEndToEndTest(bridge)
        logger.check("End-to-end test", testPassed)
    }
    
    private suspend fun performEndToEndTest(bridge: IntelliJCcekBridge): Boolean {
        return try {
            val testRequest = McpRequest(
                id = "test-${System.currentTimeMillis()}",
                serverName = "test",
                method = "ping",
                params = null
            )
            
            // This should go through the full stack
            val response = bridge.mcpServers["test"]?.handleRequest(testRequest)
            
            response?.result == "pong"
        } catch (e: Exception) {
            logger.error("End-to-end test failed", e)
            false
        }
    }
    
    private fun findNativeBinary(): File? {
        val possiblePaths = listOf(
            "platform-launcher/build/bin/native/releaseExecutable/platform-launcher.kexe",
            "platform-launcher/build/bin/native/debugExecutable/platform-launcher.kexe",
            "build/native/nativeUringMcpHost",
            "/usr/local/bin/uring-mcp-host"
        )
        
        return possiblePaths
            .map { File(it) }
            .firstOrNull { it.exists() && it.canExecute() }
    }
    
    private fun checkSharedMemory(): Boolean {
        return try {
            val shmDir = File("/dev/shm")
            shmDir.exists() && shmDir.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkPorts(range: IntRange): Boolean {
        return range.all { port ->
            try {
                java.net.ServerSocket(port).use { true }
            } catch (e: Exception) {
                logger.debug("Port $port is in use")
                false
            }
        }
    }
    
    private fun cleanup() {
        logger.section("Cleanup")
        
        // Remove shared memory
        try {
            Files.deleteIfExists(Paths.get("/dev/shm/uring_mcp_ccek"))
            logger.info("Removed shared memory")
        } catch (e: Exception) {
            logger.error("Failed to remove shared memory", e)
        }
        
        // Kill native process if needed
        ProcessHandle.allProcesses()
            .filter { it.info().command().orElse("").contains("platform-launcher") }
            .forEach { 
                it.destroyForcibly()
                logger.info("Killed native process ${it.pid()}")
            }
    }
}

/**
 * Simple logger for debugging
 */
class McpLogger(private val name: String) {
    
    fun section(title: String) {
        println("\n${"=".repeat(60)}")
        println("  $title")
        println("=".repeat(60))
    }
    
    fun step(message: String) {
        println("\n▶️  $message")
    }
    
    fun info(message: String) {
        println("ℹ️  [$name] $message")
    }
    
    fun debug(message: String) {
        if (System.getenv("DEBUG") != null) {
            println("🔍 [$name] $message")
        }
    }
    
    fun check(description: String, passed: Boolean) {
        val symbol = if (passed) "✅" else "❌"
        println("$symbol $description")
    }
    
    fun success(message: String) {
        println("✅ [$name] $message")
    }
    
    fun error(message: String, throwable: Throwable? = null) {
        System.err.println("❌ [$name] ERROR: $message")
        throwable?.printStackTrace()
    }
}

/**
 * Exception types
 */
class TimeoutException(message: String) : Exception(message)