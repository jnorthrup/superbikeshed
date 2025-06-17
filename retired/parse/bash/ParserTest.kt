package parse.bash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParserTest {
    @Test
    fun `test simple list expression`() {
        val input = "{a,b,c}"
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `test numeric sequence`() {
        val input = "{1..3}"
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("1", "2", "3"), result)
    }

    @Test
    fun `test reverse numeric sequence`() {
        val input = "{3..1}"
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("3", "2", "1"), result)
    }

    @Test
    fun `test character sequence`() {
        val input = "{a..c}"
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `test reverse character sequence`() {
        val input = "{c..a}"
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("c", "b", "a"), result)
    }

    @Test
    fun `test prefix and suffix`() {
        val input = "pre{a,b}suf"
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("preasuf", "prebsuf"), result)
    }

    @Test
    fun `test nested expressions`() {
        val input = "a{b,c{d,e}}"
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("ab", "acd", "ace"), result)
    }

    @Test
    fun `test complex expression`() {
        val input = "file-{1..3}-{a,b}.txt"
        val result = BashBraceParser.of(input).parse()
        assertEquals(
            listOf(
                "file-1-a.txt",
                "file-1-b.txt",
                "file-2-a.txt",
                "file-2-b.txt",
                "file-3-a.txt",
                "file-3-b.txt"
            ),
            result
        )
    }

    @Test
    fun `test invalid sequence`() {
        val input = "{1..a}"
        assertFailsWith<IllegalArgumentException> {
            BashBraceParser.of(input).parse()
        }
    }

    @Test
    fun `test invalid character sequence`() {
        val input = "{ab..c}"
        assertFailsWith<IllegalArgumentException> {
            BashBraceParser.of(input).parse()
        }
    }

    @Test
    fun `test malformed expression`() {
        val input = "{a,b"
        assertFailsWith<IllegalArgumentException> {
            BashBraceParser.of(input).parse()
        }
    }

    @Test
    fun `test empty expression`() {
        val input = "{}"
        assertFailsWith<IllegalArgumentException> {
            BashBraceParser.of(input).parse()
        }
    }

    @Test
    fun `test whitespace handling`() {
        val input = " { a , b } "
        val result = BashBraceParser.of(input).parse()
        assertEquals(listOf("a", "b"), result)
    }
} 