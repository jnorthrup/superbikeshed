package nexus.pure

import borg.trikeshed.lib.*

/**
 * Comprehensive effect handlers for different execution contexts
 */

// Handler registry for composable effect handling
class HandlerRegistry {
    private val handlers = mutableMapOf<String, EffectHandler<Operation<*>>>()
    
    fun register(name: String, handler: EffectHandler<Operation<*>>) {
        handlers[name] = handler
    }
    
    fun get(name: String): EffectHandler<Operation<*>>? = handlers[name]
    
    fun getOrDefault(name: String): EffectHandler<Operation<*>> = 
        handlers[name] ?: IOHandler()
}

// Logging handler decorator
class LoggingHandler(
    private val delegate: EffectHandler<Operation<*>>,
    private val logger: (String) -> Unit = ::println
) : EffectHandler<Operation<*>> {
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        logger("Executing: $operation")
        val start = System.currentTimeMillis()
        try {
            val result = delegate.handle(operation)
            val duration = System.currentTimeMillis() - start
            logger("Completed: $operation (${duration}ms)")
            return result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            logger("Failed: $operation (${duration}ms) - ${e.message}")
            throw e
        }
    }
}

// Caching handler decorator
class CachingHandler(
    private val delegate: EffectHandler<Operation<*>>,
    private val ttlMs: Long = 30000L
) : EffectHandler<Operation<*>> {
    
    private data class CacheEntry<A>(val value: A, val timestamp: Long)
    private val cache = mutableMapOf<Operation<*>, CacheEntry<*>>()
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        val now = System.currentTimeMillis()
        val cached = cache[operation]
        
        return if (cached != null && (now - cached.timestamp) < ttlMs) {
            @Suppress("UNCHECKED_CAST")
            cached.value as A
        } else {
            val result = delegate.handle(operation)
            cache[operation] = CacheEntry(result, now)
            result
        }
    }
}

// Retry handler decorator
class RetryHandler(
    private val delegate: EffectHandler<Operation<*>>,
    private val maxRetries: Int = 3,
    private val backoffMs: Long = 1000L
) : EffectHandler<Operation<*>> {
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                return delegate.handle(operation)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(backoffMs * (attempt + 1))
                }
            }
        }
        
        throw lastException ?: RuntimeException("Retry failed for operation: $operation")
    }
}

// Circuit breaker handler
class CircuitBreakerHandler(
    private val delegate: EffectHandler<Operation<*>>,
    private val failureThreshold: Int = 5,
    private val recoveryTimeMs: Long = 60000L
) : EffectHandler<Operation<*>> {
    
    private enum class State { CLOSED, OPEN, HALF_OPEN }
    
    private var state = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > recoveryTimeMs) {
                    state = State.HALF_OPEN
                } else {
                    throw RuntimeException("Circuit breaker is OPEN for operation: $operation")
                }
            }
            State.HALF_OPEN, State.CLOSED -> {
                // Continue with execution
            }
        }
        
        return try {
            val result = delegate.handle(operation)
            if (state == State.HALF_OPEN) {
                state = State.CLOSED
                failureCount = 0
            }
            result
        } catch (e: Exception) {
            failureCount++
            lastFailureTime = System.currentTimeMillis()
            
            if (failureCount >= failureThreshold) {
                state = State.OPEN
            }
            
            throw e
        }
    }
}

// Rate limiting handler
class RateLimitingHandler(
    private val delegate: EffectHandler<Operation<*>>,
    private val maxRequestsPerSecond: Int = 10
) : EffectHandler<Operation<*>> {
    
    private val requestTimes = mutableListOf<Long>()
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        val now = System.currentTimeMillis()
        
        // Remove old entries
        requestTimes.removeAll { it < now - 1000L }
        
        if (requestTimes.size >= maxRequestsPerSecond) {
            val oldestRequest = requestTimes.minOrNull() ?: now
            val waitTime = 1000L - (now - oldestRequest)
            if (waitTime > 0) {
                kotlinx.coroutines.delay(waitTime)
            }
        }
        
        requestTimes.add(now)
        return delegate.handle(operation)
    }
}

// Metrics collection handler
class MetricsHandler(
    private val delegate: EffectHandler<Operation<*>>
) : EffectHandler<Operation<*>> {
    
    data class OperationMetrics(
        var count: Long = 0,
        var totalDuration: Long = 0,
        var failures: Long = 0,
        var lastExecuted: Long = 0
    )
    
    private val metrics = mutableMapOf<String, OperationMetrics>()
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        val operationType = operation::class.simpleName ?: "Unknown"
        val start = System.currentTimeMillis()
        
        return try {
            val result = delegate.handle(operation)
            val duration = System.currentTimeMillis() - start
            
            metrics.compute(operationType) { _, existing ->
                val m = existing ?: OperationMetrics()
                m.count++
                m.totalDuration += duration
                m.lastExecuted = System.currentTimeMillis()
                m
            }
            
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            
            metrics.compute(operationType) { _, existing ->
                val m = existing ?: OperationMetrics()
                m.count++
                m.totalDuration += duration
                m.failures++
                m.lastExecuted = System.currentTimeMillis()
                m
            }
            
            throw e
        }
    }
    
    fun getMetrics(): Map<String, OperationMetrics> = metrics.toMap()
    
    fun getAverageDuration(operationType: String): Double {
        val m = metrics[operationType] ?: return 0.0
        return if (m.count > 0) m.totalDuration.toDouble() / m.count else 0.0
    }
    
    fun getSuccessRate(operationType: String): Double {
        val m = metrics[operationType] ?: return 0.0
        return if (m.count > 0) (m.count - m.failures).toDouble() / m.count else 0.0
    }
}

// Simulation handler for testing
class SimulationHandler(
    private val responses: Map<String, Any?> = emptyMap(),
    private val delays: Map<String, Long> = emptyMap(),
    private val failures: Map<String, Exception> = emptyMap(),
    private val failureRate: Double = 0.0
) : EffectHandler<Operation<*>> {
    
    private val random = kotlin.random.Random.Default
    
    override suspend fun <A> handle(operation: Operation<A>): A {
        val operationType = operation::class.simpleName ?: "Unknown"
        
        // Simulate delay
        delays[operationType]?.let { delay ->
            kotlinx.coroutines.delay(delay)
        }
        
        // Simulate random failures
        if (random.nextDouble() < failureRate) {
            throw RuntimeException("Simulated failure for $operationType")
        }
        
        // Simulate specific failures
        failures[operationType]?.let { exception ->
            throw exception
        }
        
        // Return specific response or default
        return responses[operationType] as? A ?: when (operation) {
            is Operation.ReadFile -> "simulated file content" as A
            is Operation.WriteFile -> Unit as A
            is Operation.ListFiles -> arrayOf("file1.txt", "file2.txt").toSeries() as A
            is Operation.HttpGet -> """{"status": "simulated"}""" as A
            is Operation.HttpPost -> """{"result": "simulated"}""" as A
            is Operation.GetEnv -> null as A
            is Operation.SetEnv -> Unit as A
            is Operation.Print -> Unit as A
            is Operation.ReadLine -> "simulated input" as A
            is Operation.Now -> System.currentTimeMillis() as A
            is Operation.Sleep -> Unit as A
            is Operation.Random -> random.nextDouble() as A
            is Operation.RandomInt -> random.nextInt(operation.min, operation.max + 1) as A
            is Operation.GetState<*> -> null as A
            is Operation.SetState<*> -> Unit as A
            is Operation.Choose -> operation.options.firstOrNull() as A ?: throw RuntimeException("No options to choose from")
            is Operation.Fail<*> -> throw RuntimeException(operation.error)
        }
    }
}

// Handler composition utilities
infix fun EffectHandler<Operation<*>>.withLogging(logger: (String) -> Unit = ::println): EffectHandler<Operation<*>> =
    LoggingHandler(this, logger)

infix fun EffectHandler<Operation<*>>.withCaching(ttlMs: Long = 30000L): EffectHandler<Operation<*>> =
    CachingHandler(this, ttlMs)

infix fun EffectHandler<Operation<*>>.withRetry(maxRetries: Int = 3): EffectHandler<Operation<*>> =
    RetryHandler(this, maxRetries)

infix fun EffectHandler<Operation<*>>.withCircuitBreaker(failureThreshold: Int = 5): EffectHandler<Operation<*>> =
    CircuitBreakerHandler(this, failureThreshold)

infix fun EffectHandler<Operation<*>>.withRateLimit(maxRequestsPerSecond: Int = 10): EffectHandler<Operation<*>> =
    RateLimitingHandler(this, maxRequestsPerSecond)

infix fun EffectHandler<Operation<*>>.withMetrics(): Join<EffectHandler<Operation<*>>, MetricsHandler> {
    val metricsHandler = MetricsHandler(this)
    return metricsHandler j metricsHandler
}

// Builder for composing handlers
class HandlerBuilder {
    private var handler: EffectHandler<Operation<*>> = IOHandler()
    
    fun base(handler: EffectHandler<Operation<*>>): HandlerBuilder {
        this.handler = handler
        return this
    }
    
    fun withLogging(logger: (String) -> Unit = ::println): HandlerBuilder {
        handler = handler withLogging logger
        return this
    }
    
    fun withCaching(ttlMs: Long = 30000L): HandlerBuilder {
        handler = handler withCaching ttlMs
        return this
    }
    
    fun withRetry(maxRetries: Int = 3, backoffMs: Long = 1000L): HandlerBuilder {
        handler = RetryHandler(handler, maxRetries, backoffMs)
        return this
    }
    
    fun withCircuitBreaker(failureThreshold: Int = 5, recoveryTimeMs: Long = 60000L): HandlerBuilder {
        handler = CircuitBreakerHandler(handler, failureThreshold, recoveryTimeMs)
        return this
    }
    
    fun withRateLimit(maxRequestsPerSecond: Int = 10): HandlerBuilder {
        handler = RateLimitingHandler(handler, maxRequestsPerSecond)
        return this
    }
    
    fun withMetrics(): Join<HandlerBuilder, MetricsHandler> {
        val (newHandler, metricsHandler) = handler.withMetrics()
        handler = newHandler
        return this j metricsHandler
    }
    
    fun build(): EffectHandler<Operation<*>> = handler
}

// DSL for building handlers
fun handler(block: HandlerBuilder.() -> Unit): EffectHandler<Operation<*>> =
    HandlerBuilder().apply(block).build()

// Predefined handler configurations
object CommonHandlers {
    
    val production = handler {
        base(IOHandler())
        withLogging()
        withRetry(3)
        withCircuitBreaker(5)
        withRateLimit(100)
    }
    
    val development = handler {
        base(IOHandler())
        withLogging { message -> println("[DEV] $message") }
        withRetry(1)
    }
    
    val testing = SimulationHandler()
    
    fun withMetrics() = handler {
        base(IOHandler())
        withLogging()
        withRetry(3)
    }.withMetrics()
    
    fun simulation(
        responses: Map<String, Any?> = emptyMap(),
        delays: Map<String, Long> = emptyMap(),
        failures: Map<String, Exception> = emptyMap(),
        failureRate: Double = 0.0
    ) = SimulationHandler(responses, delays, failures, failureRate)
}

// Environment-specific handler selection
object HandlerEnvironment {
    
    private val environmentHandlers = mapOf(
        "production" to CommonHandlers.production,
        "development" to CommonHandlers.development,
        "test" to CommonHandlers.testing
    )
    
    fun get(environment: String = getEnvironment()): EffectHandler<Operation<*>> =
        environmentHandlers[environment] ?: CommonHandlers.development
    
    private fun getEnvironment(): String =
        System.getenv("NEXUS_ENV") ?: "development"
}