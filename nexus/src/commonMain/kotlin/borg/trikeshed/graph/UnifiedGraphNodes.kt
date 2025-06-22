package borg.trikeshed.graph

import borg.trikeshed.lib.*

/**
 * Unified Graph Node System for Nexus-Kotlin-Entity-Scanner-KSP Integration
 * 
 * This module defines the canonical TrikeShed-native graph node representations
 * that all analysis modules (nexus, kotlin-entity-scanner, ksp-processors) share.
 * 
 * Design principles:
 * - All types are TrikeShed value classes using @JvmInline 
 * - Use Join<A,B> instead of Pair<A,B>
 * - Use Series<T> for collections
 * - Zero-cost abstractions with compile-time optimization
 */

/**
 * Unique identifier for graph nodes
 */
@JvmInline
value class UnifiedNodeId(val id: String)

/**
 * Confidence score for analysis results (0-255 scale for performance)
 */
@JvmInline 
value class NodeConfidence(val confidence: UByte)

/**
 * Type classification for graph nodes
 */
@JvmInline
value class NodeType(val type: String)

/**
 * Metadata about the analysis context
 */
@JvmInline
value class AnalysisContext(val context: String)

/**
 * Core graph node representation
 * Join<Join<NodeId, NodeType>, NodeConfidence> provides:
 * - node.a.a = UnifiedNodeId
 * - node.a.b = NodeType  
 * - node.b = NodeConfidence
 */
typealias GraphNode = Join<Join<UnifiedNodeId, NodeType>, NodeConfidence>

/**
 * Collection of graph nodes using TrikeShed Series
 */
typealias GraphNodeSeries = Series<GraphNode>

/**
 * Dependency edge between two nodes
 */
typealias DependencyEdge = Join<UnifiedNodeId, UnifiedNodeId>

/**
 * Collection of dependency edges
 */
typealias DependencyGraph = Series<DependencyEdge>

/**
 * Analysis metadata with context information
 */
typealias AnalysisMetadata = Join<AnalysisContext, NodeConfidence>

/**
 * Unified analysis result combining nodes, dependencies, and metadata
 * Structure:
 * - result.a.a = GraphNodeSeries (the actual nodes)
 * - result.a.b = DependencyGraph (relationships between nodes)
 * - result.b = AnalysisMetadata (context and overall confidence)
 */
typealias UnifiedAnalysisResult = Join<Join<GraphNodeSeries, DependencyGraph>, AnalysisMetadata>

/**
 * Extension functions for working with GraphNode
 */
val GraphNode.nodeId: UnifiedNodeId get() = this.a.a
val GraphNode.nodeType: NodeType get() = this.a.b  
val GraphNode.confidence: NodeConfidence get() = this.b

/**
 * Extension functions for working with DependencyEdge
 */
val DependencyEdge.source: UnifiedNodeId get() = this.a
val DependencyEdge.target: UnifiedNodeId get() = this.b

/**
 * Extension functions for working with UnifiedAnalysisResult
 */
val UnifiedAnalysisResult.nodes: GraphNodeSeries get() = this.a.a
val UnifiedAnalysisResult.dependencies: DependencyGraph get() = this.a.b
val UnifiedAnalysisResult.metadata: AnalysisMetadata get() = this.b

/**
 * Utility functions for creating graph nodes
 */
fun createGraphNode(id: String, type: String, confidence: UByte): GraphNode {
    return (UnifiedNodeId(id) j NodeType(type)) j NodeConfidence(confidence)
}

fun createDependencyEdge(sourceId: String, targetId: String): DependencyEdge {
    return UnifiedNodeId(sourceId) j UnifiedNodeId(targetId)
}

fun createAnalysisResult(
    nodes: GraphNodeSeries,
    dependencies: DependencyGraph, 
    context: String,
    overallConfidence: UByte
): UnifiedAnalysisResult {
    val metadata = AnalysisContext(context) j NodeConfidence(overallConfidence)
    return (nodes j dependencies) j metadata
}

/**
 * Node type constants for consistency across modules
 */
object NodeTypes {
    const val CLASS = "class"
    const val FUNCTION = "function"
    const val PROPERTY = "property"
    const val INTERFACE = "interface"
    const val ENUM = "enum"
    const val ANNOTATION = "annotation"
    const val OBJECT = "object"
    const val COMPANION_OBJECT = "companion_object"
    const val CONSTRUCTOR = "constructor"
    const val PARAMETER = "parameter"
    const val TYPE_PARAMETER = "type_parameter"
    const val PACKAGE = "package"
    const val IMPORT = "import"
    const val FILE = "file"
    const val MODULE = "module"
}

/**
 * Analysis context constants
 */
object AnalysisContexts {
    const val NEXUS_AI = "nexus_ai"
    const val KOTLIN_ENTITY_SCANNER = "kotlin_entity_scanner"
    const val KSP_PROCESSOR = "ksp_processor"
    const val INTELLIJ_PSI = "intellij_psi"
    const val UNIFIED_ANALYSIS = "unified_analysis"
}

/**
 * Confidence level constants (0-255 scale)
 */
object ConfidenceLevels {
    const val VERY_LOW: UByte = 51u    // ~20%
    const val LOW: UByte = 102u        // ~40%  
    const val MEDIUM: UByte = 153u     // ~60%
    const val HIGH: UByte = 204u       // ~80%
    const val VERY_HIGH: UByte = 255u  // 100%
}