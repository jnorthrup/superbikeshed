/**
 * Bash Brace Parser - Modernized
 *
 * Example usage:
 * ```kotlin
 * val result = BashBraceParser.of("file{1,2,3}.txt").parse()
 * println(result) // [file1.txt, file2.txt, file3.txt]
 * ```
 *
 * TODO: Add support for nested braces.
 */
package parse.bash

/**
 * Result of parsing: either a value and remaining tokens, or an error.
 */
sealed class ParseResult<out T> {
    data class Success<T>(val value: T, val remaining: List<Token>) : ParseResult<T>()
    data class Failure(val error: ParseError) : ParseResult<Nothing>()
}

/**
 * Error type for parsing failures.
 */
sealed class ParseError(val message: String) {
    class UnexpectedToken(val token: Token) : ParseError("Unexpected token: $token")
    class UnexpectedEOF : ParseError("Unexpected end of input")
    class InvalidLiteral(val literal: String) : ParseError("Invalid literal: $literal")
    class InvalidSequence(val start: String, val end: String) : ParseError("Invalid sequence: $start..$end")
    class NotANumber(val value: String) : ParseError("Not a number: $value")
    class NotAChar(val value: String) : ParseError("Not a single character: $value")
    class Generic(message: String) : ParseError(message)
}

@JvmInline
value class Parser<T>(val parse: (List<Token>) -> ParseResult<T>) {
    companion object {
        fun <T> pure(value: T): Parser<T> = Parser { tokens -> ParseResult.Success(value, tokens) }
        fun <T> fail(error: ParseError): Parser<T> = Parser { ParseResult.Failure(error) }
        fun token(type: TokenType): Parser<Token> = Parser { tokens ->
            if (tokens.isEmpty()) ParseResult.Failure(ParseError.UnexpectedEOF())
            else {
                val token = tokens.first()
                if (token.type == type) ParseResult.Success(token, tokens.drop(1))
                else ParseResult.Failure(ParseError.UnexpectedToken(token))
            }
        }
        fun <T> memoize(parser: Parser<T>): Parser<T> {
            val cache = mutableMapOf<List<Token>, ParseResult<T>>()
            return Parser { tokens ->
                cache.getOrPut(tokens) { parser.parse(tokens) }
            }
        }
    }
    fun <R> map(transform: (T) -> R): Parser<R> = Parser { tokens ->
        when (val result = parse(tokens)) {
            is ParseResult.Success -> ParseResult.Success(transform(result.value), result.remaining)
            is ParseResult.Failure -> result
        }
    }
    fun <R> flatMap(transform: (T) -> Parser<R>): Parser<R> = Parser { tokens ->
        when (val result = parse(tokens)) {
            is ParseResult.Success -> transform(result.value).parse(result.remaining)
            is ParseResult.Failure -> result
        }
    }
    fun or(other: Parser<T>): Parser<T> = Parser { tokens ->
        when (val result = parse(tokens)) {
            is ParseResult.Success -> result
            is ParseResult.Failure -> other.parse(tokens)
        }
    }
    fun many(): Parser<List<T>> = Parser { tokens ->
        val results = mutableListOf<T>()
        var remaining = tokens
        while (true) {
            when (val result = parse(remaining)) {
                is ParseResult.Success -> {
                    results.add(result.value)
                    remaining = result.remaining
                }
                is ParseResult.Failure -> break
            }
        }
        ParseResult.Success(results, remaining)
    }
    fun optional(): Parser<T?> = Parser { tokens ->
        when (val result = parse(tokens)) {
            is ParseResult.Success -> ParseResult.Success(result.value, result.remaining)
            is ParseResult.Failure -> ParseResult.Success(null, tokens)
        }
    }
}

/**
 * Bash-specific parsers for brace expressions.
 */
object BashParsers {
    val lbrace: Parser<Token> = Parser.token(TokenType.LBRACE)
    val rbrace: Parser<Token> = Parser.token(TokenType.RBRACE)
    val comma: Parser<Token> = Parser.token(TokenType.COMMA)
    val sequence: Parser<Token> = Parser.token(TokenType.SEQUENCE)
    val literal: Parser<String> = Parser { tokens ->
        if (tokens.isEmpty()) ParseResult.Failure(ParseError.UnexpectedEOF())
        else {
            val token = tokens.first()
            if (token.type == TokenType.LITERAL) ParseResult.Success(token.literal, tokens.drop(1))
            else ParseResult.Failure(ParseError.UnexpectedToken(token))
        }
    }
    val number: Parser<Int> = literal.map {
        it.toIntOrNull() ?: throw ParseError.NotANumber(it)
    }
    val char: Parser<Char> = literal.map {
        if (it.length == 1) it[0] else throw ParseError.NotAChar(it)
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
                                else -> throw ParseError.InvalidSequence(start, end)
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
                (comma.flatMap { _ -> literal }).many().flatMap { rest ->
                    rbrace.map { _ -> listOf(first) + rest }
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
                suffix.map { suf -> expr.map { pre + it + suf } }
            }
        }
    )
}

/**
 * Mode for brace expansion: ORDERED (preserve order), UNORDERED (deduped, sorted).
 */
enum class Mode { ORDERED, UNORDERED }

/**
 * Public API for Bash brace parsing.
 */
@JvmInline
value class BashBraceParser(val input: String) {
    /**
     * Parses the input string into a list of expanded strings, or throws on error.
     * @param mode Mode.ORDERED (default) preserves order, Mode.UNORDERED dedupes and sorts.
     */
    fun parse(mode: Mode = Mode.ORDERED): List<String> {
        val tokens = BashBrace.of(input).scanTokens()
        val result = when (val r = BashParsers.fullExpr.parse(tokens)) {
            is ParseResult.Success -> r.value
            is ParseResult.Failure -> throw IllegalArgumentException("Failed to parse: $input\n${r.error.message}")
        }
        return when (mode) {
            Mode.ORDERED -> result
            Mode.UNORDERED -> result.toSet().sorted()
        }
    }
    companion object {
        fun of(input: String): BashBraceParser = BashBraceParser(input)
    }
} 