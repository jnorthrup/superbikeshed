@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

import kotlinx.coroutines.flow.Flow
// import kotlinx.serialization.Serializable

/**
 * CCekEngine - Compressed Communication Engine Kit
 * Provides high-performance compressed inter-process communication using async I/O
 */
expect class CCekEngine {
    /**
     * Initialize the CCek engine with compression settings
     */
    fun initialize(compressionLevel: Int = 6, bufferSize: Int = 8192)
    
    /**
     * Clean up resources
     */
    fun cleanup()
    
    /**
     * Send compressed data to a target
     */
    suspend fun send(target: String, data: ByteArray): Int
    
    /**
     * Send serializable object with compression
     */
    suspend fun <T> sendObject(target: String, obj: T): Int
    
    /**
     * Receive compressed data from a source
     */
    suspend fun receive(source: String, buffer: ByteArray): Int
    
    /**
     * Receive and deserialize object with decompression
     */
    suspend fun <T> receiveObject(source: String): T?
    
    /**
     * Broadcast compressed data to multiple targets
     */
    suspend fun broadcast(targets: List<String>, data: ByteArray): List<Int>
    
    /**
     * Get flow of incoming compressed messages
     */
    fun incomingMessages(): Flow<CCekMessage>
    
    /**
     * Get flow of outgoing compressed messages
     */
    fun outgoingMessages(): Flow<CCekMessage>
    
    /**
     * Get compression statistics
     */
    fun getCompressionStats(): CompressionStats
    
    /**
     * Set compression algorithm
     */
    fun setCompressionAlgorithm(algorithm: CompressionAlgorithm)
    
    companion object {
        fun create(): CCekEngine
    }
}

/**
 * Represents a compressed message in the CCek system
 */
// @Serializable
data class CCekMessage(
    val id: Long,
    val source: String,
    val target: String,
    val data: ByteArray,
    val compressedSize: Int,
    val originalSize: Int,
    val compressionRatio: Double,
    val timestamp: Long = 0L, // TODO: Platform-specific timestamp
    val algorithm: CompressionAlgorithm = CompressionAlgorithm.ZSTD
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this::class != other!!::class) return false
        
        other as CCekMessage
        
        if (id != other.id) return false
        if (source != other.source) return false
        if (target != other.target) return false
        if (!data.contentEquals(other.data)) return false
        if (compressedSize != other.compressedSize) return false
        if (originalSize != other.originalSize) return false
        if (compressionRatio != other.compressionRatio) return false
        if (timestamp != other.timestamp) return false
        if (algorithm != other.algorithm) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + compressedSize
        result = 31 * result + originalSize.hashCode()
        result = 31 * result + compressionRatio.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * Compression algorithms supported by CCek
 */
enum class CompressionAlgorithm(val id: Int, val algorithmName: String) {
    ZSTD(1, "zstd"),
    LZ4(2, "lz4"),
    GZIP(3, "gzip"),
    BROTLI(4, "brotli"),
    NONE(0, "none")
}

/**
 * Compression statistics
 */
// @Serializable
data class CompressionStats(
    val totalMessages: Long = 0,
    val totalCompressedBytes: Long = 0,
    val totalOriginalBytes: Long = 0,
    val averageCompressionRatio: Double = 0.0,
    val algorithmUsage: Map<CompressionAlgorithm, Long> = emptyMap(),
    val throughputBytesPerSecond: Double = 0.0,
    val latencyMs: Double = 0.0
) {
    val overallCompressionRatio: Double
        get() = if (totalOriginalBytes > 0) totalCompressedBytes.toDouble() / totalOriginalBytes else 0.0
    
    val spaceSavedBytes: Long
        get() = totalOriginalBytes - totalCompressedBytes
    
    val spaceSavedPercentage: Double
        get() = if (totalOriginalBytes > 0) (spaceSavedBytes.toDouble() / totalOriginalBytes) * 100 else 0.0
}

/**
 * CCek configuration
 */
// @Serializable
data class CCekConfig(
    val compressionLevel: Int = 6,
    val bufferSize: Int = 8192,
    val defaultAlgorithm: CompressionAlgorithm = CompressionAlgorithm.ZSTD,
    val enableStats: Boolean = true,
    val maxMessageSize: Int = 1024 * 1024, // 1MB
    val connectionTimeoutMs: Long = 5000,
    val retryAttempts: Int = 3,
    val enableEncryption: Boolean = false,
    val encryptionKey: String? = null
) 