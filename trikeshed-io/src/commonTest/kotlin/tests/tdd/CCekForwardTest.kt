@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package tests.tdd

import borg.trikeshed.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlin.test.*

/**
 * TDD Test for CCekEngine Forward Functionality
 * 
 * Tests the CCEK Series 0: Basic Protocol Adapters forward implementation
 */
class CCekForwardTest {
    
    @Test
    fun `test forward message routing`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            // Send message to target
            val testData = "Hello, World!".toByteArray()
            val bytesSent = engine.send("test-target", testData)
            
            assertEquals(testData.size, bytesSent)
            
            // Verify message appears in outgoing flow
            val outgoingMessages = engine.outgoingMessages().toList()
            assertTrue(outgoingMessages.isNotEmpty())
            
            val sentMessage = outgoingMessages.first()
            assertEquals("local", sentMessage.source)
            assertEquals("test-target", sentMessage.target)
            assertContentEquals(testData, sentMessage.data)
            assertEquals(CompressionAlgorithm.NONE, sentMessage.algorithm)
        } finally {
            engine.cleanup()
        }
    }
    
    @Test
    fun `test forward message reception`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            // Send message first
            val testData = "Test message".toByteArray()
            engine.send("test-source", testData)
            
            // Receive message
            val buffer = ByteArray(1024)
            val bytesReceived = engine.receive("test-source", buffer)
            
            assertEquals(testData.size, bytesReceived)
            assertContentEquals(testData, buffer.copyOf(bytesReceived))
        } finally {
            engine.cleanup()
        }
    }
    
    @Test
    fun `test forward object serialization`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            val testObject = "Serialized object"
            val bytesSent = engine.sendObject("test-target", testObject)
            
            assertTrue(bytesSent > 0)
            
            // Verify object can be received
            val receivedObject = engine.receiveObject<String>("test-target")
            assertEquals(testObject, receivedObject)
        } finally {
            engine.cleanup()
        }
    }
    
    @Test
    fun `test forward broadcast functionality`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            val targets = listOf("target1", "target2", "target3")
            val testData = "Broadcast message".toByteArray()
            
            val results = engine.broadcast(targets, testData)
            
            assertEquals(targets.size, results.size)
            results.forEach { bytesSent ->
                assertEquals(testData.size, bytesSent)
            }
            
            // Verify all targets received the message
            targets.forEach { target ->
                val buffer = ByteArray(1024)
                val bytesReceived = engine.receive(target, buffer)
                assertEquals(testData.size, bytesReceived)
                assertContentEquals(testData, buffer.copyOf(bytesReceived))
            }
        } finally {
            engine.cleanup()
        }
    }
    
    @Test
    fun `test forward message flow tracking`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            val testData = "Flow test".toByteArray()
            
            // Collect incoming messages
            val incomingMessages = mutableListOf<CCekMessage>()
            engine.incomingMessages().toList().let { incomingMessages.addAll(it) }
            
            // Send message
            engine.send("flow-target", testData)
            
            // Verify message appears in incoming flow
            val newIncomingMessages = engine.incomingMessages().toList()
            assertTrue(newIncomingMessages.isNotEmpty())
            
            val receivedMessage = newIncomingMessages.first()
            assertEquals("local", receivedMessage.source)
            assertEquals("flow-target", receivedMessage.target)
            assertContentEquals(testData, receivedMessage.data)
        } finally {
            engine.cleanup()
        }
    }
    
    @Test
    fun `test forward compression stats`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            val stats = engine.getCompressionStats()
            
            assertNotNull(stats)
            assertEquals(0L, stats.totalMessages)
            assertEquals(0L, stats.totalCompressedBytes)
            assertEquals(0L, stats.totalOriginalBytes)
            assertEquals(1.0, stats.averageCompressionRatio)
            assertTrue(stats.algorithmUsage.containsKey(CompressionAlgorithm.NONE))
        } finally {
            engine.cleanup()
        }
    }
    
    @Test
    fun `test forward initialization validation`() = runTest {
        val engine = CCekEngine.create()
        
        // Should throw exception when not initialized
        assertFailsWith<IllegalStateException> {
            engine.send("target", "test".toByteArray())
        }
        
        assertFailsWith<IllegalStateException> {
            engine.receive("source", ByteArray(1024))
        }
        
        assertFailsWith<IllegalStateException> {
            engine.broadcast(listOf("target"), "test".toByteArray())
        }
    }
    
    @Test
    fun `test forward routing table management`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            // Send to multiple targets to create routing entries
            val targets = listOf("router1", "router2", "router3")
            val testData = "Routing test".toByteArray()
            
            engine.broadcast(targets, testData)
            
            // Verify all targets can receive messages
            targets.forEach { target ->
                val buffer = ByteArray(1024)
                val received = engine.receive(target, buffer)
                assertEquals(testData.size, received)
            }
        } finally {
            engine.cleanup()
        }
    }
    
    @Test
    fun `test forward message ID generation`() = runTest {
        val engine = CCekEngine.create()
        engine.initialize()
        
        try {
            val testData = "ID test".toByteArray()
            
            // Send multiple messages and verify unique IDs
            val messageIds = mutableSetOf<Long>()
            
            repeat(10) {
                engine.send("id-test-target", testData)
                val messages = engine.outgoingMessages().toList()
                val messageId = messages.last().id
                assertFalse(messageIds.contains(messageId), "Message ID should be unique")
                messageIds.add(messageId)
            }
        } finally {
            engine.cleanup()
        }
    }
} 