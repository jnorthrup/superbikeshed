package nexus.pure

/**
 * Pure functional interactive LLM system with composable tools
 */

// Interactive session state
data class SessionState(
    val id: String,
    val tools: Indexed<ToolDefinition<*, *>>,
    val history: Indexed<Interaction>,
    val context: Map<String, Any?>,
    val metrics: Map<String, Any?>
)

// Interaction types
sealed class Interaction {
    data class UserInput(val message: String, val timestamp: Long) : Interaction()
    data class ToolExecution(
        val toolId: ToolId, 
        val input: Any?, 
        val output: Result<Any?>, 
        val duration: Long,
        val timestamp: Long
    ) : Interaction()
    data class SystemMessage(val message: String, val timestamp: Long) : Interaction()
    data class Error(val error: String, val timestamp: Long) : Interaction()
}

// Interactive commands
sealed class Command {
    data class ExecuteTool(val toolId: ToolId, val input: String) : Command()
    data class ListTools(val query: String? = null) : Command()
    data class DescribeTool(val toolId: ToolId) : Command()
    data class ComposeTool(val composition: String) : Command()
    data class Chat(val message: String) : Command()
    data class ShowHistory(val limit: Int = 10) : Command()
    data class ShowMetrics : Command()
    data class Help : Command()
    data class Exit : Command()
}

// Command parser
object CommandParser {
    
    fun parse(input: String): Command {
        val trimmed = input.trim()
        
        return when {
            trimmed.startsWith("/tools") -> {
                val query = trimmed.removePrefix("/tools").trim().takeIf { it.isNotEmpty() }
                Command.ListTools(query)
            }
            trimmed.startsWith("/describe ") -> {
                val toolId = trimmed.removePrefix("/describe ").trim()
                Command.DescribeTool(ToolId(toolId))
            }
            trimmed.startsWith("/compose ") -> {
                val composition = trimmed.removePrefix("/compose ").trim()
                Command.ComposeTool(composition)
            }
            trimmed.startsWith("/exec ") -> {
                val parts = trimmed.removePrefix("/exec ").split(" ", limit = 2)
                if (parts.size >= 2) {
                    Command.ExecuteTool(ToolId(parts[0]), parts[1])
                } else {
                    Command.ExecuteTool(ToolId(parts[0]), "")
                }
            }
            trimmed.startsWith("/history") -> {
                val limit = trimmed.removePrefix("/history").trim().toIntOrNull() ?: 10
                Command.ShowHistory(limit)
            }
            trimmed == "/metrics" -> Command.ShowMetrics
            trimmed == "/help" -> Command.Help
            trimmed == "/exit" || trimmed == "/quit" -> Command.Exit
            else -> Command.Chat(trimmed)
        }
    }
}

// Interactive session manager
class InteractiveSession(
    private val registry: PureToolRegistry,
    private val handler: EffectHandler<Operation<*>> = HandlerEnvironment.get()
) {
    private var state = SessionState(
        id = "session-${System.currentTimeMillis()}",
        tools = registry.getAllSpecs().map { spec ->
            registry.get<Any?, Any?>(spec.id).getOrElse { 
                throw IllegalStateException("Tool not found: ${spec.id}")
            }
        }.toTypedArray().toSeries(),
        history = emptyArray<Interaction>().toSeries(),
        context = emptyMap(),
        metrics = emptyMap()
    )
    
    suspend fun execute(command: Command): String = when (command) {
        is Command.ExecuteTool -> executeTool(command.toolId, command.input)
        is Command.ListTools -> listTools(command.query)
        is Command.DescribeTool -> describeTool(command.toolId)
        is Command.ComposeTool -> composeTool(command.composition)
        is Command.Chat -> processChat(command.message)
        is Command.ShowHistory -> showHistory(command.limit)
        is Command.ShowMetrics -> showMetrics()
        is Command.Help -> showHelp()
        is Command.Exit -> "Goodbye!"
    }
    
    private suspend fun executeTool(toolId: ToolId, input: String): String {
        val tool = registry.get<String, Any?>(toolId).getOrElse { 
            return "Tool not found: ${toolId.value}"
        }
        
        val start = System.currentTimeMillis()
        
        return try {
            val result = tool.execute(input, Environment(handler))
            val duration = System.currentTimeMillis() - start
            
            addToHistory(Interaction.ToolExecution(toolId, input, result, duration, System.currentTimeMillis()))
            
            when (result) {
                is Result.Success -> "Result: ${result.value}"
                is Result.Failure -> "Error: ${result.error}"
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            addToHistory(Interaction.Error("Tool execution failed: ${e.message}", System.currentTimeMillis()))
            "Error executing tool: ${e.message}"
        }
    }
    
    private fun listTools(query: String?): String {
        val tools = if (query != null) {
            registry.search(query)
        } else {
            state.tools
        }
        
        if (tools.isEmpty()) {
            return if (query != null) "No tools found for query: $query" else "No tools available"
        }
        
        return buildString {
            appendLine("Available tools:")
            tools.forEach { tool ->
                appendLine("  ${tool.spec.id.value}: ${tool.spec.name} - ${tool.spec.description}")
            }
        }
    }
    
    private fun describeTool(toolId: ToolId): String {
        val tool = registry.get<Any?, Any?>(toolId).getOrElse {
            return "Tool not found: ${toolId.value}"
        }
        
        return buildString {
            appendLine("Tool: ${tool.spec.name}")
            appendLine("ID: ${tool.spec.id.value}")
            appendLine("Description: ${tool.spec.description}")
            appendLine("Input Type: ${tool.spec.inputType}")
            appendLine("Output Type: ${tool.spec.outputType}")
            
            if (tool.spec.constraints.isNotEmpty()) {
                appendLine("Constraints:")
                tool.spec.constraints.forEach { constraint ->
                    appendLine("  - $constraint")
                }
            }
            
            if (tool.spec.examples.isNotEmpty()) {
                appendLine("Examples:")
                tool.spec.examples.forEach { example ->
                    appendLine("  ${example.name}: ${example.input} -> ${example.expectedOutput}")
                    example.description?.let { desc ->
                        appendLine("    $desc")
                    }
                }
            }
        }
    }
    
    private fun composeTool(composition: String): String {
        // Parse simple composition expressions like "echo | uppercase" or "readFile & length"
        return try {
            when {
                " | " in composition -> {
                    val parts = composition.split(" | ").map { it.trim() }
                    "Composition: ${parts.joinToString(" -> ")}\n(Sequential composition not yet implemented)"
                }
                " & " in composition -> {
                    val parts = composition.split(" & ").map { it.trim() }
                    "Composition: ${parts.joinToString(" & ")}\n(Parallel composition not yet implemented)"
                }
                " || " in composition -> {
                    val parts = composition.split(" || ").map { it.trim() }
                    "Composition: ${parts.joinToString(" OR ")}\n(Choice composition not yet implemented)"
                }
                else -> "Invalid composition syntax. Use | for sequence, & for parallel, || for choice"
            }
        } catch (e: Exception) {
            "Error parsing composition: ${e.message}"
        }
    }
    
    private suspend fun processChat(message: String): String {
        addToHistory(Interaction.UserInput(message, System.currentTimeMillis()))
        
        // Simple response for now - in a real implementation would integrate with LLM
        return when {
            "hello" in message.lowercase() -> "Hello! I'm the Nexus interactive system. Type /help for commands."
            "help" in message.lowercase() -> showHelp()
            "tools" in message.lowercase() -> listTools(null)
            else -> "I received: \"$message\". Try using /help to see available commands."
        }
    }
    
    private fun showHistory(limit: Int): String {
        val recent = state.history.takeLast(limit)
        
        if (recent.isEmpty()) {
            return "No history available"
        }
        
        return buildString {
            appendLine("Recent interactions:")
            recent.forEach { interaction ->
                when (interaction) {
                    is Interaction.UserInput -> 
                        appendLine("  User: ${interaction.message}")
                    is Interaction.ToolExecution -> 
                        appendLine("  Tool ${interaction.toolId.value}: ${interaction.input} -> ${interaction.output} (${interaction.duration}ms)")
                    is Interaction.SystemMessage -> 
                        appendLine("  System: ${interaction.message}")
                    is Interaction.Error -> 
                        appendLine("  Error: ${interaction.error}")
                }
            }
        }
    }
    
    private fun showMetrics(): String {
        val toolExecutions = state.history.filterIsInstance<Interaction.ToolExecution>()
        
        if (toolExecutions.isEmpty()) {
            return "No metrics available"
        }
        
        val byTool = toolExecutions.groupBy { it.toolId.value }
        
        return buildString {
            appendLine("Tool Execution Metrics:")
            byTool.forEach { (toolId, executions) ->
                val totalExecutions = executions.size
                val successCount = executions.count { it.output is Result.Success }
                val avgDuration = executions.map { it.duration }.average()
                val successRate = (successCount.toDouble() / totalExecutions * 100)
                
                appendLine("  $toolId:")
                appendLine("    Executions: $totalExecutions")
                appendLine("    Success Rate: ${"%.1f".format(successRate)}%")
                appendLine("    Avg Duration: ${"%.1f".format(avgDuration)}ms")
            }
        }
    }
    
    private fun showHelp(): String = """
        Nexus Interactive LLM System
        
        Commands:
          /tools [query]     - List available tools (optionally filtered)
          /describe <tool>   - Show detailed tool information
          /exec <tool> <arg> - Execute a tool with argument
          /compose <expr>    - Compose tools (| for sequence, & parallel, || choice)
          /history [limit]   - Show interaction history
          /metrics          - Show execution metrics
          /help             - Show this help
          /exit             - Exit the session
          
        You can also chat normally and the system will respond.
        
        Example compositions:
          /compose echo | uppercase     (sequential)
          /compose readFile & length    (parallel)  
          /compose tool1 || tool2       (choice)
    """.trimIndent()
    
    private fun addToHistory(interaction: Interaction) {
        state = state.copy(
            history = (state.history.toList() + interaction).toTypedArray().toSeries()
        )
    }
    
    private fun <T> Indexed<T>.takeLast(n: Int): Indexed<T> {
        val list = toList()
        return if (list.size <= n) this else list.takeLast(n).toTypedArray().toSeries()
    }
    
    private fun <T> Indexed<T>.isEmpty(): Boolean = size == 0
    
    fun getState(): SessionState = state
}

// Main interactive interface
class NexusInteractive(
    private val handler: EffectHandler<Operation<*>> = HandlerEnvironment.get()
) {
    private val registry = PureToolRegistry()
    private lateinit var session: InteractiveSession
    
    init {
        initializeTools()
    }
    
    private fun initializeTools() {
        // Register built-in tools
        registry.register(PureAlgebraicTools.echo)
        registry.register(PureAlgebraicTools.uppercase)
        registry.register(PureAlgebraicTools.readFileContent)
        registry.register(PureAlgebraicTools.counter)
        
        // Register composed tools
        registry.register(MonadicExamples.processText)
        registry.register(MonadicExamples.flexibleRead)
        registry.register(MonadicExamples.robustRead)
    }
    
    suspend fun start() {
        session = InteractiveSession(registry, handler)
        
        println("🤖 Nexus Interactive LLM System")
        println("Type /help for commands or just chat normally")
        println()
        
        while (true) {
            print("nexus> ")
            val input = readlnOrNull()?.trim() ?: break
            
            if (input.isEmpty()) continue
            
            val command = CommandParser.parse(input)
            val response = session.execute(command)
            
            println(response)
            println()
            
            if (command is Command.Exit) break
        }
    }
    
    fun getSession(): InteractiveSession = session
    fun getRegistry(): PureToolRegistry = registry
}

// Entry point for the pure functional interactive system
object PureFunctionalNexus {
    
    @JvmStatic
    suspend fun main(args: Array<String>) {
        val environment = args.getOrNull(0) ?: "development"
        val handler = HandlerEnvironment.get(environment)
        
        val interactive = NexusInteractive(handler)
        interactive.start()
    }
    
    // Factory methods for different configurations
    fun withHandler(handler: EffectHandler<Operation<*>>): NexusInteractive =
        NexusInteractive(handler)
    
    fun development(): NexusInteractive = 
        NexusInteractive(CommonHandlers.development)
    
    fun production(): NexusInteractive = 
        NexusInteractive(CommonHandlers.production)
    
    fun testing(
        responses: Map<String, Any?> = emptyMap(),
        failureRate: Double = 0.0
    ): NexusInteractive = 
        NexusInteractive(CommonHandlers.simulation(responses, failureRate = failureRate))
}