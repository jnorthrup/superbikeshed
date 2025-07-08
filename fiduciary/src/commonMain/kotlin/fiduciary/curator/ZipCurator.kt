package fiduciary.curator

import fiduciary.fetch.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*

/**
 * Curates thousands of files from ZIP archives
 * Builds indexes, tracks metadata, enables selective extraction
 */
class ZipCurator(
    private val fetcher: ZipRangeFetcher
) {
    
    /**
     * Index all files across all archives
     */
    suspend fun indexAllFiles(): CuratedIndex {
        val centralDirs = fetcher.fetchZipCentralDirs()
        val allEntries = mutableListOf<CuratedEntry>()
        
        centralDirs.forEach { centralDir ->
            println("Indexing ${centralDir.archiveName}: ${centralDir.entries.size} files")
            
            centralDir.entries.forEachIndexed { index, entry ->
                allEntries.add(CuratedEntry(
                    archiveUrl = centralDir.archiveUrl,
                    archiveName = centralDir.archiveName,
                    entry = entry,
                    globalIndex = allEntries.size,
                    archiveIndex = index
                ))
            }
        }
        
        println("Total files indexed: ${allEntries.size}")
        return CuratedIndex(
            entries = allEntries,
            totalFiles = allEntries.size,
            totalCompressedSize = allEntries.sumOf { it.entry.compressedSize },
            totalUncompressedSize = allEntries.sumOf { it.entry.uncompressedSize }
        )
    }
    
    /**
     * Filter files by pattern for selective processing
     */
    fun filterFiles(index: CuratedIndex, patterns: List<String>): List<CuratedEntry> {
        return index.entries.filter { entry ->
            patterns.any { pattern ->
                entry.entry.filename.contains(pattern, ignoreCase = true)
            }
        }
    }
    
    /**
     * Group files by extension for batch processing
     */
    fun groupByExtension(index: CuratedIndex): Map<String, List<CuratedEntry>> {
        return index.entries.groupBy { entry ->
            entry.entry.filename.substringAfterLast('.', "no_extension")
        }
    }
    
    /**
     * Create extraction plan for efficient batch processing
     */
    fun createExtractionPlan(
        entries: List<CuratedEntry>,
        maxBatchSize: Long = 10 * 1024 * 1024 // 10MB batches
    ): List<ExtractionBatch> {
        val batches = mutableListOf<ExtractionBatch>()
        var currentBatch = mutableListOf<CuratedEntry>()
        var currentSize = 0L
        
        entries.sortedBy { it.entry.localHeaderOffset }.forEach { entry ->
            if (currentSize + entry.entry.compressedSize > maxBatchSize && currentBatch.isNotEmpty()) {
                batches.add(ExtractionBatch(
                    entries = currentBatch.toList(),
                    totalSize = currentSize,
                    rangeStart = currentBatch.first().entry.localHeaderOffset,
                    rangeEnd = currentBatch.last().entry.localHeaderOffset + currentBatch.last().entry.compressedSize
                ))
                currentBatch = mutableListOf()
                currentSize = 0L
            }
            currentBatch.add(entry)
            currentSize += entry.entry.compressedSize
        }
        
        if (currentBatch.isNotEmpty()) {
            batches.add(ExtractionBatch(
                entries = currentBatch,
                totalSize = currentSize,
                rangeStart = currentBatch.first().entry.localHeaderOffset,
                rangeEnd = currentBatch.last().entry.localHeaderOffset + currentBatch.last().entry.compressedSize
            ))
        }
        
        return batches
    }
}

@Serializable
data class CuratedEntry(
    val archiveUrl: String,
    val archiveName: String,
    val entry: ZipEntry,
    val globalIndex: Int,
    val archiveIndex: Int
)

@Serializable
data class CuratedIndex(
    val entries: List<CuratedEntry>,
    val totalFiles: Int,
    val totalCompressedSize: Long,
    val totalUncompressedSize: Long
)

@Serializable
data class ExtractionBatch(
    val entries: List<CuratedEntry>,
    val totalSize: Long,
    val rangeStart: Long,
    val rangeEnd: Long
)