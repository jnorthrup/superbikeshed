package borg.trikeshed.db

import borg.trikeshed.net.http.HttpClient
import borg.trikeshed.net.http.HttpMethod

/**
 * A fake HTTP client for testing the RelaxFactory implementation.
 * This simulates CouchDB responses for development and testing.
 */
class FakeHttpClient : HttpClient {
    override suspend fun request(url: String, method: HttpMethod, body: String?): HttpClient.HttpResponse {
        println("HttpClient: Making $method request to $url")
        // Simulate responses for the demo
        return when {
            method == HttpMethod.GET && url.endsWith("/test-doc") ->
                HttpClient.HttpResponse(200, """{"name":"Trike","wheels":3}""")
            method == HttpMethod.PUT ->
                HttpClient.HttpResponse(201, """{"ok":true, "id":"test-doc", "rev":"1-abc"}""")
            method == HttpMethod.HEAD ->
                HttpClient.HttpResponse(200, "") // Document exists
            method == HttpMethod.GET && url.endsWith("/trikeshed-db") ->
                HttpClient.HttpResponse(200, """{"db_name":"trikeshed-db","doc_count":0,"update_seq":"0"}""")
            else -> HttpClient.HttpResponse(404, """{"error":"not_found"}""")
        }
    }
} 