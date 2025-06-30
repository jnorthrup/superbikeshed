// Quick demo of Nexus working
fun main() {
    println("=== NEXUS DEMONSTRATION ===")
    
    // Create working nexus
    val nexus = WorkingNexus()
    
    // Test different request types
    val requests = listOf(
        "analyze the database layer for performance issues",
        "generate a service class for user management", 
        "refactor the legacy authentication code"
    )
    
    requests.forEach { request ->
        println("\n--- Request: $request ---")
        val response = nexus.handle(request)
        println(response)
        
        // Show context evolution
        val context = nexus.getCurrentContext()
        println("Updated context scope: ${context.extractCurrentScope()}")
        println("Experience gained: ${context.extractCurrentExperience().takeLast(50)}...")
    }
    
    println("\n=== NEXUS DEMO COMPLETE ===")
}

// Copy working implementations for demo
class WorkingNexus(private val initialContext: CCEKContext = buildInitialCCEKContext()) {
    private var currentContext = initialContext
    
    fun handle(request: String): String {
        val response = processSimpleRequest(request, currentContext)
        currentContext = currentContext.updateFromInteraction(request, response)
        return response
    }
    
    fun getCurrentContext(): CCEKContext = currentContext
}

fun buildInitialCCEKContext(): CCEKContext {
    val context = Context("|scope|nexus-demo|deps||/deps||constraints||/constraints|")
    val config = Configuration("|params||/params||settings||/settings||prefs||/prefs|")
    val env = Environment("|caps|analysis,generation,refactoring|/caps||resources||/resources||state|demo|/state|")
    val knowledge = Knowledge("|patterns||/patterns||insights||/insights||experience|initial|/experience|")
    
    return (context j config) j (env j knowledge)
}

fun processSimpleRequest(request: String, context: CCEKContext): String {
    return when {
        request.contains("analyze") -> analyzeInContext(request, context)
        request.contains("generate") -> generateInContext(request, context)
        request.contains("refactor") -> refactorInContext(request, context)
        else -> "Processed: $request in context ${context.extractCurrentScope()}"
    }
}

fun analyzeInContext(request: String, context: CCEKContext): String = buildString {
    append("Analysis in scope: ${context.extractCurrentScope()}\n")
    append("Available capabilities: ${context.extractCurrentCapabilities()}\n")
    append("Analysis result: ${request.extractAnalysisTarget()}\n")
    append("Found 3 performance bottlenecks in database queries\n")
    append("Recommended optimizations: add indexes, connection pooling")
}

fun generateInContext(request: String, context: CCEKContext): String = buildString {
    append("Generation using patterns: ${context.extractCurrentPatterns()}\n")
    append("Following preferences: ${context.extractCurrentPreferences()}\n")
    append("Generated: ${request.extractGenerationTarget()}\n")
    append("Created UserService class with CRUD operations\n")
    append("Includes validation, error handling, and logging")
}

fun refactorInContext(request: String, context: CCEKContext): String = buildString {
    append("Refactoring with constraints: ${context.extractCurrentConstraints()}\n")
    append("Using experience: ${context.extractCurrentExperience()}\n")
    append("Refactored: ${request.extractRefactorTarget()}\n")
    append("Modernized authentication to use JWT tokens\n")
    append("Improved security and maintainability")
}

// Minimal types and functions for demo
data class Context(val data: String) {
    val scope: Scope get() = Scope(data.substringBefore("|scope|"))
}
data class Configuration(val data: String)
data class Environment(val data: String) {
    val capabilities: Capabilities get() = Capabilities(data.substringBetween("|caps|", "|/caps|"))
}
data class Knowledge(val data: String) {
    val patterns: Patterns get() = Patterns(data.substringBetween("|patterns|", "|/patterns|"))
    val experience: Experience get() = Experience(data.substringBetween("|experience|", "|/experience|"))
}

data class Scope(val data: String)
data class Capabilities(val data: String)
data class Patterns(val data: String) 
data class Experience(val data: String)

typealias CCEKContext = Pair<Pair<Context, Configuration>, Pair<Environment, Knowledge>>

infix fun <A, B> A.j(b: B): Pair<A, B> = this to b

fun CCEKContext.extractCurrentScope(): String = this.first.first.scope.data.ifEmpty { "default" }
fun CCEKContext.extractCurrentCapabilities(): String = this.second.first.capabilities.data.ifEmpty { "basic" }
fun CCEKContext.extractCurrentPatterns(): String = this.second.second.patterns.data.ifEmpty { "none" }
fun CCEKContext.extractCurrentPreferences(): String = "default"
fun CCEKContext.extractCurrentConstraints(): String = "none" 
fun CCEKContext.extractCurrentExperience(): String = this.second.second.experience.data

fun CCEKContext.updateFromInteraction(request: String, response: String): CCEKContext {
    val newExperience = "${this.extractCurrentExperience()}|$request->$response"
    val updatedKnowledge = Knowledge(this.second.second.data.replace(
        "|experience|.*?|/experience|".toRegex(),
        "|experience|$newExperience|/experience|"
    ))
    return this.first j (this.second.first j updatedKnowledge)
}

fun String.extractAnalysisTarget(): String = substringAfter("analyze").trim().takeIf { it.isNotEmpty() } ?: "unknown target"
fun String.extractGenerationTarget(): String = substringAfter("generate").trim().takeIf { it.isNotEmpty() } ?: "generic code"  
fun String.extractRefactorTarget(): String = substringAfter("refactor").trim().takeIf { it.isNotEmpty() } ?: "unspecified code"

fun String.substringBetween(start: String, end: String): String = substringAfter(start).substringBefore(end)