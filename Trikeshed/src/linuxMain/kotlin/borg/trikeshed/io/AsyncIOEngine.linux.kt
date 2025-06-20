package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow
import borg.trikeshed.io.IOOperation
import borg.trikeshed.io.IOResult
import borg.trikeshed.io.IOHandle

actual class AsyncIOEngine private constructor(private val delegate: EngineDelegate) {
    actual fun initialize() = delegate.initialize()
    actual fun cleanup() = delegate.cleanup()
    actual suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long): Int = delegate.read(handle, buffer, offset)
    actual suspend fun write(handle: IOHandle, data: ByteArray, offset: Long): Int = delegate.write(handle, data, offset)
    actual suspend fun submitBatch(operations: List<IOOperation>): List<IOResult> = delegate.submitBatch(operations)
    actual fun completedOperations(): Flow<IOResult> = delegate.completedOperations()

    interface EngineDelegate {
        fun initialize()
        fun cleanup()
        suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long): Int
        suspend fun write(handle: IOHandle, data: ByteArray, offset: Long): Int
        suspend fun submitBatch(operations: List<IOOperation>): List<IOResult>
        fun completedOperations(): Flow<IOResult>
    }

    actual companion object {
        actual fun create(): AsyncIOEngine {
            val forced = System.getenv("ASYNC_IO_ENGINE")?.lowercase()
            val engines = listOf(
                "uring" to { tryLiburing() },
                "epoll" to { tryEpoll() },
                "kqueue" to { tryKqueue() }
            )
            if (forced != null) {
                val found = engines.firstOrNull { it.first == forced }?.second?.invoke()
                if (found != null) return found
                throw IOException("Requested async I/O engine '$forced' is not available on this system.")
            }
            for ((_, factory) in engines) {
                val engine = factory()
                if (engine != null) return engine
            }
            throw IOException("No supported async I/O engine found (tried io_uring, epoll, kqueue)")
        }

        private fun tryLiburing(): AsyncIOEngine? = try {
            val engine = LiburingAsyncIOEngine()
            engine.initialize()
            AsyncIOEngine(object : EngineDelegate {
                override fun initialize() = engine.initialize()
                override fun cleanup() = engine.cleanup()
                override suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long) = engine.read(handle, buffer, offset)
                override suspend fun write(handle: IOHandle, data: ByteArray, offset: Long) = engine.write(handle, data, offset)
                override suspend fun submitBatch(operations: List<IOOperation>) = engine.submitBatch(operations)
                override fun completedOperations() = engine.completedOperations()
            })
        } catch (e: Throwable) {
            null
        }

        private fun tryEpoll(): AsyncIOEngine? = try {
            val engine = EpollAsyncIOEngine()
            engine.initialize()
            AsyncIOEngine(object : EngineDelegate {
                override fun initialize() = engine.initialize()
                override fun cleanup() = engine.cleanup()
                override suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long) = engine.read(handle, buffer, offset)
                override suspend fun write(handle: IOHandle, data: ByteArray, offset: Long) = engine.write(handle, data, offset)
                override suspend fun submitBatch(operations: List<IOOperation>) = engine.submitBatch(operations)
                override fun completedOperations() = engine.completedFlow
            })
        } catch (e: Throwable) {
            null
        }

        private fun tryKqueue(): AsyncIOEngine? = try {
            val engine = KQueueAsyncIOEngine()
            engine.initialize()
            AsyncIOEngine(object : EngineDelegate {
                override fun initialize() = engine.initialize()
                override fun cleanup() = engine.cleanup()
                override suspend fun read(handle: IOHandle, buffer: ByteArray, offset: Long) = engine.read(handle, buffer, offset)
                override suspend fun write(handle: IOHandle, data: ByteArray, offset: Long) = engine.write(handle, data, offset)
                override suspend fun submitBatch(operations: List<IOOperation>) = engine.submitBatch(operations)
                override fun completedOperations() = engine.completedFlow
            })
        } catch (e: Throwable) {
            null
        }
    }
} 