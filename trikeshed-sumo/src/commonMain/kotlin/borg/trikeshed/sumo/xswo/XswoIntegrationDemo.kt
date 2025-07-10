@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.sumo.xswo

import borg.trikeshed.lib.*
import borg.trikeshed.sumo.kif.*
import kotlinx.coroutines.flow.*

/**
 * xSWO Integration Demo with BBCursive
 * 
 * Demonstrates the absorption of xSWO collections and Boost Spirit grammars
 * into bbcursive patterns using Join composition and register-at-a-time scanning.
 * 
 * This demo shows how xSWO's HAT-trie, quadbag, and SQL-2003/SPARQL grammars
 * are integrated into the TrikeShed ecosystem.
 */

object XswoIntegrationDemo {
    
    /**
     * Main demo function showing xSWO integration
     */
    suspend fun runDemo() {
        println("=== xSWO Integration Demo with BBCursive ===")
        println()
        
        // 1. HAT-trie demo
        demoHatTrie()
        println()
        
        // 2. Quadbag demo
        demoQuadBag()
        println()
        
        // 3. Array hash demo
        demoArrayHash()
        println()
        
        // 4. SQL-2003 grammar demo
        demoSql2003Grammar()
        println()
        
        // 5. SPARQL grammar demo
        demoSparqlGrammar()
        println()
        
        // 6. BBCursive scanner demo
        demoBbcursiveScanner()
        println()
        
        // 7. Triple dispatch demo
        demoTripleDispatch()
        println()
        
        // 8. Full integration workflow demo
        demoFullIntegration()
        println()
        
        println("=== xSWO Integration Demo Complete ===")
    }
    
    /**
     * Demo HAT-trie using bbcursive patterns
     */
    private fun demoHatTrie() {
        println("1. HAT-trie Demo (Cache-conscious Trie)")
        println("   Based on xSWO hat_trie.h")
        
        val trie = HatTrie<String>()
        
        // Insert using bbcursive pattern
        trie.insert("hello", "world")
        trie.insert("help", "me")
        trie.insert("hero", "zero")
        trie.insert("herb", "garden")
        trie.insert("herd", "animals")
        
        println("   Inserted: hello->world, help->me, hero->zero, herb->garden, herd->animals")
        
        // Search using bbcursive pattern
        println("   Search results:")
        println("     hello -> ${trie.search("hello")}")
        println("     help -> ${trie.search("help")}")
        println("     hero -> ${trie.search("hero")}")
        println("     herb -> ${trie.search("herb")}")
        println("     herd -> ${trie.search("herd")}")
        println("     notfound -> ${trie.search("notfound")}")
        
        println("   ✅ HAT-trie using Join composition and bbcursive patterns")
    }
    
    /**
     * Demo quadbag using bbcursive patterns
     */
    private fun demoQuadBag() {
        println("2. Quadbag Demo (Multi-index RDF Container)")
        println("   Based on xSWO quadbag.hpp Boost.MultiIndex usage")
        
        val quadBag = QuadBag<String>()
        
        // Insert quads using bbcursive pattern
        quadBag.insert(Quad("alice", "knows", "bob", "context1"))
        quadBag.insert(Quad("bob", "knows", "charlie", "context1"))
        quadBag.insert(Quad("alice", "likes", "pizza", "context2"))
        quadBag.insert(Quad("charlie", "knows", "alice", "context1"))
        quadBag.insert(Quad("bob", "likes", "coffee", "context2"))
        
        println("   Inserted RDF quads using Join composition")
        
        // Query by subject using bbcursive pattern
        val aliceQuads = quadBag.findBySubject("alice")
        println("   Quads where alice is subject: ${aliceQuads.size}")
        
        // Query by predicate using bbcursive pattern
        val knowsQuads = quadBag.findByPredicate("knows")
        println("   Quads with 'knows' predicate: ${knowsQuads.size}")
        
        // SPARQL-like query using bbcursive pattern
        val aliceKnows = quadBag.query(subject = "alice", predicate = "knows")
        println("   Alice knows: ${aliceKnows.size} results")
        if (aliceKnows.size > 0) {
            println("     ${aliceKnows[0].subject} ${aliceKnows[0].predicate} ${aliceKnows[0].`object`}")
        }
        
        // Test Join composition conversion
        val quad = Quad("s", "p", "o", "c")
        val join = quad.toJoin()
        val reconstructed = Quad.fromJoin(join)
        println("   Join composition test: ${quad == reconstructed}")
        
        println("   ✅ Quadbag using Join composition and bbcursive patterns")
    }
    
    /**
     * Demo array hash using bbcursive patterns
     */
    private fun demoArrayHash() {
        println("3. Array Hash Demo (Fast Hash Table)")
        println("   Based on xSWO array_hash.h")
        
        val arrayHash = ArrayHash<String>(8)
        
        // Insert using bbcursive pattern
        arrayHash.insert("key1", "value1")
        arrayHash.insert("key2", "value2")
        arrayHash.insert("key3", "value3")
        arrayHash.insert("key1", "value1_updated") // Update existing
        
        println("   Inserted key-value pairs using bbcursive pattern")
        
        // Find using bbcursive pattern
        println("   Find results:")
        println("     key1 -> ${arrayHash.find("key1")}")
        println("     key2 -> ${arrayHash.find("key2")}")
        println("     key3 -> ${arrayHash.find("key3")}")
        println("     key4 -> ${arrayHash.find("key4")}")
        
        // Get entries using Join composition
        val entries = arrayHash.entries()
        println("   All entries using Join composition: ${entries.size}")
        for (i in 0 until entries.size) {
            val entry = entries[i]
            println("     ${entry.a} -> ${entry.b}")
        }
        
        // Remove using bbcursive pattern
        arrayHash.remove("key1")
        println("   After removing key1: ${arrayHash.find("key1")}")
        
        println("   ✅ Array hash using Join composition and bbcursive patterns")
    }
    
    /**
     * Demo SQL-2003 grammar using bbcursive patterns
     */
    private fun demoSql2003Grammar() {
        println("4. SQL-2003 Grammar Demo")
        println("   Based on xSWO s2k3.bnf")
        
        val sqlTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("SELECT", 1),
            KifToken.Symbol("*", 8),
            KifToken.Symbol("FROM", 10),
            KifToken.Symbol("users", 15),
            KifToken.Symbol("WHERE", 21),
            KifToken.Symbol("age", 27),
            KifToken.Symbol(">", 31),
            KifToken.Symbol("18", 33),
            KifToken.ParenClose(35)
        )
        
        println("   Parsing SQL tokens using bbcursive patterns")
        
        val result = Sql2003Grammar.SQL_STATEMENT.b(sqlTokens)
        if (result != null) {
            val expression = result.a
            val elements = expression.toList()
            
            println("   Parsed SQL statement:")
            println("     Elements: ${elements.size}")
            for (i in 0 until elements.size) {
                val element = elements[i]
                when (element) {
                    is KifExpression.Atom -> println("       $i: ${element.value}")
                    is KifExpression.Str -> println("       $i: \"${element.value}\"")
                    else -> println("       $i: ${element}")
                }
            }
        } else {
            println("   Failed to parse SQL statement")
        }
        
        // Test character classification
        val digitToken = listOf(KifToken.Symbol("5", 0))
        val letterToken = listOf(KifToken.Symbol("A", 0))
        val specialToken = listOf(KifToken.Symbol("+", 0))
        
        val digitResult = Sql2003Grammar.DIGIT.b(digitToken)
        val letterResult = Sql2003Grammar.SIMPLE_LATIN_LETTER.b(letterToken)
        val specialResult = Sql2003Grammar.SQL_SPECIAL_CHAR.b(specialToken)
        
        println("   Character classification:")
        println("     '5' is digit: ${digitResult != null}")
        println("     'A' is letter: ${letterResult != null}")
        println("     '+' is special: ${specialResult != null}")
        
        println("   ✅ SQL-2003 grammar using Join composition and bbcursive patterns")
    }
    
    /**
     * Demo SPARQL grammar using bbcursive patterns
     */
    private fun demoSparqlGrammar() {
        println("5. SPARQL Grammar Demo")
        println("   Based on xSWO sparql.cpp")
        
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
        
        println("   Parsing SPARQL tokens using bbcursive patterns")
        
        val result = SparqlGrammar.SPARQL_QUERY.b(sparqlTokens)
        if (result != null) {
            val expression = result.a
            val elements = expression.toList()
            
            println("   Parsed SPARQL query:")
            println("     Elements: ${elements.size}")
            for (i in 0 until elements.size) {
                val element = elements[i]
                when (element) {
                    is KifExpression.Atom -> println("       $i: ${element.value}")
                    is KifExpression.Str -> println("       $i: \"${element.value}\"")
                    else -> println("       $i: ${element}")
                }
            }
        } else {
            println("   Failed to parse SPARQL query")
        }
        
        println("   ✅ SPARQL grammar using Join composition and bbcursive patterns")
    }
    
    /**
     * Demo bbcursive scanner for xSWO collections
     */
    private fun demoBbcursiveScanner() {
        println("6. BBCursive Scanner Demo")
        println("   Register-at-a-time scanning for xSWO collections")
        
        // Create test collections
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
        
        println("   Scanner results:")
        println("     HAT-trie scan: ${trieScan.size} items")
        println("     Quadbag scan: ${quadBagScan.size} items")
        println("     Array hash scan: ${arrayHashScan.size} items")
        
        // Test grammar scanner
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
        
        val sqlResults = XswoBbcursiveGrammarScanner.scanSql2003Grammar(sqlTokens)
        val sparqlResults = XswoBbcursiveGrammarScanner.scanSparqlGrammar(sparqlTokens)
        
        println("   Grammar scanner results:")
        println("     SQL-2003: ${sqlResults.size} expressions")
        println("     SPARQL: ${sparqlResults.size} expressions")
        
        println("   ✅ BBCursive scanner using register-at-a-time patterns")
    }
    
    /**
     * Demo triple dispatch for xSWO operations
     */
    private fun demoTripleDispatch() {
        println("7. Triple Dispatch Demo")
        println("   Compile-time optimized dispatch for xSWO operations")
        
        // Create test collections
        val trie = HatTrie<String>()
        trie.insert("test", "value")
        
        val quadBag = QuadBag<String>()
        quadBag.insert(Quad("s", "p", "o", "c"))
        
        val arrayHash = ArrayHash<String>()
        arrayHash.insert("key", "value")
        
        // Test collection triple dispatch
        val trieResult = XswoCollectionProcessor.dispatch(trie, "search", listOf("test"))
        val quadBagResult = XswoCollectionProcessor.dispatch(quadBag, "query", listOf("s", null, null, null))
        val arrayHashResult = XswoCollectionProcessor.dispatch(arrayHash, "find", listOf("key"))
        
        println("   Collection dispatch results:")
        println("     HAT-trie search: $trieResult")
        println("     Quadbag query: ${quadBagResult != null}")
        println("     Array hash find: $arrayHashResult")
        
        // Test grammar triple dispatch
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
        
        val sqlResult = XswoGrammarProcessor.dispatch("sql2003", sqlTokens, "parse")
        val sparqlResult = XswoGrammarProcessor.dispatch("sparql", sparqlTokens, "parse")
        
        println("   Grammar dispatch results:")
        println("     SQL-2003 parse: ${sqlResult?.size} expressions")
        println("     SPARQL parse: ${sparqlResult?.size} expressions")
        
        println("   ✅ Triple dispatch using compile-time optimization")
    }
    
    /**
     * Demo full integration workflow
     */
    private fun demoFullIntegration() {
        println("8. Full Integration Workflow Demo")
        println("   Complete xSWO + BBCursive + TrikeShed integration")
        
        // 1. Create collections using xSWO patterns
        val trie = HatTrie<String>()
        val quadBag = QuadBag<String>()
        val arrayHash = ArrayHash<String>()
        
        // 2. Populate with SUMO-like data
        trie.insert("concept", "Human")
        trie.insert("relation", "subclass")
        trie.insert("instance", "Socrates")
        
        quadBag.insert(Quad("Human", "subclass", "Mammal", "SUMO"))
        quadBag.insert(Quad("Mammal", "subclass", "Animal", "SUMO"))
        quadBag.insert(Quad("Socrates", "instance", "Human", "SUMO"))
        
        arrayHash.insert("Human", "Mammal")
        arrayHash.insert("Mammal", "Animal")
        arrayHash.insert("Socrates", "Human")
        
        println("   Created collections with SUMO knowledge")
        
        // 3. Parse grammar using xSWO Boost Spirit patterns
        val grammarTokens = listOf(
            KifToken.ParenOpen(0),
            KifToken.Symbol("subclass", 1),
            KifToken.Symbol("Human", 10),
            KifToken.Symbol("Mammal", 16),
            KifToken.ParenClose(23)
        )
        
        val grammarResult = Sql2003Grammar.SQL_STATEMENT.b(grammarTokens)
        println("   Parsed grammar using Boost Spirit patterns: ${grammarResult != null}")
        
        // 4. Use bbcursive scanner to process collections
        val trieScan = XswoBbcursiveScanner.scanHatTrie(trie)
        val quadBagScan = XswoBbcursiveScanner.scanQuadBag(quadBag)
        val arrayHashScan = XswoBbcursiveScanner.scanArrayHash(arrayHash)
        
        println("   Scanned collections using bbcursive patterns:")
        println("     HAT-trie: ${trieScan.size} items")
        println("     Quadbag: ${quadBagScan.size} items")
        println("     Array hash: ${arrayHashScan.size} items")
        
        // 5. Test triple dispatch integration
        val trieQuery = XswoCollectionProcessor.dispatch(trie, "search", listOf("concept"))
        val quadBagQuery = XswoCollectionProcessor.dispatch(quadBag, "query", listOf("Human", "subclass", null, null))
        val grammarParse = XswoGrammarProcessor.dispatch("sql2003", grammarTokens, "parse")
        
        println("   Triple dispatch results:")
        println("     Concept lookup: $trieQuery")
        println("     Subclass query: ${quadBagQuery != null}")
        println("     Grammar parse: ${grammarParse?.size} expressions")
        
        // 6. Demonstrate knowledge inference
        println("   Knowledge inference:")
        val humanSubclass = quadBag.query(subject = "Human", predicate = "subclass")
        if (humanSubclass.size > 0) {
            val superclass = humanSubclass[0].`object`
            println("     Human is subclass of: $superclass")
            
            val mammalSubclass = quadBag.query(subject = superclass, predicate = "subclass")
            if (mammalSubclass.size > 0) {
                val animalClass = mammalSubclass[0].`object`
                println("     $superclass is subclass of: $animalClass")
                println("     Therefore, Human is subclass of: $animalClass (transitive)")
            }
        }
        
        println("   ✅ Full xSWO + BBCursive + TrikeShed integration complete")
    }
}

/**
 * Main function to run the demo
 */
suspend fun main() {
    XswoIntegrationDemo.runDemo()
} 