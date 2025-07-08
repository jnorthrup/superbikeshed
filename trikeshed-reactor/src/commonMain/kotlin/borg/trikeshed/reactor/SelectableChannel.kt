@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

/**
 * Core interface for selectable channels in the TrikeShed reactor system.
 * This abstracts NIO channel operations for multiplatform use.
 */
expect interface SelectableChannel {
    val isOpen: Boolean
    suspend fun close()
    fun configureBlocking(block: Boolean): SelectableChannel
    val isBlocking: Boolean
}

interface WritableChannel : SelectableChannel {
    suspend fun write(buffer: ByteBuffer): Int
}

interface ReadableChannel : SelectableChannel {
    suspend fun read(buffer: ByteBuffer): Int
}

interface BufferPool {
    suspend fun acquire(): ByteBuffer
    suspend fun release(buffer: ByteBuffer)
    suspend fun accept(): ClientChannel?
}

interface ServerChannel : SelectableChannel {
    suspend fun bind(port: Int)
    suspend fun accept(): ClientChannel?
}

expect class ClientChannel : ReadableChannel, WritableChannel {
    suspend fun connect(host: String, port: Int)
}


/**
 * Platform-specific selector interface for non-blocking I/O operations.
 */
expect class SelectorInterface() {
    fun select(): Int
    fun wakeup()
    fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey
    fun selectedKeys(): Set<SelectionKey>
    suspend fun close()
}