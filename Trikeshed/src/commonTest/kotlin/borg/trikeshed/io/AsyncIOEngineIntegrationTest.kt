package borg.trikeshed.io

import kotlin.test.*

/**
 * Integration tests for AsyncIOEngine demonstrating cross-platform usage
 */
class AsyncIOEngineIntegrationTest {
    
    @Test
    fun testAsyncIOEngineLifecycle() {
        // Test the complete lifecycle of an AsyncIOEngine
        val engine = AsyncIOEngine.create()
        
        // Initialize
        engine.initialize()
        
        // Verify it's ready
        val flow = engine.completedOperations()
        assertNotNull(flow)
        
        // Cleanup
        engine.cleanup()
    }
    
    @Test
    fun testIOOperationDataStructures() {
        // Test the data structures used by AsyncIOEngine
        
        // Create test data
        val readBuffer = ByteArray(1024) { it.toByte() }
        val writeData = "Hello, Async I/O!".toByteArray()
        
        // Create operations
        val readOp = IOOperation(
            id = 1L,
            type = IOOperation.IOType.READ,
            fd = 1,
            buffer = readBuffer,
            offset = 0L
        )
        
        val writeOp = IOOperation(
            id = 2L,
            type = IOOperation.IOType.WRITE,
            fd = 1,
            buffer = writeData,
            offset = 0L
        )
        
        // Verify operations
        assertEquals(1L, readOp.id)
        assertEquals(IOOperation.IOType.READ, readOp.type)
        assertEquals(1024, readOp.buffer.size)
        
        assertEquals(2L, writeOp.id)
        assertEquals(IOOperation.IOType.WRITE, writeOp.type)
        assertEquals(writeData.size, writeOp.buffer.size)
    }
    
    @Test
    fun testIOResultDataStructures() {
        // Test the result data structures
        
        // Success case
        val successResult = IOResult(1L, 1024, 0)
        assertTrue(successResult.isSuccess)
        assertFalse(successResult.isError)
        assertEquals(1024, successResult.bytesTransferred)
        
        // Error case
        val errorResult = IOResult(2L, -1, 5)
        assertFalse(errorResult.isSuccess)
        assertTrue(errorResult.isError)
        assertEquals(-1, errorResult.bytesTransferred)
        assertEquals(5, errorResult.error)
    }
    
    @Test
    fun testIOOperationEqualityAndHashCode() {
        val buffer1 = ByteArray(100) { it.toByte() }
        val buffer2 = ByteArray(100) { it.toByte() }
        
        val op1 = IOOperation(1L, IOOperation.IOType.READ, 1, buffer1, 0L)
        val op2 = IOOperation(1L, IOOperation.IOType.READ, 1, buffer2, 0L)
        val op3 = IOOperation(2L, IOOperation.IOType.WRITE, 1, buffer1, 0L)
        
        // Equality
        assertEquals(op1, op2)
        assertNotEquals(op1, op3)
        
        // Hash code
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1.hashCode(), op3.hashCode())
    }
    
    @Test
    fun testIOOperationTypes() {
        // Test the IOType enum
        val types = IOOperation.IOType.values()
        assertEquals(2, types.size)
        
        assertTrue(types.contains(IOOperation.IOType.READ))
        assertTrue(types.contains(IOOperation.IOType.WRITE))
        
        // Test string representation
        assertEquals("READ", IOOperation.IOType.READ.name)
        assertEquals("WRITE", IOOperation.IOType.WRITE.name)
    }
    
    @Test
    fun testMultipleEngineInstances() {
        // Test that multiple engine instances can coexist
        val engine1 = AsyncIOEngine.create()
        val engine2 = AsyncIOEngine.create()
        
        assertNotSame(engine1, engine2)
        
        engine1.initialize()
        engine2.initialize()
        
        val flow1 = engine1.completedOperations()
        val flow2 = engine2.completedOperations()
        
        assertNotNull(flow1)
        assertNotNull(flow2)
        
        engine1.cleanup()
        engine2.cleanup()
    }
    
    @Test
    fun testEngineReinitialization() {
        // Test that an engine can be reinitialized after cleanup
        val engine = AsyncIOEngine.create()
        
        // First cycle
        engine.initialize()
        engine.cleanup()
        
        // Second cycle
        engine.initialize()
        engine.cleanup()
        
        // Should not throw any exceptions
    }
} 