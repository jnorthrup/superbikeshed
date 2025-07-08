@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.Selector
import kotlinx.coroutines.*

actual class SimpleReactor actual constructor() {
    internal val selector = Selector.open()
    internal val callbacks = mutableMapOf<Channel, suspend () -> Unit>()
    internal var running = false
    
    actual suspend fun select(): Int {
        return selector.select()
    }
    
    actual suspend fun register(channel: Channel, callback: suspend () -> Unit) {
        callbacks[channel] = callback
    }
    
    actual suspend fun run() {
        running = true
        while (running) {
            select()
            // Process selected channels and run callbacks
            // This is a simplified implementation
            delay(10) // Prevent busy loop
        }
    }
    
    actual suspend fun stop() {
        running = false
        selector.wakeup()
    }
}