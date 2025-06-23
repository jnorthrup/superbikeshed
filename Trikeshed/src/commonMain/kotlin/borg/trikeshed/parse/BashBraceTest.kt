package borg.trikeshed.parse

import kotlin.test.Test
import kotlin.test.assertEquals

class BashBraceTest {
    @Test
    fun `test simple brace expression`() {
        val input = "{a,b}"
        val tokens = BashBrace.of(input).scanTokens()
        
        assertEquals(6, tokens.size)
        assertEquals(Token(TokenType.LBRACE, "{", 0), tokens[0])
        assertEquals(Token(TokenType.LITERAL, "a", 1), tokens[1])
        assertEquals(Token(TokenType.COMMA, ",", 2), tokens[2])
        assertEquals(Token(TokenType.LITERAL, "b", 3), tokens[3])
        assertEquals(Token(TokenType.RBRACE, "}", 4), tokens[4])
        assertEquals(Token(TokenType.EOF, "", 5), tokens[5])
    }

    @Test
    fun `test sequence expression`() {
        val input = "{1..3}"
        val tokens = BashBrace.of(input).scanTokens()
        
        assertEquals(6, tokens.size)
        assertEquals(Token(TokenType.LBRACE, "{", 0), tokens[0])
        assertEquals(Token(TokenType.LITERAL, "1", 1), tokens[1])
        assertEquals(Token(TokenType.SEQUENCE, "..", 2), tokens[2])
        assertEquals(Token(TokenType.LITERAL, "3", 4), tokens[3])
        assertEquals(Token(TokenType.RBRACE, "}", 5), tokens[4])
        assertEquals(Token(TokenType.EOF, "", 6), tokens[5])
    }

    @Test
    fun `test nested braces`() {
        val input = "a{b,c{d,e}}"
        val tokens = BashBrace.of(input).scanTokens()
        
        assertEquals(12, tokens.size)
        assertEquals(Token(TokenType.LITERAL, "a", 0), tokens[0])
        assertEquals(Token(TokenType.LBRACE, "{", 1), tokens[1])
        assertEquals(Token(TokenType.LITERAL, "b", 2), tokens[2])
        assertEquals(Token(TokenType.COMMA, ",", 3), tokens[3])
        assertEquals(Token(TokenType.LITERAL, "c", 4), tokens[4])
        assertEquals(Token(TokenType.LBRACE, "{", 5), tokens[5])
        assertEquals(Token(TokenType.LITERAL, "d", 6), tokens[6])
        assertEquals(Token(TokenType.COMMA, ",", 7), tokens[7])
        assertEquals(Token(TokenType.LITERAL, "e", 8), tokens[8])
        assertEquals(Token(TokenType.RBRACE, "}", 9), tokens[9])
        assertEquals(Token(TokenType.RBRACE, "}", 10), tokens[10])
        assertEquals(Token(TokenType.EOF, "", 11), tokens[11])
    }

    @Test
    fun `test escaped characters`() {
        val input = "a\\{b,c\\}"
        val tokens = BashBrace.of(input).scanTokens()
        
        assertEquals(7, tokens.size)
        assertEquals(Token(TokenType.LITERAL, "a", 0), tokens[0])
        assertEquals(Token(TokenType.LITERAL, "{", 1), tokens[1])
        assertEquals(Token(TokenType.LITERAL, "b", 3), tokens[2])
        assertEquals(Token(TokenType.COMMA, ",", 4), tokens[3])
        assertEquals(Token(TokenType.LITERAL, "c", 5), tokens[4])
        assertEquals(Token(TokenType.LITERAL, "}", 6), tokens[5])
        assertEquals(Token(TokenType.EOF, "", 8), tokens[6])
    }

    @Test
    fun `test whitespace handling`() {
        val input = " { a , b } "
        val tokens = BashBrace.of(input).scanTokens()
        
        assertEquals(6, tokens.size)
        assertEquals(Token(TokenType.LBRACE, "{", 1), tokens[0])
        assertEquals(Token(TokenType.LITERAL, "a", 3), tokens[1])
        assertEquals(Token(TokenType.COMMA, ",", 5), tokens[2])
        assertEquals(Token(TokenType.LITERAL, "b", 7), tokens[3])
        assertEquals(Token(TokenType.RBRACE, "}", 9), tokens[4])
        assertEquals(Token(TokenType.EOF, "", 11), tokens[5])
    }

    @Test
    fun `test character sequence`() {
        val input = "{a..c}"
        val tokens = BashBrace.of(input).scanTokens()
        
        assertEquals(6, tokens.size)
        assertEquals(Token(TokenType.LBRACE, "{", 0), tokens[0])
        assertEquals(Token(TokenType.LITERAL, "a", 1), tokens[1])
        assertEquals(Token(TokenType.SEQUENCE, "..", 2), tokens[2])
        assertEquals(Token(TokenType.LITERAL, "c", 4), tokens[3])
        assertEquals(Token(TokenType.RBRACE, "}", 5), tokens[4])
        assertEquals(Token(TokenType.EOF, "", 6), tokens[5])
    }

    @Test
    fun `test error handling`() {
        val input = "a\\"
        val tokens = BashBrace.of(input).scanTokens()
        
        assertEquals(2, tokens.size)
        assertEquals(Token(TokenType.LITERAL, "a", 0), tokens[0])
        assertEquals(Token(TokenType.ERROR, "unexpected end of input after escape", 1), tokens[1])
    }
} 