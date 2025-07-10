package borg.trikeshed.sumo.kif

import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class KifParserTest {

    @Test
    fun `test tokenize basic symbols`() {
        val input = "(subclass Human Mammal)"
        val tokens = KifParser.tokenize(input)
        
        assertEquals(5, tokens.size)
        assertTrue(tokens[0] is KifToken.ParenOpen)
        assertTrue(tokens[1] is KifToken.Symbol)
        assertEquals("subclass", (tokens[1] as KifToken.Symbol).value)
        assertTrue(tokens[2] is KifToken.Symbol)
        assertEquals("Human", (tokens[2] as KifToken.Symbol).value)
        assertTrue(tokens[3] is KifToken.Symbol)
        assertEquals("Mammal", (tokens[3] as KifToken.Symbol).value)
        assertTrue(tokens[4] is KifToken.ParenClose)
    }

    @Test
    fun `test tokenize with strings`() {
        val input = """(instance "Socrates" Human)"""
        val tokens = KifParser.tokenize(input)
        
        assertEquals(4, tokens.size)
        assertTrue(tokens[0] is KifToken.ParenOpen)
        assertTrue(tokens[1] is KifToken.Str)
        assertEquals("Socrates", (tokens[1] as KifToken.Str).value)
        assertTrue(tokens[2] is KifToken.Symbol)
        assertEquals("Human", (tokens[2] as KifToken.Symbol).value)
        assertTrue(tokens[3] is KifToken.ParenClose)
    }

    @Test
    fun `test tokenize with comments`() {
        val input = """
            ; This is a comment
            (subclass Human Mammal)
        """.trimIndent()
        val tokens = KifParser.tokenize(input)
        
        // Comments should be ignored
        assertEquals(5, tokens.size)
        assertTrue(tokens[0] is KifToken.ParenOpen)
        assertTrue(tokens[1] is KifToken.Symbol)
        assertEquals("subclass", (tokens[1] as KifToken.Symbol).value)
    }

    @Test
    fun `test tokenize with whitespace`() {
        val input = "  (  subclass   Human   Mammal  )  "
        val tokens = KifParser.tokenize(input)
        
        // Only structural tokens should be present
        assertEquals(5, tokens.size)
        assertTrue(tokens[0] is KifToken.ParenOpen)
        assertTrue(tokens[1] is KifToken.Symbol)
        assertTrue(tokens[2] is KifToken.Symbol)
        assertTrue(tokens[3] is KifToken.Symbol)
        assertTrue(tokens[4] is KifToken.ParenClose)
    }

    @Test
    fun `test parse simple expression`() = runBlocking {
        val input = "(subclass Human Mammal)"
        val expressions = KifParser.parse(input).toList()
        
        assertEquals(1, expressions.size)
        val expr = expressions[0]
        assertTrue(expr is KifExpression.Cons)
        
        val list = expr.toList()
        assertEquals(3, list.size)
        assertTrue(list[0] is KifExpression.Atom)
        assertEquals("subclass", (list[0] as KifExpression.Atom).value)
        assertEquals("Human", (list[1] as KifExpression.Atom).value)
        assertEquals("Mammal", (list[2] as KifExpression.Atom).value)
    }

    @Test
    fun `test parse nested expression`() = runBlocking {
        val input = "(and (subclass Human Mammal) (instance Socrates Human))"
        val expressions = KifParser.parse(input).toList()
        
        assertEquals(1, expressions.size)
        val expr = expressions[0]
        assertTrue(expr is KifExpression.Cons)
        
        val list = expr.toList()
        assertEquals(3, list.size)
        assertEquals("and", (list[0] as KifExpression.Atom).value)
        assertTrue(list[1] is KifExpression.Cons) // nested (subclass Human Mammal)
        assertTrue(list[2] is KifExpression.Cons) // nested (instance Socrates Human)
    }

    @Test
    fun `test parse multiple expressions`() = runBlocking {
        val input = """
            (subclass Human Mammal)
            (subclass Mammal Animal)
            (instance Socrates Human)
        """.trimIndent()
        val expressions = KifParser.parse(input).toList()
        
        assertEquals(3, expressions.size)
        assertTrue(expressions[0] is KifExpression.Cons)
        assertTrue(expressions[1] is KifExpression.Cons)
        assertTrue(expressions[2] is KifExpression.Cons)
    }

    @Test
    fun `test parse with strings`() = runBlocking {
        val input = """(instance "Socrates" Human)"""
        val expressions = KifParser.parse(input).toList()
        
        assertEquals(1, expressions.size)
        val expr = expressions[0]
        val list = expr.toList()
        assertEquals(3, list.size)
        assertEquals("instance", (list[0] as KifExpression.Atom).value)
        assertTrue(list[1] is KifExpression.Str)
        assertEquals("Socrates", (list[1] as KifExpression.Str).value)
        assertEquals("Human", (list[2] as KifExpression.Atom).value)
    }

    @Test
    fun `test KifExpression toString`() {
        val atom = KifExpression.Atom("test")
        assertEquals("test", atom.toString())
        
        val str = KifExpression.Str("hello")
        assertEquals("\"hello\"", str.toString())
        
        val cons = KifExpression.Cons(
            KifExpression.Atom("a"),
            KifExpression.Cons(
                KifExpression.Atom("b"),
                KifExpression.Nil
            )
        )
        assertEquals("(a b)", cons.toString())
    }

    @Test
    fun `test KifQueryServer basic functionality`() = runBlocking {
        val sampleKif = """
            (subclass Human Mammal)
            (subclass Mammal Animal)
            (subclass Animal Organism)
            (instance Socrates Human)
        """.trimIndent()

        val server = KifQueryServer()
        server.ingest(sampleKif)

        // Test direct subclass relationships
        assertTrue(server.isSubclassOf("Human", "Mammal"))
        assertTrue(server.isSubclassOf("Mammal", "Animal"))
        assertTrue(server.isSubclassOf("Animal", "Organism"))

        // Test transitive relationships
        assertTrue(server.isSubclassOf("Human", "Animal"))
        assertTrue(server.isSubclassOf("Human", "Organism"))
        assertTrue(server.isSubclassOf("Mammal", "Organism"))

        // Test reverse relationships (should be false)
        assertFalse(server.isSubclassOf("Mammal", "Human"))
        assertFalse(server.isSubclassOf("Animal", "Human"))
        assertFalse(server.isSubclassOf("Organism", "Human"))

        // Test non-existent relationships
        assertFalse(server.isSubclassOf("Human", "NonExistent"))
        assertFalse(server.isSubclassOf("NonExistent", "Human"))
    }

    @Test
    fun `test KifQueryServer with complex hierarchy`() = runBlocking {
        val complexKif = """
            (subclass Dog Mammal)
            (subclass Cat Mammal)
            (subclass Mammal Animal)
            (subclass Bird Animal)
            (subclass Animal Organism)
            (subclass Plant Organism)
        """.trimIndent()

        val server = KifQueryServer()
        server.ingest(complexKif)

        // Test sibling relationships
        assertTrue(server.isSubclassOf("Dog", "Mammal"))
        assertTrue(server.isSubclassOf("Cat", "Mammal"))
        assertFalse(server.isSubclassOf("Dog", "Cat"))
        assertFalse(server.isSubclassOf("Cat", "Dog"))

        // Test cross-branch relationships
        assertTrue(server.isSubclassOf("Dog", "Animal"))
        assertTrue(server.isSubclassOf("Bird", "Animal"))
        assertFalse(server.isSubclassOf("Dog", "Bird"))
        assertFalse(server.isSubclassOf("Bird", "Dog"))

        // Test organism-level relationships
        assertTrue(server.isSubclassOf("Dog", "Organism"))
        assertTrue(server.isSubclassOf("Plant", "Organism"))
        assertFalse(server.isSubclassOf("Dog", "Plant"))
        assertFalse(server.isSubclassOf("Plant", "Dog"))
    }

    @Test
    fun `test parse error handling`() {
        // Test unmatched parenthesis
        assertFailsWith<KifParseException> {
            runBlocking {
                KifParser.parse("(subclass Human Mammal").first()
            }
        }

        // Test empty input
        runBlocking {
            val expressions = KifParser.parse("").toList()
            assertEquals(0, expressions.size)
        }
    }

    @Test
    fun `test KifExpression toList extension`() {
        val cons = KifExpression.Cons(
            KifExpression.Atom("a"),
            KifExpression.Cons(
                KifExpression.Atom("b"),
                KifExpression.Cons(
                    KifExpression.Atom("c"),
                    KifExpression.Nil
                )
            )
        )
        
        val list = cons.toList()
        assertEquals(3, list.size)
        assertEquals("a", (list[0] as KifExpression.Atom).value)
        assertEquals("b", (list[1] as KifExpression.Atom).value)
        assertEquals("c", (list[2] as KifExpression.Atom).value)
    }
} 