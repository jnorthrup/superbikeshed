package borg.trikeshed.net.quic


import borg.trikeshed.lib.*
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

    /**
     * Write bytes to stream using TrikeShed Indexed<Byte>
     */
    suspend fun writeBytes(data: Indexed<Byte>): Boolean {
        if (closed) return false
        // Convert Indexed<Byte> to ByteArray for platform buffer
        val bytes = ByteArray(data.a) { i -> data[i] }
        val buffer = PlatformByteBuffer.wrap(bytes)
        // Implementation would send via QUIC connection
        return true
    }

    // Stream buffer for incoming data
    private val receiveBuffer = mutableListOf<PlatformByteBuffer>()
    private var totalAvailableBytes = 0

    /**
     * Check if stream has data available for reading
     */
    fun hasData(): Boolean {
        return totalAvailableBytes > 0 && !closed
    }

    /**
     * Get total available bytes across all buffered data
     */
    fun getAvailableBytes(): Int {
        return totalAvailableBytes
    }

    /**
     * Read bytes from stream buffer using TrikeShed patterns
     */
    suspend fun readBytes(maxBytes: Int): Indexed<Byte> {
        if (closed || maxBytes <= 0 || !hasData()) {
            return 0 j { 0.toByte() }
        }
        
        val bytesToRead = minOf(maxBytes, totalAvailableBytes)
        val result = mutableListOf<Byte>()
        var remaining = bytesToRead
        
        while (remaining > 0 && receiveBuffer.isNotEmpty()) {
            val buffer = receiveBuffer.first()
            val available = buffer.remaining()
            
            if (available <= remaining) {
                // Consume entire buffer
                val bytes = ByteArray(available)
                buffer.get(bytes)
                result.addAll(bytes.toList())
                remaining -= available
                totalAvailableBytes -= available
                receiveBuffer.removeFirst()
            } else {
                // Partial buffer consumption
                val bytes = ByteArray(remaining)
                buffer.get(bytes)
                result.addAll(bytes.toList())
                totalAvailableBytes -= remaining
                remaining = 0
            }
        }
        
        return result.size j { i: Int -> result[i] }
    }

    /**
     * Internal method to add received data to stream buffer
     */
    internal suspend fun addReceivedData(buffer: PlatformByteBuffer) {
        if (!closed) {
            receiveBuffer.add(buffer)
            totalAvailableBytes += buffer.remaining()
            // Also send to coroutine channel for async processing
            internalReceiveChannel.send(buffer)
        }
    }
} 