package borg.trikeshed.couchdb

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// Define a simple context element for CouchDB operations
data class CouchDBContext(val dbName: String, val baseUrl: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CouchDBContext>
    override val key: CoroutineContext.Key<CouchDBContext> = Key
}

// Data classes for blob operations
@Serializable
data class BlobPutRequest(val dbName: String, val id: String, val data: @Serializable(with = ByteArraySerializer::class) ByteArray)
@Serializable
data class BlobPutResponse(val id: String, val success: Boolean, val message: String? = null, val rev: String? = null)
@Serializable
data class BlobGetRequest(val dbName: String, val id: String)
@Serializable
data class BlobGetResponse(val id: String, val data: @Serializable(with = ByteArraySerializer::class) ByteArray? = null, val found: Boolean = false)
@Serializable
data class BlobUpdateRequest(val dbName: String, val id: String, val data: @Serializable(with = ByteArraySerializer::class) ByteArray, val rev: String? = null)
@Serializable
data class BlobUpdateResponse(val id: String, val success: Boolean, val message: String? = null, val rev: String? = null)
@Serializable
data class BlobDeleteRequest(val dbName: String, val id: String, val rev: String? = null)
@Serializable
data class BlobDeleteResponse(val id: String, val success: Boolean, val message: String? = null, val rev: String? = null)

@Serializable
data class DbCreateRequest(val dbName: String)
@Serializable
data class DbCreateResponse(val dbName: String, val success: Boolean, val message: String? = null)

@Serializable
data class DbDeleteRequest(val dbName: String)
@Serializable
data class DbDeleteResponse(val dbName: String, val success: Boolean, val message: String? = null)

@Serializable
data class DbListRequest(val dummy: Boolean = true) // Dummy request for listing dbs
@Serializable
data class DbListResponse(val dbNames: List<String>, val success: Boolean, val message: String? = null)

@Serializable
data class BulkDocsRequest(val dbName: String, val docs: List<@Serializable(with = ByteArraySerializer::class) ByteArray>)
@Serializable
data class BulkDocsResponse(val results: List<BlobPutResponse>, val success: Boolean, val message: String? = null)


// Custom serializer for ByteArray to allow @Serializable on data classes containing ByteArray
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializer(forClass = ByteArray::class)
object ByteArraySerializer : kotlinx.serialization.KSerializer<ByteArray> {
    override val descriptor = kotlinx.serialization.descriptors.buildPrimitiveDescriptor("ByteArray", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ByteArray) = encoder.encodeString(value.decodeToString())
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder) = decoder.decodeString().encodeToByteArray()
}

/**
 * Channelized mock service for CouchDB blob operations.
 * This simulates an end-to-end round trip using channels.
 */
class ChannelizedBlobService {

    // Channels for put operations
    val putRequestChannel = Channel<BlobPutRequest>()
    val putResponseChannel = Channel<BlobPutResponse>()

    // Channels for get operations
    val getRequestChannel = Channel<BlobGetRequest>()
    val getResponseChannel = Channel<BlobGetResponse>()

    // Channels for update operations
    val updateRequestChannel = Channel<BlobUpdateRequest>()
    val updateResponseChannel = Channel<BlobUpdateResponse>()

    // Channels for delete operations
    val deleteRequestChannel = Channel<BlobDeleteRequest>()
    val deleteResponseChannel = Channel<BlobDeleteResponse>()

    // Channels for database operations
    val createDbRequestChannel = Channel<DbCreateRequest>()
    val createDbResponseChannel = Channel<DbCreateResponse>()

    val deleteDbRequestChannel = Channel<DbDeleteRequest>()
    val deleteDbResponseChannel = Channel<DbDeleteResponse>()

    val listDbsRequestChannel = Channel<DbListRequest>()
    val listDbsResponseChannel = Channel<DbListResponse>()

    // Channels for bulk operations
    val bulkDocsRequestChannel = Channel<BulkDocsRequest>()
    val bulkDocsResponseChannel = Channel<BulkDocsResponse>()


    // In-memory store to simulate blob storage (our "tiny CRUD table")
    // dbName -> docId -> docContent
    private val blobStore = ConcurrentHashMap<String, ConcurrentHashMap<String, ByteArray>>()
    // dbName -> docId -> rev
    private val revisions = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    private val json = Json { prettyPrint = true }

    private fun generateRev(): String {
        return "1-" + (0..31).map { (('a'..'f') + ('0'..'9')).random() }.joinToString("")
    }

    private suspend fun logStoreState(operation: String, context: CoroutineContext) {
        val dbContext = context[CouchDBContext]
        println("\n--- Mock Server ($operation) - DB: ${dbContext?.dbName ?: "N/A"} --- ")
        println("Current Blob Store (Tiny CRUD Table) State:")
        if (blobStore.isEmpty()) {
            println("  (empty)")
        } else {
            blobStore.forEach { (dbName, dbContent) ->
                println("  DB: $dbName")
                if (dbContent.isEmpty()) {
                    println("    (empty)")
                } else {
                    dbContent.forEach { (id, data) ->
                        val rev = revisions[dbName]?.get(id) ?: "N/A"
                        println("    ID: \"$id\", Rev: \"$rev\", Data: \"${data.decodeToString()}\"")
                    }
                }
            }
        }
        println("--------------------------------------------------")
        delay(50) // Simulate twittering delay
    }

    /**
     * Coroutine that processes incoming put/update requests and sends responses.
     * This simulates the server-side processing of a blob put/update.
     */
    suspend fun processPutRequests(context: CoroutineContext) = withContext(context) {
        putRequestChannel.consumeAsFlow()
            .onEach { request ->
                val db = blobStore.getOrPut(request.dbName) { ConcurrentHashMap() }
                val dbRevs = revisions.getOrPut(request.dbName) { ConcurrentHashMap() }
                val currentRev = dbRevs[request.id]
                val newRev = generateRev()

                println("Mock Server (Put/Update): Received request for blob \"${request.id}\" for DB: ${request.dbName}")
                db[request.id] = request.data
                dbRevs[request.id] = newRev
                putResponseChannel.send(BlobPutResponse(request.id, true, "Blob stored/updated successfully", newRev))
                logStoreState("Put/Update", context)
            }
            .collect()
    }

    /**
     * Coroutine that processes incoming get requests and sends responses.
     * This simulates the server-side processing of a blob get.
     */
    suspend fun processGetRequests(context: CoroutineContext) = withContext(context) {
        getRequestChannel.consumeAsFlow()
            .onEach { request ->
                val db = blobStore[request.dbName]
                println("Mock Server (Get): Received request for blob \"${request.id}\" for DB: ${request.dbName}")
                val data = db?.get(request.id)
                getResponseChannel.send(BlobGetResponse(request.id, data, data != null))
                logStoreState("Get", context)
            }
            .collect()
    }

    /**
     * Coroutine that processes incoming update requests and sends responses.
     * This simulates the server-side processing of a blob update.
     */
    suspend fun processUpdateRequests(context: CoroutineContext) = withContext(context) {
        updateRequestChannel.consumeAsFlow()
            .onEach { request ->
                val db = blobStore[request.dbName]
                val dbRevs = revisions[request.dbName]
                println("Mock Server (Update): Received request for blob \"${request.id}\" for DB: ${request.dbName}")
                if (db != null && dbRevs != null && db.containsKey(request.id)) {
                    // Basic revision check
                    if (request.rev != null && request.rev != dbRevs[request.id]) {
                        updateResponseChannel.send(BlobUpdateResponse(request.id, false, "Conflict", dbRevs[request.id]))
                    } else {
                        val newRev = generateRev()
                        db[request.id] = request.data
                        dbRevs[request.id] = newRev
                        updateResponseChannel.send(BlobUpdateResponse(request.id, true, "Blob updated successfully", newRev))
                    }
                } else {
                    updateResponseChannel.send(BlobUpdateResponse(request.id, false, "Blob not found for update"))
                }
                logStoreState("Update", context)
            }
            .collect()
    }

    /**
     * Coroutine that processes incoming delete requests and sends responses.
     * This simulates the server-side processing of a blob delete.
     */
    suspend fun processDeleteRequests(context: CoroutineContext) = withContext(context) {
        deleteRequestChannel.consumeAsFlow()
            .onEach { request ->
                val db = blobStore[request.dbName]
                val dbRevs = revisions[request.dbName]
                println("Mock Server (Delete): Received request for blob \"${request.id}\" for DB: ${request.dbName}")
                if (db != null && dbRevs != null && db.containsKey(request.id)) {
                    // Basic revision check
                    if (request.rev != null && request.rev != dbRevs[request.id]) {
                        deleteResponseChannel.send(BlobDeleteResponse(request.id, false, "Conflict", dbRevs[request.id]))
                    } else {
                        val newRev = "2-" + generateRev().substringAfter("-") // Simulate new revision for deletion
                        db.remove(request.id)
                        dbRevs.remove(request.id)
                        deleteResponseChannel.send(BlobDeleteResponse(request.id, true, "Blob deleted successfully", newRev))
                    }
                } else {
                    deleteResponseChannel.send(BlobDeleteResponse(request.id, false, "Blob not found for deletion"))
                }
                logStoreState("Delete", context)
            }
            .collect()
    }

    /**
     * Coroutine that processes incoming create DB requests and sends responses.
     */
    suspend fun processCreateDbRequests(context: CoroutineContext) = withContext(context) {
        createDbRequestChannel.consumeAsFlow()
            .onEach { request ->
                println("Mock Server (Create DB): Received request for DB \"${request.dbName}\"")
                if (blobStore.containsKey(request.dbName)) {
                    createDbResponseChannel.send(DbCreateResponse(request.dbName, false, "Database already exists"))
                } else {
                    blobStore[request.dbName] = ConcurrentHashMap()
                    revisions[request.dbName] = ConcurrentHashMap()
                    createDbResponseChannel.send(DbCreateResponse(request.dbName, true, "Database created successfully"))
                }
                logStoreState("Create DB", context)
            }
            .collect()
    }

    /**
     * Coroutine that processes incoming delete DB requests and sends responses.
     */
    suspend fun processDeleteDbRequests(context: CoroutineContext) = withContext(context) {
        deleteDbRequestChannel.consumeAsFlow()
            .onEach { request ->
                println("Mock Server (Delete DB): Received request for DB \"${request.dbName}\"")
                if (blobStore.remove(request.dbName) != null) {
                    revisions.remove(request.dbName)
                    deleteDbResponseChannel.send(DbDeleteResponse(request.dbName, true, "Database deleted successfully"))
                } else {
                    deleteDbResponseChannel.send(DbDeleteResponse(request.dbName, false, "Database not found"))
                }
                logStoreState("Delete DB", context)
            }
            .collect()
    }

    /**
     * Coroutine that processes incoming list DBs requests and sends responses.
     */
    suspend fun processListDbsRequests(context: CoroutineContext) = withContext(context) {
        listDbsRequestChannel.consumeAsFlow()
            .onEach { request ->
                println("Mock Server (List DBs): Received request")
                val dbNames = blobStore.keys.toList()
                listDbsResponseChannel.send(DbListResponse(dbNames, true, "Databases listed successfully"))
                logStoreState("List DBs", context)
            }
            .collect()
    }

    /**
     * Coroutine that processes incoming bulk docs requests and sends responses.
     */
    suspend fun processBulkDocsRequests(context: CoroutineContext) = withContext(context) {
        bulkDocsRequestChannel.consumeAsFlow()
            .onEach { request ->
                val db = blobStore.getOrPut(request.dbName) { ConcurrentHashMap() }
                val dbRevs = revisions.getOrPut(request.dbName) { ConcurrentHashMap() }
                val results = mutableListOf<BlobPutResponse>()
                println("Mock Server (Bulk Docs): Received request for DB \"${request.dbName}\" with ${request.docs.size} documents")
                request.docs.forEach { docBytes ->
                    // Assuming docBytes is a JSON string with an _id field
                    val docJson = Json.parseToJsonElement(docBytes.decodeToString()).jsonObject
                    val id = docJson["_id"]?.jsonPrimitive?.content
                    if (id != null) {
                        val newRev = generateRev()
                        db[id] = docBytes
                        dbRevs[id] = newRev
                        results.add(BlobPutResponse(id, true, "Document processed", newRev))
                    } else {
                        results.add(BlobPutResponse("", false, "Document missing _id"))
                    }
                }
                bulkDocsResponseChannel.send(BulkDocsResponse(results, true, "Bulk operation completed"))
                logStoreState("Bulk Docs", context)
            }
            .collect()
    }


    /**
     * Client-side function to put/create a blob.
     * Sends a request through the channel and waits for a response.
     */
    suspend fun putBlob(dbName: String, id: String, data: ByteArray, context: CoroutineContext): BlobPutResponse = withContext(context) {
        putRequestChannel.send(BlobPutRequest(dbName, id, data))
        putResponseChannel.receive()
    }

    /**
     * Client-side function to get a blob.
     * Sends a request through the channel and waits for a response.
     */
    suspend fun getBlob(dbName: String, id: String, context: CoroutineContext): BlobGetResponse = withContext(context) {
        getRequestChannel.send(BlobGetRequest(dbName, id))
        getResponseChannel.receive()
    }

    /**
     * Client-side function to update a blob.
     * Sends a request through the channel and waits for a response.
     */
    suspend fun updateBlob(dbName: String, id: String, data: ByteArray, rev: String?, context: CoroutineContext): BlobUpdateResponse = withContext(context) {
        updateRequestChannel.send(BlobUpdateRequest(dbName, id, data, rev))
        updateResponseChannel.receive()
    }

    /**
     * Client-side function to delete a blob.
     * Sends a request through the channel and waits for a response.
     */
    suspend fun deleteBlob(dbName: String, id: String, rev: String?, context: CoroutineContext): BlobDeleteResponse = withContext(context) {
        deleteRequestChannel.send(BlobDeleteRequest(dbName, id, rev))
        deleteResponseChannel.receive()
    }

    /**
     * Client-side function to create a database.
     */
    suspend fun createDb(dbName: String, context: CoroutineContext): DbCreateResponse = withContext(context) {
        createDbRequestChannel.send(DbCreateRequest(dbName))
        createDbResponseChannel.receive()
    }

    /**
     * Client-side function to delete a database.
     */
    suspend fun deleteDb(dbName: String, context: CoroutineContext): DbDeleteResponse = withContext(context) {
        deleteDbRequestChannel.send(DbDeleteRequest(dbName))
        deleteDbResponseChannel.receive()
    }

    /**
     * Client-side function to list all databases.
     */
    suspend fun listDbs(context: CoroutineContext): DbListResponse = withWithContext(context) {
        listDbsRequestChannel.send(DbListRequest())
        listDbsResponseChannel.receive()
    }

    /**
     * Client-side function for bulk document operations.
     */
    suspend fun bulkDocs(dbName: String, docs: List<ByteArray>, context: CoroutineContext): BulkDocsResponse = withContext(context) {
        bulkDocsRequestChannel.send(BulkDocsRequest(dbName, docs))
        bulkDocsResponseChannel.receive()
    }
}