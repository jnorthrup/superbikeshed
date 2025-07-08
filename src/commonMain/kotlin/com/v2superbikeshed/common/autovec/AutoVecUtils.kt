package com.v2superbikeshed.common.autovec

import kotlin.experimental.ExperimentalUnsignedTypes

/**
 * Custom annotation to hint the compiler about vectorizable functions.
 * This is a conceptual hint and its effectiveness depends on the Kotlin compiler's capabilities.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Vectorizable

/**
 * Finds the first occurrence of a byte in a ByteArray.
 * Designed to be auto-vectorization friendly.
 *
 * @param data The ByteArray to search.
 * @param target The byte to find.
 * @return The index of the first occurrence, or -1 if not found.
 */
@Vectorizable
@OptIn(ExperimentalUnsignedTypes::class)
inline fun findByte(data: ByteArray, target: Byte): Int {
    for (i in data.indices) {
        if (data[i] == target) return i
    }
    return -1
}

/**
 * Checks if any of the 8 bytes in a Long value represent whitespace characters.
 * Uses SWAR (SIMD Within A Register) principles for potential vectorization.
 *
 * @param bytes A Long value representing 8 bytes.
 * @return True if any byte is a whitespace character (space, tab, LF, CR), false otherwise.
 */
@Vectorizable
@OptIn(ExperimentalUnsignedTypes::class)
inline fun isWhitespace8(bytes: Long): Boolean {
    // WS_MASK: 0x20 (space), 0x09 (tab), 0x0A (LF), 0x0D (CR)
    // This mask needs to be carefully constructed for SWAR.
    // The example in the markdown is simplified. A more robust SWAR for multiple chars is complex.
    // For demonstration, we'll use a direct check or a simplified mask concept.
    // The original markdown example's WS_MASK (0x20090A0DL) is for a specific SWAR technique
    // that might not directly map to a simple 'and' operation for multiple distinct bytes.
    // A more direct approach for auto-vectorization is often a simple loop or specific intrinsics.

    // For true SWAR, you'd typically check against a set of values.
    // This is a conceptual representation.
    val space = 0x20L
    val tab = 0x09L
    val lf = 0x0AL
    val cr = 0x0DL

    // Check each byte position for any of the whitespace characters
    for (i in 0 until 8) {
        val byte = (bytes shr (i * 8)) and 0xFF
        if (byte == space || byte == tab || byte == lf || byte == cr) {
            return true
        }
    }
    return false
}

/**
 * Matches a 4-byte pattern within a ByteArray at a given position.
 * Uses SWAR (SIMD Within A Register) principles by packing bytes into an Int.
 *
 * @param buffer The ByteArray to search within.
 * @param pos The starting position in the buffer.
 * @param pattern The 4-byte pattern packed into an Int.
 * @return True if the pattern matches, false otherwise or if out of bounds.
 */
@Vectorizable
inline fun match4(buffer: ByteArray, pos: Int, pattern: Int): Boolean {
    if (pos + 3 >= buffer.size) return false
    val packed = (buffer[pos].toInt() and 0xFF shl 24) or
                 (buffer[pos+1].toInt() and 0xFF shl 16) or
                 (buffer[pos+2].toInt() and 0xFF shl 8) or
                 (buffer[pos+3].toInt() and 0xFF)
    return packed == pattern
}

/**
 * Aligns a ByteArray to an 8-byte boundary by creating a new array.
 * This is useful for operations that benefit from memory alignment for SIMD.
 *
 * @receiver The original ByteArray.
 * @return A new ByteArray, 8-byte aligned, containing the original data.
 */
@Vectorizable
fun ByteArray.toAlignedPyramid(): ByteArray {
    val alignedSize = (size + 7) and -8 // 8-byte aligned size
    val aligned = ByteArray(alignedSize)
    this.copyInto(aligned)
    return aligned
}

// Note: The original markdown example for toAlignedPyramid included 'j { i -> ... }'
// which seems to refer to a 'Join' concept. Since 'Join' is deprecated,
// this implementation focuses purely on the byte array alignment aspect.
// The 'Pyramid' naming might imply a specific data structure beyond simple alignment,
// but based on the provided code, it's primarily about alignment for SIMD.
