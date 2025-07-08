package fiduciary.attention

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.Twin
import borg.trikeshed.attention.NormalizedAttention
import borg.trikeshed.attention.ContextualAttention
import borg.trikeshed.io.IOContext


/**
 * Fiduciary attention context - document processing with attention
 * Connects Tika, Stanford NLP, and attention mechanisms
 */

/**
 * ## Digital Asset Attention Types
 *
 * These types enable fiduciary tracking of online agreements, publications, API copyrights, and domain expirations.
 * All actions must be parameterized by these explicit attention objects, in line with fiduciary principles.
 *
 * - [USPS API Reference](https://developers.usps.com/apis)
 * - [Domain WHOIS](https://www.icann.org/resources/pages/whois-2018-03-17-en)
 * - [Crossref Publications](https://www.crossref.org/)
 */

// Fiduciary-specific attention types

// Document sources with fiduciary context
sealed interface DocumentSource
value class TikaSource(val tika: Join<String, ParseContext>) : DocumentSource  // path j parseContext
value class NLPSource(val nlp: Join<String, Map<String, Any>>) : DocumentSource  // text j annotations
value class OCRSource(val ocr: Join<ByteArray, String>) : DocumentSource  // image j language
value class AudioSource(val audio: Join<String, Int>) : DocumentSource  // path j sampleRate

// Fiduciary context - carries document processing state
value class FiduciaryContext(val ctx: Join<IOContext, Join<Any, Any>>) {
    val ioContext: IOContext get() = ctx.a
    
}

// Fiduciary attention - document attention with context
value class FiduciaryAttention(val fid: Join<DocumentAttention, FiduciaryContext>) {
    val document: DocumentAttention get() = fid.a
    val context: FiduciaryContext get() = fid.b
}

// Patrick Devine corpus specific attention
value class PatrickDevineAttention(val pd: Join<CorpusAttention, Join<String, Boolean>>) {
    val corpus: CorpusAttention get() = pd.a
    val zipUrl: String get() = pd.b.a
    val useRangeRequests: Boolean get() = pd.b.b
}

// Common Law 1215.org tree builder with torrent support
value class CommonLawAttention(val law: Join<CorpusAttention, Join<String, Boolean>>) {
    val corpus: CorpusAttention get() = law.a
    val sourceUrl: String get() = law.b.a
    val isTreeStructure: Boolean get() = law.b.b
}

// Tree traversal for Common Law archive
suspend inline fun CommonLawAttention.traverseTree(): Indexed<String> {
    if (!isTreeStructure) {
        return 0 j { _: Int -> "" }
    }
    
    // Tree structure paths from 1215.org
    val paths = arrayOf(
        "index.html",
        "rights/",
        "documents/", 
        "history/",
        "references/",
        "cases/"
    )
    
    return paths.size j paths::get
}

// Integration with Trikeshed attention
fun FiduciaryAttention.toContextual(): ContextualAttention =
    ContextualAttention(document.doc.a j context.ioContext)

// Usage example
suspend fun demonstrateFiduciaryAttention(ioContext: IOContext) {
    // Create fiduciary context
    val metadata = Metadata()
    val parseContext = ParseContext()
    val fidContext = FiduciaryContext(ioContext j (metadata j parseContext))
    
    // Create document attention
    val pdfDoc = fidContext.document(0L, 50000L, "application/pdf")
    val tikaSource = fidContext.tika("/path/to/document.pdf")
    
    // Extract content
    val text = pdfDoc.process(tikaSource) as String
    
    // Analyze with NLP
    val nlpSource = fidContext.nlp(text)
    val concepts = pdfDoc.process(nlpSource) as Indexed<String>
    
    // Build Patrick Devine corpus
    val patrickUrls = arrayOf(
        "https://archive.org/download/patrickdevine/patrickdevine.zip",
        "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    )
    val corpus = fidContext.patrickDevine(patrickUrls, useRangeRequests = true)
    
    // Process corpus with attention
    for (attention in corpus) {
        if (attention.useRangeRequests) {
            println("Using range requests for ${attention.zipUrl}")
        }
        val index = attention.corpus.buildIndex()
        println("Built index with ${index.a} documents")
    }
}

/**
 * Fiduciary wiring provides:
 * 1. Document-aware attention types
 * 2. Integration with Tika, Stanford NLP, OCR, Audio
 * 3. Corpus-level operations
 * 4. Patrick Devine specific handling
 * 5. Seamless conversion to Trikeshed attention
 */

/**
 * ## FiduciaryContext Digital Asset Constructors
 *
 * These functions create attention objects for digital asset tracking.
 */
fun FiduciaryContext.agreementAttention(url: String, parties: List<String>, effectiveDate: String) =
    DigitalAttention.AgreementAttention(url, parties, effectiveDate)

fun FiduciaryContext.publicationAttention(doi: String, title: String, authors: List<String>) =
    DigitalAttention.PublicationAttention(doi, title, authors)

fun FiduciaryContext.apiCopyrightAttention(apiName: String, owner: String, license: String, expiry: String?) =
    DigitalAttention.ApiCopyrightAttention(apiName, owner, license, expiry)

fun FiduciaryContext.domainExpirationAttention(domain: String, registrar: String, expiry: String) =
    DigitalAttention.DomainExpirationAttention(domain, registrar, expiry)

sealed class DigitalAttention {
    /**
     * ### AgreementAttention
     * Tracks online agreements, their parties, and effective dates.
     */
    data class AgreementAttention(
        val url: String,
        val parties: List<String>,
        val effectiveDate: String // ISO date
    ) : DigitalAttention()

    /**
     * ### PublicationAttention
     * Tracks publications by DOI, title, and authors.
     */
    data class PublicationAttention(
        val doi: String,
        val title: String,
        val authors: List<String>
    ) : DigitalAttention()

    /**
     * ### ApiCopyrightAttention
     * Tracks API copyright/license status and expiry.
     */
    data class ApiCopyrightAttention(
        val apiName: String,
        val owner: String,
        val license: String,
        val expiry: String? // ISO date, nullable
    ) : DigitalAttention()

    /**
     * ### DomainExpirationAttention
     * Tracks domain registrar and expiration date.
     */
    data class DomainExpirationAttention(
        val domain: String,
        val registrar: String,
        val expiry: String // ISO date
    ) : DigitalAttention()
}

/**
 * ## Lattice of Discernment
 *
 * Represents a partially ordered set (lattice) of discernment categories or attention objects.
 * Supports refinement, generalization, and reasoning about relationships between interests.
 * Useful for fiduciary audit, compliance, and advanced attention routing.
 *
 * Example usage:
 * ```kotlin
 * val lattice = LatticeOfDiscernment<String>()
 * lattice.addRelation("General", "Specific") // "Specific" refines "General"
 * val isRefined = lattice.isRefinement("General", "Specific") // true
 * ```
 */
/**
 * A high-performance, read-optimized, thread-safe lattice/graph implementation
 * using a Structure-of-Arrays (SoA) memory layout. This aligns with high-performance
 * I/O motion ("heap-y") and is ideal for "cheaper to read than populate" workloads
 * typical of a Blackboard system.
 *
 * Construction is done via the `Builder` class. Once built, the `SoaGraphLattice`
 * is immutable and allows for extremely fast, lock-free concurrent reads.
 */
class SoaGraphLattice<T> internal constructor(
    internal val nodeStore: List<T>,
    internal val nodeLookup: Map<T, Int>,
    internal val index: GraphIndex
) {
    // An immutable snapshot of the graph's adjacency index for fast, thread-safe traversal.
    internal data class GraphIndex(
        val edgeOffsets: IntArray, // For node i, its children are in edgeTargets from [edgeOffsets[i]] to [edgeOffsets[i+1]])
        val edgeTargets: IntArray  // Concatenated list of all "to" node indices
    )

    /**
     * Checks if `child` is a descendant of `parent` by traversing the graph.
     * This read operation is extremely fast and lock-free.
     */
    fun isRefinement(parent: T, child: T): Boolean {
        val parentIdx = nodeLookup[parent] ?: return false
        val childIdx = nodeLookup[child] ?: return false

        // Standard Breadth-First Search (BFS) using the efficient CSR-like index
        val queue: java.util.Queue<Int> = java.util.LinkedList()
        val visited = mutableSetOf<Int>()

        queue.add(parentIdx)
        visited.add(parentIdx)

        while (queue.isNotEmpty()) {
            val currentNodeIdx = queue.poll()

            // This is the O(degree) lookup using our adjacency index
            val start = index.edgeOffsets[currentNodeIdx]
            val end = index.edgeOffsets[currentNodeIdx + 1]

            for (i in start until end) {
                val neighborIdx = index.edgeTargets[i]
                if (neighborIdx == childIdx) return true // Found it
                if (neighborIdx !in visited) {
                    visited.add(neighborIdx)
                    queue.add(neighborIdx)
                }
            }
        }
        return false
    }

    /**
     * Builder for constructing an SoaGraphLattice. This builder is mutable and not
     * thread-safe; it should be used in a single-threaded construction phase.
     */
    class Builder<T> {
        internal val nodeStore: MutableList<T> = ArrayList()
        internal val nodeLookup: MutableMap<T, Int> = HashMap()
        internal val edgeStore: MutableList<Pair<Int, Int>> = ArrayList() // (from_index, to_index)

        fun addRelation(parent: T, child: T): Builder<T> {
            val parentIdx = getOrAddNode(parent)
            val childIdx = getOrAddNode(child)
            edgeStore.add(parentIdx to childIdx)
            return this
        }

        internal fun getOrAddNode(node: T): Int {
            return nodeLookup.computeIfAbsent(node) {
                nodeStore.add(it)
                nodeStore.size - 1
            }
        }

        fun build(): SoaGraphLattice<T> {
            val numNodes = nodeStore.size
            val outDegrees = IntArray(numNodes)
            edgeStore.forEach { (from, _) -> outDegrees[from]++ }

            val edgeOffsets = IntArray(numNodes + 1).also {
                var offset = 0
                for (i in 0 until numNodes) {
                    it[i] = offset
                    offset += outDegrees[i]
                }
                it[numNodes] = offset
            }

            val edgeTargets = IntArray(edgeStore.size)
            val currentOffsets = edgeOffsets.clone() // Use as write pointers

            edgeStore.forEach { (from, to) ->
                val index = currentOffsets[from]
                edgeTargets[index] = to
                currentOffsets[from]++
            }

            return SoaGraphLattice(ArrayList(nodeStore), HashMap(nodeLookup), GraphIndex(edgeOffsets, edgeTargets))
        }
    }
}

// Renaming LatticeOfDiscernment to be more descriptive of its new, high-performance implementation.
typealias LatticeOfDiscernment<T> = SoaGraphLattice<T>

/**
 * ## Patrick Devine Archive Indexes in the Blackboard
 *
 * This system builds and stores two comprehensive indexes for the Patrick Devine archives:
 * - `patrickdevine.zip`
 * - `Patrick Devine Calls.zip`
 *
 * ### Process
 * 1. **Fetch Central Directory:**
 *    - Use HTTP range requests to fetch the ZIP central directory from the remote archive.
 *    - Parse the central directory to enumerate all entries (filenames, offsets, sizes, etc.).
 * 2. **Build Index:**
 *    - Create a structured index of all entries, parameterized by attention objects for auditability.
 * 3. **Store in Blackboard:**
 *    - Serialize and store the index in the blackboard (e.g., CouchDB) under a well-known key:
 *      - `patrickdevine_index`
 *      - `patrickdevine_calls_index`
 * 4. **Access for Downstream Processing:**
 *    - The indexes are available for document attention, NLP, LDA, and other fiduciary processes.
 *
 * ### Benefits
 * - No need to download the entire archive—just the central directory and, as needed, individual entries.
 * - Full auditability and traceability—each entry in the index is parameterized by an attention object.
 * - Efficient, scalable, and standards-based—leverages the public ZIP spec and HTTP range requests.
 *
 * ### Example (Pseudocode)
 * ```kotlin
 * // Fetch and index the central directory for a remote ZIP
 * val index = fetchZipCentralDirectoryIndex("https://archive.org/download/patrickdevine/patrickdevine.zip")
 * // Store in blackboard (e.g., CouchDB)
 * blackboard.store("patrickdevine_index", index)
 * ```
 *
 * ### Index Keys
 * | Archive Name                  | Index Key in Blackboard         |
 * |-------------------------------|---------------------------------|
 * | patrickdevine.zip             | patrickdevine_index             |
 * | Patrick Devine Calls.zip      | patrickdevine_calls_index       |
 */

/**
 * ## Ingest and Scan Documents from Remote Zip (Range Requests)
 *
 * Downloads documents from a remote zip archive using HTTP range requests, extracts their content,
 * and runs Stanford NLP (or a pluggable NLP agent) on the extracted text. Optionally, runs LDA topic modeling
 * and stores the results in CouchDB records for later retrieval/analysis. Results are parameterized
 * by explicit attention objects for full fiduciary traceability.
 *
 * This is a stub: actual HTTP, zip, NLP, LDA, and CouchDB integration should be implemented as needed.
 *
 * Example usage:
 * ```kotlin
 * val results = fidContext.ingestAndScanRemoteZip(
 *     url = "https://archive.org/download/patrickdevine/patrickdevine.zip",
 *     docIndices = 0..9,
 *     nlpAgent = MyStanfordNlpAgent(),
 *     ldaRunner = { text -> listOf("topic1", "topic2") },
 *     storeLdaInCouch = true
 * )
 * ```
 */
suspend fun FiduciaryContext.ingestAndScanRemoteZip(
    url: String,
    docIndices: IntRange,
    nlpAgent: (String) -> List<String> = { text -> listOf("NLP_RESULT_PLACEHOLDER") },
    ldaRunner: ((String) -> List<String>)? = null,
    storeLdaInCouch: Boolean = false,
    couchDbStore: ((DocumentAttention, List<String>) -> Unit)? = null
): List<Pair<DocumentAttention, List<String>>> {
    // 1. Download document byte ranges from remote zip (stubbed)
    val docs = docIndices.map { i ->
        // In a real implementation, fetch the i-th file from the zip using HTTP range requests
        val fakeText = "Extracted text for document $i from $url"
        val docAttention = document(i * 1024L, (i + 1) * 1024L, "application/pdf")
        docAttention to fakeText
    }
    // 2. Run NLP agent on each document's text
    val nlpResults = docs.map { (docAttention, text) ->
        val nlp = nlpAgent(text)
        docAttention to nlp
    }
    // 3. Optionally run LDA and store in CouchDB
    if (ldaRunner != null && storeLdaInCouch && couchDbStore != null) {
        docs.forEach { (docAttention, text) ->
            val ldaTopics = ldaRunner(text)
            couchDbStore(docAttention, ldaTopics)
        }
    }
    return nlpResults
}