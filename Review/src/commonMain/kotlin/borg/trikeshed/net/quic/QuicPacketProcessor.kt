package borg.trikeshed.net.quic

import borg.trikeshed.net.crypto.CryptoServiceKey
// QuicPacket, QuicPacketHeader, LongHeader, ShortHeader are in .QuicPacket
// QuicFrame is in .QuicFrames
// PacketNumber, ConnectionId are in .QuicTypes
import borg.trikeshed.net.quic.crypto.QuicSecrets
// QuicConnectionManager and EncryptionLevel are in .QuicConnectionManager
import borg.trikeshed.net.quic.QuicPacketType.INITIAL // For checking header type
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

/**
 * An object responsible for QUIC packet serialization, protection (encryption and header protection),
 * deserialization, and unprotection (decryption and header protection removal).
 *
 * This object contains stateless utility functions that operate on packet data using
 * provided cryptographic services and connection state.
 */
object QuicPacketProcessor {

    private const val AEAD_TAG_LENGTH = 16 // Common for AES-128-GCM

    /**
     * Serializes a [QuicPacket] into a ByteArray, encrypts its payload, and applies header protection.
     *
     * @param context The coroutine context, used to retrieve the [CryptoService].
     * @param packet The [QuicPacket] to serialize and protect. The `packet.header.rawPacketNumberBytes`
     *               should be the correctly truncated packet number bytes to be written to the wire.
     *               The `packet.header.packetNumber` is the full, reconstructed packet number used for nonce generation.
     * @param connManager The [QuicConnectionManager] for the current connection, used to fetch secrets.
     * @param encryptionLevel The [EncryptionLevel] at which to protect the packet.
     * @return A [ByteArray] containing the protected QUIC packet, ready for transmission, or `null` on failure.
     */
    suspend fun serializeAndProtectPacket(
        context: CoroutineContext,
        packet: QuicPacket,
        connManager: QuicConnectionManager,
        encryptionLevel: EncryptionLevel
    ): ByteArray? {
        val cryptoService = context[CryptoServiceKey] ?: return null // CryptoService not found
        val secrets = connManager.getCurrentSecretsForSend(encryptionLevel) ?: return null // Secrets not available for this level

        // --- 1. Serialize Header and Payload (Preliminary) ---
        // This is a complex part involving writing fields according to QUIC wire format.
        // For LongHeader: first_byte | version (4) | DCID Len (1) | DCID (*) | SCID Len (1) | SCID (*) | Token Len (*) | Token (*) | Length (varint) | Packet Number (rawPacketNumberBytes)
        // For ShortHeader: first_byte | DCID (*) | Packet Number (rawPacketNumberBytes)
        // The `packet.unprotectedPayload` is the QUIC frames serialized.
        // Let's assume `serializedHeaderBytes` and `serializedPayloadBytes` (which is `packet.unprotectedPayload`) are prepared.
        // The `packet.header.rawPacketNumberBytes` are the bytes used for the PN field on the wire.
        // The `packet.header.packetNumber` is the full PN for nonce generation.

        // Placeholder for actual serialization logic:
        val preliminaryPacketBytes: ByteArray // = serializeHeader(packet.header) + packet.unprotectedPayload
        val headerLength: Int // = length of serializedHeaderBytes
        // Placeholder for actual serialization logic - THIS SECTION SHOULD BE REPLACED
        // val preliminaryPacketBytes: ByteArray
        // val headerLength: Int
        // preliminaryPacketBytes = byteArrayOf() // stub
        // headerLength = 0 // stub
        // if (true) return null // Force failure until stub is removed
        // END OF SECTION TO BE REPLACED

        // --- Start of new logic from previous turn for serializeAndProtectPacket ---
        val cryptoService = context[CryptoServiceKey]
            ?: throw IllegalStateException("CryptoService not found in CoroutineContext")
        val secrets = connManager.getCurrentSecretsForSend(encryptionLevel)
            ?: return null // Secrets not available

        val isLongHeader = packet.header is LongHeader

        // --- 1. Serialize Header (excluding Packet Number itself initially) ---
        val serializedHeaderBytesWithoutPN: ByteArray
        val actualPayloadLengthForHeaderField: ULong // For LongHeader's length field

        when (val hdr = packet.header) {
            is LongHeader -> {
                actualPayloadLengthForHeaderField = hdr.rawPacketNumberBytes.size.toULong() +
                        packet.unprotectedPayload.size.toULong() +
                        AEAD_TAG_LENGTH.toULong()
                serializedHeaderBytesWithoutPN = serializeLongHeader(hdr, actualPayloadLengthForHeaderField)
            }
            is ShortHeader -> {
                val dcid = connManager.connection.serverId
                    ?: connManager.connection.clientId
                serializedHeaderBytesWithoutPN = serializeShortHeader(hdr, dcid)
            }
        }

        val unprotectedHeaderBytes = serializedHeaderBytesWithoutPN + packet.header.rawPacketNumberBytes
        val associatedData = unprotectedHeaderBytes

        val encryptedPayloadWithTag = cryptoService.aeadEncrypt(
            secrets,
            packet.unprotectedPayload,
            associatedData,
            packet.header.packetNumber
        ) ?: return null

        val packetBytesBeforeHp = unprotectedHeaderBytes + encryptedPayloadWithTag

        val pnOffset = serializedHeaderBytesWithoutPN.size
        val sampleOffset = pnOffset + 4

        val headerSample: ByteArray
        if (sampleOffset > packetBytesBeforeHp.size) { // Not enough data to even start sampling for 16 bytes
             // This situation should ideally be avoided by ensuring packets are large enough for HP sample,
             // or HP algorithm should specify handling of short samples (e.g. padding).
             // For now, if we can't get any sample bytes after pnOffset + 4, this is an issue.
             // If sampleOffset is valid, but sampleOffset + 16 is not, take what's available and pad.
            if (sampleOffset <= packetBytesBeforeHp.size) {
                 val partialSample = packetBytesBeforeHp.sliceArray(sampleOffset until packetBytesBeforeHp.size)
                 headerSample = partialSample + ByteArray(16 - partialSample.size) // Pad with zeros
            } else {
                return null // Cannot get any sample bytes.
            }
        } else {
            val sampleEndIndex = kotlin.math.min(sampleOffset + 16, packetBytesBeforeHp.size)
            headerSample = packetBytesBeforeHp.sliceArray(sampleOffset until sampleEndIndex)
            if (headerSample.size < 16) { // Should only happen if packetBytesBeforeHp.size < sampleOffset + 16
                headerSample = headerSample + ByteArray(16 - headerSample.size) // Pad with zeros
            }
        }

        val hpKey = secrets.headerProtectionKey
        val hpMask = cryptoService.generateHeaderProtectionMask(hpKey, headerSample)

        if (hpMask.size < 5) return null

        val protectedPacketBytes = packetBytesBeforeHp.copyOf()
        protectedPacketBytes[0] = protectedPacketBytes[0] xor hpMask[0]

        val pnLengthToProtect = kotlin.math.min(packet.header.rawPacketNumberBytes.size, 4)
        for (i in 0 until pnLengthToProtect) {
            protectedPacketBytes[pnOffset + i] = protectedPacketBytes[pnOffset + i] xor hpMask[1 + i]
        }
        return protectedPacketBytes
    }

    /**
     * Deserializes a raw byte array into a [QuicPacket], unprotects its header, and decrypts its payload.
     *
     * @param context The coroutine context, used to retrieve the [CryptoService].
     * @param rawPacketBytes The raw bytes received from the network.
     * @param connManager The [QuicConnectionManager] for the current connection.
     * @param expectedPacketType The [QuicPacketType] expected for this packet, used for Long Headers to parse correctly.
     * @param expectedDestCid The destination [ConnectionId] this endpoint expects to see on the packet.
     * @return A [QuicPacket] if deserialization, unprotection, and decryption are successful, or `null` otherwise.
     */
    suspend fun deserializeAndUnprotectPacket(
        context: CoroutineContext,
        rawPacketBytes: ByteArray,
        connManager: QuicConnectionManager,
        expectedPacketType: QuicPacketType,
        expectedDestCid: ConnectionId
    ): QuicPacket? {
        if (rawPacketBytes.isEmpty()) return null
        val cryptoService = context[CryptoServiceKey]
            ?: throw IllegalStateException("CryptoService not found in CoroutineContext")

        val firstProtectedByte = rawPacketBytes[0]
        val isLongHeader = (firstProtectedByte.toUByte() and 0x80u) != 0u.toUByte()

        var pnOffset: Int
        var parsedActualPnLengthAfterUnmask: Int // To be determined after first byte unmasking
        val sample: ByteArray

        if (isLongHeader) {
            var currentOffset = 1 // Start after the first byte (which is protected)
            // Version (4 bytes) - assumed not protected by HP mask itself, read for structure
            if (currentOffset + 4 > rawPacketBytes.size) return null
            currentOffset += 4 // Skip Version bytes

            // DCID Len & DCID
            if (currentOffset >= rawPacketBytes.size) return null
            val dcidLenVal = rawPacketBytes[currentOffset].toUByte().toInt()
            currentOffset += 1
            if (currentOffset + dcidLenVal > rawPacketBytes.size) return null
            currentOffset += dcidLenVal // Skip DCID bytes

            // SCID Len & SCID
            if (currentOffset >= rawPacketBytes.size) return null
            val scidLenVal = rawPacketBytes[currentOffset].toUByte().toInt()
            currentOffset += 1
            if (currentOffset + scidLenVal > rawPacketBytes.size) return null
            currentOffset += scidLenVal // Skip SCID bytes

            try {
                if (expectedPacketType == QuicPacketType.INITIAL) {
                    val (tokenLen, tokenLenBytesRead) = decodeVarint(rawPacketBytes, currentOffset)
                    currentOffset += tokenLenBytesRead
                    if (currentOffset + tokenLen.toInt() > rawPacketBytes.size) return null
                    currentOffset += tokenLen.toInt() // Skip Token itself
                }
                // All Long Headers (except Retry) have a Length field.
                val (_, lengthFieldBytesRead) = decodeVarint(rawPacketBytes, currentOffset)
                currentOffset += lengthFieldBytesRead
            } catch (e: IllegalArgumentException) { return null }
            pnOffset = currentOffset

            val sampleSourceOffset = pnOffset + 4
            if (sampleSourceOffset > rawPacketBytes.size && pnOffset <= rawPacketBytes.size) { // If pnOffset is valid but sample starts beyond packet end
                 // This case implies the packet is too short to have a PN and a sample after it.
                 // It might be possible if PN is present but there are no bytes 4 positions after it.
                 // Sample from what's available after pnOffset, then pad.
                 val availableForSample = rawPacketBytes.sliceArray(pnOffset until rawPacketBytes.size)
                 sample = if (availableForSample.size >= 16) availableForSample.copyOfRange(0,16) // Should not happen if sampleSourceOffset > size
                          else availableForSample + ByteArray(16-availableForSample.size)

            } else if (sampleSourceOffset > rawPacketBytes.size) { // Cannot even start sampling
                return null
            } else {
                val sampleEnd = kotlin.math.min(rawPacketBytes.size, sampleSourceOffset + 16)
                val rawSample = rawPacketBytes.sliceArray(sampleSourceOffset until sampleEnd)
                sample = if (rawSample.size < 16) rawSample + ByteArray(16 - rawSample.size) else rawSample
            }
        } else { // Short Header
            parsedActualPnLengthAfterUnmask = (firstProtectedByte.toUByte() and 0x03u).toInt() + 1
            pnOffset = 1 + expectedDestCid.size
            if (pnOffset + parsedActualPnLengthAfterUnmask > rawPacketBytes.size) return null

            val sampleSourceOffset = pnOffset + 4
            if (sampleSourceOffset > rawPacketBytes.size && pnOffset <= rawPacketBytes.size) {
                 val availableForSample = rawPacketBytes.sliceArray(pnOffset until rawPacketBytes.size)
                 sample = if (availableForSample.size >= 16) availableForSample.copyOfRange(0,16)
                          else availableForSample + ByteArray(16-availableForSample.size)
            } else if (sampleSourceOffset > rawPacketBytes.size) {
                 return null
            } else {
                val sampleEnd = kotlin.math.min(rawPacketBytes.size, sampleSourceOffset + 16)
                val rawSample = rawPacketBytes.sliceArray(sampleSourceOffset until sampleEnd)
                sample = if (rawSample.size < 16) rawSample + ByteArray(16 - rawSample.size) else rawSample
            }
        }

        val encryptionLevel = connManager.getReceiveEncryptionLevel() // TODO: make this more robust based on expectedPacketType
        val secrets = connManager.getCurrentSecretsForReceive(encryptionLevel) ?: return null
        val hpKey = secrets.headerProtectionKey
        val hpMask = cryptoService.generateHeaderProtectionMask(hpKey, sample)
        if (hpMask.size < 5) return null

        val unprotectedRawBytes = rawPacketBytes.copyOf()
        val originalFirstByte = (firstProtectedByte xor hpMask[0])
        unprotectedRawBytes[0] = originalFirstByte

        if (isLongHeader) {
            val parsedPacketTypeBits = (originalFirstByte.toUByte().toInt() shr 4) and 0x03
            val parsedPacketType = QuicPacketType.entries.find { it.typeValue.toInt() == parsedPacketTypeBits }
            if (parsedPacketType != expectedPacketType) return null
            parsedActualPnLengthAfterUnmask = (originalFirstByte.toUByte() and 0x03u).toInt() + 1
            if (((originalFirstByte.toUByte().toInt() shr 2) and 0x03) != 0) return null // Reserved bits check
        } else { // Short Header
             if ((originalFirstByte.toUByte().toInt() and 0x0C) != 0) return null // Reserved bits check
        }

        if (pnOffset + parsedActualPnLengthAfterUnmask > unprotectedRawBytes.size) return null
        for (i in 0 until parsedActualPnLengthAfterUnmask) {
            if (i < 4) unprotectedRawBytes[pnOffset + i] = unprotectedRawBytes[pnOffset + i] xor hpMask[1 + i]
            else break
        }

        val parsedHeaderResult: Pair<QuicPacketHeader, Int>? = if (isLongHeader) {
            parseLongHeader(unprotectedRawBytes, originalFirstByte, 1)
        } else {
            parseShortHeader(unprotectedRawBytes, originalFirstByte, 1, expectedDestCid)
        }

        if (parsedHeaderResult == null) return null
        val truncatedPn = parsedHeader.rawPacketNumberBytes.toULong() // Uses helper defined below
        val reconstructedPN = reconstructPacketNumber(
            truncatedPn,
            connManager.connection.largestReceivedPacketNumberFromPeer,
            parsedHeader.rawPacketNumberBytes.size
        )
        // Defer updating connManager.largestReceivedPacketNumberFromPeer until after successful decryption.

        // --- 6. Decrypt Payload ---
        if (headerLengthIncludingPn > unprotectedRawBytes.size) return null // Invalid header length reported
        val ciphertextWithTag = unprotectedRawBytes.sliceArray(headerLengthIncludingPn until unprotectedRawBytes.size)
        val associatedDataForAEAD = unprotectedRawBytes.sliceArray(0 until headerLengthIncludingPn) // Full unprotected header as AAD

        val plaintextPayload = cryptoService.aeadDecrypt(secrets, ciphertextWithTag, associatedDataForAEAD, reconstructedPN)
            ?: {
                // println("AEAD Decryption failed. PN: $reconstructedPN, Level: $encryptionLevel")
                return null // Decryption failed
            }()


        // If decryption is successful, now update the largest received PN
        connManager.processReceivedPacketNumberFromPeer(reconstructedPN)

        // --- 7. Construct QuicPacket ---
        // Update the parsed header with the full reconstructed packet number.
        val finalHeader = when (parsedHeader) {
            is LongHeader -> parsedHeader.copy(packetNumber = reconstructedPN)
            is ShortHeader -> parsedHeader.copy(packetNumber = reconstructedPN)
        }

        // TODO: Implement Frame Parsing from plaintextPayload
        // For now, QuicPacket stores the raw payload.
        return QuicPacket(finalHeader, plaintextPayload)
    }

    // Helper to convert byte array packet number to ULong
    private fun ByteArray.toULong(): ULong {
        if (this.isEmpty() || this.size > 8) throw IllegalArgumentException("Invalid byte array size for ULong conversion")
        var result = 0uL
        for (byte in this) {
            result = (result shl 8) or byte.toUByte().toULong()
        }
        return result
    }

    // Removed estimatePnOffsetForLongHeaderPreHpRemoval as its logic is integrated into deserializeAndProtectPacket

    /**
     * Reconstructs the full packet number from its truncated version and the context of previously received packet numbers.
     * Based on RFC 9000, Appendix A: Sample Packet Number Decoding Algorithm.
     *
     * @param truncatedPn The truncated packet number received in the packet header (up to 4 bytes).
     * @param largestReceivedOrAckedPn The largest full packet number acknowledged or received so far on this connection.
     * @param pnLengthBytes The length of the packet number field in bytes (1 to 4).
     * @return The reconstructed full [PacketNumber].
     */
    private fun reconstructPacketNumber(
        truncatedPn: ULong,
        largestReceivedOrAckedPn: ULong,
        pnLengthBytes: Int
    ): PacketNumber {
        val pnNBits = pnLengthBytes * 8
        val expectedPn = largestReceivedOrAckedPn + 1uL
        val pnWindow = 1uL shl pnNBits
        val pnHalfWindow = pnWindow / 2uL
        val pnMask = pnWindow - 1uL

        // Candidate PN calculation (see RFC 9000 Appendix A)
        val candidatePn = (expectedPn and pnMask.inv()) or truncatedPn

        return if (candidatePn <= expectedPn - pnHalfWindow && candidatePn < (1uL shl 62) - pnWindow) {
            candidatePn + pnWindow
        } else if (candidatePn > expectedPn + pnHalfWindow && expectedPn >= pnWindow) {
            candidatePn - pnWindow
        } else {
            candidatePn
        }
    }

    // TODO: Helper functions for:
    // - findPacketNumberOffset(headerBytes: ByteArray, header: QuicPacketHeader): Int
    // - parsePnLengthFromFirstByte(firstByte: Byte, isLongHeader: Boolean): Int
    // - decodePacketNumber(pnBytes: ByteArray): ULong
    // - manuallySerializeHeader(header: QuicPacketHeader): ByteArray
    // - parseHeader(bytes: ByteArray, isLongHeader: Boolean, unprotectedFirstByte: Byte, pnBytes: ByteArray): QuicPacketHeader?
    // - calculateHeaderLength(header: QuicPacketHeader): Int
    // - updateHeaderWithReconstructedPN(header: QuicPacketHeader, reconstructedPn: PacketNumber, rawPnBytes: ByteArray): QuicPacketHeader
    // - determineEncryptionLevelBasedOnHeaderAndState(...)
    // - CryptoService.getHeaderProtectionMask(secrets, sample) // This might need to be added to CryptoService if not there

    // --- Varint Encoding/Decoding (RFC 9000 Section 16) ---

    /**
     * Encodes a ULong value into a variable-length integer byte array.
     * @param value The ULong value to encode.
     * @return ByteArray representing the varint.
     */
    private fun encodeVarint(value: ULong): ByteArray {
        return when {
            value < (1uL shl 6) -> byteArrayOf(value.toByte())
            value < (1uL shl 14) -> byteArrayOf(
                (0x40uL or (value shr 8)).toByte(),
                value.toByte()
            )
            value < (1uL shl 30) -> byteArrayOf(
                (0x80uL or (value shr 24)).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte()
            )
            value < (1uL shl 62) -> byteArrayOf(
                (0xC0uL or (value shr 56)).toByte(),
                (value shr 48).toByte(),
                (value shr 40).toByte(),
                (value shr 32).toByte(),
                (value shr 24).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte()
            )
            else -> throw IllegalArgumentException("Value too large for varint encoding: $value")
        }
    }

    /**
     * Decodes a variable-length integer from a byte array.
     * @param bytes The ByteArray to read from.
     * @param initialOffset The offset to start reading from.
     * @return Pair containing the decoded ULong value and the number of bytes read.
     * @throws IllegalArgumentException if varint is malformed or not enough data.
     */
    private fun decodeVarint(bytes: ByteArray, initialOffset: Int): Pair<ULong, Int> {
        if (initialOffset >= bytes.size) throw IllegalArgumentException("Cannot decode varint, offset out of bounds")
        val firstByte = bytes[initialOffset].toUByte()
        val length = 1 shl (firstByte.toInt() shr 6) // Determine length: 1, 2, 4, or 8 bytes
        if (initialOffset + length > bytes.size) throw IllegalArgumentException("Not enough bytes to decode varint of length $length")

        var value = (firstByte and 0x3Fu).toULong() // Mask out the first two bits
        for (i in 1 until length) {
            value = (value shl 8) or bytes[initialOffset + i].toUByte().toULong()
        }
        return Pair(value, length)
    }

    /**
     * Serializes the Long Header of a QUIC packet, up to (but not including) the Packet Number field.
     * The resulting ByteArray is often used as Associated Data (AD) for AEAD encryption.
     *
     * @param header The [LongHeader] data to serialize.
     * @param actualPayloadLength The length of the packet's payload (frames) plus the length of the
     *                            packet number field plus the length of the AEAD authentication tag.
     *                            This value is encoded in the 'Length' field of the Long Header.
     * @return A ByteArray representing the serialized Long Header fields before the Packet Number.
     */
    private fun serializeLongHeader(header: LongHeader, actualPayloadLength: ULong): ByteArray {
        val pnLengthBits = (header.rawPacketNumberBytes.size - 1) and 0x03 // 00, 01, 10, 11 for 1-4 bytes
        var firstByte = (0x80 or 0x40).toByte() // Long Header bit, Fixed Bit
        firstByte = firstByte or (header.type.typeValue.toInt() shl 2).toByte() // Type bits (shifted to occupy bits 4 and 5 for QUIC v1 like type)
        // Correction: QUIC v1 type bits are header.type.typeValue shl 4 for bits 0-1 of type.
        // Example: INITIAL (0x00) -> 0x00, HANDSHAKE (0x01) -> 0x10, etc.
        // However, the RFC diagram shows "Type (2)" then "Fixed (1)" then "Specific (4)".
        // Let's use the common interpretation: 1100TTTT (Long, Fixed, Type) or similar.
        // For draft-29+ (RFC 9000): Long (1), Fixed (1), Packet Type (2), Reserved (2), PN Length (2)
        // So, 11TTxxLL where TT is packet type, xx reserved, LL is pn_length_bits
        // Type bits for INITIAL (0x0), HANDSHAKE (0x1), ZERORTT (0x2), RETRY (0x3)
        // So header.type.typeValue is 0,1,2,3. It needs to be shifted by 4 for the TT bits.
        firstByte = (0xC0 or (header.type.typeValue.toInt() shl 4) or pnLengthBits).toByte()


        val versionBytes = ByteArray(4)
        versionBytes[0] = (header.version shr 24).toByte()
        versionBytes[1] = (header.version shr 16).toByte()
        versionBytes[2] = (header.version shr 8).toByte()
        versionBytes[3] = header.version.toByte()

        val dcidLenByte = header.destinationConnectionId.size.toByte()
        val scidLenByte = header.sourceConnectionId.size.toByte()

        val tokenBytes = if (header.type == QuicPacketType.INITIAL && header.token != null) {
            val tokenLenBytes = encodeVarint(header.token.size.toULong())
            tokenLenBytes + header.token
        } else {
            ByteArray(0)
        }
        // The 'Length' field in a Long Header packet includes the length of the Packet Number field
        // and the protected payload (including the authentication tag).
        val lengthFieldBytes = encodeVarint(actualPayloadLength)


        return byteArrayOf(firstByte) +
                versionBytes +
                byteArrayOf(dcidLenByte) + header.destinationConnectionId +
                byteArrayOf(scidLenByte) + header.sourceConnectionId +
                tokenBytes +
                lengthFieldBytes
    }


    /**
     * Parses a Long Header from raw bytes, assuming the first byte has already been unprotected.
     * This function reads from the offset *after* the first byte.
     *
     * @param rawBytes The byte array containing the packet data.
     * @param originalFirstByte The first byte of the packet, *after* header protection has been removed.
     * @param offsetAfterFirstByte The offset in rawBytes to start parsing from (typically 1).
     * @return A Pair containing the parsed [LongHeader] and the total number of bytes read for this header
     *         (including the first byte and up to the end of the packet number field).
     *         Returns null if parsing fails (e.g., insufficient data).
     */
    private fun parseLongHeader(
        rawBytes: ByteArray,
        originalFirstByte: Byte,
        offsetAfterFirstByte: Int
    ): Pair<LongHeader, Int>? {
        var currentOffset = offsetAfterFirstByte

        // --- Version (4 bytes) ---
        if (currentOffset + 4 > rawBytes.size) return null
        val version = (rawBytes[currentOffset].toUInt() shl 24) or
                      (rawBytes[currentOffset + 1].toUInt() shl 16) or
                      (rawBytes[currentOffset + 2].toUInt() shl 8) or
                      rawBytes[currentOffset + 3].toUInt()
        currentOffset += 4

        // --- DCID (Length + ID) ---
        if (currentOffset >= rawBytes.size) return null
        val dcidLen = rawBytes[currentOffset].toUByte().toInt()
        currentOffset += 1
        if (currentOffset + dcidLen > rawBytes.size) return null
        val dcid = rawBytes.copyOfRange(currentOffset, currentOffset + dcidLen)
        currentOffset += dcidLen

        // --- SCID (Length + ID) ---
        if (currentOffset >= rawBytes.size) return null
        val scidLen = rawBytes[currentOffset].toUByte().toInt()
        currentOffset += 1
        if (currentOffset + scidLen > rawBytes.size) return null
        val scid = rawBytes.copyOfRange(currentOffset, currentOffset + scidLen)
        currentOffset += scidLen

        // --- Packet Type from originalFirstByte (bits 4-5 for RFC 9000) ---
        // Original first byte: 11TTxxLL (Long, Fixed, Type, Reserved, PN Len)
        val packetTypeVal = ((originalFirstByte.toUByte().toInt() shr 4) and 0x03).toUByte()
        val packetType = QuicPacketType.entries.find { it.typeValue == packetTypeVal } ?: return null // Unknown packet type

        // --- Token (Varint Length + Token) - Only for INITIAL packets ---
        var token: ByteArray? = null
        if (packetType == QuicPacketType.INITIAL) {
            try {
                val (tokenLen, tokenLenBytesRead) = decodeVarint(rawBytes, currentOffset)
                currentOffset += tokenLenBytesRead
                if (tokenLen > Int.MAX_VALUE.toULong() || currentOffset + tokenLen.toInt() > rawBytes.size) return null
                token = rawBytes.copyOfRange(currentOffset, currentOffset + tokenLen.toInt())
                currentOffset += tokenLen.toInt()
            } catch (e: IllegalArgumentException) {
                return null // Malformed varint or not enough data for token
            }
        }

        // --- Length (Varint) ---
        // This length includes the PN + Payload + Auth Tag
        val (payloadLengthField, lengthBytesRead) = try {
            decodeVarint(rawBytes, currentOffset)
        } catch (e: IllegalArgumentException) {
            return null // Malformed varint or not enough data
        }
        currentOffset += lengthBytesRead

        // --- Packet Number Length from originalFirstByte (bits 0-1) ---
        val pnLength = (originalFirstByte.toInt() and 0x03) + 1 // 00 -> 1 byte, 01 -> 2 bytes, etc.
        if (currentOffset + pnLength > rawBytes.size) return null
        val rawPacketNumberBytes = rawBytes.copyOfRange(currentOffset, currentOffset + pnLength)
        currentOffset += pnLength

        // The full packet number is not reconstructed here.
        // The `packetNumber` field in LongHeader is the *full* PN, which is reconstructed later.
        // For now, we can set it to 0uL or a placeholder, as this parsed header is intermediate.
        // The `rawPacketNumberBytes` are the crucial part from parsing.
        // The `length` field of the LongHeader data class is defined as "length of packet number + protected payload + integrity check".
        // This matches the `payloadLengthField` we just parsed.

        val longHeader = LongHeader(
            type = packetType,
            version = version,
            destinationConnectionId = dcid,
            sourceConnectionId = scid,
            token = token,
            packetNumber = 0uL, // Placeholder: Full PN reconstructed later
            rawPacketNumberBytes = rawPacketNumberBytes,
            length = payloadLengthField // This is PN + Payload + Tag
        )
        // currentOffset is now the total length of the header *including* the PN.
        // The number of bytes read for *this function's scope* is currentOffset - offsetAfterFirstByte + 1 (for the first byte)
        // However, the instruction asks for "total number of bytes read for this header (from originalFirstByte up to and including the PN)"
        // which `currentOffset` correctly represents if we consider `offsetAfterFirstByte` was 1.
        // Let's clarify: the return Int should be total bytes of the header on the wire.
        // If offsetAfterFirstByte is 1, then currentOffset is the length of (Version + CIDs + Token + Length + PN). Add 1 for first byte.
        // The currentOffset already includes the length of PN.
        return Pair(longHeader, currentOffset)
    }

    /**
     * Serializes the Short Header of a QUIC packet, up to (but not including) the Packet Number field.
     * The resulting ByteArray is often used as Associated Data (AD) for AEAD encryption.
     *
     * @param header The [ShortHeader] data to serialize.
     * @param destinationConnectionId The Destination Connection ID to include in the header. This comes
     *                                from the connection context, as it's not always present or reliable
     *                                in the [ShortHeader] data class instance itself (which might be from parsing).
     * @return A ByteArray representing the serialized Short Header fields before the Packet Number.
     */
    private fun serializeShortHeader(header: ShortHeader, destinationConnectionId: ConnectionId): ByteArray {
        val pnLengthBits = (header.rawPacketNumberBytes.size - 1) and 0x03 // 00, 01, 10, 11 for 1-4 bytes
        var firstByte = 0x40.toByte() // Fixed Bit (1), Short Header form bit (0)

        if (header.spinBit) {
            firstByte = firstByte or 0x20.toByte() // 00100000 - Spin Bit (bit 2)
        }
        if (header.keyPhaseBit) {
            firstByte = firstByte or 0x04.toByte() // 00000100 - Key Phase Bit (bit 3 in RFC, but bit position depends on definition)
            // RFC 9000: Short Header: 010SPPNN (S=Spin, PP=KeyPhase, NN=PN Len) -> This is wrong, diagram is 01KSPPNN
            // RFC 9000: 0 (Short), 1 (Fixed), S (Spin), K (Key Phase), P P (PN Len-1)
            // So: 01 S K PP (PP = PN Len bits 00,01,10,11)
            // Bit 2 (0x20) for Spin
            // Bit 3 (0x10) for Key Phase
            // Bits 4,5 (0x0C) for Reserved (must be 0)
            // Bits 6,7 (0x03) for PN Length
            // Reconstruct first byte based on RFC 9000 Figure 10 (Short Header format)
            // 01000000 (Fixed Bit)
            // + Spin Bit (pos 2, mask 0x20)
            // + Key Phase Bit (pos 3, mask 0x10)
            // + PN Length Bits (pos 6,7 from original first byte)
            // Correct first byte construction for Short Header (RFC 9000):
            //   0 (distinguishes from Long Header)
            //   1 (Fixed Bit)
            //   S (Spin Bit)
            //   K (Key Phase Bit) - this bit is at position 3 (0-indexed from MSB)
            //   0 (Reserved Bit) - this bit is at position 4
            //   0 (Reserved Bit) - this bit is at position 5
            //   L L (Packet Number Length - 1) - these bits are at position 6 and 7
            // Let's use the definition from instruction: (spin_bit << 5) | (key_phase_bit << 4)
            // If pn_length_bits are bits 0,1 (LSB of byte), then this matches 01(spin)(KP)xx(PNLen)
            // The common representation is 01SP R R LL (S=Spin, P=KeyPhase, R=Reserved, L=PN Len)
            // Bit 0: 0 (Short Header)
            // Bit 1: 1 (Fixed)
            // Bit 2: Spin (header.spinBit)
            // Bit 3: Key Phase (header.keyPhaseBit)
            // Bit 4: Reserved (0)
            // Bit 5: Reserved (0)
            // Bit 6,7: PN Length - 1 (pnLengthBits)
            firstByte = 0x40 // Fixed bit is 1, form bit implicitly 0.
            if (header.spinBit) firstByte = firstByte or 0x20 // Spin bit at pos 2
            if (header.keyPhaseBit) firstByte = firstByte or 0x10 // Key Phase bit at pos 3
            // Bits 4 and 5 are reserved, should be 0.
            firstByte = firstByte or (pnLengthBits.toByte()) // PN length bits at pos 6,7
        }


        // The destinationConnectionId is explicitly passed as it's from connection context for sending.
        return byteArrayOf(firstByte) + destinationConnectionId
    }

    /**
     * Parses a Short Header from raw bytes, assuming the first byte has already been unprotected.
     * This function reads from the offset *after* the first byte.
     *
     * @param rawBytes The byte array containing the packet data.
     * @param originalFirstByte The first byte of the packet, *after* header protection has been removed.
     * @param offsetAfterFirstByte The offset in rawBytes to start parsing from (typically 1).
     * @param expectedDestCid The Connection ID expected by this endpoint for this connection.
     *                        The parsed DCID from the packet must match this.
     * @return A Pair containing the parsed [ShortHeader] and the total number of bytes read for this header
     *         (including the first byte and up to the end of the packet number field).
     *         Returns null if parsing fails (e.g., insufficient data, DCID mismatch).
     */
    private fun parseShortHeader(
        rawBytes: ByteArray,
        originalFirstByte: Byte,
        offsetAfterFirstByte: Int,
        expectedDestCid: ConnectionId
    ): Pair<ShortHeader, Int>? {
        var currentOffset = offsetAfterFirstByte

        // Extract Spin and Key Phase bits from originalFirstByte (RFC 9000 Short Header)
        // 01SP RR LL (S = Spin at bit 2, K = Key Phase at bit 3 (renamed P for diagram), RR = Reserved, LL = PN Len)
        val spinBit = (originalFirstByte.toInt() and 0x20) != 0
        val keyPhaseBit = (originalFirstByte.toInt() and 0x10) != 0
        // Bits 4,5 are reserved.

        // --- Destination Connection ID ---
        // The length of expectedDestCid determines how many bytes to read.
        val dcidLen = expectedDestCid.size
        if (currentOffset + dcidLen > rawBytes.size) return null // Not enough bytes for DCID
        val destinationConnectionId = rawBytes.copyOfRange(currentOffset, currentOffset + dcidLen)
        currentOffset += dcidLen

        // Validate DCID
        if (!destinationConnectionId.contentEquals(expectedDestCid)) {
            // This packet is not for this connection based on DCID.
            return null
        }

        // --- Packet Number Length from originalFirstByte (bits 6-7) ---
        val pnLength = (originalFirstByte.toInt() and 0x03) + 1 // 00 -> 1 byte, 01 -> 2 bytes, etc.
        if (currentOffset + pnLength > rawBytes.size) return null
        val rawPacketNumberBytes = rawBytes.copyOfRange(currentOffset, currentOffset + pnLength)
        currentOffset += pnLength

        // The full packet number is not reconstructed here.
        // The `packetNumber` field in ShortHeader is the *full* PN, reconstructed later.
        val shortHeader = ShortHeader(
            spinBit = spinBit,
            keyPhaseBit = keyPhaseBit,
            packetNumber = 0uL, // Placeholder: Full PN reconstructed later
            rawPacketNumberBytes = rawPacketNumberBytes,
            destinationConnectionId = destinationConnectionId // Store the parsed DCID
        )

        // currentOffset is now the total length of the header including the PN.
        return Pair(shortHeader, currentOffset)
    }
}
