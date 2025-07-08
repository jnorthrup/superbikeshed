package fiduciary.protocol

import kotlin.test.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * TDD Test Suite for CouchDB API and RelaxFactory API
 * 
 * Based on Fiduciary Omnibus Architecture:
 * - CouchDB API inherits basic database functionality
 * - RelaxFactory API inherits from CouchDB API
 * - UserID key relationship management
 * - Document storage and retrieval
 * - Change feed support
 */
class CouchDBAPITest {
    
    @Test
    fun `CouchDB API should create and retrieve documents`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("test-db")
        val document = CouchDocument(
            id = "doc-123",
            revision = "1-abc",
            data = JsonObject(mapOf(
                "name" to JsonPrimitive("Test Document"),
                "value" to JsonPrimitive(42)
            ))
        )
        
        // When
        val created = couchAPI.createDocument(document)
        val retrieved = couchAPI.getDocument(document.id)
        
        // Then
        assertNotNull(created)
        assertNotNull(retrieved)
        assertEquals(document.id, retrieved.id)
        assertEquals("Test Document", retrieved.data["name"]?.toString()?.trim('"'))
    }
    
    @Test
    fun `CouchDB API should update documents with revision handling`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("test-db")
        val originalDoc = CouchDocument(
            id = "update-doc",
            revision = "1-abc",
            data = JsonObject(mapOf("value" to JsonPrimitive(10)))
        )
        
        // When
        val created = couchAPI.createDocument(originalDoc)
        val updated = couchAPI.updateDocument(originalDoc.copy(
            revision = created.revision,
            data = JsonObject(mapOf("value" to JsonPrimitive(20)))
        ))
        
        // Then
        assertNotNull(updated)
        assertNotEquals(created.revision, updated.revision)
        assertEquals(20, updated.data["value"]?.toString()?.toInt())
    }
    
    @Test
    fun `CouchDB API should handle document conflicts`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("conflict-db")
        val document = CouchDocument(
            id = "conflict-doc",
            revision = "1-abc",
            data = JsonObject(mapOf("value" to JsonPrimitive(1)))
        )
        
        // When
        val created = couchAPI.createDocument(document)
        
        // Try to update with stale revision
        val conflictUpdate = document.copy(
            revision = "1-abc", // Stale revision
            data = JsonObject(mapOf("value" to JsonPrimitive(2)))
        )
        
        // Then
        assertFailsWith<CouchDBConflictException> {
            couchAPI.updateDocument(conflictUpdate)
        }
    }
    
    @Test
    fun `CouchDB API should support bulk operations`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("bulk-db")
        val documents = listOf(
            CouchDocument("bulk-1", "1-a", JsonObject(mapOf("value" to JsonPrimitive(1)))),
            CouchDocument("bulk-2", "1-b", JsonObject(mapOf("value" to JsonPrimitive(2)))),
            CouchDocument("bulk-3", "1-c", JsonObject(mapOf("value" to JsonPrimitive(3))))
        )
        
        // When
        val results = couchAPI.bulkInsert(documents)
        
        // Then
        assertEquals(3, results.size)
        assertTrue(results.all { it.success })
        
        // Verify all documents exist
        documents.forEach { doc ->
            val retrieved = couchAPI.getDocument(doc.id)
            assertNotNull(retrieved)
            assertEquals(doc.id, retrieved.id)
        }
    }
    
    @Test
    fun `CouchDB API should support change feeds`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("changes-db")
        val changes = mutableListOf<CouchChange>()
        
        // When
        couchAPI.subscribeToChanges { change ->
            changes.add(change)
        }
        
        // Create some documents
        val doc1 = CouchDocument("change-1", "1-a", JsonObject(mapOf("value" to JsonPrimitive(1))))
        val doc2 = CouchDocument("change-2", "1-b", JsonObject(mapOf("value" to JsonPrimitive(2))))
        
        couchAPI.createDocument(doc1)
        couchAPI.createDocument(doc2)
        
        // Then
        assertTrue(changes.size >= 2)
        assertTrue(changes.any { it.documentId == "change-1" })
        assertTrue(changes.any { it.documentId == "change-2" })
    }
    
    @Test
    fun `CouchDB API should support queries`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("query-db")
        val documents = listOf(
            CouchDocument("query-1", "1-a", JsonObject(mapOf(
                "type" to JsonPrimitive("user"),
                "age" to JsonPrimitive(25)
            ))),
            CouchDocument("query-2", "1-b", JsonObject(mapOf(
                "type" to JsonPrimitive("user"),
                "age" to JsonPrimitive(30)
            ))),
            CouchDocument("query-3", "1-c", JsonObject(mapOf(
                "type" to JsonPrimitive("admin"),
                "age" to JsonPrimitive(35)
            )))
        )
        
        documents.forEach { couchAPI.createDocument(it) }
        
        // When
        val userQuery = CouchQuery(
            selector = mapOf("type" to "user"),
            fields = listOf("age")
        )
        val results = couchAPI.query(userQuery)
        
        // Then
        assertEquals(2, results.size)
        assertTrue(results.all { it.data["type"]?.toString()?.contains("user") == true })
    }
    
    @Test
    fun `RelaxFactory API should inherit from CouchDB API`() = runTest {
        // Given
        val relaxFactory = RelaxFactoryAPI("relax-db")
        
        // When
        val isCouchAPI = relaxFactory is CouchDBAPI
        
        // Then
        assertTrue(isCouchAPI, "RelaxFactory API should inherit from CouchDB API")
    }
    
    @Test
    fun `RelaxFactory API should support factory patterns`() = runTest {
        // Given
        val relaxFactory = RelaxFactoryAPI("factory-db")
        
        // When
        val userFactory = relaxFactory.createDocumentFactory("user")
        val user = userFactory.create(mapOf(
            "name" to JsonPrimitive("John Doe"),
            "email" to JsonPrimitive("john@example.com")
        ))
        
        // Then
        assertNotNull(user)
        assertTrue(user.id.startsWith("user-"))
        assertEquals("John Doe", user.data["name"]?.toString()?.trim('"'))
    }
    
    @Test
    fun `RelaxFactory API should support relaxed validation`() = runTest {
        // Given
        val relaxFactory = RelaxFactoryAPI("relaxed-db")
        
        // When
        val relaxedDoc = relaxFactory.createRelaxedDocument(
            id = "relaxed-1",
            data = JsonObject(mapOf(
                "flexible_field" to JsonPrimitive("any value"),
                "dynamic_property" to JsonPrimitive(123)
            ))
        )
        
        // Then
        assertNotNull(relaxedDoc)
        assertEquals("relaxed-1", relaxedDoc.id)
        assertTrue(relaxedDoc.data.containsKey("flexible_field"))
    }
    
    @Test
    fun `RelaxFactory API should support schema evolution`() = runTest {
        // Given
        val relaxFactory = RelaxFactoryAPI("evolution-db")
        
        // Original schema
        val v1Document = CouchDocument(
            id = "evolving-doc",
            revision = "1-abc",
            data = JsonObject(mapOf(
                "name" to JsonPrimitive("Test"),
                "schema_version" to JsonPrimitive(1)
            ))
        )
        
        // When
        val created = relaxFactory.createDocument(v1Document)
        val evolved = relaxFactory.evolveSchema(created, targetVersion = 2)
        
        // Then
        assertNotNull(evolved)
        assertEquals(2, evolved.data["schema_version"]?.toString()?.toInt())
        assertTrue(evolved.data.containsKey("name")) // Old field preserved
    }
    
    @Test
    fun `RelaxFactory API should support template documents`() = runTest {
        // Given
        val relaxFactory = RelaxFactoryAPI("template-db")
        val template = DocumentTemplate(
            type = "article",
            requiredFields = listOf("title", "content"),
            optionalFields = listOf("author", "tags"),
            defaultValues = mapOf(
                "status" to JsonPrimitive("draft"),
                "created_at" to JsonPrimitive(System.currentTimeMillis())
            )
        )
        
        // When
        val article = relaxFactory.createFromTemplate(template, mapOf(
            "title" to JsonPrimitive("Test Article"),
            "content" to JsonPrimitive("This is test content")
        ))
        
        // Then
        assertNotNull(article)
        assertEquals("Test Article", article.data["title"]?.toString()?.trim('"'))
        assertEquals("draft", article.data["status"]?.toString()?.trim('"'))
        assertTrue(article.data.containsKey("created_at"))
    }
    
    @Test
    fun `CouchDB API should support user key management`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("user-keys-db")
        val userKey = UserKey("user-456", "ADMIN_USER")
        
        // When
        couchAPI.registerUserKey(userKey)
        val retrievedKey = couchAPI.getUserKey(userKey.userId)
        
        // Then
        assertNotNull(retrievedKey)
        assertEquals(userKey.userId, retrievedKey.userId)
        assertEquals(userKey.keyType, retrievedKey.keyType)
    }
    
    @Test
    fun `RelaxFactory API should support document relationships`() = runTest {
        // Given
        val relaxFactory = RelaxFactoryAPI("relations-db")
        
        // Create parent document
        val parent = CouchDocument(
            id = "parent-doc",
            revision = "1-abc",
            data = JsonObject(mapOf("type" to JsonPrimitive("parent")))
        )
        
        // When
        val createdParent = relaxFactory.createDocument(parent)
        val child = relaxFactory.createRelatedDocument(
            parentId = createdParent.id,
            relationshipType = "child-of",
            data = JsonObject(mapOf("type" to JsonPrimitive("child")))
        )
        
        val relationships = relaxFactory.getRelatedDocuments(createdParent.id)
        
        // Then
        assertNotNull(child)
        assertEquals(1, relationships.size)
        assertEquals(child.id, relationships[0].id)
    }
    
    @Test
    fun `CouchDB API should handle database operations`() = runTest {
        // Given
        val couchAPI = CouchDBAPI("db-ops-test")
        
        // When
        val dbInfo = couchAPI.getDatabaseInfo()
        val compacted = couchAPI.compactDatabase()
        
        // Then
        assertNotNull(dbInfo)
        assertEquals("db-ops-test", dbInfo.name)
        assertTrue(dbInfo.documentCount >= 0)
        assertTrue(compacted)
    }
    
    @Test
    fun `RelaxFactory API should support batch processing`() = runTest {
        // Given
        val relaxFactory = RelaxFactoryAPI("batch-db")
        val batchSize = 100
        
        // When
        val batchProcessor = relaxFactory.createBatchProcessor(batchSize)
        repeat(250) { i ->
            batchProcessor.add(CouchDocument(
                id = "batch-$i",
                revision = "1-a",
                data = JsonObject(mapOf("index" to JsonPrimitive(i)))
            ))
        }
        
        val results = batchProcessor.process()
        
        // Then
        assertEquals(3, results.size) // 3 batches: 100, 100, 50
        assertTrue(results.all { it.success })
        assertEquals(250, results.sumOf { it.processedCount })
    }
}