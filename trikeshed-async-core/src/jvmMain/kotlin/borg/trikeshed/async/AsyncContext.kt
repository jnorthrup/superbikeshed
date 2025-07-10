@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.async

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.CoroutineContext

/**
 * JVM implementation of AsyncContext using optimized thread pools.
 */
actual class AsyncContext internal constructor(
    internal val dispatcher: CoroutineDispatcher
) {
    
    actual companion object {
        internal val defaultExecutor = ForkJoinPool.commonPool()
        internal val ioExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "TrikeShed-IO").apply {
                isDaemon = true
            }
        }
        internal val computeExecutor = ForkJoinPool(
            Runtime.getRuntime().availableProcessors(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
        )
        
        actual fun default(): AsyncContext = 
            AsyncContext(defaultExecutor.asCoroutineDispatcher())
            
        actual fun io(): AsyncContext = 
            AsyncContext(ioExecutor.asCoroutineDispatcher())
            
        actual fun compute(): AsyncContext = 
            AsyncContext(computeExecutor.asCoroutineDispatcher())
    }
    
    actual fun toCoroutineContext(): CoroutineContext = dispatcher
    
    /**
     * JVM-specific: Access to underlying executor for interop.
     */
    fun asExecutor(): Executor = when (dispatcher) {
        is ExecutorCoroutineDispatcher -> dispatcher.executor
        else -> defaultExecutor
    }
}