package borg.trikeshed.reactor

expect class ByteBuffer {
    companion object {
        fun allocate(capacity: Int): ByteBuffer
        fun wrap(array: ByteArray): ByteBuffer
    }
    
    fun put(byte: Byte): ByteBuffer
    fun get(): Byte
    fun flip(): ByteBuffer
    fun clear(): ByteBuffer
    fun position(): Int
    fun position(newPosition: Int): ByteBuffer
    fun limit(): Int
    fun limit(newLimit: Int): ByteBuffer
    fun capacity(): Int
    fun hasRemaining(): Boolean
    fun remaining(): Int
    fun array(): ByteArray
}