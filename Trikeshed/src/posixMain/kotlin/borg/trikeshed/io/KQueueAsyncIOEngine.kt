package borg.trikeshed.io

import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.cancellation.CancellationException

class KQueueAsyncIOEngine {
    private var kq: Int = -1
    private val PENDING_EVENTS = 1024
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _completedFlow = MutableSharedFlow<IOResult>()
    val completedFlow: Flow<IOResult> = _completedFlow

    fun initialize() {
        kq = kqueue()
        if (kq == -1) {
            throw IOException("Failed to create kqueue: ${strerror(errno)?.toKString()}")
        }
        scope.launch { run() }
    }

    fun cleanup() {
        if (kq != -1) {
            close(kq)
        }
        scope.cancel()
    }

    private suspend fun run() = memScoped {
        val events = allocArray<kevent>(PENDING_EVENTS)

        while (isActive) {
            val n = kevent(kq, null, 0, events, PENDING_EVENTS, null)

            if (n < 0) {
                if (errno == EINTR) continue
                throw IOException("kevent failed: ${strerror(errno)?.toKString()}")
            }

            for (i in 0 until n) {
                val event = events[i]
                val udata = event.udata?.asStableRef<IOContinuation>()?.get()
                if (udata != null) {
                    val bytesTransferred = event.data.toInt()
                    val error = if (event.flags.toInt() and EV_ERROR != 0) event.data.toInt() else 0
                    val result = IOResult(udata.operation.id, bytesTransferred, error)
                    udata.continuation.resume(result)
                    scope.launch { _completedFlow.emit(result) }
                    udata.ref.dispose()
                }
            }
        }
    }

    suspend fun submit(op: IOOperation): IOResult {
        return suspendCancellableCoroutine { continuation ->
            val ioCont = IOContinuation(op, continuation)
            val udata = StableRef.create(ioCont).asCPointer()

            memScoped {
                val ke = alloc<kevent>()
                val filter = when(op.type) {
                    IOOperation.IOType.READ -> EVFILT_READ
                    IOOperation.IOType.WRITE -> EVFILT_WRITE
                }
                
                EV_SET(ke.ptr, op.fd.convert(), filter.convert(), (EV_ADD or EV_ONESHOT).convert(), 0, 0, udata)

                if (kevent(kq, ke.ptr, 1, null, 0, null) == -1) {
                    val ex = IOException("Failed to register fd ${op.fd} with kqueue: ${strerror(errno)?.toKString()}")
                    continuation.resumeWithException(ex)
                }
            }

            continuation.invokeOnCancellation {
                // In a real implementation, we would need a more robust cancellation mechanism.
                ioCont.ref.dispose()
            }
        }
    }
}

private class IOContinuation(
    val operation: IOOperation,
    val continuation: CancellableContinuation<IOResult>
) {
    val ref = StableRef.create(this)
} 