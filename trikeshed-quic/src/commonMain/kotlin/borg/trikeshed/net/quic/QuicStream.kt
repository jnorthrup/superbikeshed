@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import borg.trikeshed.lib.*
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.Volatile

// Simple platform types (same as in QuicConnection)
class PlatformByteBuffer(internal val data: ByteArray) {
    internal var position = 0
    internal var limit = data.size
    
    fun remaining(): Int = limit - position
    fun array(): ByteArray = data
    fun position(): Int = position
    fun flip() { limit = position; position = 0 }
    fun get(bytes: ByteArray) { /* mock */ }
    
    companion object {
        fun wrap(bytes: ByteArray) = PlatformByteBuffer(bytes)
    }
}

class PlatformInetSocketAddress(val host: String, val port: Int)

/**
 * Represents a QUIC stream
 */
class QuicStream(
    val id: Long,
    internal val bufferSize: Int,
    initialWindowSize: Long, // Passed from QuicConfig.initialStreamFlowControlWindow
    val priority: Int, // Stream priority
    // Channel for this stream's incoming data, to be populated by the central packet receiver
    internal val internalReceiveChannel: Channel<PlatformByteBuffer> = Channel(Channel.BUFFERED)
) {
    @Volatile
    internal var closed = false
    var remoteAddress: PlatformInetSocketAddress? = null // This should be set when stream is created or by first packet

    // Stream-level flow control properties
    var bytesSentOnStream: Long = 0L
    var currentStreamFlowControlWindow: Long = initialWindowSize
        internal set // Window size can be updated by WINDOW_UPDATE frames (conceptually)

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
        val bytes = ByteArray(data.size) { i -> data[i] }
        val buffer = PlatformByteBuffer.wrap(bytes)
        // Implementation would send via QUIC connection
        return true
    }

    // Stream buffer for incoming data
    internal val receiveBuffer = mutableListOf<PlatformByteBuffer>()
    internal var totalAvailableBytes = 0

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
    
    // Additional methods needed by QuicClient and QuicServer
    
    /**
     * Send data to stream
     */
    suspend fun send(data: ByteArray, fin: Boolean = false) {
        if (closed) return
        val buffer = PlatformByteBuffer.wrap(data)
        // In real implementation: send via QUIC connection with fin flag
        if (fin) {
            close()
        }
    }
    
    /**
     * Receive data from stream
     */
    suspend fun receive(): ByteArray {
        if (closed || !hasData()) return ByteArray(0)
        
        val indexed = readBytes(getAvailableBytes())
        return ByteArray(indexed.a) { i -> indexed.b(i) }
    }
    
    /**
     * Check if stream is finished (closed or FIN received)
     */
    fun isFinished(): Boolean = closed
} 