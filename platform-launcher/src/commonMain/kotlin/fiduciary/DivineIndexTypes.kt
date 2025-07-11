package fiduciary

import borg.trikeshed.lib.*

/**
 * Taxonomical Typealiases for Divine Index Fetching
 * 
 * Defines the core types for extracting ZIP central directory indexes
 * from Patrick Devine archives using TrikeShed tools.
 */

// Archive identification
typealias ArchiveId = String
typealias ArchiveUrl = String
typealias ArchiveName = String

// ZIP structure types
typealias ZipOffset = Long
typealias ZipSize = Long
typealias ZipSignature = Long
typealias ZipMethod = Int
typealias ZipCRC32 = Long

// Central directory types
typealias CentralDirOffset = ZipOffset
typealias CentralDirSize = ZipSize
typealias CentralDirSignature = ZipSignature

// Entry identification
typealias EntryName = String
typealias EntryOffset = ZipOffset
typealias EntryCompressedSize = ZipSize
typealias EntryUncompressedSize = ZipSize
typealias EntryMethod = ZipMethod
typealias EntryCRC32 = ZipCRC32

// Binary data types
typealias ZipBinaryData = Indexed<Byte>
typealias CentralDirBinary = ZipBinaryData
typealias EntryBinary = ZipBinaryData

// HTTP range types
typealias RangeStart = Long
typealias RangeEnd = Long
typealias RangeRequest = Join<RangeStart, RangeEnd>

// Quality assessment types
typealias QualityScore = Double
typealias ResolutionDPI = Int
typealias ContrastRatio = Double
typealias NoiseLevel = Double

// Processing metadata
typealias ProcessingTimestamp = Long
typealias ProcessingDuration = Long
typealias ConfidenceScore = Double

// Storage types
typealias LfsPath = String
typealias LfsPointer = String
typealias LfsSize = Long

// Error types
typealias ErrorCode = String
typealias ErrorMessage = String
typealias ErrorContext = Map<String, String>

// Result types
typealias FetchResult = Either<ErrorMessage, CentralDirBinary>
typealias IndexResult = Either<ErrorMessage, Indexed<ZipEntry>>
typealias LfsResult = Either<ErrorMessage, LfsPath>

// ZIP entry structure
data class ZipEntry(
    val name: EntryName,
    val offset: EntryOffset,
    val compressedSize: EntryCompressedSize,
    val uncompressedSize: EntryUncompressedSize,
    val method: EntryMethod,
    val crc32: EntryCRC32,
    val mimeType: String? = null
)

// Central directory structure
data class CentralDirectory(
    val offset: CentralDirOffset,
    val size: CentralDirSize,
    val signature: CentralDirSignature,
    val entries: Indexed<ZipEntry>,
    val binary: CentralDirBinary
)

// Archive metadata
data class ArchiveMetadata(
    val id: ArchiveId,
    val url: ArchiveUrl,
    val name: ArchiveName,
    val totalSize: ZipSize,
    val centralDirOffset: CentralDirOffset,
    val centralDirSize: CentralDirSize,
    val entryCount: Int,
    val timestamp: ProcessingTimestamp
)

// Fetching configuration
data class FetchConfig(
    val archives: Indexed<ArchiveUrl>,
    val outputDir: String = "fiduciary/lfs",
    val chunkSize: ZipSize = 64 * 1024L, // 64KB chunks
    val maxRetries: Int = 3,
    val timeoutMs: Long = 30000L
)

// Processing statistics
data class FetchStats(
    val totalArchives: Int,
    val successfulFetches: Int,
    val failedFetches: Int,
    val totalBytesFetched: LfsSize,
    val averageFetchTime: ProcessingDuration,
    val errors: Indexed<ErrorMessage>
) 