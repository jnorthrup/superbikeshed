package borg.trikeshed.reactor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.ByteBufferFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import platform.posix.clock_gettime
import platform.posix.CLOCK_MONOTONIC
import platform.posix.timespec
import kotlin.time.TimeSource

actual val IO: CoroutineDispatcher = Dispatchers.Default

@OptIn(ExperimentalForeignApi::class)
actual object System {
    actual fun currentTimeMillis(): Long = TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
    actual fun nanoTime(): Long = memScoped {
        val timespec = alloc<timespec>()
        clock_gettime(CLOCK_MONOTONIC.toUInt(), timespec.ptr)
        return timespec.tv_sec * 1_000_000_000L + timespec.tv_nsec
    }
}

actual class PlatformIO {
    actual fun createSelector(): SelectorInterface = SelectorInterface()
    
    actual fun createServerChannel(): ServerChannel = NativeServerChannel()
    
    actual fun createClientChannel(): ClientChannel = NativeClientChannel()
    
    actual fun createBufferPool(bufferSize: Int): BufferPool = NativeBufferPool(bufferSize)
    
    actual companion object {
        actual fun create(): PlatformIO = PlatformIO()
    }
}

class NativeServerChannel : ServerChannel {
    private val selectableChannel = NativeSelectableChannel()
    
    override fun isOpen(): Boolean = selectableChannel.isOpen()
    override fun close() = selectableChannel.close()
    override fun configureBlocking(block: Boolean): SelectableChannel = selectableChannel.configureBlocking(block)
    override fun isBlocking(): Boolean = selectableChannel.isBlocking()
    
    override suspend fun bind(port: Int) {
        TODO("Native ServerChannel bind not implemented")
    }
    
    override suspend fun accept(): ClientChannel? {
        TODO("Native ServerChannel accept not implemented")
    }
}

class NativeClientChannel : ClientChannel {
    private val selectableChannel = NativeSelectableChannel()
    
    override fun isOpen(): Boolean = selectableChannel.isOpen()
    override fun close() = selectableChannel.close()
    override fun configureBlocking(block: Boolean): SelectableChannel = selectableChannel.configureBlocking(block)
    override fun isBlocking(): Boolean = selectableChannel.isBlocking()
    
    override suspend fun connect(host: String, port: Int) {
        TODO("Native ClientChannel connect not implemented")
    }
    
    override suspend fun read(buffer: PlatformByteBuffer): Int {
        TODO("Native ClientChannel read not implemented")
    }
    
    override suspend fun write(buffer: PlatformByteBuffer): Int {
        TODO("Native ClientChannel write not implemented")
    }
}

class NativeBufferPool(private val bufferSize: Int) : BufferPool {
    override suspend fun acquire(): PlatformByteBuffer {
        return ByteBufferFactory.allocate(bufferSize)
    }
    
    override suspend fun release(buffer: PlatformByteBuffer) {
        // Native memory management - could implement pooling here
    }
    
    override suspend fun accept(): ClientChannel? {
        TODO("Native BufferPool accept not implemented")
    }
}

actual interface ServerChannel : SelectableChannel {
    actual fun bind(port: Int)
    actual fun accept(): ClientChannel?
}

actual interface ClientChannel : SelectableChannel {
    actual fun connect(host: String, port: Int)
    actual fun read(buffer: ByteBuffer): Int
    actual fun write(buffer: ByteBuffer): Int
} 