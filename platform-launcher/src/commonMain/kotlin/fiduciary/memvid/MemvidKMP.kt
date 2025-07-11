package fiduciary.memvid

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.math.*

/**
 * Memvid KMP - Kotlin Multiplatform port of Memvid
 * 
 * Video-based memory storage using QR codes in MP4 files
 * Based on https://github.com/Olow304/memvid
 */

// === Core Types ===

@Serializable
data class MemvidIndex(
    val version: String = "1.0",
    val chunks: List<ChunkMetadata>,
    val embeddings: List<FloatArray>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ChunkMetadata(
    val id: String,
    val text: String,
    val frameNumber: Int,
    val qrPosition: QRPosition,
    val timestamp: Long
)

@Serializable
data class QRPosition(
    val x: Int,
    val y: Int,
    val size: Int
)

// === QR Code Generation (Multiplatform) ===

interface QRCodeGenerator {
    fun generate(text: String, size: Int): ByteArray
}

/**
 * Simple QR code generator using byte patterns
 * Real implementation would use proper QR algorithm
 */
class SimpleQRGenerator : QRCodeGenerator {
    override fun generate(text: String, size: Int): ByteArray {
        val pixels = ByteArray(size * size)
        val hash = text.hashCode()
        
        // Generate pattern based on text hash
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                // Create pattern from hash bits
                val bit = (hash shr ((x + y) % 32)) and 1
                pixels[idx] = if (bit == 1) 0 else 255.toByte()
            }
        }
        
        // Add QR finder patterns (simplified)
        addFinderPattern(pixels, size, 0, 0)
        addFinderPattern(pixels, size, size - 7, 0)
        addFinderPattern(pixels, size, 0, size - 7)
        
        return pixels
    }
    
    private fun addFinderPattern(pixels: ByteArray, size: Int, offsetX: Int, offsetY: Int) {
        val pattern = arrayOf(
            intArrayOf(1,1,1,1,1,1,1),
            intArrayOf(1,0,0,0,0,0,1),
            intArrayOf(1,0,1,1,1,0,1),
            intArrayOf(1,0,1,1,1,0,1),
            intArrayOf(1,0,1,1,1,0,1),
            intArrayOf(1,0,0,0,0,0,1),
            intArrayOf(1,1,1,1,1,1,1)
        )
        
        for (y in pattern.indices) {
            for (x in pattern[y].indices) {
                val px = offsetX + x
                val py = offsetY + y
                if (px in 0 until size && py in 0 until size) {
                    pixels[py * size + px] = if (pattern[y][x] == 1) 0 else 255.toByte()
                }
            }
        }
    }
}

// === Video Encoding (Multiplatform) ===

/**
 * Simplified MP4 encoder for Memvid
 * Stores QR codes as frames
 */
class MemvidMP4Encoder(
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val fps: Int = 30,
    private val qrSize: Int = 256
) {
    private val qrGenerator = SimpleQRGenerator()
    private val frames = mutableListOf<VideoFrame>()
    
    /**
     * Add text chunk as QR code frame
     */
    fun addChunk(text: String, metadata: ChunkMetadata) {
        val qrData = qrGenerator.generate(text, qrSize)
        val frame = createFrame(qrData, metadata)
        frames.add(frame)
    }
    
    /**
     * Create video frame with QR code
     */
    private fun createFrame(qrData: ByteArray, metadata: ChunkMetadata): VideoFrame {
        val pixels = IntArray(width * height)
        
        // Fill background
        pixels.fill(0xFFFFFFFF.toInt())
        
        // Place QR code
        val qrX = metadata.qrPosition.x
        val qrY = metadata.qrPosition.y
        
        for (y in 0 until qrSize) {
            for (x in 0 until qrSize) {
                val qrIdx = y * qrSize + x
                val frameX = qrX + x
                val frameY = qrY + y
                
                if (frameX in 0 until width && frameY in 0 until height) {
                    val frameIdx = frameY * width + frameX
                    val pixel = if (qrData[qrIdx] == 0.toByte()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    pixels[frameIdx] = pixel
                }
            }
        }
        
        return VideoFrame(
            frameNumber = metadata.frameNumber.toLong(),
            timestamp = metadata.timestamp,
            width = width,
            height = height,
            pixels = pixels
        )
    }
    
    /**
     * Build MP4 file (platform-specific implementation needed)
     */
    fun buildVideo(): ByteArray {
        // Simplified: return frame data
        // Real implementation would encode to MP4
        val totalSize = frames.size * width * height * 4
        val videoData = ByteArray(totalSize)
        var offset = 0
        
        frames.forEach { frame ->
            frame.pixels.forEach { pixel ->
                videoData[offset++] = (pixel shr 24).toByte()
                videoData[offset++] = (pixel shr 16).toByte()
                videoData[offset++] = (pixel shr 8).toByte()
                videoData[offset++] = pixel.toByte()
            }
        }
        
        return videoData
    }
}

// === Embeddings (Multiplatform) ===

/**
 * Simple text embeddings for semantic search
 */
class SimpleEmbeddings {
    /**
     * Generate embedding vector for text
     */
    fun embed(text: String): FloatArray {
        val words = text.lowercase().split(Regex("\\s+"))
        val embedding = FloatArray(128) // 128-dimensional
        
        // Simple bag-of-words style embedding
        words.forEach { word ->
            val hash = word.hashCode()
            for (i in embedding.indices) {
                val bit = (hash shr (i % 32)) and 1
                embedding[i] += if (bit == 1) 1f else -1f
            }
        }
        
        // Normalize
        val magnitude = sqrt(embedding.map { it * it }.sum())
        if (magnitude > 0) {
            for (i in embedding.indices) {
                embedding[i] /= magnitude
            }
        }
        
        return embedding
    }
    
    /**
     * Cosine similarity between embeddings
     */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        return dotProduct
    }
}

// === Main Memvid KMP API ===

/**
 * Memvid encoder - builds video memory
 */
class MemvidEncoder(
    private val videoWidth: Int = 1920,
    private val videoHeight: Int = 1080
) {
    private val chunks = mutableListOf<ChunkMetadata>()
    private val embeddings = mutableListOf<FloatArray>()
    private val mp4Encoder = MemvidMP4Encoder(videoWidth, videoHeight)
    private val embeddingModel = SimpleEmbeddings()
    
    /**
     * Add text chunks
     */
    fun addChunks(textChunks: List<String>) {
        textChunks.forEachIndexed { index, text ->
            addText(text, metadata = mapOf("index" to index.toString()))
        }
    }
    
    /**
     * Add single text with metadata
     */
    fun addText(text: String, metadata: Map<String, String> = emptyMap()) {
        val chunkId = "chunk_${chunks.size}"
        val frameNumber = chunks.size
        
        // Calculate QR position (grid layout)
        val qrPerRow = videoWidth / 300
        val row = frameNumber / qrPerRow
        val col = frameNumber % qrPerRow
        
        val chunkMetadata = ChunkMetadata(
            id = chunkId,
            text = text,
            frameNumber = frameNumber,
            qrPosition = QRPosition(
                x = col * 300 + 22,
                y = row * 300 + 22,
                size = 256
            ),
            timestamp = System.currentTimeMillis()
        )
        
        chunks.add(chunkMetadata)
        embeddings.add(embeddingModel.embed(text))
        mp4Encoder.addChunk(text, chunkMetadata)
    }
    
    /**
     * Build video and index
     */
    fun buildVideo(): Pair<ByteArray, MemvidIndex> {
        val videoData = mp4Encoder.buildVideo()
        val index = MemvidIndex(
            chunks = chunks,
            embeddings = embeddings
        )
        
        return videoData to index
    }
}

/**
 * Memvid retriever - searches video memory
 */
class MemvidRetriever(
    private val index: MemvidIndex
) {
    private val embeddingModel = SimpleEmbeddings()
    
    /**
     * Search for similar chunks
     */
    fun search(query: String, topK: Int = 5): List<SearchResult> {
        val queryEmbedding = embeddingModel.embed(query)
        
        val results = index.chunks.mapIndexed { idx, chunk ->
            val similarity = embeddingModel.similarity(queryEmbedding, index.embeddings[idx])
            SearchResult(chunk, similarity)
        }
        
        return results
            .sortedByDescending { it.score }
            .take(topK)
    }
}

/**
 * Search result with chunk and score
 */
data class SearchResult(
    val chunk: ChunkMetadata,
    val score: Float
)

/**
 * Memvid chat interface
 */
class MemvidChat(
    private val retriever: MemvidRetriever
) {
    /**
     * Chat with video memory
     */
    fun chat(query: String): String {
        val results = retriever.search(query, topK = 3)
        
        return buildString {
            appendLine("Found ${results.size} relevant chunks:")
            results.forEach { result ->
                appendLine("- [Score: %.3f] ${result.chunk.text}".format(result.score))
            }
        }
    }
}