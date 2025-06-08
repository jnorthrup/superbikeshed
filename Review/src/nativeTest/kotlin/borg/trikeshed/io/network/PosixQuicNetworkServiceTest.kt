package borg.trikeshed.io.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest // From kotlinx-coroutines-test, ensure it's configured for native
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail // Explicit fail import

class PosixQuicNetworkServiceTest {

    private val service = PosixQuicNetworkService // Get the singleton instance

    @Test
    fun testBindAndClose() = runTest {
        assertFalse(service.isBound, "Socket should not be bound initially")
        assertNull(service.localAddress, "Local address should be null initially")

        val boundAddress = service.bind() // Bind to any available port on all interfaces (typically 0.0.0.0)
        assertNotNull(boundAddress, "Bound address should not be null")
        assertTrue(boundAddress.port > 0, "Port should be valid and ephemeral")
        // Host might be "0.0.0.0" or a specific interface depending on OS behavior with getaddrinfo(null,...) + getsockname
        // For Posix, binding to null host via getaddrinfo AI_PASSIVE typically results in 0.0.0.0 (INADDR_ANY)
        // and getsockname on such a socket often returns 0.0.0.0.
        // This is acceptable for "bind to all interfaces".
        assertTrue(boundAddress.host == "0.0.0.0" || boundAddress.host.isNotEmpty(), "Host should be 0.0.0.0 or a valid IP")

        assertTrue(service.isBound, "Socket should be bound after bind()")
        assertNotNull(service.localAddress, "Local address should be set after bind()")
        assertEquals(boundAddress, service.localAddress)

        service.close()
        assertFalse(service.isBound, "Socket should not be bound after close()")
        assertNull(service.localAddress, "Local address should be null after close()")
    }

    @Test
    fun testBindSpecificAddressAndPort() = runTest {
        val specificAddress = NetworkAddress("127.0.0.1", 0) // Ephemeral port on loopback
        var boundAddress: NetworkAddress? = null
        try {
            boundAddress = service.bind(specificAddress)
            assertNotNull(boundAddress)
            assertEquals("127.0.0.1", boundAddress!!.host)
            assertTrue(boundAddress.port > 0, "Bound port should be ephemeral and positive")
        } finally {
            service.close()
        }
    }

    @Test
    fun testSendReceiveLoopback() = runTest(timeout = 4000L) { // Overall test timeout
        val serverAddress = service.bind(NetworkAddress("127.0.0.1", 0))
        assertNotNull(serverAddress, "Server address should not be null")

        val message = "Hello Native QUIC Test"
        val sendPacket = DatagramPacket(message.encodeToByteArray(), serverAddress!!)

        // Sender job
        val clientJob = launch(Dispatchers.Default) { // Use Default dispatcher for potentially blocking native calls
            service.send(sendPacket)
        }

        var receivedPacket: DatagramPacket? = null
        try {
            withTimeout(2000L) { // Timeout for the receive operation
                receivedPacket = service.receive(1024)
            }
        } catch (e: TimeoutCancellationException) {
            fail("Receive operation timed out")
        }

        clientJob.join()

        assertNotNull(receivedPacket, "Received packet should not be null")
        assertEquals(message, receivedPacket!!.data.decodeToString(), "Received data should match sent message")
        assertEquals(serverAddress.port, receivedPacket!!.address.port, "Sender port should match server port in this loopback")
        assertEquals(serverAddress.host, receivedPacket!!.address.host, "Sender host should match server host in this loopback")

        service.close()
    }

    @Test
    fun testResolveLocalhost() = runTest {
        val addresses = service.resolve("localhost", 8080)
        assertNotNull(addresses)
        assertTrue(addresses.isNotEmpty(), "Should resolve localhost to at least one address")

        // localhost can resolve to 127.0.0.1 (IPv4) or ::1 (IPv6)
        // We check if either is present. PosixQuicNetworkService.resolve uses AF_UNSPEC.
        val foundLoopback = addresses.any { it.host == "127.0.0.1" || it.host == "::1" }
        assertTrue(foundLoopback, "Resolved addresses for localhost should include 127.0.0.1 or ::1. Found: ${addresses.joinToString()}")

        addresses.forEach { address ->
             assertEquals(8080, address.port, "Port should be 8080 for all resolved addresses")
        }
    }

    @Test
    fun testResolveUnknownHost() = runTest {
         val addresses = service.resolve("nonexistent.domain.for.quic.native.test", 80)
         assertTrue(addresses.isEmpty(), "Resolving unknown host should return empty list. Got: ${addresses.joinToString()}")
    }
}
