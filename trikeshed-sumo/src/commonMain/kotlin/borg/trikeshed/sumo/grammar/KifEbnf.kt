@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.grammar

import borg.trikeshed.lib.*

/**
 * KIF (Knowledge Interchange Format) EBNF Grammar
 * 
 * This defines the formal grammar for KIF expressions used in SUMO.
 * The grammar is designed to be string-averse and SIMD-friendly.
 * 
 * EBNF Grammar:
 * 
 * kif_file     = { s_expression | comment | whitespace }
 * s_expression = "(" { term } ")"
 * term         = constant | variable | s_expression | string
 * constant     = word
 * variable     = "?" word
 * string       = '"' { char | escape } '"'
 * word         = letter { letter | digit | "-" | "_" }
 * letter       = "A" | "B" | ... | "Z" | "a" | "b" | ... | "z"
 * digit        = "0" | "1" | ... | "9"
 * char         = any character except '"' or '\'
 * escape       = '\' char
 * comment      = ";" { char } newline
 * whitespace   = space | tab | newline | carriage_return
 * newline      = "\n"
 * space        = " "
 * tab          = "\t"
 * carriage_return = "\r"
 */
object KifEbnf {
    
    /**
     * Grammar rules as Join-based patterns
     * Each rule is a Join<Pattern, Action> where Pattern defines recognition
     * and Action defines the semantic action
     */
    
    // Core grammar rules using Join composition
    val KIF_FILE: GrammarRule = "kif_file" j { tokens ->
        tokens.filter { it !is KifToken.Whitespace && it !is KifToken.Comment }
    }
    
    val S_EXPRESSION: GrammarRule = "s_expression" j { tokens ->
        when {
            tokens.firstOrNull() is KifToken.ParenOpen -> {
                val (expr, remaining) = parseSExpression(tokens)
                expr to remaining
            }
            else -> null
        }
    }
    
    val TERM: GrammarRule = "term" j { tokens ->
        when (val token = tokens.firstOrNull()) {
            is KifToken.Symbol -> {
                val term = parseTerm(token)
                term to tokens.drop(1)
            }
            is KifToken.Str -> {
                val term = parseString(token)
                term to tokens.drop(1)
            }
            is KifToken.ParenOpen -> {
                val (expr, remaining) = parseSExpression(tokens)
                expr to remaining
            }
            else -> null
        }
    }
    
    val CONSTANT: GrammarRule = "constant" j { tokens ->
        when (val token = tokens.firstOrNull()) {
            is KifToken.Symbol -> {
                if (!token.value.startsWith("?")) {
                    val constant = parseConstant(token)
                    constant to tokens.drop(1)
                } else null
            }
            else -> null
        }
    }
    
    val VARIABLE: GrammarRule = "variable" j { tokens ->
        when (val token = tokens.firstOrNull()) {
            is KifToken.Symbol -> {
                if (token.value.startsWith("?")) {
                    val variable = parseVariable(token)
                    variable to tokens.drop(1)
                } else null
            }
            else -> null
        }
    }
    
    val STRING: GrammarRule = "string" j { tokens ->
        when (val token = tokens.firstOrNull()) {
            is KifToken.Str -> {
                val string = parseString(token)
                string to tokens.drop(1)
            }
            else -> null
        }
    }
    
    /**
     * Character classification for SIMD-friendly scanning
     */
    object CharClasses {
        // Character class lookup table for SIMD optimization
        val LETTER_MASK = 0x01
        val DIGIT_MASK = 0x02
        val WHITESPACE_MASK = 0x04
        val SPECIAL_MASK = 0x08
        val QUOTE_MASK = 0x10
        val PAREN_MASK = 0x20
        val COMMENT_MASK = 0x40
        
        // SIMD-friendly character classification table
        val CHAR_CLASS_TABLE = IntArray(256).apply {
            // Letters
            for (i in 'a'.code..'z'.code) this[i] = LETTER_MASK
            for (i in 'A'.code..'Z'.code) this[i] = LETTER_MASK
            
            // Digits
            for (i in '0'.code..'9'.code) this[i] = DIGIT_MASK
            
            // Whitespace
            this[' '.code] = WHITESPACE_MASK
            this['\t'.code] = WHITESPACE_MASK
            this['\r'.code] = WHITESPACE_MASK
            this['\n'.code] = WHITESPACE_MASK
            
            // Special characters
            this['-'.code] = SPECIAL_MASK
            this['_'.code] = SPECIAL_MASK
            this['?'.code] = SPECIAL_MASK
            
            // Quotes and parentheses
            this['"'.code] = QUOTE_MASK
            this['('.code] = PAREN_MASK
            this[')'.code] = PAREN_MASK
            
            // Comments
            this[';'.code] = COMMENT_MASK
        }
        
        fun isLetter(char: Char): Boolean = 
            CHAR_CLASS_TABLE[char.code] and LETTER_MASK != 0
        
        fun isDigit(char: Char): Boolean = 
            CHAR_CLASS_TABLE[char.code] and DIGIT_MASK != 0
        
        fun isWhitespace(char: Char): Boolean = 
            CHAR_CLASS_TABLE[char.code] and WHITESPACE_MASK != 0
        
        fun isSpecial(char: Char): Boolean = 
            CHAR_CLASS_TABLE[char.code] and SPECIAL_MASK != 0
        
        fun isQuote(char: Char): Boolean = 
            CHAR_CLASS_TABLE[char.code] and QUOTE_MASK != 0
        
        fun isParen(char: Char): Boolean = 
            CHAR_CLASS_TABLE[char.code] and PAREN_MASK != 0
        
        fun isComment(char: Char): Boolean = 
            CHAR_CLASS_TABLE[char.code] and COMMENT_MASK != 0
    }
    
    /**
     * Grammar rule type using Join composition
     */
    typealias GrammarRule = Join<String, (List<KifToken>) -> ParseResult?>
    
    /**
     * Parse result using Join composition
     */
    typealias ParseResult = Join<KifExpression, List<KifToken>>
    
    // Parser implementation functions
    private fun parseSExpression(tokens: List<KifToken>): ParseResult? {
        if (tokens.firstOrNull() !is KifToken.ParenOpen) return null
        
        var currentIndex = 1
        val elements = mutableListOf<KifExpression>()
        
        while (currentIndex < tokens.size && tokens[currentIndex] !is KifToken.ParenClose) {
            when (val token = tokens[currentIndex]) {
                is KifToken.ParenOpen -> {
                    val (nestedExpr, remaining) = parseSExpression(tokens.drop(currentIndex)) ?: return null
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
    
    private fun parseTerm(token: KifToken.Symbol): KifExpression {
        return KifExpression.Atom(token.value)
    }
    
    private fun parseString(token: KifToken.Str): KifExpression {
        return KifExpression.Str(token.value)
    }
    
    private fun parseConstant(token: KifToken.Symbol): KifExpression {
        return KifExpression.Atom(token.value)
    }
    
    private fun parseVariable(token: KifToken.Symbol): KifExpression {
        return KifExpression.Atom(token.value)
    }
}

// Import the KIF expression types from the parser
import borg.trikeshed.sumo.kif.KifExpression
import borg.trikeshed.sumo.kif.KifToken 