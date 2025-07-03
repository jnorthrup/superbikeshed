package borg.trikeshed.io

import kotlin.test.*

class LiburingAsyncIOEngineTest {
    
    @Test
    fun testLiburingAsyncIOEngineCreation() {
        val engine = LiburingAsyncIOEngine.create()
        assertNotNull(engine)
    }
    
    @Test
    fun testLiburingAsyncIOEngineInitialization() {
        val engine = LiburingAsyncIOEngine.create()
        // Should not throw an exception
        engine.initialize()
    }
    
    @Test
    fun testLiburingAsyncIOEngineCleanup() {
        val engine = LiburingAsyncIOEngine.create()
        engine.initialize()
        // Should not throw an exception
        engine.cleanup()
    }
    
    @Test
    fun testLiburingCompletedOperationsFlow() {
        val engine = LiburingAsyncIOEngine.create()
        engine.initialize()
        
        val flow = engine.completedOperations()
        assertNotNull(flow)
        
        engine.cleanup()
    }
    
    @Test
    fun testLiburingMultipleInitialization() {
        val engine = LiburingAsyncIOEngine.create()
        
        // Should be able to initialize multiple times
        engine.initialize()
        engine.cleanup()
        engine.initialize()
        engine.cleanup()
    }
    
    @Test
    fun testLiburingCleanupWithoutInitialization() {
        val engine = LiburingAsyncIOEngine.create()
        // Should not throw an exception when cleaning up without initialization
        engine.cleanup()
    }
    
    @Test
    fun testLiburingEngineType() {
        val engine = LiburingAsyncIOEngine.create()
        assertTrue(engine is LiburingAsyncIOEngine)
    }
} 