@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.reactor

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * Reactor pattern implementation for TrikeShed
 * Real implementation for async event processing
 */
class Reactor<T> {
    internal val events = Channel<T>(Channel.UNLIMITED)
    internal val handlers = mutableListOf<suspend (T) -> Unit>()
    internal var job: Job? = null
    
    fun on(handler: suspend (T) -> Unit): Reactor<T> {
        handlers.add(handler)
        return this
    }
    
    suspend fun emit(event: T) {
        events.send(event)
    }
    
    fun start(scope: CoroutineScope): Reactor<T> {
        job = scope.launch {
            for (event in events) {
                handlers.forEach { handler ->
                    launch { handler(event) }
                }
            }
        }
        return this
    }
    
    fun stop() {
        job?.cancel()
        events.close()
    }
    
    companion object {
        fun <T> create(): Reactor<T> = Reactor()
        
        fun <A, B> spawn(a: A, block: suspend (A) -> B): Deferred<B> {
            return GlobalScope.async {
                block(a)
            }
        }
    }
}

/**
 * Flow-based reactor for streaming operations
 */
class FlowReactor<T> {
    internal val _events = MutableSharedFlow<T>()
    val events: SharedFlow<T> = _events.asSharedFlow()
    
    suspend fun emit(event: T) {
        _events.emit(event)
    }
    
    fun subscribe(scope: CoroutineScope, handler: suspend (T) -> Unit): Job {
        return events.onEach { event ->
            handler(event)
        }.launchIn(scope)
    }
}