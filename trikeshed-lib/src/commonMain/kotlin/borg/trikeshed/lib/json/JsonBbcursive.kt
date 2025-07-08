package borg.trikeshed.lib.json

import borg.trikeshed.lib.ByteIndexedBuffer
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.parse.bbcursive.ann.Backtracking
import borg.trikeshed.parse.bbcursive.ann.Skipper

// bbcursive-style UnaryOperator for ByteIndexedBuffer, now returning a value
fun interface BbcursiveOp<T> {
    fun apply(buffer: ByteIndexedBuffer): Pair<ByteIndexedBuffer?, T?>
}

// Combinator functions for sequencing parsers
fun <A, B> bb(
    buffer: ByteIndexedBuffer,
    op1: BbcursiveOp<A>,
    op2: BbcursiveOp<B>
): Pair<ByteIndexedBuffer?, Pair<A?, B?>?> {
    val (b1, r1) = op1.apply(buffer)
    b1 ?: return null to null
    val (b2, r2) = op2.apply(b1)
    b2 ?: return null to null
    return b2 to (r1 to r2)
}

fun <A, B, C> bb(
    buffer: ByteIndexedBuffer,
    op1: BbcursiveOp<A>,
    op2: BbcursiveOp<B>,
    op3: BbcursiveOp<C>
): Pair<ByteIndexedBuffer?, Triple<A?, B?, C?>?> {
    val (b1, r1) = op1.apply(buffer)
    b1 ?: return null to null
    val (b2, r2) = op2.apply(b1)
    b2 ?: return null to null
    val (b3, r3) = op3.apply(b2)
    b3 ?: return null to null
    return b3 to Triple(r1, r2, r3)
}

/**
 * JSON parser using bbcursive patterns with ByteIndexedBuffer
 * Side-by-side comparison with previous CharSeries approach
 */
object JsonBbcursive {

    // Basic token parsers
    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun ws(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        buffer.skipWs to Unit
    }

    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun quote(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '"'.code.toByte()) buffer to Unit else null to null
    }

    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun comma(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == ','.code.toByte()) buffer to Unit else null to null
    }

    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun colon(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == ':'.code.toByte()) buffer to Unit else null to null
    }

    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun lbrace(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '{'.code.toByte()) buffer to Unit else null to null
    }

    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun rbrace(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '}'.code.toByte()) buffer to Unit else null to null
    }

    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun lbracket(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '['.code.toByte()) buffer to Unit else null to null
    }

    @Backtracking
    @Suppress("NOTHING_TO_INLINE")
    inline fun rbracket(): BbcursiveOp<Unit> = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == ']'.code.toByte()) buffer to Unit else null to null
    }

    // String parsing using bbcursive patterns
    @Backtracking
    fun string(): BbcursiveOp<JsonValue.JsonString> = BbcursiveOp { buffer ->
        val startPos = buffer.pos
        
        // Check for opening quote
        if (!buffer.hasRemaining || buffer.peek != '"'.code.toByte()) {
            return@BbcursiveOp null to null
        }
        buffer.get // consume opening quote
        
        val stringStart = buffer.pos
        val bytes = mutableListOf<Byte>()
        
        while (buffer.hasRemaining) {
            val c = buffer.get
            when (c) {
                '"'.code.toByte() -> {
                    // Found closing quote
                    val extractedString = bytes.toByteArray().decodeToString()
                    return@BbcursiveOp buffer to JsonValue.JsonString(extractedString)
                }
                '\\'.code.toByte() -> {
                    // Handle escape sequence
                    if (buffer.hasRemaining) {
                        val escaped = buffer.get
                        val actual = when (escaped) {
                            'n'.code.toByte() -> '\n'.code.toByte()
                            't'.code.toByte() -> '\t'.code.toByte()
                            'r'.code.toByte() -> '\r'.code.toByte()
                            '"'.code.toByte() -> '"'.code.toByte()
                            '\\'.code.toByte() -> '\\'.code.toByte()
                            '/'.code.toByte() -> '/'.code.toByte()
                            'b'.code.toByte() -> '\b'.code.toByte()
                            'f'.code.toByte() -> '\u000C'.code.toByte()
                            else -> escaped
                        }
                        bytes.add(actual)
                    }
                }
                else -> bytes.add(c)
            }
        }
        
        // No closing quote found
        buffer.pos(startPos)
        null to null
    }

    // Number parsing
    @Backtracking
    fun number(): BbcursiveOp<JsonValue.JsonNumber> = BbcursiveOp { buffer ->
        val startPos = buffer.pos
        val bytes = mutableListOf<Byte>()
        
        // Optional minus
        if (buffer.hasRemaining && buffer.peek == '-'.code.toByte()) {
            bytes.add(buffer.get)
        }
        
        // At least one digit required
        if (!buffer.hasRemaining || !buffer.peek.toInt().toChar().isDigit()) {
            buffer.pos(startPos)
            return@BbcursiveOp null to null
        }
        
        // Integer part
        while (buffer.hasRemaining && buffer.peek.toInt().toChar().isDigit()) {
            bytes.add(buffer.get)
        }
        
        // Optional decimal part
        if (buffer.hasRemaining && buffer.peek == '.'.code.toByte()) {
            bytes.add(buffer.get)
            
            // At least one digit after decimal
            if (!buffer.hasRemaining || !buffer.peek.toInt().toChar().isDigit()) {
                buffer.pos(startPos)
                return@BbcursiveOp null to null
            }
            
            while (buffer.hasRemaining && buffer.peek.toInt().toChar().isDigit()) {
                bytes.add(buffer.get)
            }
        }
        
        // Optional exponent
        if (buffer.hasRemaining && (buffer.peek == 'e'.code.toByte() || buffer.peek == 'E'.code.toByte())) {
            bytes.add(buffer.get)
            
            // Optional sign
            if (buffer.hasRemaining && (buffer.peek == '+'.code.toByte() || buffer.peek == '-'.code.toByte())) {
                bytes.add(buffer.get)
            }
            
            // At least one digit required
            if (!buffer.hasRemaining || !buffer.peek.toInt().toChar().isDigit()) {
                buffer.pos(startPos)
                return@BbcursiveOp null to null
            }
            
            while (buffer.hasRemaining && buffer.peek.toInt().toChar().isDigit()) {
                bytes.add(buffer.get)
            }
        }
        
        val numberString = bytes.toByteArray().decodeToString()
        val value = numberString.toDoubleOrNull()
        if (value == null) {
            buffer.pos(startPos)
            null to null
        } else {
            buffer to JsonValue.JsonNumber(value)
        }
    }

    // String literal matcher
    fun <T> strlit(literal: String, value: T): BbcursiveOp<T> = BbcursiveOp { buffer ->
        val bytes = literal.encodeToByteArray()
        val saved = buffer.pos
        
        for (b in bytes) {
            if (!buffer.hasRemaining || buffer.get != b) {
                buffer.pos(saved)
                return@BbcursiveOp null to null
            }
        }
        
        buffer to value
    }

    // Boolean and null using bbcursive strlit
    @Backtracking
    fun true_(): BbcursiveOp<JsonValue.JsonBoolean> = strlit("true", JsonValue.JsonBoolean(true))

    @Backtracking
    fun false_(): BbcursiveOp<JsonValue.JsonBoolean> = strlit("false", JsonValue.JsonBoolean(false))

    @Backtracking
    fun null_(): BbcursiveOp<JsonValue.JsonNull> = strlit("null", JsonValue.JsonNull)

    // Forward declarations for recursive structures
    internal lateinit var _value: BbcursiveOp<JsonValue>
    internal lateinit var _array: BbcursiveOp<JsonValue.JsonArray>
    internal lateinit var _object: BbcursiveOp<JsonValue.JsonObject>

    // Value (recursive) - bbcursive style with alternatives
    @Backtracking
    fun value(): BbcursiveOp<JsonValue> = BbcursiveOp { buffer ->
        string().apply(buffer) as Pair<ByteIndexedBuffer?, JsonValue?>?
            ?: number().apply(buffer) as Pair<ByteIndexedBuffer?, JsonValue?>?
            ?: true_().apply(buffer) as Pair<ByteIndexedBuffer?, JsonValue?>?
            ?: false_().apply(buffer) as Pair<ByteIndexedBuffer?, JsonValue?>?
            ?: null_().apply(buffer) as Pair<ByteIndexedBuffer?, JsonValue?>?
            ?: _array.apply(buffer) as Pair<ByteIndexedBuffer?, JsonValue?>?
            ?: _object.apply(buffer) as Pair<ByteIndexedBuffer?, JsonValue?>?
            ?: (null to null)
    }

    // Array elements using bbcursive bb() sequencing
    @Backtracking
    fun arrayElements(): BbcursiveOp<Indexed<JsonValue>> = BbcursiveOp { buffer ->
        val elements = mutableListOf<JsonValue>()
        var b = buffer

        val result1 = bb(b, ws(), value(), ws())
        result1.first ?: return@BbcursiveOp null to null
        val v1 = result1.second?.second
        v1 ?: return@BbcursiveOp null to null
        elements.add(v1)
        b = result1.first!!

        while (true) {
            val saved = b.pos
            val resultNext = bb(b, comma(), ws(), value())
            if (resultNext.first == null) {
                b.pos(saved)
                break
            }
            val vNext = (resultNext.second as? Triple<*, *, *>)?.third
            vNext ?: break
            elements.add(vNext as JsonValue)
            b = resultNext.first!!
        }
        b to (elements.size j { i -> elements[i] })
    }

    // Array using bbcursive bb() composition
    @Backtracking
    fun array(): BbcursiveOp<JsonValue.JsonArray> = BbcursiveOp { buffer ->
        var b = buffer

        val result1 = lbracket().apply(b)
        result1.first ?: return@BbcursiveOp null to null
        b = result1.first!!
        
        val wsResult = ws().apply(b)
        b = wsResult.first ?: b

        // Try empty array
        val saved = b.pos
        val emptyResult = rbracket().apply(b)
        if (emptyResult.first != null) {
            return@BbcursiveOp emptyResult.first to JsonValue.JsonArray(0 j { JsonValue.JsonNull })
        }

        b.pos(saved)

        // Non-empty array
        val (bElements, elements) = arrayElements().apply(b)
        bElements ?: return@BbcursiveOp null to null
        b = bElements

        val endResult = rbracket().apply(b)
        endResult.first ?: return@BbcursiveOp null to null

        endResult.first to JsonValue.JsonArray(elements!!)
    }

    // Object member using bbcursive bb() sequencing
    @Backtracking
    fun member(): BbcursiveOp<Pair<String, JsonValue>> = BbcursiveOp { buffer ->
        val result1 = bb(buffer, ws(), string(), ws())
        result1.first ?: return@BbcursiveOp null to null
        val key = result1.second?.second as? JsonValue.JsonString
        key ?: return@BbcursiveOp null to null
        val b1 = result1.first!!

        val (b2, _) = bb(b1, colon(), ws())
        b2 ?: return@BbcursiveOp null to null

        val result3 = bb(b2, value(), ws())
        result3.first ?: return@BbcursiveOp null to null
        val value = result3.second?.first
        value ?: return@BbcursiveOp null to null
        val b3 = result3.first!!

        b3 to (key.value to value)
    }

    // Object members
    @Backtracking
    fun members(): BbcursiveOp<Indexed<Join<String, JsonValue>>> = BbcursiveOp { buffer ->
        val members = mutableListOf<Join<String, JsonValue>>()
        var b = buffer

        val (b1, member) = member().apply(b)
        b1 ?: return@BbcursiveOp null to null
        members.add(member!!.first j member.second)
        b = b1

        while (true) {
            val saved = b.pos
            val result = bb(b, comma(), ws())
            if (result.first == null) {
                b.pos(saved)
                break
            }
            b = result.first!!
            
            val memberResult = member().apply(b)
            if (memberResult.first == null) break
            b = memberResult.first!!
            members.add(memberResult.second!!.first j memberResult.second!!.second)
        }
        b to (members.size j { i -> members[i] })
    }

    // Object using bbcursive bb() composition
    @Backtracking
    fun object_(): BbcursiveOp<JsonValue.JsonObject> = BbcursiveOp { buffer ->
        var b = buffer

        val result1 = lbrace().apply(b)
        result1.first ?: return@BbcursiveOp null to null
        b = result1.first!!
        
        val wsResult = ws().apply(b)
        b = wsResult.first ?: b

        // Try empty object
        val saved = b.pos
        val emptyResult = rbrace().apply(b)
        if (emptyResult.first != null) {
            return@BbcursiveOp emptyResult.first to JsonValue.JsonObject(0 j { "" j JsonValue.JsonNull })
        }

        b.pos(saved)

        // Non-empty object
        val (bMembers, members) = members().apply(b)
        bMembers ?: return@BbcursiveOp null to null
        b = bMembers

        val endResult = rbrace().apply(b)
        endResult.first ?: return@BbcursiveOp null to null

        endResult.first to JsonValue.JsonObject(members!!)
    }

    // Initialize recursive references
    init {
        _value = value()
        _array = array()
        _object = object_()
    }

    // Parse function for external use
    fun parse(input: String): JsonValue? {
        val buffer = ByteIndexedBuffer(input)
        val (b1, _) = ws().apply(buffer)
        val (b2, result) = value().apply(b1 ?: buffer)
        b2 ?: return null
        val (b3, _) = ws().apply(b2)
        // Ensure we consumed all input
        return if (b3 != null && !b3.hasRemaining) result else null
    }
}