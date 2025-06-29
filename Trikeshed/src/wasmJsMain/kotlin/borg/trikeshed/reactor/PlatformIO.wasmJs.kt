package borg.trikeshed.reactor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import borg.trikeshed.nio.ByteBufferFactory

// Platform-specific IO dispatcher for WASM
actual val IO: CoroutineDispatcher = Dispatchers.Default

// Platform-specific System utilities for WASM
actual object System {
    actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()
    actual fun nanoTime(): Long = kotlin.js.Date.now().toLong() * 1_000_000 // Approximate
}

actual class PlatformIO {
    actual fun createSelector(): SelectorInterface = SelectorInterface()
    
    actual fun createServerChannel(): ServerChannel = WasmServerChannel()
    
    actual fun createClientChannel(): ClientChannel = WasmClientChannel()
    
    actual fun createBufferPool(bufferSize: Int): BufferPool = WasmBufferPool(bufferSize)
    
    actual companion object {
        actual fun create(): PlatformIO = PlatformIO()
    }
}

class WasmServerChannel : ServerChannel {
    private var open = true
    
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
    override fun configureBlocking(block: Boolean): SelectableChannel = this
    override fun isBlocking(): Boolean = false
    
    override fun bind(port: Int) {
        TODO("Server socket binding not available in WASM - requires host binding")
    }
    
    override fun accept(): ClientChannel? {
        TODO("Server socket accept not available in WASM - requires host binding")
    }
}

class WasmClientChannel : ClientChannel {
    private var open = true
    
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
    override fun configureBlocking(block: Boolean): SelectableChannel = this
    override fun isBlocking(): Boolean = false
    
    override fun connect(host: String, port: Int) {
        TODO("Direct TCP connections not available in WASM - requires host binding")
    }
    
    override fun read(buffer: ByteBuffer): Int {
        TODO("Direct socket read not available in WASM - requires host binding")
    }
    
    override fun write(buffer: ByteBuffer): Int {
        TODO("Direct socket write not available in WASM - requires host binding")
    }
}

class WasmBufferPool(private val bufferSize: Int) : BufferPool {
    override fun acquire(): ByteBuffer {
        return ByteBufferFactory.allocate(bufferSize)
    }
    
    override fun release(buffer: ByteBuffer) {
        // WASM has garbage collection, no manual release needed
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