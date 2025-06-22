package borg.trikeshed.io

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * DSL-driven stackable bubbling router for coroutines and keys
 * Provides fluent composition and context management
 */
class CoroutineRouter private constructor(
    private val parent: CoroutineRouter? = null,
    private val context: CoroutineContext = EmptyCoroutineContext,
    private val handlers: MutableMap<Any, suspend (Any) -> Any> = mutableMapOf(),
    private val interceptors: MutableList<suspend (Any) -> Any> = mutableListOf(),
    private val name: String = "router"
) {
    
    /**
     * DSL builder for creating router configurations
     */
    class RouterBuilder {
        private var context: CoroutineContext = EmptyCoroutineContext
        private var name: String = "router"
        private val handlers = mutableMapOf<Any, suspend (Any) -> Any>()
        private val interceptors = mutableListOf<suspend (Any) -> Any>()
        
        fun context(ctx: CoroutineContext) = apply { context = ctx }
        fun name(n: String) = apply { name = n }
        
        fun handler(key: Any, block: suspend (Any) -> Any) = apply {
            handlers[key] = block
        }
        
        fun interceptor(block: suspend (Any) -> Any) = apply {
            interceptors.add(block)
        }
        
        fun build(parent: CoroutineRouter? = null): CoroutineRouter {
            return CoroutineRouter(parent, context, handlers, interceptors, name)
        }
    }
    
    companion object {
        /**
         * DSL entry point for creating routers
         */
        fun router(block: RouterBuilder.() -> Unit): CoroutineRouter {
            return RouterBuilder().apply(block).build()
        }
        
        /**
         * Create a root router
         */
        fun root(block: RouterBuilder.() -> Unit): CoroutineRouter {
            return router(block)
        }
    }
    
    /**
     * DSL for building child routers
     */
    fun child(block: RouterBuilder.() -> Unit): CoroutineRouter {
        return RouterBuilder().apply(block).build(this)
    }
    
    /**
     * DSL for adding handlers inline
     */
    fun handle(key: Any, block: suspend (Any) -> Any): CoroutineRouter {
        handlers[key] = block
        return this
    }
    
    /**
     * DSL for adding interceptors inline
     */
    fun intercept(block: suspend (Any) -> Any): CoroutineRouter {
        interceptors.add(block)
        return this
    }
    
    /**
     * DSL for routing operations
     */
    suspend fun route(key: Any, data: Any = Unit): Any {
        return routeWithContext(key, data, context)
    }
    
    /**
     * DSL for routing with custom context
     */
    suspend fun routeWithContext(key: Any, data: Any, ctx: CoroutineContext): Any {
        return withContext(ctx) {
            // Apply interceptors
            val interceptedData = interceptors.fold(data) { acc, interceptor ->
                interceptor(acc)
            }
            
            // Try local handler first, then bubble up
            handlers[key]?.invoke(interceptedData) 
                ?: parent?.routeWithContext(key, interceptedData, ctx)
                ?: throw NoHandlerException("No handler found for key: $key")
        }
    }
    
    /**
     * DSL for creating flows from routing operations
     */
    fun routeFlow(key: Any, data: Any = Unit): Flow<Any> = flow {
        emit(route(key, data))
    }
    
    /**
     * DSL for parallel routing
     */
    suspend fun routeParallel(operations: List<Pair<Any, Any>>): List<Any> {
        return coroutineScope {
            operations.map { (key, data) ->
                async { route(key, data) }
            }.awaitAll()
        }
    }
    
    /**
     * DSL for conditional routing
     */
    suspend fun routeIf(condition: Boolean, key: Any, data: Any = Unit): Any? {
        return if (condition) route(key, data) else null
    }
    
    /**
     * DSL for routing with fallback
     */
    suspend fun routeWithFallback(
        primaryKey: Any,
        fallbackKey: Any,
        data: Any = Unit
    ): Any {
        return try {
            route(primaryKey, data)
        } catch (e: NoHandlerException) {
            route(fallbackKey, data)
        }
    }
    
    /**
     * DSL for routing with timeout
     */
    suspend fun routeWithTimeout(
        key: Any,
        data: Any = Unit,
        timeoutMs: Long = 5000
    ): Any {
        return withTimeout(timeoutMs) {
            route(key, data)
        }
    }
    
    /**
     * DSL for routing with retry
     */
    suspend fun routeWithRetry(
        key: Any,
        data: Any = Unit,
        maxRetries: Int = 3,
        delayMs: Long = 100
    ): Any {
        repeat(maxRetries) { attempt ->
            try {
                return route(key, data)
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) throw e
                kotlinx.coroutines.delay(delayMs * (attempt + 1))
            }
        }
        throw Exception("Retry failed")
    }
    
    /**
     * DSL for routing with circuit breaker
     */
    suspend fun routeWithCircuitBreaker(
        key: Any,
        data: Any = Unit,
        failureThreshold: Int = 5
    ): Any {
        val circuitKey = "circuit_breaker_$key"
        val failureCount = handlers[circuitKey] as? Int ?: 0
        
        if (failureCount >= failureThreshold) {
            throw CircuitBreakerOpenException("Circuit breaker open for key: $key")
        }
        
        return try {
            val result = route(key, data)
            handlers[circuitKey] = { 0 }
            result
        } catch (e: Exception) {
            handlers[circuitKey] = { failureCount + 1 }
            throw e
        }
    }
    
    /**
     * DSL for routing with metrics
     */
    suspend fun routeWithMetrics(
        key: Any,
        data: Any = Unit,
        metricsHandler: suspend (String, Long) -> Unit
    ): Any = withTiming(metricsHandler) { route(key, data) }
    
    /**
     * DSL for routing with logging
     */
    suspend fun routeWithLogging(
        key: Any,
        data: Any = Unit,
        logger: suspend (String) -> Unit
    ): Any = withLogging(logger) { route(key, data) }
    
    /**
     * DSL for routing with observability
     */
    suspend fun routeWithObservability(
        key: Any,
        data: Any = Unit,
        observer: suspend (String, Any, Any?) -> Unit
    ): Any = withObservability(observer) { route(key, data) }
    
    /**
     * DSL for routing with caching
     */
    suspend fun routeWithCache(
        key: Any,
        data: Any = Unit,
        cache: MutableMap<Any, Any> = mutableMapOf(),
        ttlMs: Long = 60000
    ): Any {
        val cacheKey = "$key:$data"
        return cache[cacheKey] ?: route(key, data).also { result ->
            cache[cacheKey] = result
        }
    }
    
    /**
     * DSL for routing with validation
     */
    suspend fun routeWithValidation(
        key: Any,
        data: Any = Unit,
        validator: suspend (Any) -> Boolean
    ): Any {
        if (!validator(data)) {
            throw ValidationException("Data validation failed for key: $key")
        }
        return route(key, data)
    }
    
    /**
     * DSL for routing with transformation
     */
    suspend fun routeWithTransform(
        key: Any,
        data: Any = Unit,
        transform: suspend (Any) -> Any
    ): Any = route(key, transform(data))
    
    /**
     * DSL for routing with aggregation
     */
    suspend fun routeWithAggregation(
        keys: List<Any>,
        data: Any = Unit,
        aggregator: suspend (List<Any>) -> Any
    ): Any = aggregator(keys.map { route(it, data) })
    
    /**
     * DSL for routing with fan-out
     */
    suspend fun routeWithFanOut(
        key: Any,
        data: Any = Unit,
        fanOutKeys: List<Any>
    ): List<Any> {
        return coroutineScope {
            fanOutKeys.map { async { route(it, data) } }.awaitAll()
        }
    }
    
    /**
     * DSL for routing with fan-in
     */
    suspend fun routeWithFanIn(
        keys: List<Any>,
        data: Any = Unit,
        fanInKey: Any
    ): Any {
        return route(fanInKey, keys.map { route(it, data) })
    }
    
    /**
     * DSL for routing with batching
     */
    suspend fun routeWithBatching(
        operations: List<Pair<Any, Any>>,
        batchSize: Int = 10
    ): List<Any> {
        return operations.chunked(batchSize).flatMap { batch ->
            coroutineScope {
                batch.map { (key, data) -> async { route(key, data) } }.awaitAll()
            }
        }
    }
    
    /**
     * DSL for routing with priority
     */
    suspend fun routeWithPriority(
        operations: List<Triple<Any, Any, Int>> // key, data, priority
    ): List<Any> {
        return operations.sortedByDescending { it.third }.map { (key, data, _) -> route(key, data) }
    }
    
    /**
     * DSL for routing with rate limiting
     */
    suspend fun routeWithRateLimit(
        key: Any,
        data: Any = Unit,
        rateLimiter: RateLimiter
    ): Any {
        rateLimiter.acquire()
        return route(key, data)
    }
    
    /**
     * DSL for routing with load balancing
     */
    suspend fun routeWithLoadBalancing(
        keys: List<Any>,
        data: Any = Unit,
        loadBalancer: LoadBalancer
    ): Any {
        return route(loadBalancer.select(keys), data)
    }
    
    /**
     * DSL for routing with health checking
     */
    suspend fun routeWithHealthCheck(
        key: Any,
        data: Any = Unit,
        healthChecker: suspend (Any) -> Boolean
    ): Any {
        if (!healthChecker(key)) {
            throw HealthCheckException("Health check failed for key: $key")
        }
        return route(key, data)
    }
    
    /**
     * Helper functions to reduce code duplication
     */
    private suspend fun <T> withTiming(
        metricsHandler: suspend (String, Long) -> Unit,
        block: suspend () -> T
    ): T {
        val startTime = Clock.System.now().toEpochMilliseconds()
        return try {
            block().also {
                metricsHandler("success", Clock.System.now().toEpochMilliseconds() - startTime)
            }
        } catch (e: Exception) {
            metricsHandler("failure", Clock.System.now().toEpochMilliseconds() - startTime)
            throw e
        }
    }
    
    private suspend fun <T> withLogging(
        logger: suspend (String) -> Unit,
        block: suspend () -> T
    ): T {
        logger("Starting operation")
        return try {
            block().also { logger("Operation successful") }
        } catch (e: Exception) {
            logger("Operation failed: ${e.message}")
            throw e
        }
    }
    
    private suspend fun <T> withObservability(
        observer: suspend (String, Any, Any?) -> Unit,
        block: suspend () -> T
    ): T {
        observer("start", "operation", null)
        return try {
            block().also { result -> observer("success", "operation", result) }
        } catch (e: Exception) {
            observer("error", "operation", e)
            throw e
        }
    }
}

/**
 * DSL extensions for easier router creation
 */
fun router(block: CoroutineRouter.RouterBuilder.() -> Unit): CoroutineRouter {
    return CoroutineRouter.router(block)
}

fun CoroutineRouter.child(block: CoroutineRouter.RouterBuilder.() -> Unit): CoroutineRouter {
    return this.child(block)
}

/**
 * DSL for creating specialized routers
 */
fun asyncRouter(block: CoroutineRouter.RouterBuilder.() -> Unit): CoroutineRouter {
    return router {
        context(Dispatchers.IO)
        block()
    }
}

fun mainRouter(block: CoroutineRouter.RouterBuilder.() -> Unit): CoroutineRouter {
    return router {
        context(Dispatchers.Main)
        block()
    }
}

fun defaultRouter(block: CoroutineRouter.RouterBuilder.() -> Unit): CoroutineRouter {
    return router {
        context(Dispatchers.Default)
        block()
    }
}

/**
 * Supporting classes for advanced routing features
 */
class RateLimiter(private val permitsPerSecond: Int) {
    private var lastCheck = Clock.System.now().toEpochMilliseconds()
    private var available = permitsPerSecond
    
    suspend fun acquire() {
        while (available <= 0) {
            val now = Clock.System.now().toEpochMilliseconds()
            val timePassed = now - lastCheck
            available = minOf(permitsPerSecond, available + (timePassed * permitsPerSecond / 1000).toInt())
            lastCheck = now
            
            if (available <= 0) {
                // Simple yield instead of blocking sleep
                yield()
            }
        }
        available--
    }
}

class LoadBalancer {
    private var currentIndex = 0
    
    fun <T> select(items: List<T>): T {
        if (items.isEmpty()) throw IllegalArgumentException("Cannot select from empty list")
        return items[currentIndex++ % items.size]
    }
}

/**
 * Exception classes
 */
class NoHandlerException(message: String) : Exception(message)
class CircuitBreakerOpenException(message: String) : Exception(message)
class ValidationException(message: String) : Exception(message)
class HealthCheckException(message: String) : Exception(message) 