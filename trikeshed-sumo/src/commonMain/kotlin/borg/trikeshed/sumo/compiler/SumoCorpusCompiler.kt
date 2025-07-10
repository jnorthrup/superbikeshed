@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.compiler

import borg.trikeshed.lib.*
import borg.trikeshed.sumo.kif.*
import kotlinx.coroutines.flow.*

/**
 * SUMO Corpus Compiler - Bootstrap Compile-Time Knowledge Base
 * 
 * This demonstrates the complete pipeline from raw KIF corpus to optimized
 * compile-time knowledge base using Join composition and double/triple dispatch patterns.
 * 
 * The compiler operates in phases:
 * 1. Corpus Ingestion: Parse KIF files into typed expressions
 * 2. Knowledge Extraction: Extract ontological relationships using Join composition
 * 3. Transitive Closure: Compute full hierarchy using Join-based graph
 * 4. Code Generation: Generate optimized query functions with Join patterns
 */
class SumoCorpusCompiler {
    
    // Join-based knowledge graph
    private val joinGraph = SumoJoinGraph()
    
    /**
     * Phase 1: Corpus Ingestion
     * Parses KIF files and extracts typed expressions
     */
    suspend fun ingestCorpus(kifFiles: List<String>): CorpusStats {
        println("=== Phase 1: Corpus Ingestion ===")
        val startTime = System.currentTimeMillis()
        
        var totalExpressions = 0
        var subclassExpressions = 0
        var instanceExpressions = 0
        
        kifFiles.forEach { kifContent ->
            KifParser.parse(kifContent).collect { expr ->
                totalExpressions++
                
                when {
                    isSubclassExpression(expr) -> {
                        extractSubclassRelationship(expr)
                        subclassExpressions++
                    }
                    isInstanceExpression(expr) -> {
                        extractInstanceRelationship(expr)
                        instanceExpressions++
                    }
                }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        val graph = joinGraph.buildGraph()
        val uniqueConcepts = graph.a.size
        
        println("Ingestion complete: ${totalExpressions} expressions in ${duration}ms")
        println("  - Subclass expressions: ${subclassExpressions}")
        println("  - Instance expressions: ${instanceExpressions}")
        println("  - Unique concepts: ${uniqueConcepts}")
        
        return CorpusStats(
            totalExpressions = totalExpressions,
            subclassExpressions = subclassExpressions,
            instanceExpressions = instanceExpressions,
            uniqueConcepts = uniqueConcepts,
            duration = duration
        )
    }
    
    /**
     * Phase 2: Knowledge Extraction with Double Dispatch
     * Uses visitor pattern for type-safe expression processing
     */
    private fun extractSubclassRelationship(expr: KifExpression) {
        expr.accept(SubclassExtractor)
    }
    
    private fun extractInstanceRelationship(expr: KifExpression) {
        expr.accept(InstanceExtractor)
    }
    
    /**
     * Double Dispatch Visitor for Subclass Extraction
     */
    private object SubclassExtractor : KifExpressionVisitor<Unit> {
        override fun visitCons(cons: KifExpression.Cons): Unit {
            val list = cons.toList()
            if (list.size == 3 && 
                list[0] is KifExpression.Atom && 
                (list[0] as KifExpression.Atom).value == "subclass") {
                
                val sub = extractConceptName(list[1])
                val sup = extractConceptName(list[2])
                
                if (sub != null && sup != null) {
                    val subId = getOrCreateConceptId(sub)
                    val supId = getOrCreateConceptId(sup)
                    subclassMatrix.getOrPut(subId) { mutableSetOf() }.add(supId)
                }
            }
        }
        
        override fun visitAtom(atom: KifExpression.Atom): Unit = Unit
        override fun visitStr(str: KifExpression.Str): Unit = Unit
        override fun visitNil(nil: KifExpression.Nil): Unit = Unit
    }
    
    /**
     * Double Dispatch Visitor for Instance Extraction
     */
    private object InstanceExtractor : KifExpressionVisitor<Unit> {
        override fun visitCons(cons: KifExpression.Cons): Unit {
            val list = cons.toList()
            if (list.size == 3 && 
                list[0] is KifExpression.Atom && 
                (list[0] as KifExpression.Atom).value == "instance") {
                
                val instance = extractConceptName(list[1])
                val concept = extractConceptName(list[2])
                
                if (instance != null && concept != null) {
                    val instanceId = getOrCreateConceptId(instance)
                    val conceptId = getOrCreateConceptId(concept)
                    instanceMatrix.getOrPut(instanceId) { mutableSetOf() }.add(conceptId)
                }
            }
        }
        
        override fun visitAtom(atom: KifExpression.Atom): Unit = Unit
        override fun visitStr(str: KifExpression.Str): Unit = Unit
        override fun visitNil(nil: KifExpression.Nil): Unit = Unit
    }
    
    /**
     * Phase 3: Transitive Closure Computation
     * Computes full hierarchy using optimized integer operations
     */
    fun computeTransitiveClosure(): ClosureStats {
        println("=== Phase 3: Transitive Closure ===")
        val startTime = System.currentTimeMillis()
        
        var iterations = 0
        var totalRelationships = 0
        var changed = true
        
        while (changed) {
            changed = false
            iterations++
            
            for ((subId, directSupers) in subclassMatrix.toMap()) {
                val newSupers = directSupers.flatMap { superId ->
                    subclassMatrix[superId] ?: emptySet()
                }
                
                if (subclassMatrix.getOrPut(subId) { mutableSetOf() }.addAll(newSupers)) {
                    changed = true
                }
            }
        }
        
        totalRelationships = subclassMatrix.values.sumOf { it.size }
        val duration = System.currentTimeMillis() - startTime
        
        println("Transitive closure complete in ${iterations} iterations")
        println("  - Total relationships: ${totalRelationships}")
        println("  - Duration: ${duration}ms")
        
        return ClosureStats(
            iterations = iterations,
            totalRelationships = totalRelationships,
            duration = duration
        )
    }
    
    /**
     * Phase 4: Code Generation
     * Generates optimized compile-time query functions
     */
    fun generateCompileTimeQueries(): GeneratedCode {
        println("=== Phase 4: Code Generation ===")
        
        val subclassQueries = generateSubclassQueries()
        val instanceQueries = generateInstanceQueries()
        val conceptQueries = generateConceptQueries()
        
        return GeneratedCode(
            subclassQueries = subclassQueries,
            instanceQueries = instanceQueries,
            conceptQueries = conceptQueries
        )
    }
    
    /**
     * Generate optimized subclass query functions
     */
    private fun generateSubclassQueries(): String {
        val sb = StringBuilder()
        sb.appendLine("// Generated Subclass Queries")
        sb.appendLine("object SubclassQueries {")
        
        // Generate lookup table
        sb.appendLine("  private val subclassMatrix = mapOf(")
        subclassMatrix.forEach { (subId, supers) ->
            val subName = conceptNames[subId]
            val superNames = supers.map { conceptNames[it] }
            sb.appendLine("    \"$subName\" to setOf(${superNames.joinToString(", ") { "\"$it\"" }})")
        }
        sb.appendLine("  )")
        
        // Generate query function
        sb.appendLine("""
          @JvmStatic
          fun isSubclassOf(sub: String, sup: String): Boolean {
              return subclassMatrix[sub]?.contains(sup) ?: false
          }
        """.trimIndent())
        
        sb.appendLine("}")
        return sb.toString()
    }
    
    /**
     * Generate optimized instance query functions
     */
    private fun generateInstanceQueries(): String {
        val sb = StringBuilder()
        sb.appendLine("// Generated Instance Queries")
        sb.appendLine("object InstanceQueries {")
        
        // Generate lookup table
        sb.appendLine("  private val instanceMatrix = mapOf(")
        instanceMatrix.forEach { (instanceId, concepts) ->
            val instanceName = conceptNames[instanceId]
            val conceptNames = concepts.map { conceptNames[it] }
            sb.appendLine("    \"$instanceName\" to setOf(${conceptNames.joinToString(", ") { "\"$it\"" }})")
        }
        sb.appendLine("  )")
        
        // Generate query function
        sb.appendLine("""
          @JvmStatic
          fun isInstanceOf(instance: String, concept: String): Boolean {
              return instanceMatrix[instance]?.contains(concept) ?: false
          }
        """.trimIndent())
        
        sb.appendLine("}")
        return sb.toString()
    }
    
    /**
     * Generate concept utility functions
     */
    private fun generateConceptQueries(): String {
        val sb = StringBuilder()
        sb.appendLine("// Generated Concept Utilities")
        sb.appendLine("object ConceptQueries {")
        
        sb.appendLine("  private val conceptNames = listOf(")
        conceptNames.forEach { name ->
            sb.appendLine("    \"$name\"")
        }
        sb.appendLine("  )")
        
        sb.appendLine("""
          @JvmStatic
          fun getAllConcepts(): List<String> = conceptNames
          
          @JvmStatic
          fun getConceptCount(): Int = conceptNames.size
        """.trimIndent())
        
        sb.appendLine("}")
        return sb.toString()
    }
    
    // String-averse helper functions
    private fun getOrCreateConceptId(name: String): Int {
        return conceptIds.getOrPut(name) {
            conceptNames.add(name)
            conceptNames.size - 1
        }
    }
    
    private fun extractConceptName(expr: KifExpression): String? = when (expr) {
        is KifExpression.Atom -> expr.value
        is KifExpression.Str -> expr.value
        else -> null
    }
    
    private fun isSubclassExpression(expr: KifExpression): Boolean {
        return expr is KifExpression.Cons &&
               expr.car is KifExpression.Atom &&
               (expr.car as KifExpression.Atom).value == "subclass"
    }
    
    private fun isInstanceExpression(expr: KifExpression): Boolean {
        return expr is KifExpression.Cons &&
               expr.car is KifExpression.Atom &&
               (expr.car as KifExpression.Atom).value == "instance"
    }
}

/**
 * Triple Dispatch Pattern for Expression Type Combinations
 * Enables compile-time optimization of operations between different expression types
 */
interface ExpressionDispatcher<R> {
    fun dispatch(expr1: KifExpression, expr2: KifExpression): R
}

/**
 * Example triple dispatch implementation for expression comparison
 */
object ExpressionComparator : ExpressionDispatcher<Boolean> {
    override fun dispatch(expr1: KifExpression, expr2: KifExpression): Boolean {
        return when {
            expr1 is KifExpression.Atom && expr2 is KifExpression.Atom -> 
                expr1.value == expr2.value
            expr1 is KifExpression.Str && expr2 is KifExpression.Str -> 
                expr1.value == expr2.value
            expr1 is KifExpression.Cons && expr2 is KifExpression.Cons -> 
                dispatch(expr1.car, expr2.car) && dispatch(expr1.cdr, expr2.cdr)
            expr1 is KifExpression.Nil && expr2 is KifExpression.Nil -> 
                true
            else -> false
        }
    }
}

// Data classes for statistics
data class CorpusStats(
    val totalExpressions: Int,
    val subclassExpressions: Int,
    val instanceExpressions: Int,
    val uniqueConcepts: Int,
    val duration: Long
)

data class ClosureStats(
    val iterations: Int,
    val totalRelationships: Int,
    val duration: Long
)

data class GeneratedCode(
    val subclassQueries: String,
    val instanceQueries: String,
    val conceptQueries: String
) 