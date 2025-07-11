package fiduciary.percolator

import kotlinx.coroutines.*
import java.io.File
import kotlin.concurrent.thread

/**
 * Run a Percolator Node
 * 
 * Volunteers run this to contribute processing power to the network
 */
fun main(args: Array<String>) {
    val nodeConfig = parseConfig(args)
    
    println("""
    ╔════════════════════════════════════════════╗
    ║      CONTENT PERCOLATOR VOLUNTEER NODE     ║
    ╚════════════════════════════════════════════╝
    
    Thank you for contributing to the distributed
    content extraction network!
    
    Node Configuration:
    - Coordinator: ${nodeConfig.coordinatorUrl}
    - Max concurrent work: ${nodeConfig.maxConcurrent}
    - Work directory: ${nodeConfig.workDir}
    
    Press Ctrl+C to stop
    """.trimIndent())
    
    // Create work directory
    File(nodeConfig.workDir).mkdirs()
    
    // Start daemon
    val daemon = PercolatorDaemon(
        coordinatorUrl = nodeConfig.coordinatorUrl,
        maxConcurrentWork = nodeConfig.maxConcurrent,
        workDir = nodeConfig.workDir
    )
    
    daemon.start()
    
    // Register shutdown hook
    Runtime.getRuntime().addShutdownHook(thread {
        println("\n🛑 Shutting down percolator node...")
        runBlocking {
            daemon.stop()
        }
        println("✅ Node stopped cleanly")
    })
    
    // Keep main thread alive
    Thread.currentThread().join()
}

data class NodeConfig(
    val coordinatorUrl: String = "https://percolator.fiduciary.network",
    val maxConcurrent: Int = 5,
    val workDir: String = "./percolator-work"
)

fun parseConfig(args: Array<String>): NodeConfig {
    var config = NodeConfig()
    
    args.forEach { arg ->
        when {
            arg.startsWith("--coordinator=") -> {
                config = config.copy(coordinatorUrl = arg.substringAfter("="))
            }
            arg.startsWith("--max-concurrent=") -> {
                config = config.copy(maxConcurrent = arg.substringAfter("=").toInt())
            }
            arg.startsWith("--work-dir=") -> {
                config = config.copy(workDir = arg.substringAfter("="))
            }
            arg == "--help" -> {
                printHelp()
                System.exit(0)
            }
        }
    }
    
    return config
}

fun printHelp() {
    println("""
    Content Percolator Volunteer Node
    
    Usage: java -jar percolator-node.jar [options]
    
    Options:
      --coordinator=URL     Coordinator server URL (default: https://percolator.fiduciary.network)
      --max-concurrent=N    Maximum concurrent work units (default: 5)
      --work-dir=PATH      Working directory (default: ./percolator-work)
      --help               Show this help message
    
    Example:
      java -jar percolator-node.jar --max-concurrent=10 --work-dir=/tmp/percolator
    
    To contribute to the network, simply run the node and it will:
    1. Connect to the coordinator
    2. Claim work units (file extraction tasks)
    3. Process files using minimal bandwidth (range requests)
    4. Submit results back to the network
    
    Your node will appear in the network statistics at:
    https://percolator.fiduciary.network/stats
    """.trimIndent())
}