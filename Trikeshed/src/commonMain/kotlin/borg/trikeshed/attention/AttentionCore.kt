package borg.trikeshed.attention

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import kotlinx.coroutines.flow.Flow

// Normalized type hierarchy harmonized with nexus attention ontology
typealias ByteOffset = Long
typealias ByteLength = Long
typealias AttentionId = String
typealias SourceHandle = String

// Nexus attention integration types
typealias AttentionVector = Join<AttentionDirection, AttentionMagnitude>
typealias AttentionDirection = String  // "torrent-dht", "http-range", "local-file", "ipfs-block"
typealias AttentionMagnitude = Int     // Priority allocation (0-100)
typealias AttentionDistribution = Indexed<AttentionVector>
typealias AttentionFeedback = Join<AttentionVector, AttentionResult>

// Interest patterns from nexus
typealias InterestVector = Join<InterestType, InterestWeight>
typealias InterestType = String        // "sequential", "random-access", "streaming", "batch"
typealias InterestWeight = Double      // 0.0 to 1.0
typealias InterestProfile = Indexed<InterestVector>

/**
 * Core attention frame - standardized across all attention mechanisms
 */
data class AttentionFrame(
    val startOffset: ByteOffset,
    val endOffset: ByteOffset,
    val priority: Int = 0
) {
    val length: ByteLength get() = endOffset - startOffset
    
    companion object {
        fun range(start: ByteOffset, length: ByteLength): AttentionFrame =
            AttentionFrame(start, start + length)
    }
}

/**
 * Attention source types - exhaustive taxonomy
 */
sealed class AttentionSource {
    data class Local(val path: String) : AttentionSource()
    data class HTTP(val url: String) : AttentionSource()
    data class Torrent(val infoHash: ByteArray, val filePath: String) : AttentionSource()
    data class DHT(val nodeId: ByteArray, val key: ByteArray) : AttentionSource()
    data class IPFS(val cid: String) : AttentionSource()
}

/**
 * Unified attention interface - all attention mechanisms implement this
 * Harmonized with nexus attention and interest patterns
 */
interface AttentionMechanism {
    val id: AttentionId
    val supportedSources: Indexed<AttentionSource>
    val attentionVector: AttentionVector
    val interestProfile: InterestProfile
    
    suspend fun fetchFrame(
        source: AttentionSource,
        frame: AttentionFrame
    ): Indexed<Byte>
    
    fun streamFrames(
        source: AttentionSource,
        frames: Indexed<AttentionFrame>
    ): Flow<Indexed<Byte>>
    
    // Nexus integration methods
    fun calculateAttentionScore(source: AttentionSource, frame: AttentionFrame): Double
    fun adaptToInterest(profile: InterestProfile): AttentionMechanism
}

/**
 * Attention result with complete metadata
 */
data class AttentionResult(
    val frame: AttentionFrame,
    val data: Indexed<Byte>,
    val sourceId: SourceHandle,
    val latencyMs: Long,
    val compressionRatio: Double? = null
)

/**
 * Normalized attention orchestrator
 */
class AttentionOrchestrator {
    private val mechanisms: MutableMap<AttentionId, AttentionMechanism> = mutableMapOf()
    
    fun register(mechanism: AttentionMechanism) {
        mechanisms[mechanism.id] = mechanism
    }
    
    suspend fun fetch(
        source: AttentionSource,
        frame: AttentionFrame,
        preferredMechanism: AttentionId? = null
    ): AttentionResult {
        val mechanism: AttentionMechanism = preferredMechanism?.let { 
            mechanisms[it] 
        } ?: selectMechanism(source)
        
        val startTime: Long = System.currentTimeMillis()
        val data: Indexed<Byte> = mechanism.fetchFrame(source, frame)
        val endTime: Long = System.currentTimeMillis()
        
        return AttentionResult(
            frame = frame,
            data = data,
            sourceId = generateSourceId(source),
            latencyMs = endTime - startTime
        )
    }
    
    private fun selectMechanism(source: AttentionSource): AttentionMechanism {
        for ((_, mechanism) in mechanisms) {
            for (i in 0 until mechanism.supportedSources.a) {
                val supportedSource: AttentionSource = mechanism.supportedSources.b(i)
                if (isCompatible(source, supportedSource)) {
                    return mechanism
                }
            }
        }
        throw IllegalArgumentException("No mechanism supports source: $source")
    }
    
    private fun isCompatible(source: AttentionSource, supported: AttentionSource): Boolean {
        return when {
            source is AttentionSource.Local && supported is AttentionSource.Local -> true
            source is AttentionSource.HTTP && supported is AttentionSource.HTTP -> true
            source is AttentionSource.Torrent && supported is AttentionSource.Torrent -> true
            source is AttentionSource.DHT && supported is AttentionSource.DHT -> true
            source is AttentionSource.IPFS && supported is AttentionSource.IPFS -> true
            else -> false
        }
    }
    
    private fun generateSourceId(source: AttentionSource): SourceHandle {
        return when (source) {
            is AttentionSource.Local -> "local:${source.path}"
            is AttentionSource.HTTP -> "http:${source.url}"
            is AttentionSource.Torrent -> "torrent:${source.infoHash.contentHashCode()}:${source.filePath}"
            is AttentionSource.DHT -> "dht:${source.nodeId.contentHashCode()}"
            is AttentionSource.IPFS -> "ipfs:${source.cid}"
        }
    }
}

/**
 * Global attention orchestrator instance
 */
val attentionOrchestrator = AttentionOrchestrator()