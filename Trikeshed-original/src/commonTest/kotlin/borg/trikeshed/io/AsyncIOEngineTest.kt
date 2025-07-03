package borg.trikeshed.io

import kotlin.test.*

class AsyncIOEngineTest {
    
    @Test
    fun testAsyncIOEngineCreation() {
        val engine = AsyncIOEngine.create()
        assertNotNull(engine)
    }
    
    @Test
    fun testAsyncIOEngineInitialization() {
        val engine = AsyncIOEngine.create()
        // Should not throw an exception
        engine.initialize()
    }
    
    @Test
    fun testAsyncIOEngineCleanup() {
        val engine = AsyncIOEngine.create()
        engine.initialize()
        // Should not throw an exception
        engine.cleanup()
    }
    
    @Test
    fun testIOOperationCreation() {
        val buffer = ByteArray(1024)
        val operation = IOOperation(
            id = 1L,
            type = IOOperation.IOType.READ,
            fd = 1,
            buffer = buffer,
            offset = 0L
        )
        
        assertEquals(1L, operation.id)
        assertEquals(IOOperation.IOType.READ, operation.type)
        assertEquals(1, operation.fd)
        assertSame(buffer, operation.buffer)
        assertEquals(0L, operation.offset)
    }
    
    @Test
    fun testIOOperationEquality() {
        val buffer1 = ByteArray(1024) { it.toByte() }
        val buffer2 = ByteArray(1024) { it.toByte() }
        
        val operation1 = IOOperation(1L, IOOperation.IOType.READ, 1, buffer1, 0L)
        val operation2 = IOOperation(1L, IOOperation.IOType.READ, 1, buffer2, 0L)
        val operation3 = IOOperation(2L, IOOperation.IOType.WRITE, 1, buffer1, 0L)
        
        assertEquals(operation1, operation2)
        assertNotEquals(operation1, operation3)
    }
    
    @Test
    fun testIOResultCreation() {
        val result = IOResult(1L, 1024, 0)
        
        assertEquals(1L, result.operationId)
        assertEquals(1024, result.bytesTransferred)
        assertEquals(0, result.error)
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
    }
    
    @Test
    fun testIOResultError() {
        val result = IOResult(1L, -1, 5)
        
        assertEquals(1L, result.operationId)
        assertEquals(-1, result.bytesTransferred)
        assertEquals(5, result.error)
        assertFalse(result.isSuccess)
        assertTrue(result.isError)
    }
    
    @Test
    fun testCompletedOperationsFlow() {
        val engine = AsyncIOEngine.create()
        engine.initialize()
        
        val flow = engine.completedOperations()
        assertNotNull(flow)
        
        engine.cleanup()
    }
    
    @Test
    fun testIOOperationTypes() {
        assertEquals(2, IOOperation.IOType.values().size)
        assertTrue(IOOperation.IOType.values().contains(IOOperation.IOType.READ))
        assertTrue(IOOperation.IOType.values().contains(IOOperation.IOType.WRITE))
    }
    
    @Test
    fun testIOOperationHashCode() {
        val buffer1 = ByteArray(100) { it.toByte() }
        val buffer2 = ByteArray(100) { it.toByte() }
        
        val operation1 = IOOperation(1L, IOOperation.IOType.READ, 1, buffer1, 0L)
        val operation2 = IOOperation(1L, IOOperation.IOType.READ, 1, buffer2, 0L)
        val operation3 = IOOperation(2L, IOOperation.IOType.WRITE, 1, buffer1, 0L)
        
        assertEquals(operation1.hashCode(), operation2.hashCode())
        assertNotEquals(operation1.hashCode(), operation3.hashCode())
    }
} 