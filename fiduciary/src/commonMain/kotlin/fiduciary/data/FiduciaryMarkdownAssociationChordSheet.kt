package fiduciary.data

import borg.trikeshed.lib.*
import fiduciary.*
import kotlin.collections.*

/**
 * Fiduciary Markdown Association Chord Sheet
 *
 * Orchestrates the digestion and association of markdown documents with system concepts, components, and processing stages.
 * Declarative only: no implementation logic.
 */

data class FiduciaryMarkdownDocument(
    val docId: String,                // Unique identifier (e.g., filename)
    val title: String,                // Title or heading
    val content: String,              // Full markdown content
    val topics: Indexed<String> = Indexed(0) { "" }, // Extracted topics/keywords
    val associations: Indexed<String> = Indexed(0) { "" } // Related components, stages, or concepts
)

data class AssociatedConcepts(
    val components: Indexed<String>,  // System components referenced
    val pipelineStages: Indexed<String>, // Processing stages (e.g., extraction, analysis)
    val attentionFlows: Indexed<String>, // Attention/processing flows
    val efficiencyNotes: Indexed<String> // Efficiency or orchestration notes
)

// Mapping from markdown document to associated concepts
typealias MarkdownAssociationPlan = MetaSeries<FiduciaryMarkdownDocument, AssociatedConcepts>

object FiduciaryMarkdownAssociationChordSheet {
    // Orchestrates the mapping of markdowns to associated concepts and flows
    // Example: markdownDoc j { doc -> AssociatedConcepts(...) }
} 