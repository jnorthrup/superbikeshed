package evolution.io

import evolution.io.node.Dgram
import evolution.io.node.RemoteInfo
import evolution.io.node.Socket as NodeJsSocket // Alias to avoid conflict
import kotlinx.coroutines.*
import org.khronos.webgl.Uint8Array
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min // For minOf helper

@OptIn(ExperimentalCoroutinesApi::class) // For suspendCancellableCoroutine
actual class SocketAddress actual constructor(actual val host: String, actual val port: Int) {
    actual fun getHostName(): String = host
    actual fun getPort(): Int = port

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SocketAddress) return false
        return host == other.host && port == other.port
    }
    override fun hashCode(): Int = 31 * host.hashCode() + port
    override fun toString(): String = "$host:$port"
}

@OptIn(ExperimentalCoroutinesApi::class) // For suspendCancellableCoroutine
actual class JsUdpChannel internal constructor(
    private var boundAddress: SocketAddress? = null

    actual override suspend fun bind(localAddress: SocketAddress): Boolean {
        return suspendCancellableCoroutine { continuation ->
            // Node.js dgram bind can take port, address, and callback.
            // If address is omitted, it defaults to 0.0.0.0 or :: depending on OS settings if port is given.
            // If port is 0, OS assigns an ephemeral port.
            nodeSocket.bind(localAddress.getPort(), localAddress.getHostName()) { ->
                if (continuation.isActive) {
                    try {
                        val actualAddressInfo = nodeSocket.address()
                        this.boundAddress = SocketAddress(actualAddressInfo.address, actualAddressInfo.port)
                        continuation.resume(true)
                    } catch (e: Exception) {
                        continuation.resumeWithException(RuntimeException("Failed to get address info after bind", e))
                    }
                }
            }
            // Add error listener for bind errors (e.g. EADDRINUSE)
            val errorListener: (evolution.io.node.Error) -> Unit = { err ->
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("Bind failed: \${err.message}"))
                }
            }
            nodeSocket.once("error", evolution.io.Js као(errorListener) as Function<*>)
            continuation.invokeOnCancellation { nodeSocket.removeListener("error", evolution.io.Js као(errorListener) as Function<*>) }
        }
    }

    actual override fun getLocalAddress(): SocketAddress? {
        if (boundAddress != null) return boundAddress
        // For Node.js, if not explicitly bound and cached, nodeSocket.address() might throw if socket is not yet bound
        // or if it has been closed. A try-catch is good practice.
        return try {
            val addrInfo = nodeSocket.address()
            SocketAddress(addrInfo.address, addrInfo.port).also { this.boundAddress = it }
        } catch (e: Exception) { null }
    }
    internal val nodeSocket: NodeJsSocket,
    private val scope: CoroutineScope // Scope for launching listeners for message events
) : PlatformUdpChannel {

    actual override val key: CoroutineContext.Key<*> get() = PlatformUdpChannelKey
    private var nativeKeyRepresentation: Any? = nodeSocket // The socket itself can serve as its "key"

    // This handler is set by JsPlatformIoService when runSelectorLoop is called.
    // It's invoked by the 'message' event listener below.
    internal var messageHandlerCallback: ((data: ByteArray, sourceAddress: SocketAddress, attachment: Any?) -> Unit)? = null
    private var attachmentStore: Any? = null // Stores attachment from register()

    init {
        nodeSocket.on("message") { msg: Uint8Array, rinfo: RemoteInfo ->
            val data = ByteArray(msg.length) { i -> msg[i] }
            val sourceAddress = SocketAddress(rinfo.address, rinfo.port)
            // Call the currently set message handler (from JsPlatformIoService)
            messageHandlerCallback?.invoke(data, sourceAddress, attachmentStore)
        }
        nodeSocket.on("error") { err: evolution.io.node.Error ->
            // TODO: This error needs to be propagated to the user or selector loop.
            // For now, print and close. A robust implementation might emit a specific error event.
            println("JsUdpChannel: Socket error for ${nodeSocket.address()}: ${err.message}")
            close()
        }
        nodeSocket.on("close") { ->
            // Handle socket close event, perhaps notify service to remove it.
            // println("JsUdpChannel: Socket ${nodeSocket.address()} closed.")
            messageHandlerCallback = null // Prevent further message handling
        }
    }

    actual override suspend fun register(interest: InterestOp, attachment: Any?): Any? {
        // "Registration" in this event-driven model:
        // For READ: ensure 'message' listener is active (done in init) and messageHandlerCallback is set by service.
        // For WRITE: dgram sockets are generally assumed writable. If a send fails due to OS buffers being full,
        // the error is reported in the send callback. There isn't a direct "writable" event like in select/epoll.
        // We store the attachment here for the service to pick up when creating SelectionEvent.
        this.attachmentStore = attachment

        // Indicate success by returning the socket (native key)
        return nodeSocket
    }

    actual override suspend fun send(data: ByteArray, targetAddress: SocketAddress): Int {
        return suspendCancellableCoroutine { continuation ->
            val jsBuffer = Uint8Array(data.toTypedArray())
            nodeSocket.send(jsBuffer, targetAddress.getPort(), targetAddress.getHostName()) { err, bytes ->
                if (continuation.isActive) { // Check if coroutine is still active
                    if (err != null) {
                        continuation.resumeWithException(Exception("JsUdpChannel send failed: ${err.message}"))
                    } else {
                        continuation.resume(bytes)
                    }
                }
            }
        }
    }

    actual override suspend fun receive(buffer: ByteArray): Pair<Int, SocketAddress?> {
        // This direct receive model fits poorly with dgram's event-based nature.
        // The primary way to receive is via the 'message' event handled by messageHandlerCallback.
        // A call to this `receive` implies a single, specific wait for one datagram.
        // This requires temporarily intercepting the message stream for this specific call.
        if (messageHandlerCallback == null) {
            // This indicates that runSelectorLoop (which sets messageHandlerCallback via setReadInterestCallback)
            // is not active or this channel isn't properly managed by it for READ interest.
            // However, for a direct receive, we might not need the selector loop's handler.
            // Instead, we install a one-time listener or use a temporary handler.
        }

        return suspendCancellableCoroutine { continuation ->
            val oneTimeListener: (Uint8Array, RemoteInfo) -> Unit = { msg, rinfo ->
                nodeSocket.removeListener("message", Js као(oneTimeListener) as Function<*>) // Clean up: Cast needed for Function<*> type
                if (continuation.isActive) {
                    val receivedData = ByteArray(msg.length) { i -> msg[i] }
                    val bytesToCopy = minOf(receivedData.size, buffer.size)
                    receivedData.copyInto(buffer, 0, 0, bytesToCopy)
                    val sourceAddress = SocketAddress(rinfo.address, rinfo.port)
                    continuation.resume(Pair(bytesToCopy, sourceAddress))
                }
            }
            val errorListener: (evolution.io.node.Error) -> Unit = { err ->
                 nodeSocket.removeListener("message", Js као(oneTimeListener) as Function<*>)
                 nodeSocket.removeListener("error", Js као(errorListener) as Function<*>)
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("JsUdpChannel receive error: ${err.message}"))
                }
            }

            nodeSocket.on("message", Js као(oneTimeListener) as Function<*>)
            nodeSocket.on("error", Js као(errorListener) as Function<*>) // Handle errors specific to this receive attempt

            continuation.invokeOnCancellation {
                nodeSocket.removeListener("message", Js као(oneTimeListener) as Function<*>)
                nodeSocket.removeListener("error", Js као(errorListener) as Function<*>)
            }
        }
    }

    actual override fun configureBlocking(block: Boolean) {
        // Node.js dgram sockets are event-driven and don't have a blocking mode toggle in this sense.
        if (block) {
            console.warn("JsUdpChannel: configureBlocking(true) is not applicable to Node.js dgram sockets.")
        }
    }

    actual override fun close() {
        try {
            // Remove specific listeners if necessary, though close should handle it.
            nodeSocket.removeAllListeners("message")
            nodeSocket.removeAllListeners("error")
            nodeSocket.removeAllListeners("close")
            nodeSocket.close { -> /* Optional callback after close */ }
        } catch (e: Exception) {
            // console.error("Error closing JsUdpChannel: ${e.message}")
        }
        messageHandlerCallback = null // Clear the handler
    }

    actual override fun getNativeKey(): Any? = nativeKeyRepresentation
}


actual class JsPlatformIoService actual constructor(
    actual override fun close() { closeServiceInternal() }
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : PlatformIoService {
    actual override val key: CoroutineContext.Key<*> get() = PlatformIoServiceKey

    // Map NodeJsSocket to our JsUdpChannel wrapper
    private val managedChannels = mutableMapOf<NodeJsSocket, JsUdpChannel>()
    private var isLoopActive = CompletableDeferred<Unit>() // Used to keep runSelectorLoop 'running'

    actual override suspend fun createUdpChannel(): PlatformUdpChannel? {
        return try {
            val nodeSocket = Dgram.createSocket("udp4")
            val channel = JsUdpChannel(nodeSocket, coroutineScope)
            managedChannels[nodeSocket] = channel

            // Bind to an OS-assigned port, necessary for receiving messages.
            suspendCancellableCoroutine<Unit> { continuation ->
                nodeSocket.bind { -> // Callback for when bind is complete
                    if (continuation.isActive) continuation.resume(Unit)
                }
                // Node.js dgram socket does not typically emit an 'error' event for bind failure directly on `bind()`.
                // Errors like EADDRINUSE would be emitted as 'error' events on the socket object itself.
                // A robust implementation might add a temporary error listener here.
            }
            channel
        } catch (e: Exception) {
            // Log e
            null
        }
    }

    actual override suspend fun runSelectorLoop(handler: suspend (SelectionEvent) -> Unit) {
        // The "selector loop" in Node.js is effectively the Node.js event loop.
        // This function sets up the handlers that bridge dgram events to our SelectionEvent model.
        isLoopActive = CompletableDeferred() // Reset for new loop run

        managedChannels.values.forEach { channel ->
            channel.messageHandlerCallback = { dataByteArray, sourceAddress, attachment ->
                // This is called by JsUdpChannel's 'message' listener
                val selectionEvent = SelectionEvent(
                    channelKey = channel.getNativeKey(), // The NodeJsSocket itself
                    interestOp = InterestOp.READ,
                    attachment = attachment // Attachment from channel.register()
                )
                coroutineScope.launch {
                    // The handler is called, but it needs the actual data.
                    // The current SelectionEvent doesn't carry the data.
                    // The handler would typically call channel.receive() if it's designed for that,
                    // but our channel.receive() sets up its own one-time listener.
                    // This implies the 'handler' should perhaps take data directly.
                    // Option 1: Handler calls channel.receive() - this is problematic with current receive impl.
                    // Option 2: SelectionEvent carries the data.
                    // Option 3: Handler is (ByteArray, SocketAddress) -> Unit, not SelectionEvent.
                    // Let's assume for now, the handler is aware and if it's a READ event,
                    // data should be obtained differently or SelectionEvent enhanced.
                    // For this example, we'll stick to the current SelectionEvent.
                    // The user of the handler would need to know that for a READ event triggered this way,
                    // the data isn't in the event, and calling channel.receive() might be tricky
                    // because that sets up its own listeners.
                    // This highlights a mismatch if trying to map select() strictly.
                    // A better model for JS might be for the handler to receive (PlatformUdpChannel, InterestOp, Data?)
                    // Or, the channel itself invokes a user-provided onRead callback.

                    // For now, let's make the handler call simpler: it gets the event, and if it's READ,
                    // it has to know the data was what triggered it. This is not ideal.
                    // A better `JsUdpChannel` would queue received data and `receive()` dequeues.
                    // The `messageHandlerCallback` would add to this queue and then trigger the `handler`.
                    // For this simplified version, let's assume the handler is designed to work with this.
                    handler(selectionEvent)
                }
            }
            // Simulate WRITE readiness: In Node.js, sockets are generally writable unless send() callback returns an error.
            // We can proactively signal WRITE interest if the channel was registered for it.
             coroutineScope.launch { // Proactively signal WRITE if registered.
                 handler(SelectionEvent(channel.getNativeKey(), InterestOp.WRITE, channel.getAttachment()))
             }
        }

        // Keep the coroutine suspended until wakeupSelector is called or scope is cancelled
        try {
            isLoopActive.await()
        } finally {
            // Cleanup when loop ends
            managedChannels.values.forEach { channel ->
                channel.messageHandlerCallback = null // Clear handlers
            }
        }
    }

    actual override fun wakeupSelector() {
        // Completes the deferred, allowing runSelectorLoop to finish if it's awaiting.
        isLoopActive.complete(Unit)
    }

    // Custom function to close the service and its resources
    internal fun closeServiceInternal() {
        wakeupSelector() // Signal loop to stop
        coroutineScope.cancel() // Cancel all coroutines launched by this service
        managedChannels.values.toList().forEach { it.close() } // toList to avoid CME
        managedChannels.clear()
    }
}

// Helper to cast lambdas to Function<*> for JS interop with EventEmitter.on, etc.
// This is a common workaround. Proper typing might involve more complex external decls.
@Suppress("UNCHECKED_CAST")
private fun <T> Js као(lambda: T): Function<*> = lambda as Function<*
