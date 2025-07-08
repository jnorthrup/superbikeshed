@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.async

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlin.coroutines.CoroutineContext

/**
 * Core async abstractions for the TrikeShed reactor foundation.
 * This module provides the fundamental building blocks for all async operations.
 */

// ===== ASYNC CONTEXTS =====

/**
 * Async execution context for platform-specific optimizations.
 */
expect class AsyncContext {
    companion object {
        fun default(): AsyncContext
        fun io(): AsyncContext
        fun compute(): AsyncContext
    }
    
    fun toCoroutineContext(): CoroutineContext
}

/**
 * Async scheduler for managing execution timing.
 */
interface AsyncScheduler {
    suspend fun schedule(delay: Long, block: suspend () -> Unit): AsyncJob
    suspend fun scheduleAtFixedRate(period: Long, block: suspend () -> Unit): AsyncJob
    suspend fun scheduleWithCron(cronExpression: String, block: suspend () -> Unit): AsyncJob
}

/**
 * Async job handle for scheduled operations.
 */
interface AsyncJob {
    val isActive: Boolean
    val isCompleted: Boolean
    val isCancelled: Boolean
    
    suspend fun join()
    fun cancel(cause: CancellationException? = null)
    suspend fun result(): Any?
}

// ===== ASYNC OPERATIONS =====

/**
 * Async operation result with success/failure tracking.
 */
@Serializable
sealed class AsyncResult<out T> {
    @Serializable
    data class Success<T>(val value: T) : AsyncResult<T>()
    
    @Serializable
    data class Failure(val error: String, val cause: String? = null) : AsyncResult<Nothing>()
    
    @Serializable
    data object Pending : AsyncResult<Nothing>()
    
    @Serializable
    data object Cancelled : AsyncResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure
    fun isPending(): Boolean = this is Pending
    fun isCancelled(): Boolean = this is Cancelled
    
    fun getOrNull(): T? = when (this) {
        is Success -> value
        else -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw Exception("$error: $cause")
        is Pending -> throw IllegalStateException("Operation is still pending")
        is Cancelled -> throw CancellationException("Operation was cancelled")
    }
}

/**
 * Async operation with lifecycle tracking.
 */
class AsyncOperation<T>(
    internal val operation: suspend () -> T,
    internal val context: CoroutineContext = Dispatchers.Default
) {
    internal var _result: AsyncResult<T> = AsyncResult.Pending
    internal var job: Job? = null
    
    val result: AsyncResult<T> get() = _result
    
    suspend fun execute(): AsyncResult<T> {
        if (_result !is AsyncResult.Pending) {
            return _result
        }
        
        job = CoroutineScope(context).launch {
            try {
                val value = operation()
                _result = AsyncResult.Success(value)
            } catch (e: CancellationException) {
                _result = AsyncResult.Cancelled
                throw e
            } catch (e: Exception) {
                _result = AsyncResult.Failure(e.message ?: "Unknown error", e.cause?.message)
            }
        }
        
        job?.join()
        return _result
    }
    
    fun cancel() {
        job?.cancel()
        if (_result is AsyncResult.Pending) {
            _result = AsyncResult.Cancelled
        }
    }
    
    suspend fun await(): T = execute().getOrThrow()
}

// ===== ASYNC PRIMITIVES =====

/**
 * Async mutex for resource synchronization.
 */
class AsyncMutex {
    internal val mutex = kotlinx.coroutines.sync.Mutex()
    
    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock(action)
    
    fun tryLock(): Boolean = mutex.tryLock()
    fun unlock() = mutex.unlock()
}

/**
 * Async semaphore for resource pooling.
 */
class AsyncSemaphore(permits: Int) {
    internal val semaphore = kotlinx.coroutines.sync.Semaphore(permits)
    
    suspend fun <T> withPermit(action: suspend () -> T): T = semaphore.withPermit(action)
    
    suspend fun acquire() = semaphore.acquire()
    fun release() = semaphore.release()
    
    val availablePermits: Int get() = semaphore.availablePermits
}

/**
 * Async condition for event coordination.
 */
class AsyncCondition {
    internal val channel = Channel<Unit>(Channel.UNLIMITED)
    
    suspend fun await() {
        channel.receive()
    }
    
    fun signal() {
        channel.trySend(Unit)
    }
    
    fun signalAll() {
        repeat(Int.MAX_VALUE) {
            if (!channel.trySend(Unit).isSuccess) break
        }
    }
}

// ===== ASYNC FLOWS =====

/**
 * Async flow operators for stream processing.
 */
object AsyncFlows {
    
    fun <T> Flow<T>.withTimeout(timeoutMs: Long): Flow<T> = flow {
        withTimeout(timeoutMs) {
            collect { emit(it) }
        }
    }
    
    fun <T> Flow<T>.retryWithBackoff(
        maxAttempts: Int = 3,
        baseDelayMs: Long = 1000
    ): Flow<T> = flow {
        var attempt = 0
        while (attempt < maxAttempts) {
            try {
                collect { emit(it) }
                break
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(baseDelayMs * (1L shl (attempt - 1)))
            }
        }
    }
    
    fun <T> Flow<T>.rateLimited(
        permits: Int,
        windowMs: Long
    ): Flow<T> = flow {
        val semaphore = AsyncSemaphore(permits)
        val window = windowMs
        var lastReset = System.currentTimeMillis()
        
        collect { value ->
            val now = System.currentTimeMillis()
            if (now - lastReset >= window) {
                // Reset the window
                repeat(permits - semaphore.availablePermits) {
                    semaphore.release()
                }
                lastReset = now
            }
            
            semaphore.acquire()
            emit(value)
        }
    }
    
    fun <T> Flow<T>.buffer(size: Int): Flow<T> = 
        this.buffer(capacity = size)
    
    fun <T> Flow<T>.conflate(): Flow<T> = 
        this.conflate()
}

// ===== ASYNC CHANNELS =====

/**
 * Async channel for communication between coroutines.
 */
class AsyncChannel<T>(capacity: Int = Channel.UNLIMITED) {
    internal val channel = Channel<T>(capacity)
    
    suspend fun send(element: T) = channel.send(element)
    suspend fun receive(): T = channel.receive()
    
    fun trySend(element: T): Boolean = channel.trySend(element).isSuccess
    fun tryReceive(): T? = channel.tryReceive().getOrNull()
    
    fun close(cause: Throwable? = null) = channel.close(cause)
    
    fun asFlow(): Flow<T> = channel.receiveAsFlow()
}

/**
 * Async broadcast channel for one-to-many communication.
 */
class AsyncBroadcastChannel<T> {
    internal val subscribers = mutableListOf<AsyncChannel<T>>()
    internal val mutex = AsyncMutex()
    
    suspend fun subscribe(): AsyncChannel<T> = mutex.withLock {
        val channel = AsyncChannel<T>()
        subscribers.add(channel)
        channel
    }
    
    suspend fun unsubscribe(channel: AsyncChannel<T>) = mutex.withLock {
        subscribers.remove(channel)
        channel.close()
    }
    
    suspend fun broadcast(element: T) = mutex.withLock {
        subscribers.forEach { channel ->
            channel.trySend(element)
        }
    }
    
    suspend fun close() = mutex.withLock {
        subscribers.forEach { it.close() }
        subscribers.clear()
    }
}

// ===== ASYNC UTILITIES =====

/**
 * Async utilities for common patterns.
 */
object AsyncUtils {
    
    suspend fun <T> timeout(timeoutMs: Long, block: suspend () -> T): T =
        withTimeout(timeoutMs, block)
    
    suspend fun <T> retry(
        maxAttempts: Int = 3,
        delayMs: Long = 1000,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: Exception("Retry failed after $maxAttempts attempts")
    }
    
    suspend fun <A, B> parallel(
        blockA: suspend () -> A,
        blockB: suspend () -> B
    ): Pair<A, B> = coroutineScope {
        val deferredA = async { blockA() }
        val deferredB = async { blockB() }
        Pair(deferredA.await(), deferredB.await())
    }
    
    suspend fun <T> raceFirst(vararg blocks: suspend () -> T): T = coroutineScope {
        val deferreds = blocks.map { async { it() } }
        select<T> {
            deferreds.forEach { deferred ->
                deferred.onAwait { result ->
                    deferreds.forEach { it.cancel() }
                    result
                }
            }
        }
    }
    
    fun <T> asyncLazy(block: suspend () -> T): suspend () -> T {
        var result: T? = null
        var computed = false
        val mutex = kotlinx.coroutines.sync.Mutex()
        
        return {
            mutex.withLock {
                if (!computed) {
                    result = block()
                    computed = true
                }
                result!!
            }
        }
    }
}

// ===== ASYNC METRICS =====

/**
 * Async operation metrics for monitoring.
 */
@Serializable
data class AsyncMetrics(
    val totalOperations: Long = 0,
    val successfulOperations: Long = 0,
    val failedOperations: Long = 0,
    val cancelledOperations: Long = 0,
    val averageExecutionTimeMs: Double = 0.0,
    val maxExecutionTimeMs: Long = 0,
    val minExecutionTimeMs: Long = Long.MAX_VALUE
) {
    val successRate: Double get() = if (totalOperations > 0) successfulOperations.toDouble() / totalOperations else 0.0
    val failureRate: Double get() = if (totalOperations > 0) failedOperations.toDouble() / totalOperations else 0.0
}

/**
 * Async metrics collector.
 */
class AsyncMetricsCollector {
    internal var metrics = AsyncMetrics()
    internal val mutex = AsyncMutex()
    
    suspend fun recordOperation(result: AsyncResult<*>, executionTimeMs: Long) = mutex.withLock {
        metrics = metrics.copy(
            totalOperations = metrics.totalOperations + 1,
            successfulOperations = if (result.isSuccess()) metrics.successfulOperations + 1 else metrics.successfulOperations,
            failedOperations = if (result.isFailure()) metrics.failedOperations + 1 else metrics.failedOperations,
            cancelledOperations = if (result.isCancelled()) metrics.cancelledOperations + 1 else metrics.cancelledOperations,
            averageExecutionTimeMs = (metrics.averageExecutionTimeMs * (metrics.totalOperations - 1) + executionTimeMs) / metrics.totalOperations,
            maxExecutionTimeMs = maxOf(metrics.maxExecutionTimeMs, executionTimeMs),
            minExecutionTimeMs = if (metrics.minExecutionTimeMs == Long.MAX_VALUE) executionTimeMs else minOf(metrics.minExecutionTimeMs, executionTimeMs)
        )
    }
    
    suspend fun getMetrics(): AsyncMetrics = mutex.withLock { metrics }
    
    suspend fun reset() = mutex.withLock {
        metrics = AsyncMetrics()
    }
}