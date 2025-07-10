@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.binding

import borg.trikeshed.lib.*
import borg.trikeshed.sumo.grammar.*
import borg.trikeshed.sumo.kif.*
import borg.trikeshed.sumo.types.*

/**
 * SUMO Binding Layer
 * 
 * This connects the EBNF grammar to the taxonomical types,
 * implementing semantic actions and type conversions using
 * Join composition patterns and double/triple dispatch.
 * 
 * The binding layer provides:
 * 1. Grammar-to-Type mapping
 * 2. Semantic action implementation
 * 3. Type conversion utilities
 * 4. Compile-time optimization hooks
 */
class SumoBinding {
    
    // String interning for concept names
    private val conceptNameIntern = mutableMapOf<String, ConceptName>()
    private val conceptIdCounter = mutableMapOf<String, Int>()
    
    /**
     * Bind KIF expression to SUMO concept using Join composition
     */
    fun bindExpression(expr: KifExpression): BoundExpression {
        return expr.accept(ExpressionBinder)
    }
    
    /**
     * Bind KIF expression to SUMO relationship
     */
    fun bindRelationship(expr: KifExpression): Relationship? {
        return expr.accept(RelationshipBinder)
    }
    
    /**
     * Bind KIF expression to SUMO axiom
     */
    fun bindAxiom(expr: KifExpression): Axiom? {
        return expr.accept(AxiomBinder)
    }
    
    /**
     * Create knowledge base from bound expressions
     */
    fun createKnowledgeBase(expressions: List<BoundExpression>): KnowledgeBase {
        val concepts = mutableListOf<Concept>()
        val relationships = mutableListOf<Relationship>()
        
        expressions.forEach { boundExpr ->
            when (boundExpr) {
                is BoundExpression.Concept -> concepts.add(boundExpr.concept)
                is BoundExpression.Relationship -> relationships.add(boundExpr.relationship)
                is BoundExpression.Axiom -> {
                    // Extract concepts and relationships from axiom
                    extractFromAxiom(boundExpr.axiom, concepts, relationships)
                }
            }
        }
        
        val conceptIndex = concepts.size j { i -> concepts[i] }
        val relationshipIndex = relationships.size j { i -> relationships[i] }
        
        return conceptIndex j relationshipIndex
    }
    
    /**
     * Double Dispatch Visitor for Expression Binding
     */
    private object ExpressionBinder : KifExpressionVisitor<BoundExpression> {
        override fun visitCons(cons: KifExpression.Cons): BoundExpression {
            val list = cons.toList()
            
            return when {
                isSubclassExpression(list) -> {
                    val sub = bindConceptName(list[1])
                    val sup = bindConceptName(list[2])
                    val relationship = subclass(sub, sup)
                    BoundExpression.Relationship(relationship)
                }
                isInstanceExpression(list) -> {
                    val instance = bindConceptName(list[1])
                    val concept = bindConceptName(list[2])
                    val relationship = instance(instance, concept)
                    BoundExpression.Relationship(relationship)
                }
                isAxiomExpression(list) -> {
                    val axiom = bindAxiomFromList(list)
                    BoundExpression.Axiom(axiom)
                }
                else -> {
                    // Treat as concept definition
                    val concept = bindConceptFromList(list)
                    BoundExpression.Concept(concept)
                }
            }
        }
        
        override fun visitAtom(atom: KifExpression.Atom): BoundExpression {
            val concept = bindConceptName(atom.value)
            return BoundExpression.Concept(concept)
        }
        
        override fun visitStr(str: KifExpression.Str): BoundExpression {
            val concept = bindConceptName(str.value)
            return BoundExpression.Concept(concept)
        }
        
        override fun visitNil(nil: KifExpression.Nil): BoundExpression {
            return BoundExpression.Concept(ConceptId.ROOT j ConceptName.EMPTY)
        }
    }
    
    /**
     * Double Dispatch Visitor for Relationship Binding
     */
    private object RelationshipBinder : KifExpressionVisitor<Relationship?> {
        override fun visitCons(cons: KifExpression.Cons): Relationship? {
            val list = cons.toList()
            
            return when {
                isSubclassExpression(list) -> {
                    val sub = bindConceptName(list[1])
                    val sup = bindConceptName(list[2])
                    subclass(sub, sup)
                }
                isInstanceExpression(list) -> {
                    val instance = bindConceptName(list[1])
                    val concept = bindConceptName(list[2])
                    instance(instance, concept)
                }
                else -> null
            }
        }
        
        override fun visitAtom(atom: KifExpression.Atom): Relationship? = null
        override fun visitStr(str: KifExpression.Str): Relationship? = null
        override fun visitNil(nil: KifExpression.Nil): Relationship? = null
    }
    
    /**
     * Double Dispatch Visitor for Axiom Binding
     */
    private object AxiomBinder : KifExpressionVisitor<Axiom?> {
        override fun visitCons(cons: KifExpression.Cons): Axiom? {
            val list = cons.toList()
            
            return when {
                isAxiomExpression(list) -> bindAxiomFromList(list)
                else -> null
            }
        }
        
        override fun visitAtom(atom: KifExpression.Atom): Axiom? = null
        override fun visitStr(str: KifExpression.Str): Axiom? = null
        override fun visitNil(nil: KifExpression.Nil): Axiom? = null
    }
    
    /**
     * Triple Dispatch for Expression Type Combinations
     */
    interface ExpressionDispatcher<R> {
        fun dispatch(expr1: KifExpression, expr2: KifExpression, context: BindingContext): R
    }
    
    /**
     * Example triple dispatch implementation
     */
    object ExpressionComparator : ExpressionDispatcher<Boolean> {
        override fun dispatch(expr1: KifExpression, expr2: KifExpression, context: BindingContext): Boolean {
            return when {
                expr1 is KifExpression.Atom && expr2 is KifExpression.Atom -> 
                    expr1.value == expr2.value
                expr1 is KifExpression.Str && expr2 is KifExpression.Str -> 
                    expr1.value == expr2.value
                expr1 is KifExpression.Cons && expr2 is KifExpression.Cons -> 
                    dispatch(expr1.car, expr2.car, context) && dispatch(expr1.cdr, expr2.cdr, context)
                expr1 is KifExpression.Nil && expr2 is KifExpression.Nil -> 
                    true
                else -> false
            }
        }
    }
    
    // Helper functions for binding
    private fun bindConceptName(name: String): Concept {
        val conceptName = conceptNameIntern.getOrPut(name) { ConceptName(name) }
        val id = conceptIdCounter.getOrPut(name) { conceptIdCounter.size }
        return ConceptId(id) j conceptName
    }
    
    private fun bindConceptFromList(list: List<KifExpression>): Concept {
        val name = when (val first = list.firstOrNull()) {
            is KifExpression.Atom -> first.value
            is KifExpression.Str -> first.value
            else -> "Unknown"
        }
        return bindConceptName(name)
    }
    
    private fun bindAxiomFromList(list: List<KifExpression>): Axiom {
        val axiomType = when (val first = list.firstOrNull()) {
            is KifExpression.Atom -> when (first.value) {
                "subclass" -> AxiomType.DEFINITION
                "instance" -> AxiomType.ASSERTION
                else -> AxiomType.THEOREM
            }
            else -> AxiomType.THEOREM
        }
        
        val mainConcept = bindConceptFromList(list)
        val relatedConcepts = list.drop(1).map { bindExpression(it).concept }
        val conceptIndex = relatedConcepts.size j { i -> relatedConcepts[i] }
        
        return (axiomType j mainConcept) j conceptIndex
    }
    
    private fun isSubclassExpression(list: List<KifExpression>): Boolean {
        return list.size == 3 && 
               list[0] is KifExpression.Atom && 
               (list[0] as KifExpression.Atom).value == "subclass"
    }
    
    private fun isInstanceExpression(list: List<KifExpression>): Boolean {
        return list.size == 3 && 
               list[0] is KifExpression.Atom && 
               (list[0] as KifExpression.Atom).value == "instance"
    }
    
    private fun isAxiomExpression(list: List<KifExpression>): Boolean {
        return list.isNotEmpty() && 
               list[0] is KifExpression.Atom && 
               (list[0] as KifExpression.Atom).value in setOf("subclass", "instance", "domain", "range")
    }
    
    private fun extractFromAxiom(axiom: Axiom, concepts: MutableList<Concept>, relationships: MutableList<Relationship>) {
        val mainConcept = axiom.a.b
        concepts.add(mainConcept)
        
        val relatedConcepts = axiom.b.play
        concepts.addAll(relatedConcepts)
        
        // Create relationships based on axiom type
        when (axiom.a.a) {
            AxiomType.DEFINITION -> {
                // Definition axioms create subclass relationships
                relatedConcepts.forEach { related ->
                    relationships.add(subclass(mainConcept, related))
                }
            }
            AxiomType.ASSERTION -> {
                // Assertion axioms create instance relationships
                relatedConcepts.forEach { related ->
                    relationships.add(instance(mainConcept, related))
                }
            }
            else -> {
                // Other axioms create general relationships
                relatedConcepts.forEach { related ->
                    relationships.add(relationship(mainConcept, related, RelationshipType.RELATION))
                }
            }
        }
    }
}

// Binding result types using Join composition
sealed class BoundExpression {
    data class Concept(val concept: Concept) : BoundExpression()
    data class Relationship(val relationship: Relationship) : BoundExpression()
    data class Axiom(val axiom: Axiom) : BoundExpression()
    
    val concept: Concept
        get() = when (this) {
            is Concept -> this.concept
            is Relationship -> this.relationship.a.a
            is Axiom -> this.axiom.a.b
        }
}

// Binding context for triple dispatch
data class BindingContext(
    val knowledgeBase: KnowledgeBase? = null,
    val conceptCache: Map<String, Concept> = emptyMap(),
    val relationshipCache: Map<String, Relationship> = emptyMap()
) 