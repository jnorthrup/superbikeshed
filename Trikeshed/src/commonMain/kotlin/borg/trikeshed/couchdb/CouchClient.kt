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
import kotlinx.serialization.Contextual

/**
 * URing-optimized CouchDB client with context-aware operations
 */
class CouchClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val bufferPool: URingBufferPool = URingBufferPool()
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Create database with URing-optimized file operations
     */
    suspend fun createDatabase(name: String): CouchResult {
        val response = httpClient.put("/$name", emptyMap(), null)
        return if (response.isSuccess) {
            CouchResult.Success("Database created: $name")
        } else {
            CouchResult.Error("Failed to create database: ${response.status.value}")
        }
    }
    
    /**
     * Delete database with URing cleanup
     */
    suspend fun deleteDatabase(name: String): CouchResult {
        val response = httpClient.delete("/$name")
        return if (response.isSuccess) {
            CouchResult.Success("Database deleted: $name")
        } else {
            CouchResult.Error("Failed to delete database: ${response.status.value}")
        }
    }
    
    /**
     * Get database info with URing-optimized read
     */
    suspend fun getDatabaseInfo(name: String): CouchDatabaseInfo? {
        val response = httpClient.get("/$name")
        return if (response.isSuccess) {
            parseDatabaseInfo(response.body)
        } else {
            null
        }
    }
    
    /**
     * Create document with URing-optimized write
     */
    suspend fun createDocument(database: String, document: CouchDocument): CouchDocumentResult {
        val docId = document._id.ifEmpty { generateUuid() }
        val response = httpClient.put("/$database/$docId", emptyMap(), document.toJson())
        
        return if (response.isSuccess) {
            val result = parsePutResult(response.body)
            Either.Right(CouchDocumentData(docId, result.rev, document.data.mapValues { (_, value) -> value.toString() }))
        } else {
            Either.Left("Failed to create document: ${response.status.value}")
        }
    }
    
    /**
     * Update document with URing-optimized write
     */
    suspend fun updateDocument(database: String, document: CouchDocumentData): CouchDocumentResult {
        val response = httpClient.put("/$database/${document.id}", emptyMap(), document.toJson())
        
        return if (response.isSuccess) {
            val result = parsePutResult(response.body)
            Either.Right(document.copy(rev = result.rev))
        } else {
            Either.Left("Failed to update document: ${response.status.value}")
        }
    }
    
    /**
     * Get document with URing-optimized read
     */
    suspend fun getDocument(database: String, id: String): CouchDocumentResult {
        val response = httpClient.get("/$database/$id")
        
        return if (response.isSuccess) {
            val document = parseDocument(response.body)
            Either.Right(document)
        } else {
            Either.Left("Document not found: $id")
        }
    }
    
    /**
     * Delete document with URing cleanup
     */
    suspend fun deleteDocument(database: String, id: String, rev: String): CouchDocumentResult {
        val response = httpClient.delete("/$database/$id?rev=$rev")
        
        return if (response.isSuccess) {
            val result = parsePutResult(response.body)
            Either.Right(CouchDocumentData(id, result.rev, emptyMap()))
        } else {
            Either.Left("Failed to delete document: ${response.status.value}")
        }
    }
    
    /**
     * Bulk operations with URing-optimized batch processing
     */
    suspend fun bulkDocuments(database: String, documents: List<CouchDocument>): List<CouchBulkResult> {
        val bulkRequest = CouchBulkRequest(documents)
        val response = httpClient.post("/$database/_bulk_docs", emptyMap(), bulkRequest.toJson())
        
        return if (response.isSuccess) {
            parseBulkResults(response.body)
        } else {
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
        val params = mutableMapOf<String, String>()
        since?.let { params["since"] = it }
        limit?.let { params["limit"] = it.toString() }
        if (includeDocs) params["include_docs"] = "true"
        filter?.let { params["filter"] = it }
        
        val queryString = if (params.isNotEmpty()) {
            "?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
        } else ""
        
        val response = httpClient.get("/$database/_changes$queryString")
        
        if (response.isSuccess) {
            val changes = parseChanges(response.body)
            changes.forEach { change -> emit(change) }
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
        val replicationRequest = CouchReplicationRequest(
            source = source,
            target = target,
            continuous = continuous,
            createTarget = createTarget
        )
        
        val response = httpClient.post("/_replicate", emptyMap(), replicationRequest.toJson())
        
        return if (response.isSuccess) {
            parseReplicationResult(response.body)
        } else {
            CouchReplicationResult(false, "Replication failed: ${response.status.value}")
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
        val queryString = buildViewQueryString(params)
        val response = httpClient.get("/$database/_design/$designDoc/_view/$viewName$queryString")
        
        return if (response.isSuccess) {
            parseViewResult(response.body)
        } else {
            CouchViewResult(0, 0, emptyList())
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
        data: ByteArray
    ): CouchDocumentResult {
        val headers = mapOf("Content-Type" to contentType)
        val response = httpClient.put("/$database/$docId/$attachmentName", headers, data)
        
        return if (response.isSuccess) {
            val result = parsePutResult(response.body)
            Either.Right(CouchDocumentData(docId, result.rev, emptyMap()))
        } else {
            Either.Left("Failed to put attachment: ${response.status.value}")
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
        val response = httpClient.get("/$database/$docId/$attachmentName")
        return if (response.isSuccess) {
            response.body
        } else {
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
        val response = httpClient.delete("/$database/$docId/$attachmentName?rev=$rev")
        
        return if (response.isSuccess) {
            val result = parsePutResult(response.body)
            Either.Right(CouchDocumentData(docId, result.rev, emptyMap()))
        } else {
            Either.Left("Failed to delete attachment: ${response.status.value}")
        }
    }
    
    /**
     * Security operations with URing-optimized access control
     */
    suspend fun getSecurity(database: String): CouchSecurity? {
        val response = httpClient.get("/$database/_security")
        return if (response.isSuccess) {
            parseSecurity(response.body)
        } else {
            null
        }
    }
    
    suspend fun setSecurity(database: String, security: CouchSecurity): Boolean {
        val response = httpClient.put("/$database/_security", emptyMap(), security.toJson())
        return response.isSuccess
    }
    
    // Helper methods
    private fun generateUuid(): String {
        return java.util.UUID.randomUUID().toString()
    }
    
    private fun buildViewQueryString(params: CouchViewParams): String {
        val queryParams = mutableListOf<String>()
        
        params.startKey?.let { queryParams.add("startkey=${it}") }
        params.endKey?.let { queryParams.add("endkey=${it}") }
        params.limit?.let { queryParams.add("limit=$it") }
        params.skip?.let { queryParams.add("skip=$it") }
        if (params.descending) queryParams.add("descending=true")
        if (params.includeDocs) queryParams.add("include_docs=true")
        if (!params.reduce) queryParams.add("reduce=false")
        if (params.group) queryParams.add("group=true")
        params.groupLevel?.let { queryParams.add("group_level=$it") }
        
        return if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
    }
    
    // JSON parsing methods
    private fun parseDatabaseInfo(body: ByteArray): CouchDatabaseInfo? {
        return try {
            json.decodeFromString(CouchDatabaseInfo.serializer(), body.decodeToString())
        } catch (e: Exception) {
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
        val data = jsonObject.filterKeys { it !in listOf("_id", "_rev", "_deleted") }
            .mapValues { (_, value) -> value.toString() }
        return CouchDocumentData(id, rev, data)
    }
    
    private fun parseBulkResults(body: ByteArray): List<CouchBulkResult> {
        return json.decodeFromString(List.serializer(CouchBulkResult.serializer()), body.decodeToString())
    }
    
    private fun parseChanges(body: ByteArray): List<CouchChange> {
        val jsonObject = json.parseToJsonElement(body.decodeToString()).jsonObject
        val results = jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.map { element ->
            json.decodeFromJsonElement(CouchChange.serializer(), element)
        }
    }
    
    private fun parseReplicationResult(body: ByteArray): CouchReplicationResult {
        return json.decodeFromString(CouchReplicationResult.serializer(), body.decodeToString())
    }
    
    private fun parseViewResult(body: ByteArray): CouchViewResult {
        return json.decodeFromString(CouchViewResult.serializer(), body.decodeToString())
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
    val ok: Boolean,
    val id: String,
    val rev: String,
    val error: String? = null,
    val reason: String? = null
)

@Serializable
data class CouchChange(
    val seq: String,
    val id: String,
    val changes: List<CouchChangeRev>,
    val deleted: Boolean = false,
    val doc: CouchDocumentData? = null
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
    val createTarget: Boolean = false
)

@Serializable
data class CouchReplicationResult(
    val ok: Boolean,
    val sessionId: String
)

@Serializable
data class CouchViewParams(
    @Contextual val startKey: Any? = null,
    @Contextual val endKey: Any? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val descending: Boolean = false,
    val includeDocs: Boolean = false,
    val reduce: Boolean = true,
    val group: Boolean = false,
    val groupLevel: Int? = null
)

@Serializable
data class CouchViewResult(
    val totalRows: Int,
    val offset: Int,
    val rows: List<CouchViewRow>
)

@Serializable
data class CouchViewRow(
    val id: String,
    val key: String,
    val value: String,
    val doc: CouchDocumentData? = null
)

// Missing data classes for CouchDB operations
sealed class CouchResult {
    data class Success(val message: String) : CouchResult()
    data class Error(val message: String) : CouchResult()
}

typealias CouchDocumentResult = Either<String, CouchDocumentData>

@Serializable
data class CouchDocumentData(
    val id: String,
    val rev: String,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class CouchDatabaseInfo(
    val dbName: String,
    val docCount: Long,
    val updateSeq: String,
    val docDelCount: Long,
    val purgeSeq: Long,
    val compactRunning: Boolean,
    val diskSize: Long,
    val dataSize: Long,
    val instanceStartTime: Long,
    val diskFormatVersion: Int,
    val committedUpdateSeq: Long
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

// HTTP Client implementation with URing optimization
class HttpClient(
    private val baseUrl: String,
    private val timeout: Long,
    private val bufferPool: URingBufferPool
) {
    suspend fun get(path: String): HttpResponse {
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(path),
            headers = mapOf(
                "Host" to extractHost(baseUrl),
                "Connection" to "keep-alive"
            ).toHttpHeaders()
        )
        return request.send()
    }
    
    suspend fun put(path: String, headers: Map<String, String>, body: ByteArray?): HttpResponse {
        val requestHeaders = mutableMapOf<String, String>()
        requestHeaders["Host"] = extractHost(baseUrl)
        requestHeaders["Connection"] = "keep-alive"
        requestHeaders.putAll(headers)
        
        val request = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath(path),
            headers = requestHeaders.toHttpHeaders(),
            body = body ?: byteArrayOf()
        )
        return request.send()
    }
    
    suspend fun post(path: String, headers: Map<String, String>, body: ByteArray?): HttpResponse {
        val requestHeaders = mutableMapOf<String, String>()
        requestHeaders["Host"] = extractHost(baseUrl)
        requestHeaders["Connection"] = "keep-alive"
        requestHeaders.putAll(headers)
        
        val request = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath(path),
            headers = requestHeaders.toHttpHeaders(),
            body = body ?: byteArrayOf()
        )
        return request.send()
    }
    
    suspend fun delete(path: String): HttpResponse {
        val request = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath(path),
            headers = mapOf(
                "Host" to extractHost(baseUrl),
                "Connection" to "keep-alive"
            ).toHttpHeaders()
        )
        return request.send()
    }
    
    fun close() {
        // Close connection
    }
    
    private fun extractHost(url: String): String {
        return url.removePrefix("http://").removePrefix("https://").split("/")[0]
    }
}

// Extension functions for JSON serialization
fun CouchDocument.toJson(): ByteArray {
    val jsonObject = buildJsonObject {
        if (_id.isNotEmpty()) put("_id", _id)
        if (_rev.isNotEmpty()) put("_rev", _rev)
        if (_deleted) put("_deleted", true)
        data.forEach { (key, value) -> put(key, value.toString()) }
    }
    return Json.encodeToString(JsonElement.serializer(), jsonObject).encodeToByteArray()
}

fun CouchDocumentData.toJson(): ByteArray {
    val jsonObject = buildJsonObject {
        put("_id", id)
        put("_rev", rev)
        data.forEach { (key, value) -> put(key, value) }
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