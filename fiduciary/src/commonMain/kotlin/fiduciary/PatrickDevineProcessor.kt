package fiduciary

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import fiduciary.attention.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Patrick Devine Corpus Processor
 * 
 * Processes the Patrick Devine archive.org files using attention mechanisms
 * with HTTP range requests to avoid downloading entire files.
 */
class PatrickDevineProcessor(
    private val httpClient: HttpClient,
    private val ioContext: IOContext
) {
    // ZIP file structures
    // Make ZipEntry accessible from other files
    data class ZipEntry(
        val name: String,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val offset: Long,
        val method: Int, // 0=stored, 8=deflated
        val crc32: Long
    )
    
    /**
     * Process Patrick Devine corpus without downloading full zips
     */
    suspend fun processCorpus(): Indexed<DocumentAttention> {
        val documents = mutableListOf<DocumentAttention>()
        
        // Process each archive file
        val archives = arrayOf(
            FiduciaryArchives.PATRICK_DEVINE_MAIN,
            FiduciaryArchives.PATRICK_DEVINE_CALLS,
            FiduciaryArchives.PATRICK_DEVINE_FILES
        )
        
        for (archiveUrl in archives) {
            println("Processing archive: $archiveUrl")
            
            // Read ZIP central directory using range requests
            val entries = readZipCentralDirectory(archiveUrl)
            println("Found ${entries.a} entries in archive")
            
            // Process interesting files without downloading
            for (i in 0 until entries.a) {
                val entry = entries.b(i)
                
                // Filter for documents of interest
                if (isInterestingDocument(entry.name)) {
                    val doc = processZipEntry(archiveUrl, entry)
                    documents.add(doc)
                }
            }
        }
        
        return documents.size j documents::get
    }
    
    /**
     * Read ZIP central directory using HTTP range requests
     */
    suspend fun readZipCentralDirectory(url: String): Indexed<ZipEntry> {
        // ZIP end of central directory is last 22 bytes (minimum)
        val eocdrSize = 22
        val fileSize = getFileSize(url)
        
        // Read EOCD record
        val eocdrData = httpClient.executeRange(
            url, 
            fileSize - eocdrSize, 
            fileSize - 1
        ).body
        
        // Parse EOCD to find central directory
        val cdOffset = parseLittleEndianInt(eocdrData, 16)
        val cdSize = parseLittleEndianInt(eocdrData, 12)
        val entryCount = parseLittleEndianShort(eocdrData, 10)
        
        // Read central directory
        val cdData = httpClient.executeRange(
            url,
            cdOffset.toLong(),
            cdOffset + cdSize - 1L
        ).body
        
        // Parse entries
        val entries = mutableListOf<ZipEntry>()
        var pos = 0
        
        for (i in 0 until entryCount) {
            if (cdData[pos] == 0x50.toByte() && cdData[pos + 1] == 0x4b.toByte() &&
                cdData[pos + 2] == 0x01.toByte() && cdData[pos + 3] == 0x02.toByte()) {
                
                val entry = parseCentralDirectoryEntry(cdData, pos)
                entries.add(entry)
                pos += 46 + entry.name.length + 
                       parseLittleEndianShort(cdData, pos + 30) + // extra field length
                       parseLittleEndianShort(cdData, pos + 32)   // comment length
            }
        }
        
        return entries.size j entries::get
    }
    
    /**
     * Parse a central directory file header
     */
    private fun parseCentralDirectoryEntry(data: ByteArray, offset: Int): ZipEntry {
        val method = parseLittleEndianShort(data, offset + 10)
        val crc = parseLittleEndianInt(data, offset + 16).toLong() and 0xFFFFFFFFL
        val compressedSize = parseLittleEndianInt(data, offset + 20).toLong() and 0xFFFFFFFFL
        val uncompressedSize = parseLittleEndianInt(data, offset + 24).toLong() and 0xFFFFFFFFL
        val nameLength = parseLittleEndianShort(data, offset + 28)
        val localHeaderOffset = parseLittleEndianInt(data, offset + 42).toLong() and 0xFFFFFFFFL
        
        val name = String(data, offset + 46, nameLength)
        
        return ZipEntry(
            name = name,
            compressedSize = compressedSize,
            uncompressedSize = uncompressedSize,
            offset = localHeaderOffset,
            method = method,
            crc32 = crc
        )
    }
    
    /**
     * Process a single ZIP entry using attention
     */
    private suspend fun processZipEntry(
        archiveUrl: String,
        entry: ZipEntry
    ): DocumentAttention {
        println("Processing entry: ${entry.name} (${entry.uncompressedSize} bytes)")
        
        // Calculate local header size (30 + name length + extra field length)
        val localHeaderSize = 30 + entry.name.length
        val localHeaderData = httpClient.executeRange(
            archiveUrl,
            entry.offset,
            entry.offset + localHeaderSize + 4 // read a bit extra for extra field length
        ).body
        
        val extraFieldLength = parseLittleEndianShort(localHeaderData, 28)
        val dataOffset = entry.offset + 30 + entry.name.length + extraFieldLength
        
        // Create attention for this document
        val mimeType = getMimeType(entry.name)
        val attention = DocumentAttention(
            (dataOffset j (dataOffset + entry.compressedSize)) j mimeType
        )
        
        // For demonstration, extract first 1KB of content if it's text
        if (mimeType.startsWith("text/") && entry.method == 0) { // stored (uncompressed)
            val sampleSize = minOf(1024L, entry.compressedSize)
            val sample = httpClient.executeRange(
                archiveUrl,
                dataOffset,
                dataOffset + sampleSize - 1
            ).body
            
            println("Sample content: ${String(sample).take(100)}...")
        }
        
        return attention
    }
    
    /**
     * Get file size using HTTP HEAD request
     */
    private suspend fun getFileSize(url: String): Long {
        val request = HttpRequest(
            method = HttpMethod.HEAD,
            path = HttpRequestPath(url),
            headers = 2 j { i ->
                when (i) {
                    0 -> HttpHeaderName("Host") j HttpHeaderValue("archive.org")
                    1 -> HttpHeaderName("User-Agent") j HttpHeaderValue("PatrickDevineProcessor/1.0")
                    else -> throw IndexOutOfBoundsException()
                }
            }
        )
        
        val response = httpClient.execute(request)
        
        // Find Content-Length header
        for (i in 0 until response.headers.a) {
            val header = response.headers.b(i)
            if (header.a.value.equals("Content-Length", ignoreCase = true)) {
                return header.b.value.toLong()
            }
        }
        
        throw Exception("Could not determine file size")
    }
    
    /**
     * Check if document is interesting for processing
     */
    private fun isInterestingDocument(name: String): Boolean {
        val lowercaseName = name.lowercase()
        return when {
            lowercaseName.endsWith(".pdf") -> true
            lowercaseName.endsWith(".doc") -> true
            lowercaseName.endsWith(".docx") -> true
            lowercaseName.endsWith(".txt") -> true
            lowercaseName.endsWith(".html") -> true
            lowercaseName.endsWith(".htm") -> true
            lowercaseName.endsWith(".mp3") -> true
            lowercaseName.endsWith(".wav") -> true
            lowercaseName.endsWith(".jpg") -> true
            lowercaseName.endsWith(".jpeg") -> true
            lowercaseName.endsWith(".png") -> true
            else -> false
        }
    }
    
    /**
     * Get MIME type from filename
     */
    private fun getMimeType(filename: String): String {
        val lowercaseName = filename.lowercase()
        return when {
            lowercaseName.endsWith(".pdf") -> "application/pdf"
            lowercaseName.endsWith(".doc") -> "application/msword"
            lowercaseName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lowercaseName.endsWith(".txt") -> "text/plain"
            lowercaseName.endsWith(".html") || lowercaseName.endsWith(".htm") -> "text/html"
            lowercaseName.endsWith(".mp3") -> "audio/mpeg"
            lowercaseName.endsWith(".wav") -> "audio/wav"
            lowercaseName.endsWith(".jpg") || lowercaseName.endsWith(".jpeg") -> "image/jpeg"
            lowercaseName.endsWith(".png") -> "image/png"
            else -> "application/octet-stream"
        }
    }
    
    // Little-endian parsing utilities
    private fun parseLittleEndianShort(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or 
        ((data[offset + 1].toInt() and 0xFF) shl 8)
    
    private fun parseLittleEndianInt(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or 
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)
}

/**
 * Process Patrick Devine corpus with document extraction
 */
class PatrickDevineDocumentProcessor(
    private val processor: PatrickDevineProcessor,
    private val fiduciaryContext: FiduciaryContext
) {
    /**
     * Build searchable index of corpus
     */
    suspend fun buildCorpusIndex(): CorpusAttention {
        val documents = processor.processCorpus()
        
        // Group by document type
        val byType = mutableMapOf<String, MutableList<DocumentAttention>>()
        
        for (i in 0 until documents.a) {
            val doc = documents.b(i)
            val mimeType = doc.doc.b
            byType.getOrPut(mimeType) { mutableListOf() }.add(doc)
        }
        
        println("\nCorpus Summary:")
        byType.forEach { (type, docs) ->
            println("$type: ${docs.size} documents")
        }
        
        return fiduciaryContext.corpus(documents, "patrick-devine-complete")
    }
    
    /**
     * Extract concepts from text documents
     */
    suspend fun extractConcepts(corpus: CorpusAttention): Indexed<ConceptAttention> {
        val concepts = mutableListOf<ConceptAttention>()
        
        for (i in 0 until corpus.corpus.a.a) {
            val doc = corpus.corpus.a.b(i)
            
            // Only process text-based documents
            if (doc.doc.b.startsWith("text/") || 
                doc.doc.b == "application/pdf") {
                
                // Mock concept extraction
                val docConcepts = arrayOf(
                    "legal", "maritime", "sovereignty", 
                    "jurisdiction", "common-law"
                )
                
                val conceptAttention = fiduciaryContext.concepts(
                    doc,
                    docConcepts.size j docConcepts::get
                )
                
                concepts.add(conceptAttention)
            }
        }
        
        return concepts.size j concepts::get
    }
}

/**
 * Demonstration of Patrick Devine processing
 */
suspend fun processPatrickDevineCorpus() {
    val ioContext = IOContext.NioContext("patrick-devine")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    val processor = PatrickDevineProcessor(httpClient, ioContext)
    
    // Create fiduciary context
    val metadata = org.apache.tika.metadata.Metadata()
    val parseContext = org.apache.tika.parser.ParseContext()
    val fidContext = FiduciaryContext(ioContext j (metadata j parseContext))
    
    val docProcessor = PatrickDevineDocumentProcessor(processor, fidContext)
    
    // Build corpus index
    val corpus = docProcessor.buildCorpusIndex()
    println("Built corpus with ${corpus.corpus.a.a} documents")
    
    // Extract concepts
    val concepts = docProcessor.extractConcepts(corpus)
    println("Extracted concepts from ${concepts.a} documents")
}