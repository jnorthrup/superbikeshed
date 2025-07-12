package borg.trikeshed.couchdb

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Comprehensive CouchDB Server Test Suite
 * 
 * TDD Test Suite for CouchDB Server implementation covering:
 * - Database operations (create, delete, list)
 * - Document CRUD operations
 * - Bulk operations
 * - Changes feed
 * - Error handling
 * - Performance tests
 * - Integration with blob service
 */
class CouchDBServerTest {

    private lateinit var serverContext: kotlin.coroutines.CoroutineContext
    private lateinit var blobService: ChannelizedBlobService
    private lateinit var server: CouchDBServer

    @BeforeTest
    fun setup() {
        serverContext = newSingleThreadContext("TestServerContext")
        blobService = ChannelizedBlobService()
        server = CouchDBServer(blobService, serverContext)
    }

    @AfterTest
    fun cleanup() {
        server.stop()
        serverContext.close()
    }

    // ===== SERVER STARTUP TESTS =====

    @Test
    fun `should start server successfully`() = runTest {
        // When
        val serverJob = launch { server.start() }
        delay(100) // Allow server to start

        // Then
        assertTrue(serverJob.isActive, "Server should be running")
        serverJob.cancel()
    }

    @Test
    fun `should handle server greeting request`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // When
        val request = MockHttpRequest(method = "GET", path = "/")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(200, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertEquals("Welcome", jsonResponse["couchdb"]?.jsonPrimitive?.content)
        assertEquals("1.7.2", jsonResponse["version"]?.jsonPrimitive?.content)
    }

    // ===== DATABASE OPERATIONS TESTS =====

    @Test
    fun `should list databases when empty`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // When
        val request = MockHttpRequest(method = "GET", path = "/_all_dbs")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(200, response.status)
        assertNotNull(response.body)
        val dbList = Json.parseToJsonElement(response.body!!).jsonArray
        assertEquals(0, dbList.size, "Should return empty database list")
    }

    @Test
    fun `should create database successfully`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // When
        val request = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(201, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertTrue(jsonResponse["ok"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `should fail to create database with invalid name`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // When
        val request = MockHttpRequest(method = "PUT", path = "/invalid/name")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(412, response.status) // Precondition Failed
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertEquals("file_exists", jsonResponse["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should delete database successfully`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database first
        val createRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createRequest)
        server.httpResponseChannel.receive()

        // When
        val deleteRequest = MockHttpRequest(method = "DELETE", path = "/testdb")
        server.httpRequestChannel.send(deleteRequest)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(200, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertTrue(jsonResponse["ok"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `should fail to delete non-existent database`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // When
        val request = MockHttpRequest(method = "DELETE", path = "/nonexistent")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(404, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertEquals("not_found", jsonResponse["error"]?.jsonPrimitive?.content)
    }

    // ===== DOCUMENT CRUD TESTS =====

    @Test
    fun `should create document successfully`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database first
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        val documentBody = """{"_id":"doc1","name":"Test Document","value":42}"""

        // When
        val request = MockHttpRequest(
            method = "PUT",
            path = "/testdb/doc1",
            body = documentBody
        )
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(201, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertTrue(jsonResponse["ok"]?.jsonPrimitive?.boolean == true)
        assertEquals("doc1", jsonResponse["id"]?.jsonPrimitive?.content)
        assertNotNull(jsonResponse["rev"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should retrieve document successfully`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database and document
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        val documentBody = """{"_id":"doc1","name":"Test Document","value":42}"""
        val createDocRequest = MockHttpRequest(
            method = "PUT",
            path = "/testdb/doc1",
            body = documentBody
        )
        server.httpRequestChannel.send(createDocRequest)
        server.httpResponseChannel.receive()

        // When
        val getRequest = MockHttpRequest(method = "GET", path = "/testdb/doc1")
        server.httpRequestChannel.send(getRequest)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(200, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertEquals("doc1", jsonResponse["_id"]?.jsonPrimitive?.content)
        assertEquals("Test Document", jsonResponse["name"]?.jsonPrimitive?.content)
        assertEquals(42, jsonResponse["value"]?.jsonPrimitive?.int)
    }

    @Test
    fun `should fail to retrieve non-existent document`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        // When
        val request = MockHttpRequest(method = "GET", path = "/testdb/nonexistent")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(404, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertEquals("not_found", jsonResponse["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `should update document with revision`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database and document
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        val originalBody = """{"_id":"doc1","name":"Original","value":1}"""
        val createDocRequest = MockHttpRequest(
            method = "PUT",
            path = "/testdb/doc1",
            body = originalBody
        )
        server.httpRequestChannel.send(createDocRequest)
        val createResponse = server.httpResponseChannel.receive()
        val originalRev = Json.parseToJsonElement(createResponse.body!!).jsonObject["rev"]?.jsonPrimitive?.content

        // When
        val updatedBody = """{"_id":"doc1","name":"Updated","value":2}"""
        val updateRequest = MockHttpRequest(
            method = "PUT",
            path = "/testdb/doc1",
            body = updatedBody
        )
        server.httpRequestChannel.send(updateRequest)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(201, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertTrue(jsonResponse["ok"]?.jsonPrimitive?.boolean == true)
        val newRev = jsonResponse["rev"]?.jsonPrimitive?.content
        assertNotNull(newRev)
        assertNotEquals(originalRev, newRev, "Revision should change after update")
    }

    @Test
    fun `should delete document with revision`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database and document
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        val documentBody = """{"_id":"doc1","name":"Test Document","value":42}"""
        val createDocRequest = MockHttpRequest(
            method = "PUT",
            path = "/testdb/doc1",
            body = documentBody
        )
        server.httpRequestChannel.send(createDocRequest)
        val createResponse = server.httpResponseChannel.receive()
        val rev = Json.parseToJsonElement(createResponse.body!!).jsonObject["rev"]?.jsonPrimitive?.content

        // When
        val deleteRequest = MockHttpRequest(
            method = "DELETE",
            path = "/testdb/doc1",
            headers = mapOf("rev" to rev!!)
        )
        server.httpRequestChannel.send(deleteRequest)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(200, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        assertTrue(jsonResponse["ok"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `should fail to delete document without revision`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database and document
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        val documentBody = """{"_id":"doc1","name":"Test Document","value":42}"""
        val createDocRequest = MockHttpRequest(
            method = "PUT",
            path = "/testdb/doc1",
            body = documentBody
        )
        server.httpRequestChannel.send(createDocRequest)
        server.httpResponseChannel.receive()

        // When
        val deleteRequest = MockHttpRequest(method = "DELETE", path = "/testdb/doc1")
        server.httpRequestChannel.send(deleteRequest)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(400, response.status)
        assertTrue(response.body?.contains("Missing revision") == true)
    }

    // ===== BULK OPERATIONS TESTS =====

    @Test
    fun `should handle bulk document operations`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        val bulkBody = """{
            "docs": [
                {"_id":"bulk1","name":"Doc 1","value":1},
                {"_id":"bulk2","name":"Doc 2","value":2},
                {"_id":"bulk3","name":"Doc 3","value":3}
            ]
        }"""

        // When
        val request = MockHttpRequest(
            method = "POST",
            path = "/testdb/_bulk_docs",
            body = bulkBody
        )
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(201, response.status)
        assertNotNull(response.body)
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonArray
        assertEquals(3, jsonResponse.size)
        
        // Verify all documents were created successfully
        jsonResponse.forEach { result ->
            val resultObj = result.jsonObject
            assertTrue(resultObj["ok"]?.jsonPrimitive?.boolean == true)
            assertNotNull(resultObj["id"]?.jsonPrimitive?.content)
            assertNotNull(resultObj["rev"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `should fail bulk operations with invalid body`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        // When
        val request = MockHttpRequest(
            method = "POST",
            path = "/testdb/_bulk_docs"
            // Missing body
        )
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(400, response.status)
        assertTrue(response.body?.contains("Missing body") == true)
    }

    @Test
    fun `should fail bulk operations with missing docs array`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        val invalidBody = """{"invalid":"body"}"""

        // When
        val request = MockHttpRequest(
            method = "POST",
            path = "/testdb/_bulk_docs",
            body = invalidBody
        )
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(400, response.status)
        assertTrue(response.body?.contains("Missing 'docs' array") == true)
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    fun `should handle unsupported HTTP methods`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // When
        val request = MockHttpRequest(method = "PATCH", path = "/testdb")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(405, response.status)
        assertEquals("Method Not Allowed", response.body)
    }

    @Test
    fun `should handle unknown paths`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // When
        val request = MockHttpRequest(method = "GET", path = "/unknown/path")
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(404, response.status)
        assertEquals("Not Found", response.body)
    }

    @Test
    fun `should handle malformed JSON in document creation`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        // When
        val request = MockHttpRequest(
            method = "PUT",
            path = "/testdb/doc1",
            body = "{invalid json}"
        )
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()

        // Then
        assertEquals(500, response.status) // Should handle JSON parsing error
    }

    // ===== INTEGRATION TESTS =====

    @Test
    fun `should handle complete document lifecycle`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // 1. Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        val createDbResponse = server.httpResponseChannel.receive()
        assertEquals(201, createDbResponse.status)

        // 2. Create document
        val documentBody = """{"_id":"lifecycle","name":"Lifecycle Test","value":100}"""
        val createDocRequest = MockHttpRequest(
            method = "PUT",
            path = "/testdb/lifecycle",
            body = documentBody
        )
        server.httpRequestChannel.send(createDocRequest)
        val createDocResponse = server.httpResponseChannel.receive()
        assertEquals(201, createDocResponse.status)
        val rev = Json.parseToJsonElement(createDocResponse.body!!).jsonObject["rev"]?.jsonPrimitive?.content

        // 3. Retrieve document
        val getRequest = MockHttpRequest(method = "GET", path = "/testdb/lifecycle")
        server.httpRequestChannel.send(getRequest)
        val getResponse = server.httpResponseChannel.receive()
        assertEquals(200, getResponse.status)

        // 4. Update document
        val updateBody = """{"_id":"lifecycle","name":"Updated Lifecycle","value":200}"""
        val updateRequest = MockHttpRequest(
            method = "PUT",
            path = "/testdb/lifecycle",
            body = updateBody
        )
        server.httpRequestChannel.send(updateRequest)
        val updateResponse = server.httpResponseChannel.receive()
        assertEquals(201, updateResponse.status)

        // 5. Delete document
        val deleteRequest = MockHttpRequest(
            method = "DELETE",
            path = "/testdb/lifecycle",
            headers = mapOf("rev" to rev!!)
        )
        server.httpRequestChannel.send(deleteRequest)
        val deleteResponse = server.httpResponseChannel.receive()
        assertEquals(200, deleteResponse.status)

        // 6. Verify document is deleted
        val getDeletedRequest = MockHttpRequest(method = "GET", path = "/testdb/lifecycle")
        server.httpRequestChannel.send(getDeletedRequest)
        val getDeletedResponse = server.httpResponseChannel.receive()
        assertEquals(404, getDeletedResponse.status)
    }

    @Test
    fun `should handle concurrent requests`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/testdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        // When - Send multiple concurrent requests
        val requests = (1..5).map { i ->
            MockHttpRequest(
                method = "PUT",
                path = "/testdb/concurrent$i",
                body = """{"_id":"concurrent$i","value":$i}"""
            )
        }

        requests.forEach { request ->
            server.httpRequestChannel.send(request)
        }

        // Then - All requests should be processed
        repeat(5) {
            val response = server.httpResponseChannel.receive()
            assertEquals(201, response.status)
        }
    }

    // ===== PERFORMANCE TESTS =====

    @Test
    fun `should handle large bulk operations efficiently`() = runTest {
        // Given
        launch { server.start() }
        delay(100)

        // Create database
        val createDbRequest = MockHttpRequest(method = "PUT", path = "/perfdb")
        server.httpRequestChannel.send(createDbRequest)
        server.httpResponseChannel.receive()

        // Create large bulk request
        val docs = (1..100).map { i ->
            """{"_id":"perf$i","name":"Performance Doc $i","value":$i,"largeField":"${"x".repeat(100)}"}"""
        }
        val bulkBody = """{"docs":[${docs.joinToString(",")}]}"""

        // When
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val request = MockHttpRequest(
            method = "POST",
            path = "/perfdb/_bulk_docs",
            body = bulkBody
        )
        server.httpRequestChannel.send(request)
        val response = server.httpResponseChannel.receive()
        val endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        // Then
        assertEquals(201, response.status)
        val duration = endTime - startTime
        assertTrue(duration < 5000, "Bulk operation took too long: ${duration}ms")
        
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonArray
        assertEquals(100, jsonResponse.size)
    }
} 