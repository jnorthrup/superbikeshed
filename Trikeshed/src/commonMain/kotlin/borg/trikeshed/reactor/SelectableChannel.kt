package borg.trikeshed.reactor

/**
 * Core interface for selectable channels in the TrikeShed reactor system.
 * This abstracts NIO channel operations for multiplatform use.
 */
expect interface SelectableChannel {
    fun isOpen(): Boolean
    fun close()
    fun configureBlocking(block: Boolean): SelectableChannel
    fun isBlocking(): Boolean
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
expect class SelectorInterface {
    fun select(): Int
    fun wakeup()
    fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey
    fun selectedKeys(): Set<SelectionKey>
    fun close()
}