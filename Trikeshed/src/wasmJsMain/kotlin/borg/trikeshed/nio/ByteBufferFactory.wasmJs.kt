package borg.trikeshed.nio

actual object ByteBufferFactory {
    actual fun allocate(capacity: Int): ByteBuffer = 
        WasmByteBuffer(capacity)
    
    actual fun allocateDirect(capacity: Int): ByteBuffer = 
        // WASM doesn't distinguish between direct and heap buffers
        WasmByteBuffer(capacity)
    
    actual fun wrap(array: ByteArray): ByteBuffer {
        val buffer = WasmByteBuffer(array.size)
        buffer.put(array)
        buffer.flip()
        return buffer
    }
}