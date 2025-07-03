package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer
import kotlin.uuid.ExperimentalUuidApi

/**
 * URing-optimized CouchDB client with context-aware operations
 */
interface CouchClient {
    enum class Transport { HTTP, QUIC }
    
    suspend fun getServerInfo(): JsonObject
    suspend fun listDatabases(): Indexed<String>
    suspend fun createDatabase(name: String): CouchResponse
    suspend fun deleteDatabase(name: String): CouchResponse
    suspend fun getDatabaseInfo(name: String): CouchDatabaseInfo
    
    suspend fun getDocument(dbName: String, docId: String): CouchDocument?
    suspend fun putDocument(dbName: String, doc: CouchDocument): CouchResponse
    suspend fun deleteDocument(dbName: String, docId: String, rev: String): CouchResponse
    suspend fun bulkDocs(dbName: String, request: BulkDocsRequest): Indexed<CouchResponse>
    
    suspend fun queryView(
        dbName: String,
        designDoc: String,
        viewName: String,
        params: ViewQueryParams = ViewQueryParams()
    ): ViewResponse<JsonElement, JsonElement>
    
    suspend fun getChanges(
        dbName: String,
        params: ChangesFeedParams = ChangesFeedParams()
    ): ChangesResponse
    
    suspend fun replicate(request: ReplicationRequest): ReplicationResponse
    suspend fun putDesignDocument(dbName: String, doc: DesignDocument): CouchResponse
}
    enum class Transport { HTTP, QUIC }
    
    suspend fun getServerInfo(): JsonObject
    suspend fun listDatabases(): Indexed<String>
    suspend fun createDatabase(name: String): CouchResponse
    suspend fun deleteDatabase(name: String): CouchResponse
    suspend fun getDatabaseInfo(name: String): CouchDatabaseInfo
    
    suspend fun getDocument(dbName: String, docId: String): CouchDocument?
    suspend fun putDocument(dbName: String, doc: CouchDocument): CouchResponse
    suspend fun deleteDocument(dbName: String, docId: String, rev: String): CouchResponse
    suspend fun bulkDocs(dbName: String, request: BulkDocsRequest): Indexed<CouchResponse>
    
    suspend fun queryView(
        dbName: String,
        designDoc: String,
        viewName: String,
        params: ViewQueryParams = ViewQueryParams()
    ): ViewResponse<JsonElement, JsonElement>
    
    suspend fun getChanges(
        dbName: String,
        params: ChangesFeedParams = ChangesFeedParams()
    ): ChangesResponse
    
    suspend fun replicate(request: ReplicationRequest): ReplicationResponse
    suspend fun putDesignDocument(dbName: String, doc: DesignDocument): CouchResponse
}
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
    @OptIn(ExperimentalUuidApi::class)
    private fun generateUuid(): String {
        return kotlin.uuid.Uuid.random().toString()
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
            .mapValues { (_, value) -> value.jsonPrimitive.content }
        return CouchDocumentData(id, rev, data)
    }
    
    private fun parseBulkResults(body: ByteArray): List<CouchBulkResult> {
        return json.decodeFromString(ListSerializer(CouchBulkResult.serializer()), body.decodeToString())
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



 