package borg.trikeshed.reflection

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * ENTROPY RULE ENGINE - High-Entropy, Chained Rule System
 * 
 * This integrates the beneficial patterns from kotlin-entity-scanner:
 * - High-entropy, chained rule system with maximum entropy decision making
 * - Shannon entropy calculation for rule prioritization
 * - Bidirectional chaining with forward prediction and backward validation
 * - Iterative refinement with convergence detection
 */

// ═══════════════════════════════════════════════════════════════════════════════
// ENTROPY RULE ENGINE CORE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Rule entropy value representing information content
 */
value class RuleEntropy(val entropy: Double) {
    companion object {
        val HIGH = RuleEntropy(3.8)
        val MEDIUM = RuleEntropy(2.5)
        val LOW = RuleEntropy(1.2)
    }
}

/**
 * Activation threshold for rule firing
 */
value class ActivationThreshold(val threshold: Double) {
    companion object {
        val STRICT = ActivationThreshold(0.95)
        val MODERATE = ActivationThreshold(0.85)
        val LENIENT = ActivationThreshold(0.75)
    }
}

/**
 * Rule priority for ordering
 */
value class RulePriority(val priority: UByte) {
    companion object {
        val CRITICAL = RulePriority(255u)
        val HIGH = RulePriority(200u)
        val MEDIUM = RulePriority(150u)
        val LOW = RulePriority(100u)
    }
}

/**
 * Rule confidence level
 */
value class RuleConfidence(val confidence: UByte) {
    companion object {
        val CERTAIN = RuleConfidence(255u)
        val HIGH = RuleConfidence(200u)
        val MEDIUM = RuleConfidence(150u)
        val LOW = RuleConfidence(100u)
    }
}

/**
 * Parsing rule function type
 */
typealias ParsingRule = suspend (ParseContext, Int) -> Boolean

/**
 * Parse context containing current state
 */
data class ParseContext(
    val source: String,
    val position: Int,
    val tokens: Indexed<String>,
    val metadata: Map<String, Any?> = emptyMap()
) {
    fun advance(by: Int = 1): ParseContext = copy(position = position + by)
    fun withMetadata(key: String, value: Any?): ParseContext = 
        copy(metadata = metadata + (key to value))
}

/**
 * Entropy rule combining parsing rule with entropy information
 */
typealias EntropyRule = Join<ParsingRule, Join<RuleEntropy, ActivationThreshold>>

/**
 * Prioritized rule with priority ordering
 */
typealias PrioritizedRule = Join<EntropyRule, RulePriority>

/**
 * Rule cluster as a series of prioritized rules
 */
typealias RuleCluster = Indexed<PrioritizedRule>

/**
 * Graph node for analysis results
 */
data class GraphNode(
    val nodeId: String,
    val depType: String,
    val confidence: RuleConfidence
)

/**
 * Analysis result containing graph nodes and refinement information
 */
typealias AnalysisResult = Join<Indexed<GraphNode>, Indexed<String>>

// ═══════════════════════════════════════════════════════════════════════════════
// RULE CREATION UTILITIES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Create a high-entropy rule with specified parameters
 */
fun createHighEntropyRule(
    name: String,
    entropy: Double,
    threshold: Double,
    priority: UByte,
    rule: ParsingRule
): PrioritizedRule {
    val entropyRule = rule j (RuleEntropy(entropy) j ActivationThreshold(threshold))
    return entropyRule j RulePriority(priority)
}

/**
 * Calculate Shannon entropy for a rule based on its information content
 */
fun calculateRuleEntropy(
    ruleName: String,
    successRate: Double,
    informationGain: Double
): RuleEntropy {
    val baseEntropy = -successRate * kotlin.math.ln(successRate)
    val informationEntropy = informationGain * kotlin.math.ln(2.0)
    return RuleEntropy(baseEntropy + informationEntropy)
}

// ═══════════════════════════════════════════════════════════════════════════════
// FORWARD CHAINING RULES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Aggressive forward chaining rules for pattern detection
 */
object AggressiveForwardChains {
    
    fun kotlinKeywordChain(): RuleCluster = listOf(
        createHighEntropyRule("class_keyword", 3.8, 0.95, 255u) { context, pos ->
            detectKeywordWithContext(context, pos, "class", KeywordContext.TOP_LEVEL)
        },
        createHighEntropyRule("fun_keyword", 3.7, 0.94, 254u) { context, pos ->
            detectKeywordWithContext(context, pos, "fun", KeywordContext.ANY)
        },
        createHighEntropyRule("data_modifier", 3.0, 0.85, 238u) { context, pos ->
            detectModifierChain(context, pos, "data", ModifierContext.CLASS_ONLY)
        },
        createHighEntropyRule("suspend_modifier", 2.8, 0.83, 235u) { context, pos ->
            detectModifierChain(context, pos, "suspend", ModifierContext.FUNCTION_ONLY)
        }
    ).toIndexed()
    
    fun seriesPatternChain(): RuleCluster = listOf(
        createHighEntropyRule("series_construction", 3.5, 0.92, 250u) { context, pos ->
            detectSeriesConstruction(context, pos)
        },
        createHighEntropyRule("join_operator", 3.3, 0.90, 245u) { context, pos ->
            detectJoinOperator(context, pos)
        },
        createHighEntropyRule("alpha_transform", 3.1, 0.88, 240u) { context, pos ->
            detectAlphaTransform(context, pos)
        }
    ).toIndexed()
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKWARD CHAINING RULES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Aggressive backward chaining rules for validation
 */
object AggressiveBackwardChains {
    
    fun typeValidationChain(): RuleCluster = listOf(
        createHighEntropyRule("class_hierarchy_back", 3.7, 0.94, 255u) { context, pos ->
            validateClassHierarchy(context, pos) && resolveInheritanceChain(context, pos)
        },
        createHighEntropyRule("function_signature_back", 3.6, 0.93, 254u) { context, pos ->
            validateFunctionSignature(context, pos) && resolveParameterTypes(context, pos)
        },
        createHighEntropyRule("series_type_back", 3.4, 0.91, 248u) { context, pos ->
            validateSeriesType(context, pos) && resolveSeriesBounds(context, pos)
        }
    ).toIndexed()
    
    fun metaclassValidationChain(): RuleCluster = listOf(
        createHighEntropyRule("join_composition_back", 3.5, 0.92, 252u) { context, pos ->
            validateJoinComposition(context, pos) && resolveJoinTypes(context, pos)
        },
        createHighEntropyRule("tensor_shape_back", 3.2, 0.89, 242u) { context, pos ->
            validateTensorShape(context, pos) && resolveTensorDimensions(context, pos)
        }
    ).toIndexed()
}

// ═══════════════════════════════════════════════════════════════════════════════
// ULTRA AGGRESSIVE RULE ENGINE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Ultra aggressive rule engine with maximum entropy chaining
 */
object UltraAggressiveRuleEngine {
    
    /**
     * Execute maximum entropy chaining with iterative refinement
     */
    suspend fun executeMaxEntropyChaining(
        source: String,
        maxIterations: Int = 10,
        convergenceThreshold: Double = 0.001
    ): AnalysisResult {
        var context = createEnhancedParseContext(source)
        var previousSystemEntropy = 0.0
        var iteration = 0
        
        val forwardRules = AggressiveForwardChains.kotlinKeywordChain() + 
                          AggressiveForwardChains.seriesPatternChain()
        val backwardRules = AggressiveBackwardChains.typeValidationChain() + 
                           AggressiveBackwardChains.metaclassValidationChain()
        
        while (iteration < maxIterations) {
            context = executeAggressiveForwardChain(context, forwardRules)
            context = executeAggressiveBackwardChain(context, backwardRules)
            
            val currentSystemEntropy = calculateSystemEntropy(context)
            if (abs(currentSystemEntropy - previousSystemEntropy) < convergenceThreshold) {
                break
            }
            previousSystemEntropy = currentSystemEntropy
            iteration++
        }
        
        return finalizeParsingResults(context)
    }
    
    /**
     * Execute aggressive forward chain
     */
    private suspend fun executeAggressiveForwardChain(
        context: ParseContext,
        rules: RuleCluster
    ): ParseContext {
        var currentContext = context
        
        // Sort rules by priority (highest first)
        val sortedRules = rules.play.sortedByDescending { it.b.priority.toInt() }
        
        for (rule in sortedRules) {
            val (entropyRule, priority) = rule
            val (parsingRule, entropyInfo) = entropyRule
            val (entropy, threshold) = entropyInfo
            
            // Check if rule should fire based on entropy and threshold
            if (entropy.entropy >= threshold.threshold) {
                val success = parsingRule(currentContext, currentContext.position)
                if (success) {
                    currentContext = currentContext.withMetadata(
                        "rule_fired", "${entropy.entropy}_${priority.priority}"
                    )
                }
            }
        }
        
        return currentContext
    }
    
    /**
     * Execute aggressive backward chain
     */
    private suspend fun executeAggressiveBackwardChain(
        context: ParseContext,
        rules: RuleCluster
    ): ParseContext {
        var currentContext = context
        
        // Sort rules by priority (highest first)
        val sortedRules = rules.play.sortedByDescending { it.b.priority.toInt() }
        
        for (rule in sortedRules) {
            val (entropyRule, priority) = rule
            val (parsingRule, entropyInfo) = entropyRule
            val (entropy, threshold) = entropyInfo
            
            // Backward validation with higher threshold
            val backwardThreshold = threshold.threshold * 1.1
            if (entropy.entropy >= backwardThreshold) {
                val success = parsingRule(currentContext, currentContext.position)
                if (success) {
                    currentContext = currentContext.withMetadata(
                        "backward_validated", "${entropy.entropy}_${priority.priority}"
                    )
                }
            }
        }
        
        return currentContext
    }
    
    /**
     * Calculate system entropy for convergence detection
     */
    private fun calculateSystemEntropy(context: ParseContext): Double {
        val ruleFirings = context.metadata.filterKeys { it.startsWith("rule_fired") }.size
        val validations = context.metadata.filterKeys { it.startsWith("backward_validated") }.size
        val totalRules = ruleFirings + validations
        
        if (totalRules == 0) return 0.0
        
        val firingRate = ruleFirings.toDouble() / totalRules
        val validationRate = validations.toDouble() / totalRules
        
        return -(firingRate * kotlin.math.ln(firingRate) + validationRate * kotlin.math.ln(validationRate))
    }
    
    /**
     * Finalize parsing results into analysis result
     */
    private fun finalizeParsingResults(context: ParseContext): AnalysisResult {
        val graphNodes = extractGraphNodes(context)
        val refinements = extractRefinements(context)
        
        return graphNodes j refinements
    }
    
    /**
     * Extract graph nodes from parse context
     */
    private fun extractGraphNodes(context: ParseContext): Indexed<GraphNode> {
        val nodes = mutableListOf<GraphNode>()
        
        // Extract nodes from metadata
        context.metadata.forEach { (key, value) ->
            if (key.startsWith("rule_fired") || key.startsWith("backward_validated")) {
                val parts = value.toString().split("_")
                if (parts.size >= 2) {
                    val entropy = parts[0].toDoubleOrNull() ?: 0.0
                    val priority = parts[1].toUByteOrNull() ?: 0u
                    
                    nodes.add(GraphNode(
                        nodeId = key,
                        depType = if (key.startsWith("rule_fired")) "forward" else "backward",
                        confidence = RuleConfidence((entropy * 255 / 4.0).toUByte())
                    ))
                }
            }
        }
        
        return nodes.size j { i -> nodes[i] }
    }
    
    /**
     * Extract refinements from parse context
     */
    private fun extractRefinements(context: ParseContext): Indexed<String> {
        val refinements = mutableListOf<String>()
        
        // Add refinements based on metadata
        if (context.metadata.isNotEmpty()) {
            refinements.add("Enhanced parsing with ${context.metadata.size} rule applications")
        }
        
        return refinements.size j { i -> refinements[i] }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS AND CONTEXTS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Keyword context for detection
 */
enum class KeywordContext {
    TOP_LEVEL, FUNCTION, CLASS, ANY
}

/**
 * Modifier context for detection
 */
enum class ModifierContext {
    CLASS_ONLY, FUNCTION_ONLY, ANY
}

/**
 * Create enhanced parse context
 */
fun createEnhancedParseContext(source: String): ParseContext {
    val tokens = source.split("\\s+".toRegex()).toIndexed()
    return ParseContext(source, 0, tokens)
}

/**
 * Detect keyword with context
 */
suspend fun detectKeywordWithContext(
    context: ParseContext,
    pos: Int,
    keyword: String,
    allowedContext: KeywordContext
): Boolean {
    return if (pos < context.tokens.size) {
        context.tokens[pos] == keyword
    } else {
        false
    }
}

/**
 * Detect modifier chain
 */
suspend fun detectModifierChain(
    context: ParseContext,
    pos: Int,
    modifier: String,
    allowedContext: ModifierContext
): Boolean {
    return if (pos < context.tokens.size) {
        context.tokens[pos] == modifier
    } else {
        false
    }
}

/**
 * Detect Series construction patterns
 */
suspend fun detectSeriesConstruction(context: ParseContext, pos: Int): Boolean {
    return if (pos + 2 < context.tokens.size) {
        context.tokens[pos] == "j" && context.tokens[pos + 1] == "{"
    } else {
        false
    }
}

/**
 * Detect Join operator usage
 */
suspend fun detectJoinOperator(context: ParseContext, pos: Int): Boolean {
    return if (pos + 1 < context.tokens.size) {
        context.tokens[pos] == "j"
    } else {
        false
    }
}

/**
 * Detect alpha transform usage
 */
suspend fun detectAlphaTransform(context: ParseContext, pos: Int): Boolean {
    return if (pos + 1 < context.tokens.size) {
        context.tokens[pos] == "α"
    } else {
        false
    }
}

/**
 * Validate class hierarchy
 */
suspend fun validateClassHierarchy(context: ParseContext, pos: Int): Boolean {
    // Simplified validation - would need full parser implementation
    return true
}

/**
 * Resolve inheritance chain
 */
suspend fun resolveInheritanceChain(context: ParseContext, pos: Int): Boolean {
    // Simplified resolution - would need full parser implementation
    return true
}

/**
 * Validate function signature
 */
suspend fun validateFunctionSignature(context: ParseContext, pos: Int): Boolean {
    // Simplified validation - would need full parser implementation
    return true
}

/**
 * Resolve parameter types
 */
suspend fun resolveParameterTypes(context: ParseContext, pos: Int): Boolean {
    // Simplified resolution - would need full parser implementation
    return true
}

/**
 * Validate Series type
 */
suspend fun validateSeriesType(context: ParseContext, pos: Int): Boolean {
    // Simplified validation - would need full parser implementation
    return true
}

/**
 * Resolve Series bounds
 */
suspend fun resolveSeriesBounds(context: ParseContext, pos: Int): Boolean {
    // Simplified resolution - would need full parser implementation
    return true
}

/**
 * Validate Join composition
 */
suspend fun validateJoinComposition(context: ParseContext, pos: Int): Boolean {
    // Simplified validation - would need full parser implementation
    return true
}

/**
 * Resolve Join types
 */
suspend fun resolveJoinTypes(context: ParseContext, pos: Int): Boolean {
    // Simplified resolution - would need full parser implementation
    return true
}

/**
 * Validate Tensor shape
 */
suspend fun validateTensorShape(context: ParseContext, pos: Int): Boolean {
    // Simplified validation - would need full parser implementation
    return true
}

/**
 * Resolve Tensor dimensions
 */
suspend fun resolveTensorDimensions(context: ParseContext, pos: Int): Boolean {
    // Simplified resolution - would need full parser implementation
    return true
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Convert List to Indexed
 */
fun <T> List<T>.toIndexed(): Indexed<T> = this.size j { i -> this[i] }

/**
 * Concatenate two Indexed collections
 */
operator fun <T> Indexed<T>.plus(other: Indexed<T>): Indexed<T> {
    val totalSize = this.size + other.size
    return totalSize j { i ->
        if (i < this.size) this[i] else other[i - this.size]
    }
} 