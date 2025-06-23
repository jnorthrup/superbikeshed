package borg.trikeshed.nio

actual object ByteBufferFactory {
    actual fun allocate(capacity: Int): ByteBuffer = 
        ByteBuffer.allocate(capacity)
    
    actual fun allocateDirect(capacity: Int): ByteBuffer = 
        // Native doesn't distinguish between direct and heap buffers
        ByteBuffer.allocate(capacity)
    
    actual fun wrap(array: ByteArray): ByteBuffer = 
        ByteBuffer.wrap(array)
}