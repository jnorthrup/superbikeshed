package borg.trikeshed.attention

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Series as Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import kotlin.jvm.JvmInline

// Symmetrical stepladder of inline value classes for attention evidence

// === A-Side Stepladder: Historical Content Evidence ===

@JvmInline
value class HistoricalEvidence(val packed: ULong) {
    val accessCount: UShort get() = (packed and 0xFFFFUL).toUShort()
    val hitRatio: UShort get() = ((packed shr 16) and 0xFFFFUL).toUShort()
    val latencyMs: UShort get() = ((packed shr 32) and 0xFFFFUL).toUShort()
    val reliability: UShort get() = ((packed shr 48) and 0xFFFFUL).toUShort()
    
    infix operator fun plus(access: AccessPattern): HistoricalEvidence = 
        HistoricalEvidence(packed + access.weight)
    
    companion object {
        fun pack(accessCount: UShort, hitRatio: UShort, latencyMs: UShort, reliability: UShort): HistoricalEvidence =
            HistoricalEvidence(
                accessCount.toULong() or 
                (hitRatio.toULong() shl 16) or
                (latencyMs.toULong() shl 32) or
                (reliability.toULong() shl 48)
            )
    }
}

@JvmInline
value class AccessPattern(val weight: ULong)

// === B-Side Stepladder: Byte Content Evidence ===

@JvmInline
value class ByteEvidence(val packed: ULong) {
    val entropy: UShort get() = (packed and 0xFFFFUL).toUShort()
    val compression: UShort get() = ((packed shr 16) and 0xFFFFUL).toUShort()
    val sequentiality: UShort get() = ((packed shr 32) and 0xFFFFUL).toUShort()
    val sparsity: UShort get() = ((packed shr 48) and 0xFFFFUL).toUShort()
    
    infix operator fun plus(byte: Byte): ByteEvidence = apply {
        // Accumulate byte characteristics
        val newEntropy = entropy + (byte.toInt() and 0xFF).toUShort()
        // Update packed representation
    }
    
    companion object {
        fun pack(entropy: UShort, compression: UShort, sequentiality: UShort, sparsity: UShort): ByteEvidence =
            ByteEvidence(
                entropy.toULong() or
                (compression.toULong() shl 16) or
                (sequentiality.toULong() shl 32) or
                (sparsity.toULong() shl 48)
            )
    }
}

// === Double-Dispatch Join Pattern ===

typealias AttentionEvidence = Join<HistoricalEvidence, ByteEvidence>

// Symmetrical dispatch outcome - both A and B improve the result
sealed class AttentionOutcome {
    @JvmInline
    value class OptimalTorrent(val confidence: Double) : AttentionOutcome()
    
    @JvmInline
    value class OptimalHTTP(val confidence: Double) : AttentionOutcome()
    
    @JvmInline
    value class OptimalLocal(val confidence: Double) : AttentionOutcome()
    
    @JvmInline
    value class OptimalDHT(val confidence: Double) : AttentionOutcome()
}

// Double-dispatch through Join<A,B> pattern
class AttentionDispatcher {
    
    // First dispatch: Historical evidence (A-side)
    fun dispatch(evidence: AttentionEvidence): AttentionOutcome {
        val historical: HistoricalEvidence = evidence.a
        val byteContent: ByteEvidence = evidence.b
        
        return when {
            // Symmetrical evaluation - both A and B contribute
            historical.accessCount > 100U && byteContent.compression > 80U -> 
                dispatchToTorrent(historical, byteContent)
            
            historical.latencyMs < 50U && byteContent.sequentiality > 90U ->
                dispatchToHTTP(historical, byteContent)
            
            historical.reliability > 95U && byteContent.sparsity < 10U ->
                dispatchToLocal(historical, byteContent)
            
            else -> dispatchToDHT(historical, byteContent)
        }
    }
    
    // Second dispatch: Inline dispatch calling {b: B, _: Nothing -> b.something}
    private inline fun dispatchToTorrent(
        historical: HistoricalEvidence, 
        byteContent: ByteEvidence
    ): AttentionOutcome.OptimalTorrent {
        // Symmetrical stepladder evaluation
        val confidence: Double = evaluateSymmetrical(historical, byteContent) { h: HistoricalEvidence, b: ByteEvidence ->
            (h.hitRatio.toDouble() / 65535.0) * (b.compression.toDouble() / 65535.0)
        }
        return AttentionOutcome.OptimalTorrent(confidence)
    }
    
    private inline fun dispatchToHTTP(
        historical: HistoricalEvidence,
        byteContent: ByteEvidence  
    ): AttentionOutcome.OptimalHTTP {
        val confidence: Double = evaluateSymmetrical(historical, byteContent) { h: HistoricalEvidence, b: ByteEvidence ->
            (1.0 - h.latencyMs.toDouble() / 65535.0) * (b.sequentiality.toDouble() / 65535.0)
        }
        return AttentionOutcome.OptimalHTTP(confidence)
    }
    
    private inline fun dispatchToLocal(
        historical: HistoricalEvidence,
        byteContent: ByteEvidence
    ): AttentionOutcome.OptimalLocal {
        val confidence: Double = evaluateSymmetrical(historical, byteContent) { h: HistoricalEvidence, b: ByteEvidence ->
            (h.reliability.toDouble() / 65535.0) * (1.0 - b.sparsity.toDouble() / 65535.0)
        }
        return AttentionOutcome.OptimalLocal(confidence)
    }
    
    private inline fun dispatchToDHT(
        historical: HistoricalEvidence,
        byteContent: ByteEvidence
    ): AttentionOutcome.OptimalDHT {
        val confidence: Double = evaluateSymmetrical(historical, byteContent) { h: HistoricalEvidence, b: ByteEvidence ->
            (h.accessCount.toDouble() / 65535.0) * (b.entropy.toDouble() / 65535.0)
        }
        return AttentionOutcome.OptimalDHT(confidence)
    }
    
    // Symmetrical evaluation function - both A and B stepladders contribute
    private inline fun evaluateSymmetrical(
        a: HistoricalEvidence,
        b: ByteEvidence,
        evaluator: (HistoricalEvidence, ByteEvidence) -> Double
    ): Double = evaluator(a, b)
}

// Evidence accumulation following TypeEvidence pattern
class AttentionEvidenceBuilder {
    private var historical: HistoricalEvidence = HistoricalEvidence(0UL)
    private var byteContent: ByteEvidence = ByteEvidence(0UL)
    
    // A-side accumulation
    infix fun accumulate(pattern: AccessPattern): AttentionEvidenceBuilder = apply {
        historical = historical + pattern
    }
    
    // B-side accumulation  
    infix fun accumulate(byte: Byte): AttentionEvidenceBuilder = apply {
        byteContent = byteContent + byte
    }
    
    // Join the symmetrical stepladders
    fun build(): AttentionEvidence = historical j byteContent
}

// Usage pattern following TypeEvidence style
fun buildAttentionEvidence(
    accessHistory: Indexed<AccessPattern>,
    contentBytes: Indexed<Byte>
): AttentionEvidence {
    val builder = AttentionEvidenceBuilder()
    
    // Accumulate A-side evidence
    for (i in 0 until accessHistory.a) {
        builder accumulate accessHistory.b(i)
    }
    
    // Accumulate B-side evidence
    for (i in 0 until contentBytes.a) {
        builder accumulate contentBytes.b(i)
    }
    
    return builder.build()
}