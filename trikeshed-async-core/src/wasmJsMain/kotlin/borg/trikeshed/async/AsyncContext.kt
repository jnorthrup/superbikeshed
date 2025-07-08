@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.async

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * WASM/JS implementation of AsyncContext using single-threaded dispatchers.
 */
actual class AsyncContext internal constructor(
    internal val dispatcher: CoroutineDispatcher
) {
    
    actual companion object {
        actual fun default(): AsyncContext = 
            AsyncContext(Dispatchers.Default)
            
        actual fun io(): AsyncContext = 
            AsyncContext(Dispatchers.Default) // WASM/JS is single-threaded
            
        actual fun compute(): AsyncContext = 
            AsyncContext(Dispatchers.Default)
    }
    
    actual fun toCoroutineContext(): CoroutineContext = dispatcher
}