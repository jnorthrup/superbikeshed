package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

actual class AsyncIOEngine {
    private val kqueueEngine = KQueueAsyncIOEngine()
    private var nextId = 0L

    actual fun initialize() {
        kqueueEngine.initialize()
    }

    actual fun cleanup() {
        kqueueEngine.cleanup()
    }

    actual suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long): Int {
        val op = IOOperation(nextId++, IOOperation.IOType.READ, handle, buffer, offset)
        val result = kqueueEngine.submit(op)
        if (result.isError) {
            throw IOException("Read failed with error code ${result.error}")
        }
        return result.bytesTransferred
    }

    actual suspend fun write(handle: IOHandle, data: ByteArray, offset: Long): Int {
        val op = IOOperation(nextId++, IOOperation.IOType.WRITE, handle, data, offset)
        val result = kqueueEngine.submit(op)
        if (result.isError) {
            throw IOException("Write failed with error code ${result.error}")
        }
        return result.bytesTransferred
    }

    actual suspend fun submitBatch(operations: List<IOOperation>): List<IOResult> = coroutineScope {
        operations.map { op ->
            async { kqueueEngine.submit(op) }
        }.awaitAll()
    }

    actual fun completedOperations(): Flow<IOResult> {
        return kqueueEngine.completedFlow
    }

    actual companion object {
        actual fun create(): AsyncIOEngine = AsyncIOEngine()
    }
} 