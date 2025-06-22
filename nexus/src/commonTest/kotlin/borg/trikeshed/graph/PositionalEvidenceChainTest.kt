package borg.trikeshed.graph

import borg.trikeshed.lib.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Positional Evidence Chain Test
 * 
 * Demonstrates how token scanners collect evidence based on positional relationships
 * from keyword anchors using forward and backward chaining rules.
 * 
 * Key concepts:
 * - Keyword anchors (class, fun, val, etc.) serve as reference points
 * - Forward chaining: what follows a keyword tells us about intent
 * - Backward chaining: what precedes a keyword provides context
 * - Positional statistics create evidence signatures
 */
class PositionalEvidenceChainTest {
    
    /**
     * Positional evidence collected relative to keyword anchors
     */
    data class PositionalEvidence(
        // Distance from anchor (negative = before, positive = after)
        val relativePosition: Int,
        val tokenType: TokenType,
        val tokenValue: String,
        val anchorKeyword: KeywordAnchor
    )
    
    /**
     * Evidence chain builder that tracks positional relationships
     */
    class PositionalEvidenceChain {
        private val forwardChain = mutableListOf<PositionalEvidence>()
        private val backwardChain = mutableListOf<PositionalEvidence>()
        private val statistics = PositionalStatistics()
        
        /**
         * Process tokens with positional awareness from anchor
         */
        fun processTokenSequence(tokens: List<Token>, anchorIndex: Int) {
            val anchor = tokens[anchorIndex]
            require(anchor.type == TokenType.KEYWORD) { "Anchor must be keyword" }
            
            val anchorKeyword = KeywordAnchor.fromString(anchor.value)
            
            // Backward chaining - analyze what comes before
            for (i in (anchorIndex - 1) downTo maxOf(0, anchorIndex - 5)) {
                val token = tokens[i]
                val evidence = PositionalEvidence(
                    relativePosition = i - anchorIndex,
                    tokenType = token.type,
                    tokenValue = token.value,
                    anchorKeyword = anchorKeyword
                )
                backwardChain.add(evidence)
                statistics.recordBackwardPattern(evidence)
            }
            
            // Forward chaining - analyze what comes after
            for (i in (anchorIndex + 1) until minOf(tokens.size, anchorIndex + 10)) {
                val token = tokens[i]
                val evidence = PositionalEvidence(
                    relativePosition = i - anchorIndex,
                    tokenType = token.type,
                    tokenValue = token.value,
                    anchorKeyword = anchorKeyword
                )
                forwardChain.add(evidence)
                statistics.recordForwardPattern(evidence)
            }
        }
        
        /**
         * Deduce pattern based on positional evidence statistics
         */
        fun deducePattern(): CodePattern {
            return statistics.deducePattern()
        }
        
        fun getStatistics() = statistics
    }
    
    /**
     * Positional statistics accumulator
     */
    class PositionalStatistics {
        // Position -1 (immediately before anchor)
        var modifierBeforeCount = 0
        var annotationBeforeCount = 0
        var visibilityBeforeCount = 0
        
        // Position +1 (immediately after anchor)
        var identifierAfterCount = 0
        var genericAfterCount = 0
        var colonAfterCount = 0
        
        // Position +2 patterns
        var typeAt2Count = 0
        var equalsAt2Count = 0
        var openParenAt2Count = 0
        
        // Extended patterns
        var lambdaWithinRange = 0
        var inheritancePattern = 0
        var propertyPattern = 0
        var functionPattern = 0
        
        fun recordBackwardPattern(evidence: PositionalEvidence) {
            when (evidence.relativePosition) {
                -1 -> when (evidence.tokenType) {
                    TokenType.MODIFIER -> modifierBeforeCount++
                    TokenType.ANNOTATION -> annotationBeforeCount++
                    TokenType.VISIBILITY -> visibilityBeforeCount++
                    else -> {}
                }
            }
        }
        
        fun recordForwardPattern(evidence: PositionalEvidence) {
            when (evidence.relativePosition) {
                1 -> when (evidence.tokenType) {
                    TokenType.IDENTIFIER -> identifierAfterCount++
                    TokenType.GENERIC_START -> genericAfterCount++
                    TokenType.COLON -> colonAfterCount++
                    else -> {}
                }
                2 -> when (evidence.tokenType) {
                    TokenType.TYPE -> typeAt2Count++
                    TokenType.EQUALS -> equalsAt2Count++
                    TokenType.OPEN_PAREN -> openParenAt2Count++
                    else -> {}
                }
            }
            
            // Detect extended patterns
            if (evidence.tokenType == TokenType.LAMBDA_START && evidence.relativePosition in 1..5) {
                lambdaWithinRange++
            }
            if (evidence.tokenValue == ":" && evidence.relativePosition == 2) {
                inheritancePattern++
            }
        }
        
        fun deducePattern(): CodePattern {
            return when {
                // val/var name: Type = ...
                identifierAfterCount > 0 && colonAfterCount > 0 && typeAt2Count > 0 ->
                    CodePattern.PROPERTY_DECLARATION
                
                // fun name<T>(...) 
                identifierAfterCount > 0 && (genericAfterCount > 0 || openParenAt2Count > 0) ->
                    CodePattern.FUNCTION_DECLARATION
                
                // class Name : SuperType
                identifierAfterCount > 0 && inheritancePattern > 0 ->
                    CodePattern.CLASS_WITH_INHERITANCE
                
                // inline fun name() = { ... }
                modifierBeforeCount > 0 && lambdaWithinRange > 0 ->
                    CodePattern.INLINE_LAMBDA_FUNCTION
                
                // @Annotation class Name
                annotationBeforeCount > 0 && identifierAfterCount > 0 ->
                    CodePattern.ANNOTATED_DECLARATION
                
                else -> CodePattern.UNKNOWN
            }
        }
    }
    
    /**
     * Token representation
     */
    data class Token(
        val type: TokenType,
        val value: String
    )
    
    enum class TokenType {
        KEYWORD, IDENTIFIER, MODIFIER, ANNOTATION, VISIBILITY,
        TYPE, EQUALS, COLON, OPEN_PAREN, CLOSE_PAREN,
        GENERIC_START, GENERIC_END, LAMBDA_START, LAMBDA_END,
        OPERATOR, LITERAL, COMMA, DOT
    }
    
    enum class KeywordAnchor {
        CLASS, INTERFACE, OBJECT, FUN, VAL, VAR, ENUM, SEALED;
        
        companion object {
            fun fromString(s: String) = valueOf(s.uppercase())
        }
    }
    
    enum class CodePattern {
        PROPERTY_DECLARATION,
        FUNCTION_DECLARATION,
        CLASS_WITH_INHERITANCE,
        INLINE_LAMBDA_FUNCTION,
        ANNOTATED_DECLARATION,
        UNKNOWN
    }
    
    @Test
    fun testPropertyDeclarationPattern() {
        // "private val name: String = "value""
        val tokens = listOf(
            Token(TokenType.VISIBILITY, "private"),
            Token(TokenType.KEYWORD, "val"),
            Token(TokenType.IDENTIFIER, "name"),
            Token(TokenType.COLON, ":"),
            Token(TokenType.TYPE, "String"),
            Token(TokenType.EQUALS, "="),
            Token(TokenType.LITERAL, "\"value\"")
        )
        
        val chain = PositionalEvidenceChain()
        chain.processTokenSequence(tokens, anchorIndex = 1) // "val" is anchor
        
        assertEquals(CodePattern.PROPERTY_DECLARATION, chain.deducePattern())
        
        val stats = chain.getStatistics()
        assertEquals(1, stats.visibilityBeforeCount) // private before val
        assertEquals(1, stats.identifierAfterCount)   // name after val
        assertEquals(1, stats.colonAfterCount)        // : after val
    }
    
    @Test
    fun testFunctionDeclarationPattern() {
        // "suspend fun process(data: String): Result"
        val tokens = listOf(
            Token(TokenType.MODIFIER, "suspend"),
            Token(TokenType.KEYWORD, "fun"),
            Token(TokenType.IDENTIFIER, "process"),
            Token(TokenType.OPEN_PAREN, "("),
            Token(TokenType.IDENTIFIER, "data"),
            Token(TokenType.COLON, ":"),
            Token(TokenType.TYPE, "String"),
            Token(TokenType.CLOSE_PAREN, ")"),
            Token(TokenType.COLON, ":"),
            Token(TokenType.TYPE, "Result")
        )
        
        val chain = PositionalEvidenceChain()
        chain.processTokenSequence(tokens, anchorIndex = 1) // "fun" is anchor
        
        assertEquals(CodePattern.FUNCTION_DECLARATION, chain.deducePattern())
        
        val stats = chain.getStatistics()
        assertEquals(1, stats.modifierBeforeCount)    // suspend before fun
        assertEquals(1, stats.identifierAfterCount)   // process after fun
        assertEquals(1, stats.openParenAt2Count)      // ( at position +2
    }
    
    @Test
    fun testClassInheritancePattern() {
        // "data class User : Entity()"
        val tokens = listOf(
            Token(TokenType.MODIFIER, "data"),
            Token(TokenType.KEYWORD, "class"),
            Token(TokenType.IDENTIFIER, "User"),
            Token(TokenType.COLON, ":"),
            Token(TokenType.TYPE, "Entity"),
            Token(TokenType.OPEN_PAREN, "("),
            Token(TokenType.CLOSE_PAREN, ")")
        )
        
        val chain = PositionalEvidenceChain()
        chain.processTokenSequence(tokens, anchorIndex = 1) // "class" is anchor
        
        assertEquals(CodePattern.CLASS_WITH_INHERITANCE, chain.deducePattern())
        
        val stats = chain.getStatistics()
        assertEquals(1, stats.inheritancePattern) // : detected at position +2
    }
    
    @Test
    fun testForwardBackwardChainingInteraction() {
        // "inline fun <T> builder(init: () -> T): T = init()"
        val tokens = listOf(
            Token(TokenType.MODIFIER, "inline"),
            Token(TokenType.KEYWORD, "fun"),
            Token(TokenType.GENERIC_START, "<"),
            Token(TokenType.TYPE, "T"),
            Token(TokenType.GENERIC_END, ">"),
            Token(TokenType.IDENTIFIER, "builder"),
            Token(TokenType.OPEN_PAREN, "("),
            Token(TokenType.IDENTIFIER, "init"),
            Token(TokenType.COLON, ":"),
            Token(TokenType.LAMBDA_START, "()"),
            Token(TokenType.LAMBDA_END, "->"),
            Token(TokenType.TYPE, "T"),
            Token(TokenType.CLOSE_PAREN, ")"),
            Token(TokenType.COLON, ":"),
            Token(TokenType.TYPE, "T"),
            Token(TokenType.EQUALS, "="),
            Token(TokenType.IDENTIFIER, "init"),
            Token(TokenType.OPEN_PAREN, "("),
            Token(TokenType.CLOSE_PAREN, ")")
        )
        
        val chain = PositionalEvidenceChain()
        chain.processTokenSequence(tokens, anchorIndex = 1) // "fun" is anchor
        
        val stats = chain.getStatistics()
        assertEquals(1, stats.modifierBeforeCount)   // inline before
        assertEquals(1, stats.genericAfterCount)     // <T> after
        assertTrue(stats.lambdaWithinRange > 0)      // lambda parameter detected
        
        // This demonstrates how forward and backward evidence combine
        // to recognize an inline builder function pattern
    }
    
    @Test
    fun testMultipleAnchorAnalysis() {
        // Analyze same code from different anchor points
        val tokens = listOf(
            Token(TokenType.ANNOTATION, "@Entity"),
            Token(TokenType.KEYWORD, "class"),
            Token(TokenType.IDENTIFIER, "User"),
            Token(TokenType.OPEN_PAREN, "("),
            Token(TokenType.KEYWORD, "val"),
            Token(TokenType.IDENTIFIER, "id"),
            Token(TokenType.COLON, ":"),
            Token(TokenType.TYPE, "Long")
        )
        
        // Analyze from "class" anchor
        val classChain = PositionalEvidenceChain()
        classChain.processTokenSequence(tokens, anchorIndex = 1)
        assertEquals(CodePattern.ANNOTATED_DECLARATION, classChain.deducePattern())
        
        // Analyze from "val" anchor (inside constructor)
        val valChain = PositionalEvidenceChain()
        valChain.processTokenSequence(tokens, anchorIndex = 4)
        assertEquals(CodePattern.PROPERTY_DECLARATION, valChain.deducePattern())
        
        // Different anchors reveal different patterns in same code
    }
    
    @Test
    fun testStatisticalClustering() {
        // Test how multiple similar patterns strengthen confidence
        val chain = PositionalEvidenceChain()
        
        // Simulate processing multiple property declarations
        repeat(5) {
            val tokens = listOf(
                Token(TokenType.KEYWORD, "val"),
                Token(TokenType.IDENTIFIER, "prop$it"),
                Token(TokenType.COLON, ":"),
                Token(TokenType.TYPE, "String")
            )
            chain.processTokenSequence(tokens, 0)
        }
        
        val stats = chain.getStatistics()
        assertEquals(5, stats.identifierAfterCount)
        assertEquals(5, stats.colonAfterCount)
        
        // Statistical clustering strengthens pattern recognition
        assertEquals(CodePattern.PROPERTY_DECLARATION, chain.deducePattern())
    }
}