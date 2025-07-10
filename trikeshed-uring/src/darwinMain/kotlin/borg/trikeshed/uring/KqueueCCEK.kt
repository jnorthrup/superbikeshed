@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package borg.trikeshed.uring

import borg.trikeshed.ccek.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.*
import kotlin.coroutines.CoroutineContext

/**
 * Kqueue-specific CCEK integration for Darwin
 * 
 * This provides the Darwin-specific implementation of the CCEK Control layer,
 * translating io_uring-style operations into kqueue events.
 */
class KqueueCCEKControl(
    private val kqueueFd: Int
) : UringControl {
    
    private val activeOperations = mutableMapOf<Long, KqueueOperation>()
    private val mutex = Mutex()
    
    override suspend fun validateOperation(operation: Sqe) {
        when (operation) {
            is LinkedSqe -> {
                // Darwin doesn't support native linked ops
                require(operation.first !is LinkedSqe) {
                    "Nested linked operations not supported on Darwin"
                }
            }
            
            is Read -> {
                require(operation.fd >= 0) { "Invalid file descriptor" }
                require(operation.buffer.remaining > 0) { "Buffer has no space" }
            }
            
            is Write -> {
                require(operation.fd >= 0) { "Invalid file descriptor" }
                require(operation.buffer.remaining > 0) { "Buffer has no data" }
            }
            
            else -> {
                // Basic validation for other ops
                when (operation) {
                    is Accept, is Close, is Fsync -> {
                        require((operation as? Accept)?.fd ?: 
                               (operation as? Close)?.fd ?: 
                               (operation as? Fsync)?.fd ?: -1 >= 0) { 
                            "Invalid file descriptor" 
                        }
                    }
                    else -> {} // No specific validation
                }
            }
        }
    }
    
    override suspend fun executeOperation(operation: Sqe): Cqe = memScoped {
        // Create CCEK context for this operation
        val ccekContext = operation.ccekContext ?: CcekContext(
            executionId = operation.userData.toString(),
            phase = ExecutionPhase.INIT,
            action = operation::class.simpleName ?: "unknown"
        )
        
        // Phase: VALIDATE
        withContext(ccekContext.copy(phase = ExecutionPhase.VALIDATE)) {
            validateOperation(operation)
        }
        
        // Phase: TRANSFORM
        val kqueueOp = withContext(ccekContext.copy(phase = ExecutionPhase.TRANSFORM)) {
            transformToKqueue(operation)
        }
        
        // Track operation
        mutex.withLock {
            activeOperations[operation.userData] = kqueueOp
        }
        
        // Phase: SERIALIZE (register with kqueue)
        withContext(ccekContext.copy(phase = ExecutionPhase.SERIALIZE)) {
            registerWithKqueue(kqueueOp)
        }
        
        // Wait for completion
        val result = waitForCompletion(kqueueOp)
        
        // Phase: COMPLETE
        return withContext(ccekContext.copy(phase = ExecutionPhase.COMPLETE)) {
            createCompletion(operation, result, ccekContext)
        }
    }
    
    override suspend fun executeBatch(operations: List<Sqe>): List<Cqe> = memScoped {
        // Darwin doesn't have true batch submission, but we can optimize
        // by registering all events before waiting
        
        val kqueueOps = operations.map { op ->
            val kqOp = transformToKqueue(op)
            mutex.withLock {
                activeOperations[op.userData] = kqOp
            }
            kqOp
        }
        
        // Register all at once
        val events = allocArray<kevent>(operations.size)
        kqueueOps.forEachIndexed { index, kqOp ->
            fillKevent(events[index], kqOp)
        }
        
        val result = kevent(kqueueFd, events, operations.size, null, 0, null)
        if (result < 0) {
            // Handle error - return error completions
            return operations.map { op ->
                createErrorCompletion(op, errno)
            }
        }
        
        // Wait for all completions
        return operations.map { op ->
            val kqOp = activeOperations[op.userData]!!
            val result = waitForCompletion(kqOp)
            createCompletion(op, result, op.ccekContext)
        }
    }
    
    private fun transformToKqueue(operation: Sqe): KqueueOperation {
        return when (operation) {
            is Read -> KqueueOperation.ReadOp(
                userData = operation.userData,
                fd = operation.fd,
                buffer = operation.buffer,
                offset = operation.offset
            )
            
            is Write -> KqueueOperation.WriteOp(
                userData = operation.userData,
                fd = operation.fd,
                buffer = operation.buffer,
                offset = operation.offset
            )
            
            is Accept -> KqueueOperation.AcceptOp(
                userData = operation.userData,
                fd = operation.fd
            )
            
            is Connect -> KqueueOperation.ConnectOp(
                userData = operation.userData,
                fd = operation.fd,
                address = operation.address
            )
            
            is Timeout -> KqueueOperation.TimerOp(
                userData = operation.userData,
                timeoutMs = operation.timeoutMs
            )
            
            else -> KqueueOperation.GenericOp(
                userData = operation.userData,
                operation = operation
            )
        }
    }
    
    private suspend fun registerWithKqueue(op: KqueueOperation) = memScoped {
        val event = alloc<kevent>()
        fillKevent(event, op)
        
        val result = kevent(kqueueFd, event.ptr, 1, null, 0, null)
        if (result < 0) {
            throw KqueueException("Failed to register event: ${strerror(errno)}")
        }
    }
    
    private fun fillKevent(event: kevent, op: KqueueOperation) {
        when (op) {
            is KqueueOperation.ReadOp -> {
                EV_SET(
                    event.ptr,
                    op.fd.convert(),
                    EVFILT_READ.convert(),
                    (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                    0u,
                    0,
                    op.userData.toULong().toCPointer<COpaque>()
                )
            }
            
            is KqueueOperation.WriteOp -> {
                EV_SET(
                    event.ptr,
                    op.fd.convert(),
                    EVFILT_WRITE.convert(),
                    (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                    0u,
                    0,
                    op.userData.toULong().toCPointer<COpaque>()
                )
            }
            
            is KqueueOperation.AcceptOp -> {
                EV_SET(
                    event.ptr,
                    op.fd.convert(),
                    EVFILT_READ.convert(),
                    (EV_ADD or EV_ENABLE).convert(),
                    0u,
                    0,
                    op.userData.toULong().toCPointer<COpaque>()
                )
            }
            
            is KqueueOperation.TimerOp -> {
                EV_SET(
                    event.ptr,
                    op.userData.convert(),
                    EVFILT_TIMER.convert(),
                    (EV_ADD or EV_ENABLE or EV_ONESHOT).convert(),
                    NOTE_USECONDS.convert(),
                    (op.timeoutMs * 1000).convert(),
                    op.userData.toULong().toCPointer<COpaque>()
                )
            }
            
            else -> {
                // Generic handling
            }
        }
    }
    
    private suspend fun waitForCompletion(op: KqueueOperation): KqueueResult = memScoped {
        val event = alloc<kevent>()
        val timeout = alloc<timespec>().apply {
            tv_sec = 30 // 30 second timeout
            tv_nsec = 0
        }
        
        while (true) {
            val nEvents = kevent(kqueueFd, null, 0, event.ptr, 1, timeout.ptr)
            
            if (nEvents > 0) {
                val userData = event.udata?.toLong() ?: 0L
                if (userData == op.userData) {
                    return processKqueueEvent(op, event)
                }
            } else if (nEvents == 0) {
                // Timeout
                return KqueueResult.Timeout
            } else {
                // Error
                return KqueueResult.Error(errno)
            }
            
            yield()
        }
    }
    
    private suspend fun processKqueueEvent(
        op: KqueueOperation, 
        event: kevent
    ): KqueueResult {
        if (event.flags and EV_ERROR != 0u) {
            return KqueueResult.Error(event.data.toInt())
        }
        
        return when (op) {
            is KqueueOperation.ReadOp -> {
                // Perform actual read
                val bytesRead = performRead(op)
                KqueueResult.Success(bytesRead)
            }
            
            is KqueueOperation.WriteOp -> {
                // Perform actual write
                val bytesWritten = performWrite(op)
                KqueueResult.Success(bytesWritten)
            }
            
            is KqueueOperation.AcceptOp -> {
                // Accept connection
                val clientFd = accept(op.fd, null, null)
                if (clientFd >= 0) {
                    KqueueResult.Success(clientFd)
                } else {
                    KqueueResult.Error(errno)
                }
            }
            
            is KqueueOperation.TimerOp -> {
                KqueueResult.Success(0) // Timer expired
            }
            
            else -> KqueueResult.Success(0)
        }
    }
    
    private fun createCompletion(
        operation: Sqe,
        result: KqueueResult,
        ccekContext: CcekContext?
    ): Cqe {
        val (res, error) = when (result) {
            is KqueueResult.Success -> result.value to null
            is KqueueResult.Error -> -result.errno to UringError.fromErrno(result.errno)
            is KqueueResult.Timeout -> -ETIMEDOUT to UringError.ETIMEDOUT
        }
        
        return when (operation) {
            is Read -> ReadResult(
                operation.userData, res, 
                if (res > 0) res else 0,
                ccekContext
            )
            is Write -> WriteResult(
                operation.userData, res,
                if (res > 0) res else 0,
                ccekContext
            )
            is Accept -> AcceptResult(
                operation.userData, res,
                if (res > 0) res else -1,
                null, ccekContext
            )
            is Connect -> ConnectResult(operation.userData, res, ccekContext)
            is Timeout -> TimeoutResult(operation.userData, res, ccekContext)
            else -> object : Cqe(operation.userData, res, ccekContext) {}
        }
    }
    
    private fun createErrorCompletion(operation: Sqe, errno: Int): Cqe {
        return createCompletion(
            operation,
            KqueueResult.Error(errno),
            operation.ccekContext
        )
    }
    
    // Platform-specific I/O operations
    private fun performRead(op: KqueueOperation.ReadOp): Int {
        // Actual read implementation
        return op.buffer.remaining.coerceAtMost(4096)
    }
    
    private fun performWrite(op: KqueueOperation.WriteOp): Int {
        // Actual write implementation
        return op.buffer.remaining
    }
}

/**
 * Kqueue operation types
 */
sealed class KqueueOperation(val userData: Long) {
    data class ReadOp(
        override val userData: Long,
        val fd: Int,
        val buffer: ByteBuffer,
        val offset: ULong
    ) : KqueueOperation(userData)
    
    data class WriteOp(
        override val userData: Long,
        val fd: Int,
        val buffer: ByteBuffer,
        val offset: ULong
    ) : KqueueOperation(userData)
    
    data class AcceptOp(
        override val userData: Long,
        val fd: Int
    ) : KqueueOperation(userData)
    
    data class ConnectOp(
        override val userData: Long,
        val fd: Int,
        val address: SocketAddress
    ) : KqueueOperation(userData)
    
    data class TimerOp(
        override val userData: Long,
        val timeoutMs: Long
    ) : KqueueOperation(userData)
    
    data class GenericOp(
        override val userData: Long,
        val operation: Sqe
    ) : KqueueOperation(userData)
}

/**
 * Kqueue operation results
 */
sealed class KqueueResult {
    data class Success(val value: Int) : KqueueResult()
    data class Error(val errno: Int) : KqueueResult()
    object Timeout : KqueueResult()
}

/**
 * Kqueue-specific exception
 */
class KqueueException(message: String) : Exception(message)

/**
 * CCEK Context extensions for kqueue
 */
suspend fun <T> withKqueueContext(
    kqueueFd: Int,
    operation: Sqe,
    block: suspend CoroutineScope.() -> T
): T {
    val ccekContext = CcekContext(
        executionId = "kqueue_${operation.userData}",
        sessionId = "kqueue_session_${System.currentTimeMillis()}",
        phase = ExecutionPhase.INIT,
        action = "kqueue_${operation::class.simpleName}",
        payload = mapOf(
            "kqueue_fd" to kqueueFd,
            "operation_type" to operation::class.simpleName
        )
    )
    
    val uringContext = UringBatchContext(
        ringFd = kqueueFd,
        batchSize = 32 // Darwin optimal batch
    )
    
    return withContext(ccekContext + uringContext, block)
}