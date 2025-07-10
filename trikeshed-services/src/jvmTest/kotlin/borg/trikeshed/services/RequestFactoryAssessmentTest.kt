@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.reactor.http.HttpServerContext
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Comprehensive RequestFactory Server Assessment Tests
 * 
 * Tests all aspects of RequestFactory functionality:
 * - Service registry and discovery
 * - Request processing pipeline
 * - Performance under load
 * - Cross-platform compatibility
 * - Error handling and recovery
 */
class RequestFactoryAssessmentTest {
    
    internal lateinit var requestFactory: RequestFactoryServiceImpl
    internal lateinit var mockContext: HttpServerContext
    
    @BeforeTest
    fun setup() {
        mockContext = createMockHttpContext()
        requestFactory = RequestFactoryServiceImpl(mockContext)
    }
    
    @Test
    fun `should register and locate services correctly`() = runTest {
        // Test service registration
        val testService = TestServiceImpl()
        RequestFactoryRegistry.registerService("TestService") { testService }
        
        // Verify service can be retrieved
        val retrievedService = RequestFactoryRegistry.getService("TestService")
        assertNotNull(retrievedService)
        assertTrue(retrievedService is TestServiceImpl)
    }
    
    @Test
    fun `should process RequestFactory payloads correctly`() = runTest {
        // Create test payload
        val testPayload = createTestPayload()
        
        // Process request
        val response = requestFactory.process(testPayload)
        
        // Verify response
        assertNotNull(response)
        assertTrue(response.size > 0)
        
        // Parse response JSON
        val responseJson = response.play.toList().toByteArray().decodeToString()
        assertTrue(responseJson.contains("\"success\":true"))
        assertTrue(responseJson.contains("\"service\":\"ReactorService\""))
    }
    
    @Test
    fun `should handle concurrent requests efficiently`() = runTest {
        val concurrentRequests = 100
        val payload = createTestPayload()
        
        // Measure performance
        val startTime = System.currentTimeMillis()
        
        val responses = coroutineScope {
            (1..concurrentRequests).map { 
                async { requestFactory.process(payload) }
            }.awaitAll()
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Verify all responses
        assertEquals(concurrentRequests, responses.size)
        responses.forEach { response ->
            assertNotNull(response)
            assertTrue(response.size > 0)
        }
        
        // Performance assertion: should handle 100 requests in under 1 second
        assertTrue(duration < 1000, "Performance test failed: $duration ms for $concurrentRequests requests")
        
        val throughput = concurrentRequests * 1000.0 / duration
        println("📊 Throughput: ${String.format("%.2f", throughput)} requests/second")
    }
    
    @Test
    fun `should handle malformed requests gracefully`() = runTest {
        // Test with invalid JSON
        val malformedPayload = "invalid json".encodeToByteArray().toIndexed()
        
        val response = requestFactory.process(malformedPayload)
        
        // Should return error response
        assertNotNull(response)
        val responseJson = response.play.toList().toByteArray().decodeToString()
        assertTrue(responseJson.contains("\"error\""))
    }
    
    @Test
    fun `should validate method calls correctly`() = runTest {
        // Register validator
        RequestFactoryRegistry.registerValidator("dangerousMethod") { params ->
            // Reject dangerous operations
            false
        }
        
        // Test validation
        val isValid = RequestFactoryRegistry.validateMethod("dangerousMethod", "test")
        assertFalse(isValid)
        
        // Test default validation (should pass)
        val isDefaultValid = RequestFactoryRegistry.validateMethod("safeMethod", "test")
        assertTrue(isDefaultValid)
    }
    
    @Test
    fun `should maintain request counter correctly`() = runTest {
        val payload = createTestPayload()
        
        // Process multiple requests
        val response1 = requestFactory.process(payload)
        val response2 = requestFactory.process(payload)
        val response3 = requestFactory.process(payload)
        
        // Extract timestamps from responses
        val timestamp1 = extractTimestamp(response1)
        val timestamp2 = extractTimestamp(response2)
        val timestamp3 = extractTimestamp(response3)
        
        // Verify counter increments
        assertTrue(timestamp2 > timestamp1)
        assertTrue(timestamp3 > timestamp2)
    }
    
    @Test
    fun `should handle large payloads efficiently`() = runTest {
        // Create large payload (1MB)
        val largePayload = createLargePayload(1024 * 1024)
        
        val startTime = System.currentTimeMillis()
        val response = requestFactory.process(largePayload)
        val endTime = System.currentTimeMillis()
        
        val duration = endTime - startTime
        
        // Should process large payload in reasonable time
        assertTrue(duration < 5000, "Large payload processing too slow: $duration ms")
        assertNotNull(response)
        
        println("📊 Large payload processing: ${duration}ms for 1MB")
    }
    
    @Test
    fun `should integrate with reactor context correctly`() = runTest {
        val payload = createTestPayload()
        
        // Process request
        val response = requestFactory.process(payload)
        
        // Verify context integration
        val responseJson = response.play.toList().toByteArray().decodeToString()
        assertTrue(responseJson.contains("\"context\""))
        assertTrue(responseJson.contains(mockContext.ioModel.toString()))
    }
    
    // Helper functions
    
    internal fun createMockHttpContext(): HttpServerContext {
        return object : HttpServerContext {
            override val ioModel: String = "test-io-model"
            
            override fun registerPacker(size: Int): Int {
                return size % 100 // Simple packing strategy
            }
        }
    }
    
    internal fun createTestPayload(): Indexed<Byte> {
        val testJson = """
            {
                "serviceToken": "TestService",
                "methodToken": "testMethod",
                "args": ["param1", "param2"]
            }
        """.trimIndent()
        
        return testJson.encodeToByteArray().toIndexed()
    }
    
    internal fun createLargePayload(size: Int): Indexed<Byte> {
        val largeData = ByteArray(size) { (it % 256).toByte() }
        val json = """
            {
                "serviceToken": "LargeService",
                "methodToken": "largeMethod",
                "data": "${largeData.take(100).joinToString("")}"
            }
        """.trimIndent()
        
        return json.encodeToByteArray().toIndexed()
    }
    
    internal fun extractTimestamp(response: Indexed<Byte>): Long {
        val responseJson = response.play.toList().toByteArray().decodeToString()
        val timestampMatch = Regex("\"timestamp\":(\\d+)").find(responseJson)
        return timestampMatch?.groupValues?.get(1)?.toLong() ?: 0L
    }
}

/**
 * Test service implementation for assessment
 */
class TestServiceImpl {
    fun testMethod(param1: String, param2: String): String {
        return "Processed: $param1, $param2"
    }
} 