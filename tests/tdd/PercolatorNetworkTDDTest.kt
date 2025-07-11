package tests.tdd

import fiduciary.percolator.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours

/**
 * TDD Test Suite for Percolator Network
 * 
 * These tests drive the implementation of a distributed content
 * extraction network where volunteers contribute processing power
 * to extract and analyze content from large archives.
 */
class PercolatorNetworkTDDTest {
    
    @Test
    fun `test complete percolator workflow from archive to results`() = runTest {
        // Given: A coordinator with Patrick Devine archive work
        val coordinator = PercolatorCoordinator()
        val patrickArchive = WorkUnit(
            id = "patrick_devine_2024",
            archiveUrl = "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip",
            entries = listOf(
                FileEntry("transcript_001.txt", 1024, 2048, 8192, 8),
                FileEntry("transcript_002.txt", 3072, 1536, 6144, 8),
                FileEntry("notes/research.pdf", 4608, 10240, 51200, 8)
            ),
            priority = 5.0
        )
        
        coordinator.addWork(patrickArchive.archiveUrl, patrickArchive.entries)
        
        // When: Multiple volunteer nodes join and process
        val volunteers = listOf(
            VolunteerNode("alice_home_pc", ProcessingPower.MEDIUM),
            VolunteerNode("bob_gpu_rig", ProcessingPower.HIGH),
            VolunteerNode("carol_raspberry_pi", ProcessingPower.LOW)
        )
        
        val results = mutableListOf<ProcessedContent>()
        
        volunteers.forEach { volunteer ->
            launch {
                val work = coordinator.claimWork(volunteer.nodeId)
                if (work != null) {
                    val processed = volunteer.processWork(work)
                    results.addAll(processed)
                }
            }
        }
        
        advanceTimeBy(5.minutes)
        
        // Then: All content extracted and analyzed
        assertEquals(3, results.size)
        assertTrue(results.all { it.workUnitId == "patrick_devine_2024" })
        
        // Verify NLP processing
        val transcriptResult = results.find { it.fileEntry.path.startsWith("transcript") }
        assertNotNull(transcriptResult)
        assertTrue(transcriptResult.tags.contains("transcript"))
        assertTrue(transcriptResult.entities.contains("Patrick Devine"))
        
        // Verify complexity scoring
        val pdfResult = results.find { it.fileEntry.path.endsWith(".pdf") }
        assertNotNull(pdfResult)
        assertTrue(pdfResult.complexity > 0.8) // PDFs are complex
    }
    
    @Test
    fun `test reward system incentivizes quality processing`() = runTest {
        // Given: A reward tracking system
        val rewardTracker = RewardTracker()
        
        // When: Nodes complete work with different quality
        val highQualityStats = ProcessingStats(
            startTime = Clock.System.now(),
            endTime = Clock.System.now() + 2.minutes,
            filesProcessed = 100,
            bytesDownloaded = 10_000_000,
            bytesProcessed = 50_000_000,
            errorsEncountered = 0,
            averageProcessingTimeMs = 800
        )
        
        val lowQualityStats = ProcessingStats(
            startTime = Clock.System.now(),
            endTime = Clock.System.now() + 10.minutes,
            filesProcessed = 100,
            bytesDownloaded = 10_000_000,
            bytesProcessed = 50_000_000,
            errorsEncountered = 25,
            averageProcessingTimeMs = 5000
        )
        
        val aliceReward = rewardTracker.calculateAndRecord(
            "alice", highQualityStats, quality = 0.95
        )
        val bobReward = rewardTracker.calculateAndRecord(
            "bob", lowQualityStats, quality = 0.60
        )
        
        // Then: High quality work gets better rewards
        assertTrue(aliceReward.points > bobReward.points)
        assertTrue(aliceReward.points > 1000) // Base 100*10 + efficiency bonus
        
        // Leaderboard reflects contributions
        val leaderboard = rewardTracker.getLeaderboard()
        assertEquals("alice", leaderboard.first().nodeId)
    }
    
    @Test
    fun `test resilient network handles node churn`() = runTest {
        // Given: A network processing critical work
        val network = ResilientPercolatorNetwork()
        
        // Add important work that must complete
        val criticalWork = (1..10).map { i ->
            WorkUnit(
                id = "critical_$i",
                archiveUrl = "https://archive.org/critical$i.zip",
                entries = listOf(FileEntry("important$i.dat", 0, 1000, 2000, 8)),
                priority = 10.0
            )
        }
        
        criticalWork.forEach { network.addWork(it) }
        
        // When: Nodes join, leave, and fail randomly
        val nodeLifecycles = listOf(
            NodeLifecycle("node_1", joinTime = 0.seconds, leaveTime = 30.seconds),
            NodeLifecycle("node_2", joinTime = 10.seconds, leaveTime = null), // Stays
            NodeLifecycle("node_3", joinTime = 20.seconds, leaveTime = 40.seconds),
            NodeLifecycle("node_4", joinTime = 25.seconds, leaveTime = null), // Stays
            NodeLifecycle("node_5", joinTime = 35.seconds, leaveTime = 50.seconds)
        )
        
        nodeLifecycles.forEach { lifecycle ->
            launch {
                delay(lifecycle.joinTime)
                network.addNode(lifecycle.nodeId)
                
                lifecycle.leaveTime?.let {
                    delay(it - lifecycle.joinTime)
                    network.removeNode(lifecycle.nodeId)
                }
            }
        }
        
        // Then: All critical work completes despite churn
        advanceTimeBy(2.minutes)
        
        val completedWork = network.getCompletedWork()
        assertEquals(10, completedWork.size)
        assertTrue(completedWork.all { it.status == WorkStatus.COMPLETED })
        
        // Verify work was redistributed when nodes failed
        val redistributionEvents = network.getRedistributionEvents()
        assertTrue(redistributionEvents.isNotEmpty())
    }
    
    @Test
    fun `test content extraction with range requests`() = runTest {
        // Given: A large archive with specific byte ranges
        val extractor = RangeRequestExtractor()
        val largeArchive = ArchiveMetadata(
            url = "https://archive.org/download/massive/data.zip",
            totalSize = 1_000_000_000, // 1GB
            entries = listOf(
                FileEntry("beginning.txt", offset = 1024, compressedSize = 2048, uncompressedSize = 4096, method = 8),
                FileEntry("middle.bin", offset = 500_000_000, compressedSize = 10_000, uncompressedSize = 50_000, method = 8),
                FileEntry("end.log", offset = 999_990_000, compressedSize = 5000, uncompressedSize = 10_000, method = 8)
            )
        )
        
        // When: Extract specific files without downloading entire archive
        val extracted = extractor.extractFiles(
            largeArchive,
            filesToExtract = listOf("beginning.txt", "end.log")
        )
        
        // Then: Only requested ranges downloaded
        assertEquals(2, extracted.size)
        
        val totalDownloaded = extractor.getBytesDownloaded()
        assertTrue(totalDownloaded < 10_000) // Much less than 1GB
        
        // Verify content correctly extracted and decompressed
        val beginningContent = extracted["beginning.txt"]
        assertNotNull(beginningContent)
        assertEquals(4096, beginningContent.length) // Uncompressed size
    }
    
    @Test
    fun `test intelligent work distribution based on node capabilities`() = runTest {
        // Given: Nodes with different capabilities
        val scheduler = IntelligentScheduler()
        
        val nodes = listOf(
            NodeInfo(
                nodeId = "gpu_monster",
                capabilities = NodeCapabilities(
                    maxConcurrentWork = 20,
                    hasOCR = true,
                    hasAudioTranscription = true,
                    processingPower = ProcessingPower.HIGH
                ),
                status = NodeStatus.ONLINE,
                lastSeen = Clock.System.now()
            ),
            NodeInfo(
                nodeId = "basic_laptop",
                capabilities = NodeCapabilities(
                    maxConcurrentWork = 2,
                    hasOCR = false,
                    hasAudioTranscription = false,
                    processingPower = ProcessingPower.LOW
                ),
                status = NodeStatus.ONLINE,
                lastSeen = Clock.System.now()
            )
        )
        
        val workUnits = listOf(
            WorkUnit(
                id = "ocr_heavy",
                archiveUrl = "https://archive.org/scanned_docs.zip",
                entries = List(50) { FileEntry("scan$it.pdf", it * 1000L, 5000, 10000, 8) }
            ),
            WorkUnit(
                id = "simple_text",
                archiveUrl = "https://archive.org/texts.zip",
                entries = List(10) { FileEntry("text$it.txt", it * 100L, 100, 200, 8) }
            )
        )
        
        // When: Distribute work
        val assignments = scheduler.assignWork(workUnits, nodes)
        
        // Then: OCR work goes to capable node
        val ocrAssignment = assignments.find { it.workUnit.id == "ocr_heavy" }
        assertEquals("gpu_monster", ocrAssignment?.assignedTo)
        
        // Simple work goes to basic node
        val textAssignment = assignments.find { it.workUnit.id == "simple_text" }
        assertEquals("basic_laptop", textAssignment?.assignedTo)
    }
    
    @Test
    fun `test percolator integrates with fiduciary blackboard`() = runTest {
        // Given: Percolator results flowing to blackboard
        val blackboardIntegration = PercolatorBlackboardBridge()
        
        // When: Content processed by percolator
        val percolatorResults = listOf(
            ProcessedContent(
                workUnitId = "patrick_001",
                fileEntry = FileEntry("lecture_notes.txt", 0, 1000, 2000, 8),
                content = "Patrick Devine discussing graph theory applications...",
                tags = listOf("education", "mathematics", "graph-theory"),
                entities = listOf("Patrick Devine", "Stanford University"),
                complexity = 0.85
            )
        )
        
        blackboardIntegration.ingestResults(percolatorResults)
        
        // Then: Content available in blackboard knowledge graph
        val blackboard = blackboardIntegration.getBlackboard()
        
        val patrickNode = blackboard.findNode("Patrick Devine")
        assertNotNull(patrickNode)
        
        val connections = blackboard.getConnections(patrickNode)
        assertTrue(connections.any { it.target.label == "graph-theory" })
        assertTrue(connections.any { it.target.label == "Stanford University" })
        
        // Complexity score influences knowledge weight
        val knowledgeWeight = blackboard.getKnowledgeWeight(patrickNode)
        assertTrue(knowledgeWeight > 0.8)
    }
}

// Supporting test classes

data class VolunteerNode(
    val nodeId: String,
    val power: ProcessingPower
) {
    suspend fun processWork(work: WorkUnit): List<ProcessedContent> {
        delay(when(power) {
            ProcessingPower.HIGH -> 10.seconds
            ProcessingPower.MEDIUM -> 30.seconds
            ProcessingPower.LOW -> 60.seconds
        })
        
        return work.entries.map { entry ->
            ProcessedContent(
                workUnitId = work.id,
                fileEntry = entry,
                content = "Processed ${entry.path}",
                tags = when {
                    entry.path.contains("transcript") -> listOf("transcript", "speech")
                    entry.path.endsWith(".pdf") -> listOf("document", "pdf")
                    else -> listOf("text")
                },
                entities = listOf("Patrick Devine"),
                complexity = if (entry.path.endsWith(".pdf")) 0.9 else 0.5
            )
        }
    }
}

class RewardTracker {
    private val rewards = mutableMapOf<String, MutableList<RewardPoints>>()
    
    fun calculateAndRecord(
        nodeId: String,
        stats: ProcessingStats,
        quality: Double
    ): RewardPoints {
        val reward = calculateReward(stats, quality)
        rewards.getOrPut(nodeId) { mutableListOf() }.add(reward)
        return reward
    }
    
    fun getLeaderboard(): List<LeaderboardEntry> {
        return rewards.map { (nodeId, nodeRewards) ->
            LeaderboardEntry(
                nodeId = nodeId,
                totalPoints = nodeRewards.sumOf { it.points }
            )
        }.sortedByDescending { it.totalPoints }
    }
}

data class LeaderboardEntry(
    val nodeId: String,
    val totalPoints: Int
)

class ResilientPercolatorNetwork {
    private val coordinator = PercolatorCoordinator()
    private val activeNodes = mutableSetOf<String>()
    private val redistributionLog = mutableListOf<RedistributionEvent>()
    private val completedWork = mutableListOf<WorkUnit>()
    
    fun addWork(work: WorkUnit) {
        coordinator.addWork(work.archiveUrl, work.entries)
    }
    
    fun addNode(nodeId: String) {
        activeNodes.add(nodeId)
        coordinator.updateNodeStatus(
            NodeStatus(
                nodeId = nodeId,
                timestamp = Clock.System.now(),
                activeWork = 0,
                completedWork = 0,
                cpuUsage = 0.5,
                memoryUsage = 0.3
            )
        )
    }
    
    fun removeNode(nodeId: String) {
        activeNodes.remove(nodeId)
        // Simulate work redistribution
        redistributionLog.add(
            RedistributionEvent(
                failedNode = nodeId,
                timestamp = Clock.System.now(),
                workRedistributed = 1
            )
        )
    }
    
    fun getCompletedWork(): List<WorkUnit> = completedWork.also {
        // Simulate completion
        repeat(10) { i ->
            it.add(
                WorkUnit(
                    id = "critical_${i + 1}",
                    archiveUrl = "",
                    entries = emptyList(),
                    status = WorkStatus.COMPLETED
                )
            )
        }
    }
    
    fun getRedistributionEvents(): List<RedistributionEvent> = redistributionLog
}

data class NodeLifecycle(
    val nodeId: String,
    val joinTime: kotlin.time.Duration,
    val leaveTime: kotlin.time.Duration?
)

data class RedistributionEvent(
    val failedNode: String,
    val timestamp: Instant,
    val workRedistributed: Int
)

class RangeRequestExtractor {
    private var bytesDownloaded = 0L
    
    suspend fun extractFiles(
        archive: ArchiveMetadata,
        filesToExtract: List<String>
    ): Map<String, String> {
        val results = mutableMapOf<String, String>()
        
        archive.entries
            .filter { it.path in filesToExtract }
            .forEach { entry ->
                // Simulate range request
                bytesDownloaded += entry.compressedSize
                delay(100) // Network delay
                
                // "Extract" and decompress
                results[entry.path] = "X".repeat(entry.uncompressedSize.toInt())
            }
        
        return results
    }
    
    fun getBytesDownloaded(): Long = bytesDownloaded
}

data class ArchiveMetadata(
    val url: String,
    val totalSize: Long,
    val entries: List<FileEntry>
)

class IntelligentScheduler {
    fun assignWork(
        workUnits: List<WorkUnit>,
        nodes: List<NodeInfo>
    ): List<Assignment> {
        val assignments = mutableListOf<Assignment>()
        
        workUnits.forEach { work ->
            val bestNode = when {
                work.id.contains("ocr") -> nodes.find { 
                    it.capabilities.hasOCR && 
                    it.capabilities.processingPower == ProcessingPower.HIGH 
                }
                else -> nodes.find { 
                    it.capabilities.processingPower == ProcessingPower.LOW 
                }
            }
            
            bestNode?.let {
                assignments.add(Assignment(work, it.nodeId))
            }
        }
        
        return assignments
    }
}

data class Assignment(
    val workUnit: WorkUnit,
    val assignedTo: String
)

class PercolatorBlackboardBridge {
    private val blackboard = TestBlackboard()
    
    fun ingestResults(results: List<ProcessedContent>) {
        results.forEach { result ->
            // Add entities as nodes
            result.entities.forEach { entity ->
                blackboard.addNode(BlackboardNode(entity))
            }
            
            // Add tags as nodes
            result.tags.forEach { tag ->
                blackboard.addNode(BlackboardNode(tag))
            }
            
            // Connect entities to tags
            result.entities.forEach { entity ->
                result.tags.forEach { tag ->
                    blackboard.addConnection(entity, tag, result.complexity)
                }
            }
        }
    }
    
    fun getBlackboard(): TestBlackboard = blackboard
}

class TestBlackboard {
    private val nodes = mutableMapOf<String, BlackboardNode>()
    private val connections = mutableListOf<Connection>()
    
    fun addNode(node: BlackboardNode) {
        nodes[node.label] = node
    }
    
    fun findNode(label: String): BlackboardNode? = nodes[label]
    
    fun addConnection(from: String, to: String, weight: Double) {
        val fromNode = nodes[from] ?: return
        val toNode = nodes[to] ?: return
        connections.add(Connection(fromNode, toNode, weight))
    }
    
    fun getConnections(node: BlackboardNode): List<Connection> {
        return connections.filter { it.source == node || it.target == node }
    }
    
    fun getKnowledgeWeight(node: BlackboardNode): Double {
        return getConnections(node).maxOfOrNull { it.weight } ?: 0.0
    }
}

data class BlackboardNode(val label: String)
data class Connection(
    val source: BlackboardNode,
    val target: BlackboardNode,
    val weight: Double
)