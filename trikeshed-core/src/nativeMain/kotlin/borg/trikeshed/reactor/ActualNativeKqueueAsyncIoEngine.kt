package borg.trikeshed.reactor

import kotlinx.cinterop.*
import platform.posix.*
import borg.trikeshed.nio.ByteBuffer
import borg.trikeshed.nio.NativeArrayByteBuffer // Assuming this is your actual ByteBuffer for native
import borg.trikeshed.io.network.NetworkAddress
import borg.trikeshed.net.NativeNetworkUtils
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

@OptIn(ExperimentalForeignApi::class)
actual class AsyncIoEngine actual constructor() : CoroutineContext.Element {
    actual companion object Key : CoroutineContext.Key<AsyncIoEngine>
    override val key: CoroutineContext.Key<*> get() = Key

    private val kq: Int = kqueue()
    private val activeOperations = mutableMapOf<ULong, KqueueOpData>() // Store pending ops by user_data ID

    private var nextUserData: ULong = 1UL // User data for kevent.udata

    // Data class to hold information about a pending kqueue operation
    private data class KqueueOpData(
        val handle: AsyncOperationHandle,
        val fd: Int,
        val filter: Short, // EVFILT_READ or EVFILT_WRITE
        val attachment: Any?,
        val buffer: ByteBuffer?, // For read/write operations
        val fileOffset: Long = -1L // For pread/pwrite
    )

    init {
        if (kq == -1) {
            throw RuntimeException("kqueue() creation failed: ${strerror(errno)?.toKString()}")
        }
    }

    actual override fun registerDescriptor(fd: Int) {
        // kqueue usually auto-registers FDs when an event filter is added for them.
        // No explicit separate registration step like io_uring might need for fixed files.
        println("Kqueue AsyncIoEngine: registerDescriptor($fd) - implicit via submit.")
    }

    actual override fun unregisterDescriptor(fd: Int) {
        // To unregister, remove all event filters associated with fd.
        // This is a best-effort removal.
        activeOperations.values.filter { it.fd == fd }.forEach { opData ->
            memScoped {
                val ke = alloc<kevent>()
                EV_SET(ke.ptr, opData.fd.convert(), opData.filter, EV_DELETE.toUShort(), 0u, 0, null)
                if (kevent(kq, ke.ptr, 1, null, 0, null) == -1) {
                    println("Kqueue: Error trying to delete event for fd ${opData.fd} on unregister: ${strerror(errno)?.toKString()}")
                }
            }
            activeOperations.remove(opData.handle.id()) // Remove from our map
        }
        println("Kqueue AsyncIoEngine: unregisterDescriptor($fd) - removed associated kevent filters.")
    }

    private fun submitKevent(
        fd: Int,
        filter: Short,
        flags: UShort,
        fflags: UInt,
        data: Long,
        attachment: Any?,
        buffer: ByteBuffer?,
        fileOffset: Long
    ): AsyncOperationHandle = memScoped {
        val userData = nextUserData++
        val handle = NativeAsyncOperationHandle(userData)
        val opData = KqueueOpData(handle, fd, filter, attachment, buffer, fileOffset)
        activeOperations[userData] = opData

        val ke = alloc<kevent>()
        EV_SET(ke.ptr, fd.convert(), filter, flags, fflags, data, interpretCPointer<COpaquePointerVar>(userData.toLong()))
        
        if (kevent(kq, ke.ptr, 1, null, 0, null) == -1) {
            activeOperations.remove(userData) // Rollback if submission fails
            throw RuntimeException("kevent add failed for fd $fd, filter $filter: ${strerror(errno)?.toKString()}")
        }
        return handle
    }

    actual override fun submitRead(fd: Int, buffer: ByteBuffer, fileOffset: Long, attachment: Any?): AsyncOperationHandle {
        // EV_ONESHOT: event is reported once and then automatically deleted.
        // This is suitable for one-shot read operations.
        return submitKevent(fd, EVFILT_READ, (EV_ADD or EV_ONESHOT).toUShort(), 0u, 0, attachment, buffer, fileOffset)
    }

    actual override fun submitWrite(fd: Int, buffer: ByteBuffer, fileOffset: Long, attachment: Any?): AsyncOperationHandle {
       return submitKevent(fd, EVFILT_WRITE, (EV_ADD or EV_ONESHOT).toUShort(), 0u, 0, attachment, buffer, fileOffset)
    }

    actual override fun submitAccept(serverFd: Int, attachment: Any?): AsyncOperationHandle {
        // For accept, we register EVFILT_READ on the server socket.
        // The completion will indicate readability, then we call accept().
        // EV_CLEAR: automatically re-arms the event after it's triggered.
        // EV_ONESHOT is also an option if you want to re-submit accept after each completion.
        return submitKevent(serverFd, EVFILT_READ, (EV_ADD or EV_CLEAR).toUShort(), 0u, 0, attachment, null, -1L)
    }
    
    actual override fun submitConnect(clientFd: Int, remoteAddress: NetworkAddress, attachment: Any?): AsyncOperationHandle {
        // For non-blocking connect, first call connect() which might return EINPROGRESS.
        // Then monitor fd for writability (EVFILT_WRITE) for connect completion.
        val connectResult = memScoped {
            NativeNetworkUtils.networkAddressToNativeSockaddr(remoteAddress)!!.usePinned { pinnedAddr ->
                platform.posix.connect(clientFd, pinnedAddr.get().ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            }
        }

        if (connectResult == 0) { // Connected immediately (rare for non-blocking)
            // Create a synthetic completion event for immediate success.
            // This handle will be processed by pollCompletions.
            val userData = nextUserData++
            val handle = NativeAsyncOperationHandle(userData)
            activeOperations[userData] = KqueueOpData(handle, clientFd, EVFILT_WRITE, attachment, null, -1L) // Store as a write op for consistency
            // Manually add a completed event to be picked up by pollCompletions
            // This is a simplification; a real system might have an internal queue for immediate completions.
            // For now, we rely on the next pollCompletions call to pick this up.
            return handle
        } else if (errno != EINPROGRESS) {
            throw RuntimeException("connect() failed: ${strerror(errno)?.toKString()}")
        }
        // If EINPROGRESS, monitor for writability
        return submitKevent(clientFd, EVFILT_WRITE, (EV_ADD or EV_ONESHOT).toUShort(), 0u, 0, attachment, null, -1L)
    }
    
    actual override fun submitCancel(handleToCancel: AsyncOperationHandle, attachment: Any?): AsyncOperationHandle {
        val opToCancelId = (handleToCancel as NativeAsyncOperationHandle).id
        val opToCancel = activeOperations[opToCancelId]
        if (opToCancel != null) {
            memScoped {
                val ke = alloc<kevent>()
                EV_SET(ke.ptr, opToCancel.fd.convert(), opToCancel.filter, EV_DELETE.toUShort(), 0u, 0, null)
                if (kevent(kq, ke.ptr, 1, null, 0, null) == -1) {
                    println("Kqueue: Error trying to delete event for fd ${opToCancel.fd} on cancel: ${strerror(errno)?.toKString()}")
                }
            }
            activeOperations.remove(opToCancelId) // Remove from our map
            // For kqueue EV_DELETE, there isn't a direct "cancel completion" event.
            // We return a new handle for the cancellation request itself.
            // Its completion indicates the cancellation attempt finished.
            val cancelHandle = NativeAsyncOperationHandle(nextUserData++)
            // We could immediately add a synthetic completion for this cancelHandle if needed.
            // For now, it's just a handle to track the request.
            return cancelHandle
        }
        throw IllegalArgumentException("Handle not found for cancellation: ${handleToCancel.id()}")
    }

    actual override suspend fun pollCompletions(maxEvents: Int, timeoutMillis: Long): List<AsyncCompletionEvent> = withContext(Dispatchers.Default) {
        val completions = mutableListOf<AsyncCompletionEvent>()
        memScoped {
            val events = allocArray<kevent>(maxEvents)
            val ts: CPointer<timespec>? = if (timeoutMillis >= 0) {
                alloc<timespec> {
                    tv_sec = timeoutMillis / 1000
                    tv_nsec = (timeoutMillis % 1000) * 1_000_000
                }.ptr
            } else null // Infinite timeout if null

            val nev = kevent(kq, null, 0, events, maxEvents, ts)

            if (nev == -1) {
                if (errno == EINTR) return@withContext emptyList() // Interrupted, retry
                throw RuntimeException("kevent poll failed: ${strerror(errno)?.toKString()}")
            }

            for (i in 0 until nev) {
                val currentEvent = events[i]
                val handleId = currentEvent.udata.toLong().toULong()
                val operationData = activeOperations.remove(handleId) // Operation completes, remove from map

                if (operationData == null) {
                    println("Warning: Kqueue completion for unknown user_data: $handleId, flags: ${currentEvent.flags}, data: ${currentEvent.data}")
                    continue // Already cancelled or spurious
                }

                val (originalHandle, fd, filter, attachment, buffer, fileOffset) = operationData
                
                var result = 0
                var errorCode: Int? = null

                if ((currentEvent.flags.toInt() and EV_ERROR) != 0) {
                    errorCode = currentEvent.data.toInt() // data field contains errno on EV_ERROR
                    result = -1 // Indicate operation error
                } else {
                    when (filter) {
                        EVFILT_READ -> {
                            // For accept, the event signals readability, then we call accept()
                            if (attachment is Pair<*, *>) { // Heuristic: if attachment is a Pair, it might be a connect/accept context
                                val clientAddr = alloc<sockaddr_storage>()
                                val clientAddrLen = alloc<socklen_tVar>()
                                clientAddrLen.value = sizeOf<sockaddr_storage>().convert()
                                val newClientFd = platform.posix.accept(fd, clientAddr.ptr.reinterpret(), clientAddrLen.ptr)
                                if (newClientFd == -1) { result = -1; errorCode = errno }
                                else result = newClientFd
                            } else { // Regular read
                                val nioBuffer = (buffer as NativeArrayByteBuffer).getInternalNativeArray()
                                val bytesRead = if (fileOffset != -1L) {
                                    pread(fd, nioBuffer.refTo(buffer.position()), buffer.remaining().convert(), fileOffset).toInt()
                                } else {
                                    platform.posix.read(fd, nioBuffer.refTo(buffer.position()), buffer.remaining().convert()).toInt()
                                }
                                if (bytesRead == -1) errorCode = errno else buffer.position(buffer.position() + bytesRead)
                                result = bytesRead
                            }
                        }
                        EVFILT_WRITE -> {
                            // For connect, writability signals completion. Check SO_ERROR.
                            if (attachment is Pair<*, *>) { // Heuristic: if attachment is a Pair, it might be a connect context
                                 val errorOptVal = alloc<IntVar>()
                                 val errorOptLen = alloc<socklen_tVar>()
                                 errorOptLen.value = sizeOf<IntVar>().convert()
                                 if (getsockopt(fd, SOL_SOCKET, SO_ERROR, errorOptVal.ptr, errorOptLen.ptr) == -1) {
                                     result = -1; errorCode = errno
                                 } else if (errorOptVal.value != 0) {
                                     result = -1; errorCode = errorOptVal.value
                                 } else {
                                     result = 0 // Connect success
                                 }
                            } else { // Regular write
                                val nioBuffer = (buffer as NativeArrayByteBuffer).getInternalNativeArray()
                                val bytesWritten = if (fileOffset != -1L) {
                                    pwrite(fd, nioBuffer.refTo(buffer.position()), buffer.remaining().convert(), fileOffset).toInt()
                                } else {
                                    platform.posix.write(fd, nioBuffer.refTo(buffer.position()), buffer.remaining().convert()).toInt()
                                }
                                 if (bytesWritten == -1) errorCode = errno else buffer.position(buffer.position() + bytesWritten)
                                 result = bytesWritten
                            }
                        }
                    }
                }
                completions.add(AsyncCompletionEvent(originalHandle, attachment, result, errorCode))
            }
        }
        return completions
    }

    actual override fun close() {
        if (kq != -1) {
            // Attempt to remove all active events before closing
            activeOperations.values.forEach { opData ->
                memScoped {
                    val ke = alloc<kevent>()
                    EV_SET(ke.ptr, opData.fd.convert(), opData.filter, EV_DELETE.toUShort(), 0u, 0, null)
                    kevent(kq, ke.ptr, 1, null, 0, null) // Ignore errors on best-effort cleanup
                }
            }
            platform.posix.close(kq)
        }
        activeOperations.clear()
        println("Kqueue AsyncIoEngine closed.")
    }
}

// Internal handle for Kqueue operations
@JvmInline
internal actual value class NativeAsyncOperationHandle(val id: ULong) : AsyncOperationHandle {
    // Helper to get the ULong ID from the AsyncOperationHandle for internal use
    fun id(): ULong = id
}
