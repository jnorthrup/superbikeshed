package borg.trikeshed.reactor

import borg.trikeshed.nio.PlatformByteBuffer

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
    suspend fun write(buffer: PlatformByteBuffer): Int
}

interface ReadableChannel : SelectableChannel {
    suspend fun read(buffer: PlatformByteBuffer): Int
}

interface BufferPool {
    suspend fun acquire(): PlatformByteBuffer
    suspend fun release(buffer: PlatformByteBuffer)
    suspend fun accept(): ClientChannel?
}

expect interface ServerChannel : SelectableChannel {
    suspend fun bind(port: Int)
    suspend fun accept(): ClientChannel?
}

expect interface ClientChannel : ReadableChannel, WritableChannel {
    suspend fun connect(host: String, port: Int)
}

/**
 * Represents a selection key for channel/selector operations.
 */
expect class SelectionKey {
    fun channel(): SelectableChannel
    fun selector(): SelectorInterface
    fun isValid(): Boolean
    fun cancel()
    fun interestOps(): Int
    fun interestOps(ops: Int): SelectionKey
    fun readyOps(): Int
    fun isReadable(): Boolean
    fun isWritable(): Boolean
    fun isConnectable(): Boolean
    fun isAcceptable(): Boolean
    fun attachment(): Any?
    fun attach(ob: Any?): Any?
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