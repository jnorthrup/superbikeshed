package borg.trikeshed.couchdb.services

import borg.trikeshed.couchdb.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * CouchDB Service Implementation
 * 
 * TDD Implementation of CouchDB service layer providing high-level operations
 * for CouchDB integration with TrikeShed channels.
 */

class CouchDBService(
    private val blobService: ChannelizedBlobService = ChannelizedBlobService(),
    private val serverContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.IO
) {
    private val databases = mutableMapOf<String, CouchDBContext>()
    private val changeListeners = mutableMapOf<String, MutableList<(CouchChange) -> Unit>>()
    private val activeConnections = mutableMapOf<String, CouchDBConnection>()

    /**
     * Initialize CouchDB service
     */
    suspend fun initialize(): Boolean {
        return try {
            // Start blob service processors
            startBlobServiceProcessors()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create database
     */
    suspend fun createDatabase(name: String): CouchDBDatabaseInfo {
        val context = CouchDBContext(name, "http://localhost:5984/$name")
        databases[name] = context

        val createResponse = blobService.createDb(name, context)
        if (!createResponse.success) {
            throw CouchDBServiceException("Failed to create database: ${createResponse.message}")
        }

        return CouchDBDatabaseInfo(
            db_name = name,
            doc_count = 0,
            doc_del_count = 0,
            update_seq = 0,
            purge_seq = 0,
            compact_running = false,
            disk_size = 0,
            data_size = 0,
            instance_start_time = System.currentTimeMillis().toString(),
            disk_format_version = 8,
            committed_update_seq = 0
        )
    }

    /**
     * Delete database
     */
    suspend fun deleteDatabase(name: String): Boolean {
        val context = databases[name] ?: throw CouchDBServiceException("Database not found: $name")
        
        val deleteResponse = blobService.deleteDb(name, context)
        if (deleteResponse.success) {
            databases.remove(name)
            changeListeners.remove(name)
            return true
        }
        
        return false
    }

    /**
     * Get database info
     */
    suspend fun getDatabaseInfo(name: String): CouchDBDatabaseInfo {
        val context = databases[name] ?: throw CouchDBServiceException("Database not found: $name")
        
        // For now, return basic info. In a real implementation, this would query the actual database
        return CouchDBDatabaseInfo(
            db_name = name,
            doc_count = 0,
            doc_del_count = 0,
            update_seq = 0,
            purge_seq = 0,
            compact_running = false,
            disk_size = 0,
            data_size = 0,
            instance_start_time = System.currentTimeMillis().toString(),
            disk_format_version = 8,
            committed_update_seq = 0
        )
    }

    /**
     * Create document
     */
    suspend fun createDocument(dbName: String, document: CouchDocument): CouchDocument {
        val context = databases[dbName] ?: throw CouchDBServiceException("Database not found: $dbName")
        
        val putResponse = blobService.putBlob(dbName, document._id, document.data.toString().encodeToByteArray(), context)
        if (!putResponse.success) {
            throw CouchDBServiceException("Failed to create document: ${putResponse.message}")
        }

        val createdDocument = document.copy(
            _rev = putResponse.rev
        )

        // Emit change event
        emitChange(dbName, CouchChange(
            sequenceNumber = System.currentTimeMillis(),
            documentId = document._id,
            revision = putResponse.rev,
            deleted = false
        ))

        return createdDocument
    }

    /**
     * Get document
     */
    suspend fun getDocument(dbName: String, docId: String): CouchDocument? {
        val context = databases[dbName] ?: throw CouchDBServiceException("Database not found: $dbName")
        
        val getResponse = blobService.getBlob(dbName, docId, context)
        if (!getResponse.found || getResponse.data == null) {
            return null
        }

        // Parse document from blob data
        val documentData = getResponse.data.decodeToString()
        val jsonObject = kotlinx.serialization.json.Json.parseToJsonElement(documentData).jsonObject

        return CouchDocument(
            _id = docId,
            _rev = getResponse.rev ?: "1-unknown",
            data = jsonObject
        )
    }

    /**
     * Update document
     */
    suspend fun updateDocument(dbName: String, document: CouchDocument): CouchDocument {
        val context = databases[dbName] ?: throw CouchDBServiceException("Database not found: $dbName")
        
        val putResponse = blobService.putBlob(dbName, document._id, document.data.toString().encodeToByteArray(), context)
        if (!putResponse.success) {
            throw CouchDBServiceException("Failed to update document: ${putResponse.message}")
        }

        val updatedDocument = document.copy(
            _rev = putResponse.rev
        )

        // Emit change event
        emitChange(dbName, CouchChange(
            sequenceNumber = System.currentTimeMillis(),
            documentId = document._id,
            revision = putResponse.rev,
            deleted = false
        ))

        return updatedDocument
    }

    /**
     * Delete document
     */
    suspend fun deleteDocument(dbName: String, docId: String, revision: String): Boolean {
        val context = databases[dbName] ?: throw CouchDBServiceException("Database not found: $dbName")
        
        val deleteResponse = blobService.deleteBlob(dbName, docId, revision, context)
        if (deleteResponse.success) {
            // Emit change event
            emitChange(dbName, CouchChange(
                sequenceNumber = System.currentTimeMillis(),
                documentId = docId,
                revision = deleteResponse.rev,
                deleted = true
            ))
            return true
        }
        
        return false
    }

    /**
     * Bulk operations
     */
    suspend fun bulkDocs(dbName: String, documents: List<CouchDocument>): List<CouchBulkResult> {
        val context = databases[dbName] ?: throw CouchDBServiceException("Database not found: $dbName")
        
        val docs = documents.map { doc ->
            doc.data.toString().encodeToByteArray()
        }

        val bulkResponse = blobService.bulkDocs(dbName, docs, context)
        if (!bulkResponse.success) {
            throw CouchDBServiceException("Bulk operation failed: ${bulkResponse.message}")
        }

        return bulkResponse.results.map { result ->
            CouchBulkResult(
                ok = result.success,
                id = result.id,
                rev = result.rev,
                error = if (!result.success) result.message else null
            )
        }
    }

    /**
     * Subscribe to changes
     */
    fun subscribeToChanges(dbName: String, listener: (CouchChange) -> Unit) {
        changeListeners.getOrPut(dbName) { mutableListOf() }.add(listener)
    }

    /**
     * Unsubscribe from changes
     */
    fun unsubscribeFromChanges(dbName: String, listener: (CouchChange) -> Unit) {
        changeListeners[dbName]?.remove(listener)
    }

    /**
     * Get changes feed
     */
    suspend fun getChanges(dbName: String, since: String? = null): CouchChangesResponse {
        val changes = mutableListOf<CouchChange>()
        
        // In a real implementation, this would query the actual changes feed
        // For now, return empty changes
        
        return CouchChangesResponse(
            results = changes,
            last_seq = since ?: "0"
        )
    }

    /**
     * List all databases
     */
    suspend fun listDatabases(): List<String> {
        val listResponse = blobService.listDbs(serverContext)
        return if (listResponse.success) {
            listResponse.dbNames
        } else {
            emptyList()
        }
    }

    /**
     * Create connection
     */
    suspend fun createConnection(dbName: String): CouchDBConnection {
        val context = databases[dbName] ?: throw CouchDBServiceException("Database not found: $dbName")
        
        val connection = CouchDBConnection(
            id = "conn-${System.currentTimeMillis()}",
            databaseName = dbName,
            context = context,
            isActive = true
        )
        
        activeConnections[connection.id] = connection
        return connection
    }

    /**
     * Close connection
     */
    fun closeConnection(connectionId: String) {
        activeConnections.remove(connectionId)
    }

    /**
     * Close all connections
     */
    fun closeAllConnections() {
        activeConnections.clear()
    }

    /**
     * Shutdown service
     */
    fun shutdown() {
        closeAllConnections()
        blobService.stop()
    }

    // Private helper methods

    private suspend fun startBlobServiceProcessors() {
        // Start blob service processors in background
        kotlinx.coroutines.launch(serverContext) { blobService.processPutRequests(serverContext) }
        kotlinx.coroutines.launch(serverContext) { blobService.processGetRequests(serverContext) }
        kotlinx.coroutines.launch(serverContext) { blobService.processUpdateRequests(serverContext) }
        kotlinx.coroutines.launch(serverContext) { blobService.processDeleteRequests(serverContext) }
        kotlinx.coroutines.launch(serverContext) { blobService.processCreateDbRequests(serverContext) }
        kotlinx.coroutines.launch(serverContext) { blobService.processDeleteDbRequests(serverContext) }
        kotlinx.coroutines.launch(serverContext) { blobService.processListDbsRequests(serverContext) }
        kotlinx.coroutines.launch(serverContext) { blobService.processBulkDocsRequests(serverContext) }
    }

    private fun emitChange(dbName: String, change: CouchChange) {
        changeListeners[dbName]?.forEach { listener ->
            try {
                listener(change)
            } catch (e: Exception) {
                // Log error but don't fail the operation
            }
        }
    }
}

// ===== DATA STRUCTURES =====

data class CouchDBConnection(
    val id: String,
    val databaseName: String,
    val context: CouchDBContext,
    val isActive: Boolean
)

data class CouchBulkResult(
    val ok: Boolean,
    val id: String,
    val rev: String,
    val error: String? = null
)

data class CouchChangesResponse(
    val results: List<CouchChange>,
    val last_seq: String
)

// ===== EXCEPTIONS =====

class CouchDBServiceException(message: String, cause: Throwable? = null) : Exception(message, cause) 