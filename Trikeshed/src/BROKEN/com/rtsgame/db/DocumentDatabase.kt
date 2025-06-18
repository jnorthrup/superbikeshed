package com.rtsgame.db

import com.rtsgame.storage.ContentAddressedStorage
import gk.kademlia.DistributedHashTable
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Interface for document database operations
 */
interface DocumentDatabase {
    /**
     * Store a document and return its ID
     */
    suspend fun putDocument(doc: Document): String

    /**
     * Retrieve a document by ID
     */
    suspend fun getDocument(id: String): Document?

    /**
     * Stream of updates for a specific document
     */
    fun watchDocument(id: String): Flow<Document>
}

/**
 * Represents a document in the database
 */
@Serializable
data class Document(
    val id: String,
    val content: Map<String, Any>,
    val attachments: Map<String, String> = emptyMap() // CID -> filename mapping
)

/**
 * Implementation using content-addressed storage and DHT
 */
class DistributedDocumentDatabase(
    private val storage: ContentAddressedStorage,
    private val dht: DistributedHashTable
) : DocumentDatabase {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun putDocument(doc: Document): String {
        // Store attachments in content-addressed storage
        val attachmentCids = doc.attachments.mapValues { (_, filename) ->
            // TODO: Implement actual file storage
            "dummy-cid"
        }

        // Create document with attachment CIDs
        val docWithCids = doc.copy(attachments = attachmentCids)
        
        // Serialize and store document
        val serialized = json.encodeToString(docWithCids)
        val docCid = storage.put(serialized.toByteArray())
        
        // Store document reference in DHT
        dht.put(doc.id.toByteArray(), docCid.toByteArray())
        
        return doc.id
    }

    override suspend fun getDocument(id: String): Document? {
        // Get document CID from DHT
        val docCid = dht.get(id.toByteArray())?.toString(Charsets.UTF_8) ?: return null
        
        // Get document from storage
        val serialized = storage.get(docCid)?.toString(Charsets.UTF_8) ?: return null
        
        return try {
            json.decodeFromString<Document>(serialized)
        } catch (e: Exception) {
            null
        }
    }

    override fun watchDocument(id: String): Flow<Document> {
        // TODO: Implement proper document watching
        throw NotImplementedError("Watch functionality not yet implemented")
    }
} 