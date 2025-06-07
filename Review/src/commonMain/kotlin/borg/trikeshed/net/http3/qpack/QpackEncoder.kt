package borg.trikeshed.net.http3.qpack

import borg.trikeshed.net.http.HttpHeaders // Typealias for Map<String, List<String>>
import borg.trikeshed.net.quic.util.BufferWriter
import borg.trikeshed.net.quic.utils.VarInt // For VarInt.encode for direct use if needed for prefixes

/**
 * QPACK Encoder (Minimal Implementation).
 * - Supports static table references (exact match and name-only match).
 * - Supports literal encoding for names and values (Huffman bit always 0).
 * - Does NOT support dynamic table or Huffman coding.
 * - Uses simplified QPACK instruction prefix handling (first byte check).
 */
class QpackEncoder {

    fun encode(headers: HttpHeaders, streamIdForContext: Long): ByteArray {
        // streamIdForContext is ignored in this minimal, dynamic-table-less encoder.
        val writer = BufferWriter()

        headers.forEach { (name, values) ->
            values.forEach { value ->
                val lowerCaseName = name.lowercase() // QPACK names are typically compared case-insensitively (or are lowercase)

                // 1. Check for exact (name, value) match in static table
                val staticExactMatchIndex = findInStaticTable(lowerCaseName, value)
                if (staticExactMatchIndex != null) {
                    // Encode as Indexed Header Field - Static Table
                    // Format: 1 T Index (T=1 for static table)
                    // Simplified: 1xxxxxxx (index in lower 7 bits)
                    // This assumes index fits in 7 bits and T=1 is implicit for static.
                    // QPACK spec: 1 T Index. If T=1 (static), then it's 11xxxxxx for index < 62.
                    // If index >= 62, then 1111111 (0xFF), then varint(index - 62).
                    if (staticExactMatchIndex <= QPACK_PREFIX_INDEX_6BIT_MAX) { // Max 6-bit index (0-61 for T=1)
                        writer.writeByte((QpackInstructionPrefix.INDEXED_HEADER_FIELD_VALUE or 0x40 or staticExactMatchIndex).toByte()) // 11xxxxxx
                    } else {
                        writer.writeByte(0xFF.toByte()) // Prefix for larger static index
                        writer.writeVarint((staticExactMatchIndex - (QPACK_PREFIX_INDEX_6BIT_MAX + 1)).toLong())
                    }
                    continue // Next header field
                }

                // 2. Check for name-only match in static table
                val staticNameMatchIndex = findNameInStaticTable(lowerCaseName)
                if (staticNameMatchIndex != null) {
                    // Encode as Literal Header Field with Name Reference (Static Table)
                    // Format: 01 N T Index (N=0 for not post-base, T=1 for static)
                    // Simplified: 0101xxxx (index in lower 4 bits) for N=0, T=1
                    // QPACK spec: 01 N T Index. If T=1 (static), N=0 (not post-base for static ref usually)
                    // So, 0101iiii for index < 15.
                    // If index >= 15, then 01011111 (0x5F), then varint(index - 15)
                    // Huffman bit (H) for value is 0.
                    if (staticNameMatchIndex <= QPACK_PREFIX_INDEX_4BIT_MAX) { // Max 4-bit index (0-14 for this prefix)
                        // Prefix 0101 (N=0, T=1) | 4-bit index
                        writer.writeByte((QpackInstructionPrefix.LITERAL_NAME_REF_STATIC_VALUE or 0x10 or staticNameMatchIndex).toByte())
                    } else {
                        writer.writeByte((QpackInstructionPrefix.LITERAL_NAME_REF_STATIC_VALUE or 0x10 or 0x0F).toByte()) // 01011111
                        writer.writeVarint((staticNameMatchIndex - (QPACK_PREFIX_INDEX_4BIT_MAX + 1)).toLong())
                    }
                    // Write value literal (H=0 assumed by writeQStringLiteral's simplified varint length)
                    writer.writeQStringLiteral(value)
                    continue // Next header field
                }

                // 3. Literal Header Field with Literal Name
                // Format: 001 H Name | Value
                // Simplified: 00100000 (H=0 for name, next 4 bits unused by this simple prefix)
                // QPACK spec: 001 H NameLen NameVal ValueLen ValueVal. H is for name.
                // Prefix 001xxxxx. H is bit 4 (001Hxxxx).
                // For H=0: 0010xxxx. Let's use 00100000 (0x20) as the base instruction byte.
                writer.writeByte(QpackInstructionPrefix.LITERAL_LITERAL_NAME_VALUE.toByte()) // 00100000 (H=0 for name)
                // Write name literal (H=0 assumed by writeQStringLiteral)
                writer.writeQStringLiteral(lowerCaseName)
                // Write value literal (H=0 assumed by writeQStringLiteral)
                writer.writeQStringLiteral(value)
            }
        }
        return writer.toByteArray()
    }
}
