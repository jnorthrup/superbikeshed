@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.uring

import borg.trikeshed.ccek.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Submission Queue Entry (SQE) - CCEK-aware operations
 * 
 * These are the operations you submit to the uring.
 * Each carries CCEK context for orchestration.
 */
sealed class Sqe(
    val userData: Long,
    val ccekContext: CcekContext? = null
) {
    // Unique ID generator
    companion object {
        private val idCounter = atomic(0L)
        fun nextId(): Long = idCounter.incrementAndGet()
    }
}

// --- File System Operations ---
data class Read(
    val fd: Int,
    val buffer: ByteBuffer,
    val offset: ULong = 0u,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

data class Write(
    val fd: Int,
    val buffer: ByteBuffer,
    val offset: ULong = 0u,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

data class Fsync(
    val fd: Int,
    val flags: Int = 0,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

data class Close(
    val fd: Int,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

// --- Network Operations ---
data class Accept(
    val fd: Int,
    val flags: Int = 0,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

data class Connect(
    val fd: Int,
    val address: SocketAddress,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

data class Send(
    val fd: Int,
    val buffer: ByteBuffer,
    val flags: Int = 0,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

data class Receive(
    val fd: Int,
    val buffer: ByteBuffer,
    val flags: Int = 0,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

// --- Chaining / Linking ---
/**
 * LinkedSqe - Represents chained operations (IOSQE_IO_LINK)
 * The continuation lambda produces the next operation based on the result.
 */
data class LinkedSqe(
    val first: Sqe,
    val then: suspend (Cqe) -> Sqe?, // Continuation with CCEK context
    override val userData: Long = first.userData,
    override val ccekContext: CcekContext? = first.ccekContext
) : Sqe(userData, ccekContext)

// --- Advanced Operations ---
data class Timeout(
    val timeoutMs: Long,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

data class Cancel(
    val targetId: Long,
    override val userData: Long = nextId(),
    override val ccekContext: CcekContext? = null
) : Sqe(userData, ccekContext)

/**
 * Completion Queue Entry (CQE) - CCEK-aware results
 * 
 * These are the results you receive from completed operations.
 */
sealed class Cqe(
    val userData: Long,
    val result: Int,
    val ccekContext: CcekContext? = null
) {
    val isSuccess: Boolean get() = result >= 0
    val isError: Boolean get() = result < 0
    val error: UringError? get() = if (isError) UringError.fromErrno(-result) else null
}

// --- Concrete CQE Types ---
class ReadResult(
    userData: Long, 
    result: Int,
    val bytesRead: Int = if (result >= 0) result else 0,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class WriteResult(
    userData: Long, 
    result: Int,
    val bytesWritten: Int = if (result >= 0) result else 0,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class FsyncResult(
    userData: Long, 
    result: Int,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class AcceptResult(
    userData: Long, 
    result: Int,
    val clientFd: Int = if (result >= 0) result else -1,
    val clientAddress: SocketAddress? = null,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class ConnectResult(
    userData: Long, 
    result: Int,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class SendResult(
    userData: Long, 
    result: Int,
    val bytesSent: Int = if (result >= 0) result else 0,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class ReceiveResult(
    userData: Long, 
    result: Int,
    val bytesReceived: Int = if (result >= 0) result else 0,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class TimeoutResult(
    userData: Long, 
    result: Int,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

class CancelResult(
    userData: Long, 
    result: Int,
    val cancelledCount: Int = if (result >= 0) result else 0,
    ccekContext: CcekContext? = null
) : Cqe(userData, result, ccekContext)

/**
 * Socket address abstraction
 */
expect class SocketAddress {
    val host: String
    val port: Int
}

/**
 * Error handling for uring operations
 */
enum class UringError(val errno: Int, val message: String) {
    EAGAIN(11, "Resource temporarily unavailable"),
    EINVAL(22, "Invalid argument"),
    ENOENT(2, "No such file or directory"),
    EBADF(9, "Bad file descriptor"),
    ENOMEM(12, "Out of memory"),
    EBUSY(16, "Device or resource busy"),
    EINTR(4, "Interrupted system call"),
    EIO(5, "I/O error"),
    EPIPE(32, "Broken pipe"),
    ECONNRESET(104, "Connection reset by peer"),
    ETIMEDOUT(110, "Connection timed out");
    
    companion object {
        fun fromErrno(errno: Int): UringError? = values().find { it.errno == errno }
    }
}

/**
 * Atomic counter for thread-safe ID generation
 */
expect class atomic<T>(value: T) {
    fun get(): T
    fun set(value: T)
    fun incrementAndGet(): T
}

/**
 * DSL for building linked operations with CCEK context
 */
class LinkedSqeBuilder(
    private val first: Sqe,
    private val ccekContext: CcekContext? = null
) {
    private val continuations = mutableListOf<suspend (Cqe) -> Sqe?>()
    
    fun then(block: suspend (Cqe) -> Sqe?): LinkedSqeBuilder {
        continuations.add(block)
        return this
    }
    
    fun thenIf(condition: (Cqe) -> Boolean, block: suspend (Cqe) -> Sqe?): LinkedSqeBuilder {
        continuations.add { cqe ->
            if (condition(cqe)) block(cqe) else null
        }
        return this
    }
    
    fun build(): LinkedSqe {
        return LinkedSqe(
            first = first,
            then = { cqe ->
                continuations.fold(null as Sqe?) { _, cont -> cont(cqe) }
            },
            ccekContext = ccekContext
        )
    }
}

/**
 * Extension function for easy chaining
 */
fun Sqe.chain(ccekContext: CcekContext? = null): LinkedSqeBuilder = 
    LinkedSqeBuilder(this, ccekContext)