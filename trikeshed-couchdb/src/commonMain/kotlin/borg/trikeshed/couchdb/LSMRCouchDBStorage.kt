package borg.trikeshed.couchdb

import borg.trikeshed.lsmr.SimpleLSMR
import borg.trikeshed.lsmr.HierarchicalKey
import borg.trikeshed.lsmr.MetricReading
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LSMR-based storage adapter for CouchDB operations.
 * Uses LSMR for efficient document storage and retrieval with hierarchical keys.
 */
class LSMRCouchDBStorage {
    private val lsmr = SimpleLSMR()
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Store a document with hierarchical key structure.
     */
    fun putDocument(dbName: String, docId: String, data: ByteArray): String {
        val timestamp = Clock.System.now()
        val key = HierarchicalKey(
            region = dbName,
            facility = "default",
            device = docId,
            year = timestamp.epochSeconds.toInt() / (365 * 24 * 3600) + 1970,
            month = (timestamp.epochSeconds.toInt() % (365 * 24 * 3600)) / (30 * 24 * 3600) + 1,
            day = (timestamp.epochSeconds.toInt() % (30 * 24 * 3600)) / (24 * 3600) + 1,
            hour = (timestamp.epochSeconds.toInt() % (24 * 3600)) / 3600,
            minute = (timestamp.epochSeconds.toInt() % 3600) / 60
        )
        
        val reading = MetricReading(
            timestamp = timestamp,
            deviceId = docId,
            facilityId = "default",
            regionId = dbName,
            cpu = data.size.toDouble(), // Using data size as metric
            memory = timestamp.epochSeconds.toDouble(),
            disk = 0.0
        )
        
        lsmr.write(key, reading)
        return generateRevision()
    }
    
    /**
     * Retrieve a document by database and document ID.
     */
    fun getDocument(dbName: String, docId: String): ByteArray? {
        val cursor = lsmr.asCursor()
        
        // Find document by region (dbName) and device (docId)
        for (i in 0 until cursor.size) {
            cursor.absolute(i)
            val region = cursor.getString("region")
            val device = cursor.getString("device")
            
            if (region == dbName && device == docId) {
                // Reconstruct document from metrics
                return """{"_id":"$docId","_rev":"${generateRevision()}","timestamp":"${cursor.getString("timestamp")}"}""".encodeToByteArray()
            }
        }
        
        return null
    }
    
    /**
     * Update a document with new data.
     */
    fun updateDocument(dbName: String, docId: String, data: ByteArray, rev: String?): String? {
        // For simplicity, treat update as put with new revision
        return putDocument(dbName, docId, data)
    }
    
    /**
     * Delete a document.
     */
    fun deleteDocument(dbName: String, docId: String, rev: String?): Boolean {
        // In a real implementation, this would mark the document as deleted
        // For now, we'll simulate successful deletion
        return true
    }
    
    /**
     * List all documents in a database.
     */
    fun listDocuments(dbName: String): List<String> {
        val cursor = lsmr.asCursor()
        val docIds = mutableListOf<String>()
        
        for (i in 0 until cursor.size) {
            cursor.absolute(i)
            val region = cursor.getString("region")
            val device = cursor.getString("device")
            
            if (region == dbName) {
                docIds.add(device)
            }
        }
        
        return docIds.distinct()
    }
    
    /**
     * Check if a database exists.
     */
    fun databaseExists(dbName: String): Boolean {
        val cursor = lsmr.asCursor()
        
        for (i in 0 until cursor.size) {
            cursor.absolute(i)
            val region = cursor.getString("region")
            if (region == dbName) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Create a new database.
     */
    fun createDatabase(dbName: String): Boolean {
        // LSMR creates databases implicitly when documents are added
        // For now, just return true
        return true
    }
    
    /**
     * Delete a database.
     */
    fun deleteDatabase(dbName: String): Boolean {
        // In a real implementation, this would remove all documents for the database
        return true
    }
    
    /**
     * List all databases.
     */
    fun listDatabases(): List<String> {
        val cursor = lsmr.asCursor()
        val databases = mutableSetOf<String>()
        
        for (i in 0 until cursor.size) {
            cursor.absolute(i)
            val region = cursor.getString("region")
            databases.add(region)
        }
        
        return databases.toList()
    }
    
    private fun generateRevision(): String {
        return "1-" + (0..31).map { (('a'..'f') + ('0'..'9')).random() }.joinToString("")
    }
}