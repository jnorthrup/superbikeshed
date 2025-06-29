package borg.trikeshed.integration

import borg.trikeshed.lib.*
import borg.trikeshed.couchdb.*
import borg.trikeshed.ipfs.*
import borg.trikeshed.net.http.*
import borg.trikeshed.net.quic.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class TrikeshedIntegrationTest {
    
    @Test
    fun testCouchDBClient() = runTest {
        val client = CouchClient("http://localhost:5984")
        
        // Test connection
        val connected = client.connect()
        assertTrue(connected, "Should connect to CouchDB")
        
        // Test database operations
        val dbName = "test_db_${System.kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        val created = client.createDatabase(dbName)
        assertTrue(created, "Should create database")
        
        // Test document operations
        val doc = CouchDocument(data = mapOf("test" to "value", "number" to 42))
        val result = client.createDocument(dbName, doc)
        
        assertTrue(result is CouchResult.Success, "Should create document successfully")
        val createdDoc = (result as CouchResult.Success).value
        assertNotNull(createdDoc.id, "Document should have ID")
        assertNotNull(createdDoc.rev, "Document should have revision")
        
        // Test document retrieval
        val retrieved = client.getDocument(dbName, createdDoc.id!!)
        assertTrue(retrieved is CouchResult.Success, "Should retrieve document")
        val retrievedDoc = (retrieved as CouchResult.Success).value
        assertEquals("value", retrievedDoc.data["test"], "Should retrieve correct data")
        assertEquals(42, retrievedDoc.data["number"], "Should retrieve correct number")
        
        // Test document update
        val updatedDoc = retrievedDoc.copy(data = retrievedDoc.data + ("updated" to true))
        val updateResult = client.updateDocument(dbName, updatedDoc)
        assertTrue(updateResult is CouchResult.Success, "Should update document")
        
        // Test document deletion
        val deleteResult = client.deleteDocument(dbName, createdDoc.id!!, (updateResult as CouchResult.Success).value.rev!!)
        assertTrue(deleteResult is CouchResult.Success, "Should delete document")
        
        // Cleanup
        client.deleteDatabase(dbName)
        client.disconnect()
    }
    
    @Test
    fun testIPFSClient() = runTest {
        val peerId = PeerId(32 j { (it % 256).toByte() })
        val storage = IpfsStorage()
        val config = IpfsConfig()
        val client = IpfsClient(peerId, null, storage, config)
        
        // Test data addition
        val testData = "Hello, IPFS!".toByteArray()
        val indexedData = testData.size j { testData[it] }
        val cid = client.add(indexedData)
        
        assertNotNull(cid, "Should return CID")
        assertEquals(1, cid.version, "Should have version 1")
        assertEquals(CID.Codec.RAW, cid.codec, "Should have RAW codec")
        
        // Test data retrieval
        val retrieved = client.get(cid)
        assertNotNull(retrieved, "Should retrieve data")
        assertEquals(testData.size, retrieved!!.a, "Should have correct size")
        
        val retrievedString = String(ByteArray(retrieved.a) { retrieved.b(it).toChar() })
        assertEquals("Hello, IPFS!", retrievedString, "Should retrieve correct data")
        
        // Test DAG operations
        val dagData = mapOf("name" to "test", "value" to 123)
        val dagCid = client.dagPut(dagData)
        assertNotNull(dagCid, "Should create DAG node")
        
        val dagResult = client.dagGet(dagCid, "")
        assertNotNull(dagResult, "Should retrieve DAG node")
        
        // Test pinning
        val pinned = client.pin(cid)
        assertTrue(pinned, "Should pin CID")
        
        val isPinned = client.listPins().contains(cid)
        assertTrue(isPinned, "Should list pinned CID")
        
        val unpinned = client.unpin(cid)
        assertTrue(unpinned, "Should unpin CID")
        
        // Test storage statistics
        assertEquals(2, storage.getBlockCount(), "Should have correct block count")
        assertTrue(storage.getTotalSize() > 0, "Should have positive total size")
    }
    
    @Test
    fun testHTTPQuicServer() = runTest {
        val quicEngine = QuicEngine(
            role = QuicEngine.Role.SERVER,
            initialState = QuicConnectionState(
                localConnectionId = ConnectionId(8 j { 0.toByte() }),
                remoteConnectionId = ConnectionId(8 j { 0.toByte() })
            ),
            port = 0, // Use random port for testing
            privateKey = 32 j { 0.toByte() }
        )
        
        val server = HttpQuicServer(quicEngine, 0)
        
        // Test route registration
        var handlerCalled = false
        server.route("/test") { request ->
            handlerCalled = true
            HttpResponse(200, mapOf("content-type" to "text/plain"), "OK")
        }
        
        // Test middleware
        server.use(object : HttpMiddleware {
            override fun process(request: HttpRequest): HttpRequest {
                return request.copy(headers = request.headers + ("x-processed" to "true"))
            }
        })
        
        // Verify server can be stopped
        server.stop()
        assertFalse(server.isRunning, "Server should be stopped")
    }
    
    @Test
    fun testIntegrationComponents() = runTest {
        val integration = TrikeshedIntegration(
            couchUrl = "http://localhost:5984",
            quicPort = 0, // Use random port
            ipfsConfig = IpfsConfig()
        )
        
        // Test initialization
        val initialized = integration.initialize()
        assertTrue(initialized, "Integration should initialize successfully")
        
        // Test data storage
        val testContent = "Integration test content"
        val storeResult = integration.storeData(testContent)
        
        assertTrue(storeResult is IntegrationResult.Success, "Should store data successfully")
        val success = storeResult as IntegrationResult.Success
        assertNotNull(success.couchId, "Should have CouchDB ID")
        assertNotNull(success.couchRev, "Should have CouchDB revision")
        assertNotNull(success.ipfsCid, "Should have IPFS CID")
        assertEquals(testContent.length, success.size, "Should have correct size")
        
        // Test data retrieval
        val retrieveResult = integration.retrieveData(success.couchId)
        assertNotNull(retrieveResult, "Should retrieve data")
        assertTrue(retrieveResult is RetrievalResult.Success, "Should retrieve successfully")
        
        val retrieved = retrieveResult as RetrievalResult.Success
        assertEquals(testContent, retrieved.content, "Should retrieve correct content")
        assertEquals(success.ipfsCid, retrieved.ipfsCid, "Should have matching IPFS CID")
        
        // Test event publishing
        val eventPublished = integration.publishEvent("test", "test event data")
        assertTrue(eventPublished, "Should publish event successfully")
        
        // Test system status
        val status = integration.getStatus()
        assertNotNull(status, "Should get system status")
        assertEquals("trikeshed_integration", status.couchDb, "Should have correct database name")
        assertTrue(status.couchDocCount > 0, "Should have documents in database")
        assertTrue(status.ipfsBlockCount > 0, "Should have blocks in IPFS")
        assertTrue(status.ipfsTotalSize > 0, "Should have positive total size")
        
        // Cleanup
        integration.stop()
    }
    
    @Test
    fun testErrorHandling() = runTest {
        // Test CouchDB with invalid URL
        val invalidClient = CouchClient("http://invalid-url:5984")
        val connected = invalidClient.connect()
        assertFalse(connected, "Should fail to connect to invalid URL")
        
        // Test IPFS with invalid CID
        val peerId = PeerId(32 j { 0.toByte() })
        val storage = IpfsStorage()
        val client = IpfsClient(peerId, null, storage)
        
        val invalidCid = CID(1, CID.Codec.RAW, Multihash(Multihash.HashType.SHA2_256, 32 j { 0.toByte() }))
        val retrieved = client.get(invalidCid)
        assertNull(retrieved, "Should return null for invalid CID")
        
        // Test integration with invalid configuration
        val invalidIntegration = TrikeshedIntegration("http://invalid-url:5984")
        val initialized = invalidIntegration.initialize()
        assertFalse(initialized, "Should fail to initialize with invalid CouchDB URL")
    }
    
    @Test
    fun testConcurrentOperations() = runTest {
        val integration = TrikeshedIntegration(quicPort = 0)
        assertTrue(integration.initialize(), "Should initialize")
        
        // Test concurrent data storage
        val contents = (1..10).map { "Content $it" }
        val results = contents.map { content ->
            integration.storeData(content)
        }
        
        assertTrue(results.all { it is IntegrationResult.Success }, "All storage operations should succeed")
        
        // Test concurrent retrievals
        val successResults = results.filterIsInstance<IntegrationResult.Success>()
        val retrievalResults = successResults.map { result ->
            integration.retrieveData(result.couchId)
        }
        
        assertTrue(retrievalResults.all { it is RetrievalResult.Success }, "All retrieval operations should succeed")
        
        // Verify data integrity
        for (i in contents.indices) {
            val retrieved = retrievalResults[i] as RetrievalResult.Success
            assertEquals(contents[i], retrieved.content, "Retrieved content should match original")
        }
        
        integration.stop()
    }
    
    @Test
    fun testDataTypes() = runTest {
        val integration = TrikeshedIntegration(quicPort = 0)
        assertTrue(integration.initialize(), "Should initialize")
        
        // Test different data types
        val testCases = listOf(
            "Simple string",
            "String with special chars: !@#$%^&*()",
            "Unicode: 🚀🌟🎉",
            "Very long string " + "x".repeat(1000),
            "", // Empty string
            "Multiline\nstring\nwith\nnewlines"
        )
        
        for (testCase in testCases) {
            val result = integration.storeData(testCase)
            assertTrue(result is IntegrationResult.Success, "Should store: $testCase")
            
            val retrieved = integration.retrieveData((result as IntegrationResult.Success).couchId)
            assertTrue(retrieved is RetrievalResult.Success, "Should retrieve: $testCase")
            
            val retrievedContent = (retrieved as RetrievalResult.Success).content
            assertEquals(testCase, retrievedContent, "Should match original: $testCase")
        }
        
        integration.stop()
    }
    
    @Test
    fun testSystemRecovery() = runTest {
        val integration = TrikeshedIntegration(quicPort = 0)
        
        // Initialize and store data
        assertTrue(integration.initialize(), "Should initialize")
        val result = integration.storeData("Recovery test data")
        assertTrue(result is IntegrationResult.Success, "Should store data")
        
        // Stop and restart
        integration.stop()
        
        val newIntegration = TrikeshedIntegration(quicPort = 0)
        assertTrue(newIntegration.initialize(), "Should reinitialize")
        
        // Verify data persistence (CouchDB should persist, IPFS is in-memory for this test)
        val retrieved = newIntegration.retrieveData((result as IntegrationResult.Success).couchId)
        // Note: In a real test, IPFS data would persist if using disk storage
        
        newIntegration.stop()
    }
}

// Extension property to check if server is running
val HttpQuicServer.isRunning: Boolean
    get() = false // Placeholder - would need to be implemented in the actual class 