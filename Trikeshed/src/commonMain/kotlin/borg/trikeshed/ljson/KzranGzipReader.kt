@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson


import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.min

/**
 * KZRAN-based Gzip Random Access Reader
 * 
 * Implements the KZRAN algorithm for random access into gzip streams by building
 * an index of sync points where decompression can safely resume.
 * 
 * For 90GB galaxy map files, this allows seeking to any position without
 * decompressing the entire file.
 */

// Type aliases for clarity
typealias FileOffset = Long
typealias UncompressedOffset = Long
typealias BlockSize = Int
typealias WindowBits = Int

/**
 * KZRAN sync point - represents a position where we can resume decompression
 */
data class KzranPoint(
    val compressedOffset: FileOffset,      // Position in compressed stream
    val uncompressedOffset: UncompressedOffset, // Position in uncompressed data
    val windowData: ByteArray,             // Last 32KB of uncompressed data
    val bitOffset: Int                     // Bit offset within byte (0-7)
)

/**
 * KZRAN index for a gzip file
 */
data class KzranIndex(
    val points: List<KzranPoint>,
    val blockSize: BlockSize = 1024 * 1024, // 1MB blocks by default
    val totalUncompressedSize: UncompressedOffset? = null
)

/**
 * Attention frame - defines a region of interest in the uncompressed data
 */
data class AttentionFrame(
    val startOffset: UncompressedOffset,
    val endOffset: UncompressedOffset,
    val priority: Int = 0,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * KZRAN-based gzip reader with attention framing
 */
class KzranGzipReader(
    private val httpClient: HttpRangeClient,
    private val indexCache: KzranIndexCache = InMemoryKzranCache()
) {
    companion object {
        const val WINDOW_SIZE = 32768 // 32KB sliding window for deflate
        const val DEFAULT_BLOCK_SIZE = 1024 * 1024 // 1MB blocks
        const val INDEX_INTERVAL = 10 * 1024 * 1024 // Build index point every 10MB
    }
    
    /**
     * Build KZRAN index for a gzip file
     * This is a one-time operation that should be cached
     */
    suspend fun buildIndex(
        url: String,
        blockSize: BlockSize = DEFAULT_BLOCK_SIZE
    ): KzranIndex = coroutineScope {
        val points = mutableListOf<KzranPoint>()
        var compressedOffset = 0L
        var uncompressedOffset = 0L
        val window = SlidingWindow(WINDOW_SIZE)
        
        // Stream through the file building index points
        httpClient.streamBytes(url, 0, null).collect { chunk ->
            // Process chunk through zlib inflater
            val inflater = ZlibInflater()
            inflater.setInput(chunk)
            
            while (!inflater.needsInput()) {
                val output = ByteArray(blockSize)
                val decompressed = inflater.inflate(output)
                
                // Update sliding window
                window.append(output, 0, decompressed)
                
                uncompressedOffset += decompressed
                
                // Create index point every INDEX_INTERVAL
                if (uncompressedOffset % INDEX_INTERVAL == 0L) {
                    points.add(KzranPoint(
                        compressedOffset = compressedOffset,
                        uncompressedOffset = uncompressedOffset,
                        windowData = window.getWindow(),
                        bitOffset = inflater.getBitOffset()
                    ))
                }
            }
            
            compressedOffset += chunk.size
        }
        
        KzranIndex(
            points = points,
            blockSize = blockSize,
            totalUncompressedSize = uncompressedOffset
        )
    }
    
    /**
     * Read data from specific attention frames using KZRAN index
     */
    fun readAttentionFrames(
        url: String,
        index: KzranIndex,
        frames: List<AttentionFrame>
    ): Flow<Indexed<Byte>> = flow {
        // Sort frames by priority and position
        val sortedFrames = frames.sortedWith(
            compareByDescending<AttentionFrame> { it.priority }
                .thenBy { it.startOffset }
        )
        
        for (frame in sortedFrames) {
            // Find the closest KZRAN point before our target
            val syncPoint = index.points
                .filter { it.uncompressedOffset <= frame.startOffset }
                .maxByOrNull { it.uncompressedOffset }
                ?: index.points.first()
            
            // Calculate how much to skip after sync point
            val skipBytes = frame.startOffset - syncPoint.uncompressedOffset
            val readBytes = frame.endOffset - frame.startOffset
            
            // Fetch compressed data starting from sync point
            val compressedData = httpClient.fetchRange(
                url,
                syncPoint.compressedOffset,
                syncPoint.compressedOffset + estimateCompressedSize(readBytes)
            )
            
            // Decompress from sync point
            val decompressed = decompressFromSyncPoint(
                compressedData,
                syncPoint,
                skipBytes,
                readBytes
            )
            
            emit(decompressed)
        }
    }
    
    /**
     * Stream JSON objects from attention frames with SIMD-accelerated parsing
     */
    fun streamJsonObjects(
        url: String,
        index: KzranIndex,
        frames: List<AttentionFrame>
    ): Flow<JsonObject> = flow {
        readAttentionFrames(url, index, frames).collect { blockData ->
            // Convert to UByteArray for SIMD processing
            val ubyteData = blockData.toUByteArray()
            
            // Use SIMD JSON scanner to find object boundaries
            val bitmap = JsonBitmapSimd.createBitmap(ubyteData)
            val structuralBits = JsonBitmapProcessor.decodeToStructuralBits(
                bitmap,
                blockData.a
            )
            
            // Extract complete JSON objects from the block
            val objects = extractJsonObjects(blockData, structuralBits)
            objects.forEach { obj ->
                emit(obj)
            }
        }
    }
    
    /**
     * Decompress from a KZRAN sync point
     */
    private suspend fun decompressFromSyncPoint(
        compressedData: ByteArray,
        syncPoint: KzranPoint,
        skipBytes: Long,
        readBytes: Long
    ): Indexed<Byte> = coroutineScope {
        val inflater = ZlibInflater()
        
        // Initialize inflater with sync point's window
        inflater.setDictionary(syncPoint.windowData)
        inflater.setBitOffset(syncPoint.bitOffset)
        inflater.setInput(compressedData)
        
        val output = mutableListOf<Byte>()
        var totalDecompressed = 0L
        
        while (!inflater.finished() && totalDecompressed < skipBytes + readBytes) {
            val buffer = ByteArray(DEFAULT_BLOCK_SIZE)
            val decompressed = inflater.inflate(buffer)
            
            // Skip bytes if needed
            val startIdx = if (totalDecompressed < skipBytes) {
                min(decompressed, (skipBytes - totalDecompressed).toInt())
            } else 0
            
            // Calculate how many bytes to keep
            val keepBytes = min(
                decompressed - startIdx,
                (skipBytes + readBytes - totalDecompressed - startIdx).toInt()
            )
            
            if (keepBytes > 0) {
                output.addAll(buffer.slice(startIdx until startIdx + keepBytes))
            }
            
            totalDecompressed += decompressed
        }
        
        output.size j { i: Int -> output[i] }
    }
    
    /**
     * Estimate compressed size based on typical compression ratios
     */
    private fun estimateCompressedSize(uncompressedSize: Long): Long {
        // Assume 10:1 compression ratio for JSON, add 20% buffer
        return (uncompressedSize / 10 * 1.2).toLong()
    }
    
    /**
     * Extract complete JSON objects from a block using structural bits
     */
    private fun extractJsonObjects(
        blockData: Indexed<Byte>,
        structuralBits: UByteArray
    ): List<JsonObject> {
        val objects = mutableListOf<JsonObject>()
        var braceDepth = 0
        var objectStart = -1
        
        for (i in 0 until blockData.a) {
            val structBits = getStructuralBits(structuralBits, i)
            
            when (structBits) {
                JsonBitmapProcessor.JsStateEvent.ScopeOpen.ordinal -> {
                    if (braceDepth == 0 && blockData[i].toInt().toChar() == '{') {
                        objectStart = i
                    }
                    braceDepth++
                }
                JsonBitmapProcessor.JsStateEvent.ScopeClose.ordinal -> {
                    braceDepth--
                    if (braceDepth == 0 && objectStart >= 0) {
                        // Extract complete object
                        val objectBytes = ByteArray(i - objectStart + 1)
                        for (j in objectStart..i) {
                            objectBytes[j - objectStart] = blockData[j]
                        }
                        
                        try {
                            val jsonString = objectBytes.decodeToString()
                            objects.add(JsonObject(jsonString))
                        } catch (e: Exception) {
                            // Skip malformed objects
                        }
                        
                        objectStart = -1
                    }
                }
            }
        }
        
        return objects
    }
    
    private fun getStructuralBits(bits: UByteArray, position: Int): Int {
        val byteIdx = position / 4
        val bitShift = (3 - (position % 4)) * 2
        return (bits[byteIdx].toInt() shr bitShift) and 0b11
    }
}

/**
 * Sliding window for maintaining decompression context
 */
class SlidingWindow(private val size: Int) {
    private val buffer = ByteArray(size)
    private var position = 0
    
    fun append(data: ByteArray, offset: Int, length: Int) {
        val copyLength = min(length, size)
        val startOffset = if (length > size) length - size else 0
        
        if (copyLength < size - position) {
            // Simple append
            data.copyInto(buffer, position, offset + startOffset, offset + startOffset + copyLength)
            position += copyLength
        } else {
            // Wrap around
            val firstPart = size - position
            data.copyInto(buffer, position, offset + startOffset, offset + startOffset + firstPart)
            data.copyInto(buffer, 0, offset + startOffset + firstPart, offset + startOffset + firstPart + copyLength - firstPart)
            position = (position + copyLength) % size
        }
    }
    
    fun getWindow(): ByteArray {
        val result = ByteArray(size)
        if (position == 0) {
            buffer.copyInto(result, 0, 0, 0 + size)
        } else {
            buffer.copyInto(result, 0, position, position + size - position)
            buffer.copyInto(result, size - position, 0, 0 + position)
        }
        return result
    }
}

/**
 * HTTP client with range request support
 */
interface HttpRangeClient {
    suspend fun fetchRange(url: String, start: Long, end: Long): ByteArray
    fun streamBytes(url: String, start: Long, end: Long?): Flow<ByteArray>
}

/**
 * Cache for KZRAN indices
 */
interface KzranIndexCache {
    suspend fun get(url: String): KzranIndex?
    suspend fun put(url: String, index: KzranIndex)
}

/**
 * Simple in-memory cache implementation
 */
class InMemoryKzranCache : KzranIndexCache {
    private val cache = mutableMapOf<String, KzranIndex>()
    
    override suspend fun get(url: String): KzranIndex? = cache[url]
    override suspend fun put(url: String, index: KzranIndex) {
        cache[url] = index
    }
}

/**
 * Placeholder for zlib inflater with bit-level control
 */
class ZlibInflater {
    fun setInput(data: ByteArray) {}
    fun setDictionary(dict: ByteArray) {}
    fun setBitOffset(offset: Int) {}
    fun needsInput(): Boolean = true
    fun inflate(output: ByteArray): Int = 0
    fun finished(): Boolean = true
    fun getBitOffset(): Int = 0
}

/**
 * JSON object wrapper
 */
data class JsonObject(val raw: String)

/**
 * Placeholder for SIMD JSON bitmap
 * TODO: Implement actual SIMD bitmap creation
 */
object JsonBitmapSimd {
    fun createBitmap(input: UByteArray): ULongArray {
        // Simple implementation that marks structural characters
        val result = ULongArray((input.size + 15) / 16)
        
        for (i in input.indices) {
            val char = input[i].toInt().toChar()
            val isStructural = when (char) {
                '{', '}', '[', ']', ':', ',' -> true
                else -> false
            }
            
            if (isStructural) {
                val ulongIndex = i / 16
                val bitPosition = (i % 16) * 4
                result[ulongIndex] = result[ulongIndex] or (1UL shl bitPosition)
            }
        }
        
        return result
    }
}

/**
 * Extension to convert Indexed<Byte> to UByteArray
 */
fun Indexed<Byte>.toUByteArray(): UByteArray {
    val result = UByteArray(a)
    for (i in 0 until a) {
        result[i] = this[i].toUByte()
    }
    return result
}