package borg.trikeshed.taxonomy

import borg.trikeshed.cursor.*
import borg.trikeshed.parse.TypeEvidence
import borg.trikeshed.lib.*
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.common.*
import borg.trikeshed.graph.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Type alias for content identifiers (CIDs)
 */
typealias ContentId = String

/**
 * Type alias for semantic identifiers
 */
typealias SemanticId = String

/**
 * Type alias for versioned content identifiers
 */
typealias VersionedCID = String

/**
 * Type alias for attention scores
 */
typealias AttentionScore = Double

/**
 * Type alias for concept vectors
 */
typealias ConceptVector = List<Float>

/**
 * Type alias for Merkle tree hashes
 */
typealias MerkleHash = String

/**
 * Base interface for all taxonomic entities
 */
interface TaxonomicEntity {
    val id: SemanticId
    val version: VersionedCID
    val attention: AttentionScore
    val concepts: ConceptVector
    val properties: Map<String, Any>
    val contentId: ContentId
    val merkleHash: MerkleHash
}

/**
 * DSL for defining taxonomic relationships
 */
@DslMarker
annotation class TaxonomyDSL

/**
 * Builder for taxonomic entities
 */
@TaxonomyDSL
class TaxonomicEntityBuilder {
    var id: SemanticId = ""
    var version: VersionedCID = ""
    var attention: AttentionScore = 0.0
    var concepts: ConceptVector = emptyList()
    private val properties = mutableMapOf<String, Any>()
    var contentId: ContentId = ""
    var merkleHash: MerkleHash = ""

    fun property(name: String, value: Any) {
        properties[name] = value
    }

    fun build(): TaxonomicEntity = DefaultTaxonomicEntity(
        id = id,
        version = version,
        attention = attention,
        concepts = concepts,
        properties = properties,
        contentId = contentId,
        merkleHash = merkleHash
    )
}

/**
 * Default implementation of TaxonomicEntity
 */
data class DefaultTaxonomicEntity(
    override val id: SemanticId,
    override val version: VersionedCID,
    override val attention: AttentionScore,
    override val concepts: ConceptVector,
    override val properties: Map<String, Any>,
    override val contentId: ContentId,
    override val merkleHash: MerkleHash
) : TaxonomicEntity

/**
 * DSL function for creating taxonomic entities
 */
fun taxonomic(init: TaxonomicEntityBuilder.() -> Unit): TaxonomicEntity {
    return TaxonomicEntityBuilder().apply(init).build()
}

/**
 * Type alias for relationship types
 */
typealias RelationshipType = String

/**
 * Type alias for relationship weights
 */
typealias RelationshipWeight = Double

/**
 * Represents a relationship between taxonomic entities
 */
data class TaxonomicRelationship(
    val source: SemanticId,
    val target: SemanticId,
    val type: RelationshipType,
    val weight: RelationshipWeight,
    val bidirectional: Boolean = false,
    val merkleHash: MerkleHash
)

/**
 * Builder for taxonomic relationships
 */
@TaxonomyDSL
class TaxonomicRelationshipBuilder {
    var source: SemanticId = ""
    var target: SemanticId = ""
    var type: RelationshipType = ""
    var weight: RelationshipWeight = 1.0
    var bidirectional: Boolean = false
    var merkleHash: MerkleHash = ""

    fun build(): TaxonomicRelationship = TaxonomicRelationship(
        source = source,
        target = target,
        type = type,
        weight = weight,
        bidirectional = bidirectional,
        merkleHash = merkleHash
    )
}

/**
 * DSL function for creating taxonomic relationships
 */
fun relationship(init: TaxonomicRelationshipBuilder.() -> Unit): TaxonomicRelationship {
    return TaxonomicRelationshipBuilder().apply(init).build()
}

/**
 * Type alias for attention models
 */
typealias AttentionModel = (ConceptVector, ConceptVector) -> AttentionScore

/**
 * Type alias for concept spaces
 */
typealias ConceptSpace = Map<SemanticId, ConceptVector>

/**
 * Represents a taxonomic graph with entities and relationships
 */
data class TaxonomicGraph(
    val entities: Map<SemanticId, TaxonomicEntity>,
    val relationships: List<TaxonomicRelationship>,
    val conceptSpace: ConceptSpace,
    val attentionModel: AttentionModel,
    val merkleRoot: MerkleHash
) {
    /**
     * Calculate attention score between two entities
     */
    fun calculateAttention(source: SemanticId, target: SemanticId): AttentionScore {
        val sourceEntity = entities[source] ?: return 0.0
        val targetEntity = entities[target] ?: return 0.0
        return attentionModel(sourceEntity.concepts, targetEntity.concepts)
    }

    /**
     * Find related entities with attention scores
     */
    fun findRelatedEntities(entityId: SemanticId, threshold: AttentionScore = 0.5): List<Join<SemanticId, AttentionScore>> {
        val entity = entities[entityId] ?: return emptyList()
        return entities.entries
            .filter { it.key != entityId }
            .map { it.key j calculateAttention(entityId, it.key) }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
    }

    /**
     * Get immutable snapshot of the graph
     */
    fun snapshot(): TaxonomicGraph = copy()

    /**
     * Watch for changes to entities
     */
    fun watchEntities(): Flow<TaxonomicEntity> = flowOf()

    /**
     * Watch for changes to relationships
     */
    fun watchRelationships(): Flow<TaxonomicRelationship> = flowOf()
}

/**
 * Builder for taxonomic graphs
 */
@TaxonomyDSL
class TaxonomicGraphBuilder {
    private val entities = mutableMapOf<SemanticId, TaxonomicEntity>()
    private val relationships = mutableListOf<TaxonomicRelationship>()
    private val conceptSpace = mutableMapOf<SemanticId, ConceptVector>()
    private var attentionModel: AttentionModel = { a, b -> 
        // Default cosine similarity using vector operations
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            val aVal = a[i].toDouble()
            val bVal = b[i].toDouble()
            dotProduct += aVal * bVal
            normA += aVal * aVal
            normB += bVal * bVal
        }
        
        if (normA == 0.0 || normB == 0.0) 0.0 else dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }
    private var merkleRoot: MerkleHash = ""

    fun entity(init: TaxonomicEntityBuilder.() -> Unit) {
        val entity = TaxonomicEntityBuilder().apply(init).build()
        entities[entity.id] = entity
        conceptSpace[entity.id] = entity.concepts
    }

    fun relationship(init: TaxonomicRelationshipBuilder.() -> Unit) {
        relationships.add(TaxonomicRelationshipBuilder().apply(init).build())
    }

    fun build(): TaxonomicGraph = TaxonomicGraph(
        entities = entities,
        relationships = relationships,
        conceptSpace = conceptSpace,
        attentionModel = attentionModel,
        merkleRoot = merkleRoot
    )
}

/**
 * DSL function for creating taxonomic graphs
 */
fun taxonomicGraph(init: TaxonomicGraphBuilder.() -> Unit): TaxonomicGraph {
    return TaxonomicGraphBuilder().apply(init).build()
}

/**
 * Extension function to create a taxonomic entity from a cursor row
 */
fun TaxonomicEntityBuilder.fromCursorRow(cursor: CoreTensorCursorWithMeta<Any>, rowIndex: Int) {
    val row = cursor.a.row(rowIndex)
    val meta = cursor.b
    
    id = row(0).toString()
    version = row(1).toString()
    attention = (row(2) as? Number)?.toDouble() ?: 0.0
    
    // Convert concept vector to list
    concepts = (row(3) as? List<*>)?.map { (it as? Number)?.toFloat() ?: 0f } ?: emptyList()
    
    // Add remaining columns as properties
    for (i in 4 until row.totalSize) {
        val columnMeta = meta(intArrayOf(i))
        property(columnMeta.name, row(i))
    }
}

/**
 * Extension function to create a taxonomic graph from a cursor
 */
fun TaxonomicGraphBuilder.fromCursor(cursor: CoreTensorCursorWithMeta<Any>) {
    // Process each row as an entity
    val rows = cursor.a.rows
    for (i in 0 until rows) {
        val entityBuilder = TaxonomicEntityBuilder()
        entityBuilder.fromCursorRow(cursor, i)
        entity { 
            id = entityBuilder.id
            version = entityBuilder.version
            attention = entityBuilder.attention
            concepts = entityBuilder.concepts
            entityBuilder.properties.forEach { (name, value) -> property(name, value) }
        }
    }
    
    // Create relationships based on type evidence
    val typeEvidence = cursor.b.map { meta ->
        TypeEvidence().apply {
            meta.name.forEach { char -> this + char }
        }
    }
    
    // Create relationships based on type similarity
    for (i in 0 until rows) {
        for (j in i + 1 until rows) {
            val similarity = calculateTypeSimilarity(typeEvidence(i), typeEvidence(j))
            if (similarity > 0.5) {
                relationship {
                    source = cursor.a(i, 0).toString()
                    target = cursor.a(j, 0).toString()
                    type = "type_similar"
                    weight = similarity
                    bidirectional = true
                }
            }
        }
    }
}

/**
 * Calculate similarity between two type evidence objects
 */
private fun calculateTypeSimilarity(a: TypeEvidence, b: TypeEvidence): Double {
    val totalA = a.digits + a.periods + a.exponent + a.signs + a.special + a.alpha + a.truefalse
    val totalB = b.digits + b.periods + b.exponent + b.signs + b.special + b.alpha + b.truefalse
    
    if (totalA == 0U || totalB == 0U) return 0.0
    
    val digitSim = minOf(a.digits, b.digits).toDouble() / maxOf(totalA, totalB).toDouble()
    val periodSim = minOf(a.periods, b.periods).toDouble() / maxOf(totalA, totalB).toDouble()
    val alphaSim = minOf(a.alpha, b.alpha).toDouble() / maxOf(totalA, totalB).toDouble()
    
    return (digitSim + periodSim + alphaSim) / 3.0
}

/**
 * Pandas-like DSL for taxonomic operations
 */
@TaxonomyDSL
class TaxonomicPandasDSL(private val graph: TaxonomicGraph) {
    /**
     * Get entities matching a filter condition
     */
    fun filter(predicate: (TaxonomicEntity) -> Boolean): List<TaxonomicEntity> {
        return graph.entities.values.filter(predicate)
    }
    
    /**
     * Group entities by a key selector
     */
    fun <K> groupBy(keySelector: (TaxonomicEntity) -> K): Map<K, List<TaxonomicEntity>> {
        return graph.entities.values.groupBy(keySelector)
    }
    
    /**
     * Sort entities by a comparator
     */
    fun sortBy(comparator: Comparator<TaxonomicEntity>): List<TaxonomicEntity> {
        return graph.entities.values.sortedWith(comparator)
    }
    
    /**
     * Get top N entities by attention score
     */
    fun topN(n: Int): List<TaxonomicEntity> {
        return graph.entities.values.sortedByDescending { it.attention }.take(n)
    }
    
    /**
     * Get related entities for a given entity
     */
    fun related(entityId: SemanticId, threshold: AttentionScore = 0.5): List<Join<TaxonomicEntity, AttentionScore>> {
        return graph.findRelatedEntities(entityId, threshold).map { join ->
            graph.entities[join.a]!! j join.b
        }
    }
    
    /**
     * Aggregate entities by a key and value selector
     */
    fun <K, V> aggregate(
        keySelector: (TaxonomicEntity) -> K,
        valueSelector: (TaxonomicEntity) -> V,
        aggregator: (List<V>) -> V
    ): Map<K, V> {
        return graph.entities.values
            .groupBy(keySelector)
            .mapValues { (_, entities) -> aggregator(entities.map(valueSelector)) }
    }

    /**
     * Get immutable snapshot of current state
     */
    fun snapshot(): TaxonomicGraph = graph.snapshot()

    /**
     * Watch for entity changes
     */
    fun watchEntities(): Flow<TaxonomicEntity> = graph.watchEntities()

    /**
     * Watch for relationship changes
     */
    fun watchRelationships(): Flow<TaxonomicRelationship> = graph.watchRelationships()
}

/**
 * Extension function to enable pandas-like operations on taxonomic graphs
 */
fun TaxonomicGraph.pandas(): TaxonomicPandasDSL = TaxonomicPandasDSL(this) 