package borg.trikeshed.nio

import java.nio.ByteBuffer as JvmByteBuffer

actual interface ByteBuffer {
    actual fun clear()
    actual fun flip()
    actual fun hasRemaining(): Boolean
    actual fun remaining(): Int
    actual fun position(): Int
    actual fun position(newPosition: Int)
    actual fun put(byte: Byte)
    actual fun put(bytes: ByteArray)
    actual fun putInt(value: Int)
    actual fun putLong(value: Long)
    actual fun get(): Byte
    actual fun get(bytes: ByteArray)
    actual fun getInt(): Int
    actual fun getLong(): Long
    actual fun limit(): Int
    actual fun limit(newLimit: Int)
    actual fun capacity(): Int
}