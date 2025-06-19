package borg.trikeshed.reactor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val IO: CoroutineDispatcher = Dispatchers.Default

actual object System {
    actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()
    actual fun nanoTime(): Long = kotlin.js.Date.now().toLong() * 1_000_000 // Approximate
} 