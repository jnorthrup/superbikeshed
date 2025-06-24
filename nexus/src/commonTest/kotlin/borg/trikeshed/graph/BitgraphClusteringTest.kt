package borg.trikeshed.graph

import borg.trikeshed.lib.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bitgraph Clustering Test - Dense packing of positional evidence chains
 * 
 * Demonstrates how positional evidence statistics from keyword anchors
 * get packed into dense bitfield structures for rapid LLM consumption.
 * This creates a "semantic map" that preserves forward/backward chaining
 * relationships in compressed form.
 */
class BitgraphClusteringTest {
    
    /**
     * Dense evidence cluster - packs positional statistics into bit fields
     * Each field represents accumulated evidence from forward/backward chaining
     */
    @JvmInline
    value class DenseEvidenceCluster(val packed: ULong) {
        
        // Bit layout (64 bits total):
        // Bits 0-7:   Backward evidence (-5 to -1 positions)
        // Bits 8-15:  Forward evidence (+1 to +5 positions) 
        // Bits 16-23: Pattern confidence (0-255)
        // Bits 24-31: Anchor type (keyword that triggered scan)
        // Bits 32-39: Clustering strength (how many similar patterns)
        // Bits 40-47: Context depth (nesting level)
        // Bits 48-55: Semantic weight (importance score)
        // Bits 56-63: Reserved for future evidence types
        
        val backwardEvidence: UByte get() = (packed and 0xFFUL).toUByte()
        val forwardEvidence: UByte get() = ((packed shr 8) and 0xFFUL).toUByte()
        val patternConfidence: UByte get() = ((packed shr 16) and 0xFFUL).toUByte()
        val anchorType: UByte get() = ((packed shr 24) and 0xFFUL).toUByte()
        val clusteringStrength: UByte get() = ((packed shr 32) and 0xFFUL).toUByte()
        val contextDepth: UByte get() = ((packed shr 40) and 0xFFUL).toUByte()
        val semanticWeight: UByte get() = ((packed shr 48) and 0xFFUL).toUByte()
        
        companion object {
            fun create(
                backwardEvidence: UByte,
                forwardEvidence: UByte,
                patternConfidence: UByte,
                anchorType: UByte,
                clusteringStrength: UByte = 1U,
                contextDepth: UByte = 0U,
                semanticWeight: UByte = 128U
            ): DenseEvidenceCluster {
                val packed = backwardEvidence.toULong() or
                    (forwardEvidence.toULong() shl 8) or
                    (patternConfidence.toULong() shl 16) or
                    (anchorType.toULong() shl 24) or
                    (clusteringStrength.toULong() shl 32) or
                    (contextDepth.toULong() shl 40) or
                    (semanticWeight.toULong() shl 48)
                
                return DenseEvidenceCluster(packed)
            }
        }
    }
    
    /**
     * Evidence cluster collection - Series of dense clusters for LLM consumption
     */
    typealias EvidenceClusterGraph = Indexed<DenseEvidenceCluster>
    
    /**
     * Anchor type constants for bitpacking
     */
    object AnchorTypes {
        const val CLASS: UByte = 1U
        const val FUNCTION: UByte = 2U
        const val PROPERTY: UByte = 3U
        const val INTERFACE: UByte = 4U
        const val OBJECT: UByte = 5U
        const val ENUM: UByte = 6U
    }
    
    /**
     * Evidence bit patterns for specific code constructs
     */
    object EvidenceBits {
        // Backward evidence bits (what comes before anchor)
        const val VISIBILITY_BEFORE: UByte = 0x01U  // private/public before
        const val MODIFIER_BEFORE: UByte = 0x02U    // suspend/inline before
        const val ANNOTATION_BEFORE: UByte = 0x04U  // @Annotation before
        const val GENERIC_BEFORE: UByte = 0x08U     // <T> before
        
        // Forward evidence bits (what comes after anchor)
        const val IDENTIFIER_AFTER: UByte = 0x01U   // name after
        const val COLON_AFTER: UByte = 0x02U        // : after (inheritance/type)
        const val PAREN_AFTER: UByte = 0x04U        // ( after (function)
        const val LAMBDA_AFTER: UByte = 0x08U       // { after (lambda)
        const val EQUALS_AFTER: UByte = 0x10U       // = after (assignment)
    }
    
    /**
     * Evidence cluster builder - converts positional statistics to dense form
     */
    class EvidenceClusterBuilder {
        private val clusters = mutableListOf<DenseEvidenceCluster>()
        
        fun addPropertyEvidence(
            hasVisibility: Boolean = false,
            hasType: Boolean = false,
            hasInitializer: Boolean = false,
            confidence: UByte = 200U
        ) {
            var backward: UByte = 0U
            var forward: UByte = 0U
            
            if (hasVisibility) backward = backward or EvidenceBits.VISIBILITY_BEFORE
            if (hasType) forward = forward or EvidenceBits.COLON_AFTER
            if (hasInitializer) forward = forward or EvidenceBits.EQUALS_AFTER
            
            val cluster = DenseEvidenceCluster.create(
                backwardEvidence = backward,
                forwardEvidence = forward,
                patternConfidence = confidence,
                anchorType = AnchorTypes.PROPERTY
            )
            
            clusters.add(cluster)
        }
        
        fun addFunctionEvidence(
            hasSuspend: Boolean = false,
            hasInline: Boolean = false,
            hasParameters: Boolean = false,
            hasLambda: Boolean = false,
            confidence: UByte = 220U
        ) {
            var backward: UByte = 0U
            var forward: UByte = 0U
            
            if (hasSuspend || hasInline) backward = backward or EvidenceBits.MODIFIER_BEFORE
            if (hasParameters) forward = forward or EvidenceBits.PAREN_AFTER
            if (hasLambda) forward = forward or EvidenceBits.LAMBDA_AFTER
            forward = forward or EvidenceBits.IDENTIFIER_AFTER // functions always have names
            
            val cluster = DenseEvidenceCluster.create(
                backwardEvidence = backward,
                forwardEvidence = forward,
                patternConfidence = confidence,
                anchorType = AnchorTypes.FUNCTION
            )
            
            clusters.add(cluster)
        }
        
        fun addClassEvidence(
            hasAnnotation: Boolean = false,
            hasModifier: Boolean = false,
            hasInheritance: Boolean = false,
            confidence: UByte = 240U
        ) {
            var backward: UByte = 0U
            var forward: UByte = 0U
            
            if (hasAnnotation) backward = backward or EvidenceBits.ANNOTATION_BEFORE
            if (hasModifier) backward = backward or EvidenceBits.MODIFIER_BEFORE
            if (hasInheritance) forward = forward or EvidenceBits.COLON_AFTER
            forward = forward or EvidenceBits.IDENTIFIER_AFTER // classes always have names
            
            val cluster = DenseEvidenceCluster.create(
                backwardEvidence = backward,
                forwardEvidence = forward,
                patternConfidence = confidence,
                anchorType = AnchorTypes.CLASS
            )
            
            clusters.add(cluster)
        }
        
        fun build(): EvidenceClusterGraph {
            return clusters.toIndexed()
        }
    }
    
    /**
     * Pattern matcher that works on dense clusters
     */
    class DensePatternMatcher {
        
        fun matchProperty(cluster: DenseEvidenceCluster): Boolean {
            return cluster.anchorType == AnchorTypes.PROPERTY &&
                (cluster.forwardEvidence and EvidenceBits.COLON_AFTER) != 0U.toUByte()
        }
        
        fun matchFunction(cluster: DenseEvidenceCluster): Boolean {
            return cluster.anchorType == AnchorTypes.FUNCTION &&
                (cluster.forwardEvidence and EvidenceBits.IDENTIFIER_AFTER) != 0U.toUByte()
        }
        
        fun matchSuspendFunction(cluster: DenseEvidenceCluster): Boolean {
            return matchFunction(cluster) &&
                (cluster.backwardEvidence and EvidenceBits.MODIFIER_BEFORE) != 0U.toUByte()
        }
        
        fun matchAnnotatedClass(cluster: DenseEvidenceCluster): Boolean {
            return cluster.anchorType == AnchorTypes.CLASS &&
                (cluster.backwardEvidence and EvidenceBits.ANNOTATION_BEFORE) != 0U.toUByte()
        }
        
        fun calculateSemanticSimilarity(cluster1: DenseEvidenceCluster, cluster2: DenseEvidenceCluster): Float {
            // Compare evidence patterns using bit operations
            val backwardSimilarity = (cluster1.backwardEvidence and cluster2.backwardEvidence).countOneBits()
            val forwardSimilarity = (cluster1.forwardEvidence and cluster2.forwardEvidence).countOneBits()
            val anchorMatch = if (cluster1.anchorType == cluster2.anchorType) 4 else 0
            
            val totalSimilarity = backwardSimilarity + forwardSimilarity + anchorMatch
            return totalSimilarity / 12f // Normalize to 0-1 range
        }
    }
    
    @Test
    fun testDenseEvidenceClusterPacking() {
        val cluster = DenseEvidenceCluster.create(
            backwardEvidence = 0x05U, // visibility + annotation
            forwardEvidence = 0x03U,  // identifier + colon
            patternConfidence = 200U,
            anchorType = AnchorTypes.PROPERTY
        )
        
        assertEquals(0x05U, cluster.backwardEvidence)
        assertEquals(0x03U, cluster.forwardEvidence)
        assertEquals(200U, cluster.patternConfidence)
        assertEquals(AnchorTypes.PROPERTY, cluster.anchorType)
    }
    
    @Test
    fun testPropertyEvidenceClustering() {
        val builder = EvidenceClusterBuilder()
        
        // Add evidence for: "private val name: String = "value""
        builder.addPropertyEvidence(
            hasVisibility = true,
            hasType = true,
            hasInitializer = true,
            confidence = 250U
        )
        
        val clusters = builder.build()
        assertEquals(1, clusters.size)
        
        val cluster = clusters[0]
        val matcher = DensePatternMatcher()
        assertTrue(matcher.matchProperty(cluster))
        assertEquals(250U, cluster.patternConfidence)
    }
    
    @Test
    fun testFunctionEvidenceClustering() {
        val builder = EvidenceClusterBuilder()
        
        // Add evidence for: "suspend fun process(data: String) { ... }"
        builder.addFunctionEvidence(
            hasSuspend = true,
            hasParameters = true,
            hasLambda = true,
            confidence = 230U
        )
        
        val clusters = builder.build()
        val cluster = clusters[0]
        val matcher = DensePatternMatcher()
        
        assertTrue(matcher.matchFunction(cluster))
        assertTrue(matcher.matchSuspendFunction(cluster))
    }
    
    @Test
    fun testClassEvidenceClustering() {
        val builder = EvidenceClusterBuilder()
        
        // Add evidence for: "@Entity data class User : Entity"
        builder.addClassEvidence(
            hasAnnotation = true,
            hasModifier = true,
            hasInheritance = true,
            confidence = 240U
        )
        
        val clusters = builder.build()
        val cluster = clusters[0]
        val matcher = DensePatternMatcher()
        
        assertTrue(matcher.matchAnnotatedClass(cluster))
    }
    
    @Test
    fun testSemanticSimilarityCalculation() {
        val builder = EvidenceClusterBuilder()
        
        // Two similar property declarations
        builder.addPropertyEvidence(hasVisibility = true, hasType = true)
        builder.addPropertyEvidence(hasVisibility = true, hasType = true, hasInitializer = true)
        
        val clusters = builder.build()
        val matcher = DensePatternMatcher()
        
        val similarity = matcher.calculateSemanticSimilarity(clusters[0], clusters[1])
        assertTrue(similarity > 0.5f) // Should be similar
    }
    
    @Test
    fun testLLMConsumableSemanticMap() {
        val builder = EvidenceClusterBuilder()
        
        // Build a complete semantic map of a class file
        builder.addClassEvidence(hasAnnotation = true, hasInheritance = true)
        builder.addPropertyEvidence(hasVisibility = true, hasType = true)
        builder.addPropertyEvidence(hasVisibility = true, hasType = true, hasInitializer = true)
        builder.addFunctionEvidence(hasSuspend = true, hasParameters = true)
        builder.addFunctionEvidence(hasInline = true, hasLambda = true)
        
        val semanticMap = builder.build()
        
        // LLM can now rapidly scan this dense representation
        assertEquals(5, semanticMap.size)
        
        val matcher = DensePatternMatcher()
        
        // Count different pattern types in the semantic map
        var classCount = 0
        var propertyCount = 0
        var functionCount = 0
        var suspendFunctionCount = 0
        
        semanticMap.forEach { cluster ->
            when {
                cluster.anchorType == AnchorTypes.CLASS -> classCount++
                matcher.matchProperty(cluster) -> propertyCount++
                matcher.matchSuspendFunction(cluster) -> suspendFunctionCount++
                matcher.matchFunction(cluster) -> functionCount++
            }
        }
        
        assertEquals(1, classCount)
        assertEquals(2, propertyCount)
        assertEquals(1, suspendFunctionCount)
        assertEquals(1, functionCount) // inline function (not suspend)
        
        // This dense map gives LLM instant understanding of code structure
        // without parsing - like "doxygen for LLMs"
    }
    
    @Test
    fun testClusteringStrengthensPatternsRecognition() {
        val builder = EvidenceClusterBuilder()
        
        // Multiple similar properties strengthen the pattern
        repeat(5) {
            builder.addPropertyEvidence(
                hasVisibility = true,
                hasType = true,
                confidence = (200U + it * 10U).toUByte()
            )
        }
        
        val clusters = builder.build()
        val avgConfidence = clusters.map { it.patternConfidence.toInt() }.average()
        
        // Multiple instances strengthen confidence
        assertTrue(avgConfidence > 200.0)
        
        // All clusters should match the same pattern
        val matcher = DensePatternMatcher()
        assertTrue(clusters.all { matcher.matchProperty(it) })
    }
    
    private fun <T> MutableList<T>.toIndexed(): Indexed<T> {
        return this.fold(createEmptyIndexed<T>()) { acc, item -> acc.add(item) }
    }
}