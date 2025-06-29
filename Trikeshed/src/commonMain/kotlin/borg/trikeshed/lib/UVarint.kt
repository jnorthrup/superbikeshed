package borg.trikeshed.lib

/**
 * Utility for encoding and decoding Unsigned Varints (UVarints).
 * UVarints are a way to encode integers of arbitrary size using a variable number of bytes.
 * Lower numbers use fewer bytes.
 */
object UVarint {

    /**
     * Encodes a Long value as a UVarint into an Indexed<Byte>.
     *
     * @param value The non-negative Long value to encode.
     * @return An Indexed<Byte> containing the UVarint representation.
     * @throws IllegalArgumentException if the value is negative.
     */
    fun encode(value: Long): Indexed<Byte> {
        if (value < 0) throw IllegalArgumentException("UVarint cannot encode negative value: $value")

        // Use a temporary ByteArray for efficient construction
        val temp = ByteArray(10) // Max 10 bytes for a 64-bit unsigned integer
        var v = value
        var i = 0
        while (true) {
            val byteVal = (v and 0x7F).toByte()
            v = v ushr 7 // Unsigned right shift
            if (v == 0L) {
                temp[i++] = byteVal
                break
            }
            temp[i++] = ((byteVal.toInt() or 0x80).toByte()) // Set MSB for continuation
        }
        // Create a new ByteArray of the exact size and copy content
        val resultBytes = ByteArray(i)
        for (j in 0 until i) {
            resultBytes[j] = temp[j]
        }
        return resultBytes.toIndexed()
    }

    /**
     * Decodes a UVarint from an Indexed<Byte> starting at a given offset.
     *
     * @param bytes The Indexed<Byte> containing the UVarint.
     * @param offset The starting offset within `bytes` to begin decoding.
     * @return A Join<Long, Int> where 'a' is the decoded Long value and 'b' is the number of bytes read.
     * @throws IllegalArgumentException if the UVarint is malformed (unterminated or overflow).
     */
    fun decode(bytes: Indexed<Byte>, offset: Int = 0): Join<Long, Int> {
        var result = 0L
        var shift = 0
        var bytesRead = 0

        if (offset >= bytes.size) {
            throw IllegalArgumentException("UVarint decode offset out of bounds or empty input at offset.")
        }

        for (i in offset until bytes.size) {
            bytesRead++
            val byte = bytes[i]
            result = result or ((byte.toLong() and 0x7F) shl shift)
            if ((byte.toInt() and 0x80) == 0) { // Check MSB: if 0, this is the last byte
                return result j bytesRead // Using infix j from CoreTypes
            }
            shift += 7
            if (shift > 63) { // More than 10 bytes for a 64-bit number, or malformed to exceed Long.MAX_VALUE quickly
                throw IllegalArgumentException("UVarint overflow: input is too large or malformed.")
            }
        }
        throw IllegalArgumentException("UVarint unterminated: sequence does not end.")
    }
}
