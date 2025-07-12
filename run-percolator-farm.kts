#!/usr/bin/env kotlin

import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

// Percolator Farm Management Script
println("""
╔════════════════════════════════════════════╗
║      PERCOLATOR FARM MANAGEMENT            ║
╚════════════════════════════════════════════╝

This script manages percolator farms for distributed content extraction.

Available commands:
  start [options]     - Start a new percolator farm
  status [farm-id]    - Check farm status
  stop [farm-id]      - Stop a farm
  list               - List all running farms
  logs [farm-id]      - Show farm logs
  scale [farm-id] [N] - Scale farm to N nodes
  help               - Show this help

Examples:
  ./run-percolator-farm.kts start --nodes=10
  ./run-percolator-farm.kts status farm-001
  ./run-percolator-farm.kts scale farm-001 20
""".trimIndent())

// Parse command line arguments
val args = args.toList()
if (args.isEmpty()) {
    println("❌ No command specified. Use 'help' for usage information.")
    exitProcess(1)
}

val command = args[0]
val commandArgs = args.drop(1)

when (command) {
    "start" -> startFarm(commandArgs)
    "status" -> checkFarmStatus(commandArgs)
    "stop" -> stopFarm(commandArgs)
    "list" -> listFarms()
    "logs" -> showFarmLogs(commandArgs)
    "scale" -> scaleFarm(commandArgs)
    "help" -> showHelp()
    else -> {
        println("❌ Unknown command: $command")
        println("Use 'help' for usage information.")
        exitProcess(1)
    }
}

/**
 * Start a new percolator farm
 */
fun startFarm(args: List<String>) {
    println("🚀 Starting percolator farm...")
    
    // Build the project first
    println("📦 Building percolator components...")
    val buildProcess = ProcessBuilder("./gradlew", ":fiduciary:build", "-q")
        .inheritIO()
        .start()
    buildProcess.waitFor()
    
    if (buildProcess.exitValue() != 0) {
        println("❌ Build failed")
        exitProcess(1)
    }
    
    // Generate farm ID
    val farmId = "farm-${System.currentTimeMillis()}"
    val farmDir = "./farms/$farmId"
    File(farmDir).mkdirs()
    
    // Create farm configuration
    val farmConfig = buildFarmConfig(args, farmDir)
    
    // Start coordinator if not specified
    val coordinatorUrl = farmConfig.coordinatorUrl
    if (coordinatorUrl == "http://localhost:8888") {
        startCoordinator(farmDir)
    }
    
    // Start farm
    println("🌊 Starting percolator farm: $farmId")
    val farmProcess = ProcessBuilder(
        "java", "-cp", "fiduciary/build/libs/fiduciary-jvm.jar",
        "fiduciary.percolator.RunPercolatorFarmKt",
        "--coordinator=$coordinatorUrl",
        "--nodes=${farmConfig.nodeCount}",
        "--max-concurrent=${farmConfig.maxConcurrentPerNode}",
        "--work-dir=$farmDir/nodes",
        "--max-nodes=${farmConfig.maxNodes}",
        "--min-nodes=${farmConfig.minNodes}"
    ).apply {
        if (farmConfig.autoScale) {
            command().add("--auto-scale")
        }
        redirectOutput(File("$farmDir/farm.log"))
        redirectError(File("$farmDir/farm-error.log"))
    }.start()
    
    // Save farm info
    val farmInfo = """
        farmId=$farmId
        coordinatorUrl=$coordinatorUrl
        nodeCount=${farmConfig.nodeCount}
        maxConcurrent=${farmConfig.maxConcurrentPerNode}
        startedAt=${System.currentTimeMillis()}
        pid=${farmProcess.pid()}
    """.trimIndent()
    
    File("$farmDir/farm.info").writeText(farmInfo)
    
    // Wait a moment for farm to start
    Thread.sleep(5000)
    
    if (farmProcess.isAlive) {
        println("✅ Farm started successfully!")
        println("Farm ID: $farmId")
        println("Coordinator: $coordinatorUrl")
        println("Nodes: ${farmConfig.nodeCount}")
        println("Logs: $farmDir/farm.log")
        println("Dashboard: $coordinatorUrl")
    } else {
        println("❌ Failed to start farm")
        exitProcess(1)
    }
}

/**
 * Check farm status
 */
fun checkFarmStatus(args: List<String>) {
    if (args.isEmpty()) {
        println("❌ Farm ID required")
        println("Usage: ./run-percolator-farm.kts status <farm-id>")
        exitProcess(1)
    }
    
    val farmId = args[0]
    val farmDir = "./farms/$farmId"
    
    if (!File(farmDir).exists()) {
        println("❌ Farm not found: $farmId")
        exitProcess(1)
    }
    
    val farmInfo = File("$farmDir/farm.info").readText()
    val infoMap = farmInfo.lines()
        .filter { it.contains("=") }
        .associate { line ->
            val parts = line.split("=", limit = 2)
            parts[0] to parts[1]
        }
    
    println("""
    📊 FARM STATUS: $farmId
    ──────────────────────────────────────────
    Coordinator: ${infoMap["coordinatorUrl"]}
    Nodes: ${infoMap["nodeCount"]}
    Max Concurrent: ${infoMap["maxConcurrent"]}
    Started: ${formatTimestamp(infoMap["startedAt"]?.toLongOrNull())}
    PID: ${infoMap["pid"]}
    """.trimIndent())
    
    // Check if process is still running
    val pid = infoMap["pid"]?.toLongOrNull()
    if (pid != null) {
        try {
            val process = ProcessHandle.of(pid).orElse(null)
            if (process != null && process.isAlive) {
                println("✅ Farm is running")
                
                // Show recent logs
                val logFile = File("$farmDir/farm.log")
                if (logFile.exists()) {
                    val lastLines = logFile.readLines().takeLast(10)
                    println("\n📋 Recent logs:")
                    lastLines.forEach { println("  $it") }
                }
            } else {
                println("❌ Farm process is not running")
            }
        } catch (e: Exception) {
            println("❌ Could not check process status: ${e.message}")
        }
    }
}

/**
 * Stop a farm
 */
fun stopFarm(args: List<String>) {
    if (args.isEmpty()) {
        println("❌ Farm ID required")
        println("Usage: ./run-percolator-farm.kts stop <farm-id>")
        exitProcess(1)
    }
    
    val farmId = args[0]
    val farmDir = "./farms/$farmId"
    
    if (!File(farmDir).exists()) {
        println("❌ Farm not found: $farmId")
        exitProcess(1)
    }
    
    val farmInfo = File("$farmDir/farm.info").readText()
    val pid = farmInfo.lines()
        .find { it.startsWith("pid=") }
        ?.substringAfter("=")
        ?.toLongOrNull()
    
    if (pid != null) {
        try {
            val process = ProcessHandle.of(pid).orElse(null)
            if (process != null && process.isAlive) {
                println("🛑 Stopping farm: $farmId")
                process.destroy()
                
                // Wait for graceful shutdown
                if (process.onExit().get()) {
                    println("✅ Farm stopped successfully")
                } else {
                    println("⚠️  Farm may not have stopped gracefully")
                }
            } else {
                println("ℹ️  Farm process is not running")
            }
        } catch (e: Exception) {
            println("❌ Error stopping farm: ${e.message}")
        }
    }
}

/**
 * List all farms
 */
fun listFarms() {
    val farmsDir = File("./farms")
    if (!farmsDir.exists()) {
        println("ℹ️  No farms found")
        return
    }
    
    val farms = farmsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
    
    if (farms.isEmpty()) {
        println("ℹ️  No farms found")
        return
    }
    
    println("""
    📋 RUNNING FARMS
    ──────────────────────────────────────────
    """.trimIndent())
    
    farms.forEach { farmDir ->
        val farmId = farmDir.name
        val infoFile = File(farmDir, "farm.info")
        
        if (infoFile.exists()) {
            val farmInfo = infoFile.readText()
            val infoMap = farmInfo.lines()
                .filter { it.contains("=") }
                .associate { line ->
                    val parts = line.split("=", limit = 2)
                    parts[0] to parts[1]
                }
            
            val coordinatorUrl = infoMap["coordinatorUrl"] ?: "unknown"
            val nodeCount = infoMap["nodeCount"] ?: "unknown"
            val startedAt = formatTimestamp(infoMap["startedAt"]?.toLongOrNull())
            
            println("$farmId:")
            println("  Coordinator: $coordinatorUrl")
            println("  Nodes: $nodeCount")
            println("  Started: $startedAt")
            println()
        }
    }
}

/**
 * Show farm logs
 */
fun showFarmLogs(args: List<String>) {
    if (args.isEmpty()) {
        println("❌ Farm ID required")
        println("Usage: ./run-percolator-farm.kts logs <farm-id>")
        exitProcess(1)
    }
    
    val farmId = args[0]
    val farmDir = "./farms/$farmId"
    val logFile = File("$farmDir/farm.log")
    
    if (!logFile.exists()) {
        println("❌ Log file not found for farm: $farmId")
        exitProcess(1)
    }
    
    println("📋 Logs for farm: $farmId")
    println("─".repeat(50))
    
    // Show last 50 lines
    val lines = logFile.readLines()
    lines.takeLast(50).forEach { println(it) }
}

/**
 * Scale a farm
 */
fun scaleFarm(args: List<String>) {
    if (args.size < 2) {
        println("❌ Farm ID and node count required")
        println("Usage: ./run-percolator-farm.kts scale <farm-id> <node-count>")
        exitProcess(1)
    }
    
    val farmId = args[0]
    val nodeCount = args[1].toIntOrNull()
    
    if (nodeCount == null || nodeCount < 1) {
        println("❌ Invalid node count: ${args[1]}")
        exitProcess(1)
    }
    
    println("⚠️  Scaling not yet implemented")
    println("Farm: $farmId")
    println("Target nodes: $nodeCount")
    println("Please restart the farm with the new node count")
}

/**
 * Show help
 */
fun showHelp() {
    println("""
    Percolator Farm Management
    
    Usage: ./run-percolator-farm.kts <command> [options]
    
    Commands:
      start [options]     Start a new percolator farm
      status <farm-id>    Check farm status
      stop <farm-id>      Stop a farm
      list               List all running farms
      logs <farm-id>      Show farm logs
      scale <farm-id> <N> Scale farm to N nodes
      help               Show this help
    
    Start Options:
      --coordinator=URL   Coordinator server URL (default: http://localhost:8888)
      --nodes=N          Initial number of nodes (default: 5)
      --max-concurrent=N Max concurrent work per node (default: 3)
      --max-nodes=N      Maximum nodes for auto-scaling (default: 20)
      --min-nodes=N      Minimum nodes for auto-scaling (default: 2)
      --no-auto-scale    Disable auto-scaling
    
    Examples:
      ./run-percolator-farm.kts start --nodes=10
      ./run-percolator-farm.kts status farm-001
      ./run-percolator-farm.kts stop farm-001
      ./run-percolator-farm.kts logs farm-001
    """.trimIndent())
}

/**
 * Build farm configuration from arguments
 */
fun buildFarmConfig(args: List<String>, farmDir: String): FarmConfig {
    var config = FarmConfig()
    
    args.forEach { arg ->
        when {
            arg.startsWith("--coordinator=") -> {
                config = config.copy(coordinatorUrl = arg.substringAfter("="))
            }
            arg.startsWith("--nodes=") -> {
                config = config.copy(nodeCount = arg.substringAfter("=").toInt())
            }
            arg.startsWith("--max-concurrent=") -> {
                config = config.copy(maxConcurrentPerNode = arg.substringAfter("=").toInt())
            }
            arg.startsWith("--max-nodes=") -> {
                config = config.copy(maxNodes = arg.substringAfter("=").toInt())
            }
            arg.startsWith("--min-nodes=") -> {
                config = config.copy(minNodes = arg.substringAfter("=").toInt())
            }
            arg == "--no-auto-scale" -> {
                config = config.copy(autoScale = false)
            }
        }
    }
    
    return config.copy(workDirBase = "$farmDir/nodes")
}

/**
 * Start coordinator server
 */
fun startCoordinator(farmDir: String) {
    println("🌊 Starting coordinator server...")
    
    val coordinatorProcess = ProcessBuilder(
        "java", "-cp", "fiduciary/build/libs/fiduciary-jvm.jar",
        "fiduciary.percolator.PercolatorCoordinatorServerKt"
    ).apply {
        redirectOutput(File("$farmDir/coordinator.log"))
        redirectError(File("$farmDir/coordinator-error.log"))
    }.start()
    
    // Wait for coordinator to start
    Thread.sleep(5000)
    
    if (coordinatorProcess.isAlive) {
        println("✅ Coordinator started")
    } else {
        println("❌ Failed to start coordinator")
        exitProcess(1)
    }
}

/**
 * Format timestamp
 */
fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null) return "unknown"
    val date = java.util.Date(timestamp)
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)
}

// === Supporting Types ===

data class FarmConfig(
    val coordinatorUrl: String = "http://localhost:8888",
    val nodeCount: Int = 5,
    val maxConcurrentPerNode: Int = 3,
    val workDirBase: String = "./percolator-farm",
    val autoScale: Boolean = true,
    val maxNodes: Int = 20,
    val minNodes: Int = 2
) 