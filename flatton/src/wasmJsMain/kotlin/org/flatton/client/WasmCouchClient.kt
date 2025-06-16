package org.flatton.client

import org.flatton.types.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import borg.trikeshed.parse.json.*

class WasmCouchClient(
    private val baseUrl: String = "http://localhost:5984"
) : CouchClient {
    private val headers = mapOf(
        HttpHeaderName("Content-Type") to HttpHeaderValue("application/json"),
        HttpHeaderName("Accept") to HttpHeaderValue("application/json")
    )

    override suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo {
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/${dbName.value}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to get database info")
        return CouchDatabaseInfo.fromJson(response.body.toString())
    }

    override suspend fun createDatabase(dbName: DatabaseName): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to create database")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/${dbName.value}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to delete database")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun getDocument(dbName: DatabaseName, docId: DocumentId): CouchDocument {
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/${dbName.value}/${docId.value}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to get document")
        return CouchDocument.fromJson(response.body.toString())
    }

    override suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("/${dbName.value}"),
            headers = headers,
            body = doc.toJson()
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to create document")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}/${doc.id.value}"),
            headers = headers,
            body = doc.toJson()
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to update document")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/${dbName.value}/${docId.value}?rev=${rev.value}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to delete document")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument {
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/${dbName.value}/_design/${docId.value}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to get design document")
        return CouchDesignDocument.fromJson(response.body.toString())
    }

    override suspend fun createDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}/_design/${doc.id.value}"),
            headers = headers,
            body = doc.toJson()
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to create design document")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun updateDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}/_design/${doc.id.value}"),
            headers = headers,
            body = doc.toJson()
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to update design document")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun deleteDesignDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/${dbName.value}/_design/${docId.value}?rev=${rev.value}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to delete design document")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun <T> queryView(
        dbName: DatabaseName,
        designDocId: DocumentId,
        viewName: ViewName,
        params: ViewQueryParams
    ): ViewResponse<T> {
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/${dbName.value}/_design/${designDocId.value}/_view/${viewName.value}${params.toQueryString()}"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to query view")
        return ViewResponse.fromJson(response.body.toString())
    }

    override suspend fun getSecurity(dbName: DatabaseName): CouchSecurity {
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/${dbName.value}/_security"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to get security")
        return CouchSecurity.fromJson(response.body.toString())
    }

    override suspend fun updateSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}/_security"),
            headers = headers,
            body = security.toJson()
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to update security")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun enableAdminParty(config: AdminPartyConfig): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/_node/_local/_config/admins"),
            headers = headers,
            body = config.toJson()
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to enable admin party")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun disableAdminParty(): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/_node/_local/_config/admins"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to disable admin party")
        return CouchResponse.fromJson(response.body.toString())
    }

    override suspend fun getAdminPartyConfig(): AdminPartyConfig {
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/_node/_local/_config/admins"),
            headers = headers
        ).send()

        if (!response.isSuccess) throw CouchException("Failed to get admin party config")
        return AdminPartyConfig.fromJson(response.body.toString())
    }
}

class CouchException(message: String) : Exception(message) 