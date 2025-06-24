package borg.trikeshed.io

import kotlin.test.*

class AsyncIOEngineNativeTest {
    
    @Test
    fun testNativeAsyncIOEngineCreation() {
        val engine = AsyncIOEngine.create()
        assertNotNull(engine)
    }
    
    @Test
    fun testNativeAsyncIOEngineInitialization() {
        val engine = AsyncIOEngine.create()
        // Should not throw an exception
        engine.initialize()
    }
    
    @Test
    fun testNativeAsyncIOEngineCleanup() {
        val engine = AsyncIOEngine.create()
        engine.initialize()
        // Should not throw an exception
        engine.cleanup()
    }
    
    @Test
    fun testNativeCompletedOperationsFlow() {
        val engine = AsyncIOEngine.create()
        engine.initialize()
        
        val flow = engine.completedOperations()
        assertNotNull(flow)
        
        engine.cleanup()
    }
    
    @Test
    fun testNativeMultipleInitialization() {
        val engine = AsyncIOEngine.create()
        
        // Should be able to initialize multiple times
        engine.initialize()
        engine.cleanup()
        engine.initialize()
        engine.cleanup()
    }
    
    @Test
    fun testNativeCleanupWithoutInitialization() {
        val engine = AsyncIOEngine.create()
        // Should not throw an exception when cleaning up without initialization
        engine.cleanup()
    }
} 