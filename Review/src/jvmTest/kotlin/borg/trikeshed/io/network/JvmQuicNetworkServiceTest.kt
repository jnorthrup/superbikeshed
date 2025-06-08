package borg.trikeshed.io.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.* // Common JUnit assertions for JVM tests
import org.junit.Test // JUnit test annotation
import kotlin.coroutines.coroutineContext // To get current coroutineContext

class JvmQuicNetworkServiceTest {

    private val service = JvmQuicNetworkService // Get the singleton instance

    @Test
    fun testBindAndClose() = runTest { // Use runTest for coroutine tests
        assertFalse("Socket should not be bound initially", service.isBound)
        assertNull("Local address should be null initially", service.localAddress)

        val boundAddress = service.bind()
        assertNotNull("Bound address should not be null", boundAddress)
        assertTrue("Port should be valid", boundAddress.port > 0)
        assertEquals("Host should be a local address", "0.0.0.0", boundAddress.host) // Or specific local IP

        assertTrue("Socket should be bound after bind()", service.isBound)
        assertNotNull("Local address should be set after bind()", service.localAddress)
        assertEquals(boundAddress, service.localAddress)

        service.close()
        assertFalse("Socket should not be bound after close()", service.isBound)
        assertNull("Local address should be null after close()", service.localAddress)
    }

    @Test
    fun testBindSpecificAddressAndPort() = runTest {
        // Find an available port or use a common test port if possible (can be flaky)
        // For simplicity, let's try binding to port 0 on localhost which should pick an ephemeral port.
        val specificAddress = NetworkAddress("127.0.0.1", 0)
        var boundAddress: NetworkAddress? = null
        try {
            boundAddress = service.bind(specificAddress)
            assertNotNull(boundAddress)
            assertEquals("127.0.0.1", boundAddress!!.host)
            assertTrue("Bound port should be ephemeral and positive", boundAddress.port > 0)
        } finally {
            service.close() // Ensure socket is closed even if assertions fail
        }
    }

    @Test(timeout = 5000) // JUnit timeout for the whole test
    fun testSendReceiveLoopback() = runTest(dispatchTimeoutMs = 4000L) { // runTest timeout
        val serverAddress = service.bind(NetworkAddress("127.0.0.1", 0)) // Bind server to ephemeral port
        assertNotNull("Server address should not be null", serverAddress)

        val message = "Hello QUIC Test"
        val sendPacket = DatagramPacket(message.encodeToByteArray(), serverAddress!!)

        // Client (sender)
        val clientJob = launch(Dispatchers.IO) { // Launch sender in a different context if needed
            // Client doesn't need to bind to send to a known address typically.
            // However, our JvmQuicNetworkService.send might require a bound socket internally
            // if 'socket' is used directly. The current impl of JvmQuicNetworkService.send
            // uses the existing 'socket' instance, so it must be bound.
            // This means for a true loopback with one service, it sends to itself.

            // To make it cleaner, let's assume the service can send without an explicit client-side bind
            // if the underlying DatagramSocket allows it (it does).
            // Our current JvmQuicNetworkService.send uses the existing this.socket, so it must be bound.
            // This test structure is more of an integration test of the single instance.
            service.send(sendPacket) // Send to the address it's bound to
        }

        var receivedPacket: DatagramPacket? = null
        try {
            withTimeout(2000L) { // Timeout for receive operation
                 receivedPacket = service.receive(1024)
            }
        } catch (e: TimeoutCancellationException) {
            fail("Receive operation timed out")
        }

        clientJob.join() // Ensure sender job is complete

        assertNotNull("Received packet should not be null", receivedPacket)
        assertEquals("Received data should match sent message", message, receivedPacket!!.data.decodeToString())
        // Sender address might be the same as serverAddress in this loopback on single instance
        assertEquals("Sender port should match server port in this config", serverAddress.port, receivedPacket!!.address.port)
        assertEquals("Sender host should match server host", serverAddress.host, receivedPacket!!.address.host)

        service.close()
    }


    @Test
    fun testResolveLocalhost() = runTest {
        val addresses = service.resolve("localhost", 8080)
        assertNotNull(addresses)
        assertTrue("Should resolve localhost to at least one address", addresses.isNotEmpty())
        val firstAddress = addresses[0]
        assertEquals("127.0.0.1", firstAddress.host) // Common for localhost
        assertEquals(8080, firstAddress.port)
        // Note: localhost can resolve to IPv6 "::1" as well. Test might need to be more flexible.
    }

    @Test
    fun testResolveUnknownHost() = runTest {
         val addresses = service.resolve("nonexistent.domain.for.quic.test", 80)
         assertTrue("Resolving unknown host should return empty list", addresses.isEmpty())
    }
}
