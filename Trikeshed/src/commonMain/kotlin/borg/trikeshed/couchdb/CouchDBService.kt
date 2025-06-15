package borg.trikeshed.couchdb

import borg.trikeshed.lib.ByteSeries
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface CouchDBService {
    suspend fun createDatabase(name: String): Boolean
    suspend fun deleteDatabase(name: String): Boolean
    suspend fun <T : @Serializable Any> createDocument(database: String, doc: T): String
    suspend fun <T : @Serializable Any> getDocument(database: String, id: String, type: Class<T>): T?
    suspend fun <T : @Serializable Any> updateDocument(database: String, id: String, doc: T): Boolean
    suspend fun deleteDocument(database: String, id: String): Boolean
    suspend fun query(database: String, query: String): List<ByteSeries>
}

@Serializable
data class CouchDBConfig(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val useSSL: Boolean = false
) 