package nexus.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nexus.core.DefaultNexusAgent
import nexus.core.LogLevel
import nexus.server.QuicStream
import nexus.server.StreamDirection

/**
 * CouchDB-compatible API layer for the RelaxFactory server.
 * 
 * This implements the standard CouchDB document API endpoints:
 * - GET /db/docid - Retrieve document
 * - PUT /db/docid - Create/update document
 * - DELETE /db/docid - Delete document
 * - GET /db/_all_docs - List all documents
 * - POST /db/_bulk_docs - Bulk operations
 * - GET /db/_changes - Changes feed
 */
class CouchDbApi(
    private val agent: DefaultNexusAgent,
    private val ipfsBridge: IpfsBridge
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true 
    }
    
    /**
     * Handles incoming HTTP requests and routes them to appropriate handlers
     */
    suspend fun handleRequest(stream: QuicStream): CouchDbResponse {
        return try {
            val request = parseRequest(stream)
            val response = routeRequest(request)
            
            agent.logLevel.takeIf { it <= LogLevel.DEBUG }?.let {
                println("CouchDB API: ${request.method} ${request.path} -> ${response.status}")
            }
            
            response
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("CouchDB API Error: ${e.message}")
            }
            CouchDbResponse(
                status = 500,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbError.serializer(),
                    CouchDbError("Internal Server Error", e.message ?: "Unknown error")
                )
            )
        }
    }
    
    private suspend fun routeRequest(request: CouchDbRequest): CouchDbResponse {
        val pathParts = request.path.split("/").filter { it.isNotEmpty() }
        
        return when {
            // GET /db/docid
            request.method == "GET" && pathParts.size == 2 -> {
                val dbName = pathParts[0]
                val docId = pathParts[1]
                getDocument(dbName, docId, request.queryParams)
            }
            
            // PUT /db/docid
            request.method == "PUT" && pathParts.size == 2 -> {
                val dbName = pathParts[0]
                val docId = pathParts[1]
                putDocument(dbName, docId, request.body)
            }
            
            // DELETE /db/docid
            request.method == "DELETE" && pathParts.size == 2 -> {
                val dbName = pathParts[0]
                val docId = pathParts[1]
                deleteDocument(dbName, docId, request.queryParams)
            }
            
            // GET /db/_all_docs
            request.method == "GET" && pathParts.size == 2 && pathParts[1] == "_all_docs" -> {
                val dbName = pathParts[0]
                getAllDocuments(dbName, request.queryParams)
            }
            
            // POST /db/_bulk_docs
            request.method == "POST" && pathParts.size == 2 && pathParts[1] == "_bulk_docs" -> {
                val dbName = pathParts[0]
                bulkDocuments(dbName, request.body)
            }
            
            // GET /db/_changes
            request.method == "GET" && pathParts.size == 2 && pathParts[1] == "_changes" -> {
                val dbName = pathParts[0]
                getChanges(dbName, request.queryParams)
            }
            
            // Database operations
            request.method == "PUT" && pathParts.size == 1 -> {
                createDatabase(pathParts[0])
            }
            
            request.method == "DELETE" && pathParts.size == 1 -> {
                deleteDatabase(pathParts[0])
            }
            
            request.method == "GET" && pathParts.size == 1 -> {
                getDatabaseInfo(pathParts[0])
            }
            
            else -> {
                CouchDbResponse(
                    status = 404,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = json.encodeToString(
                        CouchDbError.serializer(),
                        CouchDbError("Not Found", "Endpoint not found: ${request.method} ${request.path}")
                    )
                )
            }
        }
    }
    
    private suspend fun getDocument(dbName: String, docId: String, queryParams: Map<String, String>): CouchDbResponse {
        return try {
            val document = ipfsBridge.getDocument(dbName, docId)
            CouchDbResponse(
                status = 200,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "ETag" to "\"${document.rev}\""
                ),
                body = json.encodeToString(CouchDbDocument.serializer(), document)
            )
        } catch (e: DocumentNotFoundException) {
            CouchDbResponse(
                status = 404,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbError.serializer(),
                    CouchDbError("Not Found", "Document not found: $docId")
                )
            )
        }
    }
    
    private suspend fun putDocument(dbName: String, docId: String, body: String): CouchDbResponse {
        return try {
            val document = json.decodeFromString(CouchDbDocument.serializer(), body)
            val result = ipfsBridge.putDocument(dbName, docId, document)
            
            CouchDbResponse(
                status = 201,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "ETag" to "\"${result.rev}\""
                ),
                body = json.encodeToString(CouchDbPutResult.serializer(), result)
            )
        } catch (e: Exception) {
            CouchDbResponse(
                status = 400,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbError.serializer(),
                    CouchDbError("Bad Request", "Invalid document format: ${e.message}")
                )
            )
        }
    }
    
    private suspend fun deleteDocument(dbName: String, docId: String, queryParams: Map<String, String>): CouchDbResponse {
        val rev = queryParams["rev"] ?: return CouchDbResponse(
            status = 400,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(
                CouchDbError.serializer(),
                CouchDbError("Bad Request", "Revision required for deletion")
            )
        )
        
        return try {
            val result = ipfsBridge.deleteDocument(dbName, docId, rev)
            CouchDbResponse(
                status = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(CouchDbDeleteResult.serializer(), result)
            )
        } catch (e: DocumentNotFoundException) {
            CouchDbResponse(
                status = 404,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbError.serializer(),
                    CouchDbError("Not Found", "Document not found: $docId")
                )
            )
        }
    }
    
    private suspend fun getAllDocuments(dbName: String, queryParams: Map<String, String>): CouchDbResponse {
        val documents = ipfsBridge.getAllDocuments(dbName, queryParams)
        return CouchDbResponse(
            status = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(CouchDbAllDocsResult.serializer(), documents)
        )
    }
    
    private suspend fun bulkDocuments(dbName: String, body: String): CouchDbResponse {
        val bulkRequest = json.decodeFromString(CouchDbBulkRequest.serializer(), body)
        val results = ipfsBridge.bulkDocuments(dbName, bulkRequest)
        
        return CouchDbResponse(
            status = 201,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(CouchDbBulkResult.serializer(), results)
        )
    }
    
    private suspend fun getChanges(dbName: String, queryParams: Map<String, String>): CouchDbResponse {
        val changes = ipfsBridge.getChanges(dbName, queryParams)
        return CouchDbResponse(
            status = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(CouchDbChangesResult.serializer(), changes)
        )
    }
    
    private suspend fun createDatabase(dbName: String): CouchDbResponse {
        return try {
            ipfsBridge.createDatabase(dbName)
            CouchDbResponse(
                status = 201,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbOk.serializer(),
                    CouchDbOk("Database created successfully")
                )
            )
        } catch (e: DatabaseExistsException) {
            CouchDbResponse(
                status = 412,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbError.serializer(),
                    CouchDbError("Precondition Failed", "Database already exists: $dbName")
                )
            )
        }
    }
    
    private suspend fun deleteDatabase(dbName: String): CouchDbResponse {
        return try {
            ipfsBridge.deleteDatabase(dbName)
            CouchDbResponse(
                status = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbOk.serializer(),
                    CouchDbOk("Database deleted successfully")
                )
            )
        } catch (e: DatabaseNotFoundException) {
            CouchDbResponse(
                status = 404,
                headers = mapOf("Content-Type" to "application/json"),
                body = json.encodeToString(
                    CouchDbError.serializer(),
                    CouchDbError("Not Found", "Database not found: $dbName")
                )
            )
        }
    }
    
    private suspend fun getDatabaseInfo(dbName: String): CouchDbResponse {
        val info = ipfsBridge.getDatabaseInfo(dbName)
        return CouchDbResponse(
            status = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(CouchDbDatabaseInfo.serializer(), info)
        )
    }
    
    private suspend fun parseRequest(stream: QuicStream): CouchDbRequest {
        // Parse HTTP request from stream
        // This is a simplified implementation
        val requestLine = "GET /testdb/testdoc HTTP/3" // Would parse from stream
        val parts = requestLine.split(" ")
        val method = parts[0]
        val path = parts[1]
        
        return CouchDbRequest(
            method = method,
            path = path,
            headers = emptyMap(),
            queryParams = emptyMap(),
            body = ""
        )
    }
}

// Data classes for CouchDB API

@Serializable
data class CouchDbRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val queryParams: Map<String, String>,
    val body: String
)

@Serializable
data class CouchDbResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String
)

@Serializable
data class CouchDbDocument(
    val _id: String,
    val _rev: String,
    val _deleted: Boolean = false,
    val data: Map<String, Any> = emptyMap()
)

@Serializable
data class CouchDbPutResult(
    val ok: Boolean = true,
    val id: String,
    val rev: String
)

@Serializable
data class CouchDbDeleteResult(
    val ok: Boolean = true,
    val id: String,
    val rev: String
)

@Serializable
data class CouchDbAllDocsResult(
    val total_rows: Int,
    val offset: Int,
    val rows: List<CouchDbRow>
)

@Serializable
data class CouchDbRow(
    val id: String,
    val key: String,
    val value: CouchDbRowValue
)

@Serializable
data class CouchDbRowValue(
    val rev: String
)

@Serializable
data class CouchDbBulkRequest(
    val docs: List<CouchDbDocument>
)

@Serializable
data class CouchDbBulkResult(
    val results: List<CouchDbBulkItemResult>
)

@Serializable
data class CouchDbBulkItemResult(
    val id: String,
    val rev: String,
    val ok: Boolean = true,
    val error: String? = null
)

@Serializable
data class CouchDbChangesResult(
    val results: List<CouchDbChange>,
    val last_seq: String
)

@Serializable
data class CouchDbChange(
    val seq: String,
    val id: String,
    val changes: List<CouchDbChangeItem>,
    val deleted: Boolean = false
)

@Serializable
data class CouchDbChangeItem(
    val rev: String
)

@Serializable
data class CouchDbDatabaseInfo(
    val db_name: String,
    val doc_count: Int,
    val doc_del_count: Int,
    val update_seq: String,
    val purge_seq: Int,
    val compact_running: Boolean,
    val disk_size: Int,
    val data_size: Int,
    val instance_start_time: String,
    val disk_format_version: Int,
    val committed_update_seq: Int
)

@Serializable
data class CouchDbOk(
    val ok: Boolean = true,
    val message: String? = null
)

@Serializable
data class CouchDbError(
    val error: String,
    val reason: String
)

// Exceptions
class DocumentNotFoundException(message: String) : Exception(message)
class DatabaseExistsException(message: String) : Exception(message)
class DatabaseNotFoundException(message: String) : Exception(message) 