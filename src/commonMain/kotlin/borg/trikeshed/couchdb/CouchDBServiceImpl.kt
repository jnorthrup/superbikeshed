package borg.trikeshed.couchdb

import borg.trikeshed.lib.ByteSeries
import borg.trikeshed.net.http.client.HttpClient
import borg.trikeshed.net.http.client.HttpRequest
import borg.trikeshed.net.http.client.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CouchDBServiceImpl(
    private val config: CouchDBConfig,
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : CouchDBService {

    private val baseUrl = buildString {
        append(if (config.useSSL) "https://" else "http://")
        append(config.host)
        append(":")
        append(config.port)
    }

    override suspend fun createDatabase(name: String): Boolean {
        val response = httpClient.request(
            HttpRequest(
                method = "PUT",
                url = "$baseUrl/$name",
                headers = getAuthHeaders()
            )
        )
        return response.statusCode == 201
    }

    override suspend fun deleteDatabase(name: String): Boolean {
        val response = httpClient.request(
            HttpRequest(
                method = "DELETE",
                url = "$baseUrl/$name",
                headers = getAuthHeaders()
            )
        )
        return response.statusCode == 200
    }

    override suspend fun <T : Any> createDocument(database: String, doc: T): String {
        val response = httpClient.request(
            HttpRequest(
                method = "POST",
                url = "$baseUrl/$database",
                headers = getAuthHeaders(),
                body = json.encodeToString(doc)
            )
        )
        return parseDocumentId(response)
    }

    override suspend fun <T : Any> getDocument(database: String, id: String, type: Class<T>): T? {
        val response = httpClient.request(
            HttpRequest(
                method = "GET",
                url = "$baseUrl/$database/$id",
                headers = getAuthHeaders()
            )
        )
        return if (response.statusCode == 200) {
            json.decodeFromString(response.body.toString())
        } else null
    }

    override suspend fun <T : Any> updateDocument(database: String, id: String, doc: T): Boolean {
        val response = httpClient.request(
            HttpRequest(
                method = "PUT",
                url = "$baseUrl/$database/$id",
                headers = getAuthHeaders(),
                body = json.encodeToString(doc)
            )
        )
        return response.statusCode == 201
    }

    override suspend fun deleteDocument(database: String, id: String): Boolean {
        val response = httpClient.request(
            HttpRequest(
                method = "DELETE",
                url = "$baseUrl/$database/$id",
                headers = getAuthHeaders()
            )
        )
        return response.statusCode == 200
    }

    override suspend fun query(database: String, query: String): List<ByteSeries> {
        val response = httpClient.request(
            HttpRequest(
                method = "POST",
                url = "$baseUrl/$database/_find",
                headers = getAuthHeaders(),
                body = query
            )
        )
        return if (response.statusCode == 200) {
            val jsonResponse = json.parseToJsonElement(response.body.toString()).jsonObject
            jsonResponse["docs"]?.let { docs ->
                docs.jsonArray.map { doc ->
                    ByteSeries(doc.toString().toByteArray())
                }
            } ?: emptyList()
        } else emptyList()
    }

    private fun getAuthHeaders(): Map<String, String> {
        return buildMap {
            put("Content-Type", "application/json")
            if (config.username != null && config.password != null) {
                put("Authorization", "Basic ${encodeBasicAuth(config.username, config.password)}")
            }
        }
    }

    private fun parseDocumentId(response: HttpResponse): String {
        val jsonResponse = json.parseToJsonElement(response.body.toString()).jsonObject
        return jsonResponse["id"]?.jsonPrimitive?.content ?: throw IllegalStateException("No document ID in response")
    }

    private fun encodeBasicAuth(username: String, password: String): String {
        val credentials = "$username:$password"
        return Base64.getEncoder().encodeToString(credentials.toByteArray())
    }
} 