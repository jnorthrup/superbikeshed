@file:Suppress("FunctionName")

package borg.trikeshed.parse.json

import borg.trikeshed.lib.*
import borg.trikeshed.parse.bbcursive.ann.Backtracking
import borg.trikeshed.parse.bbcursive.ann.Skipper

// bbcursive-style UnaryOperator for ByteIndexedBuffer
fun interface BbcursiveOp { fun apply(buffer: ByteIndexedBuffer): ByteIndexedBuffer? }

/**
 * JSON parser using bbcursive patterns with ByteIndexedBuffer
 * Side-by-side comparison with previous CharSeries approach
 */
object JsonBbcursive {
    
    // Core JSON tokens using bbcursive patterns
    @Skipper
    fun ws(): BbcursiveOp = BbcursiveOp { buffer ->
        buffer.skipWs
    }
    
    @Backtracking
    fun quote(): BbcursiveOp = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '"'.code.toByte()) buffer else null
    }
    
    @Backtracking 
    fun comma(): BbcursiveOp = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == ','.code.toByte()) buffer else null
    }
    
    @Backtracking
    fun colon(): BbcursiveOp = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == ':'.code.toByte()) buffer else null
    }
    
    @Backtracking
    fun lbrace(): BbcursiveOp = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '{'.code.toByte()) buffer else null
    }
    
    @Backtracking
    fun rbrace(): BbcursiveOp = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '}'.code.toByte()) buffer else null
    }
    
    @Backtracking
    fun lbracket(): BbcursiveOp = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == '['.code.toByte()) buffer else null
    }
    
    @Backtracking
    fun rbracket(): BbcursiveOp = BbcursiveOp { buffer ->
        if (buffer.hasRemaining && buffer.get == ']'.code.toByte()) buffer else null
    }

    // bbcursive-style sequencing function (like bb() from std.kt)
    fun bb(buffer: ByteIndexedBuffer?, vararg ops: BbcursiveOp): ByteIndexedBuffer? {
        var result = buffer
        for (op in ops) {
            result = op.apply(result ?: return null) ?: return null
        }
        return result
    }
    
    // String literal matching (like strlit from bbcursive)
    fun strlit(literal: String): BbcursiveOp = BbcursiveOp { buffer ->
        val bytes = literal.encodeToByteArray()
        val startPos = buffer.pos
        
        for (expectedByte in bytes) {
            if (!buffer.hasRemaining || buffer.get != expectedByte) {
                buffer.pos(startPos) // backtrack
                return@BbcursiveOp null
            }
        }
        buffer
    }

    // String parsing using bbcursive patterns
    @Backtracking
    fun stringChar(): BbcursiveOp = BbcursiveOp { buffer ->
        if (!buffer.hasRemaining) return@BbcursiveOp null
        val c = buffer.get
        when (c) {
            '"'.code.toByte() -> {
                buffer.dec()
                null
            }
            '\\'.code.toByte() -> {
                if (!buffer.hasRemaining) return@BbcursiveOp null
                buffer.get // consume escaped char
                buffer
            }
            else -> buffer
        }
    }
    
    @Backtracking
    fun string(): BbcursiveOp = BbcursiveOp { buffer ->
        bb(buffer, quote()) ?: return@BbcursiveOp null
        var b = buffer
        while (b.hasRemaining) {
            b = stringChar().apply(b) ?: break
        }
        bb(b, quote())
    }
    
    // Number parsing
    @Backtracking
    fun digit(): BbcursiveOp = BbcursiveOp { buffer ->
        if (!buffer.hasRemaining) return@BbcursiveOp null
        val c = buffer.get
        if (c in '0'.code.toByte()..'9'.code.toByte()) buffer else {
            buffer.dec()
            null
        }
    }
    
    @Backtracking
    fun number(): BbcursiveOp = BbcursiveOp { buffer ->
        var b = buffer
        
        // Optional minus
        if (b.hasRemaining && b.get == '-'.code.toByte()) {
            // consumed minus
        } else {
            b.dec()
        }
        
        // At least one digit
        b = digit().apply(b) ?: return@BbcursiveOp null
        
        // More digits
        while (b.hasRemaining) {
            val saved = b.pos
            b = digit().apply(b) ?: run {
                b.pos(saved)
                break
            }
        }
        
        // Optional decimal part
        if (b.hasRemaining && b.get == '.'.code.toByte()) {
            b = digit().apply(b) ?: return@BbcursiveOp null
            while (b.hasRemaining) {
                val saved = b.pos
                b = digit().apply(b) ?: run {
                    b.pos(saved)
                    break
                }
            }
        } else {
            b.dec()
        }
        
        // Optional exponent
        if (b.hasRemaining) {
            val c = b.get
            if (c == 'e'.code.toByte() || c == 'E'.code.toByte()) {
                if (b.hasRemaining) {
                    val sign = b.get
                    if (sign != '+'.code.toByte() && sign != '-'.code.toByte()) {
                        b.dec()
                    }
                }
                b = digit().apply(b) ?: return@BbcursiveOp null
                while (b.hasRemaining) {
                    val saved = b.pos
                    b = digit().apply(b) ?: run {
                        b.pos(saved)
                        break
                    }
                }
            } else {
                b.dec()
            }
        }
        
        b
    }
    
    // Boolean and null using bbcursive strlit
    @Backtracking
    fun true_(): BbcursiveOp = strlit("true")
    
    @Backtracking
    fun false_(): BbcursiveOp = strlit("false")
    
    @Backtracking
    fun null_(): BbcursiveOp = strlit("null")
    
    // Forward declarations for recursive structures
    private lateinit var _value: BbcursiveOp
    private lateinit var _array: BbcursiveOp
    private lateinit var _object: BbcursiveOp
    
    // Value (recursive) - bbcursive style with alternatives
    @Backtracking
    fun value(): BbcursiveOp = BbcursiveOp { buffer ->
        string().apply(buffer) 
            ?: number().apply(buffer)
            ?: true_().apply(buffer)
            ?: false_().apply(buffer)
            ?: null_().apply(buffer)
            ?: _array.apply(buffer)
            ?: _object.apply(buffer)
    }
    
    // Array elements using bbcursive bb() sequencing
    @Backtracking
    fun arrayElements(): BbcursiveOp = BbcursiveOp { buffer ->
        var b = bb(buffer, ws(), value(), ws()) ?: return@BbcursiveOp null
        
        while (true) {
            val saved = b.pos
            b = bb(b, comma(), ws(), value(), ws()) ?: run {
                b.pos(saved)
                break
            }
        }
        b
    }
    
    // Array using bbcursive bb() composition
    @Backtracking
    fun array(): BbcursiveOp = BbcursiveOp { buffer ->
        var b = bb(buffer, lbracket(), ws()) ?: return@BbcursiveOp null
        
        // Try empty array
        val saved = b.pos
        bb(b, rbracket())?.let { return@BbcursiveOp it }
        b.pos(saved)
        
        // Non-empty array
        b = arrayElements().apply(b) ?: return@BbcursiveOp null
        bb(b, rbracket())
    }
    
    // Object member using bbcursive bb() sequencing
    @Backtracking
    fun member(): BbcursiveOp = BbcursiveOp { buffer ->
        bb(buffer, ws(), string(), ws(), colon(), ws(), value(), ws())
    }
    
    // Object members 
    @Backtracking
    fun members(): BbcursiveOp = BbcursiveOp { buffer ->
        var b = member().apply(buffer) ?: return@BbcursiveOp null
        
        while (true) {
            val saved = b.pos
            b = bb(b, comma(), ws(), member()) ?: run {
                b.pos(saved)
                break
            }
        }
        b
    }
    
    // Object using bbcursive bb() composition
    @Backtracking
    fun object_(): BbcursiveOp = BbcursiveOp { buffer ->
        var b = bb(buffer, lbrace(), ws()) ?: return@BbcursiveOp null
        
        // Try empty object
        val saved = b.pos
        bb(b, rbrace())?.let { return@BbcursiveOp it }
        b.pos(saved)
        
        // Non-empty object
        b = members().apply(b) ?: return@BbcursiveOp null
        bb(b, rbrace())
    }
    
    // Initialize recursive references
    init {
        _value = value()
        _array = array()
        _object = object_()
    }
    
    // Main JSON parser using bbcursive bb() sequencing
    @Skipper
    fun json(): BbcursiveOp = BbcursiveOp { buffer ->
        bb(buffer, ws(), value(), ws())
    }
    
    // Parse function using ByteIndexedBuffer
    fun parse(jsonString: String): Boolean {
        val buffer = ByteIndexedBuffer(jsonString)
        val result = json().apply(buffer)
        return result != null && !result.hasRemaining
    }
    
    // Parse with position tracking
    fun parseWithPosition(jsonString: String): Json.ParseResult {
        val buffer = ByteIndexedBuffer(jsonString)
        val startPos = buffer.pos
        val result = json().apply(buffer)
        return Json.ParseResult(
            success = result != null && !result.hasRemaining,
            position = result?.pos ?: startPos,
            remaining = result?.rem ?: buffer.rem
        )
    }
}