package fiduciary.data

import borg.trikeshed.lib.*
import fiduciary.*
import kotlin.collections.*

/**
 * Blackboard Lattice Chord Sheet
 *
 * Orchestrates the optimization of a blackboard lattice over a graph of document tokens,
 * where the source is attention spent on sources (e.g., fragments/ranges from online indexes).
 *
 * Declarative only: no implementation logic, just type structure and relationships.
 */

// Represents a unit of attention spent on a source (e.g., fragment, range, file)
data class AttentionSource(
    val sourceId: String,                // Unique identifier for the source
    val fragmentRange: Twin<Long>,       // Byte range attended to (start j end)
    val metadata: Map<String, String> = emptyMap() // Optional metadata
)

// Represents a token extracted from an attention source
data class DocumentToken(
    val token: String,                   // The token text
    val docId: String,                   // Document or source identifier
    val position: Int,                   // Position within the document/source
    val metadata: Map<String, String> = emptyMap() // Optional metadata
)

// Graph of document tokens: nodes are tokens, edges are relationships
data class DocumentTokenEdge(
    val from: DocumentToken,
    val to: DocumentToken,
    val relation: String = "cooccurrence", // Relationship type (e.g., cooccurrence, semantic)
    val weight: Double = 1.0
)

data class DocumentTokenGraph(
    val nodes: Indexed<DocumentToken>,
    val edges: Indexed<DocumentTokenEdge>
)

// Blackboard lattice: evolving state of collaborative optimization over the token graph
data class BlackboardLattice(
    val states: Indexed<Map<String, Any>>, // Partial solutions, hypotheses, or optimization states
    val currentGraph: DocumentTokenGraph
)

/**
 * NOTE: This orchestration is deferred, persistent, and asynchronous.
 * Latencies are long, persistence is required, and results are eventual—not suitable for interactive or real-time use.
 */
data class BatchPass(
    val passId: String,                        // Unique identifier for the batch pass
    val persistenceId: String,                 // Persistence/storage identifier
    val timestamp: Long,                       // When the batch pass was initiated
    val attentionAllocations: Indexed<AttentionSource>, // Attention spent in this pass
    val actions: Indexed<AttentionAction>,     // Actions taken with attention
    val status: String = "pending"            // Status: pending, running, complete, failed, etc.
)

/**
 * Efficiency strategy for optimizing batch blackboard lattice operation.
 * Declarative only: describes the approach, not the implementation.
 */
data class EfficiencyStrategy(
    val strategyId: String,                    // Unique identifier for the strategy
    val description: String,                   // Human-readable description
    val parameters: Map<String, Any> = emptyMap() // Tunable parameters
)

data class EfficiencyMetric(
    val metricId: String,                      // Unique identifier for the metric
    val value: Double,                         // Measured or estimated value
    val unit: String = "unitless",            // Unit of measurement
    val metadata: Map<String, String> = emptyMap()
)

// Mapping from BatchPass to EfficiencyStrategy (declarative, not implementation)
typealias BatchEfficiencyPlan = MetaSeries<BatchPass, EfficiencyStrategy>

/**
 * Chord sheet object: orchestrates the flow from attention to tokens to graph to lattice
 */
object BlackboardLatticeChordSheet {
    // Declarative orchestration only
    // AttentionSource → DocumentToken (via tokenization)
    // DocumentToken → DocumentTokenGraph (as nodes/edges)
    // DocumentTokenGraph → BlackboardLattice (as optimization substrate)
}

object BlackboardLatticeEfficiencyChordSheet {
    // Orchestrates the mapping of batch passes to efficiency strategies and metrics
    // Example: batchPass j { pass -> EfficiencyStrategy(...) }
} 