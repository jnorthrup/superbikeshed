package tests.tdd

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.consumeAsFlow

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
        val agent = NexusAgent(this) // Pass TestScope as CoroutineScope
        try {
            val request = "analyze code"
            
            // When
            val response = agent.handle(request)
            
            // Then
            assertNotNull(response)
            assertTrue(response.contains("Analysis"))
            assertTrue(response.contains("interaction"))
        } finally {
            agent.close()
        }
    }
    
    @Test
    fun `should maintain interaction count`() = runTest {
        // Given
        val agent = NexusAgent(this) // Pass TestScope as CoroutineScope
        try {
            // When
            agent.handle("test1")
            agent.handle("test2")
            val response = agent.handle("test3")
            
            // Then
            assertTrue(response.contains("interaction #3"))
        } finally {
            agent.close()
        }
    }
    
    @Test
    fun `should handle different request types`() = runTest {
        // Given
        val agent = NexusAgent(this) // Pass TestScope as CoroutineScope
        try {
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
        } finally {
            agent.close()
        }
    }
    
    @Test
    fun `should handle unknown requests gracefully`() = runTest {
        // Given
        val agent = NexusAgent(this) // Pass TestScope as CoroutineScope
        try {
            val unknownRequest = "xyz123"
            
            // When
            val response = agent.handle(unknownRequest)
            
            // Then
            assertTrue(response.contains("Processed"))
            assertTrue(response.contains(unknownRequest))
        } finally {
            agent.close()
        }
    }
}

/**
 * Simple Nexus Agent implementation for TDD
 */
class NexusAgent(private val scope: CoroutineScope) {
    internal var interactions = 0
    private val requestChannel = Channel<String>()
    private val responseChannel = Channel<String>()

    init {
        scope.launch {
            processRequests()
        }
    }

    suspend fun handle(request: String): String {
        requestChannel.send(request)
        return responseChannel.receive()
    }

    private suspend fun processRequests() {
        for (request in requestChannel) {
            interactions++
            val response = when {
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
            responseChannel.send(response)
        }
    }

    fun close() {
        requestChannel.close()
        responseChannel.close()
    }
} 