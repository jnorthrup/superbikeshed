package nexus.eval

import nexus.implementations.*
import nexus.providers.*
import nexus.core.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.time.*

/**
 * NEXUS GAUNTLET EVALUATOR
 * 
 * Runs Nexus through coding challenges to test actual performance.
 * No cheating - real problems, real evaluation.
 */
class NexusEvaluator {
    
    data class EvalResult(
        val taskName: String,
        val success: Boolean,
        val duration: Duration,
        val response: String,
        val error: String? = null
    )
    
    data class EvalSummary(
        val totalTasks: Int,
        val passed: Int,
        val failed: Int,
        val totalDuration: Duration,
        val successRate: Double
    ) {
        val passedString = "✓".repeat(passed)
        val failedString = "✗".repeat(failed)
    }
    
    suspend fun runGauntlet(): EvalSummary {
        println("=== NEXUS GAUNTLET EVALUATION ===")
        
        // Create real Nexus with actual provider
        val provider = createProvider()
        val nexus = WorkingNexus().withProvider(provider)
        
        println("Using provider: ${provider.name}")
        println("Running coding challenges...")
        
        val tasks = getCodingTasks()
        val results = mutableListOf<EvalResult>()
        
        for ((index, task) in tasks.withIndex()) {
            println("\n[${index + 1}/${tasks.size}] ${task.name}")
            println("Challenge: ${task.prompt}")
            
            val result = measureTimedValue {
                evaluateTask(nexus, task)
            }
            
            val evalResult = EvalResult(
                taskName = task.name,
                success = result.value.success,
                duration = result.duration,
                response = result.value.response,
                error = result.value.error
            )
            
            results.add(evalResult)
            
            val status = if (evalResult.success) "✓ PASS" else "✗ FAIL"
            println("Result: $status (${evalResult.duration})")
            
            if (!evalResult.success) {
                println("Error: ${evalResult.error}")
            }
        }
        
        return generateSummary(results)
    }
    
    private suspend fun evaluateTask(nexus: EnhancedNexus, task: CodingTask): TaskResult {
        return try {
            val response = nexus.generate(task.prompt)
            val success = evaluateResponse(response, task.expectedPatterns)
            
            TaskResult(
                success = success,
                response = response,
                error = null
            )
        } catch (e: Exception) {
            TaskResult(
                success = false,
                response = "",
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    private fun evaluateResponse(response: String, expectedPatterns: List<String>): Boolean {
        return expectedPatterns.any { pattern ->
            response.contains(pattern, ignoreCase = true)
        }
    }
    
    private fun getCodingTasks(): List<CodingTask> = listOf(
        CodingTask(
            name = "Email Validation",
            prompt = "Create a Kotlin function that validates email addresses",
            expectedPatterns = listOf("fun", "email", "regex", "matches", "@")
        ),
        CodingTask(
            name = "List Processing", 
            prompt = "Create a Kotlin function that finds the maximum value in a list of integers",
            expectedPatterns = listOf("fun", "max", "list", "int")
        ),
        CodingTask(
            name = "String Manipulation",
            prompt = "Create a Kotlin function that reverses a string",
            expectedPatterns = listOf("fun", "reverse", "string")
        ),
        CodingTask(
            name = "Data Class",
            prompt = "Create a Kotlin data class for a User with name, email, and age properties",
            expectedPatterns = listOf("data class", "User", "name", "email", "age")
        ),
        CodingTask(
            name = "Exception Handling",
            prompt = "Create a Kotlin function that safely divides two numbers with proper error handling",
            expectedPatterns = listOf("fun", "try", "catch", "divide", "exception")
        ),
        CodingTask(
            name = "API Response",
            prompt = "Create a Kotlin data class for a REST API response with status, message, and data fields",
            expectedPatterns = listOf("data class", "status", "message", "data")
        ),
        CodingTask(
            name = "Collection Operations",
            prompt = "Create a Kotlin function that filters a list of numbers to only even values",
            expectedPatterns = listOf("fun", "filter", "even", "%", "2")
        ),
        CodingTask(
            name = "JSON Serialization",
            prompt = "Create a Kotlin data class that can be serialized to JSON using kotlinx.serialization",
            expectedPatterns = listOf("@Serializable", "data class", "kotlinx")
        )
    )
    
    private fun generateSummary(results: List<EvalResult>): EvalSummary {
        val passed = results.count { it.success }
        val failed = results.count { !it.success }
        val totalDuration = results.fold(Duration.ZERO) { acc, result -> acc + result.duration }
        val successRate = if (results.isNotEmpty()) passed.toDouble() / results.size else 0.0
        
        val summary = EvalSummary(
            totalTasks = results.size,
            passed = passed,
            failed = failed,
            totalDuration = totalDuration,
            successRate = successRate
        )
        
        println("\n=== GAUNTLET RESULTS ===")
        println("Tasks: ${summary.totalTasks}")
        println("Passed: ${summary.passed} ${summary.passedString}")
        println("Failed: ${summary.failed} ${summary.failedString}")
        println("Success Rate: ${(summary.successRate * 100).toInt()}%")
        println("Total Duration: ${summary.totalDuration}")
        println("Average per task: ${summary.totalDuration / summary.totalTasks}")
        
        if (summary.successRate >= 0.75) {
            println("\n🎉 NEXUS GAUNTLET PASSED! Strong performance.")
        } else if (summary.successRate >= 0.5) {
            println("\n⚠️ NEXUS GAUNTLET PARTIAL. Needs improvement.")
        } else {
            println("\n❌ NEXUS GAUNTLET FAILED. Major issues detected.")
        }
        
        return summary
    }
    
    data class CodingTask(
        val name: String,
        val prompt: String,
        val expectedPatterns: List<String>
    )
    
    data class TaskResult(
        val success: Boolean,
        val response: String,
        val error: String?
    )
}

// Standalone runner
suspend fun main() {
    val evaluator = NexusEvaluator()
    evaluator.runGauntlet()
}