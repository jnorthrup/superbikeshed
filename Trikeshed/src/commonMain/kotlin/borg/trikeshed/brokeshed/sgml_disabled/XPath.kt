@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.brokeshed.sgml

import borg.trikeshed.lib.*
import borg.trikeshed.lib.Either
import kotlin.jvm.JvmInline
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.play
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.α

/**
 * TrikeShed XPath System - MetaSeries + Enum Dispatch + Predicate Lattices
 * 
 * Uses MetaSeries for linking and graphs with j packing benefits
 * Taxonomical typealiases for structure
 * Enums for constants and lookups  
 * Inline classes creating predicate lattices
 */

// === TAXONOMICAL TYPEALIASES ===

// Core XPath Types
typealias XPathChar = Char
typealias XPathPosition = Int
typealias XPathLength = Int
typealias XPathDepth = Int
typealias XPathStringValue = String

// Structural Analysis Types
typealias XPathNodeBounds = Join<XPathPosition, XPathPosition> // start j end
typealias XPathNode = Join<XPathNodeType, XPathNodeBounds>

// MetaSeries Realm Specializations
typealias XPathCharIndexed = Indexed<XPathChar>
typealias XPathTokenIndexed = Indexed<XPathToken>
typealias XPathNodeIndexed = Indexed<XPathNode>
typealias XPathPredicateIndexed = Indexed<XPathPredicate>

// Predicate Lattice Types
typealias XPathPredicateResult = Join<Boolean, XPathConfidence>
typealias XPathPredicate = (XPathCharIndexed, XPathPosition) -> XPathPredicateResult
typealias XPathPredicateLattice = Indexed<XPathPredicate>

// Error Handling Types
@JvmInline
value class XPathError(val message: String)
typealias XPathResult<T> = Either<XPathError, T>

// === ENUM-BASED DISPATCH SYSTEM ===

/**
 * XPath Node Types - Enum for constants and lookups
 */
enum class XPathNodeType(val symbol: String, val precedence: Int) {
    // Element nodes
    ELEMENT("element", 100),
    ATTRIBUTE("@", 90),
    TEXT("text()", 80),
    COMMENT("comment()", 70),
    
    // Axis nodes  
    CHILD("/", 60),
    DESCENDANT("//", 50),
    PARENT("..", 40),
    ANCESTOR("ancestor::", 30),
    
    // Predicate nodes
    PREDICATE("[", 20),
    FUNCTION("function()", 10),
    
    // Special nodes
    ROOT("/", 110),
    WILDCARD("*", 0),
    CURRENT(".", 45);
    
    companion object {
        fun fromSymbol(symbol: String): XPathNodeType? = 
            values().find { it.symbol == symbol }
    }
}

/**
 * XPath Token Types - Enum for scanner dispatch
 */
enum class XPathTokenType(val symbol: String, val precedence: Int) {
    // Structural tokens
    SLASH("/", 100),
    DOUBLE_SLASH("//", 90),
    DOT(".", 80),
    DOUBLE_DOT("..", 70),
    AT("@", 60),
    BRACKET_OPEN("[", 50),
    BRACKET_CLOSE("]", 40),
    PAREN_OPEN("(", 30),
    PAREN_CLOSE(")", 20),
    
    // Operator tokens
    EQUALS("=", 10),
    NOT_EQUALS("!=", 9),
    LESS_THAN("<", 8),
    GREATER_THAN(">", 7),
    LESS_EQUAL("<=", 6),
    GREATER_EQUAL(">=", 5),
    
    // Function tokens
    FUNCTION("function", 4),
    COMMA(",", 3),
    
    // Value tokens
    IDENTIFIER("identifier", 2),
    STRING("string", 1),
    NUMBER("number", 0),
    WILDCARD("*", -1);
    
    companion object {
        fun fromSymbol(symbol: String): XPathTokenType? = 
            values().find { it.symbol == symbol }
    }
}

// === INLINE CLASS PREDICATE LATTICES ===

/**
 * XPath Confidence - Inline class for predicate lattice
 */
@JvmInline
value class XPathConfidence(val value: Double) {
    init {
        require(value in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
    }
    
    companion object {
        val CERTAIN = XPathConfidence(1.0)
        val LIKELY = XPathConfidence(0.8)
        val POSSIBLE = XPathConfidence(0.5)
        val UNLIKELY = XPathConfidence(0.2)
        val IMPOSSIBLE = XPathConfidence(0.0)
    }
}

/**
 * XPath Token - Inline class for scanner dispatch
 */
@JvmInline
value class XPathToken(val type: XPathTokenType, val value: String, val position: XPathPosition)

// XPathNode is already defined as typealias above

// === METASERIES LINKING AND GRAPHS ===

/**
 * XPath Attribute - MetaSeries for attribute linking
 */
typealias XPathAttribute = Join<XPathStringValue, XPathStringValue> // name j value
typealias XPathAttributeIndexed = Indexed<XPathAttribute>

/**
 * XPath Context - MetaSeries for graph retention
 */
typealias XPathContext = Join<XPathNodeIndexed, XPathPredicateLattice>

/**
 * XPath Expression - MetaSeries for expression composition
 */
typealias XPathExpression = Join<XPathTokenIndexed, XPathContext>

// === PREDICATE LATTICE SYSTEM ===

/**
 * XPath Predicate System - Creates predicate lattices using inline classes
 */
object XPathPredicateSystem {
    
    /**
     * Generate candidate predicates from character input
     */
    fun generateCandidatePredicates(char: XPathChar, position: XPathPosition): XPathPredicateIndexed {
        val candidates = mutableListOf<XPathPredicate>()
        
        // Generate multiple interpretation candidates using enum dispatch
        when {
            char.isLetter() -> {
                candidates.add(createPredicate("identifier", position, XPathConfidence.LIKELY))
                candidates.add(createPredicate("element_name", position, XPathConfidence.POSSIBLE))
                candidates.add(createPredicate("attribute_name", position, XPathConfidence.POSSIBLE))
            }
            char == '/' -> {
                candidates.add(createPredicate("path_separator", position, XPathConfidence.CERTAIN))
                candidates.add(createPredicate("root_indicator", position, XPathConfidence.LIKELY))
            }
            char == '@' -> {
                candidates.add(createPredicate("attribute_indicator", position, XPathConfidence.CERTAIN))
            }
            char == '[' -> {
                candidates.add(createPredicate("predicate_start", position, XPathConfidence.CERTAIN))
            }
            char == ']' -> {
                candidates.add(createPredicate("predicate_end", position, XPathConfidence.CERTAIN))
            }
            char == '.' -> {
                candidates.add(createPredicate("current_node", position, XPathConfidence.LIKELY))
                candidates.add(createPredicate("decimal_point", position, XPathConfidence.POSSIBLE))
            }
            char.isDigit() -> {
                candidates.add(createPredicate("number", position, XPathConfidence.LIKELY))
                candidates.add(createPredicate("index", position, XPathConfidence.POSSIBLE))
            }
            else -> {
                candidates.add(createPredicate("unknown", position, XPathConfidence.UNLIKELY))
            }
        }
        
        return candidates.toSeries()
    }
    
    /**
     * Apply predicates to validate/invalidate candidate states
     */
    fun applyPredicates(
        candidates: XPathPredicateIndexed,
        predicates: XPathPredicateIndexed,
        char: XPathChar,
        position: XPathPosition
    ): XPathPredicateIndexed {
        return candidates.α { candidate ->
            val results = predicates.α { predicate ->
                predicate(charIndexed, position)
            }
            
            // Aggregate confidence using predicate lattice
            val aggregateConfidence = results.α { it.b }.play.fold(XPathConfidence.IMPOSSIBLE) { acc, conf ->
                XPathConfidence((acc.value + conf.value) / 2.0)
            }
            
            // Return validated candidate with updated confidence
            if (aggregateConfidence.value > 0.3) {
                candidate
            } else {
                null
            }
        }.play.filterNotNull().toSeries()
    }
    
    /**
     * Create predicate using inline class dispatch
     */
    private fun createPredicate(
        type: String, 
        position: XPathPosition, 
        confidence: XPathConfidence
    ): XPathPredicate = { chars, pos ->
        val isValid = when (type) {
            "identifier" -> chars[pos].isLetterOrDigit() || chars[pos] == '_'
            "path_separator" -> chars[pos] == '/'
            "attribute_indicator" -> chars[pos] == '@'
            "predicate_start" -> chars[pos] == '['
            "predicate_end" -> chars[pos] == ']'
            "current_node" -> chars[pos] == '.'
            "number" -> chars[pos].isDigit()
            else -> false
        }
        
        (isValid j confidence)
    }
}

// === XPath Scanner using MetaSeries ===

/**
 * XPath Scanner - Uses MetaSeries for linking and graphs with j packing benefits
 */
object XPathScanner {
    
    /**
     * Scan XPath string into token series using MetaSeries
     */
    fun scan(xpathString: XPathStringValue): XPathResult<XPathTokenIndexed> {
        if (xpathString.isEmpty()) return Either.right(emptyIndex())
        
        val chars = xpathString.toCharArray().toSeries()
        return Either.right(tokenize(chars))
    }
    
    /**
     * Tokenize character series using α transform - the ONLY transformation operator
     */
    private fun tokenize(chars: XPathCharIndexed): XPathTokenIndexed {
        val tokens = mutableListOf<XPathToken>()
        var pos = 0

        while (pos < chars.size) {
            val token = scanNextToken(chars, pos)
            tokens.add(token)
            pos += token.value.length.coerceAtLeast(1) // Advance by token length
        }

        val tokenArray = tokens.toTypedArray()
        return tokenArray.size j tokenArray::get
    }

    private inline fun scanNextToken(chars: XPathCharIndexed, pos: XPathPosition): XPathToken {
        val char = chars[pos]
        return when {
            char.isWhitespace() -> scanWhitespace(chars, pos)
            char == '/' -> scanPathSeparator(chars, pos)
            char == '@' -> XPathToken(XPathTokenType.AT, "@", pos)
            char == '[' -> XPathToken(XPathTokenType.BRACKET_OPEN, "[", pos)
            char == ']' -> XPathToken(XPathTokenType.BRACKET_CLOSE, "]", pos)
            char == '(' -> XPathToken(XPathTokenType.PAREN_OPEN, "(", pos)
            char == ')' -> XPathToken(XPathTokenType.PAREN_CLOSE, ")", pos)
            char == '.' -> scanDot(chars, pos)
            char == '=' -> XPathToken(XPathTokenType.EQUALS, "=", pos)
            char == '!' -> scanNotEquals(chars, pos)
            char == '<' -> scanLessThan(chars, pos)
            char == '>' -> scanGreaterThan(chars, pos)
            char == ',' -> XPathToken(XPathTokenType.COMMA, ",", pos)
            char.isLetter() -> scanIdentifier(chars, pos)
            char.isDigit() -> scanNumber(chars, pos)
            char == '"' || char == '\'' -> scanString(chars, pos)
            else -> XPathToken(XPathTokenType.IDENTIFIER, char.toString(), pos)
        }
    }
    
    /**
     * Scan whitespace using Join composition
     */
    private fun scanWhitespace(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        var pos = start
        while (pos < chars.size && chars[pos].isWhitespace()) {
            pos++
        }
        return XPathToken(XPathTokenType.IDENTIFIER, " ", start)
    }
    
    /**
     * Scan path separator with double-slash detection
     */
    private fun scanPathSeparator(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        return if (start + 1 < chars.size && chars[start + 1] == '/') {
            XPathToken(XPathTokenType.DOUBLE_SLASH, "//", start)
        } else {
            XPathToken(XPathTokenType.SLASH, "/", start)
        }
    }
    
    /**
     * Scan dot with double-dot detection
     */
    private fun scanDot(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        return if (start + 1 < chars.size && chars[start + 1] == '.') {
            XPathToken(XPathTokenType.DOUBLE_DOT, "..", start)
        } else {
            XPathToken(XPathTokenType.DOT, ".", start)
        }
    }
    
    /**
     * Scan identifier using predicate lattice
     */
    private fun scanIdentifier(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        var pos = start
        while (pos < chars.size && (chars[pos].isLetterOrDigit() || chars[pos] == '_' || chars[pos] == '-')) {
            pos++
        }
        val value = chars.play.subList(start, pos).joinToString("")
        return XPathToken(XPathTokenType.IDENTIFIER, value, start)
    }
    
    /**
     * Scan number using predicate lattice
     */
    private fun scanNumber(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        var pos = start
        while (pos < chars.size && chars[pos].isDigit()) {
            pos++
        }
        val value = chars.play.subList(start, pos).joinToString("")
        return XPathToken(XPathTokenType.NUMBER, value, start)
    }
    
    /**
     * Scan string with quote handling
     */
    private fun scanString(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        val quoteChar = chars[start]
        var pos = start + 1
        while (pos < chars.size && chars[pos] != quoteChar) {
            pos++
        }
        if (pos < chars.size) pos++ // Include closing quote
        val value = chars.play.subList(start, pos).joinToString("")
        return XPathToken(XPathTokenType.STRING, value, start)
    }
    
    // Helper methods for operator scanning
    private fun scanNotEquals(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        return if (start + 1 < chars.size && chars[start + 1] == '=') {
            XPathToken(XPathTokenType.NOT_EQUALS, "!=", start)
        } else {
            XPathToken(XPathTokenType.IDENTIFIER, "!", start)
        }
    }
    
    private fun scanLessThan(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        return if (start + 1 < chars.size && chars[start + 1] == '=') {
            XPathToken(XPathTokenType.LESS_EQUAL, "<=", start)
        } else {
            XPathToken(XPathTokenType.LESS_THAN, "<", start)
        }
    }
    
    private fun scanGreaterThan(chars: XPathCharIndexed, start: XPathPosition): XPathToken {
        return if (start + 1 < chars.size && chars[start + 1] == '=') {
            XPathToken(XPathTokenType.GREATER_EQUAL, ">=", start)
        } else {
            XPathToken(XPathTokenType.GREATER_THAN, ">", start)
        }
    }
}

// === XPath Parser using MetaSeries ===

/**
 * XPath Parser - Uses MetaSeries for expression composition
 */
object XPathParser {
    
    /**
     * Parse XPath expression using MetaSeries composition
     */
    fun parse(xpathString: XPathStringValue): XPathResult<XPathExpression> {
        val scanResult = XPathScanner.scan(xpathString)
        return scanResult.α { tokens ->
            val context = createContext(tokens)
            tokens j context
        }
    }
    
    /**
     * Create context using MetaSeries linking
     */
    private fun createContext(tokens: XPathTokenIndexed): XPathContext {
        val nodes = parseNodes(tokens)
        val predicates = createPredicateLattice(tokens)
        return nodes j predicates
    }
    
    /**
     * Parse nodes using MetaSeries composition
     */
    private fun parseNodes(tokens: XPathTokenIndexed): XPathNodeIndexed {
        return tokens.α { token ->
            val nodeType = when (token.type) {
                XPathTokenType.SLASH -> XPathNodeType.CHILD
                XPathTokenType.DOUBLE_SLASH -> XPathNodeType.DESCENDANT
                XPathTokenType.DOT -> XPathNodeType.CURRENT
                XPathTokenType.DOUBLE_DOT -> XPathNodeType.PARENT
                XPathTokenType.AT -> XPathNodeType.ATTRIBUTE
                XPathTokenType.IDENTIFIER -> XPathNodeType.ELEMENT
                XPathTokenType.WILDCARD -> XPathNodeType.WILDCARD
                else -> XPathNodeType.ELEMENT
            }
            
            val bounds = token.position j (token.position + token.value.length)
            XPathNode(nodeType, bounds)
        }
    }
    
    /**
     * Create predicate lattice using MetaSeries
     */
    private fun createPredicateLattice(tokens: XPathTokenIndexed): XPathPredicateLattice {
        return tokens.α { token ->
            { chars, pos ->
                val isValid = when (token.type) {
                    XPathTokenType.IDENTIFIER -> pos < chars.size && chars[pos].isLetterOrDigit()
                    XPathTokenType.NUMBER -> pos < chars.size && chars[pos].isDigit()
                    XPathTokenType.STRING -> pos < chars.size && (chars[pos] == '"' || chars[pos] == '\'')
                    else -> true
                }
                (isValid j XPathConfidence.LIKELY)
            }
        }
    }
}

// === UTILITY EXTENSIONS ===

/**
 * Extension functions for convenient XPath processing
 */
fun XPathStringValue.scanXPath(): XPathResult<XPathTokenIndexed> = 
    XPathScanner.scan(this)

fun XPathStringValue.parseXPath(): XPathResult<XPathExpression> = 
    XPathParser.parse(this)

fun XPathTokenIndexed.parseNodes(): XPathNodeIndexed = 
    XPathParser.parseNodes(this)

/**
 * Utility functions for Series operations
 */
private fun <T> Array<T>.toSeries(): Indexed<T> = size j ::get
private fun <T> List<T>.toSeries(): Indexed<T> = size j ::get
private fun <T> emptyIndex(): Indexed<T> = 0 j { throw IndexOutOfBoundsException("Empty index") }

/**
 * Example usage demonstrating TrikeShed XPath patterns
 */
fun main() {
    val xpath = "/bookstore/book[@category='fiction']/title"
    
    // Scan tokens using MetaSeries
    val scanResult = xpath.scanXPath()
    scanResult.α { tokens ->
        println("Tokens: ${tokens.play.joinToString(", ") { "${it.type.symbol}(${it.value})" }}")
    }
    
    // Parse expression using MetaSeries
    val parseResult = xpath.parseXPath()
    parseResult.α { expression ->
        val tokens = expression.a
        val context = expression.b
        val nodes = context.a
        val predicates = context.b
        
        println("Nodes: ${nodes.play.joinToString(", ") { it.type.symbol }}")
        println("Predicates: ${predicates.size} predicate functions")
    }
} 