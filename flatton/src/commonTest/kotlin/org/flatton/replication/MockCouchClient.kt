package org.flatton.replication

import org.flatton.types.*
import org.flatton.client.CouchClient
import borg.trikeshed.lib.*

class MockCouchClient : CouchClient {
    private val documents = mutableMapOf<String, CouchDocument>()
    private val replicationDocs = mutableMapOf<String, CouchDocument>()

    override suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo {
        TODO("Not implemented")
    }

    override suspend fun createDatabase(dbName: DatabaseName): CouchResponse {
        TODO("Not implemented")
    }

    override suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse {
        TODO("Not implemented")
    }

    override suspend fun getDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId?): CouchDocument {
        return documents[docId.value] ?: throw RuntimeException("Document not found")
    }

    override suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument, docId: DocumentId?): CouchResponse {
        val id = docId?.value ?: doc._id.value
        if (doc.type == "replication") {
            replicationDocs[id] = doc
        } else {
            documents[id] = doc
        }
        return CouchResponse(ok = true, id = DocumentId(id))
    }

    override suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
        val id = doc._id.value
        if (doc.type == "replication") {
            replicationDocs[id] = doc
        } else {
            documents[id] = doc
        }
        return CouchResponse(ok = true, id = doc._id)
    }

    override suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
        replicationDocs.remove(docId.value)
        documents.remove(docId.value)
        return CouchResponse(ok = true, id = docId)
    }

    override suspend fun copyDocument(dbName: DatabaseName, fromId: DocumentId, toId: DocumentId, toRev: RevisionId?): CouchResponse {
        TODO("Not implemented")
    }

    override suspend fun bulkDocs(dbName: DatabaseName, docs: Series<CouchDocument>, allOrNothing: Boolean): Series<CouchResponse> {
        TODO("Not implemented")
    }

    override suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument {
        TODO("Not implemented")
    }

    override suspend fun saveDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse {
        TODO("Not implemented")
    }

    override suspend fun <K, V> queryView(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams
    ): ViewResponse<K, V> {
        TODO("Not implemented")
    }

    override suspend fun getSecurity(dbName: DatabaseName): CouchSecurity {
        TODO("Not implemented")
    }

    override suspend fun setSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse {
        TODO("Not implemented")
    }

    fun getReplicationDoc(id: String): CouchDocument? = replicationDocs[id]
} 