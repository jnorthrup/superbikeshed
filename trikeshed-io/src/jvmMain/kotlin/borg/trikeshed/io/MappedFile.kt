package borg.trikeshed.io

import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * JVM implementation of MappedFile using NIO memory-mapped files
 */
actual class MappedFile actual constructor(
    private val path: String,
    private val size: Long,
    private val readOnly: Boolean
) {
    private var mappedBuffer: MappedByteBuffer? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var channel: FileChannel? = null
    
    actual fun close() {
        mappedBuffer = null
        channel?.close()
        randomAccessFile?.close()
        channel = null
        randomAccessFile = null
    }
    
    actual fun open() {
        val mode = if (readOnly) "r" else "rw"
        randomAccessFile = RandomAccessFile(path, mode)
        channel = randomAccessFile!!.channel
        
        val mapMode = if (readOnly) FileChannel.MapMode.READ_ONLY else FileChannel.MapMode.READ_WRITE
        mappedBuffer = channel!!.map(mapMode, 0, size)
    }
    
    actual fun isOpen(): Boolean = mappedBuffer != null
    
    actual fun size(): Long = size
    
    actual fun get(index: Long): Byte {
        return mappedBuffer?.get(index.toInt()) ?: 0
    }
    
    actual fun put(index: Long, value: Byte) {
        if (!readOnly) {
            mappedBuffer?.put(index.toInt(), value)
        }
    }
}