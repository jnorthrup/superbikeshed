package borg.trikeshed.couchdb

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// Channelized HTTP Request and Response compositions
data class ChannelizedHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class ChannelizedHttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

class CouchDBServer(
    private val blobService: ChannelizedBlobService,
    private val serverContext: CoroutineContext
) {
    private val json = Json { prettyPrint = true }

    // Channels for channelized HTTP compositions
    val httpRequestChannel = Channel<ChannelizedHttpRequest>()
    val httpResponseChannel = Channel<ChannelizedHttpResponse>()

    suspend fun start() = coroutineScope {
        println("CouchDB Server starting...")

        // Launch the blob service processors
        launch(serverContext) { blobService.processPutRequests(serverContext) }
        launch(serverContext) { blobService.processGetRequests(serverContext) }
        launch(serverContext) { blobService.processUpdateRequests(serverContext) }
        launch(serverContext) { blobService.processDeleteRequests(serverContext) }
        launch(serverContext) { blobService.processCreateDbRequests(serverContext) }
        launch(serverContext) { blobService.processDeleteDbRequests(serverContext) }
        launch(serverContext) { blobService.processListDbsRequests(serverContext) }
        launch(serverContext) { blobService.processBulkDocsRequests(serverContext) }

        // Launch the HTTP request handler
        launch(serverContext) {
            for (request in httpRequestChannel) {
                val response = handleHttpRequest(request)
                httpResponseChannel.send(response)
            }
        }
        println("CouchDB Server started and listening for requests.")
    }

    fun stop() {
        blobService.stop()
    }

    private suspend fun handleHttpRequest(request: ChannelizedHttpRequest): ChannelizedHttpResponse {
        println("CouchDB Server: Received HTTP request: ${request.method} ${request.path}")

        val pathParts = request.path.split("/").filter { it.isNotEmpty() }

        // Handle root path
        if (request.path == "/") {
            return when (request.method) {
                "GET" -> ChannelizedHttpResponse(200, body = json.encodeToString(mapOf("couchdb" to "Welcome", "version" to "1.7.2", "channelized" to true)))
                else -> ChannelizedHttpResponse(405, body = "Method Not Allowed")
            }
        }

        // Handle _all_dbs
        if (request.path == "/_all_dbs") {
            return when (request.method) {
                "GET" -> {
                    val listResponse = blobService.listDbs(serverContext)
                    if (listResponse.success) {
                        ChannelizedHttpResponse(200, body = json.encodeToString(listResponse.dbNames))
                    } else {
                        ChannelizedHttpResponse(500, body = json.encodeToString(mapOf("error" to "internal_error", "reason" to listResponse.message)))
                    }
                }
                else -> ChannelizedHttpResponse(405, body = "Method Not Allowed")
            }
        }

        // Handle database level operations
        if (pathParts.size == 1) {
            val dbName = pathParts[0]
            val dbContext = serverContext + CouchDBContext(dbName, "http://localhost:5984/$dbName")
            return when (request.method) {
                "PUT" -> {
                    val createResponse = blobService.createDb(dbName, dbContext)
                    if (createResponse.success) {
                        ChannelizedHttpResponse(201, body = json.encodeToString(mapOf("ok" to true)))
                    } else {
                        ChannelizedHttpResponse(412, body = json.encodeToString(mapOf("error" to "file_exists", "reason" to createResponse.message)))
                    }
                }
                "DELETE" -> {
                    val deleteResponse = blobService.deleteDb(dbName, dbContext)
                    if (deleteResponse.success) {
                        ChannelizedHttpResponse(200, body = json.encodeToString(mapOf("ok" to true)))
                    } else {
                        ChannelizedHttpResponse(404, body = json.encodeToString(mapOf("error" to "not_found", "reason" to deleteResponse.message)))
                    }
                }
                else -> ChannelizedHttpResponse(405, body = "Method Not Allowed")
            }
        }

        // Handle document level operations
        if (pathParts.size == 2) {
            val dbName = pathParts[0]
            val docId = pathParts[1]
            val dbContext = serverContext + CouchDBContext(dbName, "http://localhost:5984/$dbName")

            return when (request.method) {
                "GET" -> {
                    val getResponse = blobService.getBlob(dbName, docId, dbContext)
                    if (getResponse.found && getResponse.data != null) {
                        ChannelizedHttpResponse(200, body = getResponse.data.decodeToString())
                    } else {
                        ChannelizedHttpResponse(404, body = json.encodeToString(mapOf("error" to "not_found", "reason" to "missing")))
                    }
                }
                "PUT" -> {
                    if (request.body == null) {
                        ChannelizedHttpResponse(400, body = "Bad Request: Missing body for PUT")
                    } else {
                        val putResponse = blobService.putBlob(dbName, docId, request.body.encodeToByteArray(), dbContext)
                        if (putResponse.success) {
                            ChannelizedHttpResponse(201, body = json.encodeToString(mapOf("ok" to true, "id" to putResponse.id, "rev" to putResponse.rev)))
                        } else {
                            ChannelizedHttpResponse(500, body = json.encodeToString(mapOf("error" to "failed_to_create", "reason" to putResponse.message)))
                        }
                    }
                }
                "DELETE" -> {
                    val rev = request.headers["If-Match"]?.removePrefix("\"")?.removeSuffix("\"") ?: request.headers["rev"]
                    if (rev == null) {
                        ChannelizedHttpResponse(400, body = "Bad Request: Missing revision for DELETE (use If-Match header or rev query param)")
                    } else {
                        val deleteResponse = blobService.deleteBlob(dbName, docId, rev, dbContext)
                        if (deleteResponse.success) {
                            ChannelizedHttpResponse(200, body = json.encodeToString(mapOf("ok" to true, "id" to deleteResponse.id, "rev" to deleteResponse.rev)))
                        } else if (deleteResponse.message == "Conflict") {
                            ChannelizedHttpResponse(409, body = json.encodeToString(mapOf("error" to "conflict", "reason" to "Document update conflict.")))
                        } else {
                            ChannelizedHttpResponse(404, body = json.encodeToString(mapOf("error" to "not_found", "reason" to deleteResponse.message)))
                        }
                    }
                }
                else -> ChannelizedHttpResponse(405, body = "Method Not Allowed")
            }
        }

        // Handle _bulk_docs
        if (pathParts.size == 2 && pathParts[1] == "_bulk_docs") {
            val dbName = pathParts[0]
            val dbContext = serverContext + CouchDBContext(dbName, "http://localhost:5984/$dbName")
            return when (request.method) {
                "POST" -> {
                    if (request.body == null) {
                        ChannelizedHttpResponse(400, body = "Bad Request: Missing body for _bulk_docs")
                    } else {
                        val requestBodyJson = Json.parseToJsonElement(request.body).jsonObject
                        val docsJsonArray = requestBodyJson["docs"]?.jsonArray

                        if (docsJsonArray == null) {
                            ChannelizedHttpResponse(400, body = "Bad Request: Missing 'docs' array in body")
                        }

                        val docs = docsJsonArray.map { it.toString().encodeToByteArray() }
                        val bulkResponse = blobService.bulkDocs(dbName, docs, dbContext)

                        if (bulkResponse.success) {
                            ChannelizedHttpResponse(201, body = json.encodeToString(bulkResponse.results.map { mapOf("ok" to it.success, "id" to it.id, "rev" to it.rev) }))
                        } else {
                            ChannelizedHttpResponse(500, body = json.encodeToString(mapOf("error" to "internal_error", "reason" to bulkResponse.message)))
                        }
                    }
                }
                else -> ChannelizedHttpResponse(405, body = "Method Not Allowed")
            }
        }

        return ChannelizedHttpResponse(404, body = "Not Found")
    }
}