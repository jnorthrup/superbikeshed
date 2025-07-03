package borg.trikeshed.net.ssh

import borg.trikeshed.lib.Indexed
import borg.trikeshed.net.quic.QuicConnection
import borg.trikeshed.net.quic.QuicStream
import borg.trikeshed.net.socks.SocksEgressChannel
import borg.trikeshed.net.socks.SocksIngressChannel
import borg.trikeshed.net.socks.socksEgress
import borg.trikeshed.net.socks.socksIngress
import borg.trikeshed.lib.Indexed
import borg.trikeshed.net.quic.QuicConnection
import borg.trikeshed.net.quic.QuicStream
import borg.trikeshed.net.socks.SocksEgressChannel
import borg.trikeshed.net.socks.SocksEgressChannelElement
import borg.trikeshed.net.socks.SocksIngressChannel
import borg.trikeshed.net.socks.SocksIngressChannelElement
import borg.trikeshed.net.socks.socksEgress
import borg.trikeshed.net.socks.socksIngress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SocksSSHIntegrationTest {

    private lateinit var mockQuicConnection: QuicConnection
    private lateinit var mockQuicStream: QuicStream
    private lateinit var sshConnection: SSHConnection
    private lateinit var mockSocksIngress: SocksIngressChannel
    private lateinit var mockSocksEgress: SocksEgressChannel

    @BeforeEach
    fun setup() {
        mockQuicConnection = mockk(relaxed = true)
        mockQuicStream = mockk(relaxed = true)
        mockSocksIngress = mockk(relaxed = true)
        mockSocksEgress = mockk(relaxed = true)

        coEvery { mockQuicConnection.createStream() } returns mockQuicStream
        coEvery { mockQuicStream.internalReceiveChannel } returns Channel(capacity = Channel.UNLIMITED)
        coEvery { mockQuicStream.writeBytes(any()) } returns Unit

        sshConnection = SSHConnection(mockQuicConnection)
    }

    @Test
    fun `SSHConnection should use SocksEgressChannel for sending if present in context`() = runBlocking {
        val testData = "Hello SOCKS Egress".encodeToByteArray().size j { it: Int -> "Hello SOCKS Egress".encodeToByteArray()[it] }

        // Inject SocksEgressChannel into the coroutine context
        val contextWithSocks = coroutineContext.plus(SocksEgressChannelElement(mockSocksEgress))

        coEvery { mockSocksEgress.send(any()) } returns testData.a

        // Call a function that sends data, ensuring it runs with the modified context
        withContext(contextWithSocks) {
            sshConnection.sendPacket(testData)
        }

        // Verify that send was called on the mockSocksEgress
        coVerify(exactly = 1) { mockSocksEgress.send(testData) }
        // Verify that the underlying QuicStream was NOT called
        coVerify(exactly = 0) { mockQuicStream.writeBytes(any()) }
    }

    @Test
    fun `SSHConnection should use SocksIngressChannel for receiving if present in context`() = runBlocking {
        val receivedData = "Hello SOCKS Ingress".encodeToByteArray().size j { it: Int -> "Hello SOCKS Ingress".encodeToByteArray()[it] }

        // Inject SocksIngressChannel into the coroutine context
        val contextWithSocks = coroutineContext.plus(SocksIngressChannelElement(mockSocksIngress))

        coEvery { mockSocksIngress.receive() } returns receivedData

        // Start the SSH connection, which will attempt to receive packets
        // We need to mock the internalReceiveChannel of QuicStream to return an empty buffer
        // so that the receivePacket() function falls back to socksIngress
        coEvery { mockQuicStream.internalReceiveChannel.receive() } returns borg.trikeshed.nio.PlatformByteBuffer(0)

        // Call a function that receives data, ensuring it runs with the modified context
        withContext(contextWithSocks) {
            // Simulate the connection process to trigger receivePacket
            sshConnection.connect()
            // Manually call receivePacket for testing purposes, as connect() has its own loop
            val packet = sshConnection.receivePacket()
            assertNotNull(packet)
            assertEquals(receivedData, packet.payload)
        }

        // Verify that receive was called on the mockSocksIngress
        coVerify(exactly = 1) { mockSocksIngress.receive() }
        // Verify that the underlying QuicStream's internalReceiveChannel was called (to return empty)
        coVerify(exactly = 1) { mockQuicStream.internalReceiveChannel.receive() }
    }
}
