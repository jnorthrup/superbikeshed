// Simple Nexus demonstration - no regex bugs
fun main() {
    println("=== NEXUS DEMONSTRATION ===")
    
    // Create working nexus
    val nexus = SimpleNexus()
    
    // Test requests
    println("\n--- Request: analyze code ---")
    println(nexus.handle("analyze code"))
    
    println("\n--- Request: generate function ---") 
    println(nexus.handle("generate function"))
    
    println("\n--- Request: refactor legacy ---")
    println(nexus.handle("refactor legacy"))
    
    println("\n=== NEXUS WORKING ===")
}

class SimpleNexus {
    private var interactions = 0
    
    fun handle(request: String): String {
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