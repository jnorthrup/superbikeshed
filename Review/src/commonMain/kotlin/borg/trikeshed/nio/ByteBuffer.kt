package borg.trikeshed.nio

expect interface ByteBuffer {
    fun clear()
    fun flip()
    fun hasRemaining(): Boolean
    fun remaining(): Int
    fun position(): Int
    fun position(newPosition: Int)

    fun put(byte: Byte)
    fun put(bytes: ByteArray)
    fun put(bytes: ByteArray, offset: Int, length: Int)
    fun put(source: ByteBuffer) // To put contents of another ByteBuffer

    fun putShort(value: Short)
    fun putInt(value: Int)
    fun putLong(value: Long)
    fun putFloat(value: Float)
    fun putDouble(value: Double)

    fun get(): Byte
    fun get(bytes: ByteArray)
    // fun get(bytes: ByteArray, offset: Int, length: Int) // Consider adding for completeness

    fun getShort(): Short
    fun getInt(): Int
    fun getLong(): Long
    fun getFloat(): Float
    fun getDouble(): Double

    fun limit(): Int
    fun limit(newLimit: Int)
    fun capacity(): Int

    fun duplicate(): ByteBuffer
    fun slice(): ByteBuffer
    fun rewind()

    // Optional: Consider adding isDirect, array, arrayOffset, order if commonly needed cross-platform
    // fun isDirect(): Boolean
    // fun order(): ByteOrder
    // fun order(order: ByteOrder): ByteBuffer
}

// Optional: Define ByteOrder in commonMain if not already present
// enum class ByteOrder { BIG_ENDIAN, LITTLE_ENDIAN }
