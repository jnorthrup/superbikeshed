package borg.trikeshed.nio

import java.nio.ByteBuffer

actual class PlatformByteBuffer private constructor(private val buffer: ByteBuffer) {
    actual fun putLong(value: Long) { buffer.putLong(value) }
    actual fun put(src: ByteArray, offset: Int, length: Int) { buffer.put(src, offset, length) }
    actual fun flip() { buffer.flip() }
    actual fun array(): ByteArray = buffer.array()
    actual fun limit(): Int = buffer.limit()
    actual fun remaining(): Int = buffer.remaining()
    actual fun getLong(): Long = buffer.getLong()
    actual fun get(dst: ByteArray) { buffer.get(dst) }
    actual fun position(): Int = buffer.position()
    actual companion object {
        actual fun allocate(capacity: Int): PlatformByteBuffer = PlatformByteBuffer(ByteBuffer.allocate(capacity))
        actual fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer = PlatformByteBuffer(ByteBuffer.wrap(array, offset, length))
    }
} 