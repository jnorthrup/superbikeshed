package nexus.flow

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * NexusFlow - Reactive stream-based architecture for tool execution
 * 
 * Design principles:
 * - Actor-based tool execution with message passing
 * - Reactive streams with backpressure
 * - Composable tool pipelines
 * - Type-safe message routing
 */

// Message types for actor communication
sealed interface NexusMessage {
    val id: String
    val timestamp: Long
}

sealed interface ToolMessage : NexusMessage {
    data class Execute(
        override val id: String,
        val toolName: String,
        val parameters: Map<String, Any?>,
        val replyTo: SendChannel<ToolResult>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ToolMessage
    
    data class Chain(
        override val id: String,
        val pipeline: Indexed<ToolExecution>,
        val initialInput: Map<String, Any?>,
        val replyTo: SendChannel<ToolResult>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ToolMessage
    
    data class Parallel(
        override val id: String,
        val executions: Indexed<ToolExecution>,
        val combiner: (Indexed<ToolResult>) -> ToolResult,
        val replyTo: SendChannel<ToolResult>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ToolMessage
}

data class ToolExecution(
    val toolName: String,
    val parameterMapping: (Map<String, Any?>) -> Map<String, Any?>
)

sealed interface ToolResult {
    data class Success(
        val toolName: String,
        val output: Any?,
        val metadata: Map<String, Any?> = emptyMap()
    ) : ToolResult
    
    data class Error(
        val toolName: String,
        val error: String,
        val recoverable: Boolean = true
    ) : ToolResult
    
    data class Stream(
        val toolName: String,
        val flow: Flow<StreamChunk>
    ) : ToolResult
    
    data class Deferred(
        val toolName: String,
        val deferred: kotlinx.coroutines.Deferred<ToolResult>
    ) : ToolResult
}

data class StreamChunk(
    val content: String,
    val isComplete: Boolean = false,
    val metadata: Map<String, Any?> = emptyMap()
)

// Tool capability declarations
interface ToolCapability {
    val name: String
    val description: String
    val category: ToolCategory
    val requiredPermissions: Set<Permission>
    val parameters: Indexed<Parameter>
    
    suspend fun validate(params: Map<String, Any?>): Either<ValidationError, Map<String, Any?>>
    suspend fun execute(params: Map<String, Any?>, context: ExecutionContext): ToolResult
}

enum class ToolCategory {
    FILESYSTEM, NETWORK, COMPUTATION, ANALYSIS, GENERATION, TRANSFORMATION
}

enum class Permission {
    READ_FILES, WRITE_FILES, NETWORK_ACCESS, SYSTEM_EXEC, GPU_ACCESS
}

data class Parameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null,
    val validator: (Any?) -> Boolean = { true },
    val transformer: (Any?) -> Any? = { it }
)

sealed interface ParameterType {
    data object Text : ParameterType
    data object Number : ParameterType
    data object Boolean : ParameterType
    data object FilePath : ParameterType
    data object Json : ParameterType
    data object Binary : ParameterType
    data class List(val elementType: ParameterType) : ParameterType
    data class Map(val keyType: ParameterType, val valueType: ParameterType) : ParameterType
    data class Enum(val values: Set<String>) : ParameterType
}

data class ValidationError(
    val parameter: String,
    val message: String,
    val providedValue: Any?
)

data class ExecutionContext(
    val userId: String,
    val sessionId: String,
    val permissions: Set<Permission>,
    val environment: Map<String, String>,
    val cancellationToken: CancellationToken = CancellationToken()
)

class CancellationToken {
    private val _cancelled = MutableStateFlow(false)
    val cancelled: StateFlow<Boolean> = _cancelled.asStateFlow()
    
    fun cancel() {
        _cancelled.value = true
    }
    
    suspend fun checkCancellation() {
        if (cancelled.value) {
            throw CancellationException("Operation was cancelled")
        }
    }
}

// Tool Actor implementation
class ToolActor(
    private val capability: ToolCapability,
    private val scope: CoroutineScope
) {
    private val mailbox = Channel<ToolMessage>(Channel.UNLIMITED)
    
    init {
        scope.launch {
            for (message in mailbox) {
                when (message) {
                    is ToolMessage.Execute -> handleExecute(message)
                    is ToolMessage.Chain -> handleChain(message)
                    is ToolMessage.Parallel -> handleParallel(message)
                }
            }
        }
    }
    
    suspend fun send(message: ToolMessage) {
        mailbox.send(message)
    }
    
    private suspend fun handleExecute(message: ToolMessage.Execute) {
        val result = try {
            val context = ExecutionContext(
                userId = "system",
                sessionId = message.id,
                permissions = setOf(Permission.READ_FILES),
                environment = emptyMap()
            )
            
            when (val validated = capability.validate(message.parameters)) {
                is Either.Left -> ToolResult.Error(
                    toolName = capability.name,
                    error = validated.value.message,
                    recoverable = true
                )
                is Either.Right -> capability.execute(validated.value, context)
            }
        } catch (e: Exception) {
            ToolResult.Error(
                toolName = capability.name,
                error = e.message ?: "Unknown error",
                recoverable = false
            )
        }
        
        message.replyTo.send(result)
    }
    
    private suspend fun handleChain(message: ToolMessage.Chain) {
        // Implementation for chained execution
        message.replyTo.send(ToolResult.Error(
            toolName = "chain",
            error = "Chain execution not yet implemented"
        ))
    }
    
    private suspend fun handleParallel(message: ToolMessage.Parallel) {
        // Implementation for parallel execution
        message.replyTo.send(ToolResult.Error(
            toolName = "parallel",
            error = "Parallel execution not yet implemented"
        ))
    }
    
    fun close() {
        mailbox.close()
    }
}

// Tool Registry with actor management
class ToolRegistry(private val scope: CoroutineScope) {
    private val tools = mutableMapOf<String, Join<ToolCapability, ToolActor>>()
    private val categories = mutableMapOf<ToolCategory, MutableList<String>>()
    
    fun register(capability: ToolCapability) {
        val actor = ToolActor(capability, scope)
        tools[capability.name] = capability j actor
        
        categories.getOrPut(capability.category) { mutableListOf() }
            .add(capability.name)
    }
    
    fun getActor(name: String): ToolActor? = tools[name]?.b
    
    fun getCapability(name: String): ToolCapability? = tools[name]?.a
    
    fun getByCategory(category: ToolCategory): Indexed<String> = 
        categories[category]?.toTypedArray()?.toSeries() ?: emptyArray<String>().toSeries()
    
    fun search(query: String): Indexed<ToolCapability> {
        val results = tools.values
            .filter { (capability, _) ->
                capability.name.contains(query, ignoreCase = true) ||
                capability.description.contains(query, ignoreCase = true)
            }
            .map { it.a }
            .toTypedArray()
        
        return results.toSeries()
    }
    
    suspend fun execute(
        toolName: String,
        parameters: Map<String, Any?>
    ): ToolResult = coroutineScope {
        val actor = getActor(toolName) 
            ?: return@coroutineScope ToolResult.Error(
                toolName = toolName,
                error = "Tool not found: $toolName"
            )
        
        val replyChannel = Channel<ToolResult>(1)
        val message = ToolMessage.Execute(
            id = generateId(),
            toolName = toolName,
            parameters = parameters,
            replyTo = replyChannel
        )
        
        actor.send(message)
        replyChannel.receive()
    }
    
    fun close() {
        tools.values.forEach { it.b.close() }
        tools.clear()
        categories.clear()
    }
    
    private fun generateId(): String = 
        "${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}"
}

// Flow-based tool execution with backpressure
class FlowExecutor(
    private val registry: ToolRegistry,
    private val bufferSize: Int = 64
) {
    fun executeAsFlow(
        toolName: String,
        parameters: Flow<Map<String, Any?>>,
        concurrency: Int = 1
    ): Flow<ToolResult> = flow {
        parameters
            .buffer(bufferSize)
            .map { params ->
                async { registry.execute(toolName, params) }
            }
            .buffer(concurrency)
            .collect { deferred ->
                emit(deferred.await())
            }
    }
    
    fun pipeline(vararg tools: String): Flow<Map<String, Any?>>.() -> Flow<ToolResult> = {
        this.scan(emptyMap<String, Any?>() to null as ToolResult?) { (params, _), newParams ->
            val currentParams = params + newParams
            var result: ToolResult? = null
            
            for (tool in tools) {
                result = registry.execute(tool, currentParams)
                if (result is ToolResult.Error) break
                
                // Extract output for next tool
                if (result is ToolResult.Success) {
                    val output = result.output
                    if (output is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        currentParams.plus(output as Map<String, Any?>)
                    }
                }
            }
            
            currentParams to result
        }.mapNotNull { it.second }
    }
}

// Composable tool builders
class ToolBuilder {
    private var name: String = ""
    private var description: String = ""
    private var category: ToolCategory = ToolCategory.COMPUTATION
    private val permissions = mutableSetOf<Permission>()
    private val parameters = mutableListOf<Parameter>()
    private var validator: suspend (Map<String, Any?>) -> Either<ValidationError, Map<String, Any?>> = 
        { Either.right(it) }
    private var executor: suspend (Map<String, Any?>, ExecutionContext) -> ToolResult = 
        { _, _ -> ToolResult.Error("", "Not implemented") }
    
    fun name(value: String) = apply { name = value }
    fun description(value: String) = apply { description = value }
    fun category(value: ToolCategory) = apply { category = value }
    fun requiresPermission(permission: Permission) = apply { permissions.add(permission) }
    
    fun parameter(
        name: String,
        type: ParameterType,
        description: String,
        block: ParameterBuilder.() -> Unit = {}
    ) = apply {
        val builder = ParameterBuilder(name, type, description)
        builder.block()
        parameters.add(builder.build())
    }
    
    fun validate(block: suspend (Map<String, Any?>) -> Either<ValidationError, Map<String, Any?>>) = apply {
        validator = block
    }
    
    fun execute(block: suspend (Map<String, Any?>, ExecutionContext) -> ToolResult) = apply {
        executor = block
    }
    
    fun build(): ToolCapability = object : ToolCapability {
        override val name = this@ToolBuilder.name
        override val description = this@ToolBuilder.description
        override val category = this@ToolBuilder.category
        override val requiredPermissions = this@ToolBuilder.permissions.toSet()
        override val parameters = this@ToolBuilder.parameters.toTypedArray().toSeries()
        
        override suspend fun validate(params: Map<String, Any?>) = validator(params)
        override suspend fun execute(params: Map<String, Any?>, context: ExecutionContext) = 
            executor(params, context)
    }
}

class ParameterBuilder(
    private val name: String,
    private val type: ParameterType,
    private val description: String
) {
    private var required = true
    private var defaultValue: Any? = null
    private var validator: (Any?) -> Boolean = { true }
    private var transformer: (Any?) -> Any? = { it }
    
    fun optional(default: Any? = null) = apply {
        required = false
        defaultValue = default
    }
    
    fun validate(block: (Any?) -> Boolean) = apply {
        validator = block
    }
    
    fun transform(block: (Any?) -> Any?) = apply {
        transformer = block
    }
    
    fun build() = Parameter(
        name = name,
        type = type,
        description = description,
        required = required,
        defaultValue = defaultValue,
        validator = validator,
        transformer = transformer
    )
}

// DSL for tool definition
fun tool(block: ToolBuilder.() -> Unit): ToolCapability =
    ToolBuilder().apply(block).build()

// Streaming response handler
class StreamingResponseHandler(
    private val bufferSize: Int = 16,
    private val timeout: Duration = 100.milliseconds
) {
    fun handleStream(result: ToolResult.Stream): Flow<String> = flow {
        result.flow
            .buffer(bufferSize)
            .collect { chunk ->
                emit(chunk.content)
                if (chunk.isComplete) {
                    return@collect
                }
            }
    }.flowOn(Dispatchers.IO)
    
    fun combineStreams(vararg results: ToolResult.Stream): Flow<String> = 
        results.map { handleStream(it) }
            .asIterable()
            .merge()
            .buffer(bufferSize)
}