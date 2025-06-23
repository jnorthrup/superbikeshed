package borg.trikeshed.net.quic

import kotlin.test.*
import kotlinx.coroutines.*

class QuicMissingUnitTest {
    @Test
    fun `flow control blocks send when window exceeded`() = runTest {
        val stream = QuicStream(2L, 1024, 100, 1)
        stream.updateBytesSent(100)
        assertFailsWith<QuicError.StreamError.FlowControlBlocked> {
            stream.checkFlowControl(1)
        }
    }

    @Test
    fun `cannot send on closed stream`() = runTest {
        val stream = QuicStream(3L, 1024, 100, 1)
        stream.close()
        assertFailsWith<QuicError.StreamError.StreamClosed> {
            stream.updateBytesSent(1)
        }
    }

    @Test
    fun `protocol error for version mismatch`() = runTest {
        val err = QuicError.ProtocolError.VersionMismatch(0x1, 0x2)
        assertEquals("QUIC version mismatch: local=1, remote=2", err.message)
    }

    @Test
    fun `config validation throws on invalid values`() = runTest {
        assertFailsWith<IllegalArgumentException> { QuicConfig(initialConnectionFlowControlWindow = -1) }
        assertFailsWith<IllegalArgumentException> { QuicConfig(initialStreamFlowControlWindow = -1) }
        assertFailsWith<IllegalArgumentException> { QuicConfig(maxAckDelayMs = -1) }
        assertFailsWith<IllegalArgumentException> { QuicConfig(congestionControlAlgorithm = "") }
        assertFailsWith<IllegalArgumentException> { QuicConfig(defaultStreamPriority = -1) }
        assertFailsWith<IllegalArgumentException> { QuicConfig(mtu = 0) }
    }

    @Test
    fun `session cache stores, retrieves, and clears`() = runTest {
        val cache = object : QuicSessionCache {
            private val map = mutableMapOf<Pair<String, Int>, QuicSessionData>()
            override fun getSession(serverAddress: String, port: Int) = map[serverAddress to port]
            override fun storeSession(serverAddress: String, port: Int, sessionData: QuicSessionData) {
                map[serverAddress to port] = sessionData
            }
            override fun clearSession(serverAddress: String, port: Int) {
                map.remove(serverAddress to port)
            }
        }
        val session = QuicSessionData("host", 1, byteArrayOf(1), byteArrayOf(2), 123L)
        cache.storeSession("host", 1, session)
        assertEquals(session, cache.getSession("host", 1))
        cache.clearSession("host", 1)
        assertNull(cache.getSession("host", 1))
    }

    @Test
    fun `connection error for not connected`() = runTest {
        val err = QuicError.ConnectionError.NotConnected()
        assertEquals("QUIC connection not established", err.message)
    }

    @Test
    fun `transport error for packet too large`() = runTest {
        val err = QuicError.TransportError.PacketTooLarge(2001, 1500)
        assertEquals("Packet size 2001 exceeds MTU 1500", err.message)
    }

    @Test
    fun `stream error for invalid stream id`() = runTest {
        val err = QuicError.StreamError.InvalidStreamId(999)
        assertEquals("Invalid stream ID: 999", err.message)
    }

    @Test
    fun `stream error for stream limit exceeded`() = runTest {
        val err = QuicError.StreamError.StreamLimitExceeded()
        assertEquals("Maximum number of streams exceeded", err.message)
    }
} 