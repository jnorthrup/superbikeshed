package evolution

import kotlin.coroutines.CoroutineContext
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.min
// Import new types from QuicSpecTypes.kt, QuicTypes.kt
import evolution.* // Wildcard import for simplicity

// Custom Exception for QUIC Parsing Errors
class QuicParsingException(val errorCode: TransportErrorCode, message: String) : Exception(message)

<<<<<<< HEAD
// Imports for borg.trikeshed.* were present but not used by current logic. Removed for brevity.
// If they are needed by other parts of QuicCurl.kt not shown, they'd be kept.

// Expect declarations for TLS handshake functions (remain unchanged)
expect fun generateClientHello(initialDestConnId: ByteArray, serverName: String): ByteArray
expect fun processServerHello(serverHello: ByteArray): Boolean // This likely needs more context now too.

// QUIC Packet Types - (remains unchanged)
enum class QuicPacketType(val typeValue: UByte) {
    INITIAL(0x0u), HANDSHAKE(0x1u), ZERO_RTT(0x2u), RETRY(0x3u)
}

// Represents a QUIC packet.
// 'header' field added to store the unprotected header after unprotectPacket.
// 'payload' is the decrypted payload.
// Constructor now matches the dummy one in actual QuicCrypto.kt files.
data class QuicPacket(
    var header: ByteArray, // Made var to be set by unprotectPacket/parsing logic
    var payload: ByteArray, // Decrypted payload
    var packetType: QuicPacketType?, // Parsed from unprotected header
    var connectionId: ByteArray,    // Parsed from unprotected header (typically DCID)
    var packetNumber: ULong,        // Parsed and reconstructed from unprotected header
    // Removed protectionMask, protectedPayload from this common data class,
    // as they are intermediate states handled by protect/unprotect.
    // If needed for other purposes, they could be added back.
) {
    // Secondary constructor for convenience, matching common usage.
    // This constructor is used by 'unprotectPacket' to create the initial QuicPacket obj.
    constructor(
        packetType: QuicPacketType?,
        connectionId: ByteArray,
        packetNumber: ULong,
        payload: ByteArray
        // No header in this constructor, will be set by unprotectPacket or parsing logic
    ) : this(ByteArray(0), payload, packetType, connectionId, packetNumber)

    // getBytes is for sending, so it should use `protectPacket`.
    // For receiving, we have the raw bytes and then the QuicPacket object.
    // This method might be less relevant for a received packet object.
    // Let's remove it from here to avoid confusion with protectPacket's output.
    // fun getBytes(): ByteArray = ...


    // This was used by tests and `actual protectPacket` as AAD.
    // If QuicPacket always has its `header` field populated with the unprotected header,
    // this can just return `this.header`.
    internal fun headerPlaceholder(): ByteArray = this.header


=======
// Expect declaration for cryptographic operations
expect object Crypto {
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray // ikm could be ConnectionID.value
    // info could be HKDFLabel.value
    fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray
    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
    fun aesEcbEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray // Parameter order swapped
}

// Expect declarations for TLS handshake functions
// Refactored to use strong types for payloads
expect fun generateClientHelloBytes(initialDestConnId: ConnectionID, serverName: String, clientHello: ClientHelloPayload): ByteArray
expect fun parseServerHelloPayload(data: ByteArray): ServerHelloPayload? // data is the TLS record payload

// QuicPacketType enum removed, will use LongHeaderPacketType from QuicSpecTypes.kt

// Represents a parsed QUIC packet
// Updated to use strong types
data class QuicPacket(
    val packetType: LongHeaderPacketType?, // Using new type from QuicSpecTypes
    val connectionId: ConnectionID,    // Using new type
    val packetNumber: PacketNumber,    // Using new type
    val payload: ByteArray             // Plaintext payload
) {
    // equals and hashCode will rely on the value class implementations for ConnectionID and PacketNumber
    // and LongHeaderPacketType's default enum implementation.
    // Explicit implementation can be removed if default behavior is sufficient.
>>>>>>> origin/jules_wip_8844705664950451013
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as QuicPacket
<<<<<<< HEAD
        if (!header.contentEquals(other.header)) return false
        if (!payload.contentEquals(other.payload)) return false
=======
>>>>>>> origin/jules_wip_8844705664950451013
        if (packetType != other.packetType) return false
        if (connectionId != other.connectionId) return false
        if (packetNumber != other.packetNumber) return false
<<<<<<< HEAD
=======
        if (!payload.contentEquals(other.payload)) return false
>>>>>>> origin/jules_wip_8844705664950451013
        return true
    }

    override fun hashCode(): Int {
<<<<<<< HEAD
        var result = header.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (packetType?.hashCode() ?: 0)
        result = 31 * result + connectionId.contentHashCode()
=======
        var result = packetType?.hashCode() ?: 0
        result = 31 * result + connectionId.hashCode()
>>>>>>> origin/jules_wip_8844705664950451013
        result = 31 * result + packetNumber.hashCode()
        result = 31 * result + payload.contentHashCode() // Keep for payload
        return result
    }
}

<<<<<<< HEAD

=======
// Data class to hold derived initial keys
// Kept internal ByteArrays for keys as QuicSpecTypes doesn't have specific key wrappers yet.
>>>>>>> origin/jules_wip_8844705664950451013
data class QuicInitialKeys(
    val key: ByteArray, val iv: ByteArray, val hp: ByteArray
) {
<<<<<<< HEAD
    // Retaining these simplified accessors for now.
    // Actual key derivation for different packet number spaces is complex.
    fun headerProtectionKey(context: CoroutineContext): ByteArray = hp
    fun payloadProtectionKey(context: CoroutineContext): ByteArray = key
    fun payloadIv(context: CoroutineContext): ByteArray = iv
}

internal class MutableInt(var value: Int) // Remains unchanged
=======
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

// Helper class for passing int by reference (remains unchanged)
internal class MutableInt(var value: Int)

// Derives initial secrets as per RFC 9000, Section 5.2
// Updated to take ConnectionID and use HKDFLabel
internal fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys {
    val initialSalt = byteArrayOf(
        0x38, 0x76, 0x25, 0x7a, 0x4c, 0x8f, 0xac, 0x34,
        0x44, 0xce, 0x8f, 0x9a, 0x38, 0x60, 0xdf, 0x0b,
        0x60, 0x63, 0x0e, 0x84
    )

    // Use .value to get ByteArray from ConnectionID
    val initialSecret = Crypto.hkdfExtract(initialSalt, clientDstConnId.value)

    // Use HKDFLabel for info parameters
    val clientInitialSecret = Crypto.hkdfExpand(initialSecret, HKDFLabel("client in".encodeToByteArray()).value, 32)
    val serverInitialSecret = Crypto.hkdfExpand(initialSecret, HKDFLabel("server in".encodeToByteArray()).value, 32)

    val keyLen = 16 // AES-128 key length
    val ivLen = 12  // AES-128-GCM IV length
    val hpLen = 16  // Header Protection key length

    val clientKey = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic key".encodeToByteArray()).value, keyLen)
    val clientIv = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic iv".encodeToByteArray()).value, ivLen)
    val clientHp = Crypto.hkdfExpand(clientInitialSecret, HKDFLabel("quic hp".encodeToByteArray()).value, hpLen)
>>>>>>> origin/jules_wip_8844705664950451013

// MODIFIED: Added CoroutineContext, keys; made suspend; returns QuicPacket?
// This calls the global CCEK-based deriveInitialSecrets
internal suspend fun deriveInitialSecrets(context: CoroutineContext, clientDstConnId: ByteArray): QuicInitialKeys {
    val hkdfService = context[HkdfServiceKey]
        ?: throw IllegalStateException("HkdfService not found. Add platform-specific HkdfService to context.")
    val initialSalt = byteArrayOf(0x38, 0x76, 0x25, 0x7a, 0x4c, 0x8f, 0xac, 0x34, 0x44, 0xce, 0x8f, 0x9a, 0x38, 0x60, 0xdf, 0x0b, 0x60, 0x63, 0x0e, 0x84)
    val initialSecret = hkdfService.extract(initialSalt, clientDstConnId)
    val clientInitialSecret = hkdfService.expand(initialSecret, "client in".encodeToByteArray(), 32)
    val keyLen = 16; val ivLen = 12; val hpLen = 16
    val clientKey = hkdfService.expand(clientInitialSecret, "quic key".encodeToByteArray(), keyLen)
    val clientIv = hkdfService.expand(clientInitialSecret, "quic iv".encodeToByteArray(), ivLen)
    val clientHp = hkdfService.expand(clientInitialSecret, "quic hp".encodeToByteArray(), hpLen)
    return QuicInitialKeys(clientKey, clientIv, clientHp)
}

<<<<<<< HEAD

// MODIFIED: Main entry point for parsing. Now calls unprotectPacket first.
suspend fun parseQuicPacket(
    context: CoroutineContext,
    protectedData: ByteArray,
    connection: QuicConnection,
    keys: QuicInitialKeys // Keys for the current encryption level
): QuicPacket? {
    if (protectedData.isEmpty()) {
        // log error or return null
        return null
    }

    // 1. Unprotect the packet (header unmasking and payload decryption)
    val unprotectedPkt = unprotectPacket(context, protectedData, keys, connection)
        ?: return null // Unprotection failed (e.g., tag mismatch)

    // At this point, unprotectedPkt.header is the unprotected header,
    // and unprotectedPkt.payload is the decrypted payload.
    // The QuicPacket from unprotectPacket has placeholder type, CID, PN.
    // Now, parse these details from its unprotectedPkt.header.

    // 2. Determine if it's a Long Header or Short Header packet from unprotected header
    val firstByte = unprotectedPkt.header.firstOrNull()?.toUByte() ?: return null // Invalid header
<<<<<<< HEAD

=======
    
>>>>>>> origin/jules_wip_6906935130323988499
    return if ((firstByte and 0x80u) != 0u.toUByte()) { // Long Header
        parseLongHeaderPacketInternal(unprotectedPkt, connection)
    } else { // Short Header
        parseShortHeaderPacketInternal(unprotectedPkt, firstByte, connection)
    }
}

// MODIFIED: Now parses an *already unprotected* header from QuicPacket.
// Updates the QuicPacket with parsed info (type, DCID, PN).
internal fun parseLongHeaderPacketInternal(
    packet: QuicPacket, // Input: QuicPacket with unprotected header and decrypted payload
    connection: QuicConnection
): QuicPacket? {
    val unprotectedHeader = packet.header
    if (unprotectedHeader.isEmpty()) return null // Should not happen if unprotectPacket succeeded

    val offsetRef = MutableInt(0)
    var offset = offsetRef.value
    
    val headerByte = unprotectedHeader[offset++].toUByte()
    // Fixed bit check (already done by unprotectPacket implicitly if it parsed it)
    // For now, assume header is valid if unprotection passed.

    val parsedPacketType = when ((headerByte and 0x30u) shr 4) {
        0x0u -> QuicPacketType.INITIAL
        0x1u -> QuicPacketType.ZERO_RTT
        0x2u -> QuicPacketType.HANDSHAKE
        0x3u -> QuicPacketType.RETRY
        else -> return null // Invalid packet type
    }
    val pnLengthBits = (headerByte and 0x03u).toInt()
    val pnLength = pnLengthBits + 1

    offset += 4 // Skip Version (4 bytes) - a real parser would store this

    val destConnIdLen = unprotectedHeader[offset++].toUByte().toInt()
    if (destConnIdLen > 20 || offset + destConnIdLen > unprotectedHeader.size) return null
    val destConnId = unprotectedHeader.sliceArray(offset until offset + destConnIdLen)
    offset += destConnIdLen
    
    val srcConnIdLen = unprotectedHeader[offset++].toUByte().toInt()
    if (srcConnIdLen > 20 || offset + srcConnIdLen > unprotectedHeader.size) return null
    // val srcConnId = unprotectedHeader.sliceArray(offset until offset + srcConnIdLen) // Not stored in QuicPacket directly
    offset += srcConnIdLen
    
    if (parsedPacketType == QuicPacketType.INITIAL) {
        if (offset >= unprotectedHeader.size) return null
        val (tokenLenValue, tokenLenBytes) = decodeVarint(unprotectedHeader, offset) // from unprotected header
        offset += tokenLenBytes
        if (offset + tokenLenValue.toInt() > unprotectedHeader.size) return null
        offset += tokenLenValue.toInt() // Skip Token
    }
    
    // The "Length" field in a long header covers PN + Payload.
    // Since payload is already decrypted and separate, this field isn't used here to find payload.
    // We just need to parse the PN from the header.
    if (offset >= unprotectedHeader.size) return null // Check before decodeVarint for length
    val (payloadLenVarint, payloadLenBytes) = decodeVarint(unprotectedHeader, offset) // This is original protected length field
    offset += payloadLenBytes
    
    if (offset + pnLength > unprotectedHeader.size) return null
    var truncatedPacketNumber = 0uL
    for (i in 0 until pnLength) {
        truncatedPacketNumber = (truncatedPacketNumber shl 8) or unprotectedHeader[offset++].toUByte().toULong()
    }

    val reconstructedPacketNumber = reconstructPacketNumber(truncatedPacketNumber, pnLength, connection)

    // Update connection state (simplified)
    when (parsedPacketType) {
        QuicPacketType.INITIAL -> connection.state = QuicConnectionStateEnum.HANDSHAKE
        QuicPacketType.HANDSHAKE -> connection.state = QuicConnectionStateEnum.CONNECTED
        QuicPacketType.ZERO_RTT -> if (connection.state == QuicConnectionStateEnum.HANDSHAKE) connection.state = QuicConnectionStateEnum.CONNECTED
        else -> { /* Other types or state unchanged */ }
    }
    
    // Update and return the input packet
    packet.packetType = parsedPacketType
    packet.connectionId = destConnId // Typically, this is the DCID from the packet
    packet.packetNumber = reconstructedPacketNumber
    // packet.payload is already the decrypted payload from unprotectPacket
    // packet.header is already the unprotected header
    return packet
}

// MODIFIED: Now parses an *already unprotected* header from QuicPacket.
internal fun parseShortHeaderPacketInternal(
    packet: QuicPacket, // Input: QuicPacket with unprotected header and decrypted payload
    firstByte: UByte,   // First byte of the unprotected header
    connection: QuicConnection
): QuicPacket? {
    val unprotectedHeader = packet.header
    // For short headers, the unprotectedHeader might be very short (just flags + PN)
    // Offset starts from 0 as firstByte is from unprotectedHeader[0]
    var offset = 0

    // DCID is implicit (connection.connectionId for the established connection)
    // First byte (already passed as firstByte) contains PN length
    offset++ // Consume the first byte (already used for PNL derivation and type check)

    val pnLength = (firstByte.toInt() and 0x03) + 1 // PNL from firstByte
    if (offset + pnLength > unprotectedHeader.size) return null // Check against remaining header size
=======
// Main entry point for receiving and processing a QUIC packet
// `connection` parameter now uses refactored QuicConnection (with ConnectionID, PacketNumber)
fun parseQuicPacket(data: ByteArray, connection: QuicConnection): QuicPacket {
    if (data.isEmpty()) throw QuicParsingException(TransportErrorCode.FRAME_ENCODING_ERROR, "Received empty packet data.")

    try {
        val mutableOffset = MutableInt(0)
        val packet = if ((data[0].toUByte() and 0x80u) != 0u.toUByte()) { // Long Header
            parseLongHeaderPacket(data, mutableOffset, connection)
        } else { // Short Header
            parseShortHeaderPacket(data, mutableOffset, data[0].toUByte(), connection)
        }
        
        // Basic packet type validation only
        // The following check is redundant because parseLongHeaderPacket ensures packetType is non-null for long headers or throws.
        // if (packet.packetType == null && (data[0].toUByte() and 0x80u) != 0u.toUByte()) {
        //      // This condition means it's a long header (first bit is 1) but packetType is null
        //     throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "Long header packet parsed with null packet type.")
        // }
        
        when (packet.packetType) {
            LongHeaderPacketType.INITIAL -> connection.state = QuicConnectionStateEnum.HANDSHAKE
            LongHeaderPacketType.HANDSHAKE -> connection.state = QuicConnectionStateEnum.CONNECTED // Simplified
            LongHeaderPacketType.ZERO_RTT -> if (connection.state == QuicConnectionStateEnum.HANDSHAKE) connection.state = QuicConnectionStateEnum.CONNECTED
            else -> { /* Short header or other types, state might not change directly here */ }
        }
        
        return packet
    } catch (e: Exception) {
        // Consider mapping internal errors to TransportErrorCode
        // For now, rethrow as IllegalStateException or a custom QuicParsingException
        throw IllegalStateException("Failed to parse QUIC packet: ${e.message}", e)
    }
}

// Updated to use strong types and refactored QuicConnection
internal fun parseLongHeaderPacket(data: ByteArray, offsetRef: MutableInt, connection: QuicConnection): QuicPacket {
    if (data.isEmpty()) throw QuicParsingException(TransportErrorCode.FRAME_ENCODING_ERROR, "Packet data is empty for parseLongHeaderPacket.")
    var offset = offsetRef.value
    
    val headerByte = data[offset++].toUByte()
    // First bit (headerForm) is 1 for Long Header
    if ((headerByte and 0x80u) == 0u.toUByte()) throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "parseLongHeaderPacket called with short header data (first bit is 0).")
    if ((headerByte and 0x40u) == 0u.toUByte()) throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "Invalid fixed bit for Long Header (must be 1). Header byte: $headerByte")
    
    // Pass the full headerByte to fromByte, as it handles masking and shifting.
    val packetType = LongHeaderPacketType.fromByte(headerByte)
        ?: throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "Invalid Long Header packet type bits in header: $headerByte")

    val packetNumberLengthBits = (headerByte and 0x03u).toInt()
    val pnLength = packetNumberLengthBits + 1

    if (data.size < offset + 4) error("Packet too short for Version")
    val versionUInt = ((data[offset].toUByte().toUInt() shl 24) or
                      (data[offset + 1].toUByte().toUInt() shl 16) or
                      (data[offset + 2].toUByte().toUInt() shl 8) or
                      data[offset + 3].toUByte().toUInt())
    val version = QuicVersion(versionUInt) // Use QuicVersion type
    offset += 4
    
    if (version.value != QuicVersion.VERSION_1.value && version.value != QuicVersion.NEGOTIATION.value) {
        // error("Unsupported QUIC version: ${version.value}") // Stricter check
    }
    
    if (data.size < offset + 1) error("Packet too short for DCID Len")
    val destConnIdLen = data[offset++].toUByte().toInt()
    if (destConnIdLen > 20) error("Invalid dest conn ID length: $destConnIdLen")
    if (data.size < offset + destConnIdLen) error("Packet too short for DCID")
    val destConnIdBytes = data.sliceArray(offset until offset + destConnIdLen)
    val destConnId = ConnectionID(destConnIdBytes) // Use ConnectionID type
    offset += destConnIdLen
    
    if (data.size < offset + 1) error("Packet too short for SCID Len")
    val srcConnIdLen = data[offset++].toUByte().toInt()
    if (srcConnIdLen > 20) error("Invalid src conn ID length: $srcConnIdLen")
    if (data.size < offset + srcConnIdLen) error("Packet too short for SCID")
    val srcConnIdBytes = data.sliceArray(offset until offset + srcConnIdLen)
    // val srcConnId = ConnectionID(srcConnIdBytes) // SCID parsed, store if needed
    offset += srcConnIdLen

    if (packetType == LongHeaderPacketType.INITIAL) {
        if (offset >= data.size) error("Packet too short for Token Length (varint)")
        val (tokenLenValue, tokenLenBytes) = decodeVarint(data, offset)
        offset += tokenLenBytes
        if (data.size < offset + tokenLenValue.toInt()) error("Packet too short for Token")
        offset += tokenLenValue.toInt() // Token itself
    }
    
    if (offset >= data.size) error("Packet too short for Payload Length (varint)")
    val (payloadLenValue, payloadLenBytes) = decodeVarint(data, offset)
    offset += payloadLenBytes
    
    if (data.size < offset + pnLength) error("Packet too short for Packet Number ($pnLength bytes at offset $offset)")
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
        if (reconstructedPnULong > (ULong.MAX_VALUE - window)) error("Packet number overflow during reconstruction (+window)")
        reconstructedPnULong += window
    } else if (reconstructedPnULong > expectedPacketNumber + (window / 2uL) && reconstructedPnULong >= window) {
        if (reconstructedPnULong < window) error("Packet number underflow during reconstruction (-window)")
        reconstructedPnULong -= window
    }
    val reconstructedPacketNumber = PacketNumber(reconstructedPnULong) // Use PacketNumber type

    if (reconstructedPacketNumber.value > largestReceived) {
        connection.largestReceivedPacketNumber = reconstructedPacketNumber
    }

    val protectedPayloadAndTagLength = payloadLenValue.toInt() - pnLength
    if (protectedPayloadAndTagLength < 0) error("Invalid payload length calculation")
    if (data.size < offset + protectedPayloadAndTagLength) {
        error("Packet too short for protected payload and AEAD tag. Expected ${offset + protectedPayloadAndTagLength}, have ${data.size - offset}")
    }
    val payload = data.sliceArray(offset until offset + protectedPayloadAndTagLength)
    offset += protectedPayloadAndTagLength
    offsetRef.value = offset
    
    return QuicPacket(
        packetType = packetType,
        connectionId = destConnId,
        packetNumber = reconstructedPacketNumber,
        payload = payload // This is still protected payload here
    )
}

// Updated to use strong types and refactored QuicConnection
internal fun parseShortHeaderPacket(data: ByteArray, offsetRef: MutableInt, firstByte: UByte, connection: QuicConnection): QuicPacket {
    var offset = offsetRef.value
    
    // First byte (already read as firstByte) contains flags and PN length
    // Bit 7 (0x80) is 0 for Short Header
    // Bit 6 (0x40) is Fixed Bit (must be 1)
    if ((firstByte and 0xC0u) != 0x40u.toUByte()) { // Check Header Form (0) and Fixed Bit (1)
        throw QuicParsingException(TransportErrorCode.PROTOCOL_VIOLATION, "Invalid Short Header fixed bit or header form. First byte: $firstByte")
    }

    val pnLength = (firstByte.toInt() and 0x03) + 1
    if (offset + pnLength > data.size) throw QuicParsingException(TransportErrorCode.FRAME_ENCODING_ERROR, "Packet too short for Packet Number ($pnLength bytes at offset $offset)")
>>>>>>> origin/jules_wip_8844705664950451013
    
    var truncatedPacketNumberULong = 0uL
    for (i in 0 until pnLength) {
<<<<<<< HEAD
        truncatedPacketNumber = (truncatedPacketNumber shl 8) or unprotectedHeader[offset++].toUByte().toULong()
    }
    
    val reconstructedPacketNumber = reconstructPacketNumber(truncatedPacketNumber, pnLength, connection)

    // Update and return the input packet
    packet.packetType = null // Short header
    packet.connectionId = connection.connectionId // Use connection's established DCID
    packet.packetNumber = reconstructedPacketNumber
    return packet
}

// Helper for PN reconstruction, extracted for reuse
internal fun reconstructPacketNumber(truncatedPn: ULong, pnLengthBytes: Int, connection: QuicConnection): ULong {
    val expectedPacketNumber = connection.largestReceivedPacketNumber + 1uL
    val packetNumberBits = pnLengthBytes * 8
    val window = 1uL shl packetNumberBits
    var reconstructedPn = (expectedPacketNumber and (window.inv().inv())) or truncatedPn // Bitwise AND with NOT of (window -1)
    if (reconstructedPn <= expectedPacketNumber - (window / 2uL)) {
        if (reconstructedPn > ULong.MAX_VALUE - window) error("Packet number overflow (+window)")
        reconstructedPn += window
    } else if (reconstructedPn > expectedPacketNumber + (window / 2uL) && reconstructedPn >= window) {
         if (window > reconstructedPn && expectedPacketNumber >= window) error("Packet number underflow (-window)") // Condition for underflow needs care
        reconstructedPn -= window
    }
    if (reconstructedPn > connection.largestReceivedPacketNumber) {
        connection.largestReceivedPacketNumber = reconstructedPn
    }
    return reconstructedPn
}
 
// decodeVarint and encodeVarint remain unchanged from previous version of QuicCurl.kt
internal fun decodeVarint(data: ByteArray, offset: Int): Pair<ULong, Int> {
    if (offset >= data.size) error("Attempted to decode varint past end of data")
    val firstByte = data[offset].toUByte(); val prefix = firstByte and 0xC0u
    val value: ULong; val length: Int
    when (prefix) {
        0x00u.toUByte() -> { value = (firstByte and 0x3Fu).toULong(); length = 1 }
        0x40u.toUByte() -> { if (offset + 1 >= data.size) error("Packet too short for 2-byte varint"); value = ((firstByte and 0x3Fu).toULong() shl 8) or data[offset + 1].toUByte().toULong(); length = 2 }
        0x80u.toUByte() -> { if (offset + 3 >= data.size) error("Packet too short for 4-byte varint"); value = ((firstByte and 0x3Fu).toULong() shl 24) or (data[offset + 1].toUByte().toULong() shl 16) or (data[offset + 2].toUByte().toULong() shl 8) or data[offset + 3].toUByte().toULong(); length = 4 }
        0xC0u.toUByte() -> { if (offset + 7 >= data.size) error("Packet too short for 8-byte varint"); value = ((firstByte and 0x3Fu).toULong() shl 56) or (data[offset + 1].toUByte().toULong() shl 48) or (data[offset + 2].toUByte().toULong() shl 40) or (data[offset + 3].toUByte().toULong() shl 32) or (data[offset + 4].toUByte().toULong() shl 24) or (data[offset + 5].toUByte().toULong() shl 16) or (data[offset + 6].toUByte().toULong() shl 8) or data[offset + 7].toUByte().toULong(); length = 8 }
=======
        truncatedPacketNumberULong = (truncatedPacketNumberULong shl 8) or data[offset++].toUByte().toULong()
    }
    
    val largestReceived = (connection.largestReceivedPacketNumber as PacketNumber).value
    val expectedPacketNumber = largestReceived + 1uL
    val packetNumberBits = pnLength * 8
    val window = 1uL shl packetNumberBits

    var reconstructedPnULong = (expectedPacketNumber and (window - 1uL).inv()) or truncatedPacketNumberULong
    if (reconstructedPnULong <= expectedPacketNumber - (window / 2uL)) {
         if (reconstructedPnULong > (ULong.MAX_VALUE - window)) error("Packet number overflow during reconstruction (+window)")
        reconstructedPnULong += window
    } else if (reconstructedPnULong > expectedPacketNumber + (window / 2uL) && reconstructedPnULong >= window) {
        if (reconstructedPnULong < window) error("Packet number underflow during reconstruction (-window)")
        reconstructedPnULong -= window
    }
    val reconstructedPacketNumber = PacketNumber(reconstructedPnULong) // Use PacketNumber type

    if (reconstructedPacketNumber.value > largestReceived) {
        connection.largestReceivedPacketNumber = reconstructedPacketNumber
    }

    val payload = data.sliceArray(offset until data.size)
    offsetRef.value = offset + payload.size

    return QuicPacket(
        packetType = null, // Short header
        connectionId = connection.connectionId as ConnectionID, // Use established DCID
        packetNumber = reconstructedPacketNumber,
        payload = payload // This is still protected payload here
    )
}
 
// decodeVarint and encodeVarint remain unchanged as they deal with generic ULong values
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
>>>>>>> origin/jules_wip_8844705664950451013
        else -> error("Invalid varint prefix")
    }
    return Pair(value, length)
}
<<<<<<< HEAD
internal fun encodeVarint(value: ULong): ByteArray {
    return when { value < 64uL -> byteArrayOf(value.toByte()); value < 16384uL -> byteArrayOf((0x40u or (value shr 8).toUByte()).toByte(), (value and 0xFFu).toByte()); value < 1073741824uL -> byteArrayOf((0x80u or (value shr 24).toUByte()).toByte(), ((value shr 16) and 0xFFu).toByte(), ((value shr 8) and 0xFFu).toByte(), (value and 0xFFu).toByte()); else -> byteArrayOf((0xC0u or (value shr 56).toUByte()).toByte(), ((value shr 48) and 0xFFu).toByte(), ((value shr 40) and 0xFFu).toByte(), ((value shr 32) and 0xFFu).toByte(), ((value shr 24) and 0xFFu).toByte(), ((value shr 16) and 0xFFu).toByte(), ((value shr 8) and 0xFFu).toByte(), (value and 0xFFu).toByte()) }
}

// expect fun protectPacket signature already aligned with QuicCrypto.kt (suspend, takes context)
// No change needed here for its signature.
internal expect suspend fun protectPacket(
    context: CoroutineContext, packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection
): ByteArray

// QuicConnection class (local dummy from previous step)
// Needs to be defined or imported. Assuming it's the one from QuicConnectionState.kt
// For this subtask, assuming QuicConnection class definition is accessible.
// (It was added locally in a previous version of this subtask, but removed if not needed directly by this file's changes)
// Let's ensure it's here for self-containment of this file block.
class QuicConnection(
    val connectionId: ByteArray = generateConnectionId(), // Used as default by dummy QuicPacket
    var state: QuicConnectionStateEnum = QuicConnectionStateEnum.INITIAL,
    var packetNumber: ULong = 0uL, // This is for sending; for receiving, largestReceivedPacketNumber is key
    var largestReceivedPacketNumber: ULong = 0uL
) {
    private companion object { // Made companion private
        private val random = kotlin.random.Random.Default
        fun generateConnectionId(): ByteArray {
            val cid = ByteArray(8); random.nextBytes(cid); return cid
        }
    }
     override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuicConnection) return false
        if (!connectionId.contentEquals(other.connectionId)) return false
        if (state != other.state) return false
        if (packetNumber != other.packetNumber) return false
        if (largestReceivedPacketNumber != other.largestReceivedPacketNumber) return false
        return true
     }
    override fun hashCode(): Int {
        var result = connectionId.contentHashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + packetNumber.hashCode()
        result = 31 * result + largestReceivedPacketNumber.hashCode()
        return result
    }
}
enum class QuicConnectionStateEnum { INITIAL, HANDSHAKE, CONNECTED, CLOSING, CLOSED } // Simplified

// Added global unprotectPacket expect fun, consistent with QuicCrypto.kt
internal expect suspend fun unprotectPacket(
    context: CoroutineContext,
    protectedPacketBytes: ByteArray,
    keys: QuicInitialKeys,
    connection: QuicConnection
): QuicPacket?
=======

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

// Changed return type to ProtectedPayload
internal expect fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ProtectedPayload

// Helper function to encode packet number to a fixed length byte array (max 4 bytes for QUIC)
// Left-pads with zeros if PN is smaller than pnLength.
internal fun encodePacketNumber(pn: ULong, pnLength: Int): ByteArray {
    require(pnLength in 1..4) { "Packet number length must be between 1 and 4 bytes." }
    val bytes = ByteArray(pnLength)
    for (i in 0 until pnLength) {
        bytes[pnLength - 1 - i] = ((pn shr (i * 8)) and 0xFFuL).toByte()
    }
    return bytes
}

// This is the common logic for packet protection including header protection.
// Actual platform implementations of `protectPacket` will call this and wrap the result in ProtectedPayload.
internal fun commonProtectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ByteArray {
    // 1. Serialize Header (Simplified - focusing on parts to be protected)
    //    A more complete serialization would handle all header fields, versions, CIDs, etc.

    val isLongHeader = packet.packetType != null
    var firstHeaderByte: Byte
    val pnBytes: ByteArray
    var currentPnLength: Int // Actual bytes used for packet number on the wire (1 to 4)

    val pnValue = packet.packetNumber.value

    // Determine Packet Number Length (pn_length from RFC 9000) - simplified
    // A real implementation determines this based on unacknowledged packet count or other strategies.
    // Here, we'll choose a length based on the magnitude of the packet number for simplicity.
    currentPnLength = when {
        pnValue < 256uL -> 1 // Fits in 1 byte (if first 2 bits of PN are 00)
        pnValue < 65536uL -> 2 // Fits in 2 bytes (if first 2 bits of PN are 01)
        pnValue < 16777216uL -> 3 // Fits in 3 bytes (if first 2 bits of PN are 10) - QUIC uses up to 4
        else -> 4 // Fits in 4 bytes (if first 2 bits of PN are 11)
    }
    // Ensure it's at least 1 for short headers if PN is 0.
    if (!isLongHeader && currentPnLength == 0 && pnValue == 0uL) currentPnLength = 1


    pnBytes = encodePacketNumber(pnValue, currentPnLength)

    // Construct first byte (simplified)
    if (isLongHeader) {
        // Example: Initial Packet (0xC0 base) + PN Length bits
        // Bits: 1100 LLLL (L = PN length-1)
        firstHeaderByte = (0xC0 or ((currentPnLength -1) & 0x03)).toByte()
        // This should also include packet type bits, e.g., Initial (00), Handshake (02) in bits 4,5
        // packet.packetType.value is UByte. Example for Initial (type 00 in bits 4-5):
        firstHeaderByte = firstHeaderByte or ((packet.packetType!!.value.toInt() shl 4).toByte()) // Assuming packetType is not null for long header
    } else {
        // Short Header: 0100 LLLL (L = PN length-1), plus other bits (spin, key phase)
        firstHeaderByte = (0x40 or ((currentPnLength -1) & 0x03)).toByte()
        // TODO: Incorporate Spin Bit, Key Phase Bit, etc. for Short Headers
    }

    // Simplified header: firstByte + DCID + (SCID if Long Header) + PN + (other fields like version, token, length for Long)
    // For AAD, we need a more complete header. For this example, let's assume a conceptual header.
    // Let's create a mutable header that we can modify with header protection.
    // A more robust implementation would build the header properly.

    // Conceptual: Build the header up to the packet number.
    // This is highly simplified. Real header construction is complex.
    val dcidBytes = packet.connectionId.value // Assuming this is the DCID
    var serializedHeaderList = mutableListOf<Byte>()
    serializedHeaderList.add(firstHeaderByte)

    if (isLongHeader) {
        // Simplified: Assume fixed length DCID/SCID for this example part, or use actual lengths
        // Version (4 bytes) - Placeholder
        serializedHeaderList.addAll(QuicVersion.VERSION_1.value.toUInt().toBigInteger().toByteArray().takeLast(4)) // Simplistic version
        serializedHeaderList.add(dcidBytes.size.toByte()) // DCID Len
        serializedHeaderList.addAll(dcidBytes.toList())
        serializedHeaderList.add(0.toByte()) // SCID Len (placeholder)
        // Placeholder for Token Length + Token
        // Placeholder for Payload Length (VarInt) - this depends on encrypted payload + tag size
    } else { // Short Header
         serializedHeaderList.addAll(dcidBytes.toList())
    }

    val packetNumberOffset = serializedHeaderList.size // Packet number starts after this
    serializedHeaderList.addAll(pnBytes.toList())

    // Placeholder for payload length in long headers (would be added after PN)
    // For now, AAD will be header up to and including (unprotected) PN
    val aad = serializedHeaderList.toByteArray()


    // 2. Construct Nonce for AEAD
    val nonce = ByteArray(keys.iv.size)
    val pnForNonce = packet.packetNumber.value // ULong
    for (i in nonce.indices) {
        val shift = (nonce.size - 1 - i) * 8
        val pnByte = if (shift < 64) ((pnForNonce shr shift) and 0xFFuL).toByte() else 0
        nonce[i] = (keys.iv.getOrElse(i) { 0 } xor pnByte)
    }

    // 3. AEAD Encryption of Payload
    val encryptedPayloadAndTag = Crypto.aesGcmEncrypt(keys.key, nonce, packet.payload, aad)

    // 4. Header Protection
    // Sample from the ciphertext. RFC 9001 Section 5.4.1:
    // "usually the 16 bytes starting at an offset of sample_offset bytes from the start of the packet number field"
    // sample_offset is max(4, pn_length).
    // For simplicity here, let's take first 16 bytes of encryptedPayloadAndTag as sample,
    // as the exact PN offset in the *final* packet isn't trivial without full serialization.
    // A more accurate sample would be from the final packet's protected payload part.
    val sampleOffsetInCiphertext = minOf(encryptedPayloadAndTag.size, currentPnLength + 4) // Conceptual offset
    val sampleEnd = minOf(encryptedPayloadAndTag.size, sampleOffsetInCiphertext + 16)
    val sample = if (sampleOffsetInCiphertext < sampleEnd) {
        encryptedPayloadAndTag.sliceArray(sampleOffsetInCiphertext until sampleEnd)
    } else {
        // Fallback if ciphertext is too short for preferred sample, pad or use what's available
        encryptedPayloadAndTag.take(16).toByteArray().let { if(it.size < 16) it + ByteArray(16-it.size) else it }
    }

    if (sample.size < 16 && isLongHeader) { // AES block size is 16. Pad if necessary for ECB.
         // This padding is a simplification. Real HP might not need padding if cipher handles partial blocks.
         // However, our Crypto.aesEcbEncrypt placeholder might assume full blocks.
    }


    val hpMaskBytes = Crypto.aesEcbEncrypt(sample, keys.hp) // plaintext, key
    val mask = hpMaskBytes.take(5).toByteArray() // Mask is typically 5 bytes

    // Apply mask to header (first byte and packet number)
    // The 'serializedHeaderList' currently holds the unprotected header.

    // Protect first byte
    serializedHeaderList[0] = (serializedHeaderList[0].toInt() xor mask[0].toInt()).toByte()

    // Protect Packet Number bytes
    for (i in 0 until currentPnLength) {
        if (packetNumberOffset + i < serializedHeaderList.size && (1 + i) < mask.size) {
            serializedHeaderList[packetNumberOffset + i] =
                (serializedHeaderList[packetNumberOffset + i].toInt() xor mask[1 + i].toInt()).toByte()
        }
    }

    val protectedHeader = serializedHeaderList.toByteArray()

    // 5. Combine protected header and encrypted payload
    return protectedHeader + encryptedPayloadAndTag
}
>>>>>>> origin/jules_wip_8844705664950451013
