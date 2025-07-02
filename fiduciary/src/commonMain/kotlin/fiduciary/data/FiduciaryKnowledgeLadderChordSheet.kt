package fiduciary.data

import borg.trikeshed.lib.*
import fiduciary.*
import fiduciary.data.*
import kotlin.collections.*

/**
 * Fiduciary Knowledge Ladder Chord Sheet
 *
 * Consolidates markdown association, blackboard lattice, batch, and efficiency orchestration.
 * Models the Jacob's Ladder of CCEK handoffs as a declarative sequence:
 *   Markdown → Concepts → Tokens → Graph → BatchPass → Efficiency
 * Declarative only: no implementation logic.
 */

data class FiduciaryMarkdownDocument(
    val docId: String,
    val title: String,
    val content: String,
    val topics: Indexed<String> = Indexed(0) { "" },
    val associations: Indexed<String> = Indexed(0) { "" }
)

data class AssociatedConcepts(
    val components: Indexed<String>,
    val pipelineStages: Indexed<String>,
    val attentionFlows: Indexed<String>,
    val efficiencyNotes: Indexed<String>
)

data class AttentionSource(
    val sourceId: String,
    val fragmentRange: Twin<Long>,
    val metadata: Map<String, String> = emptyMap()
)

data class DocumentToken(
    val token: String,
    val docId: String,
    val position: Int,
    val metadata: Map<String, String> = emptyMap()
)

data class DocumentTokenEdge(
    val from: DocumentToken,
    val to: DocumentToken,
    val relation: String = "cooccurrence",
    val weight: Double = 1.0
)

data class DocumentTokenGraph(
    val nodes: Indexed<DocumentToken>,
    val edges: Indexed<DocumentTokenEdge>
)

data class BlackboardLattice(
    val states: Indexed<Map<String, Any>>,
    val currentGraph: DocumentTokenGraph
)

data class BatchPass(
    val passId: String,
    val persistenceId: String,
    val timestamp: Long,
    val attentionAllocations: Indexed<AttentionSource>,
    val actions: Indexed<AttentionAction>,
    val status: String = "pending"
)

data class EfficiencyStrategy(
    val strategyId: String,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
)

data class EfficiencyMetric(
    val metricId: String,
    val value: Double,
    val unit: String = "unitless",
    val metadata: Map<String, String> = emptyMap()
)

data class AttentionAction(
    val action: String,
    val parameters: Map<String, Any> = emptyMap()
)

typealias MarkdownAssociationPlan = MetaSeries<FiduciaryMarkdownDocument, AssociatedConcepts>
typealias TokenizationPlan = MetaSeries<FiduciaryMarkdownDocument, Indexed<DocumentToken>>
typealias TokenGraphPlan = MetaSeries<Indexed<DocumentToken>, DocumentTokenGraph>
typealias LatticePlan = MetaSeries<DocumentTokenGraph, BlackboardLattice>
typealias BatchEfficiencyPlan = MetaSeries<BatchPass, EfficiencyStrategy>

data class KnowledgeLadder(
    val markdowns: Indexed<FiduciaryMarkdownDocument>,
    val associations: MarkdownAssociationPlan,
    val tokenizations: TokenizationPlan,
    val tokenGraphs: TokenGraphPlan,
    val lattices: LatticePlan,
    val batchPasses: Indexed<BatchPass>,
    val efficiencyPlans: BatchEfficiencyPlan
)

object FiduciaryKnowledgeLadderChordSheet {
    // Declarative orchestration of the full knowledge ladder
    // Jacob's Ladder of CCEK handoffs: Markdown → Concepts → Tokens → Graph → Lattice → BatchPass → Efficiency
} 