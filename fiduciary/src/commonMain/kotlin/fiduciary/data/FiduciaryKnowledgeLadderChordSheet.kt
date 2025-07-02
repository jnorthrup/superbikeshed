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

// Interest and Attention
data class Interest(
    val interestId: String,
    val source: String, // e.g., "FiduciaryMarkdownDocument", "Kline"
    val target: String, // e.g., docId, kline timestamp
    val weight: Double = 1.0
)

data class Attention(
    val attentionId: String,
    val interestId: String,
    val allocation: Double, // e.g., 0.5 (50% of attention)
    val metadata: Map<String, Any> = emptyMap()
)

// ta4k Integration
@JvmInline value class Price(val value: Double)
@JvmInline value class Volume(val value: Double)
@JvmInline value class UnixTimestamp(val millis: Long)

data class OHLC(
    val open: Price,
    val high: Price,
    val low: Price,
    val close: Price
)

data class Kline(
    val timestamp: UnixTimestamp,
    val ohlc: OHLC,
    val volume: Volume
)

data class BarSeries(
    val id: String,
    val bars: Indexed<Kline>
)

data class Indicator(
    val id: String,
    val barSeriesId: String,
    val parameters: Map<String, Any>,
    val values: Indexed<Double>
)

data class TimeSeriesInterest(
    val interestId: String,
    val barSeriesId: String,
    val range: Twin<Long>? = null, // Optional range of timestamps
    val indicatorId: String? = null, // Optional interest in a specific indicator
    val weight: Double = 1.0
)

data class KlineAttention(
    val attentionId: String,
    val interestId: String,
    val allocation: Double,
    val metadata: Map<String, Any> = emptyMap()
)


typealias MarkdownAssociationPlan = MetaSeries<FiduciaryMarkdownDocument, AssociatedConcepts>
typealias TokenizationPlan = MetaSeries<FiduciaryMarkdownDocument, Indexed<DocumentToken>>
typealias TokenGraphPlan = MetaSeries<Indexed<DocumentToken>, DocumentTokenGraph>
typealias LatticePlan = MetaSeries<DocumentTokenGraph, BlackboardLattice>
typealias BatchEfficiencyPlan = MetaSeries<BatchPass, EfficiencyStrategy>
typealias InterestPlan = MetaSeries<Any, Interest>
typealias AttentionAllocationPlan = MetaSeries<Interest, Attention>
typealias IndicatorCalculationPlan = MetaSeries<BarSeries, Indicator>
typealias SignalGenerationPlan = MetaSeries<Indicator, Any>


data class KnowledgeLadder(
    val markdowns: Indexed<FiduciaryMarkdownDocument>,
    val associations: MarkdownAssociationPlan,
    val tokenizations: TokenizationPlan,
    val tokenGraphs: TokenGraphPlan,
    val lattices: LatticePlan,
    val batchPasses: Indexed<BatchPass>,
    val efficiencyPlans: BatchEfficiencyPlan,
    val interests: InterestPlan,
    val attentionAllocations: AttentionAllocationPlan,
    val barSeries: Indexed<BarSeries>,
    val indicatorCalculations: IndicatorCalculationPlan,
    val signalGenerations: SignalGenerationPlan
)

object FiduciaryKnowledgeLadderChordSheet {
    // Declarative orchestration of the full knowledge ladder
    // Jacob's Ladder of CCEK handoffs: Markdown → Concepts → Tokens → Graph → Lattice → BatchPass → Efficiency
}  