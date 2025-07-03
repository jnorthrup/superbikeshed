package borg.trikeshed.nio

/**
 * A platform-agnostic, NIO-like byte buffer.
 * This is the single, unified buffer abstraction for TrikeShed.
 */
expect class ByteBuffer {
    companion object {
        fun allocate(capacity: Int): ByteBuffer
        fun wrap(array: ByteArray): ByteBuffer
    }

    val capacity: Int
    var position: Int
    var limit: Int

    fun remaining(): Int
    fun hasRemaining(): Boolean

    fun get(): Byte
    fun get(dst: ByteArray, offset: Int = 0, length: Int = dst.size): ByteBuffer
    
    fun put(byte: Byte): ByteBuffer
    fun put(src: ByteArray, offset: Int = 0, length: Int = src.size): ByteBuffer

    fun getInt(): Int
    fun putInt(value: Int): ByteBuffer
    fun getLong(): Long
    fun putLong(value: Long): ByteBuffer

    fun flip(): ByteBuffer
    fun rewind(): ByteBuffer
    fun clear(): ByteBuffer
    
    fun array(): ByteArray
} 