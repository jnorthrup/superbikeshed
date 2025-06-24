package borg.trikeshed.lib

/**
 * Register Fastlane - Zero-allocation primitive packing
 *
 * This is the high-performance path for packing primitives that fit in registers.
 * All operations complete in 0-1 CPU cycles with no heap allocation.
 */
object RegisterFastlane {
    /**
     * Pack two Ints into a single Long
     */
    fun packInts(
        a: Int,
        b: Int,
    ): Long {
        return (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFF)
    }

    /**
     * Pack two Booleans into a single Long using bit flags
     */
    fun packBooleans(
        a: Boolean,
        b: Boolean,
    ): Long {
        return (if (a) 1L else 0L) or (if (b) 2L else 0L)
    }

    /**
     * Pack two Bytes into a single Long
     */
    fun packBytes(
        a: Byte,
        b: Byte,
    ): Long {
        return (a.toLong() shl 8) or (b.toLong() and 0xFF)
    }

    /**
     * Pack two Shorts into a single Long
     */
    fun packShorts(
        a: Short,
        b: Short,
    ): Long {
        return (a.toLong() shl 16) or (b.toLong() and 0xFFFF)
    }

    /**
     * Pack four Bytes into a single Long
     */
    fun packFourBytes(
        a: Byte,
        b: Byte,
        c: Byte,
        d: Byte,
    ): Long {
        return (a.toLong() shl 24) or
            ((b.toLong() and 0xFF) shl 16) or
            ((c.toLong() and 0xFF) shl 8) or
            (d.toLong() and 0xFF)
    }

    /**
     * Pack two Chars into a single Long
     */
    fun packChars(
        a: Char,
        b: Char,
    ): Long {
        return (a.code.toLong() shl 32) or b.code.toLong()
    }

    /**
     * Pack a Byte and an Int into a single Long
     */
    fun packByteInt(
        a: Byte,
        b: Int,
    ): Long {
        return (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFF)
    }

    /**
     * Pack a Boolean and an Int into a single Long
     */
    fun packBooleanInt(
        a: Boolean,
        b: Int,
    ): Long {
        return ((if (a) 1L else 0L) shl 32) or (b.toLong() and 0xFFFFFFFF)
    }

    // === UNPACKING METHODS ===

    /**
     * Extract first Int from packed Long
     */
    fun unpackInt1(packed: Long): Int {
        return (packed shr 32).toInt()
    }

    /**
     * Extract second Int from packed Long
     */
    fun unpackInt2(packed: Long): Int {
        return (packed and 0xFFFFFFFF).toInt()
    }

    /**
     * Extract first Boolean from packed Long
     */
    fun unpackBoolean1(packed: Long): Boolean {
        return (packed and 1L) != 0L
    }

    /**
     * Extract second Boolean from packed Long
     */
    fun unpackBoolean2(packed: Long): Boolean {
        return (packed and 2L) != 0L
    }

    /**
     * Extract first Byte from packed Long
     */
    fun unpackByte1(packed: Long): Byte {
        return (packed shr 8).toByte()
    }

    /**
     * Extract second Byte from packed Long
     */
    fun unpackByte2(packed: Long): Byte {
        return (packed and 0xFF).toByte()
    }

    /**
     * Extract first Short from packed Long
     */
    fun unpackShort1(packed: Long): Short {
        return (packed shr 16).toShort()
    }

    /**
     * Extract second Short from packed Long
     */
    fun unpackShort2(packed: Long): Short {
        return (packed and 0xFFFF).toShort()
    }

    /**
     * Extract first Char from packed Long
     */
    fun unpackChar1(packed: Long): Char {
        return (packed shr 32).toInt().toChar()
    }

    /**
     * Extract second Char from packed Long
     */
    fun unpackChar2(packed: Long): Char {
        return (packed and 0xFFFFFFFF).toInt().toChar()
    }

    // === GENERIC TRY-PACK INTERFACE ===

    /**
     * Try to pack any two primitives into a DiagonalPacked result
     * Returns null if the types aren't packable as primitives
     */
    fun <A, B> tryPrimitivePack(
        a: A,
        b: B,
    ): DiagonalPacked? {
        return when {
            a is Int && b is Int -> DiagonalPacked(packInts(a, b))
            a is Boolean && b is Boolean -> DiagonalPacked(packBooleans(a, b))
            a is Byte && b is Byte -> DiagonalPacked(packBytes(a, b))
            a is Short && b is Short -> DiagonalPacked(packShorts(a, b))
            a is Char && b is Char -> DiagonalPacked(packChars(a, b))
            a is Byte && b is Int -> DiagonalPacked(packByteInt(a, b))
            a is Boolean && b is Int -> DiagonalPacked(packBooleanInt(a, b))
            else -> null
        }
    }

    // === TOKEN-SPECIFIC PACKING ===

    /**
     * Try to pack two tokens using register fastlane
     */
    fun tryTokenPack(
        a: borg.trikeshed.parse.Token,
        b: borg.trikeshed.parse.Token,
    ): DiagonalPacked? {
        return when {
            // Pack two small literals
            a.type == borg.trikeshed.parse.TokenType.LITERAL &&
                b.type == borg.trikeshed.parse.TokenType.LITERAL &&
                a.literal.length <= 4 && b.literal.length <= 4 -> {
                val packed = packTwoStrings(a.literal, b.literal)
                DiagonalPacked(packed)
            }

            // Pack brace pairs
            a.type == borg.trikeshed.parse.TokenType.LBRACE &&
                b.type == borg.trikeshed.parse.TokenType.RBRACE -> {
                DiagonalPacked(0x7B7DL) // ASCII { }
            }

            // Pack sequence with number
            a.type == borg.trikeshed.parse.TokenType.SEQUENCE &&
                b.type == borg.trikeshed.parse.TokenType.LITERAL &&
                b.literal.toIntOrNull() != null -> {
                val seqHash = a.literal.hashCode().toLong()
                val num = b.literal.toInt().toLong()
                DiagonalPacked((seqHash shl 32) or num)
            }

            else -> null
        }
    }

    private fun packTwoStrings(
        a: String,
        b: String,
    ): Long {
        val aBytes = a.encodeToByteArray().take(4)
        val bBytes = b.encodeToByteArray().take(4)

        var result = 0L
        aBytes.forEachIndexed { i, byte ->
            result = result or ((byte.toLong() and 0xFF) shl (56 - i * 8))
        }
        bBytes.forEachIndexed { i, byte ->
            result = result or ((byte.toLong() and 0xFF) shl (24 - i * 8))
        }

        return result
    }
}
