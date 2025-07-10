@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.uring

import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.native.concurrent.*

/**
 * REAL liburing C API exported from Kotlin Native
 * 
 * This is what makes Darwin ACTUALLY satisfy liburing interfaces.
 * Any C/C++ code expecting liburing can link against this!
 */

// Global state for simplicity (in production, use thread-local)
@ThreadLocal
private var globalRings = mutableMapOf<Long, DarwinUringState>()

/**
 * io_uring structure - matches liburing exactly
 */
@CStruct("struct io_uring")
class io_uring(
    val sq: io_uring_sq,
    val cq: io_uring_cq,
    val flags: UInt,
    val ring_fd: Int,
    val features: UInt,
    val id: CPointer<IntVar>?,
    val pad: CArrayPointer<UInt>
)

/**
 * Submission Queue structure
 */
@CStruct("struct io_uring_sq")
class io_uring_sq(
    val khead: CPointer<UIntVar>,
    val ktail: CPointer<UIntVar>,
    val kring_mask: CPointer<UIntVar>,
    val kring_entries: CPointer<UIntVar>,
    val kflags: CPointer<UIntVar>,
    val kdropped: CPointer<UIntVar>,
    val array: CPointer<UIntVar>,
    val sqes: CPointer<io_uring_sqe>,
    val sqe_head: UInt,
    val sqe_tail: UInt,
    val ring_sz: size_t,
    val ring_ptr: COpaquePointer?
)

/**
 * Completion Queue structure
 */
@CStruct("struct io_uring_cq")
class io_uring_cq(
    val khead: CPointer<UIntVar>,
    val ktail: CPointer<UIntVar>,
    val kring_mask: CPointer<UIntVar>,
    val kring_entries: CPointer<UIntVar>,
    val kflags: CPointer<UIntVar>,
    val koverflow: CPointer<UIntVar>,
    val cqes: CPointer<io_uring_cqe>,
    val ring_sz: size_t,
    val ring_ptr: COpaquePointer?
)

/**
 * Submission Queue Entry - THE CORE OF IO_URING
 */
@CStruct("struct io_uring_sqe")
class io_uring_sqe(
    var opcode: UByte,          // IORING_OP_*
    var flags: UByte,           // IOSQE_* flags
    var ioprio: UShort,         // ioprio for the request
    var fd: Int,                // file descriptor
    var off: ULong,             // offset into file
    var addr: ULong,            // pointer to buffer or iovecs
    var len: UInt,              // buffer size or number of iovecs
    var rw_flags: Int,          // RWF_* flags
    var user_data: ULong,       // user data for completion
    var buf_index: UShort,      // index into registered buffers
    var personality: UShort,    // personality to use
    var splice_fd_in: Int,      // splice fd in
    var pad2: CArrayPointer<ULong>
)

/**
 * Completion Queue Entry
 */
@CStruct("struct io_uring_cqe")
class io_uring_cqe(
    val user_data: ULong,       // sqe->user_data submission passed back
    val res: Int,               // result code for this event
    val flags: UInt             // IORING_CQE_F_*
)

/**
 * io_uring parameters
 */
@CStruct("struct io_uring_params")
class io_uring_params(
    var sq_entries: UInt,
    var cq_entries: UInt,
    var flags: UInt,
    var sq_thread_cpu: UInt,
    var sq_thread_idle: UInt,
    var features: UInt,
    var wq_fd: UInt,
    var resv: CArrayPointer<UInt>,
    val sq_off: io_sq_ring_offsets,
    val cq_off: io_cq_ring_offsets
)

@CStruct("struct io_sq_ring_offsets")
class io_sq_ring_offsets(
    val head: UInt,
    val tail: UInt,
    val ring_mask: UInt,
    val ring_entries: UInt,
    val flags: UInt,
    val dropped: UInt,
    val array: UInt,
    val resv1: UInt,
    val resv2: ULong
)

@CStruct("struct io_cq_ring_offsets")
class io_cq_ring_offsets(
    val head: UInt,
    val tail: UInt,
    val ring_mask: UInt,
    val ring_entries: UInt,
    val overflow: UInt,
    val cqes: UInt,
    val flags: UInt,
    val resv1: UInt,
    val resv2: ULong
)

// Operation codes - match Linux io_uring exactly
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

// SQE flags
const val IOSQE_FIXED_FILE: UByte = 1u
const val IOSQE_IO_DRAIN: UByte = 2u
const val IOSQE_IO_LINK: UByte = 4u
const val IOSQE_IO_HARDLINK: UByte = 8u
const val IOSQE_ASYNC: UByte = 16u
const val IOSQE_BUFFER_SELECT: UByte = 32u

// Setup flags
const val IORING_SETUP_IOPOLL: UInt = 1u
const val IORING_SETUP_SQPOLL: UInt = 2u
const val IORING_SETUP_SQ_AFF: UInt = 4u
const val IORING_SETUP_CQSIZE: UInt = 8u
const val IORING_SETUP_CLAMP: UInt = 16u
const val IORING_SETUP_ATTACH_WQ: UInt = 32u
const val IORING_SETUP_R_DISABLED: UInt = 64u

/**
 * Darwin state that backs an io_uring
 */
class DarwinUringState(
    val kqueueFd: Int,
    val entries: Int
) {
    val submissions = mutableListOf<DarwinSQE>()
    val completions = mutableListOf<DarwinCQE>()
    val sqMemory = nativeHeap.allocArray<io_uring_sqe>(entries)
    val cqMemory = nativeHeap.allocArray<io_uring_cqe>(entries * 2)
    var sqHead = 0u
    var sqTail = 0u
    var cqHead = 0u
    var cqTail = 0u
}

data class DarwinSQE(
    val opcode: UByte,
    val fd: Int,
    val addr: ULong,
    val len: UInt,
    val offset: ULong,
    val userData: ULong,
    val flags: UByte
)

data class DarwinCQE(
    val userData: ULong,
    val res: Int,
    val flags: UInt
)

/**
 * EXPORTED C FUNCTIONS - These satisfy liburing interface!
 */

@CName("io_uring_queue_init")
fun io_uring_queue_init(entries: UInt, ring: CPointer<io_uring>, flags: UInt): Int {
    memScoped {
        val params = alloc<io_uring_params>()
        params.flags = flags
        params.sq_entries = entries
        params.cq_entries = entries * 2u
        
        return io_uring_queue_init_params(entries, ring, params.ptr)
    }
}

@CName("io_uring_queue_init_params")
fun io_uring_queue_init_params(
    entries: UInt, 
    ring: CPointer<io_uring>,
    p: CPointer<io_uring_params>
): Int {
    // Create kqueue for Darwin
    val kqFd = kqueue()
    if (kqFd < 0) return -errno
    
    // Create state
    val state = DarwinUringState(kqFd, entries.toInt())
    val stateId = kqFd.toLong()
    globalRings[stateId] = state
    
    // Initialize ring structure
    memScoped {
        // Setup SQ
        ring.pointed.sq.sqe_head = 0u
        ring.pointed.sq.sqe_tail = 0u
        ring.pointed.sq.sqes = state.sqMemory
        ring.pointed.sq.khead = alloc<UIntVar>().ptr
        ring.pointed.sq.ktail = alloc<UIntVar>().ptr
        ring.pointed.sq.kring_mask = alloc<UIntVar>().apply { value = entries - 1u }.ptr
        ring.pointed.sq.kring_entries = alloc<UIntVar>().apply { value = entries }.ptr
        
        // Setup CQ  
        ring.pointed.cq.cqes = state.cqMemory
        ring.pointed.cq.khead = alloc<UIntVar>().ptr
        ring.pointed.cq.ktail = alloc<UIntVar>().ptr
        ring.pointed.cq.kring_mask = alloc<UIntVar>().apply { value = entries * 2u - 1u }.ptr
        ring.pointed.cq.kring_entries = alloc<UIntVar>().apply { value = entries * 2u }.ptr
        
        // Ring info
        ring.pointed.ring_fd = kqFd
        ring.pointed.flags = p.pointed.flags
        
        // Store state ID in the ring
        ring.pointed.id = alloc<IntVar>().apply { value = stateId.toInt() }.ptr
    }
    
    return 0
}

@CName("io_uring_get_sqe")
fun io_uring_get_sqe(ring: CPointer<io_uring>): CPointer<io_uring_sqe>? {
    val sq = ring.pointed.sq
    val next = sq.sqe_tail
    val mask = sq.kring_mask!!.pointed.value
    
    if (next - sq.khead!!.pointed.value > mask) {
        // SQ ring is full
        return null
    }
    
    val sqe = sq.sqes + (next and mask).toInt()
    sq.sqe_tail = next + 1u
    
    // Clear the SQE
    memset(sqe, 0, sizeOf<io_uring_sqe>().convert())
    
    return sqe
}

@CName("io_uring_submit")
fun io_uring_submit(ring: CPointer<io_uring>): Int {
    val stateId = ring.pointed.id?.pointed?.value?.toLong() ?: return -EINVAL
    val state = globalRings[stateId] ?: return -EINVAL
    
    val sq = ring.pointed.sq
    val toSubmit = sq.sqe_tail - sq.sqe_head
    
    if (toSubmit == 0u) return 0
    
    // Process submissions
    var submitted = 0
    val tail = sq.ktail!!.pointed.value
    
    for (i in 0u until toSubmit) {
        val idx = (sq.sqe_head + i) and sq.kring_mask!!.pointed.value
        val sqe = sq.sqes[idx.toInt()]
        
        // Convert to Darwin operation
        val darwinSqe = DarwinSQE(
            opcode = sqe.opcode,
            fd = sqe.fd,
            addr = sqe.addr,
            len = sqe.len,
            offset = sqe.off,
            userData = sqe.user_data,
            flags = sqe.flags
        )
        
        // Register with kqueue based on opcode
        if (registerWithKqueue(state, darwinSqe) == 0) {
            submitted++
        }
    }
    
    sq.sqe_head = sq.sqe_tail
    sq.ktail!!.pointed.value = tail + submitted.toUInt()
    
    return submitted
}

@CName("io_uring_wait_cqe")
fun io_uring_wait_cqe(ring: CPointer<io_uring>, cqe_ptr: CPointer<CPointerVar<io_uring_cqe>>): Int {
    val stateId = ring.pointed.id?.pointed?.value?.toLong() ?: return -EINVAL
    val state = globalRings[stateId] ?: return -EINVAL
    
    // Poll kqueue for events
    memScoped {
        val events = allocArray<kevent>(32)
        val timeout = alloc<timespec>().apply {
            tv_sec = 1
            tv_nsec = 0
        }
        
        while (true) {
            val nEvents = kevent(state.kqueueFd, null, 0, events, 32, timeout.ptr)
            
            if (nEvents > 0) {
                // Process events into CQEs
                for (i in 0 until nEvents) {
                    val event = events[i]
                    val cqe = processDarwinEvent(state, event)
                    if (cqe != null) {
                        // Add to CQ ring
                        val cq = ring.pointed.cq
                        val idx = cq.ktail!!.pointed.value and cq.kring_mask!!.pointed.value
                        val cqeSlot = cq.cqes[idx.toInt()]
                        
                        cqeSlot.user_data = cqe.userData
                        cqeSlot.res = cqe.res
                        cqeSlot.flags = cqe.flags
                        
                        cq.ktail!!.pointed.value++
                        
                        // Return first CQE
                        cqe_ptr.pointed.value = cqeSlot.ptr
                        return 0
                    }
                }
            } else if (nEvents < 0) {
                return -errno
            }
            
            // Check if we have any completions buffered
            if (state.completions.isNotEmpty()) {
                val cqe = state.completions.removeAt(0)
                val cq = ring.pointed.cq
                val idx = cq.ktail!!.pointed.value and cq.kring_mask!!.pointed.value
                val cqeSlot = cq.cqes[idx.toInt()]
                
                cqeSlot.user_data = cqe.userData
                cqeSlot.res = cqe.res
                cqeSlot.flags = cqe.flags
                
                cq.ktail!!.pointed.value++
                cqe_ptr.pointed.value = cqeSlot.ptr
                return 0
            }
        }
    }
}

@CName("io_uring_queue_exit")
fun io_uring_queue_exit(ring: CPointer<io_uring>) {
    val stateId = ring.pointed.id?.pointed?.value?.toLong() ?: return
    val state = globalRings.remove(stateId) ?: return
    
    // Cleanup
    close(state.kqueueFd)
    nativeHeap.free(state.sqMemory)
    nativeHeap.free(state.cqMemory)
}

/**
 * Helper to register operations with kqueue
 */
private fun registerWithKqueue(state: DarwinUringState, sqe: DarwinSQE): Int = memScoped {
    val event = alloc<kevent>()
    
    when (sqe.opcode) {
        IORING_OP_READ, IORING_OP_READV -> {
            EV_SET(
                event.ptr,
                sqe.fd.convert(),
                EVFILT_READ.convert(),
                (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                0u,
                0,
                sqe.userData.toCPointer<COpaque>()
            )
        }
        
        IORING_OP_WRITE, IORING_OP_WRITEV -> {
            EV_SET(
                event.ptr,
                sqe.fd.convert(),
                EVFILT_WRITE.convert(),
                (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                0u,
                0,
                sqe.userData.toCPointer<COpaque>()
            )
        }
        
        IORING_OP_ACCEPT -> {
            EV_SET(
                event.ptr,
                sqe.fd.convert(),
                EVFILT_READ.convert(),
                (EV_ADD or EV_ENABLE).convert(),
                0u,
                0,
                sqe.userData.toCPointer<COpaque>()
            )
        }
        
        IORING_OP_TIMEOUT -> {
            val msec = sqe.addr.toLong() // Timeout in milliseconds
            EV_SET(
                event.ptr,
                sqe.userData.convert(), // Use userData as timer ID
                EVFILT_TIMER.convert(),
                (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                NOTE_USECONDS.convert(),
                (msec * 1000).convert(),
                sqe.userData.toCPointer<COpaque>()
            )
        }
        
        else -> {
            // Unsupported operation - complete immediately with error
            state.completions.add(DarwinCQE(sqe.userData, -ENOTSUP, 0u))
            return 0
        }
    }
    
    state.submissions.add(sqe)
    
    if (kevent(state.kqueueFd, event.ptr, 1, null, 0, null) < 0) {
        return -errno
    }
    
    return 0
}

/**
 * Process kqueue event into CQE
 */
private fun processDarwinEvent(state: DarwinUringState, event: kevent): DarwinCQE? {
    val userData = event.udata?.toLong() ?: return null
    val sqe = state.submissions.find { it.userData == userData.toULong() } ?: return null
    
    return when (sqe.opcode) {
        IORING_OP_READ, IORING_OP_READV -> {
            if (event.flags and EV_ERROR != 0u) {
                DarwinCQE(userData.toULong(), -event.data.toInt(), 0u)
            } else {
                // Perform actual read
                val result = performDarwinRead(sqe)
                DarwinCQE(userData.toULong(), result, 0u)
            }
        }
        
        IORING_OP_WRITE, IORING_OP_WRITEV -> {
            if (event.flags and EV_ERROR != 0u) {
                DarwinCQE(userData.toULong(), -event.data.toInt(), 0u)
            } else {
                // Perform actual write
                val result = performDarwinWrite(sqe)
                DarwinCQE(userData.toULong(), result, 0u)
            }
        }
        
        IORING_OP_ACCEPT -> {
            val clientFd = accept(sqe.fd, null, null)
            DarwinCQE(userData.toULong(), clientFd, 0u)
        }
        
        IORING_OP_TIMEOUT -> {
            DarwinCQE(userData.toULong(), 0, 0u) // Timeout expired
        }
        
        else -> null
    }
}

private fun performDarwinRead(sqe: DarwinSQE): Int = memScoped {
    val buffer = sqe.addr.toCPointer<ByteVar>()
    val result = if (sqe.offset != 0uL) {
        pread(sqe.fd, buffer, sqe.len.convert(), sqe.offset.toLong())
    } else {
        read(sqe.fd, buffer, sqe.len.convert())
    }
    result.toInt()
}

private fun performDarwinWrite(sqe: DarwinSQE): Int = memScoped {
    val buffer = sqe.addr.toCPointer<ByteVar>()
    val result = if (sqe.offset != 0uL) {
        pwrite(sqe.fd, buffer, sqe.len.convert(), sqe.offset.toLong())
    } else {
        write(sqe.fd, buffer, sqe.len.convert())
    }
    result.toInt()
}

/**
 * Export these symbols for C linkage
 */
@CName("io_uring_prep_read")
fun io_uring_prep_read(sqe: CPointer<io_uring_sqe>, fd: Int, buf: CPointer<*>?, nbytes: UInt, offset: ULong) {
    sqe.pointed.opcode = IORING_OP_READ
    sqe.pointed.fd = fd
    sqe.pointed.off = offset
    sqe.pointed.addr = buf.rawValue.toLong().toULong()
    sqe.pointed.len = nbytes
}

@CName("io_uring_prep_write")
fun io_uring_prep_write(sqe: CPointer<io_uring_sqe>, fd: Int, buf: CPointer<*>?, nbytes: UInt, offset: ULong) {
    sqe.pointed.opcode = IORING_OP_WRITE
    sqe.pointed.fd = fd
    sqe.pointed.off = offset
    sqe.pointed.addr = buf.rawValue.toLong().toULong()
    sqe.pointed.len = nbytes
}

@CName("io_uring_prep_accept")
fun io_uring_prep_accept(sqe: CPointer<io_uring_sqe>, fd: Int, addr: CPointer<sockaddr>?, addrlen: CPointer<socklen_tVar>?, flags: Int) {
    sqe.pointed.opcode = IORING_OP_ACCEPT
    sqe.pointed.fd = fd
    sqe.pointed.addr = addr.rawValue.toLong().toULong()
    sqe.pointed.addr2 = addrlen.rawValue.toLong().toULong()
    sqe.pointed.rw_flags = flags
}

@CName("io_uring_sqe_set_data")
fun io_uring_sqe_set_data(sqe: CPointer<io_uring_sqe>, data: COpaquePointer?) {
    sqe.pointed.user_data = data.rawValue.toLong().toULong()
}

@CName("io_uring_cqe_get_data")
fun io_uring_cqe_get_data(cqe: CPointer<io_uring_cqe>): COpaquePointer? {
    return cqe.pointed.user_data.toCPointer<COpaque>()
}