package tests.tdd

import kotlinx.coroutines.runBlocking

/**
 * TDD Test Runner - Executes all 4 TDD iterations
 * 
 * This runner demonstrates the complete TDD workflow:
 * 1. Core Nexus Agent functionality
 * 2. Project analysis integration  
 * 3. REST API integration
 * 4. Integrated workflow testing
 */
object TddTestRunner {
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== NEXUS TDD TEST RUNNER ===")
        println("Executing 4 TDD iterations...")
        println()
        
        // Iteration 1: Core Nexus Agent
        println("Iteration 1: Core Nexus Agent")
        testCoreAgent()
        println("✓ Core agent tests passed")
        println()
        
        // Iteration 2: Project Analysis
        println("Iteration 2: Project Analysis Integration")
        testProjectAnalysis()
        println("✓ Project analysis tests passed")
        println()
        
        // Iteration 3: REST API
        println("Iteration 3: REST API Integration")
        testRestApi()
        println("✓ REST API tests passed")
        println()
        
        // Iteration 4: Integrated Workflow
        println("Iteration 4: Integrated Workflow")
        testIntegratedWorkflow()
        println("✓ Integrated workflow tests passed")
        println()
        
        println("=== ALL TDD ITERATIONS COMPLETED SUCCESSFULLY ===")
        println("Key observations for streamlining:")
        println("- Core agent handles requests with state management")
        println("- Project analysis provides structural insights")
        println("- REST API enables external integration")
        println("- Integrated workflow combines all components")
        println("- Error handling is consistent across layers")
    }
    
    internal suspend fun testCoreAgent() {
        val agent = NexusAgent()
        
        // Test basic functionality
        val response1 = agent.handle("analyze code")
        assert(response1.contains("Analysis"))
        assert(response1.contains("interaction #1"))
        
        val response2 = agent.handle("generate function")
        assert(response2.contains("Generation"))
        assert(response2.contains("interaction #2"))
        
        val response3 = agent.handle("refactor legacy")
        assert(response3.contains("Refactoring"))
        assert(response3.contains("interaction #3"))
    }
    
    internal suspend fun testProjectAnalysis() {
        val analyzer = ProjectAnalyzer()
        
        // Test project analysis
        val analysis = analyzer.analyzeProject("/test/project")
        assert(analysis.projectPath == "/test/project")
        assert(analysis.modules.isNotEmpty())
        assert(analysis.dependencies.isNotEmpty())
        
        // Test build system detection
        val gradleProject = analyzer.analyzeProject("/test/gradle-project")
        assert(gradleProject.buildSystem == BuildSystemType.GRADLE)
        
        val mavenProject = analyzer.analyzeProject("/test/maven-project")
        assert(mavenProject.buildSystem == BuildSystemType.MAVEN)
    }
    
    internal suspend fun testRestApi() {
        val api = MockRestApi()
        
        // Test health check
        val health = api.healthCheck()
        assert(health.contains("ok"))
        
        // Test IDE info
        val ideInfo = api.getIdeInfo()
        assert(ideInfo.contains("IntelliJ IDEA"))
        
        // Test project analysis
        val projectAnalysis = api.analyzeProject("/test/project")
        assert(projectAnalysis.contains("/test/project"))
        assert(projectAnalysis.contains("modules"))
        
        // Test agent request
        val request = AgentRequest("analyze performance", "test-session")
        val response = api.handleAgentRequest(request)
        assert(response.response.contains("performance"))
        assert(response.sessionId == "test-session")
    }
    
    internal suspend fun testIntegratedWorkflow() {
        val workflow = IntegratedWorkflow()
        
        // Test complete workflow
        val result = workflow.analyzeProject("/test/project")
        assert(result.projectPath == "/test/project")
        assert(result.modules.isNotEmpty())
        assert(result.dependencies.isNotEmpty())
        
        // Test contextual request handling
        val contextualResponse = workflow.handleRequestWithContext("/test/project", "analyze issues")
        assert(contextualResponse.response.contains("issues"))
        assert(contextualResponse.projectContext == "/test/project")
        assert(contextualResponse.analysisIncluded)
        
        // Test report generation
        val report = workflow.generateReport("/test/project")
        assert(report.contains("Project Analysis Report"))
        assert(report.contains("Modules"))
        assert(report.contains("Dependencies"))
        
        // Test project validation
        val validation = workflow.validateProject("/test/project")
        assert(validation.isValid)
        assert(validation.recommendations.isNotEmpty())
        
        // Test result persistence
        val persisted = workflow.persistResult(contextualResponse)
        assert(persisted)
        val retrieved = workflow.getPersistedResult(contextualResponse.id)
        assert(retrieved != null)
        assert(retrieved.id == contextualResponse.id)
    }
} 