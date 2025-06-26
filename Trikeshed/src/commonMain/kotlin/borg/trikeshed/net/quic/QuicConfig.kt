@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic


data class QuicConfig(
    // Existing parameters
    val streamBufferSize: Int? = null,
    val maxConcurrentStreams: Long = MAX_STREAMS,
    val enable0RTT: Boolean = true,

    // New parameters for protocol optimization
    val congestionControlAlgorithm: String = "cubic", // e.g., "cubic", "bbr", "reno", "custom_db_optimized"
    val initialConnectionFlowControlWindow: Long = 64 * 1024, // 64KB default
    val initialStreamFlowControlWindow: Long = 32 * 1024, // 32KB default per stream
    val maxAckDelayMs: Long = 25, // Max time in ms receiver can delay sending an ACK (conceptual)
    val defaultStreamPriority: Int = 10 // Default priority for new streams (e.g., 0=high, 10=medium, 20=low)
) {
    companion object {
        const val MAX_STREAMS = 1L shl 62 // 2^62 concurrent streams
        const val DEFAULT_STREAM_BUFFER_SIZE = 64 * 1024 // 64KB
        const val STREAM_ID_HEADER_SIZE = Long.SIZE_BYTES // 8 bytes for Stream ID
    }

    init {
        require(initialConnectionFlowControlWindow >= 0) { "Initial connection flow control window cannot be negative." }
        require(initialStreamFlowControlWindow >= 0) { "Initial stream flow control window cannot be negative." }
        require(maxAckDelayMs >= 0) { "Max ACK delay cannot be negative." }
        require(congestionControlAlgorithm.isNotBlank()) { "Congestion control algorithm name cannot be blank."}
        require(defaultStreamPriority >= 0) { "Default stream priority cannot be negative."}
    }
} 