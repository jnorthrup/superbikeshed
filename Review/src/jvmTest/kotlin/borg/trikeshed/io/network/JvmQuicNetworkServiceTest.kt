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
import borg.trikeshed.lib.Join // Placeholder import
import borg.trikeshed.lib.first // Assuming extension property
import borg.trikeshed.lib.second // Assuming extension property
import borg.trikeshed.lib.isNotEmpty // Assuming extension property for Series

class JvmQuicNetworkServiceTest {

    private val service = JvmQuicNetworkService // Get the singleton instance

    @Test
    fun testBindAndClose() = runTest { // Use runTest for coroutine tests
        assertFalse("Socket should not be bound initially", service.isBound)
        assertNull("Local address should be null initially", service.localAddress)

        val boundAddress = service.bind()
        assertNotNull("Bound address should not be null", boundAddress)
        assertTrue("Port should be valid", boundAddress.second > 0)
        assertEquals("Host should be a local address", "0.0.0.0", boundAddress.first) // Or specific local IP

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
        val specificAddress = Join("127.0.0.1", 0)
        var boundAddress: NetworkAddress? = null
        try {
            boundAddress = service.bind(specificAddress)
            assertNotNull(boundAddress)
            assertEquals("127.0.0.1", boundAddress!!.first)
            assertTrue("Bound port should be ephemeral and positive", boundAddress.second > 0)
        } finally {
            service.close() // Ensure socket is closed even if assertions fail
        }
    }

    @Test(timeout = 5000) // JUnit timeout for the whole test
    fun testSendReceiveLoopback() = runTest(dispatchTimeoutMs = 4000L) { // runTest timeout
        val serverAddress = service.bind(Join("127.0.0.1", 0)) // Bind server to ephemeral port
        assertNotNull("Server address should not be null", serverAddress)

        val message = "Hello QUIC Test"
        val messageBytes = message.encodeToByteArray()
        // Constructing DatagramPacket: Join<ByteArray, Join<NetworkAddress, Int>>
        val sendPacket = Join(messageBytes, Join(serverAddress, messageBytes.size))


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
        assertEquals("Sender port should match server port in this config", serverAddress.second, receivedPacket!!.address.second)
        assertEquals("Sender host should match server host", serverAddress.first, receivedPacket!!.address.first)

        service.close()
    }


    @Test
    fun testResolveLocalhost() = runTest {
        val addresses = service.resolve("localhost", 8080)
        assertNotNull(addresses)
        assertTrue("Should resolve localhost to at least one address", addresses.isNotEmpty()) // Assuming Series.isNotEmpty()
        val firstAddress = addresses.toList().first() // Assuming Series.toList() and then List.first()
        assertEquals("127.0.0.1", firstAddress.first) // Common for localhost
        assertEquals(8080, firstAddress.second)
        // Note: localhost can resolve to IPv6 "::1" as well. Test might need to be more flexible.
    }

    @Test
    fun testResolveUnknownHost() = runTest {
         val addresses = service.resolve("nonexistent.domain.for.quic.test", 80)
         assertTrue("Resolving unknown host should return empty list", !addresses.isNotEmpty()) // Assuming Series.isNotEmpty()
    }
}
