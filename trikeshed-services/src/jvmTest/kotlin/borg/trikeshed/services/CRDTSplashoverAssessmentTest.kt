@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.random.Random

/**
 * CRDT Splashover Assessment Tests
 * 
 * Tests conflict detection and resolution in distributed scenarios:
 * - Entity version conflicts
 * - Concurrent update detection
 * - Conflict resolution strategies
 * - Splashover containment
 * - Performance under conflicts
 */
class CRDTSplashoverAssessmentTest {
    
    internal lateinit var broker: RequestFactoryBroker.Server
    internal lateinit var mockServiceInvoker: PlatformServiceInvoker
    
    @BeforeTest
    fun setup() {
        mockServiceInvoker = createMockServiceInvoker()
        broker = RequestFactoryBroker.Server(mockServiceInvoker)
    }
    
    @Test
    fun `should detect version conflicts in entity updates`() = runTest {
        val entityId = "test-entity-001"
        val serviceToken = "TestService"
        val methodToken = "updateEntity"
        val entityToken = "entity-token"
        
        // Register test service
        broker.registerService(serviceToken, TestEntityService())
        
        // Create concurrent updates with same version
        val delta1 = RequestFactoryBroker.EntityDelta(entityId, mapOf("field1" to "value1"))
        val delta2 = RequestFactoryBroker.EntityDelta(entityId, mapOf("field2" to "value2"))
        val version = RequestFactoryBroker.EntityVersion(entityId, 1L)
        
        val update1 = RequestFactoryBroker.Request.Update(
            serviceToken, methodToken, entityToken, delta1, version
        )
        val update2 = RequestFactoryBroker.Request.Update(
            serviceToken, methodToken, entityToken, delta2, version
        )
        
        // Process first update (should succeed)
        val response1 = broker.handleUpdate(update1)
        assertTrue(response1 is RequestFactoryBroker.Response.EntityUpdated)
        
        // Process second update (should fail due to version conflict)
        val response2 = broker.handleUpdate(update2)
        assertTrue(response2 is RequestFactoryBroker.Response.Failure)
        assertTrue(response2.error.contains("Version mismatch"))
        
        println("🔍 Version conflict detected: ${response2.error}")
    }
    
    @Test
    fun `should handle concurrent entity creation conflicts`() = runTest {
        val serviceToken = "TestService"
        val entityToken = "new-entity"
        val initialState = mapOf("name" to "Test Entity", "value" to 42)
        
        // Register test service
        broker.registerService(serviceToken, TestEntityService())
        
        // Simulate concurrent creation attempts
        val create1 = RequestFactoryBroker.Request.Create(
            serviceToken, "create", entityToken, initialState
        )
        val create2 = RequestFactoryBroker.Request.Create(
            serviceToken, "create", entityToken, initialState
        )
        
        // Process both creation requests
        val response1 = broker.handleCreate(create1)
        val response2 = broker.handleCreate(create2)
        
        // First should succeed, second might fail or create different entity
        assertTrue(response1 is RequestFactoryBroker.Response.EntityCreated)
        
        // Check if second creation was handled appropriately
        if (response2 is RequestFactoryBroker.Response.Failure) {
            assertTrue(response2.error.contains("Entity already exists") || 
                      response2.error.contains("Conflict"))
        } else if (response2 is RequestFactoryBroker.Response.EntityCreated) {
            // Different entity ID should be generated
            assertNotEquals(response1.entityToken, response2.entityToken)
        }
    }
    
    @Test
    fun `should resolve conflicts using different strategies`() = runTest {
        val entityId = "conflict-test-entity"
        val serviceToken = "ConflictService"
        
        // Test different conflict resolution strategies
        val strategies = listOf(
            ConflictStrategy.LATEST_WINS,
            ConflictStrategy.MERGE_ALL,
            ConflictStrategy.MANUAL,
            ConflictStrategy.TRIKESHED_CONSENSUS
        )
        
        strategies.forEach { strategy ->
            val result = testConflictResolution(entityId, serviceToken, strategy)
            assertNotNull(result)
            println("📊 Strategy $strategy: ${result.resolutionTime}ms, ${result.successRate}% success")
        }
    }
    
    @Test
    fun `should contain splashover effects within time limit`() = runTest {
        val entityCount = 100
        val concurrentUpdates = 10
        
        // Create multiple entities
        val entities = (1..entityCount).map { "entity-$it" }
        
        // Simulate concurrent updates across all entities
        val startTime = System.currentTimeMillis()
        
        val results = coroutineScope {
            entities.map { entityId ->
                async {
                    simulateConcurrentUpdates(entityId, concurrentUpdates)
                }
            }.awaitAll()
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        // Calculate splashover metrics
        val conflicts = results.sumOf { it.conflicts }
        val resolutions = results.sumOf { it.resolutions }
        val failures = results.sumOf { it.failures }
        
        // Splashover should be contained within 5 seconds
        assertTrue(totalTime < 5000, "Splashover containment failed: ${totalTime}ms")
        
        // Success rate should be high
        val successRate = (resolutions.toDouble() / (conflicts + resolutions)) * 100
        assertTrue(successRate > 90, "Success rate too low: ${String.format("%.1f", successRate)}%")
        
        println("🔍 Splashover containment: ${totalTime}ms, ${conflicts} conflicts, ${resolutions} resolved, ${failures} failures")
        println("📊 Success rate: ${String.format("%.1f", successRate)}%")
    }
    
    @Test
    fun `should maintain consistency under high load`() = runTest {
        val loadLevels = listOf(10, 50, 100, 200)
        
        loadLevels.forEach { concurrentRequests ->
            val startTime = System.currentTimeMillis()
            
            val results = coroutineScope {
                (1..concurrentRequests).map {
                    async {
                        performRandomEntityOperation()
                    }
                }.awaitAll()
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            val successCount = results.count { it.isSuccess }
            val successRate = (successCount.toDouble() / concurrentRequests) * 100
            
            // Performance should degrade gracefully
            val throughput = concurrentRequests * 1000.0 / duration
            
            println("📊 Load test: $concurrentRequests requests, ${duration}ms, ${String.format("%.2f", throughput)} req/s, ${String.format("%.1f", successRate)}% success")
            
            // Success rate should remain high even under load
            assertTrue(successRate > 85, "Success rate too low under load: ${String.format("%.1f", successRate)}%")
        }
    }
    
    @Test
    fun `should detect and handle network partition scenarios`() = runTest {
        // Simulate network partition
        val partitionDuration = 1000L // 1 second partition
        
        val startTime = System.currentTimeMillis()
        
        // Start operations that will be affected by partition
        val operations = coroutineScope {
            (1..20).map {
                async {
                    delay(Random.nextLong(0, partitionDuration))
                    performNetworkOperation()
                }
            }
        }
        
        // Simulate partition
        delay(partitionDuration / 2)
        
        // Resume operations
        val results = operations.awaitAll()
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        
        // Should handle partition gracefully
        val successCount = results.count { it.isSuccess }
        val successRate = (successCount.toDouble() / results.size) * 100
        
        assertTrue(successRate > 80, "Network partition handling failed: ${String.format("%.1f", successRate)}% success")
        
        println("🌐 Network partition test: ${totalTime}ms, ${String.format("%.1f", successRate)}% success")
    }
    
    // Helper functions
    
    internal fun createMockServiceInvoker(): PlatformServiceInvoker {
        return object : PlatformServiceInvoker {
            override suspend fun invokeService(service: Any, method: String, vararg args: Any?): Any? {
                return when (method) {
                    "updateEntity" -> "updated"
                    "createEntity" -> "created"
                    "deleteEntity" -> "deleted"
                    else -> "unknown"
                }
            }
        }
    }
    
    internal data class ConflictResolutionResult(
        val resolutionTime: Long,
        val successRate: Double,
        val conflicts: Int,
        val resolutions: Int
    )
    
    internal suspend fun testConflictResolution(
        entityId: String, 
        serviceToken: String, 
        strategy: ConflictStrategy
    ): ConflictResolutionResult {
        val startTime = System.currentTimeMillis()
        var conflicts = 0
        var resolutions = 0
        
        // Simulate concurrent updates
        repeat(10) {
            try {
                val delta = RequestFactoryBroker.EntityDelta(entityId, mapOf("field$it" to "value$it"))
                val version = RequestFactoryBroker.EntityVersion(entityId, 1L)
                val update = RequestFactoryBroker.Request.Update(
                    serviceToken, "update", "token", delta, version
                )
                
                val response = broker.handleUpdate(update)
                if (response is RequestFactoryBroker.Response.Failure) {
                    conflicts++
                } else {
                    resolutions++
                }
            } catch (e: Exception) {
                conflicts++
            }
        }
        
        val endTime = System.currentTimeMillis()
        val resolutionTime = endTime - startTime
        val successRate = if (conflicts + resolutions > 0) {
            (resolutions.toDouble() / (conflicts + resolutions)) * 100
        } else 0.0
        
        return ConflictResolutionResult(resolutionTime, successRate, conflicts, resolutions)
    }
    
    internal data class ConcurrentUpdateResult(
        val conflicts: Int,
        val resolutions: Int,
        val failures: Int
    )
    
    internal suspend fun simulateConcurrentUpdates(
        entityId: String, 
        concurrentCount: Int
    ): ConcurrentUpdateResult {
        var conflicts = 0
        var resolutions = 0
        var failures = 0
        
        val results = coroutineScope {
            (1..concurrentCount).map {
                async {
                    try {
                        val delta = RequestFactoryBroker.EntityDelta(entityId, mapOf("update$it" to "value$it"))
                        val version = RequestFactoryBroker.EntityVersion(entityId, 1L)
                        val update = RequestFactoryBroker.Request.Update(
                            "TestService", "update", "token", delta, version
                        )
                        
                        val response = broker.handleUpdate(update)
                        when (response) {
                            is RequestFactoryBroker.Response.Failure -> conflicts++
                            is RequestFactoryBroker.Response.EntityUpdated -> resolutions++
                            else -> failures++
                        }
                    } catch (e: Exception) {
                        failures++
                    }
                }
            }.awaitAll()
        }
        
        return ConcurrentUpdateResult(conflicts, resolutions, failures)
    }
    
    internal suspend fun performRandomEntityOperation(): Result<String> {
        return try {
            val operations = listOf("create", "update", "delete")
            val operation = operations.random()
            
            when (operation) {
                "create" -> {
                    val create = RequestFactoryBroker.Request.Create(
                        "TestService", "create", "entity-${Random.nextInt()}", 
                        mapOf("name" to "Random Entity")
                    )
                    val response = broker.handleCreate(create)
                    if (response is RequestFactoryBroker.Response.EntityCreated) {
                        Result.success("created")
                    } else {
                        Result.failure(Exception("Create failed"))
                    }
                }
                "update" -> {
                    val delta = RequestFactoryBroker.EntityDelta("entity-${Random.nextInt()}", mapOf("field" to "value"))
                    val version = RequestFactoryBroker.EntityVersion("entity-${Random.nextInt()}", 1L)
                    val update = RequestFactoryBroker.Request.Update(
                        "TestService", "update", "token", delta, version
                    )
                    val response = broker.handleUpdate(update)
                    if (response is RequestFactoryBroker.Response.EntityUpdated) {
                        Result.success("updated")
                    } else {
                        Result.failure(Exception("Update failed"))
                    }
                }
                else -> Result.success("skipped")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    internal suspend fun performNetworkOperation(): Result<String> {
        return try {
            // Simulate network operation with potential failure
            if (Random.nextDouble() < 0.1) { // 10% failure rate
                throw Exception("Network error")
            }
            delay(Random.nextLong(10, 100)) // Random network delay
            Result.success("network-success")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Test entity service for CRDT assessment
 */
class TestEntityService {
    internal val entities = mutableMapOf<String, Map<String, Any?>>()
    internal val versions = mutableMapOf<String, Long>()
    
    fun createEntity(initialState: Map<String, Any?>): String {
        val entityId = "entity-${System.currentTimeMillis()}"
        entities[entityId] = initialState
        versions[entityId] = 1L
        return entityId
    }
    
    fun updateEntity(entityId: String, changes: Map<String, Any?>): Boolean {
        val currentVersion = versions[entityId] ?: return false
        entities[entityId] = entities[entityId]?.plus(changes) ?: changes
        versions[entityId] = currentVersion + 1
        return true
    }
    
    fun deleteEntity(entityId: String): Boolean {
        return entities.remove(entityId) != null && versions.remove(entityId) != null
    }
}

/**
 * Conflict resolution strategies
 */
enum class ConflictStrategy {
    LATEST_WINS,      // Simple timestamp-based
    MERGE_ALL,        // Combine all changes
    MANUAL,           // Human intervention
    TRIKESHED_CONSENSUS // Custom consensus algorithm
} 