import java.nio.ByteBuffer as JvmByteBuffer

actual class ByteBuffer {
    private var buffer: JvmByteBuffer = JvmByteBuffer.allocate(0)  // Default buffer, can be replaced with sized buffer in constructor if needed

    fun clear() = buffer.clear()
    fun flip() = buffer.flip()
    fun hasRemaining(): Boolean = buffer.hasRemaining()
    fun remaining(): Int = buffer.remaining()
    fun position(): Int = buffer.position()
    fun position(newPosition: Int) { buffer.position(newPosition) }
    fun put(byte: Byte) { buffer.put(byte) }
    fun put(bytes: ByteArray) { buffer.put(bytes) }
    fun putInt(value: Int) { buffer.putInt(value) }
    fun putLong(value: Long) { buffer.putLong(value) }
    fun get(): Byte = buffer.get()
    fun get(bytes: ByteArray) { buffer.get(bytes) }
    fun getInt(): Int = buffer.getInt()
    fun getLong(): Long = buffer.getLong()
    fun limit(): Int = buffer.limit()
    fun limit(newLimit: Int) { buffer.limit(newLimit) }
    fun capacity(): Int = buffer.capacity()
}