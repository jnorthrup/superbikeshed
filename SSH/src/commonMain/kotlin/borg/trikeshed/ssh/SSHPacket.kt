package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.crypto.*
import kotlinx.coroutines.*

/**
 * SSH Packet Encoding and Decoding
 * 
 * Handles the wire format for SSH packets including encryption,
 * padding, and MAC computation.
 */

// Packet encoder
class SSHPacketEncoder(
    internal val crypto: SSHCryptoService,
    internal val context: SSHTransportContext
) {
    suspend fun encode(
        messageType: SSHMessageType,
        payload: SSHPayload,
        sequenceNumber: SSHSequenceNumber
    ): SSHWirePacket {
        // Build complete payload with message type
        val fullPayload = (payload.component1() + 1) j { i: Int ->
            if (i == 0) messageType.value else payload[i - 1]
        }
        
        // Create packet with proper padding
        val packet = createPacket(fullPayload)
        
        // Encrypt if keys are active
        return crypto.encrypt(packet.encode(), context)
    }
    
    internal fun createPacket(payload: SSHPayload): SSHPacket {
        val blockSize = getBlockSize()
        val payloadLength = payload.component1()
        val paddingLengthFieldSize = 1
        val packetLengthFieldSize = 4
        
        // Calculate padding (4-255 bytes)
        var paddingLength = blockSize - ((packetLengthFieldSize + paddingLengthFieldSize + payloadLength) % blockSize)
        if (paddingLength < 4) {
            paddingLength += blockSize
        }
        
        // Generate random padding
        val padding = generatePadding(paddingLength)
        
        val packetLength = (paddingLengthFieldSize + payloadLength + paddingLength).toUInt()
        
        return SSHPacket(
            length = packetLength,
            paddingLength = paddingLength.toByte(),
            messageType = if (payload.component1() > 0) SSHMessageType.fromByte(payload[0]) ?: SSHMessageType.IGNORE else SSHMessageType.IGNORE,
            payload = if (payload.component1() > 1) {
                (payload.component1() - 1) j { i: Int -> payload[i + 1] }
            } else {
                0 j { 0.toByte() }
            },
            padding = padding,
            mac = 0 j { 0.toByte() } // MAC computed during encryption
        )
    }
    
    internal fun getBlockSize(): Int {
        // Get block size from current cipher
        // Default to 8 for initial handshake
        return 8
    }
    
    internal fun generatePadding(length: Int): Indexed<Byte> {
        return length j { i: Int -> (kotlin.random.Random.nextInt(256)).toByte() }
    }
}

// Packet decoder
class SSHPacketDecoder(
    internal val crypto: SSHCryptoService,
    internal val context: SSHTransportContext
) {
    suspend fun decode(
        wirePacket: SSHWirePacket,
        sequenceNumber: SSHSequenceNumber
    ): SSHPacket? {
        // Decrypt if keys are active
        val decrypted = crypto.decrypt(wirePacket, context)
        
        // Parse packet structure
        return parsePacket(decrypted)
    }
    
    internal fun parsePacket(data: SSHPayload): SSHPacket? {
        if (data.component1() < 6) return null // Minimum packet size
        
        // Extract packet length (4 bytes)
        val packetLength = ((data[0].toInt() and 0xFF) shl 24) or
                          ((data[1].toInt() and 0xFF) shl 16) or
                          ((data[2].toInt() and 0xFF) shl 8) or
                          (data[3].toInt() and 0xFF)
        
        // Extract padding length (1 byte)
        val paddingLength = data[4]
        
        // Extract message type (1 byte)
        val messageType = SSHMessageType.fromByte(data[5]) ?: return null
        
        // Calculate payload boundaries
        val payloadStart = 6
        val payloadEnd = 4 + packetLength.toInt() - paddingLength
        
        if (payloadEnd > data.component1()) return null // Incomplete packet
        
        // Extract payload
        val payload = (payloadEnd - payloadStart) j { i: Int ->
            data[payloadStart + i]
        }
        
        // Extract padding
        val paddingStart = payloadEnd
        val paddingEnd = paddingStart + paddingLength
        
        if (paddingEnd > data.component1()) return null // Invalid padding
        
        val padding = paddingLength.toInt() j { i: Int ->
            data[paddingStart + i]
        }
        
        // MAC is handled separately during decryption
        val mac = 0 j { 0.toByte() }
        
        return SSHPacket(
            length = packetLength.toUInt(),
            paddingLength = paddingLength,
            messageType = messageType,
            payload = payload,
            padding = padding,
            mac = mac
        )
    }
}

// Wire format builder
fun SSHPacket.encode(): SSHWirePacket {
    val totalSize = 4 + 1 + 1 + payload.component1() + padding.component1() + mac.component1()
    
    return totalSize j { i: Int ->
        when {
            i < 4 -> {
                // Packet length field
                val shift = (3 - i) * 8
                ((length shr shift) and 0xFFu).toByte()
            }
            i == 4 -> paddingLength
            i == 5 -> messageType.value
            i < 6 + payload.component1() -> payload[i - 6]
            i < 6 + payload.component1() + padding.component1() -> padding[i - 6 - payload.component1()]
            else -> mac[i - 6 - payload.component1() - padding.component1()]
        }
    }
}

// Message builders for common SSH messages
object SSHMessages {
    fun disconnect(
        reason: SSHDisconnectReason,
        description: String,
        language: String = ""
    ): SSHPayload {
        val descBytes = description.encodeToByteArray()
        val langBytes = language.encodeToByteArray()
        
        val size = 4 + 4 + descBytes.size + 4 + langBytes.size
        
        return size j { i: Int ->
            when {
                i < 4 -> ((reason shr ((3 - i) * 8)) and 0xFFu).toByte()
                i < 8 -> ((descBytes.size shr ((7 - i) * 8)) and 0xFF).toByte()
                i < 8 + descBytes.size -> descBytes[i - 8]
                i < 12 + descBytes.size -> ((langBytes.size shr ((11 + descBytes.size - i) * 8)) and 0xFF).toByte()
                else -> langBytes[i - 12 - descBytes.size]
            }
        }
    }
    
    fun serviceRequest(serviceName: String): SSHPayload {
        val nameBytes = serviceName.encodeToByteArray()
        val size = 4 + nameBytes.size
        
        return size j { i: Int ->
            when {
                i < 4 -> ((nameBytes.size shr ((3 - i) * 8)) and 0xFF).toByte()
                else -> nameBytes[i - 4]
            }
        }
    }
    
    fun serviceAccept(serviceName: String): SSHPayload {
        return serviceRequest(serviceName) // Same format
    }
    
    fun ignore(data: Indexed<Byte>): SSHPayload {
        val size = 4 + data.component1()
        
        return size j { i: Int ->
            when {
                i < 4 -> ((data.component1() shr ((3 - i) * 8)) and 0xFF).toByte()
                else -> data[i - 4]
            }
        }
    }
    
    fun debug(
        alwaysDisplay: Boolean,
        message: String,
        language: String = ""
    ): SSHPayload {
        val msgBytes = message.encodeToByteArray()
        val langBytes = language.encodeToByteArray()
        
        val size = 1 + 4 + msgBytes.size + 4 + langBytes.size
        
        return size j { i: Int ->
            when {
                i == 0 -> if (alwaysDisplay) 1 else 0
                i < 5 -> ((msgBytes.size shr ((4 - i) * 8)) and 0xFF).toByte()
                i < 5 + msgBytes.size -> msgBytes[i - 5]
                i < 9 + msgBytes.size -> ((langBytes.size shr ((8 + msgBytes.size - i) * 8)) and 0xFF).toByte()
                else -> langBytes[i - 9 - msgBytes.size]
            }
        }
    }
    
    fun unimplemented(sequenceNumber: UInt): SSHPayload {
        return 4 j { i: Int ->
            ((sequenceNumber shr ((3 - i) * 8)) and 0xFFu).toByte()
        }
    }
}

// String encoding/decoding helpers
fun encodeSSHString(str: String): Indexed<Byte> {
    val bytes = str.encodeToByteArray()
    return (4 + bytes.size) j { i: Int ->
        when {
            i < 4 -> ((bytes.size shr ((3 - i) * 8)) and 0xFF).toByte()
            else -> bytes[i - 4]
        }
    }
}

fun decodeSSHString(data: Indexed<Byte>, offset: Int = 0): Join<String, Int>? {
    if (offset + 4 > data.component1()) return null
    
    val length = ((data[offset].toInt() and 0xFF) shl 24) or
                 ((data[offset + 1].toInt() and 0xFF) shl 16) or
                 ((data[offset + 2].toInt() and 0xFF) shl 8) or
                 (data[offset + 3].toInt() and 0xFF)
    
    if (offset + 4 + length > data.component1()) return null
    
    val bytes = ByteArray(length) { i ->
        data[offset + 4 + i]
    }
    
    return bytes.decodeToString() j offset + 4 + length
}

// Name list encoding/decoding
fun encodeNameList(names: List<String>): Indexed<Byte> {
    return encodeSSHString(names.joinToString(","))
}

fun decodeNameList(data: Indexed<Byte>, offset: Int = 0): Join<List<String>, Int>? {
    val result = decodeSSHString(data, offset) ?: return null
    val names = if (result.component1().isEmpty()) emptyList() else result.component1().split(",")
    return names j result.component2()
}

// Binary data encoding
fun encodeSSHBinary(data: Indexed<Byte>): Indexed<Byte> {
    return (4 + data.component1()) j { i: Int ->
        when {
            i < 4 -> ((data.component1() shr ((3 - i) * 8)) and 0xFF).toByte()
            else -> data[i - 4]
        }
    }
}

fun decodeSSHBinary(data: Indexed<Byte>, offset: Int = 0): Join<Indexed<Byte>, Int>? {
    if (offset + 4 > data.component1()) return null
    
    val length = ((data[offset].toInt() and 0xFF) shl 24) or
                 ((data[offset + 1].toInt() and 0xFF) shl 16) or
                 ((data[offset + 2].toInt() and 0xFF) shl 8) or
                 (data[offset + 3].toInt() and 0xFF)
    
    if (offset + 4 + length > data.component1()) return null
    
    val binary = length j { i: Int -> data[offset + 4 + i] }
    
    return binary j offset + 4 + length
}

// Multi-precision integer encoding
fun encodeMPInt(value: Indexed<Byte>): Indexed<Byte> {
    // Remove leading zeros except for sign bit
    var start = 0
    while (start < value.component1() - 1 && value[start] == 0.toByte()) {
        start++
    }
    
    // Add padding if high bit is set (to maintain positive sign)
    val needsPadding = value.component1() > start && (value[start].toInt() and 0x80) != 0
    val length = value.component1() - start + (if (needsPadding) 1 else 0)
    
    return (4 + length) j { i: Int ->
        when {
            i < 4 -> ((length shr ((3 - i) * 8)) and 0xFF).toByte()
            needsPadding && i == 4 -> 0
            else -> value[start + i - 4 - (if (needsPadding) 1 else 0)]
        }
    }
}

fun decodeMPInt(data: Indexed<Byte>, offset: Int = 0): Join<Indexed<Byte>, Int>? {
    return decodeSSHBinary(data, offset)
}