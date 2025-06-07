package evolution.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import kotlin.coroutines.CoroutineContext

// --- Actual JVM SocketAddress ---
actual class SocketAddress actual constructor(actual val host: String, actual val port: Int) {
    internal val nioAddress: InetSocketAddress = InetSocketAddress(host, port)

    actual fun getHostName(): String = nioAddress.hostString
    actual fun getPort(): Int = nioAddress.port

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        // Ensure 'other' is of the same actual type for a meaningful comparison of nioAddress.
        // If 'other' is a SocketAddress from a different platform, it wouldn't have nioAddress.
        if (other !is SocketAddress) return false // Check if it's the common expect type
        val otherJvmAddress = other as? evolution.io.SocketAddress // Safe cast to JVM actual
                                  ?: return nioAddress.equals(other) // Fallback or handle error if 'other' is not JVM SocketAddress

        return nioAddress == otherJvmAddress.nioAddress
    }

    override fun hashCode(): Int = nioAddress.hashCode()
    override fun toString(): String = nioAddress.toString()
}

// --- Actual JVM PlatformUdpChannel ---
actual class JvmUdpChannel(
    private var boundAddress: SocketAddress? = null

    actual override suspend fun bind(localAddress: SocketAddress): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                nioChannel.bind(localAddress.nioAddress)
                val actualBoundAddress = nioChannel.localAddress as? java.net.InetSocketAddress
                if (actualBoundAddress != null) { this.boundAddress = SocketAddress(actualBoundAddress.hostString, actualBoundAddress.port) }
                true
            } catch (e: Exception) {
                // Optionally log exception e
                false
            }
        }
    }

    actual override fun getLocalAddress(): SocketAddress? {
        // Return cached boundAddress first if available (after explicit bind)
        if (boundAddress != null) return boundAddress
        // Otherwise, try to get from nioChannel.localAddress (might be null if not bound)
        val localNioAddress = try { nioChannel.localAddress as? java.net.InetSocketAddress } catch (e: java.nio.channels.ClosedChannelException) { null }
        return localNioAddress?.let { SocketAddress(it.hostString, it.port) }
    }
    private val nioChannel: DatagramChannel,
    private val selector: Selector // Selector from the creating JvmPlatformIoService
) : PlatformUdpChannel {

    actual override val key: CoroutineContext.Key<*> get() = PlatformUdpChannelKey
    private var nativeKey: SelectionKey? = null

    init {
        nioChannel.configureBlocking(false)
    }

    actual override suspend fun register(interest: InterestOp, attachment: Any?): Any? {
        return withContext(Dispatchers.IO) { // NIO operations can block
            val nioInterest = when (interest) {
                InterestOp.READ -> SelectionKey.OP_READ
                InterestOp.WRITE -> SelectionKey.OP_WRITE
            }
            // Channel must be registered in non-blocking mode.
            // If already registered, modify interestOps.
            if (nativeKey?.isValid == true) {
                nativeKey!!.interestOps(nioInterest)
                nativeKey!!.attach(attachment) // Update attachment
            } else {
                nativeKey = nioChannel.register(selector, nioInterest, attachment)
            }
            nativeKey
        }
    }

    actual override suspend fun send(data: ByteArray, targetAddress: SocketAddress): Int {
        return withContext(Dispatchers.IO) {
            val byteBuffer = ByteBuffer.wrap(data)
            nioChannel.send(byteBuffer, targetAddress.nioAddress)
        }
    }

    actual override suspend fun receive(buffer: ByteArray): Pair<Int, SocketAddress?> {
        return withContext(Dispatchers.IO) {
            val byteBuffer = ByteBuffer.wrap(buffer)
            val sourceNioAddress = nioChannel.receive(byteBuffer) as? InetSocketAddress
            val bytesRead = byteBuffer.position()

            val sourceSocketAddress = sourceNioAddress?.let {
                SocketAddress(it.hostString, it.port)
            }
            Pair(bytesRead, sourceSocketAddress)
        }
    }

    actual override fun configureBlocking(block: Boolean) {
        // Defer to Dispatchers.IO for safety, though configureBlocking itself might be quick.
        // Note: Cannot change blocking mode while registered with a Selector on some OS.
        // This should ideally be called before first registration.
        kotlinx.coroutines.runBlocking(Dispatchers.IO) { // Or make this suspend too
             if (nioChannel.isRegistered && nioChannel.isBlocking != block) {
                 // This is problematic. For simplicity, we might throw or ignore.
                 // Let's throw for now if trying to change while registered, unless it's to non-blocking.
                 if (block) throw IllegalStateException("Cannot change to blocking mode while channel is registered with a selector.")
             }
            nioChannel.configureBlocking(block)
        }
    }

    actual override fun close() {
        // Defer to Dispatchers.IO for safety
         kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            nativeKey?.cancel() // Cancel the key upon closing the channel
            nioChannel.close()
        }
    }

    actual override fun getNativeKey(): Any? = nativeKey

    // Internal access for JvmPlatformIoService
    internal fun getNioChannel(): DatagramChannel = nioChannel
}


// --- Actual JVM PlatformIoService ---
actual class JvmPlatformIoService actual constructor() : PlatformIoService {
    actual override fun close() { closeSelectorInternal() }
    actual override val key: CoroutineContext.Key<*> get() = PlatformIoServiceKey

    private val selector: Selector = Selector.open()

    actual override suspend fun createUdpChannel(): PlatformUdpChannel? {
        return withContext(Dispatchers.IO) {
            try {
                val datagramChannel = DatagramChannel.open()
                datagramChannel.configureBlocking(false) // Ensure non-blocking before passing to JvmUdpChannel constructor
                // Pass this service's selector to the channel for registration
                JvmUdpChannel(datagramChannel, selector)
            } catch (e: Exception) {
                // Log error (e.g., using a logging framework or printing to stderr)
                // println("Error creating UDP channel: ${e.message}")
                null
            }
        }
    }

    actual override suspend fun runSelectorLoop(handler: suspend (SelectionEvent) -> Unit) {
        withContext(Dispatchers.IO) { // Selector operations are blocking
            while (isActive && selector.isOpen) { // Check coroutine isActive and selector.isOpen
                try {
                    // Use a timeout to allow checking isActive more frequently if selector is not woken up.
                    // A small timeout (e.g., 100ms) can be a good compromise.
                    val selectedCount = selector.select(100) // Timeout in milliseconds

                    if (!selector.isOpen) break // Exit if selector closed during select
                    // if (selectedCount == 0 && selector.isOpen) { // select() might return 0 if wakeup() or timeout.
                    // No need to explicitly check selectedCount == 0 if timeout is used, loop will continue.
                    // }

                    // Check coroutine cancellation before processing keys
                    if (!isActive) break

                    val selectedKeys = selector.selectedKeys()
                    val iterator = selectedKeys.iterator()

                    while (iterator.hasNext()) {
                        val selectionKey = iterator.next()
                        iterator.remove()

                        if (!selectionKey.isValid) {
                            continue
                        }

                        val attachment = selectionKey.attachment()

                        // It's crucial that the handler can deal with the possibility of the key becoming invalid
                        // during its own suspend operations if it were to, for example, close the channel.
                        if (selectionKey.isValid && selectionKey.isReadable) {
                            handler(SelectionEvent(selectionKey, InterestOp.READ, attachment))
                        }
                        // Check isValid again as handler for READ might have invalidated the key (e.g., closed channel)
                        if (selectionKey.isValid && selectionKey.isWritable) {
                            handler(SelectionEvent(selectionKey, InterestOp.WRITE, attachment))
                        }
                    }
                } catch (e: java.nio.channels.ClosedSelectorException) {
                    // Selector closed, exit loop
                    break
                } catch (e: Exception) {
                    // Log error (e.g. using a logging framework)
                    // println("Error in selector loop: ${e.message}")
                    // Depending on the error, might want to break or continue.
                    // For robustness, many selector loops might continue on non-fatal errors.
                    // For this example, we'll break.
                    break
                }
            }
        }
    }

    actual override fun wakeupSelector() {
        // Check if selector is open to avoid ClosedSelectorException on wakeup itself,
        // though wakeup() on a closed selector is usually a no-op or benign.
        if (selector.isOpen) {
            selector.wakeup()
        }
    }

    // Consider adding a close() method to JvmPlatformIoService to close the selector
    internal fun closeSelectorInternal() { // Renamed to close for consistency with other closeable resources
        // Defer to Dispatchers.IO for safety
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            if (selector.isOpen) {
                // Make copies of keys before iterating and closing/cancelling
                // to avoid ConcurrentModificationException if selector.keys() is a live set.
                val keys = selector.keys().toList() // Create a copy
                keys.forEach { key ->
                    try {
                        key.channel()?.close() // Close the underlying Java NIO channel
                        key.cancel()
                    } catch (e: Exception) {
                        // Log error during key/channel cleanup
                        // println("Error closing channel/key during selector close: ${e.message}")
                    }
                }
                try {
                    selector.close()
                } catch (e: Exception) {
                     // Log error closing selector
                    // println("Error closing selector: ${e.message}")
                }
            }
        }
    }
}
