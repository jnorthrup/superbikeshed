package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * CouchDB Protocol Implementation
 * Pure TrikeShed implementation of CouchDB wire protocol
 * Enhanced with production-ready implementation from git history
 */

// CouchDB response
@Serializable
data class CouchResponse(
    val ok: Boolean = false,
    val id: String? = null,
    val rev: String? = null,
    val error: String? = null,
    val reason: String? = null
)

// Document with metadata
@Serializable
data class CouchDocument(
    @SerialName("_id") val id: String? = null,
    @SerialName("_rev") val rev: String? = null,
    @SerialName("_deleted") val deleted: Boolean? = null,
    @SerialName("_attachments") val attachments: JsonObject? = null,
    val data: Map<String, JsonElement> = emptyMap()
) {
    fun toJson(): JsonObject = buildJsonObject {
        id?.let { put("_id", it) }
        rev?.let { put("_rev", it) }
        deleted?.let { put("_deleted", it) }
        attachments?.let { put("_attachments", it) }
        data.forEach { (key, value) -> put(key, value) }
    }
}

// Database info
@Serializable
data class CouchDatabaseInfo(
    @SerialName("db_name") val dbName: String,
    @SerialName("doc_count") val docCount: Long,
    @SerialName("doc_del_count") val docDelCount: Long,
    @SerialName("update_seq") val updateSeq: String,
    @SerialName("purge_seq") val purgeSeq: Long,
    @SerialName("compact_running") val compactRunning: Boolean,
    @SerialName("disk_size") val diskSize: Long,
    @SerialName("data_size") val dataSize: Long,
    @SerialName("instance_start_time") val instanceStartTime: String,
    @SerialName("disk_format_version") val diskFormatVersion: Int,
    @SerialName("committed_update_seq") val committedUpdateSeq: String
)

// View query parameters
@Serializable
data class ViewQueryParams(
    val key: JsonElement? = null,
    val startkey: JsonElement? = null,
    val endkey: JsonElement? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val descending: Boolean = false,
    val include_docs: Boolean = false,
    val inclusive_end: Boolean = true,
    val reduce: Boolean? = null,
    val group: Boolean = false,
    val group_level: Int? = null
) {
    fun toQueryString(): String {
        val params = mutableListOf<String>()
        key?.let { params.add("key=${Json.encodeToString(it)}") }
        startkey?.let { params.add("startkey=${Json.encodeToString(it)}") }
        endkey?.let { params.add("endkey=${Json.encodeToString(it)}") }
        limit?.let { params.add("limit=$it") }
        skip?.let { params.add("skip=$it") }
        if (descending) params.add("descending=true")
        if (include_docs) params.add("include_docs=true")
        if (!inclusive_end) params.add("inclusive_end=false")
        reduce?.let { params.add("reduce=$it") }
        if (group) params.add("group=true")
        group_level?.let { params.add("group_level=$it") }
        return if (params.isEmpty()) "" else "?${params.joinToString("&")}"
    }
}

// View response
@Serializable
data class ViewResponse<K, V>(
    val total_rows: Int,
    val offset: Int,
    val rows: Indexed<ViewRow<K, V>>
)

@Serializable
data class ViewRow<K, V>(
    val id: String,
    val key: K,
    val value: V,
    val doc: CouchDocument? = null
)

// Bulk docs request
@Serializable
data class BulkDocsRequest(
    val docs: Indexed<JsonObject>,
    val new_edits: Boolean = true,
    val all_or_nothing: Boolean = false
) {
    fun toJson(): JsonObject = buildJsonObject {
        putJsonArray("docs") {
            for (i in 0 until docs.a) {
                add(docs.b(i))
            }
        }
        put("new_edits", new_edits)
        put("all_or_nothing", all_or_nothing)
    }
}

// Changes feed
@Serializable
data class ChangesFeedParams(
    val since: String = "0",
    val limit: Int? = null,
    val style: String = "main_only",
    val feed: String = "normal", // normal, continuous, longpoll
    val heartbeat: Long? = null,
    val timeout: Long? = null,
    val filter: String? = null,
    val include_docs: Boolean = false
) {
    fun toQueryString(): String {
        val params = mutableListOf<String>()
        params.add("since=$since")
        limit?.let { params.add("limit=$it") }
        params.add("style=$style")
        params.add("feed=$feed")
        heartbeat?.let { params.add("heartbeat=$it") }
        timeout?.let { params.add("timeout=$it") }
        filter?.let { params.add("filter=$it") }
        if (include_docs) params.add("include_docs=true")
        return "?${params.joinToString("&")}"
    }
}

@Serializable
data class ChangesResponse(
    val results: Indexed<Change>,
    val last_seq: String,
    val pending: Int
)

@Serializable
data class Change(
    val seq: String,
    val id: String,
    val changes: Indexed<ChangeRev>,
    val deleted: Boolean = false,
    val doc: CouchDocument? = null
)

@Serializable
data class ChangeRev(
    val rev: String
)

// Replication
@Serializable
data class ReplicationRequest(
    val source: String,
    val target: String,
    val continuous: Boolean = false,
    val create_target: Boolean = false,
    val filter: String? = null,
    val query_params: JsonObject? = null,
    val doc_ids: Indexed<String>? = null
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("source", source)
        put("target", target)
        put("continuous", continuous)
        put("create_target", create_target)
        filter?.let { put("filter", it) }
        query_params?.let { put("query_params", it) }
        doc_ids?.let { ids ->
            putJsonArray("doc_ids") {
                for (i in 0 until ids.a) {
                    add(ids.b(i))
                }
            }
        }
    }
}

@Serializable
data class ReplicationResponse(
    val ok: Boolean,
    val session_id: String? = null,
    val source_last_seq: String? = null,
    val history: Indexed<ReplicationHistory>? = null
)

@Serializable
data class ReplicationHistory(
    val session_id: String,
    val start_time: String,
    val end_time: String,
    val start_last_seq: String,
    val end_last_seq: String,
    val recorded_seq: String,
    val missing_checked: Long,
    val missing_found: Long,
    val docs_read: Long,
    val docs_written: Long,
    val doc_write_failures: Long
)

// Design document
@Serializable
data class DesignDocument(
    @SerialName("_id") val id: String,
    @SerialName("_rev") val rev: String? = null,
    val language: String = "javascript",
    val views: Map<String, ViewDefinition> = emptyMap(),
    val shows: Map<String, String> = emptyMap(),
    val lists: Map<String, String> = emptyMap(),
    val updates: Map<String, String> = emptyMap(),
    val filters: Map<String, String> = emptyMap(),
    val validate_doc_update: String? = null,
    val rewrites: Indexed<RewriteRule> = emptyIndex(),
    val options: JsonObject? = null
)

@Serializable
data class ViewDefinition(
    val map: String,
    val reduce: String? = null
)

@Serializable
data class RewriteRule(
    val from: String,
    val to: String,
    val method: String? = null,
    val query: String? = null
)

// CouchDB client interface
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