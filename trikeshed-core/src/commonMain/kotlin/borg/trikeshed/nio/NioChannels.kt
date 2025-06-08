package borg.trikeshed.nio

import borg.trikeshed.core.Tensor // For Tensor<Byte> buffers
import evolution.io.SocketAddress // Reusing existing expect class for SocketAddress

/**
 * A selectable channel for network I/O operations.
 * This is a marker interface that network channels like [ServerSocketChannel]
 * and [ClientSocketChannel] can implement if they are to be used with a selector mechanism.
 */
interface NioSelectable {
    /**
     * Closes this channel.
     */
    fun close()

    /**
     * Returns whether this channel is open.
     */
    fun isOpen(): Boolean

    /**
     * Configures this channel to be blocking or non-blocking.
     * This method should ideally be called before registering with a selector.
     * @param block `true` to configure as blocking, `false` for non-blocking.
     * @throws IllegalStateException if called in an inappropriate state (e.g., already registered).
     */
    fun configureBlocking(block: Boolean)

    // Consider adding:
    // fun nativeHandle(): Any // To get the underlying platform-specific handle for selector registration
}

/**
 * Represents a server socket channel capable of listening for incoming TCP connections.
 */
expect interface ServerSocketChannel : NioSelectable {
    /**
     * Binds the channel's socket to a local address and configures it to listen for connections.
     * @param host The hostname or IP address to bind to. "0.0.0.0" or "::" for all interfaces.
     * @param port The port number to bind to. Use 0 for an ephemeral port.
     * @param backlog The maximum length of the queue of incoming connections. Default is 50.
     * @return The [SocketAddress] the channel is actually bound to (especially useful if an ephemeral port was requested).
     * @throws Exception if binding fails (e.g., address in use).
     */
    suspend fun bind(host: String, port: Int, backlog: Int = 50): SocketAddress

    /**
     * Accepts a new connection.
     * This is a suspending function that completes when a new connection is accepted or an error occurs.
     * @return A [ClientSocketChannel] for the new connection, or `null` if the server socket was closed
     *         or a non-recoverable error occurred during accept (though exceptions are preferred for errors).
     * @throws Exception if an I/O error occurs during accept.
     */
    suspend fun accept(): ClientSocketChannel?

    /**
     * Returns the local [SocketAddress] this channel is bound to.
     * @return The local socket address, or `null` if the channel is not bound.
     */
    fun localAddress(): SocketAddress?
}

/**
 * Represents a client socket channel for TCP network communication.
 */
expect interface ClientSocketChannel : NioSelectable {
    /**
     * Connects this channel's socket to a remote address.
     * For non-blocking channels, this method initiates the connection and might return `false`
     * if the connection is pending. Use [finishConnect] to complete the connection sequence.
     * For blocking channels, it waits until the connection is established or an error occurs.
     * @param host The hostname or IP address of the remote server.
     * @param port The port number of the remote server.
     * @return `true` if the connection is immediately established (typical for blocking mode or already connected),
     *         `false` if the connection operation is in progress (typical for non-blocking mode).
     * @throws Exception if an I/O error occurs.
     */
    suspend fun connect(host: String, port: Int): Boolean

    /**
     * Finishes the process of connecting a socket channel.
     * This method is used for channels in non-blocking mode after `connect` returns `false`.
     * It waits until the connection is established or an error occurs.
     * @return `true` if the connection is now established.
     * @throws Exception if an I/O error occurs during connection.
     */
    suspend fun finishConnect(): Boolean

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     * @param buffer The `Tensor<Byte>` (rank 1) to read data into.
     * @param offset The offset within the buffer at which to start writing data. Defaults to 0.
     * @param length The maximum number of bytes to read. Defaults to `buffer.totalSize - offset`.
     * @return The number of bytes read, possibly 0, or -1 if the channel has reached end-of-stream.
     * @throws Exception if an I/O error occurs.
     */
    suspend fun read(buffer: Tensor<Byte>, offset: Int = 0, length: Int = buffer.totalSize - offset): Int

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     * @param buffer The `Tensor<Byte>` (rank 1) containing data to be written.
     * @param offset The offset within the buffer from which to start reading data. Defaults to 0.
     * @param length The number of bytes to write. Defaults to `buffer.totalSize - offset`.
     * @return The number of bytes written, possibly 0.
     * @throws Exception if an I/O error occurs.
     */
    suspend fun write(buffer: Tensor<Byte>, offset: Int = 0, length: Int = buffer.totalSize - offset): Int

    /**
     * Returns whether this channel's socket is connected.
     */
    fun isConnected(): Boolean

    /**
     * Returns the remote [SocketAddress] this channel is connected to.
     * @return The remote socket address, or `null` if the channel is not connected.
     */
    fun remoteAddress(): SocketAddress?

    /**
     * Returns the local [SocketAddress] of this channel's socket.
     * @return The local socket address, or `null` if the channel's socket is not bound.
     */
    fun localAddress(): SocketAddress?
}
