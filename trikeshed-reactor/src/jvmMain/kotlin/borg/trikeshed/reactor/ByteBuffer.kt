@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.ByteBuffer as JvmByteBuffer

/**
 * JVM platform implementation using java.nio.ByteBuffer.
 * This bridges our common API to the JVM's efficient buffer implementation.
 */
actual class ByteBuffer(internal val jvmBuffer: JvmByteBuffer) {
    
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer = 
            ByteBuffer(JvmByteBuffer.allocate(capacity))
            
        actual fun wrap(array: ByteArray): ByteBuffer = 
            ByteBuffer(JvmByteBuffer.wrap(array))
    }
    
    // Single byte operations
    actual fun put(byte: Byte): ByteBuffer {
        jvmBuffer.put(byte)
        return this
    }
    
    actual fun get(): Byte = jvmBuffer.get()
    
    // Bulk operations
    actual fun put(src: ByteArray): ByteBuffer {
        jvmBuffer.put(src)
        return this
    }
    
    actual fun put(src: ByteArray, offset: Int, length: Int): ByteBuffer {
        jvmBuffer.put(src, offset, length)
        return this
    }
    
    actual fun get(dst: ByteArray): ByteBuffer {
        jvmBuffer.get(dst)
        return this
    }
    
    actual fun get(dst: ByteArray, offset: Int, length: Int): ByteBuffer {
        jvmBuffer.get(dst, offset, length)
        return this
    }
    
    // Buffer state operations
    actual fun flip(): ByteBuffer {
        jvmBuffer.flip()
        return this
    }
    
    actual fun clear(): ByteBuffer {
        jvmBuffer.clear()
        return this
    }
    
    actual fun rewind(): ByteBuffer {
        jvmBuffer.rewind()
        return this
    }
    
    actual fun mark(): ByteBuffer {
        jvmBuffer.mark()
        return this
    }
    
    actual fun reset(): ByteBuffer {
        jvmBuffer.reset()
        return this
    }
    
    // Position and limit operations
    actual fun position(): Int = jvmBuffer.position()
    
    actual fun position(newPosition: Int): ByteBuffer {
        jvmBuffer.position(newPosition)
        return this
    }
    
    actual fun limit(): Int = jvmBuffer.limit()
    
    actual fun limit(newLimit: Int): ByteBuffer {
        jvmBuffer.limit(newLimit)
        return this
    }
    
    actual fun capacity(): Int = jvmBuffer.capacity()
    
    // Remaining operations
    actual fun hasRemaining(): Boolean = jvmBuffer.hasRemaining()
    
    actual fun remaining(): Int = jvmBuffer.remaining()
    
    // Array access
    actual fun array(): ByteArray = jvmBuffer.array()
    
    /**
     * Access to underlying JVM ByteBuffer for interop.
     */
    fun jvmBuffer(): JvmByteBuffer = jvmBuffer
}