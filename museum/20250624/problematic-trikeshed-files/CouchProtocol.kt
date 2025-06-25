package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.lib.CZero.nz
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonObject as KotlinxJsonObject
import borg.trikeshed.lib.JsonObject as TrikeShedJsonObject

/**
 * CouchDB Protocol Implementation
 * Pure TrikeShed implementation of CouchDB wire protocol
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
    @SerialName("_attachments") val attachments: KotlinxJsonObject? = null,
    val data: KotlinxJsonObject = KotlinxJsonObject(emptyMap())
) {
    fun toJson(): KotlinxJsonObject = buildJsonObject {
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
    val docs: Indexed<KotlinxJsonObject>,
    val new_edits: Boolean = true,
    val all_or_nothing: Boolean = false
) {
    fun toJson(): KotlinxJsonObject = buildJsonObject {
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
    val query_params: KotlinxJsonObject? = null,
    val doc_ids: Indexed<String>? = null
) {
    fun toJson(): KotlinxJsonObject = buildJsonObject {
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
data class ReplicationStatus(
    val ok: Boolean,
    val session_id: String,
    val source_last_seq: String,
    val history: Indexed<ReplicationHistory>
)

@Serializable
data class ReplicationHistory(
    val session_id: String,
    val start_time: String,
    val end_time: String,
    val start_last_seq: String,
    val end_last_seq: String,
    val recorded_seq: String,
    val missing_checked: Int,
    val missing_found: Int,
    val docs_read: Int,
    val docs_written: Int,
    val doc_write_failures: Int
)

// Security
@Serializable
data class CouchSecurity(
    val admins: SecurityObject = SecurityObject(),
    val members: SecurityObject = SecurityObject()
) {
    @Serializable
    data class SecurityObject(
        val names: Indexed<String> = emptyIndex(),
        val roles: Indexed<String> = emptyIndex()
    )
    
    fun toJson(): KotlinxJsonObject = buildJsonObject {
        putJsonObject("admins") {
            putJsonArray("names") {
                for (i in 0 until admins.names.a) {
                    add(admins.names.b(i))
                }
            }
            putJsonArray("roles") {
                for (i in 0 until admins.roles.a) {
                    add(admins.roles.b(i))
                }
            }
        }
        putJsonObject("members") {
            putJsonArray("names") {
                for (i in 0 until members.names.a) {
                    add(members.names.b(i))
                }
            }
            putJsonArray("roles") {
                for (i in 0 until members.roles.a) {
                    add(members.roles.b(i))
                }
            }
        }
    }
}

// Design document
@Serializable
data class CouchDesignDocument(
    @SerialName("_id") val id: String,
    @SerialName("_rev") val rev: String? = null,
    val language: String = "javascript",
    val views: Map<String, ViewDefinition> = emptyMap(),
    val shows: Map<String, String> = emptyMap(),
    val lists: Map<String, String> = emptyMap(),
    val filters: Map<String, String> = emptyMap(),
    val updates: Map<String, String> = emptyMap(),
    val validate_doc_update: String? = null
) {
    @Serializable
    data class ViewDefinition(
        val map: String,
        val reduce: String? = null
    )
    
    fun toJson(): KotlinxJsonObject = buildJsonObject {
        put("_id", id)
        rev?.let { put("_rev", it) }
        put("language", language)
        
        if (views.isNotEmpty()) {
            putJsonObject("views") {
                views.forEach { (name, view) ->
                    putJsonObject(name) {
                        put("map", view.map)
                        view.reduce?.let { put("reduce", it) }
                    }
                }
            }
        }
        
        if (shows.isNotEmpty()) {
            putJsonObject("shows") {
                shows.forEach { (name, code) -> put(name, code) }
            }
        }
        
        if (lists.isNotEmpty()) {
            putJsonObject("lists") {
                lists.forEach { (name, code) -> put(name, code) }
            }
        }
        
        if (filters.isNotEmpty()) {
            putJsonObject("filters") {
                filters.forEach { (name, code) -> put(name, code) }
            }
        }
        
        if (updates.isNotEmpty()) {
            putJsonObject("updates") {
                updates.forEach { (name, code) -> put(name, code) }
            }
        }
        
        validate_doc_update?.let { put("validate_doc_update", it) }
    }
}

// Helper to create empty Indexed
private fun <T> emptyIndex(): Indexed<T> = 0 j { throw NoSuchElementException() }