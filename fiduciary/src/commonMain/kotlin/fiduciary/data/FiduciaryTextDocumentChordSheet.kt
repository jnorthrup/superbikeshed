package fiduciary.data

import borg.trikeshed.lib.*
import fiduciary.*
import kotlin.collections.*

/**
 * Fiduciary Text Document Chord Sheet
 *
 * Orchestrates the reduction from archive.org ZIP file index fragment range requests
 * to a collection of text documents for fiduciary analysis.
 *
 * This is a declarative specification: no implementation logic, only field and relationship mapping.
 */

// Normalized attention for ZIP fragment (start j end)
typealias ZipFragmentRange = Twin<Long>

// Data class representing a text document sourced from archive.org ZIP fragment
// (Fields are declarative, not implementation)
data class FiduciaryTextDocument(
    val archiveId: String,           // archive.org identifier
    val zipFile: String,             // ZIP file name or path
    val fragmentRange: ZipFragmentRange, // Byte range in ZIP (start j end)
    val content: String? = null,     // Extracted text (optional, may be loaded on demand)
    val metadata: Map<String, String> = emptyMap(), // Optional metadata
    val interest: Double = 0.0
)

// Indexed collection of fiduciary text documents
typealias FiduciaryTextDocumentCollection = Indexed<FiduciaryTextDocument>

/**
 * Chord sheet object: orchestrates the mapping from ZIP fragment to text document
 */
object FiduciaryTextDocumentChordSheet {
    // Declarative mapping: ZIP fragment (archiveId, zipFile, fragmentRange) → FiduciaryTextDocument
    // No implementation logic, only orchestration
    // Example: (archiveId, zipFile, fragmentRange) j { (id, zip, range) -> FiduciaryTextDocument(id, zip, range) }

    // Collection orchestration: Indexed<FiduciaryTextDocument>
    // Example: documents: Indexed<FiduciaryTextDocument>
} 