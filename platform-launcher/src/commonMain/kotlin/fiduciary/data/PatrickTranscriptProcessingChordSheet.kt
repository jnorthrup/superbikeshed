package fiduciary.data

import borg.trikeshed.lib.*
import fiduciary.*
import kotlin.collections.*

/**
 * Patrick Transcript Processing Pipeline Chord Sheet
 *
 * Orchestrates the actual processing pipeline for Patrick Devine transcripts.
 * Declarative sequence: ZIP Central Directory → Transcript Filtering → Batch Processing → Cataloging
 * No implementation logic, only orchestration.
 */

data class ZIPCentralDirectory(
    val archiveUrl: String,                 // Archive.org URL
    val entries: Indexed<ZIPEntry>,         // All ZIP entries
    val totalSize: Long,                    // Total archive size
    val metadata: Map<String, String> = emptyMap()
)

data class ZIPEntry(
    val name: String,                       // Entry name/path
    val offset: Long,                       // Byte offset in ZIP
    val compressedSize: Long,               // Compressed size
    val uncompressedSize: Long,             // Uncompressed size
    val mimeType: String? = null,           // Detected MIME type
    val metadata: Map<String, String> = emptyMap()
)

data class TranscriptFilterCriteria(
    val includeExtensions: Indexed<String> = Indexed(0) { "" }, // .txt, .transcript, etc.
    val excludePatterns: Indexed<String> = Indexed(0) { "" },   // Patterns to exclude
    val minSize: Long = 0L,                 // Minimum file size
    val maxSize: Long = Long.MAX_VALUE,     // Maximum file size
    val namePatterns: Indexed<String> = Indexed(0) { "" }       // Name patterns to include
)

data class BatchProcessingStage(
    val stageId: String,                    // Unique stage identifier
    val stageType: String,                  // extraction, nlp_tagging, cataloging, etc.
    val inputEntries: Indexed<PatrickTranscriptEntry>,
    val outputEntries: Indexed<Any>? = null, // Output depends on stage type
    val status: String = "pending",         // pending, running, complete, failed
    val metadata: Map<String, String> = emptyMap()
)

// Declarative orchestration mappings
typealias ZIPDirectoryPlan = MetaSeries<String, ZIPCentralDirectory>
typealias TranscriptFilterPlan = MetaSeries<Join<ZIPCentralDirectory, TranscriptFilterCriteria>, Indexed<PatrickTranscriptEntry>>
typealias BatchProcessingPlan = MetaSeries<Indexed<PatrickTranscriptEntry>, Indexed<BatchProcessingStage>>

object PatrickTranscriptProcessingChordSheet {
    // Orchestrates the full processing pipeline
    // ZIP Central Directory → Transcript Filtering → Batch Processing → Cataloging
    // Example: \1 j { \2: Int -> readZIPDirectory(url) }
    // Example: (zipDir, \1 j { \2: Int -> filterTranscripts(dir, criteria) }
    // Example: \1 j { \2: Int -> createBatchStages(entries) }
} 