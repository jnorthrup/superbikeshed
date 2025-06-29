package borg.trikeshed.nio

actual object ByteBufferFactory {
    actual fun allocate(capacity: Int): ByteBuffer = 
        NativeByteBuffer(ByteArray(capacity))
    
    actual fun allocateDirect(capacity: Int): ByteBuffer = 
        // Native doesn't distinguish between direct and heap buffers
        NativeByteBuffer(ByteArray(capacity))
    
    actual fun wrap(array: ByteArray): ByteBuffer = 
        NativeByteBuffer(array.copyOf())
}