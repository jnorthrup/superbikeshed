package borg.trikeshed.isam

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * JVM implementation of FileAccess using NIO FileChannel
 */
actual abstract class FileAccess actual constructor(actual val filename: String) : CommonCloseable {
    
    actual abstract val platformCloseable: Any?
    
    actual abstract val size: Long
    
    actual abstract fun readAt(position: Long, length: Int): ByteArray
    
    actual abstract fun writeAt(position: Long, data: ByteArray)
}

/**
 * JVM FileChannel-based implementation
 */
class JVMFileAccess(
    filename: String,
    private val channel: FileChannel
) : FileAccess(filename) {
    
    override val platformCloseable: FileChannel = channel
    
    override val size: Long
        get() = channel.size()
    
    override fun readAt(position: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        val bytesRead = channel.read(buffer, position)
        buffer.flip()
        
        return if (bytesRead == length) {
            buffer.array()
        } else {
            // Handle partial reads
            val result = ByteArray(bytesRead.coerceAtLeast(0))
            buffer.get(result)
            result
        }
    }
    
    override fun writeAt(position: Long, data: ByteArray) {
        val buffer = ByteBuffer.wrap(data)
        channel.write(buffer, position)
    }
    
    override fun close() {
        channel.close()
    }
}

/**
 * Create FileAccess for reading
 */
fun openFileForReading(filename: String): FileAccess {
    val path = Paths.get(filename)
    val channel = FileChannel.open(path, StandardOpenOption.READ)
    return JVMFileAccess(filename, channel)
}

/**
 * Create FileAccess for writing
 */
fun openFileForWriting(filename: String): FileAccess {
    val path = Paths.get(filename)
    val channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING
    )
    return JVMFileAccess(filename, channel)
}