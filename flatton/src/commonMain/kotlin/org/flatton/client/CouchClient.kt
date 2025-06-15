package org.flatton.client

import org.flatton.types.*

interface CouchClient {
    suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo
    suspend fun createDatabase(dbName: DatabaseName): CouchResponse
    suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse
    
    suspend fun getDocument(dbName: DatabaseName, docId: DocumentId): CouchDocument
    suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse
    suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse
    suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse
    
    suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument
    suspend fun createDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse
    suspend fun updateDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse
    suspend fun deleteDesignDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse
    
    suspend fun queryView<T>(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams = ViewQueryParams()
    ): ViewResponse<T>
    
    suspend fun getSecurity(dbName: DatabaseName): CouchSecurity
    suspend fun updateSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse
    
    suspend fun enableAdminParty(config: AdminPartyConfig): CouchResponse
    suspend fun disableAdminParty(): CouchResponse
    suspend fun getAdminPartyConfig(): AdminPartyConfig
} 