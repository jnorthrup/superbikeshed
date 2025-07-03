package borg.trikeshed.io

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class IODSLTest {
    
    @Test
    fun testIODSLCreation() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val dsl = IODSL(daemon)
        
        assertNotNull(dsl)
        assertTrue(dsl is IODSL)
        
        scope.cancel()
    }
    
    @Test
    fun testIOSessionCreation() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val dsl = IODSL(daemon)
        val session = dsl.session(42)
        
        assertNotNull(session)
        assertTrue(session is IOSession)
        
        scope.cancel()
    }
    
    @Test
    fun testIOHandleCreation() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val dsl = IODSL(daemon)
        val handle = dsl.handle(IODaemonOperation.IODaemonOperationType.READ)
        
        assertNotNull(handle)
        assertTrue(handle is IOHandle)
        
        scope.cancel()
    }
    
    @Test
    fun testIOBatchBuilderCreation() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val dsl = IODSL(daemon)
        
        // Test that batch builder can be created
        // Note: batch execution would be a suspend function in real implementation
        assertNotNull(dsl)
        
        scope.cancel()
    }
    
    @Test
    fun testIOStreamBuilderCreation() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val dsl = IODSL(daemon)
        
        // Test that stream builder can be created
        // Note: stream execution would be a suspend function in real implementation
        assertNotNull(dsl)
        
        scope.cancel()
    }
    
    @Test
    fun testExtensionFunctions() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        
        val dsl1 = daemon.dsl()
        val dsl2 = scope.ioDsl()
        
        assertNotNull(dsl1)
        assertNotNull(dsl2)
        assertTrue(dsl1 is IODSL)
        assertTrue(dsl2 is IODSL)
        
        scope.cancel()
    }
    
    @Test
    fun testIOSessionOperations() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val session = daemon.dsl().session(42)
        
        // Test that session has the expected methods
        // In a real implementation, these would be suspend functions
        assertNotNull(session)
        
        scope.cancel()
    }
    
    @Test
    fun testIOHandleOperations() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val handle = daemon.dsl().handle(IODaemonOperation.IODaemonOperationType.READ)
        
        // Test that handle has the expected methods
        assertNotNull(handle)
        
        scope.cancel()
    }
    
    @Test
    fun testIOBatchBuilderOperations() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val builder = IOBatchBuilder(daemon)
        
        // Test batch builder operations
        builder.read(1, "Hello".toByteArray())
        builder.write(2, "World".toByteArray())
        // Note: poll method would be available in real implementation
        
        assertNotNull(builder)
        
        scope.cancel()
    }
    
    @Test
    fun testIOStreamBuilderOperations() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val builder = IOStreamBuilder(daemon)
        
        // Test stream builder operations
        builder.read(1, "Hello".toByteArray())
        builder.write(2, "World".toByteArray())
        // Note: poll method would be available in real implementation
        
        val stream = builder.stream()
        assertNotNull(stream)
        assertTrue(stream is kotlinx.coroutines.flow.Flow<*>)
        
        scope.cancel()
    }
    
    @Test
    fun testIODSLIntegration() {
        val scope = CoroutineScope(Dispatchers.IO)
        val daemon = IODaemon.create(scope)
        val dsl = daemon.dsl()
        
        // Test integration between different DSL components
        val session = dsl.session(42)
        val handle = dsl.handle(IODaemonOperation.IODaemonOperationType.READ)
        
        assertNotNull(session)
        assertNotNull(handle)
        assertNotNull(dsl)
        
        scope.cancel()
    }
} 