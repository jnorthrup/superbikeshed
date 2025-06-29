package borg.trikeshed.io

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.system.measureTimeMillis

class IODaemonPerformanceTest {
    
    @Test
    fun testIODaemonThroughput() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        // Test configuration optimized for performance
        val config = IODaemonConfig(
            maxConcurrentOperations = 4096,
            ringSize = 1024,
            batchSize = 128,
            timeoutMs = 500,
            enablePolling = true,
            enableSqpoll = false,
            sqThreadIdle = 1000
        )
        
        runBlocking {
            daemon.initialize(config)
            
            val operationCount = 10000
            val bufferSize = 1024
            val testData = ByteArray(bufferSize) { it.toByte() }
            
            val operations = (1..operationCount).map { i ->
                IODaemonOperation(
                    id = i.toLong(),
                    type = IODaemonOperation.IODaemonOperationType.READ,
                    fd = i % 100, // Simulate multiple file descriptors
                    buffer = testData.copyOf(),
                    offset = 0L,
                    flags = 0,
                    priority = 0
                )
            }
            
            val executionTime = measureTimeMillis {
                val results = daemon.submitBatch(operations)
                assertEquals(operationCount, results.size)
                
                // Verify all operations completed successfully
                results.forEach { result ->
                    assertTrue(result.isSuccess)
                    assertEquals(bufferSize, result.bytesTransferred)
                }
            }
            
            val throughput = operationCount.toDouble() / (executionTime / 1000.0)
            val dataThroughput = (operationCount * bufferSize).toDouble() / (executionTime / 1000.0)
            
            println("Performance Results:")
            println("  Operations: $operationCount")
            println("  Execution Time: ${executionTime}ms")
            println("  Operations/sec: ${String.format("%.2f", throughput)}")
            println("  Data Throughput: ${String.format("%.2f", dataThroughput / 1024 / 1024)} MB/s")
            
            // Performance assertions
            assertTrue(throughput > 1000, "Should achieve at least 1000 ops/sec")
            assertTrue(dataThroughput > 1024 * 1024, "Should achieve at least 1 MB/s")
            
            daemon.shutdown()
        }
        
        scope.cancel()
    }
    
    @Test
    fun testIODaemonLatency() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        runBlocking {
            daemon.initialize()
            
            val iterations = 1000
            val buffer = "Test".toByteArray()
            val latencies = mutableListOf<Long>()
            
            repeat(iterations) { i ->
                val operation = IODaemonOperation(
                    id = i.toLong(),
                    type = IODaemonOperation.IODaemonOperationType.READ,
                    fd = 1,
                    buffer = buffer,
                    offset = 0L,
                    flags = 0,
                    priority = 0
                )
                
                val latency = measureTimeMillis {
                    val result = daemon.submit(operation)
                    assertTrue(result.isSuccess)
                }
                
                latencies.add(latency)
            }
            
            val avgLatency = latencies.average()
            val minLatency = latencies.minOrNull() ?: 0L
            val maxLatency = latencies.maxOrNull() ?: 0L
            val p95Latency = latencies.sorted()[latencies.size * 95 / 100]
            val p99Latency = latencies.sorted()[latencies.size * 99 / 100]
            
            println("Latency Results:")
            println("  Average: ${String.format("%.2f", avgLatency)}ms")
            println("  Min: ${minLatency}ms")
            println("  Max: ${maxLatency}ms")
            println("  95th percentile: ${p95Latency}ms")
            println("  99th percentile: ${p99Latency}ms")
            
            // Latency assertions
            assertTrue(avgLatency < 50, "Average latency should be under 50ms")
            assertTrue(p95Latency < 100, "95th percentile latency should be under 100ms")
            
            daemon.shutdown()
        }
        
        scope.cancel()
    }
    
    @Test
    fun testIODaemonConcurrency() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        runBlocking {
            daemon.initialize()
            
            val concurrentJobs = 100
            val operationsPerJob = 100
            val buffer = "Concurrent".toByteArray()
            
            val executionTime = measureTimeMillis {
                val jobs = (1..concurrentJobs).map { jobId ->
                    async {
                        val operations = (1..operationsPerJob).map { opId ->
                            IODaemonOperation(
                                id = (jobId * 1000L + opId),
                                type = IODaemonOperation.IODaemonOperationType.READ,
                                fd = jobId,
                                buffer = buffer,
                                offset = 0L,
                                flags = 0,
                                priority = 0
                            )
                        }
                        
                        val results = daemon.submitBatch(operations)
                        assertEquals(operationsPerJob, results.size)
                        results.forEach { assertTrue(it.isSuccess) }
                    }
                }
                
                jobs.awaitAll()
            }
            
            val totalOperations = concurrentJobs * operationsPerJob
            val throughput = totalOperations.toDouble() / (executionTime / 1000.0)
            
            println("Concurrency Results:")
            println("  Concurrent Jobs: $concurrentJobs")
            println("  Operations per Job: $operationsPerJob")
            println("  Total Operations: $totalOperations")
            println("  Execution Time: ${executionTime}ms")
            println("  Throughput: ${String.format("%.2f", throughput)} ops/sec")
            
            // Concurrency assertions
            assertTrue(throughput > 500, "Should handle concurrent operations efficiently")
            
            daemon.shutdown()
        }
        
        scope.cancel()
    }
    
    @Test
    fun testIODaemonMemoryEfficiency() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        runBlocking {
            daemon.initialize()
            
            val operationCount = 10000
            val largeBuffer = ByteArray(64 * 1024) // 64KB buffer
            
            val memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            
            val operations = (1..operationCount).map { i ->
                IODaemonOperation(
                    id = i.toLong(),
                    type = IODaemonOperation.IODaemonOperationType.READ,
                    fd = i % 10,
                    buffer = largeBuffer.copyOf(),
                    offset = 0L,
                    flags = 0,
                    priority = 0
                )
            }
            
            val results = daemon.submitBatch(operations)
            assertEquals(operationCount, results.size)
            
            // Force garbage collection to measure memory usage
            System.gc()
            val memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memoryUsed = memoryAfter - memoryBefore
            
            val memoryPerOperation = memoryUsed.toDouble() / operationCount
            
            println("Memory Efficiency Results:")
            println("  Operations: $operationCount")
            println("  Buffer Size: ${largeBuffer.size / 1024}KB")
            println("  Memory Used: ${memoryUsed / 1024 / 1024}MB")
            println("  Memory per Operation: ${String.format("%.2f", memoryPerOperation / 1024)}KB")
            
            // Memory efficiency assertions
            assertTrue(memoryPerOperation < 1024 * 1024, "Should use less than 1MB per operation")
            
            daemon.shutdown()
        }
        
        scope.cancel()
    }
    
    @Test
    fun testIODaemonScalability() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        runBlocking {
            daemon.initialize()
            
            val scales = listOf(100, 1000, 10000)
            val results = mutableMapOf<Int, Double>()
            
            scales.forEach { scale ->
                val operations = (1..scale).map { i ->
                    IODaemonOperation(
                        id = i.toLong(),
                        type = IODaemonOperation.IODaemonOperationType.READ,
                        fd = i % 100,
                        buffer = "Scalability".toByteArray(),
                        offset = 0L,
                        flags = 0,
                        priority = 0
                    )
                }
                
                val executionTime = measureTimeMillis {
                    val batchResults = daemon.submitBatch(operations)
                    assertEquals(scale, batchResults.size)
                    batchResults.forEach { assertTrue(it.isSuccess) }
                }
                
                val throughput = scale.toDouble() / (executionTime / 1000.0)
                results[scale] = throughput
                
                println("Scale $scale: ${String.format("%.2f", throughput)} ops/sec")
            }
            
            // Scalability assertions
            assertTrue(results[100]!! > 100, "Should handle 100 operations efficiently")
            assertTrue(results[1000]!! > 500, "Should handle 1000 operations efficiently")
            assertTrue(results[10000]!! > 1000, "Should handle 10000 operations efficiently")
            
            daemon.shutdown()
        }
        
        scope.cancel()
    }
} 