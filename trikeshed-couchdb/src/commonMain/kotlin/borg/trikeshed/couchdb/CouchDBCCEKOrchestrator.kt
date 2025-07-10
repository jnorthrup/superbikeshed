package borg.trikeshed.couchdb

import borg.trikeshed.ccek.*
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CCEK orchestrator for CouchDB operations.
 * Provides execution context, validation, transformation, and serialization
 * for all CouchDB blob and document operations.
 */
class CouchDBCCEKOrchestrator {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Execute CouchDB PUT operation with CCEK orchestration.
     */
    suspend fun executeput(
        dbName: String,
        docId: String,
        data: ByteArray,
        storage: LSMRCouchDBStorage,
        baseContext: CoroutineContext
    ): BlobPutResponse {
        
        // Create CCEK context for this operation
        val ccekContext = CcekContext(
            action = "COUCHDB_PUT",
            payload = Triple(dbName, docId, data),
            phase = ExecutionPhase.INIT,
            validator = { payload ->
                // Validate CouchDB document structure
                when (payload) {
                    is Triple<*, *, *> -> {
                        val (db, id, content) = payload as Triple<String, String, ByteArray>
                        db.isNotBlank() && id.isNotBlank() && content.isNotEmpty()
                    }
                    else -> false
                }
            }
        )
        
        val combinedContext = baseContext + ccekContext
        
        return withContext(combinedContext) {
            // Create transformation pipeline for CouchDB PUT
            val pipeline = ccekPipeline("couchdb_put") {
                validate("document_structure", "json_format", "required_fields")
                transform("add_metadata", "normalize_fields", "validate_revision")
                serialize(SerializationFormat.JSON)
                metadata("operation", "PUT")
                metadata("database", dbName)
                metadata("document_id", docId)
            }
            
            // Execute through CCEK engine
            val engine = CCEKEngine(ccekContext)
            
            try {
                // Phase 1: Validation
                val validationResult = engine.execute(
                    Triple(dbName, docId, data),
                    pipeline
                )
                
                when (validationResult) {
                    is ExecutionResult.Success -> {
                        // Phase 2: Transform and store
                        val enhancedData = enhanceDocumentWithMetadata(data, docId)
                        val revision = storage.putDocument(dbName, docId, enhancedData)
                        
                        // Phase 3: Response generation
                        BlobPutResponse(
                            id = docId,
                            success = true,
                            message = "Document stored successfully via CCEK orchestration",
                            rev = revision
                        )
                    }
                    is ExecutionResult.Error -> {
                        BlobPutResponse(
                            id = docId,
                            success = false,
                            message = "CCEK validation failed: ${validationResult.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                BlobPutResponse(
                    id = docId,
                    success = false,
                    message = "CCEK execution error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Execute CouchDB GET operation with CCEK orchestration.
     */
    suspend fun executeGet(
        dbName: String,
        docId: String,
        storage: LSMRCouchDBStorage,
        baseContext: CoroutineContext
    ): BlobGetResponse {
        
        val ccekContext = CcekContext(
            action = "COUCHDB_GET",
            payload = Pair(dbName, docId),
            phase = ExecutionPhase.INIT,
            validator = { payload ->
                when (payload) {
                    is Pair<*, *> -> {
                        val (db, id) = payload as Pair<String, String>
                        db.isNotBlank() && id.isNotBlank()
                    }
                    else -> false
                }
            }
        )
        
        val combinedContext = baseContext + ccekContext
        
        return withContext(combinedContext) {
            val pipeline = ccekPipeline("couchdb_get") {
                validate("document_exists", "access_permissions")
                transform("extract_metadata", "filter_fields")
                serialize(SerializationFormat.JSON)
                metadata("operation", "GET")
                metadata("database", dbName)
                metadata("document_id", docId)
            }
            
            val engine = CCEKEngine(ccekContext)
            
            try {
                val result = engine.execute(Pair(dbName, docId), pipeline)
                
                when (result) {
                    is ExecutionResult.Success -> {
                        val data = storage.getDocument(dbName, docId)
                        BlobGetResponse(
                            id = docId,
                            data = data,
                            found = data != null
                        )
                    }
                    is ExecutionResult.Error -> {
                        BlobGetResponse(
                            id = docId,
                            data = null,
                            found = false
                        )
                    }
                }
            } catch (e: Exception) {
                BlobGetResponse(
                    id = docId,
                    data = null,
                    found = false
                )
            }
        }
    }
    
    /**
     * Execute CouchDB UPDATE operation with CCEK orchestration.
     */
    suspend fun executeUpdate(
        dbName: String,
        docId: String,
        data: ByteArray,
        revision: String?,
        storage: LSMRCouchDBStorage,
        baseContext: CoroutineContext
    ): BlobUpdateResponse {
        
        val ccekContext = CcekContext(
            action = "COUCHDB_UPDATE",
            payload = Quadruple(dbName, docId, data, revision),
            phase = ExecutionPhase.INIT,
            validator = { payload ->
                when (payload) {
                    is Quadruple<*, *, *, *> -> {
                        val (db, id, content, rev) = payload as Quadruple<String, String, ByteArray, String?>
                        db.isNotBlank() && id.isNotBlank() && content.isNotEmpty()
                    }
                    else -> false
                }
            }
        )
        
        val combinedContext = baseContext + ccekContext
        
        return withContext(combinedContext) {
            val pipeline = ccekPipeline("couchdb_update") {
                validate("document_exists", "revision_match", "update_permissions")
                transform("merge_changes", "update_metadata", "increment_revision")
                serialize(SerializationFormat.JSON)
                metadata("operation", "UPDATE")
                metadata("database", dbName)
                metadata("document_id", docId)
                metadata("revision", revision ?: "unknown")
            }
            
            val engine = CCEKEngine(ccekContext)
            
            try {
                val result = engine.execute(
                    Quadruple(dbName, docId, data, revision),
                    pipeline
                )
                
                when (result) {
                    is ExecutionResult.Success -> {
                        val newRevision = storage.updateDocument(dbName, docId, data, revision)
                        if (newRevision != null) {
                            BlobUpdateResponse(
                                id = docId,
                                success = true,
                                message = "Document updated successfully via CCEK orchestration",
                                rev = newRevision
                            )
                        } else {
                            BlobUpdateResponse(
                                id = docId,
                                success = false,
                                message = "Document not found or revision conflict"
                            )
                        }
                    }
                    is ExecutionResult.Error -> {
                        BlobUpdateResponse(
                            id = docId,
                            success = false,
                            message = "CCEK validation failed: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                BlobUpdateResponse(
                    id = docId,
                    success = false,
                    message = "CCEK execution error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Execute CouchDB DELETE operation with CCEK orchestration.
     */
    suspend fun executeDelete(
        dbName: String,
        docId: String,
        revision: String?,
        storage: LSMRCouchDBStorage,
        baseContext: CoroutineContext
    ): BlobDeleteResponse {
        
        val ccekContext = CcekContext(
            action = "COUCHDB_DELETE",
            payload = Triple(dbName, docId, revision),
            phase = ExecutionPhase.INIT,
            validator = { payload ->
                when (payload) {
                    is Triple<*, *, *> -> {
                        val (db, id, rev) = payload as Triple<String, String, String?>
                        db.isNotBlank() && id.isNotBlank()
                    }
                    else -> false
                }
            }
        )
        
        val combinedContext = baseContext + ccekContext
        
        return withContext(combinedContext) {
            val pipeline = ccekPipeline("couchdb_delete") {
                validate("document_exists", "revision_match", "delete_permissions")
                transform("mark_deleted", "preserve_history")
                serialize(SerializationFormat.JSON)
                metadata("operation", "DELETE")
                metadata("database", dbName)
                metadata("document_id", docId)
                metadata("revision", revision ?: "unknown")
            }
            
            val engine = CCEKEngine(ccekContext)
            
            try {
                val result = engine.execute(
                    Triple(dbName, docId, revision),
                    pipeline
                )
                
                when (result) {
                    is ExecutionResult.Success -> {
                        val deleted = storage.deleteDocument(dbName, docId, revision)
                        if (deleted) {
                            BlobDeleteResponse(
                                id = docId,
                                success = true,
                                message = "Document deleted successfully via CCEK orchestration",
                                rev = generateDeletionRevision()
                            )
                        } else {
                            BlobDeleteResponse(
                                id = docId,
                                success = false,
                                message = "Document not found or revision conflict"
                            )
                        }
                    }
                    is ExecutionResult.Error -> {
                        BlobDeleteResponse(
                            id = docId,
                            success = false,
                            message = "CCEK validation failed: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                BlobDeleteResponse(
                    id = docId,
                    success = false,
                    message = "CCEK execution error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Execute bulk operations with CCEK orchestration.
     */
    suspend fun executeBulkDocs(
        dbName: String,
        docs: List<ByteArray>,
        storage: LSMRCouchDBStorage,
        baseContext: CoroutineContext
    ): BulkDocsResponse {
        
        val ccekContext = CcekContext(
            action = "COUCHDB_BULK_DOCS",
            payload = Pair(dbName, docs),
            phase = ExecutionPhase.INIT,
            validator = { payload ->
                when (payload) {
                    is Pair<*, *> -> {
                        val (db, documents) = payload as Pair<String, List<ByteArray>>
                        db.isNotBlank() && documents.isNotEmpty()
                    }
                    else -> false
                }
            }
        )
        
        val combinedContext = baseContext + ccekContext
        
        return withContext(combinedContext) {
            val pipeline = ccekPipeline("couchdb_bulk_docs") {
                validate("bulk_document_format", "batch_size_limits")
                transform("batch_process", "atomic_operations")
                serialize(SerializationFormat.JSON)
                metadata("operation", "BULK_DOCS")
                metadata("database", dbName)
                metadata("document_count", docs.size.toString())
            }
            
            val engine = CCEKEngine(ccekContext)
            val results = mutableListOf<BlobPutResponse>()
            
            try {
                val validationResult = engine.execute(Pair(dbName, docs), pipeline)
                
                when (validationResult) {
                    is ExecutionResult.Success -> {
                        docs.forEach { docBytes ->
                            try {
                                val docJson = json.parseToJsonElement(docBytes.decodeToString()).jsonObject
                                val id = docJson["_id"]?.jsonPrimitive?.content
                                
                                if (id != null) {
                                    val revision = storage.putDocument(dbName, id, docBytes)
                                    results.add(BlobPutResponse(id, true, "Document processed via CCEK", revision))
                                } else {
                                    results.add(BlobPutResponse("", false, "Document missing _id field"))
                                }
                            } catch (e: Exception) {
                                results.add(BlobPutResponse("", false, "JSON parsing error: ${e.message}"))
                            }
                        }
                        
                        BulkDocsResponse(
                            results = results,
                            success = true,
                            message = "Bulk operation completed via CCEK orchestration"
                        )
                    }
                    is ExecutionResult.Error -> {
                        BulkDocsResponse(
                            results = emptyList(),
                            success = false,
                            message = "CCEK validation failed: ${validationResult.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                BulkDocsResponse(
                    results = emptyList(),
                    success = false,
                    message = "CCEK execution error: ${e.message}"
                )
            }
        }
    }
    
    private fun enhanceDocumentWithMetadata(data: ByteArray, docId: String): ByteArray {
        return try {
            val docJson = json.parseToJsonElement(data.decodeToString()).jsonObject.toMutableMap()
            docJson["_ccek_processed"] = kotlinx.serialization.json.JsonPrimitive(true)
            docJson["_ccek_timestamp"] = kotlinx.serialization.json.JsonPrimitive(kotlinx.datetime.Clock.System.now().toString())
            json.encodeToString(JsonObject(docJson)).encodeToByteArray()
        } catch (e: Exception) {
            data
        }
    }
    
    private fun generateDeletionRevision(): String {
        return "2-deleted-" + (0..15).map { (('a'..'f') + ('0'..'9')).random() }.joinToString("")
    }
}

/**
 * Helper data class for quadruple values.
 */
data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)