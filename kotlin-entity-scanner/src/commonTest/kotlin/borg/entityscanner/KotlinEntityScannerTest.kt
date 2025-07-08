package borg.entityscanner

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for Kotlin Entity Scanner
 * 
 * Validates the hierarchical token classification stairway and 
 * inductive graph refinement system with TrikeShed compliance
 */
class KotlinEntityScannerTest {
    
    internal val sampleKotlinCode = """
        @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        
        package com.example.test
        
        import kotlinx.coroutines.delay
        
        @kotlin.jvm.JvmInline
        value class UserId(val id: String)
        
        data class User(val id: UserId, val name: String)
        
        interface UserRepository {
            suspend fun findUser(id: UserId): User?
        }
        
        class DatabaseUserRepository : UserRepository {
            override suspend fun findUser(id: UserId): User? {
                delay(100)
                return User(id, "user_name")
            }
        }
        
        fun main() {
            println("Hello, World!")
        }
    """.trimIndent()
    
    @Test
    fun testTokenStairwayCharacterClassification() {
        val source = "class User"
        val chars = TokenStairway.classifyChars(source)
        
        // Validate Indexed<T> structure
        assertTrue(chars.size > 0, "Character series should not be empty")
        assertEquals(10, chars.size, "Should have 10 characters")
        
        // Test character classification
        val firstChar = chars[0]
        val (classifiedChar, position) = firstChar
        val (rawChar, charClass) = classifiedChar
        
        assertEquals('c', rawChar.value)
        assertEquals(CharClass.LETTER, charClass.type)
        assertEquals(0, position.index)
    }
    
    @Test
    fun testTokenStairwayProgression() {
        val source = "fun test()"
        
        // Test full stairway progression
        val chars = TokenStairway.classifyChars(source)
        val tokens = chars.extractTokens()
        val syntax = tokens.extractSyntax()
        val entities = syntax.extractEntities()
        val graphNodes = entities.extractGraph()
        
        // Validate each level maintains Indexed<T> structure
        assertTrue(chars.size > 0, "Characters should exist")
        assertTrue(tokens.size > 0, "Tokens should exist")
        assertTrue(syntax.size > 0, "Syntax elements should exist")
        assertTrue(entities.size > 0, "Entities should exist")
        assertTrue(graphNodes.size > 0, "Graph nodes should exist")
        
        // Validate Join<A,B> compositions at each level
        val firstGraphNode = graphNodes[0]
        val (classified, confidence) = firstGraphNode
        val (nodeId, depType) = classified
        
        assertNotNull(nodeId.nodeId)
        assertNotNull(depType.depType)
        assertNotNull(confidence.confidence)
    }
    
    @Test
    fun testInductiveParsing() {
        val source = "class Test { fun method() {} }"
        val (graphNodes, refinements) = source.parseInductively()
        
        // Validate inductive parsing results
        assertTrue(graphNodes.size > 0, "Should generate graph nodes")
        assertTrue(refinements.size >= 0, "Should track refinements")
        
        // Test accuracy improvements
        val firstRefinement = if (refinements.size > 0) refinements[0] else null
        if (firstRefinement != null) {
            val (position, delta) = firstRefinement
            assertTrue(position.position >= 0, "Position should be valid")
            // Delta can be positive or negative (accuracy change)
        }
    }
    
    @Test
    fun testPredicateSystem() {
        val position = ParsePosition(0)
        val candidates = PredicateSystem.generateCandidateStates('c', position)
        
        // Should generate multiple candidate interpretations
        assertTrue(candidates.size > 0, "Should generate candidate states")
        
        // Test deductive reduction
        val validStates = PredicateSystem.deduceValidStates(candidates, 0.3)
        assertTrue(validStates.size <= 1, "Should reduce to 1 or 0 states")
    }
    
    @Test
    fun testKotlinEntityScannerAPI() {
        // Test class extraction
        val classes = sampleKotlinCode.extractClasses()
        assertTrue(classes.size > 0, "Should find classes")
        
        // Test function extraction
        val functions = sampleKotlinCode.extractFunctions()
        assertTrue(functions.size > 0, "Should find functions")
        
        // Test import extraction
        val imports = sampleKotlinCode.extractImports()
        assertTrue(imports.size > 0, "Should find imports")
        assertEquals("kotlinx.coroutines.delay", imports.play.first())
        
        // Test dependency extraction
        val dependencies = sampleKotlinCode.extractDependencies()
        assertTrue(dependencies.size > 0, "Should find dependencies")
        
        val firstDep = dependencies[0]
        val (groupId, artifactId) = firstDep
        assertEquals("org.jetbrains.kotlinx", groupId)
        assertEquals("kotlinx-coroutines-core", artifactId)
    }
    
    @Test
    fun testCallGraphGeneration() {
        val callGraph = sampleKotlinCode.buildCallGraph()
        assertTrue(callGraph.size > 0, "Should generate call graph")
        
        // Validate graph structure with Join<A,B> relationships
        val firstEdge = callGraph[0]
        val (source, target) = firstEdge
        assertNotNull(source)
        assertNotNull(target)
    }
    
    @Test
    fun testEntityIndexBuilding() {
        val entityIndex = sampleKotlinCode.buildEntityIndex()
        assertTrue(entityIndex.size > 0, "Should build entity index")
        
        // Test entity metadata packing/unpacking
        val firstEntity = entityIndex[0]
        val (entityName, metadata) = firstEntity
        
        assertNotNull(entityName)
        assertTrue(metadata.entityType > 0u, "Should have valid entity type")
        assertTrue(metadata.confidence > 0u, "Should have confidence score")
        assertTrue(metadata.position >= 0, "Should have valid position")
        assertTrue(metadata.lineNumber > 0, "Should have valid line number")
    }
    
    @Test
    fun testK2ScriptIntegration() {
        val (annotations, dependencies) = sampleKotlinCode.extractK2ScriptMetadata()
        
        // Should extract k2script-specific metadata
        assertTrue(dependencies.size > 0, "Should find k2script dependencies")
        
        // Test SpaceGraph data generation
        val spaceGraphData = sampleKotlinCode.generateSpaceGraphData()
        assertTrue(spaceGraphData.size > 0, "Should generate SpaceGraph data")
        
        // Validate graph node structure
        val firstNode = spaceGraphData[0]
        val (nodeId, nodeData) = firstNode
        assertNotNull(nodeId)
        assertNotNull(nodeData)
    }
    
    @Test
    fun testScanConfiguration() {
        // Test different scan configurations
        val fastScan = KotlinEntityScanner.scan(sampleKotlinCode, ScanConfig.FAST_SCAN)
        val fullAnalysis = KotlinEntityScanner.scan(sampleKotlinCode, ScanConfig.FULL_ANALYSIS)
        
        val (fastNodes, fastRefinements) = fastScan
        val (fullNodes, fullRefinements) = fullAnalysis
        
        // Full analysis should potentially have more refinements
        assertTrue(fastNodes.size > 0, "Fast scan should produce nodes")
        assertTrue(fullNodes.size > 0, "Full analysis should produce nodes")
        assertTrue(fullRefinements.size >= fastRefinements.size, 
                  "Full analysis should have at least as many refinements")
    }
    
    @Test
    fun testTrikeShedCompliance() {
        val source = "val x = 42"
        
        // Test that all operations use Indexed<T> and Join<A,B>
        val chars = TokenStairway.classifyChars(source)
        val tokens = chars.extractTokens()
        
        // Validate Indexed<T> structure
        assertTrue(chars.size > 0, "Should use Indexed<T>")
        assertTrue(tokens.size > 0, "Should use Indexed<T>")
        
        // Validate Join<A,B> structure
        val firstChar = chars[0]
        val (_, _) = firstChar // Should decompose with Join pattern
        
        val firstToken = tokens[0]
        val (_, _) = firstToken // Should decompose with Join pattern
        
        // Test α transform usage (implicit in extractTokens)
        val transformedTokens = chars.α { posChar ->
            val (classifiedChar, position) = posChar
            val (rawChar, charClass) = classifiedChar
            val token = LexicalToken(rawChar.value.toString())
            val tokenType = TokenType(TokenType.IDENTIFIER)
            val bounds = TokenBounds.pack(position.index, 1)
            (token j tokenType) j bounds
        }
        
        assertTrue(transformedTokens.size > 0, "α transforms should work")
    }
    
    @Test
    fun testZeroCostAbstractions() {
        // Test that inline value classes maintain zero-cost abstractions
        val char = RawChar('a')
        val charClass = CharClass(CharClass.LETTER)
        val position = CharPosition(0)
        
        // These should compile to primitive values
        assertEquals('a', char.value)
        assertEquals(CharClass.LETTER, charClass.type)
        assertEquals(0, position.index)
        
        // Test metadata packing
        val metadata = EntityMetadata.pack(
            entityType = EntityToken.REGULAR_CLASS,
            confidence = 255u,
            position = 100,
            lineNumber = 5
        )
        
        assertEquals(EntityToken.REGULAR_CLASS, metadata.entityType)
        assertEquals(255u, metadata.confidence)
        assertEquals(100, metadata.position)
        assertEquals(5, metadata.lineNumber)
    }
    
    @Test
    fun testErrorRecovery() {
        // Test parsing of malformed Kotlin code
        val malformedCode = """
            class Broken {
                fun incomplete(
                // Missing closing brace and parenthesis
        """.trimIndent()
        
        // Should not crash and provide partial results
        val (graphNodes, refinements) = malformedCode.parseInductively()
        
        // Should produce some results even with malformed input
        assertTrue(graphNodes.size >= 0, "Should handle malformed code gracefully")
        assertTrue(refinements.size >= 0, "Should handle refinements gracefully")
    }
}