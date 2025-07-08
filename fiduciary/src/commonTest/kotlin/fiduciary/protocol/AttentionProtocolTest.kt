package fiduciary.protocol

import kotlin.test.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.test.runTest

/**
 * TDD Test Suite for Attention Protocol and Memvid Attention
 * 
 * Based on Fiduciary Omnibus Architecture:
 * - Attention Protocol inherits basic attention functionality
 * - Memvid Attention inherits from Attention Protocol
 * - AttentionID key relationship management
 * - Concurrent tubular flows for attention tracking
 */
class AttentionProtocolTest {
    
    @Test
    fun `Attention Protocol should track attention events`() = runTest {
        // Given
        val attentionProtocol = AttentionProtocol()
        val entityId = "entity-123"
        val attentionEvent = AttentionEvent(
            id = "attention-1",
            entityId = entityId,
            type = AttentionType.FOCUS,
            intensity = 0.8,
            timestamp = System.currentTimeMillis()
        )
        
        // When
        attentionProtocol.recordAttention(attentionEvent)
        val events = attentionProtocol.getAttentionEvents(entityId)
        
        // Then
        assertEquals(1, events.size)
        assertEquals(attentionEvent.id, events[0].id)
        assertEquals(attentionEvent.intensity, events[0].intensity)
    }
    
    @Test
    fun `Attention Protocol should calculate attention scores`() = runTest {
        // Given
        val attentionProtocol = AttentionProtocol()
        val entityId = "scored-entity"
        val events = listOf(
            AttentionEvent("att-1", entityId, AttentionType.FOCUS, 0.7, 1000L),
            AttentionEvent("att-2", entityId, AttentionType.ENGAGEMENT, 0.9, 2000L),
            AttentionEvent("att-3", entityId, AttentionType.DISTRACTION, 0.3, 3000L)
        )
        
        // When
        events.forEach { attentionProtocol.recordAttention(it) }
        val score = attentionProtocol.calculateAttentionScore(entityId)
        
        // Then
        assertTrue(score > 0.0)
        assertTrue(score <= 1.0)
        // Score should be weighted average: (0.7 + 0.9 + 0.3) / 3 ≈ 0.63
        assertEquals(0.63, score, 0.1)
    }
    
    @Test
    fun `Attention Protocol should handle attention decay`() = runTest {
        // Given
        val attentionProtocol = AttentionProtocol()
        val entityId = "decay-entity"
        val oldEvent = AttentionEvent(
            id = "old-attention",
            entityId = entityId,
            type = AttentionType.FOCUS,
            intensity = 1.0,
            timestamp = System.currentTimeMillis() - 3600000 // 1 hour ago
        )
        
        // When
        attentionProtocol.recordAttention(oldEvent)
        attentionProtocol.applyDecay(decayRate = 0.5, timeDeltaMs = 3600000)
        val currentScore = attentionProtocol.calculateAttentionScore(entityId)
        
        // Then
        assertTrue(currentScore < 1.0, "Attention should decay over time")
        assertEquals(0.5, currentScore, 0.1)
    }
    
    @Test
    fun `Attention Protocol should support attention aggregation`() = runTest {
        // Given
        val attentionProtocol = AttentionProtocol()
        val entityIds = listOf("entity-1", "entity-2", "entity-3")
        
        entityIds.forEach { entityId ->
            attentionProtocol.recordAttention(
                AttentionEvent(
                    id = "att-$entityId",
                    entityId = entityId,
                    type = AttentionType.FOCUS,
                    intensity = 0.8,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        
        // When
        val aggregated = attentionProtocol.aggregateAttention(entityIds)
        
        // Then
        assertEquals(3, aggregated.entityCount)
        assertEquals(2.4, aggregated.totalIntensity, 0.1) // 3 * 0.8
        assertEquals(0.8, aggregated.averageIntensity, 0.1)
    }
    
    @Test
    fun `Memvid Attention should inherit from Attention Protocol`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        
        // When
        val isAttentionProtocol = memvidAttention is AttentionProtocol
        
        // Then
        assertTrue(isAttentionProtocol, "MemvidAttention should inherit from AttentionProtocol")
    }
    
    @Test
    fun `Memvid Attention should track video attention`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        val videoId = "video-123"
        val timestamp = 45.0 // 45 seconds into video
        
        // When
        memvidAttention.recordVideoAttention(videoId, timestamp, 0.9)
        val attentionMap = memvidAttention.getVideoAttentionMap(videoId)
        
        // Then
        assertTrue(attentionMap.containsKey(timestamp))
        assertEquals(0.9, attentionMap[timestamp])
    }
    
    @Test
    fun `Memvid Attention should identify attention peaks`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        val videoId = "peak-video"
        val attentionData = mapOf(
            10.0 to 0.3,
            20.0 to 0.7,
            30.0 to 0.9, // Peak
            40.0 to 0.6,
            50.0 to 0.8,
            60.0 to 0.4
        )
        
        // When
        attentionData.forEach { (timestamp, intensity) ->
            memvidAttention.recordVideoAttention(videoId, timestamp, intensity)
        }
        val peaks = memvidAttention.findAttentionPeaks(videoId, threshold = 0.8)
        
        // Then
        assertEquals(2, peaks.size)
        assertTrue(peaks.contains(30.0))
        assertTrue(peaks.contains(50.0))
    }
    
    @Test
    fun `Memvid Attention should generate attention heatmap`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        val videoId = "heatmap-video"
        val videoDuration = 120.0 // 2 minutes
        
        // Record attention at various points
        for (i in 0..11) {
            val timestamp = i * 10.0
            val intensity = 0.5 + 0.3 * kotlin.math.sin(i * 0.5) // Sine wave pattern
            memvidAttention.recordVideoAttention(videoId, timestamp, intensity)
        }
        
        // When
        val heatmap = memvidAttention.generateAttentionHeatmap(videoId, videoDuration, buckets = 12)
        
        // Then
        assertEquals(12, heatmap.size)
        assertTrue(heatmap.all { it >= 0.0 && it <= 1.0 })
    }
    
    @Test
    fun `Memvid Attention should correlate with content features`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        val videoId = "feature-video"
        val contentFeatures = mapOf(
            "speech_rate" to 0.7,
            "scene_change" to 0.8,
            "face_detection" to 0.9,
            "text_overlay" to 0.6
        )
        
        // When
        memvidAttention.recordVideoAttention(videoId, 30.0, 0.85)
        val correlation = memvidAttention.correlateWithFeatures(videoId, 30.0, contentFeatures)
        
        // Then
        assertNotNull(correlation)
        assertTrue(correlation.containsKey("face_detection"))
        assertTrue(correlation["face_detection"]!! > 0.5) // Should correlate with high attention
    }
    
    @Test
    fun `Memvid Attention should support attention triggers`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        val videoId = "trigger-video"
        val triggers = mutableListOf<AttentionTrigger>()
        
        // When
        memvidAttention.setAttentionTrigger(videoId, threshold = 0.8) { trigger ->
            triggers.add(trigger)
        }
        
        memvidAttention.recordVideoAttention(videoId, 25.0, 0.9) // Above threshold
        memvidAttention.recordVideoAttention(videoId, 35.0, 0.7) // Below threshold
        
        // Then
        assertEquals(1, triggers.size)
        assertEquals(25.0, triggers[0].timestamp)
        assertEquals(0.9, triggers[0].intensity)
    }
    
    @Test
    fun `Memvid Attention should track attention patterns`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        val videoId = "pattern-video"
        
        // Simulate viewing pattern: high attention at start, dip in middle, spike at end
        val pattern = listOf(
            0.0 to 0.9,   // High start
            10.0 to 0.8,
            20.0 to 0.6,
            30.0 to 0.4,  // Dip
            40.0 to 0.5,
            50.0 to 0.9   // Spike at end
        )
        
        // When
        pattern.forEach { (timestamp, intensity) ->
            memvidAttention.recordVideoAttention(videoId, timestamp, intensity)
        }
        
        val attentionPattern = memvidAttention.analyzeAttentionPattern(videoId)
        
        // Then
        assertEquals(PatternType.ENGAGEMENT_RECOVERY, attentionPattern.type)
        assertTrue(attentionPattern.confidence > 0.5)
    }
    
    @Test
    fun `Attention Protocol should support attention key management`() = runTest {
        // Given
        val attentionProtocol = AttentionProtocol()
        val attentionKey = AttentionKey("attention-789", "VIDEO_ATTENTION")
        
        // When
        attentionProtocol.registerAttentionKey(attentionKey)
        val retrievedKey = attentionProtocol.getAttentionKey(attentionKey.attentionId)
        
        // Then
        assertNotNull(retrievedKey)
        assertEquals(attentionKey.attentionId, retrievedKey.attentionId)
        assertEquals(attentionKey.keyType, retrievedKey.keyType)
    }
    
    @Test
    fun `Memvid Attention should handle multi-viewer attention`() = runTest {
        // Given
        val memvidAttention = MemvidAttention()
        val videoId = "multi-viewer-video"
        val viewers = listOf("viewer-1", "viewer-2", "viewer-3")
        
        // When
        viewers.forEach { viewerId ->
            memvidAttention.recordViewerAttention(videoId, viewerId, 30.0, 0.7 + (viewerId.last().digitToInt() * 0.1))
        }
        
        val aggregatedAttention = memvidAttention.getAggregatedAttention(videoId, 30.0)
        
        // Then
        assertEquals(0.8, aggregatedAttention, 0.05) // Average of 0.7, 0.8, 0.9
    }
    
    @Test
    fun `Memvid Attention should support attention synchronization`() = runTest {
        // Given
        val memvidAttention1 = MemvidAttention()
        val memvidAttention2 = MemvidAttention()
        val videoId = "sync-video"
        
        // When
        memvidAttention1.recordVideoAttention(videoId, 15.0, 0.6)
        memvidAttention2.recordVideoAttention(videoId, 25.0, 0.8)
        
        val synchronized = memvidAttention1.synchronizeWith(memvidAttention2)
        val combinedMap = synchronized.getVideoAttentionMap(videoId)
        
        // Then
        assertEquals(2, combinedMap.size)
        assertTrue(combinedMap.containsKey(15.0))
        assertTrue(combinedMap.containsKey(25.0))
    }
    
    @Test
    fun `Attention Protocol should handle concurrent attention recording`() = runTest {
        // Given
        val attentionProtocol = AttentionProtocol()
        val entityId = "concurrent-entity"
        val eventCount = 100
        
        // When
        repeat(eventCount) { i ->
            attentionProtocol.recordAttention(
                AttentionEvent(
                    id = "concurrent-$i",
                    entityId = entityId,
                    type = AttentionType.FOCUS,
                    intensity = 0.5,
                    timestamp = System.currentTimeMillis() + i
                )
            )
        }
        
        val events = attentionProtocol.getAttentionEvents(entityId)
        
        // Then
        assertEquals(eventCount, events.size)
        assertTrue(events.all { it.entityId == entityId })
    }
}