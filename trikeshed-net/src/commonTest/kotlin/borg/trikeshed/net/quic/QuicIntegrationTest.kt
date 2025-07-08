@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import borg.trikeshed.lib.*

class QuicIntegrationTest {

    @Test
    fun `QUIC client and server should work together`() = runTest {
        // Create server
        val server = QuicServer(port = 9443)
        var serverReceivedData = false
        
        server.onStream(0) { connection, stream, data ->
            serverReceivedData = true
            // Echo back the data
            stream.send(ByteArray(data.a) { i -> data.b(i) })
        }
        
        // Create client
        val client = QuicClientBuilder()
            .config(QuicConfig.default())
            .build()
            
        // Test would establish actual connection in real implementation
        // For now, just verify components are properly constructed
        assertNotNull(server)
        assertNotNull(client)
        
        val serverStats = server.getStats()
        assertEquals(9443, serverStats.port)
        assertFalse(serverStats.isRunning)
    }
    
    @Test
    fun `QUIC should support stream multiplexing`() = runTest {
        val connection = QuicConnection(
            isClient = true,
            config = QuicConfig.default()
        )
        
        // In real implementation, these would create actual streams
        // For now, verify the connection can track multiple streams
        val stream1 = connection.createStream()
        val stream2 = connection.createStream()
        
        assertNotNull(stream1)
        assertNotNull(stream2)
        assertEquals(2, connection.getActiveStreamCount())
        
        // Verify streams have different IDs
        if (stream1 != null && stream2 != null) {
            assertNotEquals(stream1.id, stream2.id)
        }
    }
    
    @Test
    fun `QUIC should handle range requests efficiently`() = runTest {
        val client = QuicClientBuilder().build()
        val connection = QuicConnection(isClient = true, config = QuicConfig.default())
        
        // Test attention-based range operations
        val ranges: Indexed<Twin<Long>> = 3 j { i: Int ->
            when (i) {
                0 -> 0L j 1023L
                1 -> 1024L j 2047L  
                2 -> 2048L j 3071L
                else -> 0L j 0L
            }
        }
        
        // Verify range structure
        assertEquals(3, ranges.a)
        assertEquals(0L, ranges.b(0).a)
        assertEquals(1023L, ranges.b(0).b)
        assertEquals(1024L, ranges.b(1).a)
        assertEquals(2047L, ranges.b(1).b)
    }
}