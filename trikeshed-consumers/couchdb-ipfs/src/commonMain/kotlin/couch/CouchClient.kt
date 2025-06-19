package borg.trikeshed.consumers.couchdbipfs

import borg.trikeshed.lib.*

/**
 * CouchDB client interface.
 * Provides type-safe access to CouchDB operations.
 */
interface CouchClient {
    suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo
    suspend fun createDatabase(dbName: DatabaseName): CouchResponse
    suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse
    suspend fun getDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId? = null): CouchDocument
    suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument, docId: DocumentId? = null): CouchResponse
    suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse
    suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse
    suspend fun copyDocument(dbName: DatabaseName, fromId: DocumentId, toId: DocumentId, toRev: RevisionId? = null): CouchResponse
    suspend fun bulkDocs(dbName: DatabaseName, docs: Series<CouchDocument>, allOrNothing: Boolean = false): Series<CouchResponse>
    suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument
    suspend fun saveDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse
    suspend fun <K, V> queryView(dbName: DatabaseName, designDocId: DocumentId, viewName: ViewName, params: ViewQueryParams): ViewResponse<K, V>
    suspend fun getSecurity(dbName: DatabaseName): CouchSecurity
    suspend fun setSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse
} 