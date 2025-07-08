package tests.tdd

import kotlin.test.*
import kotlinx.coroutines.test.runTest

/**
 * TDD Iteration 1: Core Nexus Agent
 * 
 * Test-Driven Development for the fundamental Nexus Agent capabilities:
 * - Request handling
 * - Response generation  
 * - State management
 * - Basic task execution
 */
class NexusAgentTest {
    
    @Test
    fun `should handle basic request and return response`() = runTest {
        // Given
        val agent = NexusAgent()
        val request = "analyze code"
        
        // When
        val response = agent.handle(request)
        
        // Then
        assertNotNull(response)
        assertTrue(response.contains("Analysis"))
        assertTrue(response.contains("interaction"))
    }
    
    @Test
    fun `should maintain interaction count`() = runTest {
        // Given
        val agent = NexusAgent()
        
        // When
        agent.handle("test1")
        agent.handle("test2")
        val response = agent.handle("test3")
        
        // Then
        assertTrue(response.contains("interaction #3"))
    }
    
    @Test
    fun `should handle different request types`() = runTest {
        // Given
        val agent = NexusAgent()
        
        // When & Then
        val analyzeResponse = agent.handle("analyze performance")
        assertTrue(analyzeResponse.contains("performance"))
        assertTrue(analyzeResponse.contains("issues"))
        
        val generateResponse = agent.handle("generate function")
        assertTrue(generateResponse.contains("function"))
        assertTrue(generateResponse.contains("Created"))
        
        val refactorResponse = agent.handle("refactor legacy")
        assertTrue(refactorResponse.contains("legacy"))
        assertTrue(refactorResponse.contains("Modernized"))
    }
    
    @Test
    fun `should handle unknown requests gracefully`() = runTest {
        // Given
        val agent = NexusAgent()
        val unknownRequest = "xyz123"
        
        // When
        val response = agent.handle(unknownRequest)
        
        // Then
        assertTrue(response.contains("Processed"))
        assertTrue(response.contains(unknownRequest))
    }
}

/**
 * Simple Nexus Agent implementation for TDD
 */
class NexusAgent {
    internal var interactions = 0
    
    suspend fun handle(request: String): String {
        interactions++
        
        return when {
            request.contains("analyze") -> {
                """Analysis complete (interaction #$interactions):
                |Target: ${request.substringAfter("analyze").trim()}
                |Found 3 issues: performance, security, maintainability
                |Recommended fixes: caching, input validation, refactoring""".trimMargin()
            }
            request.contains("generate") -> {
                """Generation complete (interaction #$interactions):
                |Target: ${request.substringAfter("generate").trim()}
                |Created class with CRUD operations
                |Includes error handling and logging""".trimMargin()
            }
            request.contains("refactor") -> {
                """Refactoring complete (interaction #$interactions):
                |Target: ${request.substringAfter("refactor").trim()}
                |Modernized architecture
                |Improved maintainability and performance""".trimMargin()
            }
            else -> "Processed: $request (interaction #$interactions)"
        }
    }
} 