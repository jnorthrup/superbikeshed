@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.uring

import borg.trikeshed.ccek.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import borg.trikeshed.lib.ByteBuffer
import borg.trikeshed.lib.Closeable
import kotlin.coroutines.CoroutineContext

/**
 * TrikeUring - A channelized, multiplatform facade for io_uring-style I/O.
 * 
 * This is the "beer goggles" interface that makes any platform look like io_uring.
 * On Darwin, it's backed by kqueue. On Linux, by real io_uring. On JVM, by NIO.
 * 
 * CCEK provides the orchestration layer for consistent behavior across platforms.
 */
interface TrikeUring : CoroutineScope, Closeable {
    /**
     * The channel for submitting operations to the ring.
     * Send your Sqe (Submission Queue Entry) here.
     */
    val submission: SendChannel<Sqe>
    
    /**
     * The flow of completed operations from the ring.
     * Collect your Cqe (Completion Queue Entry) from here.
     */
    val completion: Flow<Cqe>
    
    /**
     * The CCEK orchestrator managing this uring instance
     */
    val orchestrator: CCEKUringOrchestrator
    
    /**
     * Registers a set of buffers for potentially more efficient I/O,
     * simulating io_uring's registered buffers.
     */
    suspend fun registerBuffers(buffers: List<ByteBuffer>)
    
    /**
     * Registers a set of files for potentially more efficient I/O.
     */
    suspend fun registerFiles(fds: IntArray)
    
    /**
     * Submit a batch of operations efficiently
     */
    suspend fun submitBatch(operations: List<Sqe>)
    
    /**
     * Wait for at least n completions
     */
    suspend fun waitCompletions(n: Int): List<Cqe>
}

/**
 * Factory function to create the platform-specific implementation
 */
expect fun createTrikeUring(
    scope: CoroutineScope,
    config: UringConfig = UringConfig()
): TrikeUring

/**
 * Configuration for TrikeUring
 */
data class UringConfig(
    val ringSize: Int = 256,
    val sqeDepth: Int = 4096,
    val cqeDepth: Int = 8192,
    val enableSqPoll: Boolean = false,
    val sqPollIdleMs: Int = 1000,
    val ccekContext: CcekContext? = null
)

/**
 * Execute with TrikeUring context
 */
suspend fun <T> withTrikeUring(
    config: UringConfig = UringConfig(),
    block: suspend TrikeUring.() -> T
): T = coroutineScope {
    val uring = createTrikeUring(this, config)
    try {
        uring.block()
    } finally {
        uring.close()
    }
}