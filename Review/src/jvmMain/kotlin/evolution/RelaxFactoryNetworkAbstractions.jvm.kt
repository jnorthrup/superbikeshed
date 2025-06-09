@file:Suppress("NOTHING_TO_INLINE")

package evolution

import borg.trikeshed.lib.*
import java.nio.channels.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.jvm.*

/**
 * RelaxFactory Network Abstractions - JVM Implementation
 * 
 * Platform-specific implementation using Java NIO, maintaining the exact
 * RelaxFactory patterns: SelectionKey as session handle, enqueue pattern,
 * and lambda receiving selector with minimal declaration.
 */

/**
 * 🔄 JVM Network Reactor using Java NIO Selector
 */
actual object NetworkReactor {
    
    private val enqueueQueue = ConcurrentLinkedQueue<EnqueueItem>()
    private var _selector: Selector? = null
    private var selectorThread: Thread? = null
    
    actual var killswitch: Boolean = false
    
    actual val selector: NetworkSelector?
        get() = _selector?.let { JvmNetworkSelector(it) }
    
    /**
     * RelaxFactory enqueue pattern - exact implementation
     */
    actual fun enqueue(channel: NetworkChannel, op: NetworkOp, vararg payload: Any?) {
        val jvmChannel = (channel as JvmNetworkChannel).channel
        
        // RelaxFactory thread-safety pattern
        if (Thread.currentThread() == selectorThread) {
            try {
                jvmChannel.register(_selector!!, op.mask, payload)
            } catch (e: ClosedChannelException) {
                e.printStackTrace()
            }
        } else {
            enqueueQueue.add(EnqueueItem(jvmChannel, op.mask, payload))
        }
        
        _selector?.wakeup()
    }
    
    /**
     * RelaxFactory init pattern with protocol decoder
     */
    actual fun init(visitor: AsyncIOVisitor, vararg args: String) {
        _selector = Selector.open()
        selectorThread = Thread.currentThread()
        
        selectLoop(visitor)
    }
    
    /**
     * Main selector loop - exact RelaxFactory pattern
     */
    actual fun selectLoop(visitor: AsyncIOVisitor) {
        synchronized(Unit) {
            var timeoutMax = 1024L
            var timeout = 1L
            
            while (!killswitch) {
                // Process enqueue queue (RelaxFactory pattern)
                while (!enqueueQueue.isEmpty()) {
                    val item = enqueueQueue.remove()
                    try {
                        item.channel.configureBlocking(false)
                        val selectionKey = item.channel.register(_selector!!, item.op, item.payload)
                        assert(selectionKey != null)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                
                // Select with timeout
                val selectedCount = _selector!!.select(timeout)
                
                if (selectedCount > 0) {
                    timeout = 1L // Reset timeout on activity
                    val selectedKeys = _selector!!.selectedKeys()
                    val iterator = selectedKeys.iterator()
                    
                    while (iterator.hasNext()) {
                        val selectionKey = iterator.next()
                        iterator.remove()
                        
                        try {
                            val handle = SessionHandle(selectionKey.attachment())
                            
                            // Dispatch based on ready operations (RelaxFactory pattern)
                            when {
                                selectionKey.isReadable -> visitor.onRead(handle)
                                selectionKey.isWritable -> visitor.onWrite(handle)
                                selectionKey.isAcceptable -> visitor.onAccept(handle)
                                selectionKey.isConnectable -> visitor.onConnect(handle)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            selectionKey.cancel()
                        }
                    }
                } else {
                    // Exponential backoff on no activity
                    timeout = minOf(timeout * 2, timeoutMax)
                }
            }
        }
    }
    
    /**
     * Enqueue item for thread-safe registration
     */
    private data class EnqueueItem(
        val channel: SelectableChannel,
        val op: Int,
        val payload: Array<out Any?>
    )
}

/**
 * 📡 JVM Network Channel Implementation
 */
actual class NetworkChannel(val channel: SocketChannel) {
    
    actual fun configureNonBlocking() {
        channel.configureBlocking(false)
    }
    
    actual fun finishConnect(): Boolean = channel.finishConnect()
    
    actual fun isOpen(): Boolean = channel.isOpen
    
    actual fun close() = channel.close()
}

/**
 * JVM Network Server Channel Implementation  
 */
actual class NetworkServerChannel(val channel: ServerSocketChannel) {
    
    actual fun accept(): NetworkChannel? = 
        channel.accept()?.let { NetworkChannel(it) }
    
    actual fun configureNonBlocking() {
        channel.configureBlocking(false)
    }
    
    actual fun isOpen(): Boolean = channel.isOpen
    
    actual fun close() = channel.close()
}

/**
 * JVM Network Selector Implementation
 */
actual class NetworkSelector(val selector: Selector) {
    
    actual fun select(timeoutMs: Long): Int = selector.select(timeoutMs)
    
    actual fun wakeup() = selector.wakeup()
    
    actual fun close() = selector.close()
}

/**
 * JVM-specific wrapper for type safety
 */
class JvmNetworkSelector(selector: Selector) : NetworkSelector(selector)

/**
 * 🏭 JVM Factory Methods
 */
object JvmNetworkFactory {
    
    /**
     * Create JVM network channel from SocketChannel
     */
    fun createChannel(socketChannel: SocketChannel): NetworkChannel = 
        NetworkChannel(socketChannel)
    
    /**
     * Create JVM server channel from ServerSocketChannel
     */
    fun createServerChannel(serverSocketChannel: ServerSocketChannel): NetworkServerChannel = 
        NetworkServerChannel(serverSocketChannel)
    
    /**
     * Create JVM selector
     */
    fun createSelector(): NetworkSelector = 
        NetworkSelector(Selector.open())
}

/**
 * 🔧 JVM Extensions for RelaxFactory Compatibility
 */

/**
 * Convert Java NIO SelectionKey to SessionHandle
 */
fun SelectionKey.toSessionHandle(): SessionHandle = SessionHandle(this.attachment())

/**
 * Convert Java NIO SocketChannel to NetworkChannel
 */
fun SocketChannel.toNetworkChannel(): NetworkChannel = NetworkChannel(this)

/**
 * Convert Java NIO ServerSocketChannel to NetworkServerChannel  
 */
fun ServerSocketChannel.toNetworkServerChannel(): NetworkServerChannel = NetworkServerChannel(this)

/**
 * RelaxFactory-style channel registration
 */
fun SocketChannel.enqueueWithPayload(op: NetworkOp, vararg payload: Any?) {
    NetworkReactor.enqueue(this.toNetworkChannel(), op, *payload)
}

/**
 * 🚀 JVM HTTP Operations (RelaxFactory pattern)
 */

/**
 * JVM HTTP Visitor using exact RelaxFactory patterns
 */
class JvmHttpVisitor : AsyncIOVisitor.Impl() {
    
    override fun onRead(handle: SessionHandle) {
        // Extract SelectionKey-equivalent data from handle
        val channel = handle.payload<SocketChannel>()
        channel?.let { ch ->
            try {
                val receiveBufferSize = ch.socket().receiveBufferSize
                // Process HTTP request using RelaxFactory pattern
                processHttpRequest(ch, receiveBufferSize)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onWrite(handle: SessionHandle) {
        val channel = handle.payload<SocketChannel>()
        channel?.let { ch ->
            try {
                // HTTP response writing using RelaxFactory pattern
                writeHttpResponse(ch)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun processHttpRequest(channel: SocketChannel, bufferSize: Int) {
        // RelaxFactory HTTP request processing
    }
    
    private fun writeHttpResponse(channel: SocketChannel) {
        // RelaxFactory HTTP response writing
    }
}

/**
 * JVM Proxy Visitor (RelaxFactory HttpPipeVisitor pattern)
 */
class JvmProxyVisitor : AsyncIOVisitor.Impl() {
    
    override fun onRead(handle: SessionHandle) {
        val proxyContext = handle.payload<ProxySessionContext>()
        proxyContext?.let { ctx ->
            // Bidirectional piping using RelaxFactory pattern
            pipeData(
                (ctx.sourceChannel as JvmNetworkChannel).channel,
                (ctx.targetChannel as JvmNetworkChannel).channel
            )
        }
    }
    
    private fun pipeData(from: SocketChannel, to: SocketChannel) {
        // RelaxFactory bidirectional data piping
    }
}