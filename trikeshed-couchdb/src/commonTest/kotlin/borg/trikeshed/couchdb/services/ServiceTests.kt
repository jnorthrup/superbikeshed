package borg.trikeshed.couchdb.services

import borg.trikeshed.couchdb.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Service Tests Suite
 * 
 * TDD Test Suite for all service implementations:
 * - CouchDBService
 * - GitForensicsService
 * - GitHistoryService
 * - PatrickDevineAgent
 */

class ServiceTests {

    // ===== COUCHDB SERVICE TESTS =====

    @Test
    fun `test CouchDB service initialization`() = runTest {
        val service = CouchDBService()
        val initialized = service.initialize()
        assertTrue(initialized)
    }

    @Test
    fun `test CouchDB database creation`() = runTest {
        val service = CouchDBService()
        service.initialize()

        val dbInfo = service.createDatabase("test_db")

        assertEquals("test_db", dbInfo.db_name)
        assertEquals(0, dbInfo.doc_count)
        assertEquals(0, dbInfo.doc_del_count)
        assertFalse(dbInfo.compact_running)
        assertEquals(8, dbInfo.disk_format_version)
    }

    @Test
    fun `test CouchDB database deletion`() = runTest {
        val service = CouchDBService()
        service.initialize()

        service.createDatabase("test_db")
        val deleted = service.deleteDatabase("test_db")

        assertTrue(deleted)
    }

    @Test
    fun `test CouchDB document creation`() = runTest {
        val service = CouchDBService()
        service.initialize()
        service.createDatabase("test_db")

        val document = CouchDocument(
            _id = "test_doc",
            _rev = "",
            data = JsonObject(mapOf(
                "name" to JsonPrimitive("Test Document"),
                "value" to JsonPrimitive(42)
            ))
        )

        val createdDoc = service.createDocument("test_db", document)

        assertEquals("test_doc", createdDoc._id)
        assertNotEquals("", createdDoc._rev)
        assertEquals("Test Document", createdDoc.data["name"]?.jsonPrimitive?.content)
        assertEquals(42, createdDoc.data["value"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `test CouchDB document retrieval`() = runTest {
        val service = CouchDBService()
        service.initialize()
        service.createDatabase("test_db")

        val document = CouchDocument(
            _id = "test_doc",
            _rev = "",
            data = JsonObject(mapOf(
                "name" to JsonPrimitive("Test Document"),
                "value" to JsonPrimitive(42)
            ))
        )

        service.createDocument("test_db", document)
        val retrievedDoc = service.getDocument("test_db", "test_doc")

        assertNotNull(retrievedDoc)
        assertEquals("test_doc", retrievedDoc!!._id)
        assertEquals("Test Document", retrievedDoc.data["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test CouchDB document update`() = runTest {
        val service = CouchDBService()
        service.initialize()
        service.createDatabase("test_db")

        val document = CouchDocument(
            _id = "test_doc",
            _rev = "",
            data = JsonObject(mapOf(
                "name" to JsonPrimitive("Original Name"),
                "value" to JsonPrimitive(42)
            ))
        )

        val createdDoc = service.createDocument("test_db", document)
        
        val updatedDoc = createdDoc.copy(
            data = JsonObject(mapOf(
                "name" to JsonPrimitive("Updated Name"),
                "value" to JsonPrimitive(84)
            ))
        )

        val result = service.updateDocument("test_db", updatedDoc)

        assertEquals("test_doc", result._id)
        assertNotEquals(createdDoc._rev, result._rev)
        assertEquals("Updated Name", result.data["name"]?.jsonPrimitive?.content)
        assertEquals(84, result.data["value"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `test CouchDB document deletion`() = runTest {
        val service = CouchDBService()
        service.initialize()
        service.createDatabase("test_db")

        val document = CouchDocument(
            _id = "test_doc",
            _rev = "",
            data = JsonObject(mapOf("name" to JsonPrimitive("Test Document")))
        )

        val createdDoc = service.createDocument("test_db", document)
        val deleted = service.deleteDocument("test_db", "test_doc", createdDoc._rev)

        assertTrue(deleted)
    }

    @Test
    fun `test CouchDB bulk operations`() = runTest {
        val service = CouchDBService()
        service.initialize()
        service.createDatabase("test_db")

        val documents = listOf(
            CouchDocument("doc1", "", JsonObject(mapOf("name" to JsonPrimitive("Doc 1")))),
            CouchDocument("doc2", "", JsonObject(mapOf("name" to JsonPrimitive("Doc 2")))),
            CouchDocument("doc3", "", JsonObject(mapOf("name" to JsonPrimitive("Doc 3"))))
        )

        val results = service.bulkDocs("test_db", documents)

        assertEquals(3, results.size)
        results.forEach { result ->
            assertTrue(result.ok)
            assertNotNull(result.id)
            assertNotNull(result.rev)
        }
    }

    @Test
    fun `test CouchDB connection management`() = runTest {
        val service = CouchDBService()
        service.initialize()
        service.createDatabase("test_db")

        val connection = service.createConnection("test_db")

        assertEquals("test_db", connection.databaseName)
        assertTrue(connection.isActive)
        assertTrue(connection.id.startsWith("conn-"))

        service.closeConnection(connection.id)
        // Connection should be closed
    }

    @Test
    fun `test CouchDB changes subscription`() = runTest {
        val service = CouchDBService()
        service.initialize()
        service.createDatabase("test_db")

        var changeReceived = false
        service.subscribeToChanges("test_db") { change ->
            changeReceived = true
        }

        // Create a document to trigger change
        val document = CouchDocument(
            _id = "test_doc",
            _rev = "",
            data = JsonObject(mapOf("name" to JsonPrimitive("Test Document")))
        )

        service.createDocument("test_db", document)

        // Give some time for the change to be processed
        delay(100)
        assertTrue(changeReceived)
    }

    // ===== GIT FORENSICS SERVICE TESTS =====

    @Test
    fun `test Git forensics service initialization`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val forensicsService = GitForensicsService(couchService, gitHistoryService)

        couchService.initialize()
        val session = forensicsService.initializeForensics("test_repo")

        assertEquals("test_repo", session.id)
        assertEquals("git_forensics_test_repo", session.databaseName)
        assertTrue(session.startTime > 0)
        assertNotNull(session.attentionModel)
        assertNotNull(session.patternDetector)
    }

    @Test
    fun `test Git object analysis`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val forensicsService = GitForensicsService(couchService, gitHistoryService)

        couchService.initialize()
        forensicsService.initializeForensics("test_repo")

        val objectData = "This is a test git object".encodeToByteArray()
        val analysis = forensicsService.analyzeGitObject(
            repoId = "test_repo",
            objectId = "abc123",
            objectType = GitObjectType.BLOB,
            objectData = objectData
        )

        assertEquals("abc123", analysis.objectId)
        assertEquals(GitObjectType.BLOB, analysis.objectType)
        assertTrue(analysis.analysisTime > 0)
        assertTrue(analysis.attentionScore >= 0.0 && analysis.attentionScore <= 1.0)
        assertNotNull(analysis.patterns)
        assertNotNull(analysis.metadata)
    }

    @Test
    fun `test Git forensic event creation`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val forensicsService = GitForensicsService(couchService, gitHistoryService)

        couchService.initialize()
        forensicsService.initializeForensics("test_repo")

        val eventData = mapOf(
            "commit_id" to "abc123",
            "author" to "test@example.com",
            "message" to "Test commit"
        )

        val event = forensicsService.createForensicEvent(
            repoId = "test_repo",
            eventType = GitForensicEventType.COMMIT,
            eventData = eventData
        )

        assertEquals(GitForensicEventType.COMMIT, event.eventType)
        assertTrue(event.timestamp > 0)
        assertEquals(eventData, event.data)
        assertTrue(event.attentionScore >= 0.0 && event.attentionScore <= 1.0)
    }

    @Test
    fun `test Git scene replay creation`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val forensicsService = GitForensicsService(couchService, gitHistoryService)

        couchService.initialize()
        forensicsService.initializeForensics("test_repo")

        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - 3600000 // 1 hour ago
        val endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        val replay = forensicsService.createSceneReplay(
            repoId = "test_repo",
            startTime = startTime,
            endTime = endTime
        )

        assertEquals(startTime, replay.startTime)
        assertEquals(endTime, replay.endTime)
        assertNotNull(replay.events)
        assertTrue(replay.attentionScore >= 0.0 && replay.attentionScore <= 1.0)
    }

    @Test
    fun `test Git realtime analysis`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val forensicsService = GitForensicsService(couchService, gitHistoryService)

        couchService.initialize()
        forensicsService.initializeForensics("test_repo")

        val analysisFlow = forensicsService.analyzeRealtimeChanges("test_repo")
        
        // The flow should be created successfully
        assertNotNull(analysisFlow)
    }

    // ===== GIT HISTORY SERVICE TESTS =====

    @Test
    fun `test Git history service repository initialization`() = runTest {
        val gitHistoryService = GitHistoryService()

        val repository = gitHistoryService.initializeRepository("test_repo", "/path/to/repo")

        assertEquals("test_repo", repository.id)
        assertEquals("/path/to/repo", repository.path)
        assertTrue(repository.createdAt > 0)
        assertTrue(repository.isActive)
    }

    @Test
    fun `test Git commit addition`() = runTest {
        val gitHistoryService = GitHistoryService()
        gitHistoryService.initializeRepository("test_repo", "/path/to/repo")

        val commit = gitHistoryService.addCommit(
            repoId = "test_repo",
            commitId = "abc123",
            author = "test@example.com",
            message = "Initial commit",
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            parentIds = emptyList()
        )

        assertEquals("abc123", commit.id)
        assertEquals("test@example.com", commit.author)
        assertEquals("Initial commit", commit.message)
        assertTrue(commit.timestamp > 0)
        assertTrue(commit.parentIds.isEmpty())
    }

    @Test
    fun `test Git branch addition`() = runTest {
        val gitHistoryService = GitHistoryService()
        gitHistoryService.initializeRepository("test_repo", "/path/to/repo")

        val branch = gitHistoryService.addBranch(
            repoId = "test_repo",
            branchName = "main",
            commitId = "abc123",
            isActive = true
        )

        assertEquals("main", branch.name)
        assertEquals("abc123", branch.commitId)
        assertTrue(branch.isActive)
        assertTrue(branch.createdAt > 0)
    }

    @Test
    fun `test Git commit history retrieval`() = runTest {
        val gitHistoryService = GitHistoryService()
        gitHistoryService.initializeRepository("test_repo", "/path/to/repo")

        // Add multiple commits
        repeat(5) { i ->
            gitHistoryService.addCommit(
                repoId = "test_repo",
                commitId = "commit$i",
                author = "test@example.com",
                message = "Commit $i",
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + i
            )
        }

        val history = gitHistoryService.getCommitHistory("test_repo", limit = 3)

        assertEquals(3, history.size)
        assertEquals("commit2", history[0].id)
        assertEquals("commit3", history[1].id)
        assertEquals("commit4", history[2].id)
    }

    @Test
    fun `test Git branch history retrieval`() = runTest {
        val gitHistoryService = GitHistoryService()
        gitHistoryService.initializeRepository("test_repo", "/path/to/repo")

        // Add multiple branches
        gitHistoryService.addBranch("test_repo", "main", "abc123", true)
        gitHistoryService.addBranch("test_repo", "feature", "def456", true)
        gitHistoryService.addBranch("test_repo", "hotfix", "ghi789", false)

        val branches = gitHistoryService.getBranchHistory("test_repo")

        assertEquals(3, branches.size)
        assertTrue(branches.any { it.name == "main" && it.isActive })
        assertTrue(branches.any { it.name == "feature" && it.isActive })
        assertTrue(branches.any { it.name == "hotfix" && !it.isActive })
    }

    @Test
    fun `test Git commit pattern analysis`() = runTest {
        val gitHistoryService = GitHistoryService()
        gitHistoryService.initializeRepository("test_repo", "/path/to/repo")

        // Add commits from different authors
        repeat(3) { i ->
            gitHistoryService.addCommit(
                repoId = "test_repo",
                commitId = "commit$i",
                author = "author$i@example.com",
                message = "Commit $i",
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + i
            )
        }

        val patterns = gitHistoryService.analyzeCommitPatterns("test_repo")

        assertEquals(3, patterns.totalCommits)
        assertEquals(3, patterns.uniqueAuthors)
        assertEquals(3, patterns.authorActivity.size)
        assertTrue(patterns.averageCommitsPerDay > 0.0)
    }

    // ===== PATRICK DEVINE AGENT TESTS =====

    @Test
    fun `test Patrick Devine agent initialization`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        couchService.initialize()
        val initialized = agent.initialize()

        assertTrue(initialized)
    }

    @Test
    fun `test Patrick Devine processing session creation`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        couchService.initialize()
        agent.initialize()

        val session = agent.startProcessingSession(
            sessionId = "test_session",
            databaseName = "test_db",
            processingType = ProcessingType.REAL_TIME
        )

        assertEquals("test_session", session.id)
        assertEquals("test_db", session.databaseName)
        assertEquals(ProcessingType.REAL_TIME, session.processingType)
        assertTrue(session.startTime > 0)
        assertEquals(ProcessingStatus.ACTIVE, session.status)
        assertTrue(session.agentId.startsWith("patrick_devine_"))
    }

    @Test
    fun `test Patrick Devine document processing`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        couchService.initialize()
        agent.initialize()

        val session = agent.startProcessingSession(
            sessionId = "test_session",
            databaseName = "test_db",
            processingType = ProcessingType.REAL_TIME
        )

        val document = CouchDocument(
            _id = "test_doc",
            _rev = "",
            data = JsonObject(mapOf(
                "text" to JsonPrimitive("This is a test document with some content"),
                "value" to JsonPrimitive(42)
            ))
        )

        val context = ProcessingContext(
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            user = "test_user",
            source = "test_source"
        )

        val result = agent.processDocument(session.id, document, context)

        assertEquals("test_doc", result.documentId)
        assertEquals("test_session", result.sessionId)
        assertNotNull(result.analysis)
        assertNotNull(result.intelligence)
        assertNotNull(result.actions)
        assertNotNull(result.results)
        assertTrue(result.processingTime >= 0)
    }

    @Test
    fun `test Patrick Devine document pattern analysis`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        couchService.initialize()
        agent.initialize()

        val session = agent.startProcessingSession(
            sessionId = "test_session",
            databaseName = "test_db",
            processingType = ProcessingType.BATCH
        )

        val documents = listOf(
            CouchDocument("doc1", "", JsonObject(mapOf("text" to JsonPrimitive("Document 1")))),
            CouchDocument("doc2", "", JsonObject(mapOf("text" to JsonPrimitive("Document 2")))),
            CouchDocument("doc3", "", JsonObject(mapOf("text" to JsonPrimitive("Document 3"))))
        )

        val analysis = agent.analyzeDocumentPatterns(session.id, documents)

        assertEquals("test_session", analysis.sessionId)
        assertEquals(3, analysis.documentCount)
        assertNotNull(analysis.patterns)
        assertNotNull(analysis.correlations)
        assertNotNull(analysis.anomalies)
        assertTrue(analysis.analysisTime > 0)
    }

    @Test
    fun `test Patrick Devine agent insights`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        couchService.initialize()
        agent.initialize()

        val session = agent.startProcessingSession(
            sessionId = "test_session",
            databaseName = "test_db",
            processingType = ProcessingType.REAL_TIME
        )

        val insights = agent.getAgentInsights(session.id)

        assertEquals("test_session", insights.sessionId)
        assertTrue(insights.totalActions >= 0)
        assertTrue(insights.successfulActions >= 0)
        assertTrue(insights.averageProcessingTime >= 0.0)
        assertNotNull(insights.topPatterns)
        assertNotNull(insights.recommendations)
        assertTrue(insights.insightsTime > 0)
    }

    @Test
    fun `test Patrick Devine processing events subscription`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        couchService.initialize()
        agent.initialize()

        val session = agent.startProcessingSession(
            sessionId = "test_session",
            databaseName = "test_db",
            processingType = ProcessingType.REAL_TIME
        )

        val eventsFlow = agent.subscribeToProcessingEvents(session.id)
        
        // The flow should be created successfully
        assertNotNull(eventsFlow)
    }

    // ===== INTEGRATION TESTS =====

    @Test
    fun `test service integration`() = runTest {
        // Test that all services can work together
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        // Initialize all services
        assertTrue(couchService.initialize())
        assertTrue(agent.initialize())

        // Create a database
        val dbInfo = couchService.createDatabase("integration_test_db")
        assertEquals("integration_test_db", dbInfo.db_name)

        // Initialize git repository
        val repo = gitHistoryService.initializeRepository("test_repo", "/path/to/repo")
        assertEquals("test_repo", repo.id)

        // Initialize forensics
        val forensicsSession = gitForensicsService.initializeForensics("test_repo")
        assertEquals("test_repo", forensicsSession.id)

        // Start agent processing
        val agentSession = agent.startProcessingSession(
            sessionId = "integration_session",
            databaseName = "integration_test_db",
            processingType = ProcessingType.REAL_TIME
        )
        assertEquals("integration_session", agentSession.id)

        // All services should work together without conflicts
        assertNotNull(couchService)
        assertNotNull(gitHistoryService)
        assertNotNull(gitForensicsService)
        assertNotNull(agent)
    }

    @Test
    fun `test service error handling`() = runTest {
        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        // Test error handling for non-existent resources
        assertFailsWith<CouchDBServiceException> {
            couchService.getDocument("non_existent_db", "non_existent_doc")
        }

        assertFailsWith<GitForensicsException> {
            gitForensicsService.analyzeGitObject(
                repoId = "non_existent_repo",
                objectId = "abc123",
                objectType = GitObjectType.BLOB,
                objectData = "test".encodeToByteArray()
            )
        }

        assertFailsWith<PatrickDevineException> {
            agent.processDocument(
                sessionId = "non_existent_session",
                document = CouchDocument("test", "", JsonObject(emptyMap())),
                context = ProcessingContext()
            )
        }
    }

    @Test
    fun `test service performance characteristics`() = runTest {
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        val couchService = CouchDBService()
        val gitHistoryService = GitHistoryService()
        val gitForensicsService = GitForensicsService(couchService, gitHistoryService)
        val agent = PatrickDevineAgent(couchService, gitForensicsService)

        // Initialize services
        couchService.initialize()
        agent.initialize()

        // Perform multiple operations
        repeat(10) { i ->
            val dbName = "perf_test_db_$i"
            couchService.createDatabase(dbName)
            
            val document = CouchDocument(
                _id = "doc_$i",
                _rev = "",
                data = JsonObject(mapOf("index" to JsonPrimitive(i)))
            )
            
            couchService.createDocument(dbName, document)
        }

        val endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val duration = endTime - startTime

        // Verify operations complete within reasonable time
        assertTrue(duration < 10000, "Service operations took too long: ${duration}ms")
    }
} 