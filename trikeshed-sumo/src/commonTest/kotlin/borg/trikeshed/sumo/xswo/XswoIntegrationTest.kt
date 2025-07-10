@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.xswo

import borg.trikeshed.lib.*
import borg.trikeshed.sumo.kif.*
import kotlin.test.*

/**
 * TDD Test for xSWO Integration with BBCursive
 * 
 * Tests the absorption of xSWO collections and Boost Spirit grammars
 * into bbcursive patterns using Join composition and register-at-a-time scanning.
 */

class XswoIntegrationTest {
    
    // === HAT-TRIE TESTS ===
    
    @Test
    fun `test HAT-trie insertion and search using bbcursive patterns`() {
        val trie = HatTrie<String>()
        
        // Insert using bbcursive pattern
        trie.insert("hello", "world")
        trie.insert("help", "me")
        trie.insert("hero", "zero")
        
        // Search using bbcursive pattern
        assertEquals("world", trie.search("hello"))
        assertEquals("me", trie.search("help"))
        assertEquals("zero", trie.search("hero"))
        assertNull(trie.search("notfound"))
    }
    
    @Test
    fun `test HAT-trie burst behavior using Join composition`() {
        val trie = HatTrie<Int>()
        
        // Insert enough items to trigger burst
        for (i in 0 until 20) {
            trie.insert("key$i", i)
        }
        
        // Verify all items can still be found
        for (i in 0 until 20) {
            assertEquals(i, trie.search("key$i"))
        }
    }
    
    // === QUADBAG TESTS ===
    
    @Test
    fun `test quadbag insertion and querying using bbcursive patterns`() {
        val quadBag = QuadBag<String>()
        
        // Insert quads using bbcursive pattern
        quadBag.insert(Quad("alice", "knows", "bob", "context1"))
        quadBag.insert(Quad("bob", "knows", "charlie", "context1"))
        quadBag.insert(Quad("alice", "likes", "pizza", "context2"))
        
        // Query by subject using bbcursive pattern
        val aliceQuads = quadBag.findBySubject("alice")
        assertEquals(2, aliceQuads.size)
        
        // Query by predicate using bbcursive pattern
        val knowsQuads = quadBag.findByPredicate("knows")
        assertEquals(2, knowsQuads.size)
        
        // SPARQL-like query using bbcursive pattern
        val aliceKnows = quadBag.query(subject = "alice", predicate = "knows")
        assertEquals(1, aliceKnows.size)
        assertEquals("bob", aliceKnows[0].`object`)
    }
    
    @Test
    fun `test quadbag Join composition conversion`() {
        val quad = Quad("s", "p", "o", "c")
        val join = quad.toJoin()
        
        // Verify Join composition
        assertEquals("s", join.a.a)
        assertEquals("p", join.a.b)
        assertEquals("o", join.b.a)
        assertEquals("c", join.b.b)
        
        // Verify conversion back
        val reconstructed = Quad.fromJoin(join)
        assertEquals(quad, reconstructed)
    }
    
    // === ARRAY HASH TESTS ===
    
    @Test
    fun `test array hash operations using bbcursive patterns`() {
        val arrayHash = ArrayHash<String>(8)
        
        // Insert using bbcursive pattern
        assertTrue(arrayHash.insert("key1", "value1"))
        assertTrue(arrayHash.insert("key2", "value2"))
        assertFalse(arrayHash.insert("key1", "value1_updated")) // Already exists
        
        // Find using bbcursive pattern
        assertEquals("value1_updated", arrayHash.find("key1"))
        assertEquals("value2", arrayHash.find("key2"))
        assertNull(arrayHash.find("key3"))
        
        // Remove using bbcursive pattern
        assertTrue(arrayHash.remove("key1"))
        assertNull(arrayHash.find("key1"))
        assertFalse(arrayHash.remove("key1")) // Already removed
    }
    
    @Test
    fun `test array hash entries using Join composition`() {
        val arrayHash = ArrayHash<String>(4)
        arrayHash.insert("a", "1")
        arrayHash.insert("b", "2")
        
        val entries = arrayHash.entries()
        assertEquals(2, entries.size)
        
        // Verify Join composition
        val entry1 = entries[0]
        assertEquals("a", entry1.a)
        assertEquals("1", entry1.b)
        
        val entry2 = entries[1]
        assertEquals("b", entry2.a)
        assertEquals("2", entry2.b)
    }
    
    // === HAT SET TESTS ===
    
    @Test
    fun `test hat set operations using bbcursive patterns`() {
        val hatSet = HatSet<String>()
        
        // Insert using bbcursive pattern
        assertTrue(hatSet.insert("value1"))
        assertTrue(hatSet.insert("value2"))
        assertFalse(hatSet.insert("value1")) // Already exists
        
        // Contains using bbcursive pattern
        assertTrue(hatSet.contains("value1"))
        assertTrue(hatSet.contains("value2"))
        assertFalse(hatSet.contains("value3"))
    }
    
    // === SQL-2003 GRAMMAR TESTS ===
    
    @Test
    fun `test SQL-2003 grammar parsing using bbcursive patterns`() {
        val sqlTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("SELECT", 1),
            KifToken.Symbol("*", 8),
            KifToken.Symbol("FROM", 10),
            KifToken.Symbol("users", 15),
            KifToken.ParenClose(21)
        )
        
        val result = Sql2003Grammar.SQL_STATEMENT.b(sqlTokens)
        assertNotNull(result)
        
        val expression = result.a
        assertTrue(expression is KifExpression.Cons)
        
        val elements = expression.toList()
        assertEquals(4, elements.size)
        assertEquals("SELECT", (elements[0] as KifExpression.Atom).value)
        assertEquals("*", (elements[1] as KifExpression.Atom).value)
        assertEquals("FROM", (elements[2] as KifExpression.Atom).value)
        assertEquals("users", (elements[3] as KifExpression.Atom).value)
    }
    
    @Test
    fun `test SQL-2003 character classification using Join composition`() {
        val digitToken = listOf(KifToken.Symbol("5", 0))
        val letterToken = listOf(KifToken.Symbol("A", 0))
        val specialToken = listOf(KifToken.Symbol("+", 0))
        val invalidToken = listOf(KifToken.Symbol("@", 0))
        
        // Test digit classification
        val digitResult = Sql2003Grammar.DIGIT.b(digitToken)
        assertNotNull(digitResult)
        assertEquals('5', digitResult.a)
        
        // Test letter classification
        val letterResult = Sql2003Grammar.SIMPLE_LATIN_LETTER.b(letterToken)
        assertNotNull(letterResult)
        assertEquals('A', letterResult.a)
        
        // Test special character classification
        val specialResult = Sql2003Grammar.SQL_SPECIAL_CHAR.b(specialToken)
        assertNotNull(specialResult)
        assertEquals('+', specialResult.a)
        
        // Test invalid character
        val invalidResult = Sql2003Grammar.SQL_SPECIAL_CHAR.b(invalidToken)
        assertNull(invalidResult)
    }
    
    // === SPARQL GRAMMAR TESTS ===
    
    @Test
    fun `test SPARQL grammar parsing using bbcursive patterns`() {
        val sparqlTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("SELECT", 1),
            KifToken.Symbol("?s", 8),
            KifToken.Symbol("?p", 11),
            KifToken.Symbol("?o", 14),
            KifToken.ParenOpen(17),
            KifToken.Symbol("WHERE", 18),
            KifToken.ParenOpen(24),
            KifToken.Symbol("?s", 25),
            KifToken.Symbol("?p", 28),
            KifToken.Symbol("?o", 31),
            KifToken.ParenClose(34),
            KifToken.ParenClose(35),
            KifToken.ParenClose(36)
        )
        
        val result = SparqlGrammar.SPARQL_QUERY.b(sparqlTokens)
        assertNotNull(result)
        
        val expression = result.a
        assertTrue(expression is KifExpression.Cons)
        
        val elements = expression.toList()
        assertEquals(3, elements.size)
        assertEquals("SELECT", (elements[0] as KifExpression.Atom).value)
        assertEquals("?s", (elements[1] as KifExpression.Atom).value)
        assertEquals("?p", (elements[2] as KifExpression.Atom).value)
    }
    
    // === BBCURSIVE SCANNER TESTS ===
    
    @Test
    fun `test xSWO bbcursive scanner for collections`() {
        val trie = HatTrie<String>()
        trie.insert("test", "value")
        
        val quadBag = QuadBag<String>()
        quadBag.insert(Quad("s", "p", "o", "c"))
        
        val arrayHash = ArrayHash<String>()
        arrayHash.insert("key", "value")
        
        // Test scanner operations
        val trieScan = XswoBbcursiveScanner.scanHatTrie(trie)
        val quadBagScan = XswoBbcursiveScanner.scanQuadBag(quadBag)
        val arrayHashScan = XswoBbcursiveScanner.scanArrayHash(arrayHash)
        
        // Verify scanner returns Indexed using Join composition
        assertTrue(trieScan is Indexed<*>)
        assertTrue(quadBagScan is Indexed<*>)
        assertTrue(arrayHashScan is Indexed<*>)
    }
    
    @Test
    fun `test xSWO bbcursive grammar scanner`() {
        val sqlTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("SELECT", 1),
            KifToken.Symbol("*", 8),
            KifToken.ParenClose(10)
        )
        
        val sparqlTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("ASK", 1),
            KifToken.ParenOpen(5),
            KifToken.Symbol("?s", 6),
            KifToken.Symbol("?p", 9),
            KifToken.Symbol("?o", 12),
            KifToken.ParenClose(15),
            KifToken.ParenClose(16)
        )
        
        // Test SQL-2003 grammar scanner
        val sqlResults = XswoBbcursiveGrammarScanner.scanSql2003Grammar(sqlTokens)
        assertEquals(1, sqlResults.size)
        
        // Test SPARQL grammar scanner
        val sparqlResults = XswoBbcursiveGrammarScanner.scanSparqlGrammar(sparqlTokens)
        assertEquals(1, sparqlResults.size)
    }
    
    // === TRIPLE DISPATCH TESTS ===
    
    @Test
    fun `test xSWO collection triple dispatch`() {
        val trie = HatTrie<String>()
        trie.insert("test", "value")
        
        val quadBag = QuadBag<String>()
        quadBag.insert(Quad("s", "p", "o", "c"))
        
        val arrayHash = ArrayHash<String>()
        arrayHash.insert("key", "value")
        
        // Test triple dispatch for collections
        val trieResult = XswoCollectionProcessor.dispatch(trie, "search", listOf("test"))
        assertEquals("value", trieResult)
        
        val quadBagResult = XswoCollectionProcessor.dispatch(quadBag, "query", listOf("s", null, null, null))
        assertNotNull(quadBagResult)
        
        val arrayHashResult = XswoCollectionProcessor.dispatch(arrayHash, "find", listOf("key"))
        assertEquals("value", arrayHashResult)
    }
    
    @Test
    fun `test xSWO grammar triple dispatch`() {
        val sqlTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("SELECT", 1),
            KifToken.Symbol("*", 8),
            KifToken.ParenClose(10)
        )
        
        val sparqlTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("ASK", 1),
            KifToken.ParenClose(5)
        )
        
        // Test triple dispatch for grammars
        val sqlResult = XswoGrammarProcessor.dispatch("sql2003", sqlTokens, "parse")
        assertNotNull(sqlResult)
        assertEquals(1, sqlResult.size)
        
        val sparqlResult = XswoGrammarProcessor.dispatch("sparql", sparqlTokens, "parse")
        assertNotNull(sparqlResult)
        assertEquals(1, sparqlResult.size)
    }
    
    // === FACTORY FUNCTION TESTS ===
    
    @Test
    fun `test grammar rule factory functions`() {
        val customParser = { tokens: List<KifToken> ->
            if (tokens.isNotEmpty()) {
                val token = tokens.first()
                if (token is KifToken.Symbol && token.value == "CUSTOM") {
                    KifExpression.Atom("parsed") j tokens.drop(1)
                } else null
            } else null
        }
        
        // Test SQL-2003 rule factory
        val sqlRule = sql2003Rule("custom_rule", customParser)
        assertEquals("custom_rule", sqlRule.a)
        
        val sqlTokens = listOf(KifToken.Symbol("CUSTOM", 0))
        val sqlResult = sqlRule.b(sqlTokens)
        assertNotNull(sqlResult)
        assertEquals("parsed", (sqlResult.a as KifExpression.Atom).value)
        
        // Test SPARQL rule factory
        val sparqlRule = sparqlRule("custom_rule", customParser)
        assertEquals("custom_rule", sparqlRule.a)
        
        val sparqlResult = sparqlRule.b(sqlTokens)
        assertNotNull(sparqlResult)
        assertEquals("parsed", (sparqlResult.a as KifExpression.Atom).value)
        
        // Test combined rule factory
        val combinedRule = combinedRule("combined", listOf(sqlRule, sparqlRule))
        assertEquals("combined", combinedRule.a)
        
        val combinedResult = combinedRule.b(sqlTokens)
        assertNotNull(combinedResult)
        assertEquals("parsed", (combinedResult.a as KifExpression.Atom).value)
    }
    
    // === INTEGRATION TESTS ===
    
    @Test
    fun `test full xSWO integration workflow`() {
        // 1. Create collections using xSWO patterns
        val trie = HatTrie<String>()
        val quadBag = QuadBag<String>()
        val arrayHash = ArrayHash<String>()
        
        // 2. Populate with data
        trie.insert("concept", "Human")
        quadBag.insert(Quad("Human", "subclass", "Mammal", "SUMO"))
        arrayHash.insert("Human", "Mammal")
        
        // 3. Parse grammar using xSWO Boost Spirit patterns
        val grammarTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("subclass", 1),
            KifToken.Symbol("Human", 10),
            KifToken.Symbol("Mammal", 16),
            KifToken.ParenClose(23)
        )
        
        val grammarResult = Sql2003Grammar.SQL_STATEMENT.b(grammarTokens)
        assertNotNull(grammarResult)
        
        // 4. Use bbcursive scanner to process collections
        val trieScan = XswoBbcursiveScanner.scanHatTrie(trie)
        val quadBagScan = XswoBbcursiveScanner.scanQuadBag(quadBag)
        val arrayHashScan = XswoBbcursiveScanner.scanArrayHash(arrayHash)
        
        // 5. Verify all components work together
        assertTrue(trieScan is Indexed<*>)
        assertTrue(quadBagScan is Indexed<*>)
        assertTrue(arrayHashScan is Indexed<*>)
        assertTrue(grammarResult.a is KifExpression)
        
        // 6. Test triple dispatch integration
        val trieQuery = XswoCollectionProcessor.dispatch(trie, "search", listOf("concept"))
        assertEquals("Human", trieQuery)
        
        val quadBagQuery = XswoCollectionProcessor.dispatch(quadBag, "query", listOf("Human", "subclass", null, null))
        assertNotNull(quadBagQuery)
        
        val grammarParse = XswoGrammarProcessor.dispatch("sql2003", grammarTokens, "parse")
        assertNotNull(grammarParse)
    }
} 