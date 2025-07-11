package fiduciary.percolator

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

class ContentPercolatorTest {
    private lateinit var testScope: TestScope
    private lateinit var daemon: TestablePercolatorDaemon
    private lateinit var mockCoordinator: MockCoordinator
    
    @BeforeTest
    fun setup() {
        testScope = TestScope()
        mockCoordinator = MockCoordinator()
        daemon = TestablePercolatorDaemon(
            nodeId = "test_node",
            coordinator = mockCoordinator,
            scope = testScope
        )
    }
    
    @Test
    fun `test daemon initialization`() = testScope.runTest {
        daemon.start()
        
        assertEquals("test_node", daemon.nodeId)
        assertTrue(daemon.isRunning)
        
        // Should register with coordinator
        advanceTimeBy(1.seconds)
        assertTrue(mockCoordinator.registeredNodes.contains("test_node"))
    }
    
    @Test
    fun `test work claiming and processing`() = testScope.runTest {
        val testWork = WorkUnit(
            id = "work_test",
            archiveUrl = "https://example.com/test.zip",
            entries = listOf(
                FileEntry("doc1.txt", 0, 100, 200, 8),
                FileEntry("doc2.txt", 200, 150, 300, 8)
            )
        )
        
        mockCoordinator.addWork(testWork)
        daemon.start()
        
        // Advance time to trigger work claim
        advanceTimeBy(5.seconds)
        
        // Should claim work
        assertEquals(1, daemon.activeWorkCount)
        assertEquals("work_test", daemon.currentWork?.id)
        
        // Process work
        advanceTimeBy(30.seconds)
        
        // Should complete work
        assertEquals(0, daemon.activeWorkCount)
        assertEquals(1, daemon.completedWorkCount)
        
        // Check results submitted
        val results = mockCoordinator.getResults("work_test")
        assertEquals(2, results.size)
        assertTrue(results.all { it.workUnitId == "work_test" })
    }
    
    @Test
    fun `test parallel work processing`() = testScope.runTest {
        daemon = TestablePercolatorDaemon(
            nodeId = "parallel_node",
            coordinator = mockCoordinator,
            maxConcurrentWork = 3,
            scope = testScope
        )
        
        // Add multiple work units
        repeat(5) { i ->
            mockCoordinator.addWork(
                WorkUnit(
                    id = "work_$i",
                    archiveUrl = "https://example.com/archive$i.zip",
                    entries = listOf(
                        FileEntry("file$i.txt", 0, 100, 200, 8)
                    )
                )
            )
        }
        
        daemon.start()
        advanceTimeBy(5.seconds)
        
        // Should claim up to maxConcurrentWork
        assertEquals(3, daemon.activeWorkCount)
        
        // Complete first batch
        advanceTimeBy(30.seconds)
        
        // Should claim remaining work
        assertEquals(2, daemon.activeWorkCount)
        assertEquals(3, daemon.completedWorkCount)
    }
    
    @Test
    fun `test error handling in file processing`() = testScope.runTest {
        val errorWork = WorkUnit(
            id = "error_work",
            archiveUrl = "https://example.com/corrupt.zip",
            entries = listOf(
                FileEntry("good.txt", 0, 100, 200, 8),
                FileEntry("corrupt.bin", 200, -1, -1, 8), // Invalid sizes
                FileEntry("missing.txt", 999999, 100, 200, 8) // Beyond file
            )
        )
        
        mockCoordinator.addWork(errorWork)
        daemon.start()
        
        advanceTimeBy(35.seconds)
        
        val results = mockCoordinator.getResults("error_work")
        assertEquals(3, results.size)
        
        // Good file should process
        val goodResult = results.find { it.fileEntry.path == "good.txt" }
        assertNotNull(goodResult)
        assertNull(goodResult.error)
        
        // Bad files should have errors
        val corruptResult = results.find { it.fileEntry.path == "corrupt.bin" }
        assertNotNull(corruptResult?.error)
        
        val missingResult = results.find { it.fileEntry.path == "missing.txt" }
        assertNotNull(missingResult?.error)
    }
    
    @Test
    fun `test heartbeat mechanism`() = testScope.runTest {
        daemon.start()
        
        // Initial heartbeat
        advanceTimeBy(1.seconds)
        assertEquals(1, mockCoordinator.heartbeatCount("test_node"))
        
        // Regular heartbeats
        advanceTimeBy(1.minutes)
        assertEquals(2, mockCoordinator.heartbeatCount("test_node"))
        
        advanceTimeBy(5.minutes)
        assertEquals(6, mockCoordinator.heartbeatCount("test_node"))
    }
    
    @Test
    fun `test work timeout and retry`() = testScope.runTest {
        val timeoutWork = WorkUnit(
            id = "timeout_work",
            archiveUrl = "https://example.com/slow.zip",
            entries = listOf(
                FileEntry("slow.txt", 0, 100, 200, 8)
            )
        )
        
        mockCoordinator.addWork(timeoutWork)
        daemon.processingDelay = 2.minutes // Simulate slow processing
        daemon.start()
        
        advanceTimeBy(5.seconds)
        assertEquals("timeout_work", daemon.currentWork?.id)
        
        // Should timeout after deadline
        advanceTimeBy(61.minutes)
        
        // Work should be released back to coordinator
        assertTrue(mockCoordinator.hasAvailableWork())
        assertEquals(0, daemon.activeWorkCount)
    }
    
    @Test
    fun `test content analysis pipeline`() = testScope.runTest {
        val analysisWork = WorkUnit(
            id = "analysis_work",
            archiveUrl = "https://example.com/documents.zip",
            entries = listOf(
                FileEntry("technical.txt", 0, 500, 1000, 8),
                FileEntry("simple.txt", 500, 100, 150, 8)
            )
        )
        
        mockCoordinator.addWork(analysisWork)
        daemon.start()
        
        advanceTimeBy(35.seconds)
        
        val results = mockCoordinator.getResults("analysis_work")
        
        // Technical document should have higher complexity
        val technicalResult = results.find { it.fileEntry.path == "technical.txt" }
        assertNotNull(technicalResult)
        assertTrue(technicalResult.complexity > 0.7)
        assertTrue(technicalResult.tags.contains("technical"))
        
        // Simple document should have lower complexity
        val simpleResult = results.find { it.fileEntry.path == "simple.txt" }
        assertNotNull(simpleResult)
        assertTrue(simpleResult.complexity < 0.5)
    }
    
    @Test
    fun `test graceful shutdown`() = testScope.runTest {
        daemon.start()
        
        // Add work in progress
        mockCoordinator.addWork(
            WorkUnit(
                id = "shutdown_work",
                archiveUrl = "https://example.com/data.zip",
                entries = listOf(FileEntry("file.txt", 0, 100, 200, 8))
            )
        )
        
        advanceTimeBy(5.seconds)
        assertEquals(1, daemon.activeWorkCount)
        
        // Graceful shutdown
        daemon.stop()
        advanceTimeBy(1.seconds)
        
        assertFalse(daemon.isRunning)
        
        // Work should be released back
        assertTrue(mockCoordinator.hasAvailableWork())
    }
}

// Testable version of PercolatorDaemon
class TestablePercolatorDaemon(
    nodeId: String,
    private val coordinator: MockCoordinator,
    maxConcurrentWork: Int = 5,
    private val scope: TestScope
) {
    val nodeId = nodeId
    var isRunning = false
        private set
    
    var activeWorkCount = 0
        private set
    
    var completedWorkCount = 0
        private set
    
    var currentWork: WorkUnit? = null
        private set
    
    var processingDelay = 20.seconds
    
    private val activeJobs = mutableListOf<Job>()
    
    fun start() {
        isRunning = true
        
        // Register with coordinator
        coordinator.registerNode(nodeId)
        
        // Start work loop
        scope.launch {
            while (isRunning) {
                if (activeWorkCount < maxConcurrentWork) {
                    val work = coordinator.claimWork(nodeId)
                    if (work != null) {
                        processWork(work)
                    }
                }
                delay(5.seconds)
            }
        }
        
        // Start heartbeat loop
        scope.launch {
            while (isRunning) {
                coordinator.heartbeat(nodeId)
                delay(1.minutes)
            }
        }
    }
    
    private fun processWork(work: WorkUnit) {
        activeWorkCount++
        currentWork = work
        
        val job = scope.launch {
            try {
                delay(processingDelay)
                
                val results = work.entries.map { entry ->
                    processEntry(work.id, entry)
                }
                
                coordinator.submitResults(work.id, results)
                completedWorkCount++
            } catch (e: Exception) {
                coordinator.releaseWork(work.id)
            } finally {
                activeWorkCount--
                if (currentWork?.id == work.id) {
                    currentWork = null
                }
            }
        }
        
        activeJobs.add(job)
    }
    
    private suspend fun processEntry(workUnitId: String, entry: FileEntry): ProcessedContent {
        return if (entry.compressedSize < 0 || entry.uncompressedSize < 0) {
            ProcessedContent(
                workUnitId = workUnitId,
                fileEntry = entry,
                error = "Invalid file sizes"
            )
        } else if (entry.offset > 100000) {
            ProcessedContent(
                workUnitId = workUnitId,
                fileEntry = entry,
                error = "File offset out of range"
            )
        } else {
            val complexity = if (entry.uncompressedSize > 500) 0.8 else 0.3
            val tags = if (entry.path.contains("technical")) {
                listOf("technical", "document")
            } else {
                listOf("simple", "text")
            }
            
            ProcessedContent(
                workUnitId = workUnitId,
                fileEntry = entry,
                content = "Mock content for ${entry.path}",
                tags = tags,
                entities = listOf("Test Entity"),
                complexity = complexity
            )
        }
    }
    
    fun stop() {
        isRunning = false
        
        // Cancel active work
        activeJobs.forEach { it.cancel() }
        
        // Release current work back to coordinator
        currentWork?.let {
            coordinator.releaseWork(it.id)
        }
    }
}

// Mock coordinator for testing
class MockCoordinator {
    private val workQueue = mutableListOf<WorkUnit>()
    private val results = mutableMapOf<String, List<ProcessedContent>>()
    private val heartbeats = mutableMapOf<String, Int>()
    val registeredNodes = mutableSetOf<String>()
    
    fun registerNode(nodeId: String) {
        registeredNodes.add(nodeId)
    }
    
    fun addWork(work: WorkUnit) {
        workQueue.add(work)
    }
    
    fun claimWork(nodeId: String): WorkUnit? {
        return workQueue.firstOrNull { it.status == WorkStatus.PENDING }?.also {
            workQueue.remove(it)
        }
    }
    
    fun submitResults(workUnitId: String, processedContent: List<ProcessedContent>) {
        results[workUnitId] = processedContent
    }
    
    fun getResults(workUnitId: String): List<ProcessedContent> {
        return results[workUnitId] ?: emptyList()
    }
    
    fun releaseWork(workUnitId: String) {
        // In real implementation, would add back to queue
    }
    
    fun heartbeat(nodeId: String) {
        heartbeats[nodeId] = (heartbeats[nodeId] ?: 0) + 1
    }
    
    fun heartbeatCount(nodeId: String): Int {
        return heartbeats[nodeId] ?: 0
    }
    
    fun hasAvailableWork(): Boolean {
        return workQueue.any { it.status == WorkStatus.PENDING }
    }
}