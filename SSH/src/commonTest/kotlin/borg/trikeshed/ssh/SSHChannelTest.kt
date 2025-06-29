package borg.trikeshed.ssh

import borg.trikeshed.lib.toIndexed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SSHChannelTest {

    private fun createMockTransport(isServer: Boolean = true): SSHTransport {
        // This mock transport needs to be able to "send" packets to a test buffer
        // and allow test to "receive" packets from another buffer.
        // For now, it's just a basic instance.
        return SSHTransport(isServer = isServer)
    }

    @Test
    fun testServerHandlesChannelOpen_Session() {
        val serverTransport = createMockTransport(isServer = true)
        val channelManager = SSHChannelManager(serverTransport, isServer = true)

        val channelType = SSHChannelType.SESSION.typeName
        val senderChannelId = 100u // Client's chosen channel ID
        val initialWindow = 2048u
        val maxPacket = 1024u

        val openPayload = mutableListOf<Byte>()
        openPayload.add(SSHMessageType.CHANNEL_OPEN.value)
        openPayload.addAll(channelType.toSSHString())
        openPayload.addAll(senderChannelId.toBytes())
        openPayload.addAll(initialWindow.toBytes())
        openPayload.addAll(maxPacket.toBytes())
        // No type-specific data for "session"

        val openPacket = SSHPacket(SSHMessageType.CHANNEL_OPEN, 0u, 0u,0u, openPayload.toIndexed())
        val responses = channelManager.processPacket(openPacket)

        assertEquals(1, responses.size)
        // TODO: Parse response and confirm it's CHANNEL_OPEN_CONFIRMATION
        // And that it contains the server's allocated channel ID, client's senderChannelId,
        // and server's window/maxpacket sizes.

        // Example check if processPacket returned an SSHPacket structure:
        // val confirmPacket = responses[0]
        // assertEquals(SSHMessageType.CHANNEL_OPEN_CONFIRMATION, confirmPacket.messageType)
    }

    @Test
    fun testClientOpensSessionChannel_AndReceivesConfirmation() {
        val clientTransport = createMockTransport(isServer = false) // For client to send OPEN
        val clientChannelManager = SSHChannelManager(clientTransport, isServer = false)

        // Client initiates channel open
        val clientChannel = clientChannelManager.openSessionChannel()
        assertNotNull(clientChannel, "Client should be able to request a session channel")

        // Assume the CHANNEL_OPEN packet was "sent" by clientTransport.
        // Now, simulate server sending CHANNEL_OPEN_CONFIRMATION back.

        val serverAssignedChannelId = 200u // Server's ID for this channel
        val serverInitialWindow = SSHChannelManager.DEFAULT_WINDOW_SIZE
        val serverMaxPacket = SSHChannelManager.DEFAULT_MAX_PACKET_SIZE

        val confirmPayload = mutableListOf<Byte>()
        confirmPayload.add(SSHMessageType.CHANNEL_OPEN_CONFIRMATION.value)
        confirmPayload.addAll(clientChannel.localId.toBytes()) // Recipient channel (client's localId)
        confirmPayload.addAll(serverAssignedChannelId.toBytes()) // Sender channel (server's ID for it)
        confirmPayload.addAll(serverInitialWindow.toBytes())
        confirmPayload.addAll(serverMaxPacket.toBytes())

        val confirmPacket = SSHPacket(SSHMessageType.CHANNEL_OPEN_CONFIRMATION, 1u, 0u,0u, confirmPayload.toIndexed())

        // The channel manager should route this to the correct clientChannel instance
        clientChannelManager.processPacket(confirmPacket)

        assertEquals(serverAssignedChannelId, clientChannel.remoteId)
        assertEquals(serverInitialWindow, clientChannel.remoteWindowSize) // Assuming direct update
        assertEquals(serverMaxPacket, clientChannel.remoteMaxPacketSize) // Assuming direct update
        // TODO: Check if onChannelOpened callback was triggered if it was set.
    }

    @Test
    fun testChannelDataTransfer_WindowUpdate() {
        val mockTransport = createMockTransport(isServer = true) // Doesn't matter much for this
        val localId = 0u
        val remoteId = 100u
        val initialWindow = 1000u
        val maxPacket = 500u

        val channel = SSHChannel(
            localId, remoteId, mockTransport,
            localWindowSize = initialWindow, remoteWindowSize = initialWindow,
            localMaxPacketSize = maxPacket, remoteMaxPacketSize = maxPacket,
            channelType = SSHChannelType.SESSION
        )

        val dataToSend = ByteArray(200) { it.toByte() }.toIndexed()
        // channel.sendData(dataToSend) // This would try to use mockTransport.sendPacket

        // Simulate receiving data
        val receivedDataPayload = mutableListOf<Byte>()
        receivedDataPayload.add(SSHMessageType.CHANNEL_DATA.value)
        receivedDataPayload.addAll(localId.toBytes()) // Data is for our localId
        receivedDataPayload.addAll(dataToSend.toSSHString()) // Data itself is SSH string encoded

        val dataPacket = SSHPacket(SSHMessageType.CHANNEL_DATA, 2u, 0u,0u, receivedDataPayload.toIndexed())

        var dataReceivedByCallback: Indexed<Byte>? = null
        channel.onDataReceived = { data, streamId ->
            dataReceivedByCallback = data
        }
        channel.handleData(dataPacket.payload) // Directly call handler

        assertNotNull(dataReceivedByCallback)
        assertEquals(dataToSend.a, dataReceivedByCallback!!.a)
        // TODO: Compare content if necessary

        assertEquals(initialWindow - dataToSend.a.toUInt(), channel.localWindowSize, "Local window should decrease")
    }

    @Test
    fun testChannelRequest_Shell_Pty() {
         val mockTransport = createMockTransport(isServer = true)
         val channel = SSHChannel(0u, 100u, mockTransport, 1000u, 1000u, 500u, 500u, SSHChannelType.SESSION)

        // Simulate server-side channel receiving a pty-req
        val ptyReqSpecificData = "vt100".toSSHString() + 80u.toBytes() + 24u.toBytes() + 0u.toBytes() + 0u.toBytes() + "".toSSHString() // term, w, h, w_pix, h_pix, modes
        val ptyReqPayload = mutableListOf<Byte>().apply {
            add(SSHMessageType.CHANNEL_REQUEST.value)
            addAll(channel.localId.toBytes())
            addAll(SSHChannelRequest.PTY_REQ.requestType.toSSHString())
            add(1.toByte()) // want_reply = true
            addAll(ptyReqSpecificData)
        }
        val ptyReqPacket = SSHPacket(SSHMessageType.CHANNEL_REQUEST, 3u, 0u,0u, ptyReqPayload.toIndexed())
        var response = channel.handleChannelRequest(ptyReqPacket.payload) // Call handler directly

        assertNotNull(response, "PTY request should get a reply")
        // TODO: Parse response and check it's CHANNEL_SUCCESS (based on current dummy logic)

        // Simulate server-side channel receiving a shell request
        val shellReqPayload = mutableListOf<Byte>().apply {
            add(SSHMessageType.CHANNEL_REQUEST.value)
            addAll(channel.localId.toBytes())
            addAll(SSHChannelRequest.SHELL.requestType.toSSHString())
            add(1.toByte()) // want_reply = true
            // No specific data for "shell"
        }
        val shellReqPacket = SSHPacket(SSHMessageType.CHANNEL_REQUEST, 4u, 0u,0u, shellReqPayload.toIndexed())
        response = channel.handleChannelRequest(shellReqPacket.payload)
        assertNotNull(response, "Shell request should get a reply")
        // TODO: Parse response and check it's CHANNEL_SUCCESS
    }


    // --- Utility functions ---
    private fun String.toSSHString(): Indexed<Byte> {
        val bytes = this.encodeToByteArray()
        val lengthBytes = bytes.size.toUInt().toBytes()
        return lengthBytes + bytes.toIndexed()
    }
    private fun Indexed<Byte>.toSSHString(): Indexed<Byte> {
        val lengthBytes = this.a.toUInt().toBytes()
        return lengthBytes + this
    }
    private fun UInt.toBytes(): Indexed<Byte> = byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte()).toIndexed()
    private fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { i -> this[i] }
    private operator fun Indexed<Byte>.plus(other: Indexed<Byte>): Indexed<Byte> {
        val result = ByteArray(this.a + other.a)
        for(i in 0 until this.a) result[i] = this[i]
        for(i in 0 until other.a) result[this.a + i] = other[i]
        return result.toIndexed()
    }
}
