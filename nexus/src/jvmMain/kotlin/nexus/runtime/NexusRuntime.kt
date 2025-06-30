package nexus.runtime

import borg.trikeshed.lib.*
import nexus.capabilities.*
import nexus.reflect.*
import nexus.compose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import k2script.ai.llm.LiteLLMClient
import java.io.File
import java.nio.file.*
import kotlin.io.path.*

/**
 * Nexus Runtime - The ultimate interactive system with all advanced features
 */
object NexusRuntime {
    
    // Runtime state
    private val capabilityRegistry = CapabilityRegistry()
    private val toolRegistry = ToolRegistry()
    private val toolDiscovery = ToolDiscovery(toolRegistry)
    private val hotReloader = HotReloader()
    private val distributedExecutor = DistributedExecutor()
    
    // Interactive session
    data class Session(
        val id: String = generateId(),
        val user: User,
        val capabilities: MutableSet<Capability> = mutableSetOf(),
        val variables: MutableMap<String, Any?> = mutableMapOf(),
        val history: MutableList<HistoryEntry> = mutableListOf(),
        val executor: ToolExecutor = LocalToolExecutor()
    )
    
    data class User(
        val id: String,
        val name: String,
        val permissions: Set<Permission>
    )
    
    data class HistoryEntry(
        val timestamp: Long,
        val input: String,
        val output: Any?,
        val duration: Long
    )
    
    // Hot reload support
    class HotReloader {
        private val watchers = mutableMapOf<Path, WatchService>()
        private val reloadHandlers = mutableMapOf<Path, suspend (Path) -> Unit>()
        
        suspend fun watch(path: Path, handler: suspend (Path) -> Unit) = coroutineScope {
            val watchService = FileSystems.getDefault().newWatchService()
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
            watchers[path] = watchService
            reloadHandlers[path] = handler
            
            launch {
                while (isActive) {
                    val key = watchService.take()
                    for (event in key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            val changed = path.resolve(event.context() as Path)
                            handler(changed)
                        }
                    }
                    key.reset()
                }
            }
        }
        
        fun stopWatching(path: Path) {
            watchers[path]?.close()
            watchers.remove(path)
            reloadHandlers.remove(path)
        }
    }
    
    // Distributed execution
    class DistributedExecutor {
        private val nodes = mutableMapOf<String, ExecutionNode>()
        
        data class ExecutionNode(
            val id: String,
            val address: String,
            val capabilities: Set<Capability>,
            val status: NodeStatus = NodeStatus.AVAILABLE
        )
        
        enum class NodeStatus {
            AVAILABLE, BUSY, OFFLINE
        }
        
        suspend fun execute(
            tool: ToolMetadata,
            parameters: Map<String, Any?>,
            preferredNode: String? = null
        ): Any? = coroutineScope {
            val node = selectNode(tool.capability, preferredNode)
                ?: throw IllegalStateException("No available node for capability: ${tool.capability.id}")
            
            // In a real implementation, this would make a remote call
            LocalToolExecutor().execute(tool, parameters)
        }
        
        private fun selectNode(capability: Capability, preferredNode: String?): ExecutionNode? {
            if (preferredNode != null) {
                val node = nodes[preferredNode]
                if (node?.status == NodeStatus.AVAILABLE && node.capabilities.any { it.implies(capability) }) {
                    return node
                }
            }
            
            return nodes.values
                .filter { it.status == NodeStatus.AVAILABLE }
                .filter { node -> node.capabilities.any { it.implies(capability) } }
                .randomOrNull()
        }
        
        fun registerNode(node: ExecutionNode) {
            nodes[node.id] = node
        }
        
        fun unregisterNode(nodeId: String) {
            nodes.remove(nodeId)
        }
    }
    
    // Local tool executor
    class LocalToolExecutor : ToolExecutor {
        override suspend fun execute(tool: ToolMetadata, parameters: Map<String, Any?>): Any? {
            // Validate parameters
            for (param in tool.interface.inputs) {
                if (param.required && param.name !in parameters) {
                    throw IllegalArgumentException("Missing required parameter: ${param.name}")
                }
                
                parameters[param.name]?.let { value ->
                    if (!param.type.isAssignableFrom(value)) {
                        throw IllegalArgumentException(
                            "Parameter '${param.name}' type mismatch: expected ${param.type.describe()}"
                        )
                    }
                    
                    param.validation?.let { rule ->
                        when (val result = rule.validate(value)) {
                            is ValidationResult.Error -> throw IllegalArgumentException(
                                "Parameter '${param.name}' validation failed: ${result.message}"
                            )
                            is ValidationResult.Success -> Unit
                        }
                    }
                }
            }
            
            // Execute tool (simplified - would call actual implementation)
            return when (tool.id) {
                "echo" -> parameters["message"]
                "calculate" -> {
                    val expr = parameters["expression"] as? String ?: ""
                    javax.script.ScriptEngineManager().getEngineByName("JavaScript").eval(expr)
                }
                "file_read" -> File(parameters["path"] as String).readText()
                else -> throw UnsupportedOperationException("Tool not implemented: ${tool.id}")
            }
        }
    }
    
    // Main interactive loop
    suspend fun runInteractive() = coroutineScope {
        println(styled("🚀 Nexus Runtime - Advanced Interactive System", Style.Title))
        println(styled("Capabilities: ${capabilityRegistry.search(CapabilityQuery()).size} registered", Style.Info))
        println(styled("Tools: ${toolRegistry.search(ToolQuery()).size} available", Style.Info))
        println()
        
        // Initialize default tools
        registerDefaultTools()
        
        // Create session
        val session = Session(
            user = User("user", "Interactive User", setOf(
                Permission.FileSystem("read", setOf(FileOperation.READ)),
                Permission.Execution("execute", setOf("calculate"))
            ))
        )
        
        // Start hot reload for tools directory
        val toolsPath = Paths.get("tools")
        if (toolsPath.exists()) {
            hotReloader.watch(toolsPath) { changed ->
                println(styled("🔄 Reloading: $changed", Style.Warning))
                reloadTool(changed)
            }
        }
        
        // Command loop
        while (isActive) {
            print(styled("> ", Style.Prompt))
            val input = readlnOrNull() ?: break
            
            if (input.isBlank()) continue
            
            val startTime = System.currentTimeMillis()
            
            try {
                val result = processInput(input, session)
                val duration = System.currentTimeMillis() - startTime
                
                session.history.add(HistoryEntry(startTime, input, result, duration))
                
                when (result) {
                    is CommandResult.Exit -> break
                    is CommandResult.Output -> println(styled(result.text, Style.Success))
                    is CommandResult.Error -> println(styled("Error: ${result.message}", Style.Error))
                    is CommandResult.ToolResult -> displayToolResult(result.value)
                    is CommandResult.Stream -> handleStream(result.flow)
                }
                
            } catch (e: Exception) {
                println(styled("Error: ${e.message}", Style.Error))
                e.printStackTrace()
            }
        }
        
        println(styled("\nGoodbye!", Style.Title))
    }
    
    // Input processing
    private suspend fun processInput(input: String, session: Session): CommandResult = when {
        input.startsWith("/") -> processCommand(input.substring(1), session)
        input.startsWith("@") -> processToolCall(input.substring(1), session)
        input.contains("=") && !input.contains(" ") -> processAssignment(input, session)
        else -> processNaturalLanguage(input, session)
    }
    
    private suspend fun processCommand(command: String, session: Session): CommandResult {
        val parts = command.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val args = parts.getOrNull(1) ?: ""
        
        return when (cmd) {
            "help" -> CommandResult.Output(helpText())
            "tools" -> CommandResult.Output(listTools())
            "capabilities" -> CommandResult.Output(listCapabilities())
            "introspect" -> introspectTool(args)
            "compose" -> composeTools(args, session)
            "reload" -> reloadTools()
            "nodes" -> CommandResult.Output(listNodes())
            "history" -> CommandResult.Output(showHistory(session))
            "clear" -> {
                session.variables.clear()
                session.history.clear()
                CommandResult.Output("Session cleared")
            }
            "exit", "quit" -> CommandResult.Exit
            else -> CommandResult.Error("Unknown command: /$cmd")
        }
    }
    
    private suspend fun processToolCall(input: String, session: Session): CommandResult {
        val parts = input.split(" ", limit = 2)
        val toolName = parts[0]
        val paramString = parts.getOrNull(1) ?: ""
        
        val tool = toolRegistry.getLatest(toolName)
            ?: return CommandResult.Error("Tool not found: $toolName")
        
        // Check capabilities
        if (!session.capabilities.any { it.implies(tool.capability) }) {
            // Request capability
            println(styled("Tool requires capability: ${tool.capability.metadata.name}", Style.Warning))
            print("Grant capability? (y/n): ")
            if (readln().lowercase() == "y") {
                session.capabilities.add(tool.capability)
            } else {
                return CommandResult.Error("Capability not granted")
            }
        }
        
        val parameters = parseParameters(paramString, session)
        
        return try {
            val result = session.executor.execute(tool, parameters)
            CommandResult.ToolResult(result)
        } catch (e: Exception) {
            CommandResult.Error("Tool execution failed: ${e.message}")
        }
    }
    
    private fun processAssignment(input: String, session: Session): CommandResult {
        val parts = input.split("=", limit = 2)
        val name = parts[0].trim()
        val value = parts[1].trim()
        
        session.variables[name] = parseValue(value)
        return CommandResult.Output("$name = ${session.variables[name]}")
    }
    
    private suspend fun processNaturalLanguage(input: String, session: Session): CommandResult {
        // Use LLM to understand intent and suggest tools
        val suggestions = suggestTools(input)
        
        if (suggestions.isNotEmpty()) {
            println(styled("Suggested tools:", Style.Info))
            suggestions.forEachIndexed { index, tool ->
                println("  ${index + 1}. ${tool.id} - ${tool.documentation.summary}")
            }
            print("Select tool (1-${suggestions.size}) or press Enter to continue: ")
            
            val selection = readlnOrNull()?.toIntOrNull()
            if (selection != null && selection in 1..suggestions.size) {
                val tool = suggestions[selection - 1]
                return CommandResult.Output("Use: @${tool.id} ${tool.interface.inputs.joinToString(" ") { 
                    "${it.name}=<${it.type.describe()}>" 
                }}")
            }
        }
        
        // Process with LLM
        val response = callLLM(input, session)
        return CommandResult.Output(response)
    }
    
    private suspend fun callLLM(input: String, session: Session): String {
        val messages = listOf(
            mapOf("role" to "system", "content" to "You are Nexus, an advanced AI assistant with tool capabilities."),
            mapOf("role" to "user", "content" to input)
        )
        
        return try {
            val future = LiteLLMClient.complete(
                model = System.getenv("NEXUS_MODEL") ?: "gpt-3.5-turbo",
                messages = messages,
                temperature = 0.7,
                maxTokens = 2000
            )
            
            val response = future.get()
            response.content ?: "No response"
        } catch (e: Exception) {
            "LLM error: ${e.message}"
        }
    }
    
    // Helper functions
    private fun parseParameters(paramString: String, session: Session): Map<String, Any?> {
        val params = mutableMapOf<String, Any?>()
        val pattern = Regex("(\\w+)=([\"']?)([^\"'\\s]+)\\2")
        
        pattern.findAll(paramString).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[3]
            
            // Resolve variables
            params[key] = if (value.startsWith("$")) {
                session.variables[value.substring(1)]
            } else {
                parseValue(value)
            }
        }
        
        return params
    }
    
    private fun parseValue(value: String): Any = when {
        value == "true" -> true
        value == "false" -> false
        value.toIntOrNull() != null -> value.toInt()
        value.toDoubleOrNull() != null -> value.toDouble()
        else -> value
    }
    
    private fun suggestTools(input: String): List<ToolMetadata> {
        // Simple keyword matching - could use more sophisticated NLP
        val keywords = input.lowercase().split(" ")
        return toolRegistry.search(ToolQuery()).filter { tool ->
            keywords.any { keyword ->
                tool.id.lowercase().contains(keyword) ||
                tool.documentation.summary.lowercase().contains(keyword) ||
                tool.documentation.description.lowercase().contains(keyword)
            }
        }.take(5)
    }
    
    private fun displayToolResult(result: Any?) {
        when (result) {
            is String -> println(result)
            is Number -> println(styled(result.toString(), Style.Success))
            is Boolean -> println(styled(result.toString(), Style.Success))
            is List<*> -> result.forEach { println("  • $it") }
            is Map<*, *> -> result.forEach { (k, v) -> println("  $k: $v") }
            else -> println(result?.toString() ?: "null")
        }
    }
    
    private suspend fun handleStream(flow: Flow<String>) {
        flow.collect { chunk ->
            print(chunk)
        }
        println()
    }
    
    // Commands implementation
    private fun helpText() = """
        |Commands:
        |  /help                - Show this help
        |  /tools               - List available tools
        |  /capabilities        - List registered capabilities
        |  /introspect <tool>   - Show detailed tool information
        |  /compose <expr>      - Compose tools declaratively
        |  /reload              - Reload all tools
        |  /nodes               - List execution nodes
        |  /history             - Show session history
        |  /clear               - Clear session
        |  /exit                - Exit the program
        |
        |Tool usage:
        |  @toolname param1=value1 param2=value2
        |
        |Variables:
        |  name=value           - Assign variable
        |  @tool param=$name    - Use variable
        |
        |Natural language:
        |  Just type naturally and Nexus will understand
    """.trimMargin()
    
    private fun listTools(): String = buildString {
        appendLine("Available tools:")
        toolRegistry.search(ToolQuery()).groupBy { it.capability.metadata.tags.firstOrNull() ?: "Other" }
            .forEach { (category, tools) ->
                appendLine("\n$category:")
                tools.forEach { tool ->
                    appendLine("  • ${tool.id} v${tool.version} - ${tool.documentation.summary}")
                }
            }
    }
    
    private fun listCapabilities(): String = buildString {
        appendLine("Registered capabilities:")
        capabilityRegistry.search(CapabilityQuery()).forEach { capability ->
            appendLine("  • ${capability.id.value} - ${capability.metadata.description}")
        }
    }
    
    private fun introspectTool(toolId: String): CommandResult {
        val tool = toolRegistry.getLatest(toolId)
            ?: return CommandResult.Error("Tool not found: $toolId")
        
        return CommandResult.Output(buildString {
            appendLine("Tool: ${tool.id} v${tool.version}")
            appendLine("Capability: ${tool.capability.id.value}")
            appendLine()
            appendLine("Description: ${tool.documentation.description}")
            appendLine()
            appendLine("Parameters:")
            tool.interface.inputs.forEach { param ->
                appendLine("  ${param.name}: ${param.type.describe()} ${if (param.required) "(required)" else "(optional)"}")
                appendLine("    ${param.description}")
            }
            appendLine()
            appendLine("Output: ${tool.interface.outputs.type.describe()}")
            if (tool.interface.errors.size > 0) {
                appendLine()
                appendLine("Errors:")
                tool.interface.errors.forEach { error ->
                    appendLine("  ${error.code}: ${error.message}")
                }
            }
        })
    }
    
    private suspend fun composeTools(expression: String, session: Session): CommandResult {
        // Parse and execute tool composition expression
        // This would use the composition DSL in a real implementation
        return CommandResult.Output("Composition not yet implemented")
    }
    
    private suspend fun reloadTools(): CommandResult {
        // Reload all tools from disk
        return CommandResult.Output("Reloaded ${toolRegistry.search(ToolQuery()).size} tools")
    }
    
    private suspend fun reloadTool(path: Path) {
        // Reload specific tool from path
        println("Reloading tool from: $path")
    }
    
    private fun listNodes(): String = buildString {
        appendLine("Execution nodes:")
        distributedExecutor.nodes.values.forEach { node ->
            appendLine("  • ${node.id} at ${node.address} - ${node.status}")
        }
    }
    
    private fun showHistory(session: Session): String = buildString {
        appendLine("Session history:")
        session.history.takeLast(10).forEach { entry ->
            val time = java.time.Instant.ofEpochMilli(entry.timestamp)
            appendLine("  [${time}] ${entry.input} (${entry.duration}ms)")
        }
    }
    
    // Tool registration
    private fun registerDefaultTools() {
        // Echo tool
        val echoCapability = SimpleCapability(
            id = CapabilityId.generate("nexus", "echo"),
            metadata = CapabilityMetadata(
                name = "Echo",
                description = "Echo back input",
                version = "1.0.0",
                tags = arrayOf("utility", "simple").toSeries()
            ),
            permissions = emptySet()
        )
        
        capabilityRegistry.register(echoCapability)
        
        toolRegistry.register(ToolMetadata(
            id = "echo",
            version = SemanticVersion(1, 0, 0),
            capability = echoCapability,
            interface = ToolInterface(
                inputs = arrayOf(
                    ParameterSpec(
                        name = "message",
                        type = TypeSpec.Primitive(String::class),
                        description = "Message to echo"
                    )
                ).toSeries(),
                outputs = OutputSpec(TypeSpec.Primitive(String::class)),
                errors = emptyArray<ErrorSpec>().toSeries(),
                sideEffects = emptyArray<SideEffect>().toSeries(),
                constraints = emptyArray<Constraint>().toSeries()
            ),
            implementation = ToolImplementation(
                language = "kotlin",
                runtime = RuntimeInfo(
                    name = "jvm",
                    version = SemanticVersion(21, 0, 0),
                    platform = Platform.CURRENT
                ),
                dependencies = emptyArray<Dependency>().toSeries()
            ),
            documentation = Documentation(
                summary = "Echo back the input message",
                description = "A simple tool that returns the input message unchanged",
                parameters = mapOf("message" to ParameterDoc("The message to echo back"))
            ),
            examples = arrayOf(
                Example(
                    name = "Basic echo",
                    description = "Echo a simple message",
                    input = mapOf("message" to "Hello, World!"),
                    expectedOutput = "Hello, World!"
                )
            ).toSeries()
        ))
        
        // Add more default tools...
    }
    
    // Result types
    sealed interface CommandResult {
        data object Exit : CommandResult
        data class Output(val text: String) : CommandResult
        data class Error(val message: String) : CommandResult
        data class ToolResult(val value: Any?) : CommandResult
        data class Stream(val flow: Flow<String>) : CommandResult
    }
    
    // Styling
    enum class Style(val code: String) {
        Title("\u001B[1;36m"),
        Prompt("\u001B[1;32m"),
        Info("\u001B[0;36m"),
        Success("\u001B[0;32m"),
        Warning("\u001B[0;33m"),
        Error("\u001B[0;31m"),
        Reset("\u001B[0m")
    }
    
    private fun styled(text: String, style: Style) = "${style.code}$text${Style.Reset.code}"
    
    private fun generateId() = "nexus-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt(1000, 9999)}"
}

// Entry point
suspend fun main() = NexusRuntime.runInteractive()