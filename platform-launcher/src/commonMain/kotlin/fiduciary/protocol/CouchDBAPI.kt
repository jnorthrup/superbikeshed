package fiduciary.protocol

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * CouchDB API - Basic database functionality
 * 
 * From Fiduciary Omnibus Architecture:
 * - Inherits basic database functionality
 * - Manages UserID key relationships
 * - Supports document storage and retrieval
 * - Change feed support
 */

/**
 * CouchDB Document representation
 */
@Serializable
data class CouchDocument(
    val id: String,
    val revision: String,
    val data: JsonObject,
    val attachments: Map<String, CouchAttachment> = emptyMap(),
    val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * CouchDB Attachment
 */
@Serializable
data class CouchAttachment(
    val contentType: String,
    val data: ByteArray,
    val length: Int,
    val digest: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as CouchAttachment
        
        if (contentType != other.contentType) return false
        if (!data.contentEquals(other.data)) return false
        if (length != other.length) return false
        if (digest != other.digest) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + length
        result = 31 * result + digest.hashCode()
        return result
    }
}

/**
 * CouchDB Change event
 */
@Serializable
data class CouchChange(
    val sequenceNumber: Long,
    val documentId: String,
    val revision: String,
    val deleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * CouchDB Query
 */
@Serializable
data class CouchQuery(
    val selector: Map<String, Any>,
    val fields: List<String> = emptyList(),
    val sort: List<Map<String, String>> = emptyList(),
    val limit: Int = 100,
    val skip: Int = 0
)

/**
 * Database info
 */
@Serializable
data class DatabaseInfo(
    val name: String,
    val documentCount: Int,
    val deletedCount: Int,
    val updateSequence: Long,
    val diskSize: Long,
    val dataSize: Long
)

/**
 * Bulk operation result
 */
@Serializable
data class BulkResult(
    val success: Boolean,
    val processedCount: Int,
    val errors: List<String> = emptyList()
)

/**
 * User key for omnibus architecture
 */
@Serializable
data class UserKey(
    val userId: String,
    val keyType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val permissions: Set<String> = emptySet()
)

/**
 * CouchDB exceptions
 */
class CouchDBException(message: String, cause: Throwable? = null) : Exception(message, cause)
class CouchDBConflictException(message: String) : CouchDBException(message)
class CouchDBNotFoundException(message: String) : CouchDBException(message)

/**
 * Base CouchDB API implementation
 */
open class CouchDBAPI(
    protected val databaseName: String
) {
    private val documents = mutableMapOf<String, CouchDocument>()
    private val userKeys = mutableMapOf<String, UserKey>()
    private val changeListeners = mutableListOf<(CouchChange) -> Unit>()
    private val mutex = Mutex()
    private var sequenceNumber = 0L
    
    /**
     * Create a new document
     */
    open suspend fun createDocument(document: CouchDocument): CouchDocument {
        mutex.withLock {
            if (documents.containsKey(document.id)) {
                throw CouchDBConflictException("Document ${document.id} already exists")
            }
            
            val newRevision = generateRevision()
            val newDocument = document.copy(
                revision = newRevision,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            documents[document.id] = newDocument
            
            // Emit change event
            emitChange(CouchChange(
                sequenceNumber = ++sequenceNumber,
                documentId = document.id,
                revision = newRevision
            ))
            
            return newDocument
        }
    }
    
    /**
     * Get document by ID
     */
    open suspend fun getDocument(id: String): CouchDocument {
        mutex.withLock {
            return documents[id] ?: throw CouchDBNotFoundException("Document $id not found")
        }
    }
    
    /**
     * Update existing document
     */
    open suspend fun updateDocument(document: CouchDocument): CouchDocument {
        mutex.withLock {
            val existing = documents[document.id]
                ?: throw CouchDBNotFoundException("Document ${document.id} not found")
            
            if (existing.revision != document.revision) {
                throw CouchDBConflictException("Document ${document.id} has been modified")
            }
            
            val newRevision = generateRevision()
            val updatedDocument = document.copy(
                revision = newRevision,
                updatedAt = System.currentTimeMillis()
            )
            
            documents[document.id] = updatedDocument
            
            // Emit change event
            emitChange(CouchChange(
                sequenceNumber = ++sequenceNumber,
                documentId = document.id,
                revision = newRevision
            ))
            
            return updatedDocument
        }
    }
    
    /**
     * Delete document
     */
    open suspend fun deleteDocument(id: String, revision: String): Boolean {
        mutex.withLock {
            val existing = documents[id]
                ?: throw CouchDBNotFoundException("Document $id not found")
            
            if (existing.revision != revision) {
                throw CouchDBConflictException("Document $id has been modified")
            }
            
            val deletedDocument = existing.copy(
                deleted = true,
                revision = generateRevision(),
                updatedAt = System.currentTimeMillis()
            )
            
            documents[id] = deletedDocument
            
            // Emit change event
            emitChange(CouchChange(
                sequenceNumber = ++sequenceNumber,
                documentId = id,
                revision = deletedDocument.revision,
                deleted = true
            ))
            
            return true
        }
    }
    
    /**
     * Bulk insert documents
     */
    open suspend fun bulkInsert(documents: List<CouchDocument>): List<BulkResult> {
        val results = mutableListOf<BulkResult>()
        
        documents.forEach { doc ->
            try {
                createDocument(doc)
                results.add(BulkResult(success = true, processedCount = 1))
            } catch (e: CouchDBException) {
                results.add(BulkResult(
                    success = false,
                    processedCount = 0,
                    errors = listOf(e.message ?: "Unknown error")
                ))
            }
        }
        
        return results
    }
    
    /**
     * Query documents
     */
    open suspend fun query(query: CouchQuery): List<CouchDocument> {
        mutex.withLock {
            var results = documents.values.filter { doc ->
                !doc.deleted && matchesSelector(doc, query.selector)
            }
            
            // Apply sorting
            if (query.sort.isNotEmpty()) {
                results = applySorting(results, query.sort)
            }
            
            // Apply pagination
            results = results.drop(query.skip).take(query.limit)
            
            // Project fields if specified
            if (query.fields.isNotEmpty()) {
                results = results.map { doc ->
                    val projectedData = JsonObject(
                        query.fields.associate { field ->
                            field to (doc.data[field] ?: JsonNull)
                        }
                    )
                    doc.copy(data = projectedData)
                }
            }
            
            return results
        }
    }
    
    /**
     * Subscribe to change feed
     */
    open suspend fun subscribeToChanges(listener: (CouchChange) -> Unit) {
        mutex.withLock {
            changeListeners.add(listener)
        }
    }
    
    /**
     * Unsubscribe from change feed
     */
    open suspend fun unsubscribeFromChanges(listener: (CouchChange) -> Unit) {
        mutex.withLock {
            changeListeners.remove(listener)
        }
    }
    
    /**
     * Get database info
     */
    open suspend fun getDatabaseInfo(): DatabaseInfo {
        mutex.withLock {
            val allDocs = documents.values
            val activeDocs = allDocs.filter { !it.deleted }
            val deletedDocs = allDocs.filter { it.deleted }
            
            return DatabaseInfo(
                name = databaseName,
                documentCount = activeDocs.size,
                deletedCount = deletedDocs.size,
                updateSequence = sequenceNumber,
                diskSize = calculateDiskSize(allDocs),
                dataSize = calculateDataSize(activeDocs)
            )
        }
    }
    
    /**
     * Compact database
     */
    open suspend fun compactDatabase(): Boolean {
        mutex.withLock {
            // Remove deleted documents
            val activeDocuments = documents.filterValues { !it.deleted }
            documents.clear()
            documents.putAll(activeDocuments)
            return true
        }
    }
    
    /**
     * Register user key
     */
    open suspend fun registerUserKey(userKey: UserKey) {
        mutex.withLock {
            userKeys[userKey.userId] = userKey
        }
    }
    
    /**
     * Get user key
     */
    open suspend fun getUserKey(userId: String): UserKey? {
        mutex.withLock {
            return userKeys[userId]
        }
    }
    
    /**
     * Get all user keys
     */
    open suspend fun getAllUserKeys(): List<UserKey> {
        mutex.withLock {
            return userKeys.values.toList()
        }
    }
    
    // Helper methods
    
    private fun generateRevision(): String {
        return "${System.currentTimeMillis()}-${Random.nextInt(1000000)}"
    }
    
    private fun emitChange(change: CouchChange) {
        changeListeners.forEach { listener ->
            try {
                listener(change)
            } catch (e: Exception) {
                // Log error but don't fail the operation
            }
        }
    }
    
    private fun matchesSelector(document: CouchDocument, selector: Map<String, Any>): Boolean {
        return selector.all { (key, value) ->
            val docValue = document.data[key]
            when (value) {
                is String -> docValue?.toString()?.contains(value) == true
                is Number -> docValue?.toString()?.toDoubleOrNull() == value.toDouble()
                is Boolean -> docValue?.toString()?.toBoolean() == value
                else -> docValue?.toString() == value.toString()
            }
        }
    }
    
    private fun applySorting(
        documents: List<CouchDocument>,
        sortSpecs: List<Map<String, String>>
    ): List<CouchDocument> {
        if (sortSpecs.isEmpty()) return documents
        
        return documents.sortedWith { doc1, doc2 ->
            for (sortSpec in sortSpecs) {
                val field = sortSpec.keys.first()
                val direction = sortSpec[field] ?: "asc"
                
                val value1 = doc1.data[field]?.toString() ?: ""
                val value2 = doc2.data[field]?.toString() ?: ""
                
                val comparison = if (direction == "desc") {
                    value2.compareTo(value1)
                } else {
                    value1.compareTo(value2)
                }
                
                if (comparison != 0) return@sortedWith comparison
            }
            0
        }
    }
    
    private fun calculateDiskSize(documents: List<CouchDocument>): Long {
        return documents.sumOf { doc ->
            doc.data.toString().length.toLong() +
            doc.attachments.values.sumOf { it.length.toLong() }
        }
    }
    
    private fun calculateDataSize(documents: List<CouchDocument>): Long {
        return documents.sumOf { doc ->
            doc.data.toString().length.toLong()
        }
    }
}

/**
 * Document template for factory pattern
 */
@Serializable
data class DocumentTemplate(
    val type: String,
    val requiredFields: List<String>,
    val optionalFields: List<String> = emptyList(),
    val defaultValues: Map<String, JsonElement> = emptyMap(),
    val validation: Map<String, String> = emptyMap()
)

/**
 * Document factory
 */
class DocumentFactory(
    private val template: DocumentTemplate,
    private val couchAPI: CouchDBAPI
) {
    suspend fun create(data: Map<String, JsonElement>): CouchDocument {
        // Validate required fields
        template.requiredFields.forEach { field ->
            if (!data.containsKey(field)) {
                throw IllegalArgumentException("Required field '$field' is missing")
            }
        }
        
        // Merge with default values
        val mergedData = template.defaultValues + data
        
        // Generate ID
        val id = "${template.type}-${System.currentTimeMillis()}-${Random.nextInt(1000)}"
        
        return CouchDocument(
            id = id,
            revision = "1-initial",
            data = JsonObject(mergedData)
        )
    }
}

/**
 * Batch processor for bulk operations
 */
class BatchProcessor(
    private val batchSize: Int,
    private val couchAPI: CouchDBAPI
) {
    private val documents = mutableListOf<CouchDocument>()
    
    fun add(document: CouchDocument) {
        documents.add(document)
    }
    
    suspend fun process(): List<BulkResult> {
        val results = mutableListOf<BulkResult>()
        
        documents.chunked(batchSize).forEach { batch ->
            val batchResult = couchAPI.bulkInsert(batch)
            val successCount = batchResult.count { it.success }
            val errors = batchResult.flatMap { it.errors }
            
            results.add(BulkResult(
                success = errors.isEmpty(),
                processedCount = successCount,
                errors = errors
            ))
        }
        
        return results
    }
}

/**
 * RelaxFactory API - Inherits from CouchDB API
 * 
 * Adds relaxed document handling:
 * - Factory patterns for document creation
 * - Schema evolution support
 * - Template-based document generation
 * - Flexible validation
 */
class RelaxFactoryAPI(
    databaseName: String
) : CouchDBAPI(databaseName) {
    
    private val documentFactories = mutableMapOf<String, DocumentFactory>()
    private val documentTemplates = mutableMapOf<String, DocumentTemplate>()
    private val relationshipMutex = Mutex()
    private val relationships = mutableMapOf<String, MutableList<String>>()
    
    /**
     * Create document factory for type
     */
    suspend fun createDocumentFactory(type: String): DocumentFactory {
        val template = documentTemplates[type] ?: DocumentTemplate(
            type = type,
            requiredFields = emptyList(),
            optionalFields = emptyList(),
            defaultValues = mapOf(
                "type" to JsonPrimitive(type),
                "created_at" to JsonPrimitive(System.currentTimeMillis())
            )
        )
        
        val factory = DocumentFactory(template, this)
        documentFactories[type] = factory
        return factory
    }
    
    /**
     * Create relaxed document with flexible validation
     */
    suspend fun createRelaxedDocument(
        id: String,
        data: JsonObject,
        validate: Boolean = false
    ): CouchDocument {
        val document = CouchDocument(
            id = id,
            revision = "1-initial",
            data = data
        )
        
        if (validate) {
            // Perform basic validation
            validateRelaxedDocument(document)
        }
        
        return createDocument(document)
    }
    
    /**
     * Evolve document schema
     */
    suspend fun evolveSchema(
        document: CouchDocument,
        targetVersion: Int
    ): CouchDocument {
        val currentVersion = document.data["schema_version"]?.toString()?.toIntOrNull() ?: 1
        
        if (currentVersion >= targetVersion) {
            return document
        }
        
        var evolvedData = document.data.toMutableMap()
        
        // Apply evolution rules
        for (version in (currentVersion + 1)..targetVersion) {
            evolvedData = applySchemaEvolution(evolvedData, version)
        }
        
        val evolvedDocument = document.copy(
            data = JsonObject(evolvedData)
        )
        
        return updateDocument(evolvedDocument)
    }
    
    /**
     * Create document from template
     */
    suspend fun createFromTemplate(
        template: DocumentTemplate,
        data: Map<String, JsonElement>
    ): CouchDocument {
        val factory = DocumentFactory(template, this)
        val document = factory.create(data)
        return createDocument(document)
    }
    
    /**
     * Create related document
     */
    suspend fun createRelatedDocument(
        parentId: String,
        relationshipType: String,
        data: JsonObject
    ): CouchDocument {
        val relatedData = data.toMutableMap()
        relatedData["parent_id"] = JsonPrimitive(parentId)
        relatedData["relationship_type"] = JsonPrimitive(relationshipType)
        
        val id = "related-${System.currentTimeMillis()}-${Random.nextInt(1000)}"
        val document = CouchDocument(
            id = id,
            revision = "1-initial",
            data = JsonObject(relatedData)
        )
        
        val created = createDocument(document)
        
        // Track relationship
        relationshipMutex.withLock {
            relationships.getOrPut(parentId) { mutableListOf() }.add(created.id)
        }
        
        return created
    }
    
    /**
     * Get related documents
     */
    suspend fun getRelatedDocuments(parentId: String): List<CouchDocument> {
        relationshipMutex.withLock {
            val relatedIds = relationships[parentId] ?: return emptyList()
            return relatedIds.mapNotNull { id ->
                try {
                    getDocument(id)
                } catch (e: CouchDBNotFoundException) {
                    null
                }
            }
        }
    }
    
    /**
     * Create batch processor
     */
    fun createBatchProcessor(batchSize: Int): BatchProcessor {
        return BatchProcessor(batchSize, this)
    }
    
    /**
     * Register document template
     */
    suspend fun registerTemplate(template: DocumentTemplate) {
        documentTemplates[template.type] = template
    }
    
    /**
     * Get document template
     */
    suspend fun getTemplate(type: String): DocumentTemplate? {
        return documentTemplates[type]
    }
    
    // Helper methods
    
    private fun validateRelaxedDocument(document: CouchDocument) {
        // Basic validation - can be extended
        if (document.id.isEmpty()) {
            throw IllegalArgumentException("Document ID cannot be empty")
        }
        
        if (document.data.isEmpty()) {
            throw IllegalArgumentException("Document data cannot be empty")
        }
    }
    
    private fun applySchemaEvolution(
        data: MutableMap<String, JsonElement>,
        targetVersion: Int
    ): MutableMap<String, JsonElement> {
        when (targetVersion) {
            2 -> {
                // Example evolution: add timestamp fields
                data["schema_version"] = JsonPrimitive(2)
                data["migrated_at"] = JsonPrimitive(System.currentTimeMillis())
            }
            3 -> {
                // Example evolution: normalize field names
                data["schema_version"] = JsonPrimitive(3)
                data.keys.filter { it.contains("_") }.forEach { key ->
                    val camelCaseKey = key.split("_").joinToString("") { part ->
                        part.replaceFirstChar { it.uppercase() }
                    }.replaceFirstChar { it.lowercase() }
                    data[camelCaseKey] = data.remove(key)!!
                }
            }
        }
        
        return data
    }
}