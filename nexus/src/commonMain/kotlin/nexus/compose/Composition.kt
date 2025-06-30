package nexus.compose

import borg.trikeshed.lib.*
import nexus.capabilities.*
import nexus.reflect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Declarative tool composition language for Nexus
 * 
 * Design principles:
 * - Functional composition of tools
 * - Type-safe data flow
 * - Lazy evaluation
 * - Composable error handling
 */

// Core composition types
sealed interface ToolExpression<out T> {
    suspend fun evaluate(context: CompositionContext): Result<T>
    fun describe(): String
    
    // Combinators
    fun <R> map(transform: suspend (T) -> R): ToolExpression<R> = 
        MappedExpression(this, transform)
    
    fun <R> flatMap(transform: suspend (T) -> ToolExpression<R>): ToolExpression<R> = 
        FlatMappedExpression(this, transform)
    
    fun filter(predicate: suspend (T) -> Boolean): ToolExpression<T> = 
        FilteredExpression(this, predicate)
    
    fun recover(handler: suspend (Throwable) -> T): ToolExpression<T> = 
        RecoveredExpression(this, handler)
    
    fun retry(times: Int = 3, delay: Long = 1000): ToolExpression<T> = 
        RetryExpression(this, times, delay)
    
    fun cache(ttl: Long? = null): ToolExpression<T> = 
        CachedExpression(this, ttl)
    
    fun timeout(duration: Long): ToolExpression<T> = 
        TimeoutExpression(this, duration)
}

// Basic expressions
data class ConstantExpression<T>(val value: T) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext) = Result.success(value)
    override fun describe() = "const($value)"
}

data class VariableExpression<T>(
    val name: String,
    val type: TypeSpec
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> {
        val value = context.variables[name]
        return if (value != null && type.isAssignableFrom(value)) {
            @Suppress("UNCHECKED_CAST")
            Result.success(value as T)
        } else {
            Result.failure(IllegalArgumentException("Variable '$name' not found or type mismatch"))
        }
    }
    override fun describe() = "var($name: ${type.describe()})"
}

data class ToolCallExpression<T>(
    val toolId: String,
    val parameters: Map<String, ToolExpression<*>>,
    val outputType: TypeSpec
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> = coroutineScope {
        // Evaluate all parameter expressions
        val evaluatedParams = parameters.mapValues { (_, expr) ->
            expr.evaluate(context).getOrElse { 
                return@coroutineScope Result.failure(it) 
            }
        }
        
        // Execute tool
        val tool = context.toolRegistry.getLatest(toolId)
            ?: return@coroutineScope Result.failure(
                IllegalArgumentException("Tool '$toolId' not found")
            )
        
        try {
            val result = context.executor.execute(tool, evaluatedParams)
            @Suppress("UNCHECKED_CAST")
            Result.success(result as T)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun describe() = "$toolId(${parameters.entries.joinToString(", ") { 
        "${it.key}=${it.value.describe()}" 
    }})"
}

// Composition expressions
data class SequenceExpression<T>(
    val expressions: Indexed<ToolExpression<*>>,
    val resultSelector: suspend (Indexed<Any?>) -> T
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> = coroutineScope {
        val results = mutableListOf<Any?>()
        
        for (expr in expressions) {
            when (val result = expr.evaluate(context)) {
                is Result.success -> results.add(result.getOrNull())
                is Result.failure -> return@coroutineScope Result.failure(result.exceptionOrNull()!!)
            }
        }
        
        try {
            Result.success(resultSelector(results.toTypedArray().toSeries()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun describe() = "sequence(${expressions.joinToString(", ") { it.describe() }})"
}

data class ParallelExpression<T>(
    val expressions: Indexed<ToolExpression<*>>,
    val combiner: suspend (Indexed<Any?>) -> T,
    val failFast: Boolean = true
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> = coroutineScope {
        val deferreds = expressions.map { expr ->
            async { expr.evaluate(context) }
        }
        
        if (failFast) {
            // Cancel all on first failure
            val results = mutableListOf<Any?>()
            for (deferred in deferreds) {
                when (val result = deferred.await()) {
                    is Result.success -> results.add(result.getOrNull())
                    is Result.failure -> {
                        deferreds.forEach { it.cancel() }
                        return@coroutineScope Result.failure(result.exceptionOrNull()!!)
                    }
                }
            }
            Result.success(combiner(results.toTypedArray().toSeries()))
        } else {
            // Collect all results, including failures
            val results = deferreds.map { it.await().getOrNull() }.toTypedArray().toSeries()
            try {
                Result.success(combiner(results))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override fun describe() = "parallel(${expressions.joinToString(", ") { it.describe() }})"
}

data class ConditionalExpression<T>(
    val condition: ToolExpression<Boolean>,
    val ifTrue: ToolExpression<T>,
    val ifFalse: ToolExpression<T>
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> {
        return when (val condResult = condition.evaluate(context)) {
            is Result.success -> {
                if (condResult.getOrNull() == true) {
                    ifTrue.evaluate(context)
                } else {
                    ifFalse.evaluate(context)
                }
            }
            is Result.failure -> Result.failure(condResult.exceptionOrNull()!!)
        }
    }
    
    override fun describe() = "if(${condition.describe()}) then ${ifTrue.describe()} else ${ifFalse.describe()}"
}

data class LoopExpression<T, R>(
    val items: ToolExpression<Indexed<T>>,
    val body: suspend (T, Int) -> ToolExpression<R>,
    val accumulator: suspend (MutableList<R>, R) -> Unit = { list, item -> list.add(item) }
) : ToolExpression<List<R>> {
    override suspend fun evaluate(context: CompositionContext): Result<List<R>> = coroutineScope {
        when (val itemsResult = items.evaluate(context)) {
            is Result.success -> {
                val itemList = itemsResult.getOrNull() ?: return@coroutineScope Result.failure(
                    IllegalStateException("Items evaluation returned null")
                )
                
                val results = mutableListOf<R>()
                itemList.forEachIndexed { index, item ->
                    val expr = body(item, index)
                    when (val result = expr.evaluate(context)) {
                        is Result.success -> result.getOrNull()?.let { 
                            accumulator(results, it) 
                        }
                        is Result.failure -> return@coroutineScope Result.failure(result.exceptionOrNull()!!)
                    }
                }
                Result.success(results)
            }
            is Result.failure -> Result.failure(itemsResult.exceptionOrNull()!!)
        }
    }
    
    override fun describe() = "foreach(${items.describe()}) { ... }"
}

// Modifier expressions
data class MappedExpression<T, R>(
    val source: ToolExpression<T>,
    val transform: suspend (T) -> R
) : ToolExpression<R> {
    override suspend fun evaluate(context: CompositionContext): Result<R> {
        return when (val result = source.evaluate(context)) {
            is Result.success -> {
                try {
                    Result.success(transform(result.getOrNull()!!))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is Result.failure -> Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    override fun describe() = "${source.describe()}.map(...)"
}

data class FlatMappedExpression<T, R>(
    val source: ToolExpression<T>,
    val transform: suspend (T) -> ToolExpression<R>
) : ToolExpression<R> {
    override suspend fun evaluate(context: CompositionContext): Result<R> {
        return when (val result = source.evaluate(context)) {
            is Result.success -> {
                try {
                    val expr = transform(result.getOrNull()!!)
                    expr.evaluate(context)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is Result.failure -> Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    override fun describe() = "${source.describe()}.flatMap(...)"
}

data class FilteredExpression<T>(
    val source: ToolExpression<T>,
    val predicate: suspend (T) -> Boolean
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> {
        return when (val result = source.evaluate(context)) {
            is Result.success -> {
                val value = result.getOrNull()!!
                try {
                    if (predicate(value)) {
                        Result.success(value)
                    } else {
                        Result.failure(FilterException("Value filtered out"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is Result.failure -> Result.failure(result.exceptionOrNull()!!)
        }
    }
    
    override fun describe() = "${source.describe()}.filter(...)"
}

data class RecoveredExpression<T>(
    val source: ToolExpression<T>,
    val handler: suspend (Throwable) -> T
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> {
        return when (val result = source.evaluate(context)) {
            is Result.success -> result
            is Result.failure -> {
                try {
                    Result.success(handler(result.exceptionOrNull()!!))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }
    
    override fun describe() = "${source.describe()}.recover(...)"
}

data class RetryExpression<T>(
    val source: ToolExpression<T>,
    val times: Int,
    val delay: Long
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> {
        var lastException: Throwable? = null
        
        repeat(times) { attempt ->
            when (val result = source.evaluate(context)) {
                is Result.success -> return result
                is Result.failure -> {
                    lastException = result.exceptionOrNull()
                    if (attempt < times - 1) {
                        delay(delay)
                    }
                }
            }
        }
        
        return Result.failure(lastException ?: IllegalStateException("Retry failed"))
    }
    
    override fun describe() = "${source.describe()}.retry($times, ${delay}ms)"
}

data class CachedExpression<T>(
    val source: ToolExpression<T>,
    val ttl: Long?
) : ToolExpression<T> {
    private var cachedValue: T? = null
    private var cachedAt: Long = 0
    
    override suspend fun evaluate(context: CompositionContext): Result<T> {
        val now = System.currentTimeMillis()
        
        if (cachedValue != null && (ttl == null || now - cachedAt < ttl)) {
            return Result.success(cachedValue!!)
        }
        
        return when (val result = source.evaluate(context)) {
            is Result.success -> {
                cachedValue = result.getOrNull()
                cachedAt = now
                result
            }
            is Result.failure -> result
        }
    }
    
    override fun describe() = "${source.describe()}.cache(${ttl ?: "forever"})"
}

data class TimeoutExpression<T>(
    val source: ToolExpression<T>,
    val duration: Long
) : ToolExpression<T> {
    override suspend fun evaluate(context: CompositionContext): Result<T> = 
        withTimeoutOrNull(duration) {
            source.evaluate(context)
        } ?: Result.failure(TimeoutException("Expression timed out after ${duration}ms"))
    
    override fun describe() = "${source.describe()}.timeout(${duration}ms)"
}

// Exceptions
class FilterException(message: String) : Exception(message)
class TimeoutException(message: String) : Exception(message)

// Composition context
data class CompositionContext(
    val variables: MutableMap<String, Any?> = mutableMapOf(),
    val toolRegistry: ToolRegistry,
    val executor: ToolExecutor,
    val capabilities: Set<Capability> = emptySet(),
    val tracing: TracingContext? = null
) {
    fun withVariable(name: String, value: Any?): CompositionContext = 
        copy(variables = (variables + (name to value)).toMutableMap())
    
    fun withVariables(vars: Map<String, Any?>): CompositionContext = 
        copy(variables = (variables + vars).toMutableMap())
}

// Tool executor interface
interface ToolExecutor {
    suspend fun execute(tool: ToolMetadata, parameters: Map<String, Any?>): Any?
}

// Tracing for debugging
data class TracingContext(
    val enabled: Boolean = true,
    val collector: TraceCollector
)

interface TraceCollector {
    fun startSpan(name: String, attributes: Map<String, Any?> = emptyMap()): Span
}

interface Span {
    fun setAttribute(key: String, value: Any?)
    fun end()
}

// DSL for building expressions
class ExpressionBuilder<T> {
    fun const(value: T): ToolExpression<T> = ConstantExpression(value)
    
    fun <V> variable(name: String, type: TypeSpec): ToolExpression<V> = 
        VariableExpression(name, type)
    
    fun <R> call(
        toolId: String,
        outputType: TypeSpec,
        parameters: Map<String, ToolExpression<*>> = emptyMap()
    ): ToolExpression<R> = ToolCallExpression(toolId, parameters, outputType)
    
    fun <R> sequence(
        vararg expressions: ToolExpression<*>,
        resultSelector: suspend (Indexed<Any?>) -> R
    ): ToolExpression<R> = SequenceExpression(
        expressions.toSeries(),
        resultSelector
    )
    
    fun <R> parallel(
        vararg expressions: ToolExpression<*>,
        combiner: suspend (Indexed<Any?>) -> R,
        failFast: Boolean = true
    ): ToolExpression<R> = ParallelExpression(
        expressions.toSeries(),
        combiner,
        failFast
    )
    
    fun <R> conditional(
        condition: ToolExpression<Boolean>,
        ifTrue: ToolExpression<R>,
        ifFalse: ToolExpression<R>
    ): ToolExpression<R> = ConditionalExpression(condition, ifTrue, ifFalse)
    
    fun <I, R> loop(
        items: ToolExpression<Indexed<I>>,
        body: suspend (I, Int) -> ToolExpression<R>
    ): ToolExpression<List<R>> = LoopExpression(items, body)
}

// DSL entry point
fun <T> toolExpression(block: ExpressionBuilder<T>.() -> ToolExpression<T>): ToolExpression<T> =
    ExpressionBuilder<T>().block()

// Convenience functions
suspend fun <T> ToolExpression<T>.execute(
    toolRegistry: ToolRegistry,
    executor: ToolExecutor,
    variables: Map<String, Any?> = emptyMap()
): Result<T> {
    val context = CompositionContext(
        variables = variables.toMutableMap(),
        toolRegistry = toolRegistry,
        executor = executor
    )
    return evaluate(context)
}