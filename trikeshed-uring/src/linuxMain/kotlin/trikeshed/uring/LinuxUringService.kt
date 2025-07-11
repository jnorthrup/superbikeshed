package trikeshed.uring

import kotlinx.cinterop.*
import trikeshed.uring.native.linux.*

// Data class to hold StableRef and operation details
private data class LinuxPendingOperation(
    val originalUserData: Long,
    val pinnedBuffers: List<StableRef<ByteArray>>,
    val type: Int
)

// Map to track pending operations
private val pendingOperations = mutableMapOf<Long, LinuxPendingOperation>()
private var nextInternalId: Long = 1000000L

actual class UringContext internal constructor(
    internal val nativePtr: COpaquePointer?
) {
    fun isValid(): Boolean = nativePtr != null
}

actual fun createUringService(): UringService = LinuxUringService

object LinuxUringService : UringService {
    
    override fun createContext(entries: Int, flags: Int): UringContext {
        if (entries <= 0) throw IllegalArgumentException("Entries must be positive")
        
        val contextPtr = uring_setup_context(entries.toUInt(), flags.toUInt())
        if (contextPtr == null) {
            throw RuntimeException("Failed to create io_uring context")
        }
        
        return UringContext(contextPtr)
    }
    
    override fun submitOperations(context: UringContext, operations: List<IoOperation>): Int {
        if (!context.isValid()) throw IllegalArgumentException("Invalid context")
        if (operations.isEmpty()) return 0
        
        return memScoped {
            val opsArray = allocArray<uring_operation>(operations.size)
            
            operations.forEachIndexed { index, op ->
                val nativeOp = opsArray[index]
                val pinnedBuffers = mutableListOf<StableRef<ByteArray>>()
                
                // Ensure unique user data
                val userData = if (op.userData == 0L) nextInternalId++ else op.userData
                
                nativeOp.fd = op.fd
                nativeOp.user_data = userData
                
                when (op) {
                    is Read -> {
                        nativeOp.type = OP_READ
                        nativeOp.offset = op.offset
                        val bufferRef = StableRef.create(op.buffer)
                        pinnedBuffers.add(bufferRef)
                        nativeOp.buffer = op.buffer.refTo(0).getPointer(this)
                        nativeOp.buffer_len = op.buffer.size.toULong()
                    }
                    
                    is ReadV -> {
                        nativeOp.type = OP_READV
                        nativeOp.offset = op.offset
                        nativeOp.iovec_count = op.buffers.size
                        
                        val iovecs = allocArray<uring_iovec>(op.buffers.size)
                        op.buffers.forEachIndexed { i, buffer ->
                            val bufferRef = StableRef.create(buffer)
                            pinnedBuffers.add(bufferRef)
                            iovecs[i].iov_base = buffer.refTo(0).getPointer(this)
                            iovecs[i].iov_len = buffer.size.toULong()
                        }
                        nativeOp.iovecs = iovecs
                    }
                    
                    is Write -> {
                        nativeOp.type = OP_WRITE
                        nativeOp.offset = op.offset
                        val bufferRef = StableRef.create(op.buffer)
                        pinnedBuffers.add(bufferRef)
                        nativeOp.buffer = op.buffer.refTo(0).getPointer(this)
                        nativeOp.buffer_len = op.buffer.size.toULong()
                    }
                    
                    is WriteV -> {
                        nativeOp.type = OP_WRITEV
                        nativeOp.offset = op.offset
                        nativeOp.iovec_count = op.buffers.size
                        
                        val iovecs = allocArray<uring_iovec>(op.buffers.size)
                        op.buffers.forEachIndexed { i, buffer ->
                            val bufferRef = StableRef.create(buffer)
                            pinnedBuffers.add(bufferRef)
                            iovecs[i].iov_base = buffer.refTo(0).getPointer(this)
                            iovecs[i].iov_len = buffer.size.toULong()
                        }
                        nativeOp.iovecs = iovecs
                    }
                    
                    is Fsync -> {
                        nativeOp.type = OP_FSYNC
                        nativeOp.fsync_data_only = if (op.fsyncDataOnly) 1 else 0
                    }
                    
                    is Accept -> {
                        nativeOp.type = OP_ACCEPT
                    }
                    
                    is Send -> {
                        nativeOp.type = OP_SEND
                        nativeOp.flags = op.flags
                        val bufferRef = StableRef.create(op.buffer)
                        pinnedBuffers.add(bufferRef)
                        nativeOp.buffer = op.buffer.refTo(0).getPointer(this)
                        nativeOp.buffer_len = op.buffer.size.toULong()
                    }
                    
                    is Receive -> {
                        nativeOp.type = OP_RECV
                        nativeOp.flags = op.flags
                        val bufferRef = StableRef.create(op.buffer)
                        pinnedBuffers.add(bufferRef)
                        nativeOp.buffer = op.buffer.refTo(0).getPointer(this)
                        nativeOp.buffer_len = op.buffer.size.toULong()
                    }
                    
                    is Close -> {
                        nativeOp.type = OP_CLOSE
                    }
                    
                    else -> throw IllegalArgumentException("Unsupported operation type: ${op::class}")
                }
                
                // Store operation info for cleanup after completion
                pendingOperations[userData] = LinuxPendingOperation(
                    originalUserData = op.userData,
                    pinnedBuffers = pinnedBuffers,
                    type = nativeOp.type
                )
            }
            
            val result = uring_submit_operations(
                context.nativePtr,
                opsArray,
                operations.size
            )
            
            if (result < 0) {
                // Clean up on error
                operations.forEach { op ->
                    pendingOperations[op.userData]?.pinnedBuffers?.forEach { it.dispose() }
                    pendingOperations.remove(op.userData)
                }
                throw RuntimeException("Failed to submit operations: $result")
            }
            
            result
        }
    }
    
    override fun pollCompletions(
        context: UringContext,
        maxCompletions: Int,
        timeoutMs: Long
    ): List<IoCompletion> {
        if (!context.isValid()) throw IllegalArgumentException("Invalid context")
        if (maxCompletions <= 0) return emptyList()
        
        return memScoped {
            val completions = allocArray<uring_completion>(maxCompletions)
            
            val count = uring_poll_completions(
                context.nativePtr,
                completions,
                maxCompletions,
                timeoutMs
            )
            
            if (count < 0) {
                throw RuntimeException("Failed to poll completions: $count")
            }
            
            val results = mutableListOf<IoCompletion>()
            
            for (i in 0 until count) {
                val completion = completions[i]
                val userData = completion.user_data
                
                // Clean up pinned buffers
                pendingOperations[userData]?.let { pendingOp ->
                    pendingOp.pinnedBuffers.forEach { it.dispose() }
                    pendingOperations.remove(userData)
                }
                
                results.add(IoCompletion(
                    userData = userData,
                    result = completion.result
                ))
            }
            
            results
        }
    }
    
    override fun destroyContext(context: UringContext) {
        if (!context.isValid()) throw IllegalArgumentException("Invalid context")
        
        // Clean up any remaining pending operations
        pendingOperations.values.forEach { op ->
            op.pinnedBuffers.forEach { it.dispose() }
        }
        pendingOperations.clear()
        
        uring_cleanup_context(context.nativePtr)
    }
}