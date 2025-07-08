package fiduciary.data

import borg.trikeshed.lib.*
import fiduciary.*
import kotlin.collections.*

/**
 * Patrick Transcript Cataloging & NLP Tagging Chord Sheet
 *
 * Orchestrates the cataloging and NLP tagging of Patrick Devine text transcripts from ZIP files.
 * Declarative sequence: ZIP Entry → Transcript → NLP Tags → Catalog Entry
 * No implementation logic, only orchestration.
 */

data class PatrickTranscriptEntry(
    val entryId: String,                    // Unique identifier for the transcript entry
    val zipPath: String,                    // Path within the ZIP file
    val fragmentRange: Twin<Long>,          // Byte range in ZIP (start j end)
    val transcriptText: String? = null,     // Extracted text (optional, may be loaded on demand)
    val metadata: Map<String, String> = emptyMap()
)

data class NLPTag(
    val tagId: String,                      // Unique identifier for the tag
    val tagType: String,                    // Type: entity, concept, relation, sentiment, etc.
    val tagValue: String,                   // The actual tag value
    val confidence: Double = 1.0,           // Confidence score
    val position: Twin<Int>? = null,        // Position in text (start j end)
    val metadata: Map<String, String> = emptyMap()
)

data class CatalogEntry(
    val catalogId: String,                  // Unique catalog identifier
    val transcriptEntry: PatrickTranscriptEntry,
    val nlpTags: Indexed<NLPTag>,
    val summary: String? = null,            // Optional summary
    val timestamp: Long,                    // When cataloged
    val metadata: Map<String, String> = emptyMap()
)

// Declarative orchestration mappings
typealias TranscriptExtractionPlan = MetaSeries<PatrickTranscriptEntry, String>
typealias NLPTaggingPlan = MetaSeries<String, Indexed<NLPTag>>
typealias CatalogingPlan = MetaSeries<Join<PatrickTranscriptEntry, Indexed<NLPTag>>, CatalogEntry>

object PatrickTranscriptChordSheet {
    // Orchestrates the full pipeline: ZIP Entry → Transcript → NLP Tags → Catalog Entry
    // Example: transcriptEntry j { entry -> extractTranscript(entry) }
    // Example: transcriptText j { text -> tagWithNLP(text) }
    // Example: (transcriptEntry, nlpTags) j { (entry, tags) -> createCatalogEntry(entry, tags) }
} 