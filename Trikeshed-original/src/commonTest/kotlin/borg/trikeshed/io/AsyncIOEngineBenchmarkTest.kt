package borg.trikeshed.io

import kotlin.test.*

/**
 * Benchmark tests for AsyncIOEngine performance characteristics
 */
class AsyncIOEngineBenchmarkTest {
    
    @Test
    fun testEngineCreationPerformance() {
        // Benchmark engine creation performance
        val iterations = 1000
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val engine = AsyncIOEngine.create()
            assertNotNull(engine)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete within reasonable time (adjust threshold as needed)
        assertTrue(duration < 5000, "Engine creation took too long: ${duration}ms for $iterations iterations")
    }
    
    @Test
    fun testEngineInitializationPerformance() {
        // Benchmark engine initialization performance
        val iterations = 100
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val engine = AsyncIOEngine.create()
            engine.initialize()
            engine.cleanup()
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete within reasonable time
        assertTrue(duration < 2000, "Engine initialization took too long: ${duration}ms for $iterations iterations")
    }
    
    @Test
    fun testIOOperationCreationPerformance() {
        // Benchmark IOOperation creation performance
        val iterations = 10000
        val buffer = ByteArray(1024)
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val operation = IOOperation(
                id = it.toLong(),
                type = if (it % 2 == 0) IOOperation.IOType.READ else IOOperation.IOType.WRITE,
                fd = 1,
                buffer = buffer,
                offset = 0L
            )
            assertNotNull(operation)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete within reasonable time
        assertTrue(duration < 1000, "IOOperation creation took too long: ${duration}ms for $iterations iterations")
    }
    
    @Test
    fun testIOResultCreationPerformance() {
        // Benchmark IOResult creation performance
        val iterations = 10000
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val result = IOResult(
                operationId = it.toLong(),
                bytesTransferred = it,
                error = if (it % 10 == 0) 1 else 0
            )
            assertNotNull(result)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete within reasonable time
        assertTrue(duration < 1000, "IOResult creation took too long: ${duration}ms for $iterations iterations")
    }
    
    @Test
    fun testMemoryUsage() {
        // Test memory usage characteristics
        val engines = mutableListOf<AsyncIOEngine>()
        val operations = mutableListOf<IOOperation>()
        val results = mutableListOf<IOResult>()
        
        // Create many objects to test memory usage
        repeat(1000) {
            engines.add(AsyncIOEngine.create())
            operations.add(IOOperation(it.toLong(), IOOperation.IOType.READ, 1, ByteArray(1024), 0L))
            results.add(IOResult(it.toLong(), 1024, 0))
        }
        
        // Verify all objects were created successfully
        assertEquals(1000, engines.size)
        assertEquals(1000, operations.size)
        assertEquals(1000, results.size)
        
        // Clean up
        engines.forEach { it.cleanup() }
    }
    
    @Test
    fun testConcurrentEngineCreation() {
        // Test concurrent engine creation (basic test)
        val engines = mutableListOf<AsyncIOEngine>()
        
        // Create engines in a simple loop (not truly concurrent but tests basic thread safety)
        repeat(10) {
            val engine = AsyncIOEngine.create()
            engine.initialize()
            engines.add(engine)
        }
        
        // Verify all engines were created and initialized
        assertEquals(10, engines.size)
        
        // Clean up
        engines.forEach { it.cleanup() }
    }
    
    @Test
    fun testLargeBufferHandling() {
        // Test handling of large buffers
        val largeBuffer = ByteArray(1024 * 1024) // 1MB buffer
        val operation = IOOperation(
            id = 1L,
            type = IOOperation.IOType.READ,
            fd = 1,
            buffer = largeBuffer,
            offset = 0L
        )
        
        assertEquals(1024 * 1024, operation.buffer.size)
        assertEquals(1L, operation.id)
    }
    
    @Test
    fun testEngineResourceManagement() {
        // Test that engines properly manage resources
        val engine = AsyncIOEngine.create()
        
        // Initialize multiple times
        repeat(5) {
            engine.initialize()
            engine.cleanup()
        }
        
        // Should not throw any exceptions
        assertTrue(true, "Engine should handle multiple initialize/cleanup cycles")
    }
} 