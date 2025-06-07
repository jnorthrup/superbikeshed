package borg.trikeshed.net.quic.utils

import borg.trikeshed.net.quic.QuicFrameType

/**
 * Creates a QUIC CRYPTO frame.
 *
 * @param offset The offset in the cryptographic handshake stream.
 * @param data The crypto data to include in the frame.
 * @return ByteArray representing the serialized CRYPTO frame.
 */
fun createCryptoFrame(offset: ULong, data: ByteArray): ByteArray {
    val typeByte = QuicFrameType.CRYPTO
    val offsetBytes = offset.encodeVarInt() // Uses VarInt encoding
    val lengthBytes = data.size.toULong().encodeVarInt() // Uses VarInt encoding for data length

    return byteArrayOf(typeByte.toByte()) + offsetBytes + lengthBytes + data
}

/**
 * Parses a CRYPTO frame from the beginning of the given ByteArray.
 *
 * @param bytes The ByteArray potentially starting with a CRYPTO frame.
 * @return A Pair containing the parsed crypto data (ByteArray) and the total number of bytes read for this frame.
 *         Returns null if parsing fails or if it's not a CRYPTO frame.
 */
fun parseCryptoFrame(bytes: ByteArray): Pair<ByteArray, Int>? {
    if (bytes.isEmpty()) return null
    var currentOffset = 0

    val frameType = bytes[currentOffset++].toUByte()
    if (frameType != QuicFrameType.CRYPTO) {
        // Not a CRYPTO frame
        return null
    }

    // Parse Offset (VarInt)
    if (currentOffset >= bytes.size) return null
    val (cryptoOffsetVal, offsetBytesRead) = try {
        bytes.decodeVarInt(currentOffset)
    } catch (e: IllegalArgumentException) {
        return null // Malformed VarInt
    }
    currentOffset += offsetBytesRead

    // Parse Length (VarInt)
    if (currentOffset >= bytes.size) return null
    val (cryptoDataLengthVal, lengthBytesRead) = try {
        bytes.decodeVarInt(currentOffset)
    } catch (e: IllegalArgumentException) {
        return null // Malformed VarInt
    }
    currentOffset += lengthBytesRead

    val cryptoDataLength = cryptoDataLengthVal.toInt()
    if (cryptoDataLength < 0) return null // Invalid length

    // Read Crypto Data
    if (currentOffset + cryptoDataLength > bytes.size) {
        return null // Not enough data for the specified length
    }
    val cryptoData = bytes.sliceArray(currentOffset until currentOffset + cryptoDataLength)
    currentOffset += cryptoDataLength

    return Pair(cryptoData, currentOffset)
}

/**
 * Creates a QUIC STREAM frame.
 * Frame Type: 0x08-0x0f
 *  .<y_bin_570> . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
 * | Frame Type (i) | Stream ID (i) | Offset (i) | Length (i) | Stream Data (*) Stream Data (*) |
 *  ._______________________________________________________________________ . . . . . . . . . .
 * The lower three bits of the type byte are:
 * - Bit 2 (0x04 - OFF bit): If set, an Offset field is present.
 * - Bit 1 (0x02 - LEN bit): If set, a Length field is present.
 * - Bit 0 (0x01 - FIN bit): If set, this is the final data for this stream.
 *
 * @param streamId The ID of the stream.
 * @param offset The offset in the stream for this data. If 0 and no other data has been sent, OFF bit can be 0.
 * @param data The stream data.
 * @param fin Whether this is the final data for the stream.
 * @return ByteArray representing the serialized STREAM frame.
 */
fun createStreamFrame(streamId: ULong, offset: ULong, data: ByteArray, fin: Boolean = false): ByteArray {
    var typeByte = 0x08u.toUByte() // Base for STREAM frames

    val hasOffset = offset > 0uL
    val hasLength = true // For simplicity in this version, always include length. Spec allows omitting if packet is full.

    if (hasOffset) typeByte = typeByte or 0x04u
    if (hasLength) typeByte = typeByte or 0x02u
    if (fin) typeByte = typeByte or 0x01u

    val streamIdBytes = streamId.encodeVarInt()
    val offsetBytes = if (hasOffset) offset.encodeVarInt() else byteArrayOf()
    val lengthBytes = if (hasLength) data.size.toULong().encodeVarInt() else byteArrayOf()

    return byteArrayOf(typeByte.toByte()) + streamIdBytes + offsetBytes + lengthBytes + data
}

/**
 * Parses a STREAM frame from the beginning of the given ByteArray.
 * This is a basic parser and assumes length is always present if LEN bit is set.
 *
 * @param bytes The ByteArray potentially starting with a STREAM frame.
 * @return A Triple containing Stream ID (ULong), Offset (ULong), and Data (ByteArray).
 *         Returns null if parsing fails or if it's not a STREAM frame.
 */
fun parseStreamFrame(bytes: ByteArray): Triple<ULong, ULong, ByteArray>? {
    if (bytes.isEmpty()) return null
    var currentOffset = 0

    val frameTypeByte = bytes[currentOffset++].toUByte()
    if (frameTypeByte < 0x08u || frameTypeByte > 0x0Fu) {
        // Not a STREAM frame
        return null
    }

    val hasFin = (frameTypeByte and 0x01u) != 0u.toUByte()
    val hasLen = (frameTypeByte and 0x02u) != 0u.toUByte()
    val hasOff = (frameTypeByte and 0x04u) != 0u.toUByte()

    // Parse Stream ID (VarInt)
    if (currentOffset >= bytes.size) return null
    val (streamId, streamIdBytesRead) = try {
        bytes.decodeVarInt(currentOffset)
    } catch (e: IllegalArgumentException) { return null }
    currentOffset += streamIdBytesRead

    // Parse Offset (VarInt), if present
    var streamOffset = 0uL
    if (hasOff) {
        if (currentOffset >= bytes.size) return null
        val (offsetVal, offsetBytesRead) = try {
            bytes.decodeVarInt(currentOffset)
        } catch (e: IllegalArgumentException) { return null }
        streamOffset = offsetVal
        currentOffset += offsetBytesRead
    }

    // Parse Length (VarInt), if present
    var dataLength: Int
    if (hasLen) {
        if (currentOffset >= bytes.size) return null
        val (lenVal, lengthBytesRead) = try {
            bytes.decodeVarInt(currentOffset)
        } catch (e: IllegalArgumentException) { return null }
        dataLength = lenVal.toInt()
        if (dataLength < 0) return null // Invalid length
        currentOffset += lengthBytesRead
    } else {
        // If LEN bit is not set, data extends to the end of the packet.
        // This parser needs packet boundary context to handle that correctly.
        // For simplicity, we'll assume LEN is always present for now or that this frame is the last in its context.
        dataLength = bytes.size - currentOffset
        if (dataLength < 0) return null
    }

    // Read Stream Data
    if (currentOffset + dataLength > bytes.size) {
        return null // Not enough data
    }
    val streamData = bytes.sliceArray(currentOffset until currentOffset + dataLength)
    // currentOffset += dataLength // Not needed as it's the last field consumed by this simple parser

    return Triple(streamId, streamOffset, streamData)
    // FIN bit (`hasFin`) is parsed but not returned in this Triple, could be added if needed.
}


/**
 * Represents data parsed from an ACK frame.
 * ECN counts are omitted for simplicity in this version.
 */
data class AckFrameData(
    val largestAcknowledged: ULong,
    val ackDelay: ULong,
    val ackRangeCount: ULong,
    val firstAckRange: ULong,
    val ackRanges: List<Pair<ULong, ULong>> // Gap, Acked Count
)

/**
 * Creates a QUIC ACK frame.
 * Frame Type: 0x02 or 0x03 (if ECN counts are present)
 * Simplified version: Does not include ECN counts.
 *
 * @param largestAcked The largest acknowledged packet number.
 * @param ackDelay The delay in acknowledging this packet, in microseconds, scaled by ack_delay_exponent.
 * @param ackRanges List of Pairs, where each Pair is (Gap, Acked Range Length). Gap is 0 for the first range.
 *                  The first range is from (Largest Acked - First Ack Range) to Largest Acked.
 * @param firstAckRange The number of contiguous packets preceding Largest Acknowledged that are being ACKed.
 *                      (i.e., Largest Acked - First Ack Range is the smallest in this range).
 * @return ByteArray representing the serialized ACK frame.
 */
fun createAckFrame(
    largestAcked: Long, // Using Long as packet numbers are often handled as Long
    ackDelay: ULong,
    ackRanges: List<Pair<ULong, ULong>>, // Each pair: (Gap to previous range, Length of this acked range)
    firstAckRange: ULong // Number of packets in the first range (from largestAcked downwards)
): ByteArray {
    val typeByte = QuicFrameType.ACK
    var frame = byteArrayOf(typeByte.toByte())

    frame += largestAcked.toULong().encodeVarInt()
    frame += ackDelay.encodeVarInt()
    frame += ackRanges.size.toULong().encodeVarInt() // ACK Range Count
    frame += firstAckRange.encodeVarInt() // First ACK Range

    for (ackRange in ackRanges) {
        frame += ackRange.first.encodeVarInt()  // Gap
        frame += ackRange.second.encodeVarInt() // Acked Range Length
    }
    return frame
}

/**
 * Parses a QUIC ACK frame.
 * This is a basic parser and does not handle ECN counts.
 *
 * @param bytes The ByteArray potentially starting with an ACK frame.
 * @return An [AckFrameData] object, or null if parsing fails.
 */
fun parseAckFrame(bytes: ByteArray): AckFrameData? {
    if (bytes.isEmpty()) return null
    var offset = 0

    val frameType = bytes[offset++].toUByte()
    if (frameType != QuicFrameType.ACK && frameType != 0x03u.toUByte() /* ACK with ECN */) {
        return null // Not an ACK frame
    }
    // ECN counts would be parsed here if frameType is 0x03

    try {
        val (largestAcked, laBytes) = bytes.decodeVarInt(offset); offset += laBytes
        val (ackDelay, adBytes) = bytes.decodeVarInt(offset); offset += adBytes
        val (ackRangeCount, arcBytes) = bytes.decodeVarInt(offset); offset += arcBytes
        val (firstAckRange, farBytes) = bytes.decodeVarInt(offset); offset += farBytes

        val ackRanges = mutableListOf<Pair<ULong, ULong>>()
        for (i in 0 until ackRangeCount.toInt()) {
            if (offset >= bytes.size) return null // Check bounds before reading gap
            val (gap, gapBytes) = bytes.decodeVarInt(offset); offset += gapBytes
            if (offset >= bytes.size) return null // Check bounds before reading acked range length
            val (ackedRangeLen, arlBytes) = bytes.decodeVarInt(offset); offset += arlBytes
            ackRanges.add(Pair(gap, ackedRangeLen))
        }
        // Note: This parser doesn't validate if all bytes were consumed if there's trailing data
        // A more robust parser would return bytesConsumed as well.
        return AckFrameData(largestAcked, ackDelay, ackRangeCount, firstAckRange, ackRanges)
    } catch (e: IllegalArgumentException) {
        return null // VarInt parsing error or insufficient data
    }
}
