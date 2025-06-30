package borg.trikeshed.wireproto

import borg.trikeshed.lib.*

/**
 * Wire protocol interface for encoding/decoding data
 */
interface WireProto {
    fun pack(data: ByteArray): ByteArray
    fun unpack(data: ByteArray): ByteArray
    
    fun encode(data: Any): Series<Byte>
    fun <T> decode(data: Series<Byte>): T
}

/**
 * Default wire protocol implementation
 */
class DefaultWireProto : WireProto {
    override fun pack(data: ByteArray): ByteArray {
        // Placeholder implementation
        return data
    }
    
    override fun unpack(data: ByteArray): ByteArray {
        // Placeholder implementation
        return data
    }
    
    override fun encode(data: Any): Series<Byte> {
        // Placeholder implementation
        return emptySeries()
    }
    
    override fun <T> decode(data: Series<Byte>): T {
        // Placeholder implementation
        throw NotImplementedError("Decoding not implemented")
    }
}