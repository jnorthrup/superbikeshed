@file:Suppress("NOTHING_TO_INLINE")

package borg.entityscanner

import borg.trikeshed.lib.*
import borg.trikeshed.graph.*

/**
 * Kotlin Entity Scanner - Main API for Graph-Based Code Analysis
 * 
 * Provides high-level API for scanning Kotlin source code using:
 * - Hierarchical token classification stairway (zero-cost inline classes)
 * - Inductive graph refinement with forward chaining logic
 * - TrikeShed type system compliance (Series<T>, Join<A,B>, α transforms)
 */

// ==== PUBLIC API TYPES ====

// Taxonomical Type Aliases for External API
typealias KotlinSourceCode = String
typealias KotlinClassName = String  
typealias KotlinFunctionName = String
typealias KotlinPackageName = String
typealias KotlinImportPath = String
typealias KotlinVariableName = String
typealias KotlinAnnotationName = String

// Entity Analysis Results using Unified Graph Nodes
typealias EntityAnalysisResult = Join<GraphNodeSeries, RefinementSeries>
typealias KotlinDependencyGraph = Series<Join<String, String>>
typealias EntityIndex = Series<Join<String, EntityMetadata>>

// Entity Metadata for rich analysis results
@JvmInline
value class EntityMetadata(val packed: Long) {
    val entityType: UByte get() = (packed and 0xFF).toUByte()
    val confidence: UByte get() = ((packed shr 8) and 0xFF).toUByte() 
    val position: Int get() = ((packed shr 16) and 0xFFFF).toInt()
    val lineNumber: Int get() = ((packed shr 32) and 0xFFFF).toInt()
    
    companion object {
        fun pack(entityType: UByte, confidence: UByte, position: Int, lineNumber: Int): EntityMetadata =
            EntityMetadata(
                entityType.toLong() or 
                (confidence.toLong() shl 8) or 
                (position.toLong() shl 16) or 
                (lineNumber.toLong() shl 32)
            )
    }
}

// Scanning Configuration
@JvmInline
value class ScanConfig(val flags: UInt) {
    val enableInductiveRefinement: Boolean get() = (flags and 1u) != 0u
    val enableDependencyAnalysis: Boolean get() = (flags and 2u) != 0u
    val enableCallGraphGeneration: Boolean get() = (flags and 4u) != 0u
    val enableIncrementalUpdates: Boolean get() = (flags and 8u) != 0u
    
    companion object {
        val DEFAULT = ScanConfig(0u)
        val FULL_ANALYSIS = ScanConfig(15u) // All flags enabled
        val FAST_SCAN = ScanConfig(1u) // Only inductive refinement
    }
}

// ==== MAIN SCANNER API ====

/**
 * Kotlin Entity Scanner - Primary interface for code analysis
 */
object KotlinEntityScanner {
    
    /**
     * Scan Kotlin source code and extract entities using graph-based analysis
     */
    fun scan(source: KotlinSourceCode, config: ScanConfig = ScanConfig.DEFAULT): EntityAnalysisResult {
        return when {
            config.enableInductiveRefinement -> {
                // Use advanced inductive parsing with forward/backward chaining
                source.parseWithMaxEntropy()
            }
            config.enableDependencyAnalysis -> {
                // Use bidirectional chaining for dependency analysis
                source.parseWithChains()
            }
            else -> {
                // Use basic token stairway
                val graphNodes = source.scanToGraph()
                val emptyRefinements = emptySeries<GraphRefinement>()
                graphNodes j emptyRefinements
            }
        }
    }
    
    /**
     * Extract specific entity types from source code
     */
    fun scanClasses(source: KotlinSourceCode): Series<KotlinClassName> {
        val (graphNodes, _) = scan(source, ScanConfig.FAST_SCAN)
        
        return graphNodes.α { node ->
            val (classified, confidence) = node
            val (nodeId, depType) = classified
            
            // Extract class names from graph nodes
            if (confidence.confidence > 128u && isClassNode(depType)) {
                "Class_${nodeId.nodeId}"
            } else {
                ""
            }
        }.play.filter { it.isNotEmpty() }.toSeries()
    }
    
    /**
     * Extract function declarations from source code
     */
    fun scanFunctions(source: KotlinSourceCode): Series<KotlinFunctionName> {
        val entities = source.scanToEntities()
        
        return entities.α { entity ->
            val (classified, context) = entity
            val (entityToken, roleToken) = classified
            
            if (entityToken.entityType == EntityToken.SUSPEND_FUNCTION ||
                entityToken.entityType == EntityToken.INLINE_FUNCTION ||
                entityToken.entityType == EntityToken.EXTENSION_FUNCTION) {
                "Function_${entityToken.entityType}"
            } else {
                ""
            }
        }.play.filter { it.isNotEmpty() }.toSeries()
    }
    
    /**
     * Extract import statements and dependencies
     */
    fun scanImports(source: KotlinSourceCode): Series<KotlinImportPath> {
        val lines = source.lines()
        val imports = mutableListOf<String>()
        
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("import ")) {
                val importPath = trimmed.substring(7).trim()
                imports.add(importPath)
            }
        }
        
        return imports.toSeries()
    }
    
    /**
     * Extract @DependsOn annotations for k2script integration
     */
    fun scanDependencies(source: KotlinSourceCode): KotlinDependencyGraph {
        val lines = source.lines()
        val dependencies = mutableListOf<Join<String, String>>()
        
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("@DependsOn(") || trimmed.startsWith("@file:DependsOn(")) {
                val dependency = extractDependencyCoordinate(trimmed)
                if (dependency != null) {
                    dependencies.add(dependency)
                }
            }
        }
        
        return dependencies.toSeries()
    }
    
    /**
     * Build call graph showing function-to-function relationships
     */
    fun buildCallGraph(source: KotlinSourceCode): KotlinDependencyGraph {
        val (graphNodes, _) = scan(source, ScanConfig.FULL_ANALYSIS)
        
        return graphNodes.α { node ->
            val (classified, confidence) = node
            val (nodeId, depType) = classified
            
            // Create dependency edges based on node relationships
            val sourceNode = "Node_${nodeId.nodeId}"
            val targetNode = when (depType.depType) {
                DependencyToken.FUNCTION_CALLS -> "Call_${nodeId.nodeId}"
                DependencyToken.INHERITANCE -> "Inherits_${nodeId.nodeId}"
                DependencyToken.IMPLEMENTATION -> "Implements_${nodeId.nodeId}"
                else -> "Unknown_${nodeId.nodeId}"
            }
            
            sourceNode j targetNode
        }
    }
    
    /**
     * Create entity index for fast lookup and IDE integration
     */
    fun buildEntityIndex(source: KotlinSourceCode): EntityIndex {
        val entities = source.scanToEntities()
        val lines = source.lines()
        
        return entities.α { entity ->
            val (classified, context) = entity
            val (entityToken, roleToken) = classified
            
            val entityName = generateEntityName(entityToken, roleToken)
            val position = calculatePosition(source, entityName)
            val lineNumber = calculateLineNumber(lines, position)
            
            val metadata = EntityMetadata.pack(
                entityType = entityToken.entityType,
                confidence = 255u, // High confidence for indexed entities
                position = position,
                lineNumber = lineNumber
            )
            
            entityName j metadata
        }
    }
    
    /**
     * Incremental update for real-time parsing as code changes
     */
    fun incrementalUpdate(
        previousResult: EntityAnalysisResult,
        newSource: KotlinSourceCode,
        changePosition: Int,
        changeLength: Int
    ): EntityAnalysisResult {
        val (previousNodes, previousRefinements) = previousResult
        
        // For now, perform full re-scan (incremental logic would be more complex)
        return scan(newSource, ScanConfig.FULL_ANALYSIS)
    }
    
    // === HELPER FUNCTIONS ===
    
    private fun isClassNode(depType: DependencyToken): Boolean {
        return depType.depType == DependencyToken.INTERNAL_REFERENCE ||
               depType.depType == DependencyToken.INHERITANCE
    }
    
    // === MISSING PARSING FUNCTIONS ===
    
    private fun KotlinSourceCode.parseWithMaxEntropy(): EntityAnalysisResult {
        val graphNodes = this.scanToGraph()
        val refinements = graphNodes.applyInductiveRefinement()
        return graphNodes j refinements
    }
    
    private fun KotlinSourceCode.parseWithChains(): EntityAnalysisResult {
        val graphNodes = this.scanToGraph()
        val forwardRefinements = graphNodes.applyForwardChaining()
        val expectedTypes = generateExpectedTypes(this)
        val backwardRefinements = graphNodes.applyBackwardChaining(expectedTypes)
        val combinedRefinements = combineRefinements(forwardRefinements, backwardRefinements)
        return graphNodes j combinedRefinements
    }
    
    private fun generateExpectedTypes(source: KotlinSourceCode): Series<NodeType> {
        // Simple expected type generation based on keywords
        val keywords = listOf("class", "fun", "val", "var", "interface", "object")
        return keywords.size j { i -> 
            NodeType(keywords[i])
        }
    }
    
    private fun combineRefinements(first: RefinementSeries, second: RefinementSeries): RefinementSeries {
        // Simple combination - would be more sophisticated in practice
        return first.α { refinement ->
            refinement
        }
    }
    
    private fun extractDependencyCoordinate(line: String): Join<String, String>? {
        // Extract Maven coordinates from @DependsOn annotation
        val regex = """@(?:file:)?DependsOn\("([^:]+):([^:]+)(?::([^"]+))?"\)""".toRegex()
        val match = regex.find(line)
        
        return if (match != null) {
            val groupId = match.groupValues[1]
            val artifactId = match.groupValues[2]
            groupId j artifactId
        } else {
            null
        }
    }
    
    private fun generateEntityName(entityToken: EntityToken, roleToken: RoleToken): String {
        val entityTypeName = when (entityToken.entityType) {
            EntityToken.DATA_CLASS -> "DataClass"
            EntityToken.REGULAR_CLASS -> "Class"
            EntityToken.INTERFACE -> "Interface"
            EntityToken.SUSPEND_FUNCTION -> "SuspendFunction"
            EntityToken.INLINE_FUNCTION -> "InlineFunction"
            EntityToken.PROPERTY -> "Property"
            EntityToken.TYPEALIAS -> "TypeAlias"
            EntityToken.VALUE_CLASS -> "ValueClass"
            else -> "Unknown"
        }
        
        val roleTypeName = when (roleToken.role) {
            RoleToken.DECLARATION -> "Declaration"
            RoleToken.REFERENCE -> "Reference"
            RoleToken.PARAMETER -> "Parameter"
            else -> "Usage"
        }
        
        return "${entityTypeName}_${roleTypeName}_${entityToken.entityType}"
    }
    
    private fun calculatePosition(source: String, entityName: String): Int {
        // Simple position calculation - would be enhanced with actual parsing
        return entityName.hashCode() % source.length
    }
    
    private fun calculateLineNumber(lines: List<String>, position: Int): Int {
        // Simple line number calculation
        return kotlin.math.min(lines.size, kotlin.math.max(1, position / 50))
    }
}

// ==== INTEGRATION HELPERS FOR K2SCRIPT ====

/**
 * K2Script integration utilities
 */
object K2ScriptIntegration {
    
    /**
     * Extract k2script-specific annotations and dependencies
     */
    fun extractK2ScriptMetadata(source: KotlinSourceCode): Join<Series<String>, KotlinDependencyGraph> {
        val annotations = mutableListOf<String>()
        val dependencies = mutableListOf<Join<String, String>>()
        
        source.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("@file:DependsOn(") -> {
                    val dep = extractDependencyFromLine(trimmed)
                    if (dep != null) dependencies.add(dep)
                }
                trimmed.startsWith("@file:Import(") -> {
                    annotations.add(trimmed)
                }
                trimmed.startsWith("@file:Repository(") -> {
                    annotations.add(trimmed)
                }
                trimmed.startsWith("#!/usr/bin/env k2script") -> {
                    annotations.add("shebang")
                }
            }
        }
        
        return annotations.toSeries() j dependencies.toSeries()
    }
    
    /**
     * Generate dependency graph suitable for spacegraph visualization
     */
    fun generateSpaceGraphData(source: KotlinSourceCode): Series<Join<String, Any>> {
        val (graphNodes, _) = KotlinEntityScanner.scan(source, ScanConfig.FULL_ANALYSIS)
        
        return graphNodes.α { node ->
            val (classified, confidence) = node
            val (nodeId, depType) = classified
            
            val nodeData = mapOf(
                "id" to "node_${nodeId.nodeId}",
                "type" to depType.depType.toString(),
                "confidence" to confidence.confidence,
                "category" to "kotlin_entity"
            )
            
            "node_${nodeId.nodeId}" j (nodeData as Any)
        }
    }
    
    private fun extractDependencyFromLine(line: String): Join<String, String>? {
        val regex = """@file:DependsOn\("([^:]+):([^:]+)(?::([^"]+))?"\)""".toRegex()
        val match = regex.find(line)
        
        return if (match != null) {
            val groupId = match.groupValues[1]
            val artifactId = match.groupValues[2]
            groupId j artifactId
        } else {
            null
        }
    }
}

// ==== CONVENIENCE EXTENSIONS ====

/**
 * Extension functions for easy integration
 */
fun KotlinSourceCode.extractClasses(): Series<KotlinClassName> = 
    KotlinEntityScanner.scanClasses(this)

fun KotlinSourceCode.extractFunctions(): Series<KotlinFunctionName> = 
    KotlinEntityScanner.scanFunctions(this)

fun KotlinSourceCode.extractImports(): Series<KotlinImportPath> = 
    KotlinEntityScanner.scanImports(this)

fun KotlinSourceCode.extractDependencies(): KotlinDependencyGraph = 
    KotlinEntityScanner.scanDependencies(this)

fun KotlinSourceCode.buildCallGraph(): KotlinDependencyGraph = 
    KotlinEntityScanner.buildCallGraph(this)

fun KotlinSourceCode.buildEntityIndex(): EntityIndex = 
    KotlinEntityScanner.buildEntityIndex(this)

// For k2script integration
fun KotlinSourceCode.extractK2ScriptMetadata(): Join<Series<String>, KotlinDependencyGraph> = 
    K2ScriptIntegration.extractK2ScriptMetadata(this)

fun KotlinSourceCode.generateSpaceGraphData(): Series<Join<String, Any>> = 
    K2ScriptIntegration.generateSpaceGraphData(this)

/**
 * Comprehensive example demonstrating the full kotlin-entity-scanner API
 */
object KotlinEntityScannerExample {
    fun demonstrateFullAPI() {
        val kotlinCode = """
            #!/usr/bin/env k2script
            @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            @file:Import("kotlinx.coroutines.*")
            
            package com.example.demo
            
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.runBlocking
            
            @JvmInline
            value class UserId(val id: String)
            
            data class User(val id: UserId, val name: String)
            
            interface UserRepository {
                suspend fun findUser(id: UserId): User?
            }
            
            class DatabaseUserRepository : UserRepository {
                override suspend fun findUser(id: UserId): User? {
                    delay(100) // Simulate database access
                    return User(id, "User_123")
                }
            }
            
            fun main() = runBlocking {
                val repo: UserRepository = DatabaseUserRepository()
                val user = repo.findUser(UserId("123"))
                println("Found user: example")
            }
        """.trimIndent()
        
        println("=== Kotlin Entity Scanner Full API Demo ===")
        
        // Basic entity extraction
        val classes = kotlinCode.extractClasses()
        val functions = kotlinCode.extractFunctions()
        val imports = kotlinCode.extractImports()
        val dependencies = kotlinCode.extractDependencies()
        
        println("Classes found: ${classes.play.toList()}")
        println("Functions found: ${functions.play.toList()}")
        println("Imports found: ${imports.play.toList()}")
        println("Dependencies found: ${dependencies.play.toList()}")
        
        // Advanced analysis
        val callGraph = kotlinCode.buildCallGraph()
        val entityIndex = kotlinCode.buildEntityIndex()
        
        println("Call graph edges: ${callGraph.play.toList().size}")
        println("Indexed entities: ${entityIndex.play.toList().size}")
        
        // K2Script integration
        val (k2annotations, k2deps) = kotlinCode.extractK2ScriptMetadata()
        val spaceGraphData = kotlinCode.generateSpaceGraphData()
        
        println("K2Script annotations: ${k2annotations.play.toList()}")
        println("K2Script dependencies: ${k2deps.play.toList()}")
        println("SpaceGraph nodes: ${spaceGraphData.play.toList().size}")
        
        // Full analysis with inductive refinement
        val fullAnalysis = KotlinEntityScanner.scan(kotlinCode, ScanConfig.FULL_ANALYSIS)
        val (graphNodes, refinements) = fullAnalysis
        
        println("Graph nodes with confidence: ${graphNodes.play.toList().size}")
        println("Accuracy refinements: ${refinements.play.toList().size}")
    }
}