package borg.trikeshed.parse

import borg.trikeshed.lib.*

data class ParseResult<out T>(
    val value: T,
    val remaining: Indexed<Token>,
)

@kotlin.jvm.JvmInline
value class Parser<T>(val parse: (Indexed<Token>) -> ParseResult<T>?) {
    companion object {
        fun <T> pure(value: T): Parser<T> = Parser { tokens -> ParseResult(value, tokens) }

        fun <T> fail(): Parser<T> = Parser { null }

        fun token(type: TokenType): Parser<Token> =
            Parser { tokens ->
                if (tokens.size == 0) {
                    null
                } else {
                    val token = tokens[0]
                    if (token.type == type) {
                        ParseResult(token, tokens.size - 1 j { i -> tokens[i + 1] })
                    } else {
                        null
                    }
                }
            }

        fun <T> memoize(parser: Parser<T>): Parser<T> {
            val cache = mutableMapOf<Indexed<Token>, ParseResult<T>?>()
            return Parser { tokens ->
                cache.getOrPut(tokens) { parser.parse(tokens) }
            }
        }
    }

    fun <R> map(transform: (T) -> R): Parser<R> =
        Parser { tokens ->
            parse(tokens)?.let { result ->
                ParseResult(transform(result.value), result.remaining)
            }
        }

    fun <R> flatMap(transform: (T) -> Parser<R>): Parser<R> =
        Parser { tokens ->
            parse(tokens)?.let { result ->
                transform(result.value).parse(result.remaining)
            }
        }

    fun or(other: Parser<T>): Parser<T> =
        Parser { tokens ->
            parse(tokens) ?: other.parse(tokens)
        }

    fun many(): Parser<Indexed<T>> =
        Parser { tokens ->
            val results = mutableListOf<T>()
            var remaining = tokens

            while (true) {
                val result = parse(remaining) ?: break
                results.add(result.value)
                remaining = result.remaining
            }

            ParseResult(results.toIdx(), remaining)
        }

    fun optional(): Parser<T?> =
        Parser { tokens ->
            parse(tokens) ?: ParseResult(null, tokens)
        }
}

// Bash Brace specific parsers
object BashParsers {
    val lbrace: Parser<Token> = Parser.token(TokenType.LBRACE)
    val rbrace: Parser<Token> = Parser.token(TokenType.RBRACE)
    val comma: Parser<Token> = Parser.token(TokenType.COMMA)
    val sequence: Parser<Token> = Parser.token(TokenType.SEQUENCE)

    val literal: Parser<String> =
        Parser { tokens ->
            if (tokens.size == 0) {
                null
            } else {
                val token = tokens[0]
                if (token.type == TokenType.LITERAL) {
                    ParseResult(token.literal, tokens.size - 1 j { i -> tokens[i + 1] })
                } else {
                    null
                }
            }
        }

    val number: Parser<Int> = literal.map { it.toIntOrNull() ?: throw IllegalArgumentException("Not a number: $it") }

    val char: Parser<Char> =
        literal.map {
            if (it.length == 1) it[0] else throw IllegalArgumentException("Not a single character: $it")
        }

    val sequenceExpr: Parser<Indexed<String>> =
        Parser.memoize(
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
                                        val range = if (step > 0) startNum..endNum else startNum downTo endNum
                                        range.map { it.toString() }.toIdx()
                                    }
                                    start.length == 1 && end.length == 1 -> {
                                        val startChar = start[0]
                                        val endChar = end[0]
                                        val step = if (startChar <= endChar) 1 else -1
                                        val range = if (step > 0) startChar..endChar else startChar downTo endChar
                                        range.map { it.toString() }.toIdx()
                                    }
                                    else -> throw IllegalArgumentException("Invalid sequence: $start..$end")
                                }
                            }
                        }
                    }
                }
            },
        )

    val listExpr: Parser<Indexed<String>> =
        Parser.memoize(
            lbrace.flatMap { _ ->
                literal.flatMap { first ->
                    (
                        comma.flatMap { _ ->
                            literal
                        }
                    ).many().flatMap { rest ->
                        rbrace.map { _ ->
                            (listOf(first) + rest.play.toList()).toIdx()
                        }
                    }
                }
            },
        )

    val braceExpr: Parser<Indexed<String>> =
        Parser.memoize(
            sequenceExpr.or(listExpr),
        )

    val prefix: Parser<String> = literal.optional().map { it ?: "" }
    val suffix: Parser<String> = literal.optional().map { it ?: "" }

    val fullExpr: Parser<Indexed<String>> =
        Parser.memoize(
            prefix.flatMap { pre ->
                braceExpr.flatMap { expr ->
                    suffix.map { suf ->
                        expr α { pre + it + suf }
                    }
                }
            },
        )
}

// Public API
@kotlin.jvm.JvmInline
value class BashBraceParser(val input: String) {
    fun parse(): Indexed<String> {
        val tokens = BashBrace.of(input).scanTokens().toIdx()
        return BashParsers.fullExpr.parse(tokens)?.value
            ?: throw IllegalArgumentException("Failed to parse: $input")
    }

    companion object {
        fun of(input: String): BashBraceParser = BashBraceParser(input)
    }
} 
