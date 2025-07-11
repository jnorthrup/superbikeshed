package borg.trikeshed.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.runBlocking

class AsyncIOEngineJvm : AsyncIOEngine {
    override suspend fun initialize() {}
    override suspend fun cleanup() {}

    // For demo: treat handle as a path hash, map to path (not production safe)
    private fun handleToPath(handle: Int): String {
        // Use AsyncFileManager for proper handle mapping
        return runBlocking { AsyncFileManager.instance.getPath(handle) }
            ?: throw IllegalArgumentException("Unknown handle: $handle")
    }

    override suspend fun read(handle: Int, buffer: ByteArray, offset: Long): Int = withContext(Dispatchers.IO) {
        // For demo, assume handle is a path string's hashCode
        val path = handleToPath(handle)
        AsynchronousFileChannel.open(Paths.get(path), StandardOpenOption.READ).use { channel ->
            val byteBuffer = ByteBuffer.wrap(buffer)
            val future = channel.read(byteBuffer, offset)
            future.get() // blocks, but in Dispatchers.IO
        }
    }

    override suspend fun write(handle: Int, data: ByteArray, offset: Long): Int = withContext(Dispatchers.IO) {
        val path = handleToPath(handle)
        AsynchronousFileChannel.open(
            Paths.get(path), 
            StandardOpenOption.WRITE, 
            StandardOpenOption.CREATE, 
            StandardOpenOption.TRUNCATE_EXISTING
        ).use { channel ->
            val byteBuffer = ByteBuffer.wrap(data)
            val future = channel.write(byteBuffer, offset)
            future.get() // blocks, but in Dispatchers.IO
        }
    }

    override suspend fun submitBatch(operations: List<IOOperation>): List<IOResult> {
        return super.submitBatch(operations)
    }

    override fun completedOperations() = super.completedOperations()
}

actual fun createAsyncIOEngine(): AsyncIOEngine = AsyncIOEngineJvm() 