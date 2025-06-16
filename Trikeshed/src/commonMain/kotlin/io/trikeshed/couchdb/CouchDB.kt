package io.trikeshed.couchdb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CouchDBConfig(
    val url: String,
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class DatabaseInfo(
    val db_name: String,
    val doc_count: Int,
    val doc_del_count: Int,
    val update_seq: String,
    val purge_seq: Int,
    val compact_running: Boolean,
    val disk_size: Long,
    val data_size: Long,
    val instance_start_time: String,
    val disk_format_version: Int
)

@Serializable
data class Document(
    val _id: String,
    val _rev: String? = null,
    val _deleted: Boolean = false,
    val _attachments: Map<String, Attachment>? = null,
    val _conflicts: List<String>? = null,
    val _deleted_conflicts: List<String>? = null,
    val _local_seq: String? = null,
    val _revs_info: List<RevisionInfo>? = null,
    val _revisions: Revisions? = null
)

@Serializable
data class Attachment(
    val content_type: String,
    val data: String,
    val digest: String,
    val encoded_length: Int,
    val encoding: String,
    val length: Int,
    val revpos: Int,
    val stub: Boolean
)

@Serializable
data class RevisionInfo(
    val rev: String,
    val status: String
)

@Serializable
data class Revisions(
    val ids: List<String>,
    val start: Int
)

interface CouchDBClient {
    suspend fun getDatabases(): List<String>
    suspend fun getDatabaseInfo(dbName: String): DatabaseInfo
    suspend fun createDatabase(dbName: String): Boolean
    suspend fun deleteDatabase(dbName: String): Boolean
    suspend fun getDocument(dbName: String, docId: String): Document
    suspend fun saveDocument(dbName: String, document: Document): Document
    suspend fun deleteDocument(dbName: String, docId: String, rev: String): Boolean
    suspend fun query(dbName: String, query: Map<String, Any>): List<Document>
}

class CouchDBClientImpl(private val config: CouchDBConfig) : CouchDBClient {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Implementation will be platform specific
    override suspend fun getDatabases(): List<String> = TODO()
    override suspend fun getDatabaseInfo(dbName: String): DatabaseInfo = TODO()
    override suspend fun createDatabase(dbName: String): Boolean = TODO()
    override suspend fun deleteDatabase(dbName: String): Boolean = TODO()
    override suspend fun getDocument(dbName: String, docId: String): Document = TODO()
    override suspend fun saveDocument(dbName: String, document: Document): Document = TODO()
    override suspend fun deleteDocument(dbName: String, docId: String, rev: String): Boolean = TODO()
    override suspend fun query(dbName: String, query: Map<String, Any>): List<Document> = TODO()
} 