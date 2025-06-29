package borg.trikeshed.multiformats.base

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.emptyIndexed
import borg.trikeshed.lib.toIndexed

/**
 * Base16 (Hexadecimal) encoding and decoding utility.
 * Produces lowercase hex strings. Decodes both uppercase and lowercase hex strings.
 */
object Base16 {
    private val LOWERCASE_HEX_CHARS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    /**
     * Encodes an Indexed<Byte> into a Base16 (hexadecimal) string.
     *
     * @param input The data to encode.
     * @return The lowercase hexadecimal string representation.
     */
    fun encode(input: Indexed<Byte>): String {
        if (input.size == 0) return ""
        val result = CharArray(input.size * 2)
        for (i in 0 until input.size) {
            val byte = input[i].toInt() and 0xFF // Ensure positive value for bitwise ops
            result[i * 2] = LOWERCASE_HEX_CHARS[byte ushr 4] // Upper nibble
            result[i * 2 + 1] = LOWERCASE_HEX_CHARS[byte and 0x0F]  // Lower nibble
        }
        return result.concatToString()
    }

    /**
     * Decodes a Base16 (hexadecimal) string into an Indexed<Byte>.
     * Accepts both uppercase and lowercase hex characters.
     *
     * @param input The hexadecimal string to decode.
     * @return An Indexed<Byte> containing the decoded data.
     * @throws IllegalArgumentException if the input string has an odd length or contains invalid hex characters.
     */
    fun decode(input: String): Indexed<Byte> {
        if (input.isEmpty()) return emptyIndexed() // From CoreTypes.kt
        if (input.length % 2 != 0) {
            throw IllegalArgumentException("Hex string must have an even number of characters, got length ${input.length}.")
        }

        val outputSize = input.length / 2
        val result = ByteArray(outputSize) // Temporary ByteArray for building
        for (i in 0 until outputSize) {
            val highNibbleChar = input[i * 2]
            val lowNibbleChar = input[i * 2 + 1]

            val highNibble = decodeHexChar(highNibbleChar)
            val lowNibble = decodeHexChar(lowNibbleChar)

            result[i] = ((highNibble shl 4) or lowNibble).toByte()
        }
        return result.toIndexed() // Convert to Indexed<Byte> using extension from CoreTypes.kt
    }

    private fun decodeHexChar(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10 // Accept uppercase for decoding
            else -> throw IllegalArgumentException("Invalid hex character: '$c'")
        }
    }
}
