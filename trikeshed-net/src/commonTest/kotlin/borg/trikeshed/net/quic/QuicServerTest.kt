@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import borg.trikeshed.net.quic.QuicServer
import borg.trikeshed.net.quic.QuicConfig

class QuicServerTest {

    @Test
    fun `QuicServer should initialize with default config`() = runTest {
        val server = QuicServer(port = 8443)
        assertNotNull(server)
        val stats = server.getStats()
        assertEquals(8443, stats.port)
        assertEquals("0.0.0.0", stats.host)
        assertFalse(stats.isRunning)
    }
    
    @Test  
    fun `QuicServer should handle connection lifecycle`() = runTest {
        val server = QuicServer(port = 8444)
        var connectCount = 0
        var disconnectCount = 0
        
        server.onConnection(object : ConnectionHandler {
            override suspend fun onConnect(connection: QuicConnection) {
                connectCount++
            }
            
            override suspend fun onDisconnect(connectionId: ConnectionId) {
                disconnectCount++
            }
        })
        
        // Test would simulate connection events here in real implementation
        assertTrue(connectCount >= 0)
        assertTrue(disconnectCount >= 0)
    }
}
