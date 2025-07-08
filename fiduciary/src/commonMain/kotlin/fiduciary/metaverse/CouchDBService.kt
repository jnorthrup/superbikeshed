package fiduciary.metaverse

import borg.trikeshed.lib.*
import borg.trikeshed.couchdb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

/**
 * CouchDB Service for Realtime Document Recording
 * 
 * Provides CouchDB integration for the fiduciary metaverse:
 * - Document storage and retrieval
 * - Changes feed subscription
 * - Real-time updates
 * - Query support
 * - Database management
 */
class CouchDBService {
    
    // Active database connections
    internal val databases = mutableMapOf<String, CouchDatabase>()
    
    // Changes feed subscriptions
    internal val changesSubscriptions = mutableMapOf<String, MutableList<(CouchChange) -> Unit>>()
    
    // Document cache
    internal val documentCache = mutableMapOf<String, MutableMap<String, CouchDocument>>()

    /**
     * Create a new database
     */
    suspend fun createDatabase(databaseName: String): Boolean {
        return try {
            val database = CouchDatabase(databaseName)
            databases[databaseName] = database
            documentCache[databaseName] = mutableMapOf()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Save a document to CouchDB
     */
    suspend fun saveDocument(
        databaseName: String,
        document: CouchDocument
    ): CouchDocument {
        
        val database = databases.getOrPut(databaseName) { CouchDatabase(databaseName) }
        val cache = documentCache.getOrPut(databaseName) { mutableMapOf() }
        
        // Generate revision if not present
        val docWithRev = if (document.rev == null) {
            document.copy(rev = generateRevision())
        } else {
            document
        }
        
        // Save to database
        val savedDoc = database.save(docWithRev)
        
        // Update cache
        cache[savedDoc.id ?: ""] = savedDoc
        
        // Emit change
        emitChange(databaseName, CouchChange(
            id = savedDoc.id ?: "",
            seq = generateSequence(),
            changes = listOf(CouchChangeItem(savedDoc.rev ?: "")),
            deleted = false,
            document = savedDoc
        ))
        
        return savedDoc
    }

    /**
     * Get a document by ID
     */
    suspend fun getDocument(
        databaseName: String,
        documentId: String
    ): CouchDocument? {
        
        val cache = documentCache[databaseName]
        if (cache != null && cache.containsKey(documentId)) {
            return cache[documentId]
        }
        
        val database = databases[databaseName] ?: return null
        val document = database.get(documentId)
        
        if (document != null) {
            documentCache.getOrPut(databaseName) { mutableMapOf() }[documentId] = document
        }
        
        return document
    }

    /**
     * Query documents
     */
    suspend fun queryDocuments(
        database: String,
        query: Map<String, Any>
    ): List<CouchDocument> {
        
        val databaseInstance = databases[database] ?: return emptyList()
        val cache = documentCache[database] ?: return emptyList()
        
        // Simple query implementation (in real CouchDB, would use views or Mango queries)
        return cache.values.filter { document ->
            query.all { (key, value) ->
                when (value) {
                    is Map<*, *> -> {
                        val mapValue = value as Map<*, *>
                        when (mapValue["\$gte"]) {
                            is Long -> {
                                val docValue = document.data[key]?.jsonPrimitive?.long
                                docValue != null && docValue >= (mapValue["\$gte"] as Long)
                            }
                            else -> true
                        }
                    }
                    else -> {
                        val docValue = document.data[key]?.jsonPrimitive?.content
                        docValue == value.toString()
                    }
                }
            }
        }
    }

    /**
     * Subscribe to changes feed
     */
    fun subscribeToChanges(
        databaseName: String,
        callback: (CouchChange) -> Unit
    ) {
        changesSubscriptions.getOrPut(databaseName) { mutableListOf() }.add(callback)
    }

    /**
     * Unsubscribe from changes feed
     */
    fun unsubscribeFromChanges(
        databaseName: String,
        callback: (CouchChange) -> Unit
    ) {
        changesSubscriptions[databaseName]?.remove(callback)
    }

    /**
     * Get changes feed as Flow
     */
    fun getChangesFeed(databaseName: String): Flow<CouchChange> {
        return flow {
            // In real implementation, would connect to CouchDB changes feed
            // For demo, we'll emit changes from our internal tracking
            changesSubscriptions[databaseName]?.forEach { callback ->
                // This would be called when changes are emitted
            }
        }
    }

    /**
     * Delete a document
     */
    suspend fun deleteDocument(
        databaseName: String,
        documentId: String,
        revision: String
    ): Boolean {
        
        val database = databases[databaseName] ?: return false
        val cache = documentCache[databaseName]
        
        val success = database.delete(documentId, revision)
        
        if (success) {
            cache?.remove(documentId)
            
            // Emit deletion change
            emitChange(databaseName, CouchChange(
                id = documentId,
                seq = generateSequence(),
                changes = listOf(CouchChangeItem(revision)),
                deleted = true,
                document = null
            ))
        }
        
        return success
    }

    /**
     * Get database info
     */
    suspend fun getDatabaseInfo(databaseName: String): CouchDatabaseInfo? {
        val database = databases[databaseName] ?: return null
        return database.getInfo()
    }

    /**
     * Compact database
     */
    suspend fun compactDatabase(databaseName: String): Boolean {
        val database = databases[databaseName] ?: return false
        return database.compact()
    }

    // Private helper methods

    internal fun emitChange(databaseName: String, change: CouchChange) {
        changesSubscriptions[databaseName]?.forEach { callback ->
            try {
                callback(change)
            } catch (e: Exception) {
                // Log error but don't break other subscribers
                println("Error in changes callback: ${e.message}")
            }
        }
    }

    internal fun generateRevision(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextInt()}"
    }

    internal fun generateSequence(): String {
        return Clock.System.now().toEpochMilliseconds().toString()
    }
}

/**
 * CouchDB Database
 */
class CouchDatabase(val name: String) {
    
    internal val documents = mutableMapOf<String, CouchDocument>()
    internal val revisions = mutableMapOf<String, MutableList<String>>()
    internal var documentCount = 0L
    internal var updateSeq = 0L

    suspend fun save(document: CouchDocument): CouchDocument {
        val docId = document.id ?: generateDocumentId()
        val docRev = document.rev ?: generateRevision()
        
        val savedDoc = document.copy(
            id = docId,
            rev = docRev
        )
        
        documents[docId] = savedDoc
        revisions.getOrPut(docId) { mutableListOf() }.add(docRev)
        
        documentCount++
        updateSeq++
        
        return savedDoc
    }

    suspend fun get(documentId: String): CouchDocument? {
        return documents[documentId]
    }

    suspend fun delete(documentId: String, revision: String): Boolean {
        val document = documents[documentId] ?: return false
        
        val deletedDoc = document.copy(
            rev = revision,
            deleted = true
        )
        
        documents[documentId] = deletedDoc
        updateSeq++
        
        return true
    }

    suspend fun getInfo(): CouchDatabaseInfo {
        return CouchDatabaseInfo(
            dbName = name,
            docCount = documentCount,
            docDelCount = documents.values.count { it.deleted == true }.toLong(),
            updateSeq = updateSeq.toString(),
            purgeSeq = 0L,
            compactRunning = false,
            diskSize = 0L,
            dataSize = 0L,
            instanceStartTime = Clock.System.now().toString(),
            diskFormatVersion = 8,
            committedUpdateSeq = updateSeq.toString()
        )
    }

    suspend fun compact(): Boolean {
        // Remove deleted documents
        documents.entries.removeIf { (_, doc) -> doc.deleted == true }
        return true
    }

    internal fun generateDocumentId(): String {
        return "doc-${Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextInt()}"
    }

    internal fun generateRevision(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextInt()}"
    }
}

// Data types

data class CouchChange(
    val id: String,
    val seq: String,
    val changes: List<CouchChangeItem>,
    val deleted: Boolean = false,
    val document: CouchDocument? = null
)

data class CouchChangeItem(
    val rev: String
)

data class CouchDocument(
    val id: String? = null,
    val rev: String? = null,
    val deleted: Boolean? = null,
    val attachments: JsonObject? = null,
    val data: Map<String, JsonPrimitive> = emptyMap()
) {
    fun toJson(): JsonObject = buildJsonObject {
        id?.let { put("_id", it) }
        rev?.let { put("_rev", it) }
        deleted?.let { put("_deleted", it) }
        attachments?.let { put("_attachments", it) }
        data.forEach { (key, value) -> put(key, value) }
    }
}

data class CouchDatabaseInfo(
    val dbName: String,
    val docCount: Long,
    val docDelCount: Long,
    val updateSeq: String,
    val purgeSeq: Long,
    val compactRunning: Boolean,
    val diskSize: Long,
    val dataSize: Long,
    val instanceStartTime: String,
    val diskFormatVersion: Int,
    val committedUpdateSeq: String
) 