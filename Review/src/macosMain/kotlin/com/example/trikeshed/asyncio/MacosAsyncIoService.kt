package com.example.trikeshed.asyncio

import com.example.trikeshed.asyncio.native.macos.*
import kotlinx.cinterop.*
import platform.posix.EINVAL
// ENOSYS is not directly used by Kotlin
import platform.posix.timespec
import platform.posix.O_NONBLOCK // For fcntl
import platform.posix.F_GETFL // For fcntl
import platform.posix.F_SETFL // For fcntl
import platform.posix.fcntl // For fcntl

actual class AsyncIoContext internal constructor(internal val kqfd: AsyncIoContextMacos) { // AsyncIoContextMacos is Int
    fun isValid(): Boolean = kqfd >= 0
}

actual fun createAsyncIoService(): AsyncIoService = MacosAsyncIoService

private data class MacosPendingOpInternalData(
    val originalOpUserData: Long, // The user_data from Kotlin's IoOperation
    val pinnedBuffers: List<StableRef<ByteArray>>,
    val type: Int // Store original operation type for cleanup if needed
)

// This registry maps the original IoOperation.userData (ensured unique)
// to the MacosPendingOpInternalData which holds stable refs.
private val macosPendingOpsRegistry = mutableMapOf<Long, MacosPendingOpInternalData>()
private var nextInternalOpId: Long = 1 // To ensure unique IDs if op.userData is not unique or 0


internal object MacosAsyncIoService : AsyncIoService {

    override fun createContext(entries: Int, flags: Int): AsyncIoContext {
        // entries and flags are not directly used by kqueue setup but are part of the common interface
        val kq = setup_kqueue_context()
        if (kq == -1) {
            throw RuntimeException("Failed to create native kqueue context. Check native logs (stderr) for details.")
        }
        return AsyncIoContext(kq)
    }

    override fun destroyContext(context: AsyncIoContext) {
        if (context.kqfd >= 0) {
            cleanup_kqueue_context(context.kqfd)
            // Cleanup: Iterate and dispose any remaining StableRefs
            // This is simplified; a robust solution would associate pending ops with a context explicitly
            val keysToRemove = mutableListOf<Long>()
            macosPendingOpsRegistry.forEach { (userData, opData) ->
                // This check is not perfect as we don't store context in opData.
                // A proper multi-context system would need to track which ops belong to which context.
                // For now, assume global cleanup or that polling handles all ops.
                 opData.pinnedBuffers.forEach { it.dispose() }
                 keysToRemove.add(userData)
            }
            keysToRemove.forEach { macosPendingOpsRegistry.remove(it) }
        }
    }

    override fun submitOperations(context: AsyncIoContext, operations: List<IoOperation>): Int {
        val kqfd = context.kqfd
        if (kqfd < 0) return -EINVAL
        if (operations.isEmpty()) return 0

        val arena = Arena()
        var successfullyPreparedForC = 0
        // Can't use operations.size directly for nativeOpsArray if some ops are skipped (e.g. fcntl fails)
        // So, build a list of successfully prepared ops first.
        val preparedNativeOps = mutableListOf<Pair<pinned_op_macos, MacosPendingOpInternalData>>()

        try {
            for (op in operations) {
                // Ensure FD is non-blocking. This should ideally be done when FD is obtained.
                val currentFdFlags = fcntl(op.fd, F_GETFL)
                if (currentFdFlags == -1) {
                    // perror("fcntl F_GETFL failed for fd ${op.fd}") // Logging this might be too noisy
                    continue // Skip this operation
                }
                if ((currentFdFlags and O_NONBLOCK) == 0) {
                    if (fcntl(op.fd, F_SETFL, currentFdFlags or O_NONBLOCK) == -1) {
                        // perror("fcntl F_SETFL O_NONBLOCK failed for fd ${op.fd}")
                        continue // Skip this operation
                    }
                }

                val originalUserData = if (op.userData == 0L) nextInternalOpId++ else op.userData
                // Ensure op.userData is updated if it was 0, as this is the key for the registry
                op.userData = originalUserData

                val currentPinnedBuffers = mutableListOf<StableRef<ByteArray>>()
                val nativeOp = arena.alloc<pending_op_macos>() // Allocate one by one

                nativeOp.fd = op.fd
                nativeOp.user_data = op.userData
                nativeOp.offset = -1L

                when (op) {
                    is IoOperation.ReadV -> {
                        nativeOp.type = KOTLIN_OP_READV
                        nativeOp.offset = op.offset
                        if (op.buffers.isNotEmpty()) {
                            val iovecs = arena.allocArray<kotlin_iovec>(op.buffers.size)
                            op.buffers.forEachIndexed { iovec_idx, byteArray ->
                                val stableRef = StableRef.create(byteArray)
                                currentPinnedBuffers.add(stableRef)
                                iovecs[iovec_idx].iov_base = stableRef.get().refTo(0)
                                iovecs[iovec_idx].iov_len = byteArray.size.convert()
                            }
                            nativeOp.iovecs = iovecs
                            nativeOp.iovec_count = op.buffers.size
                        } else { nativeOp.iovec_count = 0; nativeOp.iovecs = null; }
                    }
                    is IoOperation.WriteV -> {
                        nativeOp.type = KOTLIN_OP_WRITEV
                        nativeOp.offset = op.offset
                        if (op.buffers.isNotEmpty()) {
                            val iovecs = arena.allocArray<kotlin_iovec>(op.buffers.size)
                            op.buffers.forEachIndexed { iovec_idx, byteArray ->
                                val stableRef = StableRef.create(byteArray)
                                currentPinnedBuffers.add(stableRef)
                                iovecs[iovec_idx].iov_base = stableRef.get().refTo(0)
                                iovecs[iovec_idx].iov_len = byteArray.size.convert()
                            }
                            nativeOp.iovecs = iovecs
                            nativeOp.iovec_count = op.buffers.size
                        } else { nativeOp.iovec_count = 0; nativeOp.iovecs = null; }
                    }
                    is IoOperation.Fsync -> {
                        nativeOp.type = KOTLIN_OP_FSYNC
                        nativeOp.fsync_data_only = if (op.fsyncDataOnly) 1 else 0
                    }
                    is IoOperation.Recv -> {
                        nativeOp.type = KOTLIN_OP_RECV
                        val stableRef = StableRef.create(op.buffer)
                        currentPinnedBuffers.add(stableRef)
                        nativeOp.buffer = stableRef.get().refTo(0)
                        nativeOp.buffer_len = op.buffer.size.convert()
                        nativeOp.flags = op.flags
                    }
                    is IoOperation.Send -> {
                        nativeOp.type = KOTLIN_OP_SEND
                        val stableRef = StableRef.create(op.buffer)
                        currentPinnedBuffers.add(stableRef)
                        nativeOp.buffer = stableRef.get().refTo(0)
                        nativeOp.buffer_len = op.buffer.size.convert()
                        nativeOp.flags = op.flags
                    }
                }
                val opData = MacosPendingOpInternalData(op.userData, currentPinnedBuffers, nativeOp.type)
                // Store in registry *before* passing to C, so if C errors, we can find it.
                macosPendingOpsRegistry[op.userData] = opData
                preparedNativeOps.add(nativeOp to opData)
            }

            if (preparedNativeOps.isEmpty()) return 0

            // Now allocate the C array for only successfully prepared ops
            val finalNativeOpsArray = arena.allocArray<pending_op_macos>(preparedNativeOps.size)
            preparedNativeOps.forEachIndexed { index, pair ->
                finalNativeOpsArray[index].readValue(pair.first.rawPtr) // Copy from temp alloc to final array
            }

            successfullyPreparedForC = submit_io_operations_macos(kqfd, finalNativeOpsArray, preparedNativeOps.size)

            if (successfullyPreparedForC < 0) { // Global error from C submit (e.g., kevent call failed)
                preparedNativeOps.forEach { pair -> // All prepared ops failed if submit_io failed
                    macosPendingOpsRegistry.remove(pair.second.originalOpUserData)
                    pair.second.pinnedBuffers.forEach { it.dispose() }
                }
            } else if (successfullyPreparedForC < preparedNativeOps.size) {
                // C layer skipped some ops it was given (e.g. FSYNC or internal error for specific op).
                // We need to identify which ones were *not* submitted by C and clean them up.
                // This is complex as the C layer's return value is just a count.
                // The current C code for submit returns number of *successfully prepared* for kevent,
                // and if kevent call itself fails, it returns -errno.
                // Assuming C's `successfully_prepared` count means those ops whose udata is now managed by kqueue or freed by C.
                // For those that C skipped (e.g. FSYNC), C already freed the op_state and iovecs.
                // We only need to clean up Kotlin StableRefs for those C skipped.
                // This requires more detailed feedback from C about *which* ops were skipped.
                // For now, this path is a known simplification.
            }
            return successfullyPreparedForC
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
        val kqfd = context.kqfd
        if (kqfd < 0) return -EINVAL

        val maxNativeCompletions = 32
        val arena = Arena()
        try {
            val nativeCompletionsArray = arena.allocArray<completion_result_macos>(maxNativeCompletions)
            val tsPointer: CPointer<timespec>? = when {
                timeoutMillis > 0 -> arena.alloc<timespec>().apply {
                    tv_sec = (timeoutMillis / 1000).toLong()
                    tv_nsec = ((timeoutMillis % 1000) * 1000000).toLong()
                }.ptr
                timeoutMillis == 0 -> arena.alloc<timespec>().apply { tv_sec = 0; tv_nsec = 0 }.ptr
                else -> null
            }

            val numCompleted = poll_io_completions_macos(
                kqfd,
                nativeCompletionsArray,
                maxNativeCompletions,
                minCompletions,
                tsPointer
            )

            if (numCompleted > 0) {
                for (i in 0 until numCompleted) {
                    val nativeCompletion = nativeCompletionsArray[i]
                    // nativeCompletion.user_data is the one we set in C's op_state->op_details.user_data
                    // which is our original (potentially generated) op.userData.
                    val opData = macosPendingOpsRegistry.remove(nativeCompletion.user_data)

                    if (opData != null) {
                        completions.add(IoCompletion(
                            userData = opData.originalOpUserData, // This is the key used for the map
                            result = nativeCompletion.result
                        ))
                        opData.pinnedBuffers.forEach { it.dispose() }
                    } else {
                        // This case should ideally not happen if udata is managed correctly.
                        // It means a completion event was received for userData not in our registry.
                        println("Warning: Received kqueue completion for unknown userData: ${nativeCompletion.user_data}")
                    }
                }
            } else if (numCompleted < 0) {
                 println("Error polling macOS completions: native_poll returned $numCompleted, errno: ${nativeCompletion.result}")
            }
            return numCompleted
        } finally {
            arena.clear()
        }
    }
}
