import kotlin.math.*
package borg.trikeshed.couchdb.assimilation
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * CouchDB Document Types - Borged from TypeScript
 * Original: ../superbikeshed/flatton/src/types/couchdb.ts
 */

// Type aliases
typealias DocumentId = String
typealias RevisionId = String
typealias DatabaseName = String

/**
 * CouchDB Document interface
 */
data class CouchDocument(
    val _id: DocumentId,
    val _rev: RevisionId? = null,
    val additionalProperties: Map<String, @Contextual Any> = emptyMap()
) {
    // Dynamic property access simulation
    operator fun get(key: String): Any? = when (key) {
        "_id" -> _id
        "_rev" -> _rev
        else -> additionalProperties[key]
    }
}

/**
 * CouchDB View definition
 */
data class CouchView(
    val map: String,
    val reduce: String? = null
)

/**
 * CouchDB Design Document
 */
data class CouchDesignDocument(
    val _id: DocumentId,
    val _rev: RevisionId? = null,
    val views: Map<String, CouchView>,
    val language: String = "javascript"
)

/**
 * CouchDB Database Information
 */
data class CouchDatabaseInfo(
    val db_name: DatabaseName,
    val doc_count: Int,
    val doc_del_count: Int,
    val update_seq: String,
    val purge_seq: String,
    val compact_running: Boolean,
    val disk_size: Long,
    val data_size: Long,
    val instance_start_time: String,
    val disk_format_version: Int,
    val committed_update_seq: String,
    val compacted_seq: String,
    val uuid: String
)

/**
 * CouchDB Security configuration
 */
data class CouchSecurity(
    val admins: CouchSecurityGroup,
    val members: CouchSecurityGroup
)

data class CouchSecurityGroup(
    val names: List<String>,
    val roles: List<String>
)

/**
 * Admin Party Mode configuration
 */
data class AdminPartyConfig(
    val enabled: Boolean,
    val admin_roles: List<String>
)

/**
 * CouchDB Response types
 */
data class CouchResponse(
    val ok: Boolean,
    val id: DocumentId,
    val rev: RevisionId
)

data class CouchError(
    val error: String,
    val reason: String
)

/**
 * View Query Parameters
 */
data class ViewQueryParams(
    val key: @Contextual Any? = null,
    val keys: List<@Contextual Any>? = null,
    val startkey: @Contextual Any? = null,
    val endkey: @Contextual Any? = null,
    val startkey_docid: DocumentId? = null,
    val endkey_docid: DocumentId? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val descending: Boolean? = null,
    val include_docs: Boolean? = null,
    val reduce: Boolean? = null,
    val group: Boolean? = null,
    val group_level: Int? = null
)

/**
 * View Response
 */
data class ViewResponse<T>(
    val total_rows: Int,
    val offset: Int,
    val rows: List<CouchViewRow<T>>
)

data class CouchViewRow<T>(
    val id: DocumentId,
    val key: @Contextual Any,
    val value: T,
    val doc: CouchDocument? = null
) 