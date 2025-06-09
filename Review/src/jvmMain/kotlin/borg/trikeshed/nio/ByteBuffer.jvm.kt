package borg.trikeshed.nio

import java.nio.ByteBuffer as JavaByteBuffer

// This class is the actual JVM implementation of the common expect interface ByteBuffer
// from src/commonMain/kotlin/borg/trikeshed/nio/ByteBuffer.kt
actual class JvmByteBuffer(internal val delegate: JavaByteBuffer) : ByteBuffer {

    actual override fun clear() { delegate.clear() }
    actual override fun flip() { delegate.flip() }
    actual override fun hasRemaining(): Boolean = delegate.hasRemaining()
    actual override fun remaining(): Int = delegate.remaining()
    actual override fun position(): Int = delegate.position()
    actual override fun position(newPosition: Int) { delegate.position(newPosition) }

    actual override fun put(byte: Byte) { delegate.put(byte) }
    actual override fun put(bytes: ByteArray) { delegate.put(bytes) }
    actual override fun put(bytes: ByteArray, offset: Int, length: Int) { delegate.put(bytes, offset, length) }

    actual override fun put(source: ByteBuffer) {
        if (source is JvmByteBuffer) {
            // Efficiently put data if the source is also a JvmByteBuffer
            delegate.put(source.delegate)
        } else {
            // Standard way to put data from a generic ByteBuffer
            if (source.hasRemaining()) { // Check if there's anything to read
                val tempArray = ByteArray(source.remaining())
                source.get(tempArray) // Read data from source into tempArray
                delegate.put(tempArray) // Put data from tempArray into this buffer
            }
        }
    }

    actual override fun putShort(value: Short) { delegate.putShort(value) }
    actual override fun putInt(value: Int) { delegate.putInt(value) }
    actual override fun putLong(value: Long) { delegate.putLong(value) }
    actual override fun putFloat(value: Float) { delegate.putFloat(value) }
    actual override fun putDouble(value: Double) { delegate.putDouble(value) }

    actual override fun get(): Byte = delegate.get()
    actual override fun get(bytes: ByteArray) { delegate.get(bytes) }
    // Note: The expect interface does not currently have get(bytes, offset, length)

    actual override fun getShort(): Short = delegate.getShort()
    actual override fun getInt(): Int = delegate.getInt()
    actual override fun getLong(): Long = delegate.getLong()
    actual override fun getFloat(): Float = delegate.getFloat()
    actual override fun getDouble(): Double = delegate.getDouble()

    actual override fun limit(): Int = delegate.limit()
    actual override fun limit(newLimit: Int) { delegate.limit(newLimit) }
    actual override fun capacity(): Int = delegate.capacity()

    actual override fun duplicate(): ByteBuffer = JvmByteBuffer(delegate.duplicate())
    actual override fun slice(): ByteBuffer = JvmByteBuffer(delegate.slice())
    actual override fun rewind() { delegate.rewind() }
}
