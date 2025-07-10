@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

/**
 * WasmJS implementation of MappedFile
 */
actual class MappedFile actual constructor(
    internal val path: String,
    internal val size: Long,
    internal val readOnly: Boolean
) {
    internal val buffer = ByteArray(size.toInt())
    internal var isOpenFlag = false
    
    actual fun close() {
        isOpenFlag = false
    }
    
    actual fun open() {
        isOpenFlag = true
    }
    
    actual fun isOpen(): Boolean = isOpenFlag
    
    actual fun size(): Long = size
    
    actual fun get(index: Long): Byte {
        return if (index < size) buffer[index.toInt()] else 0
    }
    
    actual fun put(index: Long, value: Byte) {
        if (!readOnly && index < size) {
            buffer[index.toInt()] = value
        }
    }
}