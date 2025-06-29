package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Contextual
import kotlin.uuid.ExperimentalUuidApi

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.SerialName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid // Ensure Uuid is imported if not already via other means

/**
 * A client for interacting with a CouchDB server using the Trikeshed HTTP client infrastructure.
 *
 * This client provides methods for common CouchDB operations such as managing databases,
 * documents, views, replication, and attachments. It relies on `HttpRequest.send()`
 * for network communication, which in turn uses platform-specific `ClientChannel` implementations.
 *
 * Usage:
 * ```
 * val client = CouchClient(baseUrl = "http://localhost:5984")
 * val result = client.createDatabase("mydb")
 * // ...
 * ```
 *
 * Note: The "URing-optimized" comments in method descriptions are aspirational,
 * dependent on the underlying `ClientChannel` actual implementations leveraging URing.
 * JSON serialization and deserialization are handled using `kotlinx.serialization`.
 */
class CouchClient(
    private val baseUrl: String, // e.g., "http://localhost:5984"
    // private val bufferPool: URingBufferPool = URingBufferPool() // bufferPool might be managed by HttpRequest.send() or its underlying ClientChannel impls
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    private fun buildCommonHeaders(hostOnly: String): Map<String, String> {
        return mapOf(
            "Host" to hostOnly,
            "Connection" to "close", // Keep-alive needs more complex management, start with close
            "Accept" to "application/json"
        )
    }
    
    private fun buildCommonHeadersWithContentType(hostOnly: String): Map<String, String> {
        return buildCommonHeaders(hostOnly) + ("Content-Type" to "application/json")
    }

    private fun extractHostFromBaseUrl(): String {
        return baseUrl.removePrefix("http://").removePrefix("https://").split("/")[0]
    }

    /**
     * Create database with URing-optimized file operations
     */
    suspend fun createDatabase(name: String): CouchResult {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/$name"),
            headers = buildCommonHeadersWithContentType(hostOnly).toHttpHeaders(),
            body = byteArrayOf()
        )
        try {
            val response = request.send()
            return if (response.isSuccess) {
                CouchResult.Success("Database created: $name")
            } else {
                CouchResult.Error("Failed to create database: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
            return CouchResult.Error("Failed to create database: ${e.message}")
        }
    }
    
    /**
     * Delete database with URing cleanup
     */
    suspend fun deleteDatabase(name: String): CouchResult {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/$name"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )
        try {
            val response = request.send()
            return if (response.isSuccess) {
                CouchResult.Success("Database deleted: $name")
            } else {
                CouchResult.Error("Failed to delete database: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
            return CouchResult.Error("Failed to delete database: ${e.message}")
        }
    }
    
    /**
     * Get database info with URing-optimized read
     */
    suspend fun getDatabaseInfo(name: String): CouchDatabaseInfo? {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$name"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )
        return try {
            val response = request.send()
            if (response.isSuccess) {
                parseDatabaseInfo(response.body)
            } else {
                // Log error: response.status.value, response.body.decodeToString()
                null
            }
        } catch (e: Exception) {
            // Log error: e.message
            null
        }
    }
    
    /**
     * Create document with URing-optimized write
     */
    suspend fun createDocument(database: String, document: CouchDocument): CouchDocumentResult {
        val hostOnly = extractHostFromBaseUrl()
        val docId = document._id.ifEmpty { generateUuid() }
        val request = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/$database/$docId"),
            headers = buildCommonHeadersWithContentType(hostOnly).toHttpHeaders(),
            body = document.toJson()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                val result = parsePutResult(response.body)
                Either.Right(CouchDocumentData(docId, result.rev, document.data.mapValues { (_, value) -> value.toString() }))
            } else {
                Either.Left("Failed to create document: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
            Either.Left("Failed to create document: ${e.message}")
        }
    }
    
    /**
     * Update document with URing-optimized write
     */
    suspend fun updateDocument(database: String, document: CouchDocumentData): CouchDocumentResult {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/$database/${document.id}"),
            headers = buildCommonHeadersWithContentType(hostOnly).toHttpHeaders(),
            body = document.toJson()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                val result = parsePutResult(response.body)
                Either.Right(document.copy(rev = result.rev))
            } else {
                Either.Left("Failed to update document: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
            Either.Left("Failed to update document: ${e.message}")
        }
    }
    
    /**
     * Get document with URing-optimized read
     */
    suspend fun getDocument(database: String, id: String): CouchDocumentResult {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$database/$id"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                val document = parseDocument(response.body)
                Either.Right(document)
            } else {
                Either.Left("Document not found: $id. Status: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
             Either.Left("Document not found: $id. Error: ${e.message}")
        }
    }
    
    /**
     * Delete document with URing cleanup
     */
    suspend fun deleteDocument(database: String, id: String, rev: String): CouchDocumentResult {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/$database/$id?rev=$rev"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                val result = parsePutResult(response.body)
                Either.Right(CouchDocumentData(id, result.rev, emptyMap()))
            } else {
                Either.Left("Failed to delete document: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
            Either.Left("Failed to delete document: ${e.message}")
        }
    }
    
    /**
     * Bulk operations with URing-optimized batch processing
     */
    suspend fun bulkDocuments(database: String, documents: List<CouchDocument>): List<CouchBulkResult> {
        val hostOnly = extractHostFromBaseUrl()
        val bulkRequest = CouchBulkRequest(documents)
        val request = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("/$database/_bulk_docs"),
            headers = buildCommonHeadersWithContentType(hostOnly).toHttpHeaders(),
            body = bulkRequest.toJson()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                parseBulkResults(response.body)
            } else {
                // Log error
                emptyList()
            }
        } catch (e: Exception) {
            // Log error
            emptyList()
        }
    }
    
    /**
     * Changes feed with URing-optimized streaming
     */
    suspend fun getChanges(
        database: String,
        since: String? = null,
        limit: Int? = null,
        includeDocs: Boolean = false,
        filter: String? = null
    ): Flow<CouchChange> = flow {
        val hostOnly = extractHostFromBaseUrl()
        val params = mutableMapOf<String, String>()
        since?.let { params["since"] = it }
        limit?.let { params["limit"] = it.toString() }
        if (includeDocs) params["include_docs"] = "true"
        filter?.let { params["filter"] = it }
        
        val queryString = if (params.isNotEmpty()) {
            "?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
        } else ""
        
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$database/_changes$queryString"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )

        try {
            val response = request.send()
            if (response.isSuccess) {
                val changes = parseChanges(response.body)
                changes.forEach { change -> emit(change) }
            } else {
                // Log error
            }
        } catch (e: Exception) {
            // Log error
        }
    }
    
    /**
     * Replication with URing-optimized data transfer
     */
    suspend fun replicate(
        source: String,
        target: String,
        continuous: Boolean = false,
        createTarget: Boolean = false
    ): CouchReplicationResult {
        val hostOnly = extractHostFromBaseUrl() // Assumes replication endpoint is on the same CouchDB instance
        val replicationRequest = CouchReplicationRequest(
            source = source, // This might be a full URL or local DB name
            target = target, // This might be a full URL or local DB name
            continuous = continuous,
            createTarget = createTarget
        )
        
        val request = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("/_replicate"),
            headers = buildCommonHeadersWithContentType(hostOnly).toHttpHeaders(),
            body = replicationRequest.toJson()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                parseReplicationResult(response.body)
            } else {
                CouchReplicationResult(false, "Replication failed: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
             CouchReplicationResult(false, "Replication failed: ${e.message}")
        }
    }
    
    /**
     * View queries with URing-optimized read
     */
    suspend fun queryView(
        database: String,
        designDoc: String,
        viewName: String,
        params: CouchViewParams = CouchViewParams()
    ): CouchViewResult {
        val hostOnly = extractHostFromBaseUrl()
        val queryString = buildViewQueryString(params)
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$database/_design/$designDoc/_view/$viewName$queryString"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                parseViewResult(response.body)
            } else {
                CouchViewResult(0, 0, emptyList()) // Consider returning error info
            }
        } catch (e: Exception) {
            CouchViewResult(0, 0, emptyList()) // Consider returning error info
        }
    }
    
    /**
     * Attachment operations with URing-optimized file I/O
     */
    suspend fun putAttachment(
        database: String,
        docId: String,
        attachmentName: String,
        contentType: String,
        data: ByteArray,
        rev: String? = null // rev is often required for attachments
    ): CouchDocumentResult {
        val hostOnly = extractHostFromBaseUrl()
        val path = if (rev != null) "/$database/$docId/$attachmentName?rev=$rev" else "/$database/$docId/$attachmentName"
        val request = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath(path),
            headers = (buildCommonHeaders(hostOnly) + ("Content-Type" to contentType)).toHttpHeaders(),
            body = data
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                val result = parsePutResult(response.body)
                Either.Right(CouchDocumentData(docId, result.rev, emptyMap())) // Body might not contain full doc data
            } else {
                Either.Left("Failed to put attachment: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
            Either.Left("Failed to put attachment: ${e.message}")
        }
    }
    
    /**
     * Get attachment with URing-optimized read
     */
    suspend fun getAttachment(
        database: String,
        docId: String,
        attachmentName: String
    ): ByteArray? {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$database/$docId/$attachmentName"),
            headers = mapOf( // Accept might vary depending on attachment
                "Host" to hostOnly,
                "Connection" to "close"
            ).toHttpHeaders()
        )
        return try {
            val response = request.send()
            if (response.isSuccess) {
                response.body
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Delete attachment with URing cleanup
     */
    suspend fun deleteAttachment(
        database: String,
        docId: String,
        attachmentName: String,
        rev: String
    ): CouchDocumentResult {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/$database/$docId/$attachmentName?rev=$rev"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )
        
        return try {
            val response = request.send()
            if (response.isSuccess) {
                val result = parsePutResult(response.body)
                Either.Right(CouchDocumentData(docId, result.rev, emptyMap()))
            } else {
                Either.Left("Failed to delete attachment: ${response.status.value} - ${response.body.decodeToString()}")
            }
        } catch (e: Exception) {
            Either.Left("Failed to delete attachment: ${e.message}")
        }
    }
    
    /**
     * Security operations with URing-optimized access control
     */
    suspend fun getSecurity(database: String): CouchSecurity? {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/$database/_security"),
            headers = buildCommonHeaders(hostOnly).toHttpHeaders()
        )
        return try {
            val response = request.send()
            if (response.isSuccess) {
                parseSecurity(response.body)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun setSecurity(database: String, security: CouchSecurity): Boolean {
        val hostOnly = extractHostFromBaseUrl()
        val request = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/$database/_security"),
            headers = buildCommonHeadersWithContentType(hostOnly).toHttpHeaders(),
            body = security.toJson()
        )
        return try {
            request.send().isSuccess
        } catch (e: Exception) {
            false
        }
    }
    
    // Helper methods
    @OptIn(ExperimentalUuidApi::class)
    private fun generateUuid(): String {
        return kotlin.uuid.Uuid.random().toString()
    }
    
    private fun buildViewQueryString(params: CouchViewParams): String {
        val queryParams = mutableListOf<String>()
        
        // Ensure keys are JSON encoded strings for query parameters
        params.startKey?.let { queryParams.add("startkey=${json.encodeToString(JsonElement.serializer(), toJsonLiteral(it))}") }
        params.endKey?.let { queryParams.add("endkey=${json.encodeToString(JsonElement.serializer(), toJsonLiteral(it))}") }
        params.limit?.let { queryParams.add("limit=$it") }
        params.skip?.let { queryParams.add("skip=$it") }
        if (params.descending) queryParams.add("descending=true")
        if (params.includeDocs) queryParams.add("include_docs=true")
        if (!params.reduce) queryParams.add("reduce=false") // CouchDB defaults to reduce=true for map/reduce views
        if (params.group) queryParams.add("group=true")
        params.groupLevel?.let { queryParams.add("group_level=$it") }
        
        return if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
    }

    private fun toJsonLiteral(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is JsonElement -> value // Already a JsonElement
            // Add other common types if necessary, or rely on kotlinx.serialization for complex objects if passed as string
            else -> JsonPrimitive(value.toString()) // Fallback, might not be correct for all CouchDB key types
        }
    }
    
    // JSON parsing methods
    private fun parseDatabaseInfo(body: ByteArray): CouchDatabaseInfo? {
        return try {
            json.decodeFromString(CouchDatabaseInfo.serializer(), body.decodeToString())
        } catch (e: Exception) {
            // Log exception e
            null
        }
    }
    
    private fun parsePutResult(body: ByteArray): CouchPutResult {
        return json.decodeFromString(CouchPutResult.serializer(), body.decodeToString())
    }
    
    private fun parseDocument(body: ByteArray): CouchDocumentData {
        val jsonObject = json.parseToJsonElement(body.decodeToString()).jsonObject
        val id = jsonObject["_id"]?.jsonPrimitive?.content ?: ""
        val rev = jsonObject["_rev"]?.jsonPrimitive?.content ?: ""
        val data = jsonObject.filterKeys { it !in listOf("_id", "_rev", "_deleted", "_attachments") }
            .mapValues { (_, value) -> value.toString() } // This is a simplification, values might not be strings
        return CouchDocumentData(id, rev, data)
    }
    
    private fun parseBulkResults(body: ByteArray): List<CouchBulkResult> {
         return try {
            json.decodeFromString(ListSerializer(CouchBulkResult.serializer()), body.decodeToString())
        } catch (e: Exception) {
            // Log exception
            emptyList()
        }
    }
    
    private fun parseChanges(body: ByteArray): List<CouchChange> {
        return try {
            val jsonObject = json.parseToJsonElement(body.decodeToString()).jsonObject
            val results = jsonObject["results"]?.jsonArray ?: return emptyList()
            results.map { element ->
                json.decodeFromJsonElement(CouchChange.serializer(), element)
            }
        } catch (e: Exception) {
            // Log exception
            emptyList()
        }
    }
    
    private fun parseReplicationResult(body: ByteArray): CouchReplicationResult {
        return json.decodeFromString(CouchReplicationResult.serializer(), body.decodeToString())
    }
    
    private fun parseViewResult(body: ByteArray): CouchViewResult {
         return try {
            json.decodeFromString(CouchViewResult.serializer(), body.decodeToString())
        } catch (e: Exception) {
            // Log exception
            CouchViewResult(0,0, emptyList())
        }
    }
    
    private fun parseSecurity(body: ByteArray): CouchSecurity {
        return json.decodeFromString(CouchSecurity.serializer(), body.decodeToString())
    }
}

/**
 * URing-optimized buffer pool for CouchDB operations
 */
class URingBufferPool {
    private val buffers = mutableListOf<ByteArray>()
    private val bufferSize = 8192
    
    fun acquire(): ByteArray {
        return buffers.removeFirstOrNull() ?: ByteArray(bufferSize)
    }
    
    fun release(buffer: ByteArray) {
        if (buffer.size == bufferSize) {
            buffers.add(buffer)
        }
    }
    
    fun close() {
        buffers.clear()
    }
}

// Data classes for CouchDB operations
@Serializable
data class CouchBulkRequest(
    val docs: List<CouchDocument>
)

@Serializable
data class CouchBulkResult(
    val ok: Boolean? = null, // Made nullable as per CouchDB, not always present on error
    val id: String,
    val rev: String? = null, // Made nullable
    val error: String? = null,
    val reason: String? = null
)

@Serializable
data class CouchChange(
    val seq: JsonElement, // Sequence can be string or int
    val id: String,
    val changes: List<CouchChangeRev>,
    val deleted: Boolean = false,
    val doc: JsonObject? = null // Doc is a JsonObject
)

@Serializable
data class CouchChangeRev(
    val rev: String
)

@Serializable
data class CouchReplicationRequest(
    val source: String,
    val target: String,
    val continuous: Boolean = false,
    val createTarget: Boolean = false,
    val doc_ids: List<String>? = null, // Optional field
    val proxy: String? = null // Optional field
)

@Serializable
data class CouchReplicationResult(
    val ok: Boolean,
    @SerialName("session_id") val sessionId: String, // CouchDB uses session_id
    @SerialName("source_last_seq") val sourceLastSeq: JsonElement? = null, // Can be string or int
    @SerialName("history") val history: List<ReplicationHistoryEntry>? = null // Optional
)

@Serializable
data class ReplicationHistoryEntry(
    @SerialName("session_id") val sessionId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("start_last_seq") val startLastSeq: JsonElement,
    @SerialName("end_last_seq") val endLastSeq: JsonElement,
    @SerialName("recorded_seq") val recordedSeq: JsonElement,
    @SerialName("missing_checked") val missingChecked: Long,
    @SerialName("missing_found") val missingFound: Long,
    @SerialName("docs_read") val docsRead: Long,
    @SerialName("docs_written") val docsWritten: Long,
    @SerialName("doc_write_failures") val docWriteFailures: Long
)


@Serializable
data class CouchViewParams(
    @Contextual val key: Any? = null, // Single key
    @Contextual val keys: List<Any>? = null, // Multiple keys for POST
    @Contextual val startKey: Any? = null,
    @Contextual val endKey: Any? = null,
    @SerialName("startkey_docid") @Contextual val startKeyDocId: String? = null,
    @SerialName("endkey_docid") @Contextual val endKeyDocId: String? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val descending: Boolean = false,
    @SerialName("include_docs") val includeDocs: Boolean = false,
    val reduce: Boolean? = null, // Nullable to allow CouchDB default (true for reduce views, false for map)
    val group: Boolean = false,
    @SerialName("group_level") val groupLevel: Int? = null,
    @SerialName("stale") val stale: String? = null, // ok, update_after, false (as string)
    @SerialName("update_seq") val updateSeq: Boolean = false
)

@Serializable
data class CouchViewResult(
    @SerialName("total_rows") val totalRows: Long, // Changed to Long
    val offset: Long, // Changed to Long
    val rows: List<CouchViewRow>,
    @SerialName("update_seq") val updateSeq: JsonElement? = null // Optional, can be string or int
)

@Serializable
data class CouchViewRow(
    val id: String? = null, // Not always present (e.g. with reduce)
    val key: JsonElement, // Key can be any JSON value
    val value: JsonElement, // Value can be any JSON value
    val doc: JsonObject? = null // Optional document
)

// Missing data classes for CouchDB operations
sealed class CouchResult {
    data class Success(val message: String) : CouchResult()
    data class Error(val message: String) : CouchResult()
}

typealias CouchDocumentResult = Either<String, CouchDocumentData>

@Serializable
data class CouchDocumentData(
    @SerialName("_id") val id: String, // Use @SerialName for _id and _rev
    @SerialName("_rev") val rev: String,
    val data: Map<String, String> = emptyMap(), // This remains simplified; real docs have complex JSON values
    @SerialName("_deleted") val deleted: Boolean? = null, // Optional
    @SerialName("_attachments") val attachments: Map<String, CouchAttachmentInfo>? = null // Optional
)

@Serializable
data class CouchAttachmentInfo(
    val stub: Boolean? = null,
    @SerialName("content_type") val contentType: String,
    val length: Long,
    val revpos: Int? = null, // Not always present
    val digest: String? = null // Not always present if stub=true
    // data is not part of info, it's separate
)


@Serializable
data class CouchDatabaseInfo(
    @SerialName("db_name") val dbName: String,
    @SerialName("doc_count") val docCount: Long,
    @SerialName("update_seq") val updateSeq: JsonElement, // Can be string or int
    @SerialName("doc_del_count") val docDelCount: Long,
    @SerialName("purge_seq") val purgeSeq: JsonElement, // Can be string or int
    @SerialName("compact_running") val compactRunning: Boolean,
    @SerialName("disk_size") val diskSize: Long,
    @SerialName("data_size") val dataSize: Long,
    @SerialName("instance_start_time") val instanceStartTime: String, // Usually a string
    @SerialName("disk_format_version") val diskFormatVersion: Int,
    @SerialName("committed_update_seq") val committedUpdateSeq: JsonElement // Can be string or int
)

@Serializable
data class CouchPutResult(
    val ok: Boolean,
    val id: String,
    val rev: String
)

@Serializable
data class CouchSecurity(
    val admins: SecurityObject = SecurityObject(),
    val members: SecurityObject = SecurityObject()
) {
    @Serializable
    data class SecurityObject(
        val names: List<String> = emptyList(),
        val roles: List<String> = emptyList()
    )
}

// Extension functions for JSON serialization
fun CouchDocument.toJson(): ByteArray {
    val jsonObject = buildJsonObject {
        if (_id.isNotEmpty()) put("_id", JsonPrimitive(_id))
        if (_rev.isNotEmpty()) put("_rev", JsonPrimitive(_rev))
        if (_deleted) put("_deleted", JsonPrimitive(true))
        data.forEach { (key, value) -> put(key, JsonPrimitive(value.toString())) }
    }
    return Json.encodeToString(JsonElement.serializer(), jsonObject).encodeToByteArray()
}

fun CouchDocumentData.toJson(): ByteArray {
    val jsonObject = buildJsonObject {
        put("_id", JsonPrimitive(id))
        put("_rev", JsonPrimitive(rev))
        data.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }
    return Json.encodeToString(JsonElement.serializer(), jsonObject).encodeToByteArray()
}

fun CouchBulkRequest.toJson(): ByteArray {
    return Json.encodeToString(CouchBulkRequest.serializer(), this).encodeToByteArray()
}

fun CouchReplicationRequest.toJson(): ByteArray {
    return Json.encodeToString(CouchReplicationRequest.serializer(), this).encodeToByteArray()
}

fun CouchSecurity.toJson(): ByteArray {
    return Json.encodeToString(CouchSecurity.serializer(), this).encodeToByteArray()
}

@Serializable
data class CouchDocument(
    val _id: String = "",
    val _rev: String = "",
    val _deleted: Boolean = false,
    val data: Map<String, String> = emptyMap()
) 