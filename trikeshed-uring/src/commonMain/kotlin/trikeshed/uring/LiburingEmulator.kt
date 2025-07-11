package trikeshed.uring

import trikeshed.lib.*

/**
 * Userspace emulator for liburing that works across all platforms.
 * Provides identical interface to liburing but using TrikeShed type aliases.
 */

// TrikeShed type aliases for collections
typealias BufferList = Indexed<ByteArray>
typealias FdList = Indexed<Int>
typealias IoVecList = Indexed<IoVec>

// io_uring structure equivalents
data class IoUring(
    val sq: SubmissionQueue,
    val cq: CompletionQueue,
    val flags: UInt = 0u,
    val features: UInt = 0u
)

data class SubmissionQueue(
    val ring_entries: UInt,
    val ring_mask: UInt,
    val khead: UInt,
    val ktail: UInt,
    val sqes: MutableList<IoUringSqe>
)

data class CompletionQueue(
    val ring_entries: UInt,
    val ring_mask: UInt,
    val khead: UInt,
    val ktail: UInt,
    val cqes: MutableList<IoUringCqe>
)

// Submission Queue Entry - identical to liburing
data class IoUringSqe(
    var opcode: UByte = 0u,
    var flags: UByte = 0u,
    var ioprio: UShort = 0u,
    var fd: Int = -1,
    var off: Long = 0,
    var addr: Long = 0,
    var len: UInt = 0u,
    var user_data: ULong = 0u,
    var buf_index: UShort = 0u,
    var personality: UShort = 0u,
    var splice_fd_in: Int = 0,
    var addr2: Long = 0,
    var addr3: Long = 0
)

// Completion Queue Entry - identical to liburing
data class IoUringCqe(
    val user_data: ULong,
    val res: Int,
    val flags: UInt
)

// io_uring opcodes - matching liburing constants
object IoUringOp {
    const val IORING_OP_NOP: UByte = 0u
    const val IORING_OP_READV: UByte = 1u
    const val IORING_OP_WRITEV: UByte = 2u
    const val IORING_OP_FSYNC: UByte = 3u
    const val IORING_OP_READ_FIXED: UByte = 4u
    const val IORING_OP_WRITE_FIXED: UByte = 5u
    const val IORING_OP_POLL_ADD: UByte = 6u
    const val IORING_OP_POLL_REMOVE: UByte = 7u
    const val IORING_OP_SYNC_FILE_RANGE: UByte = 8u
    const val IORING_OP_SENDMSG: UByte = 9u
    const val IORING_OP_RECVMSG: UByte = 10u
    const val IORING_OP_TIMEOUT: UByte = 11u
    const val IORING_OP_TIMEOUT_REMOVE: UByte = 12u
    const val IORING_OP_ACCEPT: UByte = 13u
    const val IORING_OP_ASYNC_CANCEL: UByte = 14u
    const val IORING_OP_LINK_TIMEOUT: UByte = 15u
    const val IORING_OP_CONNECT: UByte = 16u
    const val IORING_OP_FALLOCATE: UByte = 17u
    const val IORING_OP_OPENAT: UByte = 18u
    const val IORING_OP_CLOSE: UByte = 19u
    const val IORING_OP_FILES_UPDATE: UByte = 20u
    const val IORING_OP_STATX: UByte = 21u
    const val IORING_OP_READ: UByte = 22u
    const val IORING_OP_WRITE: UByte = 23u
    const val IORING_OP_FADVISE: UByte = 24u
    const val IORING_OP_MADVISE: UByte = 25u
    const val IORING_OP_SEND: UByte = 26u
    const val IORING_OP_RECV: UByte = 27u
    const val IORING_OP_OPENAT2: UByte = 28u
    const val IORING_OP_EPOLL_CTL: UByte = 29u
    const val IORING_OP_SPLICE: UByte = 30u
    const val IORING_OP_PROVIDE_BUFFERS: UByte = 31u
    const val IORING_OP_REMOVE_BUFFERS: UByte = 32u
}

// SQE flags
object IoUringSqeFlags {
    const val IOSQE_FIXED_FILE: UByte = 1u
    const val IOSQE_IO_DRAIN: UByte = 2u
    const val IOSQE_IO_LINK: UByte = 4u
    const val IOSQE_IO_HARDLINK: UByte = 8u
    const val IOSQE_ASYNC: UByte = 16u
    const val IOSQE_BUFFER_SELECT: UByte = 32u
}

// Setup flags
object IoUringSetupFlags {
    const val IORING_SETUP_IOPOLL: UInt = 1u
    const val IORING_SETUP_SQPOLL: UInt = 2u
    const val IORING_SETUP_SQ_AFF: UInt = 4u
    const val IORING_SETUP_CQSIZE: UInt = 8u
    const val IORING_SETUP_CLAMP: UInt = 16u
    const val IORING_SETUP_ATTACH_WQ: UInt = 32u
    const val IORING_SETUP_R_DISABLED: UInt = 64u
}

// Enter flags
object IoUringEnterFlags {
    const val IORING_ENTER_GETEVENTS: UInt = 1u
    const val IORING_ENTER_SQ_WAKEUP: UInt = 2u
    const val IORING_ENTER_SQ_WAIT: UInt = 4u
    const val IORING_ENTER_EXT_ARG: UInt = 8u
}

// Fsync flags
object IoUringFsyncFlags {
    const val IORING_FSYNC_DATASYNC: UInt = 1u
}

// io_uring params - using TrikeShed types
data class IoUringParams(
    var sq_entries: UInt = 0u,
    var cq_entries: UInt = 0u,
    var flags: UInt = 0u,
    var sq_thread_cpu: UInt = 0u,
    var sq_thread_idle: UInt = 0u,
    var features: UInt = 0u,
    var wq_fd: UInt = 0u,
    var resv: Indexed<UInt> = listOf(0u, 0u, 0u),
    val sq_off: IoSqringOffsets = IoSqringOffsets(),
    val cq_off: IoCqringOffsets = IoCqringOffsets()
)

data class IoSqringOffsets(
    var head: UInt = 0u,
    var tail: UInt = 0u,
    var ring_mask: UInt = 0u,
    var ring_entries: UInt = 0u,
    var flags: UInt = 0u,
    var dropped: UInt = 0u,
    var array: UInt = 0u,
    var resv1: UInt = 0u,
    var resv2: ULong = 0u
)

data class IoCqringOffsets(
    var head: UInt = 0u,
    var tail: UInt = 0u,
    var ring_mask: UInt = 0u,
    var ring_entries: UInt = 0u,
    var overflow: UInt = 0u,
    var cqes: UInt = 0u,
    var flags: UInt = 0u,
    var resv1: UInt = 0u,
    var resv2: ULong = 0u
)

// iovec structure using TrikeShed types
data class IoVec(
    val iov_base: ByteArray,
    val iov_len: ULong
)

/**
 * Main liburing-compatible API functions
 */
interface LiburingEmulator {
    // Queue setup and teardown
    fun io_uring_queue_init(entries: UInt, ring: IoUring, flags: UInt): Int
    fun io_uring_queue_init_params(entries: UInt, ring: IoUring, p: IoUringParams): Int
    fun io_uring_queue_exit(ring: IoUring)
    
    // Getting SQEs
    fun io_uring_get_sqe(ring: IoUring): IoUringSqe?
    
    // Submission
    fun io_uring_submit(ring: IoUring): Int
    fun io_uring_submit_and_wait(ring: IoUring, wait_nr: UInt): Int
    fun io_uring_submit_and_wait_timeout(
        ring: IoUring,
        cqe_ptr: Join<IoUringCqe?, Int>,
        wait_nr: UInt,
        ts: TimeSpec?,
        sigmask: SigSet?
    ): Int
    
    // Waiting for completions
    fun io_uring_wait_cqe(ring: IoUring, cqe_ptr: Join<IoUringCqe?, Int>): Int
    fun io_uring_wait_cqe_timeout(
        ring: IoUring,
        cqe_ptr: Join<IoUringCqe?, Int>,
        ts: TimeSpec?
    ): Int
    fun io_uring_peek_cqe(ring: IoUring, cqe_ptr: Join<IoUringCqe?, Int>): Int
    fun io_uring_wait_cqes(
        ring: IoUring,
        cqe_ptr: Join<IoUringCqe?, Int>,
        wait_nr: UInt,
        ts: TimeSpec?,
        sigmask: SigSet?
    ): Int
    
    // Marking CQE as seen
    fun io_uring_cqe_seen(ring: IoUring, cqe: IoUringCqe)
    
    // Advanced CQE iteration using TrikeShed Series
    fun io_uring_for_each_cqe(ring: IoUring): Series<IoUringCqe>
    
    // SQE data setters
    fun io_uring_sqe_set_data(sqe: IoUringSqe, data: Any)
    fun io_uring_cqe_get_data(cqe: IoUringCqe): Any?
    fun io_uring_sqe_set_data64(sqe: IoUringSqe, data: ULong)
    fun io_uring_cqe_get_data64(cqe: IoUringCqe): ULong
    
    // SQE prep helpers - using TrikeShed types
    fun io_uring_prep_readv(sqe: IoUringSqe, fd: Int, iovecs: IoVecList, nr_vecs: UInt, offset: Long)
    fun io_uring_prep_read(sqe: IoUringSqe, fd: Int, buf: ByteArray, nbytes: UInt, offset: Long)
    fun io_uring_prep_writev(sqe: IoUringSqe, fd: Int, iovecs: IoVecList, nr_vecs: UInt, offset: Long)
    fun io_uring_prep_write(sqe: IoUringSqe, fd: Int, buf: ByteArray, nbytes: UInt, offset: Long)
    fun io_uring_prep_fsync(sqe: IoUringSqe, fd: Int, fsync_flags: UInt)
    fun io_uring_prep_nop(sqe: IoUringSqe)
    fun io_uring_prep_accept(sqe: IoUringSqe, fd: Int, addr: SocketAddress?, addrlen: UInt, flags: Int)
    fun io_uring_prep_connect(sqe: IoUringSqe, fd: Int, addr: SocketAddress, addrlen: UInt)
    fun io_uring_prep_send(sqe: IoUringSqe, sockfd: Int, buf: ByteArray, len: ULong, flags: Int)
    fun io_uring_prep_recv(sqe: IoUringSqe, sockfd: Int, buf: ByteArray, len: ULong, flags: Int)
    fun io_uring_prep_close(sqe: IoUringSqe, fd: Int)
    
    // Buffer and file registration - using TrikeShed Indexed
    fun io_uring_register_buffers(ring: IoUring, iovecs: IoVecList, nr_iovecs: UInt): Int
    fun io_uring_unregister_buffers(ring: IoUring): Int
    fun io_uring_register_files(ring: IoUring, files: FdList, nr_files: UInt): Int
    fun io_uring_unregister_files(ring: IoUring): Int
    
    // SQE flags helpers
    fun io_uring_sqe_set_flags(sqe: IoUringSqe, flags: UInt)
    fun io_uring_prep_link(sqe: IoUringSqe)
    fun io_uring_prep_hardlink(sqe: IoUringSqe)
}

// Platform-specific types
expect class TimeSpec
expect class SigSet

/**
 * Create platform-specific liburing emulator
 */
expect fun createLiburingEmulator(): LiburingEmulator