package borg.trikeshed.reactor

import kotlinx.cinterop.*
import platform.posix.*
import zlinux_uring.*         // Your C-interop bindings for liburing
import linux_uring.include.*  // Your helper enums/constants (UringOpcode, UringSetupFlags, etc.)
import borg.trikeshed.nio.ByteBuffer
import borg.trikeshed.nio.NativeArrayByteBuffer // Assuming this is your actual ByteBuffer for native
import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.net.NativeNetworkUtils
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
actual class AsyncIoEngine actual constructor() : CoroutineContext.Element {
    actual companion object Key : CoroutineContext.Key<AsyncIoEngine> {
        private const val QUEUE_DEPTH = 32 // Default queue depth
    }
    override val key: CoroutineContext.Key<*> get() = Key

    private val ring: io_uring = nativeHeap.alloc() // Allocate io_uring struct on native heap
    private val ringFd: Int // File descriptor for the io_uring instance
    
    // Mapped memory pointers for SQ, CQ, SQEs
    private val submissionQueue: CPointer<io_uring_sqe>
    private val completionQueue: CPointer<io_uring_cqe>
    private val sqRingPtr: CPointer<io_uring_sq>
    private val cqRingPtr: CPointer<io_uring_cq>

    // Pointers to ring buffer metadata (head, tail, mask, etc.)
    private val sq_head: CPointer<UIntVar>
    private val sq_tail: CPointer<UIntVar>
    private val sq_ring_mask: UInt
    private val sq_ring_entries: UInt
    private val sq_flags: CPointer<UIntVar>
    
    private val cq_head: CPointer<UIntVar>
    private val cq_tail: CPointer<UIntVar>
    private val cq_ring_mask: UInt
    private val cq_ring_entries: UInt

    // Sizes of the mapped memory regions, needed for munmap
    private val sqRingSize: ULong
    private val cqRingSize: ULong
    private val sqesSize: ULong

    private val activeOperations = mutableMapOf<ULong, Pair<AsyncOperationHandle, Any?>>()
    private var nextUserData: ULong = 1UL // User data for io_uring_sqe, must be non-zero for cancellation

    init {
        memScoped { // Use memScoped for temporary allocations like io_uring_params
            val params = alloc<io_uring_params>()
            platform.posix.memset(params.ptr, 0, sizeOf<io_uring_params>().convert())
            // Optionally set flags in params.flags, e.g., UringSetupFlags.uringSetupIopoll.src
            // params.flags = UringSetupFlags.uringSetupSqpoll.src // Example: enable SQ polling

            val ret = io_uring_queue_init_params(QUEUE_DEPTH.toUInt(), ring.ptr, params.ptr)
            if (ret < 0) {
                nativeHeap.free(ring.rawPtr) // Free the allocated io_uring struct on failure
                throw RuntimeException("io_uring_queue_init_params failed: ${strerror(-ret)?.toKString()}")
            }
            ringFd = ring.ring_fd // Store the file descriptor

            // Map the rings and SQEs based on the parameters returned by the kernel
            val p = params.pointed
            
            sqRingSize = (p.sq_off.array + p.sq_entries * sizeOf<UIntVar>().toUInt()).toULong()
            sqRingPtr = mmap(
                null, sqRingSize.convert(), PROT_READ or PROT_WRITE,
                MAP_SHARED or MAP_POPULATE, ringFd, IORING_OFF_SQ_RING.toLong()
            )!!.reinterpret()

            sq_head = (sqRingPtr.rawValue.toLong() + p.sq_off.head).toCPointer()!!
            sq_tail = (sqRingPtr.rawValue.toLong() + p.sq_off.tail).toCPointer()!!
            sq_ring_mask = p.sq_off.ring_mask.toUInt()
            sq_ring_entries = p.sq_off.ring_entries.toUInt()
            sq_flags = (sqRingPtr.rawValue.toLong() + p.sq_off.flags).toCPointer()!!

            cqRingSize = (p.cq_off.cqes + p.cq_entries * sizeOf<io_uring_cqe>().toUInt()).toULong()
            val singleMmap = (p.features and IORING_FEAT_SINGLE_MMAP.toUInt()) != 0U
            cqRingPtr = if(singleMmap) sqRingPtr.reinterpret() // If single mmap, CQ is part of SQ mapping
                     else mmap(
                        null, cqRingSize.convert(), PROT_READ or PROT_WRITE,
                        MAP_SHARED or MAP_POPULATE, ringFd, IORING_OFF_CQ_RING.toLong()
                     )!!.reinterpret()

            cq_head = (cqRingPtr.rawValue.toLong() + p.cq_off.head).toCPointer()!!
            cq_tail = (cqRingPtr.rawValue.toLong() + p.cq_off.tail).toCPointer()!!
            cq_ring_mask = p.cq_off.ring_mask.toUInt()
            cq_ring_entries = p.cq_off.ring_entries.toUInt()
            
            sqesSize = (p.sq_entries * sizeOf<io_uring_sqe>().toUInt()).toULong()
            submissionQueue = mmap(
                null, sqesSize.convert(),
                PROT_READ or PROT_WRITE, MAP_SHARED or MAP_POPULATE, ringFd, IORING_OFF_SQES.toLong()
            )!!.reinterpret()
            
            completionQueue = (cqRingPtr.rawValue.toLong() + p.cq_off.cqes.toLong()).toCPointer()!!
        }
    }

    private fun getSqeAndUserData(): Pair<CPointer<io_uring_sqe>, ULong> {
        val sqe = io_uring_get_sqe(ring.ptr)
        if (sqe == null) {
            // Submission queue is full. Attempt to submit and retry.
            val submitResult = io_uring_submit(ring.ptr)
            if (submitResult < 0) {
                throw RuntimeException("io_uring_submit failed on full queue: ${strerror(-submitResult)?.toKString()}")
            }
            val newSqe = io_uring_get_sqe(ring.ptr)
            if (newSqe == null) {
                throw IllegalStateException("io_uring submission queue full even after submit. Consider increasing QUEUE_DEPTH or handling backpressure.")
            }
            return getSqeAndUserData() // Recursive call to get a new SQE and user data
        }
        val userData = nextUserData++
        return Pair(sqe, userData)
    }

    actual override fun registerDescriptor(fd: Int) {
        // For io_uring, FDs can be registered for more efficient access with IOSQE_FIXED_FILE.
        // This function could call io_uring_register(ring.ptr, IORING_REGISTER_FILES, &fd, 1).
        // For now, direct FD use is simpler and common.
        println("Linux AsyncIoEngine: registerDescriptor($fd) - No-op in this basic version, direct FD used.")
    }

    actual override fun unregisterDescriptor(fd: Int) {
        println("Linux AsyncIoEngine: unregisterDescriptor($fd) - No-op in this basic version.")
    }

    actual override fun submitRead(fd: Int, buffer: ByteBuffer, fileOffset: Long, attachment: Any?): AsyncOperationHandle {
        val (sqe, userData) = getSqeAndUserData()
        val handle = LinuxAsyncOperationHandle(userData)
        activeOperations[userData] = Pair(handle, attachment)

        val internalBuffer = (buffer as NativeArrayByteBuffer).getInternalNativeArray() // Access underlying ByteArray
        internalBuffer.usePinned { pinned ->
            io_uring_prep_read(sqe, fd, pinned.addressOf(buffer.position()), buffer.remaining().toUInt(), fileOffset.convert())
        }
        io_uring_sqe_set_data(sqe, interpretCPointer<COpaquePointerVar>(userData.toLong()))
        io_uring_submit(ring.ptr) // Submit immediately for simplicity. Batching is more efficient.
        return handle
    }

    actual override fun submitWrite(fd: Int, buffer: ByteBuffer, fileOffset: Long, attachment: Any?): AsyncOperationHandle {
        val (sqe, userData) = getSqeAndUserData()
        val handle = LinuxAsyncOperationHandle(userData)
        activeOperations[userData] = Pair(handle, attachment)

        val internalBuffer = (buffer as NativeArrayByteBuffer).getInternalNativeArray()
        internalBuffer.usePinned { pinned ->
            io_uring_prep_write(sqe, fd, pinned.addressOf(buffer.position()), buffer.remaining().toUInt(), fileOffset.convert())
        }
        io_uring_sqe_set_data(sqe, interpretCPointer<COpaquePointerVar>(userData.toLong()))
        io_uring_submit(ring.ptr)
        return handle
    }
     actual override fun submitAccept(serverFd: Int, attachment: Any?): AsyncOperationHandle = memScoped {
        val (sqe, userData) = getSqeAndUserData()
        val handle = LinuxAsyncOperationHandle(userData)
        activeOperations[userData] = Pair(handle, attachment)

        val clientAddr = alloc<sockaddr_storage>() // Enough space for IPv4/IPv6
        val clientAddrLen = alloc<socklen_tVar>()
        clientAddrLen.value = sizeOf<sockaddr_storage>().convert()

        io_uring_prep_accept(sqe, serverFd, clientAddr.ptr.reinterpret(), clientAddrLen.ptr, 0 /* flags */)
        io_uring_sqe_set_data(sqe, interpretCPointer<COpaquePointerVar>(userData.toLong()))
        io_uring_submit(ring.ptr)
        return handle
    }

    actual override fun submitConnect(clientFd: Int, remoteAddress: NetworkAddress, attachment: Any?): AsyncOperationHandle {
        val (sqe, userData) = getSqeAndUserData()
        val handle = LinuxAsyncOperationHandle(userData)
        activeOperations[userData] = Pair(handle, attachment)
        
        NativeNetworkUtils.networkAddressToNativeSockaddr(remoteAddress)!!.usePinned { pinnedAddr ->
            io_uring_prep_connect(sqe, clientFd, pinnedAddr.get().ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        }
        io_uring_sqe_set_data(sqe, interpretCPointer<COpaquePointerVar>(userData.toLong()))
        io_uring_submit(ring.ptr)
        return handle
    }
    
    actual override fun submitCancel(handleToCancel: AsyncOperationHandle, attachment: Any?): AsyncOperationHandle {
        val (sqe, userData) = getSqeAndUserData()
        val cancelHandle = LinuxAsyncOperationHandle(userData)
        activeOperations[userData] = Pair(cancelHandle, attachment)

        val userDataToCancel = (handleToCancel as LinuxAsyncOperationHandle).id
        io_uring_prep_cancel(sqe, interpretCPointer<COpaquePointerVar>(userDataToCancel.toLong()), 0) // flags=0
        io_uring_sqe_set_data(sqe, interpretCPointer<COpaquePointerVar>(userData.toLong())) // user_data for THIS cancel op
        io_uring_submit(ring.ptr)
        return cancelHandle
    }

    actual override suspend fun pollCompletions(maxEvents: Int, timeoutMillis: Long): List<AsyncCompletionEvent> = withContext(Dispatchers.Default) {
        val completions = mutableListOf<AsyncCompletionEvent>()
        val numSubmitted = io_uring_submit(ring.ptr) // Submit any pending SQEs
        if (numSubmitted < 0 && numSubmitted != -EBUSY) { // EBUSY might mean kernel is busy, not necessarily fatal for completions
            // Skip waiting for completions if submit itself failed critically
             println("Warning: io_uring_submit failed with ${-numSubmitted} before polling completions. Err: ${strerror(-numSubmitted)?.toKString()}")
             return@withContext completions
        }


        memScoped {
            val cqePtr = alloc<CPointerVar<io_uring_cqe>>()
            
            val ts: CPointer<__kernel_timespec>? = if (timeoutMillis >= 0) {
                alloc<__kernel_timespec> {
                    tv_sec = timeoutMillis / 1000
                    tv_nsec = (timeoutMillis % 1000) * 1_000_000
                }.ptr
            } else null // Infinite timeout if null

            // If timeoutMillis is 0, we just peek. Otherwise, we wait for at least one event.
            if (timeoutMillis != 0L) {
                val ret = if (timeoutMillis < 0) { // Infinite wait
                    io_uring_wait_cqe(ring.ptr, cqePtr.ptr)
                } else { // Timed wait
                    io_uring_wait_cqe_timeout(ring.ptr, cqePtr.ptr, ts)
                }
                
                if (ret < 0) {
                    if (ret == -ETIME) { // Timeout expired
                        return@withContext emptyList()
                    }
                    if (ret == -EINTR) { // Interrupted, can retry or return empty
                        return@withContext emptyList()
                    }
                    throw RuntimeException("io_uring_wait_cqe failed: ${strerror(-ret)?.toKString()}")
                }
            }
            
            // Process all available CQEs up to maxEvents
            var eventsProcessed = 0
            while (eventsProcessed < maxEvents && isActive) {
                val peekRet = io_uring_peek_cqe(ring.ptr, cqePtr.ptr)
                if (peekRet < 0) {
                    if (peekRet == -EAGAIN) break // No more CQEs available
                    throw RuntimeException("io_uring_peek_cqe failed: ${strerror(-peekRet)?.toKString()}")
                }
                val cqe = cqePtr.value ?: break // Should not be null if peekRet was 0

                val userData = cqe.pointed.user_data.toLong().toULong()
                val opInfo = activeOperations.remove(userData)

                if (opInfo != null) {
                    val (handle, attachment) = opInfo
                    val result = cqe.pointed.res
                    val error = if (result < 0) -result else null // Convert negative result to positive errno
                    completions.add(AsyncCompletionEvent(handle, attachment, result, error))
                    eventsProcessed++
                } else {
                    // Spurious completion or user_data mismatch (e.g., already cancelled and removed)
                    println("Warning: io_uring completion for unknown user_data: $userData, result: ${cqe.pointed.res}")
                }
                io_uring_cqe_seen(ring.ptr, cqe) // Mark as consumed
            }
        }
        return completions
    }

    actual override fun close() {
        if (ringFd != -1) { // Check if the ring was successfully initialized
            // Unmap memory regions before closing the ring_fd
            munmap(sqRingPtr, sqRingSize.convert())
            // Only unmap cqRingPtr separately if it's not the same as sqRingPtr (i.e., IORING_FEAT_SINGLE_MMAP was not used)
            if (cqRingPtr != sqRingPtr) {
                munmap(cqRingPtr, cqRingSize.convert())
            }
            munmap(submissionQueue, sqesSize.convert())
            
            platform.posix.close(ringFd) // Close the io_uring file descriptor
        }
        nativeHeap.free(ring.rawPtr) // Free the memory allocated for the io_uring struct itself
        activeOperations.clear()
        println("Linux AsyncIoEngine (io_uring) closed.")
    }
}

// Internal handle for Linux io_uring operations
@JvmInline
internal actual value class LinuxAsyncOperationHandle(val id: ULong) : AsyncOperationHandle
