package borg.trikeshed.net.quic

import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformInetSocketAddress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Represents a QUIC stream with proper flow control and error handling
 */
class QuicStream(
    val id: Long,
    private val bufferSize: Int,
    initialWindowSize: Long, // Passed from QuicConfig.initialStreamFlowControlWindow
    val priority: Int, // Stream priority
    // Channel for this stream's incoming data, to be populated by the central packet receiver
    internal val internalReceiveChannel: Channel<PlatformByteBuffer> = Channel(Channel.BUFFERED)
) {
    private val mutex = Mutex()
    
    @Volatile
    private var closed = false
    var remoteAddress: PlatformInetSocketAddress? = null // This should be set when stream is created or by first packet

    // Stream-level flow control properties
    private var _bytesSentOnStream: Long = 0L
    var bytesSentOnStream: Long
        get() = _bytesSentOnStream
        internal set(value) {
            _bytesSentOnStream = value
        }

    private var _currentStreamFlowControlWindow: Long = initialWindowSize
    val currentStreamFlowControlWindow: Long
        get() = _currentStreamFlowControlWindow

    /**
     * Closes the stream and its resources
     * @throws QuicError.StreamError if already closed
     */
    suspend fun close() = mutex.withLock {
        if (closed) {
            throw QuicError.StreamError.StreamClosed(id)
        }
        closed = true
        internalReceiveChannel.close() // Close the channel when stream is closed
    }

    /**
     * Checks if the stream is closed
     */
    suspend fun isClosed(): Boolean = mutex.withLock { closed }

    /**
     * Updates the stream's flow control window
     * @throws QuicError.StreamError if stream is closed
     */
    suspend fun updateFlowControlWindow(newMaxData: Long) = mutex.withLock {
        if (closed) {
            throw QuicError.StreamError.StreamClosed(id)
        }

        val newWindow = newMaxData - _bytesSentOnStream
        if (newWindow > _currentStreamFlowControlWindow) {
            _currentStreamFlowControlWindow = newWindow
            println("Stream $id window updated to $_currentStreamFlowControlWindow (Max data: $newMaxData)")
        }
    }

    /**
     * Checks if sending data of the given size would exceed the flow control window
     * @throws QuicError.StreamError if stream is closed
     */
    suspend fun checkFlowControl(dataSize: Long) = mutex.withLock {
        if (closed) {
            throw QuicError.StreamError.StreamClosed(id)
        }

        if (_bytesSentOnStream + dataSize > _currentStreamFlowControlWindow) {
            throw QuicError.StreamError.FlowControlBlocked(
                id,
                _currentStreamFlowControlWindow,
                _bytesSentOnStream + dataSize
            )
        }
    }

    /**
     * Updates the number of bytes sent on this stream
     * @throws QuicError.StreamError if stream is closed or flow control would be exceeded
     */
    suspend fun updateBytesSent(bytesSent: Long) = mutex.withLock {
        if (closed) {
            throw QuicError.StreamError.StreamClosed(id)
        }

        if (_bytesSentOnStream + bytesSent > _currentStreamFlowControlWindow) {
            throw QuicError.StreamError.FlowControlBlocked(
                id,
                _currentStreamFlowControlWindow,
                _bytesSentOnStream + bytesSent
            )
        }

        _bytesSentOnStream += bytesSent
    }
} 