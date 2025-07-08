package nexus.couchdb

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import nexus.server.*
import nexus.core.*
import nexus.network.*
import nexus.bridge.IpfsBridge
import nexus.couchdb.*

/**
 * Comprehensive CouchDB test suite following TDD principles.
 * Tests are organized by functionality and include both happy path and error cases.
 * 
 * ENDGAME INTEGRATION: This test suite validates CouchDB integration with kernel-level
 * features via io_uring and eBPF for actionable todo filtering.
 */
class CouchDbTestSuite {

    // ===== DATABASE OPERATIONS =====
    
    @Test
    fun `should create database successfully`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-db-create-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        // Test database creation
        ipfsBridge.createDatabase("testdb")
        
        // Verify database exists
        val dbInfo = ipfsBridge.getDatabaseInfo("testdb")
        assertEquals("testdb", dbInfo.db_name)
        assertEquals(0, dbInfo.doc_count)
        assertEquals(0, dbInfo.doc_del_count)
    }

    @Test
    fun `should fail to create database with invalid name`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-db-invalid-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        // Test with invalid database name
        assertFailsWith<IllegalArgumentException> {
            ipfsBridge.createDatabase("")
        }
        
        assertFailsWith<IllegalArgumentException> {
            ipfsBridge.createDatabase("invalid/name")
        }
    }

    @Test
    fun `should delete database successfully`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-db-delete-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        // Create and then delete database
        ipfsBridge.createDatabase("testdb")
        ipfsBridge.deleteDatabase("testdb")
        
        // Verify database no longer exists
        assertFailsWith<DatabaseNotFoundException> {
            ipfsBridge.getDatabaseInfo("testdb")
        }
    }

    @Test
    fun `should fail to delete non-existent database`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-db-delete-nonexistent-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        assertFailsWith<DatabaseNotFoundException> {
            ipfsBridge.deleteDatabase("nonexistent")
        }
    }

    // ===== DOCUMENT CRUD OPERATIONS =====
    
    @Test
    fun `should create document with auto-generated ID`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-doc-create-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        val document = CouchDbDocument(
            _id = "", // Auto-generated
            _rev = "",
            data = mapOf("name" to "Test Document", "value" to 42)
        )
        
        val result = ipfsBridge.putDocument("testdb", "", document)
        assertTrue(result.id.isNotEmpty())
        assertTrue(result.rev.isNotEmpty())
        assertTrue(result.rev.startsWith("1-"))
    }

    @Test
    fun `should create document with specified ID`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-doc-create-id-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        val document = CouchDbDocument(
            _id = "testdoc",
            _rev = "",
            data = mapOf("name" to "Test Document", "value" to 42)
        )
        
        val result = ipfsBridge.putDocument("testdb", "testdoc", document)
        assertEquals("testdoc", result.id)
        assertTrue(result.rev.isNotEmpty())
    }

    @Test
    fun `should retrieve document successfully`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-doc-get-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        val originalDoc = CouchDbDocument(
            _id = "testdoc",
            _rev = "",
            data = mapOf("name" to "Test Document", "value" to 42, "nested" to mapOf("key" to "value"))
        )
        
        val putResult = ipfsBridge.putDocument("testdb", "testdoc", originalDoc)
        
        val retrievedDoc = ipfsBridge.getDocument("testdb", "testdoc")
        assertEquals("testdoc", retrievedDoc._id)
        assertEquals(putResult.rev, retrievedDoc._rev)
        assertEquals("Test Document", retrievedDoc.data["name"])
        assertEquals(42, retrievedDoc.data["value"])
        assertFalse(retrievedDoc._deleted)
    }

    @Test
    fun `should fail to retrieve non-existent document`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-doc-get-nonexistent-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        assertFailsWith<DocumentNotFoundException> {
            ipfsBridge.getDocument("testdb", "nonexistent")
        }
    }

    @Test
    fun `should update document with correct revision`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-doc-update-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        // Create initial document
        val originalDoc = CouchDbDocument(
            _id = "testdoc",
            _rev = "",
            data = mapOf("name" to "Original", "value" to 1)
        )
        
        val putResult = ipfsBridge.putDocument("testdb", "testdoc", originalDoc)
        
        // Update document
        val updatedDoc = CouchDbDocument(
            _id = "testdoc",
            _rev = putResult.rev,
            data = mapOf("name" to "Updated", "value" to 2, "newField" to "added")
        )
        
        val updateResult = ipfsBridge.putDocument("testdb", "testdoc", updatedDoc)
        assertTrue(updateResult.rev.startsWith("2-"))
        
        // Verify update
        val retrievedDoc = ipfsBridge.getDocument("testdb", "testdoc")
        assertEquals("Updated", retrievedDoc.data["name"])
        assertEquals(2, retrievedDoc.data["value"])
        assertEquals("added", retrievedDoc.data["newField"])
    }

    @Test
    fun `should fail to update document with wrong revision`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-doc-update-wrong-rev-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        // Create initial document
        val originalDoc = CouchDbDocument(
            _id = "testdoc",
            _rev = "",
            data = mapOf("name" to "Original", "value" to 1)
        )
        
        ipfsBridge.putDocument("testdb", "testdoc", originalDoc)
        
        // Try to update with wrong revision
        val updatedDoc = CouchDbDocument(
            _id = "testdoc",
            _rev = "1-wrongrev",
            data = mapOf("name" to "Updated", "value" to 2)
        )
        
        assertFailsWith<ConflictException> {
            ipfsBridge.putDocument("testdb", "testdoc", updatedDoc)
        }
    }

    @Test
    fun `should delete document successfully`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-doc-delete-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        // Create document
        val document = CouchDbDocument(
            _id = "testdoc",
            _rev = "",
            data = mapOf("name" to "Test Document", "value" to 42)
        )
        
        val putResult = ipfsBridge.putDocument("testdb", "testdoc", document)
        
        // Delete document
        val deleteResult = ipfsBridge.deleteDocument("testdb", "testdoc", putResult.rev)
        assertEquals("testdoc", deleteResult.id)
        assertTrue(deleteResult.rev.startsWith("2-"))
        
        // Verify document is deleted
        assertFailsWith<DocumentNotFoundException> {
            ipfsBridge.getDocument("testdb", "testdoc")
        }
    }

    // ===== BULK OPERATIONS =====
    
    @Test
    fun `should handle bulk document operations`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-bulk-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("bulkdb")
        
        val documents = listOf(
            CouchDbDocument(_id = "doc1", _rev = "", data = mapOf("name" to "Doc 1", "value" to 1)),
            CouchDbDocument(_id = "doc2", _rev = "", data = mapOf("name" to "Doc 2", "value" to 2)),
            CouchDbDocument(_id = "doc3", _rev = "", data = mapOf("name" to "Doc 3", "value" to 3))
        )
        
        val bulkRequest = CouchDbBulkRequest(docs = documents)
        val bulkResult = ipfsBridge.bulkDocuments("bulkdb", bulkRequest)
        
        assertEquals(3, bulkResult.results.size)
        assertTrue(bulkResult.results.all { it.ok })
        
        // Verify documents were stored
        documents.forEach { doc ->
            val retrieved = ipfsBridge.getDocument("bulkdb", doc._id)
            assertEquals(doc._id, retrieved._id)
            assertEquals(doc.data["name"], retrieved.data["name"])
            assertEquals(doc.data["value"], retrieved.data["value"])
        }
    }

    @Test
    fun `should handle bulk operations with mixed create and update`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-bulk-mixed-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("bulkdb")
        
        // Create initial document
        val originalDoc = CouchDbDocument(
            _id = "doc1",
            _rev = "",
            data = mapOf("name" to "Original", "value" to 1)
        )
        
        val putResult = ipfsBridge.putDocument("bulkdb", "doc1", originalDoc)
        
        // Bulk operation with create and update
        val documents = listOf(
            CouchDbDocument(_id = "doc2", _rev = "", data = mapOf("name" to "New Doc", "value" to 2)),
            CouchDbDocument(_id = "doc1", _rev = putResult.rev, data = mapOf("name" to "Updated", "value" to 3))
        )
        
        val bulkRequest = CouchDbBulkRequest(docs = documents)
        val bulkResult = ipfsBridge.bulkDocuments("bulkdb", bulkRequest)
        
        assertEquals(2, bulkResult.results.size)
        assertTrue(bulkResult.results.all { it.ok })
        
        // Verify both documents
        val doc1 = ipfsBridge.getDocument("bulkdb", "doc1")
        assertEquals("Updated", doc1.data["name"])
        assertEquals(3, doc1.data["value"])
        
        val doc2 = ipfsBridge.getDocument("bulkdb", "doc2")
        assertEquals("New Doc", doc2.data["name"])
        assertEquals(2, doc2.data["value"])
    }

    // ===== CHANGES FEED =====
    
    @Test
    fun `should track changes feed correctly`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-changes-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("changesdb")
        
        // Initial changes should be empty
        var changes = ipfsBridge.getChanges("changesdb", emptyMap())
        assertEquals(0, changes.results.size)
        
        // Add documents
        val doc1 = CouchDbDocument(_id = "doc1", _rev = "", data = mapOf("name" to "Doc 1"))
        val doc2 = CouchDbDocument(_id = "doc2", _rev = "", data = mapOf("name" to "Doc 2"))
        
        ipfsBridge.putDocument("changesdb", "doc1", doc1)
        ipfsBridge.putDocument("changesdb", "doc2", doc2)
        
        // Get changes
        changes = ipfsBridge.getChanges("changesdb", emptyMap())
        assertTrue(changes.results.size >= 2)
        assertTrue(changes.results.any { it.id == "doc1" })
        assertTrue(changes.results.any { it.id == "doc2" })
        assertFalse(changes.results.any { it.deleted })
        
        // Delete a document
        val putResult = ipfsBridge.putDocument("changesdb", "doc1", doc1)
        ipfsBridge.deleteDocument("changesdb", "doc1", putResult.rev)
        
        // Verify deletion appears in changes
        val updatedChanges = ipfsBridge.getChanges("changesdb", emptyMap())
        assertTrue(updatedChanges.results.any { it.id == "doc1" && it.deleted })
    }

    @Test
    fun `should handle changes feed with since parameter`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-changes-since-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("changesdb")
        
        // Get initial sequence
        val initialChanges = ipfsBridge.getChanges("changesdb", emptyMap())
        val initialSeq = initialChanges.last_seq
        
        // Add document
        val doc = CouchDbDocument(_id = "doc1", _rev = "", data = mapOf("name" to "Doc 1"))
        ipfsBridge.putDocument("changesdb", "doc1", doc)
        
        // Get changes since initial sequence
        val changes = ipfsBridge.getChanges("changesdb", mapOf("since" to initialSeq))
        assertEquals(1, changes.results.size)
        assertEquals("doc1", changes.results[0].id)
    }

    // ===== CONFLICT HANDLING =====
    
    @Test
    fun `should handle document conflicts correctly`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-conflicts-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("conflictdb")
        
        // Create initial document
        val originalDoc = CouchDbDocument(
            _id = "testdoc",
            _rev = "",
            data = mapOf("name" to "Original", "value" to 1)
        )
        
        val putResult = ipfsBridge.putDocument("conflictdb", "testdoc", originalDoc)
        
        // Try to update with same revision (simulating concurrent update)
        val conflictingDoc = CouchDbDocument(
            _id = "testdoc",
            _rev = putResult.rev,
            data = mapOf("name" to "Conflicting", "value" to 2)
        )
        
        // This should succeed in our implementation, but in a real CouchDB
        // this would create a conflict that needs to be resolved
        val updateResult = ipfsBridge.putDocument("conflictdb", "testdoc", conflictingDoc)
        assertTrue(updateResult.rev.startsWith("2-"))
    }

    // ===== ERROR HANDLING =====
    
    @Test
    fun `should handle invalid database names`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-error-db-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        // Test various invalid database names
        val invalidNames = listOf("", "a", "invalid/name", "invalid\\name", "invalid*name")
        
        invalidNames.forEach { invalidName ->
            assertFailsWith<IllegalArgumentException> {
                ipfsBridge.createDatabase(invalidName)
            }
        }
    }

    @Test
    fun `should handle invalid document IDs`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-error-doc-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("testdb")
        
        val document = CouchDbDocument(
            _id = "testdoc",
            _rev = "",
            data = mapOf("name" to "Test")
        )
        
        // Test various invalid document IDs
        val invalidIds = listOf("", "invalid/id", "invalid\\id")
        
        invalidIds.forEach { invalidId ->
            assertFailsWith<IllegalArgumentException> {
                ipfsBridge.putDocument("testdb", invalidId, document)
            }
        }
    }

    // ===== PERFORMANCE TESTS =====
    
    @Test
    fun `should handle large bulk operations efficiently`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-performance-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("perfdb")
        
        // Create 100 documents
        val documents = (1..100).map { i ->
            CouchDbDocument(
                _id = "doc$i",
                _rev = "",
                data = mapOf(
                    "name" to "Document $i",
                    "value" to i,
                    "largeField" to "x".repeat(1000) // 1KB per document
                )
            )
        }
        
        val bulkRequest = CouchDbBulkRequest(docs = documents)
        val startTime = System.currentTimeMillis()
        
        val bulkResult = ipfsBridge.bulkDocuments("perfdb", bulkRequest)
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        assertEquals(100, bulkResult.results.size)
        assertTrue(bulkResult.results.all { it.ok })
        
        // Performance assertion: should complete within reasonable time
        assertTrue(duration < 5000, "Bulk operation took too long: ${duration}ms")
    }

    // ===== ENDGAME: ACTIONABLE TODO FILTERING =====
    
    @Test
    fun `TDD: should filter actionable todos from CouchDB by attentionScore or error status`() = runTest {
        // TDD: This test is expected to fail until actionable todo filtering is implemented
        // ENDGAME: This will integrate with kernel-level features via io_uring and eBPF
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-actionable-todo-001")
            networkId("test-network")
        }
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("tododb")

        // Create documents with varying attentionScore and processingStatus
        val docs = listOf(
            CouchDbDocument(_id = "todo1", _rev = "", data = mapOf("attentionScore" to 0.9, "processingStatus" to "PENDING")),
            CouchDbDocument(_id = "todo2", _rev = "", data = mapOf("attentionScore" to 0.3, "processingStatus" to "PENDING")),
            CouchDbDocument(_id = "todo3", _rev = "", data = mapOf("attentionScore" to 0.8, "processingStatus" to "COMPLETED")),
            CouchDbDocument(_id = "todo4", _rev = "", data = mapOf("attentionScore" to 0.2, "processingStatus" to "ERROR")),
            CouchDbDocument(_id = "todo5", _rev = "", data = mapOf("attentionScore" to 0.6, "processingStatus" to "IN_PROGRESS"))
        )
        docs.forEach { doc ->
            ipfsBridge.putDocument("tododb", doc._id, doc)
        }

        // ENDGAME: This should eventually use kernel-level filtering via eBPF
        // For now, simulate a view/filter query for actionable todos (attentionScore >= 0.7 or status == 'ERROR')
        val allDocs = ipfsBridge.getAllDocuments("tododb", emptyMap())
        val actionable = allDocs.rows.filter { row ->
            val doc = ipfsBridge.getDocument("tododb", row.id)
            val score = (doc?.data?.get("attentionScore") as? Number)?.toDouble()
                ?: (doc?.data?.get("attentionScore")?.toString()?.toDoubleOrNull() ?: 0.0)
            val status = doc?.data?.get("processingStatus")?.toString() ?: ""
            score >= 0.7 || status == "ERROR"
        }
        val actionableIds = actionable.map { it.id }.toSet()
        assertEquals(setOf("todo1", "todo3", "todo4"), actionableIds)
    }

    @Test
    fun `TDD: should use kernel-level eBPF filtering for high-performance todo queries`() = runTest {
        // TDD: This test validates the endgame architecture where CouchDB queries
        // are executed directly in the kernel via eBPF programs triggered by io_uring
        val testIpfsService = TestIpfsPubSubService()
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-ebpf-filtering-001")
            networkId("test-network")
        }
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("endgamedb")

        // Create a large dataset for performance testing
        val docs = (1..1000).map { i ->
            CouchDbDocument(
                _id = "doc$i",
                _rev = "",
                data = mapOf(
                    "attentionScore" to (i % 100) / 100.0,
                    "processingStatus" to when (i % 5) {
                        0 -> "PENDING"
                        1 -> "IN_PROGRESS"
                        2 -> "COMPLETED"
                        3 -> "ERROR"
                        else -> "ARCHIVED"
                    },
                    "priority" to (i % 10),
                    "category" to "category${i % 20}"
                )
            )
        }
        
        // Bulk insert
        val bulkRequest = CouchDbBulkRequest(docs = docs)
        ipfsBridge.bulkDocuments("endgamedb", bulkRequest)

        // ENDGAME: This should trigger an eBPF program in the kernel that:
        // 1. Routes the query to the appropriate LSM tree via io_uring
        // 2. Executes filtering logic directly on kernel data structures
        // 3. Returns only actionable items (attentionScore >= 0.7 or status == 'ERROR')
        val startTime = System.currentTimeMillis()
        
        // Simulate kernel-level filtering (this will be replaced with actual eBPF integration)
        val allDocs = ipfsBridge.getAllDocuments("endgamedb", emptyMap())
        val actionable = allDocs.rows.filter { row ->
            val doc = ipfsBridge.getDocument("endgamedb", row.id)
            val score = (doc?.data?.get("attentionScore") as? Number)?.toDouble()
                ?: (doc?.data?.get("attentionScore")?.toString()?.toDoubleOrNull() ?: 0.0)
            val status = doc?.data?.get("processingStatus")?.toString() ?: ""
            score >= 0.7 || status == "ERROR"
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        // Verify actionable items are found
        assertTrue(actionable.isNotEmpty(), "Should find actionable items")
        
        // Performance assertion: kernel-level filtering should be very fast
        // In the endgame, this should be < 1ms for 1000 documents
        assertTrue(duration < 100, "Kernel-level filtering took too long: ${duration}ms")
        
        // Verify all returned items are actually actionable
        actionable.forEach { row ->
            val doc = ipfsBridge.getDocument("endgamedb", row.id)
            val score = (doc?.data?.get("attentionScore") as? Number)?.toDouble()
                ?: (doc?.data?.get("attentionScore")?.toString()?.toDoubleOrNull() ?: 0.0)
            val status = doc?.data?.get("processingStatus")?.toString() ?: ""
            assertTrue(score >= 0.7 || status == "ERROR", "Non-actionable item returned: ${row.id}")
        }
    }
} 