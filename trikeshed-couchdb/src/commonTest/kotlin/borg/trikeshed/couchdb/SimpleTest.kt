package borg.trikeshed.couchdb

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Simple standalone tests for CouchDB components.
 */
class SimpleTest {
    
    @Test
    fun testLSMRStorage() {
        val storage = LSMRCouchDBStorage()
        
        val testData = """{"_id":"test1","message":"Hello!"}""".encodeToByteArray()
        val revision = storage.putDocument("testdb", "test1", testData)
        
        assertNotNull(revision)
        assertTrue(revision.startsWith("1-"))
        
        val retrieved = storage.getDocument("testdb", "test1") 
        assertNotNull(retrieved)
        assertTrue(retrieved.decodeToString().contains("test1"))
    }
    
    @Test
    fun testQuicAdapter() {
        val config = QuicCouchDBConfig()
        val adapter = QuicCouchDBAdapter(config)
        
        assertEquals("QUIC-CouchDB", adapter.protocolName)
        
        val message = QuicCouchDBMessage.DocumentGet(
            database = "test",
            documentId = "doc1"
        )
        
        val serialized = adapter.serializeMessage(message)
        assertTrue(serialized.a > 0)
    }
    
    @Test
    fun testBasicSerialization() {
        val response = QuicCouchDBMessage.Response(
            statusCode = 200,
            body = """{"ok": true}""",
            success = true
        )
        
        assertEquals(200, response.statusCode)
        assertTrue(response.success)
    }
}