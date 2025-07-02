package nexus.interactive

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.α
import borg.trikeshed.lib.toSeries
import k2script.ai.llm.LiteLLMClient
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Enhanced Nexus Interactive System with improved ergonomics
 * 
 * Design principles:
 * - DSL for tool registration
 * - Sealed classes for commands
 * - Proper use of Indexed<T> instead of mutable lists
 * - Functional error handling with Either
 * - Coroutine-based execution model
 */
object NexusInteractive {
    
    // Terminal styling using value classes for type safety
    @JvmInline
    value class AnsiColor(val code: String) {
        operator fun invoke(text: String) = "$code$text$RESET"
        companion object {
            private const val RESET = "\u001B[0m"
            val BoldCyan = AnsiColor("\u001B[1;36m")
            val BoldGreen = AnsiColor("\u001B[1;32m")
            val BoldRed = AnsiColor("\u001B[1;31m")
            val BoldYellow = AnsiColor("\u001B[1;33m")
            val BoldBlue = AnsiColor("\u001B[1;34m")
            val Cyan = AnsiColor("\u001B[0;36m")
            val Gray = AnsiColor("\u001B[0;90m")
        }
    }
    
    // Command system using sealed classes for exhaustive pattern matching
    sealed interface Command {
        data object Help : Command
        data object Exit : Command
        data object ListTools : Command
        data object ClearHistory : Command
        data object ShowModel : Command
        data class Chat(val message: String) : Command
        data class SetModel(val model: String) : Command
        data class SetTimeout(val duration: Duration) : Command
    }
    
    // Tool definition with improved type safety
    data class Tool(
        val name: String,
        val description: String,
        val parameters: Indexed<ToolParameter>,
        val execute: suspend (ToolContext) -> ToolResult
    )
    
    data class ToolParameter(
        val name: String,
        val type: ParameterType,
        val description: String,
        val required: Boolean = true,
        val defaultValue: Any? = null
    )
    
    enum class ParameterType {
        STRING, NUMBER, BOOLEAN, FILE_PATH, JSON
    }
    
    data class ToolContext(
        val parameters: Map<String, Any>,
        val conversationHistory: Indexed<ConversationEntry>,
        val currentModel: String
    )
    
    sealed interface ToolResult {
        data class Success(val output: String) : ToolResult
        data class Error(val message: String) : ToolResult
        data class Deferred(val job: Deferred<String>) : ToolResult
    }
    
    data class ConversationEntry(
        val role: ConversationRole,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class ConversationRole { USER, ASSISTANT, SYSTEM, TOOL }
    
    // Tool registry with builder DSL
    class ToolRegistry {
        private val tools = mutableListOf<Tool>()
        
        fun tool(name: String, description: String, block: ToolBuilder.() -> Unit) {
            val builder = ToolBuilder(name, description)
            builder.block()
            tools.add(builder.build())
        }
        
        fun toIndexed(): Indexed<Tool> = tools.toTypedArray().toSeries()
        
        fun findByName(name: String): Tool? = tools.find { it.name == name }
    }
    
    class ToolBuilder(private val name: String, private val description: String) {
        private val parameters = mutableListOf<ToolParameter>()
        private var executeBlock: suspend (ToolContext) -> ToolResult = { 
            ToolResult.Error("No implementation provided")
        }
        
        fun parameter(
            name: String, 
            type: ParameterType, 
            description: String,
            required: Boolean = true,
            defaultValue: Any? = null
        ) {
            parameters.add(ToolParameter(name, type, description, required, defaultValue))
        }
        
        fun execute(block: suspend (ToolContext) -> ToolResult) {
            executeBlock = block
        }
        
        fun build() = Tool(
            name = name,
            description = description,
            parameters = parameters.toTypedArray().toSeries(),
            execute = executeBlock
        )
    }
    
    // Session state using immutable data
    data class SessionState(
        val conversationHistory: Indexed<ConversationEntry> = emptyArray<ConversationEntry>().toSeries(),
        val currentModel: String = System.getenv("NEXUS_MODEL") ?: "gpt-3.5-turbo",
        val timeout: Duration = 30.seconds
    ) {
        fun addEntry(entry: ConversationEntry): SessionState = 
            copy(conversationHistory = (conversationHistory.toList() + entry).toTypedArray().toSeries())
        
        fun clearHistory(): SessionState = 
            copy(conversationHistory = emptyArray<ConversationEntry>().toSeries())
        
        fun setModel(model: String): SessionState = 
            copy(currentModel = model)
        
        fun setTimeout(duration: Duration): SessionState = 
            copy(timeout = duration)
    }
    
    // Default tools using the DSL
    private fun createDefaultTools() = ToolRegistry().apply {
        tool("echo", "Echo back the input message") {
            parameter("message", ParameterType.STRING, "The message to echo")
            execute { ctx ->
                val message = ctx.parameters["message"]?.toString() 
                    ?: return@execute ToolResult.Error("No message provided")
                ToolResult.Success(message)
            }
        }
        
        tool("time", "Get the current time") {
            execute {
                ToolResult.Success(java.time.LocalDateTime.now().toString())
            }
        }
        
        tool("calculate", "Perform basic math calculations") {
            parameter("expression", ParameterType.STRING, "Math expression to evaluate")
            execute { ctx ->
                val expr = ctx.parameters["expression"]?.toString() 
                    ?: return@execute ToolResult.Error("No expression provided")
                try {
                    val engine = javax.script.ScriptEngineManager().getEngineByName("JavaScript")
                    val result = engine.eval(expr)
                    ToolResult.Success("Result: $result")
                } catch (e: Exception) {
                    ToolResult.Error("Error calculating: ${e.message}")
                }
            }
        }
        
        tool("file_read", "Read contents of a file") {
            parameter("path", ParameterType.FILE_PATH, "Path to the file to read")
            execute { ctx ->
                val path = ctx.parameters["path"]?.toString() 
                    ?: return@execute ToolResult.Error("No file path provided")
                try {
                    val content = java.io.File(path).readText()
                    ToolResult.Success(content)
                } catch (e: Exception) {
                    ToolResult.Error("Error reading file: ${e.message}")
                }
            }
        }
        
        tool("list_files", "List files in a directory") {
            parameter("path", ParameterType.FILE_PATH, "Directory path", required = false, defaultValue = ".")
            execute { ctx ->
                val path = ctx.parameters["path"]?.toString() ?: "."
                try {
                    val dir = java.io.File(path)
                    if (dir.isDirectory) {
                        val files = dir.listFiles()?.joinToString("\n") { 
                            "${if (it.isDirectory) "📁" else "📄"} ${it.name}"
                        } ?: "Empty directory"
                        ToolResult.Success(files)
                    } else {
                        ToolResult.Error("Not a directory: $path")
                    }
                } catch (e: Exception) {
                    ToolResult.Error("Error listing files: ${e.message}")
                }
            }
        }
        
        tool("search", "Search for text in files") {
            parameter("pattern", ParameterType.STRING, "Search pattern")
            parameter("path", ParameterType.FILE_PATH, "Directory to search in", false, ".")
            parameter("recursive", ParameterType.BOOLEAN, "Search recursively", false, true)
            execute { ctx ->
                val pattern = ctx.parameters["pattern"]?.toString() 
                    ?: return@execute ToolResult.Error("No pattern provided")
                val path = ctx.parameters["path"]?.toString() ?: "."
                val recursive = ctx.parameters["recursive"] as? Boolean ?: true
                
                // Deferred execution for potentially long-running search
                ToolResult.Deferred(GlobalScope.async {
                    try {
                        val results = searchFiles(java.io.File(path), pattern, recursive)
                        if (results.isEmpty()) {
                            "No matches found"
                        } else {
                            results.joinToString("\n") { (file, line, lineNum) ->
                                "${file.path}:$lineNum: $line"
                            }
                        }
                    } catch (e: Exception) {
                        "Search error: ${e.message}"
                    }
                })
            }
        }
    }
    
    // Helper function for file search
    private fun searchFiles(
        dir: java.io.File, 
        pattern: String, 
        recursive: Boolean
    ): List<Triple<java.io.File, String, Int>> {
        val results = mutableListOf<Triple<java.io.File, String, Int>>()
        val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
        
        dir.walkTopDown().maxDepth(if (recursive) Int.MAX_VALUE else 1).forEach { file ->
            if (file.isFile && file.canRead()) {
                try {
                    file.readLines().forEachIndexed { index, line ->
                        if (regex.containsMatchIn(line)) {
                            results.add(Triple(file, line.trim(), index + 1))
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be read
                }
            }
        }
        
        return results
    }
    
    // Command parser
    private fun parseCommand(input: String): Command = when {
        input.isBlank() -> Command.Chat("")
        input == "exit" || input == "quit" || input == "bye" -> Command.Exit
        input == "help" || input == "/help" -> Command.Help
        input == "tools" || input == "/tools" -> Command.ListTools
        input == "clear" || input == "/clear" -> Command.ClearHistory
        input == "model" || input == "/model" -> Command.ShowModel
        input.startsWith("/model ") -> Command.SetModel(input.removePrefix("/model ").trim())
        input.startsWith("/timeout ") -> {
            val duration = input.removePrefix("/timeout ").trim().toIntOrNull()?.seconds
            duration?.let { Command.SetTimeout(it) } ?: Command.Chat(input)
        }
        else -> Command.Chat(input)
    }
    
    // Main interaction loop
    suspend fun runInteractive() = coroutineScope {
        val tools = createDefaultTools()
        var state = SessionState()
        
        with(AnsiColor) {
            println(BoldCyan("🚀 Nexus Interactive - Enhanced Edition"))
            println(BoldCyan("=" * 50))
            println("Type ${Cyan("/help")} for commands or chat naturally.")
            println(Gray("Model: ${state.currentModel} | Timeout: ${state.timeout}"))
            println()
        }
        
        while (isActive) {
            try {
                print(AnsiColor.BoldGreen("You: "))
                val input = readlnOrNull() ?: break
                
                when (val command = parseCommand(input)) {
                    is Command.Exit -> {
                        println(AnsiColor.BoldCyan("Goodbye! May your architectures be sound."))
                        break
                    }
                    
                    is Command.Help -> showHelp()
                    is Command.ListTools -> showTools(tools)
                    is Command.ClearHistory -> {
                        state = state.clearHistory()
                        println(AnsiColor.Cyan("Conversation history cleared."))
                    }
                    is Command.ShowModel -> {
                        println(AnsiColor.Cyan("Current model: ${state.currentModel}"))
                    }
                    is Command.SetModel -> {
                        state = state.setModel(command.model)
                        println(AnsiColor.Cyan("Model set to: ${command.model}"))
                    }
                    is Command.SetTimeout -> {
                        state = state.setTimeout(command.duration)
                        println(AnsiColor.Cyan("Timeout set to: ${command.duration}"))
                    }
                    is Command.Chat -> {
                        if (command.message.isNotBlank()) {
                            state = state.addEntry(ConversationEntry(ConversationRole.USER, command.message))
                            val response = processChat(command.message, state, tools)
                            println(AnsiColor.BoldBlue("Nexus: ") + response)
                            state = state.addEntry(ConversationEntry(ConversationRole.ASSISTANT, response))
                        }
                    }
                }
                
            } catch (e: Exception) {
                println(AnsiColor.BoldRed("Error: ${e.message}"))
                e.printStackTrace()
            }
        }
    }
    
    private fun showHelp() {
        println(AnsiColor.BoldCyan("\nAvailable Commands:"))
        val commands = listOf(
            "/help" to "Show this help",
            "/tools" to "List available tools",
            "/clear" to "Clear conversation history",
            "/model" to "Show current model",
            "/model <name>" to "Set model",
            "/timeout <seconds>" to "Set timeout",
            "exit" to "Exit the program"
        )
        commands.forEach { (cmd, desc) ->
            println(AnsiColor.Cyan("  $cmd") + " - $desc")
        }
        println("\nOr just chat naturally!")
    }
    
    private fun showTools(registry: ToolRegistry) {
        println(AnsiColor.BoldCyan("\nAvailable Tools:"))
        registry.toIndexed().forEachIndexed { _, tool ->
            println(AnsiColor.Cyan("  • ${tool.name}") + " - ${tool.description}")
            if (tool.parameters.size > 0) {
                tool.parameters.forEach { param ->
                    val req = if (param.required) "required" else "optional"
                    println(AnsiColor.Gray("    - ${param.name} (${param.type}, $req): ${param.description}"))
                }
            }
        }
    }
    
    private suspend fun processChat(
        message: String,
        state: SessionState,
        tools: ToolRegistry
    ): String = withContext(Dispatchers.IO) {
        val context = ToolContext(
            parameters = emptyMap(),
            conversationHistory = state.conversationHistory,
            currentModel = state.currentModel
        )
        
        val messages = LLMIntegration.formatMessages(
            conversationHistory = state.conversationHistory,
            tools = tools
        )
        
        // Add the new user message
        val allMessages = messages + mapOf("role" to "user", "content" to message)
        
        when (val result = LLMIntegration.completeWithTools(
            messages = allMessages,
            model = state.currentModel,
            tools = tools,
            context = context,
            timeout = state.timeout
        )) {
            is Either.Right -> result.value
            is Either.Left -> {
                val error = result.value
                when (error) {
                    is LLMIntegration.LLMError.NetworkError -> 
                        "Network error: ${error.message}"
                    is LLMIntegration.LLMError.ParseError -> 
                        "Parse error: ${error.message}"
                    is LLMIntegration.LLMError.ToolExecutionError -> 
                        "Tool '${error.toolName}' error: ${error.error}"
                    is LLMIntegration.LLMError.TimeoutError -> 
                        "Request timed out after ${error.duration}"
                    is LLMIntegration.LLMError.ModelNotAvailable -> 
                        "Model '${error.model}' is not available"
                    is LLMIntegration.LLMError.RateLimitError -> 
                        "Rate limit exceeded. Please try again later."
                }
            }
        }
    }
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        runInteractive()
    }
}

// Extension for string multiplication
private operator fun String.times(count: Int): String = repeat(count)