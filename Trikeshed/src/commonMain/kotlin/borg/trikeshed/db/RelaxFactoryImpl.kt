package borg.trikeshed.db

import borg.trikeshed.ccek.*
import borg.trikeshed.net.http.HttpClient
import borg.trikeshed.net.http.HttpMethod
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer

/**
 * The CCEK implementation of the RelaxFactory.
 * It uses other services (HttpClient, JsonService) and configuration (CouchDbContext)
 * from its coroutineContext to perform its duties.
 */
class RelaxFactoryImpl : RelaxFactory {

    override suspend fun <T> get(id: String, serializer: KSerializer<T>): T? {
        // All logic is wrapped in coroutineScope to get access to the context.
        return coroutineScope {
            // Gracefully get required services from the context. Fail fast if they're missing.
            val dbContext = coroutineContext[CouchDbContext.Key] ?: error("CouchDbContext not found in context")
            val httpClient = coroutineContext[HttpClient.Key] ?: error("HttpClient service not found in context")
            val json = coroutineContext[JsonService.Key] ?: error("JsonService not found in context")

            val url = "${dbContext.baseUrl}/${dbContext.dbName}/$id"
            val response = httpClient.request(url, HttpMethod.GET)

            if (response.status == 404) {
                null
            } else {
                json.fromJson(response.body, serializer)
            }
        }
    }

    override suspend fun <T : Any> put(id: String, document: T, serializer: KSerializer<T>): CouchDbResponse {
        return coroutineScope {
            val dbContext = coroutineContext[CouchDbContext.Key] ?: error("CouchDbContext not found in context")
            val httpClient = coroutineContext[HttpClient.Key] ?: error("HttpClient service not found in context")
            val json = coroutineContext[JsonService.Key] ?: error("JsonService not found in context")

            val url = "${dbContext.baseUrl}/${dbContext.dbName}/$id"
            val jsonBody = json.toJson(document, serializer)
            val response = httpClient.request(url, HttpMethod.PUT, body = jsonBody)

            // For now, create a simple response. In a real implementation, 
            // we would properly deserialize the CouchDB response
            CouchDbResponse(ok = response.status == 201, id = id, rev = "1-abc")
        }
    }

    override suspend fun exists(id: String): Boolean {
        return coroutineScope {
            val dbContext = coroutineContext[CouchDbContext.Key] ?: error("CouchDbContext not found in context")
            val httpClient = coroutineContext[HttpClient.Key] ?: error("HttpClient service not found in context")
            val url = "${dbContext.baseUrl}/${dbContext.dbName}/$id"
            // A HEAD request is more efficient for existence checks
            val response = httpClient.request(url, HttpMethod.HEAD)
            response.status != 404
        }
    }
    
    override suspend fun info(): String {
        return coroutineScope {
            val dbContext = coroutineContext[CouchDbContext.Key] ?: error("CouchDbContext not found in context")
            val httpClient = coroutineContext[HttpClient.Key] ?: error("HttpClient service not found in context")
            val url = "${dbContext.baseUrl}/${dbContext.dbName}"
            httpClient.request(url, HttpMethod.GET).body
        }
    }

    override fun <T> changesFlow(serializer: KSerializer<T>): Flow<T> = flow {
        // A real implementation would use a long-polling or continuous HTTP connection
        // to CouchDB's _changes feed. This is a placeholder for that logic.
        println("Pretending to listen to the changes feed...")
        // In a real scenario, you'd emit documents as they change in the DB.
    }
} 