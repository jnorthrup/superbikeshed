package borg.entityscanner

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
// import borg.trikeshed.lib.play // Not strictly needed in this file if toSeries is direct
// Assuming this extension exists or can be added if List.toSeries() is not a general TrikeShed lib feature
// For now, we'll assume it will be made available or is part of the TrikeShed setup.
// fun <T> List<T>.toSeries(): Series<T> = size j { index -> this[index] }


/**
 * A basic lexer for Kotlin scripts, focusing on tokens relevant for
 * annotations, directives, and top-level declarations.
 */
object KotlinScriptLexer {

    fun tokenize(source: String): TokenSeries {
        val tokens = mutableListOf<BoundedToken>()
        var currentIndex = 0

        while (currentIndex < source.length) {
            val char = source[currentIndex]
            val startIndex = currentIndex

            when {
                char.isWhitespace() -> {
                    // Skip whitespace tokens for parser simplicity
                    currentIndex = readWhile(source, currentIndex) { it.isWhitespace() }.second
                    continue
                }

                char == '/' && currentIndex + 1 < source.length && source[currentIndex + 1] == '/' -> {
                    // Single-line comment
                    val (value, nextIndex) = readUntil(source, currentIndex) { it == '\n' }
                    tokens.add(createBoundedToken(value, TokenType(TokenType.COMMENT), startIndex))
                    currentIndex = nextIndex
                }

                char == '/' && currentIndex + 1 < source.length && source[currentIndex + 1] == '*' -> {
                    // Multi-line comment (basic, no nesting support for now)
                    var value = ""
                    var currentPos = currentIndex
                    var nesting = 0
                    // Basic handling of /* ... */, does not correctly handle nested comments
                    // A more robust implementation would track nesting levels.
                    while(currentPos < source.length) {
                        if (source[currentPos] == '/' && currentPos + 1 < source.length && source[currentPos+1] == '*') {
                            nesting++
                            currentPos += 2
                            value += "/*"
                            continue
                        }
                        if (source[currentPos] == '*' && currentPos + 1 < source.length && source[currentPos+1] == '/') {
                            nesting--
                            value += "*/"
                            currentPos += 2
                            if (nesting == 0) break // Exit if top-level comment is closed
                        } else {
                            value += source[currentPos]
                            currentPos++
                        }
                         if (nesting == 0 && value.endsWith("*/")) break // Ensure we break if it was the outer comment
                    }
                     if (!value.endsWith("*/") && nesting > 0) { // Unterminated comment
                        // Handle as error or partial comment
                    }

                    tokens.add(createBoundedToken(value, TokenType(TokenType.COMMENT), startIndex))
                    currentIndex = currentPos
                }

                char == '@' -> {
                    if (currentIndex + "file:".length < source.length && source.substring(currentIndex + 1, currentIndex + 1 + "file:".length) == "file:") {
                         val annotationMarker = "@file:"
                         tokens.add(createBoundedToken(annotationMarker, TokenType(TokenType.ANNOTATION), startIndex))
                         currentIndex += annotationMarker.length
                    } else {
                        tokens.add(createBoundedToken("@", TokenType(TokenType.PUNCTUATION), startIndex)) // Or a specific ANNOTATION_MARKER type
                        currentIndex++
                    }
                }

                char.isLetter() || char == '_' -> {
                    val (value, nextIndex) = readWhile(source, currentIndex) { it.isLetterOrDigit() || it == '_' }
                    val tokenType = keywordToTokenType(value) ?: TokenType(TokenType.IDENTIFIER)
                    tokens.add(createBoundedToken(value, tokenType, startIndex))
                    currentIndex = nextIndex
                }

                char.isDigit() -> {
                    val (value, nextIndex) = readWhile(source, currentIndex) { it.isDigit() }
                    // Could add basic float support here too
                    tokens.add(createBoundedToken(value, TokenType(TokenType.LITERAL_NUMBER), startIndex))
                    currentIndex = nextIndex
                }

                char == '"' -> {
                    var valueAccumulator = char.toString()
                    currentIndex++ // Consume opening quote
                    var escaped = false
                    var contentEnd = currentIndex
                    while(contentEnd < source.length) {
                        val currentCh = source[contentEnd]
                        valueAccumulator += currentCh
                        if (currentCh == '\\' && !escaped) { // Corrected: check for backslash for escape
                            escaped = true
                        } else if (currentCh == '"' && !escaped) {
                            break // Closing quote
                        } else {
                            escaped = false
                        }
                        contentEnd++
                    }
                     currentIndex = contentEnd + 1 // Past the closing quote or end of string
                    tokens.add(createBoundedToken(valueAccumulator, TokenType(TokenType.LITERAL_STRING), startIndex))
                }

                // Punctuation and Operators (single characters for simplicity now)
                else -> {
                    val value = char.toString()
                    val tokenType = symbolToTokenType(char) // Get the TokenType instance
                    tokens.add(createBoundedToken(value, tokenType, startIndex))
                    currentIndex++
                }
            }
        }
        // This requires a `toSeries()` extension on List<BoundedToken> to be available in scope.
        // For example: fun <T> List<T>.toSeries(): Series<T> = size j { index -> this[index] }
        // This should ideally be part of the borg.trikeshed.lib or a common utility.
        return borg.trikeshed.lib.ListToSeriesConverter.toSeries(tokens) // Assuming a helper object for conversion
    }

    private fun createBoundedToken(value: String, typeInstance: TokenType, startIndex: Int): BoundedToken {
        val lexicalToken = LexicalToken(value)
        val bounds = TokenBounds.pack(startIndex, value.length)
        return (lexicalToken j typeInstance) j bounds
    }

    private fun readWhile(source: String, startIndex: Int, predicate: (Char) -> Boolean): Pair<String, Int> {
        var currentIndex = startIndex
        while (currentIndex < source.length && predicate(source[currentIndex])) {
            currentIndex++
        }
        return Pair(source.substring(startIndex, currentIndex), currentIndex)
    }

    private fun readUntil(source: String, startIndex: Int, predicate: (Char) -> Boolean): Pair<String, Int> {
        var currentIndex = startIndex
        while (currentIndex < source.length && !predicate(source[currentIndex])) {
            currentIndex++
        }
        // If predicate is for newline, include it in the token value for single-line comments
        if (currentIndex < source.length && predicate(source[currentIndex]) && source[currentIndex] == '\n') {
            // currentIndex++ // Optionally consume the newline as part of the comment token
        }
        return Pair(source.substring(startIndex, currentIndex), currentIndex)
    }

    private fun keywordToTokenType(value: String): TokenType? {
        return when (value) {
            "package" -> TokenType(TokenType.KEYWORD)
            "import" -> TokenType(TokenType.KEYWORD)
            "class" -> TokenType(TokenType.KEYWORD)
            "interface" -> TokenType(TokenType.KEYWORD)
            "object" -> TokenType(TokenType.KEYWORD)
            "fun" -> TokenType(TokenType.KEYWORD)
            "val" -> TokenType(TokenType.KEYWORD)
            "var" -> TokenType(TokenType.KEYWORD)
            "true", "false" -> TokenType(TokenType.LITERAL_BOOLEAN)
            // Add other relevant keywords:
            "is", "in", "if", "else", "when", "try", "catch", "finally", "for", "do", "while",
            "return", "throw", "super", "this", "null",
            "get", "set", // Contextual keywords
            // Modifiers
            "public", "private", "protected", "internal",
            "enum", "sealed", "annotation", "data", "inner", "override", "lateinit",
            "operator", "infix", "suspend", "inline", "external", "abstract", "final", "open",
            "const", "companion"
            -> TokenType(TokenType.KEYWORD)
            else -> null
        }
    }

    private fun symbolToTokenType(char: Char): TokenType {
        return when (char) {
            '(', ')', '{', '}', '[', ']' -> TokenType(TokenType.PUNCTUATION)
            '.', ',', ';', '?' -> TokenType(TokenType.PUNCTUATION) // Added '?'
            ':' -> TokenType(TokenType.PUNCTUATION) // Potentially COLON type if more specific needed
            // Basic operators; could be expanded for multi-char like '==', '!=', '->', '::' etc.
            '+', '-', '*', '%', '<', '>', '=', '!', '&', '|', '^', '~' -> TokenType(TokenType.OPERATOR)
            else -> TokenType(TokenType.UNKNOWN)
        }
    }
}

// Extension function to convert List to Series/Indexed
fun <T> List<T>.toIndexed(): Indexed<T> = size j { i -> this[i] }
