package borg.trikeshed.io

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IODaemonJvmTest {
    
    @Test
    fun testIODaemonCreation() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        assertNotNull(daemon)
        assertTrue(daemon is IODaemon)
        
        scope.cancel()
    }
    
    @Test
    fun testIODaemonConfig() {
        val config = IODaemonConfig(
            maxConcurrentOperations = 1024,
            ringSize = 256,
            batchSize = 32,
            timeoutMs = 1000,
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
    }
    
    @Test
    fun testIODaemonOperationTypes() {
        val types = IODaemonOperation.IODaemonOperationType.values()
        
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.READ))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.WRITE))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.READV))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.WRITEV))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.POLL))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.ACCEPT))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.CONNECT))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.SEND))
        assertTrue(types.contains(IODaemonOperation.IODaemonOperationType.RECV))
        
        assertEquals(9, types.size)
    }
    
    @Test
    fun testIODaemonResult() {
        val result = IODaemonResult(
            operationId = 1L,
            bytesTransferred = 1024,
            error = 0,
            flags = 1,
            timestamp = 1234567890L
        )
        
        assertEquals(1L, result.operationId)
        assertEquals(1024, result.bytesTransferred)
        assertEquals(0, result.error)
        assertEquals(1, result.flags)
        assertEquals(1234567890L, result.timestamp)
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        
        val errorResult = IODaemonResult(
            operationId = 2L,
            bytesTransferred = 0,
            error = -1,
            flags = 0,
            timestamp = 1234567890L
        )
        
        assertFalse(errorResult.isSuccess)
        assertTrue(errorResult.isError)
    }
    
    @Test
    fun testIODaemonStats() {
        val stats = IODaemonStats(
            totalOperations = 1000L,
            successfulOperations = 950L,
            failedOperations = 50L,
            totalBytesTransferred = 1024000L,
            averageLatencyMs = 15.5,
            queueDepth = 25,
            uptimeMs = 60000L
        )
        
        assertEquals(1000L, stats.totalOperations)
        assertEquals(950L, stats.successfulOperations)
        assertEquals(50L, stats.failedOperations)
        assertEquals(1024000L, stats.totalBytesTransferred)
        assertEquals(15.5, stats.averageLatencyMs)
        assertEquals(25, stats.queueDepth)
        assertEquals(60000L, stats.uptimeMs)
    }
    
    @Test
    fun testIODSLIntegration() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val dsl = daemon.dsl()
        
        assertNotNull(dsl)
        assertTrue(dsl is IODSL)
        
        val session = dsl.session(42)
        assertNotNull(session)
        assertTrue(session is IOSession)
        
        val handle = dsl.handle(IODaemonOperation.IODaemonOperationType.READ)
        assertNotNull(handle)
        assertTrue(handle is IOHandle)
        
        scope.cancel()
    }
} 