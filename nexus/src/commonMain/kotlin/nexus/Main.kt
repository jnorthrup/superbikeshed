package nexus

import k2script.ai.llm.LiteLLMClient
import k2script.ai.llm.LLMResponse
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import java.io.IOException
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series as Indexed

/**
 * Nexus Agent - AI-powered task execution using k2script's LiteLLMClient
 * 
 * Enhanced architecture following k2script and TrikeShed patterns:
 * - Main entry point that parses arguments
 * - NexusConfigBuilder for configuration management
 * - NexusActionExecutor for task execution
 * - TrikeShed data structures (Series<T>, Join<A,B>)
 * - Direct integration with k2script's LiteLLMClient
 */

// === CONFIGURATION MANAGEMENT ===

/**
 * Nexus configuration using TrikeShed Join pattern
 */
data class NexusConfig(
    val model: String = "gpt-3.5-turbo",
    val temperature: Double = 0.7,
    val maxTokens: Int = 1000,
    val apiKey: String? = null
)

/**
 * ConfigBuilder following k2script patterns
 */
class NexusConfigBuilder {
    private var model: String = "gpt-3.5-turbo"
    private var temperature: Double = 0.7
    private var maxTokens: Int = 1000
    private var apiKey: String? = null
    
    fun model(value: String) = apply { model = value }
    fun temperature(value: Double) = apply { temperature = value }
    fun maxTokens(value: Int) = apply { maxTokens = value }
    fun apiKey(value: String?) = apply { apiKey = value }
    
    fun build(): NexusConfig = NexusConfig(model, temperature, maxTokens, apiKey)
}

// === ACTION EXECUTION SYSTEM ===

/**
 * Nexus actions using sealed class pattern from k2script
 */
sealed class NexusAction {
    data class AITask(val prompt: String, val config: NexusConfig = NexusConfig()) : NexusAction()
    data class ConfigTask(val config: NexusConfig) : NexusAction()
    data class HelpTask(val command: String? = null) : NexusAction()
}

/**
 * Action executor using TrikeShed patterns for message handling
 */
class NexusActionExecutor {
    suspend fun execute(action: NexusAction): Result<String> {
        return try {
            when (action) {
                is NexusAction.AITask -> executeAITask(action)
                is NexusAction.ConfigTask -> executeConfigTask(action)
                is NexusAction.HelpTask -> executeHelpTask(action)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun executeAITask(task: NexusAction.AITask): Result<String> {
        try {
            // Use TrikeShed Join<A,B> for structured message data
            val systemMessage = "role" j "system" j ("content" j "You are Nexus, an AI agent that helps with development tasks. Provide clear, actionable responses.")
            val userMessage = "role" j "user" j ("content" j task.prompt)
            
            // Convert to legacy format for LiteLLMClient compatibility
            val messages = listOf(
                mapOf("role" to "system", "content" to "You are Nexus, an AI agent that helps with development tasks. Provide clear, actionable responses."),
                mapOf("role" to "user", "content" to task.prompt)
            )
            
            val future = LiteLLMClient.complete(
                model = task.config.model,
                messages = messages,
                temperature = task.config.temperature,
                maxTokens = task.config.maxTokens
            )
            
            val response = future.get()
            
            return if (response.status == "success") {
                Result.success(response.content ?: "No content received")
            } else {
                Result.failure(Exception("AI request failed: ${response.error_message}"))
            }
            
        } catch (e: IOException) {
            return Result.failure(Exception("Failed to communicate with AI service: ${e.message}\nMake sure you have set up your API keys (e.g., OPENAI_API_KEY)"))
        } catch (e: Exception) {
            return Result.failure(Exception("Unexpected error during AI task: ${e.message}"))
        }
    }
    
    private fun executeConfigTask(task: NexusAction.ConfigTask): Result<String> {
        return Result.success("Configuration updated: ${task.config}")
    }
    
    private fun executeHelpTask(task: NexusAction.HelpTask): Result<String> {
        val helpText = """
            Nexus Agent - AI-powered task execution
            
            Usage: nexus <command> [args...]
            
            Commands:
              --ai <prompt>     Execute AI-powered task using LiteLLM
              --help, -h        Show this help
              --version, -v     Show version
              
            Examples:
              nexus --ai "analyze the current codebase and suggest improvements"
              nexus --ai "create a summary of recent changes"
        """.trimIndent()
        return Result.success(helpText)
    }
}
// === MAIN ENTRY POINT ===

object Nexus {
    private val executor = NexusActionExecutor()
    
    fun main(args: Array<String>) = runBlocking {
        try {
            val action = parseArgs(args)
            val result = executor.execute(action)
            
            result.fold(
                onSuccess = { output -> 
                    println(output)
                },
                onFailure = { error ->
                    System.err.println("Nexus error: ${error.message}")
                    error.printStackTrace()
                    exitProcess(1)
                }
            )
        } catch (e: Exception) {
            System.err.println("Nexus error: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }
    
    private fun parseArgs(args: Array<String>): NexusAction {
        return when {
            args.isEmpty() -> NexusAction.HelpTask()
            args[0] == "--help" || args[0] == "-h" -> NexusAction.HelpTask()
            args[0] == "--version" || args[0] == "-v" -> NexusAction.HelpTask("version")
            args[0] == "--ai" -> {
                if (args.size < 2) {
                    throw IllegalArgumentException("AI prompt is required. Usage: nexus --ai <prompt>")
                }
                val prompt = args.drop(1).joinToString(" ")
                val config = NexusConfigBuilder().build() // Default config, can be enhanced
                NexusAction.AITask(prompt, config)
            }
            else -> {
                // Generic task handling
                val prompt = "Execute task: ${args.joinToString(" ")}"
                NexusAction.AITask(prompt)
            }
        }
    }
    
    private fun showVersion() {
        println("Nexus Agent 1.0.0")
        println("Enhanced with TrikeShed and k2script patterns")
        println("Powered by k2script LiteLLMClient")
    }
}

// Main function for application entry point
fun main(args: Array<String>) = Nexus.main(args)