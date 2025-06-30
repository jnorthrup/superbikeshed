package borg.trikeshed.reactor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import borg.trikeshed.nio.ByteBufferFactory

// Platform-specific IO dispatcher for JavaScript
actual val IO: CoroutineDispatcher = Dispatchers.Default

// Platform-specific System utilities for JavaScript
actual object System {
    actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()
    actual fun nanoTime(): Long = kotlin.js.Date.now().toLong() * 1_000_000 // Approximate
}

actual class PlatformIO {
    actual fun createSelector(): SelectorInterface = SelectorInterface()
    
    actual fun createServerChannel(): ServerChannel = JsServerChannel()
    
    actual fun createClientChannel(): ClientChannel = JsClientChannel()
    
    actual fun createBufferPool(bufferSize: Int): BufferPool = JsBufferPool(bufferSize)
    
    actual companion object {
        actual fun create(): PlatformIO = PlatformIO()
    }
}

class JsServerChannel : ServerChannel {
    private var open = true
    
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
    override fun configureBlocking(block: Boolean): SelectableChannel = this
    override fun isBlocking(): Boolean = false
    
    override fun bind(port: Int) {
        // Browser cannot bind server sockets directly
        TODO("Server socket binding not available in browser - requires WebSocket server")
    }
    
    override fun accept(): ClientChannel? {
        // Browser cannot accept connections directly
        TODO("Server socket accept not available in browser - requires WebSocket server")
    }
}

class JsClientChannel : ClientChannel {
    private var open = true
    
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
    override fun configureBlocking(block: Boolean): SelectableChannel = this
    override fun isBlocking(): Boolean = false
    
    override fun connect(host: String, port: Int) {
        // Browser connections require WebSocket or fetch API
        TODO("Direct TCP connections not available in browser - requires WebSocket or fetch API")
    }
    
    override fun read(buffer: ByteBuffer): Int {
        // Browser doesn't support direct socket reads
        TODO("Direct socket read not available in browser - requires WebSocket or streams API")
    }
    
    override fun write(buffer: ByteBuffer): Int {
        // Browser doesn't support direct socket writes
        TODO("Direct socket write not available in browser - requires WebSocket or streams API")
    }
}

class JsBufferPool(private val bufferSize: Int) : BufferPool {
    override fun acquire(): ByteBuffer {
        return ByteBufferFactory.allocate(bufferSize)
    }
    
    override fun release(buffer: ByteBuffer) {
        // JavaScript has garbage collection, no manual release needed
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