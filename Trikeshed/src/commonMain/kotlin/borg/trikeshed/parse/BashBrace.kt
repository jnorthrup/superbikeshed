package borg.trikeshed.parse

value class Token(val type: TokenType, val literal: String, val pos: Int)

enum class TokenType {
    EOF,
    ERROR,
    LITERAL,
    LBRACE,
    RBRACE,
    COMMA,
    DOT,
    SEQUENCE
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
            if (r == '{' || r == '}' || r == ',' || r == '.' || r.isWhitespace()) {
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
            if (!r.isWhitespace()) {
                backup()
                break
            }
        }
    }
}

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

value class ParseResult<out T>(
    val value: T,
    val remaining: List<Token>
)

value class Parser<T>(val parse: (List<Token>) -> ParseResult<T>?) {
    companion object {
        fun <T> pure(value: T): Parser<T> = Parser { tokens -> ParseResult(value, tokens) }
        fun <T> fail(): Parser<T> = Parser { null }
        fun token(type: TokenType): Parser<Token> = Parser { tokens ->
            if (tokens.isEmpty()) null
            else {
                val token = tokens.first()
                if (token.type == type) ParseResult(token, tokens.drop(1))
                else null
            }
        }
        fun <T> memoize(parser: Parser<T>): Parser<T> {
            val cache = mutableMapOf<List<Token>, ParseResult<T>?>()
            return Parser { tokens ->
                cache.getOrPut(tokens) { parser.parse(tokens) }
            }
        }
    }
    fun <R> map(transform: (T) -> R): Parser<R> = Parser { tokens ->
        parse(tokens)?.let { result ->
            ParseResult(transform(result.value), result.remaining)
        }
    }
    fun <R> flatMap(transform: (T) -> Parser<R>): Parser<R> = Parser { tokens ->
        parse(tokens)?.let { result ->
            transform(result.value).parse(result.remaining)
        }
    }
    fun or(other: Parser<T>): Parser<T> = Parser { tokens ->
        parse(tokens) ?: other.parse(tokens)
    }
    fun many(): Parser<List<T>> = Parser { tokens ->
        val results = mutableListOf<T>()
        var remaining = tokens
        while (true) {
            val result = parse(remaining) ?: break
            results.add(result.value)
            remaining = result.remaining
        }
        ParseResult(results, remaining)
    }
    fun optional(): Parser<T?> = Parser { tokens ->
        parse(tokens) ?: ParseResult(null, tokens)
    }
}

object BashParsers {
    val lbrace: Parser<Token> = Parser.token(TokenType.LBRACE)
    val rbrace: Parser<Token> = Parser.token(TokenType.RBRACE)
    val comma: Parser<Token> = Parser.token(TokenType.COMMA)
    val sequence: Parser<Token> = Parser.token(TokenType.SEQUENCE)
    val literal: Parser<String> = Parser { tokens ->
        if (tokens.isEmpty()) null
        else {
            val token = tokens.first()
            if (token.type == TokenType.LITERAL) ParseResult(token.literal, tokens.drop(1))
            else null
        }
    }
    val number: Parser<Int> = literal.map { it.toIntOrNull() ?: throw IllegalArgumentException("Not a number: $it") }
    val char: Parser<Char> = literal.map {
        if (it.length == 1) it[0] else throw IllegalArgumentException("Not a single character: $it")
    }
    val sequenceExpr: Parser<List<String>> = Parser.memoize(
        lbrace.flatMap { _ ->
            (number.map { it.toString() }.or(char.map { it.toString() })).flatMap { start ->
                sequence.flatMap { _ ->
                    (number.map { it.toString() }.or(char.map { it.toString() })).flatMap { end ->
                        rbrace.map { _ ->
                            when {
                                start.all { it.isDigit() } && end.all { it.isDigit() } -> {
                                    val startNum = start.toInt()
                                    val endNum = end.toInt()
                                    val step = if (startNum <= endNum) 1 else -1
                                    (startNum..endNum step step).map { it.toString() }
                                }
                                start.length == 1 && end.length == 1 -> {
                                    val startChar = start[0]
                                    val endChar = end[0]
                                    val step = if (startChar <= endChar) 1 else -1
                                    (startChar..endChar step step).map { it.toString() }
                                }
                                else -> throw IllegalArgumentException("Invalid sequence: $start..$end")
                            }
                        }
                    }
                }
            }
        }
    )
    val listExpr: Parser<List<String>> = Parser.memoize(
        lbrace.flatMap { _ ->
            literal.flatMap { first ->
                (comma.flatMap { _ ->
                    literal
                }).many().flatMap { rest ->
                    rbrace.map { _ ->
                        listOf(first) + rest
                    }
                }
            }
        }
    )
    val braceExpr: Parser<List<String>> = Parser.memoize(
        sequenceExpr.or(listExpr)
    )
    val prefix: Parser<String> = literal.optional().map { it ?: "" }
    val suffix: Parser<String> = literal.optional().map { it ?: "" }
    val fullExpr: Parser<List<String>> = Parser.memoize(
        prefix.flatMap { pre ->
            braceExpr.flatMap { expr ->
                suffix.map { suf ->
                    expr.map { pre + it + suf }
                }
            }
        }
    )
}

@JvmInline
value class BashBraceParser(val input: String) {
    fun parse(): List<String> {
        val tokens = BashBrace.of(input).scanTokens()
        return BashParsers.fullExpr.parse(tokens)?.value
            ?: throw IllegalArgumentException("Failed to parse: $input")
    }
    companion object {
        fun of(input: String): BashBraceParser = BashBraceParser(input)
    }
} 