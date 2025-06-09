package borg.trikeshed.nio

actual object ByteBufferFactory {
    actual fun allocate(capacity: Int): ByteBuffer {
        return NativeArrayByteBuffer(ByteArray(capacity), capacity)
    }

    actual fun allocateDirect(capacity: Int): ByteBuffer {
        // In Kotlin/Native, "direct" memory allocation can be more complex if it means off-heap via C interop.
        // Currently, this implementation uses a standard ByteArray, which is on the Kotlin/Native heap and GC-managed.
        // This simplifies memory management for users of the ByteBuffer.
        // If true off-heap buffers are needed, NativeArrayByteBuffer would need to be significantly changed,
        // or a different ByteBuffer implementation would be required for allocateDirect.
        return NativeArrayByteBuffer(ByteArray(capacity), capacity)
    }

    actual fun wrap(array: ByteArray, offset: Int, length: Int): ByteBuffer {
        require(offset >= 0 && offset <= array.size) { "Offset $offset is out of bounds for array size ${array.size}" }
        require(length >= 0 && offset + length <= array.size) { "Length $length is out of bounds for offset $offset and array size ${array.size}" }

        if (offset == 0 && length == array.size) {
            // If wrapping the whole array, we can use it directly.
            return NativeArrayByteBuffer(array, length, 0, length)
        } else {
            // If wrapping a sub-array, copy the relevant part.
            // This is because NativeArrayByteBuffer currently doesn't support an internal offset
            // into a shared larger ByteArray for a true "view".
            val subArray = array.copyOfRange(offset, offset + length)
            return NativeArrayByteBuffer(subArray, subArray.size, 0, subArray.size)
        }
    }
}
