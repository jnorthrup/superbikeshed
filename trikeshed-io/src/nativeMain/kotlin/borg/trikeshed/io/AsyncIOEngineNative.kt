@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

class AsyncIOEngineNative : AsyncIOEngine {
    override suspend fun initialize() {}
    override suspend fun cleanup() {}

    private fun handleToPath(handle: Int): String {
        // Use AsyncFileManager for proper handle mapping
        return runBlocking { AsyncFileManager.instance.getPath(handle) }
            ?: throw IllegalArgumentException("Unknown handle: $handle")
    }

    override suspend fun read(handle: Int, buffer: ByteArray, offset: Long): Int = withContext(Dispatchers.Default) {
        val path = handleToPath(handle)
        val file = fopen(path, "rb") ?: return@withContext -1
        
        try {
            fseek(file, offset, SEEK_SET)
            buffer.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, buffer.size.toULong(), file).toInt()
            }
        } finally {
            fclose(file)
        }
        
        // TODO: Implement true async I/O using:
        // - Linux: io_uring for high-performance async I/O
        // - macOS: kqueue for event-driven async I/O
        // - Windows: IOCP for completion port-based async I/O
    }

    override suspend fun write(handle: Int, data: ByteArray, offset: Long): Int = withContext(Dispatchers.Default) {
        val path = handleToPath(handle)
        val file = fopen(path, "wb") ?: return@withContext -1
        
        try {
            fseek(file, offset, SEEK_SET)
            data.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, data.size.toULong(), file).toInt()
            }
        } finally {
            fclose(file)
        }
    }

    override suspend fun submitBatch(operations: List<IOOperation>): List<IOResult> {
        return super.submitBatch(operations)
    }

    override fun completedOperations() = super.completedOperations()
}

actual fun createAsyncIOEngine(): AsyncIOEngine = AsyncIOEngineNative() 