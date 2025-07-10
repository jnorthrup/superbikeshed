@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor

import kotlinx.coroutines.*

actual class ClientChannel : ReadableChannel, WritableChannel {
    internal var _isOpen = false
    
    override val isOpen: Boolean get() = _isOpen
    
    override suspend fun close() {
        _isOpen = false
    }
    
    override fun configureBlocking(block: Boolean): SelectableChannel = this
    override val isBlocking: Boolean get() = false
    
    actual suspend fun connect(host: String, port: Int) {
        // In a real implementation, this would use WebSocket or fetch API
        _isOpen = true
    }
    
    override suspend fun write(buffer: ByteBuffer): Int {
        // Mock implementation - in real use would write to WebSocket
        return buffer.remaining()
    }
    
    override suspend fun read(buffer: ByteBuffer): Int {
        // Mock implementation - in real use would read from WebSocket
        return 0
    }
}

actual class SelectionKey(internal var valid: Boolean = true) {
    actual fun isValid(): Boolean = valid
    actual fun cancel() { valid = false }
    actual fun interestOps(): Int = 0
    actual fun interestOps(ops: Int): SelectionKey = this
    actual fun readyOps(): Int = 0
    actual fun channel(): Any = Unit
    actual fun selector(): Any = Unit
    actual fun isReadable(): Boolean = false
    actual fun isWritable(): Boolean = false
    actual fun isConnectable(): Boolean = false
    actual fun isAcceptable(): Boolean = false
    actual fun attachment(): Any? = null
    actual fun attach(ob: Any?): Any? = null
}

actual class SelectorInterface actual constructor() {
    actual fun select(): Int = 0
    actual fun wakeup() {}
    actual fun register(channel: SelectableChannel, ops: Int, attachment: Any?): SelectionKey =
        SelectionKey()
    actual fun selectedKeys(): Set<SelectionKey> = emptySet()
    actual suspend fun close() {}
}

actual class IOOperation actual constructor(actual val value: Int) {
    actual companion object {
        actual val Read = IOOperation(1)
        actual val Write = IOOperation(4)
        actual val Accept = IOOperation(16)
        actual val Connect = IOOperation(8)
    }
}

actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()