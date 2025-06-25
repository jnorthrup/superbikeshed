package nexus.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nexus.api.*
import nexus.core.DefaultNexusAgent
import nexus.core.GossipMessage
import nexus.core.IpfsPubSubService
import nexus.core.LogLevel
import borg.trikeshed.lib.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * IPFS Bridge that translates CouchDB API calls into IPFS operations.
 * 
 * This bridge implements:
 * - PUT -> Add document to IPFS, get CID
 * - GET -> Resolve document ID to CID, fetch from IPFS
 * - DELETE -> Mark document as deleted, update index
 * - PubSub for document indices and updates across the network
 * - Document versioning and conflict resolution
 */
class IpfsBridge(
    private val agent: DefaultNexusAgent,
    private val ipfsService: IpfsPubSubService,
    private val scope: CoroutineScope
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true 
    }
    
    // Simple IPFS storage using TrikeShed Indexed<Byte>
    private val ipfsStorage = mutableMapOf<String, Indexed<Byte>>()
    
    // Document ID to CID mapping (in production, this would be stored in IPFS)
    private val documentIndex = mutableMapOf<String, DocumentIndexEntry>()
    private val databaseIndex = mutableMapOf<String, DatabaseIndexEntry>()
    
    // Sequence numbers for document versions
    private val sequenceCounter = AtomicLong(1)
    
    // Changes feed for real-time updates
    private val _changes = MutableSharedFlow<CouchDbChange>()
    val changes: Flow<CouchDbChange> = _changes.asSharedFlow()
    
    init {
        // Subscribe to document index updates from other nodes
        scope.launch {
            ipfsService.subscribe("nexus/document-index").collect { message ->
                handleIndexUpdate(message)
            }
        }
        
        // Subscribe to database index updates
        scope.launch {
            ipfsService.subscribe("nexus/database-index").collect { message ->
                handleDatabaseIndexUpdate(message)
            }
        }
    }
    
    /**
     * Creates a new database in IPFS
     */
    suspend fun createDatabase(dbName: String) {
        if (databaseIndex.containsKey(dbName)) {
            throw DatabaseExistsException("Database already exists: $dbName")
        }
        
        val dbInfo = DatabaseIndexEntry(
            name = dbName,
            cid = generateCid("database:$dbName"),
            createdAt = System.currentTimeMillis(),
            documentCount = 0,
            deletedCount = 0,
            updateSeq = "0"
        )
        
        databaseIndex[dbName] = dbInfo
        
        // Publish database creation to network
        val payload = json.encodeToString(DatabaseIndexEntry.serializer(), dbInfo)
        ipfsService.publish("nexus/database-index", payload)
        
        agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
            println("Created database: $dbName with CID: ${dbInfo.cid}")
        }
    }
    
    /**
     * Deletes a database from IPFS
     */
    suspend fun deleteDatabase(dbName: String) {
        val dbInfo = databaseIndex[dbName] ?: throw DatabaseNotFoundException("Database not found: $dbName")
        
        // Mark all documents as deleted
        val documents = documentIndex.values.filter { it.dbName == dbName }
        documents.forEach { doc ->
            val deletedDoc = doc.copy(
                deleted = true,
                rev = generateRevision(),
                updatedAt = System.currentTimeMillis()
            )
            documentIndex[doc.id] = deletedDoc
        }
        
        // Remove from database index
        databaseIndex.remove(dbName)
        
        // Publish database deletion to network
        val payload = json.encodeToString(
            DatabaseDeletionEntry.serializer(),
            DatabaseDeletionEntry(dbName, System.currentTimeMillis())
        )
        ipfsService.publish("nexus/database-index", payload)
        
        agent.logLevel.takeIf { it <= LogLevel.INFO }?.let {
            println("Deleted database: $dbName")
        }
    }
    
    /**
     * Gets database information
     */
    suspend fun getDatabaseInfo(dbName: String): CouchDbDatabaseInfo {
        val dbInfo = databaseIndex[dbName] ?: throw DatabaseNotFoundException("Database not found: $dbName")
        
        val documents = documentIndex.values.filter { it.dbName == dbName }
        val docCount = documents.count { !it.deleted }
        val deletedCount = documents.count { it.deleted }
        
        return CouchDbDatabaseInfo(
            db_name = dbName,
            doc_count = docCount,
            doc_del_count = deletedCount,
            update_seq = dbInfo.updateSeq,
            purge_seq = 0,
            compact_running = false,
            disk_size = 0, // Would calculate actual size in IPFS
            data_size = 0, // Would calculate actual data size
            instance_start_time = dbInfo.createdAt.toString(),
            disk_format_version = 1,
            committed_update_seq = dbInfo.updateSeq.toIntOrNull() ?: 0
        )
    }
    
    /**
     * Stores a document in IPFS using TrikeShed Indexed<Byte>
     */
    suspend fun putDocument(dbName: String, docId: String, document: CouchDbDocument): CouchDbPutResult {
        // Verify database exists
        if (!databaseIndex.containsKey(dbName)) {
            throw DatabaseNotFoundException("Database not found: $dbName")
        }
        
        val rev = generateRevision()
        
        // Store document using TrikeShed Indexed<Byte>
        val documentData = json.encodeToString(CouchDbDocument.serializer(), document.copy(_rev = rev))
        val dataIndexed = documentData.toByteArray().size j { documentData.toByteArray()[it] }
        val cid = addToIpfs(dataIndexed)
        
        val indexEntry = DocumentIndexEntry(
            id = docId,
            dbName = dbName,
            cid = cid,
            rev = rev,
            deleted = document._deleted,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // Update local index
        documentIndex[docId] = indexEntry
        
        // Update database sequence
        val dbInfo = databaseIndex[dbName]!!
        val newSeq = sequenceCounter.incrementAndGet().toString()
        databaseIndex[dbName] = dbInfo.copy(
            documentCount = dbInfo.documentCount + 1,
            updateSeq = newSeq
        )
        
        // Publish document update to network
        val payload = json.encodeToString(DocumentIndexEntry.serializer(), indexEntry)
        ipfsService.publish("nexus/document-index", payload)
        
        // Emit change for changes feed
        _changes.emit(CouchDbChange(
            seq = newSeq,
            id = docId,
            changes = listOf(CouchDbChangeItem(rev)),
            deleted = document._deleted
        ))
        
        agent.logLevel.takeIf { it <= LogLevel.DEBUG }?.let {
            println("Stored document: $docId in database: $dbName with CID: $cid")
        }
        
        return CouchDbPutResult(id = docId, rev = rev)
    }
    
    /**
     * Retrieves a document from IPFS
     */
    suspend fun getDocument(dbName: String, docId: String): CouchDbDocument? {
        val indexEntry = documentIndex[docId] ?: return null
        
        if (indexEntry.deleted) {
            throw DocumentNotFoundException("Document is deleted: $docId")
        }
        
        // Retrieve from IPFS storage
        val dataIndexed = ipfsStorage[indexEntry.cid] ?: return null
        val documentData = dataIndexed.toByteArray().let { String(it) }
        
        return json.decodeFromString(CouchDbDocument.serializer(), documentData)
    }
    
    /**
     * Deletes a document from IPFS
     */
    suspend fun deleteDocument(dbName: String, docId: String, rev: String): CouchDbPutResult {
        val indexEntry = documentIndex[docId] ?: throw DocumentNotFoundException("Document not found: $docId")
        
        if (indexEntry.rev != rev) {
            throw DocumentNotFoundException("Document revision mismatch")
        }
        
        val newRev = generateRevision()
        val deletedDoc = indexEntry.copy(
            deleted = true,
            rev = newRev,
            updatedAt = System.currentTimeMillis()
        )
        
        documentIndex[docId] = deletedDoc
        
        // Update database sequence
        val dbInfo = databaseIndex[dbName]!!
        val newSeq = sequenceCounter.incrementAndGet().toString()
        databaseIndex[dbName] = dbInfo.copy(
            deletedCount = dbInfo.deletedCount + 1,
            updateSeq = newSeq
        )
        
        // Publish deletion to network
        val payload = json.encodeToString(DocumentIndexEntry.serializer(), deletedDoc)
        ipfsService.publish("nexus/document-index", payload)
        
        // Emit change for changes feed
        _changes.emit(CouchDbChange(
            seq = newSeq,
            id = docId,
            changes = listOf(CouchDbChangeItem(newRev)),
            deleted = true
        ))
        
        return CouchDbPutResult(id = docId, rev = newRev)
    }
    
    /**
     * Gets all documents in a database
     */
    suspend fun getAllDocuments(dbName: String, queryParams: Map<String, String>): CouchDbAllDocsResult {
        if (!databaseIndex.containsKey(dbName)) {
            throw DatabaseNotFoundException("Database not found: $dbName")
        }
        
        val documents = documentIndex.values.filter { it.dbName == dbName }
        val includeDeleted = queryParams["include_docs"] == "true"
        val filteredDocs = if (includeDeleted) documents else documents.filter { !it.deleted }
        
        val rows = filteredDocs.map { doc ->
            CouchDbRow(
                id = doc.id,
                key = doc.id,
                value = CouchDbRowValue(rev = doc.rev)
            )
        }
        
        return CouchDbAllDocsResult(
            total_rows = rows.size,
            offset = 0,
            rows = rows
        )
    }
    
    /**
     * Performs bulk document operations
     */
    suspend fun bulkDocuments(dbName: String, bulkRequest: CouchDbBulkRequest): CouchDbBulkResult {
        val results = mutableListOf<CouchDbBulkItemResult>()
        
        bulkRequest.docs.forEach { doc ->
            try {
                val result = putDocument(dbName, doc._id, doc)
                results.add(CouchDbBulkItemResult(
                    id = result.id,
                    rev = result.rev,
                    ok = true
                ))
            } catch (e: Exception) {
                results.add(CouchDbBulkItemResult(
                    id = doc._id,
                    rev = "",
                    ok = false,
                    error = e.message
                ))
            }
        }
        
        return CouchDbBulkResult(results = results)
    }
    
    /**
     * Gets changes feed for a database
     */
    suspend fun getChanges(dbName: String, queryParams: Map<String, String>): CouchDbChangesResult {
        if (!databaseIndex.containsKey(dbName)) {
            throw DatabaseNotFoundException("Database not found: $dbName")
        }
        
        val documents = documentIndex.values.filter { it.dbName == dbName }
        val changes = documents.map { doc ->
            CouchDbChange(
                seq = doc.updatedAt.toString(),
                id = doc.id,
                changes = listOf(CouchDbChangeItem(doc.rev)),
                deleted = doc.deleted
            )
        }
        
        val lastSeq = documents.maxOfOrNull { it.updatedAt }?.toString() ?: "0"
        
        return CouchDbChangesResult(
            results = changes,
            last_seq = lastSeq
        )
    }
    
    // Private helper methods
    
    private fun addToIpfs(data: Indexed<Byte>): String {
        // Simple hash-based CID generation
        val hash = computeSimpleHash(data)
        val cid = "bafy" + hash // Simplified CID format
        ipfsStorage[cid] = data
        return cid
    }
    
    private fun computeSimpleHash(data: Indexed<Byte>): String {
        // Simple hash function for demo purposes
        var hash = 0L
        for (i in 0 until data.a) {
            hash = hash * 31 + data.b(i).toLong()
        }
        return hash.toString(16).padStart(16, '0')
    }
    
    private fun Indexed<Byte>.toByteArray(): ByteArray {
        return ByteArray(a) { b(it) }
    }
    
    private fun generateCid(prefix: String): String {
        return "bafy" + (prefix.hashCode() * 31 + System.currentTimeMillis()).toString(16).padStart(16, '0')
    }
    
    private fun generateRevision(): String {
        return sequenceCounter.incrementAndGet().toString()
    }
    
    private suspend fun handleIndexUpdate(message: GossipMessage) {
        try {
            val indexEntry = json.decodeFromString(DocumentIndexEntry.serializer(), message.payload)
                documentIndex[indexEntry.id] = indexEntry
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("Error handling index update: ${e.message}")
            }
        }
    }
    
    private suspend fun handleDatabaseIndexUpdate(message: GossipMessage) {
            try {
                val dbEntry = json.decodeFromString(DatabaseIndexEntry.serializer(), message.payload)
                databaseIndex[dbEntry.name] = dbEntry
        } catch (e: Exception) {
            agent.logLevel.takeIf { it <= LogLevel.ERROR }?.let {
                println("Error handling database index update: ${e.message}")
            }
        }
    }
}

// Data classes for IPFS bridge

@Serializable
data class DocumentIndexEntry(
    val id: String,
    val dbName: String,
    val cid: String,
    val rev: String,
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class DatabaseIndexEntry(
    val name: String,
    val cid: String,
    val createdAt: Long,
    val documentCount: Int,
    val deletedCount: Int,
    val updateSeq: String
)

@Serializable
data class DatabaseDeletionEntry(
    val dbName: String,
    val deletedAt: Long
) 