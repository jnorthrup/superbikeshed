package borg.trikeshed.parse

data class Token(val type: TokenType, val literal: String, val pos: Int)

enum class TokenType {
    EOF,
    ERROR,
    LITERAL,
    LBRACE,
    RBRACE,
    COMMA,
    DOT,
    SEQUENCE,
}

class BashScanner(val input: String) {
    private var pos: Int = 0
    private var start: Int = 0
    private var width: Int = 0

    fun scan(): Token {
        start = pos
        skipWhitespace()

        if (pos >= input.length) {
            return Token(TokenType.EOF, "", pos)
        }

        val r = next()
        return when (r) {
            '{' -> Token(TokenType.LBRACE, "{", start)
            '}' -> Token(TokenType.RBRACE, "}", start)
            ',' -> Token(TokenType.COMMA, ",", start)
            '.' -> {
                if (peek() == '.') {
                    next() // consume second dot
                    Token(TokenType.SEQUENCE, "..", start)
                } else {
                    Token(TokenType.DOT, ".", start)
                }
            }
            '\\' -> {
                if (pos < input.length) {
                    val escaped = next()
                    Token(TokenType.LITERAL, escaped.toString(), start)
                } else {
                    Token(TokenType.ERROR, "unexpected end of input after escape", start)
                }
            }
            else -> {
                backup()
                scanLiteral()
            }
        }
    }

    private fun scanLiteral(): Token {
        while (true) {
            val r = next()
            if (r == Char.MIN_VALUE || r == '{' || r == '}' || r == ',' || r == '.' || r.isWhitespace()) {
                backup()
                break
            }
        }
        return Token(TokenType.LITERAL, input.substring(start, pos), start)
    }

    private fun next(): Char {
        if (pos >= input.length) {
            width = 0
            return Char.MIN_VALUE
        }
        val r = input[pos]
        width = 1
        pos++
        return r
    }

    private fun backup() {
        pos -= width
        width = 0
    }

    private fun peek(): Char {
        val r = next()
        backup()
        return r
    }

    private fun skipWhitespace() {
        while (true) {
            val r = next()
            if (r == Char.MIN_VALUE || !r.isWhitespace()) {
                backup()
                break
            }
        }
    }
}

@kotlin.jvm.JvmInline
value class BashBrace(val scanner: BashScanner) {
    fun scanTokens(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = scanner.scan()
            tokens.add(token)
            if (token.type == TokenType.EOF || token.type == TokenType.ERROR) {
                break
            }
        }
        return tokens
    }

    companion object {
        fun of(input: String): BashBrace = BashBrace(BashScanner(input))
    }
}
