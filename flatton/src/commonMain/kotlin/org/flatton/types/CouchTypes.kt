package org.flatton.types

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import borg.trikeshed.lib.toSeries

@JvmInline
value class DocumentId(val value: String)

@JvmInline
value class RevisionId(val value: String)

@JvmInline
value class DatabaseName(val value: String)

@JvmInline
value class ViewName(val value: String)

@JvmInline
value class MapFunction(val value: String)

@JvmInline
value class ReduceFunction(val value: String)

data class CouchDocument(
    val id: DocumentId,
    val rev: RevisionId? = null,
    val data: Map<String, Any> = emptyMap()
) {
    fun toJson(): String = JsonImpl.stringify(mapOf(
        "_id" to id.value,
        "_rev" to (rev?.value),
        "data" to data
    ))

    companion object {
        fun fromJson(json: String): CouchDocument {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
            return CouchDocument(
                id = DocumentId(map["_id"] as String),
                rev = (map["_rev"] as? String)?.let { RevisionId(it) },
                data = (map["data"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value } ?: emptyMap()
            )
        }
    }
}

data class CouchView(
    val map: MapFunction,
    val reduce: ReduceFunction? = null
)

data class CouchDesignDocument(
    val id: DocumentId,
    val rev: RevisionId? = null,
    val views: Map<ViewName, CouchView>,
    val language: String = "javascript"
) {
    fun toJson(): String = JsonImpl.stringify(mapOf(
        "_id" to "_design/${id.value}",
        "_rev" to (rev?.value),
        "views" to views.mapKeys { it.key.value },
        "language" to language
    ))

    companion object {
        fun fromJson(json: String): CouchDesignDocument {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
            return CouchDesignDocument(
                id = DocumentId((map["_id"] as String).removePrefix("_design/")),
                rev = (map["_rev"] as? String)?.let { RevisionId(it) },
                views = (map["views"] as? Map<*, *>)?.mapKeys { ViewName(it.key.toString()) }?.mapValues { 
                    val viewMap = it.value as Map<*, *>
                    CouchView(
                        map = MapFunction(viewMap["map"] as String),
                        reduce = (viewMap["reduce"] as? String)?.let { ReduceFunction(it) }
                    )
                } ?: emptyMap(),
                language = map["language"] as? String ?: "javascript"
            )
        }
    }
}

data class CouchDatabaseInfo(
    val dbName: DatabaseName,
    val docCount: Int,
    val docDelCount: Int,
    val updateSeq: String,
    val purgeSeq: String,
    val compactRunning: Boolean,
    val diskSize: Long,
    val dataSize: Long,
    val instanceStartTime: String,
    val diskFormatVersion: Int,
    val committedUpdateSeq: String,
    val compactedSeq: String,
    val uuid: String
) {
    companion object {
        fun fromJson(json: String): CouchDatabaseInfo {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
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
}

data class CouchSecurity(
    val admins: SecurityRoles,
    val members: SecurityRoles
) {
    fun toJson(): String = JsonImpl.stringify(mapOf(
        "admins" to mapOf(
            "names" to admins.names.▶.toList(),
            "roles" to admins.roles.▶.toList()
        ),
        "members" to mapOf(
            "names" to members.names.▶.toList(),
            "roles" to members.roles.▶.toList()
        )
    ))

    companion object {
        fun fromJson(json: String): CouchSecurity {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
            return CouchSecurity(
                admins = SecurityRoles(
                    names = ((map["admins"] as? Map<*, *>)?.get("names") as? List<String> ?: emptyList()).toSeries(),
                    roles = ((map["admins"] as? Map<*, *>)?.get("roles") as? List<String> ?: emptyList()).toSeries()
                ),
                members = SecurityRoles(
                    names = ((map["members"] as? Map<*, *>)?.get("names") as? List<String> ?: emptyList()).toSeries(),
                    roles = ((map["members"] as? Map<*, *>)?.get("roles") as? List<String> ?: emptyList()).toSeries()
                )
            )
        }
    }
}

data class SecurityRoles(
    val names: Series<String>,
    val roles: Series<String>
)

data class AdminPartyConfig(
    val enabled: Boolean,
    val adminRoles: Series<String>
) {
    fun toJson(): String = JsonImpl.stringify(mapOf(
        "enabled" to enabled,
        "admin_roles" to adminRoles.▶.toList()
    ))

    companion object {
        fun fromJson(json: String): AdminPartyConfig {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
            return AdminPartyConfig(
                enabled = map["enabled"] as Boolean,
                adminRoles = (map["admin_roles"] as? List<String> ?: emptyList()).toSeries()
            )
        }
    }
}

data class CouchResponse(
    val ok: Boolean,
    val id: DocumentId,
    val rev: RevisionId
) {
    companion object {
        fun fromJson(json: String): CouchResponse {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
            return CouchResponse(
                ok = map["ok"] as Boolean,
                id = DocumentId(map["id"] as String),
                rev = RevisionId(map["rev"] as String)
            )
        }
    }
}

data class CouchError(
    val error: String,
    val reason: String
) {
    companion object {
        fun fromJson(json: String): CouchError {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
            return CouchError(
                error = map["error"] as String,
                reason = map["reason"] as String
            )
        }
    }
}

data class ViewQueryParams(
    val key: Any? = null,
    val keys: Series<Any>? = null,
    val startKey: Any? = null,
    val endKey: Any? = null,
    val startKeyDocId: DocumentId? = null,
    val endKeyDocId: DocumentId? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val descending: Boolean? = null,
    val includeDocs: Boolean? = null,
    val reduce: Boolean? = null,
    val group: Boolean? = null,
    val groupLevel: Int? = null
) {
    fun toQueryString(): String {
        val params = buildString {
            key?.let { append("key=${JsonImpl.stringify(it)}&") }
            keys?.let { append("keys=${JsonImpl.stringify(it.▶.toList())}&") }
            startKey?.let { append("startkey=${JsonImpl.stringify(it)}&") }
            endKey?.let { append("endkey=${JsonImpl.stringify(it)}&") }
            startKeyDocId?.let { append("startkey_docid=${it.value}&") }
            endKeyDocId?.let { append("endkey_docid=${it.value}&") }
            limit?.let { append("limit=$it&") }
            skip?.let { append("skip=$it&") }
            descending?.let { append("descending=$it&") }
            includeDocs?.let { append("include_docs=$it&") }
            reduce?.let { append("reduce=$it&") }
            group?.let { append("group=$it&") }
            groupLevel?.let { append("group_level=$it&") }
        }.removeSuffix("&")
        return if (params.isEmpty()) "" else "?$params"
    }
}

data class ViewResponse<T>(
    val totalRows: Int,
    val offset: Int,
    val rows: Series<ViewRow<T>>
) {
    companion object {
        inline fun <reified T> fromJson(json: String): ViewResponse<T> {
            val map = JsonImpl.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Invalid JSON")
            return ViewResponse(
                totalRows = map["total_rows"] as Int,
                offset = map["offset"] as Int,
                rows = ((map["rows"] as? List<*>)?.map { rowMap ->
                    val row = rowMap as Map<*, *>
                    ViewRow(
                        id = DocumentId(row["id"] as String),
                        key = row["key"] as Any,
                        value = row["value"] as T,
                        doc = (row["doc"] as? Map<*, *>)?.let { CouchDocument.fromJson(JsonImpl.stringify(it)) }
                    )
                } ?: emptyList()).toSeries()
            )
        }
    }
}

data class ViewRow<T>(
    val id: DocumentId,
    val key: Any,
    val value: T,
    val doc: CouchDocument? = null
)
