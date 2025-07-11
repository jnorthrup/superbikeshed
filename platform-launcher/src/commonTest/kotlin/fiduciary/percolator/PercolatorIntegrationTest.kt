finish low hanging fruit before taking on a missing modulepackage fiduciary.percolator

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

class PercolatorIntegrationTest {
    private lateinit var testScope: TestScope
    private lateinit var coordinator: PercolatorCoordinator
    private lateinit var nodes: List<TestablePercolatorDaemon>
    
    @BeforeTest
    fun setup() {
        testScope = TestScope()
        coordinator = PercolatorCoordinator()
        nodes = emptyList()
    }
    
    @AfterTest
    fun teardown() {
        nodes.forEach { it.stop() }
    }
    
    @Test
    fun `test single node network`() = testScope.runTest {
        // Setup coordinator with work
        coordinator.addWork(
            archiveUrl = "https://archive.org/test.zip",
            entries = (1..10).map { i ->
                FileEntry("file$i.txt", i * 100L, 100, 200, 8)
            }
        )
        
        // Start single node
        val node = createNode("node_1")
        nodes = listOf(node)
        node.start()
        
        // Let it process
        advanceTimeBy(1.minutes)
        
        // Check stats
        val stats = coordinator.getNetworkStats()
        assertEquals(1, stats.totalNodes)
        assertEquals(1, stats.activeNodes)
        assertEquals(0, stats.pendingWork)
        assertEquals(1, stats.completedWork)
    }
    
    @Test
    fun `test multi-node network with load balancing`() = testScope.runTest {
        // Add 20 work units
        repeat(20) { i ->
            coordinator.addWork(
                archiveUrl = "https://archive.org/archive$i.zip",
                entries = listOf(
                    FileEntry("content$i.txt", 0, 500, 1000, 8)
                )
            )
        }
        
        // Start 3 nodes with different capabilities
        val fastNode = createNode("fast_node", processingDelay = 10.seconds)
        val mediumNode = createNode("medium_node", processingDelay = 20.seconds)
        val slowNode = createNode("slow_node", processingDelay = 40.seconds)
        
        nodes = listOf(fastNode, mediumNode, slowNode)
        nodes.forEach { it.start() }
        
        // Initial claim
        advanceTimeBy(5.seconds)
        
        // All nodes should have work
        assertTrue(nodes.all { it.activeWorkCount > 0 })
        
        // Let them process for a while
        advanceTimeBy(2.minutes)
        
        // Fast node should have completed more work
        assertTrue(fastNode.completedWorkCount > slowNode.completedWorkCount)
        
        val stats = coordinator.getNetworkStats()
        assertEquals(3, stats.totalNodes)
        assertEquals(3, stats.activeNodes)
        assertTrue(stats.completedWork > 10)
    }
    
    @Test
    fun `test node failure and work redistribution`() = testScope.runTest {
        // Add work
        repeat(5) { i ->
            coordinator.addWork(
                archiveUrl = "https://archive.org/data$i.zip",
                entries = listOf(FileEntry("data$i.dat", 0, 1000, 2000, 8))
            )
        }
        
        // Start two nodes
        val node1 = createNode("node_1")
        val node2 = createNode("node_2")
        nodes = listOf(node1, node2)
        
        node1.start()
        node2.start()
        
        // Both claim work
        advanceTimeBy(5.seconds)
        assertTrue(node1.activeWorkCount > 0)
        assertTrue(node2.activeWorkCount > 0)
        
        val node1Work = node1.currentWork?.id
        
        // Simulate node1 failure
        node1.stop()
        advanceTimeBy(1.seconds)
        
        // Work should be available again
        assertTrue(coordinator.getNetworkStats().pendingWork > 0 || 
                  coordinator.getNetworkStats().claimedWork > 0)
        
        // Node2 should eventually pick up the failed work
        advanceTimeBy(1.minutes)
        
        // All work should be completed by node2
        val stats = coordinator.getNetworkStats()
        assertEquals(5, stats.completedWork + node2.activeWorkCount)
    }
    
    @Test
    fun `test network scaling`() = testScope.runTest {
        // Add 100 work units
        repeat(100) { i ->
            coordinator.addWork(
                archiveUrl = "https://archive.org/bulk$i.zip",
                entries = listOf(FileEntry("bulk$i.txt", 0, 200, 400, 8))
            )
        }
        
        val initialStats = coordinator.getNetworkStats()
        assertEquals(100, initialStats.pendingWork)
        
        // Start with 2 nodes
        val initialNodes = (1..2).map { createNode("node_$it", processingDelay = 5.seconds) }
        nodes = initialNodes.toMutableList()
        initialNodes.forEach { it.start() }
        
        advanceTimeBy(10.seconds)
        
        // Add more nodes as work progresses
        val additionalNodes = (3..5).map { createNode("node_$it", processingDelay = 5.seconds) }
        (nodes as MutableList).addAll(additionalNodes)
        additionalNodes.forEach { it.start() }
        
        // Process all work
        advanceTimeBy(3.minutes)
        
        // All work should be completed
        val finalStats = coordinator.getNetworkStats()
        assertEquals(100, finalStats.completedWork)
        assertEquals(0, finalStats.pendingWork)
        assertEquals(5, finalStats.totalNodes)
    }
    
    @Test
    fun `test work prioritization`() = testScope.runTest {
        // Add high priority work
        coordinator.addWork(
            archiveUrl = "https://archive.org/urgent.zip",
            entries = listOf(FileEntry("urgent.txt", 0, 100, 200, 8)),
            priority = 10.0
        )
        
        // Add normal priority work
        repeat(5) { i ->
            coordinator.addWork(
                archiveUrl = "https://archive.org/normal$i.zip",
                entries = listOf(FileEntry("normal$i.txt", 0, 100, 200, 8)),
                priority = 1.0
            )
        }
        
        val node = createNode("priority_node")
        nodes = listOf(node)
        node.start()
        
        // Should claim high priority work first
        advanceTimeBy(5.seconds)
        assertEquals("urgent.txt", node.currentWork?.entries?.first()?.path)
    }
    
    @Test
    fun `test bandwidth-aware distribution`() = testScope.runTest {
        // Large work unit
        val largeWork = listOf(
            FileEntry("huge.bin", 0, 10_000_000, 50_000_000, 8)
        )
        
        // Small work units
        val smallWork = (1..5).map { i ->
            listOf(FileEntry("small$i.txt", 0, 1000, 2000, 8))
        }
        
        coordinator.addWork("https://archive.org/huge.zip", largeWork)
        smallWork.forEach { 
            coordinator.addWork("https://archive.org/small.zip", it)
        }
        
        // High bandwidth node
        val highBandwidthNode = createNode(
            "high_bw",
            maxBandwidth = 1000,
            processingDelay = 30.seconds
        )
        
        // Low bandwidth node
        val lowBandwidthNode = createNode(
            "low_bw",
            maxBandwidth = 10,
            processingDelay = 10.seconds
        )
        
        nodes = listOf(highBandwidthNode, lowBandwidthNode)
        nodes.forEach { it.start() }
        
        advanceTimeBy(5.seconds)
        
        // High bandwidth node should get large work
        val highBwWork = highBandwidthNode.currentWork
        assertTrue(
            highBwWork?.entries?.any { it.compressedSize > 1_000_000 } ?: false
        )
    }
    
    @Test
    fun `test result aggregation and verification`() = testScope.runTest {
        // Add work that produces specific results
        coordinator.addWork(
            archiveUrl = "https://archive.org/documents.zip",
            entries = listOf(
                FileEntry("doc1.txt", 0, 100, 200, 8),
                FileEntry("doc2.txt", 200, 100, 200, 8),
                FileEntry("doc3.txt", 400, 100, 200, 8)
            )
        )
        
        val node = createNode("aggregator_node")
        nodes = listOf(node)
        node.start()
        
        advanceTimeBy(1.minutes)
        
        // Verify all documents were processed
        assertEquals(1, node.completedWorkCount)
        
        // In real system, would verify:
        // - All files extracted
        // - Tags/entities consolidated
        // - Results stored in CouchDB
    }
    
    // Helper to create test nodes
    private fun createNode(
        nodeId: String,
        processingDelay: kotlin.time.Duration = 20.seconds,
        maxBandwidth: Int = 100
    ): TestablePercolatorDaemon {
        return TestablePercolatorDaemon(
            nodeId = nodeId,
            coordinator = TestCoordinatorAdapter(coordinator),
            scope = testScope
        ).apply {
            this.processingDelay = processingDelay
        }
    }
}

// Adapter to connect test daemon to real coordinator
class TestCoordinatorAdapter(
    private val coordinator: PercolatorCoordinator
) : MockCoordinator() {
    
    override fun claimWork(nodeId: String): WorkUnit? {
        // Update node status first
        coordinator.updateNodeStatus(
            NodeStatus(
                nodeId = nodeId,
                timestamp = Clock.System.now(),
                activeWork = 1,
                completedWork = 0,
                cpuUsage = 0.5,
                memoryUsage = 0.3
            )
        )
        
        return coordinator.claimWork(nodeId)
    }
    
    override fun registerNode(nodeId: String) {
        super.registerNode(nodeId)
        coordinator.updateNodeStatus(
            NodeStatus(
                nodeId = nodeId,
                timestamp = Clock.System.now(),
                activeWork = 0,
                completedWork = 0,
                cpuUsage = 0.1,
                memoryUsage = 0.2
            )
        )
    }
    
    override fun heartbeat(nodeId: String) {
        super.heartbeat(nodeId)
        coordinator.updateNodeStatus(
            NodeStatus(
                nodeId = nodeId,
                timestamp = Clock.System.now(),
                activeWork = 0, // Would track real active work
                completedWork = 0, // Would track real completed
                cpuUsage = 0.5,
                memoryUsage = 0.3
            )
        )
    }
}

// Extension to make coordinator testable
fun PercolatorCoordinator.addWork(
    archiveUrl: String,
    entries: List<FileEntry>,
    priority: Double = 1.0
) {
    val workUnit = WorkUnit(
        id = "work_${System.currentTimeMillis()}_${(0..9999).random()}",
        archiveUrl = archiveUrl,
        entries = entries,
        priority = priority
    )
    
    // Need to add to internal queue
    this.addWork(archiveUrl, entries)
}