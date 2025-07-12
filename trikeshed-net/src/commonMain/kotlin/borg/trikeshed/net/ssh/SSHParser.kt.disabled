@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.net.ssh

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.Indexed

// Extension function to read a UInt32 from a ByteIndexedBuffer
internal fun ByteIndexedBuffer.readUInt32(): UInt? {
    if (this.rem < 4) return null
    val b0 = this.get
    val b1 = this.get
    val b2 = this.get
    val b3 = this.get
    val value = b0.toUByte().toUInt() shl 24 or
            (b1.toUByte().toUInt() shl 16) or
            (b2.toUByte().toUInt() shl 8) or
            b3.toUByte().toUInt()
    return value
}

// Extension function to read a Byte from a ByteIndexedBuffer
internal fun ByteIndexedBuffer.readByte(): Byte? {
    if (this.rem < 1) return null
    return this.get
}

// Extension function to read a specified number of bytes from a ByteIndexedBuffer
internal fun ByteIndexedBuffer.readBytes(count: Int): Indexed<Byte>? {
    if (this.rem < count) return null
    val bytes = ByteArray(count) { this.get }
    val result = object : Indexed<Byte> {
        override val a: Int = count
        override val b: (Int) -> Byte = { i -> bytes[i] }
    }
    return result
}

// UnaryOperator for reading an SSH-formatted string (length-prefixed bytes)
object ReadSSHString {
    operator fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
        val originalPos = buffer.pos
        val length = buffer.readUInt32() ?: return null
        if (buffer.rem < length.toInt()) {
            buffer.pos(originalPos) // Rewind
            return null
        }
        buffer.pos(buffer.pos + length.toInt())
        return buffer
    }
}

// UnaryOperator for reading an SSH-formatted name list (length-prefixed, comma-separated strings)
object ReadSSHNameList {
    operator fun invoke(buffer: ByteIndexedBuffer): ByteIndexedBuffer? {
        val originalPos = buffer.pos
        val length = buffer.readUInt32() ?: return null
        if (buffer.rem < length.toInt()) {
            buffer.pos(originalPos) // Rewind
            return null
        }
        buffer.pos(buffer.pos + length.toInt())
        return buffer
    }
}

// Parser for SSHPacket
object SSHPacketParser {
    fun parse(buffer: ByteIndexedBuffer): SSHPacket? {
        val originalPos = buffer.pos

        // Packet Length (uint32)
        val packetLength = buffer.readUInt32() ?: return null

        // Padding Length (byte)
        val paddingLength = buffer.readByte() ?: return null

        // Payload and Padding
        val payloadAndPaddingLength = packetLength.toInt() - 1 // -1 for padding_length field
        val payloadAndPadding = buffer.readBytes(payloadAndPaddingLength) ?: return null

        val payload = payloadAndPadding // TODO: slice(0, payloadAndPaddingLength - paddingLength)
        val padding = payloadAndPadding // TODO: slice(payloadAndPaddingLength - paddingLength, paddingLength)

        // MAC (if present, not part of packetLength)
        // For now, assume MAC is handled externally or not present in initial parsing
        val mac = object : Indexed<Byte> {
            override val a: Int = 0
            override val b: (Int) -> Byte = { 0.toByte() }
        } // Placeholder

        return SSHPacket(
            packetLength = packetLength,
            paddingLength = paddingLength,
            payload = payload,
            padding = padding,
            mac = mac
        )
    }
}

// Parser for KexInit message
object KexInitParser {
    suspend fun parse(buffer: ByteIndexedBuffer): KexInit? {
        val originalPos = buffer.pos

        // Message type (byte) - should be SSH_MSG_KEXINIT (0x14)
        val messageType = buffer.readByte() ?: return null
        if (messageType != SSHProtocol.MessageTypes.SSH_MSG_KEXINIT) {
            buffer.pos(originalPos)
            return null
        }

        // Cookie (16 bytes)
        val cookie = buffer.readBytes(16) ?: return null

        // Name lists (length-prefixed strings)
        val kexAlgorithms = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val serverHostKeyAlgorithms = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val encryptionAlgorithmsClientToServer = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val encryptionAlgorithmsServerToClient = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val macAlgorithmsClientToServer = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val macAlgorithmsServerToClient = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val compressionAlgorithmsClientToServer = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val compressionAlgorithmsServerToClient = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val languagesClientToServer = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null
        val languagesServerToClient = buffer.readSSHNameList()?.decodeUtf8()?.asString()?.split(",")?.toIdx() ?: return null

        // First kex packet follows (byte)
        val firstKexPacketFollows = buffer.readByte()?.toInt() != 0 ?: return null

        // Reserved (uint32)
        val reserved = buffer.readUInt32() ?: return null

        return KexInit(
            cookie = cookie,
            kexAlgorithms = kexAlgorithms,
            serverHostKeyAlgorithms = serverHostKeyAlgorithms,
            encryptionAlgorithmsClientToServer = encryptionAlgorithmsClientToServer,
            encryptionAlgorithmsServerToClient = encryptionAlgorithmsServerToClient,
            macAlgorithmsClientToServer = macAlgorithmsClientToServer,
            macAlgorithmsServerToClient = macAlgorithmsServerToClient,
            compressionAlgorithmsClientToServer = compressionAlgorithmsClientToServer,
            compressionAlgorithmsServerToClient = compressionAlgorithmsServerToClient,
            languagesClientToServer = languagesClientToServer,
            languagesServerToClient = languagesServerToClient,
            firstKexPacketFollows = firstKexPacketFollows,
            reserved = reserved
        )
    }
}

// Parser for KexDhReply message
object KexDhReplyParser {
    suspend fun parse(buffer: ByteIndexedBuffer): KexDhReply? {
        val originalPos = buffer.pos

        // Message type (byte) - should be SSH_MSG_KEXDH_REPLY (0x1F)
        val messageType = buffer.readByte() ?: return null
        if (messageType != SSHProtocol.MessageTypes.SSH_MSG_KEXDH_REPLY) {
            buffer.pos(originalPos)
            return null
        }

        // Server public host key (SSH string)
        val hostKey = buffer.readSSHString() ?: return null

        // Ephemeral server public key (mpint)
        val ephemeralPublicKey = buffer.readSSHString() ?: return null

        // Signature of H (SSH string)
        val signature = buffer.readSSHString() ?: return null

        return KexDhReply(
            hostKey = hostKey,
            ephemeralPublicKey = ephemeralPublicKey,
            signature = signature
        )
    }
}

// Parser for SFTP packets
object SftpPacketParser {
    suspend fun parse(buffer: ByteIndexedBuffer): SSHPayload? {
        val originalPos = buffer.pos

        // Length (uint32)
        val length = buffer.readUInt32() ?: return null
        if (buffer.rem < length.toInt()) {
            buffer.pos(originalPos)
            return null
        }

        // Type (byte)
        val type = buffer.readByte() ?: return null

        // Request ID (uint32) - for most SFTP packets
        val requestId = buffer.readUInt32() ?: return null

        // Payload (remaining bytes)
        val payload = buffer.readBytes(length.toInt() - 1 - 4) ?: return null // -1 for type, -4 for requestId

        // For now, we return the raw payload. Specific SFTP packet types will have their own parsers.
        return payload
    }
}

// Extension functions for SSH parsing
fun ByteIndexedBuffer.readSSHNameList(): Indexed<Byte>? {
    val length = this.readUInt32() ?: return null
    if (this.rem < length.toInt()) {
        return null
    }
    return this.readBytes(length.toInt())
}

fun ByteIndexedBuffer.readSSHString(): Indexed<Byte>? {
    val length = this.readUInt32() ?: return null
    if (this.rem < length.toInt()) {
        return null
    }
    return this.readBytes(length.toInt())
}

fun Indexed<Byte>.decodeUtf8(): DecodedString {
    val bytes = ByteArray(this.a) { i -> this.b(i) }
    return DecodedString(bytes.decodeToString())
}

class DecodedString(val value: String) {
    fun asString(): String = value
}

fun List<String>.toIdx(): Indexed<String> {
    val list = this
    return object : Indexed<String> {
        override val a: Int = list.size
        override val b: (Int) -> String = { list[it] }
    }
}
