package borg.trikeshed.reactor

import borg.trikeshed.lib.*

/**
 * WasmJs implementation of ByteBuffer
 * Simplified stub implementation for compilation
 */
actual class ByteBuffer {
    private val data = mutableListOf<Byte>()
    private var pos = 0
    private var lim = 0
    
    actual companion object {
        actual fun allocate(capacity: Int): ByteBuffer {
            return ByteBuffer()
        }
        
        actual fun wrap(array: ByteArray): ByteBuffer {
            val buffer = ByteBuffer()
            array.forEach { buffer.data.add(it) }
            buffer.lim = array.size
            return buffer
        }
    }
    
    actual fun put(byte: Byte): ByteBuffer {
        data.add(byte)
        return this
    }
    
    actual fun get(): Byte {
        return if (pos < data.size) data[pos++] else 0
    }
    
    actual fun flip(): ByteBuffer {
        lim = data.size
        pos = 0
        return this
    }
    
    actual fun clear(): ByteBuffer {
        data.clear()
        pos = 0
        lim = 0
        return this
    }
    
    actual fun position(): Int = pos
    actual fun position(newPosition: Int): ByteBuffer {
        pos = newPosition
        return this
    }
    
    actual fun limit(): Int = lim
    actual fun limit(newLimit: Int): ByteBuffer {
        lim = newLimit
        return this
    }
    
    actual fun capacity(): Int = data.size
    actual fun hasRemaining(): Boolean = pos < lim
    actual fun remaining(): Int = lim - pos
    actual fun array(): ByteArray = data.toByteArray()
}