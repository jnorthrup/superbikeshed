package nexus

import kotlinx.coroutines.runBlocking

/**
 * Nexus Agent - AI-powered task execution using k2script's LiteLLMClient
 * 
 * This follows the working architecture pattern from k2script:
 * - Main entry point that parses arguments
 * - Config builder to manage settings  
 * - Action executor that handles specific tasks
 * - Direct integration with k2script's LiteLLMClient
 */
object Nexus {
    
    suspend fun main(args: Array<String>) {
        try {
            when {
                args.isEmpty() -> showHelp()
                args[0] == "--help" || args[0] == "-h" -> showHelp()
                args[0] == "--version" || args[0] == "-v" -> showVersion()
                args[0] == "--ai" -> executeAITask(args.drop(1).toTypedArray())
                args[0] == "--interactive" || args[0] == "-i" -> runInteractive()
                else -> executeTask(args)
            }
        } catch (e: Exception) {
            printError("Nexus error: ${e.message}")
            exitProgram(1)
        }
    }
    
    private fun showHelp() {
        println("""
            Nexus Agent - AI-powered task execution
            
            Usage: nexus <command> [args...]
            
            Commands:
              --ai <prompt>     Execute AI-powered task using LiteLLM
              --interactive, -i Run interactive LLM mode with tools
              --help, -h        Show this help
              --version, -v     Show version
              
            Examples:
              nexus --ai "analyze the current codebase and suggest improvements"
              nexus --ai "create a summary of recent changes"
              nexus --interactive
        """.trimIndent())
    }
    
    private fun showVersion() {
        println("Nexus Agent 1.0.0")
        println("Powered by k2script LiteLLMClient")
    }
    
    private suspend fun executeAITask(args: Array<String>) {
        if (args.isEmpty()) {
            printError("Error: AI prompt is required")
            printError("Usage: nexus --ai <prompt>")
            exitProgram(1)
        }
        
        val prompt = args.joinToString(" ")
        println("Nexus: Executing AI task...")
        println("Prompt: $prompt")
        
        // Platform-specific AI execution
        executePlatformAITask(prompt)
    }
    
    private suspend fun executeTask(args: Array<String>) {
        val task = args[0]
        val taskArgs = args.drop(1).toTypedArray()
        
        println("Nexus: Executing task '$task' with args: ${taskArgs.joinToString(", ")}")
        
        // TODO: Implement task execution logic
        println("Nexus: Task execution pattern established")
    }
    
    private suspend fun runInteractive() {
        // Platform-specific implementation
        runInteractivePlatform()
    }
}

// Platform-specific functions to be implemented per platform
expect fun getPlatformName(): String
expect suspend fun runInteractivePlatform()
expect suspend fun executePlatformAITask(prompt: String)
expect fun printError(message: String)
expect fun exitProgram(code: Int)

// Main function for application entry point
fun main(args: Array<String>) = runBlocking {
    Nexus.main(args)
}