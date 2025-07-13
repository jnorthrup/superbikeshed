package com.trikeshed.uring

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import borg.trikeshed.lib.ByteBuffer
import borg.trikeshed.lib.Closeable

// Mock PosixError and SocketAddress for commonMain (or define them properly if they exist elsewhere)
// For now, we'll use simple interfaces/data classes to allow compilation.
data class PosixError(val errno: Int) {
    companion object {
        fun fromErrno(errno: Int) = PosixError(errno)
    }
}

interface SocketAddress

/**
 * A sealed interface representing a single operation to be submitted.
 * The `userData` is crucial for correlating completions back to submissions.
 */
sealed class Sqe(open val userData: Long)

// --- File System Operations ---
data class Read(
    val fd: Int,
    val buffer: ByteBuffer,
    val offset: ULong,
    override val userData: Long = nextId()
) : Sqe(userData)

data class Write(
    val fd: Int,
    val buffer: ByteBuffer,
    val offset: ULong,
    override val userData: Long = nextId()
) : Sqe(userData)

data class Fsync(
    val fd: Int,
    override val userData: Long = nextId()
) : Sqe(userData)

// --- Network Operations ---
data class Accept(
    val fd: Int,
    override val userData: Long = nextId()
) : Sqe(userData)

data class Connect(
    val fd: Int,
    val address: SocketAddress,
    override val userData: Long = nextId()
) : Sqe(userData)

data class Send(
    val fd: Int,
    val buffer: ByteBuffer,
    override val userData: Long = nextId()
) : Sqe(userData)

data class Receive(
    val fd: Int,
    val buffer: ByteBuffer,
    override val userData: Long = nextId()
) : Sqe(userData)

// --- Chaining / Linking ---
// This is the metaprogramming part that simulates IOSQE_IO_LINK.
// It holds a continuation lambda that produces the next Sqe.
data class LinkedSqe(
    val first: Sqe,
    val then: (Cqe) -> Sqe?, // The continuation
    override val userData: Long = first.userData
) : Sqe(userData)

// --- Helper for unique IDs ---
private var idCounter = 0L
fun nextId(): Long = idCounter++ // Simple atomic counter for testing/mocking

/**
 * A sealed interface representing the result of a completed operation.
 * It is identified by the `userData` from its corresponding Sqe.
 */
sealed class Cqe(open val userData: Long, open val result: Int) {
    val isSuccess: Boolean get() = result >= 0
    val isError: Boolean get() = result < 0
    val error: PosixError? get() = if (isError) PosixError.fromErrno(-result) else null
}

// --- Concrete CQE Types ---
class ReadResult(override val userData: Long, override val result: Int) : Cqe(userData, result)
class WriteResult(override val userData: Long, override val result: Int) : Cqe(userData, result)
class FsyncResult(override val userData: Long, override val result: Int) : Cqe(userData, result)
class AcceptResult(override val userData: Long, override val result: Int, val clientAddress: SocketAddress?) : Cqe(userData, result)
class ConnectResult(override val userData: Long, override val result: Int) : Cqe(userData, result)
// ... and so on for Send, Receive, etc.

/**
 * A channelized, multiplatform facade for io_uring-style I/O.
 */
interface TrikeUring : CoroutineScope, Closeable {
    /**
     * The channel for submitting operations to the ring.
     */
    val submission: SendChannel<Sqe>
    
    /**
     * The flow of completed operations from the ring.
     */
    val completion: Flow<Cqe>

    /**
     * Registers a set of buffers for potentially more efficient I/O,
     * simulating io_uring's registered buffers.
     */
    suspend fun registerBuffers(buffers: List<ByteBuffer>)
    
    /**
     * Registers a set of files for potentially more efficient I/O.
     */
    suspend fun registerFiles(fds: IntArray)
}

// Factory function to create the platform-specific implementation

