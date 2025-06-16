package io.trikeshed.couchdb

import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.js.json

class CouchDBClientJs(config: CouchDBConfig) : CouchDBClientImpl(config) {
    private val baseUrl = config.url.trimEnd('/')
    private val headers = json(
        "Accept" to "application/json",
        "Content-Type" to "application/json"
    ).apply {
        if (config.username != null && config.password != null) {
            val auth = "${config.username}:${config.password}"
            val encoded = kotlinx.browser.window.btoa(auth)
            set("Authorization", "Basic $encoded")
        }
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null
    ): Response {
        val init = RequestInit(
            method = method,
            headers = headers,
            body = body
        )
        
        return kotlinx.browser.window.fetch("$baseUrl/$path", init).await()
    }

    override suspend fun getDatabases(): List<String> {
        val response = request("GET", "_all_dbs")
        return Json.decodeFromString<List<String>>(response.text().await())
    }

    override suspend fun getDatabaseInfo(dbName: String): DatabaseInfo {
        val response = request("GET", dbName)
        return Json.decodeFromString(response.text().await())
    }

    override suspend fun createDatabase(dbName: String): Boolean {
        val response = request("PUT", dbName)
        return response.ok
    }

    override suspend fun deleteDatabase(dbName: String): Boolean {
        val response = request("DELETE", dbName)
        return response.ok
    }

    override suspend fun getDocument(dbName: String, docId: String): Document {
        val response = request("GET", "$dbName/$docId")
        return Json.decodeFromString(response.text().await())
    }

    override suspend fun saveDocument(dbName: String, document: Document): Document {
        val body = Json.encodeToString(Document.serializer(), document)
        val response = request("PUT", "$dbName/${document._id}", body)
        return Json.decodeFromString(response.text().await())
    }

    override suspend fun deleteDocument(dbName: String, docId: String, rev: String): Boolean {
        val response = request("DELETE", "$dbName/$docId?rev=$rev")
        return response.ok
    }

    override suspend fun query(dbName: String, query: Map<String, Any>): List<Document> {
        val body = Json.encodeToString(Json.serializersModule.serializer(), query)
        val response = request("POST", "$dbName/_find", body)
        val result = Json.decodeFromString<QueryResult>(response.text().await())
        return result.docs
    }
}

@kotlinx.serialization.Serializable
private data class QueryResult(
    val docs: List<Document>
) 