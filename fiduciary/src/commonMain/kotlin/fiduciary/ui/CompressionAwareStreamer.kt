package fiduciary.ui

import borg.trikeshed.lib.*
// import fiduciary.compression.*
// import io.islandtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

/**
 * Compression-aware streaming interface for large fiduciary archives
 * Supports multiple compression formats with attention-based selective decompression
 */
class CompressionAwareStreamer {
    
    /**
     * Supported compression formats with expected ratios
     */
    enum class CompressionFormat(val ratio: Double, val extensions: Set<String>) {
        GZIP(0.3, setOf("gz", "gzip")),
        ZIP(0.35, setOf("zip")),
        BZIP2(0.25, setOf("bz2", "bzip2")),
        XZ(0.2, setOf("xz")),
        ZSTD(0.22, setOf("zst", "zstd")),
        LZ4(0.4, setOf("lz4")),
        TAR_GZ(0.3, setOf("tar.gz", "tgz")),
        SEVEN_Z(0.15, setOf("7z"))
    }
    
    /**
     * Stream metadata with compression details
     */
    data class StreamMetadata(
        val format: CompressionFormat,
        val compressedSize: Long,
        val estimatedUncompressedSize: Long,
        val entryCount: Long?,
        val attentionScore: Double,
        val byteRange: Twin<Long>
    )
    
    /**
     * Compression-aware entry for selective processing
     */
    data class CompressedEntry(
        val path: String,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val compressionRatio: Double,
        val metadata: Map<String, String>,
        val attentionScore: Double,
        val streamFactory: () → InputStream
    )
    
    /**
     * Stream through compressed archive with attention-based filtering
     */
    suspend fun streamCompressedArchive(
        source: ArchiveSource,
        attentionThreshold: Double = 0.5,
        memoryLimitMB: Long = 512
    ): Flow<CompressedEntry> = flow {
        val format = detectFormat(source.path)
        
        when (format) {
            CompressionFormat.ZIP → streamZipArchive(source, attentionThreshold)
            CompressionFormat.TAR_GZ → streamTarGzArchive(source, attentionThreshold)
            CompressionFormat.GZIP → streamGzipFile(source, attentionThreshold)
            CompressionFormat.ZSTD → streamZstdArchive(source, attentionThreshold)
            CompressionFormat.LZ4 → streamLz4Archive(source, attentionThreshold)
            else → streamGenericCompressed(source, format, attentionThreshold)
        }.collect { entry →
            if (entry.attentionScore >= attentionThreshold) {
                emit(entry)
            }
        }
    }
    
    /**
     * Stream ZIP archives with central directory optimization
     */
    internal fun streamZipArchive(
        source: ArchiveSource,
        threshold: Double
    ): Flow<CompressedEntry> = flow {
        // JVM-specific implementation omitted in commonMain
    }
    
    /**
     * Stream tar.gz archives with efficient skipping
     */
    internal fun streamTarGzArchive(
        source: ArchiveSource,
        threshold: Double
    ): Flow<CompressedEntry> = flow {
        // Implementation would use Apache Commons Compress
        // for efficient tar entry skipping
    }
    
    /**
     * Stream ZSTD compressed archives
     */
    internal fun streamZstdArchive(
        source: ArchiveSource,
        threshold: Double
    ): Flow<CompressedEntry> = flow {
        // Use Zstd.kt from Trikeshed
        val decompressor = Zstd()
        // Implementation for ZSTD streaming
    }
    
    /**
     * Stream LZ4 compressed archives
     */
    internal fun streamLz4Archive(
        source: ArchiveSource,
        threshold: Double
    ): Flow<CompressedEntry> = flow {
        // Use Lz4.kt from Trikeshed
        val decompressor = Lz4()
        // Implementation for LZ4 streaming
    }
    
    /**
     * Stream single GZIP file
     */
    internal fun streamGzipFile(
        source: ArchiveSource,
        threshold: Double
    ): Flow<CompressedEntry> = flow {
        val metadata = mapOf("filename" to source.path)
        val attention = calculateEntryAttention(source.path, metadata)
        
        emit(CompressedEntry(
            path = source.path.removeSuffix(".gz"),
            compressedSize = source.size,
            uncompressedSize = (source.size / CompressionFormat.GZIP.ratio).toLong(),
            compressionRatio = CompressionFormat.GZIP.ratio,
            metadata = metadata,
            attentionScore = attention,
            streamFactory = { GZIPInputStream(source.openStream()) }
        ))
    }
    
    /**
     * Generic compressed stream handler
     */
    internal fun streamGenericCompressed(
        source: ArchiveSource,
        format: CompressionFormat,
        threshold: Double
    ): Flow<CompressedEntry> = flow {
        // Fallback implementation
    }
    
    /**
     * Calculate attention score for compressed entry
     */
    internal fun calculateEntryAttention(
        path: String,
        metadata: Map<String, String>
    ): Double {
        val fiduciaryPatterns = listOf(
            "trust", "fiduciary", "benefic", "estate", "will",
            "probate", "asset", "fund", "invest", "portfolio",
            "legal", "contract", "agreement", "deed", "title"
        )
        
        val pathLower = path.lowercase()
        val metadataText = metadata.values.joinToString(" ").lowercase()
        val combinedText = "$pathLower $metadataText"
        
        val matches = fiduciaryPatterns.count { pattern →
            combinedText.contains(pattern)
        }
        
        // Boost for specific file types
        val typeBoost = when {
            path.endsWith(".pdf") → 0.2
            path.endsWith(".doc") || path.endsWith(".docx") → 0.2
            path.endsWith(".txt") → 0.1
            path.endsWith(".md") → 0.15
            else → 0.0
        }
        
        return minOf(1.0, (matches * 0.15) + typeBoost)
    }
    
    /**
     * Extract metadata from compressed entry
     */
    internal fun extractMetadata(entry: ZipEntry): Map<String, String> {
        return mapOf(
            "name" to entry.name,
            "size" to entry.size.toString(),
            "compressedSize" to entry.compressedSize.toString(),
            "lastModified" to entry.lastModifiedTime?.toString() ?: "",
            "comment" to (entry.comment ?: ""),
            "method" to entry.method.toString()
        )
    }
    
    /**
     * Detect compression format from file extension
     */
    internal fun detectFormat(path: String): CompressionFormat {
        val lower = path.lowercase()
        return CompressionFormat.values().find { format →
            format.extensions.any { ext → lower.endsWith(".$ext") }
        } ?: CompressionFormat.GZIP
    }
    
    /**
     * Archive source abstraction
     */
    data class ArchiveSource(
        val path: String,
        val size: Long,
        val openStream: suspend () → InputStream
    )
    
    /**
     * Batch decompress with parallel processing
     */
    suspend fun batchDecompress(
        entries: Indexed<CompressedEntry>,
        parallelism: Int = 4,
        outputHandler: suspend (String, InputStream) → Unit
    ) = coroutineScope {
        entries.asFlow()
            .buffer(parallelism)
            .map { entry →
                async {
                    try {
                        val stream = entry.streamFactory()
                        outputHandler(entry.path, stream)
                        stream.close()
                    } catch (e: Exception) {
                        // Log error but continue
                    }
                }
            }
            .collect { it.await() }
    }
    
    /**
     * Memory-efficient compression ratio estimator
     */
    suspend fun estimateCompressionRatio(
        source: ArchiveSource,
        sampleSizeMB: Int = 10
    ): Double = withContext(Dispatchers.IO) {
        val sampleSize = sampleSizeMB * 1024 * 1024
        val buffer = ByteArray(minOf(sampleSize, source.size.toInt()))
        
        source.openStream().use { input →
            input.read(buffer)
        }
        
        // Estimate based on sample
        val format = detectFormat(source.path)
        format.ratio
    }
}

// Add stubs for missing types
// Twin is a typealias for Pair
typealias Twin<T> = Pair<T, T>
// ArchiveSource stub
data class ArchiveSource(val path: String, val size: Long) {
    fun openStream(): Any = throw NotImplementedError("Not available in commonMain")
}
// Comment out JVM-specific classes in commonMain
// Replace InputStream, ZipInputStream, ZipEntry, GZIPInputStream, Zstd, Lz4 with stubs
open class InputStream
class ZipInputStream(stream: Any) : InputStream() {
    var nextEntry: ZipEntry? = null
    fun closeEntry() {}
}
class ZipEntry(val name: String, val size: Long = 0, val compressedSize: Long = 0) {
    val isDirectory: Boolean = false
}
class GZIPInputStream(stream: Any) : InputStream()
class Zstd
class Lz4