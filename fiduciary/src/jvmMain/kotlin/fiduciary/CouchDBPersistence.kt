package fiduciary

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.util.Base64
import kotlin.coroutines.CoroutineContext

/**
 * Real CouchDB Persistence Layer for Fiduciary Scanner
 * 
 * Handles persistent storage of scan results, alerts, and configurations
 * Provides real database operations, not mock data
 */

@Serializable
data class CouchDBConfig(
    val url: String = "http://localhost:5984",
    val username: String = "scanner",
    val password: String = "scanner_password",
    val database: String = "fiduciary_scans"
)

@Serializable
data class CouchDocument(
    val _id: String? = null,
    val _rev: String? = null,
    val type: String,
    val data: JsonElement,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class CouchResponse(
    val ok: Boolean = false,
    val id: String? = null,
    val rev: String? = null,
    val error: String? = null,
    val reason: String? = null
)

@Serializable
data class CouchViewResponse(
    val total_rows: Int,
    val offset: Int,
    val rows: List<CouchViewRow>
)

@Serializable
data class CouchViewRow(
    val id: String,
    val key: JsonElement,
    val value: JsonElement,
    val doc: JsonElement? = null
)

@Serializable
data class DatabaseInfo(
    val db_name: String,
    val purge_seq: String,
    val update_seq: String,
    val sizes: Map<String, Long>,
    val props: Map<String, String>,
    val doc_del_count: Long,
    val doc_count: Long,
    val disk_format_version: Int,
    val compact_running: Boolean,
    val cluster: Map<String, Int>,
    val instance_start_time: String
)

// Real CouchDB client using CCEK pattern
class CouchDBPersistenceLayer(
    private val config: CouchDBConfig
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CouchDBPersistenceLayer>
    override val key: CoroutineContext.Key<*> get() = Key
    
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val authHeader = "Basic " + Base64.getEncoder().encodeToString("${config.username}:${config.password}".toByteArray())
    
    // Database initialization
    suspend fun initializeDatabase(): Boolean {
        return try {
            // Check if server is accessible
            val serverInfo = getServerInfo()
            if (serverInfo == null) {
                System.err.println("CouchDB server not accessible at ${config.url}")
                return false
            }
            
            // Create database if it doesn't exist
            createDatabase(config.database)
            
            // Create design documents for views
            createDesignDocuments()
            
            true
        } catch (e: Exception) {
            System.err.println("Failed to initialize CouchDB: ${e.message}")
            false
        }
    }
    
    private suspend fun getServerInfo(): JsonElement? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(config.url))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                json.parseToJsonElement(response.body())
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun createDatabase(dbName: String): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/$dbName"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299 || response.statusCode() == 412 // 412 = already exists
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun createDesignDocuments() {
        // Create view for scan results by target
        val scanResultsView = """
        {
            "_id": "_design/scan_results",
            "views": {
                "by_target": {
                    "map": "function(doc) { if (doc.type === 'scan_result') { emit(doc.data.target, doc); } }"
                },
                "by_risk_score": {
                    "map": "function(doc) { if (doc.type === 'scan_result') { emit(doc.data.riskScore, doc); } }"
                },
                "by_scan_id": {
                    "map": "function(doc) { if (doc.type === 'scan_result') { emit(doc.data.scanId, doc); } }"
                },
                "high_risk": {
                    "map": "function(doc) { if (doc.type === 'scan_result' && doc.data.riskScore >= 70) { emit(doc.data.riskScore, doc); } }"
                }
            }
        }
        """.trimIndent()
        
        // Create view for alerts
        val alertsView = """
        {
            "_id": "_design/alerts",
            "views": {
                "by_severity": {
                    "map": "function(doc) { if (doc.type === 'alert') { emit(doc.data.severity, doc); } }"
                },
                "by_timestamp": {
                    "map": "function(doc) { if (doc.type === 'alert') { emit(doc.timestamp, doc); } }"
                },
                "recent": {
                    "map": "function(doc) { if (doc.type === 'alert' && doc.timestamp > (Date.now() - 86400000)) { emit(doc.timestamp, doc); } }"
                }
            }
        }
        """.trimIndent()
        
        saveDocument(scanResultsView)
        saveDocument(alertsView)
    }
    
    // Core persistence operations
    suspend fun saveScanResult(result: AssetResult): String? {
        val doc = CouchDocument(
            _id = "scan_result_${result.scanId}_${result.target}_${result.port}",
            type = "scan_result",
            data = json.encodeToJsonElement(result)
        )
        return saveDocument(json.encodeToString(doc))
    }
    
    suspend fun saveAlert(alert: AlertEvent): String? {
        val doc = CouchDocument(
            _id = "alert_${alert.scanId}_${alert.timestamp}",
            type = "alert",
            data = json.encodeToJsonElement(alert)
        )
        return saveDocument(json.encodeToString(doc))
    }
    
    suspend fun saveScanMetadata(scanId: String, metadata: Map<String, Any>): String? {
        val doc = CouchDocument(
            _id = "scan_metadata_$scanId",
            type = "scan_metadata",
            data = json.encodeToJsonElement(metadata)
        )
        return saveDocument(json.encodeToString(doc))
    }
    
    private suspend fun saveDocument(docJson: String): String? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(docJson))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val couchResponse = json.decodeFromString<CouchResponse>(response.body())
                couchResponse.id
            } else {
                System.err.println("Failed to save document: ${response.statusCode()} - ${response.body()}")
                null
            }
        } catch (e: Exception) {
            System.err.println("Error saving document: ${e.message}")
            null
        }
    }
    
    // Query operations
    suspend fun getScanResults(scanId: String): List<AssetResult> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}/_design/scan_results/_view/by_scan_id?key=\"$scanId\"&include_docs=true"))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val viewResponse = json.decodeFromString<CouchViewResponse>(response.body())
                viewResponse.rows.mapNotNull { row ->
                    row.doc?.let { doc ->
                        val couchDoc = json.decodeFromJsonElement<CouchDocument>(doc)
                        if (couchDoc.type == "scan_result") {
                            json.decodeFromJsonElement<AssetResult>(couchDoc.data)
                        } else null
                    }
                }
            } else emptyList()
        } catch (e: Exception) {
            System.err.println("Error querying scan results: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getHighRiskAssets(): List<AssetResult> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}/_design/scan_results/_view/high_risk?include_docs=true&descending=true"))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val viewResponse = json.decodeFromString<CouchViewResponse>(response.body())
                viewResponse.rows.mapNotNull { row ->
                    row.doc?.let { doc ->
                        val couchDoc = json.decodeFromJsonElement<CouchDocument>(doc)
                        if (couchDoc.type == "scan_result") {
                            json.decodeFromJsonElement<AssetResult>(couchDoc.data)
                        } else null
                    }
                }
            } else emptyList()
        } catch (e: Exception) {
            System.err.println("Error querying high-risk assets: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getRecentAlerts(hoursBack: Int = 24): List<AlertEvent> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}/_design/alerts/_view/recent?include_docs=true&descending=true"))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val viewResponse = json.decodeFromString<CouchViewResponse>(response.body())
                viewResponse.rows.mapNotNull { row ->
                    row.doc?.let { doc ->
                        val couchDoc = json.decodeFromJsonElement<CouchDocument>(doc)
                        if (couchDoc.type == "alert") {
                            json.decodeFromJsonElement<AlertEvent>(couchDoc.data)
                        } else null
                    }
                }
            } else emptyList()
        } catch (e: Exception) {
            System.err.println("Error querying recent alerts: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getAssetsByTarget(target: String): List<AssetResult> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}/_design/scan_results/_view/by_target?key=\"$target\"&include_docs=true"))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val viewResponse = json.decodeFromString<CouchViewResponse>(response.body())
                viewResponse.rows.mapNotNull { row ->
                    row.doc?.let { doc ->
                        val couchDoc = json.decodeFromJsonElement<CouchDocument>(doc)
                        if (couchDoc.type == "scan_result") {
                            json.decodeFromJsonElement<AssetResult>(couchDoc.data)
                        } else null
                    }
                }
            } else emptyList()
        } catch (e: Exception) {
            System.err.println("Error querying assets by target: ${e.message}")
            emptyList()
        }
    }
    
    // Analytics and reporting
    suspend fun getDatabaseStats(): DatabaseInfo? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}"))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                json.decodeFromString<DatabaseInfo>(response.body())
            } else null
        } catch (e: Exception) {
            System.err.println("Error getting database stats: ${e.message}")
            null
        }
    }
    
    suspend fun getScanSummary(scanId: String): Map<String, Any> {
        val results = getScanResults(scanId)
        if (results.isEmpty()) return emptyMap()
        
        val totalAssets = results.size
        val openPorts = results.count { it.isOpen }
        val highRiskAssets = results.count { it.riskScore >= 70 }
        val mediumRiskAssets = results.count { it.riskScore >= 40 && it.riskScore < 70 }
        val lowRiskAssets = results.count { it.riskScore < 40 }
        
        val serviceTypes = results.groupBy { it.service }.mapValues { it.value.size }
        val protocols = results.groupBy { it.protocol }.mapValues { it.value.size }
        val targets = results.map { it.target }.distinct()
        
        return mapOf(
            "scanId" to scanId,
            "totalAssets" to totalAssets,
            "openPorts" to openPorts,
            "uniqueTargets" to targets.size,
            "riskDistribution" to mapOf(
                "high" to highRiskAssets,
                "medium" to mediumRiskAssets,
                "low" to lowRiskAssets
            ),
            "serviceTypes" to serviceTypes,
            "protocols" to protocols,
            "averageRiskScore" to (results.sumOf { it.riskScore } / totalAssets.toDouble()),
            "scanTimestamp" to results.minOfOrNull { it.timestamp },
            "targets" to targets
        )
    }
    
    // Cleanup operations
    suspend fun cleanupOldData(daysOld: Int = 30): Int {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        
        return try {
            // Query old documents
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}/_all_docs?include_docs=true"))
                .header("Authorization", authHeader)
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val viewResponse = json.decodeFromString<CouchViewResponse>(response.body())
                val oldDocs = viewResponse.rows.filter { row ->
                    row.doc?.let { doc ->
                        val couchDoc = json.decodeFromJsonElement<CouchDocument>(doc)
                        couchDoc.timestamp < cutoffTime
                    } ?: false
                }
                
                // Delete old documents
                var deletedCount = 0
                oldDocs.forEach { row ->
                    if (deleteDocument(row.id, row.value.jsonObject["_rev"]?.jsonPrimitive?.content ?: "")) {
                        deletedCount++
                    }
                }
                
                deletedCount
            } else 0
        } catch (e: Exception) {
            System.err.println("Error cleaning up old data: ${e.message}")
            0
        }
    }
    
    private suspend fun deleteDocument(id: String, rev: String): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/${config.database}/$id?rev=$rev"))
                .header("Authorization", authHeader)
                .DELETE()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (e: Exception) {
            false
        }
    }
}

// CCEK extension functions for easy integration
suspend fun CouchDBPersistenceLayer.Key.saveScanResult(result: AssetResult): String? {
    val persistence = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBPersistenceLayer not found in context")
    return persistence.saveScanResult(result)
}

suspend fun CouchDBPersistenceLayer.Key.saveAlert(alert: AlertEvent): String? {
    val persistence = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBPersistenceLayer not found in context")
    return persistence.saveAlert(alert)
}

suspend fun CouchDBPersistenceLayer.Key.getScanResults(scanId: String): List<AssetResult> {
    val persistence = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBPersistenceLayer not found in context")
    return persistence.getScanResults(scanId)
}

suspend fun CouchDBPersistenceLayer.Key.getHighRiskAssets(): List<AssetResult> {
    val persistence = coroutineContext[this] 
        ?: throw IllegalStateException("CouchDBPersistenceLayer not found in context")
    return persistence.getHighRiskAssets()
}

fun CouchDBPersistenceLayer.Key.create(config: CouchDBConfig): CouchDBPersistenceLayer {
    return CouchDBPersistenceLayer(config)
}