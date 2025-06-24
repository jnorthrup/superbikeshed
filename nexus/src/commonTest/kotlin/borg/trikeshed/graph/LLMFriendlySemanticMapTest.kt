package borg.trikeshed.graph

import borg.trikeshed.lib.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * LLM-Friendly Semantic Map Test
 * 
 * Creates semantic maps optimized for LLM consumption WITHOUT bit operations.
 * Uses explicit, readable structures that LLMs can understand directly.
 * 
 * The goal: "doxygen for LLMs" but with human-readable evidence chains
 * instead of mathematical bitfield operations.
 */
class LLMFriendlySemanticMapTest {
    
    /**
     * LLM-readable evidence structure - no bit math required
     */
    data class ReadableEvidence(
        val symbolName: String,
        val symbolType: String,
        val beforeKeyword: List<String> = emptyList(),
        val afterKeyword: List<String> = emptyList(),
        val confidenceLevel: String, // "high", "medium", "low"
        val reasoningSteps: List<String> = emptyList(),
        val architecturalPattern: String = "unknown"
    )
    
    /**
     * Pattern signatures that LLMs can read directly
     */
    object PatternSignatures {
        fun suspendFunction(evidence: ReadableEvidence): Boolean {
            return evidence.symbolType == "function" &&
                "suspend" in evidence.beforeKeyword &&
                evidence.afterKeyword.isNotEmpty()
        }
        
        fun dataClass(evidence: ReadableEvidence): Boolean {
            return evidence.symbolType == "class" &&
                "data" in evidence.beforeKeyword &&
                evidence.afterKeyword.contains(":")
        }
        
        fun privateProperty(evidence: ReadableEvidence): Boolean {
            return evidence.symbolType == "property" &&
                "private" in evidence.beforeKeyword &&
                ":" in evidence.afterKeyword
        }
        
        fun annotatedDeclaration(evidence: ReadableEvidence): Boolean {
            return evidence.beforeKeyword.any { it.startsWith("@") }
        }
        
        fun extensionFunction(evidence: ReadableEvidence): Boolean {
            return evidence.symbolType == "function" &&
                evidence.reasoningSteps.any { "extension_receiver" in it }
        }
    }
    
    /**
     * LLM-friendly semantic map - structured for direct LLM comprehension
     */
    data class SemanticMap(
        val codeStructure: List<ReadableEvidence>,
        val patternSummary: Map<String, Int>,
        val confidenceSummary: Map<String, List<String>>,
        val architecturalInsights: List<String>
    ) {
        
        /**
         * Generate natural language description for LLM
         */
        fun toLLMDescription(): String {
            val patterns = patternSummary.entries.joinToString(", ") { "${it.value} ${it.key}" }
            val highConfidence = confidenceSummary["high"]?.size ?: 0
            val insights = architecturalInsights.joinToString("; ")
            
            return """
                Code Analysis Summary:
                - Found: $patterns
                - High confidence items: $highConfidence
                - Architectural insights: $insights
                - Total symbols analyzed: ${codeStructure.size}
            """.trimIndent()
        }
        
        /**
         * Find symbols matching a readable pattern
         */
        fun findSymbolsMatching(description: String): List<ReadableEvidence> {
            return codeStructure.filter { evidence ->
                description.lowercase() in evidence.architecturalPattern.lowercase() ||
                evidence.reasoningSteps.any { description.lowercase() in it.lowercase() }
            }
        }
        
        /**
         * Get reasoning chain in natural language
         */
        fun getReasoningFor(symbolName: String): String? {
            val evidence = codeStructure.find { it.symbolName == symbolName }
            return evidence?.reasoningSteps?.joinToString(" → ")
        }
    }
    
    /**
     * Evidence collector that builds LLM-readable structures
     */
    class LLMFriendlyEvidenceCollector {
        private val evidence = mutableListOf<ReadableEvidence>()
        
        fun analyzeKotlinCode(sourceCode: String): SemanticMap {
            val lines = sourceCode.lines().filter { it.trim().isNotEmpty() }
            
            lines.forEach { line ->
                val tokens = line.trim().split(Regex("\\s+"))
                analyzeTokenSequence(tokens, line)
            }
            
            return buildSemanticMap()
        }
        
        private fun analyzeTokenSequence(tokens: List<String>, originalLine: String) {
            // Find keyword anchors
            val keywordIndex = tokens.indexOfFirst { 
                it in listOf("class", "fun", "val", "var", "interface", "object") 
            }
            
            if (keywordIndex == -1) return
            
            val keyword = tokens[keywordIndex]
            val beforeKeyword = tokens.subList(0, keywordIndex)
            val afterKeyword = tokens.subList(keywordIndex + 1, tokens.size)
            
            val symbolName = afterKeyword.firstOrNull { it.matches(Regex("[a-zA-Z][a-zA-Z0-9]*")) } ?: "unknown"
            
            val reasoning = buildReasoningSteps(keyword, beforeKeyword, afterKeyword, originalLine)
            val pattern = deduceArchitecturalPattern(keyword, beforeKeyword, afterKeyword, reasoning)
            val confidence = calculateReadableConfidence(beforeKeyword, afterKeyword, reasoning)
            
            evidence.add(ReadableEvidence(
                symbolName = symbolName,
                symbolType = keyword,
                beforeKeyword = beforeKeyword,
                afterKeyword = afterKeyword,
                confidenceLevel = confidence,
                reasoningSteps = reasoning,
                architecturalPattern = pattern
            ))
        }
        
        private fun buildReasoningSteps(
            keyword: String, 
            before: List<String>, 
            after: List<String>,
            line: String
        ): List<String> {
            val steps = mutableListOf<String>()
            
            steps.add("found_keyword: $keyword")
            
            if (before.isNotEmpty()) {
                steps.add("before_keyword: ${before.joinToString(" ")}")
            }
            
            if (after.isNotEmpty()) {
                steps.add("after_keyword: ${after.take(3).joinToString(" ")}")
            }
            
            // Pattern-specific reasoning
            when (keyword) {
                "class" -> {
                    if ("data" in before) steps.add("detected_data_class_modifier")
                    if (":" in after) steps.add("detected_inheritance_pattern")
                    if (before.any { it.startsWith("@") }) steps.add("detected_annotation")
                }
                "fun" -> {
                    if ("suspend" in before) steps.add("detected_coroutine_function")
                    if ("inline" in before) steps.add("detected_inline_optimization")
                    if ("(" in after) steps.add("detected_function_parameters")
                }
                "val", "var" -> {
                    if ("private" in before) steps.add("detected_encapsulation")
                    if (":" in after) steps.add("detected_explicit_typing")
                    if ("=" in after) steps.add("detected_initialization")
                }
            }
            
            return steps
        }
        
        private fun deduceArchitecturalPattern(
            keyword: String,
            before: List<String>,
            after: List<String>, 
            reasoning: List<String>
        ): String {
            return when {
                keyword == "class" && "data" in before -> "value_object_pattern"
                keyword == "class" && ":" in after -> "inheritance_hierarchy"
                keyword == "fun" && "suspend" in before -> "async_operation"
                keyword == "fun" && "inline" in before -> "performance_optimization"
                keyword in listOf("val", "var") && "private" in before -> "encapsulated_state"
                before.any { it.startsWith("@") } -> "annotation_driven_design"
                else -> "standard_declaration"
            }
        }
        
        private fun calculateReadableConfidence(
            before: List<String>,
            after: List<String>,
            reasoning: List<String>
        ): String {
            val evidenceCount = before.size + after.size + reasoning.size
            return when {
                evidenceCount >= 6 -> "high"
                evidenceCount >= 3 -> "medium"
                else -> "low"
            }
        }
        
        private fun buildSemanticMap(): SemanticMap {
            val patternCounts = evidence.groupingBy { it.architecturalPattern }.eachCount()
            val confidenceGroups = evidence.groupBy { it.confidenceLevel }
                .mapValues { (_, items) -> items.map { it.symbolName } }
            
            val insights = generateArchitecturalInsights()
            
            return SemanticMap(
                codeStructure = evidence.toList(),
                patternSummary = patternCounts,
                confidenceSummary = confidenceGroups,
                architecturalInsights = insights
            )
        }
        
        private fun generateArchitecturalInsights(): List<String> {
            val insights = mutableListOf<String>()
            
            val patterns = evidence.groupingBy { it.architecturalPattern }.eachCount()
            
            if (patterns["async_operation"] ?: 0 > 2) {
                insights.add("Heavy use of coroutines suggests reactive architecture")
            }
            
            if (patterns["value_object_pattern"] ?: 0 > 1) {
                insights.add("Data classes indicate domain modeling approach")
            }
            
            if (patterns["encapsulated_state"] ?: 0 > 0) {
                insights.add("Private properties show good encapsulation practices")
            }
            
            val highConfidenceCount = evidence.count { it.confidenceLevel == "high" }
            if (highConfidenceCount > evidence.size * 0.7) {
                insights.add("Code structure is clear and well-defined")
            }
            
            return insights
        }
    }
    
    @Test
    fun testLLMFriendlyEvidenceCollection() {
        val kotlinCode = """
            @Entity
            data class User(
                private val id: Long,
                val name: String
            )
        """.trimIndent()
        
        val collector = LLMFriendlyEvidenceCollector()
        val semanticMap = collector.analyzeKotlinCode(kotlinCode)
        
        // LLM can read this directly without bit math
        assertTrue(semanticMap.codeStructure.isNotEmpty())
        
        val classEvidence = semanticMap.codeStructure.find { it.symbolType == "class" }
        assertEquals("User", classEvidence?.symbolName)
        assertEquals("value_object_pattern", classEvidence?.architecturalPattern)
        assertTrue(classEvidence?.reasoningSteps?.contains("detected_data_class_modifier") == true)
        assertTrue(classEvidence?.reasoningSteps?.contains("detected_annotation") == true)
    }
    
    @Test
    fun testSuspendFunctionDetection() {
        val kotlinCode = "suspend fun processData(input: String): Result<String>"
        
        val collector = LLMFriendlyEvidenceCollector()
        val semanticMap = collector.analyzeKotlinCode(kotlinCode)
        
        val functionEvidence = semanticMap.codeStructure.find { it.symbolType == "fun" }
        assertEquals("processData", functionEvidence?.symbolName)
        assertEquals("async_operation", functionEvidence?.architecturalPattern)
        assertTrue(functionEvidence?.reasoningSteps?.contains("detected_coroutine_function") == true)
        
        // LLM can use simple pattern matching
        assertTrue(PatternSignatures.suspendFunction(functionEvidence!!))
    }
    
    @Test
    fun testNaturalLanguageDescription() {
        val kotlinCode = """
            suspend fun saveUser(user: User): Result<Unit>
            suspend fun loadUser(id: Long): User?
            private val cache: Map<Long, User> = emptyMap()
        """.trimIndent()
        
        val collector = LLMFriendlyEvidenceCollector()
        val semanticMap = collector.analyzeKotlinCode(kotlinCode)
        
        val description = semanticMap.toLLMDescription()
        
        // LLM gets human-readable summary
        assertTrue("async_operation" in description)
        assertTrue("encapsulated_state" in description)
        assertTrue("Heavy use of coroutines" in semanticMap.architecturalInsights.joinToString())
    }
    
    @Test
    fun testLLMQueryInterface() {
        val kotlinCode = """
            @Service
            class UserService {
                suspend fun findUser(id: Long): User?
                private val logger = LoggerFactory.getLogger()
            }
        """.trimIndent()
        
        val collector = LLMFriendlyEvidenceCollector()
        val semanticMap = collector.analyzeKotlinCode(kotlinCode)
        
        // LLM can query using natural language
        val asyncFunctions = semanticMap.findSymbolsMatching("async")
        assertTrue(asyncFunctions.isNotEmpty())
        
        val reasoning = semanticMap.getReasoningFor("findUser")
        assertTrue(reasoning?.contains("coroutine") == true)
        
        // No bit math required - just string matching and pattern recognition
    }
    
    @Test
    fun testArchitecturalInsightGeneration() {
        val kotlinCode = """
            data class User(val id: Long, val name: String)
            data class Order(val id: Long, val userId: Long)
            data class Product(val id: Long, val name: String)
            
            suspend fun processOrder(order: Order): Result<Unit>
            suspend fun notifyUser(userId: Long): Unit
            suspend fun updateInventory(productId: Long): Unit
        """.trimIndent()
        
        val collector = LLMFriendlyEvidenceCollector()
        val semanticMap = collector.analyzeKotlinCode(kotlinCode)
        
        // LLM gets architectural insights without computing
        val insights = semanticMap.architecturalInsights
        assertTrue(insights.any { "domain modeling" in it })
        assertTrue(insights.any { "reactive architecture" in it })
        
        // Pattern summary is LLM-readable
        assertEquals(3, semanticMap.patternSummary["value_object_pattern"])
        assertEquals(3, semanticMap.patternSummary["async_operation"])
    }
    
    @Test
    fun testConfidenceLevelsForLLM() {
        val kotlinCode = """
            // High confidence - lots of evidence
            @Repository
            suspend fun saveUser(user: User): Result<Unit>
            
            // Low confidence - minimal evidence  
            fun simple() = Unit
        """.trimIndent()
        
        val collector = LLMFriendlyEvidenceCollector()
        val semanticMap = collector.analyzeKotlinCode(kotlinCode)
        
        // LLM can understand confidence without math
        val highConfidence = semanticMap.confidenceSummary["high"] ?: emptyList()
        val lowConfidence = semanticMap.confidenceSummary["low"] ?: emptyList()
        
        assertTrue(highConfidence.contains("saveUser"))
        assertTrue(lowConfidence.contains("simple"))
        
        // LLM reads: "high confidence items: 1" instead of doing calculations
    }
}