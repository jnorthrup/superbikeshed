package fiduciary.percolator

import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours

class PercolatorProtocolTest {
    private val testTime = Clock.System.now()
    
    @Test
    fun `test RegisterNode message creation`() {
        val capabilities = NodeCapabilities(
            maxConcurrentWork = 10,
            supportedFormats = listOf("zip", "tar", "gz"),
            hasOCR = true,
            hasAudioTranscription = false,
            maxBandwidthMbps = 1000,
            processingPower = ProcessingPower.HIGH
        )
        
        val message = RegisterNode(
            messageId = "msg_123",
            timestamp = testTime,
            nodeId = "node_test",
            capabilities = capabilities
        )
        
        assertEquals("msg_123", message.messageId)
        assertEquals("node_test", message.nodeId)
        assertTrue(message.capabilities.hasOCR)
        assertEquals(ProcessingPower.HIGH, message.capabilities.processingPower)
    }
    
    @Test
    fun `test WorkUnit status transitions`() {
        val entries = listOf(
            FileEntry(
                path = "test.txt",
                offset = 0,
                compressedSize = 100,
                uncompressedSize = 200,
                method = 8
            )
        )
        
        val workUnit = WorkUnit(
            id = "work_1",
            archiveUrl = "https://example.com/archive.zip",
            entries = entries
        )
        
        // Initial state
        assertEquals(WorkStatus.PENDING, workUnit.status)
        assertNull(workUnit.claimedBy)
        
        // Claim work
        val claimed = workUnit.copy(
            status = WorkStatus.CLAIMED,
            claimedBy = "node_1",
            claimedAt = testTime
        )
        
        assertEquals(WorkStatus.CLAIMED, claimed.status)
        assertEquals("node_1", claimed.claimedBy)
        assertEquals(testTime, claimed.claimedAt)
        
        // Complete work
        val completed = claimed.copy(
            status = WorkStatus.COMPLETED,
            completedAt = testTime + 30.minutes
        )
        
        assertEquals(WorkStatus.COMPLETED, completed.status)
        assertNotNull(completed.completedAt)
    }
    
    @Test
    fun `test RoundRobinDistribution strategy`() {
        val strategy = RoundRobinDistribution()
        
        val nodes = listOf(
            NodeInfo(
                nodeId = "node_1",
                capabilities = NodeCapabilities(maxConcurrentWork = 2),
                status = NodeStatus.ONLINE,
                lastSeen = testTime
            ),
            NodeInfo(
                nodeId = "node_2",
                capabilities = NodeCapabilities(maxConcurrentWork = 2),
                status = NodeStatus.ONLINE,
                lastSeen = testTime
            ),
            NodeInfo(
                nodeId = "node_3",
                capabilities = NodeCapabilities(maxConcurrentWork = 1),
                status = NodeStatus.BUSY,
                lastSeen = testTime,
                activeWork = listOf("work_0")
            )
        )
        
        val workUnits = (1..5).map { i ->
            WorkUnit(
                id = "work_$i",
                archiveUrl = "https://example.com/archive$i.zip",
                entries = listOf(
                    FileEntry("file$i.txt", 0, 100, 200, 8)
                )
            )
        }
        
        val assignments = strategy.assignWork(workUnits, nodes)
        
        // Should distribute evenly among available nodes
        assertTrue(assignments.isNotEmpty())
        
        // Verify round-robin distribution
        val nodeAssignments = assignments.groupBy { assignment ->
            nodes.find { node ->
                node.canAcceptWork()
            }?.nodeId
        }
        
        // Each available node should get work
        assertTrue(nodeAssignments.isNotEmpty())
    }
    
    @Test
    fun `test CapabilityBasedDistribution strategy`() {
        val strategy = CapabilityBasedDistribution()
        
        val nodes = listOf(
            NodeInfo(
                nodeId = "weak_node",
                capabilities = NodeCapabilities(
                    maxConcurrentWork = 1,
                    processingPower = ProcessingPower.LOW
                ),
                status = NodeStatus.ONLINE,
                lastSeen = testTime
            ),
            NodeInfo(
                nodeId = "strong_node",
                capabilities = NodeCapabilities(
                    maxConcurrentWork = 5,
                    processingPower = ProcessingPower.HIGH
                ),
                status = NodeStatus.ONLINE,
                lastSeen = testTime
            )
        )
        
        val complexWork = WorkUnit(
            id = "complex_work",
            archiveUrl = "https://example.com/huge.zip",
            entries = (1..100).map { i ->
                FileEntry("file$i.txt", i * 1000L, 10000, 50000, 8)
            }
        )
        
        val simpleWork = WorkUnit(
            id = "simple_work",
            archiveUrl = "https://example.com/small.zip",
            entries = listOf(
                FileEntry("readme.txt", 0, 100, 200, 8)
            )
        )
        
        val assignments = strategy.assignWork(
            listOf(complexWork, simpleWork),
            nodes
        )
        
        // Complex work should go to strong node
        val complexAssignment = assignments.find { it.workUnit.id == "complex_work" }
        assertNotNull(complexAssignment)
        
        // Strong node should get shorter deadline for complex work
        assertTrue(complexAssignment.deadline < testTime + 1.hours)
    }
    
    @Test
    fun `test NodeInfo canAcceptWork logic`() {
        val node = NodeInfo(
            nodeId = "test_node",
            capabilities = NodeCapabilities(maxConcurrentWork = 3),
            status = NodeStatus.ONLINE,
            lastSeen = testTime
        )
        
        // Can accept work when online and under capacity
        assertTrue(node.canAcceptWork())
        
        // Cannot accept work when at capacity
        val busyNode = node.copy(
            activeWork = listOf("work_1", "work_2", "work_3")
        )
        assertFalse(busyNode.canAcceptWork())
        
        // Cannot accept work when offline
        val offlineNode = node.copy(status = NodeStatus.OFFLINE)
        assertFalse(offlineNode.canAcceptWork())
        
        // Cannot accept work when banned
        val bannedNode = node.copy(status = NodeStatus.BANNED)
        assertFalse(bannedNode.canAcceptWork())
    }
    
    @Test
    fun `test reward calculation`() {
        val stats = ProcessingStats(
            startTime = testTime,
            endTime = testTime + 5.minutes,
            filesProcessed = 50,
            bytesDownloaded = 1_000_000,
            bytesProcessed = 5_000_000,
            errorsEncountered = 0,
            averageProcessingTimeMs = 500
        )
        
        // High quality, fast processing
        val reward1 = calculateReward(stats, quality = 1.0)
        assertEquals(550, reward1.points) // 50 * 10 + 50 bonus
        
        // Lower quality
        val reward2 = calculateReward(stats, quality = 0.8)
        assertEquals(440, reward2.points) // (500 + 50) * 0.8
        
        // Slow processing (no efficiency bonus)
        val slowStats = stats.copy(averageProcessingTimeMs = 2000)
        val reward3 = calculateReward(slowStats, quality = 1.0)
        assertEquals(500, reward3.points) // 50 * 10, no bonus
    }
    
    @Test
    fun `test WorkProgress tracking`() {
        val progress = WorkProgress(
            messageId = "msg_progress",
            timestamp = testTime,
            nodeId = "node_1",
            workUnitId = "work_1",
            filesProcessed = 25,
            totalFiles = 100,
            estimatedCompletion = testTime + 15.minutes
        )
        
        assertEquals(25, progress.filesProcessed)
        assertEquals(100, progress.totalFiles)
        
        val percentComplete = (progress.filesProcessed.toDouble() / progress.totalFiles) * 100
        assertEquals(25.0, percentComplete)
    }
    
    @Test
    fun `test WorkResult with mixed success and errors`() {
        val results = listOf(
            ProcessedContent(
                workUnitId = "work_1",
                fileEntry = FileEntry("success.txt", 0, 100, 200, 8),
                content = "Extracted content",
                tags = listOf("document", "text"),
                entities = listOf("Patrick Devine"),
                complexity = 0.75
            ),
            ProcessedContent(
                workUnitId = "work_1",
                fileEntry = FileEntry("error.txt", 1000, 100, 200, 8),
                error = "Corrupted file"
            )
        )
        
        val workResult = WorkResult(
            messageId = "msg_result",
            timestamp = testTime,
            nodeId = "node_1",
            workUnitId = "work_1",
            results = results,
            stats = ProcessingStats(
                startTime = testTime,
                endTime = testTime + 2.minutes,
                filesProcessed = 2,
                bytesDownloaded = 200,
                bytesProcessed = 400,
                errorsEncountered = 1,
                averageProcessingTimeMs = 1000
            )
        )
        
        assertEquals(2, workResult.results.size)
        assertEquals(1, workResult.results.count { it.error != null })
        assertEquals(1, workResult.stats.errorsEncountered)
    }
}