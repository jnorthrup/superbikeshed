package borg.trikeshed.net.quic

import borg.trikeshed.lib.*

/**
 * Minimal placeholder QUIC stream for compilation
 */
class QuicStream {
    suspend fun write(data: Indexed<Byte>): Boolean {
        // Placeholder implementation
        return true
    }
    
    suspend fun read(): Indexed<Byte>? {
        // Placeholder implementation
        return null
    }
    
    fun close() {
        // Placeholder implementation
    }
} 