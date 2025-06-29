package borg.trikeshed.reactor

import borg.trikeshed.nio.PlatformByteBuffer

/**
 * Represents a platform-agnostic client socket channel for TCP connections.
 * Actual implementations for JVM, Native, and JS/WASM are required.
 */
expect class ClientChannel {
    /**
     * Connects to the specified [host] and [port].
     * This is a suspending function that completes when the connection is established or fails.
     * @param host The hostname or IP address to connect to.
     * @param port The port number to connect to.
     * @throws RuntimeException if the connection fails (specific exceptions depend on platform actuals).
     */
    suspend fun connect(host: String, port: Int)

    /**
     * Reads a sequence of bytes from this channel into the given [buffer].
     * This is a suspending function that completes when some bytes have been read,
     * or the end-of-stream is reached, or an error occurs.
     * @param buffer The buffer into which bytes are to be transferred.
     * @return The number of bytes read, possibly zero, or -1 if the channel has reached end-of-stream.
     * @throws RuntimeException if an I/O error occurs (specific exceptions depend on platform actuals).
     */
    suspend fun read(buffer: PlatformByteBuffer): Int

    /**
     * Writes a sequence of bytes to this channel from the given [buffer].
     * This is a suspending function that completes when some bytes have been written or an error occurs.
     * @param buffer The buffer from which bytes are to be retrieved.
     * @return The number of bytes written, possibly zero.
     * @throws RuntimeException if an I/O error occurs (specific exceptions depend on platform actuals).
     */
    suspend fun write(buffer: PlatformByteBuffer): Int

    /**
     * Returns whether this channel is currently connected.
     * @return `true` if connected, `false` otherwise.
     */
    fun isConnected(): Boolean

    /**
     * Closes this channel.
     * If the channel is already closed then invoking this method has no effect.
     * Any pending read or write operations may be cancelled.
     */
    fun close()
}