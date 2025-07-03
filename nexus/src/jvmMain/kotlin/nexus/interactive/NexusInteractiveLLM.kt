package nexus.interactive

import k2script.ai.llm.LiteLLMClient
import k2script.ai.llm.LLMRequest
import k2script.ai.llm.LLMResponse

import borg.trikeshed.lib.Join
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

/**
 * Interactive LLM with tools for Nexus
 * 
 * Following k2script architecture patterns:
 * - Direct integration with LiteLLMClient
 * - Tool management and execution
 * - Interactive CLI with ANSI colors
 * - Uses TrikeShed data structures (Indexed/Indexed, Join)
 */
object NexusInteractiveLLM {
    
    // ANSI color codes
    private const val RESET = "\u001B[0m"
    private const val BOLD_CYAN = "\u001B[1;36m"
    private const val BOLD_GREEN = "\u001B[1;32m"
    private const val BOLD_RED = "\u001B[1;31m"
    private const val BOLD_YELLOW = "\u001B[1;33m"
    private const val BOLD_BLUE = "\u001B[1;34m"
    private const val CYAN = "\u001B[0;36m"
    
    // Tool definition using TrikeShed Join
    data class Tool(
        val name: String,
        val description: String,
        val execute: suspend (Map<String, Any>) -> String
    )
    
    // Conversation history using Indexed/Indexed
    private val conversationHistory = mutableListOf<Join<String, String>>()
    
    // Available tools
    private val tools: Indexed<Tool> = Indexed.of(
        Tool(
            name = "echo",
            description = "Echo back the input message",
            execute = { params ->
                params["message"]?.toString() ?: "No message provided"
            }
        ),
        Tool(
            name = "time",
            description = "Get the current time",
            execute = { _ ->
                java.time.LocalDateTime.now().toString()
            }
        ),
        Tool(
            name = "calculate",
            description = "Perform basic math calculations",
            execute = { params ->
                val expression = params["expression"]?.toString() ?: return@Tool "No expression provided"
                try {
                    // Simple calculator using scripting engine
                    val engine = javax.script.ScriptEngineManager().getEngineByName("JavaScript")
                    val result = engine.eval(expression)
                    "Result: $result"
                } catch (e: Exception) {
                    "Error calculating: ${e.message}"
                }
            }
        ),
        Tool(
            name = "file_read",
            description = "Read contents of a file",
            execute = { params ->
                val path = params["path"]?.toString() ?: return@Tool "No file path provided"
                try {
                    java.io.File(path).readText()
                } catch (e: Exception) {
                    "Error reading file: ${e.message}"
                }
            }
        ),
        Tool(
            name = "list_files",
            description = "List files in a directory",
            execute = { params ->
                val path = params["path"]?.toString() ?: "."
                try {
                    val dir = java.io.File(path)
                    if (dir.isDirectory) {
                        dir.listFiles()?.joinToString("\n") { it.name } ?: "Empty directory"
                    } else {
                        "Not a directory: $path"
                    }
                } catch (e: Exception) {
                    "Error listing files: ${e.message}"
                }
            }
        )
    )
    
    private fun colorPrint(text: String, color: String = RESET) {
        print("$color$text$RESET")
    }
    
    private fun colorPrintln(text: String, color: String = RESET) {
        println("$color$text$RESET")
    }
    
    private fun formatToolsForLLM(): String {
        return tools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}"
        }
    }
    
    private suspend fun processWithTools(
        prompt: String,
        client: LiteLLMClient = LiteLLMClient
    ): String = coroutineScope {
        
        // Build messages with tool context
        val systemPrompt = """
            You are Nexus, an AI assistant with access to the following tools:
            ${formatToolsForLLM()}
            
            When you need to use a tool, respond with:
            TOOL_USE: <tool_name>
            PARAMS: <json_params>
            
            Example:
            TOOL_USE: calculate
            PARAMS: {"expression": "2 + 2"}
            
            After using a tool, incorporate the result into your response naturally.
        """.trimIndent()
        
        val messages = mutableListOf(
            mapOf("role" to "system", "content" to systemPrompt)
        )
        
        // Add conversation history
        conversationHistory.forEach { entry ->
            messages.add(mapOf("role" to entry.a, "content" to entry.b))
        }
        
        // Add current prompt
        messages.add(mapOf("role" to "user", "content" to prompt))
        
        try {
            val future = client.complete(
                model = System.getenv("NEXUS_MODEL") ?: "gpt-3.5-turbo",
                messages = messages,
                temperature = 0.7,
                maxTokens = 1000
            )
            
            val response = future.get()
            
            if (response.status == "success") {
                val content = response.content ?: "No response received"
                
                // Check for tool use
                if (content.contains("TOOL_USE:")) {
                    val toolMatch = Regex("TOOL_USE:\\s*(\\w+)").find(content)
                    val paramsMatch = Regex("PARAMS:\\s*\\{([^}]+)\\}").find(content)
                    
                    if (toolMatch != null) {
                        val toolName = toolMatch.groupValues[1]
                        val tool = tools.find { it.name == toolName }
                        
                        if (tool != null) {
                            colorPrintln("🔧 Using tool: $toolName", BOLD_YELLOW)
                            
                            // Parse parameters (simplified)
                            val params = if (paramsMatch != null) {
                                try {
                                    // Simple parameter parsing
                                    val paramStr = "{${paramsMatch.groupValues[1]}}"
                                    parseSimpleJson(paramStr)
                                } catch (e: Exception) {
                                    emptyMap()
                                }
                            } else {
                                emptyMap()
                            }
                            
                            // Execute tool
                            val toolResult = tool.execute(params)
                            colorPrintln("Tool result: $toolResult", CYAN)
                            
                            // Get final response incorporating tool result
                            messages.add(mapOf(
                                "role" to "assistant",
                                "content" to "I used the $toolName tool and got: $toolResult"
                            ))
                            messages.add(mapOf(
                                "role" to "user",
                                "content" to "Please provide a natural response incorporating that result."
                            ))
                            
                            val finalFuture = client.complete(
                                model = System.getenv("NEXUS_MODEL") ?: "gpt-3.5-turbo",
                                messages = messages,
                                temperature = 0.7,
                                maxTokens = 500
                            )
                            
                            val finalResponse = finalFuture.get()
                            return@coroutineScope finalResponse.content ?: toolResult
                        } else {
                            return@coroutineScope "Unknown tool: $toolName\n\n$content"
                        }
                    }
                }
                
                content
            } else {
                "Error: ${response.error_message}"
            }
        } catch (e: Exception) {
            "Error processing request: ${e.message}"
        }
    }
    
    private fun parseSimpleJson(json: String): Map<String, Any> {
        // Very simple JSON parser for demo purposes
        val map = mutableMapOf<String, Any>()
        val cleaned = json.trim().removePrefix("{").removeSuffix("}")
        val pairs = cleaned.split(",")
        
        for (pair in pairs) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"')
                val value = parts[1].trim().trim('"')
                map[key] = value
            }
        }
        
        return map
    }
    
    fun runInteractive() = runBlocking {
        colorPrintln("🚀 Nexus LLM Interactive Mode", BOLD_CYAN)
        colorPrintln("=" * 40, BOLD_CYAN)
        colorPrintln("Hello! I am Nexus, your AI assistant with tool capabilities.", RESET)
        colorPrintln("Type 'help' for commands or chat naturally.", CYAN)
        
        while (true) {
            try {
                colorPrint("\nYou: ", BOLD_GREEN)
                val input = readLine()?.trim() ?: break
                
                when (input.lowercase()) {
                    "exit", "quit", "bye" -> {
                        colorPrintln("Nexus: Goodbye! May your architectures be sound.", BOLD_CYAN)
                        break
                    }
                    "help" -> {
                        colorPrintln("\nAvailable commands:", BOLD_CYAN)
                        colorPrintln("  help     - Show this help", CYAN)
                        colorPrintln("  tools    - List available tools", CYAN)
                        colorPrintln("  clear    - Clear conversation history", CYAN)
                        colorPrintln("  model    - Show current model", CYAN)
                        colorPrintln("  exit     - Exit the program", CYAN)
                        colorPrintln("\nOr just chat naturally!", RESET)
                    }
                    "tools" -> {
                        colorPrintln("\nAvailable tools:", BOLD_CYAN)
                        tools.forEach { tool ->
                            colorPrintln("  • ${tool.name}: ${tool.description}", CYAN)
                        }
                    }
                    "clear" -> {
                        conversationHistory.clear()
                        colorPrintln("Conversation history cleared.", BOLD_CYAN)
                    }
                    "model" -> {
                        val model = System.getenv("NEXUS_MODEL") ?: "gpt-3.5-turbo"
                        colorPrintln("Current model: $model", BOLD_CYAN)
                    }
                    else -> {
                        colorPrint("\nNexus: ", BOLD_BLUE)
                        
                        // Add to history
                        conversationHistory.add(Join("user", input))
                        
                        // Process with LLM
                        val response = processWithTools(input)
                        colorPrintln(response, RESET)
                        
                        // Add response to history
                        conversationHistory.add(Join("assistant", response))
                    }
                }
                
            } catch (e: Exception) {
                colorPrintln("\nError: ${e.message}", BOLD_RED)
                e.printStackTrace()
            }
        }
    }
    
    @JvmStatic
    fun main(args: Array<String>) {
        runInteractive()
    }
}

// Extension to create String multiplication
private operator fun String.times(count: Int): String = this.repeat(count)