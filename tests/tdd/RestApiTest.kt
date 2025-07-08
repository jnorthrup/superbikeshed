package tests.tdd

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

/**
 * TDD Iteration 3: REST API Integration
 * 
 * Test-Driven Development for REST API functionality:
 * - Health check endpoints
 * - Project analysis endpoints
 * - Agent interaction endpoints
 * - Error handling
 */
class RestApiTest {
    
    @Test
    fun `should respond to health check`() = testApplication {
        // Given
        val client = createClient { }
        
        // When
        val response = client.get("/api/health")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ok"))
    }
    
    @Test
    fun `should provide IDE information`() = testApplication {
        // Given
        val client = createClient { }
        
        // When
        val response = client.get("/api/ide/info")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("name"))
        assertTrue(body.contains("version"))
    }
    
    @Test
    fun `should analyze project via API`() = testApplication {
        // Given
        val client = createClient { }
        val projectPath = "/test/project"
        
        // When
        val response = client.get("/api/project/analyze?path=$projectPath")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(projectPath))
        assertTrue(body.contains("modules"))
    }
    
    @Test
    fun `should handle agent requests via API`() = testApplication {
        // Given
        val client = createClient { }
        val request = AgentRequest("analyze code", "test-session")
        
        // When
        val response = client.post("/api/agent/request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Analysis"))
        assertTrue(body.contains("test-session"))
    }
    
    @Test
    fun `should handle invalid project paths`() = testApplication {
        // Given
        val client = createClient { }
        val invalidPath = "/nonexistent/project"
        
        // When
        val response = client.get("/api/project/analyze?path=$invalidPath")
        
        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("not found"))
    }
    
    @Test
    fun `should provide project dependencies via API`() = testApplication {
        // Given
        val client = createClient { }
        val projectPath = "/test/project"
        
        // When
        val response = client.get("/api/project/dependencies?path=$projectPath")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("dependencies"))
        assertTrue(body.contains("kotlin-stdlib"))
    }
}

data class AgentRequest(
    val message: String,
    val sessionId: String
)

data class AgentResponse(
    val response: String,
    val sessionId: String,
    val timestamp: Long
)

/**
 * Mock REST API implementation for TDD
 */
class MockRestApi {
    fun healthCheck(): String = """{"status":"ok","service":"Nexus API"}"""
    
    fun getIdeInfo(): String = """{"name":"IntelliJ IDEA","version":"2024.3","build":"241.8102.112"}"""
    
    fun analyzeProject(path: String): String {
        if (!path.startsWith("/test/")) {
            throw IllegalArgumentException("Project not found: $path")
        }
        return """{"projectPath":"$path","modules":[{"name":"main","sourceDirs":["$path/src/main/kotlin"]}]}"""
    }
    
    fun getDependencies(path: String): String {
        if (!path.startsWith("/test/")) {
            throw IllegalArgumentException("Project not found: $path")
        }
        return """{"dependencies":[{"name":"kotlin-stdlib","version":"1.9.0","type":"library"}]}"""
    }
    
    fun handleAgentRequest(request: AgentRequest): AgentResponse {
        val response = when {
            request.message.contains("analyze") -> "Analysis complete for: ${request.message}"
            request.message.contains("generate") -> "Generation complete for: ${request.message}"
            else -> "Processed: ${request.message}"
        }
        
        return AgentResponse(
            response = response,
            sessionId = request.sessionId,
            timestamp = System.currentTimeMillis()
        )
    }
} 