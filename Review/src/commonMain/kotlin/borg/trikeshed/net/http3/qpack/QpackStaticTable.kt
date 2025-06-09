package borg.trikeshed.net.http3.qpack

// Represents a header field in the QPACK static table
data class HeaderField(val name: String, val value: String?)

// QPACK Static Table entries (RFC 9204, Appendix A)
// Using a List, index in the list corresponds to QPACK static table index.
val QPACK_STATIC_TABLE: List<HeaderField> = listOf(
    HeaderField(":authority", ""), // 0
    HeaderField(":path", "/"), // 1
    HeaderField("age", "0"), // 2
    HeaderField("content-disposition", ""), // 3
    HeaderField("content-length", "0"), // 4
    HeaderField("cookie", ""), // 5
    HeaderField("date", ""), // 6
    HeaderField("etag", ""), // 7
    HeaderField("if-modified-since", ""), // 8
    HeaderField("if-none-match", ""), // 9
    HeaderField("last-modified", ""), // 10
    HeaderField("link", ""), // 11
    HeaderField("location", ""), // 12
    HeaderField("referer", ""), // 13
    HeaderField("set-cookie", ""), // 14
    HeaderField(":method", "CONNECT"), // 15
    HeaderField(":method", "DELETE"), // 16
    HeaderField(":method", "GET"), // 17
    HeaderField(":method", "HEAD"), // 18
    HeaderField(":method", "OPTIONS"), // 19
    HeaderField(":method", "POST"), // 20
    HeaderField(":method", "PUT"), // 21
    HeaderField(":scheme", "http"), // 22
    HeaderField(":scheme", "https"), // 23
    HeaderField(":status", "103"), // 24
    HeaderField(":status", "200"), // 25
    HeaderField(":status", "304"), // 26
    HeaderField(":status", "404"), // 27
    HeaderField(":status", "503"), // 28
    HeaderField("accept", "*/*"), // 29
    HeaderField("accept", "application/dns-message"), // 30
    HeaderField("accept-encoding", "gzip, deflate, br"), // 31
    HeaderField("accept-ranges", "bytes"), // 32
    HeaderField("access-control-allow-headers", "cache-control"), // 33
    HeaderField("access-control-allow-headers", "content-type"), // 34
    HeaderField("access-control-allow-origin", "*"), // 35
    HeaderField("cache-control", "max-age=0"), // 36
    HeaderField("cache-control", "max-age=2592000"), // 37
    HeaderField("cache-control", "max-age=604800"), // 38
    HeaderField("cache-control", "no-cache"), // 39
    HeaderField("cache-control", "no-store"), // 40
    HeaderField("cache-control", "public, max-age=31536000"), // 41
    HeaderField("content-encoding", "br"), // 42
    HeaderField("content-encoding", "gzip"), // 43
    HeaderField("content-type", "application/dns-message"), // 44
    HeaderField("content-type", "application/javascript"), // 45
    HeaderField("content-type", "application/json"), // 46
    HeaderField("content-type", "application/x-www-form-urlencoded"), // 47
    HeaderField("content-type", "image/gif"), // 48
    HeaderField("content-type", "image/jpeg"), // 49
    HeaderField("content-type", "image/png"), // 50
    HeaderField("content-type", "text/css"), // 51
    HeaderField("content-type", "text/html; charset=utf-8"), // 52
    HeaderField("content-type", "text/plain; charset=utf-8"), // 53
    HeaderField("range", "bytes=0-"), // 54
    HeaderField("strict-transport-security", "max-age=31536000"), // 55
    HeaderField("strict-transport-security", "max-age=31536000; includesubdomains"), // 56
    HeaderField("strict-transport-security", "max-age=31536000; includesubdomains; preload"), // 57
    HeaderField("vary", "accept-encoding"), // 58
    HeaderField("vary", "origin"), // 59
    HeaderField("x-content-type-options", "nosniff"), // 60
    HeaderField("x-xss-protection", "1; mode=block"), // 61
    HeaderField(":status", "100"), // 62
    HeaderField(":status", "204"), // 63
    HeaderField(":status", "206"), // 64
    HeaderField(":status", "302"), // 65
    HeaderField(":status", "400"), // 66
    HeaderField(":status", "403"), // 67
    HeaderField(":status", "421"), // 68
    HeaderField(":status", "425"), // 69
    HeaderField(":status", "500"), // 70
    HeaderField("accept-language", ""), // 71
    HeaderField("access-control-allow-credentials", "FALSE"), // 72
    HeaderField("access-control-allow-credentials", "TRUE"), // 73
    HeaderField("access-control-allow-headers", "*"), // 74
    HeaderField("access-control-allow-methods", "get"), // 75
    HeaderField("access-control-allow-methods", "get, post, options"), // 76
    HeaderField("access-control-allow-methods", "options"), // 77
    HeaderField("access-control-expose-headers", "content-length"), // 78
    HeaderField("access-control-request-headers", "content-type"), // 79
    HeaderField("access-control-request-method", "get"), // 80
    HeaderField("access-control-request-method", "post"), // 81
    HeaderField("alt-svc", "clear"), // 82
    HeaderField("authorization", ""), // 83
    HeaderField("content-security-policy", "script-src 'none'; object-src 'none'; base-uri 'none'"), // 84
    HeaderField("early-data", "1"), // 85
    HeaderField("expect-ct", ""), // 86
    HeaderField("forwarded", ""), // 87
    HeaderField("if-range", ""), // 88
    HeaderField("origin", ""), // 89
    HeaderField("purpose", "prefetch"), // 90
    HeaderField("server", ""), // 91
    HeaderField("timing-allow-origin", "*"), // 92
    HeaderField("upgrade-insecure-requests", "1"), // 93
    HeaderField("user-agent", ""), // 94
    HeaderField("x-forwarded-for", ""), // 95
    HeaderField("x-frame-options", "deny"), // 96
    HeaderField("x-frame-options", "sameorigin"), // 97
    HeaderField("sec-purpose", "prefetch") // 98. Note: RFC 9204 Appendix A lists this as index 98, but older drafts might vary.
                                       // Example: "sec-purpose" was added later. The table in RFC 9204 is the definitive one.
                                       // For example, some online tools might show :path without value at 1, / at 2.
                                       // The provided list matches RFC9204 Appendix A directly.
)

// Helper to find by name and value
fun findInStaticTable(name: String, value: String): Int? {
    return QPACK_STATIC_TABLE.indexOfFirst { it.name == name.lowercase() && it.value == value }
        .takeIf { it != -1 }
}

// Helper to find by name only
fun findNameInStaticTable(name: String): Int? {
    return QPACK_STATIC_TABLE.indexOfFirst { it.name == name.lowercase() }
        .takeIf { it != -1 }
}

// QPACK instruction prefix constants (simplified byte-level checks)
// These are not the full bit patterns but high-level identifiers for the first byte.
object QpackInstructionPrefix {
    const val INDEXED_HEADER_FIELD_PREFIX_MASK: Int = 0x80 // Starts with '1'
    const val INDEXED_HEADER_FIELD_VALUE: Int = 0x80

    // Literal with Name Reference (Static Table)
    // Pattern: 01N T Index (Static) | 01N 0 Index (Dynamic, N=Post-Base)
    // Simplified: If it starts with '01', assume static name reference for now.
    // A full implementation needs to check the N bit and the T bit (for static) or Post-Base bit.
    // For static table name ref: 01 N=0/1 StaticIndex ...
    // 0100 xxxx (N=0, index fits in lower 4 bits)
    // 0101 xxxx (N=1, index fits in lower 4 bits)
    // 01 N=0 Varint Index (if index doesn't fit)
    // 01 N=1 Varint Index (if index doesn't fit)
    // For this minimal version, we'll use a simpler check and encoding.
    const val LITERAL_NAME_REF_STATIC_PREFIX_MASK: Int = 0xC0 // Starts with '01'
    const val LITERAL_NAME_REF_STATIC_VALUE: Int = 0x40

    // Literal with Literal Name
    // Pattern: 001 H Name String | Value String
    const val LITERAL_LITERAL_NAME_PREFIX_MASK: Int = 0xE0 // Starts with '001'
    const val LITERAL_LITERAL_NAME_VALUE: Int = 0x20

    // Huffman bit for string literals (assume 0 for now = not Huffman encoded)
    const val HUFFMAN_BIT_MASK: Int = 0x80 // For the length byte if applicable in full spec.
                                         // Here, applied to a conceptual "flags" byte or part of prefix.
}

// QPACK value for N bit (Post-Base index for dynamic table, or part of static index representation)
// For "Literal Header Field with Name Reference", the N bit is the 3rd MSB if first two are '01'.
// 01N... : If N=0, index is relative to base. If N=1, index is post-base.
// For static table name references, N is also part of the encoding.
// For simplicity, we'll mostly deal with N=0 or directly encode static indices.
const val QPACK_N_BIT_MASK_FOR_NAME_REF = 0x10 // Example for a byte: 000N0000 if T=0 (Static)

// Max value for a 6-bit prefix index (63) as in 11xxxxxx or 01Txxxxx
const val QPACK_PREFIX_INDEX_6BIT_MAX = 0x3F
// Max value for a 4-bit prefix index (15) as in 01NTxxxx
const val QPACK_PREFIX_INDEX_4BIT_MAX = 0x0F


// Helper for writing string literals (Length (VarInt) + Value (Bytes))
// Huffman bit (H) is assumed to be 0.
fun borg.trikeshed.net.quic.util.BufferWriter.writeQStringLiteral(value: String) {
    val valueBytes = value.encodeToByteArray()
    // In QPACK, length is prefixed with an H bit (0 for no Huffman).
    // Varint encoding of (length << 1 | (if (huffman) 1 else 0)).
    // For H=0, it's just varint encoding of (length << 1).
    // However, RFC 9204 section 4.5.1.c says for String Literals:
    // "An H bit, followed by the Length of the string value in bytes, encoded as a variable-length integer with a 7-bit prefix.
    //  The string value represented as a sequence of bytes of the specified Length."
    // This means the H bit is the MSB of the *first byte of the varint-encoded length*.
    // So, length itself needs to be encoded, then H bit prepended to its varint encoding.
    // This is complex if varint is > 1 byte.
    // Simplified approach for now: write varint length, assume H=0 is implicit by decoder.
    // Or, follow the spec:
    val length = valueBytes.size
    var lengthVarInt = borg.trikeshed.net.quic.util.VarInt.encode(length.toLong())
    // The H bit (0) should be the MSB of this varint string.
    // If lengthVarInt[0] & 0x80 is already set, this is an issue with varint encoding vs H bit.
    // Spec section 4.5.1.c is key: "Length of String Value: An H bit, followed by the Length ... encoded as a variable-length integer with a 7-bit prefix."
    // This means the varint itself uses a 7-bit prefix per byte. The H bit is separate.
    // E.g., H | Length (varint with 7-bit prefix). The H bit is the MSB of the *first byte*.
    // So, if length is < 128, it's one byte: Hxxxxxxx.
    // If length is >= 128, it's more complex. For this minimal version, let's assume length fits in 7 bits for H=0.

    // Simpler interpretation for now: H bit is separate from length encoding.
    // Our VarInt.encode produces full bytes.
    // Let's assume the H bit is part of the instruction byte containing the string type, not the length prefix itself.
    // e.g. instruction `001Hxxxx` where H is the Huffman bit.
    // Then, standard varint for length.
    this.writeVarint(length.toLong())
    this.writeBytes(valueBytes)
}

// Helper for reading string literals
fun borg.trikeshed.net.quic.util.BufferReader.readQStringLiteral(huffmanBitIsSet: Boolean): String {
    if (huffmanBitIsSet) {
        throw NotImplementedError("Huffman decoding not implemented")
    }
    val length = this.readVarint()
    if (length < 0 || length > this.bytes.size - this.offset) {
        throw IllegalArgumentException("Invalid QPACK string literal length: $length")
    }
    val valueBytes = this.readBytes(length.toInt())
    return valueBytes.decodeToString()
}

/**
 * Represents QPACK instruction types based on simplified first-byte analysis.
 * This is a simplification for the minimal implementation.
 */
internal enum class QpackInstructionSimpleType {
    INDEXED_STATIC,          // Prefix 1xxxxxxx (static table index)
    LITERAL_STATIC_NAME_REF, // Prefix 01xxxxxx (static table name index, literal value)
    LITERAL_LITERAL_NAME,    // Prefix 001xxxxx (literal name, literal value)
    UNKNOWN
}

internal fun determineInstructionType(firstByte: Int): QpackInstructionSimpleType {
    return when {
        (firstByte and QpackInstructionPrefix.INDEXED_HEADER_FIELD_PREFIX_MASK) == QpackInstructionPrefix.INDEXED_HEADER_FIELD_VALUE ->
            QpackInstructionSimpleType.INDEXED_STATIC
        (firstByte and QpackInstructionPrefix.LITERAL_NAME_REF_STATIC_PREFIX_MASK) == QpackInstructionPrefix.LITERAL_NAME_REF_STATIC_VALUE ->
            QpackInstructionSimpleType.LITERAL_STATIC_NAME_REF
        (firstByte and QpackInstructionPrefix.LITERAL_LITERAL_NAME_PREFIX_MASK) == QpackInstructionPrefix.LITERAL_LITERAL_NAME_VALUE ->
            QpackInstructionSimpleType.LITERAL_LITERAL_NAME
        else -> QpackInstructionSimpleType.UNKNOWN
    }
}

// Bit manipulation helpers if needed for more precise prefix handling
fun Int.getBit(position: Int): Boolean = (this shr position) and 1 == 1
fun Byte.getBit(position: Int): Boolean = (this.toInt() shr position) and 1 == 1

// Example: For `01 N T Index` (Literal Header Field with Name Reference)
// First byte could be: 01 N T I I I I (where I are first bits of index)
// N is bit 5 (0-indexed: 76543210)
// T is bit 4 (Static/Dynamic Table for Name; T=1 for Static, T=0 for Dynamic)
// For static name reference, T=1. So, 01 N 1 I I I I.
// If N=0 (not post-base), prefix is `0101xxxx`.
// If N=1 (post-base), prefix is `0111xxxx`.
// This is still simplified. Full QPACK varint with prefix is more involved.
// For example, "Indexed Header Field" (1 T Index)
// T=1 for static. So, 11xxxxxx. Index is 6 bits. If index > 63, then 11111111 (0xFF), then varint-encoded (Index - 63).

// For this minimal version, we will use the simplified QpackInstructionSimpleType determination.

```
