package nexus

import kotlinx.coroutines.*
import nexus.ai.*
import nexus.scanner.*
import nexus.tools.*
import nexus.telemetry.*
import java.io.File
import kotlin.system.exitProcess

/**
 * Nexus - Main Entry Point
 * 
 * Clean, focused agent implementation following k2script patterns
 */
object Nexus {
    const val VERSION = "0.1.0"
    const val NAME = "Nexus Development Agent"
    
    fun printHelp() {
        println("""
            $NAME v$VERSION
            
            Usage: nexus [options] [command] [args...]
            
            Commands:
              run <script>        Execute a Kotlin script
              scan [path]         Scan environment capabilities
              task <description>  Execute AI-powered task
              serve               Start LSP server
              tools [tool] [args] List or execute available tools
              telemetry [cmd]     Manage IDE telemetry collection
              help                Show this help
              
            Options:
              --config <file>     Use configuration file
              --verbose           Enable verbose output
              --ai-provider <id>  Set AI provider (default: litellm)
              --version           Show version
              
            Examples:
              nexus scan                    # Scan current directory
              nexus task "refactor to use Series<T>"
              nexus run analysis.kts
              nexus tools                   # List available tools
              nexus tools gradle build      # Execute gradle build
        """.trimIndent())
    }
}

/**
 * Configuration builder following k2script pattern
 */
class NexusConfig {
    var verbose: Boolean = false
    var configFile: File? = null
    var aiProvider: String = "litellm"
    var workingDir: File = File(".")
    
    companion object {
        fun fromArgs(args: Array<String>): Pair<NexusConfig, List<String>> {
            val config = NexusConfig()
            val remainingArgs = mutableListOf<String>()
            
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--verbose" -> config.verbose = true
                    "--config" -> {
                        if (i + 1 < args.size) {
                            config.configFile = File(args[++i])
                        }
                    }
                    "--ai-provider" -> {
                        if (i + 1 < args.size) {
                            config.aiProvider = args[++i]
                        }
                    }
                    "--version" -> {
                        println("${Nexus.NAME} v${Nexus.VERSION}")
                        exitProcess(0)
                    }
                    "--help", "-h" -> {
                        Nexus.printHelp()
                        exitProcess(0)
                    }
                    else -> remainingArgs.add(args[i])
                }
                i++
            }
            
            // Load config file if specified
            config.configFile?.let { file ->
                if (file.exists()) {
                    // TODO: Load configuration from file
                }
            }
            
            return config to remainingArgs
        }
    }
}

/**
 * Action executor following k2script pattern
 */
class ActionExecutor(private val config: NexusConfig) {
    private val toolOrchestrator = ToolOrchestrator(config.workingDir)
    
    suspend fun execute(command: String, args: List<String>) {
        when (command) {
            "run" -> executeRun(args)
            "scan" -> executeScan(args)
            "task" -> executeTask(args)
            "serve" -> executeServe(args)
            "tools" -> executeTools(args)
            "telemetry" -> executeTelemetry(args)
            "help" -> Nexus.printHelp()
            else -> {
                println("Unknown command: $command")
                println("Run 'nexus help' for usage")
                exitProcess(1)
            }
        }
    }
    
    private suspend fun executeRun(args: List<String>) {
        if (args.isEmpty()) {
            println("Error: Script file required")
            println("Usage: nexus run <script.kts>")
            exitProcess(1)
        }
        
        val scriptFile = File(args[0])
        if (!scriptFile.exists()) {
            println("Error: Script file not found: ${scriptFile.absolutePath}")
            exitProcess(1)
        }
        
        println("Running script: ${scriptFile.name}")
        // TODO: Integrate script execution engine
    }
    
    private suspend fun executeScan(args: List<String>) {
        val path = args.firstOrNull()?.let { File(it) } ?: config.workingDir
        println("Scanning environment: ${path.absolutePath}")
        
        val scanner = EnvironmentScanner(path)
        val info = scanner.scan()
        scanner.printReport(info)
    }
    
    private suspend fun executeTask(args: List<String>) {
        if (args.isEmpty()) {
            println("Error: Task description required")
            println("Usage: nexus task <description>")
            exitProcess(1)
        }
        
        val taskDescription = args.joinToString(" ")
        println("Executing task: $taskDescription")
        println("Using AI provider: ${config.aiProvider}")
        
        try {
            val provider = AIProviderFactory.create(config.aiProvider)
            val result = provider.completeTask(taskDescription)
            println("\nResult:")
            println(result)
        } catch (e: Exception) {
            println("Error executing task: ${e.message}")
            if (config.verbose) {
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun executeServe(args: List<String>) {
        val port = args.firstOrNull()?.toIntOrNull() ?: 7777
        println("Starting Nexus LSP server on port $port...")
        
        val lspServer = nexus.lsp.NexusLspServer(port)
        
        // Start server in background
        val serverJob = GlobalScope.launch {
            try {
                lspServer.start()
            } catch (e: Exception) {
                println("LSP Server error: ${e.message}")
                if (config.verbose) {
                    e.printStackTrace()
                }
            }
        }
        
        println("LSP Server started on port $port")
        println("Press Ctrl+C to stop...")
        
        // Wait for shutdown signal
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                println("\nShutting down LSP server...")
                lspServer.stop()
                serverJob.cancel()
            }
        })
        
        // Keep main thread alive
        serverJob.join()
    }
    
    private suspend fun executeTools(args: List<String>) {
        if (args.isEmpty()) {
            // List available tools
            val tools = toolOrchestrator.discoverTools()
            println("Available tools:")
            tools.forEach { (name, info) ->
                println("  $name (${info.version ?: "unknown"}) - ${info.capabilities.joinToString(", ")}")
            }
        } else {
            // Execute specific tool
            val toolName = args[0]
            val toolArgs = args.drop(1)
            
            try {
                val result = toolOrchestrator.executeTool(toolName, toolArgs)
                if (result.success) {
                    println(result.output)
                } else {
                    println("Error: ${result.errorOutput}")
                    exitProcess(result.exitCode)
                }
            } catch (e: Exception) {
                println("Error executing tool: ${e.message}")
                exitProcess(1)
            }
        }
    }
    
    private suspend fun executeTelemetry(args: List<String>) {
        val subcommand = args.firstOrNull() ?: "status"
        
        when (subcommand) {
            "start" -> {
                println("Starting unified telemetry collection...")
                val telemetrySystem = UnifiedTelemetrySystem()
                telemetrySystem.initialize()
                telemetrySystem.start()
                
                println("Telemetry collection started")
                println("Collecting from: IntelliJ, VS Code, Eclipse")
                println("Press Ctrl+C to stop...")
                
                // Keep running
                Runtime.getRuntime().addShutdownHook(Thread {
                    runBlocking {
                        telemetrySystem.stop()
                    }
                })
                
                // Wait indefinitely
                while (true) {
                    delay(1000)
                    // Could periodically print metrics here
                }
            }
            
            "status" -> {
                println("Telemetry Status:")
                println("- IntelliJ: Not connected")
                println("- VS Code: Not connected") 
                println("- Eclipse: Not connected")
                println("Run 'nexus telemetry start' to begin collection")
            }
            
            "report" -> {
                val telemetrySystem = UnifiedTelemetrySystem()
                val report = telemetrySystem.generateReport()
                
                println("=== Telemetry Report ===")
                println("Period: ${report.startTime} to ${report.endTime}")
                println("Total events: ${report.totalEvents}")
                println("\nEvents by IDE:")
                report.eventsByIDE.forEach { (ide, count) ->
                    println("  $ide: $count")
                }
                println("\nEvents by Type:")
                report.eventsByType.forEach { (type, count) ->
                    println("  $type: $count")
                }
                println("\nTop Metrics:")
                report.topMetrics.take(5).forEach { metric ->
                    println("  ${metric.name}: ${metric.value} ${metric.unit}")
                }
            }
            
            "metrics" -> {
                val telemetrySystem = UnifiedTelemetrySystem()
                val metrics = telemetrySystem.getMetrics()
                
                println("Current Metrics:")
                metrics.forEach { (key, value) ->
                    println("  $key: ${value.value} ${value.unit}")
                    value.tags.forEach { (tag, tagValue) ->
                        println("    $tag: $tagValue")
                    }
                }
            }
            
            else -> {
                println("Unknown telemetry command: $subcommand")
                println("Available commands: start, status, report, metrics")
            }
        }
    }
}

/**
 * Main entry point
 */
fun main(args: Array<String>) = runBlocking {
    try {
        // Parse configuration and arguments
        val (config, remainingArgs) = NexusConfig.fromArgs(args)
        
        if (remainingArgs.isEmpty()) {
            Nexus.printHelp()
            exitProcess(0)
        }
        
        // Execute command
        val command = remainingArgs[0]
        val commandArgs = remainingArgs.drop(1)
        
        val executor = ActionExecutor(config)
        executor.execute(command, commandArgs)
        
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        if (args.contains("--verbose")) {
            e.printStackTrace()
        }
        exitProcess(1)
    }
}