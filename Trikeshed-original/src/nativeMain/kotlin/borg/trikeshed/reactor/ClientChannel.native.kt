@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.reactor

/**
 * Native implementation of ClientChannel
 */
actual class ClientChannel {
    actual fun isConnected(): Boolean = false
    
    actual fun close() {
        // Simplified implementation
    }
}