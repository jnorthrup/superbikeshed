@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import borg.trikeshed.lib.*

class QuicTDDTest {

    @Test
    fun `QUIC client and server should establish channel-based connection`() = runTest {
        // Given: A mock transport layer
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        
        // When: Both sides connect
        val clientConnected = clientConn.connect()
        val serverConnected = serverConn.connect()
        
        // Then: Both connections should be established
        assertTrue(clientConnected)
        assertTrue(serverConnected)
        assertTrue(clientConn.isAlive())
        assertTrue(serverConn.isAlive())
    }
    
    @Test
    fun `QUIC should support bidirectional stream communication`() = runTest {
        // Given: Connected client and server
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        clientConn.connect()
        serverConn.connect()
        
        // When: Client creates a stream and sends data
        val clientStream = clientConn.createStream()
        val testData = "Hello QUIC!".toByteArray()
        clientStream.send(testData)
        
        // And: Server accepts the stream and reads data
        val serverStream = serverConn.acceptStream()
        val receivedData = serverStream.receive()
        
        // Then: Data should be transmitted correctly
        assertTrue(receivedData.contentEquals(testData))
        assertEquals(clientStream.id + 1, serverStream.id) // Server streams are odd
    }
    
    @Test
    fun `QUIC should handle multiple concurrent streams`() = runTest {
        // Given: Connected client and server
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        clientConn.connect()
        serverConn.connect()
        
        // When: Client creates multiple streams
        val stream1 = clientConn.createStream()
        val stream2 = clientConn.createStream()
        val stream3 = clientConn.createStream()
        
        // Then: All streams should have unique IDs and be tracked
        assertEquals(3, clientConn.getActiveStreamCount())
        assertTrue(stream1.id != stream2.id)
        assertTrue(stream2.id != stream3.id)
        assertTrue(stream1.id != stream3.id)
        
        // And: Stream IDs should follow client pattern (even numbers)
        assertTrue(stream1.id % 2 == 0L)
        assertTrue(stream2.id % 2 == 0L)
        assertTrue(stream3.id % 2 == 0L)
    }
    
    @Test
    fun `QUIC should support request-response pattern`() = runTest {
        // Given: Connected client and server
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        clientConn.connect()
        serverConn.connect()
        
        // When: Client sends a request
        val clientStream = clientConn.createStream()
        val request = "GET /api/data HTTP/3\r\n\r\n".toByteArray()
        clientStream.send(request)
        
        // And: Server processes request and sends response
        val serverStream = serverConn.acceptStream()
        val receivedRequest = serverStream.receive()
        val response = "HTTP/3 200 OK\r\nContent-Length: 13\r\n\r\nHello Client!".toByteArray()
        serverStream.send(response)
        
        // Then: Client should receive the response
        val receivedResponse = clientStream.receive()
        
        assertTrue(receivedRequest.contentEquals(request))
        assertTrue(receivedResponse.contentEquals(response))
    }
    
    @Test
    fun `QUIC should handle stream completion with FIN flag`() = runTest {
        // Given: Connected client and server with a stream
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        clientConn.connect()
        serverConn.connect()
        
        val clientStream = clientConn.createStream()
        val serverStream = serverConn.acceptStream()
        
        // When: Client sends data with FIN flag
        val data = "Final message".toByteArray()
        clientStream.send(data, fin = true)
        
        // Then: Stream should be marked as finished
        assertTrue(clientStream.isFinished())
        
        // And: Server should receive the data
        val received = serverStream.receive()
        assertTrue(received.contentEquals(data))
    }
    
    @Test
    fun `QUIC should support attention-based range operations`() = runTest {
        // Given: Connected client and server
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        clientConn.connect()
        serverConn.connect()
        
        // When: Client requests multiple ranges (simulating attention mechanism)
        val ranges: Indexed<Twin<Long>> = 3 j { i: Int ->
            when (i) {
                0 -> 0L j 1023L      // First kilobyte
                1 -> 2048L j 3071L   // Third kilobyte (skipping second)
                2 -> 4096L j 5119L   // Fifth kilobyte
                else -> 0L j 0L
            }
        }
        
        // Then: Each range should be properly structured
        assertEquals(3, ranges.a)
        assertEquals(0L, ranges.b(0).a)      // First range start
        assertEquals(1023L, ranges.b(0).b)   // First range end
        assertEquals(2048L, ranges.b(1).a)   // Second range start (gap demonstrates attention)
        assertEquals(3071L, ranges.b(1).b)   // Second range end
        
        // And: Simulate sending range requests over separate streams
        val streams = mutableListOf<MockQuicStream>()
        for (i in 0 until ranges.a) {
            val stream = clientConn.createStream()
            val range = ranges.b(i)
            val rangeHeader = "Range: bytes=${range.a}-${range.b}\r\n".toByteArray()
            stream.send(rangeHeader)
            streams.add(stream)
        }
        
        assertEquals(3, streams.size)
        assertEquals(3, clientConn.getActiveStreamCount())
    }
    
    @Test
    fun `QUIC should handle connection lifecycle properly`() = runTest {
        // Given: A transport and connected pair
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        clientConn.connect()
        serverConn.connect()
        
        // When: Streams are created and used
        val clientStream = clientConn.createStream()
        val serverStream = serverConn.acceptStream()
        
        assertTrue(clientConn.isAlive())
        assertTrue(serverConn.isAlive())
        assertEquals(1, clientConn.getActiveStreamCount())
        assertEquals(1, serverConn.getActiveStreamCount())
        
        // And: Connections are closed
        clientConn.close()
        serverConn.close()
        
        // Then: Streams should be cleaned up
        assertEquals(0, clientConn.getActiveStreamCount())
        assertEquals(0, serverConn.getActiveStreamCount())
        assertFalse(clientConn.isAlive())
        assertFalse(serverConn.isAlive())
    }
    
    @Test
    fun `QUIC should demonstrate HTTP3 over QUIC pattern`() = runTest {
        // Given: Client and server with HTTP/3 simulation
        val transport = MockQuicTransport()
        val (clientConn, serverConn) = transport.createConnectedPair()
        clientConn.connect()
        serverConn.connect()
        
        // When: Client makes HTTP/3 style request with multiple resources
        val requests = listOf(
            "GET /index.html HTTP/3",
            "GET /style.css HTTP/3", 
            "GET /script.js HTTP/3"
        )
        
        val clientStreams = mutableListOf<MockQuicStream>()
        
        // Send all requests concurrently (HTTP/3 multiplexing)
        for (request in requests) {
            val stream = clientConn.createStream()
            stream.send(request.toByteArray())
            clientStreams.add(stream)
        }
        
        // Then: All requests should be sent on separate streams
        assertEquals(3, clientConn.getActiveStreamCount())
        assertEquals(3, clientStreams.size)
        
        // And: Server can handle all requests independently
        val serverStreams = mutableListOf<MockQuicStream>()
        repeat(3) {
            val serverStream = serverConn.acceptStream()
            serverStreams.add(serverStream)
        }
        
        assertEquals(3, serverConn.getActiveStreamCount())
        
        // Verify each server stream can receive its request
        for (i in serverStreams.indices) {
            val received = serverStreams[i].receive()
            assertTrue(received.isNotEmpty())
            // In real implementation, would verify specific request content
        }
    }
}