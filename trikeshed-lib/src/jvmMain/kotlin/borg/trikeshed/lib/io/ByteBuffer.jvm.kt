package borg.trikeshed.lib.io

import java.nio.ByteBuffer as JvmByteBuffer

actual class ByteBuffer internal constructor(internal val buffer: JvmByteBuffer) {
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer = ByteBuffer(JvmByteBuffer.allocate(capacity))
        actual fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(JvmByteBuffer.wrap(array))
    }

    actual fun put(byte: Byte): ByteBuffer {
        buffer.put(byte)
        return this
    }
    actual fun get(): Byte = buffer.get()
    actual fun put(src: ByteArray): ByteBuffer {
        buffer.put(src)
        return this
    }
    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        buffer.put(src, offset, length)
        return this
    }
    actual fun get(dst: ByteArray): ByteBuffer {
        buffer.get(dst)
        return this
    }
    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        buffer.get(dst, offset, length)
        return this
    }
    actual fun flip(): ByteBuffer {
        buffer.flip()
        return this
    }
    actual fun clear(): ByteBuffer {
        buffer.clear()
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
    actual fun position(): Int = buffer.position()
    actual fun position(newPosition: Int): ByteBuffer {
        buffer.position(newPosition)
        return this
    }
    actual fun limit(): Int = buffer.limit()
    actual fun limit(newLimit: Int): ByteBuffer {
        buffer.limit(newLimit)
        return this
    }
    actual fun capacity(): Int = buffer.capacity()
    actual fun hasRemaining(): Boolean = buffer.hasRemaining()
    actual fun remaining(): Int = buffer.remaining()
    actual fun array(): ByteArray = buffer.array()
} 