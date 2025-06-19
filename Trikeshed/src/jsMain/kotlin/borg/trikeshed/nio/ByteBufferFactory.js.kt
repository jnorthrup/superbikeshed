package borg.trikeshed.nio

actual object ByteBufferFactory {
    actual fun allocate(capacity: Int): ByteBuffer = 
        JsNioByteBuffer(capacity)
    
    actual fun allocateDirect(capacity: Int): ByteBuffer = 
        // JavaScript doesn't distinguish between direct and heap buffers
        JsNioByteBuffer(capacity)
    
    actual fun wrap(array: ByteArray): ByteBuffer {
        val buffer = JsNioByteBuffer(array.size)
        buffer.put(array)
        buffer.flip()
        return buffer
    }
}