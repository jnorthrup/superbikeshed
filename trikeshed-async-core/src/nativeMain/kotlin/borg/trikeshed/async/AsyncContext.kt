@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.async

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Native implementation of AsyncContext using coroutine dispatchers.
 */
actual class AsyncContext internal constructor(
    internal val dispatcher: CoroutineDispatcher
) {
    
    actual companion object {
        actual fun default(): AsyncContext = 
            AsyncContext(Dispatchers.Default)
            
        actual fun io(): AsyncContext = 
            AsyncContext(Dispatchers.Default) // Native doesn't have separate IO dispatcher
            
        actual fun compute(): AsyncContext = 
            AsyncContext(Dispatchers.Default)
    }
    
    actual fun toCoroutineContext(): CoroutineContext = dispatcher
}