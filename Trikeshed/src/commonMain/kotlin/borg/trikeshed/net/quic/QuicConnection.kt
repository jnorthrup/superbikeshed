package borg.trikeshed.net.quic

import borg.trikeshed.lib.*

/**
 * Minimal placeholder QUIC connection for compilation
 */
class QuicConnection {
    suspend fun send(data: Indexed<Byte>): Boolean {
        // Placeholder implementation
        return true
    }
    
    suspend fun receive(): Indexed<Byte>? {
        // Placeholder implementation
        return null
    }
    
    fun close() {
        // Placeholder implementation
    }
} 