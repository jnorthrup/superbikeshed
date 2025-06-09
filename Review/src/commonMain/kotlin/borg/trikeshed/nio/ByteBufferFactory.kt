package borg.trikeshed.nio

expect object ByteBufferFactory {
    fun allocate(capacity: Int): ByteBuffer
    fun allocateDirect(capacity: Int): ByteBuffer
    fun wrap(array: ByteArray, offset: Int = 0, length: Int = array.size - offset): ByteBuffer
}
