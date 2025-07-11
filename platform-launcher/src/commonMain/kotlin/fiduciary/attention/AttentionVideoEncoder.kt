package fiduciary.attention

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.*

/**
 * Attention Video Encoder
 * 
 * Learns from memvid project insights and applies them through lavc/libavcodec.
 * Streams attention events as video channels for visualization and persistence.
 */

// === Learning from Memvid ===

/**
 * Key insights from memvid project:
 * 1. Attention data can be encoded as video frames
 * 2. Video provides natural temporal ordering
 * 3. Video compression is effective for attention memory
 * 4. Real-time streaming of attention flows
 * 5. Video persistence across sessions
 */

// === Attention Frame Types ===

/**
 * Attention frame representing a single attention event
 */
data class AttentionFrame(
    val timestamp: Long,
    val eventType: String,
    val data: Map<String, Any>,
    val intensity: Double,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Video frame with attention data encoded
 */
data class VideoFrame(
    val frameNumber: Long,
    val width: Int,
    val height: Int,
    val pixelData: ByteArray,
    val attentionData: AttentionFrame? = null
)

// === Lavc Integration ===

/**
 * Lavc-based attention video encoder
 * Applies memvid insights through libavcodec
 */
class AttentionVideoEncoder(
    internal val codec: String = "libx264",
    internal val width: Int = 1920,
    internal val height: Int = 1080,
    internal val fps: Int = 30
) {
    
    /**
     * Encode attention frame as video frame
     */
    suspend fun encodeAttentionFrame(frame: AttentionFrame): VideoFrame {
        // Create visual representation of attention data
        val pixelData = createAttentionVisualization(frame)
        
        return VideoFrame(
            frameNumber = frame.timestamp,
            width = width,
            height = height,
            pixelData = pixelData,
            attentionData = frame
        )
    }
    
    /**
     * Stream attention events as video frames
     */
    fun streamAttentionAsVideo(attentionEvents: Flow<AttentionEvent>): Flow<VideoFrame> {
        return attentionEvents.map { event ->
            val attentionFrame = eventToAttentionFrame(event)
            encodeAttentionFrame(attentionFrame)
        }
    }
    
    /**
     * Create attention visualization as pixel data
     */
    internal fun createAttentionVisualization(frame: AttentionFrame): ByteArray {
        // Create RGB pixel data representing attention
        val pixels = ByteArray(width * height * 3) // RGB
        
        when (frame.eventType) {
            "document_focus" -> createDocumentFocusVisualization(frame, pixels)
            "corpus_scan" -> createCorpusScanVisualization(frame, pixels)
            "concept_extraction" -> createConceptExtractionVisualization(frame, pixels)
            "fiduciary_action" -> createFiduciaryActionVisualization(frame, pixels)
        }
        
        return pixels
    }
    
    /**
     * Document focus visualization
     */
    internal fun createDocumentFocusVisualization(frame: AttentionFrame, pixels: ByteArray) {
        val docId = frame.data["docId"] as String
        val intensity = frame.intensity
        
        // Create heatmap-style visualization
        val centerX = width / 2
        val centerY = height / 2
        val radius = (intensity * 200).toInt()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val distance = kotlin.math.sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble())
                val pixelIndex = (y * width + x) * 3
                
                if (distance < radius) {
                    val alpha = 1.0 - (distance / radius)
                    pixels[pixelIndex] = (255 * alpha * intensity).toInt().toByte()     // R
                    pixels[pixelIndex + 1] = (128 * alpha * intensity).toInt().toByte() // G
                    pixels[pixelIndex + 2] = (255 * alpha * intensity).toInt().toByte() // B
                } else {
                    pixels[pixelIndex] = 0     // R
                    pixels[pixelIndex + 1] = 0 // G
                    pixels[pixelIndex + 2] = 0 // B
                }
            }
        }
    }
    
    /**
     * Corpus scan visualization
     */
    internal fun createCorpusScanVisualization(frame: AttentionFrame, pixels: ByteArray) {
        val scannedBytes = frame.data["scannedBytes"] as Long
        val totalBytes = frame.data["totalBytes"] as Long
        val progress = scannedBytes.toDouble() / totalBytes
        
        // Create progress bar visualization
        val barHeight = 50
        val barY = height / 2
        val barWidth = (width * progress).toInt()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelIndex = (y * width + x) * 3
                
                if (y >= barY - barHeight/2 && y <= barY + barHeight/2) {
                    if (x < barWidth) {
                        pixels[pixelIndex] = 0.toByte()     // R
                        pixels[pixelIndex + 1] = 255.toByte() // G
                        pixels[pixelIndex + 2] = 0.toByte()   // B
                    } else {
                        pixels[pixelIndex] = 64.toByte()     // R
                        pixels[pixelIndex + 1] = 64.toByte() // G
                        pixels[pixelIndex + 2] = 64.toByte() // B
                    }
                } else {
                    pixels[pixelIndex] = 0.toByte()     // R
                    pixels[pixelIndex + 1] = 0.toByte() // G
                    pixels[pixelIndex + 2] = 0.toByte() // B
                }
            }
        }
    }
    
    /**
     * Concept extraction visualization
     */
    internal fun createConceptExtractionVisualization(frame: AttentionFrame, pixels: ByteArray) {
        val confidence = frame.data["confidence"] as Double
        
        // Create network-style visualization
        val centerX = width / 2
        val centerY = height / 2
        val nodeRadius = (confidence * 100).toInt()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelIndex = (y * width + x) * 3
                val distance = kotlin.math.sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble())
                
                if (distance < nodeRadius) {
                    pixels[pixelIndex] = 255.toByte()     // R
                    pixels[pixelIndex + 1] = 255.toByte() // G
                    pixels[pixelIndex + 2] = 0.toByte()   // B
                } else {
                    pixels[pixelIndex] = 0.toByte()     // R
                    pixels[pixelIndex + 1] = 0.toByte() // G
                    pixels[pixelIndex + 2] = 0.toByte() // B
                }
            }
        }
    }
    
    /**
     * Fiduciary action visualization
     */
    internal fun createFiduciaryActionVisualization(frame: AttentionFrame, pixels: ByteArray) {
        val actionType = frame.data["actionType"] as String
        
        // Create action indicator visualization
        val centerX = width / 2
        val centerY = height / 2
        val indicatorSize = 100
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelIndex = (y * width + x) * 3
                val distance = kotlin.math.sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble())
                
                if (distance < indicatorSize) {
                    pixels[pixelIndex] = 255.toByte()     // R
                    pixels[pixelIndex + 1] = 0.toByte()   // G
                    pixels[pixelIndex + 2] = 255.toByte() // B
                } else {
                    pixels[pixelIndex] = 0.toByte()     // R
                    pixels[pixelIndex + 1] = 0.toByte() // G
                    pixels[pixelIndex + 2] = 0.toByte() // B
                }
            }
        }
    }
    
    /**
     * Convert attention event to attention frame
     */
    internal fun eventToAttentionFrame(event: AttentionEvent): AttentionFrame {
        return when (event) {
            is AttentionEvent.DocumentFocus -> AttentionFrame(
                timestamp = System.currentTimeMillis(),
                eventType = "document_focus",
                data = mapOf(
                    "docId" to event.docId,
                    "rangeStart" to event.range.a,
                    "rangeEnd" to event.range.b,
                    "duration" to event.duration
                ),
                intensity = event.intensity
            )
            is AttentionEvent.CorpusScan -> AttentionFrame(
                timestamp = System.currentTimeMillis(),
                eventType = "corpus_scan",
                data = mapOf(
                    "corpusId" to event.corpusId,
                    "scannedBytes" to event.scannedBytes,
                    "totalBytes" to event.totalBytes
                ),
                intensity = event.attentionScore
            )
            is AttentionEvent.ConceptExtraction -> AttentionFrame(
                timestamp = System.currentTimeMillis(),
                eventType = "concept_extraction",
                data = mapOf(
                    "conceptId" to event.conceptId,
                    "sourceDoc" to event.sourceDoc,
                    "confidence" to event.confidence
                ),
                intensity = event.confidence
            )
            is AttentionEvent.FiduciaryAction -> AttentionFrame(
                timestamp = event.timestamp,
                eventType = "fiduciary_action",
                data = mapOf(
                    "actionType" to event.actionType,
                    "targetId" to event.targetId,
                    "obligation" to event.obligation
                ),
                intensity = 1.0
            )
        }
    }
}

// === Integration with Fiduciary Attention ===

/**
 * Stream fiduciary attention as video
 */
suspend fun streamFiduciaryAttentionAsVideo(
    attentionEvents: Flow<AttentionEvent>,
    encoder: AttentionVideoEncoder = AttentionVideoEncoder()
): Flow<VideoFrame> {
    return encoder.streamAttentionAsVideo(attentionEvents)
}

/**
 * Create attention video from fiduciary operations
 */
suspend fun createAttentionVideo(
    operations: Indexed<FiduciaryAttention>,
    encoder: AttentionVideoEncoder = AttentionVideoEncoder()
): Flow<VideoFrame> {
    val events = operations.α { attention ->
        when (attention.document) {
            is DocumentAttention -> AttentionEvent.DocumentFocus(
                docId = attention.document.doc.a.toString(),
                range = attention.document.doc.b,
                duration = 1000L,
                intensity = 1.0
            )
            is CorpusAttention -> AttentionEvent.CorpusScan(
                corpusId = attention.document.corpus.a,
                scannedBytes = attention.document.corpus.b.a,
                totalBytes = attention.document.corpus.b.b,
                attentionScore = 1.0
            )
            else -> AttentionEvent.FiduciaryAction(
                actionType = "unknown",
                targetId = "unknown",
                obligation = "fiduciary_attention",
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    return encoder.streamAttentionAsVideo(events.asFlow())
} 