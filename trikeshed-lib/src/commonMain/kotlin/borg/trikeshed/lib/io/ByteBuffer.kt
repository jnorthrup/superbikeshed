package borg.trikeshed.lib.io

expect class ByteBuffer {
    companion object {
        fun allocate(capacity: Int): ByteBuffer
        fun wrap(array: ByteArray): ByteBuffer
    }
    
    // Single byte operations
    fun put(byte: Byte): ByteBuffer
    fun get(): Byte
    
    // Bulk operations - needed for channel implementations
    fun put(src: ByteArray): ByteBuffer
    fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer
    fun get(dst: ByteArray): ByteBuffer
    fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer
    
    // Buffer state operations
    fun flip(): ByteBuffer
    fun clear(): ByteBuffer
    fun rewind(): ByteBuffer
    fun mark(): ByteBuffer
    fun reset(): ByteBuffer
    
    // Position and limit operations
    fun position(): Int
    fun position(newPosition: Int): ByteBuffer
    fun limit(): Int
    fun limit(newLimit: Int): ByteBuffer
    fun capacity(): Int
    
    // Remaining operations
    fun hasRemaining(): Boolean
    fun remaining(): Int
    
    // Array access
    fun array(): ByteArray
}