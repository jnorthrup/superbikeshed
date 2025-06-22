package borg.trikeshed.net.http

import kotlin.coroutines.CoroutineContext

/**
 * A generic HTTP Client Service for the CCEK system.
 * This provides a clean abstraction for HTTP operations.
 */
interface HttpClient : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<HttpClient>
    
    suspend fun request(url: String, method: HttpMethod, body: String? = null): HttpResponse
    
    data class HttpResponse(
        val status: Int, 
        val body: String,
        val headers: Map<String, String> = emptyMap()
    )
} 