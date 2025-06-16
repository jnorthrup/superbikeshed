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

    private suspend fun <T> executeRequest(
        method: HttpMethod,
        path: String,
        body: String? = null,
        additionalHeaders: Map<HttpHeaderName, HttpHeaderValue> = emptyMap(),
        parser: (String) -> T
    ): T {
        val requestHeaders = headers + additionalHeaders
        val response = HttpRequest(
            method = method,
            path = HttpRequestPath(path),
            headers = requestHeaders,
            body = body
        ).send()

        if (!response.isSuccess) {
            throw CouchException("Request failed with status ${response.status}")
        }
        
        return parser(response.body.toString())
    }

    override suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo {
        return executeRequest(
            method = HttpMethod.GET,
            path = "/${dbName.value}",
            parser = CouchDatabaseInfo::fromJson
        )
    }

    override suspend fun createDatabase(dbName: DatabaseName): CouchResponse {
        return executeRequest(
            method = HttpMethod.PUT,
            path = "/${dbName.value}",
            parser = CouchResponse::fromJson
        )
    }

    override suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse {
        return executeRequest(
            method = HttpMethod.DELETE,
            path = "/${dbName.value}",
            parser = CouchResponse::fromJson
        )
    }

    override suspend fun getDocument(dbName: DatabaseName, docId: DocumentId): CouchDocument {
        return executeRequest(
            method = HttpMethod.GET,
            path = "/${dbName.value}/${docId.value}",
            parser = CouchDocument::fromJson
        )
    }

    override suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
        return executeRequest(
            method = HttpMethod.POST,
            path = "/${dbName.value}",
            body = doc.toJson(),
            parser = CouchResponse::fromJson
        )
    }

    override suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
        return executeRequest(
            method = HttpMethod.PUT,
            path = "/${dbName.value}/${doc.id.value}",
            body = doc.toJson(),
            parser = CouchResponse::fromJson
        )
    }

    override suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
        return executeRequest(
            method = HttpMethod.DELETE,
            path = "/${dbName.value}/${docId.value}?rev=${rev.value}",
            parser = CouchResponse::fromJson
        )
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
