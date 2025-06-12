package borg.trikeshed.nio

/**
 * Represents a byte buffer that can be used for zero-allocation operations.
 * This is an `expect` class, with `actual` implementations for different platforms.
 *
 * Following RelaxFactory's design, this interface provides methods for:
 * - Slicing: Creating a new buffer that shares content with a portion of this buffer.
 * - Duplicating: Creating a new buffer that shares content but has independent position/limit.
 * - Marking/Resetting: Saving and restoring a position.
 * - Reading bytes without allocation.
 */
expect abstract class ByteBuffer {
    val capacity: Int
    var position: Int
    var limit: Int

    fun clear(): ByteBuffer
    fun flip(): ByteBuffer
    fun rewind(): ByteBuffer
    fun mark(): ByteBuffer
    fun reset(): ByteBuffer

    fun slice(): ByteBuffer
    fun duplicate(): ByteBuffer

    fun get(): Byte
    fun get(index: Int): Byte
    fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer

    fun put(byte: Byte): ByteBuffer
    fun put(index: Int, byte: Byte): ByteBuffer
    fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer

    fun array(): ByteArray
    fun arrayOffset(): Int
    val hasArray: Boolean
}

/**
 * Factory for creating ByteBuffer instances.
 * This is an `expect` object, with `actual` implementations for different platforms.
 */
expect object ByteBufferFactory {
    fun allocate(capacity: Int): ByteBuffer
    fun wrap(array: ByteArray, offset: Int, length: Int): ByteBuffer
    fun wrap(array: ByteArray): ByteBuffer
}