package borg.trikeshed.reactor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel as KChannel
import java.nio.channels.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import borg.trikeshed.nio.ByteBuffer // Your common expect ByteBuffer
import borg.trikeshed.nio.JvmByteBuffer // Assuming this is your actual ByteBuffer for JVM
import borg.trikeshed.io.network.NetworkAddress
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalCoroutinesApi::class) // For suspendCancellableCoroutine
actual class AsyncIoEngine actual constructor() : CoroutineContext.Element {
    actual companion object Key : CoroutineContext.Key<AsyncIoEngine>
    override val key: CoroutineContext.Key<*> get() = Key

    // For JVM, AsynchronousChannelGroup manages thread pool for async I/O.
    private val group: AsynchronousChannelGroup = AsynchronousChannelGroup.withThreadPool(
        Executors.newCachedThreadPool() // A cached thread pool is often suitable for async I/O
    )

    private val nextOpId = AtomicLong(0)
    // Map to track synthetic FDs to actual Java NIO Channels
    private val channelMap = ConcurrentHashMap<Int, AsynchronousChannel>()
    // Channel to bridge CompletionHandler callbacks to the polling model
    private val completionQueue = KChannel<AsyncCompletionEvent>(KChannel.UNLIMITED)

    // Helper to generate a synthetic FD for a Java Channel
    private fun generateSyntheticFd(channel: AsynchronousChannel): Int {
        // Using hashCode is a simple way to get a unique-ish int, but not guaranteed unique or stable across runs.
        // A more robust solution might involve a dedicated FD allocator.
        val fd = channel.hashCode()
        channelMap[fd] = channel
        return fd
    }

    actual override fun registerDescriptor(fd: Int) {
        // For JVM, this method is primarily for associating an external 'fd' (if any) with a channel.
        // In a typical JVM async setup, you'd create the channel directly.
        println("JVM AsyncIoEngine: registerDescriptor($fd) - No-op, channels are managed internally.")
    }

    actual override fun unregisterDescriptor(fd: Int) {
        channelMap.remove(fd)?.let { channel ->
            try {
                channel.close()
            } catch (e: Exception) {
                System.err.println("Error closing channel for fd $fd: ${e.message}")
            }
        }
    }
    
    // Helper to bridge Java's CompletionHandler to our internal completion queue
    private inner class JvmCompletionHandler<V>(
        private val handle: AsyncOperationHandle,
        private val attachment: Any?,
        private val buffer: ByteBuffer? = null // For updating position on read/write
    ) : CompletionHandler<V, Any?> {

        override fun completed(resultValue: V, att: Any?) {
            val numericResult: Int = when (resultValue) {
                is Number -> resultValue.toInt()
                is AsynchronousSocketChannel -> 0 // For accept, result is the channel itself, not bytes
                else -> 0 // Default for other successful operations
            }

            // Update buffer position for read/write operations
            if (buffer != null && numericResult > 0) {
                buffer.position(buffer.position() + numericResult)
            }

            val event = AsyncCompletionEvent(handle, attachment, numericResult, null)
            runBlocking { completionQueue.send(event) } // Send to the queue for pollCompletions
        }

        override fun failed(exc: Throwable, att: Any?) {
            // Map Java exceptions to a simple error code. A more robust system would map to POSIX errno equivalents.
            val errorCode = (exc as? java.io.IOException)?.hashCode() ?: -1
            val event = AsyncCompletionEvent(handle, attachment, -1, errorCode)
            runBlocking { completionQueue.send(event) }
        }
    }

    actual override fun submitRead(fd: Int, buffer: ByteBuffer, fileOffset: Long, attachment: Any?): AsyncOperationHandle {
        val handle = JvmAsyncOperationHandle(nextOpId.incrementAndGet())
        val jvmBuffer = (buffer as JvmByteBuffer).delegate // Get java.nio.ByteBuffer

        val channel = channelMap[fd]
        when (channel) {
            is AsynchronousSocketChannel -> {
                channel.read(jvmBuffer, attachment, JvmCompletionHandler(handle, attachment, buffer))
            }
            is AsynchronousFileChannel -> {
                channel.read(jvmBuffer, fileOffset, attachment, JvmCompletionHandler(handle, attachment, buffer))
            }
            else -> throw IllegalArgumentException("No AsynchronousSocketChannel or AsynchronousFileChannel mapped for fd $fd for read.")
        }
        return handle
    }

    actual override fun submitWrite(fd: Int, buffer: ByteBuffer, fileOffset: Long, attachment: Any?): AsyncOperationHandle {
        val handle = JvmAsyncOperationHandle(nextOpId.incrementAndGet())
        val jvmBuffer = (buffer as JvmByteBuffer).delegate

        val channel = channelMap[fd]
        when (channel) {
            is AsynchronousSocketChannel -> {
                channel.write(jvmBuffer, attachment, JvmCompletionHandler(handle, attachment, buffer))
            }
            is AsynchronousFileChannel -> {
                channel.write(jvmBuffer, fileOffset, attachment, JvmCompletionHandler(handle, attachment, buffer))
            }
            else -> throw IllegalArgumentException("No AsynchronousSocketChannel or AsynchronousFileChannel mapped for fd $fd for write.")
        }
        return handle
    }

    actual override fun submitAccept(serverFd: Int, attachment: Any?): AsyncOperationHandle {
        val handle = JvmAsyncOperationHandle(nextOpId.incrementAndGet())
        val serverChannel = channelMap[serverFd] as? AsynchronousServerSocketChannel
            ?: throw IllegalArgumentException("No AsynchronousServerSocketChannel mapped for serverFd $serverFd")

        serverChannel.accept(attachment, object : CompletionHandler<AsynchronousSocketChannel, Any?> {
            override fun completed(clientChannel: AsynchronousSocketChannel, att: Any?) {
                val newClientFd = generateSyntheticFd(clientChannel) // Map new client channel to a synthetic FD
                runBlocking { completionQueue.send(AsyncCompletionEvent(handle, attachment, newClientFd, null)) }
            }
            override fun failed(exc: Throwable, att: Any?) {
                val errorCode = (exc as? java.io.IOException)?.hashCode() ?: -1
                runBlocking { completionQueue.send(AsyncCompletionEvent(handle, attachment, -1, errorCode)) }
            }
        })
        return handle
    }

    actual override fun submitConnect(clientFd: Int, remoteAddress: NetworkAddress, attachment: Any?): AsyncOperationHandle {
        val handle = JvmAsyncOperationHandle(nextOpId.incrementAndGet())
        val clientChannel = channelMap[clientFd] as? AsynchronousSocketChannel
            ?: throw IllegalArgumentException("No AsynchronousSocketChannel mapped for clientFd $clientFd")

        val socketAddress = InetSocketAddress(remoteAddress.first, remoteAddress.second)
        clientChannel.connect(socketAddress, attachment, JvmCompletionHandler(handle, attachment))
        return handle
    }
    
    actual override fun submitCancel(handleToCancel: AsyncOperationHandle, attachment: Any?): AsyncOperationHandle {
        // For Java NIO async channels, cancellation is typically via Future.cancel() if Future was used.
        // With CompletionHandlers, cancelling an in-flight OS operation is harder.
        // This is a best-effort attempt. If the operation is already in progress, it might not be cancellable.
        // The handleToCancel refers to a previously submitted operation. We don't have a direct way
        // to cancel it via its handle in this CompletionHandler-based model without storing more state.
        // A more robust implementation would store a CancellableFuture or similar alongside the handle.
        
        // For now, we'll just log and return a new handle for the cancellation request itself.
        println("JVM: Attempted cancel for op handle ${(handleToCancel as JvmAsyncOperationHandle).id}. Direct cancellation not fully supported in this sketch.")
        return JvmAsyncOperationHandle(nextOpId.incrementAndGet()) // Return a new handle for the cancel op itself
    }

    actual override suspend fun pollCompletions(maxEvents: Int, timeoutMillis: Long): List<AsyncCompletionEvent> {
        val events = mutableListOf<AsyncCompletionEvent>()
        
        // Non-blocking immediate check
        if (timeoutMillis == 0L) {
            for (i in 0 until maxEvents) {
                events.add(completionQueue.tryReceive().getOrNull() ?: break)
            }
            return events
        }
        
        // Blocking or timed wait for the first event
        val firstEvent = try {
            if (timeoutMillis < 0) completionQueue.receive() // Infinite wait
            else withTimeout(timeoutMillis) { completionQueue.receive() } // Timed wait
        } catch (e: TimeoutCancellationException) {
            return emptyList() // Timeout occurred
        }
        events.add(firstEvent)

        // Poll for more events without blocking, up to maxEvents
        for (i in 1 until maxEvents) {
             events.add(completionQueue.tryReceive().getOrNull() ?: break)
        }
        return events
    }

    actual override fun close() {
        if (!group.isShutdown) {
            try {
                completionQueue.close() // Close the completion channel
                group.shutdownNow() // Attempt to cancel ongoing tasks and shut down thread pool
                if (!group.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("JVM AsyncIoEngine: AsynchronousChannelGroup did not terminate in time.")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        // Close all mapped channels
        channelMap.values.forEach { channel ->
            try {
                channel.close()
            } catch (e: Exception) {
                System.err.println("Error closing mapped channel: ${e.message}")
            }
        }
        channelMap.clear()
        println("JVM AsyncIoEngine closed.")
    }
}

@JvmInline
actual value class JvmAsyncOperationHandle(val id: Long) : AsyncOperationHandle
