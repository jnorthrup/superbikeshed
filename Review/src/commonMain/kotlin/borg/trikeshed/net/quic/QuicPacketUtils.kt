package borg.trikeshed.net.quic

import borg.trikeshed.net.quic.utils.encodeVarInt

/**
 * Utilities for QUIC packet construction and parsing.
 */
object QuicPacketUtils {

    /**
     * Serializes the header for a QUIC Initial Packet (client-side perspective for sending).
     * The Packet Number is included in cleartext in this header before Header Protection is applied.
     * The "Length" field includes the length of the Packet Number field itself plus the (encrypted) payload and AEAD tag.
     *
     * @param version The QUIC version (e.g., QuicConstants.QUIC_VERSION_1).
     * @param dcid Destination Connection ID.
     * @param scid Source Connection ID.
     * @param token The token received from a Retry packet, if any. Empty for initial attempt.
     * @param packetNumber The packet number for this packet.
     * @param pnLengthBytes The length of the packet number encoding in bytes (1 to 4).
     * @param payloadLengthWithTagAndPn The total length of the packet number field, the (future) encrypted payload, and the AEAD tag.
     * @return ByteArray representing the serialized Initial Packet header.
     * @throws IllegalArgumentException if pnLengthBytes is not between 1 and 4.
     */
    fun serializeInitialHeader(
        version: UInt,
        dcid: ByteArray,
        scid: ByteArray,
        token: ByteArray,
        packetNumber: Long, // Using Long for flexibility, but will be encoded to pnLengthBytes
        pnLengthBytes: Int,
        payloadLengthWithTagAndPn: Int
    ): Pair<ByteArray, Int> { // Returns Header Bytes and Packet Number Offset
        if (pnLengthBytes !in 1..4) {
            throw IllegalArgumentException("Packet number length (pnLengthBytes) must be between 1 and 4. Was: $pnLengthBytes")
        }

        // First byte:
        //   Header Form (1) = 1 (Long Header)
        //   Fixed Bit (1) = 1
        //   Long Packet Type (2) = 00 (Initial)
        //   Reserved Bits (2) = 00 (MUST be 0)
        //   Packet Number Length (2) = pnLengthBytes - 1 (encoded as 00, 01, 10, 11)
        // So, 0b11000000 = 0xC0
        val firstByte = (0xC0u.toUByte() or ((pnLengthBytes - 1).toUByte() and 0x03u)).toByte()

        val header = mutableListOf<Byte>()
        header.add(firstByte)

        // Version (4 bytes)
        header.add((version shr 24).toByte())
        header.add((version shr 16).toByte())
        header.add((version shr 8).toByte())
        header.add(version.toByte())

        // DCID Len (1 byte) + DCID
        require(dcid.size <= 255) { "DCID too long." } // QUIC max CID length is 20, but field is 1 byte
        header.add(dcid.size.toByte())
        header.addAll(dcid.toList())

        // SCID Len (1 byte) + SCID
        require(scid.size <= 255) { "SCID too long." }
        header.add(scid.size.toByte())
        header.addAll(scid.toList())

        // Token Length (VarInt) + Token
        header.addAll(token.size.toULong().encodeVarInt().toList())
        header.addAll(token.toList())

        // Length (VarInt) - length of (Packet Number + Encrypted Payload + AEAD Tag)
        header.addAll(payloadLengthWithTagAndPn.toULong().encodeVarInt().toList())

        // Packet Number (encoded to pnLengthBytes)
        // Ensure packetNumber fits within the specified length.
        // Max value for 1 byte PN is 2^8-1, for 2 bytes 2^16-1 etc.
        // For simplicity, this encoding just takes lower bytes. Proper handling for larger PNs needed if pnLengthBytes is small.
        val pnBytes = ByteArray(pnLengthBytes)
        var tempPacketNumber = packetNumber
        for (i in pnLengthBytes - 1 downTo 0) {
            pnBytes[i] = (tempPacketNumber and 0xFFL).toByte()
            tempPacketNumber = tempPacketNumber shr 8
        }
        if (tempPacketNumber != 0L && tempPacketNumber != -1L) { // Check if any significant bits were lost (except for sign extension on negative)
             // This check is simplified. A more robust check would consider if packetNumber was negative or too large for pnLengthBytes.
            // For positive numbers, if tempPacketNumber is not 0, it means it overflowed.
            if (packetNumber > 0 && tempPacketNumber != 0L)
             println("Warning: Packet number $packetNumber might be too large for pnLengthBytes $pnLengthBytes")
        }
        header.addAll(pnBytes.toList())

        val packetNumberOffset = header.size - pnLengthBytes // PN is the last thing added before returning
        return Pair(header.toByteArray(), packetNumberOffset)
    }

    /**
     * Serializes the header for a QUIC Short Header Packet (1-RTT).
     * The Packet Number is included in cleartext in this header before Header Protection is applied.
     *
     * @param dcid Destination Connection ID.
     * @param packetNumber The packet number for this packet.
     * @param pnLengthBytes The length of the packet number encoding in bytes (1 to 4).
     * @param keyPhaseBit The Key Phase bit.
     * @return ByteArray representing the serialized Short Packet header.
     * @throws IllegalArgumentException if pnLengthBytes is not between 1 and 4.
     */
    fun serializeShortHeader(
        dcid: ByteArray,
        packetNumber: Long, // Using Long, will be encoded to pnLengthBytes
        pnLengthBytes: Int,
        keyPhaseBit: Boolean = false
    ): ByteArray {
        if (pnLengthBytes !in 1..4) {
            throw IllegalArgumentException("Packet number length (pnLengthBytes) must be between 1 and 4. Was: $pnLengthBytes")
        }

        // First byte for Short Header:
        //   Header Form (1) = 0 (Short Header)
        //   Fixed Bit (1) = 1
        //   Spin Bit (1) = 0 (for now, can be 0 or 1)
        //   Reserved Bits (2) = 00 (MUST be 0)
        //   Key Phase Bit (1) = keyPhaseBit
        //   Packet Number Length (2) = pnLengthBytes - 1
        // So, 0b01000_K_PP
        // Base: 0x40
        var firstByteValue = 0x40 // Header Form (0) + Fixed Bit (1)
        // Spin Bit (bit 5, index 2) - set to 0 for this simplified version
        // firstByteValue = firstByteValue or (0 shl 5)
        if (keyPhaseBit) {
            firstByteValue = firstByteValue or 0x04 // Key Phase on (bit 2, index 5)
        }
        firstByteValue = firstByteValue or ((pnLengthBytes - 1) and 0x03) // PN Length (lower 2 bits)

        val header = mutableListOf<Byte>()
        header.add(firstByteValue.toByte())

        // Destination Connection ID
        header.addAll(dcid.toList())

        // Packet Number (encoded to pnLengthBytes)
        val pnBytes = ByteArray(pnLengthBytes)
        var tempPacketNumber = packetNumber
        for (i in pnLengthBytes - 1 downTo 0) {
            pnBytes[i] = (tempPacketNumber and 0xFFL).toByte()
            tempPacketNumber = tempPacketNumber shr 8
        }
        // No explicit overflow check here for short header PN, but it should fit.
        // QUIC packet number encoding truncates.
        header.addAll(pnBytes.toList())

        return header.toByteArray()
    }

    /**
     * Calculates the offset of the Packet Number field within a serialized Short Header.
     * This assumes the DCID length is known or implicitly part of the `dcid` byte array's size,
     * which is not directly available from just the header bytes without parsing DCID.
     * However, for a known DCID, this can be determined.
     *
     * Short Header Format:
     *   First Byte (Flags) (1 byte)
     *   Destination Connection ID (0..20 bytes)
     *   Packet Number (1-4 bytes)
     *
     * @param dcidLength The length of the Destination Connection ID used in this header.
     * @return The starting offset of the packet number field from the beginning of the header.
     */
    fun calculatePacketNumberOffsetForShortHeader(dcidLength: Int): Int {
        return 1 + dcidLength // 1 (First Byte) + DCID Length
    }

    data class MinimalHeaderData(
        val firstByte: Byte,
        val isShortHeader: Boolean,
        val longHeaderType: UByte?, // Only for Long Headers (e.g. QuicPacketType.INITIAL, QuicPacketType.HANDSHAKE)
        val version: UInt?,
        val dcid: ByteArray,
        val scid: ByteArray?,
        val token: ByteArray?, // Only for Initial Packets
        val lengthField: Long?, // Only for Long Headers (value of the Length field)
        val packetNumberOffset: Int, // Offset from start of 'bytes' to where PN begins
        val packetNumberLengthProtectionBits: Int // Bits 0-1 from firstByte (value 0-3 means 1-4 bytes PN) - BEFORE unmasking HP
    )

    /**
     * Parses preliminary fields from a QUIC packet header to assist in further processing like
     * header protection removal and connection lookup.
     *
     * @param bytes The raw bytes of the QUIC packet (starting with the header).
     * @param knownShortHeaderDcidLength If this is a short header, the length of the DCID must be known
     *                                   from the connection context to correctly parse the packet number offset.
     *                                   Ignored for long headers.
     * @return [MinimalHeaderData] containing parsed fields, or null if parsing fails due to insufficient data
     *         or malformed structure.
     */
    fun parseMinimalHeaderFields(bytes: ByteArray, knownShortHeaderDcidLength: Int = 0): MinimalHeaderData? {
        if (bytes.isEmpty()) return null
        val firstByte = bytes[0]
        val isShort = (firstByte.toInt() and 0x80) == 0

        var currentOffset = 1
        val pnLengthBits = firstByte.toInt() and 0x03 // Packet Number Length bits (00, 01, 10, 11)

        if (isShort) {
            if (knownShortHeaderDcidLength < 0 || knownShortHeaderDcidLength > 20) {
                // Invalid known DCID length for a short header.
                // Cannot reliably find PN offset.
                return null
            }
            val dcidEndOffset = currentOffset + knownShortHeaderDcidLength
            if (dcidEndOffset > bytes.size) return null // Not enough bytes for DCID

            val dcid = bytes.sliceArray(currentOffset until dcidEndOffset)
            currentOffset = dcidEndOffset // Packet number starts right after DCID

            return MinimalHeaderData(
                firstByte = firstByte,
                isShortHeader = true,
                longHeaderType = null,
                version = null,
                dcid = dcid,
                scid = null, // Not present in this form in short header, but is the DCID itself
                token = null,
                lengthField = null,
                packetNumberOffset = currentOffset,
                packetNumberLengthProtectionBits = pnLengthBits
            )
        } else { // Long Header
            // Minimum Long Header: 1 (type) + 4 (ver) + 1 (dcid len) + 0 (dcid) + 1 (scid len) + 0 (scid) + 1 (len varint) + 1 (pn) = 9
            if (bytes.size < 9) return null

            val longPacketTypeBits = (firstByte.toUByte().toInt() shr 4) and 0x03
            val longHeaderType = longPacketTypeBits.toUByte()

            val version = bytes.sliceArray(currentOffset until currentOffset + 4).let {
                ((it[0].toUInt() and 0xFFu) shl 24) or
                ((it[1].toUInt() and 0xFFu) shl 16) or
                ((it[2].toUInt() and 0xFFu) shl 8) or
                (it[3].toUInt() and 0xFFu)
            }
            currentOffset += 4

            val dcidLen = bytes[currentOffset++].toInt() and 0xFF
            if (currentOffset + dcidLen > bytes.size) return null
            val dcid = bytes.sliceArray(currentOffset until currentOffset + dcidLen)
            currentOffset += dcidLen

            val scidLen = bytes[currentOffset++].toInt() and 0xFF
            if (currentOffset + scidLen > bytes.size) return null
            val scid = bytes.sliceArray(currentOffset until currentOffset + scidLen)
            currentOffset += scidLen

            var token: ByteArray? = null
            if (longHeaderType == QuicPacketType.INITIAL) {
                if (currentOffset >= bytes.size) return null // Need at least 1 byte for token length varint
                val (tokenLenVal, tokenLenBytesRead) = try { bytes.decodeVarInt(currentOffset) } catch (e: Exception) { return null }
                currentOffset += tokenLenBytesRead
                val tokenLen = tokenLenVal.toInt()
                if (tokenLen < 0 || currentOffset + tokenLen > bytes.size) return null
                token = bytes.sliceArray(currentOffset until currentOffset + tokenLen)
                currentOffset += tokenLen
            }

            if (currentOffset >= bytes.size) return null // Need at least 1 byte for length varint
            val (lengthVal, lengthBytesRead) = try { bytes.decodeVarInt(currentOffset) } catch (e: Exception) { return null }
            currentOffset += lengthBytesRead

            return MinimalHeaderData(
                firstByte = firstByte,
                isShortHeader = false,
                longHeaderType = longHeaderType,
                version = version,
                dcid = dcid,
                scid = scid,
                token = token,
                lengthField = lengthVal.toLong(),
                packetNumberOffset = currentOffset, // Packet number starts right after the Length field
                packetNumberLengthProtectionBits = pnLengthBits
            )
        }
    }

    /**
     * Extracts the actual packet number length (1-4 bytes) from an *unmasked* first byte of a packet header.
     */
    fun parsePacketNumberLengthFromFirstByte(unmaskedFirstByte: Byte, isShortHeader: Boolean): Int {
        // For both Long and Short headers, the lower 2 bits (0x03) of the first byte,
        // once unmasked, encode the packet number length MINUS ONE.
        return (unmaskedFirstByte.toInt() and 0x03) + 1
    }

}

// Helper extension for UInt to get bytes (ensure big-endian)
private fun UInt.toByte(): Byte = this.toInt().toByte()


    /**
     * Serializes the header for a QUIC Handshake Packet.
     * The Packet Number is included in cleartext in this header before Header Protection is applied.
     * The "Length" field includes the length of the Packet Number field itself plus the (encrypted) payload and AEAD tag.
     * Handshake packets do not contain a Token Length or Token.
     *
     * @param version The QUIC version (e.g., QuicConstants.QUIC_VERSION_1).
     * @param dcid Destination Connection ID.
     * @param scid Source Connection ID.
     * @param packetNumber The packet number for this packet.
     * @param pnLengthBytes The length of the packet number encoding in bytes (1 to 4).
     * @param payloadLengthWithTagAndPn The total length of the packet number field, the (future) encrypted payload, and the AEAD tag.
     * @return ByteArray representing the serialized Handshake Packet header.
     * @throws IllegalArgumentException if pnLengthBytes is not between 1 and 4.
     */
    fun serializeHandshakeHeader(
        version: UInt,
        dcid: ByteArray,
        scid: ByteArray,
        packetNumber: Long,
        pnLengthBytes: Int,
        payloadLengthWithTagAndPn: Int
    ): ByteArray {
        if (pnLengthBytes !in 1..4) {
            throw IllegalArgumentException("Packet number length (pnLengthBytes) must be between 1 and 4. Was: $pnLengthBytes")
        }

        // First byte for Handshake Packet:
        //   Header Form (1) = 1 (Long Header)
        //   Fixed Bit (1) = 1
        //   Long Packet Type (2) = 10 (Handshake PacketType.HANDSHAKE = 0x02u)
        //   Reserved Bits (2) = 00 (MUST be 0)
        //   Packet Number Length (2) = pnLengthBytes - 1
        // So, 0b11100000 = 0xE0
        val firstByte = (0xE0u.toUByte() or ((pnLengthBytes - 1).toUByte() and 0x03u)).toByte()

        val header = mutableListOf<Byte>()
        header.add(firstByte)

        // Version (4 bytes)
        header.add((version shr 24).toByte())
        header.add((version shr 16).toByte())
        header.add((version shr 8).toByte())
        header.add(version.toByte())

        // DCID Len (1 byte) + DCID
        require(dcid.size <= 255)
        header.add(dcid.size.toByte())
        header.addAll(dcid.toList())

        // SCID Len (1 byte) + SCID
        require(scid.size <= 255)
        header.add(scid.size.toByte())
        header.addAll(scid.toList())

        // Length (VarInt) - length of (Packet Number + Encrypted Payload + AEAD Tag)
        header.addAll(payloadLengthWithTagAndPn.toULong().encodeVarInt().toList())

        // Packet Number (encoded to pnLengthBytes)
        val pnBytes = ByteArray(pnLengthBytes)
        var tempPacketNumber = packetNumber
        for (i in pnLengthBytes - 1 downTo 0) {
            pnBytes[i] = (tempPacketNumber and 0xFFL).toByte()
            tempPacketNumber = tempPacketNumber shr 8
        }
        if (packetNumber > 0 && tempPacketNumber != 0L) { // Simplified overflow check
             println("Warning: Handshake Packet number $packetNumber might be too large for pnLengthBytes $pnLengthBytes")
        }
        header.addAll(pnBytes.toList())

        return header.toByteArray()
    }
}
