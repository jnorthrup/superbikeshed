@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.compiler

import borg.trikeshed.lib.*
import borg.trikeshed.sumo.kif.*

/**
 * Join-Based SUMO Knowledge Graph
 * 
 * Implements the knowledge base using proper Join composition patterns:
 * - Concept = Join<ConceptId, ConceptName>
 * - Relationship = Join<Concept, Concept>
 * - Graph = Join<Indexed<Concept>, Indexed<Relationship>>
 * - Query = Join<Concept, (Concept) -> Boolean>
 */
class SumoJoinGraph {
    
    // Core Join-based structures
    private val concepts: MutableList<Join<ConceptId, ConceptName>> = mutableListOf()
    private val relationships: MutableList<Join<Concept, Concept>> = mutableListOf()
    
    // String-averse: Use integer IDs and interned names
    private val conceptIdMap = mutableMapOf<String, ConceptId>()
    private val conceptNameMap = mutableMapOf<ConceptId, String>()
    
    /**
     * Add a concept using Join composition
     */
    fun addConcept(name: String): Concept {
        val id = conceptIdMap.getOrPut(name) { ConceptId(conceptIdMap.size) }
        val conceptName = ConceptName(name)
        val concept = id j conceptName
        
        if (!concepts.contains(concept)) {
            concepts.add(concept)
            conceptNameMap[id] = name
        }
        
        return concept
    }
    
    /**
     * Add a relationship using Join composition
     */
    fun addRelationship(sub: String, sup: String): Relationship {
        val subConcept = addConcept(sub)
        val supConcept = addConcept(sup)
        val relationship = subConcept j supConcept
        
        if (!relationships.contains(relationship)) {
            relationships.add(relationship)
        }
        
        return relationship
    }
    
    /**
     * Build the complete graph using Join composition
     */
    fun buildGraph(): JoinGraph {
        val conceptIndex = \1 j { \2: Int -> concepts[i] }
        val relationshipIndex = \1 j { \2: Int -> relationships[i] }
        
        return conceptIndex j relationshipIndex
    }
    
    /**
     * Query using Join-based patterns
     */
    fun isSubclassOf(sub: String, sup: String): Boolean {
        val graph = buildGraph()
        val conceptIndex = graph.component1()
        val relationshipIndex = graph.component2()
        
        val subConcept = findConceptByName(sub, conceptIndex)
        val supConcept = findConceptByName(sup, conceptIndex)
        
        if (subConcept == null || supConcept == null) return false
        
        // Direct relationship check
        val directRelationship = subConcept j supConcept
        if (relationshipIndex.play.contains(directRelationship)) return true
        
        // Transitive relationship check using Join composition
        return checkTransitiveRelationship(subConcept, supConcept, relationshipIndex)
    }
    
    /**
     * Transitive closure using Join composition
     */
    private fun checkTransitiveRelationship(
        sub: Concept, 
        sup: Concept, 
        relationships: Indexed<Relationship>
    ): Boolean {
        val directSupers = relationships.play.filter { rel ->
            rel.component1() == sub
        }.map { it.component2() }
        
        if (directSupers.contains(sup)) return true
        
        return directSupers.any { directSuper ->
            checkTransitiveRelationship(directSuper, sup, relationships)
        }
    }
    
    /**
     * Find concept by name using Join patterns
     */
    private fun findConceptByName(name: String, conceptIndex: Indexed<Concept>): Concept? {
        return conceptIndex.play.find { concept ->
            concept.component2().value == name
        }
    }
    
    /**
     * Generate compile-time optimized queries using Join composition
     */
    fun generateJoinQueries(): JoinQueryCode {
        val graph = buildGraph()
        val conceptIndex = graph.component1()
        val relationshipIndex = graph.component2()
        
        val subclassQueries = generateSubclassQueries(conceptIndex, relationshipIndex)
        val conceptQueries = generateConceptQueries(conceptIndex)
        
        return JoinQueryCode(subclassQueries, conceptQueries)
    }
    
    private fun generateSubclassQueries(
        concepts: Indexed<Concept>, 
        relationships: Indexed<Relationship>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("// Generated Join-Based Subclass Queries")
        sb.appendLine("object JoinSubclassQueries {")
        
        // Generate concept lookup using Join patterns
        sb.appendLine("  private val concepts = listOf(")
        concepts.play.forEach { concept ->
            val id = concept.component1().value
            val name = concept.component2().value
            sb.appendLine("    $id j \"$name\"")
        }
        sb.appendLine("  )")
        
        // Generate relationship lookup
        sb.appendLine("  private val relationships = setOf(")
        relationships.play.forEach { rel ->
            val subId = rel.component1().component1().value
            val supId = rel.component2().component1().value
            sb.appendLine("    $subId j $supId")
        }
        sb.appendLine("  )")
        
        // Generate query function using Join composition
        sb.appendLine("""
          @JvmStatic
          fun isSubclassOf(sub: String, sup: String): Boolean {
              val subConcept = concepts.find { it.component2() == sub } ?: return false
              val supConcept = concepts.find { it.component2() == sup } ?: return false
              val relationship = subConcept.component1() j supConcept.component1()
              return relationships.contains(relationship)
          }
        """.trimIndent())
        
        sb.appendLine("}")
        return sb.toString()
    }
    
    private fun generateConceptQueries(concepts: Indexed<Concept>): String {
        val sb = StringBuilder()
        sb.appendLine("// Generated Join-Based Concept Queries")
        sb.appendLine("object JoinConceptQueries {")
        
        sb.appendLine("  private val conceptIndex = \1 j { \2: Int -> concepts[i] }")
        sb.appendLine("  private val concepts = listOf(")
        concepts.play.forEach { concept ->
            val name = concept.component2().value
            sb.appendLine("    \"$name\"")
        }
        sb.appendLine("  )")
        
        sb.appendLine("""
          @JvmStatic
          fun getAllConcepts(): List<String> = concepts
          
          @JvmStatic
          fun getConceptCount(): Int = conceptIndex.component1()
          
          @JvmStatic
          fun getConceptById(id: Int): String? = 
              if (id < conceptIndex.component1()) conceptIndex.component2()(id).component2().value else null
        """.trimIndent())
        
        sb.appendLine("}")
        return sb.toString()
    }
}

// Join-based type aliases for SUMO concepts
@JvmInline value class ConceptId(val value: Int)
@JvmInline value class ConceptName(val value: String)

typealias Concept = Join<ConceptId, ConceptName>
typealias Relationship = Join<Concept, Concept>
typealias JoinGraph = Join<Indexed<Concept>, Indexed<Relationship>>
typealias JoinQuery = Join<Concept, (Concept) -> Boolean>

data class JoinQueryCode(
    val subclassQueries: String,
    val conceptQueries: String
)

/**
 * Join-based triple dispatch for expression processing
 */
interface JoinExpressionDispatcher<R> {
    fun dispatch(expr1: KifExpression, expr2: KifExpression, graph: JoinGraph): R
}

/**
 * Example triple dispatch implementation using Join composition
 */
object JoinExpressionProcessor : JoinExpressionDispatcher<Relationship?> {
    override fun dispatch(expr1: KifExpression, expr2: KifExpression, graph: JoinGraph): Relationship? {
        return when {
            expr1 is KifExpression.Cons && expr2 is KifExpression.Cons -> {
                // Process nested expressions using Join composition
                val car1 = dispatch(expr1.car, expr2.car, graph)
                val cdr1 = dispatch(expr1.cdr, expr2.cdr, graph)
                if (car1 != null && cdr1 != null) {
                    car1.component1() j cdr1.component2()
                } else null
            }
            expr1 is KifExpression.Atom && expr2 is KifExpression.Atom -> {
                // Create relationship from atom expressions
                val concept1 = ConceptId(expr1.value.hashCode()) j ConceptName(expr1.value)
                val concept2 = ConceptId(expr2.value.hashCode()) j ConceptName(expr2.value)
                concept1 j concept2
            }
            else -> null
        }
    }
} 