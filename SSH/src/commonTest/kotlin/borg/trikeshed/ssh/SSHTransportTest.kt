package borg.trikeshed.ssh

import borg.trikeshed.lib.toIndexed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SSHTransportTest {

    @Test
    fun testVersionExchange_ClientSide() {
        val clientTransport = SSHTransport(isServer = false)

        // Client prepares its version string to send
        val clientVersionData = clientTransport.getVersionExchangeData()
        assertNotNull(clientVersionData)
        assertEquals("SSH-2.0-TrikeShed_1.0\r\n", clientVersionData.toAsciiString())

        // Client receives server's version string
        val serverVersionString = "SSH-2.0-OpenSSH_8.2p1\r\n".toByteArray().toIndexed()
        clientTransport.processIncomingData(serverVersionString)

        // TODO: Add assertions here to check clientTransport's internal state if it stores serverVersion
        // For now, this test mainly checks the getVersionExchangeData and basic processing path.
    }

    @Test
    fun testVersionExchange_ServerSide() {
        val serverTransport = SSHTransport(isServer = true)

        // Server prepares its version string to send
        val serverVersionData = serverTransport.getVersionExchangeData()
        assertNotNull(serverVersionData)
        assertEquals("SSH-2.0-TrikeShed_1.0\r\n", serverVersionData.toAsciiString())

        // Server receives client's version string
        val clientVersionString = "SSH-2.0-PuTTY_Release_0.74\r\n".toByteArray().toIndexed()
        serverTransport.processIncomingData(clientVersionString)

        // TODO: Check serverTransport's state
    }

    @Test
    fun testCreateKexInitPacket() {
        val clientTransport = SSHTransport(isServer = false)
        // Simulate version exchange completed so KEXINIT can be created
        clientTransport.getVersionExchangeData() // Send client version
        clientTransport.processIncomingData("SSH-2.0-ServerTest_1.0\r\n".toByteArray().toIndexed()) // Receive server version

        val kexInitPacketBytes = clientTransport.createKexInitPacket()
        assertNotNull(kexInitPacketBytes)

        // Basic validation: check message type if we were to parse it
        // This requires more elaborate parsing than currently in SSHTransport.parsePacket
        // For now, just check it's not empty.
        assertTrue(kexInitPacketBytes.a > 0)

        // A more robust test would parse kexInitPacketBytes and verify its contents:
        // - Cookie (16 bytes)
        // - Algorithm lists (correctly formatted name-lists)
        // - first_kex_packet_follows boolean
        // - reserved uint32
    }

    @Test
    fun testCreateDisconnectPacket() {
        val transport = SSHTransport(isServer = false)
        val reason = SSHDisconnectReason.PROTOCOL_ERROR
        val description = "Test disconnect"

        val disconnectPacketBytes = transport.createDisconnectPacket(reason, description)
        assertNotNull(disconnectPacketBytes)
        assertTrue(disconnectPacketBytes.a > 0) // Basic check

        // TODO: Parse disconnectPacketBytes to verify:
        // - Message type is DISCONNECT
        // - Reason code matches
        // - Description matches
        // - Language tag is present
    }

    @Test
    fun testPacketPaddingCalculation() {
        val transport = SSHTransport(isServer = false)
        // Accessing private method via reflection or making it internal/public for testing if necessary
        // Or, test it indirectly by checking the length of a created packet.

        // Example: payload size 10, msg code 1, padding_len_byte 1, packet_len_uint32 4. Total 16.
        // calculatePaddingLength(payloadSize + 1 + 4)
        // calculatePaddingLength(10 + 1 + 4) = calculatePaddingLength(15)
        // For blockSize 8: currentPacketContentLength = 1 + 15 = 16
        // padding = 4. (16+4)%8 = 20%8 = 4. Need multiple of 8.
        // padding = 4 -> (1+10+4)%8 = 15%8 = 7 !=0
        // padding = 5 -> (1+10+5)%8 = 16%8 = 0. Padding is 5.
        // This is tricky because calculatePaddingLength takes `payloadLengthWithMsgCode`
        // Let's assume SSHTransport.createPacket calculates it correctly.

        // Test through createPacket's output length (simplified)
        // Create a packet with a known small payload
        val payload = "test".toByteArray().toIndexed() // 4 bytes
        val packetBytes = transport.createPacket(SSHMessageType.DEBUG, payload) // DEBUG has no complex structure

        // Expected structure:
        // packet_length (4 bytes, value = 1 + 4 + padding_length)
        // padding_length (1 byte)
        // msg_code (1 byte, from payload)
        // payload_data (4 bytes)
        // padding (P bytes)
        // Total = 4 + 1 + 1 + 4 + P = 10 + P
        // This total must be a multiple of 8 (default block size)
        // and P must be at least 4.
        // If P=4, total = 14 (not mult of 8)
        // If P=5, total = 15 (not mult of 8)
        // If P=6, total = 16 (mult of 8). So padding = 6.
        // Packet length field = 1 + 4 + 6 = 11

        // The createPacket in SSHTransport wraps this with its own length field for the raw bytes.
        // The `packetLength` field *inside* the packet is what `calculatePaddingLength` influences.
         assertTrue(packetBytes.a % 8 == 0, "Total packet bytes should be multiple of block size")
    }

    // --- Helper ---
    private fun Indexed<Byte>.toAsciiString(): String {
        return this.map { it.toInt().toChar() }.joinToString("")
    }
}
