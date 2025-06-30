package nexus.flow

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import k2script.ai.llm.LiteLLMClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * NexusFlow Interactive - Stream-based interactive system with advanced ergonomics
 */
object NexusFlowInteractive {
    
    // Semantic understanding for tool suggestions
    class ToolSuggestionEngine(private val registry: ToolRegistry) {
        private val semanticMap = mutableMapOf<String, Set<String>>()
        
        init {
            // Build semantic associations
            semanticMap["file"] = setOf("read", "write", "list", "search", "delete", "copy")
            semanticMap["calculate"] = setOf("math", "compute", "eval", "solve")
            semanticMap["time"] = setOf("date", "now", "current", "timestamp")
            semanticMap["search"] = setOf("find", "locate", "grep", "scan")
            semanticMap["help"] = setOf("what", "how", "explain", "describe")
        }
        
        fun suggest(input: String): Indexed<ToolSuggestion> {
            val words = input.lowercase().split(Regex("\\s+"))
            val suggestions = mutableListOf<ToolSuggestion>()
            
            // Direct matches
            registry.search(input).forEach { capability ->
                suggestions.add(ToolSuggestion(
                    tool = capability,
                    confidence = 1.0f,
                    reason = "Direct name match"
                ))
            }
            
            // Semantic matches
            words.forEach { word ->
                semanticMap.entries.forEach { (concept, keywords) ->
                    if (word in keywords) {
                        registry.getByCategory(categoryForConcept(concept)).forEach { toolName ->
                            registry.getCapability(toolName)?.let { capability ->
                                suggestions.add(ToolSuggestion(
                                    tool = capability,
                                    confidence = 0.7f,
                                    reason = "Semantic match: $concept"
                                ))
                            }
                        }
                    }
                }
            }
            
            // Category matches
            ToolCategory.values().forEach { category ->
                if (category.name.lowercase() in input.lowercase()) {
                    registry.getByCategory(category).forEach { toolName ->
                        registry.getCapability(toolName)?.let { capability ->
                            suggestions.add(ToolSuggestion(
                                tool = capability,
                                confidence = 0.5f,
                                reason = "Category match: ${category.name}"
                            ))
                        }
                    }
                }
            }
            
            return suggestions
                .distinctBy { it.tool.name }
                .sortedByDescending { it.confidence }
                .take(5)
                .toTypedArray()
                .toSeries()
        }
        
        private fun categoryForConcept(concept: String): ToolCategory = when (concept) {
            "file" -> ToolCategory.FILESYSTEM
            "calculate" -> ToolCategory.COMPUTATION
            "search" -> ToolCategory.ANALYSIS
            else -> ToolCategory.COMPUTATION
        }
    }
    
    data class ToolSuggestion(
        val tool: ToolCapability,
        val confidence: Float,
        val reason: String
    )
    
    // Interactive session state
    data class FlowSession(
        val id: String = generateSessionId(),
        val startTime: Long = System.currentTimeMillis(),
        val messageFlow: MutableSharedFlow<UserMessage> = MutableSharedFlow(),
        val responseFlow: MutableSharedFlow<SystemResponse> = MutableSharedFlow(),
        val context: PromptContext = PromptContext(),
        val registry: ToolRegistry,
        val suggestionEngine: ToolSuggestionEngine
    ) {
        fun updateContext(block: PromptContext.() -> PromptContext): FlowSession =
            copy(context = block(context))
    }
    
    sealed interface UserMessage {
        val timestamp: Long
        
        data class Text(
            val content: String,
            override val timestamp: Long = System.currentTimeMillis()
        ) : UserMessage
        
        data class ToolRequest(
            val toolName: String,
            val parameters: Map<String, Any?>,
            override val timestamp: Long = System.currentTimeMillis()
        ) : UserMessage
        
        data class Command(
            val type: CommandType,
            val args: Map<String, Any?> = emptyMap(),
            override val timestamp: Long = System.currentTimeMillis()
        ) : UserMessage
    }
    
    sealed interface SystemResponse {
        val timestamp: Long
        
        data class Text(
            val content: String,
            override val timestamp: Long = System.currentTimeMillis()
        ) : SystemResponse
        
        data class ToolResult(
            val result: nexus.flow.ToolResult,
            override val timestamp: Long = System.currentTimeMillis()
        ) : SystemResponse
        
        data class Suggestions(
            val suggestions: Indexed<ToolSuggestion>,
            override val timestamp: Long = System.currentTimeMillis()
        ) : SystemResponse
        
        data class Stream(
            val flow: Flow<String>,
            override val timestamp: Long = System.currentTimeMillis()
        ) : SystemResponse
        
        data class Error(
            val message: String,
            val recoverable: Boolean = true,
            override val timestamp: Long = System.currentTimeMillis()
        ) : SystemResponse
    }
    
    enum class CommandType {
        HELP, EXIT, CLEAR, STATUS, TOOLS, SUGGEST, HISTORY, EXPORT
    }
    
    // Main interaction loop with streaming
    suspend fun runInteractive() = coroutineScope {
        val registry = ToolRegistry(this)
        registerDefaultTools(registry)
        
        val session = FlowSession(
            registry = registry,
            suggestionEngine = ToolSuggestionEngine(registry)
        )
        
        // Response processor
        launch {
            session.responseFlow.collect { response ->
                when (response) {
                    is SystemResponse.Text -> println(styled(response.content, AnsiStyle.Info))
                    is SystemResponse.ToolResult -> handleToolResult(response.result)
                    is SystemResponse.Suggestions -> displaySuggestions(response.suggestions)
                    is SystemResponse.Stream -> handleStream(response.flow)
                    is SystemResponse.Error -> println(styled("Error: ${response.message}", AnsiStyle.Error))
                }
            }
        }
        
        // Message processor
        launch {
            session.messageFlow
                .buffer(16)
                .collect { message ->
                    processMessage(message, session)
                }
        }
        
        // UI
        println(styled("🚀 NexusFlow Interactive", AnsiStyle.Title))
        println(styled("Type 'help' for commands or describe what you want to do", AnsiStyle.Subtitle))
        println()
        
        // Input loop
        while (isActive) {
            print(styled("> ", AnsiStyle.Prompt))
            val input = readlnOrNull() ?: break
            
            val message = parseInput(input)
            session.messageFlow.emit(message)
            
            if (message is UserMessage.Command && message.type == CommandType.EXIT) {
                break
            }
        }
        
        registry.close()
    }
    
    private suspend fun processMessage(message: UserMessage, session: FlowSession) {
        when (message) {
            is UserMessage.Text -> {
                // Get tool suggestions
                val suggestions = session.suggestionEngine.suggest(message.content)
                if (suggestions.size > 0) {
                    session.responseFlow.emit(SystemResponse.Suggestions(suggestions))
                }
                
                // Process with LLM
                processWithLLM(message.content, session)
            }
            
            is UserMessage.ToolRequest -> {
                val result = session.registry.execute(message.toolName, message.parameters)
                session.responseFlow.emit(SystemResponse.ToolResult(result))
            }
            
            is UserMessage.Command -> {
                handleCommand(message, session)
            }
        }
    }
    
    private suspend fun processWithLLM(input: String, session: FlowSession) {
        val template = SpecializedTemplates.analysisPrompt
        val context = session.context
            .withGlobal("assistant_name" to "NexusFlow")
            .withGlobal("assistant_description" to "an advanced AI assistant with tool capabilities")
            .withGlobal("task_description" to input)
        
        val prompt = template.render(context)
        
        try {
            val future = LiteLLMClient.complete(
                model = System.getenv("NEXUS_MODEL") ?: "gpt-3.5-turbo",
                messages = listOf(
                    mapOf("role" to "system", "content" to prompt),
                    mapOf("role" to "user", "content" to input)
                ),
                temperature = 0.7,
                maxTokens = 2000
            )
            
            val response = future.get()
            if (response.status == "success") {
                session.responseFlow.emit(SystemResponse.Text(response.content ?: "No response"))
            } else {
                session.responseFlow.emit(SystemResponse.Error(
                    response.error_message ?: "Unknown error"
                ))
            }
        } catch (e: Exception) {
            session.responseFlow.emit(SystemResponse.Error(e.message ?: "Unknown error"))
        }
    }
    
    private suspend fun handleCommand(command: UserMessage.Command, session: FlowSession) {
        when (command.type) {
            CommandType.HELP -> displayHelp(session)
            CommandType.TOOLS -> displayTools(session)
            CommandType.STATUS -> displayStatus(session)
            CommandType.CLEAR -> clearScreen()
            CommandType.SUGGEST -> {
                val query = command.args["query"]?.toString() ?: ""
                val suggestions = session.suggestionEngine.suggest(query)
                session.responseFlow.emit(SystemResponse.Suggestions(suggestions))
            }
            CommandType.HISTORY -> displayHistory(session)
            CommandType.EXPORT -> exportSession(session)
            CommandType.EXIT -> session.responseFlow.emit(SystemResponse.Text("Goodbye!"))
        }
    }
    
    private suspend fun displayHelp(session: FlowSession) {
        val help = """
            |Commands:
            |  help              - Show this help
            |  tools             - List available tools
            |  suggest <query>   - Get tool suggestions for a query
            |  status            - Show session status
            |  history           - Show conversation history
            |  export            - Export session data
            |  clear             - Clear screen
            |  exit              - Exit the program
            |
            |Tool Usage:
            |  @toolname param1=value1 param2=value2
            |
            |Examples:
            |  @file_read path="/etc/hosts"
            |  @calculate expression="2 + 2"
            |  suggest "find files"
        """.trimMargin()
        
        session.responseFlow.emit(SystemResponse.Text(help))
    }
    
    private suspend fun displayTools(session: FlowSession) {
        val tools = buildString {
            appendLine("Available Tools:")
            ToolCategory.values().forEach { category ->
                val categoryTools = session.registry.getByCategory(category)
                if (categoryTools.size > 0) {
                    appendLine("\n${category.name}:")
                    categoryTools.forEach { toolName ->
                        session.registry.getCapability(toolName)?.let { capability ->
                            appendLine("  • ${capability.name} - ${capability.description}")
                        }
                    }
                }
            }
        }
        session.responseFlow.emit(SystemResponse.Text(tools))
    }
    
    private suspend fun displaySuggestions(suggestions: Indexed<ToolSuggestion>) {
        if (suggestions.size == 0) return
        
        val text = buildString {
            appendLine("\nSuggested tools:")
            suggestions.forEach { suggestion ->
                appendLine("  • ${suggestion.tool.name} - ${suggestion.reason} (${(suggestion.confidence * 100).toInt()}% match)")
                appendLine("    ${suggestion.tool.description}")
            }
        }
        println(styled(text, AnsiStyle.Suggestion))
    }
    
    private suspend fun handleToolResult(result: ToolResult) {
        when (result) {
            is ToolResult.Success -> {
                println(styled("✓ ${result.toolName}: ${result.output}", AnsiStyle.Success))
            }
            is ToolResult.Error -> {
                println(styled("✗ ${result.toolName}: ${result.error}", AnsiStyle.Error))
            }
            is ToolResult.Stream -> {
                handleStream(StreamingResponseHandler().handleStream(result))
            }
            is ToolResult.Deferred -> {
                val finalResult = result.deferred.await()
                handleToolResult(finalResult)
            }
        }
    }
    
    private suspend fun handleStream(flow: Flow<String>) {
        flow.collect { chunk ->
            print(chunk)
        }
        println()
    }
    
    private fun parseInput(input: String): UserMessage = when {
        input.startsWith("@") -> parseToolRequest(input)
        input.startsWith("/") -> parseCommand(input)
        else -> UserMessage.Text(input)
    }
    
    private fun parseToolRequest(input: String): UserMessage {
        val parts = input.substring(1).split(Regex("\\s+"), 2)
        val toolName = parts[0]
        val params = if (parts.size > 1) parseParameters(parts[1]) else emptyMap()
        return UserMessage.ToolRequest(toolName, params)
    }
    
    private fun parseCommand(input: String): UserMessage {
        val parts = input.substring(1).split(Regex("\\s+"), 2)
        val command = parts[0].uppercase()
        val args = if (parts.size > 1) {
            mapOf("query" to parts[1])
        } else emptyMap()
        
        return try {
            UserMessage.Command(CommandType.valueOf(command), args)
        } catch (e: IllegalArgumentException) {
            UserMessage.Text(input) // Treat as regular text if not a valid command
        }
    }
    
    private fun parseParameters(input: String): Map<String, Any?> {
        val params = mutableMapOf<String, Any?>()
        val pattern = Regex("(\\w+)=([\"']?)([^\"'\\s]+)\\2")
        pattern.findAll(input).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[3]
            params[key] = value
        }
        return params
    }
    
    private fun registerDefaultTools(registry: ToolRegistry) {
        // Echo tool
        registry.register(tool {
            name("echo")
            description("Echo back the input message")
            category(ToolCategory.COMPUTATION)
            parameter("message", ParameterType.Text, "Message to echo")
            execute { params, _ ->
                ToolResult.Success("echo", params["message"] ?: "")
            }
        })
        
        // File operations
        registry.register(tool {
            name("file_read")
            description("Read contents of a file")
            category(ToolCategory.FILESYSTEM)
            requiresPermission(Permission.READ_FILES)
            parameter("path", ParameterType.FilePath, "Path to file")
            execute { params, _ ->
                try {
                    val path = params["path"]?.toString() ?: return@execute ToolResult.Error("file_read", "No path provided")
                    val content = java.io.File(path).readText()
                    ToolResult.Success("file_read", content)
                } catch (e: Exception) {
                    ToolResult.Error("file_read", e.message ?: "Unknown error")
                }
            }
        })
        
        // Calculator
        registry.register(tool {
            name("calculate")
            description("Perform mathematical calculations")
            category(ToolCategory.COMPUTATION)
            parameter("expression", ParameterType.Text, "Math expression")
            execute { params, _ ->
                try {
                    val expr = params["expression"]?.toString() ?: return@execute ToolResult.Error("calculate", "No expression")
                    val engine = javax.script.ScriptEngineManager().getEngineByName("JavaScript")
                    val result = engine.eval(expr)
                    ToolResult.Success("calculate", result)
                } catch (e: Exception) {
                    ToolResult.Error("calculate", e.message ?: "Invalid expression")
                }
            }
        })
    }
    
    // Styling
    enum class AnsiStyle(val code: String) {
        Title("\u001B[1;36m"),
        Subtitle("\u001B[0;36m"),
        Prompt("\u001B[1;32m"),
        Info("\u001B[0;34m"),
        Success("\u001B[0;32m"),
        Error("\u001B[0;31m"),
        Warning("\u001B[0;33m"),
        Suggestion("\u001B[0;35m"),
        Reset("\u001B[0m")
    }
    
    private fun styled(text: String, style: AnsiStyle): String =
        "${style.code}$text${AnsiStyle.Reset.code}"
    
    private fun clearScreen() {
        print("\u001B[H\u001B[2J")
        System.out.flush()
    }
    
    private suspend fun displayStatus(session: FlowSession) {
        val uptime = (System.currentTimeMillis() - session.startTime) / 1000
        val status = """
            |Session: ${session.id}
            |Uptime: ${uptime}s
            |Model: ${System.getenv("NEXUS_MODEL") ?: "gpt-3.5-turbo"}
            |Tools: ${ToolCategory.values().sumOf { session.registry.getByCategory(it).size }}
        """.trimMargin()
        
        session.responseFlow.emit(SystemResponse.Text(status))
    }
    
    private suspend fun displayHistory(session: FlowSession) {
        // TODO: Implement history tracking
        session.responseFlow.emit(SystemResponse.Text("History not yet implemented"))
    }
    
    private suspend fun exportSession(session: FlowSession) {
        // TODO: Implement session export
        session.responseFlow.emit(SystemResponse.Text("Export not yet implemented"))
    }
    
    private fun generateSessionId(): String =
        "nexus-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt(1000, 9999)}"
}

// Extension to run from main
suspend fun runNexusFlow() = NexusFlowInteractive.runInteractive()