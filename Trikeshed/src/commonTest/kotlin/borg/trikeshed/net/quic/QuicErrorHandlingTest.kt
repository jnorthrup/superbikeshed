package borg.trikeshed.net.quic

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Test suite for QUIC error handling and flow control improvements
 */
class QuicErrorHandlingTest {
    
    @Test
    fun `test QuicError hierarchy`() = runTest {
        // Test connection errors
        val notConnected = QuicError.ConnectionError.NotConnected()
        assertEquals("QUIC connection not established", notConnected.message)
        
        val connectionClosed = QuicError.ConnectionError.ConnectionClosed()
        assertEquals("QUIC connection already closed", connectionClosed.message)
        
        val flowControlBlocked = QuicError.ConnectionError.FlowControlBlocked(1024L, 2048L)
        assertEquals("Connection flow control blocked: window=1024, attempted=2048", flowControlBlocked.message)
        
        // Test stream errors
        val streamNotFound = QuicError.StreamError.StreamNotFound(123L)
        assertEquals("Stream 123 not found", streamNotFound.message)
        
        val streamClosed = QuicError.StreamError.StreamClosed(456L)
        assertEquals("Stream 456 is closed", streamClosed.message)
        
        val streamFlowControlBlocked = QuicError.StreamError.FlowControlBlocked(789L, 512L, 1024L)
        assertEquals("Stream 789 flow control blocked: window=512, attempted=1024", streamFlowControlBlocked.message)
        
        // Test protocol errors
        val invalidPacket = QuicError.ProtocolError.InvalidPacket("Test packet error")
        assertEquals("Test packet error", invalidPacket.message)
        
        val versionMismatch = QuicError.ProtocolError.VersionMismatch(1L, 2L)
        assertEquals("QUIC version mismatch: local=1, remote=2", versionMismatch.message)
        
        // Test transport errors
        val packetTooLarge = QuicError.TransportError.PacketTooLarge(2000, 1500)
        assertEquals("Packet size 2000 exceeds MTU 1500", packetTooLarge.message)
    }
    
    @Test
    fun `test QuicConfig with mtu`() = runTest {
        val config = QuicConfig(mtu = 1500)
        assertEquals(1500, config.mtu)
        
        // Test validation
        assertFailsWith<IllegalArgumentException> {
            QuicConfig(mtu = 0)
        }
        
        assertFailsWith<IllegalArgumentException> {
            QuicConfig(mtu = -1)
        }
    }
    
    @Test
    fun `test QuicStream flow control`() = runTest {
        val stream = QuicStream(
            id = 1L,
            bufferSize = 1024,
            initialWindowSize = 1000L,
            priority = 5
        )
        
        // Test initial state
        assertEquals(1000L, stream.currentStreamFlowControlWindow)
        assertEquals(0L, stream.bytesSentOnStream)
        
        // Test flow control check
        stream.checkFlowControl(500L) // Should not throw
        
        // Test flow control violation
        assertFailsWith<QuicError.StreamError.FlowControlBlocked> {
            stream.checkFlowControl(1500L)
        }
        
        // Test updating bytes sent
        stream.updateBytesSent(500L)
        assertEquals(500L, stream.bytesSentOnStream)
        
        // Test flow control after update
        assertFailsWith<QuicError.StreamError.FlowControlBlocked> {
            stream.checkFlowControl(600L)
        }
        
        // Test window update
        stream.updateFlowControlWindow(2000L)
        assertEquals(1500L, stream.currentStreamFlowControlWindow) // 2000 - 500 = 1500
        
        // Test closing stream
        stream.close()
        assertTrue(stream.isClosed())
        
        // Test operations on closed stream
        assertFailsWith<QuicError.StreamError.StreamClosed> {
            stream.checkFlowControl(100L)
        }
        
        assertFailsWith<QuicError.StreamError.StreamClosed> {
            stream.updateFlowControlWindow(3000L)
        }
        
        assertFailsWith<QuicError.StreamError.StreamClosed> {
            stream.updateBytesSent(100L)
        }
    }
    
    @Test
    fun `test QuicSessionData`() = runTest {
        val sessionData = QuicSessionData(
            serverAddress = "localhost",
            port = 4433,
            sessionId = byteArrayOf(1, 2, 3, 4),
            ticket = byteArrayOf(5, 6, 7, 8),
            expirationTime = 1234567890L,
            transportParams = mapOf("max_streams" to 100)
        )
        
        assertEquals("localhost", sessionData.serverAddress)
        assertEquals(4433, sessionData.port)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), sessionData.sessionId)
        assertContentEquals(byteArrayOf(5, 6, 7, 8), sessionData.ticket)
        assertEquals(1234567890L, sessionData.expirationTime)
        assertEquals(100, sessionData.transportParams["max_streams"])
    }
} 