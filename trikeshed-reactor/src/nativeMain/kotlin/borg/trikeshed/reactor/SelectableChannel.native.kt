@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

actual interface SelectableChannel {
    actual val isOpen: Boolean
    actual suspend fun close()
    actual fun configureBlocking(block: Boolean): SelectableChannel
    actual val isBlocking: Boolean
}

// Native implementation - basic stub for now
class NativeSelectableChannel : SelectableChannel {
    internal var open = true
    internal var blocking = true
    
    override val isOpen: Boolean get() = open
    
    override suspend fun close() {
        open = false
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel {
        blocking = block
        return this
    }
    
    override val isBlocking: Boolean get() = blocking
}

class NativeWritableChannel : WritableChannel {
    internal val channel = NativeSelectableChannel()
    
    override val isOpen: Boolean get() = channel.isOpen
    override suspend fun close() = channel.close()
    override fun configureBlocking(block: Boolean): SelectableChannel = channel.configureBlocking(block)
    override val isBlocking: Boolean get() = channel.isBlocking
    
    override suspend fun write(buffer: ByteBuffer): Int {
        // Stub implementation
        return buffer.remaining()
    }
}

class NativeReadableChannel : ReadableChannel {
    internal val channel = NativeSelectableChannel()
    
    override val isOpen: Boolean get() = channel.isOpen
    override suspend fun close() = channel.close()
    override fun configureBlocking(block: Boolean): SelectableChannel = channel.configureBlocking(block)
    override val isBlocking: Boolean get() = channel.isBlocking
    
    override suspend fun read(buffer: ByteBuffer): Int {
        // Stub implementation
        return 0
    }
}