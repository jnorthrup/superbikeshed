package fiduciary.protocol

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * RelaxFactory API - Extends CouchDB API with factory patterns and relaxed validation
 * 
 * From Fiduciary Omnibus Architecture:
 * - Inherits from CouchDB API
 * - Supports factory patterns for document creation
 * - Relaxed validation for flexible schemas
 * - Schema evolution support
 * - Template-based document creation
 * - Document relationship management
 * - Batch processing capabilities
 */

/**
 * Document Template for factory patterns
 */
@Serializable
data class DocumentTemplate(
    val type: String,
    val requiredFields: List<String>,
    val optionalFields: List<String> = emptyList(),
    val defaultValues: Map<String, JsonElement> = emptyMap(),
    val validationRules: Map<String, String> = emptyMap()
)

/**
 * Document Factory for creating typed documents
 */
class DocumentFactory(
    private val template: DocumentTemplate,
    private val api: RelaxFactoryAPI
) {
    suspend fun create(data: Map<String, JsonElement>): CouchDocument {
        // Validate required fields
        template.requiredFields.forEach { field ->
            if (!data.containsKey(field)) {
                throw IllegalArgumentException("Required field '$field' is missing")
            }
        }

        // Merge with default values
        val mergedData = template.defaultValues.toMutableMap()
        mergedData.putAll(data)

        // Create document with auto-generated ID
        val document = CouchDocument(
            id = "${template.type}-${generateId()}",
            revision = "",
            data = JsonObject(mergedData)
        )

        return api.createDocument(document)
    }

    private fun generateId(): String {
        return Random.nextInt(100000, 999999).toString()
    }
}

/**
 * Batch Processor for efficient bulk operations
 */
class BatchProcessor(
    private val batchSize: Int,
    private val api: RelaxFactoryAPI
) {
    private val documents = mutableListOf<CouchDocument>()

    fun add(document: CouchDocument) {
        documents.add(document)
    }

    suspend fun process(): List<BulkResult> {
        val results = mutableListOf<BulkResult>()
        val batches = documents.chunked(batchSize)

        for (batch in batches) {
            val result = api.bulkInsert(batch)
            results.add(BulkResult(
                success = result.all { true },
                processedCount = batch.size
            ))
        }

        return results
    }
}

/**
 * RelaxFactory API Implementation
 */
class RelaxFactoryAPI(
    databaseName: String
) : CouchDBAPI(databaseName) {

    private val templates = mutableMapOf<String, DocumentTemplate>()
    private val relationships = mutableMapOf<String, MutableList<String>>()

    /**
     * Create a document factory for a specific type
     */
    fun createDocumentFactory(type: String): DocumentFactory {
        val template = templates[type] ?: DocumentTemplate(
            type = type,
            requiredFields = emptyList(),
            optionalFields = emptyList()
        )
        return DocumentFactory(template, this)
    }

    /**
     * Create a relaxed document with minimal validation
     */
    suspend fun createRelaxedDocument(
        id: String,
        data: JsonObject
    ): CouchDocument {
        val document = CouchDocument(
            id = id,
            revision = "",
            data = data
        )
        return createDocument(document)
    }

    /**
     * Evolve document schema to target version
     */
    suspend fun evolveSchema(document: CouchDocument, targetVersion: Int): CouchDocument {
        val currentVersion = document.data["schema_version"]?.jsonPrimitive?.int ?: 1
        
        if (currentVersion >= targetVersion) {
            return document
        }

        val evolvedData = document.data.toMutableMap()
        evolvedData["schema_version"] = JsonPrimitive(targetVersion)

        // Apply schema evolution rules based on version
        when (targetVersion) {
            2 -> {
                // Example: Add new field with default value
                if (!evolvedData.containsKey("created_at")) {
                    evolvedData["created_at"] = JsonPrimitive(System.currentTimeMillis())
                }
            }
            3 -> {
                // Example: Rename field
                val oldValue = evolvedData.remove("old_field_name")
                if (oldValue != null) {
                    evolvedData["new_field_name"] = oldValue
                }
            }
        }

        val evolvedDocument = document.copy(
            data = JsonObject(evolvedData),
            revision = ""
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
        return factory.create(data)
    }

    /**
     * Register a document template
     */
    fun registerTemplate(template: DocumentTemplate) {
        templates[template.type] = template
    }

    /**
     * Create a document with relationship to parent
     */
    suspend fun createRelatedDocument(
        parentId: String,
        relationshipType: String,
        data: JsonObject
    ): CouchDocument {
        val document = CouchDocument(
            id = "${relationshipType}-${generateId()}",
            revision = "",
            data = data + mapOf(
                "parent_id" to JsonPrimitive(parentId),
                "relationship_type" to JsonPrimitive(relationshipType)
            )
        )

        val created = createDocument(document)

        // Store relationship
        relationships.getOrPut(parentId) { mutableListOf() }.add(created.id)

        return created
    }

    /**
     * Get related documents for a parent
     */
    suspend fun getRelatedDocuments(parentId: String): List<CouchDocument> {
        val relatedIds = relationships[parentId] ?: return emptyList()
        return relatedIds.mapNotNull { id ->
            try {
                getDocument(id)
            } catch (e: CouchDBNotFoundException) {
                null
            }
        }
    }

    /**
     * Create a batch processor
     */
    fun createBatchProcessor(batchSize: Int): BatchProcessor {
        return BatchProcessor(batchSize, this)
    }

    /**
     * Bulk insert documents with error handling
     */
    suspend fun bulkInsert(documents: List<CouchDocument>): List<CouchDocument> {
        val results = mutableListOf<CouchDocument>()
        val errors = mutableListOf<String>()

        for (document in documents) {
            try {
                val created = createDocument(document)
                results.add(created)
            } catch (e: Exception) {
                errors.add("Failed to create document ${document.id}: ${e.message}")
            }
        }

        if (errors.isNotEmpty()) {
            throw CouchDBException("Bulk insert failed: ${errors.joinToString("; ")}")
        }

        return results
    }

    /**
     * Query documents with flexible selector
     */
    suspend fun query(query: CouchQuery): List<CouchDocument> {
        // Simple in-memory query implementation
        // In a real implementation, this would use CouchDB views or Mango queries
        val allDocuments = getAllDocuments()
        
        return allDocuments.filter { document ->
            // Apply selector
            query.selector.all { (key, value) ->
                val docValue = document.data[key]
                when (value) {
                    is String -> docValue?.jsonPrimitive?.content == value
                    is Number -> docValue?.jsonPrimitive?.int == value.toInt()
                    is Boolean -> docValue?.jsonPrimitive?.boolean == value
                    else -> docValue?.toString() == value.toString()
                }
            }
        }.take(query.limit).drop(query.skip)
    }

    /**
     * Get all documents (simplified implementation)
     */
    private suspend fun getAllDocuments(): List<CouchDocument> {
        // This is a simplified implementation
        // In a real CouchDB, you would use _all_docs with include_docs=true
        return documents.values.toList()
    }

    private fun generateId(): String {
        return Random.nextInt(100000, 999999).toString()
    }
} 