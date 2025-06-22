package borg.trikeshed.scanner

import borg.trikeshed.graph.*
import borg.trikeshed.lib.*

/**
 * Bitgraph Integration for Kotlin Entity Scanner
 * 
 * Bridges dense bitgraph symbol data from nexus into kotlin entity representations
 * for downstream KSP processing. This creates the pipeline:
 * Dense Bitgraph → Entity Scanner → KSP → Code Generation
 */
class BitgraphEntityBridge {
    
    // Symbol name cache for reverse hash lookup
    private val symbolNameCache = mutableMapOf<UInt, String>()
    
    /**
     * Register a symbol name for reverse hash lookup
     */
    fun registerSymbolName(symbolName: String, hash: UInt) {
        symbolNameCache[hash] = symbolName
    }
    
    /**
     * Bulk register symbol names from a source
     */
    fun registerSymbolNames(symbols: Map<String, UInt>) {
        symbolNameCache.putAll(symbols.entries.associate { it.value to it.key })
    }
    
    /**
     * Get symbol name from hash or generate fallback
     */
    private fun getSymbolName(hash: UInt): String {
        return symbolNameCache[hash] ?: "symbol_$hash"
    }
    
    /**
     * Convert dense bitgraph symbols to kotlin entity graph
     */
    fun processDenseBitgraph(bitgraph: RealtimeDenseBitgraph): KotlinEntityGraph {
        val entities = mutableListOf<KotlinEntity>()
        val relationships = mutableListOf<EntityRelationship>()
        
        // Process symbol nodes
        bitgraph.symbols.forEach { symbolNode ->
            val entity = convertSymbolToEntity(symbolNode)
            entities.add(entity)
        }
        
        // Process relationships
        bitgraph.relations.forEach { relationEdge ->
            val relationship = convertRelationToEntityRelationship(relationEdge)
            relationships.add(relationship)
        }
        
        return KotlinEntityGraph(
            entities = entities.toIndexed(),
            relationships = relationships.toIndexed()
        )
    }
    
    /**
     * Convert evidence-tracked symbols with full inference chains
     */
    fun processEvidenceTrackedSymbols(symbols: EvidenceSymbolGraph): EvidenceTrackedEntityGraph {
        val trackedEntities = mutableListOf<EvidenceTrackedEntity>()
        
        symbols.forEach { evidenceSymbol ->
            val entity = convertSymbolToEntity(evidenceSymbol.symbol)
            val evidenceChain = evidenceSymbol.evidenceChain
            
            val trackedEntity = EvidenceTrackedEntity(
                entity = entity,
                evidenceChain = evidenceChain,
                confidence = calculateConfidenceFromEvidence(evidenceChain)
            )
            
            trackedEntities.add(trackedEntity)
        }
        
        return EvidenceTrackedEntityGraph(trackedEntities.toIndexed())
    }
    
    private fun convertSymbolToEntity(symbolNode: DenseSymbolNode): KotlinEntity {
        val metadata = symbolNode.metadata
        val symbolType = metadata.symbolType()
        val modifiers = metadata.modifiers()
        
        return KotlinEntity(
            id = EntityId(symbolNode.hash.hash.toString()),
            name = EntityName(getSymbolName(symbolNode.hash.hash)),
            type = mapSymbolTypeToEntityType(symbolType),
            visibility = mapModifiersToVisibility(modifiers),
            modifiers = extractModifierSet(modifiers),
            location = EntityLocation(
                fileHash = metadata.fileHash(),
                lineNumber = metadata.lineNumber()
            )
        )
    }
    
    private fun convertRelationToEntityRelationship(edge: DenseRelationEdge): EntityRelationship {
        return EntityRelationship(
            sourceId = EntityId(edge.source.hash.toString()),
            targetId = EntityId(edge.target.hash.toString()),
            type = mapRelationTypeToEntityRelationType(edge.relationType.type)
        )
    }
    
    private fun mapSymbolTypeToEntityType(symbolType: UShort): EntityType {
        return when (symbolType) {
            SymbolTypeBits.CLASS -> EntityType.CLASS
            SymbolTypeBits.FUNCTION -> EntityType.FUNCTION
            SymbolTypeBits.PROPERTY -> EntityType.PROPERTY
            SymbolTypeBits.INTERFACE -> EntityType.INTERFACE
            SymbolTypeBits.ENUM -> EntityType.ENUM
            SymbolTypeBits.ANNOTATION -> EntityType.ANNOTATION
            SymbolTypeBits.OBJECT -> EntityType.OBJECT
            SymbolTypeBits.COMPANION -> EntityType.COMPANION_OBJECT
            SymbolTypeBits.CONSTRUCTOR -> EntityType.CONSTRUCTOR
            SymbolTypeBits.PARAMETER -> EntityType.PARAMETER
            SymbolTypeBits.TYPE_PARAM -> EntityType.TYPE_PARAMETER
            SymbolTypeBits.PACKAGE -> EntityType.PACKAGE
            SymbolTypeBits.IMPORT -> EntityType.IMPORT
            SymbolTypeBits.FILE -> EntityType.FILE
            SymbolTypeBits.MODULE -> EntityType.MODULE
            SymbolTypeBits.VARIABLE -> EntityType.VARIABLE
            else -> EntityType.UNKNOWN
        }
    }
    
    private fun mapModifiersToVisibility(modifiers: UShort): EntityVisibility {
        return when {
            modifiers and ModifierBits.PUBLIC != 0u.toUShort() -> EntityVisibility.PUBLIC
            modifiers and ModifierBits.PRIVATE != 0u.toUShort() -> EntityVisibility.PRIVATE
            modifiers and ModifierBits.PROTECTED != 0u.toUShort() -> EntityVisibility.PROTECTED
            modifiers and ModifierBits.INTERNAL != 0u.toUShort() -> EntityVisibility.INTERNAL
            else -> EntityVisibility.PUBLIC // Default
        }
    }
    
    private fun extractModifierSet(modifiers: UShort): Set<EntityModifier> {
        val modifierSet = mutableSetOf<EntityModifier>()
        
        if (modifiers and ModifierBits.ABSTRACT != 0u.toUShort()) modifierSet.add(EntityModifier.ABSTRACT)
        if (modifiers and ModifierBits.FINAL != 0u.toUShort()) modifierSet.add(EntityModifier.FINAL)
        if (modifiers and ModifierBits.OPEN != 0u.toUShort()) modifierSet.add(EntityModifier.OPEN)
        if (modifiers and ModifierBits.OVERRIDE != 0u.toUShort()) modifierSet.add(EntityModifier.OVERRIDE)
        if (modifiers and ModifierBits.INLINE != 0u.toUShort()) modifierSet.add(EntityModifier.INLINE)
        if (modifiers and ModifierBits.SUSPEND != 0u.toUShort()) modifierSet.add(EntityModifier.SUSPEND)
        if (modifiers and ModifierBits.OPERATOR != 0u.toUShort()) modifierSet.add(EntityModifier.OPERATOR)
        if (modifiers and ModifierBits.INFIX != 0u.toUShort()) modifierSet.add(EntityModifier.INFIX)
        if (modifiers and ModifierBits.EXTERNAL != 0u.toUShort()) modifierSet.add(EntityModifier.EXTERNAL)
        if (modifiers and ModifierBits.COMPANION != 0u.toUShort()) modifierSet.add(EntityModifier.COMPANION)
        if (modifiers and ModifierBits.DATA != 0u.toUShort()) modifierSet.add(EntityModifier.DATA)
        if (modifiers and ModifierBits.SEALED != 0u.toUShort()) modifierSet.add(EntityModifier.SEALED)
        
        return modifierSet
    }
    
    private fun mapRelationTypeToEntityRelationType(relationType: UByte): EntityRelationType {
        return when (relationType) {
            RelationTypeBits.INHERITS -> EntityRelationType.INHERITS
            RelationTypeBits.IMPLEMENTS -> EntityRelationType.IMPLEMENTS
            RelationTypeBits.CALLS -> EntityRelationType.CALLS
            RelationTypeBits.REFERENCES -> EntityRelationType.REFERENCES
            RelationTypeBits.CONTAINS -> EntityRelationType.CONTAINS
            RelationTypeBits.OVERRIDES -> EntityRelationType.OVERRIDES
            RelationTypeBits.ANNOTATED_BY -> EntityRelationType.ANNOTATED_BY
            RelationTypeBits.IMPORTS -> EntityRelationType.IMPORTS
            RelationTypeBits.EXTENDS -> EntityRelationType.EXTENDS
            RelationTypeBits.USES -> EntityRelationType.USES
            RelationTypeBits.DECLARES -> EntityRelationType.DECLARES
            RelationTypeBits.INITIALIZES -> EntityRelationType.INITIALIZES
            RelationTypeBits.THROWS -> EntityRelationType.THROWS
            RelationTypeBits.CATCHES -> EntityRelationType.CATCHES
            RelationTypeBits.LAMBDA_CAPTURES -> EntityRelationType.LAMBDA_CAPTURES
            RelationTypeBits.TYPE_BOUNDS -> EntityRelationType.TYPE_BOUNDS
            else -> EntityRelationType.UNKNOWN
        }
    }
    
    private fun calculateConfidenceFromEvidence(chain: CompleteEvidenceChain): Float {
        val analyzer = EvidenceChainAnalyzer()
        return analyzer.analyzeChainReliability(chain)
    }
}

/**
 * Entity types for kotlin-entity-scanner
 */
@JvmInline
value class EntityId(val id: String)

@JvmInline
value class EntityName(val name: String)

@JvmInline
value class EntityLocation(val packed: UInt) {
    constructor(fileHash: UShort, lineNumber: UShort) : this(
        (fileHash.toUInt() shl 16) or lineNumber.toUInt()
    )
    
    val fileHash: UShort get() = (packed shr 16).toUShort()
    val lineNumber: UShort get() = packed.toUShort()
}

enum class EntityType {
    CLASS, FUNCTION, PROPERTY, INTERFACE, ENUM, ANNOTATION,
    OBJECT, COMPANION_OBJECT, CONSTRUCTOR, PARAMETER,
    TYPE_PARAMETER, PACKAGE, IMPORT, FILE, MODULE, VARIABLE, UNKNOWN
}

enum class EntityVisibility {
    PUBLIC, PRIVATE, PROTECTED, INTERNAL
}

enum class EntityModifier {
    ABSTRACT, FINAL, OPEN, OVERRIDE, INLINE, SUSPEND,
    OPERATOR, INFIX, EXTERNAL, COMPANION, DATA, SEALED
}

enum class EntityRelationType {
    INHERITS, IMPLEMENTS, CALLS, REFERENCES, CONTAINS,
    OVERRIDES, ANNOTATED_BY, IMPORTS, EXTENDS, USES,
    DECLARES, INITIALIZES, THROWS, CATCHES, LAMBDA_CAPTURES,
    TYPE_BOUNDS, UNKNOWN
}

/**
 * Core entity representation
 */
data class KotlinEntity(
    val id: EntityId,
    val name: EntityName,
    val type: EntityType,
    val visibility: EntityVisibility,
    val modifiers: Set<EntityModifier>,
    val location: EntityLocation
)

/**
 * Entity relationship
 */
data class EntityRelationship(
    val sourceId: EntityId,
    val targetId: EntityId,
    val type: EntityRelationType
)

/**
 * Entity with evidence tracking
 */
data class EvidenceTrackedEntity(
    val entity: KotlinEntity,
    val evidenceChain: CompleteEvidenceChain,
    val confidence: Float
)

/**
 * Graph structures
 */
data class KotlinEntityGraph(
    val entities: Indexed<KotlinEntity>,
    val relationships: Indexed<EntityRelationship>
)

data class EvidenceTrackedEntityGraph(
    val trackedEntities: Indexed<EvidenceTrackedEntity>
)

/**
 * Convert MutableList to Indexed
 */
private fun <T> MutableList<T>.toIndexed(): Indexed<T> {
    return this.fold(createEmptyIndexed<T>()) { acc, item -> acc.add(item) }
}