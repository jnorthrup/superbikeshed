package trikeshed.uring

/**
 * Base interface for all io_uring operations.
 */
sealed interface IoOperation {
    /**
     * User-defined data associated with this operation.
     * This value will be returned in the corresponding completion.
     */
    val userData: Long
    
    /** The file descriptor on which the operation is to be performed. */
    val fd: Int
}

/**
 * Submission Queue Entry (SQE) - represents operations to be submitted.
 * This is the Kotlin equivalent of io_uring's sqe structure.
 */
sealed class Sqe(override val userData: Long) : IoOperation {
    abstract override val fd: Int
}

// File I/O Operations

/**
 * Represents a scatter read operation (readv).
 * @property fd The file descriptor to read from.
 * @property buffers An array of ByteArrays into which data will be read.
 * @property offset The file offset at which to start reading. If -1, reads from current position.
 */
data class ReadV(
    override val fd: Int,
    val buffers: Array<ByteArray>,
    val offset: Long = -1L,
    override val userData: Long = nextId()
) : Sqe(userData) {
    override fun equals(other: Any?) = other is ReadV && fd == other.fd && userData == other.userData
    override fun hashCode() = fd.hashCode() * 31 + userData.hashCode()
}

/**
 * Represents a single buffer read operation.
 */
data class Read(
    override val fd: Int,
    val buffer: ByteArray,
    val offset: Long = -1L,
    override val userData: Long = nextId()
) : Sqe(userData)

/**
 * Represents a gather write operation (writev).
 * @property fd The file descriptor to write to.
 * @property buffers An array of ByteArrays from which data will be written.
 * @property offset The file offset at which to start writing. If -1, writes at current position.
 */
data class WriteV(
    override val fd: Int,
    val buffers: Array<ByteArray>,
    val offset: Long = -1L,
    override val userData: Long = nextId()
) : Sqe(userData) {
    override fun equals(other: Any?) = other is WriteV && fd == other.fd && userData == other.userData
    override fun hashCode() = fd.hashCode() * 31 + userData.hashCode()
}

/**
 * Represents a single buffer write operation.
 */
data class Write(
    override val fd: Int,
    val buffer: ByteArray,
    val offset: Long = -1L,
    override val userData: Long = nextId()
) : Sqe(userData)

/**
 * Represents an fsync operation to synchronize a file's state with storage.
 */
data class Fsync(
    override val fd: Int,
    val fsyncDataOnly: Boolean = false,
    override val userData: Long = nextId()
) : Sqe(userData)

// Network Operations

/**
 * Represents an accept operation on a listening socket.
 */
data class Accept(
    override val fd: Int,
    override val userData: Long = nextId()
) : Sqe(userData)

/**
 * Represents a connect operation to establish a connection.
 */
data class Connect(
    override val fd: Int,
    val address: SocketAddress,
    override val userData: Long = nextId()
) : Sqe(userData)

/**
 * Represents a send operation on a socket.
 */
data class Send(
    override val fd: Int,
    val buffer: ByteArray,
    val flags: Int = 0,
    override val userData: Long = nextId()
) : Sqe(userData)

/**
 * Represents a receive operation on a socket.
 */
data class Receive(
    override val fd: Int,
    val buffer: ByteArray,
    val flags: Int = 0,
    override val userData: Long = nextId()
) : Sqe(userData)

/**
 * Represents a close operation for a file descriptor.
 */
data class Close(
    override val fd: Int,
    override val userData: Long = nextId()
) : Sqe(userData)

// Advanced Operations

/**
 * Represents a linked sequence of operations.
 * This simulates io_uring's IOSQE_IO_LINK flag.
 */
data class LinkedSqe(
    val first: Sqe,
    val then: (Cqe) -> Sqe?,
    override val userData: Long = first.userData
) : Sqe(userData) {
    override val fd: Int get() = first.fd
}

/**
 * Builder for creating linked operations.
 */
class SqeChainBuilder(private val first: Sqe) {
    private var chain: LinkedSqe? = null
    
    fun then(continuation: (Cqe) -> Sqe?): SqeChainBuilder {
        chain = if (chain == null) {
            LinkedSqe(first, continuation)
        } else {
            LinkedSqe(chain!!, continuation)
        }
        return this
    }
    
    fun build(): Sqe = chain ?: first
}

/**
 * Extension function to start building a chain of operations.
 */
fun Sqe.chain() = SqeChainBuilder(this)

// Helper for unique IDs
private var idCounter = 0L
private fun nextId(): Long = ++idCounter

/**
 * Platform-specific socket address representation.
 */
expect class SocketAddress