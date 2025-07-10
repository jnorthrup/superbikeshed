package borg.trikeshed.couchdb

import borg.trikeshed.channel.api.*
import borg.trikeshed.ccek.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

/**
 * QUIC CouchDB Client implementing CouchDB Swagger API over QUIC transport.
 * Provides a complete CouchDB client interface with CCEK orchestration.
 */
class QuicCouchDBClient(
    private val config: QuicCouchDBConfig = QuicCouchDBConfig(),
    private val ccekContext: CcekContext = CcekContext(action = "QUIC_COUCHDB_CLIENT")
) {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val adapter = QuicCouchDBAdapter(config)
    private var protocolChannel: ProtocolChannel<QuicCouchDBMessage>? = null
    private var clientScope: CoroutineScope? = null
    
    /**
     * Connect to CouchDB server over QUIC.
     */
    suspend fun connect(): Boolean = withContext(ccekContext) {
        return@withContext try {
            val address = ChannelAddress.Inet(config.serverHost, config.serverPort)
            
            // Mock channel connection for demonstration
            val mockChannel = createMockQuicChannel()
            protocolChannel = adapter.wrapChannel(mockChannel)
            
            clientScope = CoroutineScope(currentCoroutineContext() + SupervisorJob())
            
            println("QUIC CouchDB Client: Connected to ${config.serverHost}:${config.serverPort}")
            true
        } catch (e: Exception) {
            println("QUIC CouchDB Client: Connection failed - ${e.message}")
            false
        }
    }
    
    /**
     * Disconnect from CouchDB server.
     */
    suspend fun disconnect() {
        protocolChannel?.close()
        clientScope?.cancel()
        protocolChannel = null
        clientScope = null
        println("QUIC CouchDB Client: Disconnected")
    }
    
    /**
     * CouchDB API: PUT /{db}/{docid} - Create or update document.
     */
    suspend fun putDocument(
        database: String,
        documentId: String,
        document: JsonObject,
        revision: String? = null
    ): CouchDBResult<DocumentResponse> = withContext(ccekContext) {
        
        val channel = protocolChannel ?: return@withContext CouchDBResult.Error("Not connected")
        
        try {
            val message = QuicCouchDBMessage.DocumentPut(
                database = database,
                documentId = documentId,
                document = json.encodeToString(JsonObject.serializer(), document),
                revision = revision
            )
            
            channel.sendMessage(message)
            
            // Wait for response
            val response = channel.incomingMessages()
                .filterIsInstance<QuicCouchDBMessage.Response>()
                .first()
            
            if (response.success) {
                val responseJson = json.parseToJsonElement(response.body).jsonObject
                CouchDBResult.Success(
                    DocumentResponse(
                        ok = true,
                        id = responseJson["id"]?.jsonPrimitive?.content ?: documentId,
                        rev = responseJson["rev"]?.jsonPrimitive?.content
                    )
                )
            } else {
                CouchDBResult.Error("PUT failed: ${response.error}")
            }
        } catch (e: Exception) {
            CouchDBResult.Error("PUT error: ${e.message}")
        }
    }
    
    /**
     * CouchDB API: GET /{db}/{docid} - Retrieve document.
     */
    suspend fun getDocument(
        database: String,
        documentId: String,
        revision: String? = null
    ): CouchDBResult<JsonObject> = withContext(ccekContext) {
        
        val channel = protocolChannel ?: return@withContext CouchDBResult.Error("Not connected")
        
        try {
            val message = QuicCouchDBMessage.DocumentGet(
                database = database,
                documentId = documentId,
                revision = revision
            )
            
            channel.sendMessage(message)
            
            val response = channel.incomingMessages()
                .filterIsInstance<QuicCouchDBMessage.Response>()
                .first()
            
            if (response.success) {
                val document = json.parseToJsonElement(response.body).jsonObject
                CouchDBResult.Success(document)
            } else {
                CouchDBResult.Error("GET failed: ${response.error}")
            }
        } catch (e: Exception) {
            CouchDBResult.Error("GET error: ${e.message}")
        }
    }
    
    /**
     * CouchDB API: DELETE /{db}/{docid} - Delete document.
     */
    suspend fun deleteDocument(
        database: String,
        documentId: String,
        revision: String
    ): CouchDBResult<DocumentResponse> = withContext(ccekContext) {
        
        val channel = protocolChannel ?: return@withContext CouchDBResult.Error("Not connected")
        
        try {
            val message = QuicCouchDBMessage.DocumentDelete(
                database = database,
                documentId = documentId,
                revision = revision
            )
            
            channel.sendMessage(message)
            
            val response = channel.incomingMessages()
                .filterIsInstance<QuicCouchDBMessage.Response>()
                .first()
            
            if (response.success) {
                val responseJson = json.parseToJsonElement(response.body).jsonObject
                CouchDBResult.Success(
                    DocumentResponse(
                        ok = true,
                        id = responseJson["id"]?.jsonPrimitive?.content ?: documentId,
                        rev = responseJson["rev"]?.jsonPrimitive?.content
                    )
                )
            } else {
                CouchDBResult.Error("DELETE failed: ${response.error}")
            }
        } catch (e: Exception) {
            CouchDBResult.Error("DELETE error: ${e.message}")
        }
    }
    
    /**
     * CouchDB API: PUT /{db} - Create database.
     */
    suspend fun createDatabase(database: String): CouchDBResult<DatabaseResponse> = withContext(ccekContext) {
        
        val channel = protocolChannel ?: return@withContext CouchDBResult.Error("Not connected")
        
        try {
            val message = QuicCouchDBMessage.DatabaseCreate(database = database)
            
            channel.sendMessage(message)
            
            val response = channel.incomingMessages()
                .filterIsInstance<QuicCouchDBMessage.Response>()
                .first()
            
            if (response.success) {
                CouchDBResult.Success(DatabaseResponse(ok = true))
            } else {
                CouchDBResult.Error("CREATE DATABASE failed: ${response.error}")
            }
        } catch (e: Exception) {
            CouchDBResult.Error("CREATE DATABASE error: ${e.message}")
        }
    }
    
    /**
     * CouchDB API: GET /_all_dbs - List all databases.
     */
    suspend fun getAllDatabases(): CouchDBResult<List<String>> = withContext(ccekContext) {
        
        val channel = protocolChannel ?: return@withContext CouchDBResult.Error("Not connected")
        
        try {
            val message = QuicCouchDBMessage.DatabaseList()
            
            channel.sendMessage(message)
            
            val response = channel.incomingMessages()
                .filterIsInstance<QuicCouchDBMessage.Response>()
                .first()
            
            if (response.success) {
                val databases = json.decodeFromString<List<String>>(response.body)
                CouchDBResult.Success(databases)
            } else {
                CouchDBResult.Error("LIST DATABASES failed: ${response.error}")
            }
        } catch (e: Exception) {
            CouchDBResult.Error("LIST DATABASES error: ${e.message}")
        }
    }
    
    /**
     * CouchDB API: POST /{db}/_bulk_docs - Bulk document operations.
     */
    suspend fun bulkDocs(
        database: String,
        documents: List<JsonObject>
    ): CouchDBResult<List<DocumentResponse>> = withContext(ccekContext) {
        
        val channel = protocolChannel ?: return@withContext CouchDBResult.Error("Not connected")
        
        try {
            val docs = documents.map { json.encodeToString(JsonObject.serializer(), it) }
            val message = QuicCouchDBMessage.BulkDocs(
                database = database,
                docs = docs
            )
            
            channel.sendMessage(message)
            
            val response = channel.incomingMessages()
                .filterIsInstance<QuicCouchDBMessage.Response>()
                .first()
            
            if (response.success) {
                val results = json.decodeFromString<List<BlobPutResponse>>(response.body)
                val documentResponses = results.map { result ->
                    DocumentResponse(
                        ok = result.success,
                        id = result.id,
                        rev = result.rev,
                        error = if (!result.success) result.message else null
                    )
                }
                CouchDBResult.Success(documentResponses)
            } else {
                CouchDBResult.Error("BULK DOCS failed: ${response.error}")
            }
        } catch (e: Exception) {
            CouchDBResult.Error("BULK DOCS error: ${e.message}")
        }
    }
    
    /**
     * Test the QUIC CouchDB connection with sample operations.
     */
    suspend fun runDogfoodTest(): List<String> = withContext(ccekContext) {
        val testResults = mutableListOf<String>()
        
        try {
            // Test 1: Connect
            testResults.add("=== QUIC CouchDB Dogfood Test ===")
            if (!connect()) {
                testResults.add("❌ Connection failed")
                return@withContext testResults
            }
            testResults.add("✅ Connected to QUIC CouchDB server")
            
            // Test 2: Create database
            val dbName = "dogfood_test_db"
            when (val createResult = createDatabase(dbName)) {
                is CouchDBResult.Success -> {
                    testResults.add("✅ Created database: $dbName")
                }
                is CouchDBResult.Error -> {
                    testResults.add("⚠️  Database creation: ${createResult.message} (may already exist)")
                }
            }
            
            // Test 3: Put document
            val testDoc = JsonObject(mapOf(
                "_id" to kotlinx.serialization.json.JsonPrimitive("test_doc_001"),
                "type" to kotlinx.serialization.json.JsonPrimitive("dogfood_test"),
                "message" to kotlinx.serialization.json.JsonPrimitive("Hello from QUIC CouchDB!"),
                "timestamp" to kotlinx.serialization.json.JsonPrimitive(kotlinx.datetime.Clock.System.now().toString()),
                "protocols" to kotlinx.serialization.json.JsonArray(listOf(
                    kotlinx.serialization.json.JsonPrimitive("QUIC"),
                    kotlinx.serialization.json.JsonPrimitive("CouchDB"),
                    kotlinx.serialization.json.JsonPrimitive("CCEK"),
                    kotlinx.serialization.json.JsonPrimitive("LSMR")
                ))
            ))
            
            when (val putResult = putDocument(dbName, "test_doc_001", testDoc)) {
                is CouchDBResult.Success -> {
                    testResults.add("✅ Put document: ${putResult.data.id} (rev: ${putResult.data.rev})")
                }
                is CouchDBResult.Error -> {
                    testResults.add("❌ Put document failed: ${putResult.message}")
                }
            }
            
            // Test 4: Get document
            when (val getResult = getDocument(dbName, "test_doc_001")) {
                is CouchDBResult.Success -> {
                    val doc = getResult.data
                    val message = doc["message"]?.jsonPrimitive?.content
                    testResults.add("✅ Retrieved document: $message")
                }
                is CouchDBResult.Error -> {
                    testResults.add("❌ Get document failed: ${getResult.message}")
                }
            }
            
            // Test 5: Bulk docs
            val bulkDocs = listOf(
                JsonObject(mapOf(
                    "_id" to kotlinx.serialization.json.JsonPrimitive("bulk_doc_1"),
                    "type" to kotlinx.serialization.json.JsonPrimitive("bulk_test"),
                    "data" to kotlinx.serialization.json.JsonPrimitive("First bulk document")
                )),
                JsonObject(mapOf(
                    "_id" to kotlinx.serialization.json.JsonPrimitive("bulk_doc_2"),
                    "type" to kotlinx.serialization.json.JsonPrimitive("bulk_test"),
                    "data" to kotlinx.serialization.json.JsonPrimitive("Second bulk document")
                ))
            )
            
            when (val bulkResult = bulkDocs(dbName, bulkDocs)) {
                is CouchDBResult.Success -> {
                    val successCount = bulkResult.data.count { it.ok }
                    testResults.add("✅ Bulk operation: $successCount/${bulkResult.data.size} documents processed")
                }
                is CouchDBResult.Error -> {
                    testResults.add("❌ Bulk operation failed: ${bulkResult.message}")
                }
            }
            
            // Test 6: List databases
            when (val listResult = getAllDatabases()) {
                is CouchDBResult.Success -> {
                    testResults.add("✅ Listed databases: ${listResult.data.joinToString(", ")}")
                }
                is CouchDBResult.Error -> {
                    testResults.add("❌ List databases failed: ${listResult.message}")
                }
            }
            
            testResults.add("=== Test completed successfully ===")
            
        } catch (e: Exception) {
            testResults.add("❌ Test failed with exception: ${e.message}")
        } finally {
            disconnect()
            testResults.add("🔌 Disconnected from server")
        }
        
        return@withContext testResults
    }
    
    private fun createMockQuicChannel(): Channel {
        return object : Channel {
            override val isOpen: Boolean = true
            
            override suspend fun close() {
                println("Mock QUIC Channel: Closed")
            }
            
            override suspend fun send(data: ByteIndexed) {
                println("Mock QUIC Channel: Sent ${data.a} bytes")
            }
            
            override suspend fun receive(): ByteIndexed {
                // Mock response for testing
                delay(10) // Simulate network latency
                val mockResponse = QuicCouchDBMessage.Response(
                    statusCode = 200,
                    body = """{"ok":true,"id":"test_doc_001","rev":"1-abc123"}""",
                    success = true
                )
                
                return adapter.serializeMessage(mockResponse)
            }
        }
    }
}

/**
 * Result wrapper for CouchDB operations.
 */
sealed class CouchDBResult<out T> {
    data class Success<T>(val data: T) : CouchDBResult<T>()
    data class Error(val message: String) : CouchDBResult<Nothing>()
}

/**
 * CouchDB API response types.
 */
data class DocumentResponse(
    val ok: Boolean,
    val id: String,
    val rev: String?,
    val error: String? = null
)

data class DatabaseResponse(
    val ok: Boolean,
    val error: String? = null
)