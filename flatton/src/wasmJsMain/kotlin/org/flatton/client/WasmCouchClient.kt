package org.flatton.client

import org.flatton.types.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import borg.trikeshed.parse.json.*

class WasmCouchClient(
    private val baseUrl: String = "http://127.0.0.1:5984" // Default to localhost
) : CouchClient {
    private val headers = mapOf(
        HttpHeaderName("Content-Type") to HttpHeaderValue("application/json"),
        HttpHeaderName("Accept") to HttpHeaderValue("application/json")
    )

    private suspend fun <T> handleResponse(response: HttpResponse, onSuccess: (String) -> T): T {
        if (!response.isSuccess) {
            val errorBody = response.body.toString()
            throw CouchException("CouchDB request failed with status ${response.status.value}")
        }
        return onSuccess(response.body.toString())
    }

    override suspend fun getDatabaseInfo(dbName: DatabaseName): CouchDatabaseInfo {
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/${dbName.value}"),
            headers = headers
        ).send()
        return handleResponse(response) { CouchDatabaseInfoAdapter.fromJson(it) }
    }

    override suspend fun createDatabase(dbName: DatabaseName): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}"),
            headers = headers
        ).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }

    override suspend fun deleteDatabase(dbName: DatabaseName): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/${dbName.value}"),
            headers = headers
        ).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }

    override suspend fun getDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId?): CouchDocument {
        val path = "/${dbName.value}/${docId.value}" + if (rev != null) "?rev=${rev.value}" else ""
        val response = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath(path),
            headers = headers
        ).send()
        return handleResponse(response) { CouchDocumentAdapter.fromJson(it) }
    }

    override suspend fun createDocument(dbName: DatabaseName, doc: CouchDocument, docId: DocumentId?): CouchResponse {
        val method = if (docId != null) HttpMethod.PUT else HttpMethod.POST
        val path = if (docId != null) "/${dbName.value}/${docId.value}" else "/${dbName.value}"

        val response = HttpRequest(
            method = method,
            path = HttpRequestPath(path),
            headers = headers,
            body = CouchDocumentAdapter.toJson(doc)
        ).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }

    override suspend fun updateDocument(dbName: DatabaseName, doc: CouchDocument): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}/${doc._id.value}"),
            headers = headers,
            body = CouchDocumentAdapter.toJson(doc)
        ).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }

    override suspend fun deleteDocument(dbName: DatabaseName, docId: DocumentId, rev: RevisionId): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.DELETE,
            path = HttpRequestPath("/${dbName.value}/${docId.value}?rev=${rev.value}"),
            headers = headers
        ).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }

    override suspend fun copyDocument(dbName: DatabaseName, fromId: DocumentId, toId: DocumentId, toRev: RevisionId?): CouchResponse {
        val destination = toId.value + if (toRev != null) "?rev=${toRev.value}" else ""
        val copyHeaders = headers + (HttpHeaderName("Destination") to HttpHeaderValue(destination))
        val response = HttpRequest(
            method = HttpMethod.COPY,
            path = HttpRequestPath("/${dbName.value}/${fromId.value}"),
            headers = copyHeaders
        ).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }

    override suspend fun bulkDocs(dbName: DatabaseName, docs: Series<CouchDocument>, allOrNothing: Boolean): Series<CouchResponse> {
        val body = JsonImpl.stringify(mapOf(
            "docs" to docs.play.toList().map { JsonImpl.parse(CouchDocumentAdapter.toJson(it)) },
            "all_or_nothing" to allOrNothing
        ))
        val response = HttpRequest(
            method = HttpMethod.POST,
            path = HttpRequestPath("/${dbName.value}/_bulk_docs"),
            headers = headers,
            body = body
        ).send()
        return handleResponse(response) {
            val parsed = JsonImpl.parse(it) as? List<*> ?: emptyList<Map<String, Any?>>()
            val responses = parsed.size j { idx:Int ->
                val item = parsed[idx] as Map<String, Any?>
                CouchResponse(
                    ok = item["ok"] as? Boolean ?: (item["error"] == null),
                    id = (item["id"] as? String)?.let { idVal -> DocumentId(idVal) },
                    rev = (item["rev"] as? String)?.let { revVal -> RevisionId(revVal) },
                    error = item["error"] as? String,
                    reason = item["reason"] as? String
                )
            }
            responses
        }
    }

    override suspend fun getDesignDocument(dbName: DatabaseName, docId: DocumentId): CouchDesignDocument {
        val response = getDocument(dbName, docId)
        return CouchDesignDocumentAdapter.fromJson(CouchDocumentAdapter.toJson(response))
    }

    override suspend fun saveDesignDocument(dbName: DatabaseName, doc: CouchDesignDocument): CouchResponse {
        val response = HttpRequest(
            method = HttpMethod.PUT,
            path = HttpRequestPath("/${dbName.value}/${doc.id.asDocId().value}"),
            headers = headers,
            body = CouchDesignDocumentAdapter.toJson(doc)
        ).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }

    override suspend fun <K, V> queryView(dbName: DatabaseName, designDocId: DocumentId, viewName: ViewName, params: ViewQueryParams): ViewResponse<K, V> {
        val path = "/${dbName.value}/_design/${designDocId.value}/_view/${viewName.value}${params.toQueryString()}"
        val response = HttpRequest(method = HttpMethod.GET, path = HttpRequestPath(path), headers = headers).send()
        return handleResponse(response) { 
            TODO("ViewResponse parsing needs to be implemented with proper type handling")
        }
    }

    override suspend fun getSecurity(dbName: DatabaseName): CouchSecurity {
        val response = HttpRequest(method = HttpMethod.GET, path = HttpRequestPath("/${dbName.value}/_security"), headers = headers).send()
        return handleResponse(response) { CouchSecurityAdapter.fromJson(it) }
    }

    override suspend fun setSecurity(dbName: DatabaseName, security: CouchSecurity): CouchResponse {
        val response = HttpRequest(method = HttpMethod.PUT, path = HttpRequestPath("/${dbName.value}/_security"), headers = headers, body = CouchSecurityAdapter.toJson(security)).send()
        return handleResponse(response) { CouchResponseAdapter.fromJson(it) }
    }
}

class CouchException(message: String, val error: String? = null, val reason: String? = null) : Exception(message)