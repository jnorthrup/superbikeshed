package borg.trikeshed.couchdb

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

fun main() = runBlocking {
    val blobService = ChannelizedBlobService()
    val serverContext = newSingleThreadContext("CouchDBServerThread")
    val couchdbServer = CouchDBServer(blobService, serverContext)

    // Start the CouchDB server in a separate coroutine
    val serverJob = launch { couchdbServer.start() }

    // Give the server a moment to start up
    delay(1000)

    println("\n--- Sending Mock HTTP Requests ---")

    // Test GET / (server greeting)
    println("\n--- Test: GET / (Server Greeting) ---")
    val greetingRequest = MockHttpRequest(method = "GET", path = "/")
    launch {
        couchdbServer.httpRequestChannel.send(greetingRequest)
        val response = couchdbServer.httpResponseChannel.receive()
        println("GET / Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test GET /_all_dbs (empty initially)
    println("\n--- Test: GET /_all_dbs (Initially Empty) ---")
    val allDbsRequest1 = MockHttpRequest(method = "GET", path = "/_all_dbs")
    launch {
        couchdbServer.httpRequestChannel.send(allDbsRequest1)
        val response = couchdbServer.httpResponseChannel.receive()
        println("GET /_all_dbs Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test PUT /mydb (create database)
    println("\n--- Test: PUT /mydb (Create Database) ---")
    val createDbRequest = MockHttpRequest(method = "PUT", path = "/mydb")
    launch {
        couchdbServer.httpRequestChannel.send(createDbRequest)
        val response = couchdbServer.httpResponseChannel.receive()
        println("PUT /mydb Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test GET /_all_dbs (after creating mydb)
    println("\n--- Test: GET /_all_dbs (After Creating mydb) ---")
    val allDbsRequest2 = MockHttpRequest(method = "GET", path = "/_all_dbs")
    launch {
        couchdbServer.httpRequestChannel.send(allDbsRequest2)
        val response = couchdbServer.httpResponseChannel.receive()
        println("GET /_all_dbs Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test PUT /mydb/doc1 (create document)
    println("\n--- Test: PUT /mydb/doc1 (Create Document) ---")
    val putDoc1Request = MockHttpRequest(
        method = "PUT",
        path = "/mydb/doc1",
        body = "{\"_id\":\"doc1\",\"name\":\"Test Document 1\",\"value\":123}"
    )
    launch {
        couchdbServer.httpRequestChannel.send(putDoc1Request)
        val response = couchdbServer.httpResponseChannel.receive()
        println("PUT /mydb/doc1 Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test GET /mydb/doc1 (get document)
    println("\n--- Test: GET /mydb/doc1 (Get Document) ---")
    val getDoc1Request = MockHttpRequest(method = "GET", path = "/mydb/doc1")
    launch {
        couchdbServer.httpRequestChannel.send(getDoc1Request)
        val response = couchdbServer.httpResponseChannel.receive()
        println("GET /mydb/doc1 Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test PUT /mydb/doc1 (update document with new revision)
    println("\n--- Test: PUT /mydb/doc1 (Update Document) ---")
    val updatedDoc1Body = "{\"_id\":\"doc1\",\"name\":\"Updated Document 1\",\"value\":456}"
    val updateDoc1Request = MockHttpRequest(
        method = "PUT",
        path = "/mydb/doc1",
        body = updatedDoc1Body
    )
    launch {
        couchdbServer.httpRequestChannel.send(updateDoc1Request)
        val response = couchdbServer.httpResponseChannel.receive()
        println("PUT /mydb/doc1 (Update) Response: Status=${response.status}, Body=${response.body}")
        // Extract the new revision for later use in DELETE
        val jsonResponse = Json.parseToJsonElement(response.body!!).jsonObject
        val newRev = jsonResponse["rev"]?.jsonPrimitive?.content
        if (newRev != null) {
            println("Extracted new revision: $newRev")
            // Test DELETE /mydb/doc1 (delete document with revision)
            println("\n--- Test: DELETE /mydb/doc1 (Delete Document with Revision) ---")
            val deleteDoc1Request = MockHttpRequest(
                method = "DELETE",
                path = "/mydb/doc1",
                headers = mapOf("rev" to newRev)
            )
            launch {
                couchdbServer.httpRequestChannel.send(deleteDoc1Request)
                val deleteResponse = couchdbServer.httpResponseChannel.receive()
                println("DELETE /mydb/doc1 Response: Status=${deleteResponse.status}, Body=${deleteResponse.body}")
            }
            delay(500)
        }
    }
    delay(1000) // Give time for the nested delete to process

    // Test GET /mydb/doc1 (after deletion)
    println("\n--- Test: GET /mydb/doc1 (After Deletion) ---")
    val getDeletedDoc1Request = MockHttpRequest(method = "GET", path = "/mydb/doc1")
    launch {
        couchdbServer.httpRequestChannel.send(getDeletedDoc1Request)
        val response = couchdbServer.httpResponseChannel.receive()
        println("GET /mydb/doc1 (Deleted) Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test POST /mydb/_bulk_docs
    println("\n--- Test: POST /mydb/_bulk_docs ---")
    val bulkDocsBody = "{\"docs\":[{\"_id\":\"bulk_doc_1\",\"data\":\"value1\"},{\"_id\":\"bulk_doc_2\",\"data\":\"value2\"}]}"
    val bulkDocsRequest = MockHttpRequest(
        method = "POST",
        path = "/mydb/_bulk_docs",
        body = bulkDocsBody
    )
    launch {
        couchdbServer.httpRequestChannel.send(bulkDocsRequest)
        val response = couchdbServer.httpResponseChannel.receive()
        println("POST /mydb/_bulk_docs Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test DELETE /mydb (delete database)
    println("\n--- Test: DELETE /mydb (Delete Database) ---")
    val deleteDbRequest = MockHttpRequest(method = "DELETE", path = "/mydb")
    launch {
        couchdbServer.httpRequestChannel.send(deleteDbRequest)
        val response = couchdbServer.httpResponseChannel.receive()
        println("DELETE /mydb Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Test GET /_all_dbs (after deleting mydb)
    println("\n--- Test: GET /_all_dbs (After Deleting mydb) ---")
    val allDbsRequest3 = MockHttpRequest(method = "GET", path = "/_all_dbs")
    launch {
        couchdbServer.httpRequestChannel.send(allDbsRequest3)
        val response = couchdbServer.httpResponseChannel.receive()
        println("GET /_all_dbs Response: Status=${response.status}, Body=${response.body}")
    }
    delay(500)

    // Clean up the server job
    serverJob.cancel()
    serverContext.close()
}