package borg.trikeshed.couchdb

import borg.trikeshed.lib.*
import kotlinx.serialization.*

/**
 * CouchDB Taxonomical Typealias Specification
 * Leverages coreTypes and establishes end context keys for consistency
 */

// === CORE TYPE ALIASES ===
typealias CouchDocumentId = String
typealias CouchRevision = String
typealias CouchDatabaseName = String
typealias CouchViewName = String
typealias CouchDesignDocName = String
typealias CouchAttachmentName = String
typealias CouchContentType = String
typealias CouchQueryString = String
typealias CouchFilterName = String

// === ENUM-BASED TAXONOMICAL CLASSIFICATION ===

/**
 * CouchDB Operation Types
 */
enum class CouchOperationType {
    CREATE_DATABASE,
    DELETE_DATABASE,
    CREATE_DOCUMENT,
    UPDATE_DOCUMENT,
    GET_DOCUMENT,
    DELETE_DOCUMENT,
    BULK_OPERATION,
    REPLICATION,
    VIEW_QUERY,
    ATTACHMENT_OPERATION,
    SECURITY_OPERATION,
    CHANGES_FEED
}

/**
 * CouchDB Result Status Types
 */
enum class CouchResultStatus {
    SUCCESS,
    ERROR,
    NOT_FOUND,
    CONFLICT,
    UNAUTHORIZED,
    FORBIDDEN,
    TIMEOUT
}

/**
 * CouchDB Document States
 */
enum class CouchDocumentState {
    ACTIVE,
    DELETED,
    CONFLICTED,
    MISSING
}

/**
 * CouchDB Replication States
 */
enum class CouchReplicationState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * CouchDB Security Roles
 */
enum class CouchSecurityRole {
    ADMIN,
    MEMBER,
    READER,
    WRITER
}

// === CORE DATA STRUCTURES ===

/**
 * Core CouchDB Document with taxonomical classification
 */
@Serializable
data class CouchDocument(
    val _id: CouchDocumentId = "",
    val _rev: CouchRevision = "",
    val _deleted: Boolean = false,
    val data: Map<String, String> = emptyMap()
) {
    val state: CouchDocumentState
        get() = when {
            _deleted -> CouchDocumentState.DELETED
            _id.isEmpty() -> CouchDocumentState.MISSING
            else -> CouchDocumentState.ACTIVE
        }
}

/**
 * Core CouchDB Result with operation type classification
 */
sealed class CouchResult(val operationType: CouchOperationType) {
    data class Success(
        val message: String,
        val operationType: CouchOperationType = CouchOperationType.CREATE_DATABASE
    ) : CouchResult(operationType)
    
    data class Error(
        val message: String,
        val status: CouchResultStatus = CouchResultStatus.ERROR,
        val operationType: CouchOperationType = CouchOperationType.CREATE_DATABASE
    ) : CouchResult(operationType)
}

/**
 * Core CouchDB Document Result with state classification
 */
typealias CouchDocumentResult = Either<String, CouchDocumentData>

@Serializable
data class CouchDocumentData(
    val id: CouchDocumentId,
    val rev: CouchRevision,
    val data: Map<String, String> = emptyMap()
) {
    val state: CouchDocumentState
        get() = when {
            id.isEmpty() -> CouchDocumentState.MISSING
            else -> CouchDocumentState.ACTIVE
        }
}

// === OPERATION-SPECIFIC TYPES ===

/**
 * Bulk operation request with operation type classification
 */
@Serializable
data class CouchBulkRequest(
    val docs: List<CouchDocument>,
    val operationType: CouchOperationType = CouchOperationType.BULK_OPERATION
)

/**
 * Bulk operation result with status classification
 */
@Serializable
data class CouchBulkResult(
    val ok: Boolean,
    val id: CouchDocumentId,
    val rev: CouchRevision,
    val error: String? = null,
    val reason: String? = null
) {
    val status: CouchResultStatus
        get() = when {
            ok -> CouchResultStatus.SUCCESS
            error?.contains("conflict", ignoreCase = true) == true -> CouchResultStatus.CONFLICT
            error?.contains("not found", ignoreCase = true) == true -> CouchResultStatus.NOT_FOUND
            else -> CouchResultStatus.ERROR
        }
}

/**
 * Replication request with state classification
 */
@Serializable
data class CouchReplicationRequest(
    val source: CouchDatabaseName,
    val target: CouchDatabaseName,
    val continuous: Boolean = false,
    val createTarget: Boolean = false,
    val operationType: CouchOperationType = CouchOperationType.REPLICATION
)

/**
 * Replication result with state classification
 */
@Serializable
data class CouchReplicationResult(
    val ok: Boolean,
    val sessionId: String
) {
    val state: CouchReplicationState
        get() = when {
            ok -> CouchReplicationState.COMPLETED
            else -> CouchReplicationState.FAILED
        }
}

/**
 * View parameters with operation type classification
 */
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
    val groupLevel: Int? = null,
    val operationType: CouchOperationType = CouchOperationType.VIEW_QUERY
)

/**
 * View result with operation type classification
 */
@Serializable
data class CouchViewResult(
    val totalRows: Int,
    val offset: Int,
    val rows: List<CouchViewRow>,
    val operationType: CouchOperationType = CouchOperationType.VIEW_QUERY
)

@Serializable
data class CouchViewRow(
    val id: CouchDocumentId,
    val key: String,
    val value: String,
    val doc: CouchDocumentData? = null
)

/**
 * Changes feed with operation type classification
 */
@Serializable
data class CouchChange(
    val seq: String,
    val id: CouchDocumentId,
    val changes: List<CouchChangeRev>,
    val deleted: Boolean = false,
    val doc: CouchDocumentData? = null,
    val operationType: CouchOperationType = CouchOperationType.CHANGES_FEED
)

@Serializable
data class CouchChangeRev(
    val rev: CouchRevision
)

/**
 * Security configuration with role classification
 */
@Serializable
data class CouchSecurity(
    val admins: SecurityObject = SecurityObject(),
    val members: SecurityObject = SecurityObject(),
    val operationType: CouchOperationType = CouchOperationType.SECURITY_OPERATION
) {
    @Serializable
    data class SecurityObject(
        val names: List<String> = emptyList(),
        val roles: List<String> = emptyList()
    ) {
        fun getRoles(): List<CouchSecurityRole> = roles.mapNotNull { role ->
            CouchSecurityRole.values().find { it.name.equals(role, ignoreCase = true) }
        }
    }
}

/**
 * Database information with state classification
 */
@Serializable
data class CouchDatabaseInfo(
    val dbName: CouchDatabaseName,
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
) {
    val state: CouchDocumentState
        get() = when {
            docCount == 0L -> CouchDocumentState.MISSING
            else -> CouchDocumentState.ACTIVE
        }
}

/**
 * Put operation result with status classification
 */
@Serializable
data class CouchPutResult(
    val ok: Boolean,
    val id: CouchDocumentId,
    val rev: CouchRevision
) {
    val status: CouchResultStatus
        get() = when {
            ok -> CouchResultStatus.SUCCESS
            else -> CouchResultStatus.ERROR
        }
}

// === END CONTEXT KEYS ===

/**
 * End context keys for CouchDB operations
 */
object CouchContextKeys {
    const val OPERATION_TYPE = "couch_operation_type"
    const val RESULT_STATUS = "couch_result_status"
    const val DOCUMENT_STATE = "couch_document_state"
    const val REPLICATION_STATE = "couch_replication_state"
    const val SECURITY_ROLE = "couch_security_role"
    const val DATABASE_NAME = "couch_database_name"
    const val DOCUMENT_ID = "couch_document_id"
    const val REVISION = "couch_revision"
} 