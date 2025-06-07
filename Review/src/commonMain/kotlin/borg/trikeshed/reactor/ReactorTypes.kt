package borg.trikeshed.reactor

// Base reactor interfaces and types used by TrikeShed

interface SelectableChannel

interface SelectorInterface {
    suspend fun select(): Int 
    suspend fun selectedKeys(): Set<SelectionKey>
    suspend fun close()
}

interface ServerChannel : SelectableChannel {
    fun configureBlocking(block: Boolean)
    fun register(selector: SelectorInterface, ops: Int, att: Any? = null): SelectionKey
    suspend fun bind(port: Int)
    suspend fun accept(): ClientChannel?
    suspend fun close()
}

interface ClientChannel : SelectableChannel {
    fun configureBlocking(block: Boolean)
    fun register(selector: SelectorInterface, ops: Int, att: Any? = null): SelectionKey
    suspend fun connect(host: String, port: Int): Boolean
    suspend fun read(buffer: ByteArray): Int
    suspend fun write(buffer: ByteArray): Int
    suspend fun close()
}

expect abstract class SelectionKey {
    abstract val isValid: Boolean
    abstract val readyOps: Int
    abstract var interestOps: Int
    abstract var attachment: Any?
    
    abstract fun cancel()
    abstract fun channel(): SelectableChannel
}

interface AsyncReaction {
    suspend fun execute()
}

// Platform-specific implementations
expect class PlatformIO {
    suspend fun createSelector(): SelectorInterface
    suspend fun createServerChannel(): ServerChannel  
    suspend fun createClientChannel(): ClientChannel
    suspend fun createBufferPool(bufferSize: Int): BufferPool
    
    companion object {
        suspend fun create(): PlatformIO
    }
}

expect abstract class IOOperation {
    abstract val value: Int
    
    companion object {
        val ACCEPT: IOOperation
        val READ: IOOperation
        val WRITE: IOOperation
        val CONNECT: IOOperation
    }
}

expect open class BufferPool {
    open fun acquire(): ByteArray
    open fun release(buffer: ByteArray)
}