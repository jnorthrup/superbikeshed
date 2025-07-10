@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import kotlinx.coroutines.delay

actual class SimpleReactor actual constructor() {
    internal var running = false
    
    actual suspend fun select(): Int = 0
    
    actual suspend fun register(channel: Channel, callback: suspend () -> Unit) {
        // Placeholder
    }
    
    actual suspend fun run() {
        running = true
        while (running) {
            delay(10)
        }
    }
    
    actual suspend fun stop() {
        running = false
    }
}