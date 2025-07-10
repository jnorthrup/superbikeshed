@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.net.quic

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import borg.trikeshed.io.IOContext

class QuicClientTest {

    @Test
    fun `QuicClient should build with default config`() = runTest {
        val client = QuicClientBuilder()
            .config(QuicConfig.default())
            .build()
        
        assertNotNull(client)
    }
    
    @Test
    fun `QuicClient should support fluent builder pattern`() = runTest {
        val client = QuicClientBuilder()
            .connectTimeout(kotlin.time.Duration.parse("10s"))
            .idleTimeout(kotlin.time.Duration.parse("600s"))
            .sessionCache(DefaultQuicSessionCache())
            .build()
            
        assertNotNull(client)
    }
    
    @Test
    fun `QuicConnection should track connection state`() = runTest {
        val connection = QuicConnection(
            isClient = true,
            config = QuicConfig.default()
        )
        
        assertFalse(connection.isAlive())
        assertEquals(0, connection.getActiveStreamCount())
        assertTrue(connection.getBytesInFlight() >= 0)
    }
}