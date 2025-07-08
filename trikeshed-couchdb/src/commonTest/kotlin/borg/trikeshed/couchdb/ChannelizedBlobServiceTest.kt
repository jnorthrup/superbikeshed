package borg.trikeshed.couchdb

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.delay

class ChannelizedBlobServiceTest {

    @Test
    fun `should perform full CRUD operations with channelized mock and visualize table state`() = runBlocking {
        val service = ChannelizedBlobService()

        // Define contexts for different channel points
        val contextA = CouchDBContext("db_alpha", "http://server_a:5984")
        val contextB = CouchDBContext("db_beta", "http://server_b:5984")

        // Launch server-side processing coroutines for both contexts
        launch { service.processPutRequests(contextA) }
        launch { service.processGetRequests(contextA) }
        launch { service.processUpdateRequests(contextA) }
        launch { service.processDeleteRequests(contextA) }

        launch { service.processPutRequests(contextB) }
        launch { service.processGetRequests(contextB) }
        launch { service.processUpdateRequests(contextB) }
        launch { service.processDeleteRequests(contextB) }

        println("\n--- Starting CRUD Operations for Context A ---")
        // 1. Create (Put) a blob in Context A
        val blobIdA = "entity_A_001"
        val blobDataA_v1 = "{\"name\":\"Entity A\", \"version\":1, \"color\":\"red\"}".encodeToByteArray()
        println("Client (Put A): Sending blob '$blobIdA' with data: '${blobDataA_v1.decodeToString()}'")
        val putResponseA = service.putBlob(blobIdA, blobDataA_v1, contextA)
        assertEquals(blobIdA, putResponseA.id)
        assertEquals(true, putResponseA.success)
        delay(100) // Allow logs to settle

        // 2. Read (Get) the blob from Context A
        println("Client (Get A): Requesting blob '$blobIdA'")
        val getResponseA_v1 = service.getBlob(blobIdA, contextA)
        assertEquals(blobIdA, getResponseA_v1.id)
        assertEquals(true, getResponseA_v1.found)
        assertNotNull(getResponseA_v1.data)
        assertEquals(blobDataA_v1.decodeToString(), getResponseA_v1.data?.decodeToString())
        delay(100)

        // 3. Update the blob in Context A
        val blobDataA_v2 = "{\"name\":\"Entity A\", \"version\":2, \"color\":\"blue\"}".encodeToByteArray()
        println("Client (Update A): Sending update for blob '$blobIdA' with data: '${blobDataA_v2.decodeToString()}'")
        val updateResponseA = service.updateBlob(blobIdA, blobDataA_v2, contextA)
        assertEquals(blobIdA, updateResponseA.id)
        assertEquals(true, updateResponseA.success)
        delay(100)

        // 4. Read (Get) the updated blob from Context A
        println("Client (Get A): Requesting updated blob '$blobIdA'")
        val getResponseA_v2 = service.getBlob(blobIdA, contextA)
        assertEquals(blobIdA, getResponseA_v2.id)
        assertEquals(true, getResponseA_v2.found)
        assertNotNull(getResponseA_v2.data)
        assertEquals(blobDataA_v2.decodeToString(), getResponseA_v2.data?.decodeToString())
        delay(100)

        // 5. Delete the blob from Context A
        println("Client (Delete A): Requesting deletion of blob '$blobIdA'")
        val deleteResponseA = service.deleteBlob(blobIdA, contextA)
        assertEquals(blobIdA, deleteResponseA.id)
        assertEquals(true, deleteResponseA.success)
        delay(100)

        // 6. Attempt to Read (Get) the deleted blob from Context A
        println("Client (Get A): Requesting deleted blob '$blobIdA'")
        val getResponseA_deleted = service.getBlob(blobIdA, contextA)
        assertEquals(blobIdA, getResponseA_deleted.id)
        assertEquals(false, getResponseA_deleted.found)
        delay(100)

        println("\n--- Starting CRUD Operations for Context B ---")
        // 1. Create (Put) a blob in Context B
        val blobIdB = "entity_B_001"
        val blobDataB_v1 = "{\"name\":\"Entity B\", \"status\":\"active\"}".encodeToByteArray()
        println("Client (Put B): Sending blob '$blobIdB' with data: '${blobDataB_v1.decodeToString()}'")
        val putResponseB = service.putBlob(blobIdB, blobDataB_v1, contextB)
        assertEquals(blobIdB, putResponseB.id)
        assertEquals(true, putResponseB.success)
        delay(100)

        // 2. Read (Get) the blob from Context B
        println("Client (Get B): Requesting blob '$blobIdB'")
        val getResponseB_v1 = service.getBlob(blobIdB, contextB)
        assertEquals(blobIdB, getResponseB_v1.id)
        assertEquals(true, getResponseB_v1.found)
        assertNotNull(getResponseB_v1.data)
        assertEquals(blobDataB_v1.decodeToString(), getResponseB_v1.data?.decodeToString())
        delay(100)

        // Test getting blob from wrong context (should not be found in this mock setup)
        println("Client (Get A from B): Requesting blob '$blobIdA' from Context B")
        val getResponseAFromB = service.getBlob(blobIdA, contextB)
        assertEquals(blobIdA, getResponseAFromB.id)
        assertEquals(false, getResponseAFromB.found)
        delay(100)

        // Close channels to terminate processing coroutines gracefully
        service.putRequestChannel.close()
        service.putResponseChannel.close()
        service.getRequestChannel.close()
        service.getResponseChannel.close()
        service.updateRequestChannel.close()
        service.updateResponseChannel.close()
        service.deleteRequestChannel.close()
        service.deleteResponseChannel.close()
    }
}