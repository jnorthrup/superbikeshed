package borg.trikeshed.reactor

/**
 * Native platform-specific implementations
 * All time references must be inlined to KMP Clock class calls
 */

// Platform-specific extensions can be added here as needed

actual class SelectionKey {
    actual fun channel(): SelectableChannel {
        throw UnsupportedOperationException("Native SelectionKey not implemented")
    }
    
    actual fun selector(): SelectorInterface {
        throw UnsupportedOperationException("Native SelectionKey not implemented")
    }
    
    actual fun isValid(): Boolean = false
    
    actual fun cancel() {
        // Native implementation - no-op
    }
    
    actual fun interestOps(): Int = 0
    
    actual fun interestOps(ops: Int): SelectionKey = this
    
    actual fun readyOps(): Int = 0
    
    actual fun isReadable(): Boolean = false
    
    actual fun isWritable(): Boolean = false
    
    actual fun isConnectable(): Boolean = false
    
    actual fun isAcceptable(): Boolean = false
    
    actual fun attachment(): Any? = null
    
    actual fun attach(ob: Any?): Any? = null
}

actual class SelectorInterface {
    actual fun select(): Int = 0

    actual fun wakeup() {
        // Native implementation - no-op for now
    }

    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey {
        // Native implementation - simplified - TODO: implement proper native selector
        return SelectionKey() // Native SelectionKey constructor with no parameters
    }

    actual fun selectedKeys(): Set<SelectionKey> = emptySet()

    actual suspend fun close() {
        // Native implementation - no-op for now
    }
}
