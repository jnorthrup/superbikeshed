package borg.trikeshed.wireproto

<<<<<<< HEAD

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
=======
interface WireProto {
    fun pack(data: ByteArray): ByteArray
    fun unpack(data: ByteArray): ByteArray
>>>>>>> origin/feat/core-serialization-impl
} 