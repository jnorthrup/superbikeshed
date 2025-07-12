package fiduciary.percolator

import kotlinx.coroutines.*
import java.io.File
import kotlin.concurrent.thread

/**
 * Percolator Farm Launcher
 * 
 * Command-line tool to start and manage percolator farms
 */
fun main(args: Array<String>) {
    val config = createFarmConfig(args)
    
    println("""
    ╔════════════════════════════════════════════╗
    ║        PERCOLATOR FARM LAUNCHER            ║
    ╚════════════════════════════════════════════╝
    
    Starting percolator farm with configuration:
    - Coordinator: ${config.coordinatorUrl}
    - Initial Nodes: ${config.nodeCount}
    - Max Concurrent/Node: ${config.maxConcurrentPerNode}
    - Work Directory: ${config.workDirBase}
    - Auto-scaling: ${config.autoScale}
    - Min/Max Nodes: ${config.minNodes}/${config.maxNodes}
    
    Press Ctrl+C to stop the farm
    """.trimIndent())
    
    // Create work directory
    File(config.workDirBase).mkdirs()
    
    // Start farm
    val farm = PercolatorFarm(config)
    
    runBlocking {
        farm.start()
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(thread {
            println("\n🛑 Shutting down percolator farm...")
            farm.stop()
            println("✅ Farm stopped cleanly")
        })
        
        // Keep main thread alive
        while (isActive) {
            delay(1.minutes)
            
            // Print periodic stats
            val stats = farm.getFarmStats()
            println("""
            📊 FARM STATUS UPDATE
            ──────────────────────────────────────────
            Nodes: ${stats.activeNodes}/${stats.totalNodes} active
            Work: ${stats.totalActiveWork} active, ${stats.totalCompletedWork} completed
            CPU: ${(stats.averageCpuUsage * 100).toInt()}% avg
            Memory: ${(stats.averageMemoryUsage * 100).toInt()}% avg
            Uptime: ${stats.farmUptime / 60} minutes
            """.trimIndent())
        }
    }
}

/**
 * Print help information
 */
fun printFarmHelp() {
    println("""
    Percolator Farm Launcher
    
    Usage: java -jar percolator-farm.jar [options]
    
    Options:
      --coordinator=URL        Coordinator server URL (default: http://localhost:8888)
      --nodes=N               Initial number of nodes (default: 5)
      --max-concurrent=N      Max concurrent work per node (default: 3)
      --work-dir=PATH         Base work directory (default: ./percolator-farm)
      --max-nodes=N           Maximum nodes for auto-scaling (default: 20)
      --min-nodes=N           Minimum nodes for auto-scaling (default: 2)
      --no-auto-scale         Disable auto-scaling
      --help                  Show this help message
    
    Examples:
      # Start a basic farm
      java -jar percolator-farm.jar --nodes=10
      
      # Start farm with custom coordinator
      java -jar percolator-farm.jar --coordinator=https://percolator.example.com --nodes=20
      
      # Start farm with high concurrency
      java -jar percolator-farm.jar --max-concurrent=10 --nodes=5
      
      # Start farm with custom work directory
      java -jar percolator-farm.jar --work-dir=/tmp/percolator-farm --nodes=15
    
    The farm will:
    1. Start the specified number of percolator nodes
    2. Connect each node to the coordinator
    3. Auto-scale based on workload (if enabled)
    4. Monitor node health and restart dead nodes
    5. Report farm statistics periodically
    
    Farm Dashboard:
    - Coordinator: http://localhost:8888 (or your coordinator URL)
    - Farm Stats: Printed to console every minute
    - Node Logs: Check individual node work directories
    
    To stop the farm, press Ctrl+C or kill the process.
    """.trimIndent())
}

/**
 * Parse command line arguments and handle special cases
 */
fun parseFarmArgs(args: Array<String>): Array<String> {
    if (args.contains("--help") || args.contains("-h")) {
        printFarmHelp()
        System.exit(0)
    }
    
    return args
} 