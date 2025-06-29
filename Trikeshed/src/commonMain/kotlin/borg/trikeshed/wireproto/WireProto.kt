package borg.trikeshed.wireproto


import borg.trikeshed.lib.*

/**
 * Minimal placeholder wire protocol for compilation
 */
class WireProto {
    fun encode(data: Any): Indexed<Byte> {
        // Placeholder implementation
        return 0 j { throw NoSuchElementException() }
    }
    
    fun <T> decode(data: Indexed<Byte>): T {
        // Placeholder implementation
        throw NotImplementedError("Decoding not implemented")
    }
} 