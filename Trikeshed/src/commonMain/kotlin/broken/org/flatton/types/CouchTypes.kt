package org.flatton.types

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline

// Core CouchDB types
@JvmInline value class DatabaseName(val value: String)
@JvmInline value class DocumentId(val value: String)
@JvmInline value class RevisionId(val value: String)
@JvmInline value class ViewName(val value: String)

// Document types
data class CouchDocument(
    val _id: DocumentId,
    val _rev: RevisionId? = null,
    val data: Map<String, Any?> = emptyMap()
) {
    fun copy(
        id: DocumentId = _id,
        rev: RevisionId? = _rev,
        data: Map<String, Any?> = this.data
    ): CouchDocument = CouchDocument(id, rev, data)
}

data class CouchDesignDocument(
    val _id: DocumentId,
    val _rev: RevisionId? = null,
    val language: String = "javascript",
    val views: Map<String, ViewDefinition> = emptyMap()
)

data class ViewDefinition(
    val map: String,
    val reduce: String? = null
)

// Response types
data class CouchResponse(
    val ok: Boolean,
    val id: DocumentId,
    val rev: RevisionId,
    val error: String? = null,
    val reason: String? = null
)

data class CouchDatabaseInfo(
    val dbName: DatabaseName,
    val docCount: Long,
    val updateSeq: String,
    val sizes: DatabaseSizes
)

data class DatabaseSizes(
    val file: Long,
    val external: Long,
    val active: Long
)

data class ViewQueryParams(
    val key: Any? = null,
    val startKey: Any? = null,
    val endKey: Any? = null,
    val limit: Int? = null,
    val skip: Int = 0,
    val descending: Boolean = false,
    val includeDocs: Boolean = false,
    val group: Boolean = false,
    val groupLevel: Int? = null,
    val reduce: Boolean = true,
    val stale: String? = null
)

data class ViewResponse<K, V>(
    val total_rows: Int,
    val offset: Int,
    val rows: Series<ViewRow<K, V>>
)

data class ViewRow<K, V>(
    val id: DocumentId,
    val key: K,
    val value: V,
    val doc: CouchDocument? = null
)

// Security types
data class CouchSecurity(
    val admins: SecurityMembers = SecurityMembers(),
    val members: SecurityMembers = SecurityMembers()
)

data class SecurityMembers(
    val names: List<String> = emptyList(),
    val roles: List<String> = emptyList()
) 