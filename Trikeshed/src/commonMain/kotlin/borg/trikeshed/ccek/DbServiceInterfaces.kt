package borg.trikeshed.ccek

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlin.coroutines.CoroutineContext

/**
 * Defines the CCEK services for database interaction,
 * creating a Kotlin-native reboot of RelaxFactory.
 */

// 1. The Context Element holding database connection details.
// Any coroutine with this in its context is implicitly configured to talk to this DB.
data class CouchDbContext(
    val baseUrl: String, // e.g., "http://localhost:5984"
    val dbName: String,
    val credentials: Pair<String, String>? = null // username, password
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<CouchDbContext>
}

// 2. A Pluggable JSON serialization service. This decouples the RelaxFactory
// from a specific JSON library.
interface JsonService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<JsonService>

    fun <T> toJson(value: T, serializer: KSerializer<T>): String
    fun <T> fromJson(json: String, serializer: KSerializer<T>): T
}

// 3. The main RelaxFactory service interface. This is the primary API for your application code.
// It's clean, direct, and fully asynchronous.
interface RelaxFactory : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<RelaxFactory>

    // --- Document Operations ---
    suspend fun <T> get(id: String, serializer: KSerializer<T>): T?
    suspend fun <T: Any> put(id: String, document: T, serializer: KSerializer<T>): CouchDbResponse
    suspend fun exists(id: String): Boolean

    // --- Database Operations ---
    suspend fun info(): String // Returns raw JSON info for simplicity

    // --- Advanced Operations ---
    fun <T> changesFlow(serializer: KSerializer<T>): Flow<T>
}

// A standard data class for CouchDB's success responses.
@kotlinx.serialization.Serializable
data class CouchDbResponse(
    val ok: Boolean,
    val id: String,
    val rev: String
) 