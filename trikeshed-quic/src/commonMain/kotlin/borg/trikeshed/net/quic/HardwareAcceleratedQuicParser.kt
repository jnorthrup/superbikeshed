@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

/**
 * Hardware-accelerated QUIC header parser using the same expect/actual pattern.
 * 
 * QUIC headers have specific byte patterns that benefit from SIMD:
 * - Variable-length integer decoding (SIMD parallel decode)
 * - Connection ID extraction (SIMD pattern matching)
 * - Packet number decoding (SIMD bit manipulation)
 * 
 * The performance gap for QUIC parsing is even more critical than JSON because:
 * 1. QUIC operates at network line rates (10-100 Gbps)
 * 2. Header parsing is on the critical path for every packet
 * 3. Latency matters more than throughput for networking
 */
expect class HardwareAcceleratedQuicParser {
    
    /**
     * Parse QUIC long header format with SIMD acceleration.
     * Target: Parse headers at 10+ Gbps line rate
     */
    fun parseLongHeader(packet: ByteArray): QuicLongHeader?
    
    /**
     * Parse QUIC short header format (1-RTT packets).
     * These are the most common in established connections.
     */
    fun parseShortHeader(packet: ByteArray): QuicShortHeader?
    
    /**
     * Batch parse multiple QUIC packets in parallel.
     * Critical for high-throughput scenarios.
     */
    fun parseBatch(packets: Array<ByteArray>): Array<QuicHeader?>
    
    /**
     * Extract connection IDs using SIMD pattern matching.
     * Connection ID routing is critical for load balancers.
     */
    fun extractConnectionIds(packets: Array<ByteArray>): Array<ByteArray?>
    
    fun getPerformanceProfile(): QuicPerformanceProfile
    
    companion object {
        fun create(): HardwareAcceleratedQuicParser
    }
}

data class QuicPerformanceProfile(
    val hasNativeAcceleration: Boolean,
    val expectedPacketsPerSecond: Int,
    val expectedGbps: Double,
    val algorithmType: String
)

sealed class QuicHeader {
    abstract val version: Long
    abstract val destConnectionId: ByteArray
    abstract val srcConnectionId: ByteArray
}

data class QuicLongHeader(
    override val version: Long,
    override val destConnectionId: ByteArray,
    override val srcConnectionId: ByteArray,
    val packetType: PacketType,
    val packetNumber: Long,
    val payload: ByteArray
) : QuicHeader()

data class QuicShortHeader(
    override val version: Long,
    override val destConnectionId: ByteArray, 
    override val srcConnectionId: ByteArray,
    val keyPhase: Boolean,
    val packetNumber: Long
) : QuicHeader()

enum class PacketType {
    INITIAL,
    ZERO_RTT,
    HANDSHAKE,
    RETRY
}