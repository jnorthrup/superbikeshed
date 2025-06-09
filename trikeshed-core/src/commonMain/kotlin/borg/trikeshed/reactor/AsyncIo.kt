package borg.trikeshed.reactor

import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.nio.ByteBuffer // Assuming this is your common ByteBuffer expect interface
import kotlin.coroutines.CoroutineContext

/**
 * An opaque handle representing a submitted asynchronous I/O operation.
 * Used to correlate completion events back to their original requests.
 */
expect class AsyncOperationHandle

/**
 * Represents a completed asynchronous I/O operation.
 *
 * @property handle The handle of the operation that completed.
 * @property attachment The user-defined data associated with the operation when it was submitted.
 * @property result For read/write operations, the number of bytes transferred. For accept, the new client FD. For connect, often 0 on success.
 *                  Negative values typically indicate an error specific to the operation itself before OS errors.
 * @property errorCode A platform-specific error code (e.g., errno from POSIX) if the operation failed at the OS level. Null on success.
 */
data class AsyncCompletionEvent(
    val handle: AsyncOperationHandle,
    val attachment: Any?,
    val result: Int,
    val errorCode: Int? // POSIX errno or equivalent
)

/**
 * Defines the contract for a platform-agnostic asynchronous I/O engine,
 * inspired by completion-queue models like io_uring and kqueue.
 * This engine allows submitting various I/O operations and polling for their completions.
 */
expect class AsyncIoEngine() : CoroutineContext.Element {

    /** The key for [AsyncIoEngine] in a [CoroutineContext]. */
    companion object Key : CoroutineContext.Key<AsyncIoEngine>

    override val key: CoroutineContext.Key<*> // Implements CoroutineContext.Element

    /**
     * Prepares a file descriptor (or equivalent platform handle) for use with this engine.
     * For some backends like io_uring, this might involve registering the FD.
     * @param fd The platform-specific file descriptor (Int for POSIX).
     */
    fun registerDescriptor(fd: Int)

    /**
     * Removes a file descriptor from the engine's management.
     * @param fd The platform-specific file descriptor.
     */
    fun unregisterDescriptor(fd: Int)

    /**
     * Submits an asynchronous read operation.
     *
     * @param fd The file descriptor to read from.
     * @param buffer The ByteBuffer to read data into. Its position and limit determine where data is read and how much.
     * @param fileOffset For seekable files, the offset at which to read. Use -1 for current file position or for non-seekable FDs like sockets.
     * @param attachment Optional user-defined data to associate with this operation.
     * @return An [AsyncOperationHandle] to track this specific operation.
     * @throws IllegalStateException if the engine is closed or submission fails.
     */
    fun submitRead(
        fd: Int,
        buffer: ByteBuffer,
        fileOffset: Long = -1L, // -1 for current position / not applicable
        attachment: Any? = null
    ): AsyncOperationHandle

    /**
     * Submits an asynchronous write operation.
     *
     * @param fd The file descriptor to write to.
     * @param buffer The ByteBuffer containing data to write. Its position and limit determine what data is written.
     * @param fileOffset For seekable files, the offset at which to write. Use -1 for current file position or for non-seekable FDs.
     * @param attachment Optional user-defined data to associate with this operation.
     * @return An [AsyncOperationHandle] to track this specific operation.
     * @throws IllegalStateException if the engine is closed or submission fails.
     */
    fun submitWrite(
        fd: Int,
        buffer: ByteBuffer,
        fileOffset: Long = -1L, // -1 for current position / not applicable
        attachment: Any? = null
    ): AsyncOperationHandle

    /**
     * Submits an asynchronous accept operation for a listening server socket.
     *
     * @param serverFd The file descriptor of the listening server socket.
     * @param attachment Optional user-defined data to associate with this operation.
     * @return An [AsyncOperationHandle] to track this specific operation. The `result` in its [AsyncCompletionEvent] will be the new client FD.
     * @throws IllegalStateException if the engine is closed or submission fails.
     */
    fun submitAccept(serverFd: Int, attachment: Any? = null): AsyncOperationHandle

    /**
     * Submits an asynchronous connect operation for a client socket.
     *
     * @param clientFd The file descriptor of the client socket.
     * @param remoteAddress The [NetworkAddress] to connect to.
     * @param attachment Optional user-defined data to associate with this operation.
     * @return An [AsyncOperationHandle] to track this specific operation.
     * @throws IllegalStateException if the engine is closed or submission fails.
     */
    fun submitConnect(clientFd: Int, remoteAddress: NetworkAddress, attachment: Any? = null): AsyncOperationHandle

    /**
     * Submits a request to cancel a previously submitted operation.
     * Note: Cancellation is best-effort and may not always succeed if the operation is already in progress or completed.
     *
     * @param handle The [AsyncOperationHandle] of the operation to cancel.
     * @param attachment Optional user-defined data for this cancellation request (can be different from the original op's attachment).
     * @return An [AsyncOperationHandle] for the cancellation operation itself. Its completion indicates the cancellation attempt finished.
     * @throws IllegalStateException if the engine is closed or submission fails.
     */
    fun submitCancel(handleToCancel: AsyncOperationHandle, attachment: Any? = null): AsyncOperationHandle

    /**
     * Polls for completed I/O operations. This function may suspend if `timeoutMillis` is positive
     * and no events are immediately available, or block if `timeoutMillis` is -1 (infinite).
     *
     * @param maxEvents The maximum number of completion events to retrieve in one call.
     * @param timeoutMillis The maximum time to wait for completions:
     *                      - 0: Non-blocking, returns immediately with any available completions.
     *                      - >0: Waits up to this many milliseconds.
     *                      - -1: Waits indefinitely until at least one event completes (or an error occurs).
     * @return A list of [AsyncCompletionEvent]s that have completed. Empty if timeout occurs or no events.
     * @throws IllegalStateException if the engine is closed.
     * @throws InterruptedException if the waiting thread is interrupted (platform-dependent).
     */
    suspend fun pollCompletions(maxEvents: Int, timeoutMillis: Long): List<AsyncCompletionEvent>

    /**
     * Closes the asynchronous I/O engine and releases any associated resources.
     * Attempts to cancel any pending operations.
     */
    fun close()
}
