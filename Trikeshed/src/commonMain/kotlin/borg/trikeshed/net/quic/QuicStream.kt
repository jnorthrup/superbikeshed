package borg.trikeshed.net.quic

import borg.trikeshed.nio.PlatformByteBuffer
import borg.trikeshed.nio.PlatformInetSocketAddress
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.Volatile

/**
 * Represents a QUIC stream
 */
class QuicStream(
    val id: Long,
    private val bufferSize: Int,
    initialWindowSize: Long, // Passed from QuicConfig.initialStreamFlowControlWindow
    val priority: Int, // Stream priority
    // Channel for this stream's incoming data, to be populated by the central packet receiver
    internal val internalReceiveChannel: Channel<PlatformByteBuffer> = Channel(Channel.BUFFERED)
) {
    @Volatile
    private var closed = false
    var remoteAddress: PlatformInetSocketAddress? = null // This should be set when stream is created or by first packet

    // Stream-level flow control properties
    var bytesSentOnStream: Long = 0L
    var currentStreamFlowControlWindow: Long = initialWindowSize
        private set // Window size can be updated by WINDOW_UPDATE frames (conceptually)

    fun close() {
        closed = true
        internalReceiveChannel.close() // Close the channel when stream is closed
    }

    fun isClosed(): Boolean = closed

    // Conceptual method for updating stream window by a WINDOW_UPDATE frame
    fun updateFlowControlWindow(newMaxData: Long) {
        // In QUIC, WINDOW_UPDATE typically provides the new maximum absolute byte offset allowed.
        // This translates to increasing the window size if newMaxData is larger than current sent + window.
        // For simplicity here, let's assume it can directly increase the current window size or reset sent bytes.
        // This is a simplification.
        val newWindow = newMaxData - bytesSentOnStream
        if (newWindow > currentStreamFlowControlWindow) {
            currentStreamFlowControlWindow = newWindow
            println("Stream $id window updated to $currentStreamFlowControlWindow (Max data: $newMaxData)")
        }
    }
} 