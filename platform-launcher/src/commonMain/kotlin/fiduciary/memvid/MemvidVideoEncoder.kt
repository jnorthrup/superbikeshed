package fiduciary.memvid

import fiduciary.attention.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * Memvid Video Encoder - Encodes attention patterns as video frames
 * 
 * Creates visual representations of attention flow for side-pipe memory
 */
class MemvidVideoEncoder(
    private val frameWidth: Int = 640,
    private val frameHeight: Int = 480,
    private val frameRate: Int = 30
) {
    
    /**
     * Encode attention stream as video frames
     */
    fun encodeAttentionStream(
        events: Flow<AttentionEvent>
    ): Flow<VideoFrame> = flow {
        var frameNumber = 0L
        val attentionBuffer = mutableListOf<AttentionEvent>()
        
        events.collect { event ->
            attentionBuffer.add(event)
            
            // Generate frame every N events or time interval
            if (attentionBuffer.size >= 10 || shouldGenerateFrame(frameNumber)) {
                val frame = generateFrame(attentionBuffer, frameNumber++)
                emit(frame)
                
                // Keep sliding window of events
                if (attentionBuffer.size > 100) {
                    attentionBuffer.removeAt(0)
                }
            }
        }
        
        // Final frame with remaining events
        if (attentionBuffer.isNotEmpty()) {
            emit(generateFrame(attentionBuffer, frameNumber))
        }
    }
    
    /**
     * Generate single video frame from attention events
     */
    private fun generateFrame(
        events: List<AttentionEvent>,
        frameNumber: Long
    ): VideoFrame {
        val pixels = IntArray(frameWidth * frameHeight)
        
        // Background color (dark blue)
        pixels.fill(0xFF000033.toInt())
        
        // Render attention patterns
        events.forEachIndexed { index, event ->
            when (event) {
                is AttentionEvent.DocumentFocus -> {
                    renderDocumentFocus(pixels, event, index)
                }
                is AttentionEvent.ConceptExtraction -> {
                    renderConceptExtraction(pixels, event, index)
                }
                is AttentionEvent.CorpusScan -> {
                    renderCorpusScan(pixels, event, index)
                }
                else -> {}
            }
        }
        
        // Add frame metadata overlay
        renderMetadata(pixels, frameNumber, events.size)
        
        return VideoFrame(
            frameNumber = frameNumber,
            timestamp = System.currentTimeMillis(),
            width = frameWidth,
            height = frameHeight,
            pixels = pixels,
            metadata = mapOf(
                "eventCount" to events.size,
                "frameRate" to frameRate
            )
        )
    }
    
    /**
     * Render document focus as pulsing circles
     */
    private fun renderDocumentFocus(
        pixels: IntArray,
        event: AttentionEvent.DocumentFocus,
        index: Int
    ) {
        val centerX = (frameWidth * 0.3 + index * 20) % frameWidth
        val centerY = frameHeight / 2
        val radius = (10 + event.intensity * 30).toInt()
        val color = interpolateColor(0xFF0080FF.toInt(), 0xFFFF8000.toInt(), event.intensity.toFloat())
        
        drawCircle(pixels, centerX.toInt(), centerY, radius, color)
    }
    
    /**
     * Render concept extraction as connected nodes
     */
    private fun renderConceptExtraction(
        pixels: IntArray,
        event: AttentionEvent.ConceptExtraction,
        index: Int
    ) {
        val x = (frameWidth * 0.7 + index * 15) % frameWidth
        val y = (frameHeight * 0.3 + index * 10) % frameHeight
        val size = (5 + event.confidence * 15).toInt()
        val color = 0xFF00FF80.toInt()
        
        drawSquare(pixels, x.toInt(), y.toInt(), size, color)
        
        // Draw connections to related concepts
        val relatedCount = min(event.relatedConcepts.a, 5)
        for (i in 0 until relatedCount) {
            val angle = (i * 2 * PI / relatedCount).toFloat()
            val endX = x + cos(angle) * 50
            val endY = y + sin(angle) * 50
            drawLine(pixels, x.toInt(), y.toInt(), endX.toInt(), endY.toInt(), color and 0x80FFFFFF.toInt())
        }
    }
    
    /**
     * Render corpus scan as progress bar
     */
    private fun renderCorpusScan(
        pixels: IntArray,
        event: AttentionEvent.CorpusScan,
        index: Int
    ) {
        val y = frameHeight - 50 - index * 20
        val progress = event.scannedBytes.toFloat() / event.totalBytes
        val barWidth = (frameWidth * 0.8).toInt()
        val barHeight = 15
        val x = (frameWidth - barWidth) / 2
        
        // Background bar
        drawRectangle(pixels, x, y, barWidth, barHeight, 0xFF404040.toInt())
        
        // Progress bar
        val progressWidth = (barWidth * progress).toInt()
        val color = interpolateColor(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), progress)
        drawRectangle(pixels, x, y, progressWidth, barHeight, color)
    }
    
    /**
     * Render frame metadata
     */
    private fun renderMetadata(pixels: IntArray, frameNumber: Long, eventCount: Int) {
        // Frame number in top-left
        renderText(pixels, 10, 20, "Frame: $frameNumber", 0xFFFFFFFF.toInt())
        
        // Event count in top-right
        renderText(pixels, frameWidth - 150, 20, "Events: $eventCount", 0xFFFFFFFF.toInt())
        
        // Timestamp at bottom
        val time = System.currentTimeMillis()
        renderText(pixels, 10, frameHeight - 20, "T: $time", 0xFF808080.toInt())
    }
    
    // Basic drawing primitives
    
    private fun drawCircle(pixels: IntArray, cx: Int, cy: Int, radius: Int, color: Int) {
        for (y in (cy - radius)..(cy + radius)) {
            for (x in (cx - radius)..(cx + radius)) {
                if (x in 0 until frameWidth && y in 0 until frameHeight) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy <= radius * radius) {
                        pixels[y * frameWidth + x] = color
                    }
                }
            }
        }
    }
    
    private fun drawSquare(pixels: IntArray, x: Int, y: Int, size: Int, color: Int) {
        drawRectangle(pixels, x - size/2, y - size/2, size, size, color)
    }
    
    private fun drawRectangle(pixels: IntArray, x: Int, y: Int, width: Int, height: Int, color: Int) {
        for (py in y until (y + height)) {
            for (px in x until (x + width)) {
                if (px in 0 until frameWidth && py in 0 until frameHeight) {
                    pixels[py * frameWidth + px] = color
                }
            }
        }
    }
    
    private fun drawLine(pixels: IntArray, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        // Bresenham's line algorithm
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        
        while (true) {
            if (x in 0 until frameWidth && y in 0 until frameHeight) {
                pixels[y * frameWidth + x] = color
            }
            
            if (x == x1 && y == y1) break
            
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }
    
    private fun renderText(pixels: IntArray, x: Int, y: Int, text: String, color: Int) {
        // Simplified text rendering - just marks position
        // Real implementation would use font rendering
        for (i in text.indices) {
            val px = x + i * 8
            if (px in 0 until (frameWidth - 8) && y in 0 until frameHeight) {
                // Simple dot pattern for each character
                pixels[y * frameWidth + px] = color
            }
        }
    }
    
    private fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF
        
        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF
        
        val r = (r1 + (r2 - r1) * ratio).toInt()
        val g = (g1 + (g2 - g1) * ratio).toInt()
        val b = (b1 + (b2 - b1) * ratio).toInt()
        
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
    
    private fun shouldGenerateFrame(frameNumber: Long): Boolean {
        // Generate frame every second based on frame rate
        return frameNumber % frameRate == 0L
    }
}

/**
 * Video frame data
 */
data class VideoFrame(
    val frameNumber: Long,
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Convert to common video formats
     */
    fun toRGB888(): ByteArray {
        val rgb = ByteArray(width * height * 3)
        var idx = 0
        
        pixels.forEach { pixel ->
            rgb[idx++] = ((pixel shr 16) and 0xFF).toByte() // R
            rgb[idx++] = ((pixel shr 8) and 0xFF).toByte()  // G
            rgb[idx++] = (pixel and 0xFF).toByte()          // B
        }
        
        return rgb
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VideoFrame) return false
        return frameNumber == other.frameNumber && 
               timestamp == other.timestamp &&
               width == other.width &&
               height == other.height
    }
    
    override fun hashCode(): Int {
        return frameNumber.hashCode() * 31 + timestamp.hashCode()
    }
}