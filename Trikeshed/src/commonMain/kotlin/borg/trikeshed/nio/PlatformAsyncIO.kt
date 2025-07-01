package borg.trikeshed.nio

import borg.trikeshed.lib.ByteIndexedBuffer

/**
 * Expect interface for platform-agnostic asynchronous I/O operations.
 * This abstracts operations like reading from and writing to network sockets or file handles.
 */
expect interface PlatformAsyncIO {
    /**
     * Reads data asynchronously from the underlying I/O source.
     * @param buffer The buffer to read data into.
     * @return The number of bytes read, or -1 if the end of the stream has been reached.
     */
    suspend fun read(buffer: ByteIndexedBuffer): Int

    /**
     * Writes data asynchronously to the underlying I/O sink.
     * @param buffer The buffer containing data to write.
     * @return The number of bytes written.
     */
    suspend fun write(buffer: ByteIndexedBuffer): Int

    /**
     * Accepts a new incoming connection asynchronously.
     * @return A new PlatformAsyncIO instance representing the accepted connection.
     */
    suspend fun accept(): PlatformAsyncIO

    /**
     * Connects asynchronously to a remote address.
     * @param address The remote address to connect to.
     */
    suspend fun connect(address: PlatformInetSocketAddress)

    /**
     * Closes the I/O source/sink.
     */
    fun close()

    /**
     * Checks if the I/O source/sink is open.
     */
    val isOpen: Boolean
}
