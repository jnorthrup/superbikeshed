package trikeshed.uring

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

/**
 * Represents an opaque platform-specific context for asynchronous I/O operations.
 * Instances are created by [UringService.createContext] and must be managed by the caller,
 * ensuring [UringService.destroyContext] is called to release resources.
 */
expect class UringContext

/**
 * Main interface for io_uring-style asynchronous I/O operations.
 * Provides submission and completion queues as coroutine channels.
 */
interface TrikeUring : AutoCloseable {
    /**
     * The channel for submitting operations to the ring.
     */
    val submission: SendChannel<Sqe>
    
    /**
     * The flow of completed operations from the ring.
     */
    val completion: Flow<Cqe>
    
    /**
     * Registers a set of buffers for potentially more efficient I/O.
     * On Linux with io_uring, this enables zero-copy operations.
     */
    suspend fun registerBuffers(buffers: List<ByteArray>)
    
    /**
     * Registers a set of file descriptors for potentially more efficient I/O.
     * On Linux with io_uring, this reduces overhead for registered files.
     */
    suspend fun registerFiles(fds: IntArray)
    
    /**
     * Submits a batch of operations efficiently.
     * On platforms that support it, this submits all operations in a single syscall.
     */
    suspend fun submitBatch(operations: List<Sqe>)
}

/**
 * Factory function to create the platform-specific implementation.
 */
expect fun createTrikeUring(
    context: CoroutineContext,
    ringSize: Int = 256,
    flags: Int = 0
): TrikeUring

/**
 * Service interface for managing io_uring-style contexts.
 */
interface UringService {
    /**
     * Creates a new async I/O context.
     * @param entries The number of entries in the submission queue.
     * @param flags Platform-specific flags for context creation.
     * @return The created context.
     * @throws RuntimeException if context creation fails.
     */
    fun createContext(entries: Int = 256, flags: Int = 0): UringContext
    
    /**
     * Submits a batch of operations to the context.
     * @param context The context to submit operations to.
     * @param operations The list of operations to submit.
     * @return The number of operations successfully submitted.
     * @throws IllegalArgumentException if the context is invalid.
     */
    fun submitOperations(context: UringContext, operations: List<IoOperation>): Int
    
    /**
     * Polls for completed operations.
     * @param context The context to poll.
     * @param maxCompletions The maximum number of completions to retrieve.
     * @param timeoutMs The timeout in milliseconds (-1 for infinite, 0 for non-blocking).
     * @return A list of completed operations.
     */
    fun pollCompletions(context: UringContext, maxCompletions: Int = 16, timeoutMs: Long = 0): List<IoCompletion>
    
    /**
     * Destroys a context and releases associated resources.
     * @param context The context to destroy.
     * @throws IllegalArgumentException if the context is invalid.
     */
    fun destroyContext(context: UringContext)
}

/**
 * Creates the platform-specific UringService implementation.
 */
expect fun createUringService(): UringService