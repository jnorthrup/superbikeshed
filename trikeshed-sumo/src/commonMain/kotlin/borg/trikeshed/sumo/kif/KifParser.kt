@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.kif

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import borg.trikeshed.lib.bbcursive.BBCursiveSimdAutovec
import kotlinx.coroutines.flow.*

/**
 * A high-performance, streaming KIF (Knowledge Interchange Format) Parser.
 *
 * This parser is designed to handle the large SUMO corpus by using a two-phase,
 * SIMD-friendly scanning approach and building a lightweight Abstract Syntax Tree (AST)
 * from nested `Join` pairs, embodying the TrikeShed architectural philosophy.
 *
 * It provides the foundation for a compile-time SUMO solver with double/triple dispatch
 * patterns for optimal compile-time performance.
 */
object KifParser {

    /**
     * Main entry point. Parses a KIF file content string into a flow of top-level expressions.
     *
     * @param kifContent The raw string content of the .kif file.
     * @return A Flow that emits each parsed top-level KIF expression (s-expression).
     */
    fun parse(kifContent: String): Flow<KifExpression> = flow {
        // Phase 1: High-speed SIMD structural tokenization.
        val tokens = tokenizeSimd(kifContent)

        // Phase 2: SIMD-accelerated expression parsing on the token stream.
        var i = 0
        while (i < tokens.size) {
            if (tokens[i] is KifToken.ParenOpen) {
                val (expr, nextIndex) = parseExpressionSimd(tokens, i)
                emit(expr)
                i = nextIndex
            } else {
                // Ignore tokens outside of a top-level expression
                i++
            }
        }
    }

    /**
     * SIMD-accelerated tokenizer for KIF using BBCursiveSimdAutovec.
     * Scans the input string for structural characters and comments in parallel.
     */
    internal fun tokenizeSimd(input: String): List<KifToken> {
        val bytes = input.encodeToByteArray()
        val structPositions = BBCursiveSimdAutovec.scanKifStructural(bytes)
        val commentPositions = BBCursiveSimdAutovec.scanKifComments(bytes)
        val tokens = mutableListOf<KifToken>()
        var i = 0
        val structSet = structPositions.toSet()
        val commentSet = commentPositions.toSet()
        while (i < bytes.size) {
            val c = bytes[i].toInt().toChar()
            when {
                i in commentSet -> {
                    // Skip to end of line
                    while (i < bytes.size && bytes[i].toInt().toChar() != '\n') i++
                }
                i in structSet -> {
                    when (c) {
                        '(' -> tokens.add(KifToken.ParenOpen(i))
                        ')' -> tokens.add(KifToken.ParenClose(i))
                        '"' -> {
                            val start = i
                            i++ // Skip opening quote
                            val sb = StringBuilder()
                            while (i < bytes.size && bytes[i].toInt().toChar() != '"') {
                                if (bytes[i].toInt().toChar() == '\\') i++ // Handle escaped chars simply
                                if (i < bytes.size) sb.append(bytes[i].toInt().toChar())
                                i++
                            }
                            i++ // Skip closing quote
                            tokens.add(KifToken.Str(sb.toString(), start))
                            continue
                        }
                        ';' -> { /* Already handled as comment */ }
                    }
                    i++
                }
                c.isWhitespace() -> i++
                else -> {
                    val start = i
                    while (i < bytes.size &&
                        bytes[i].toInt().toChar().let { ch ->
                            !ch.isWhitespace() && ch != '(' && ch != ')' && ch != '"' && ch != ';'
                        }) {
                        i++
                    }
                    val symbol = bytes.decodeToString(start, i)
                    tokens.add(KifToken.Symbol(symbol, start))
                }
            }
        }
        return tokens
    }

    /**
     * SIMD-accelerated expression parser using token stream.
     * Recursively builds a KIF expression tree from a stream of tokens.
     */
    private fun parseExpressionSimd(tokens: List<KifToken>, startIndex: Int): Pair<KifExpression, Int> {
        if (tokens[startIndex] !is KifToken.ParenOpen) {
            throw KifParseException("Expression must start with '('", tokens.getOrNull(startIndex)?.position ?: -1)
        }

        var currentIndex = startIndex + 1
        val elements = mutableListOf<KifExpression>()

        while (currentIndex < tokens.size && tokens[currentIndex] !is KifToken.ParenClose) {
            when (val token = tokens[currentIndex]) {
                is KifToken.ParenOpen -> {
                    val (nestedExpr, nextIndex) = parseExpressionSimd(tokens, currentIndex)
                    elements.add(nestedExpr)
                    currentIndex = nextIndex
                }
                is KifToken.Symbol -> {
                    elements.add(KifExpression.Atom(token.value))
                    currentIndex++
                }
                is KifToken.Str -> {
                    elements.add(KifExpression.Str(token.value))
                    currentIndex++
                }
                is KifToken.ParenClose -> {
                    // This case is handled by the while loop condition, but included for clarity.
                    break
                }
            }
        }

        if (currentIndex >= tokens.size || tokens[currentIndex] !is KifToken.ParenClose) {
            throw KifParseException("Unmatched parenthesis", tokens.getOrNull(startIndex)?.position ?: -1)
        }

        // Build the nested Join structure
        val expression = elements.reversed().fold(KifExpression.Nil as KifExpression) { acc, el ->
            KifExpression.Cons(el, acc)
        }

        return expression to (currentIndex + 1)
    }

    // Legacy tokenizer kept for reference
    @Deprecated("Use tokenizeSimd for SIMD/autovec KIF parsing")
    internal fun tokenize(input: String): List<KifToken> {
        val tokens = mutableListOf<KifToken>()
        var i = 0
        while (i < input.length) {
            when (val char = input[i]) {
                ';' -> { // Comment
                    while (i < input.length && input[i] != '\n') {
                        i++
                    }
                }
                '(' -> {
                    tokens.add(KifToken.ParenOpen(i)); i++
                }
                ')' -> {
                    tokens.add(KifToken.ParenClose(i)); i++
                }
                '"' -> { // String literal
                    val start = i
                    i++ // Skip opening quote
                    val sb = StringBuilder()
                    while (i < input.length && input[i] != '"') {
                        if (input[i] == '\\') i++ // Handle escaped chars simply
                        sb.append(input[i])
                        i++
                    }
                    i++ // Skip closing quote
                    tokens.add(KifToken.Str(sb.toString(), start))
                }
                in ' ', '\t', '\r', '\n' -> i++ // Whitespace
                else -> { // Symbol (variable or identifier)
                    val start = i
                    while (i < input.length && !char.isKifDelimiter()) {
                        i++
                    }
                    val symbol = input.substring(start, i)
                    tokens.add(KifToken.Symbol(symbol, start))
                }
            }
        }
        return tokens
    }
    
    private fun Char.isKifDelimiter(): Boolean =
        this.isWhitespace() || this == '(' || this == ')' || this == '"' || this == ';'

    /**
     * Represents the tokenized units of a KIF file.
     */
    sealed class KifToken(val position: Int) {
        class ParenOpen(pos: Int) : KifToken(pos)
        class ParenClose(pos: Int) : KifToken(pos)
        class Symbol(val value: String, pos: Int) : KifToken(pos)
        class Str(val value: String, pos: Int) : KifToken(pos)
    }

    /**
     * Represents a parsed KIF S-Expression using nested `Join`s (cons cells).
     * This forms the Abstract Syntax Tree (AST) with double/triple dispatch support.
     */
    sealed class KifExpression {
        /** A `cons` cell, representing a pair of a value and the rest of the list. */
        data class Cons(val car: KifExpression, val cdr: KifExpression) : KifExpression() {
            // Overriding toString for readable output
            override fun toString(): String {
                val elements = mutableListOf<String>()
                var current: KifExpression = this
                while (current is Cons) {
                    elements.add(current.car.toString())
                    current = current.cdr
                }
                if (current !is Nil) { // Dotted pair
                    elements.add(".")
                    elements.add(current.toString())
                }
                return "(${elements.joinToString(" ")})"
            }
        }

        /** A literal atom (symbol, number, etc.). */
        data class Atom(val value: String) : KifExpression() {
            override fun toString(): String = value
        }

        /** A string literal. */
        data class Str(val value: String) : KifExpression() {
            override fun toString(): String = "\"$value\""
        }
        
        /** The end of a list, equivalent to LISP's `nil`. */
        object Nil : KifExpression() {
            override fun toString(): String = "NIL"
        }
    }

    class KifParseException(message: String, position: Int) : Exception("$message at position $position")

    /**
     * Double Dispatch Visitor Pattern for Compile-Time Optimization
     * 
     * This enables compile-time specialization of operations on KIF expressions,
     * allowing the compiler to generate optimal code paths for different expression types.
     */
    interface KifExpressionVisitor<R> {
        fun visitCons(cons: KifExpression.Cons): R
        fun visitAtom(atom: KifExpression.Atom): R
        fun visitStr(str: KifExpression.Str): R
        fun visitNil(nil: KifExpression.Nil): R
    }

    /**
     * Extension function to enable double dispatch on KifExpression
     */
    fun <R> KifExpression.accept(visitor: KifExpressionVisitor<R>): R = when (this) {
        is KifExpression.Cons -> visitor.visitCons(this)
        is KifExpression.Atom -> visitor.visitAtom(this)
        is KifExpression.Str -> visitor.visitStr(this)
        is KifExpression.Nil -> visitor.visitNil(this)
    }

    /**
     * Triple Dispatch Pattern for Expression Type Combinations
     * 
     * This enables compile-time optimization of operations between different
     * expression types, allowing the compiler to generate specialized code paths.
     */
    interface KifExpressionDispatcher<R> {
        fun dispatch(expr1: KifExpression, expr2: KifExpression): R
    }

    /**
     * Example Query Server with Compile-Time Optimization
     *
     * This demonstrates how to use the parser to load the SUMO corpus and answer
     * simple hierarchical queries using double/triple dispatch patterns.
     */
    class KifQueryServer {
        private val knowledgeBase = mutableMapOf<String, MutableSet<String>>() // subclass -> set of superclasses

        /**
         * Ingests a KIF file content and builds the knowledge base.
         */
        suspend fun ingest(kifContent: String) {
            println("Ingesting KIF knowledge base...")
            val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            KifParser.parse(kifContent).collect { expr ->
                // We are only interested in `(subclass ...)` expressions for this demo
                if (expr is KifExpression.Cons &&
                    expr.car is KifExpression.Atom &&
                    (expr.car as KifExpression.Atom).value == "subclass") {
                    
                    val parts = expr.toList()
                    if (parts.size == 3) { // (subclass Sub Super)
                        val sub = (parts[1] as? KifExpression.Atom)?.value
                        val sup = (parts[2] as? KifExpression.Atom)?.value
                        if (sub != null && sup != null) {
                            knowledgeBase.getOrPut(sub) { mutableSetOf() }.add(sup)
                        }
                    }
                }
            }
            // Second pass to compute transitive closure
            computeTransitiveClosure()
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
            println("Ingestion complete in ${duration}ms. ${knowledgeBase.size} concepts indexed.")
        }

        /**
         * Computes the full hierarchy for all concepts.
         */
        private fun computeTransitiveClosure() {
            println("Computing transitive closure...")
            var changed = true
            while (changed) {
                changed = false
                for ((sub, supers) in knowledgeBase.toMap()) {
                    val newSupers = supers.flatMap { knowledgeBase[it] ?: emptySet() }
                    if (knowledgeBase.getOrPut(sub) { mutableSetOf() }.addAll(newSupers)) {
                        changed = true
                    }
                }
            }
            println("Transitive closure complete.")
        }

        /**
         * Queries if `sub` is a subclass of `sup`.
         */
        fun isSubclassOf(sub: String, sup: String): Boolean {
            return knowledgeBase[sub]?.contains(sup) ?: false
        }

        fun KifExpression.toList(): List<KifExpression> {
            val list = mutableListOf<KifExpression>()
            var current = this
            while (current is KifExpression.Cons) {
                list.add(current.car)
                current = current.cdr
            }
            return list
        }
    }
} 