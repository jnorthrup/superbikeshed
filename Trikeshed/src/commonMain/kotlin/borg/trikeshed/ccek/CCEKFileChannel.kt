package borg.trikeshed.ccek

import borg.trikeshed.io.PlatformFileIO
import borg.trikeshed.io.PlatformFileIOImpl
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.net.socks.AsyncChannel
import borg.trikeshed.net.socks.SqeOp
import kotlinx.coroutines.channels.Channel

/**
 * CCEKFileChannel implements AsyncChannel for file I/O operations,
 * allowing file reads and writes to be treated as channels within the CCEK framework.
 */
class CCEKFileChannel(
    val filePath: String,
    private val fileIO: PlatformFileIO = PlatformFileIOImpl()
) : AsyncChannel {

    override val fd: Int = filePath.hashCode() // Simple hash for file descriptor
    override val isOpen: Boolean = true // Files are conceptually always open for read/write operations
    override val localAddress: String = filePath
    override val remoteAddress: String = "local"

    // For file channels, batch operations might not be directly supported by underlying PlatformFileIO
    // We'll simulate them by calling single read/write operations in a loop.
    override suspend fun readBatch(buffers: Indexed<ByteArray>): Indexed<Int> {
        val results = mutableListOf<Int>()
        for (i in 0 until buffers.a) {
            val buffer = buffers.b(i)
            val bytesRead = fileIO.readFile(filePath)?.let { fileContent ->
                val offset = 0 // For simplicity, assume reading from start for now
                val length = minOf(buffer.size, fileContent.a - offset)
                if (length > 0) {
                    fileContent.slice(offset, offset + length).play.copyInto(buffer, 0, 0, length)
                    length
                } else {
                    -1 // End of file
                }
            } ?: -1 // File not found or error
            results.add(bytesRead)
        }
        return results.size j results::get
    }

    override suspend fun writeBatch(buffers: Indexed<ByteArray>): Indexed<Int> {
        val results = mutableListOf<Int>()
        for (i in 0 until buffers.a) {
            val buffer = buffers.b(i)
            val success = fileIO.writeFile(filePath, buffer.size j buffer::get)
            results.add(if (success) buffer.size else -1)
        }
        return results.size j results::get
    }

    override suspend fun localAddress(): String = localAddress
    override suspend fun remoteAddress(): String = remoteAddress

    override suspend fun close() {
        // No explicit close needed for fileIO, as it's stateless
        // In a real scenario, if file handles were managed, they would be closed here.
    }

    override suspend fun submitAndWait(sqeOps: Indexed<SqeOp>): Indexed<Int> {
        // This is a simplified implementation. In a real io_uring integration,
        // these operations would be submitted to the kernel.
        val results = mutableListOf<Int>()
        for (i in 0 until sqeOps.a) {
            when (val op = sqeOps.b(i)) {
                is SqeOp.Read -> {
                    val bytesRead = fileIO.readFile(filePath)?.let { fileContent ->
                        val offset = op.offset.toInt()
                        val length = minOf(op.buffer.size, fileContent.a - offset)
                        if (length > 0) {
                            fileContent.slice(offset, offset + length).play.copyInto(op.buffer, 0, 0, length)
                            length
                        } else {
                            -1 // End of file
                        }
                    } ?: -1
                    results.add(bytesRead)
                }
                is SqeOp.Write -> {
                    val success = fileIO.writeFile(filePath, op.buffer.size j op.buffer::get)
                    results.add(if (success) op.buffer.size else -1)
                }
                else -> results.add(-1) // Unsupported operation
            }
        }
        return results.size j results::get
    }

    override val completions: Channel<Any>
        get() = Channel(Channel.UNLIMITED) // Not directly used for file operations in this simplified model
}
