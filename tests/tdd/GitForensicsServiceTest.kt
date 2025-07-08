package tests.tdd

import fiduciary.metaverse.*
import borg.trikeshed.lib.*
import borg.trikeshed.couchdb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * TDD Test Suite for Git Forensics Service
 * 
 * Tests read-only forensics capabilities for git taxonomic objects:
 * - Git object analysis and reconstruction
 * - Commit history forensics
 * - Realtime CouchDB integration
 * - Attention scoring and pattern detection
 * - Scene replay and reconstruction
 * - Taxonomic classification
 */
class GitForensicsServiceTest {
    
    internal lateinit var forensicsService: GitForensicsService
    internal lateinit var couchService: CouchDBService
    internal lateinit var gitHistoryService: GitHistoryService
    
    @BeforeTest
    fun setup() {
        couchService = CouchDBService()
        gitHistoryService = GitHistoryService()
        forensicsService = GitForensicsService(couchService, gitHistoryService)
    }
    
    // ===== FORENSIC SESSION TESTS =====
    
    @Test
    fun `should initialize forensic session with CouchDB database`() = runTest {
        // Given: Repository ID
        val repoId = "test-repo-001"
        
        // When: Initializing forensics
        val session = forensicsService.initializeForensics(repoId)
        
        // Then: Session should be created with proper database
        assertNotNull(session)
        assertEquals(repoId, session.id)
        assertEquals("git_forensics_$repoId", session.databaseName)
        assertTrue(session.startTime > 0)
        assertNotNull(session.attentionModel)
        assertNotNull(session.patternDetector)
    }
    
    @Test
    fun `should create forensic database in CouchDB`() = runTest {
        // Given: Repository ID
        val repoId = "test-repo-002"
        
        // When: Initializing forensics
        val session = forensicsService.initializeForensics(repoId)
        
        // Then: Database should exist in CouchDB
        val databaseInfo = couchService.getDatabaseInfo(session.databaseName)
        assertNotNull(databaseInfo)
        assertEquals(session.databaseName, databaseInfo.dbName)
    }
    
    // ===== GIT OBJECT ANALYSIS TESTS =====
    
    @Test
    fun `should analyze git objects and calculate attention scores`() = runTest {
        // Given: Forensic session and git objects
        val repoId = "test-repo-003"
        val session = forensicsService.initializeForensics(repoId)
        
        // Create test git objects
        val objectHashes = 3 j { i ->
            when (i) {
                0 -> "abc1234567890abcdef1234567890abcdef1234"
                1 -> "def2345678901def2345678901def2345678901"
                2 -> "ghi3456789012ghi3456789012ghi3456789012"
                else -> throw IndexOutOfBoundsException()
            }
        }
        
        // When: Analyzing git objects
        val analysis = forensicsService.analyzeGitObjects(repoId, objectHashes)
        
        // Then: Analysis should contain artifacts and attention scores
        assertNotNull(analysis)
        assertEquals(repoId, analysis.repoId)
        assertEquals(3, analysis.artifacts.size)
        assertEquals(3, analysis.attentionScores.size)
        assertEquals(3, analysis.taxonomicClasses.size)
        
        // Check that all object hashes have attention scores
        for (i in 0 until objectHashes.a) {
            val hash = objectHashes.b(i)
            assertTrue(analysis.attentionScores.containsKey(hash))
            assertTrue(analysis.taxonomicClasses.containsKey(hash))
        }
    }
    
    @Test
    fun `should classify git objects taxonomically`() = runTest {
        // Given: Forensic session and git objects
        val repoId = "test-repo-004"
        val session = forensicsService.initializeForensics(repoId)
        
        val objectHashes = 2 j { i ->
            when (i) {
                0 -> "high_attention_object_1234567890abcdef"
                1 -> "low_attention_object_abcdef1234567890"
                else -> throw IndexOutOfBoundsException()
            }
        }
        
        // When: Classifying git objects
        val classification = forensicsService.classifyGitObjects(repoId, objectHashes)
        
        // Then: Objects should be classified
        assertNotNull(classification)
        assertEquals(repoId, classification.repoId)
        assertEquals(2, classification.classifications.size)
        
        // Check that classifications are valid
        classification.classifications.values.forEach { taxonomicClass ->
            assertTrue(taxonomicClass in listOf("HIGH_ATTENTION", "MEDIUM_ATTENTION", "LOW_ATTENTION"))
        }
    }
    
    // ===== SCENE REPLAY TESTS =====
    
    @Test
    fun `should replay git scene with commit timeline`() = runTest {
        // Given: Forensic session and git history
        val repoId = "test-repo-005"
        val session = forensicsService.initializeForensics(repoId)
        
        // Initialize git history with test commits
        val initialContent = "Initial content"
        gitHistoryService.initializeHistory(repoId, initialContent)
        
        // Create test commits
        val testCommits = listOf(
            GitCommit(
                commitId = "commit-1",
                parentCommitId = null,
                author = "alice",
                timestamp = Clock.System.now().toEpochMilliseconds(),
                message = "Initial commit",
                diff = "Initial content",
                waveletId = "wavelet-1",
                documentState = "Initial content"
            ),
            GitCommit(
                commitId = "commit-2",
                parentCommitId = "commit-1",
                author = "bob",
                timestamp = Clock.System.now().toEpochMilliseconds() + 1000,
                message = "Add feature",
                diff = "+ New feature",
                waveletId = "wavelet-2",
                documentState = "Initial content\nNew feature"
            )
        )
        
        // Add commits to history
        testCommits.forEach { commit ->
            gitHistoryService.addCommit(repoId, commit)
        }
        
        // When: Replaying git scene
        val sceneId = "test-scene-001"
        val replay = forensicsService.replayGitScene(repoId, "commit-1..commit-2", sceneId)
        
        // Then: Scene replay should contain timeline and events
        assertNotNull(replay)
        assertEquals(sceneId, replay.sceneId)
        assertEquals(repoId, replay.repoId)
        assertEquals(2, replay.commits.size)
        assertEquals(2, replay.timeline.size)
        assertTrue(replay.forensicEvents.isNotEmpty())
        
        // Check timeline events
        replay.timeline.forEach { event ->
            assertNotNull(event.commitId)
            assertTrue(event.timestamp > 0)
            assertTrue(event.attentionScore >= 0.0 && event.attentionScore <= 1.0)
            assertTrue(event.forensicEvents.isNotEmpty())
        }
    }
    
    @Test
    fun `should detect attention peaks in scene replay`() = runTest {
        // Given: Forensic session with high-attention commits
        val repoId = "test-repo-006"
        val session = forensicsService.initializeForensics(repoId)
        
        // Initialize git history
        gitHistoryService.initializeHistory(repoId, "Initial")
        
        // Create high-attention commit (long message and diff)
        val highAttentionCommit = GitCommit(
            commitId = "high-attention-commit",
            parentCommitId = null,
            author = "alice",
            timestamp = Clock.System.now().toEpochMilliseconds(),
            message = "This is a very long commit message that should trigger high attention scoring because it contains detailed information about the changes being made to the codebase",
            diff = "This is a very long diff that contains many changes and should trigger high attention scoring because it represents significant modifications to the codebase",
            waveletId = "wavelet-high",
            documentState = "Modified content"
        )
        
        gitHistoryService.addCommit(repoId, highAttentionCommit)
        
        // When: Replaying scene
        val sceneId = "attention-peak-test"
        val replay = forensicsService.replayGitScene(repoId, "high-attention-commit", sceneId)
        
        // Then: Should detect attention peaks
        assertTrue(replay.attentionPeaks.isNotEmpty(), "Should detect attention peaks for high-attention commits")
        
        replay.attentionPeaks.forEach { peak ->
            assertTrue(peak.attentionScore > 0.8, "Attention peak should have score > 0.8")
        }
    }
    
    // ===== REALTIME COUCHDB INTEGRATION TESTS =====
    
    @Test
    fun `should analyze realtime CouchDB changes`() = runTest {
        // Given: Forensic session and CouchDB changes
        val repoId = "test-repo-007"
        val session = forensicsService.initializeForensics(repoId)
        
        val changes = flowOf(
            CouchChange(
                id = "doc-1",
                seq = "1",
                changes = listOf(CouchChangeItem("1-abc")),
                deleted = false,
                document = CouchDocument(id = "doc-1", data = mapOf("content" to "test"))
            ),
            CouchChange(
                id = "doc-2",
                seq = "2",
                changes = listOf(CouchChangeItem("1-def")),
                deleted = true,
                document = null
            )
        )
        
        // When: Analyzing realtime changes
        val forensicEvents = mutableListOf<ForensicEvent>()
        forensicsService.analyzeRealtimeChanges(repoId, changes).collect { event ->
            forensicEvents.add(event)
        }
        
        // Then: Should generate forensic events for each change
        assertEquals(2, forensicEvents.size)
        
        forensicEvents.forEach { event ->
            assertEquals(ForensicEventType.COUCH_CHANGE, event.type)
            assertTrue(event.attentionScore >= 0.0 && event.attentionScore <= 1.0)
            assertTrue(event.data.containsKey("documentId"))
            assertTrue(event.data.containsKey("sequence"))
            assertTrue(event.data.containsKey("deleted"))
        }
        
        // Check that deletion has lower attention score
        val deletionEvent = forensicEvents.find { event ->
            event.data["deleted"] == "true"
        }
        assertNotNull(deletionEvent)
        assertTrue(deletionEvent.attentionScore < 0.6, "Deletion should have lower attention score")
    }
    
    @Test
    fun `should store forensic events in CouchDB`() = runTest {
        // Given: Forensic session
        val repoId = "test-repo-008"
        val session = forensicsService.initializeForensics(repoId)
        
        // When: Analyzing changes
        val changes = flowOf(
            CouchChange(
                id = "test-doc",
                seq = "1",
                changes = listOf(CouchChangeItem("1-abc")),
                deleted = false,
                document = CouchDocument(id = "test-doc", data = mapOf("content" to "test"))
            )
        )
        
        forensicsService.analyzeRealtimeChanges(repoId, changes).collect { }
        
        // Then: Forensic event should be stored in CouchDB
        val documents = couchService.queryDocuments(session.databaseName, mapOf("type" to "forensic_event"))
        assertTrue(documents.isNotEmpty(), "Forensic events should be stored in CouchDB")
        
        val forensicDoc = documents.find { doc ->
            doc.data["type"]?.jsonPrimitive?.content == "forensic_event"
        }
        assertNotNull(forensicDoc, "Should find forensic event document")
    }
    
    // ===== ATTENTION PATTERN TESTS =====
    
    @Test
    fun `should analyze attention patterns from forensic events`() = runTest {
        // Given: Forensic session with events
        val repoId = "test-repo-009"
        val session = forensicsService.initializeForensics(repoId)
        
        // Add test forensic events
        val testEvents = listOf(
            ForensicEvent(
                id = "event-1",
                timestamp = Clock.System.now().toEpochMilliseconds(),
                type = ForensicEventType.COMMIT_MESSAGE,
                data = mapOf("message" to "Test commit"),
                attentionScore = 0.6
            ),
            ForensicEvent(
                id = "event-2",
                timestamp = Clock.System.now().toEpochMilliseconds() + 1000,
                type = ForensicEventType.COMMIT_DIFF,
                data = mapOf("diff" to "Test diff"),
                attentionScore = 0.8
            ),
            ForensicEvent(
                id = "event-3",
                timestamp = Clock.System.now().toEpochMilliseconds() + 2000,
                type = ForensicEventType.ATTENTION_PEAK,
                data = mapOf("peak" to "high"),
                attentionScore = 0.9
            )
        )
        
        testEvents.forEach { event ->
            session.addForensicEvent(event)
        }
        
        // When: Analyzing attention patterns
        val patterns = forensicsService.getAttentionPatterns(repoId)
        
        // Then: Should return attention patterns
        assertNotNull(patterns)
        assertEquals(repoId, patterns.repoId)
        assertTrue(patterns.patterns.containsKey("average"))
        assertTrue(patterns.patterns.containsKey("max"))
        assertTrue(patterns.patterns.containsKey("min"))
        
        // Check pattern values
        assertEquals(0.77, patterns.patterns["average"]!!, 0.01)
        assertEquals(0.9, patterns.patterns["max"]!!, 0.01)
        assertEquals(0.6, patterns.patterns["min"]!!, 0.01)
        
        // Check attention peaks
        assertEquals(1, patterns.peaks.size, "Should detect one attention peak")
    }
    
    @Test
    fun `should analyze attention patterns in time range`() = runTest {
        // Given: Forensic session with events over time
        val repoId = "test-repo-010"
        val session = forensicsService.initializeForensics(repoId)
        
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Add events with different timestamps
        val oldEvent = ForensicEvent(
            id = "old-event",
            timestamp = now - 10000, // 10 seconds ago
            type = ForensicEventType.COMMIT_MESSAGE,
            data = mapOf("message" to "Old commit"),
            attentionScore = 0.5
        )
        
        val recentEvent = ForensicEvent(
            id = "recent-event",
            timestamp = now - 1000, // 1 second ago
            type = ForensicEventType.COMMIT_DIFF,
            data = mapOf("diff" to "Recent diff"),
            attentionScore = 0.8
        )
        
        session.addForensicEvent(oldEvent)
        session.addForensicEvent(recentEvent)
        
        // When: Analyzing patterns in recent time range
        val patterns = forensicsService.getAttentionPatterns(repoId, timeRange = 5000L) // Last 5 seconds
        
        // Then: Should only include recent events
        assertEquals(1, patterns.peaks.size, "Should only include recent events in time range")
        assertEquals(0.8, patterns.patterns["average"]!!, 0.01)
    }
    
    // ===== ERROR HANDLING TESTS =====
    
    @Test
    fun `should handle missing forensic session gracefully`() = runTest {
        // Given: No forensic session
        val repoId = "nonexistent-repo"
        
        // When/Then: Should throw exception for missing session
        assertFailsWith<IllegalArgumentException> {
            forensicsService.analyzeGitObjects(repoId, 1 j { "test-hash" })
        }
        
        assertFailsWith<IllegalArgumentException> {
            forensicsService.replayGitScene(repoId, "commit-1..commit-2", "test-scene")
        }
        
        assertFailsWith<IllegalArgumentException> {
            forensicsService.getAttentionPatterns(repoId)
        }
    }
    
    @Test
    fun `should handle empty git object analysis`() = runTest {
        // Given: Forensic session
        val repoId = "test-repo-011"
        forensicsService.initializeForensics(repoId)
        
        // When: Analyzing empty object list
        val analysis = forensicsService.analyzeGitObjects(repoId, 0 j { throw IndexOutOfBoundsException() })
        
        // Then: Should return empty analysis
        assertNotNull(analysis)
        assertEquals(0, analysis.artifacts.size)
        assertEquals(0, analysis.attentionScores.size)
        assertEquals(0, analysis.taxonomicClasses.size)
    }
    
    // ===== INTEGRATION TESTS =====
    
    @Test
    fun `should integrate git history with CouchDB forensics`() = runTest {
        // Given: Complete setup
        val repoId = "integration-test-repo"
        val session = forensicsService.initializeForensics(repoId)
        
        // Initialize git history
        gitHistoryService.initializeHistory(repoId, "Initial content")
        
        // Create git commit
        val commit = GitCommit(
            commitId = "integration-commit",
            parentCommitId = null,
            author = "integration-test",
            timestamp = Clock.System.now().toEpochMilliseconds(),
            message = "Integration test commit",
            diff = "Integration test diff",
            waveletId = "integration-wavelet",
            documentState = "Integration content"
        )
        
        gitHistoryService.addCommit(repoId, commit)
        
        // When: Performing comprehensive analysis
        val objectHashes = 1 j { "integration-commit" }
        val analysis = forensicsService.analyzeGitObjects(repoId, objectHashes)
        val classification = forensicsService.classifyGitObjects(repoId, objectHashes)
        val replay = forensicsService.replayGitScene(repoId, "integration-commit", "integration-scene")
        
        // Then: All components should work together
        assertNotNull(analysis)
        assertNotNull(classification)
        assertNotNull(replay)
        
        // Check CouchDB storage
        val documents = couchService.queryDocuments(session.databaseName, mapOf("type" to "forensic_analysis"))
        assertTrue(documents.isNotEmpty(), "Analysis should be stored in CouchDB")
        
        val replayDocs = couchService.queryDocuments(session.databaseName, mapOf("type" to "scene_replay"))
        assertTrue(replayDocs.isNotEmpty(), "Scene replay should be stored in CouchDB")
    }
} 