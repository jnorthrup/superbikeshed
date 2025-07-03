package org.flatton.client

import org.flatton.types.*
import borg.trikeshed.lib.Indexed

interface CouchClient {
    suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo
    suspend fun createDatabase(dbName: DatabaseName): CouchResponse
    suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse

    suspend fun getDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId? = null): CouchDocument
    suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument, docId: DocumentId? = null): CouchResponse
    suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse
    suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse
    suspend fun copyDocument(dbName: DatabaseName, fromId: DocumentId, toId: DocumentId, toRev: RevisionId? = null): CouchResponse
    suspend fun bulkDocs(dbName: DatabaseName, docs: Indexed<CouchDocument>, allOrNothing: Boolean = false): Indexed<CouchResponse>

    suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument
    suspend fun saveDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse

    suspend fun <K, V> queryView(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams()
    ): ViewResponse<K, V>

    suspend fun getSecurity(dbName: DatabaseName): CouchSecurity
    suspend fun setSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse
}