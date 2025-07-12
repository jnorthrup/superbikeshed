package trikeshed.uring

import trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Common NIO abstraction that provides io_uring semantics
 * This is the shared implementation for JVM NIO and native kqueue/epoll
 */

// Common NIO operations that map to io_uring ops
sealed class NioOp {
    abstract val fd: Int
    abstract val userData: ULong
    
    data class Read(
        override val fd: Int,
        val buffer: ByteArray,
        val offset: Long,
        override val userData: ULong
    ) : NioOp()
    
    data class ReadV(
        override val fd: Int,
        val iovecs: IoVecList,
        val offset: Long,
        override val userData: ULong
    ) : NioOp()
    
    data class Write(
        override val fd: Int,
        val buffer: ByteArray,
        val offset: Long,
        override val userData: ULong
    ) : NioOp()
    
    data class WriteV(
        override val fd: Int,
        val iovecs: IoVecList,
        val offset: Long,
        override val userData: ULong
    ) : NioOp()
    
    data class Accept(
        override val fd: Int,
        override val userData: ULong
    ) : NioOp()
    
    data class Connect(
        override val fd: Int,
        val addr: SocketAddress,
        override val userData: ULong
    ) : NioOp()
    
    data class Close(
        override val fd: Int,
        override val userData: ULong
    ) : NioOp()
    
    data class Fsync(
        override val fd: Int,
        val dataSync: Boolean,
        override val userData: ULong
    ) : NioOp()
    
    data class Send(
        override val fd: Int,
        val buffer: ByteArray,
        val flags: Int,
        override val userData: ULong
    ) : NioOp()
    
    data class Recv(
        override val fd: Int,
        val buffer: ByteArray,
        val flags: Int,
        override val userData: ULong
    ) : NioOp()
}

// Common NIO completion
data class NioCompletion(
    val userData: ULong,
    val result: Int,
    val flags: UInt = 0u
)

/**
 * Common NIO engine interface - implemented by each platform
 */
interface NioEngine {
    suspend fun submitOp(op: NioOp)
    suspend fun submitBatch(ops: Indexed<NioOp>)
    fun pollCompletions(maxEvents: Int, timeoutMs: Long): Indexed<NioCompletion>
    fun close()
}

/**
 * Platform-specific NIO engine factory
 */
expect fun createNioEngine(): NioEngine

/**
 * Common liburing emulator implementation using NIO engine
 */
class CommonLiburingEmulator : LiburingEmulator {
    
    private val rings = mutableMapOf<IoUring, RingState>()
    
    private class RingState(
        val engine: NioEngine,
        val scope: CoroutineScope,
        val sqChannel: Channel<IoUringSqe>,
        val cqChannel: Channel<IoUringCqe>,
        val registeredBuffers: MutableList<IoVec>,
        val registeredFiles: MutableList<Int>
    ) {
        @Volatile var sqHead = 0u
        @Volatile var sqTail = 0u
        @Volatile var cqHead = 0u
        @Volatile var cqTail = 0u
        
        val pendingOps = mutableMapOf<ULong, IoUringSqe>()
    }
    
    override fun io_uring_queue_init(entries: UInt, ring: IoUring, flags: UInt): Int {
        return io_uring_queue_init_params(entries, ring, IoUringParams(flags = flags))
    }
    
    override fun io_uring_queue_init_params(entries: UInt, ring: IoUring, p: IoUringParams): Int {
        val engine = createNioEngine()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        val state = RingState(
            engine = engine,
            scope = scope,
            sqChannel = Channel(Channel.UNLIMITED),
            cqChannel = Channel(Channel.UNLIMITED),
            registeredBuffers = mutableListOf(),
            registeredFiles = mutableListOf()
        )
        
        rings[ring] = state
        
        // Start SQ processor
        scope.launch {
            for (sqe in state.sqChannel) {
                processSqe(state, sqe)
            }
        }
        
        // Start CQ poller
        scope.launch {
            while (isActive) {
                val completions = engine.pollCompletions(16, 10)
                completions.forEach { completion ->
                    val cqe = IoUringCqe(
                        user_data = completion.userData,
                        res = completion.result,
                        flags = completion.flags
                    )
                    state.cqChannel.send(cqe)
                    ring.cq.cqes.add(cqe)
                }
                delay(1)
            }
        }
        
        return 0
    }
    
    override fun io_uring_queue_exit(ring: IoUring) {
        rings[ring]?.let { state ->
            state.scope.cancel()
            state.engine.close()
            rings.remove(ring)
        }
    }
    
    override fun io_uring_get_sqe(ring: IoUring): IoUringSqe? {
        val state = rings[ring] ?: return null
        
        if (ring.sq.sqes.size >= ring.sq.ring_entries.toInt()) {
            return null // Ring full
        }
        
        val sqe = IoUringSqe()
        ring.sq.sqes.add(sqe)
        return sqe
    }
    
    override fun io_uring_submit(ring: IoUring): Int {
        val state = rings[ring] ?: return -1
        var submitted = 0
        
        runBlocking {
            ring.sq.sqes.forEach { sqe ->
                state.sqChannel.send(sqe)
                state.pendingOps[sqe.user_data] = sqe
                submitted++
            }
        }
        
        ring.sq.sqes.clear()
        return submitted
    }
    
    private suspend fun processSqe(state: RingState, sqe: IoUringSqe) {
        val op = sqeToNioOp(sqe, state) ?: return
        
        if (sqe.flags.toInt() and IoUringSqeFlags.IOSQE_IO_LINK.toInt() != 0) {
            // Handle linked operations
            state.engine.submitOp(op)
            // Wait for completion before processing next
        } else {
            state.engine.submitOp(op)
        }
    }
    
    private fun sqeToNioOp(sqe: IoUringSqe, state: RingState): NioOp? {
        return when (sqe.opcode) {
            IoUringOp.IORING_OP_READ -> {
                val buf = getBufferFromAddr(sqe.addr, sqe.len.toInt())
                NioOp.Read(sqe.fd, buf, sqe.off, sqe.user_data)
            }
            IoUringOp.IORING_OP_READV -> {
                val iovecs = getIovecsFromAddr(sqe.addr, sqe.len.toInt())
                NioOp.ReadV(sqe.fd, iovecs, sqe.off, sqe.user_data)
            }
            IoUringOp.IORING_OP_WRITE -> {
                val buf = getBufferFromAddr(sqe.addr, sqe.len.toInt())
                NioOp.Write(sqe.fd, buf, sqe.off, sqe.user_data)
            }
            IoUringOp.IORING_OP_WRITEV -> {
                val iovecs = getIovecsFromAddr(sqe.addr, sqe.len.toInt())
                NioOp.WriteV(sqe.fd, iovecs, sqe.off, sqe.user_data)
            }
            IoUringOp.IORING_OP_ACCEPT -> {
                NioOp.Accept(sqe.fd, sqe.user_data)
            }
            IoUringOp.IORING_OP_CONNECT -> {
                val addr = getSocketAddressFromAddr(sqe.addr)
                NioOp.Connect(sqe.fd, addr, sqe.user_data)
            }
            IoUringOp.IORING_OP_CLOSE -> {
                NioOp.Close(sqe.fd, sqe.user_data)
            }
            IoUringOp.IORING_OP_FSYNC -> {
                val dataSync = sqe.len.toInt() and IoUringFsyncFlags.IORING_FSYNC_DATASYNC.toInt() != 0
                NioOp.Fsync(sqe.fd, dataSync, sqe.user_data)
            }
            IoUringOp.IORING_OP_SEND -> {
                val buf = getBufferFromAddr(sqe.addr, sqe.len.toInt())
                NioOp.Send(sqe.fd, buf, sqe.ioprio.toInt(), sqe.user_data)
            }
            IoUringOp.IORING_OP_RECV -> {
                val buf = getBufferFromAddr(sqe.addr, sqe.len.toInt())
                NioOp.Recv(sqe.fd, buf, sqe.ioprio.toInt(), sqe.user_data)
            }
            else -> null
        }
    }
    
    override fun io_uring_wait_cqe(ring: IoUring, cqe_ptr: Join<IoUringCqe?, Int>): Int {
        return io_uring_wait_cqe_timeout(ring, cqe_ptr, null)
    }
    
    override fun io_uring_wait_cqe_timeout(
        ring: IoUring,
        cqe_ptr: Join<IoUringCqe?, Int>,
        ts: TimeSpec?
    ): Int {
        val state = rings[ring] ?: return -1
        
        val cqe = runBlocking {
            withTimeoutOrNull(ts?.toMillis() ?: Long.MAX_VALUE) {
                state.cqChannel.receive()
            }
        }
        
        return if (cqe != null) {
            0 // Success
        } else {
            -1 // Timeout
        }
    }
    
    override fun io_uring_peek_cqe(ring: IoUring, cqe_ptr: Join<IoUringCqe?, Int>): Int {
        val state = rings[ring] ?: return -1
        
        val cqe = state.cqChannel.tryReceive().getOrNull()
        return if (cqe != null) 0 else -1
    }
    
    override fun io_uring_cqe_seen(ring: IoUring, cqe: IoUringCqe) {
        rings[ring]?.let { state ->
            state.pendingOps.remove(cqe.user_data)
            ring.cq.cqes.remove(cqe)
        }
    }
    
    // Prep helpers implementation
    override fun io_uring_prep_readv(sqe: IoUringSqe, fd: Int, iovecs: IoVecList, nr_vecs: UInt, offset: Long) {
        sqe.opcode = IoUringOp.IORING_OP_READV
        sqe.fd = fd
        sqe.addr = iovecs.hashCode().toLong() // Store reference
        sqe.len = nr_vecs
        sqe.off = offset
    }
    
    override fun io_uring_prep_read(sqe: IoUringSqe, fd: Int, buf: ByteArray, nbytes: UInt, offset: Long) {
        sqe.opcode = IoUringOp.IORING_OP_READ
        sqe.fd = fd
        sqe.addr = buf.hashCode().toLong() // Store reference
        sqe.len = nbytes
        sqe.off = offset
    }
    
    override fun io_uring_prep_write(sqe: IoUringSqe, fd: Int, buf: ByteArray, nbytes: UInt, offset: Long) {
        sqe.opcode = IoUringOp.IORING_OP_WRITE
        sqe.fd = fd
        sqe.addr = buf.hashCode().toLong()
        sqe.len = nbytes
        sqe.off = offset
    }
    
    override fun io_uring_prep_fsync(sqe: IoUringSqe, fd: Int, fsync_flags: UInt) {
        sqe.opcode = IoUringOp.IORING_OP_FSYNC
        sqe.fd = fd
        sqe.len = fsync_flags
    }
    
    // ... implement remaining prep functions ...
    
    // Buffer/file registration
    override fun io_uring_register_buffers(ring: IoUring, iovecs: IoVecList, nr_iovecs: UInt): Int {
        rings[ring]?.let { state ->
            state.registeredBuffers.clear()
            state.registeredBuffers.addAll(iovecs)
            return 0
        }
        return -1
    }
    
    override fun io_uring_register_files(ring: IoUring, files: FdList, nr_files: UInt): Int {
        rings[ring]?.let { state ->
            state.registeredFiles.clear()
            state.registeredFiles.addAll(files)
            return 0
        }
        return -1
    }
    
    // Helper functions
    private val bufferCache = mutableMapOf<Long, ByteArray>()
    private val iovecCache = mutableMapOf<Long, IoVecList>()
    private val addrCache = mutableMapOf<Long, SocketAddress>()
    
    private fun getBufferFromAddr(addr: Long, len: Int): ByteArray {
        return bufferCache[addr] ?: ByteArray(len).also { bufferCache[addr] = it }
    }
    
    private fun getIovecsFromAddr(addr: Long, count: Int): IoVecList {
        return iovecCache[addr] ?: emptyList()
    }
    
    private fun getSocketAddressFromAddr(addr: Long): SocketAddress {
        return addrCache[addr] ?: SocketAddress()
    }
    
    // Extension functions
    private fun TimeSpec?.toMillis(): Long = this?.let { 
        // Platform-specific conversion
        1000L 
    } ?: 0L
    
    // Remaining methods...
    override fun io_uring_submit_and_wait(ring: IoUring, wait_nr: UInt): Int {
        val state = rings[ring] ?: return -1
        
        // Submit all pending SQEs
        val submitted = io_uring_submit(ring)
        if (submitted < 0) return submitted
        
        // Wait for completions
        var completed = 0
        while (completed < wait_nr) {
            val cqe = runBlocking {
                withTimeoutOrNull(1000L) {
                    state.cqChannel.receive()
                }
            }
            if (cqe != null) {
                completed++
            } else {
                break // Timeout
            }
        }
        
        return submitted
    }
    
    override fun io_uring_submit_and_wait_timeout(
        ring: IoUring, 
        cqe_ptr: Join<IoUringCqe?, Int>, 
        wait_nr: UInt, 
        ts: TimeSpec?, 
        sigmask: SigSet?
    ): Int {
        val state = rings[ring] ?: return -1
        
        // Submit all pending SQEs
        val submitted = io_uring_submit(ring)
        if (submitted < 0) return submitted
        
        // Wait for completions with timeout
        val timeout = ts?.toMillis() ?: 1000L
        val cqe = runBlocking {
            withTimeoutOrNull(timeout) {
                state.cqChannel.receive()
            }
        }
        
        return if (cqe != null) {
            cqe_ptr.component2()(0) = cqe
            0
        } else {
            -1 // Timeout
        }
    }
    
    override fun io_uring_wait_cqes(
        ring: IoUring, 
        cqe_ptr: Join<IoUringCqe?, Int>, 
        wait_nr: UInt, 
        ts: TimeSpec?, 
        sigmask: SigSet?
    ): Int {
        val state = rings[ring] ?: return -1
        
        val timeout = ts?.toMillis() ?: Long.MAX_VALUE
        val cqe = runBlocking {
            withTimeoutOrNull(timeout) {
                state.cqChannel.receive()
            }
        }
        
        return if (cqe != null) {
            cqe_ptr.component2()(0) = cqe
            0
        } else {
            -1 // Timeout
        }
    }
    
    override fun io_uring_for_each_cqe(ring: IoUring): Series<IoUringCqe> {
        val state = rings[ring] ?: return 0 j { IoUringCqe(0u, 0, 0u) }
        
        val completions = mutableListOf<IoUringCqe>()
        runBlocking {
            while (state.cqChannel.tryReceive().isSuccess) {
                val cqe = state.cqChannel.receive()
                completions.add(cqe)
            }
        }
        
        return completions.size j { completions[it] }
    }
    
    override fun io_uring_sqe_set_data(sqe: IoUringSqe, data: Any) {
        // Store data in a cache for later retrieval
        dataCache[sqe.user_data] = data
    }
    
    override fun io_uring_cqe_get_data(cqe: IoUringCqe): Any? {
        return dataCache[cqe.user_data]
    }
    
    override fun io_uring_sqe_set_data64(sqe: IoUringSqe, data: ULong) { sqe.user_data = data }
    override fun io_uring_cqe_get_data64(cqe: IoUringCqe): ULong = cqe.user_data
    override fun io_uring_prep_writev(sqe: IoUringSqe, fd: Int, iovecs: IoVecList, nr_vecs: UInt, offset: Long) {
        sqe.opcode = IoUringOp.IORING_OP_WRITEV
        sqe.fd = fd
        sqe.addr = iovecs.hashCode().toLong() // Store reference
        sqe.len = nr_vecs
        sqe.off = offset
    }
    
    override fun io_uring_prep_nop(sqe: IoUringSqe) {
        sqe.opcode = IoUringOp.IORING_OP_NOP
        sqe.fd = -1
        sqe.addr = 0L
        sqe.len = 0u
        sqe.off = 0L
    }
    
    override fun io_uring_prep_accept(sqe: IoUringSqe, fd: Int, addr: SocketAddress?, addrlen: UInt, flags: Int) {
        sqe.opcode = IoUringOp.IORING_OP_ACCEPT
        sqe.fd = fd
        sqe.addr = addr?.hashCode()?.toLong() ?: 0L
        sqe.len = addrlen
        sqe.off = flags.toLong()
    }
    
    override fun io_uring_prep_connect(sqe: IoUringSqe, fd: Int, addr: SocketAddress, addrlen: UInt) {
        sqe.opcode = IoUringOp.IORING_OP_CONNECT
        sqe.fd = fd
        sqe.addr = addr.hashCode().toLong()
        sqe.len = addrlen
        sqe.off = 0L
    }
    
    override fun io_uring_prep_send(sqe: IoUringSqe, sockfd: Int, buf: ByteArray, len: ULong, flags: Int) {
        sqe.opcode = IoUringOp.IORING_OP_SEND
        sqe.fd = sockfd
        sqe.addr = buf.hashCode().toLong()
        sqe.len = len.toUInt()
        sqe.off = flags.toLong()
    }
    
    override fun io_uring_prep_recv(sqe: IoUringSqe, sockfd: Int, buf: ByteArray, len: ULong, flags: Int) {
        sqe.opcode = IoUringOp.IORING_OP_RECV
        sqe.fd = sockfd
        sqe.addr = buf.hashCode().toLong()
        sqe.len = len.toUInt()
        sqe.off = flags.toLong()
    }
    
    override fun io_uring_prep_close(sqe: IoUringSqe, fd: Int) {
        sqe.opcode = IoUringOp.IORING_OP_CLOSE
        sqe.fd = fd
        sqe.addr = 0L
        sqe.len = 0u
        sqe.off = 0L
    }
    
    override fun io_uring_unregister_buffers(ring: IoUring): Int {
        rings[ring]?.let { state ->
            state.registeredBuffers.clear()
            return 0
        }
        return -1
    }
    
    override fun io_uring_unregister_files(ring: IoUring): Int {
        rings[ring]?.let { state ->
            state.registeredFiles.clear()
            return 0
        }
        return -1
    }
    
    override fun io_uring_sqe_set_flags(sqe: IoUringSqe, flags: UInt) {
        sqe.flags = flags
    }
    
    override fun io_uring_prep_link(sqe: IoUringSqe) {
        // Link operations are typically handled through flags
        // This is a placeholder for link-specific preparation
        sqe.flags = sqe.flags or IoUringSqeFlags.IOSQE_IO_LINK.toUInt()
    }
    
    override fun io_uring_prep_hardlink(sqe: IoUringSqe) {
        // Hardlink is a file system operation
        // This would typically be IORING_OP_LINKAT or similar
        sqe.opcode = IoUringOp.IORING_OP_LINKAT
        sqe.fd = -1
        sqe.addr = 0L
        sqe.len = 0u
        sqe.off = 0L
    }
    
    // Data cache for SQE/CQE data association
    private val dataCache = mutableMapOf<ULong, Any>()
}