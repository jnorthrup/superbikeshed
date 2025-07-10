@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.uring

import borg.trikeshed.ccek.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.coroutines.CoroutineContext

/**
 * Darwin (macOS) implementation of TrikeUring using kqueue
 * 
 * This is the "beer goggles" - making kqueue look and feel like io_uring.
 * All operations are translated to kqueue events and processed asynchronously.
 */
class DarwinTrikeUring(
    override val orchestrator: CCEKUringOrchestrator,
    private val config: UringConfig,
    scope: CoroutineScope
) : TrikeUring {
    
    // Coroutine scope for this uring instance
    override val coroutineContext: CoroutineContext = scope.coroutineContext + Job()
    
    // The kqueue file descriptor
    private val kqueueFd: Int = kqueue()
    
    // Submission channel - operations are queued here
    override val submission: SendChannel<Sqe> = Channel(config.sqeDepth)
    
    // Completion flow - results are published here
    private val completionChannel = Channel<Cqe>(config.cqeDepth)
    override val completion: Flow<Cqe> = completionChannel.receiveAsFlow()
    
    // Registered buffers (emulated)
    private val registeredBuffers = mutableListOf<ByteBuffer>()
    private val registeredFiles = mutableListOf<Int>()
    
    // Active operations tracking
    private val activeOps = mutableMapOf<Long, Sqe>()
    private val mutex = Mutex()
    
    init {
        require(kqueueFd >= 0) { "Failed to create kqueue: ${strerror(errno)}" }
        
        // Launch submission processor
        launch {
            processSubmissions()
        }
        
        // Launch completion processor
        launch {
            processCompletions()
        }
    }
    
    /**
     * Process submissions from the channel and register with kqueue
     */
    private suspend fun processSubmissions() {
        for (sqe in submission) {
            try {
                when (sqe) {
                    is LinkedSqe -> submitLinked(sqe)
                    else -> submitSingle(sqe)
                }
            } catch (e: Exception) {
                // Send error completion
                completionChannel.send(
                    createErrorCqe(sqe, -1, e.message)
                )
            }
        }
    }
    
    /**
     * Submit a single operation to kqueue
     */
    private suspend fun submitSingle(sqe: Sqe) {
        mutex.withLock {
            activeOps[sqe.userData] = sqe
        }
        
        memScoped {
            val event = alloc<kevent>()
            
            when (sqe) {
                is Read -> {
                    // Register read interest
                    EV_SET(
                        event.ptr, 
                        sqe.fd.convert(), 
                        EVFILT_READ.convert(),
                        (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                        0u,
                        0,
                        sqe.userData.toULong().toCPointer<COpaque>()
                    )
                }
                
                is Write -> {
                    // Register write interest
                    EV_SET(
                        event.ptr,
                        sqe.fd.convert(),
                        EVFILT_WRITE.convert(),
                        (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                        0u,
                        0,
                        sqe.userData.toULong().toCPointer<COpaque>()
                    )
                }
                
                is Accept -> {
                    // Register accept interest
                    EV_SET(
                        event.ptr,
                        sqe.fd.convert(),
                        EVFILT_READ.convert(),
                        (EV_ADD or EV_ENABLE).convert(),
                        0u,
                        0,
                        sqe.userData.toULong().toCPointer<COpaque>()
                    )
                }
                
                is Connect -> {
                    // For connect, we monitor write-ability
                    EV_SET(
                        event.ptr,
                        sqe.fd.convert(),
                        EVFILT_WRITE.convert(),
                        (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                        0u,
                        0,
                        sqe.userData.toULong().toCPointer<COpaque>()
                    )
                }
                
                is Timeout -> {
                    // Use EVFILT_TIMER for timeouts
                    EV_SET(
                        event.ptr,
                        sqe.userData.convert(), // Use userData as timer ID
                        EVFILT_TIMER.convert(),
                        (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                        NOTE_USECONDS.convert(),
                        (sqe.timeoutMs * 1000).convert(), // Convert to microseconds
                        sqe.userData.toULong().toCPointer<COpaque>()
                    )
                }
                
                else -> {
                    // Other operations need custom handling
                    handleSpecialOperation(sqe)
                    return
                }
            }
            
            // Submit to kqueue
            val result = kevent(kqueueFd, event.ptr, 1, null, 0, null)
            if (result < 0) {
                val error = errno
                completionChannel.send(
                    createErrorCqe(sqe, -error, strerror(error)?.toKString())
                )
            }
        }
    }
    
    /**
     * Process completions from kqueue
     */
    private suspend fun processCompletions() = memScoped {
        val events = allocArray<kevent>(config.ringSize)
        val timeout = alloc<timespec>().apply {
            tv_sec = 0
            tv_nsec = 10_000_000 // 10ms
        }
        
        while (isActive) {
            val nEvents = kevent(
                kqueueFd, 
                null, 0, 
                events, config.ringSize, 
                timeout.ptr
            )
            
            if (nEvents > 0) {
                for (i in 0 until nEvents) {
                    val event = events[i]
                    val userData = event.udata?.toLong() ?: continue
                    
                    val sqe = mutex.withLock {
                        activeOps.remove(userData)
                    } ?: continue
                    
                    val cqe = processKqueueEvent(sqe, event)
                    
                    // Execute through orchestrator for CCEK processing
                    val orchestratedCqe = orchestrator.execute(sqe)
                    
                    completionChannel.send(cqe)
                }
            }
            
            yield() // Allow other coroutines to run
        }
    }
    
    /**
     * Process a kqueue event into a completion
     */
    private suspend fun processKqueueEvent(sqe: Sqe, event: kevent): Cqe {
        return when (sqe) {
            is Read -> {
                if (event.flags and EV_ERROR != 0u) {
                    ReadResult(sqe.userData, -event.data.toInt())
                } else {
                    // Perform the actual read
                    val bytesRead = performRead(sqe)
                    ReadResult(sqe.userData, bytesRead, bytesRead)
                }
            }
            
            is Write -> {
                if (event.flags and EV_ERROR != 0u) {
                    WriteResult(sqe.userData, -event.data.toInt())
                } else {
                    // Perform the actual write
                    val bytesWritten = performWrite(sqe)
                    WriteResult(sqe.userData, bytesWritten, bytesWritten)
                }
            }
            
            is Accept -> {
                if (event.flags and EV_ERROR != 0u) {
                    AcceptResult(sqe.userData, -event.data.toInt())
                } else {
                    val clientFd = accept(sqe.fd, null, null)
                    AcceptResult(sqe.userData, clientFd, clientFd)
                }
            }
            
            is Connect -> {
                if (event.flags and EV_ERROR != 0u) {
                    ConnectResult(sqe.userData, -event.data.toInt())
                } else {
                    // Check if connection completed
                    val error = getSocketError(sqe.fd)
                    ConnectResult(sqe.userData, if (error == 0) 0 else -error)
                }
            }
            
            is Timeout -> {
                TimeoutResult(sqe.userData, 0) // Timeout expired
            }
            
            else -> {
                // Generic completion
                object : Cqe(sqe.userData, 0) {}
            }
        }
    }
    
    /**
     * Submit linked operations
     */
    private suspend fun submitLinked(linked: LinkedSqe) {
        // Submit first operation
        submitSingle(linked.first)
        
        // Set up continuation
        launch {
            // Wait for first completion
            val firstResult = completion.first { it.userData == linked.first.userData }
            
            if (firstResult.isSuccess) {
                // Execute continuation
                val next = linked.then(firstResult)
                if (next != null) {
                    submission.send(next)
                }
            }
        }
    }
    
    /**
     * Handle special operations that don't map directly to kqueue
     */
    private suspend fun handleSpecialOperation(sqe: Sqe) {
        when (sqe) {
            is Fsync -> {
                // Execute synchronously and send completion
                val result = fsync(sqe.fd)
                completionChannel.send(
                    FsyncResult(sqe.userData, if (result == 0) 0 else -errno)
                )
            }
            
            is Close -> {
                // Close synchronously
                val result = close(sqe.fd)
                completionChannel.send(
                    object : Cqe(sqe.userData, if (result == 0) 0 else -errno) {}
                )
            }
            
            else -> {
                completionChannel.send(
                    createErrorCqe(sqe, -ENOTSUP, "Operation not supported")
                )
            }
        }
    }
    
    override suspend fun registerBuffers(buffers: List<ByteBuffer>) {
        mutex.withLock {
            registeredBuffers.clear()
            registeredBuffers.addAll(buffers)
        }
    }
    
    override suspend fun registerFiles(fds: IntArray) {
        mutex.withLock {
            registeredFiles.clear()
            registeredFiles.addAll(fds.toList())
        }
    }
    
    override suspend fun submitBatch(operations: List<Sqe>) {
        // Darwin doesn't support true batching, but we can optimize
        operations.forEach { submission.send(it) }
    }
    
    override suspend fun waitCompletions(n: Int): List<Cqe> {
        return (1..n).map { completion.first() }
    }
    
    override fun close() {
        launch {
            submission.close()
            completionChannel.close()
        }
        close(kqueueFd)
        cancel()
    }
    
    // Helper functions
    
    private fun performRead(read: Read): Int {
        // Actual read implementation would go here
        // For now, simulate
        return read.buffer.remaining.coerceAtMost(1024)
    }
    
    private fun performWrite(write: Write): Int {
        // Actual write implementation would go here
        // For now, simulate
        return write.buffer.remaining
    }
    
    private fun getSocketError(fd: Int): Int = memScoped {
        val error = alloc<IntVar>()
        val len = alloc<socklen_tVar>()
        len.value = sizeOf<IntVar>().convert()
        
        getsockopt(fd, SOL_SOCKET, SO_ERROR, error.ptr, len.ptr)
        error.value
    }
    
    private fun createErrorCqe(sqe: Sqe, error: Int, message: String?): Cqe {
        return when (sqe) {
            is Read -> ReadResult(sqe.userData, error)
            is Write -> WriteResult(sqe.userData, error)
            is Accept -> AcceptResult(sqe.userData, error)
            is Connect -> ConnectResult(sqe.userData, error)
            else -> object : Cqe(sqe.userData, error) {}
        }
    }
}

/**
 * Factory implementation for Darwin
 */
actual fun createTrikeUring(
    scope: CoroutineScope,
    config: UringConfig
): TrikeUring {
    val orchestrator = CCEKUringOrchestrator.forDarwin()
    return DarwinTrikeUring(orchestrator, config, scope)
}

/**
 * Architecture detection for Darwin
 */
actual fun getArchitecture(): String {
    return when (Platform.cpuArchitecture) {
        CpuArchitecture.ARM64 -> "arm64"
        CpuArchitecture.X64 -> "x64"
        else -> "unknown"
    }
}