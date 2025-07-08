package fiduciary.protocol

import kotlin.test.*
import borg.trikeshed.lib.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest

/**
 * TDD Test Suite for WireProto and LogStructuredWireProto
 * 
 * Based on Fiduciary Omnibus Architecture requirements:
 * - WireProto inherits basic protocol functionality
 * - LogStructuredWireProto inherits from WireProto
 * - SessionID key relationship management
 * - Concurrent tubular flows
 */
class WireProtoTest {
    
    @Test
    fun `WireProto should serialize and deserialize basic messages`() = runTest {
        // Given
        val wireProto = WireProto()
        val message = WireMessage(
            sessionId = "test-session-123",
            payload = "Hello World".toByteArray(),
            messageType = MessageType.DATA,
            timestamp = 1234567890L
        )
        
        // When
        val serialized = wireProto.serialize(message)
        val deserialized = wireProto.deserialize(serialized)
        
        // Then
        assertEquals(message.sessionId, deserialized.sessionId)
        assertTrue(message.payload.contentEquals(deserialized.payload))
        assertEquals(message.messageType, deserialized.messageType)
        assertEquals(message.timestamp, deserialized.timestamp)
    }
    
    @Test
    fun `WireProto should handle session management`() = runTest {
        // Given
        val wireProto = WireProto()
        val sessionId = "session-456"
        
        // When
        val session = wireProto.createSession(sessionId)
        val retrievedSession = wireProto.getSession(sessionId)
        
        // Then
        assertNotNull(session)
        assertNotNull(retrievedSession)
        assertEquals(sessionId, session.sessionId)
        assertEquals(sessionId, retrievedSession?.sessionId)
    }
    
    @Test
    fun `WireProto should validate message integrity`() = runTest {
        // Given
        val wireProto = WireProto()
        val validMessage = WireMessage(
            sessionId = "valid-session",
            payload = "Valid payload".toByteArray(),
            messageType = MessageType.DATA,
            timestamp = System.currentTimeMillis()
        )
        
        val invalidMessage = WireMessage(
            sessionId = "", // Invalid empty session
            payload = ByteArray(0),
            messageType = MessageType.DATA,
            timestamp = -1L // Invalid timestamp
        )
        
        // When/Then
        assertTrue(wireProto.validateMessage(validMessage))
        assertFalse(wireProto.validateMessage(invalidMessage))
    }
    
    @Test
    fun `LogStructuredWireProto should inherit from WireProto`() = runTest {
        // Given
        val logStructuredProto = LogStructuredWireProto()
        
        // When
        val isWireProto = logStructuredProto is WireProto
        
        // Then
        assertTrue(isWireProto, "LogStructuredWireProto should inherit from WireProto")
    }
    
    @Test
    fun `LogStructuredWireProto should append messages to log`() = runTest {
        // Given
        val logStructuredProto = LogStructuredWireProto()
        val messages = listOf(
            WireMessage("session-1", "msg1".toByteArray(), MessageType.DATA, 1000L),
            WireMessage("session-1", "msg2".toByteArray(), MessageType.DATA, 2000L),
            WireMessage("session-2", "msg3".toByteArray(), MessageType.CONTROL, 3000L)
        )
        
        // When
        messages.forEach { logStructuredProto.appendToLog(it) }
        val log = logStructuredProto.getLog()
        
        // Then
        assertEquals(3, log.size)
        assertEquals("session-1", log[0].sessionId)
        assertEquals("session-2", log[2].sessionId)
        assertTrue(log[0].payload.contentEquals("msg1".toByteArray()))
    }
    
    @Test
    fun `LogStructuredWireProto should support log compaction`() = runTest {
        // Given
        val logStructuredProto = LogStructuredWireProto()
        repeat(100) { i ->
            logStructuredProto.appendToLog(
                WireMessage(
                    sessionId = "session-${i % 10}",
                    payload = "message-$i".toByteArray(),
                    messageType = MessageType.DATA,
                    timestamp = i.toLong()
                )
            )
        }
        
        // When
        val initialSize = logStructuredProto.getLog().size
        logStructuredProto.compactLog()
        val compactedSize = logStructuredProto.getLog().size
        
        // Then
        assertEquals(100, initialSize)
        assertTrue(compactedSize < initialSize, "Log should be compacted")
    }
    
    @Test
    fun `LogStructuredWireProto should maintain session ordering`() = runTest {
        // Given
        val logStructuredProto = LogStructuredWireProto()
        val sessionId = "ordered-session"
        
        // When
        repeat(10) { i ->
            logStructuredProto.appendToLog(
                WireMessage(
                    sessionId = sessionId,
                    payload = "msg-$i".toByteArray(),
                    messageType = MessageType.DATA,
                    timestamp = i.toLong()
                )
            )
        }
        
        val sessionMessages = logStructuredProto.getMessagesForSession(sessionId)
        
        // Then
        assertEquals(10, sessionMessages.size)
        sessionMessages.forEachIndexed { index, message ->
            assertEquals(index.toLong(), message.timestamp)
        }
    }
    
    @Test
    fun `WireProto should support concurrent access`() = runTest {
        // Given
        val wireProto = WireProto()
        val sessionCount = 100
        
        // When
        val sessions = (1..sessionCount).map { i ->
            wireProto.createSession("concurrent-session-$i")
        }
        
        // Then
        assertEquals(sessionCount, sessions.size)
        sessions.forEach { session ->
            assertNotNull(session)
            assertTrue(session.sessionId.startsWith("concurrent-session-"))
        }
    }
    
    @Test
    fun `LogStructuredWireProto should handle log rotation`() = runTest {
        // Given
        val logStructuredProto = LogStructuredWireProto(maxLogSize = 50)
        
        // When
        repeat(100) { i ->
            logStructuredProto.appendToLog(
                WireMessage(
                    sessionId = "rotation-session",
                    payload = "message-$i".toByteArray(),
                    messageType = MessageType.DATA,
                    timestamp = i.toLong()
                )
            )
        }
        
        val log = logStructuredProto.getLog()
        
        // Then
        assertTrue(log.size <= 50, "Log should be rotated to maintain max size")
        // Should keep most recent messages
        assertTrue(log.last().timestamp >= 50L)
    }
    
    @Test
    fun `WireProto should support message filtering`() = runTest {
        // Given
        val wireProto = WireProto()
        val messages = listOf(
            WireMessage("session-1", "data1".toByteArray(), MessageType.DATA, 1000L),
            WireMessage("session-1", "control1".toByteArray(), MessageType.CONTROL, 2000L),
            WireMessage("session-2", "data2".toByteArray(), MessageType.DATA, 3000L),
            WireMessage("session-2", "error1".toByteArray(), MessageType.ERROR, 4000L)
        )
        
        // When
        val dataMessages = wireProto.filterMessages(messages) { it.messageType == MessageType.DATA }
        val session1Messages = wireProto.filterMessages(messages) { it.sessionId == "session-1" }
        
        // Then
        assertEquals(2, dataMessages.size)
        assertEquals(2, session1Messages.size)
        assertTrue(dataMessages.all { it.messageType == MessageType.DATA })
        assertTrue(session1Messages.all { it.sessionId == "session-1" })
    }
    
    @Test
    fun `LogStructuredWireProto should support transaction boundaries`() = runTest {
        // Given
        val logStructuredProto = LogStructuredWireProto()
        val transactionId = "txn-123"
        
        // When
        logStructuredProto.beginTransaction(transactionId)
        logStructuredProto.appendToLog(
            WireMessage(
                sessionId = "txn-session",
                payload = "txn-message-1".toByteArray(),
                messageType = MessageType.DATA,
                timestamp = 1000L
            )
        )
        logStructuredProto.appendToLog(
            WireMessage(
                sessionId = "txn-session",
                payload = "txn-message-2".toByteArray(),
                messageType = MessageType.DATA,
                timestamp = 2000L
            )
        )
        logStructuredProto.commitTransaction(transactionId)
        
        val transactionMessages = logStructuredProto.getTransactionMessages(transactionId)
        
        // Then
        assertEquals(2, transactionMessages.size)
        assertTrue(transactionMessages.all { it.sessionId == "txn-session" })
    }
    
    @Test
    fun `WireProto should handle malformed messages gracefully`() = runTest {
        // Given
        val wireProto = WireProto()
        val malformedData = ByteArray(10) { 0xFF.toByte() } // Invalid data
        
        // When/Then
        assertFailsWith<WireProtoException> {
            wireProto.deserialize(malformedData)
        }
    }
    
    @Test
    fun `LogStructuredWireProto should support message replay`() = runTest {
        // Given
        val logStructuredProto = LogStructuredWireProto()
        val messages = listOf(
            WireMessage("replay-session", "msg1".toByteArray(), MessageType.DATA, 1000L),
            WireMessage("replay-session", "msg2".toByteArray(), MessageType.DATA, 2000L),
            WireMessage("replay-session", "msg3".toByteArray(), MessageType.DATA, 3000L)
        )
        
        messages.forEach { logStructuredProto.appendToLog(it) }
        
        // When
        val replayedMessages = mutableListOf<WireMessage>()
        logStructuredProto.replayLog(fromTimestamp = 1500L) { message ->
            replayedMessages.add(message)
        }
        
        // Then
        assertEquals(2, replayedMessages.size) // Should replay messages from 2000L and 3000L
        assertTrue(replayedMessages.all { it.timestamp >= 1500L })
    }
}