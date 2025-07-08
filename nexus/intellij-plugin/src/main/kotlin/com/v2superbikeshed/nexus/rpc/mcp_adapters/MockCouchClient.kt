package com.v2superbikeshed.nexus.rpc.mcp_adapters

import borg.trikeshed.couchdb.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class MockCouchClient : CouchClient {
    override suspend fun getServerInfo(): JsonObject {
        return JsonObject(mapOf("version" to JsonPrimitive("MockCouchDB-1.0")))
    }

    override suspend fun listDatabases(): Indexed<String> {
        return 1.j { "mock_db" }
    }

    override suspend fun createDatabase(name: String): CouchResponse {
        return CouchResponse.Success("Mock database $name created")
    }

    override suspend fun deleteDatabase(name: String): CouchResponse {
        return CouchResponse.Success("Mock database $name deleted")
    }

    override suspend fun getDatabaseInfo(name: String): CouchDatabaseInfo {
        return CouchDatabaseInfo("mock_db", 1, 1, 1, "mock_uuid")
    }

    override suspend fun getDocument(dbName: String, docId: String): CouchDocument? {
        return CouchDocument("mock_doc_id", "mock_rev", JsonObject(mapOf("data" to JsonPrimitive("mock_data"))))
    }

    override suspend fun putDocument(dbName: String, doc: CouchDocument): CouchResponse {
        return CouchResponse.Success("Mock document ${doc._id} put")
    }

    override suspend fun deleteDocument(dbName: String, docId: String, rev: String): CouchResponse {
        return CouchResponse.Success("Mock document $docId deleted")
    }

    override suspend fun bulkDocs(dbName: String, request: BulkDocsRequest): Indexed<CouchResponse> {
        return request.docs.size.j { CouchResponse.Success("Mock bulk doc processed") }
    }

    override suspend fun queryView(
        dbName: String,
        designDoc: String,
        viewName: String,
        params: ViewQueryParams
    ): ViewResponse<JsonElement, JsonElement> {
        return ViewResponse(0, 0, 0.j { ViewRow(JsonPrimitive("mock_key"), JsonPrimitive("mock_value")) })
    }

    override suspend fun getChanges(dbName: String, params: ChangesFeedParams): Flow<ChangesResult> {
        return flowOf(ChangesResult("mock_seq", "mock_id", emptyList(), false))
    }

    override suspend fun replicate(request: ReplicationRequest): ReplicationResponse {
        return ReplicationResponse("mock_session_id")
    }

    override suspend fun putDesignDocument(dbName: String, doc: DesignDocument): CouchResponse {
        return CouchResponse.Success("Mock design document put")
    }
}
