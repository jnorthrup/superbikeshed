package fiduciary.protocol

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.*

/**
 * Attention Protocol - Basic attention tracking functionality
 * 
 * From Fiduciary Omnibus Architecture:
 * - Inherits basic attention functionality
 * - Manages AttentionID key relationships
 * - Supports concurrent tubular flows for attention tracking
 */

/**
 * Attention event representation
 */
@Serializable
data class AttentionEvent(
    val id: String,
    val entityId: String,
    val type: AttentionType,
    val intensity: Double, // 0.0 to 1.0
    val timestamp: Long,
    val duration: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class AttentionType {
    FOCUS,
    ENGAGEMENT,
    DISTRACTION,
    BOREDOM,
    INTEREST,
    CONFUSION,
    COMPREHENSION
}

/**
 * Attention aggregation result
 */
@Serializable
data class AttentionAggregation(
    val entityCount: Int,
    val totalIntensity: Double,
    val averageIntensity: Double,
    val maxIntensity: Double,
    val minIntensity: Double,
    val variance: Double
)

/**
 * Attention key for omnibus architecture
 */
@Serializable
data class AttentionKey(
    val attentionId: String,
    val keyType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Base Attention Protocol implementation
 */
open class AttentionProtocol {
    private val attentionEvents = mutableMapOf<String, MutableList<AttentionEvent>>()
    private val attentionKeys = mutableMapOf<String, AttentionKey>()
    private val mutex = Mutex()
    private val decayFactor = 0.95 // Default decay rate
    
    /**
     * Record an attention event
     */
    suspend fun recordAttention(event: AttentionEvent) {
        mutex.withLock {
            attentionEvents.getOrPut(event.entityId) { mutableListOf() }.add(event)
        }
    }
    
    /**
     * Get attention events for entity
     */
    suspend fun getAttentionEvents(entityId: String): List<AttentionEvent> {
        mutex.withLock {
            return attentionEvents[entityId]?.toList() ?: emptyList()
        }
    }
    
    /**
     * Calculate attention score for entity
     */
    suspend fun calculateAttentionScore(entityId: String): Double {
        mutex.withLock {
            val events = attentionEvents[entityId] ?: return 0.0
            if (events.isEmpty()) return 0.0
            
            // Weight recent events more heavily
            val now = System.currentTimeMillis()
            var weightedSum = 0.0
            var totalWeight = 0.0
            
            events.forEach { event ->
                val timeDiff = now - event.timestamp
                val weight = exp(-timeDiff / 3600000.0) // Exponential decay over hours
                weightedSum += event.intensity * weight
                totalWeight += weight
            }
            
            return if (totalWeight > 0) weightedSum / totalWeight else 0.0
        }
    }
    
    /**
     * Apply decay to attention scores
     */
    suspend fun applyDecay(decayRate: Double = decayFactor, timeDeltaMs: Long) {
        mutex.withLock {
            attentionEvents.forEach { (entityId, events) ->
                events.forEach { event ->
                    val decayedIntensity = event.intensity * pow(decayRate, timeDeltaMs / 3600000.0)
                    val index = events.indexOf(event)
                    if (index >= 0) {
                        events[index] = event.copy(intensity = decayedIntensity)
                    }
                }
            }
        }
    }
    
    /**
     * Aggregate attention across multiple entities
     */
    suspend fun aggregateAttention(entityIds: List<String>): AttentionAggregation {
        mutex.withLock {
            val scores = entityIds.map { calculateAttentionScore(it) }
            val validScores = scores.filter { it > 0.0 }
            
            if (validScores.isEmpty()) {
                return AttentionAggregation(0, 0.0, 0.0, 0.0, 0.0, 0.0)
            }
            
            val total = validScores.sum()
            val average = total / validScores.size
            val max = validScores.maxOrNull() ?: 0.0
            val min = validScores.minOrNull() ?: 0.0
            val variance = validScores.map { (it - average).pow(2) }.sum() / validScores.size
            
            return AttentionAggregation(
                entityCount = validScores.size,
                totalIntensity = total,
                averageIntensity = average,
                maxIntensity = max,
                minIntensity = min,
                variance = variance
            )
        }
    }
    
    /**
     * Register attention key
     */
    suspend fun registerAttentionKey(attentionKey: AttentionKey) {
        mutex.withLock {
            attentionKeys[attentionKey.attentionId] = attentionKey
        }
    }
    
    /**
     * Get attention key
     */
    suspend fun getAttentionKey(attentionId: String): AttentionKey? {
        mutex.withLock {
            return attentionKeys[attentionId]
        }
    }
    
    /**
     * Get attention statistics
     */
    suspend fun getAttentionStats(): AttentionStats {
        mutex.withLock {
            val totalEvents = attentionEvents.values.sumOf { it.size }
            val totalEntities = attentionEvents.keys.size
            val avgEventsPerEntity = if (totalEntities > 0) totalEvents.toDouble() / totalEntities else 0.0
            
            return AttentionStats(
                totalEvents = totalEvents,
                totalEntities = totalEntities,
                averageEventsPerEntity = avgEventsPerEntity,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Clear old attention events
     */
    suspend fun clearOldEvents(olderThanMs: Long) {
        mutex.withLock {
            val cutoff = System.currentTimeMillis() - olderThanMs
            attentionEvents.forEach { (_, events) ->
                events.removeAll { it.timestamp < cutoff }
            }
        }
    }
}

/**
 * Attention statistics
 */
@Serializable
data class AttentionStats(
    val totalEvents: Int,
    val totalEntities: Int,
    val averageEventsPerEntity: Double,
    val lastUpdated: Long
)

/**
 * Attention trigger for reactive attention monitoring
 */
@Serializable
data class AttentionTrigger(
    val timestamp: Double,
    val intensity: Double,
    val type: AttentionType
)

/**
 * Attention pattern analysis result
 */
@Serializable
data class AttentionPattern(
    val type: PatternType,
    val confidence: Double,
    val characteristics: Map<String, Double>
)

@Serializable
enum class PatternType {
    SUSTAINED_ATTENTION,
    DECLINING_ATTENTION,
    ENGAGEMENT_RECOVERY,
    SPORADIC_ATTENTION,
    PEAK_ATTENTION
}

/**
 * MemvidAttention - Inherits from AttentionProtocol
 * 
 * Specialized for video attention tracking with:
 * - Temporal attention mapping
 * - Attention heatmaps
 * - Multi-viewer synchronization
 * - Content correlation
 */
class MemvidAttention : AttentionProtocol() {
    private val videoAttentionMaps = mutableMapOf<String, MutableMap<Double, Double>>()
    private val viewerAttentionMaps = mutableMapOf<String, MutableMap<String, MutableMap<Double, Double>>>()
    private val attentionTriggers = mutableMapOf<String, MutableList<(AttentionTrigger) -> Unit>>()
    private val memvidMutex = Mutex()
    
    /**
     * Record video attention at specific timestamp
     */
    suspend fun recordVideoAttention(videoId: String, timestamp: Double, intensity: Double) {
        memvidMutex.withLock {
            videoAttentionMaps.getOrPut(videoId) { mutableMapOf() }[timestamp] = intensity
            
            // Record as base attention event
            recordAttention(AttentionEvent(
                id = "${videoId}-${timestamp}",
                entityId = videoId,
                type = AttentionType.FOCUS,
                intensity = intensity,
                timestamp = (timestamp * 1000).toLong()
            ))
            
            // Check triggers
            checkAttentionTriggers(videoId, timestamp, intensity)
        }
    }
    
    /**
     * Record viewer-specific attention
     */
    suspend fun recordViewerAttention(
        videoId: String,
        viewerId: String,
        timestamp: Double,
        intensity: Double
    ) {
        memvidMutex.withLock {
            viewerAttentionMaps
                .getOrPut(videoId) { mutableMapOf() }
                .getOrPut(viewerId) { mutableMapOf() }[timestamp] = intensity
        }
    }
    
    /**
     * Get video attention map
     */
    suspend fun getVideoAttentionMap(videoId: String): Map<Double, Double> {
        memvidMutex.withLock {
            return videoAttentionMaps[videoId]?.toMap() ?: emptyMap()
        }
    }
    
    /**
     * Get aggregated attention for timestamp across viewers
     */
    suspend fun getAggregatedAttention(videoId: String, timestamp: Double): Double {
        memvidMutex.withLock {
            val viewerMaps = viewerAttentionMaps[videoId] ?: return 0.0
            val attentionValues = viewerMaps.values.mapNotNull { it[timestamp] }
            return if (attentionValues.isNotEmpty()) attentionValues.average() else 0.0
        }
    }
    
    /**
     * Find attention peaks in video
     */
    suspend fun findAttentionPeaks(videoId: String, threshold: Double = 0.8): List<Double> {
        memvidMutex.withLock {
            val attentionMap = videoAttentionMaps[videoId] ?: return emptyList()
            return attentionMap.filter { it.value >= threshold }.keys.toList().sorted()
        }
    }
    
    /**
     * Generate attention heatmap
     */
    suspend fun generateAttentionHeatmap(
        videoId: String,
        videoDuration: Double,
        buckets: Int = 20
    ): List<Double> {
        memvidMutex.withLock {
            val attentionMap = videoAttentionMaps[videoId] ?: return List(buckets) { 0.0 }
            val bucketSize = videoDuration / buckets
            
            return (0 until buckets).map { bucketIndex ->
                val bucketStart = bucketIndex * bucketSize
                val bucketEnd = (bucketIndex + 1) * bucketSize
                
                val bucketValues = attentionMap.filter { (timestamp, _) ->
                    timestamp >= bucketStart && timestamp < bucketEnd
                }.values
                
                bucketValues.average().takeIf { !it.isNaN() } ?: 0.0
            }
        }
    }
    
    /**
     * Correlate attention with content features
     */
    suspend fun correlateWithFeatures(
        videoId: String,
        timestamp: Double,
        contentFeatures: Map<String, Double>
    ): Map<String, Double>? {
        memvidMutex.withLock {
            val attentionMap = videoAttentionMaps[videoId] ?: return null
            val attentionValue = attentionMap[timestamp] ?: return null
            
            return contentFeatures.mapValues { (_, featureValue) ->
                // Simple correlation: higher feature values correlate with higher attention
                val correlation = attentionValue * featureValue
                correlation.coerceIn(0.0, 1.0)
            }
        }
    }
    
    /**
     * Set attention trigger
     */
    suspend fun setAttentionTrigger(
        videoId: String,
        threshold: Double,
        callback: (AttentionTrigger) -> Unit
    ) {
        memvidMutex.withLock {
            attentionTriggers.getOrPut(videoId) { mutableListOf() }.add { trigger ->
                if (trigger.intensity >= threshold) {
                    callback(trigger)
                }
            }
        }
    }
    
    /**
     * Analyze attention pattern
     */
    suspend fun analyzeAttentionPattern(videoId: String): AttentionPattern {
        memvidMutex.withLock {
            val attentionMap = videoAttentionMaps[videoId] ?: return AttentionPattern(
                PatternType.SPORADIC_ATTENTION,
                0.0,
                emptyMap()
            )
            
            val sortedEntries = attentionMap.toList().sortedBy { it.first }
            val values = sortedEntries.map { it.second }
            
            if (values.isEmpty()) {
                return AttentionPattern(PatternType.SPORADIC_ATTENTION, 0.0, emptyMap())
            }
            
            val average = values.average()
            val trend = calculateTrend(values)
            val variance = values.map { (it - average).pow(2) }.sum() / values.size
            
            val patternType = when {
                values.first() > 0.7 && values.last() < 0.5 -> PatternType.DECLINING_ATTENTION
                values.first() < 0.5 && values.last() > 0.7 -> PatternType.ENGAGEMENT_RECOVERY
                average > 0.8 && variance < 0.1 -> PatternType.SUSTAINED_ATTENTION
                values.maxOrNull() ?: 0.0 > 0.9 -> PatternType.PEAK_ATTENTION
                else -> PatternType.SPORADIC_ATTENTION
            }
            
            return AttentionPattern(
                type = patternType,
                confidence = 1.0 - variance, // Lower variance = higher confidence
                characteristics = mapOf(
                    "average" to average,
                    "trend" to trend,
                    "variance" to variance,
                    "peak" to (values.maxOrNull() ?: 0.0)
                )
            )
        }
    }
    
    /**
     * Synchronize with another MemvidAttention instance
     */
    suspend fun synchronizeWith(other: MemvidAttention): MemvidAttention {
        val result = MemvidAttention()
        
        memvidMutex.withLock {
            // Merge video attention maps
            val allVideoIds = (this.videoAttentionMaps.keys + other.videoAttentionMaps.keys).toSet()
            
            allVideoIds.forEach { videoId ->
                val thisMap = this.videoAttentionMaps[videoId] ?: emptyMap()
                val otherMap = other.videoAttentionMaps[videoId] ?: emptyMap()
                
                // Merge maps, taking maximum attention value for each timestamp
                val mergedMap = (thisMap + otherMap).toMap()
                result.videoAttentionMaps[videoId] = mergedMap.toMutableMap()
            }
            
            // Merge viewer attention maps
            val allViewerVideoIds = (this.viewerAttentionMaps.keys + other.viewerAttentionMaps.keys).toSet()
            
            allViewerVideoIds.forEach { videoId ->
                val thisViewerMap = this.viewerAttentionMaps[videoId] ?: emptyMap()
                val otherViewerMap = other.viewerAttentionMaps[videoId] ?: emptyMap()
                
                val mergedViewerMap = mutableMapOf<String, MutableMap<Double, Double>>()
                
                // Merge viewer maps
                (thisViewerMap.keys + otherViewerMap.keys).forEach { viewerId ->
                    val thisViewer = thisViewerMap[viewerId] ?: emptyMap()
                    val otherViewer = otherViewerMap[viewerId] ?: emptyMap()
                    mergedViewerMap[viewerId] = (thisViewer + otherViewer).toMutableMap()
                }
                
                result.viewerAttentionMaps[videoId] = mergedViewerMap
            }
        }
        
        return result
    }
    
    /**
     * Check attention triggers
     */
    private fun checkAttentionTriggers(videoId: String, timestamp: Double, intensity: Double) {
        val triggers = attentionTriggers[videoId] ?: return
        val trigger = AttentionTrigger(timestamp, intensity, AttentionType.FOCUS)
        
        triggers.forEach { callback ->
            callback(trigger)
        }
    }
    
    /**
     * Calculate trend in attention values
     */
    private fun calculateTrend(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        
        val n = values.size
        val x = (0 until n).map { it.toDouble() }
        val y = values
        
        val meanX = x.average()
        val meanY = y.average()
        
        var numerator = 0.0
        var denominator = 0.0
        
        for (i in 0 until n) {
            numerator += (x[i] - meanX) * (y[i] - meanY)
            denominator += (x[i] - meanX).pow(2)
        }
        
        return if (denominator != 0.0) numerator / denominator else 0.0
    }
}