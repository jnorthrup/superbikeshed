@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import java.nio.channels.SelectableChannel as JvmSelectableChannel
import java.nio.channels.WritableByteChannel
import java.nio.channels.ReadableByteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual interface SelectableChannel {
    actual val isOpen: Boolean
    actual suspend fun close()
    actual fun configureBlocking(block: Boolean): SelectableChannel
    actual val isBlocking: Boolean
}

// JVM wrapper for NIO channels
class JvmSelectableChannelWrapper(
    internal val channel: JvmSelectableChannel
) : SelectableChannel {
    override val isOpen: Boolean get() = channel.isOpen
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        channel.close()
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel {
        channel.configureBlocking(block)
        return this
    }
    
    override val isBlocking: Boolean get() = channel.isBlocking
}

// JVM implementations for readable/writable
class JvmWritableChannel(
    internal val channel: WritableByteChannel
) : WritableChannel {
    override val isOpen: Boolean get() = channel.isOpen()
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        channel.close()
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel = this
    override val isBlocking: Boolean = true
    
    override suspend fun write(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        channel.write(buffer.jvmBuffer)
    }
}

class JvmReadableChannel(
    internal val channel: ReadableByteChannel
) : ReadableChannel {
    override val isOpen: Boolean get() = channel.isOpen()
    
    override suspend fun close() = withContext(Dispatchers.IO) {
        channel.close()
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel = this
    override val isBlocking: Boolean = true
    
    override suspend fun read(buffer: ByteBuffer): Int = withContext(Dispatchers.IO) {
        channel.read(buffer.jvmBuffer)
    }
}