package borg.trikeshed.io

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class IODaemonTest {
    
    @Test
    fun testIODaemonConfig() {
        val config = IODaemonConfig(
            maxConcurrentOperations = 2048,
            ringSize = 512,
            batchSize = 64,
            timeoutMs = 2000,
            enablePolling = true,
            enableSqpoll = true,
            sqThreadIdle = 3000
        )
        
        assertEquals(2048, config.maxConcurrentOperations)
        assertEquals(512, config.ringSize)
        assertEquals(64, config.batchSize)
        assertEquals(2000L, config.timeoutMs)
        assertTrue(config.enablePolling)
        assertTrue(config.enableSqpoll)
        assertEquals(3000, config.sqThreadIdle)
    }
    
    @Test
    fun testIODaemonOperation() {
        val buffer = "Hello, World!".toByteArray()
        val operation = IODaemonOperation(
            id = 1L,
            type = IODaemonOperation.IODaemonOperationType.READ,
            fd = 42,
            buffer = buffer,
            offset = 100L,
            flags = 0,
            priority = 1
        )
        
        assertEquals(1L, operation.id)
        assertEquals(IODaemonOperation.IODaemonOperationType.READ, operation.type)
        assertEquals(42, operation.fd)
        assertContentEquals(buffer, operation.buffer)
        assertEquals(100L, operation.offset)
        assertEquals(0, operation.flags)
        assertEquals(1, operation.priority)
    }
    
    @Test
    fun testIODaemonOperationEquality() {
        val buffer1 = "Hello".toByteArray()
        val buffer2 = "Hello".toByteArray()
        val buffer3 = "World".toByteArray()
        
        val op1 = IODaemonOperation(1L, IODaemonOperation.IODaemonOperationType.READ, 1, buffer1)
        val op2 = IODaemonOperation(1L, IODaemonOperation.IODaemonOperationType.READ, 1, buffer2)
        val op3 = IODaemonOperation(2L, IODaemonOperation.IODaemonOperationType.READ, 1, buffer1)
        val op4 = IODaemonOperation(1L, IODaemonOperation.IODaemonOperationType.WRITE, 1, buffer1)
        val op5 = IODaemonOperation(1L, IODaemonOperation.IODaemonOperationType.READ, 2, buffer1)
        val op6 = IODaemonOperation(1L, IODaemonOperation.IODaemonOperationType.READ, 1, buffer3)
        
        assertEquals(op1, op2)
        assertNotEquals(op1, op3)
        assertNotEquals(op1, op4)
        assertNotEquals(op1, op5)
        assertNotEquals(op1, op6)
        
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1.hashCode(), op3.hashCode())
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
    fun testIODaemonStats() = runTest {
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
    fun testIODaemonOperationTypes() = runTest {
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
    fun testIODaemonCreate() = runTest {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        assertNotNull(daemon)
        assertTrue(daemon is IODaemon)
        
        scope.cancel()
    }
} 