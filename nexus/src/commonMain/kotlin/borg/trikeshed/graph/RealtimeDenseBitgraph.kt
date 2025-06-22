package borg.trikeshed.graph

import borg.trikeshed.lib.*

/**
 * Realtime Dense Bitgraph System for Kotlin Symbol Analysis
 * 
 * Creates register-packed Join<A,B> structures for rapid AI consumption
 * of kotlin symbol metadata in real-time during compilation and analysis.
 * 
 * Design:
 * - Dense bit packing for maximum register efficiency
 * - Realtime updates during symbol processing
 * - AI-optimized data structures for rapid querying
 * - Zero-allocation streaming updates
 */

/**
 * Bit-packed symbol identifier (32-bit hash for dense storage)
 */
@JvmInline
value class SymbolHash(val hash: UInt)

/**
 * Compact symbol metadata packed into 64 bits
 * Bits 0-15:  Symbol type (16 types max)
 * Bits 16-31: File hash (65536 files max)
 * Bits 32-47: Line number (65536 lines max)  
 * Bits 48-63: Visibility/modifiers flags
 */
@JvmInline
value class PackedSymbolMeta(val packed: ULong)

/**
 * Relationship type between symbols (8-bit for density)
 */
@JvmInline
value class RelationType(val type: UByte)

/**
 * Timestamp for realtime updates (32-bit unix timestamp)
 */
@JvmInline
value class UpdateTimestamp(val timestamp: UInt)

/**
 * Dense symbol node: hash + metadata in single 96-bit structure
 */
typealias DenseSymbolNode = Join<SymbolHash, PackedSymbolMeta>

/**
 * Dense relationship edge: source hash + target hash + relation type
 */
typealias DenseRelationEdge = Join<Join<SymbolHash, SymbolHash>, RelationType>

/**
 * Realtime update entry: symbol + timestamp for streaming
 */
typealias RealtimeUpdate = Join<DenseSymbolNode, UpdateTimestamp>

/**
 * Collection of dense symbol nodes for AI consumption
 */
typealias DenseSymbolGraph = Indexed<DenseSymbolNode>

/**
 * Collection of dense relationship edges
 */
typealias DenseRelationGraph = Indexed<DenseRelationEdge>

/**
 * Streaming update buffer for realtime processing
 */
typealias RealtimeUpdateStream = Indexed<RealtimeUpdate>

/**
 * Complete dense bitgraph combining symbols, relations, and updates
 */
typealias RealtimeDenseBitgraph = Join<Join<DenseSymbolGraph, DenseRelationGraph>, RealtimeUpdateStream>

/**
 * Symbol type constants for bit packing (0-15 range)
 */
object SymbolTypeBits {
    const val CLASS: UShort = 0u
    const val FUNCTION: UShort = 1u
    const val PROPERTY: UShort = 2u
    const val INTERFACE: UShort = 3u
    const val ENUM: UShort = 4u
    const val ANNOTATION: UShort = 5u
    const val OBJECT: UShort = 6u
    const val COMPANION: UShort = 7u
    const val CONSTRUCTOR: UShort = 8u
    const val PARAMETER: UShort = 9u
    const val TYPE_PARAM: UShort = 10u
    const val PACKAGE: UShort = 11u
    const val IMPORT: UShort = 12u
    const val FILE: UShort = 13u
    const val MODULE: UShort = 14u
    const val VARIABLE: UShort = 15u
}

/**
 * Visibility/modifier flags for bit packing
 */
object ModifierBits {
    const val PUBLIC: UShort = 0x0001u
    const val PRIVATE: UShort = 0x0002u
    const val PROTECTED: UShort = 0x0004u
    const val INTERNAL: UShort = 0x0008u
    const val ABSTRACT: UShort = 0x0010u
    const val FINAL: UShort = 0x0020u
    const val OPEN: UShort = 0x0040u
    const val OVERRIDE: UShort = 0x0080u
    const val INLINE: UShort = 0x0100u
    const val SUSPEND: UShort = 0x0200u
    const val OPERATOR: UShort = 0x0400u
    const val INFIX: UShort = 0x0800u
    const val EXTERNAL: UShort = 0x1000u
    const val COMPANION: UShort = 0x2000u
    const val DATA: UShort = 0x4000u
    const val SEALED: UShort = 0x8000u
}

/**
 * Relation type constants for relationships
 */
object RelationTypeBits {
    const val INHERITS: UByte = 0u
    const val IMPLEMENTS: UByte = 1u
    const val CALLS: UByte = 2u
    const val REFERENCES: UByte = 3u
    const val CONTAINS: UByte = 4u
    const val OVERRIDES: UByte = 5u
    const val ANNOTATED_BY: UByte = 6u
    const val IMPORTS: UByte = 7u
    const val EXTENDS: UByte = 8u
    const val USES: UByte = 9u
    const val DECLARES: UByte = 10u
    const val INITIALIZES: UByte = 11u
    const val THROWS: UByte = 12u
    const val CATCHES: UByte = 13u
    const val LAMBDA_CAPTURES: UByte = 14u
    const val TYPE_BOUNDS: UByte = 15u
}

/**
 * Extension functions for dense symbol access
 */
val DenseSymbolNode.hash: SymbolHash get() = this.a
val DenseSymbolNode.metadata: PackedSymbolMeta get() = this.b

val DenseRelationEdge.source: SymbolHash get() = this.a.a
val DenseRelationEdge.target: SymbolHash get() = this.a.b
val DenseRelationEdge.relationType: RelationType get() = this.b

val RealtimeUpdate.symbol: DenseSymbolNode get() = this.a
val RealtimeUpdate.timestamp: UpdateTimestamp get() = this.b

val RealtimeDenseBitgraph.symbols: DenseSymbolGraph get() = this.a.a
val RealtimeDenseBitgraph.relations: DenseRelationGraph get() = this.a.b
val RealtimeDenseBitgraph.updates: RealtimeUpdateStream get() = this.b

/**
 * Bit manipulation utilities for packed metadata
 */
fun PackedSymbolMeta.symbolType(): UShort = (packed and 0xFFFFu).toUShort()
fun PackedSymbolMeta.fileHash(): UShort = ((packed shr 16) and 0xFFFFu).toUShort()
fun PackedSymbolMeta.lineNumber(): UShort = ((packed shr 32) and 0xFFFFu).toUShort()
fun PackedSymbolMeta.modifiers(): UShort = ((packed shr 48) and 0xFFFFu).toUShort()

/**
 * Factory functions for creating dense structures
 */
fun createDenseSymbolNode(
    symbolName: String,
    symbolType: UShort,
    fileName: String,
    lineNumber: UShort,
    modifiers: UShort
): DenseSymbolNode {
    val hash = SymbolHash(symbolName.hashCode().toUInt())
    val fileHash = fileName.hashCode().toUInt() and 0xFFFFu
    val packed = symbolType.toULong() or
                (fileHash shl 16) or
                (lineNumber.toULong() shl 32) or
                (modifiers.toULong() shl 48)
    return hash j PackedSymbolMeta(packed)
}

fun createDenseRelationEdge(
    sourceSymbol: String,
    targetSymbol: String,
    relationType: UByte
): DenseRelationEdge {
    val sourceHash = SymbolHash(sourceSymbol.hashCode().toUInt())
    val targetHash = SymbolHash(targetSymbol.hashCode().toUInt())
    return (sourceHash j targetHash) j RelationType(relationType)
}

fun createRealtimeUpdate(
    symbolNode: DenseSymbolNode,
    currentTimeMillis: Long
): RealtimeUpdate {
    val timestamp = UpdateTimestamp((currentTimeMillis / 1000).toUInt())
    return symbolNode j timestamp
}

/**
 * Realtime Dense Bitgraph Processor with Evidence Chain Integration
 * 
 * High-performance streaming processor for kotlin symbol analysis
 * Enhanced with evidence chain tracking for inference provenance
 */
class RealtimeDenseBitgraphProcessor {
    private var symbolNodes = createEmptyIndexed<DenseSymbolNode>()
    private var relationEdges = createEmptyIndexed<DenseRelationEdge>()
    private var updateStream = createEmptyIndexed<RealtimeUpdate>()
    private val evidenceBuilder = EvidenceChainBuilder()
    private var evidenceTrackedSymbols = createEmptyIndexed<EvidenceTrackedSymbol>()
    
    /**
     * Process a single kotlin symbol with evidence chain tracking
     */
    fun processSymbol(
        name: String,
        type: UShort,
        fileName: String,
        lineNumber: UShort,
        modifiers: UShort,
        evidenceSource: String = EvidenceSources.SOURCE_CODE,
        evidenceStrength: UByte = EvidenceStrengthLevels.STRONG
    ) {
        val symbolNode = createDenseSymbolNode(name, type, fileName, lineNumber, modifiers)
        val update = createRealtimeUpdate(symbolNode, System.currentTimeMillis())
        
        // Create evidence chain for this symbol
        val inferenceSteps = createEmptyIndexed<Join<String, Float>>()
            .add(InferenceSteps.PARSE_DECLARATION j 0.95f)
            .add(InferenceSteps.RESOLVE_TYPE j 0.90f)
        
        val evidenceChain = evidenceBuilder.buildChain(
            evidenceSource,
            evidenceStrength,
            inferenceSteps
        )
        
        val evidenceTrackedSymbol = symbolNode j evidenceChain
        
        symbolNodes = symbolNodes.add(symbolNode)
        updateStream = updateStream.add(update)
        evidenceTrackedSymbols = evidenceTrackedSymbols.add(evidenceTrackedSymbol)
    }
    
    /**
     * Process a relationship between symbols
     */
    fun processRelation(
        sourceSymbol: String,
        targetSymbol: String,
        relationType: UByte
    ) {
        val edge = createDenseRelationEdge(sourceSymbol, targetSymbol, relationType)
        relationEdges = relationEdges.add(edge)
    }
    
    /**
     * Get current dense bitgraph state for AI consumption
     */
    fun getCurrentBitgraph(): RealtimeDenseBitgraph {
        return (symbolNodes j relationEdges) j updateStream
    }
    
    /**
     * Get evidence-tracked symbols for inference analysis
     */
    fun getEvidenceTrackedSymbols(): EvidenceSymbolGraph {
        return evidenceTrackedSymbols
    }
    
    /**
     * Clear update stream after processing (keeps symbols/relations)
     */
    fun flushUpdates() {
        updateStream = createEmptyIndexed<RealtimeUpdate>()
    }
    
    /**
     * Get symbol count for metrics
     */
    fun getSymbolCount(): Int = symbolNodes.size
    
    /**
     * Get relation count for metrics
     */
    fun getRelationCount(): Int = relationEdges.size
    
    /**
     * Get pending update count
     */
    fun getPendingUpdateCount(): Int = updateStream.size
}