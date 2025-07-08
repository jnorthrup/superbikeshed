@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.sumo

import borg.trikeshed.sumo.curation.*
import borg.trikeshed.sumo.kif.*
import borg.trikeshed.sumo.simd.*
import kotlin.test.*

/**
 * Test suite for SUMO curation with KIF scanner/parser and SIMD bitmap operations
 */
class SumoCurationTest {
    
    @Test
    fun testKifSimdScanner() {
        val kifContent = """
            ; Test KIF content
            (instance Entity Abstract)
            (subclass Object Entity)
            (instance Human Object)
            (=> (instance ?X Human) (instance ?X Animal))
        """.trimIndent()
        
        val scanner = KifSimdScanner(kifContent.toByteArray())
        
        // Test register-at-a-time scanning
        val scanResult = scanner.scanAtTime()
        assertNotNull(scanResult, "Scan result should not be null")
        assertTrue(scanResult is RegisterJoin<*, *>, "Scan result should be RegisterJoin")
        
        // Test character classification
        val classes = scanner.classifyKif()
        assertNotNull(classes, "Character classes should not be null")
        assertEquals(kifContent.length, classes.size, "Classes array should match input length")
        
        // Test structural bitmap
        val bitmap = scanner.buildStructuralBitmap()
        assertNotNull(bitmap, "Structural bitmap should not be null")
        assertTrue(bitmap.isNotEmpty(), "Bitmap should not be empty")
        
        // Test structural positions
        val positions = scanner.findStructuralPositions()
        assertNotNull(positions, "Structural positions should not be null")
        assertTrue(positions.isNotEmpty(), "Should find structural positions")
        
        // Verify we found parentheses
        val hasParentheses = positions.any { pos ->
            kifContent[pos] == '(' || kifContent[pos] == ')'
        }
        assertTrue(hasParentheses, "Should find parentheses in KIF content")
    }
    
    @Test
    fun testSimdBitmapEngine() {
        val engine = SimdBitmapEngine()
        val data = "Hello (World) \"Test\" ; Comment".toByteArray()
        val structuralChars = byteArrayOf(
            '('.code.toByte(), ')'.code.toByte(),
            '"'.code.toByte(), ';'.code.toByte()
        )
        
        // Test structural bitmap building
        val bitmap = engine.buildStructuralBitmap(data, structuralChars)
        assertNotNull(bitmap, "Bitmap should not be null")
        assertTrue(bitmap.isNotEmpty(), "Bitmap should not be empty")
        
        // Test position extraction
        val positions = engine.extractStructuralPositions(bitmap)
        assertNotNull(positions, "Positions should not be null")
        assertTrue(positions.isNotEmpty(), "Should find structural positions")
        
        // Verify we found expected structural characters
        val expectedPositions = listOf(6, 12, 14, 20, 26) // Positions of (, ), ", ", ;
        val foundPositions = positions.toList()
        assertTrue(foundPositions.containsAll(expectedPositions), 
            "Should find all expected structural positions")
        
        // Test next structural finding
        val nextAfter5 = engine.findNextStructural(bitmap, 5)
        assertEquals(6, nextAfter5, "Should find next structural after position 5")
        
        // Test counting in range
        val countInRange = engine.countStructuralInRange(bitmap, 0, 15)
        assertTrue(countInRange > 0, "Should count structural elements in range")
    }
    
    @Test
    fun testParallelKifScan() {
        val engine = SimdBitmapEngine()
        val data = """
            ; Comment line
            (instance Entity Abstract)
            (subclass Object Entity)
            "String with (parentheses)"
            (instance Human Object)
        """.trimIndent().toByteArray()
        
        val structuralChars = byteArrayOf(
            '('.code.toByte(), ')'.code.toByte(),
            '"'.code.toByte(), ';'.code.toByte()
        )
        
        val state = engine.parallelKifScan(data, structuralChars)
        
        // Test state arrays
        assertNotNull(state.inComment, "Comment state should not be null")
        assertNotNull(state.inString, "String state should not be null")
        assertNotNull(state.parenDepth, "Parenthesis depth should not be null")
        assertNotNull(state.escaped, "Escape state should not be null")
        assertNotNull(state.structuralBitmap, "Structural bitmap should not be null")
        
        // Verify array lengths
        assertEquals(data.size, state.inComment.size, "Comment state should match data size")
        assertEquals(data.size, state.inString.size, "String state should match data size")
        assertEquals(data.size, state.parenDepth.size, "Parenthesis depth should match data size")
        assertEquals(data.size, state.escaped.size, "Escape state should match data size")
        
        // Test that we can detect comments
        val hasComments = state.inComment.any { it }
        assertTrue(hasComments, "Should detect comments in KIF content")
        
        // Test that we can detect strings
        val hasStrings = state.inString.any { it }
        assertTrue(hasStrings, "Should detect strings in KIF content")
        
        // Test parenthesis depth tracking
        val maxDepth = state.parenDepth.maxOrNull() ?: 0
        assertTrue(maxDepth > 0, "Should track parenthesis depth")
    }
    
    @Test
    fun testKifTokenExtraction() {
        val engine = SimdBitmapEngine()
        val data = """
            (instance Entity Abstract)
            (subclass Object Entity)
            "Test String"
            (instance Human Object)
        """.trimIndent().toByteArray()
        
        val tokens = mutableListOf<KifToken>()
        
        // Collect tokens (in real implementation, this would use coroutines)
        // For testing, we'll simulate the collection
        val structuralChars = byteArrayOf(
            '('.code.toByte(), ')'.code.toByte(),
            '"'.code.toByte(), ';'.code.toByte()
        )
        
        val state = engine.parallelKifScan(data, structuralChars)
        val structuralPositions = engine.extractStructuralPositions(state.structuralBitmap)
        
        // Verify we found structural positions
        assertTrue(structuralPositions.isNotEmpty(), "Should find structural positions")
        
        // Test that we can identify different token types
        val dataString = String(data)
        val hasParentheses = structuralPositions.any { pos ->
            dataString[pos] == '(' || dataString[pos] == ')'
        }
        assertTrue(hasParentheses, "Should identify parentheses")
        
        val hasQuotes = structuralPositions.any { pos ->
            dataString[pos] == '"'
        }
        assertTrue(hasQuotes, "Should identify quotes")
    }
    
    @Test
    fun testSumoCurator() {
        val curator = SumoCurator()
        
        // Test that curator can be created
        assertNotNull(curator, "Curator should be created successfully")
        
        // Test with mock remote URL
        val mockUrl = "https://example.com/sumo.zip"
        
        // Note: In a real test, we would mock the HTTP client and test actual curation
        // For now, we'll test the structure and types
        
        // Test that we can create a curator with custom components
        val customParser = KifSimdParser()
        val customValidator = SumoValidator()
        val customCurator = SumoCurator(customParser, customValidator)
        
        assertNotNull(customCurator, "Custom curator should be created successfully")
    }
    
    @Test
    fun testSumoOntologyTypes() {
        // Test SUMO ontology data types
        val concept = SumoConcept(
            id = "Human",
            name = "Human",
            description = "A human being",
            type = ConceptType.ENTITY
        )
        
        assertNotNull(concept, "Concept should be created")
        assertEquals("Human", concept.id, "Concept ID should match")
        assertEquals(ConceptType.ENTITY, concept.type, "Concept type should match")
        
        val relation = SumoRelation(
            id = "instance",
            name = "instance",
            description = "Instance relationship",
            domain = "Entity",
            range = "Class",
            arity = 2
        )
        
        assertNotNull(relation, "Relation should be created")
        assertEquals("instance", relation.id, "Relation ID should match")
        assertEquals(2, relation.arity, "Relation arity should match")
        
        val axiom = SumoAxiom(
            id = "Human-Animal-Axiom",
            type = AxiomType.SUBCLASS,
            content = "Human is a subclass of Animal",
            kifExpression = "(subclass Human Animal)",
            description = "Human is a subclass of Animal"
        )
        
        assertNotNull(axiom, "Axiom should be created")
        assertEquals(AxiomType.SUBCLASS, axiom.type, "Axiom type should match")
        assertEquals(1.0, axiom.confidence, "Axiom confidence should default to 1.0")
    }
    
    @Test
    fun testSimdCapabilities() {
        val strategy = GenericSimdStrategy()
        val capabilities = strategy.getCapabilities()
        
        assertNotNull(capabilities, "Capabilities should not be null")
        assertEquals(64, capabilities.vectorBits, "Vector bits should be 64")
        assertEquals(8, capabilities.bytesPerVector, "Bytes per vector should be 8")
        assertEquals(8, capabilities.lanes, "Lanes should be 8")
        assertEquals(SimdPlatform.GENERIC, capabilities.platform, "Platform should be GENERIC")
        assertTrue(capabilities.features.contains(SimdFeature.VECTOR_EXTENSIONS), 
            "Should have VECTOR_EXTENSIONS feature")
    }
    
    @Test
    fun testBitmapOperations() {
        val strategy = GenericSimdStrategy()
        
        val a = longArrayOf(0b1010L, 0b1100L)
        val b = longArrayOf(0b1001L, 0b1010L)
        
        // Test OR operation
        val orResult = strategy.bitmapOr(a, b)
        assertEquals(0b1011L, orResult[0], "OR operation should work correctly")
        assertEquals(0b1110L, orResult[1], "OR operation should work correctly")
        
        // Test AND operation
        val andResult = strategy.bitmapAnd(a, b)
        assertEquals(0b1000L, andResult[0], "AND operation should work correctly")
        assertEquals(0b1000L, andResult[1], "AND operation should work correctly")
        
        // Test XOR operation
        val xorResult = strategy.bitmapXor(a, b)
        assertEquals(0b0011L, xorResult[0], "XOR operation should work correctly")
        assertEquals(0b0110L, xorResult[1], "XOR operation should work correctly")
        
        // Test NOT operation
        val notResult = strategy.bitmapNot(a)
        assertEquals(0b1111111111111111111111111111111111111111111111111111111111110101L, notResult[0], 
            "NOT operation should work correctly")
        assertEquals(0b1111111111111111111111111111111111111111111111111111111111110011L, notResult[1], 
            "NOT operation should work correctly")
    }
    
    @Test
    fun testPopulationCount() {
        val strategy = GenericSimdStrategy()
        
        val bitmap = longArrayOf(0b1010L, 0b1100L, 0b1111L)
        val count = strategy.popcount(bitmap)
        
        assertEquals(8, count, "Population count should be correct")
        // 1010 (2 bits) + 1100 (2 bits) + 1111 (4 bits) = 8 bits total
    }
    
    @Test
    fun testFindFirstSet() {
        val strategy = GenericSimdStrategy()
        
        val bitmap = longArrayOf(0L, 0b1000L, 0L)
        val firstSet = strategy.findFirstSet(bitmap)
        
        assertEquals(64 + 3, firstSet, "Should find first set bit at correct position")
        // Second word (index 1) * 64 + position of first set bit (3) = 67
    }
} 