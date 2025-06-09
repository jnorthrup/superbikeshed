package borg.trikeshed.nio

import java.nio.ByteBuffer as JavaNioByteBuffer

// Actual JVM implementation for the ByteBufferFactory
actual object ByteBufferFactory {

    actual fun allocate(capacity: Int): ByteBuffer {
        return JvmByteBuffer(JavaNioByteBuffer.allocate(capacity))
    }

    actual fun allocateDirect(capacity: Int): ByteBuffer {
        return JvmByteBuffer(JavaNioByteBuffer.allocateDirect(capacity))
    }

    actual fun wrap(array: ByteArray, offset: Int, length: Int): ByteBuffer {
        // Ensure offset and length are handled correctly by java.nio.ByteBuffer.wrap
        // The signature for JavaNioByteBuffer.wrap is wrap(array, offset, length)
        return JvmByteBuffer(JavaNioByteBuffer.wrap(array, offset, length))
    }

    // Remove JvmByteBufferWrapper class as JvmByteBuffer is now the main implementation.
    // The 'nioMigrated' flag can also be removed if it was part of the old structure.
}
