package trikeshed.uring

import trikeshed.lib.*
import kotlin.coroutines.CoroutineContext

/**
 * Proxy layer that allows runtime selection between io_uring and NIO implementations
 * This enables all platforms to use either API regardless of native capabilities
 */

// Runtime selectable backend
enum class IoBackend {
    NATIVE_URING,     // Real io_uring on Linux
    NATIVE_KQUEUE,    // Real kqueue on macOS  
    NATIVE_EPOLL,     // Real epoll on Linux (fallback)
    NIO_EMULATION,    // Common NIO for all platforms
    AUTO              // Platform optimal choice
}

/**
 * Unified proxy that provides both io_uring and NIO APIs
 * Can reposition between implementations at runtime
 */
class UringProxy(
    private val backend: IoBackend = IoBackend.AUTO,
    private val context: CoroutineContext? = null
) {
    // Lazy initialization based on backend selection
    private val actualBackend: IoBackend by lazy {
        when (backend) {
            IoBackend.AUTO -> selectOptimalBackend()
            else -> backend
        }
    }
    
    // Liburing API proxy
    private val liburingImpl: LiburingEmulator by lazy {
        when (actualBackend) {
            IoBackend.NATIVE_URING -> createNativeLiburing()
            IoBackend.NATIVE_KQUEUE -> createKqueueLiburing()
            IoBackend.NATIVE_EPOLL -> createEpollLiburing()
            IoBackend.NIO_EMULATION -> CommonLiburingEmulator()
            IoBackend.AUTO -> throw IllegalStateException("AUTO should be resolved")
        }
    }
    
    // NIO API proxy
    private val nioEngine: NioEngine by lazy {
        when (actualBackend) {
            IoBackend.NATIVE_URING -> UringNioAdapter()
            IoBackend.NATIVE_KQUEUE -> KqueueNioEngine()
            IoBackend.NATIVE_EPOLL -> EpollNioEngine()
            IoBackend.NIO_EMULATION -> createNioEngine()
            IoBackend.AUTO -> throw IllegalStateException("AUTO should be resolved")
        }
    }
    
    // Expose both APIs
    fun asLiburing(): LiburingEmulator = liburingImpl
    fun asNioEngine(): NioEngine = nioEngine
    
    // High-level TrikeUring API that works everywhere
    fun asTrikeUring(ringSize: Int = 256, flags: Int = 0): TrikeUring {
        return ProxyTrikeUring(this, context ?: EmptyCoroutineContext, ringSize, flags)
    }
    
    // Runtime backend switching (for testing/benchmarking)
    fun canSwitchBackend(): Boolean = !::actualBackend.isInitialized
    
    companion object {
        // Global proxy instances for different use cases
        val system = UringProxy(IoBackend.AUTO)
        val nio = UringProxy(IoBackend.NIO_EMULATION)
        
        // Platform detection
        private fun selectOptimalBackend(): IoBackend {
            return when {
                isLinuxWithUring() -> IoBackend.NATIVE_URING
                isMacOS() -> IoBackend.NATIVE_KQUEUE
                isLinux() -> IoBackend.NATIVE_EPOLL
                else -> IoBackend.NIO_EMULATION
            }
        }
    }
}

/**
 * TrikeUring implementation that uses the proxy
 */
private class ProxyTrikeUring(
    private val proxy: UringProxy,
    context: CoroutineContext,
    ringSize: Int,
    flags: Int
) : TrikeUring {
    
    private val liburing = proxy.asLiburing()
    private val ring = IoUring(
        sq = SubmissionQueue(
            ring_entries = ringSize.toUInt(),
            ring_mask = (ringSize - 1).toUInt(),
            khead = 0u,
            ktail = 0u,
            sqes = mutableListOf()
        ),
        cq = CompletionQueue(
            ring_entries = ringSize.toUInt(),
            ring_mask = (ringSize - 1).toUInt(),
            khead = 0u,
            ktail = 0u,
            cqes = mutableListOf()
        ),
        flags = flags.toUInt()
    )
    
    init {
        liburing.io_uring_queue_init(ringSize.toUInt(), ring, flags.toUInt())
    }
    
    override val submission = kotlinx.coroutines.channels.Channel<Sqe>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    
    override val completion = kotlinx.coroutines.flow.flow<Cqe> {
        while (true) {
            val cqeResult = Join<IoUringCqe?, Int>(null, -1)
            val ret = liburing.io_uring_wait_cqe_timeout(ring, cqeResult, null)
            
            if (ret == 0 && cqeResult.l != null) {
                val cqe = cqeResult.l!!
                emit(convertToCqe(cqe))
                liburing.io_uring_cqe_seen(ring, cqe)
            }
            
            kotlinx.coroutines.delay(1)
        }
    }.shareIn(
        kotlinx.coroutines.CoroutineScope(context),
        kotlinx.coroutines.flow.SharingStarted.Lazily
    )
    
    // Submission handler
    init {
        kotlinx.coroutines.CoroutineScope(context).launch {
            for (sqe in submission) {
                submitSqe(sqe)
            }
        }
    }
    
    private suspend fun submitSqe(sqe: Sqe) {
        val uringSqe = liburing.io_uring_get_sqe(ring) ?: return
        
        // Convert high-level Sqe to liburing sqe
        when (sqe) {
            is Read -> {
                liburing.io_uring_prep_read(uringSqe, sqe.fd, sqe.buffer, sqe.buffer.size.toUInt(), sqe.offset)
                liburing.io_uring_sqe_set_data64(uringSqe, sqe.userData.toULong())
            }
            is Write -> {
                liburing.io_uring_prep_write(uringSqe, sqe.fd, sqe.buffer, sqe.buffer.size.toUInt(), sqe.offset)
                liburing.io_uring_sqe_set_data64(uringSqe, sqe.userData.toULong())
            }
            is Fsync -> {
                liburing.io_uring_prep_fsync(uringSqe, sqe.fd, if (sqe.fsyncDataOnly) 1u else 0u)
                liburing.io_uring_sqe_set_data64(uringSqe, sqe.userData.toULong())
            }
            // ... handle other operations ...
        }
        
        liburing.io_uring_submit(ring)
    }
    
    private fun convertToCqe(cqe: IoUringCqe): Cqe {
        // Convert based on operation type (would need to track this)
        return when {
            cqe.res >= 0 -> ReadResult(cqe.user_data.toLong(), cqe.res)
            else -> ReadResult(cqe.user_data.toLong(), cqe.res)
        }
    }
    
    override suspend fun registerBuffers(buffers: List<ByteArray>) {
        val iovecs = buffers.map { buf ->
            IoVec(buf, buf.size.toULong())
        }
        liburing.io_uring_register_buffers(ring, iovecs, iovecs.size.toUInt())
    }
    
    override suspend fun registerFiles(fds: IntArray) {
        liburing.io_uring_register_files(ring, fds.toList(), fds.size.toUInt())
    }
    
    override suspend fun submitBatch(operations: List<Sqe>) {
        operations.forEach { submission.send(it) }
    }
    
    override fun close() {
        liburing.io_uring_queue_exit(ring)
        submission.close()
    }
}

/**
 * Adapter that exposes io_uring as NIO
 */
private class UringNioAdapter : NioEngine {
    private val liburing = createNativeLiburing()
    private val ring = IoUring(
        sq = SubmissionQueue(256u, 255u, 0u, 0u, mutableListOf()),
        cq = CompletionQueue(256u, 255u, 0u, 0u, mutableListOf())
    )
    
    init {
        liburing.io_uring_queue_init(256u, ring, 0u)
    }
    
    override suspend fun submitOp(op: NioOp) {
        val sqe = liburing.io_uring_get_sqe(ring) ?: return
        
        when (op) {
            is NioOp.Read -> {
                liburing.io_uring_prep_read(sqe, op.fd, op.buffer, op.buffer.size.toUInt(), op.offset)
                liburing.io_uring_sqe_set_data64(sqe, op.userData)
            }
            // ... handle other operations ...
        }
        
        liburing.io_uring_submit(ring)
    }
    
    override suspend fun submitBatch(ops: Indexed<NioOp>) {
        ops.forEach { submitOp(it) }
    }
    
    override fun pollCompletions(maxEvents: Int, timeoutMs: Long): Indexed<NioCompletion> {
        val completions = mutableListOf<NioCompletion>()
        
        repeat(maxEvents) {
            val cqeResult = Join<IoUringCqe?, Int>(null, -1)
            val ret = liburing.io_uring_peek_cqe(ring, cqeResult)
            
            if (ret == 0 && cqeResult.l != null) {
                val cqe = cqeResult.l!!
                completions.add(NioCompletion(cqe.user_data, cqe.res, cqe.flags))
                liburing.io_uring_cqe_seen(ring, cqe)
            } else {
                return@repeat
            }
        }
        
        return completions
    }
    
    override fun close() {
        liburing.io_uring_queue_exit(ring)
    }
}

// Platform detection functions
expect fun isLinuxWithUring(): Boolean
expect fun isMacOS(): Boolean
expect fun isLinux(): Boolean

// Platform-specific implementations
expect fun createNativeLiburing(): LiburingEmulator
expect fun createKqueueLiburing(): LiburingEmulator
expect fun createEpollLiburing(): LiburingEmulator
expect fun KqueueNioEngine(): NioEngine
expect fun EpollNioEngine(): NioEngine