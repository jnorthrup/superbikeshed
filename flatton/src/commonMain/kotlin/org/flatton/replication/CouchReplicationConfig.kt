package org.flatton.replication

import org.flatton.types.*
import org.flatton.client.CouchClient
import borg.trikeshed.lib.*

// Replication configuration types
@JvmInline value class ReplicationId(val value: String)
@JvmInline value class ReplicationState(val value: String)
@JvmInline value class ReplicationSource(val value: String)
@JvmInline value class ReplicationTarget(val value: String)

data class ReplicationConfig(
    val source: DatabaseName,
    val target: DatabaseName,
    val continuous: Boolean = false,
    val filter: String? = null,
    val queryParams: Map<String, String> = emptyMap(),
    val docIds: Indexed<DocumentId>? = null,
    val userContext: ReplicationUserContext? = null
)

data class ReplicationUserContext(
    val name: String,
    val roles: Indexed<String>
)

data class ReplicationStats(
    val docsRead: Long,
    val docsWritten: Long,
    val docWriteFailures: Long,
    val lastSeq: String,
    val startTime: String,
    val endTime: String? = null
)

data class ReplicationHistory(
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

data class ReplicationResponse(
    val ok: Boolean,
    val id: DocumentId,
    val history: Indexed<ReplicationHistory>? = null
)

// Extension functions for CouchClient
suspend fun CouchClient.startReplication(config: ReplicationConfig): ReplicationResponse {
    val doc = CouchDocument(
        _id = DocumentId("replication_${config.source.value}_${config.target.value}"),
        type = "replication",
        source = config.source.value,
        target = config.target.value,
        continuous = config.continuous,
        filter = config.filter,
        queryParams = config.queryParams,
        docIds = config.docIds?.map { it.value },
        userContext = config.userContext?.let { mapOf(
            "name" to it.name,
            "roles" to it.roles.toList()
        ) }
    )
    val response = createDocument(DatabaseName("_replicator"), doc)
    return ReplicationResponse(ok = response.ok, id = response.id)
}

suspend fun CouchClient.stopReplication(id: DocumentId): ReplicationResponse {
    val response = deleteDocument(DatabaseName("_replicator"), id, RevisionId("1-0"))
    return ReplicationResponse(ok = response.ok, id = response.id)
}

suspend fun CouchClient.getReplicationStatus(id: DocumentId): ReplicationResponse {
    val doc = getDocument(DatabaseName("_replicator"), id)
    val history = doc.history?.map { h ->
        ReplicationHistory(
            startTime = h.startTime,
            endTime = h.endTime,
            startLastSeq = h.startLastSeq,
            endLastSeq = h.endLastSeq,
            recordedSeq = h.recordedSeq,
            missingFound = h.missingFound,
            docsRead = h.docsRead,
            docsWritten = h.docsWritten,
            docWriteFailures = h.docWriteFailures,
            error = h.error
        )
    }
    return ReplicationResponse(ok = true, id = id, history = history?.let { Indexed.of(*it.toTypedArray()) })
} 