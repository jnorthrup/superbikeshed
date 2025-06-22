package borg.trikeshed.graph

import borg.trikeshed.lib.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Symbol Evidence Test - Demonstrates trait clustering for Kotlin symbols
 * 
 * Shows how the TypeEvidence pattern scales from CSV analysis to code comprehension.
 * Just as CSV TypeEvidence counts character classes to deduce data types,
 * SymbolEvidence counts code patterns to deduce architectural traits.
 */
class SymbolEvidenceTest {
    
    /**
     * Symbol Evidence - A trait clustering engine for Kotlin code
     * Similar to TypeEvidence but for code structure instead of character classes
     */
    data class SymbolEvidence(
        // Modifier evidence counters
        var publicCount: UShort = 0U,
        var privateCount: UShort = 0U,
        var protectedCount: UShort = 0U,
        var internalCount: UShort = 0U,
        var abstractCount: UShort = 0U,
        var finalCount: UShort = 0U,
        var openCount: UShort = 0U,
        var overrideCount: UShort = 0U,
        var inlineCount: UShort = 0U,
        var suspendCount: UShort = 0U,
        var operatorCount: UShort = 0U,
        var infixCount: UShort = 0U,
        var dataCount: UShort = 0U,
        var sealedCount: UShort = 0U,
        var companionCount: UShort = 0U,
        
        // Position evidence counters
        var topLevelPosition: UShort = 0U,
        var nestedDepth: UShort = 0U,
        var parameterPosition: UShort = 0U,
        var receiverPosition: UShort = 0U,
        
        // Relationship evidence counters
        var inheritanceCount: UShort = 0U,
        var implementsCount: UShort = 0U,
        var callCount: UShort = 0U,
        var referencesCount: UShort = 0U,
        var importCount: UShort = 0U,
        var annotationCount: UShort = 0U,
        
        // Pattern evidence counters
        var lambdaCount: UShort = 0U,
        var genericCount: UShort = 0U,
        var extensionCount: UShort = 0U,
        var dslCount: UShort = 0U
    ) {
        /**
         * Accumulate evidence from a symbol's modifiers
         * Similar to TypeEvidence's plus(char) operator
         */
        fun accumulateModifier(modifier: ModifierBits) {
            when (modifier) {
                ModifierBits.PUBLIC -> publicCount++
                ModifierBits.PRIVATE -> privateCount++
                ModifierBits.PROTECTED -> protectedCount++
                ModifierBits.INTERNAL -> internalCount++
                ModifierBits.ABSTRACT -> abstractCount++
                ModifierBits.FINAL -> finalCount++
                ModifierBits.OPEN -> openCount++
                ModifierBits.OVERRIDE -> overrideCount++
                ModifierBits.INLINE -> inlineCount++
                ModifierBits.SUSPEND -> suspendCount++
                ModifierBits.OPERATOR -> operatorCount++
                ModifierBits.INFIX -> infixCount++
                ModifierBits.DATA -> dataCount++
                ModifierBits.SEALED -> sealedCount++
                ModifierBits.COMPANION -> companionCount++
            }
        }
        
        /**
         * Deduce architectural pattern from evidence clusters
         * Similar to TypeEvidence.deduce() but for code patterns
         */
        fun deducePattern(): ArchitecturalPattern {
            return when {
                // DSL Builder pattern
                dslCount > 0U && inlineCount > 0U && extensionCount > 0U -> 
                    ArchitecturalPattern.DSL_BUILDER
                
                // Coroutine pattern
                suspendCount > 0U && (lambdaCount > 0U || parameterPosition > 0U) ->
                    ArchitecturalPattern.COROUTINE_FUNCTION
                
                // Factory pattern
                companionCount > 0U && operatorCount > 0U && topLevelPosition == 0U ->
                    ArchitecturalPattern.FACTORY_METHOD
                
                // Strategy pattern
                abstractCount > 0U && inheritanceCount > 0U && overrideCount > 0U ->
                    ArchitecturalPattern.STRATEGY_IMPLEMENTATION
                
                // Value class pattern
                dataCount > 0U && finalCount > 0U && publicCount > privateCount ->
                    ArchitecturalPattern.VALUE_CLASS
                
                // Sealed hierarchy pattern
                sealedCount > 0U && (abstractCount > 0U || inheritanceCount > 0U) ->
                    ArchitecturalPattern.SEALED_HIERARCHY
                
                // Extension function pattern
                extensionCount > 0U && topLevelPosition > 0U ->
                    ArchitecturalPattern.EXTENSION_FUNCTION
                
                // Singleton pattern
                objectCount > 0U && topLevelPosition > 0U ->
                    ArchitecturalPattern.SINGLETON_OBJECT
                
                else -> ArchitecturalPattern.UNKNOWN
            }
        }
        
        // Additional counter for object declarations
        var objectCount: UShort = 0U
        
        /**
         * Calculate confidence score based on evidence strength
         * Stronger clustering = higher confidence
         */
        fun calculateConfidence(): Float {
            val totalEvidence = publicCount + privateCount + protectedCount + internalCount +
                abstractCount + finalCount + openCount + overrideCount + inlineCount +
                suspendCount + operatorCount + infixCount + dataCount + sealedCount +
                companionCount + inheritanceCount + implementsCount + callCount +
                referencesCount + importCount + annotationCount + lambdaCount +
                genericCount + extensionCount + dslCount
            
            // More evidence = higher confidence (normalized to 0-1)
            return minOf(totalEvidence.toFloat() / 20f, 1.0f)
        }
    }
    
    /**
     * Architectural patterns that can be deduced from evidence
     */
    enum class ArchitecturalPattern {
        DSL_BUILDER,
        COROUTINE_FUNCTION,
        FACTORY_METHOD,
        STRATEGY_IMPLEMENTATION,
        VALUE_CLASS,
        SEALED_HIERARCHY,
        EXTENSION_FUNCTION,
        SINGLETON_OBJECT,
        UNKNOWN
    }
    
    @Test
    fun testDSLBuilderPatternDetection() {
        val evidence = SymbolEvidence().apply {
            inlineCount = 3U
            extensionCount = 5U
            dslCount = 2U
            publicCount = 8U
        }
        
        val pattern = evidence.deducePattern()
        assertEquals(ArchitecturalPattern.DSL_BUILDER, pattern)
        assertTrue(evidence.calculateConfidence() > 0.5f)
    }
    
    @Test
    fun testCoroutinePatternDetection() {
        val evidence = SymbolEvidence().apply {
            suspendCount = 2U
            lambdaCount = 1U
            parameterPosition = 3U
            publicCount = 2U
        }
        
        val pattern = evidence.deducePattern()
        assertEquals(ArchitecturalPattern.COROUTINE_FUNCTION, pattern)
    }
    
    @Test
    fun testFactoryPatternDetection() {
        val evidence = SymbolEvidence().apply {
            companionCount = 1U
            operatorCount = 1U  // invoke operator
            topLevelPosition = 0U  // nested in class
            publicCount = 2U
        }
        
        val pattern = evidence.deducePattern()
        assertEquals(ArchitecturalPattern.FACTORY_METHOD, pattern)
    }
    
    @Test
    fun testValueClassPatternDetection() {
        val evidence = SymbolEvidence().apply {
            dataCount = 1U
            finalCount = 1U
            publicCount = 5U  // constructor + properties
            privateCount = 1U  // maybe one private helper
        }
        
        val pattern = evidence.deducePattern()
        assertEquals(ArchitecturalPattern.VALUE_CLASS, pattern)
    }
    
    @Test
    fun testEvidenceAccumulation() {
        val evidence = SymbolEvidence()
        
        // Simulate processing a suspend inline extension function
        evidence.accumulateModifier(ModifierBits.PUBLIC)
        evidence.accumulateModifier(ModifierBits.INLINE)
        evidence.accumulateModifier(ModifierBits.SUSPEND)
        evidence.extensionCount++
        
        assertEquals(1U, evidence.publicCount)
        assertEquals(1U, evidence.inlineCount)
        assertEquals(1U, evidence.suspendCount)
        assertEquals(1U, evidence.extensionCount)
    }
    
    @Test
    fun testConfidenceCalculation() {
        val weakEvidence = SymbolEvidence().apply {
            publicCount = 1U
        }
        
        val strongEvidence = SymbolEvidence().apply {
            publicCount = 5U
            overrideCount = 3U
            inheritanceCount = 2U
            abstractCount = 1U
            annotationCount = 4U
        }
        
        assertTrue(weakEvidence.calculateConfidence() < 0.2f)
        assertTrue(strongEvidence.calculateConfidence() > 0.5f)
    }
    
    @Test
    fun testClusteringLogic() {
        // Test that similar evidence clusters lead to same pattern
        val evidence1 = SymbolEvidence().apply {
            sealedCount = 1U
            abstractCount = 1U
            inheritanceCount = 3U
        }
        
        val evidence2 = SymbolEvidence().apply {
            sealedCount = 1U
            inheritanceCount = 5U  // different count but same pattern
            abstractCount = 0U    // sealed can exist without abstract
        }
        
        assertEquals(
            ArchitecturalPattern.SEALED_HIERARCHY,
            evidence1.deducePattern()
        )
        assertEquals(
            ArchitecturalPattern.SEALED_HIERARCHY,
            evidence2.deducePattern()
        )
    }
    
    /**
     * Test that demonstrates how evidence clustering bolsters reasoning
     * Multiple weak signals combine to create strong pattern recognition
     */
    @Test
    fun testEvidenceClusteringBolstersReasoning() {
        val ambiguousEvidence = SymbolEvidence().apply {
            // Weak signals that individually mean little
            publicCount = 1U
            extensionCount = 1U
        }
        
        val clusteredEvidence = SymbolEvidence().apply {
            // Multiple related signals cluster into clear pattern
            publicCount = 3U
            extensionCount = 2U
            inlineCount = 2U
            dslCount = 1U
            // These cluster together to strongly indicate DSL builder
        }
        
        assertEquals(ArchitecturalPattern.EXTENSION_FUNCTION, ambiguousEvidence.deducePattern())
        assertEquals(ArchitecturalPattern.DSL_BUILDER, clusteredEvidence.deducePattern())
        
        // Clustered evidence has higher confidence
        assertTrue(clusteredEvidence.calculateConfidence() > ambiguousEvidence.calculateConfidence())
    }
}