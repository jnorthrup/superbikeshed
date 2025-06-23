package borg.trikeshed.analysis

import borg.trikeshed.lib.*
import kotlinx.serialization.*

/**
 * STATIC ANALYSIS ENGINE - Unified & Lightweight
 *
 * This engine unifies the concepts from SpaceGraph and a simplified, single-pass version
 * of the EntropyRuleEngine. It's designed to be light, imprecise, and fast, generating
 * a confidence-based graph of code structure without complex, iterative "language lawyer" parsing.
 *
 * Features:
 * - Single-pass, evidence-based analysis.
 * - Produces a graph of entities and relationships with confidence scores.
 * - Uses a simple, extensible rule system.
 * - Merges graph structure and analysis logic into one streamlined component.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// UNIFIED GRAPH & ANALYSIS DATA STRUCTURES
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class AnalyzedEntity(val name: String, val type: String, val confidence: Float)

@Serializable
data class Relationship(val type: String, val confidence: Float)

typealias AnalysisNode = Join<Int, AnalyzedEntity>
val AnalysisNode.id: Int get() = a
val AnalysisNode.entity: AnalyzedEntity get() = b

typealias AnalysisEdge = Join<Twin<Int>, Relationship>
val AnalysisEdge.sourceId: Int get() = a.a
val AnalysisEdge.targetId: Int get() = a.b
val AnalysisEdge.relationship: Relationship get() = b

typealias CodeGraph = Join<Indexed<AnalysisNode>, Indexed<AnalysisEdge>>
val CodeGraph.nodes: Indexed<AnalysisNode> get() = a
val CodeGraph.edges: Indexed<AnalysisEdge> get() = b

/**
 * A fragment of a CodeGraph, representing the findings of a single rule.
 */
typealias GraphFragment = Join<List<AnalysisNode>, List<AnalysisEdge>>

/**
 * A function that takes source code text and returns evidence as a GraphFragment.
 */
typealias AnalysisRule = (text: String) -> GraphFragment

// ═══════════════════════════════════════════════════════════════════════════════
// LIGHTWEIGHT ANALYSIS RULES
// ═══════════════════════════════════════════════════════════════════════════════

object StaticAnalysisRules {

    // Regex for finding simple `class` or `object` definitions.
    private val classRegex = """(?:class|object)\s+(\w+)""".toRegex()
    
    // Regex for finding simple `fun` definitions.
    private val funRegex = """fun\s+(\w+)""".toRegex()
    
    /**
     * A rule to find class and object definitions.
     */
    val findEntitiesRule: AnalysisRule = { text ->
        val nodes = classRegex.findAll(text).mapIndexed { id, match ->
            val name = match.groupValues[1]
            (id j AnalyzedEntity(name, "ClassOrObject", 0.8f))
        }.toList()
        nodes j emptyList() // Return nodes and no edges
    }

    /**
     * A rule to find function definitions.
     */
    val findFunctionsRule: AnalysisRule = { text ->
        val nodes = funRegex.findAll(text).mapIndexed { id, match ->
            // Offset the ID to avoid collision with entities
            val name = match.groupValues[1]
            (id + 1000 j AnalyzedEntity(name, "Function", 0.75f))
        }.toList()
        nodes j emptyList() // Return nodes and no edges
    }
    
    // Add other simple, single-pass rules here (e.g., for inheritance, function calls)
    
    /**
     * A collection of all active analysis rules.
     */
    val allRules: List<AnalysisRule> = listOf(findEntitiesRule, findFunctionsRule)
}


// ═══════════════════════════════════════════════════════════════════════════════
// STATIC ANALYSIS ENGINE
// ═══════════════════════════════════════════════════════════════════════════════

object StaticAnalysisEngine {

    /**
     * Analyzes source code using a single pass of lightweight rules.
     *
     * @param sourceCode The source code to analyze.
     * @param rules The list of analysis rules to apply.
     * @return A confidence-based CodeGraph of the entities and relationships found.
     */
    fun analyze(
        sourceCode: String,
        rules: List<AnalysisRule> = StaticAnalysisRules.allRules
    ): CodeGraph {
        // Apply all rules in a single pass and collect the fragments
        val allFragments = rules.map { it(sourceCode) }

        // Combine all fragments into a single graph
        val allNodes = allFragments.flatMap { it.a }
        val allEdges = allFragments.flatMap { it.b }

        return allNodes.toIndexed() j allEdges.toIndexed()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

private fun <T> List<T>.toIndexed(): Indexed<T> = size j { this[it] } 