package borg.trikeshed.quic

import borg.trikeshed.couchdb.*
import borg.trikeshed.net.*
import borg.trikeshed.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * QUIC RequestFactory Demo
 * 
 * Demonstrates:
 * - Real QUIC request/response handling
 * - CouchDB persistence for requests/responses
 * - Connection pooling and multiplexing
 * - Request metrics and monitoring
 * - Error handling and retries
 */
class QuicRequestFactoryDemo(
    private val couchDB: CouchDBClient,
    private val networkManager: NetworkManager,
    private val ioManager: IOManager
) {
    private val requestFactory = QuicRequestFactory(couchDB, networkManager, ioManager)
    
    /**
     * Demo: Basic QUIC Request/Response
     */
    suspend fun demonstrateBasicRequest() = coroutineScope {
        println("🚀 Starting Basic QUIC Request Demo")
        
        // Create a simple GET request
        val request = QuicRequest(
            id = "req-${System.currentTimeMillis()}",
            method = "GET",
            path = "https://api.example.com/users",
            headers = mapOf(
                "User-Agent" to "Trikeshed-QUIC/1.0",
                "Accept" to "application/json"
            ),
            priority = QuicRequest.RequestPriority.NORMAL,
            timeout = 30.seconds,
            retries = 3
        )
        
        println("📤 Submitting request: ${request.id}")
        val requestId = requestFactory.submitRequest(request)
        
        // Wait for response
        val response = requestFactory.getResponse(requestId, 30.seconds)
        
        println("📥 Received response:")
        println("  Status: ${response?.statusCode}")
        println("  Headers: ${response?.headers}")
        println("  Body size: ${response?.body?.size ?: 0} bytes")
        println("  Processing time: ${response?.processingTime}")
        
        // Get metrics
        val metrics = requestFactory.getRequestMetrics(requestId)
        println("📊 Request metrics:")
        println("  Start time: ${metrics?.startTime}")
        println("  End time: ${metrics?.endTime}")
        println("  Bytes sent: ${metrics?.bytesSent}")
        println("  Bytes received: ${metrics?.bytesReceived}")
        println("  Retry count: ${metrics?.retryCount}")
        println("  Error count: ${metrics?.errorCount}")
    }
    
    /**
     * Demo: Concurrent Requests with Connection Pooling
     */
    suspend fun demonstrateConcurrentRequests() = coroutineScope {
        println("🚀 Starting Concurrent Requests Demo")
        
        val requests = listOf(
            QuicRequest(
                id = "req-concurrent-1",
                method = "GET",
                path = "https://api.example.com/posts",
                priority = QuicRequest.RequestPriority.HIGH
            ),
            QuicRequest(
                id = "req-concurrent-2",
                method = "POST",
                path = "https://api.example.com/comments",
                body = """{"text": "Hello QUIC!", "author": "demo"}""".toByteArray(),
                priority = QuicRequest.RequestPriority.NORMAL
            ),
            QuicRequest(
                id = "req-concurrent-3",
                method = "PUT",
                path = "https://api.example.com/users/123",
                body = """{"name": "Updated User"}""".toByteArray(),
                priority = QuicRequest.RequestPriority.LOW
            )
        )
        
        // Submit all requests concurrently
        val requestIds = requests.map { request ->
            async {
                println("📤 Submitting ${request.id} (priority: ${request.priority})")
                requestFactory.submitRequest(request)
            }
        }.awaitAll()
        
        // Wait for all responses
        val responses = requestIds.map { requestId ->
            async {
                val response = requestFactory.getResponse(requestId, 30.seconds)
                println("📥 ${requestId}: ${response?.statusCode} (${response?.processingTime})")
                response
            }
        }.awaitAll()
        
        // Show connection pooling
        val activeConnections = requestFactory.getActiveConnections()
        println("🔗 Active connections: ${activeConnections.size}")
        activeConnections.forEach { connection ->
            println("  ${connection.id}: ${connection.remoteAddress} (${connection.state})")
        }
        
        println("✅ All concurrent requests completed")
    }
    
    /**
     * Demo: Request with Retries and Error Handling
     */
    suspend fun demonstrateErrorHandling() = coroutineScope {
        println("🚀 Starting Error Handling Demo")
        
        // Create a request that will likely fail
        val failingRequest = QuicRequest(
            id = "req-failing-${System.currentTimeMillis()}",
            method = "GET",
            path = "https://nonexistent.example.com/error",
            priority = QuicRequest.RequestPriority.NORMAL,
            retries = 3
        )
        
        println("📤 Submitting failing request: ${failingRequest.id}")
        val requestId = requestFactory.submitRequest(failingRequest)
        
        try {
            val response = requestFactory.getResponse(requestId, 10.seconds)
            println("📥 Unexpected success: ${response?.statusCode}")
        } catch (e: Exception) {
            println("❌ Expected failure: ${e.message}")
        }
        
        // Check metrics to see retry attempts
        val metrics = requestFactory.getRequestMetrics(requestId)
        println("📊 Failure metrics:")
        println("  Retry count: ${metrics?.retryCount}")
        println("  Error count: ${metrics?.errorCount}")
        
        // Query CouchDB for failed request
        val failedRequests = couchDB.queryView<Map<String, Any>>(
            "quic-requests",
            "requests",
            "by-status",
            key = "failed"
        )
        
        println("📋 Failed requests in CouchDB: ${failedRequests.rows.size}")
        failedRequests.rows.forEach { row ->
            val doc = row.value
            println("  ${doc["_id"]}: ${doc["error"]}")
        }
    }
    
    /**
     * Demo: High-Priority Request Processing
     */
    suspend fun demonstratePriorityProcessing() = coroutineScope {
        println("🚀 Starting Priority Processing Demo")
        
        // Submit requests with different priorities
        val lowPriorityRequest = QuicRequest(
            id = "req-low-${System.currentTimeMillis()}",
            method = "GET",
            path = "https://api.example.com/background",
            priority = QuicRequest.RequestPriority.LOW
        )
        
        val highPriorityRequest = QuicRequest(
            id = "req-high-${System.currentTimeMillis()}",
            method = "GET",
            path = "https://api.example.com/critical",
            priority = QuicRequest.RequestPriority.URGENT
        )
        
        // Submit low priority first
        println("📤 Submitting low priority request")
        val lowId = requestFactory.submitRequest(lowPriorityRequest)
        
        delay(100) // Small delay
        
        // Submit high priority request
        println("📤 Submitting urgent priority request")
        val highId = requestFactory.submitRequest(highPriorityRequest)
        
        // Wait for both responses
        val highResponse = async { requestFactory.getResponse(highId, 30.seconds) }
        val lowResponse = async { requestFactory.getResponse(lowId, 30.seconds) }
        
        // High priority should complete first
        val highResult = highResponse.await()
        println("📥 High priority completed: ${highResult?.statusCode}")
        
        val lowResult = lowResponse.await()
        println("📥 Low priority completed: ${lowResult?.statusCode}")
        
        // Show processing times
        println("⏱️  Processing times:")
        println("  High priority: ${highResult?.processingTime}")
        println("  Low priority: ${lowResult?.processingTime}")
    }
    
    /**
     * Demo: Connection Management
     */
    suspend fun demonstrateConnectionManagement() = coroutineScope {
        println("🚀 Starting Connection Management Demo")
        
        // Create connections to different hosts
        val connection1 = requestFactory.createConnection("api.example.com:443")
        val connection2 = requestFactory.createConnection("cdn.example.com:443")
        val connection3 = requestFactory.createConnection("api.example.com:443") // Should reuse connection1
        
        println("🔗 Created connections:")
        println("  ${connection1.id}: ${connection1.remoteAddress}")
        println("  ${connection2.id}: ${connection2.remoteAddress}")
        println("  ${connection3.id}: ${connection3.remoteAddress}")
        
        // Send requests over different connections
        val request1 = QuicRequest(
            id = "req-conn-1",
            method = "GET",
            path = "https://api.example.com/users"
        )
        
        val request2 = QuicRequest(
            id = "req-conn-2",
            method = "GET",
            path = "https://cdn.example.com/assets"
        )
        
        // Submit requests
        val req1Id = requestFactory.submitRequest(request1)
        val req2Id = requestFactory.submitRequest(request2)
        
        // Wait for responses
        val response1 = requestFactory.getResponse(req1Id, 30.seconds)
        val response2 = requestFactory.getResponse(req2Id, 30.seconds)
        
        println("📥 Responses received:")
        println("  ${req1Id}: ${response1?.statusCode}")
        println("  ${req2Id}: ${response2?.statusCode}")
        
        // Show active connections
        val activeConnections = requestFactory.getActiveConnections()
        println("🔗 Active connections after requests: ${activeConnections.size}")
        
        // Close one connection
        println("🔒 Closing connection: ${connection1.id}")
        requestFactory.closeConnection(connection1.id)
        
        val remainingConnections = requestFactory.getActiveConnections()
        println("🔗 Remaining connections: ${remainingConnections.size}")
    }
    
    /**
     * Demo: CouchDB Integration and Persistence
     */
    suspend fun demonstrateCouchDBIntegration() = coroutineScope {
        println("🚀 Starting CouchDB Integration Demo")
        
        // Submit several requests
        val requests = (1..5).map { i ->
            QuicRequest(
                id = "req-couch-$i",
                method = "GET",
                path = "https://api.example.com/data/$i",
                headers = mapOf("X-Request-ID" to "couch-demo-$i")
            )
        }
        
        val requestIds = requests.map { request ->
            requestFactory.submitRequest(request)
        }
        
        // Wait for all responses
        val responses = requestIds.map { requestId ->
            requestFactory.getResponse(requestId, 30.seconds)
        }
        
        println("📥 All requests completed")
        
        // Query CouchDB for stored data
        println("📊 CouchDB Data Analysis:")
        
        // Count requests by status
        val pendingRequests = couchDB.queryView<Map<String, Any>>(
            "quic-requests",
            "requests",
            "by-status",
            key = "pending"
        )
        
        val completedRequests = couchDB.queryView<Map<String, Any>>(
            "quic-requests",
            "requests",
            "by-status",
            key = "completed"
        )
        
        val failedRequests = couchDB.queryView<Map<String, Any>>(
            "quic-requests",
            "requests",
            "by-status",
            key = "failed"
        )
        
        println("  Pending requests: ${pendingRequests.rows.size}")
        println("  Completed requests: ${completedRequests.rows.size}")
        println("  Failed requests: ${failedRequests.rows.size}")
        
        // Show response data
        val allResponses = couchDB.queryView<Map<String, Any>>(
            "quic-responses",
            "responses",
            "by-status",
            startKey = 200,
            endKey = 299
        )
        
        println("  Successful responses: ${allResponses.rows.size}")
        
        // Show connection data
        val activeConnections = couchDB.queryView<Map<String, Any>>(
            "quic-connections",
            "connections",
            "by-state",
            key = "ESTABLISHED"
        )
        
        println("  Active connections in DB: ${activeConnections.rows.size}")
    }
    
    /**
     * Run all demos
     */
    suspend fun runAllDemos() {
        println("🎬 Running QUIC RequestFactory Demos")
        println("=" * 60)
        
        demonstrateBasicRequest()
        println("\n" + "=" * 60)
        
        demonstrateConcurrentRequests()
        println("\n" + "=" * 60)
        
        demonstrateErrorHandling()
        println("\n" + "=" * 60)
        
        demonstratePriorityProcessing()
        println("\n" + "=" * 60)
        
        demonstrateConnectionManagement()
        println("\n" + "=" * 60)
        
        demonstrateCouchDBIntegration()
        println("\n" + "=" * 60)
        
        println("✅ All QUIC RequestFactory demos completed!")
    }
} 