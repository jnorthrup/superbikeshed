package org.flatton.types

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import borg.trikeshed.lib.toSeries
// Remove kotlinx.serialization dependency - using trikeshed types

// TrikeShed-compatible JSON types
typealias JsonObject = Map<String, Any?>
typealias JsonElement = Any?

// Ontological Typealiases for CouchDB Primitives
@JvmInline value class DocumentId(val value: String)
@JvmInline value class RevisionId(val value: String)
@JvmInline value class DatabaseName(val value: String)
@JvmInline value class DesignDocId(val value: String) {
    fun asDocId(): DocumentId = DocumentId("_design/${this.value}")
}
@JvmInline value class ViewName(val value: String)
@JvmInline value class MapFunction(val code: String)
@JvmInline value class ReduceFunction(val code: String)
typealias AttachmentName = String
typealias FieldName = String
typealias JsonString = String
typealias DocCount = Int
typealias UpdateSeq = String
typealias HumanSize = String

// Data Models
data class CouchDocument(
    val _id: DocumentId,
    val _rev: RevisionId? = null,
    val type: String? = null,
    val source: String? = null,
    val target: String? = null,
    val continuous: Boolean? = null,
    val filter: String? = null,
    val queryParams: Map<String, String> = emptyMap(),
    val docIds: List<String>? = null,
    val userContext: Map<String, Any>? = null,
    val history: List<ReplicationHistoryEntry>? = null
)

data class AttachmentInfo(
    val content_type: String,
    val revpos: Int,
    val digest: String,
    val length: Long,
    val stub: Boolean? = null,
    val follows: Boolean? = null,
    val data: String? = null // Base64 encoded
)

data class CouchView(
    val map: MapFunction,
    val reduce: ReduceFunction? = null
)

data class CouchDesignDocument(
    val id: DesignDocId,
    val rev: RevisionId? = null,
    val language: String = "javascript",
    val views: Map<ViewName, CouchView> = emptyMap()
)

data class CouchDatabase(
    val name: DatabaseName,
    val status: CouchDatabaseStatus
)

data class CouchDatabaseStatus(
    val docCount: DocCount,
    val updateSeq: UpdateSeq,
    val humanSize: HumanSize
)

data class CouchDatabaseInfo(
    val dbName: DatabaseName,
    val docCount: DocCount,
    val docDelCount: Int,
    val updateSeq: UpdateSeq,
    val purgeSeq: String,
    val compactRunning: Boolean,
    val diskSize: Long,
    val dataSize: Long,
    val instanceStartTime: String,
    val diskFormatVersion: Int,
    val committedUpdateSeq: UpdateSeq,
    val compactedSeq: String,
    val uuid: String
)

data class CouchSecurity(
    val admins: SecurityPrincipal,
    val members: SecurityPrincipal
)

data class SecurityPrincipal(
    val names: Series<String>,
    val roles: Series<String>
)

data class CouchResponse(
    val ok: Boolean,
    val id: DocumentId? = null,
    val rev: RevisionId? = null,
    val error: String? = null,
    val reason: String? = null
)

data class ViewQueryParams(
    val key: JsonElement? = null,
    val keys: Series<JsonElement>? = null,
    val startKey: JsonElement? = null,
    val endKey: JsonElement? = null,
    val startKeyDocId: DocumentId? = null,
    val endKeyDocId: DocumentId? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val descending: Boolean? = null,
    val includeDocs: Boolean? = null,
    val reduce: Boolean? = null,
    val group: Boolean? = null,
    val groupLevel: Int? = null,
    val stale: String? = null
) {
    fun toQueryString(): String {
        val params = mutableListOf<String>()
        key?.let { params.add("key=${JsonImpl.stringify(it)}") }
        keys?.let { params.add("keys=${JsonImpl.stringify(it.play.toList())}") }
        startKey?.let { params.add("startkey=${JsonImpl.stringify(it)}") }
        endKey?.let { params.add("endkey=${JsonImpl.stringify(it)}") }
        startKeyDocId?.let { params.add("startkey_docid=${it.value}") }
        endKeyDocId?.let { params.add("endkey_docid=${it.value}") }
        limit?.let { params.add("limit=$it") }
        skip?.let { params.add("skip=$it") }
        descending?.let { params.add("descending=$it") }
        includeDocs?.let { params.add("include_docs=$it") }
        reduce?.let { params.add("reduce=$it") }
        group?.let { params.add("group=$it") }
        groupLevel?.let { params.add("group_level=$it") }
        stale?.let { params.add("stale=$it") }
        return if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }
}

data class ViewResponse<K, V>(
    val totalRows: Int,
    val offset: Int,
    val updateSeq: String?,
    val rows: Series<ViewRow<K, V>>
)

data class ViewRow<K, V>(
    val id: DocumentId,
    val key: K,
    val value: V,
    val doc: CouchDocument? = null
)

data class ReplicationHistoryEntry(
    val startTime: String,
    val endTime: String? = null,
    val startLastSeq: String,
    val endLastSeq: String,
    val recordedSeq: String,
    val missingFound: Long,
    val docsRead: Long,
    val docsWritten: Long,
    val docWriteFailures: Long,
    val error: String? = null
)

// Wire Protocol Adapters (Serialization/Deserialization)

object CouchDocumentAdapter {
    fun fromJson(json: String): CouchDocument {
        val map = JsonImpl.parse(json) as Map<String, Any?>
        val data = map.filterKeys { !it.startsWith("_") }
        return CouchDocument(
            _id = DocumentId(map["_id"] as String),
            _rev = (map["_rev"] as? String)?.let { RevisionId(it) },
            type = map["type"] as? String,
            source = map["source"] as? String,
            target = map["target"] as? String,
            continuous = map["continuous"] as? Boolean,
            filter = map["filter"] as? String,
            queryParams = map["query_params"] as? Map<String, String> ?: emptyMap(),
            docIds = map["doc_ids"] as? List<String>,
            userContext = map["user_context"] as? Map<String, Any>,
            history = map["history"] as? List<ReplicationHistoryEntry>
        )
    }

    fun toJson(doc: CouchDocument): String {
        val map = mutableMapOf<String, Any?>()
        map["_id"] = doc._id.value
        doc._rev?.let { map["_rev"] = it.value }
        doc.type?.let { map["type"] = it }
        doc.source?.let { map["source"] = it }
        doc.target?.let { map["target"] = it }
        doc.continuous?.let { map["continuous"] = it }
        doc.filter?.let { map["filter"] = it }
        doc.queryParams.let { map["query_params"] = it }
        doc.docIds?.let { map["doc_ids"] = it }
        doc.userContext?.let { map["user_context"] = it }
        doc.history?.let { map["history"] = it }
        return JsonImpl.stringify(map)
    }
}

object CouchDesignDocumentAdapter {
    fun fromJson(json: String): CouchDesignDocument {
        val map = JsonImpl.parse(json) as Map<String, Any?>
        return CouchDesignDocument(
            id = DesignDocId((map["_id"] as String).removePrefix("_design/")),
            rev = (map["_rev"] as? String)?.let { RevisionId(it) },
            language = map["language"] as String,
            views = (map["views"] as Map<String, Map<String, String>>).mapKeys { ViewName(it.key) }
                .mapValues {
                    CouchView(
                        map = MapFunction(it.value["map"]!!),
                        reduce = it.value["reduce"]?.let { r -> ReduceFunction(r) }
                    )
                }
        )
    }

    fun toJson(ddoc: CouchDesignDocument): String {
        return JsonImpl.stringify(mapOf(
            "_id" to ddoc.id.asDocId().value,
            "_rev" to ddoc.rev?.value,
            "language" to ddoc.language,
            "views" to ddoc.views.mapKeys { it.key.value }.mapValues {
                mapOf(
                    "map" to it.value.map.code,
                    "reduce" to it.value.reduce?.code
                ).filterValues { v -> v != null }
            }
        ))
    }
}

object CouchDatabaseInfoAdapter {
    fun fromJson(json: String): CouchDatabaseInfo {
        val map = JsonImpl.parse(json) as Map<String, Any?>
        return CouchDatabaseInfo(
            dbName = DatabaseName(map["db_name"] as String),
            docCount = map["doc_count"] as Int,
            docDelCount = map["doc_del_count"] as Int,
            updateSeq = map["update_seq"] as String,
            purgeSeq = map["purge_seq"] as String,
            compactRunning = map["compact_running"] as Boolean,
            diskSize = (map["disk_size"] as Number).toLong(),
            dataSize = (map["data_size"] as Number).toLong(),
            instanceStartTime = map["instance_start_time"] as String,
            diskFormatVersion = map["disk_format_version"] as Int,
            committedUpdateSeq = map["committed_update_seq"] as String,
            compactedSeq = map["compacted_seq"] as String,
            uuid = map["uuid"] as String
        )
    }
}

object CouchSecurityAdapter {
    fun fromJson(json: String): CouchSecurity {
        val map = JsonImpl.parse(json) as Map<String, Any?>
        return CouchSecurity(
            admins = SecurityPrincipal(
                names = ((map["admins"] as? Map<*, *>)?.get("names") as? List<String> ?: emptyList()).toSeries(),
                roles = ((map["admins"] as? Map<*, *>)?.get("roles") as? List<String> ?: emptyList()).toSeries()
            ),
            members = SecurityPrincipal(
                names = ((map["members"] as? Map<*, *>)?.get("names") as? List<String> ?: emptyList()).toSeries(),
                roles = ((map["members"] as? Map<*, *>)?.get("roles") as? List<String> ?: emptyList()).toSeries()
            )
        )
    }

    fun toJson(security: CouchSecurity): String {
        return JsonImpl.stringify(mapOf(
            "admins" to mapOf(
                "names" to security.admins.names.play.toList(),
                "roles" to security.admins.roles.play.toList()
            ),
            "members" to mapOf(
                "names" to security.members.names.play.toList(),
                "roles" to security.members.roles.play.toList()
            )
        ))
    }
}

object CouchResponseAdapter {
    fun fromJson(json: String): CouchResponse {
        val map = JsonImpl.parse(json) as Map<String, Any?>
        return CouchResponse(
            ok = map["ok"] as Boolean,
            id = (map["id"] as? String)?.let { DocumentId(it) },
            rev = (map["rev"] as? String)?.let { RevisionId(it) },
            error = map["error"] as? String,
            reason = map["reason"] as? String
        )
    }
}

class CouchException(message: String) : Exception(message)