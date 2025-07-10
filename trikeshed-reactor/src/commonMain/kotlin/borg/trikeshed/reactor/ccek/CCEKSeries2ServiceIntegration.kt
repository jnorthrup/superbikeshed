@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor.ccek

import kotlinx.coroutines.*
import kotlinx.datetime.*
import borg.trikeshed.reactor.*
import borg.trikeshed.lib.*

/**
 * CCEK Series 2: Service Integration
 * 
 * Service layers add cross-cutting concerns like load balancing,
 * circuit breaking, caching, and authentication to protocol stacks.
 */

// Service layer implementations
class LoadBalancingService(
    internal val backends: List<ProtocolStack>,
    internal val strategy: LoadBalancingStrategy = RoundRobinStrategy()
) : ServiceLayer {
    
    internal val healthChecker = HealthChecker()
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        val healthyBackends = backends.filter { healthChecker.isHealthy(it) }
        
        if (healthyBackends.isEmpty()) {
            throw NoHealthyBackendsException("All backends are unhealthy")
        }
        
        val selectedBackend = strategy.selectBackend(healthyBackends, operation)
        
        return try {
            val startTime = Clock.System.now()
            val result = selectedBackend.dispatch(operation)
            
            // Record success metrics
            strategy.recordSuccess(selectedBackend, Clock.System.now() - startTime)
            
            result
        } catch (e: Exception) {
            // Record failure and potentially mark backend unhealthy
            strategy.recordFailure(selectedBackend, e)
            healthChecker.recordFailure(selectedBackend)
            throw e
        }
    }
    
    companion object : CoroutineContext.Key<LoadBalancingService>
}

class CircuitBreakerService(
    internal val underlying: CCEKChannelization,
    internal val config: CircuitBreakerConfig = CircuitBreakerConfig()
) : ServiceLayer {
    
    internal var state: CircuitState = CircuitState.Closed
    internal var failures = 0
    internal var lastFailureTime: Instant? = null
    internal var successCount = 0
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (state) {
            CircuitState.Open -> {
                val timeSinceLastFailure = lastFailureTime?.let { 
                    Clock.System.now() - it 
                } ?: Duration.ZERO
                
                if (timeSinceLastFailure >= config.resetTimeout) {
                    state = CircuitState.HalfOpen
                    tryOperation(operation)
                } else {
                    throw CircuitOpenException("Circuit breaker is open")
                }
            }
            
            CircuitState.HalfOpen -> {
                tryOperation(operation).also {
                    successCount++
                    if (successCount >= config.successThreshold) {
                        reset()
                    }
                }
            }
            
            CircuitState.Closed -> tryOperation(operation)
        }
    }
    
    internal suspend fun <T> tryOperation(operation: ChannelOperation<T>): T {
        return try {
            underlying.dispatch(operation)
        } catch (e: Exception) {
            recordFailure()
            throw e
        }
    }
    
    internal fun recordFailure() {
        failures++
        lastFailureTime = Clock.System.now()
        
        when (state) {
            CircuitState.Closed -> {
                if (failures >= config.failureThreshold) {
                    state = CircuitState.Open
                }
            }
            CircuitState.HalfOpen -> {
                state = CircuitState.Open
                successCount = 0
            }
            CircuitState.Open -> { /* Already open */ }
        }
    }
    
    internal fun reset() {
        state = CircuitState.Closed
        failures = 0
        successCount = 0
        lastFailureTime = null
    }
    
    companion object : CoroutineContext.Key<CircuitBreakerService>
}

class CachingService(
    internal val underlying: CCEKChannelization,
    internal val cache: ChannelCache = InMemoryChannelCache(),
    internal val cacheable: (ChannelOperation<*>) -> Boolean = { it is CacheableOperation }
) : ServiceLayer {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        if (!cacheable(operation)) {
            return underlying.dispatch(operation)
        }
        
        val cacheKey = operation.cacheKey()
        
        // Check cache
        cache.get(cacheKey)?.let { cached ->
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }
        
        // Execute and cache
        val result = underlying.dispatch(operation)
        cache.put(cacheKey, result, operation.cacheTtl())
        
        return result
    }
    
    companion object : CoroutineContext.Key<CachingService>
}

class AuthenticationService(
    internal val underlying: CCEKChannelization,
    internal val authenticator: Authenticator
) : ServiceLayer {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        val context = currentCoroutineContext()
        val credentials = context[AuthCredentials] 
            ?: throw UnauthenticatedException("No credentials provided")
        
        // Authenticate
        val authToken = authenticator.authenticate(credentials)
        
        // Add auth token to operation context
        return withContext(AuthToken(authToken)) {
            underlying.dispatch(operation)
        }
    }
    
    companion object : CoroutineContext.Key<AuthenticationService>
}

// Supporting types for services
interface LoadBalancingStrategy {
    fun selectBackend(backends: List<ProtocolStack>, operation: ChannelOperation<*>): ProtocolStack
    fun recordSuccess(backend: ProtocolStack, duration: Duration)
    fun recordFailure(backend: ProtocolStack, error: Exception)
}

class RoundRobinStrategy : LoadBalancingStrategy {
    internal var currentIndex = 0
    
    override fun selectBackend(backends: List<ProtocolStack>, operation: ChannelOperation<*>): ProtocolStack {
        val selected = backends[currentIndex % backends.size]
        currentIndex++
        return selected
    }
    
    override fun recordSuccess(backend: ProtocolStack, duration: Duration) {
        // Could track latency metrics
    }
    
    override fun recordFailure(backend: ProtocolStack, error: Exception) {
        // Could track error rates
    }
}

class HealthChecker {
    internal val unhealthyBackends = mutableSetOf<ProtocolStack>()
    internal val failureCounts = mutableMapOf<ProtocolStack, Int>()
    
    fun isHealthy(backend: ProtocolStack): Boolean = backend !in unhealthyBackends
    
    fun recordFailure(backend: ProtocolStack) {
        val count = failureCounts.getOrDefault(backend, 0) + 1
        failureCounts[backend] = count
        
        if (count >= 3) {
            unhealthyBackends.add(backend)
            // Schedule health check to re-enable
            GlobalScope.launch {
                delay(30_000) // 30 seconds
                if (checkHealth(backend)) {
                    unhealthyBackends.remove(backend)
                    failureCounts.remove(backend)
                }
            }
        }
    }
    
    internal suspend fun checkHealth(backend: ProtocolStack): Boolean {
        return try {
            backend.dispatch(HealthCheck)
            true
        } catch (e: Exception) {
            false
        }
    }
}

// Circuit breaker types
enum class CircuitState {
    Open, HalfOpen, Closed
}

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val successThreshold: Int = 3,
    val resetTimeout: Duration = 60.seconds
)

// Cache abstraction
interface ChannelCache {
    suspend fun get(key: String): Any?
    suspend fun put(key: String, value: Any?, ttl: Duration)
}

class InMemoryChannelCache : ChannelCache {
    internal data class CacheEntry(
        val value: Any?,
        val expiresAt: Instant
    )
    
    internal val cache = mutableMapOf<String, CacheEntry>()
    
    override suspend fun get(key: String): Any? {
        val entry = cache[key] ?: return null
        
        return if (Clock.System.now() < entry.expiresAt) {
            entry.value
        } else {
            cache.remove(key)
            null
        }
    }
    
    override suspend fun put(key: String, value: Any?, ttl: Duration) {
        cache[key] = CacheEntry(
            value = value,
            expiresAt = Clock.System.now() + ttl
        )
    }
}

// Authentication types
data class AuthCredentials(
    val username: String,
    val password: String
) : CoroutineContext.Element {
    override val key = Companion
    companion object : CoroutineContext.Key<AuthCredentials>
}

data class AuthToken(
    val token: String
) : CoroutineContext.Element {
    override val key = Companion
    companion object : CoroutineContext.Key<AuthToken>
}

interface Authenticator {
    suspend fun authenticate(credentials: AuthCredentials): String
}

// Operations
interface CacheableOperation {
    fun cacheKey(): String
    fun cacheTtl(): Duration
}

object HealthCheck : ChannelOperation<Unit>

// Exceptions
class NoHealthyBackendsException(message: String) : Exception(message)
class CircuitOpenException(message: String) : Exception(message)
class UnauthenticatedException(message: String) : Exception(message)

// Extension functions for operation caching
fun ChannelOperation<*>.cacheKey(): String = this::class.simpleName + "_" + hashCode()
fun ChannelOperation<*>.cacheTtl(): Duration = 5.minutes