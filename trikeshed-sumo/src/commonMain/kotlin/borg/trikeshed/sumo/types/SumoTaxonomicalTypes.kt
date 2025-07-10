@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.types

import borg.trikeshed.lib.*

/**
 * SUMO Taxonomical Type Aliases
 * 
 * These define the core ontological types used in SUMO using proper
 * TrikeShed patterns with Join composition and value classes for
 * compile-time type safety and zero-cost abstractions.
 */

// === CORE ONTOLOGICAL TYPES ===

/**
 * Concept Identifier - Unique integer ID for each concept
 */
@JvmInline value class ConceptId(val value: Int) {
    companion object {
        val INVALID = ConceptId(-1)
        val ROOT = ConceptId(0)
    }
}

/**
 * Concept Name - Interned string representation
 */
@JvmInline value class ConceptName(val value: String) {
    companion object {
        val EMPTY = ConceptName("")
        val THING = ConceptName("Thing")
        val ENTITY = ConceptName("Entity")
    }
}

/**
 * Concept - Join composition of ID and Name
 */
typealias Concept = Join<ConceptId, ConceptName>

/**
 * Relationship Type - Defines the kind of ontological relationship
 */
@JvmInline value class RelationshipType(val value: Int) {
    companion object {
        val SUBCLASS = RelationshipType(1)
        val INSTANCE = RelationshipType(2)
        val PART_OF = RelationshipType(3)
        val MEMBER_OF = RelationshipType(4)
        val DOMAIN = RelationshipType(5)
        val RANGE = RelationshipType(6)
    }
}

/**
 * Relationship - Join composition of source, target, and type
 */
typealias Relationship = Join<Join<Concept, Concept>, RelationshipType>

// === KNOWLEDGE BASE STRUCTURES ===

/**
 * Knowledge Base - Join composition of concepts and relationships
 */
typealias KnowledgeBase = Join<Indexed<Concept>, Indexed<Relationship>>

/**
 * Query - Join composition of input and predicate function
 */
typealias Query<A, R> = Join<A, (A) -> R>

/**
 * Subclass Query - Specialized query for subclass relationships
 */
typealias SubclassQuery = Query<Join<ConceptName, ConceptName>, Boolean>

/**
 * Instance Query - Specialized query for instance relationships
 */
typealias InstanceQuery = Query<Join<ConceptName, ConceptName>, Boolean>

// === HIERARCHICAL STRUCTURES ===

/**
 * Hierarchy Level - Depth in the ontological hierarchy
 */
@JvmInline value class HierarchyLevel(val value: Int) {
    companion object {
        val ROOT = HierarchyLevel(0)
        val TOP_LEVEL = HierarchyLevel(1)
        val MID_LEVEL = HierarchyLevel(2)
        val LEAF_LEVEL = HierarchyLevel(3)
    }
}

/**
 * Hierarchical Concept - Concept with its level in the hierarchy
 */
typealias HierarchicalConcept = Join<Concept, HierarchyLevel>

/**
 * Hierarchy Path - Sequence of concepts from root to leaf
 */
typealias HierarchyPath = Indexed<Concept>

/**
 * Hierarchy Tree - Tree structure of concepts
 */
typealias HierarchyTree = Join<Concept, Indexed<HierarchicalConcept>>

// === SEMANTIC TYPES ===

/**
 * Semantic Type - Defines the semantic category of a concept
 */
@JvmInline value class SemanticType(val value: Int) {
    companion object {
        val PHYSICAL = SemanticType(1)
        val ABSTRACT = SemanticType(2)
        val PROCESS = SemanticType(3)
        val ATTRIBUTE = SemanticType(4)
        val RELATION = SemanticType(5)
        val FUNCTION = SemanticType(6)
    }
}

/**
 * Typed Concept - Concept with semantic type information
 */
typealias TypedConcept = Join<Concept, SemanticType>

/**
 * Semantic Query - Query that considers semantic types
 */
typealias SemanticQuery = Query<Join<TypedConcept, TypedConcept>, Boolean>

// === AXIOMATIC STRUCTURES ===

/**
 * Axiom Type - Defines the kind of logical axiom
 */
@JvmInline value class AxiomType(val value: Int) {
    companion object {
        val DEFINITION = AxiomType(1)
        val THEOREM = AxiomType(2)
        val RULE = AxiomType(3)
        val CONSTRAINT = AxiomType(4)
        val ASSERTION = AxiomType(5)
    }
}

/**
 * Axiom - Logical statement in the knowledge base
 */
typealias Axiom = Join<Join<AxiomType, Concept>, Indexed<Concept>>

/**
 * Axiom Base - Collection of axioms
 */
typealias AxiomBase = Indexed<Axiom>

// === COMPILE-TIME OPTIMIZATION TYPES ===

/**
 * Compile-Time Query - Pre-computed query result
 */
@JvmInline value class CompileTimeQuery(val value: Boolean) {
    companion object {
        val TRUE = CompileTimeQuery(true)
        val FALSE = CompileTimeQuery(false)
    }
}

/**
 * Optimized Query - Query with compile-time optimizations
 */
typealias OptimizedQuery<A, R> = Join<Query<A, R>, CompileTimeQuery>

/**
 * Query Cache - Cache of pre-computed query results
 */
typealias QueryCache = Join<Indexed<Concept>, Indexed<CompileTimeQuery>>

// === SIMD-OPTIMIZED TYPES ===

/**
 * SIMD Register - Packed data for vector operations
 */
@JvmInline value class SimdRegister(val value: Long) {
    companion object {
        val EMPTY = SimdRegister(0L)
    }
}

/**
 * Packed Concept - Concept packed into SIMD register
 */
typealias PackedConcept = Join<ConceptId, SimdRegister>

/**
 * Packed Relationship - Relationship packed into SIMD register
 */
typealias PackedRelationship = Join<Join<ConceptId, ConceptId>, RelationshipType>

/**
 * Vectorized Knowledge Base - Knowledge base optimized for SIMD
 */
typealias VectorizedKnowledgeBase = Join<Indexed<PackedConcept>, Indexed<PackedRelationship>>

// === UTILITY TYPE ALIASES ===

/**
 * Concept Pair - Pair of concepts for binary operations
 */
typealias ConceptPair = Join<Concept, Concept>

/**
 * Concept Triple - Triple of concepts for ternary operations
 */
typealias ConceptTriple = Join<Join<Concept, Concept>, Concept>

/**
 * Concept Sequence - Sequence of concepts
 */
typealias ConceptSequence = Indexed<Concept>

/**
 * Concept Set - Set of concepts
 */
typealias ConceptSet = Join<Int, (Int) -> Concept>

/**
 * Concept Map - Mapping from concepts to values
 */
typealias ConceptMap<T> = Join<Indexed<Concept>, Indexed<T>>

// === FACTORY FUNCTIONS ===

/**
 * Create a concept from ID and name
 */
fun concept(id: Int, name: String): Concept = ConceptId(id) j ConceptName(name)

/**
 * Create a relationship between two concepts
 */
fun relationship(source: Concept, target: Concept, type: RelationshipType): Relationship = 
    (source j target) j type

/**
 * Create a subclass relationship
 */
fun subclass(sub: Concept, sup: Concept): Relationship = 
    relationship(sub, sup, RelationshipType.SUBCLASS)

/**
 * Create an instance relationship
 */
fun instance(inst: Concept, concept: Concept): Relationship = 
    relationship(inst, concept, RelationshipType.INSTANCE)

/**
 * Create a query from input and predicate
 */
fun <A, R> query(input: A, predicate: (A) -> R): Query<A, R> = input j predicate

/**
 * Create a subclass query
 */
fun subclassQuery(sub: String, sup: String): SubclassQuery = 
    (ConceptName(sub) j ConceptName(sup)) j { pair -> 
        // This would be implemented with actual knowledge base lookup
        false 
    }

/**
 * Create an instance query
 */
fun instanceQuery(instance: String, concept: String): InstanceQuery = 
    (ConceptName(instance) j ConceptName(concept)) j { pair -> 
        // This would be implemented with actual knowledge base lookup
        false 
    } 