package borg.trikeshed.nio

import java.nio.ByteBuffer as JByteBuffer

actual class ByteBuffer private constructor(private val buffer: JByteBuffer) {
    actual val capacity: Int = buffer.capacity()
    actual var position: Int
        get() = buffer.position()
        set(value) { buffer.position(value) }
    actual var limit: Int
        get() = buffer.limit()
        set(value) { buffer.limit(value) }

    actual fun clear(): ByteBuffer {
        buffer.clear()
        return this
    }

    actual fun flip(): ByteBuffer {
        buffer.flip()
        return this
    }

    actual fun rewind(): ByteBuffer {
        buffer.rewind()
        return this
    }

    actual fun mark(): ByteBuffer {
        buffer.mark()
        return this
    }

    actual fun reset(): ByteBuffer {
        buffer.reset()
        return this
    }

    actual fun slice(): ByteBuffer {
        return ByteBuffer(buffer.slice())
    }

    actual fun duplicate(): ByteBuffer {
        return ByteBuffer(buffer.duplicate())
    }

    actual fun get(): Byte = buffer.get()
    actual fun get(index: Int): Byte = buffer.get(index)
    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        buffer.get(dst, offset, length)
        return this
    }

    actual fun put(byte: Byte): ByteBuffer {
        buffer.put(byte)
        return this
    }

    actual fun put(index: Int, byte: Byte): ByteBuffer {
        buffer.put(index, byte)
        return this
    }

    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        buffer.put(src, offset, length)
        return this
    }

    actual fun array(): ByteArray = buffer.array()
    actual fun arrayOffset(): Int = buffer.arrayOffset()
    actual val hasArray: Boolean = buffer.hasArray()

    companion object {
        actual fun allocate(capacity: Int): ByteBuffer {
            return ByteBuffer(JByteBuffer.allocate(capacity))
        }

        actual fun wrap(array: ByteArray, offset: Int, length: Int): ByteBuffer {
            return ByteBuffer(JByteBuffer.wrap(array, offset, length))
        }

        actual fun wrap(array: ByteArray): ByteBuffer {
            return ByteBuffer(JByteBuffer.wrap(array))
        }
    }
}