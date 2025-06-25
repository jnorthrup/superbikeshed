package borg.trikeshed.nio

expect class PlatformByteBuffer private constructor(buffer: Any) {
    fun putLong(value: Long)
    fun put(src: ByteArray, offset: Int, length: Int)
    fun flip()
    fun array(): ByteArray
    fun limit(): Int
    fun remaining(): Int
    fun getLong(): Long
    fun get(dst: ByteArray)
    fun position(): Int
    companion object {
        fun allocate(capacity: Int): PlatformByteBuffer
        fun wrap(array: ByteArray, offset: Int, length: Int): PlatformByteBuffer
    }
} 