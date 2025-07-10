@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.xswo

import borg.trikeshed.lib.*
import borg.trikeshed.sumo.grammar.*
import borg.trikeshed.sumo.kif.*

/**
 * xSWO Boost Spirit Grammar Integration with BBCursive
 * 
 * Absorbs xSWO Boost Spirit grammars (SQL-2003 BNF, SPARQL, etc.) into bbcursive patterns
 * using Join composition and register-at-a-time scanning.
 * 
 * Based on xSWO s2k3.bnf and sparql.cpp files.
 */

// === SQL-2003 GRAMMAR INTEGRATION ===

/**
 * SQL-2003 grammar rules using Join composition
 * Based on xSWO s2k3.bnf
 */
object Sql2003Grammar {
    
    // Character classes using Join composition
    val SQL_TERMINAL_CHAR: GrammarRule = "sql_terminal_character" j { tokens ->
        tokens.firstOrNull()?.let { token ->
            when (token) {
                is KifToken.Symbol -> {
                    val char = token.value.firstOrNull()
                    if (char != null && isSqlLanguageChar(char)) {
                        char j tokens.drop(1)
                    } else null
                }
                else -> null
            }
        }
    }
    
    val SQL_LANGUAGE_CHAR: GrammarRule = "sql_language_character" j { tokens ->
        tokens.firstOrNull()?.let { token ->
            when (token) {
                is KifToken.Symbol -> {
                    val char = token.value.firstOrNull()
                    if (char != null && isSqlLanguageChar(char)) {
                        char j tokens.drop(1)
                    } else null
                }
                else -> null
            }
        }
    }
    
    val SIMPLE_LATIN_LETTER: GrammarRule = "simple_latin_letter" j { tokens ->
        tokens.firstOrNull()?.let { token ->
            when (token) {
                is KifToken.Symbol -> {
                    val char = token.value.firstOrNull()
                    if (char != null && char.isLetter()) {
                        char j tokens.drop(1)
                    } else null
                }
                else -> null
            }
        }
    }
    
    val DIGIT: GrammarRule = "digit" j { tokens ->
        tokens.firstOrNull()?.let { token ->
            when (token) {
                is KifToken.Symbol -> {
                    val char = token.value.firstOrNull()
                    if (char != null && char.isDigit()) {
                        char j tokens.drop(1)
                    } else null
                }
                else -> null
            }
        }
    }
    
    val SQL_SPECIAL_CHAR: GrammarRule = "sql_special_character" j { tokens ->
        tokens.firstOrNull()?.let { token ->
            when (token) {
                is KifToken.Symbol -> {
                    val char = token.value.firstOrNull()
                    if (char != null && isSqlSpecialChar(char)) {
                        char j tokens.drop(1)
                    } else null
                }
                else -> null
            }
        }
    }
    
    // SQL statement rules
    val SQL_STATEMENT: GrammarRule = "sql_statement" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (stmt, remaining) = parseSqlStatement(tokens)
                stmt to remaining
            }
            else -> null
        }
    }
    
    val SELECT_STATEMENT: GrammarRule = "select_statement" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (stmt, remaining) = parseSelectStatement(tokens)
                stmt to remaining
            }
            else -> null
        }
    }
    
    val INSERT_STATEMENT: GrammarRule = "insert_statement" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (stmt, remaining) = parseInsertStatement(tokens)
                stmt to remaining
            }
            else -> null
        }
    }
    
    val UPDATE_STATEMENT: GrammarRule = "update_statement" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (stmt, remaining) = parseUpdateStatement(tokens)
                stmt to remaining
            }
            else -> null
        }
    }
    
    val DELETE_STATEMENT: GrammarRule = "delete_statement" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (stmt, remaining) = parseDeleteStatement(tokens)
                stmt to remaining
            }
            else -> null
        }
    }
    
    // Helper functions for character classification
    private fun isSqlLanguageChar(char: Char): Boolean {
        return char.isLetterOrDigit() || isSqlSpecialChar(char)
    }
    
    private fun isSqlSpecialChar(char: Char): Boolean {
        return char in setOf(' ', '"', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/', ':', ';', '<', '=', '>', '?', '_', '|')
    }
    
    // Parser implementation functions
    private fun parseSqlStatement(tokens: List<KifToken>): ParseResult? {
        if (tokens.firstOrNull() !is KifToken.ParenOpen) return null
        
        var currentIndex = 1
        val elements = mutableListOf<KifExpression>()
        
        while (currentIndex < tokens.size && tokens[currentIndex] !is KifToken.ParenClose) {
            when (val token = tokens[currentIndex]) {
                is KifToken.ParenOpen -> {
                    val (nestedExpr, remaining) = parseSqlStatement(tokens.drop(currentIndex)) ?: return null
                    elements.add(nestedExpr)
                    currentIndex += tokens.size - remaining.size
                }
                is KifToken.Symbol -> {
                    elements.add(KifExpression.Atom(token.value))
                    currentIndex++
                }
                is KifToken.Str -> {
                    elements.add(KifExpression.Str(token.value))
                    currentIndex++
                }
                else -> currentIndex++
            }
        }
        
        if (currentIndex >= tokens.size || tokens[currentIndex] !is KifToken.ParenClose) {
            return null
        }
        
        val expression = elements.reversed().fold(KifExpression.Nil as KifExpression) { acc, el ->
            KifExpression.Cons(el, acc)
        }
        
        return expression j tokens.drop(currentIndex + 1)
    }
    
    private fun parseSelectStatement(tokens: List<KifToken>): ParseResult? {
        // Simplified SELECT statement parsing
        return parseSqlStatement(tokens)
    }
    
    private fun parseInsertStatement(tokens: List<KifToken>): ParseResult? {
        // Simplified INSERT statement parsing
        return parseSqlStatement(tokens)
    }
    
    private fun parseUpdateStatement(tokens: List<KifToken>): ParseResult? {
        // Simplified UPDATE statement parsing
        return parseSqlStatement(tokens)
    }
    
    private fun parseDeleteStatement(tokens: List<KifToken>): ParseResult? {
        // Simplified DELETE statement parsing
        return parseSqlStatement(tokens)
    }
}

// === SPARQL GRAMMAR INTEGRATION ===

/**
 * SPARQL grammar rules using Join composition
 * Based on xSWO sparql.cpp
 */
object SparqlGrammar {
    
    // SPARQL statement types
    val SPARQL_QUERY: GrammarRule = "sparql_query" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (query, remaining) = parseSparqlQuery(tokens)
                query to remaining
            }
            else -> null
        }
    }
    
    val SELECT_QUERY: GrammarRule = "select_query" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (query, remaining) = parseSelectQuery(tokens)
                query to remaining
            }
            else -> null
        }
    }
    
    val CONSTRUCT_QUERY: GrammarRule = "construct_query" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (query, remaining) = parseConstructQuery(tokens)
                query to remaining
            }
            else -> null
        }
    }
    
    val ASK_QUERY: GrammarRule = "ask_query" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (query, remaining) = parseAskQuery(tokens)
                query to remaining
            }
            else -> null
        }
    }
    
    val DESCRIBE_QUERY: GrammarRule = "describe_query" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (query, remaining) = parseDescribeQuery(tokens)
                query to remaining
            }
            else -> null
        }
    }
    
    // SPARQL pattern rules
    val TRIPLE_PATTERN: GrammarRule = "triple_pattern" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (pattern, remaining) = parseTriplePattern(tokens)
                pattern to remaining
            }
            else -> null
        }
    }
    
    val GRAPH_PATTERN: GrammarRule = "graph_pattern" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (pattern, remaining) = parseGraphPattern(tokens)
                pattern to remaining
            }
            else -> null
        }
    }
    
    // Parser implementation functions
    private fun parseSparqlQuery(tokens: List<KifToken>): ParseResult? {
        if (tokens.firstOrNull() !is KifToken.ParenOpen) return null
        
        var currentIndex = 1
        val elements = mutableListOf<KifExpression>()
        
        while (currentIndex < tokens.size && tokens[currentIndex] !is KifToken.ParenClose) {
            when (val token = tokens[currentIndex]) {
                is KifToken.ParenOpen -> {
                    val (nestedExpr, remaining) = parseSparqlQuery(tokens.drop(currentIndex)) ?: return null
                    elements.add(nestedExpr)
                    currentIndex += tokens.size - remaining.size
                }
                is KifToken.Symbol -> {
                    elements.add(KifExpression.Atom(token.value))
                    currentIndex++
                }
                is KifToken.Str -> {
                    elements.add(KifExpression.Str(token.value))
                    currentIndex++
                }
                else -> currentIndex++
            }
        }
        
        if (currentIndex >= tokens.size || tokens[currentIndex] !is KifToken.ParenClose) {
            return null
        }
        
        val expression = elements.reversed().fold(KifExpression.Nil as KifExpression) { acc, el ->
            KifExpression.Cons(el, acc)
        }
        
        return expression j tokens.drop(currentIndex + 1)
    }
    
    private fun parseSelectQuery(tokens: List<KifToken>): ParseResult? {
        // Simplified SELECT query parsing
        return parseSparqlQuery(tokens)
    }
    
    private fun parseConstructQuery(tokens: List<KifToken>): ParseResult? {
        // Simplified CONSTRUCT query parsing
        return parseSparqlQuery(tokens)
    }
    
    private fun parseAskQuery(tokens: List<KifToken>): ParseResult? {
        // Simplified ASK query parsing
        return parseSparqlQuery(tokens)
    }
    
    private fun parseDescribeQuery(tokens: List<KifToken>): ParseResult? {
        // Simplified DESCRIBE query parsing
        return parseSparqlQuery(tokens)
    }
    
    private fun parseTriplePattern(tokens: List<KifToken>): ParseResult? {
        // Simplified triple pattern parsing
        return parseSparqlQuery(tokens)
    }
    
    private fun parseGraphPattern(tokens: List<KifToken>): ParseResult? {
        // Simplified graph pattern parsing
        return parseSparqlQuery(tokens)
    }
}

// === BBCURSIVE GRAMMAR INTEGRATION ===

/**
 * BBCursive grammar scanner for xSWO Boost Spirit grammars
 */
object XswoBbcursiveGrammarScanner {
    
    /**
     * Scan SQL-2003 grammar using register-at-a-time pattern
     */
    fun scanSql2003Grammar(tokens: List<KifToken>): Indexed<KifExpression> {
        val expressions = mutableListOf<KifExpression>()
        var currentTokens = tokens
        
        while (currentTokens.isNotEmpty()) {
            val result = Sql2003Grammar.SQL_STATEMENT.b(currentTokens)
            if (result != null) {
                expressions.add(result.a)
                currentTokens = result.b
            } else {
                break
            }
        }
        
        return expressions.size j { i -> expressions[i] }
    }
    
    /**
     * Scan SPARQL grammar using register-at-a-time pattern
     */
    fun scanSparqlGrammar(tokens: List<KifToken>): Indexed<KifExpression> {
        val expressions = mutableListOf<KifExpression>()
        var currentTokens = tokens
        
        while (currentTokens.isNotEmpty()) {
            val result = SparqlGrammar.SPARQL_QUERY.b(currentTokens)
            if (result != null) {
                expressions.add(result.a)
                currentTokens = result.b
            } else {
                break
            }
        }
        
        return expressions.size j { i -> expressions[i] }
    }
}

// === TRIPLE DISPATCH FOR GRAMMAR OPERATIONS ===

/**
 * Triple dispatch for xSWO grammar operations
 */
interface XswoGrammarDispatcher<R> {
    fun dispatch(grammar: String, tokens: List<KifToken>, operation: String): R
}

/**
 * Example triple dispatch implementation for xSWO grammars
 */
object XswoGrammarProcessor : XswoGrammarDispatcher<Indexed<KifExpression>?> {
    override fun dispatch(grammar: String, tokens: List<KifToken>, operation: String): Indexed<KifExpression>? {
        return when {
            grammar == "sql2003" && operation == "parse" -> {
                XswoBbcursiveGrammarScanner.scanSql2003Grammar(tokens)
            }
            grammar == "sparql" && operation == "parse" -> {
                XswoBbcursiveGrammarScanner.scanSparqlGrammar(tokens)
            }
            else -> null
        }
    }
}

// === GRAMMAR RULE TYPE ALIASES ===

/**
 * Grammar rule type using Join composition
 */
typealias GrammarRule = Join<String, (List<KifToken>) -> ParseResult?>

/**
 * Parse result using Join composition
 */
typealias ParseResult = Join<KifExpression, List<KifToken>>

// === FACTORY FUNCTIONS ===

/**
 * Create SQL-2003 grammar rule
 */
fun sql2003Rule(name: String, parser: (List<KifToken>) -> ParseResult?): GrammarRule = name j parser

/**
 * Create SPARQL grammar rule
 */
fun sparqlRule(name: String, parser: (List<KifToken>) -> ParseResult?): GrammarRule = name j parser

/**
 * Create combined grammar rule
 */
fun combinedRule(name: String, rules: List<GrammarRule>): GrammarRule = name j { tokens ->
    for (rule in rules) {
        val result = rule.b(tokens)
        if (result != null) return@j result
    }
    null
} 