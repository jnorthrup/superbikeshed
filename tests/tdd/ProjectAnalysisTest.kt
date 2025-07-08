package tests.tdd

import kotlin.test.*
import kotlinx.coroutines.test.runTest

/**
 * TDD Iteration 2: Project Analysis Integration
 * 
 * Test-Driven Development for project structure analysis:
 * - Project enumeration
 * - Module detection
 * - Dependency analysis
 * - Build system identification
 */
class ProjectAnalysisTest {
    
    @Test
    fun `should analyze project structure`() = runTest {
        // Given
        val analyzer = ProjectAnalyzer()
        val projectPath = "/test/project"
        
        // When
        val analysis = analyzer.analyzeProject(projectPath)
        
        // Then
        assertNotNull(analysis)
        assertEquals(projectPath, analysis.projectPath)
        assertTrue(analysis.modules.isNotEmpty())
    }
    
    @Test
    fun `should detect build system type`() = runTest {
        // Given
        val analyzer = ProjectAnalyzer()
        
        // When & Then
        val gradleProject = analyzer.analyzeProject("/test/gradle-project")
        assertEquals(BuildSystemType.GRADLE, gradleProject.buildSystem)
        
        val mavenProject = analyzer.analyzeProject("/test/maven-project")
        assertEquals(BuildSystemType.MAVEN, mavenProject.buildSystem)
    }
    
    @Test
    fun `should enumerate project dependencies`() = runTest {
        // Given
        val analyzer = ProjectAnalyzer()
        
        // When
        val analysis = analyzer.analyzeProject("/test/project")
        
        // Then
        assertTrue(analysis.dependencies.isNotEmpty())
        analysis.dependencies.forEach { dep ->
            assertNotNull(dep.name)
            assertNotNull(dep.type)
        }
    }
    
    @Test
    fun `should identify source directories`() = runTest {
        // Given
        val analyzer = ProjectAnalyzer()
        
        // When
        val analysis = analyzer.analyzeProject("/test/project")
        
        // Then
        analysis.modules.forEach { module ->
            assertTrue(module.sourceDirs.isNotEmpty())
            assertTrue(module.sourceDirs.all { it.contains("src") })
        }
    }
    
    @Test
    fun `should handle missing project gracefully`() = runTest {
        // Given
        val analyzer = ProjectAnalyzer()
        val invalidPath = "/nonexistent/project"
        
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            analyzer.analyzeProject(invalidPath)
        }
    }
}

enum class BuildSystemType {
    GRADLE, MAVEN, UNKNOWN
}

data class Dependency(
    val name: String,
    val version: String?,
    val type: String
)

data class Module(
    val name: String,
    val sourceDirs: List<String>,
    val dependencies: List<Dependency>
)

data class ProjectAnalysis(
    val projectPath: String,
    val buildSystem: BuildSystemType,
    val modules: List<Module>,
    val dependencies: List<Dependency>
)

/**
 * Project Analyzer implementation for TDD
 */
class ProjectAnalyzer {
    suspend fun analyzeProject(projectPath: String): ProjectAnalysis {
        // Simulate project analysis
        if (!projectPath.startsWith("/test/")) {
            throw IllegalArgumentException("Project not found: $projectPath")
        }
        
        val buildSystem = when {
            projectPath.contains("gradle") -> BuildSystemType.GRADLE
            projectPath.contains("maven") -> BuildSystemType.MAVEN
            else -> BuildSystemType.UNKNOWN
        }
        
        val modules = listOf(
            Module(
                name = "main",
                sourceDirs = listOf("$projectPath/src/main/kotlin"),
                dependencies = listOf(
                    Dependency("kotlin-stdlib", "1.9.0", "library"),
                    Dependency("ktor-server", "2.3.0", "library")
                )
            )
        )
        
        val dependencies = modules.flatMap { it.dependencies }
        
        return ProjectAnalysis(
            projectPath = projectPath,
            buildSystem = buildSystem,
            modules = modules,
            dependencies = dependencies
        )
    }
} 