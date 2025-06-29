package borg.trikeshed.ssh

import borg.trikeshed.lib.*

class SSHChannelManager(
    private val transport: SSHTransport,
    private val isServer: Boolean
) {
    private val channels = mutableMapOf<UInt, SSHChannel>() // Keyed by recipient channel ID
    private var nextChannelId: UInt = if (isServer) 0u else 1u // Server uses even, client uses odd, typically starting higher in practice

    companion object {
        const val MAX_CHANNELS = 256 // Arbitrary limit
        const val DEFAULT_WINDOW_SIZE = SSHProtocol.DEFAULT_WINDOW_SIZE.toUInt()
        const val DEFAULT_MAX_PACKET_SIZE = SSHProtocol.DEFAULT_MAX_PACKET_SIZE.toUInt()
    }

    fun processPacket(packet: SSHPacket): List<SSHPacket> {
        val responses = mutableListOf<SSHPacket>()
        // Determine local channel ID based on context (sender/recipient)
        // For packets sent TO us, the channel ID in the packet is our local channel ID.
        // For packets we send, we use a recipient channel ID given by the other side.

        when (packet.messageType) {
            SSHMessageType.CHANNEL_OPEN -> {
                if (isServer) { // Server handles channel open requests
                    handleChannelOpen(packet.payload)?.let { responses.addAll(it) }
                } else {
                    // Client should not receive CHANNEL_OPEN unless it's acting as a multiplexer server
                    println("Client received unexpected CHANNEL_OPEN")
                    // responses.add(transport.createDisconnectPacket(...))
                }
            }
            SSHMessageType.CHANNEL_OPEN_CONFIRMATION -> {
                val recipientChannel = packet.payload.toUInt(1) // recipient channel
                channels[recipientChannel]?.handleOpenConfirmation(packet.payload)
                    ?: println("Received CHANNEL_OPEN_CONFIRMATION for unknown channel $recipientChannel")
            }
            SSHMessageType.CHANNEL_OPEN_FAILURE -> {
                 val recipientChannel = packet.payload.toUInt(1)
                 channels[recipientChannel]?.handleOpenFailure(packet.payload)
                 channels.remove(recipientChannel) // Clean up failed channel
            }
            SSHMessageType.CHANNEL_WINDOW_ADJUST -> {
                val recipientChannel = packet.payload.toUInt(1)
                channels[recipientChannel]?.handleWindowAdjust(packet.payload)
            }
            SSHMessageType.CHANNEL_DATA -> {
                val recipientChannel = packet.payload.toUInt(1)
                channels[recipientChannel]?.handleData(packet.payload)
            }
            SSHMessageType.CHANNEL_EXTENDED_DATA -> {
                val recipientChannel = packet.payload.toUInt(1)
                channels[recipientChannel]?.handleExtendedData(packet.payload)
            }
            SSHMessageType.CHANNEL_EOF -> {
                val recipientChannel = packet.payload.toUInt(1)
                channels[recipientChannel]?.handleEof()
            }
            SSHMessageType.CHANNEL_CLOSE -> {
                val recipientChannel = packet.payload.toUInt(1)
                channels[recipientChannel]?.handleClose()
                // Both sides must send CHANNEL_CLOSE, then channel is removed
                // This is simplified: a channel might linger until it also sends CLOSE
                channels.remove(recipientChannel)
                responses.add(createChannelClosePacket(channels[recipientChannel]?.remoteChannelId ?: 0u /* Should have it */))

            }
            SSHMessageType.CHANNEL_REQUEST -> {
                 val recipientChannel = packet.payload.toUInt(1)
                 channels[recipientChannel]?.handleChannelRequest(packet.payload)?.let { responses.add(it) }
            }
            SSHMessageType.CHANNEL_SUCCESS -> {
                val recipientChannel = packet.payload.toUInt(1)
                channels[recipientChannel]?.handleRequestSuccess()
            }
            SSHMessageType.CHANNEL_FAILURE -> {
                val recipientChannel = packet.payload.toUInt(1)
                channels[recipientChannel]?.handleRequestFailure()
            }
            else -> {
                // Not a channel manager message, or should be handled by specific channel
                println("ChannelManager received non-channel message or unhandled type: ${packet.messageType}")
            }
        }
        return responses
    }

    // --- Server-side channel opening ---
    private fun handleChannelOpen(payload: Indexed<Byte>): List<SSHPacket>? {
        // Payload: string channel_type, uint32 sender_channel, uint32 initial_window_size, uint32 maximum_packet_size
        var offset = 1 // Skip message type
        val channelTypeStr = payload.parseSshString(offset) { offset += it }
        val senderChannel = payload.toUInt(offset); offset += 4
        val initialWindow = payload.toUInt(offset); offset += 4
        val maxPacket = payload.toUInt(offset); offset += 4

        // Specific data based on channel_type
        // e.g. for "session": nothing
        // e.g. for "direct-tcpip": string host_to_connect, uint32 port_to_connect, string originator_IP_address, uint32 originator_port

        println("Channel open request: type='$channelTypeStr', sender_chan=$senderChannel, window=$initialWindow, max_pkt=$maxPacket")

        if (channels.size >= MAX_CHANNELS) {
            return listOf(createChannelOpenFailurePacket(senderChannel, SSHChannelOpenFailure.RESOURCE_SHORTAGE))
        }

        val localChannelId = allocateChannelId()
        val channel = SSHChannel(
            localId = localChannelId,
            remoteId = senderChannel,
            transport = transport,
            initialRemoteWindow = initialWindow,
            maxRemotePacketSize = maxPacket,
            channelType = SSHChannelType.fromName(channelTypeStr) ?: SSHChannelType.SESSION // Default, should validate
        )
        channels[localChannelId] = channel // Store by our local ID

        // TODO: Check channel type and specific data, decide if we accept
        // For now, accept "session" type
        if (channel.channelType == SSHChannelType.SESSION) {
            return listOf(channel.createOpenConfirmationPacket())
        } else {
            return listOf(createChannelOpenFailurePacket(senderChannel, SSHChannelOpenFailure.UNKNOWN_CHANNEL_TYPE))
        }
    }

    // --- Client-side channel opening ---
    fun openSessionChannel(): SSHChannel? {
        if (isServer || channels.size >= MAX_CHANNELS) return null

        val localChannelId = allocateChannelId()
        val channel = SSHChannel(
            localId = localChannelId,
            remoteId = 0u, // Will be set by CHANNEL_OPEN_CONFIRMATION
            transport = transport,
            initialRemoteWindow = 0u, // Will be set by confirmation
            maxRemotePacketSize = 0u, // Will be set by confirmation
            channelType = SSHChannelType.SESSION
        )
        channels[localChannelId] = channel

        // Client sends CHANNEL_OPEN
        transport.sendPacket(channel.createOpenRequestPacket(DEFAULT_WINDOW_SIZE, DEFAULT_MAX_PACKET_SIZE))
        return channel
    }
    // TODO: Add methods for other channel types like openDirectTcpIpChannel, etc.

    private fun allocateChannelId(): UInt {
        // Simplified allocation. Real SSH might involve finding the lowest available ID.
        val id = nextChannelId
        nextChannelId += 2 // Increment by 2 to maintain even/odd separation if strictly followed
        if (nextChannelId >= UInt.MAX_VALUE - 1) {
            // Handle exhaustion or wrap-around (though unlikely with UInt)
            throw IllegalStateException("Channel ID exhaustion")
        }
        return id
    }

    private fun createChannelOpenFailurePacket(recipientChannel: UInt, reason: SSHChannelOpenFailure): SSHPacket {
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_OPEN_FAILURE.value)
        payload.addAll(recipientChannel.toBytes())
        payload.addAll(reason.code.toBytes())
        payload.addAll("".toSSHString()) // description (empty)
        payload.addAll("".toSSHString()) // language tag (empty)
        return transport.createPacket(SSHMessageType.CHANNEL_OPEN_FAILURE, payload.toIndexed())
    }

    private fun createChannelClosePacket(recipientChannel: UInt): SSHPacket {
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_CLOSE.value)
        payload.addAll(recipientChannel.toBytes())
        return transport.createPacket(SSHMessageType.CHANNEL_CLOSE, payload.toIndexed())
    }


    // --- Utility functions ---
    private fun Indexed<Byte>.parseSshString(startOffset: Int, updateOffset: (Int) -> Unit): String {
        if (startOffset + 4 > this.a) return ""
        val length = this.toUInt(startOffset).toInt()
        val dataStart = startOffset + 4
        if (dataStart + length > this.a) return ""
        val strBytes = this.slice(dataStart, length)
        updateOffset(4 + length) // Total bytes consumed for this string field
        return strBytes.toAsciiString()
    }
    private fun String.toSSHString(): Indexed<Byte> {
        val bytes = this.encodeToByteArray()
        val lengthBytes = bytes.size.toUInt().toBytes()
        return lengthBytes + bytes.toIndexed()
    }
    private fun UInt.toBytes(): Indexed<Byte> = byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte()).toIndexed()
    private fun Indexed<Byte>.toUInt(offset: Int): UInt {
        if (offset + 3 >= this.a) throw IndexOutOfBoundsException("Not enough bytes for UInt at offset $offset from ${this.a} bytes")
        return ((this[offset].toUInt() and 0xFFu) shl 24) or
               ((this[offset + 1].toUInt() and 0xFFu) shl 16) or
               ((this[offset + 2].toUInt() and 0xFFu) shl 8) or
               (this[offset + 3].toUInt() and 0xFFu)
    }
    private fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { i -> this[i] }
    private operator fun Indexed<Byte>.plus(other: Indexed<Byte>): Indexed<Byte> {
        val result = ByteArray(this.a + other.a)
        for(i in 0 until this.a) result[i] = this[i]
        for(i in 0 until other.a) result[this.a + i] = other[i]
        return result.toIndexed()
    }
    private fun Indexed<Byte>.toAsciiString(): String = this.map { it.toInt().toChar() }.joinToString("")
}


class SSHChannel(
    val localId: UInt,
    var remoteId: UInt, // Can be updated by CHANNEL_OPEN_CONFIRMATION
    private val transport: SSHTransport,
    private var localWindowSize: UInt = SSHChannelManager.DEFAULT_WINDOW_SIZE,
    private var remoteWindowSize: UInt,
    private var localMaxPacketSize: UInt = SSHChannelManager.DEFAULT_MAX_PACKET_SIZE,
    private var remoteMaxPacketSize: UInt,
    val channelType: SSHChannelType
) {
    private var localEOF = false
    private var remoteEOF = false
    private var localClosed = false
    private var remoteClosed = false

    // Callbacks for specific channel types (e.g., session)
    var onDataReceived: ((data: Indexed<Byte>, streamId: UInt) -> Unit)? = null // streamId for extended data
    var onChannelOpened: (() -> Unit)? = null
    var onChannelEOF: (() -> Unit)? = null
    var onChannelClosed: (() -> Unit)? = null
    var onRequestResult: ((success: Boolean) -> Unit)? = null


    // --- Packet Creation (called by Channel user) ---
    fun sendData(data: Indexed<Byte>) {
        if (localClosed || remoteWindowSize < data.a) {
            println("Channel $localId: Cannot send data (closed or no window ${remoteWindowSize} < ${data.a})")
            return
        }
        // TODO: Split data if larger than remoteMaxPacketSize
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_DATA.value)
        payload.addAll(remoteId.toBytes())
        payload.addAll(data.toSSHString()) // Data itself is SSH string encoded

        transport.sendPacket(transport.createPacket(SSHMessageType.CHANNEL_DATA, payload.toIndexed()))
        remoteWindowSize -= data.a.toUInt()
    }

    fun sendExtendedData(typeCode: UInt, data: Indexed<Byte>) {
        // Similar to sendData, but for extended data (e.g. stderr)
        if (localClosed || remoteWindowSize < data.a) return

        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_EXTENDED_DATA.value)
        payload.addAll(remoteId.toBytes())
        payload.addAll(typeCode.toBytes())
        payload.addAll(data.toSSHString())

        transport.sendPacket(transport.createPacket(SSHMessageType.CHANNEL_EXTENDED_DATA, payload.toIndexed()))
        remoteWindowSize -= data.a.toUInt()
    }

    fun sendEof() {
        if (localEOF || localClosed) return
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_EOF.value)
        payload.addAll(remoteId.toBytes())
        transport.sendPacket(transport.createPacket(SSHMessageType.CHANNEL_EOF, payload.toIndexed()))
        localEOF = true
    }

    fun sendClose() {
        if (localClosed) return
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_CLOSE.value)
        payload.addAll(remoteId.toBytes())
        transport.sendPacket(transport.createPacket(SSHMessageType.CHANNEL_CLOSE, payload.toIndexed()))
        localClosed = true
        if (remoteClosed) onChannelClosed?.invoke() // If other side also closed
    }

    fun sendWindowAdjust(bytesToAdd: UInt) {
        localWindowSize += bytesToAdd // We are allowing more data from remote
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_WINDOW_ADJUST.value)
        payload.addAll(remoteId.toBytes())
        payload.addAll(bytesToAdd.toBytes())
        transport.sendPacket(transport.createPacket(SSHMessageType.CHANNEL_WINDOW_ADJUST, payload.toIndexed()))
    }

    // --- Channel Requests (e.g., pty-req, shell, exec) ---
    fun sendRequest(requestType: String, wantReply: Boolean, requestSpecificData: Indexed<Byte>): Boolean {
        if (localClosed) return false
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_REQUEST.value)
        payload.addAll(remoteId.toBytes())
        payload.addAll(requestType.toSSHString())
        payload.add(if (wantReply) 1.toByte() else 0.toByte())
        payload.addAll(requestSpecificData) // This data is specific to the request type

        transport.sendPacket(transport.createPacket(SSHMessageType.CHANNEL_REQUEST, payload.toIndexed()))
        return true
    }


    // --- Incoming Packet Handlers (called by ChannelManager) ---
    fun handleOpenConfirmation(payload: Indexed<Byte>) {
        // Payload: uint32 recipient_channel, uint32 sender_channel, uint32 initial_window_size, uint32 maximum_packet_size
        var offset = 1 // Skip message type
        // val clientRecipientChannel = payload.toUInt(offset); offset += 4 // This is our localId
        offset +=4 // Already know our localId
        this.remoteId = payload.toUInt(offset); offset += 4
        this.remoteWindowSize = payload.toUInt(offset); offset += 4
        this.remoteMaxPacketSize = payload.toUInt(offset); offset += 4

        println("Channel $localId open confirmed. Remote ID: $remoteId, Window: $remoteWindowSize, MaxPkt: $remoteMaxPacketSize")
        onChannelOpened?.invoke()
    }

    fun handleOpenFailure(payload: Indexed<Byte>) {
        // Payload: uint32 recipient_channel, uint32 reason_code, string description, string language_tag
        var offset = 1
        offset +=4 // recipient_channel (our localId)
        val reasonCode = payload.toUInt(offset); offset += 4
        val description = payload.parseSshString(offset) { offset += it }
        // language_tag also parsed if needed

        println("Channel $localId open failed. Reason: ${SSHChannelOpenFailure.fromCode(reasonCode)}, Desc: $description")
        localClosed = true; remoteClosed = true // Mark as unusable
        onChannelClosed?.invoke() // Or a specific onChannelOpenFailed callback
    }

    fun handleWindowAdjust(payload: Indexed<Byte>) {
        // Payload: uint32 recipient_channel, uint32 bytes_to_add
        var offset = 1 + 4 // Skip msg type and our recipient_channel
        val bytesToAdd = payload.toUInt(offset)
        remoteWindowSize += bytesToAdd
        println("Channel $localId: Remote window adjusted by $bytesToAdd, new size $remoteWindowSize")
    }

    fun handleData(payload: Indexed<Byte>) {
        // Payload: uint32 recipient_channel, string data
        var offset = 1 + 4 // Skip msg type and our recipient_channel
        val data = payload.parseSshString(offset){}.fromSSHStringPayload() // Data is itself an SSH string

        if (localWindowSize < data.a) {
            // Window violation, should disconnect
            println("Channel $localId: Received data larger than window! ${data.a} > $localWindowSize")
            // transport.sendDisconnect(...)
            return
        }
        localWindowSize -= data.a.toUInt()
        onDataReceived?.invoke(data, 0u) // 0u for stdout stream

        // TODO: Send window adjust if localWindowSize gets too low
        // if (localWindowSize < SOME_THRESHOLD) sendWindowAdjust(DEFAULT_WINDOW_SIZE - localWindowSize)
    }

    fun handleExtendedData(payload: Indexed<Byte>) {
        // Payload: uint32 recipient_channel, uint32 data_type_code, string data
        var offset = 1 + 4 // Skip msg type and our recipient_channel
        val dataTypeCode = payload.toUInt(offset); offset += 4
        val data = payload.parseSshString(offset){}.fromSSHStringPayload()

        if (localWindowSize < data.a) {
            // Window violation
            return
        }
        localWindowSize -= data.a.toUInt()
        onDataReceived?.invoke(data, dataTypeCode)
    }

    fun handleEof() {
        println("Channel $localId: Remote EOF received.")
        remoteEOF = true
        onChannelEOF?.invoke()
        if (localEOF) { // If we also sent EOF, can now send CLOSE
            sendClose()
        }
    }

    fun handleClose() {
        println("Channel $localId: Remote CLOSE received.")
        remoteClosed = true
        // We must also send CLOSE if we haven't already
        if (!localClosed) {
            sendClose()
        }
        onChannelClosed?.invoke()
    }

    fun handleChannelRequest(payload: Indexed<Byte>): SSHPacket? {
        // server-side handling of requests like "pty-req", "shell", "exec"
        // Payload: uint32 recipient_channel, string request_type, boolean want_reply, <request-specific data>
        var offset = 1 + 4 // Skip msg_type, recipient_channel
        val requestTypeStr = payload.parseSshString(offset) { offset += it }
        val wantReply = payload[offset++] == 1.toByte()
        val requestSpecificData = payload.slice(offset, payload.a - offset)

        println("Channel $localId: Received request '$requestTypeStr', want_reply=$wantReply")

        // TODO: Implement actual handling for different request types
        // For now, simulate success for "shell" and "pty-req", failure for others
        var success = false
        when (SSHChannelRequest.fromType(requestTypeStr)) {
            SSHChannelRequest.SHELL, SSHChannelRequest.PTY_REQ -> {
                println("Simulating success for $requestTypeStr")
                success = true
            }
            else -> {
                println("Request type '$requestTypeStr' not supported by this simple channel.")
                success = false
            }
        }

        if (wantReply) {
            val responsePayload = mutableListOf<Byte>()
            responsePayload.add(if (success) SSHMessageType.CHANNEL_SUCCESS.value else SSHMessageType.CHANNEL_FAILURE.value)
            responsePayload.addAll(remoteId.toBytes()) // This should be client's channel ID
            return transport.createPacket(
                if (success) SSHMessageType.CHANNEL_SUCCESS else SSHMessageType.CHANNEL_FAILURE,
                responsePayload.toIndexed()
            )
        }
        return null
    }

    fun handleRequestSuccess() {
        println("Channel $localId: Request succeeded.")
        onRequestResult?.invoke(true)
    }

    fun handleRequestFailure() {
        println("Channel $localId: Request failed.")
        onRequestResult?.invoke(false)
    }

    // --- For Client to initiate a channel ---
    fun createOpenRequestPacket(initialLocalWindow: UInt, initialLocalMaxPacket: UInt): SSHPacket {
        // This is for client to send CHANNEL_OPEN
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.CHANNEL_OPEN.value)
        payload.addAll(channelType.typeName.toSSHString())
        payload.addAll(localId.toBytes()) // This is our sender channel ID
        payload.addAll(initialLocalWindow.toBytes())
        payload.addAll(initialLocalMaxPacket.toBytes())

        // Add channel-type specific data if any (e.g. for direct-tcpip)
        // if (channelType == SSHChannelType.DIRECT_TCPIP) { ... }

        return transport.createPacket(SSHMessageType.CHANNEL_OPEN, payload.toIndexed())
    }


    // --- Utility functions (can be moved to a common place) ---
    private fun Indexed<Byte>.parseSshString(startOffset: Int, updateOffset: (Int) -> Unit = {}): String {
        if (startOffset + 4 > this.a) return ""
        val length = this.toUInt(startOffset).toInt()
        val dataStart = startOffset + 4
        if (dataStart + length > this.a) return ""
        val strBytes = this.slice(dataStart, length)
        updateOffset(4 + length)
        return strBytes.toAsciiString()
    }
    private fun Indexed<Byte>.fromSSHStringPayload(): Indexed<Byte> = this
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
    private fun Indexed<Byte>.toUInt(offset: Int): UInt {
         if (offset + 3 >= this.a) throw IndexOutOfBoundsException("Not enough bytes for UInt at offset $offset from ${this.a} bytes")
        return ((this[offset].toUInt() and 0xFFu) shl 24) or
               ((this[offset + 1].toUInt() and 0xFFu) shl 16) or
               ((this[offset + 2].toUInt() and 0xFFu) shl 8) or
               (this[offset + 3].toUInt() and 0xFFu)
    }
    private fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { i -> this[i] }
    private operator fun Indexed<Byte>.plus(other: Indexed<Byte>): Indexed<Byte> {
        val result = ByteArray(this.a + other.a)
        for(i in 0 until this.a) result[i] = this[i]
        for(i in 0 until other.a) result[this.a + i] = other[i]
        return result.toIndexed()
    }
    private fun Indexed<Byte>.toAsciiString(): String = this.map { it.toInt().toChar() }.joinToString("")

    // Dummy sendPacket on transport for client-side channel opening
    // In a real setup, SSHTransport would expose a proper send method.
    private fun SSHTransport.sendPacket(packet: Indexed<Byte>) { /* TODO: Integrate with actual transport send */ }
}
