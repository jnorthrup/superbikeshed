package borg.trikeshed.net.quic.utils

import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Encodes a ULong into a QUIC variable-length integer byte array.
 * Handles 1, 2, 4, and 8 byte encodings based on the value.
 */
fun ULong.encodeVarInt(): ByteArray {
    return when {
        this < 64uL -> byteArrayOf(this.toByte()) // 0xxxxxxx (fits in 1 byte, msb2 are 00)
        this < 16384uL -> { // 01xxxxxx xxxxxxxx (fits in 2 bytes, msb2 are 01)
            val shortVal = this.toUShort()
            byteArrayOf(
                (0x40u or (shortVal shr 8)).toByte(),
                shortVal.toByte()
            )
        }
        this < 1073741824uL -> { // 10xxxxxx xxxxxxxx xxxxxxxx xxxxxxxx (fits in 4 bytes, msb2 are 10)
            val intVal = this.toUInt()
            byteArrayOf(
                (0x80u or (intVal shr 24)).toByte(),
                (intVal shr 16).toByte(),
                (intVal shr 8).toByte(),
                intVal.toByte()
            )
        }
        this <= 4611686018427387903uL -> { // 11xxxxxx xxxxxxxx ... (fits in 8 bytes, msb2 are 11)
                                         // Max value is 2^62 - 1
            byteArrayOf(
                (0xC0u or (this shr 56)).toByte(),
                (this shr 48).toByte(),
                (this shr 40).toByte(),
                (this shr 32).toByte(),
                (this shr 24).toByte(),
                (this shr 16).toByte(),
                (this shr 8).toByte(),
                this.toByte()
            )
        }
        else -> throw IllegalArgumentException("Value $this too large for QUIC VarInt encoding (max is 2^62-1)")
    }
}

/**
 * Decodes a QUIC variable-length integer from a ByteArray.
 * @param startIndex The index in the ByteArray to start decoding from.
 * @return A Pair containing the decoded ULong value and the number of bytes read.
 * @throws IllegalArgumentException if the VarInt encoding is invalid or data is insufficient.
 */
fun ByteArray.decodeVarInt(startIndex: Int = 0): Pair<ULong, Int> {
    if (startIndex >= this.size) throw IllegalArgumentException("Cannot decode VarInt: startIndex out of bounds")

    val firstByte = this[startIndex]
    val msb2 = (firstByte.toUByte().toInt() shr 6) and 0x03 // Get the first two bits

    return when (msb2) {
        0b00 -> { // 1-byte encoding: 0xxxxxxx
            Pair(firstByte.toULong() and 0x3FuL, 1)
        }
        0b01 -> { // 2-byte encoding: 01xxxxxx xxxxxxxx
            if (startIndex + 1 >= this.size) throw IllegalArgumentException("Insufficient data for 2-byte VarInt")
            val value = ((firstByte.toULong() and 0x3FuL) shl 8) or
                        (this[startIndex + 1].toULong() and 0xFFuL)
            Pair(value, 2)
        }
        0b10 -> { // 4-byte encoding: 10xxxxxx xxxxxxxx xxxxxxxx xxxxxxxx
            if (startIndex + 3 >= this.size) throw IllegalArgumentException("Insufficient data for 4-byte VarInt")
            val value = ((firstByte.toULong() and 0x3FuL) shl 24) or
                        ((this[startIndex + 1].toULong() and 0xFFuL) shl 16) or
                        ((this[startIndex + 2].toULong() and 0xFFuL) shl 8) or
                        (this[startIndex + 3].toULong() and 0xFFuL)
            Pair(value, 4)
        }
        0b11 -> { // 8-byte encoding: 11xxxxxx xxxxxxxx ...
            if (startIndex + 7 >= this.size) throw IllegalArgumentException("Insufficient data for 8-byte VarInt")
            val value = ((firstByte.toULong() and 0x3FuL) shl 56) or
                        ((this[startIndex + 1].toULong() and 0xFFuL) shl 48) or
                        ((this[startIndex + 2].toULong() and 0xFFuL) shl 40) or
                        ((this[startIndex + 3].toULong() and 0xFFuL) shl 32) or
                        ((this[startIndex + 4].toULong() and 0xFFuL) shl 24) or
                        ((this[startIndex + 5].toULong() and 0xFFuL) shl 16) or
                        ((this[startIndex + 6].toULong() and 0xFFuL) shl 8) or
                        (this[startIndex + 7].toULong() and 0xFFuL)
            Pair(value, 8)
        }
        else -> throw InternalError("Unreachable: msb2 out of expected range 0..3") // Should not happen
    }
}

// Helper to write ULong to byte array (already in BinaryUtils.kt or similar, but for VarInt context)
// These are byte-order sensitive, ensure big-endian.
private infix fun ULong.or(other: UByte): ULong = this or other.toULong()
private fun UShort.toByte(): Byte = this.toInt().toByte()
private fun UInt.toByte(): Byte = this.toInt().toByte()
private fun ULong.toByte(): Byte = this.toInt().toByte()

// Overloads for convenience if needed, though ULong covers all positive integers for QUIC varints.
fun Int.encodeVarInt(): ByteArray = this.toULong().encodeVarInt()
fun Long.encodeVarInt(): ByteArray = this.toULong().encodeVarInt() // Watch for negative Longs if used directly
fun UInt.encodeVarInt(): ByteArray = this.toULong().encodeVarInt()

// Extension for ByteArray to simplify VarInt reading when you don't need the offset.
fun ByteArray.readVarInt(): ULong? = try { decodeVarInt().first } catch (e: Exception) { null }

// For writing VarInt directly to a MutableList<Byte> or similar stream
fun MutableList<Byte>.addVarInt(value: ULong) {
    this.addAll(value.encodeVarInt().toList())
}
