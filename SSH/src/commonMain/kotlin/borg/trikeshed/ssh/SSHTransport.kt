package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.crypto.CommonCrypto
import borg.trikeshed.crypto.CryptoFactory // Assuming CryptoFactory is available

/**
 * SSH Transport Layer Implementation (RFC 4253)
 */
class SSHTransport(
    private val isServer: Boolean,
    private val crypto: CommonCrypto = CryptoFactory.getSecureRandom() // Adjust as needed
) {
    private var clientVersion: String? = null
    private var serverVersion: String = SSHProtocol.VERSION

    private var clientKexInitPayload: Indexed<Byte>? = null
    private var serverKexInitPayload: Indexed<Byte>? = null

    private var sequenceNumberOut: UInt = 0u
    private var sequenceNumberIn: UInt = 0u

    private var sessionId: Indexed<Byte>? = null
    private var encryptionCipher: CommonCrypto.SymmetricCipher? = null
    private var decryptionCipher: CommonCrypto.SymmetricCipher? = null
    private var outgoingMac: CommonCrypto.Hasher? = null
    private var incomingMac: CommonCrypto.Hasher? = null

    private var encryptionKey: CommonCrypto.SymmetricKey? = null
    private var decryptionKey: CommonCrypto.SymmetricKey? = null
    private var outgoingMacKey: CommonCrypto.SymmetricKey? = null
    private var incomingMacKey: CommonCrypto.SymmetricKey? = null

    private var transportState: TransportState = TransportState.VERSION_EXCHANGE

    private enum class TransportState {
        VERSION_EXCHANGE,
        KEX_INIT_SENT,
        KEX_INIT_RECEIVED,
        KEYS_ESTABLISHED,
        DISCONNECTED
    }

    fun processIncomingData(data: Indexed<Byte>): List<SSHPacket> {
        val receivedPackets = mutableListOf<SSHPacket>()
        // TODO: This is a simplification. Real implementation needs to handle partial packets,
        // multiple packets in one TCP segment, etc.
        // For now, assume 'data' contains one complete SSH packet or version string.

        when (transportState) {
            TransportState.VERSION_EXCHANGE -> {
                val versionString = data.toAsciiString().trim()
                if (versionString.startsWith("SSH-2.0-")) {
                    clientVersion = versionString
                    println("Received client version: $clientVersion")
                    if (!isServer) {
                        // Client receives server version
                        transportState = TransportState.KEX_INIT_RECEIVED // Expect KEXINIT next
                    } else {
                        // Server received client version, should send its own version if not already
                        // and then expect KEXINIT from client.
                        // For simplicity, assume server already sent its version.
                         transportState = TransportState.KEX_INIT_RECEIVED
                    }
                } else {
                    // Protocol mismatch or invalid version string
                    // TODO: Send SSH_MSG_DISCONNECT and close connection
                    println("Invalid version string received: $versionString")
                    transportState = TransportState.DISCONNECTED
                }
            }
            TransportState.KEX_INIT_SENT, TransportState.KEX_INIT_RECEIVED, TransportState.KEYS_ESTABLISHED -> {
                // Assume 'data' is a full packet for now
                val packet = parsePacket(data)
                if (packet != null) {
                    receivedPackets.add(packet)
                    // Further processing would happen by calling a method like handlePacket(packet)
                }
            }
            TransportState.DISCONNECTED -> {
                println("Transport is disconnected. Ignoring incoming data.")
            }
        }
        return receivedPackets
    }

    fun handlePacket(packet: SSHPacket): List<SSHPacket> {
        val responsePackets = mutableListOf<SSHPacket>()
        sequenceNumberIn++ // Assuming packet is valid at this point

        when (packet.messageType) {
            SSHMessageType.DISCONNECT -> handleDisconnect(packet)
            SSHMessageType.IGNORE -> handleIgnore(packet)
            SSHMessageType.UNIMPLEMENTED -> handleUnimplemented(packet)
            SSHMessageType.DEBUG -> handleDebug(packet)
            SSHMessageType.KEXINIT -> {
                if (isServer) {
                    clientKexInitPayload = packet.payload
                    println("Received KEXINIT from client.")
                    // Server should now send its KEXINIT if not already sent,
                    // or proceed with key exchange algorithm selection.
                    // responsePackets.add(createKexInitPacket()) // Example
                } else {
                    serverKexInitPayload = packet.payload
                    println("Received KEXINIT from server.")
                    // Client should now select algorithms and proceed with KEX.
                }
                transportState = TransportState.KEYS_ESTABLISHED // Simplified for now
            }
            // TODO: Handle other message types like SERVICE_REQUEST, NEWKEYS etc.
            else -> {
                println("Unsupported message type: ${packet.messageType}")
                responsePackets.add(createUnimplementedPacket(packet.sequenceNumber))
            }
        }
        return responsePackets
    }

    fun getVersionExchangeData(): Indexed<Byte>? {
        return if (transportState == TransportState.VERSION_EXCHANGE) {
            val versionString = "$serverVersion\r\n"
            if (isServer) transportState = TransportState.KEX_INIT_RECEIVED // Server waits for client KEXINIT
            else transportState = TransportState.KEX_INIT_SENT // Client sends KEXINIT after this
            versionString.toByteArray().toIndexed()
        } else null
    }

    private fun parsePacket(rawData: Indexed<Byte>): SSHPacket? {
        // 1. Decrypt if keys are established
        val decryptedPayload: Indexed<Byte>
        if (transportState == TransportState.KEYS_ESTABLISHED && decryptionCipher != null && decryptionKey != null) {
            // Simplified: assumes rawData is just the encrypted payload part
            // Real decryption needs IV, block chaining, etc.
            // val (ciphertext, tag) = ... split rawData ...
            // decryptedPayload = decryptionCipher!!.decrypt(decryptionKey!!, nonce, ciphertext, tag) ?: return null
            decryptedPayload = rawData // Placeholder
        } else {
            decryptedPayload = rawData
        }

        // 2. Check MAC if applicable (after decryption)
        // TODO: Implement MAC verification if keys are established

        // 3. Decompress if applicable (after MAC verification)
        // TODO: Implement decompression

        // Basic packet structure:
        // uint32 packet_length
        // byte padding_length
        // byte[n1] payload; n1 = packet_length - padding_length - 1
        // byte[n2] random_padding; n2 = padding_length
        // byte[m] mac (message authentication code); m = mac_length

        // This parsing is highly simplified and assumes rawData is the unencrypted, uncompressed packet data
        // starting from packet_length field.
        // In reality, the first few bytes might be encrypted block size or similar,
        // and then the actual encrypted packet.

        if (decryptedPayload.a < 5) return null // Min size: packet_length (4) + padding_length (1)
        val packetLength = decryptedPayload.toUInt(0)
        val paddingLength = decryptedPayload[4].toUInt()

        // Validate lengths (simplified)
        if (packetLength < paddingLength + 1 || decryptedPayload.a < packetLength + 4) { // +4 for packet_length field itself
            println("Invalid packet lengths")
            return null
        }

        val payloadLength = packetLength - paddingLength - 1u
        // Payload starts after packet_length (4 bytes) and padding_length (1 byte)
        val payload = decryptedPayload.slice(5, payloadLength.toInt())

        val messageCode = payload[0] // First byte of payload is message type
        val messageType = SSHMessageType.fromByte(messageCode) ?: run {
            println("Unknown message type code: $messageCode")
            // TODO: Send SSH_MSG_UNIMPLEMENTED if appropriate, or disconnect
            return null
        }

        // The sequence number for incoming packets is maintained by the receiver
        // and checked against the expected next sequence number.
        // The SSHPacket data structure might need to store the received sequence number.

        return SSHPacket(
            messageType,
            sequenceNumberIn, // This should be the actual received sequence number
            packetLength,
            0u, // MAC size, placeholder
            payload // This should be the actual payload part
        )
    }


    fun createPacket(messageType: SSHMessageType, payloadData: Indexed<Byte>): Indexed<Byte> {
        val payloadSize = payloadData.a
        val paddingLength = calculatePaddingLength(payloadSize + 1 + 4) // payload + padding_len_byte + packet_len_uint32
        val packetLength = (1 + payloadSize + paddingLength).toUInt() // padding_len_byte + payload + padding

        val buffer = mutableListOf<Byte>()
        // Packet Length (excluding MAC and packet_length field itself)
        buffer.add((packetLength shr 24).toByte())
        buffer.add((packetLength shr 16).toByte())
        buffer.add((packetLength shr 8).toByte())
        buffer.add(packetLength.toByte())

        // Padding Length
        buffer.add(paddingLength.toByte())

        // Payload
        payloadData.forEach { buffer.add(it) }

        // Random Padding
        for (i in 0 until paddingLength) {
            buffer.add(crypto.randomBytes(1)[0])
        }

        var packetBytes = buffer.toIndexed()

        // Encrypt if keys are established
        if (transportState == TransportState.KEYS_ESTABLISHED && encryptionCipher != null && encryptionKey != null) {
            // Simplified: assumes packetBytes is the plaintext to be encrypted
            // Real encryption needs IV, block chaining, etc.
            // val (ciphertext, tag) = encryptionCipher!!.encrypt(encryptionKey!!, nonce, packetBytes)
            // packetBytes = ciphertext + tag // Placeholder
        }

        // Add MAC if applicable (after encryption)
        if (transportState == TransportState.KEYS_ESTABLISHED && outgoingMac != null && outgoingMacKey != null) {
            // val mac = outgoingMac!!.hmac(outgoingMacKey!!, sequenceNumberOut.toBytes() + packetBytes) // seqNo + encrypted packet
            // packetBytes += mac
        }

        sequenceNumberOut++
        return packetBytes
    }

    fun createDisconnectPacket(reason: SSHDisconnectReason, description: String): Indexed<Byte> {
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.DISCONNECT.value)
        payload.addAll(reason.code.toBytes())
        payload.addAll(description.toSSHString())
        payload.addAll("en".toSSHString()) // language tag
        return createPacket(SSHMessageType.DISCONNECT, payload.toIndexed())
    }

    fun createUnimplementedPacket(rejectedPacketSequenceNumber: UInt): Indexed<Byte> {
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.UNIMPLEMENTED.value)
        payload.addAll(rejectedPacketSequenceNumber.toBytes())
        return createPacket(SSHMessageType.UNIMPLEMENTED, payload.toIndexed())
    }

    fun createKexInitPacket(): Indexed<Byte> {
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.KEXINIT.value)

        // Cookie (16 random bytes)
        payload.addAll(crypto.randomBytes(16))

        // Algorithms (name-list format)
        payload.addAll(AlgorithmPreferences.KeyExchange.preferences.joinToString(",").toSSHString())
        payload.addAll(AlgorithmPreferences.HostKey.preferences.joinToString(",").toSSHString())
        payload.addAll(AlgorithmPreferences.Cipher.preferences.joinToString(",").toSSHString()) // ctos
        payload.addAll(AlgorithmPreferences.Cipher.preferences.joinToString(",").toSSHString()) // stoc
        payload.addAll(AlgorithmPreferences.MAC.preferences.joinToString(",").toSSHString())    // ctos
        payload.addAll(AlgorithmPreferences.MAC.preferences.joinToString(",").toSSHString())    // stoc
        payload.addAll(AlgorithmPreferences.Compression.preferences.joinToString(",").toSSHString()) // ctos
        payload.addAll(AlgorithmPreferences.Compression.preferences.joinToString(",").toSSHString()) // stoc
        payload.addAll("".toSSHString()) // languages ctos (empty)
        payload.addAll("".toSSHString()) // languages stoc (empty)

        // First KEX packet follows? (boolean)
        payload.add(0.toByte()) // False

        // Reserved (uint32)
        payload.addAll(0u.toBytes())

        val kexInitData = payload.toIndexed()
        if (isServer) serverKexInitPayload = kexInitData
        else clientKexInitPayload = kexInitData

        if (transportState == TransportState.VERSION_EXCHANGE && !isServer) {
             transportState = TransportState.KEX_INIT_SENT
        } else if (transportState == TransportState.KEX_INIT_RECEIVED && isServer) {
            // Server received client KEXINIT, now sends its own
            // Next state depends on algorithm negotiation logic
        }

        return createPacket(SSHMessageType.KEXINIT, kexInitData)
    }


    private fun handleDisconnect(packet: SSHPacket) {
        // TODO: Parse disconnect message (reason code, description)
        val reasonCode = packet.payload.slice(1, 4).toUInt(0) // Skip message type byte
        val description = packet.payload.slice(5, packet.payload.a - 9).toAsciiString() // Approximation
        println("DISCONNECT received: Reason ${SSHDisconnectReason.fromCode(reasonCode)}, Desc: $description")
        transportState = TransportState.DISCONNECTED
    }

    private fun handleIgnore(packet: SSHPacket) {
        println("IGNORE received")
        // No action needed, payload is ignored
    }

    private fun handleUnimplemented(packet: SSHPacket) {
        val rejectedSeqNo = packet.payload.slice(1,4).toUInt(0) // Skip message type byte
        println("UNIMPLEMENTED received for sequence: $rejectedSeqNo")
        // Potentially log this or take action if it's unexpected
    }

    private fun handleDebug(packet: SSHPacket) {
        // Payload: byte message_type, boolean always_display, string message, string language_tag
        val alwaysDisplay = packet.payload[1] == 1.toByte()
        // This parsing is simplified, proper string parsing needed
        val message = packet.payload.slice(2, packet.payload.a - 2).toAsciiString()
        println("DEBUG received (display=$alwaysDisplay): $message")
    }

    private fun calculatePaddingLength(payloadLengthWithMsgCode: Int, blockSize: Int = 8): Int {
        // packet_length (4 bytes) + padding_length (1 byte) + payload (N bytes) + random_padding (P bytes)
        // The total length of the packet (excluding MAC) must be a multiple of the cipher block size or 8,
        // whichever is larger.
        // Padding must be at least 4 bytes.
        // packet_length = payload_length + padding_length + 1 (for padding_length byte itself)

        val currentPacketContentLength = 1 + payloadLengthWithMsgCode // padding_length_byte + payload_with_msg_code
        var padding = 4
        while ((currentPacketContentLength + padding) % blockSize != 0 || padding < 4) {
            padding++
        }
        return padding
    }

    // --- Utility functions ---
    // These should ideally be in a separate utility class or file
    private fun Indexed<Byte>.toAsciiString(): String {
        // Basic ASCII conversion, not suitable for all SSH strings (UTF-8)
        return this.map { it.toInt().toChar() }.joinToString("")
    }

    private fun Indexed<Byte>.toUInt(offset: Int): UInt {
        if (offset + 3 >= this.a) throw IndexOutOfBoundsException("Not enough bytes to read UInt")
        return ((this[offset].toUInt() and 0xFFu) shl 24) or
               ((this[offset + 1].toUInt() and 0xFFu) shl 16) or
               ((this[offset + 2].toUInt() and 0xFFu) shl 8) or
               (this[offset + 3].toUInt() and 0xFFu)
    }

    private fun String.toSSHString(): Indexed<Byte> {
        val bytes = this.encodeToByteArray()
        val lengthBytes = bytes.size.toUInt().toBytes()
        return lengthBytes + bytes.toIndexed()
    }

    private fun UInt.toBytes(): Indexed<Byte> {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        ).toIndexed()
    }

    private fun ByteArray.toIndexed(): Indexed<Byte> {
        return this.size j { i -> this[i] }
    }

    private operator fun Indexed<Byte>.plus(other: Indexed<Byte>): Indexed<Byte> {
        val result = ByteArray(this.a + other.a)
        for(i in 0 until this.a) result[i] = this[i]
        for(i in 0 until other.a) result[this.a + i] = other[i]
        return result.toIndexed()
    }
}
