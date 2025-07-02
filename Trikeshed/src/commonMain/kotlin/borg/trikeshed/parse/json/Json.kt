@file:Suppress("FunctionName")

package borg.trikeshed.parse.json

import borg.trikeshed.lib.*

// Type aliases for bbcursive sugar
typealias ByteIndexed = Join<Int, (Int) -> Byte>
typealias CharIndexed = Join<Int, (Int) -> Char>

/**
 * JSON parser using ByteIndexed and CharIndexed abstractions
 */
object Json {
    
    // Parse result class
    data class ParseResult(
        val success: Boolean,
        val position: Int,
        val remaining: Int
    )
    
    // Simple recursive descent parser for JSON
    private class JsonParser(private val input: ByteIndexed) {
        private var pos = 0
        
        private val hasRemaining: Boolean get() = pos < input.a
        private val current: Byte get() = if (hasRemaining) input.b(pos) else 0
        
        private fun advance(): Byte {
            val c = current
            pos++
            return c
        }
        
        private fun skipWhitespace() {
            while (hasRemaining && current.toInt().toChar().isWhitespace()) {
                advance()
            }
        }
        
        private fun expectChar(expected: Char): Boolean {
            skipWhitespace()
            if (hasRemaining && current == expected.code.toByte()) {
                advance()
                return true
            }
            return false
        }
        
        private fun parseString(): Boolean {
            if (!expectChar('"')) return false
            
            while (hasRemaining) {
                val c = advance()
                when (c.toInt().toChar()) {
                    '"' -> return true
                    '\\' -> {
                        if (!hasRemaining) return false
                        advance() // consume escaped char
                    }
                }
            }
            return false
        }
        
        private fun parseNumber(): Boolean {
            val start = pos
            
            // Optional minus
            if (hasRemaining && current == '-'.code.toByte()) {
                advance()
            }
            
            // At least one digit
            if (!hasRemaining || !current.toInt().toChar().isDigit()) {
                pos = start
                return false
            }
            advance()
            
            // More digits
            while (hasRemaining && current.toInt().toChar().isDigit()) {
                advance()
            }
            
            // Optional decimal part
            if (hasRemaining && current == '.'.code.toByte()) {
                advance()
                if (!hasRemaining || !current.toInt().toChar().isDigit()) {
                    pos = start
                    return false
                }
                advance()
                while (hasRemaining && current.toInt().toChar().isDigit()) {
                    advance()
                }
            }
            
            // Optional exponent
            if (hasRemaining && (current == 'e'.code.toByte() || current == 'E'.code.toByte())) {
                advance()
                if (hasRemaining && (current == '+'.code.toByte() || current == '-'.code.toByte())) {
                    advance()
                }
                if (!hasRemaining || !current.toInt().toChar().isDigit()) {
                    pos = start
                    return false
                }
                advance()
                while (hasRemaining && current.toInt().toChar().isDigit()) {
                    advance()
                }
            }
            
            return true
        }
        
        private fun parseKeyword(keyword: String): Boolean {
            val start = pos
            for (c in keyword) {
                if (!hasRemaining || current != c.code.toByte()) {
                    pos = start
                    return false
                }
                advance()
            }
            return true
        }
        
        private fun parseValue(): Boolean {
            skipWhitespace()
            return parseString() || parseNumber() || 
                   parseKeyword("true") || parseKeyword("false") || parseKeyword("null") ||
                   parseArray() || parseObject()
        }
        
        private fun parseArray(): Boolean {
            if (!expectChar('[')) return false
            
            skipWhitespace()
            if (expectChar(']')) return true // empty array
            
            do {
                if (!parseValue()) return false
                skipWhitespace()
            } while (expectChar(','))
            
            return expectChar(']')
        }
        
        private fun parseObject(): Boolean {
            if (!expectChar('{')) return false
            
            skipWhitespace()
            if (expectChar('}')) return true // empty object
            
            do {
                if (!parseString()) return false // key
                if (!expectChar(':')) return false
                if (!parseValue()) return false // value
                skipWhitespace()
            } while (expectChar(','))
            
            return expectChar('}')
        }
        
        fun parse(): ParseResult {
            val success = parseValue()
            skipWhitespace()
            val fullyParsed = success && pos >= input.a
            return ParseResult(
                success = fullyParsed,
                position = pos,
                remaining = maxOf(0, input.a - pos)
            )
        }
    }
    
    // Parse function that takes a String and returns success/failure
    fun parse(jsonString: String): Boolean {
        val byteArray = jsonString.encodeToByteArray()
        val bytes = byteArray.size j { i: Int -> byteArray[i] }
        return JsonParser(bytes).parse().success
    }
    
    // Parse with position tracking
    fun parseWithPosition(jsonString: String): ParseResult {
        val byteArray = jsonString.encodeToByteArray()
        val bytes = byteArray.size j { i: Int -> byteArray[i] }
        return JsonParser(bytes).parse()
    }
    
    // Parse ByteIndexed directly
    fun parse(bytes: ByteIndexed): Boolean {
        return JsonParser(bytes).parse().success
    }
    
    // Parse CharIndexed for text-based JSON
    fun parse(chars: CharIndexed): Boolean {
        val byteArray = (0 until chars.a).map { chars.b(it).code.toByte() }.toByteArray()
        val bytes = byteArray.size j { i: Int -> byteArray[i] }
        return parse(bytes)
    }
}