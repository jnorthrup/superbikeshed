package nexus

import k2script.ai.llm.LiteLLMClient
import k2script.ai.llm.LLMResponse
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import java.io.IOException

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
    
    fun main(args: Array<String>) = runBlocking {
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
            System.err.println("Nexus error: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
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
            System.err.println("Error: AI prompt is required")
            System.err.println("Usage: nexus --ai <prompt>")
            exitProcess(1)
        }
        
        val prompt = args.joinToString(" ")
        println("Nexus: Executing AI task...")
        println("Prompt: $prompt")
        
        try {
            // Use k2script's LiteLLMClient directly
            val messages = listOf(
                mapOf("role" to "system", "content" to "You are Nexus, an AI agent that helps with development tasks. Provide clear, actionable responses."),
                mapOf("role" to "user", "content" to prompt)
            )
            
            val future = LiteLLMClient.complete(
                model = "gpt-3.5-turbo", // Default model, can be made configurable
                messages = messages,
                temperature = 0.7,
                maxTokens = 1000
            )
            
            println("Nexus: Waiting for AI response...")
            val response = future.get()
            
            if (response.status == "success") {
                println("Nexus AI Response:")
                println("==================")
                println(response.content ?: "No content received")
            } else {
                System.err.println("Nexus: AI request failed: ${response.error_message}")
                exitProcess(1)
            }
            
        } catch (e: IOException) {
            System.err.println("Nexus: Failed to communicate with AI service: ${e.message}")
            System.err.println("Make sure you have set up your API keys (e.g., OPENAI_API_KEY)")
            exitProcess(1)
        } catch (e: Exception) {
            System.err.println("Nexus: Unexpected error during AI task: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
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

// Platform-specific function to be implemented per platform
expect suspend fun runInteractivePlatform()

// Main function for application entry point
fun main(args: Array<String>) = Nexus.main(args)