package borg.trikeshed.graph

import borg.trikeshed.lib.*

/**
 * Evidence Chain Bitgraph System using Inline Value Classes as Inference Markers
 * 
 * Each inline value class serves as a typed witness in the inference chain,
 * allowing AI to reconstruct reasoning paths and evidence provenance.
 * 
 * Design Philosophy:
 * - Inline classes = typed evidence markers
 * - Join structures = provable logical connections  
 * - Confidence values = evidence strength tracking
 * - Temporal markers = inference sequence preservation
 */

/**
 * Evidence Source - where the information originated
 */
@JvmInline
value class EvidenceSource(val source: String)

/**
 * Inference Step - specific reasoning operation performed
 */
@JvmInline
value class InferenceStep(val step: String)

/**
 * Evidence Strength - quantified reliability of the evidence
 */
@JvmInline
value class EvidenceStrength(val strength: UByte)

/**
 * Reasoning Depth - how many inference steps from source
 */
@JvmInline
value class ReasoningDepth(val depth: UShort)

/**
 * Evidence Chain ID - unique identifier for inference sequence
 */
@JvmInline
value class EvidenceChainId(val chainId: ULong)

/**
 * Primary evidence marker: source + initial strength
 */
typealias PrimaryEvidence = Join<EvidenceSource, EvidenceStrength>

/**
 * Inference operation: step + resulting strength
 */
typealias InferenceOperation = Join<InferenceStep, EvidenceStrength>

/**
 * Evidence progression: primary evidence -> inference operation
 */
typealias EvidenceProgression = Join<PrimaryEvidence, InferenceOperation>

/**
 * Reasoning chain: progression + depth tracking
 */
typealias ReasoningChain = Join<EvidenceProgression, ReasoningDepth>

/**
 * Complete evidence chain: reasoning + chain ID + timestamp
 */
typealias CompleteEvidenceChain = Join<Join<ReasoningChain, EvidenceChainId>, UpdateTimestamp>

/**
 * Symbol with evidence chain - connects symbols to their inference provenance
 */
typealias EvidenceTrackedSymbol = Join<DenseSymbolNode, CompleteEvidenceChain>

/**
 * Collection of evidence-tracked symbols
 */
typealias EvidenceSymbolGraph = Indexed<EvidenceTrackedSymbol>

/**
 * Evidence chain aggregation - multiple chains contributing to one conclusion
 */
typealias EvidenceAggregation = Join<Indexed<CompleteEvidenceChain>, EvidenceStrength>

/**
 * Confidence propagation through inference steps
 */
fun propagateConfidence(
    initialStrength: EvidenceStrength,
    inferenceReliability: Float
): EvidenceStrength {
    val newStrength = (initialStrength.strength.toFloat() * inferenceReliability).toInt()
    return EvidenceStrength(newStrength.coerceIn(0, 255).toUByte())
}

/**
 * Evidence source constants
 */
object EvidenceSources {
    const val SOURCE_CODE = "source_code"
    const val AST_PARSER = "ast_parser"
    const val KSP_PROCESSOR = "ksp_processor"
    const val KOTLIN_COMPILER = "kotlin_compiler"
    const val INTELLIJ_PSI = "intellij_psi"
    const val ENTITY_SCANNER = "entity_scanner"
    const val AI_INFERENCE = "ai_inference"
    const val STATIC_ANALYSIS = "static_analysis"
    const val RUNTIME_OBSERVATION = "runtime_observation"
}

/**
 * Inference step constants
 */
object InferenceSteps {
    const val PARSE_DECLARATION = "parse_declaration"
    const val RESOLVE_TYPE = "resolve_type"
    const val ANALYZE_USAGE = "analyze_usage"
    const val INFER_RELATIONSHIP = "infer_relationship"
    const val DEDUCE_INTENT = "deduce_intent"
    const val AGGREGATE_EVIDENCE = "aggregate_evidence"
    const val VALIDATE_CONSISTENCY = "validate_consistency"
    const val PROPAGATE_CONFIDENCE = "propagate_confidence"
    const val SYNTHESIZE_CONCLUSION = "synthesize_conclusion"
}

/**
 * Evidence strength levels
 */
object EvidenceStrengthLevels {
    const val DEFINITIVE: UByte = 255u    // Direct observation
    const val VERY_STRONG: UByte = 224u   // Multiple confirming sources
    const val STRONG: UByte = 192u        // Reliable inference
    const val MODERATE: UByte = 128u      // Reasonable deduction
    const val WEAK: UByte = 64u           // Tentative hypothesis
    const val VERY_WEAK: UByte = 32u      // Speculative guess
}

/**
 * Extension functions for evidence chain navigation
 */
val CompleteEvidenceChain.reasoning: ReasoningChain get() = this.a.a
val CompleteEvidenceChain.chainId: EvidenceChainId get() = this.a.b
val CompleteEvidenceChain.timestamp: UpdateTimestamp get() = this.b

val ReasoningChain.progression: EvidenceProgression get() = this.a
val ReasoningChain.depth: ReasoningDepth get() = this.b

val EvidenceProgression.primary: PrimaryEvidence get() = this.a
val EvidenceProgression.inference: InferenceOperation get() = this.b

val PrimaryEvidence.source: EvidenceSource get() = this.a
val PrimaryEvidence.strength: EvidenceStrength get() = this.b

val InferenceOperation.step: InferenceStep get() = this.a
val InferenceOperation.resultStrength: EvidenceStrength get() = this.b

val EvidenceTrackedSymbol.symbol: DenseSymbolNode get() = this.a
val EvidenceTrackedSymbol.evidenceChain: CompleteEvidenceChain get() = this.b

/**
 * Factory functions for creating evidence chains
 */
fun createPrimaryEvidence(
    source: String,
    strength: UByte
): PrimaryEvidence {
    return EvidenceSource(source) j EvidenceStrength(strength)
}

fun createInferenceOperation(
    step: String,
    initialStrength: EvidenceStrength,
    reliability: Float
): InferenceOperation {
    val resultStrength = propagateConfidence(initialStrength, reliability)
    return InferenceStep(step) j resultStrength
}

fun createEvidenceProgression(
    source: String,
    sourceStrength: UByte,
    inferenceStep: String,
    inferenceReliability: Float
): EvidenceProgression {
    val primary = createPrimaryEvidence(source, sourceStrength)
    val inference = createInferenceOperation(inferenceStep, primary.strength, inferenceReliability)
    return primary j inference
}

fun createCompleteEvidenceChain(
    progression: EvidenceProgression,
    depth: UShort,
    chainId: ULong,
    timestamp: UInt
): CompleteEvidenceChain {
    val reasoning = progression j ReasoningDepth(depth)
    val chainWithId = reasoning j EvidenceChainId(chainId)
    return chainWithId j UpdateTimestamp(timestamp)
}

/**
 * Evidence Chain Builder for constructing inference sequences
 */
class EvidenceChainBuilder {
    private var chainId = 0UL
    private val currentTime get() = (System.currentTimeMillis() / 1000).toUInt()
    
    fun buildChain(
        initialSource: String,
        initialStrength: UByte,
        inferenceSteps: Indexed<Join<String, Float>>
    ): CompleteEvidenceChain {
        val chainId = ++this.chainId
        
        // Start with primary evidence
        val primary = createPrimaryEvidence(initialSource, initialStrength)
        var currentStrength = primary.strength
        var depth = 0u.toUShort()
        
        // Apply inference steps
        val finalInference = if (inferenceSteps.isEmpty()) {
            // No inference steps, just primary evidence
            InferenceStep("direct_observation") j currentStrength
        } else {
            // Take the last inference step as the final result
            val lastStep = inferenceSteps.last()
            depth = inferenceSteps.size.toUShort()
            
            // Propagate confidence through all steps
            inferenceSteps.forEach { stepWithReliability ->
                currentStrength = propagateConfidence(currentStrength, stepWithReliability.b)
            }
            
            InferenceStep(lastStep.a) j currentStrength
        }
        
        val progression = primary j finalInference
        return createCompleteEvidenceChain(progression, depth, chainId, currentTime)
    }
}

/**
 * Evidence Chain Analyzer for tracking inference quality
 */
class EvidenceChainAnalyzer {
    
    /**
     * Analyze the reliability of an evidence chain
     */
    fun analyzeChainReliability(chain: CompleteEvidenceChain): Float {
        val primaryStrength = chain.reasoning.progression.primary.strength.strength.toFloat()
        val finalStrength = chain.reasoning.progression.inference.resultStrength.strength.toFloat()
        val depth = chain.reasoning.depth.depth.toFloat()
        
        // Factor in evidence degradation over inference depth
        val depthPenalty = 1.0f / (1.0f + depth * 0.1f)
        val strengthRatio = finalStrength / primaryStrength.coerceAtLeast(1.0f)
        
        return strengthRatio * depthPenalty
    }
    
    /**
     * Find the weakest link in an evidence chain
     */
    fun findWeakestLink(chain: CompleteEvidenceChain): String {
        val primaryStrength = chain.reasoning.progression.primary.strength.strength
        val finalStrength = chain.reasoning.progression.inference.resultStrength.strength
        
        return if (primaryStrength < finalStrength) {
            "primary_evidence_weak"
        } else {
            "inference_degradation"
        }
    }
    
    /**
     * Recommend improvements for evidence quality
     */
    fun recommendImprovements(chain: CompleteEvidenceChain): Indexed<String> {
        val recommendations = mutableListOf<String>()
        val reliability = analyzeChainReliability(chain)
        
        if (reliability < 0.5f) {
            recommendations.add("strengthen_primary_evidence")
        }
        
        if (chain.reasoning.depth.depth > 5u) {
            recommendations.add("reduce_inference_depth")
        }
        
        val finalStrength = chain.reasoning.progression.inference.resultStrength.strength
        if (finalStrength < EvidenceStrengthLevels.MODERATE) {
            recommendations.add("seek_additional_evidence_sources")
        }
        
        return recommendations.toIndexed()
    }
}

/**
 * Convert MutableList to Indexed for TrikeShed compatibility
 */
private fun <T> MutableList<T>.toIndexed(): Indexed<T> {
    return this.fold(createEmptyIndexed<T>()) { acc, item -> acc.add(item) }
}