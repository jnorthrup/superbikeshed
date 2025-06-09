package borg.trikeshed.nio

import borg.trikeshed.core.Tensor
import borg.trikeshed.core.internal.TensorUtils // Placeholder for Tensor <-> ByteArray/Uint8Array conversion
import evolution.io.SocketAddress // Reusing common expect class

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers // For JS, typically Dispatchers.Default or a specific JS dispatcher
import node.net.Server as NodeServer
import node.net.Socket as NodeClientSocket
import node.net.AddressInfo
import node.buffer.Buffer // Node.js Buffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.ClosedReceiveChannelException


// --- JS Actual for SocketAddress (if not already provided by evolution.io for JS) ---
// Assuming evolution.io.SocketAddress has a JS actual.
// Add internal helpers if needed for Node.js net.Socket addresses.

internal fun SocketAddress.toNodeAddressOptions(): dynamic {
    return js("({ host: this.host, port: this.port })")
}

internal fun AddressInfo.toSocketAddress(): SocketAddress {
    return SocketAddress(this.address, this.port)
}

actual interface ServerSocketChannel : NioSelectable {
    // Actual implementations will be in ActualServerSocketChannel
}

actual class ActualServerSocketChannel : ServerSocketChannel {
    private var nodeServer: NodeServer? = null
    private var _isOpen: Boolean = false
    private var _localAddress: SocketAddress? = null
    private val acceptChannel = Channel<ClientSocketChannel>(Channel.UNLIMITED) // Buffer for incoming connections

    init {
        try {
            nodeServer = node.net.createServer { clientSocket ->
                val newChannel = ActualClientSocketChannel(clientSocket)
                // clientSocket.remoteAddress and remotePort are available here to form SocketAddress
                val remoteAddrInfo = clientSocket.remoteAddress()
                if (remoteAddrInfo != null) {
                    newChannel.setInitialRemoteAddress(SocketAddress(remoteAddrInfo.address, remoteAddrInfo.port))
                }
                val offerResult = acceptChannel.trySend(newChannel)
                if (!offerResult.isSuccess) {
                    // Failed to queue, likely because consumer (accept call) is not ready or channel is full.
                    // This indicates a potential issue or that the server is overloaded.
                    // For now, close the incoming client socket if we can't queue it.
                    println("Warning: Accept channel could not queue new client. Closing incoming connection.")
                    clientSocket.destroy()
                }
            }
            _isOpen = true // Server created, considered open for binding/listening

            nodeServer?.on("error") { err ->
                // Propagate error to any pending accept call or handle globally
                acceptChannel.close(NioException("Node.js server error: \${err.message}", err.unsafeCast<Throwable>()))
                close() // Close the server socket itself on error
            }
            nodeServer?.on("close") { ->
                _isOpen = false
                acceptChannel.close() // Ensure channel is closed
            }

        } catch (e: Exception) {
            throw NioException("Failed to create Node.js net.Server", e)
        }
    }

    actual override suspend fun bind(host: String, port: Int, backlog: Int): SocketAddress {
        if (!_isOpen || nodeServer == null) throw NioException("Server socket is closed or not initialized.")
        return suspendCancellableCoroutine { continuation ->
            nodeServer!!.listen(port, host, backlog) { error ->
                if (continuation.isActive) { // Check if coroutine is still active
                    if (error == null) {
                        val addrInfo = nodeServer!!.address()
                        if (addrInfo == null) {
                             continuation.resumeWithException(NioException("bind: nodeServer.address() returned null after listen"))
                        } else {
                            _localAddress = addrInfo.toSocketAddress()
                            continuation.resume(_localAddress!!)
                        }
                    } else {
                        continuation.resumeWithException(NioException("Bind failed for \$host:\$port", error.unsafeCast<Throwable>()))
                    }
                }
            }
            continuation.invokeOnCancellation {
                // If bind is cancelled, ensure server is closed if it was started by this attempt.
                // This is complex if server is already listening. For now, assume cancellation means stop.
                // close()
            }
        }
    }

    actual override suspend fun accept(): ClientSocketChannel? {
        if (!_isOpen) return null
        return try {
            acceptChannel.receive()
        } catch (e: ClosedReceiveChannelException) {
            null // Channel closed, means server is shutting down or errored.
        } catch (e: Exception) {
            throw NioException("Accept failed due to an unexpected error", e)
        }
    }

    actual override fun localAddress(): SocketAddress? = _localAddress

    actual override fun close() {
        if (_isOpen) {
            _isOpen = false
            try {
                nodeServer?.close()
            } catch (e: Exception) { /* log e */ }
            nodeServer = null
            acceptChannel.close() // Close the channel to signal no more accepts
        }
    }

    actual override fun isOpen(): Boolean = _isOpen && nodeServer != null

    actual override fun configureBlocking(block: Boolean) {
        // Node.js 'net' module sockets are event-driven and non-blocking by default.
        // This is effectively a no-op.
        if (block) {
            console.warn("ActualServerSocketChannel.configureBlocking(true) has no direct effect in Node.js.")
        }
    }
}


actual interface ClientSocketChannel : NioSelectable {
    // Actual implementations will be in ActualClientSocketChannel
}

actual class ActualClientSocketChannel internal constructor(
    private val nodeSocket: NodeClientSocket
) : ClientSocketChannel {

    constructor() : this(node.net.Socket())

    private var _isOpen: Boolean = true // Initialized to true as socket is created
    private var _isConnected: Boolean = false
    private var _remoteAddress: SocketAddress? = null
    private var _localAddress: SocketAddress? = null

    private val dataChannel = Channel<ByteArray>(Channel.UNLIMITED) // Buffer for incoming data

    init {
        nodeSocket.on("connect") { ->
            _isConnected = true
            val remoteAddrInfo = nodeSocket.remoteAddress()
            if (remoteAddrInfo != null) {
                _remoteAddress = SocketAddress(remoteAddrInfo.address, remoteAddrInfo.port)
            }
            val localAddrInfo = nodeSocket.localAddress()
            if (localAddrInfo != null) {
                _localAddress = SocketAddress(localAddrInfo.address, localAddrInfo.port)
            }
        }
        nodeSocket.on("data") { chunk ->
            // chunk is Node.js Buffer, convert to ByteArray
            val byteArray = chunk.unsafeCast<Buffer>().toByteArray()
            val offerResult = dataChannel.trySend(byteArray)
            if(!offerResult.isSuccess) {
                 // Handle backpressure or error if channel can't accept data
                 println("Warning: Client data channel could not queue incoming data from Node socket.")
                 // Potentially pause the Node socket: nodeSocket.pause()
            }
        }
        nodeSocket.on("end") { ->
            // Peer has closed their end of the connection for writing.
            // Signal EOF to dataChannel by closing it.
            dataChannel.close()
        }
        nodeSocket.on("close") { hadError ->
            _isOpen = false
            _isConnected = false
            dataChannel.close(if (hadError.unsafeCast<Boolean>()) NioException("Socket closed due to an error") else null)
        }
        nodeSocket.on("error") { err ->
            val exception = NioException("Node.js client socket error: \${err.message}", err.unsafeCast<Throwable>())
            _isConnected = false // Assume error means not connected
            dataChannel.close(exception) // Close data channel with error
            // TODO: How to propagate this error to pending connect/read/write operations?
            // Current suspendCancellableCoroutine calls might not catch this if they completed.
        }
    }

    // Internal helper to set remote address if known from server accept
    internal fun setInitialRemoteAddress(address: SocketAddress) {
        if (!_isConnected) { // Only if not already connected via client-side connect()
            this._remoteAddress = address
            // _isConnected might be set true here if this implies a connected state (e.g. from server.accept)
            // However, connect() is the explicit client-side action to establish connection.
            // For a server-accepted socket, isConnected() should reflect its state.
            // Node.js socket from server 'connection' event is already connected.
             this._isConnected = true
        }
    }


    actual override suspend fun connect(host: String, port: Int): Boolean {
        if (!_isOpen) throw NioException("Socket is closed.")
        if (_isConnected) return true

        return suspendCancellableCoroutine { continuation ->
            nodeSocket.connect(port, host) { // connectListener
                if (continuation.isActive) {
                    _isConnected = true // Callback means connection established
                    val remoteAddrInfo = nodeSocket.remoteAddress()
                    if (remoteAddrInfo != null) {
                        _remoteAddress = SocketAddress(remoteAddrInfo.address, remoteAddrInfo.port)
                    }
                     val localAddrInfo = nodeSocket.localAddress()
                    if (localAddrInfo != null) {
                        _localAddress = SocketAddress(localAddrInfo.address, localAddrInfo.port)
                    }
                    continuation.resume(true)
                }
            }
            // Handle connection errors specifically for this connect attempt
            val errorListener: (Any) -> Unit = { err ->
                if (continuation.isActive) {
                    nodeSocket.removeListener("error", errorListener) // Clean up listener
                    continuation.resumeWithException(NioException("Connect failed for \$host:\$port", err.unsafeCast<Throwable>()))
                }
            }
            nodeSocket.once("error", errorListener) // Use once for this specific connect attempt

            continuation.invokeOnCancellation {
                // If connect is cancelled, attempt to destroy the socket if it's not fully connected.
                if (!_isConnected) {
                    nodeSocket.destroy()
                }
                nodeSocket.removeListener("error", errorListener)
            }
        }
    }

    actual override suspend fun finishConnect(): Boolean {
        // In Node.js, the callback to socket.connect() signifies the connection is complete.
        // There isn't a separate "finishConnect" step like in Java NIO non-blocking.
        return _isConnected
    }

    actual override suspend fun read(buffer: Tensor<Byte>, offset: Int, length: Int): Int {
        if (!_isOpen) throw NioException("Socket is closed.")
        if (!_isConnected) throw NioException("Socket is not connected.")
        require(buffer.rank == 1) { "Buffer tensor must be rank 1." }
        require(offset >= 0 && length > 0 && offset + length <= buffer.totalSize) { "Invalid offset/length for buffer." }

        return try {
            val receivedData = dataChannel.receive() // Suspends until data is available or channel is closed
            val bytesToCopy = minOf(receivedData.size, length)
            // Copy data from receivedData to Tensor<Byte>
            TensorUtils.updateTensorFromByteArray(buffer, offset, receivedData, bytesToCopy)
            bytesToCopy
        } catch (e: ClosedReceiveChannelException) {
            -1 // Channel closed, signifies EOF
        } catch (e: Exception) {
            throw NioException("Read failed", e)
        }
    }

    actual override suspend fun write(buffer: Tensor<Byte>, offset: Int, length: Int): Int {
        if (!_isOpen) throw NioException("Socket is closed.")
        if (!_isConnected) throw NioException("Socket is not connected.")
        require(buffer.rank == 1) { "Buffer tensor must be rank 1." }
        require(offset >= 0 && length > 0 && offset + length <= buffer.totalSize) { "Invalid offset/length for buffer." }

        val dataToWrite = TensorUtils.tensorToByteArray(buffer, offset, length)
        val nodeBuffer = dataToWrite.toUint8Array() // Convert to Uint8Array for Node.js socket.write

        return suspendCancellableCoroutine { continuation ->
            val success = nodeSocket.write(nodeBuffer) { err -> // Callback for when data is flushed or error
                if (continuation.isActive) {
                    if (err != null) {
                        continuation.resumeWithException(NioException("Write failed", err.unsafeCast<Throwable>()))
                    } else {
                        // Node.js socket.write callback doesn't give bytesWritten directly.
                        // The 'success' boolean from write() indicates if it was flushed to kernel.
                        // For simplicity, assume all 'length' bytes are written if no error.
                        // A more robust solution might need to handle partial writes if that can occur
                        // with Node.js streams and backpressure.
                        continuation.resume(length)
                    }
                }
            }
            if (!success && continuation.isActive) {
                // If write returns false, it means data was buffered in user memory due to kernel buffer being full.
                // The 'drain' event will fire when it's okay to write again.
                // For a simple suspending write, we might need to wait for 'drain' or handle this differently.
                // For now, if it buffers, we assume it will eventually be written or error out.
                // The callback will handle final success/failure.
                // If success is false, but no immediate error in callback, it means it's buffered.
                // We are already in a coroutine, so we can wait for the callback.
            }
        }
    }

    actual override fun isConnected(): Boolean = _isConnected && _isOpen && nodeSocket.remoteAddress() != null
    actual override fun remoteAddress(): SocketAddress? = _remoteAddress
    actual override fun localAddress(): SocketAddress? {
         if (_localAddress == null && _isOpen) {
            val localInfo = nodeSocket.localAddress()
            if (localInfo != null) {
                _localAddress = SocketAddress(localInfo.address, localInfo.port)
            }
        }
        return _localAddress
    }


    actual override fun close() {
        if (_isOpen) {
            _isOpen = false // Mark as closing
            try {
                nodeSocket.destroy() // Forcefully close the socket
            } catch (e: Exception) { /* log e */ }
            dataChannel.close()
        }
    }

    actual override fun isOpen(): Boolean = _isOpen && !nodeSocket.destroyed.unsafeCast<Boolean>()

    actual override fun configureBlocking(block: Boolean) {
        // Node.js 'net' module sockets are event-driven and non-blocking by default.
        if (block) {
            console.warn("ActualClientSocketChannel.configureBlocking(true) has no direct effect in Node.js.")
        }
    }
}

// --- TensorUtils Placeholder for JS ---
package borg.trikeshed.core.internal

import borg.trikeshed.core.Tensor
import node.buffer.Buffer // For Node.js Buffer

object TensorUtils {
    fun tensorToByteArray(tensor: Tensor<Byte>, offset: Int, length: Int): ByteArray {
        require(tensor.rank == 1) { "Input tensor must be rank 1." }
        require(offset >= 0 && length >= 0 && offset + length <= tensor.shape[0]) { "Invalid offset/length." }
        return ByteArray(length) { i -> tensor(intArrayOf(offset + i)) }
    }

    fun updateTensorFromByteArray(
        targetTensor: Tensor<Byte>,
        tensorOffset: Int,
        sourceByteArray: ByteArray,
        bytesToCopy: Int
    ) {
        // See comments in JVM/Native versions. This is a placeholder.
        // Assumes targetTensor is mutable or read operation returns a new Tensor.
        println("TensorUtils.updateTensorFromByteArray (JS): Read $bytesToCopy bytes into temp array. Tensor update logic pending.")
    }
}

// Node.js Buffer to ByteArray extension
fun Buffer.toByteArray(): ByteArray {
    return Uint8Array(this.buffer, this.byteOffset, this.length).toByteArray()
}

// ByteArray to Uint8Array extension (needed for nodeSocket.write)
fun ByteArray.toUint8Array(): Uint8Array {
    return Uint8Array(this.unsafeCast<ArrayBuffer>())
}
