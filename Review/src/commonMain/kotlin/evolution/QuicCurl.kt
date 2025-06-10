package evolution

import kotlin.coroutines.CoroutineContext
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.min
// Import new types from QuicSpecTypes.kt, QuicTypes.kt
import evolution.* // Wildcard import for simplicity

// Custom Exception for QUIC Parsing Errors
class QuicParsingException(val errorCode: TransportErrorCode, message: String) : Exception(message)

// Expect declaration for cryptographic operations
expect object Crypto {
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray
    fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
    fun aesEcbEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray
}

// Expect declarations for TLS handshake functions
expect fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray
expect fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload?

// Represents a parsed QUIC packet using strong types
data class QuicPacket(
    val packetType: LongHeaderPacketType?,
    val connectionId: ConnectionID,
    val packetNumber: PacketNumber,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as QuicPacket
        if (packetType != other.packetType) return false
        if (connectionId != other.connectionId) return false
        if (packetNumber != other.packetNumber) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = packetType?.hashCode() ?: 0
        result = 31 * result + connectionId.hashCode()
        result = 31 * result + packetNumber.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

// Data class to hold derived initial keys
data class QuicInitialKeys(
    val key: ByteArray, val iv: ByteArray, val hp: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as QuicInitialKeys
        if (!key.contentEquals(other.key)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!hp.contentEquals(other.hp)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + hp.contentHashCode()
        return result
    }
}

// Helper class for passing int by reference
internal class MutableInt(var value: Int)

// Derives initial secrets as per RFC 9000, Section 5.2
internal fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys {
    val initialSalt = byteArrayOf(
        0x38, 0x76, 0x25, 0x7a, 0x4c, 0x8f, 0xac, 0x34,
        0x44, 0xce, 0x8f, 0x9a, 0x38, 0x60, 0xdf, 0x0b,
        0x60, 0x63, 0x0e, 0x84
    )

    val initialSecret = Crypto.hkdfExtract(initialSalt, clientDstConnId.value)
    val clientInitialSecret = Crypto.hkdfExpand(initialSecret, HKDFLabel("client in".encodeToByteArray()).value, 32)
    
    val keyLen = 16
    val ivLen = 12
    val hpLen = 16

    val clientKey = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic key".encodeToByteArray()).value, keyLen)
    val clientIv = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic iv".encodeToByteArray()).value, ivLen)
    val clientHp = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic hp".encodeToByteArray()).value, hpLen)
    
    return QuicInitialKeys(clientKey, clientIv, clientHp)
}

// Main entry point for receiving and processing a QUIC packet
fun parseQuicPacket(data: ByteArray, connection: QuicConnection): QuicPacket {
    if (data.isEmpty()) throw QuicParsingException(TransportErrorCode.FRAME_ENCODING_ERROR, "Received empty packet data.")

    try {
        val mutableOffset = MutableInt(0)
        val packet = if ((data[0].toUByte() and 0x80u) != 0u.toUByte()) { // Long Header
            parseLongHeaderPacket(data, mutableOffset, connection)
        } else { // Short Header
            parseShortHeaderPacket(data, mutableOffset, data[0].toUByte(), connection)
        }
        
        when (packet.packetType) {
            LongHeaderPacketType.INITIAL -> connection.state = QuicConnectionStateEnum.HANDSHAKE
            LongHeaderPacketType.HANDSHAKE -> connection.state = QuicConnectionStateEnum.CONNECTED
            LongHeaderPacketType.ZERO_RTT -> if (connection.state == QuicConnectionStateEnum.HANDSHAKE) connection.state = QuicConnectionStateEnum.CONNECTED
            else -> { /* Short header or other types */ }
        }
        
        return packet
    } catch (e: Exception) {
        throw IllegalStateException("Failed to parse QUIC packet: ${e.message}", e)
    }
}

// Updated to use strong types and refactored QuicConnection
internal fun parseLongHeaderPacket(data: ByteArray, offsetRef: MutableInt, connection: QuicConnection): QuicPacket {
    if (data.isEmpty()) throw QuicParsingException(TransportErrorCode.FRAME_ENCODING_ERROR, "Packet data is empty for parseLongHeaderPacket.")
    var offset = offsetRef.value
    
    val headerByte = data[offset++].toUByte()
    if ((headerByte and 0x80u) == 0u.toUByte()) throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "parseLongHeaderPacket called with short header data.")
    if ((headerByte and 0x40u) == 0u.toUByte()) throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "Invalid fixed bit for Long Header.")
    
    val packetType = LongHeaderPacketType.fromByte(headerByte)
        ?: throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "Invalid Long Header packet type bits.")

    val packetNumberLengthBits = (headerByte and 0x03u).toInt()
    val pnLength = packetNumberLengthBits + 1

    if (data.size < offset + 4) error("Packet too short for Version")
    val versionUInt = ((data[offset].toUByte().toUInt() shl 24) or
                      (data[offset + 1].toUByte().toUInt() shl 16) or
                      (data[offset + 2].toUByte().toUInt() shl 8) or
                      data[offset + 3].toUByte().toUInt())
    val version = QuicVersion(versionUInt)
    offset += 4
    
    if (data.size < offset + 1) error("Packet too short for DCID Len")
    val destConnIdLen = data[offset++].toUByte().toInt()
    if (destConnIdLen > 20) error("Invalid dest conn ID length: $destConnIdLen")
    if (data.size < offset + destConnIdLen) error("Packet too short for DCID")
    val destConnIdBytes = data.sliceArray(offset until offset + destConnIdLen)
    val destConnId = ConnectionID(destConnIdBytes)
    offset += destConnIdLen
    
    if (data.size < offset + 1) error("Packet too short for SCID Len")
    val srcConnIdLen = data[offset++].toUByte().toInt()
    if (srcConnIdLen > 20) error("Invalid src conn ID length: $srcConnIdLen")
    if (data.size < offset + srcConnIdLen) error("Packet too short for SCID")
    offset += srcConnIdLen

    if (packetType == LongHeaderPacketType.INITIAL) {
        if (offset >= data.size) error("Packet too short for Token Length")
        val (tokenLenValue, tokenLenBytes) = decodeVarint(data, offset)
        offset += tokenLenBytes
        if (data.size < offset + tokenLenValue.toInt()) error("Packet too short for Token")
        offset += tokenLenValue.toInt()
    }
    
    if (offset >= data.size) error("Packet too short for Payload Length")
    val (payloadLenValue, payloadLenBytes) = decodeVarint(data, offset)
    offset += payloadLenBytes
    
    if (data.size < offset + pnLength) error("Packet too short for Packet Number")
    var truncatedPacketNumberULong = 0uL
    for (i in 0 until pnLength) {
        truncatedPacketNumberULong = (truncatedPacketNumberULong shl 8) or data[offset++].toUByte().toULong()
    }

    val largestReceived = (connection.largestReceivedPacketNumber as PacketNumber).value
    val expectedPacketNumber = largestReceived + 1uL
    val packetNumberBits = pnLength * 8
    val window = 1uL shl packetNumberBits

    var reconstructedPnULong = (expectedPacketNumber and (window - 1uL).inv()) or truncatedPacketNumberULong
    if (reconstructedPnULong <= expectedPacketNumber - (window / 2uL)) {
        if (reconstructedPnULong > (ULong.MAX_VALUE - window)) error("Packet number overflow during reconstruction")
        reconstructedPnULong += window
    } else if (reconstructedPnULong > expectedPacketNumber + (window / 2uL) && reconstructedPnULong >= window) {
        if (reconstructedPnULong < window) error("Packet number underflow during reconstruction")
        reconstructedPnULong -= window
    }
    val reconstructedPacketNumber = PacketNumber(reconstructedPnULong)

    if (reconstructedPacketNumber.value > largestReceived) {
        connection.largestReceivedPacketNumber = reconstructedPacketNumber
    }

    val protectedPayloadAndTagLength = payloadLenValue.toInt() - pnLength
    if (protectedPayloadAndTagLength < 0) error("Invalid payload length calculation")
    if (data.size < offset + protectedPayloadAndTagLength) {
        error("Packet too short for protected payload and AEAD tag")
    }
    val payload = data.sliceArray(offset until offset + protectedPayloadAndTagLength)
    offset += protectedPayloadAndTagLength
    offsetRef.value = offset
    
    return QuicPacket(
        packetType = packetType,
        connectionId = destConnId,
        packetNumber = reconstructedPacketNumber,
        payload = payload
    )
}

// Updated to use strong types and refactored QuicConnection
internal fun parseShortHeaderPacket(data: ByteArray, offsetRef: MutableInt, firstByte: UByte, connection: QuicConnection): QuicPacket {
    var offset = offsetRef.value
    
    if ((firstByte and 0xC0u) != 0x40u.toUByte()) {
        throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "Invalid Short Header fixed bit or header form")
    }

    val pnLength = (firstByte.toInt() and 0x03) + 1
    if (offset + pnLength > data.size) throw QuicParsingException(TransportErrorCode.FRAME_ENCODING_ERROR, "Packet too short for Packet Number")
    
    var truncatedPacketNumberULong = 0uL
    for (i in 0 until pnLength) {
        truncatedPacketNumberULong = (truncatedPacketNumberULong shl 8) or data[offset++].toUByte().toULong()
    }
    
    val largestReceived = (connection.largestReceivedPacketNumber as PacketNumber).value
    val expectedPacketNumber = largestReceived + 1uL
    val packetNumberBits = pnLength * 8
    val window = 1uL shl packetNumberBits

    var reconstructedPnULong = (expectedPacketNumber and (window - 1uL).inv()) or truncatedPacketNumberULong
    if (reconstructedPnULong <= expectedPacketNumber - (window / 2uL)) {
         if (reconstructedPnULong > (ULong.MAX_VALUE - window)) error("Packet number overflow during reconstruction")
        reconstructedPnULong += window
    } else if (reconstructedPnULong > expectedPacketNumber + (window / 2uL) && reconstructedPnULong >= window) {
        if (reconstructedPnULong < window) error("Packet number underflow during reconstruction")
        reconstructedPnULong -= window
    }
    val reconstructedPacketNumber = PacketNumber(reconstructedPnULong)

    if (reconstructedPacketNumber.value > largestReceived) {
        connection.largestReceivedPacketNumber = reconstructedPacketNumber
    }

    val payload = data.sliceArray(offset until data.size)
    offsetRef.value = offset + payload.size

    return QuicPacket(
        packetType = null, // Short header
        connectionId = connection.connectionId as ConnectionID,
        packetNumber = reconstructedPacketNumber,
        payload = payload
    )
}
 
internal fun decodeVarint(data: ByteArray, offset: Int): Pair<ULong, Int> {
    if (offset >= data.size) error("Attempted to decode varint past end of data")
    val firstByte = data[offset].toUByte()
    val prefix = firstByte and 0xC0u
    val value: ULong
    val length: Int

    when (prefix) {
        0x00u.toUByte() -> {
            value = (firstByte and 0x3Fu).toULong()
            length = 1
        }
        0x40u.toUByte() -> {
            if (offset + 1 >= data.size) error("Packet too short for 2-byte varint")
            value = ((firstByte and 0x3Fu).toULong() shl 8) or data[offset + 1].toUByte().toULong()
            length = 2
        }
        0x80u.toUByte() -> {
            if (offset + 3 >= data.size) error("Packet too short for 4-byte varint")
            value = ((firstByte and 0x3Fu).toULong() shl 24) or
                    (data[offset + 1].toUByte().toULong() shl 16) or
                    (data[offset + 2].toUByte().toULong() shl 8) or
                    data[offset + 3].toUByte().toULong()
            length = 4
        }
        0xC0u.toUByte() -> {
            if (offset + 7 >= data.size) error("Packet too short for 8-byte varint")
            value = ((firstByte and 0x3Fu).toULong() shl 56) or
                    (data[offset + 1].toUByte().toULong() shl 48) or
                    (data[offset + 2].toUByte().toULong() shl 40) or
                    (data[offset + 3].toUByte().toULong() shl 32) or
                    (data[offset + 4].toUByte().toULong() shl 24) or
                    (data[offset + 5].toUByte().toULong() shl 16) or
                    (data[offset + 6].toUByte().toULong() shl 8) or
                    data[offset + 7].toUByte().toULong()
            length = 8
        }
        else -> error("Invalid varint prefix")
    }
    return Pair(value, length)
}
internal fun encodeVarint(value: ULong): ByteArray = when {
    value < 64uL -> byteArrayOf(value.toByte())
    value < 16384uL -> byteArrayOf(
        (0x40uL or (value shr 8)).toByte(),
        (value and 0xFFuL).toByte()
    )
    value < 1073741824uL -> byteArrayOf(
        (0x80uL or (value shr 24)).toByte(),
        ((value shr 16) and 0xFFuL).toByte(),
        ((value shr 8) and 0xFFuL).toByte(),
        (value and 0xFFuL).toByte()
    )
    else -> byteArrayOf(
        (0xC0uL or (value shr 56)).toByte(),
        ((value shr 48) and 0xFFuL).toByte(),
        ((value shr 40) and 0xFFuL).toByte(),
        ((value shr 32) and 0xFFuL).toByte(),
        ((value shr 24) and 0xFFuL).toByte(),
        ((value shr 16) and 0xFFuL).toByte(),
        ((value shr 8) and 0xFFuL).toByte(),
        (value and 0xFFuL).toByte()
    )
}

// Expect functions for packet protection
internal expect fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ProtectedPayload
