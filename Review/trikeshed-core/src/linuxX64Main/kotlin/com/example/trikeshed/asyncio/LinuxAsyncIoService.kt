package com.example.trikeshed.asyncio

import com.example.trikeshed.asyncio.native.linux.*
import kotlinx.cinterop.*
import platform.posix.EINVAL

// Data class to hold StableRef and original operation details for Linux
private data class LinuxPendingOpData(
    val originalOpUserData: Long,
    val pinnedBuffers: List<StableRef<ByteArray>>,
    val type: Int // Store op type for debugging or specific cleanup if needed
)

// Map to store StableRefs for Linux, keyed by original IoOperation.userData
private val linuxPendingOperationRefs = mutableMapOf<Long, LinuxPendingOpData>()
// If op.userData is 0, we use this to assign one.
private var linuxNextInternalOpId: Long = 1000000 // Start from a different range for debuggability


actual class AsyncIoContext internal constructor(
    internal val nativeCtxPtr: COpaquePointer? // void* from C maps to COpaquePointer
) {
    fun isValid(): Boolean = nativeCtxPtr != null
}

actual fun createAsyncIoService(): AsyncIoService = LinuxAsyncIoService

internal object LinuxAsyncIoService : AsyncIoService {

    override fun createContext(entries: Int, flags: Int): AsyncIoContext {
        if (entries <= 0) throw IllegalArgumentException("Entries must be positive.")
        val contextPtr = setup_io_uring_context(entries.toUInt(), flags.toUInt())
        if (contextPtr == null) {
            throw RuntimeException("Failed to create native io_uring context. Check stderr for C errors.")
        }
        // In the previous step, this was contextPtr.reinterpret().
        // Since setup_io_uring_context returns AsyncIoContextLinux (which is void* / COpaquePointer),
        // no reinterpret() is needed to store it as COpaquePointer.
        return AsyncIoContext(contextPtr)
    }

    override fun destroyContext(context: AsyncIoContext) {
        if (context.nativeCtxPtr != null) {
            // The C function expects AsyncIoContextLinux (COpaquePointer).
            cleanup_io_uring_context(context.nativeCtxPtr)
            // Simplified cleanup. In a real app, only clear refs for THIS context.
            val keysToRemove = linuxPendingOperationRefs.keys.toList() // Avoid concurrent modification
            keysToRemove.forEach { key ->
                linuxPendingOperationRefs.remove(key)?.pinnedBuffers?.forEach { it.dispose() }
            }
        }
    }

    override fun submitOperations(context: AsyncIoContext, operations: List<IoOperation>): Int {
        val nativeContext = context.nativeCtxPtr ?: return -EINVAL
        if (operations.isEmpty()) return 0

        val arena = Arena()
        try {
            val nativeOpsArray = arena.allocArray<submitted_op_linux>(operations.size)
            // Store data for ops prepared in this batch, to manage StableRefs correctly based on submission outcome.
            val preparedOpsData = mutableListOf<LinuxPendingOpData>()

            for (index in operations.indices) {
                val op = operations[index]
                val currentActualUserData = if (op.userData == 0L) linuxNextInternalOpId++ else op.userData
                op.userData = currentActualUserData // Ensure op carries the ID used for registry

                nativeOpsArray[index].fd = op.fd
                nativeOpsArray[index].user_data = op.userData // This is the (potentially generated) unique ID
                nativeOpsArray[index].offset = when (op) {
                    is IoOperation.ReadV -> op.offset
                    is IoOperation.WriteV -> op.offset
                    else -> -1L
                }

                val currentPinnedBuffers = mutableListOf<StableRef<ByteArray>>()

                when (op) {
                    is IoOperation.ReadV -> {
                        nativeOpsArray[index].type = KOTLIN_OP_READV
                        if (op.buffers.isNotEmpty()) {
                            val iovecs = arena.allocArray<kotlin_iovec>(op.buffers.size)
                            op.buffers.forEachIndexed { iovec_idx, byteArray ->
                                val stableRef = StableRef.create(byteArray)
                                currentPinnedBuffers.add(stableRef)
                                iovecs[iovec_idx].iov_base = stableRef.get().refTo(0)
                                iovecs[iovec_idx].iov_len = byteArray.size.convert()
                            }
                            nativeOpsArray[index].iovecs = iovecs
                            nativeOpsArray[index].iovec_count = op.buffers.size
                        } else { nativeOpsArray[index].iovec_count = 0; nativeOpsArray[index].iovecs = null; }
                    }
                    is IoOperation.WriteV -> {
                        nativeOpsArray[index].type = KOTLIN_OP_WRITEV
                        if (op.buffers.isNotEmpty()) {
                            val iovecs = arena.allocArray<kotlin_iovec>(op.buffers.size)
                            op.buffers.forEachIndexed { iovec_idx, byteArray ->
                                val stableRef = StableRef.create(byteArray)
                                currentPinnedBuffers.add(stableRef)
                                iovecs[iovec_idx].iov_base = stableRef.get().refTo(0)
                                iovecs[iovec_idx].iov_len = byteArray.size.convert()
                            }
                            nativeOpsArray[index].iovecs = iovecs
                            nativeOpsArray[index].iovec_count = op.buffers.size
                        } else { nativeOpsArray[index].iovec_count = 0; nativeOpsArray[index].iovecs = null; }
                    }
                    is IoOperation.Fsync -> {
                        nativeOpsArray[index].type = KOTLIN_OP_FSYNC
                        nativeOpsArray[index].fsync_data_only = if (op.fsyncDataOnly) 1 else 0
                    }
                     is IoOperation.Recv -> {
                        nativeOpsArray[index].type = KOTLIN_OP_RECV
                        val stableRef = StableRef.create(op.buffer)
                        currentPinnedBuffers.add(stableRef)
                        nativeOpsArray[index].buffer = stableRef.get().refTo(0)
                        nativeOpsArray[index].buffer_len = op.buffer.size.convert()
                        nativeOpsArray[index].flags = op.flags
                    }
                    is IoOperation.Send -> {
                        nativeOpsArray[index].type = KOTLIN_OP_SEND
                        val stableRef = StableRef.create(op.buffer)
                        currentPinnedBuffers.add(stableRef)
                        nativeOpsArray[index].buffer = stableRef.get().refTo(0)
                        nativeOpsArray[index].buffer_len = op.buffer.size.convert()
                        nativeOpsArray[index].flags = op.flags
                    }
                }
                preparedOpsData.add(LinuxPendingOpData(op.userData, currentPinnedBuffers, nativeOpsArray[index].type))
            }

            // nativeContext is COpaquePointer, C function expects AsyncIoContextLinux (void*). No reinterpret needed.
            val submittedCount = submit_io_operations_linux(nativeContext, nativeOpsArray, operations.size)

            if (submittedCount >= 0) {
                // Add successfully submitted ops to registry
                preparedOpsData.take(submittedCount).forEach { data ->
                    linuxPendingOperationRefs[data.originalOpUserData] = data
                }
                // If submittedCount < operations.size, some ops were not submitted by C layer (e.g. SQ full)
                // Dispose StableRefs for ops that were prepared but not submitted.
                if (submittedCount < preparedOpsData.size) {
                    preparedOpsData.drop(submittedCount).forEach { data ->
                        data.pinnedBuffers.forEach { it.dispose() }
                    }
                }
            } else { // Error in submission (negative return value from C)
                // Clean up all StableRefs prepared in this batch if submit call itself failed
                preparedOpsData.forEach { data ->
                    data.pinnedBuffers.forEach { it.dispose() }
                }
            }
            return submittedCount
        } finally {
            arena.clear()
        }
    }

    override fun pollCompletions(
        context: AsyncIoContext,
        completions: MutableList<IoCompletion>,
        minCompletions: Int,
        timeoutMillis: Int
    ): Int {
        val nativeContext = context.nativeCtxPtr ?: return -EINVAL

        val maxNativeCompletions = 32 // Arbitrary batch size for retrieving completions
        val arena = Arena()
        try {
            val nativeCompletionsArray = arena.allocArray<completion_result_linux>(maxNativeCompletions)

            // nativeContext is COpaquePointer, C function expects AsyncIoContextLinux (void*).
            val numCompleted = poll_io_completions_linux(
                nativeContext,
                nativeCompletionsArray,
                maxNativeCompletions,
                minCompletions,
                timeoutMillis
            )

            if (numCompleted > 0) {
                for (i in 0 until numCompleted) {
                    val nativeCompletion = nativeCompletionsArray[i]
                    completions.add(IoCompletion(
                        userData = nativeCompletion.user_data, // This is the unique ID
                        result = nativeCompletion.result
                    ))
                    // Clean up pinned buffers for this completed operation
                    linuxPendingOperationRefs.remove(nativeCompletion.user_data)?.pinnedBuffers?.forEach { it.dispose() }
                }
            } else if (numCompleted < 0) {
                 // Error during poll, result from C is -errno
                 println("Error polling Linux completions: native_poll returned $numCompleted (errno: ${-numCompleted})")
            }
            return numCompleted
        } finally {
            arena.clear()
        }
    }
}
