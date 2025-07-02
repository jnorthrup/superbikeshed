package nexus.interactive

import borg.trikeshed.lib.*

/**
 * Interactive session for Nexus - ported from Python implementation
 * Provides a conversational interface with ANSI color support
 */
class InteractiveSession {
    
    companion object {
        // ANSI escape codes for coloring
        const val ANSI_RESET = "\u001B[0m"
        const val ANSI_BOLD = "\u001B[1m"
        const val ANSI_CYAN = "\u001B[36m"
        const val ANSI_GREEN = "\u001B[32m"
        const val ANSI_RED = "\u001B[31m"
    }
    
    fun coloredPrint(text: String, colorCode: String) {
        println("$colorCode$text$ANSI_RESET")
    }
    
    suspend fun start() {
        coloredPrint("🎯 Nexus Initiating Interactive Mode...", "$ANSI_BOLD$ANSI_CYAN")
        coloredPrint("====================================", "$ANSI_BOLD$ANSI_CYAN")
        coloredPrint("Hello, I am Nexus, your architectural AI. How can I assist you today?", ANSI_RESET)
        
        while (true) {
            try {
                print("${ANSI_BOLD}${ANSI_GREEN}You: $ANSI_RESET")
                val userInput = readInput()?.trim() ?: break
                
                when {
                    userInput.lowercase() in listOf("exit", "quit", "bye") -> {
                        coloredPrint("Nexus: Goodbye! May your architectures be sound.", "$ANSI_BOLD$ANSI_CYAN")
                        break
                    }
                    "hello" in userInput.lowercase() || "hi" in userInput.lowercase() -> {
                        coloredPrint("Nexus: Greetings, Architect! How may I serve your vision?", ANSI_RESET)
                    }
                    "purpose" in userInput.lowercase() -> {
                        coloredPrint("Nexus: My purpose is to facilitate Universal Development Autonomy. I aim to bridge conceptual design with executable reality.", ANSI_RESET)
                    }
                    "ui" in userInput.lowercase() -> {
                        coloredPrint("Nexus: My current interface is a conscientious CLI, designed for clarity and direct interaction. We can enhance it with ANSI colors and formatting.", ANSI_RESET)
                    }
                    "status" in userInput.lowercase() -> {
                        showStatus()
                    }
                    else -> {
                        coloredPrint("Nexus: I am still learning, Architect. Could you rephrase your query or ask about my purpose, UI, or status?", ANSI_RESET)
                    }
                }
                
            } catch (e: Exception) {
                coloredPrint("\nNexus: An unexpected error occurred: ${e.message}", "$ANSI_BOLD$ANSI_RED")
                break
            }
        }
    }
    
    private suspend fun showStatus() {
        coloredPrint("Nexus: Initiating status report...", ANSI_RESET)
        
        // Show attention distribution using TrikeShed types
        val attentionDistribution: Indexed<Join<String, Int>> = 4 j { i ->
            when (i) {
                0 -> "Agent Intelligence Layer" j 40
                1 -> "Event-Driven Architecture" j 30
                2 -> "Compositional Foundation" j 20
                3 -> "Meta-Development" j 10
                else -> "Unknown" j 0
            }
        }
        
        coloredPrint("\n🌀 Attention Distribution:", ANSI_RESET)
        for (i in 0 until attentionDistribution.size) {
            val component = attentionDistribution[i]
            coloredPrint("   🎯 ${component.a}: ${component.b}%", ANSI_RESET)
        }
    }
}

// Platform-specific function to read input
expect fun readInput(): String?