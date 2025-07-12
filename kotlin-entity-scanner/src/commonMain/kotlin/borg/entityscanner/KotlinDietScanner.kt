package borg.entityscanner

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j

// Use the existing CharSeries from TokenStairway.kt
typealias CharSeries = Indexed<PositionedChar>


/**
 * Represents a contiguous block (island) in the source, by index.
 * start: inclusive, end: exclusive
 */
data class IslandIndexed(
    val start: Int,
    val end: Int,
    val type: IslandIndexedType
)

enum class IslandIndexedType {
    KEYWORD,
    IDENTIFIER,
    LITERAL,
    OPERATOR,
    BRACKET,
    WHITESPACE,
    COMMENT,
    UNKNOWN
}

/**
 * Given a CharSeries, produce a Indexed<IslandIndexed> by grouping contiguous runs of the same type.
 * This is a diet scanner: no regex, just TrikeShed types and patterns.
 */
fun CharSeries.toIslandsIndexed(): Indexed<IslandIndexed> {
    val islands = mutableListOf<IslandIndexed>()
    if (this.size == 0) return 0 j { throw IndexOutOfBoundsException() }

    var i = 0
    while (i < this.size) {
        val start = i
        val first = this[i]
        val type = classifyIslandType(first)
        // Group contiguous chars of the same type
        while (i < this.size && classifyIslandType(this[i]) == type) {
            i++
        }
        islands.add(IslandIndexed(start, i, type))
    }
    return \1 j { \2: Int -> islands[idx] }
}

/**
 * Classify a PositionedChar into an IslandIndexedType using TrikeShed stairway conventions.
 */
fun classifyIslandType(posChar: PositionedChar): IslandIndexedType {
    val (classifiedChar, _) = posChar
    val (_, charClass) = classifiedChar
    return when (charClass.type) {
        // Map TrikeShed CharClass to island types
        4u -> IslandIndexedType.WHITESPACE // CharClass.WHITESPACE
        5u -> IslandIndexedType.WHITESPACE // CharClass.NEWLINE
        1u, 2u, 6u -> IslandIndexedType.IDENTIFIER // LETTER, DIGIT, UNDERSCORE
        3u, 7u, 8u, 9u, 10u, 11u, 12u, 13u, 14u, 15u, 16u, 17u, 18u, 19u, 20u -> IslandIndexedType.OPERATOR // SYMBOL, DOT, COLON, etc.
        else -> IslandIndexedType.UNKNOWN
    }
}

// Represents a contiguous block of non-whitespace text (or whitespace/comment if stored as islands)
data class Island(
    val text: String,
    val startIndex: Int,
    val type: IslandType
)

enum class IslandType {
    KEYWORD,      // fun, val, if, class, etc.
    IDENTIFIER,   // A variable name, function name, etc.
    LITERAL,      // "hello", 123, true
    OPERATOR,     // +, -, =, ->, .
    BRACKET,      // (, ), {, }, [, ]
    WHITESPACE,   // The "ocean" itself
    COMMENT,
    UNKNOWN       // Default for anything we don't recognize.
}

/**
 * Performs the initial scan: splits the source into islands and classifies each.
 */
fun initialScan(source: String): List<Island> {
    val islands = mutableListOf<Island>()
    val keywordSet = setOf(
        "fun", "val", "var", "if", "else", "class", "interface", "while", "for", "return"
    )
    val regex = """\s+|\S+""".toRegex()

    regex.findAll(source).forEach { matchResult ->
        val text = matchResult.value
        val startIndex = matchResult.range.first
        val type = when {
            text.all { it.isWhitespace() } -> IslandType.WHITESPACE
            text in keywordSet -> IslandType.KEYWORD
            text.startsWith("//") || text.startsWith("/*") -> IslandType.COMMENT
            text.matches(Regex("\"[^\"]*\"")) -> IslandType.LITERAL // Simple string literal
            text.matches(Regex("[(){}\\[\\]]")) -> IslandType.BRACKET
            text.matches(Regex("[-+*/=<>!&|.%]+")) -> IslandType.OPERATOR
            text.matches(Regex("[0-9]+")) -> IslandType.LITERAL
            text.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) -> IslandType.IDENTIFIER
            else -> IslandType.UNKNOWN
        }
        islands.add(Island(text, startIndex, type))
    }
    return islands
}

/**
 * Navigator for ad-hoc chaining and analysis over the island list.
 */
class IslandNavigator(val islands: List<Island>, var currentIndex: Int = 0) {
    fun current(): Island? = islands.getOrNull(currentIndex)

    /** Move to next non-whitespace/comment token and return it. */
    fun nextToken(): Island? {
        while (currentIndex < islands.size - 1) {
            currentIndex++
            val island = islands[currentIndex]
            if (island.type != IslandType.WHITESPACE && island.type != IslandType.COMMENT) {
                return island
            }
        }
        return null
    }

    /** Look ahead to next non-whitespace/comment token without moving cursor. */
    fun peekNextToken(): Island? {
        var lookaheadIndex = currentIndex
        while (lookaheadIndex < islands.size - 1) {
            lookaheadIndex++
            val island = islands[lookaheadIndex]
            if (island.type != IslandType.WHITESPACE && island.type != IslandType.COMMENT) {
                return island
            }
        }
        return null
    }

    /** Move to previous non-whitespace/comment token and return it. */
    fun previousToken(): Island? {
        while (currentIndex > 0) {
            currentIndex--
            val island = islands[currentIndex]
            if (island.type != IslandType.WHITESPACE && island.type != IslandType.COMMENT) {
                return island
            }
        }
        return null
    }

    /** Look back to previous non-whitespace/comment token without moving cursor. */
    fun peekPreviousToken(): Island? {
        var lookbackIndex = currentIndex
        while (lookbackIndex > 0) {
            lookbackIndex--
            val island = islands[lookbackIndex]
            if (island.type != IslandType.WHITESPACE && island.type != IslandType.COMMENT) {
                return island
            }
        }
        return null
    }
}

/**
 * Example: Find simple function declarations of the form 'fun IDENTIFIER ('
 */
data class SimpleFunction(val name: String, val start: Int)

fun findSimpleFunctions(islands: List<Island>): List<SimpleFunction> {
    val functions = mutableListOf<SimpleFunction>()
    var i = 0
    while (i < islands.size) {
        val current = islands[i]
        if (current.type == IslandType.KEYWORD && current.text == "fun") {
            // Look ahead for IDENTIFIER and then BRACKET '('
            var j = i + 1
            // Skip whitespace/comments
            while (j < islands.size && (islands[j].type == IslandType.WHITESPACE || islands[j].type == IslandType.COMMENT)) j++
            if (j < islands.size && islands[j].type == IslandType.IDENTIFIER) {
                val name = islands[j].text
                var k = j + 1
                while (k < islands.size && (islands[k].type == IslandType.WHITESPACE || islands[k].type == IslandType.COMMENT)) k++
                if (k < islands.size && islands[k].type == IslandType.BRACKET && islands[k].text == "(") {
                    functions.add(SimpleFunction(name, current.startIndex))
                }
            }
        }
        i++
    }
    return functions
}

typealias DietIsland = IslandIndexed
typealias DietIslandType = IslandIndexedType
fun CharSeries.toDietIslands(): Indexed<DietIsland> = this.toIslandsIndexed() 