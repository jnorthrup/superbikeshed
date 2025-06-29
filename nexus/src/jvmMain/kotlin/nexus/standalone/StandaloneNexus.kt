package nexus.standalone

import nexus.ai.NvidiaClient
import kotlinx.coroutines.*

/**
 * Standalone Nexus - Simple AI-powered execution without reactor
 * Direct integration with NVIDIA API for immediate functionality
 */
object StandaloneNexus {
    
    private val nvidiaClient = NvidiaClient()
    
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("🎯 Standalone Nexus starting...")
        
        when {
            args.isEmpty() -> showHelp()
            args[0] == "--help" || args[0] == "-h" -> showHelp()
            args[0] == "--ai" -> executeAI(args.drop(1).joinToString(" "))
            args[0] == "--test" -> testNvidiaConnection()
            else -> executeAI(args.joinToString(" "))
        }
    }
    
    private fun showHelp() {
        println("""
            Standalone Nexus - AI-powered execution
            
            Usage: nexus <command> [args...]
            
            Commands:
              --ai <prompt>     Execute AI-powered task
              --test           Test NVIDIA API connection
              --help, -h       Show this help
              
            Examples:
              nexus --ai "explain kotlin coroutines"
              nexus --test
        """.trimIndent())
    }
    
    private suspend fun executeAI(prompt: String) {
        if (prompt.isBlank()) {
            println("Error: AI prompt is required")
            showHelp()
            return
        }
        
        try {
            println("🤖 Processing AI request...")
            val response = nvidiaClient.simpleChat(
                prompt = prompt,
                systemPrompt = "You are Nexus, an AI agent that helps with development tasks. Provide clear, actionable responses.",
                detailedThinking = false
            )
            
            println("📝 Response:")
            println(response)
            
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private suspend fun testNvidiaConnection() {
        try {
            println("🔗 Testing NVIDIA API connection...")
            val response = nvidiaClient.simpleChat(
                prompt = "Hello, can you confirm you're working?",
                systemPrompt = "Respond briefly that you are operational.",
                detailedThinking = false
            )
            
            println("✅ Connection successful!")
            println("Response: $response")
            
        } catch (e: Exception) {
            println("❌ Connection failed: ${e.message}")
            e.printStackTrace()
        }
    }
}