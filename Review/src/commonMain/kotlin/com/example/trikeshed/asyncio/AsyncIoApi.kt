package com.example.trikeshed.asyncio

/**
 * Represents an opaque platform-specific context for asynchronous I/O operations.
 * Instances are created by [AsyncIoService.createContext] and must be managed by the caller,
 * ensuring [AsyncIoService.destroyContext] is called to release resources.
 */
expect class AsyncIoContext

/**
 * Represents a single asynchronous I/O operation to be submitted to an [AsyncIoService].
 * This is a sealed class with specific implementations for different I/O types.
 */
sealed class IoOperation {
    /**
     * User-defined data associated with this operation. This value will be returned
     * in the corresponding [IoCompletion] object, allowing users to track operations.
     * If set to 0 by the user, the service might assign a unique temporary ID for internal tracking,
     * but the original 0 (or the service-assigned ID if input was 0) should be consistently
     * returned in the completion's userData.
     * It's recommended for users to assign unique non-zero values for robust tracking.
     */
    var userData: Long = 0L

    /** The file descriptor on which the operation is to be performed. */
    abstract val fd: Int

    /**
     * Represents a scatter read operation (readv).
     * @property fd The file descriptor to read from.
     * @property buffers An array of ByteArrays into which data will be read.
     * @property offset The file offset at which to start reading. If -1, reads from the current file position.
     *                  Note: Not all platforms or file types may support offset for socket-like FDs.
     */
    data class ReadV(
        override val fd: Int,
        val buffers: Array<ByteArray>,
        val offset: Long = -1L
    ) : IoOperation()

    /**
     * Represents a gather write operation (writev).
     * @property fd The file descriptor to write to.
     * @property buffers An array of ByteArrays from which data will be written.
     * @property offset The file offset at which to start writing. If -1, writes at the current file position.
     *                  Note: Not all platforms or file types may support offset for socket-like FDs.
     */
    data class WriteV(
        override val fd: Int,
        val buffers: Array<ByteArray>,
        val offset: Long = -1L
    ) : IoOperation()

    /**
     * Represents an fsync operation to synchronize a file's in-core state with storage.
     * @property fd The file descriptor to synchronize.
     * @property fsyncDataOnly If true, synchronize only file data (e.g., `fdatasync` on Linux).
     *                       If false, synchronize data and metadata (e.g., `fsync` on Linux).
     *                       Note: macOS platform implementation currently performs a full fsync (`fsync()`)
     *                       regardless of this flag's value due to differences in underlying system calls.
     */
    data class Fsync(
        override val fd: Int,
        val fsyncDataOnly: Boolean = false
    ) : IoOperation()

    /**
     * Represents a receive operation on a socket.
     * @property fd The socket file descriptor.
     * @property buffer The ByteArray into which data will be received.
     * @property flags Flags for the recv system call (e.g., MSG_WAITALL on Linux).
     */
    data class Recv(
        override val fd: Int,
        val buffer: ByteArray,
        val flags: Int = 0
    ) : IoOperation()

    /**
     * Represents a send operation on a socket.
     * @property fd The socket file descriptor.
     * @property buffer The ByteArray from which data will be sent.
     * @property flags Flags for the send system call (e.g., MSG_NOSIGNAL on Linux).
     */
    data class Send(
        override val fd: Int,
        val buffer: ByteArray,
        val flags: Int = 0
    ) : IoOperation()
}

/**
 * Represents the result of a completed asynchronous I/O operation.
 * @property userData The user-defined data that was passed with the corresponding [IoOperation].
 *                  If the original operation had `userData` set to 0, this will reflect the
 *                  value assigned by the service (if any) or 0.
 * @property result For read/write/send/recv operations, this is the number of bytes transferred.
 *                  For fsync, this is 0 on success.
 *                  On error for any operation, this will be a negative value, typically representing `-errno`.
 */
data class IoCompletion(
    val userData: Long,
    val result: Int
    // val flags: Int, // Optional: could hold completion flags from io_uring (e.g. IORING_CQE_F_MORE)
)

/**
 * Interface for a service that provides asynchronous I/O capabilities.
 * Implementations are platform-specific (e.g., using io_uring on Linux, kqueue on macOS).
 * Callers should generally create one service instance for the application lifetime or per major I/O handling scope.
 */
interface AsyncIoService {
    /**
     * Creates a new asynchronous I/O context. Each context typically maps to a separate
     * instance of the underlying OS mechanism (e.g., an io_uring ring or a kqueue instance).
     * Multiple contexts can exist but operations are generally not shared between them.
     * @param entries A hint for the desired capacity or number of concurrent operations
     *                the context should be optimized for (e.g., io_uring queue depth).
     *                The interpretation of this parameter is platform-specific.
     * @param flags Platform-specific flags for context creation (e.g., io_uring setup flags).
     * @return An [AsyncIoContext] instance for submitting operations.
     * @throws RuntimeException if context creation fails at the native level.
     */
    fun createContext(entries: Int, flags: Int = 0): AsyncIoContext

    /**
     * Submits a list of I/O operations to be performed asynchronously.
     * This call is non-blocking. Completions must be retrieved via [pollCompletions].
     *
     * The results of these operations are delivered via [pollCompletions]. It is crucial
     * that the ByteArrays used in operations (especially for reads/recvs) are kept valid
     * and unmodified until their corresponding completion is processed, as the native layer
     * will be writing directly to their memory. The service implementations handle pinning
     * of these buffers for native access.
     *
     * @param context The [AsyncIoContext] to submit operations to.
     * @param operations A list of [IoOperation] objects to be submitted.
     *                   The `userData` field in each operation should be set by the caller for tracking.
     *                   If `userData` is 0, the service might assign a temporary unique ID for internal
     *                   tracking, but the completion event will report the `userData` that was
     *                   present in the `IoOperation` when it was submitted (or the generated one if it was 0).
     * @return The number of operations successfully queued for submission to the underlying native layer.
     *         This can be less than `operations.size` if the internal submission queue is full.
     *         Returns a negative value (typically `-errno`) on a critical error during submission.
     */
    fun submitOperations(context: AsyncIoContext, operations: List<IoOperation>): Int

    /**
     * Polls for and retrieves completed I/O operations for a given context.
     * This method is non-blocking if `timeoutMillis` is 0 or if `minCompletions` is 0 and no events are immediately available.
     *
     * @param context The [AsyncIoContext] to poll for completions.
     * @param completions A mutable list that will be populated by this function with [IoCompletion] objects
     *                    for operations that have finished. The list is **not** cleared by this function;
     *                    newly completed items are added. Callers should clear it if they want only fresh results.
     * @param minCompletions The minimum number of completions to wait for if `timeoutMillis` is not 0.
     *                       If `timeoutMillis` is 0, this parameter is often ignored by implementations (becomes non-blocking peek).
     *                       If `timeoutMillis` is -1 (blocking), the call waits until at least this many operations complete.
     * @param timeoutMillis The maximum time to wait for completions, in milliseconds.
     *                      - If 0, performs a non-blocking check.
     *                      - If -1, blocks indefinitely until at least `minCompletions` are available (if `minCompletions` > 0),
     *                        or until at least one completion is available (if `minCompletions` is 0).
     *                      - If > 0, waits for up to the specified duration. If `minCompletions` are met before the timeout,
     *                        it may return sooner.
     * @return The number of completions added to the `completions` list. This can be 0 if a timeout occurs
     *         or if a non-blocking call finds no completions. Returns a negative value (typically `-errno`) on a critical polling error.
     */
    fun pollCompletions(
        context: AsyncIoContext,
        completions: MutableList<IoCompletion>,
        minCompletions: Int,
        timeoutMillis: Int
    ): Int

    /**
     * Destroys an I/O context and releases all associated native resources.
     * After a context is destroyed, it can no longer be used.
     * It is the caller's responsibility to ensure that all operations associated with this context
     * have completed (or are no longer needed) before destroying it. Failure to do so might lead
     * to resource leaks (e.g., pinned memory not being released) or crashes if completions
     * arrive for a destroyed context.
     * @param context The [AsyncIoContext] to destroy.
     */
    fun destroyContext(context: AsyncIoContext)
}

/**
 * Creates a platform-specific instance of [AsyncIoService].
 * This factory function is the entry point to obtaining an asynchronous I/O service.
 * @return A new instance of [AsyncIoService] suitable for the current operating platform.
 * @throws UnsupportedOperationException if no AsyncIoService implementation is available for the current platform.
 */
expect fun createAsyncIoService(): AsyncIoService
