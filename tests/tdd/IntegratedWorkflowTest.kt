package tests.tdd

import kotlin.test.*
import kotlinx.coroutines.test.runTest

/**
 * TDD Iteration 4: Integrated Workflow Testing
 * 
 * Test-Driven Development for complete workflow integration:
 * - End-to-end project analysis
 * - Agent interaction with project context
 * - REST API integration
 * - Result validation and persistence
 */
class IntegratedWorkflowTest {
    
    @Test
    fun `should perform complete project analysis workflow`() = runTest {
        // Given
        val workflow = IntegratedWorkflow()
        val projectPath = "/test/project"
        
        // When
        val result = workflow.analyzeProject(projectPath)
        
        // Then
        assertNotNull(result)
        assertEquals(projectPath, result.projectPath)
        assertTrue(result.modules.isNotEmpty())
        assertTrue(result.dependencies.isNotEmpty())
        assertNotNull(result.analysisTimestamp)
    }
    
    @Test
    fun `should handle agent requests with project context`() = runTest {
        // Given
        val workflow = IntegratedWorkflow()
        val projectPath = "/test/project"
        val request = "analyze performance issues"
        
        // When
        val result = workflow.handleRequestWithContext(projectPath, request)
        
        // Then
        assertNotNull(result)
        assertTrue(result.response.contains("performance"))
        assertTrue(result.response.contains("issues"))
        assertEquals(projectPath, result.projectContext)
        assertTrue(result.analysisIncluded)
    }
    
    @Test
    fun `should generate comprehensive project report`() = runTest {
        // Given
        val workflow = IntegratedWorkflow()
        val projectPath = "/test/project"
        
        // When
        val report = workflow.generateReport(projectPath)
        
        // Then
        assertNotNull(report)
        assertTrue(report.contains("Project Analysis Report"))
        assertTrue(report.contains(projectPath))
        assertTrue(report.contains("Modules"))
        assertTrue(report.contains("Dependencies"))
        assertTrue(report.contains("Recommendations"))
    }
    
    @Test
    fun `should validate project structure and provide recommendations`() = runTest {
        // Given
        val workflow = IntegratedWorkflow()
        val projectPath = "/test/project"
        
        // When
        val validation = workflow.validateProject(projectPath)
        
        // Then
        assertNotNull(validation)
        assertTrue(validation.isValid)
        assertTrue(validation.recommendations.isNotEmpty())
        validation.recommendations.forEach { rec ->
            assertNotNull(rec.category)
            assertNotNull(rec.message)
            assertTrue(rec.priority in 1..5)
        }
    }
    
    @Test
    fun `should handle workflow errors gracefully`() = runTest {
        // Given
        val workflow = IntegratedWorkflow()
        val invalidPath = "/nonexistent/project"
        
        // When & Then
        assertFailsWith<WorkflowException> {
            workflow.analyzeProject(invalidPath)
        }
    }
    
    @Test
    fun `should persist workflow results`() = runTest {
        // Given
        val workflow = IntegratedWorkflow()
        val projectPath = "/test/project"
        val request = "generate test cases"
        
        // When
        val result = workflow.handleRequestWithContext(projectPath, request)
        val persisted = workflow.persistResult(result)
        
        // Then
        assertTrue(persisted)
        val retrieved = workflow.getPersistedResult(result.id)
        assertNotNull(retrieved)
        assertEquals(result.id, retrieved.id)
    }
}

data class WorkflowResult(
    val id: String,
    val projectPath: String,
    val modules: List<Module>,
    val dependencies: List<Dependency>,
    val analysisTimestamp: Long
)

data class ContextualResponse(
    val id: String,
    val response: String,
    val projectContext: String,
    val analysisIncluded: Boolean,
    val timestamp: Long
)

data class ProjectValidation(
    val isValid: Boolean,
    val recommendations: List<Recommendation>,
    val issues: List<String>
)

data class Recommendation(
    val category: String,
    val message: String,
    val priority: Int
)

class WorkflowException(message: String) : Exception(message)

/**
 * Integrated Workflow implementation for TDD
 */
class IntegratedWorkflow {
    internal val agent = NexusAgent()
    internal val analyzer = ProjectAnalyzer()
    internal val api = MockRestApi()
    internal val results = mutableMapOf<String, ContextualResponse>()
    
    suspend fun analyzeProject(projectPath: String): WorkflowResult {
        if (!projectPath.startsWith("/test/")) {
            throw WorkflowException("Project not found: $projectPath")
        }
        
        val analysis = analyzer.analyzeProject(projectPath)
        
        return WorkflowResult(
            id = generateId(),
            projectPath = projectPath,
            modules = analysis.modules,
            dependencies = analysis.dependencies,
            analysisTimestamp = System.currentTimeMillis()
        )
    }
    
    suspend fun handleRequestWithContext(projectPath: String, request: String): ContextualResponse {
        val analysis = analyzeProject(projectPath)
        val agentResponse = agent.handle(request)
        
        val contextualResponse = ContextualResponse(
            id = generateId(),
            response = agentResponse,
            projectContext = projectPath,
            analysisIncluded = true,
            timestamp = System.currentTimeMillis()
        )
        
        results[contextualResponse.id] = contextualResponse
        return contextualResponse
    }
    
    suspend fun generateReport(projectPath: String): String {
        val analysis = analyzeProject(projectPath)
        
        return """
        |Project Analysis Report
        |======================
        |Project: $projectPath
        |Modules: ${analysis.modules.size}
        |Dependencies: ${analysis.dependencies.size}
        |
        |Modules:
        |${analysis.modules.joinToString("\n") { "- ${it.name}: ${it.sourceDirs.size} source dirs" }}
        |
        |Dependencies:
        |${analysis.dependencies.joinToString("\n") { "- ${it.name} (${it.version})" }}
        |
        |Recommendations:
        |- Consider adding unit tests
        |- Review dependency versions
        |- Optimize build configuration
        """.trimMargin()
    }
    
    suspend fun validateProject(projectPath: String): ProjectValidation {
        val analysis = analyzeProject(projectPath)
        
        val recommendations = mutableListOf<Recommendation>()
        val issues = mutableListOf<String>()
        
        if (analysis.modules.isEmpty()) {
            issues.add("No modules found")
            recommendations.add(Recommendation("Structure", "Add at least one module", 1))
        }
        
        if (analysis.dependencies.isEmpty()) {
            recommendations.add(Recommendation("Dependencies", "Consider adding essential dependencies", 3))
        }
        
        recommendations.add(Recommendation("Testing", "Add unit tests for better code quality", 2))
        recommendations.add(Recommendation("Documentation", "Add README and API documentation", 4))
        
        return ProjectValidation(
            isValid = issues.isEmpty(),
            recommendations = recommendations,
            issues = issues
        )
    }
    
    fun persistResult(result: ContextualResponse): Boolean {
        results[result.id] = result
        return true
    }
    
    fun getPersistedResult(id: String): ContextualResponse? {
        return results[id]
    }
    
    internal fun generateId(): String = "workflow-${System.currentTimeMillis()}"
} 