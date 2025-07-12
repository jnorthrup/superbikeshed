package fiduciary.percolator

import borg.trikeshed.common.*
import borg.trikeshed.context.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlin.test.*

/**
 * TDD Test for Percolator Farm System
 * 
 * Tests the farm management functionality and ensures it follows
 * TrikeShed architectural principles.
 */
class PercolatorFarmTDDTest {
    
    @Test
    fun `test farm configuration creation`() = runTest {
        // Given: Command line arguments
        val args = arrayOf(
            "--coordinator=http://test-coordinator:8888",
            "--nodes=10",
            "--max-concurrent=5",
            "--max-nodes=50",
            "--min-nodes=3"
        )
        
        // When: Creating farm configuration
        val config = createFarmConfig(args)
        
        // Then: Configuration should be correct
        assertEquals("http://test-coordinator:8888", config.coordinatorUrl)
        assertEquals(10, config.nodeCount)
        assertEquals(5, config.maxConcurrentPerNode)
        assertEquals(50, config.maxNodes)
        assertEquals(3, config.minNodes)
        assertTrue(config.autoScale)
    }
    
    @Test
    fun `test farm creation and basic operations`() = runTest {
        // Given: A farm configuration
        val config = FarmConfig(
            coordinatorUrl = "http://localhost:8888",
            nodeCount = 3,
            maxConcurrentPerNode = 2,
            workDirBase = "./test-farm",
            autoScale = false
        )
        
        // When: Creating a farm
        val farm = PercolatorFarm(config)
        
        // Then: Farm should be created with correct configuration
        assertNotNull(farm)
        
        // And: Farm stats should be initialized
        val stats = farm.getFarmStats()
        assertEquals(0, stats.totalNodes) // No nodes started yet
        assertEquals(0, stats.activeNodes)
        assertEquals(0, stats.totalActiveWork)
        assertEquals(0, stats.totalCompletedWork)
        assertEquals("http://localhost:8888", stats.coordinatorUrl)
    }
    
    @Test
    fun `test farm node management`() = runTest {
        // Given: A farm with auto-scaling disabled
        val config = FarmConfig(
            nodeCount = 2,
            autoScale = false,
            maxNodes = 5
        )
        val farm = PercolatorFarm(config)
        
        // When: Starting the farm
        farm.start()
        
        // Then: Farm should have the specified number of nodes
        delay(1000) // Allow time for nodes to start
        
        val stats = farm.getFarmStats()
        assertEquals(2, stats.totalNodes)
        assertTrue(stats.activeNodes >= 0)
        
        // When: Adding a new node
        val newNodeId = farm.addNode("test-node-1")
        
        // Then: Node should be added
        assertEquals("test-node-1", newNodeId)
        
        // And: Farm should have one more node
        val updatedStats = farm.getFarmStats()
        assertEquals(3, updatedStats.totalNodes)
        
        // When: Removing a node
        val removed = farm.removeNode("test-node-1")
        
        // Then: Node should be removed
        assertTrue(removed)
        
        // And: Farm should have one less node
        val finalStats = farm.getFarmStats()
        assertEquals(2, finalStats.totalNodes)
    }
    
    @Test
    fun `test farm auto-scaling behavior`() = runTest {
        // Given: A farm with auto-scaling enabled
        val config = FarmConfig(
            nodeCount = 1,
            autoScale = true,
            minNodes = 2,
            maxNodes = 5
        )
        val farm = PercolatorFarm(config)
        
        // When: Starting the farm
        farm.start()
        
        // Then: Farm should auto-scale to minimum nodes
        delay(2000) // Allow time for auto-scaling
        
        val stats = farm.getFarmStats()
        assertTrue(stats.totalNodes >= config.minNodes)
        assertTrue(stats.totalNodes <= config.maxNodes)
    }
    
    @Test
    fun `test farm statistics calculation`() = runTest {
        // Given: A farm with known configuration
        val config = FarmConfig(
            nodeCount = 3,
            autoScale = false
        )
        val farm = PercolatorFarm(config)
        
        // When: Starting the farm
        farm.start()
        delay(1000)
        
        // Then: Statistics should be calculated correctly
        val stats = farm.getFarmStats()
        
        // Basic stats
        assertEquals(3, stats.totalNodes)
        assertTrue(stats.activeNodes >= 0)
        assertTrue(stats.totalActiveWork >= 0)
        assertTrue(stats.totalCompletedWork >= 0)
        
        // Resource usage should be reasonable
        assertTrue(stats.averageCpuUsage >= 0.0)
        assertTrue(stats.averageCpuUsage <= 1.0)
        assertTrue(stats.averageMemoryUsage >= 0.0)
        assertTrue(stats.averageMemoryUsage <= 1.0)
        
        // Uptime should be positive
        assertTrue(stats.farmUptime > 0)
    }
    
    @Test
    fun `test farm extension functions`() = runTest {
        // Given: A farm
        val farm = PercolatorFarm()
        
        // When: Using extension functions
        val nodes = farm.nodes()
        val stats = farm.stats()
        
        // Then: Extension functions should work
        assertNotNull(nodes)
        assertNotNull(stats)
        
        // And: Should return correct types
        assertTrue(nodes is Indexed<FarmNode>)
        assertTrue(stats is FarmStats)
    }
    
    @Test
    fun `test farm node data structure`() {
        // Given: Farm node data
        val node = FarmNode(
            nodeId = "test-node",
            status = NodeStatus.ONLINE,
            startedAt = Clock.System.now(),
            lastHeartbeat = Clock.System.now(),
            activeWork = 2,
            completedWork = 5,
            cpuUsage = 0.75,
            memoryUsage = 0.45,
            workDir = "./test-work"
        )
        
        // Then: Node should have correct properties
        assertEquals("test-node", node.nodeId)
        assertEquals(NodeStatus.ONLINE, node.status)
        assertEquals(2, node.activeWork)
        assertEquals(5, node.completedWork)
        assertEquals(0.75, node.cpuUsage)
        assertEquals(0.45, node.memoryUsage)
        assertEquals("./test-work", node.workDir)
    }
    
    @Test
    fun `test farm stats data structure`() {
        // Given: Farm statistics data
        val stats = FarmStats(
            totalNodes = 10,
            activeNodes = 8,
            totalActiveWork = 15,
            totalCompletedWork = 42,
            averageCpuUsage = 0.65,
            averageMemoryUsage = 0.35,
            farmUptime = 3600,
            coordinatorUrl = "http://test:8888"
        )
        
        // Then: Stats should have correct properties
        assertEquals(10, stats.totalNodes)
        assertEquals(8, stats.activeNodes)
        assertEquals(15, stats.totalActiveWork)
        assertEquals(42, stats.totalCompletedWork)
        assertEquals(0.65, stats.averageCpuUsage)
        assertEquals(0.35, stats.averageMemoryUsage)
        assertEquals(3600, stats.farmUptime)
        assertEquals("http://test:8888", stats.coordinatorUrl)
    }
    
    @Test
    fun `test farm configuration defaults`() {
        // Given: Default farm configuration
        val config = FarmConfig()
        
        // Then: Should have sensible defaults
        assertEquals("http://localhost:8888", config.coordinatorUrl)
        assertEquals(5, config.nodeCount)
        assertEquals(3, config.maxConcurrentPerNode)
        assertEquals("./percolator-farm", config.workDirBase)
        assertEquals(60, config.heartbeatInterval)
        assertEquals(30, config.claimInterval)
        assertTrue(config.autoScale)
        assertEquals(20, config.maxNodes)
        assertEquals(2, config.minNodes)
    }
    
    @Test
    fun `test farm graceful shutdown`() = runTest {
        // Given: A running farm
        val farm = PercolatorFarm(FarmConfig(nodeCount = 2, autoScale = false))
        farm.start()
        delay(1000)
        
        // When: Stopping the farm
        farm.stop()
        
        // Then: Farm should be stopped
        val stats = farm.getFarmStats()
        assertEquals(0, stats.totalNodes)
        assertEquals(0, stats.activeNodes)
    }
    
    @Test
    fun `test farm with no auto-scale`() = runTest {
        // Given: A farm with auto-scaling disabled
        val config = FarmConfig(
            nodeCount = 3,
            autoScale = false,
            maxNodes = 10
        )
        val farm = PercolatorFarm(config)
        
        // When: Starting the farm
        farm.start()
        delay(1000)
        
        // Then: Farm should maintain fixed node count
        val stats = farm.getFarmStats()
        assertEquals(3, stats.totalNodes)
        
        // And: Should not scale beyond initial count
        delay(5000) // Wait for potential auto-scaling
        val finalStats = farm.getFarmStats()
        assertEquals(3, finalStats.totalNodes)
    }
    
    @Test
    fun `test farm node indexing`() = runTest {
        // Given: A farm with multiple nodes
        val farm = PercolatorFarm(FarmConfig(nodeCount = 3, autoScale = false))
        farm.start()
        delay(1000)
        
        // When: Getting nodes as Indexed
        val nodes = farm.getActiveNodes()
        
        // Then: Should return Indexed<FarmNode>
        assertTrue(nodes is Indexed<FarmNode>)
        
        // And: Should have correct number of nodes
        val nodeList = nodes.toList()
        assertEquals(3, nodeList.size)
        
        // And: Each node should have valid data
        nodeList.forEach { node ->
            assertTrue(node.nodeId.isNotEmpty())
            assertTrue(node.workDir.isNotEmpty())
            assertTrue(node.startedAt > Clock.System.now() - 1.minutes)
        }
    }
} 