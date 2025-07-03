package borg.trikeshed.io

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.system.measureTimeMillis

class IODaemonTestRunner {
    
    @Test
    fun `test data structure validation`() {
        // Test IODaemonConfig
        val config = IODaemonConfig(
            maxConcurrentOperations = 1024,
            ringSize = 256,
            batchSize = 32,
            timeoutMs = 1000L,
            enablePolling = true,
            enableSqpoll = false,
            sqThreadIdle = 2000
        )
        
        assertEquals(1024, config.maxConcurrentOperations)
        assertEquals(256, config.ringSize)
        assertEquals(32, config.batchSize)
        assertEquals(1000L, config.timeoutMs)
        assertTrue(config.enablePolling)
        assertFalse(config.enableSqpoll)
        assertEquals(2000, config.sqThreadIdle)
        
        // Test IODaemonOperation
        val buffer = ByteArray(1024) { it.toByte() }
        val operation = IODaemonOperation(
            id = 1L,
            type = IODaemonOperation.IODaemonOperationType.READ,
            fd = 42,
            buffer = buffer,
            offset = 100L,
            flags = 0x01,
            priority = 1
        )
        
        assertEquals(1L, operation.id)
        assertEquals(IODaemonOperation.IODaemonOperationType.READ, operation.type)
        assertEquals(42, operation.fd)
        assertSame(buffer, operation.buffer)
        assertEquals(100L, operation.offset)
        assertEquals(0x01, operation.flags)
        assertEquals(1, operation.priority)
        
        // Test IODaemonResult
        val result = IODaemonResult(
            operationId = 1L,
            bytesTransferred = 1024,
            error = 0,
            flags = 0x01,
            timestamp = System.currentTimeMillis()
        )
        
        assertEquals(1L, result.operationId)
        assertEquals(1024, result.bytesTransferred)
        assertEquals(0, result.error)
        assertEquals(0x01, result.flags)
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        
        // Test IODaemonStats
        val stats = IODaemonStats(
            totalOperations = 1000L,
            successfulOperations = 950L,
            failedOperations = 50L,
            totalBytesTransferred = 1024000L,
            averageLatencyMs = 1.5,
            queueDepth = 10,
            uptimeMs = 60000L
        )
        
        assertEquals(1000L, stats.totalOperations)
        assertEquals(950L, stats.successfulOperations)
        assertEquals(50L, stats.failedOperations)
        assertEquals(1024000L, stats.totalBytesTransferred)
        assertEquals(1.5, stats.averageLatencyMs)
        assertEquals(10, stats.queueDepth)
        assertEquals(60000L, stats.uptimeMs)
    }
    
    @Test
    fun `test operation type enumeration`() {
        val types = IODaemonOperation.IODaemonOperationType.values()
        
        val expectedTypes = setOf(
            IODaemonOperation.IODaemonOperationType.READ,
            IODaemonOperation.IODaemonOperationType.WRITE,
            IODaemonOperation.IODaemonOperationType.READV,
            IODaemonOperation.IODaemonOperationType.WRITEV,
            IODaemonOperation.IODaemonOperationType.POLL,
            IODaemonOperation.IODaemonOperationType.ACCEPT,
            IODaemonOperation.IODaemonOperationType.CONNECT,
            IODaemonOperation.IODaemonOperationType.SEND,
            IODaemonOperation.IODaemonOperationType.RECV
        )
        
        assertEquals(expectedTypes.size, types.size)
        expectedTypes.forEach { expectedType ->
            assertTrue(types.contains(expectedType))
        }
    }
    
    @Test
    fun `test configuration validation`() {
        // Test valid configurations
        val validConfigs = listOf(
            IODaemonConfig(),
            IODaemonConfig(maxConcurrentOperations = 2048),
            IODaemonConfig(ringSize = 512),
            IODaemonConfig(batchSize = 64),
            IODaemonConfig(timeoutMs = 2000L),
            IODaemonConfig(enablePolling = true, enableSqpoll = false),
            IODaemonConfig(enablePolling = false, enableSqpoll = true)
        )
        
        validConfigs.forEach { config ->
            assertTrue(config.maxConcurrentOperations > 0)
            assertTrue(config.ringSize > 0)
            assertTrue(config.batchSize > 0)
            assertTrue(config.timeoutMs > 0)
            assertTrue(config.sqThreadIdle >= 0)
        }
    }
    
    @Test
    fun `test operation creation performance`() {
        val iterations = 10000
        
        val creationTime = measureTimeMillis {
            repeat(iterations) { id ->
                IODaemonOperation(
                    id = id.toLong(),
                    type = IODaemonOperation.IODaemonOperationType.READ,
                    fd = 42,
                    buffer = ByteArray(1024) { it.toByte() },
                    offset = (id * 1024).toLong(),
                    flags = 0,
                    priority = 0
                )
            }
        }
        
        val throughput = iterations.toDouble() / (creationTime / 1000.0)
        println("Operation creation performance: ${throughput} ops/sec")
        assertTrue(throughput > 1000) // Should create at least 1000 ops/sec
    }
    
    @Test
    fun `test batch operation creation`() {
        val batchSize = 1000
        val operations = (1..batchSize).map { id ->
            IODaemonOperation(
                id = id.toLong(),
                type = if (id % 2 == 0) IODaemonOperation.IODaemonOperationType.READ 
                       else IODaemonOperation.IODaemonOperationType.WRITE,
                fd = 42,
                buffer = ByteArray(512) { it.toByte() },
                offset = (id * 512).toLong(),
                flags = 0,
                priority = id % 3
            )
        }
        
        assertEquals(batchSize, operations.size)
        
        // Verify operation distribution
        val readOps = operations.count { it.type == IODaemonOperation.IODaemonOperationType.READ }
        val writeOps = operations.count { it.type == IODaemonOperation.IODaemonOperationType.WRITE }
        
        assertEquals(batchSize / 2, readOps)
        assertEquals(batchSize / 2, writeOps)
        
        // Verify priority distribution
        val priorities = operations.map { it.priority }.distinct().sorted()
        assertEquals(listOf(0, 1, 2), priorities)
    }
    
    @Test
    fun `test result calculation performance`() {
        val iterations = 10000
        
        val calculationTime = measureTimeMillis {
            repeat(iterations) { id ->
                IODaemonResult(
                    operationId = id.toLong(),
                    bytesTransferred = 1024,
                    error = if (id % 100 == 0) -1 else 0,
                    flags = 0,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
        
        val throughput = iterations.toDouble() / (calculationTime / 1000.0)
        println("Result calculation performance: ${throughput} ops/sec")
        assertTrue(throughput > 1000) // Should calculate at least 1000 results/sec
    }
    
    @Test
    fun `test stats calculation accuracy`() {
        val totalOps = 1000L
        val successOps = 950L
        val failedOps = 50L
        val totalBytes = 1024000L
        val avgLatency = 1.5
        val queueDepth = 10
        val uptime = 60000L
        
        val stats = IODaemonStats(
            totalOperations = totalOps,
            successfulOperations = successOps,
            failedOperations = failedOps,
            totalBytesTransferred = totalBytes,
            averageLatencyMs = avgLatency,
            queueDepth = queueDepth,
            uptimeMs = uptime
        )
        
        // Verify calculations
        assertEquals(totalOps, stats.successfulOperations + stats.failedOperations)
        
        // Verify success rate
        val successRate = stats.successfulOperations.toDouble() / stats.totalOperations
        assertEquals(0.95, successRate, 0.01)
        
        // Verify throughput (bytes per second)
        val throughput = stats.totalBytesTransferred.toDouble() / (stats.uptimeMs / 1000.0)
        assertEquals(1024000.0 / 60.0, throughput, 0.01) // 1024000 bytes / 60 seconds
    }
} 