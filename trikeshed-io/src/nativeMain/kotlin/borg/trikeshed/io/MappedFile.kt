package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)

/**
 * Native implementation of MappedFile using mmap
 */
actual class MappedFile actual constructor(
    private val path: String,
    private val size: Long,
    private val readOnly: Boolean
) {
    private var mappedPtr: CPointer<ByteVar>? = null
    private var fd: Int = -1
    
    actual fun close() {
        mappedPtr?.let {
            munmap(it, size.toULong())
        }
        if (fd >= 0) {
            close(fd)
        }
        mappedPtr = null
        fd = -1
    }
    
    actual fun open() {
        val flags = if (readOnly) O_RDONLY else O_RDWR
        fd = open(path, flags)
        if (fd < 0) return
        
        val prot = if (readOnly) PROT_READ else (PROT_READ or PROT_WRITE)
        mappedPtr = mmap(null, size.toULong(), prot, MAP_SHARED, fd, 0)?.reinterpret()
    }
    
    actual fun isOpen(): Boolean = mappedPtr != null
    
    actual fun size(): Long = size
    
    actual fun get(index: Long): Byte {
        return mappedPtr?.plus(index.toInt())?.pointed?.value ?: 0
    }
    
    actual fun put(index: Long, value: Byte) {
        if (!readOnly) {
            mappedPtr?.plus(index.toInt())?.pointed?.value = value
        }
    }
}