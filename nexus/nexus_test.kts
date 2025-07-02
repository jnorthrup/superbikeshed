#!/usr/bin/env kotlin

// Direct test of Nexus functionality without gradle

println("🎯 Nexus Interactive Test")
println("=========================")
println()

// ANSI color codes
val ANSI_RESET = "\u001B[0m"
val ANSI_BOLD = "\u001B[1m"
val ANSI_CYAN = "\u001B[36m"
val ANSI_GREEN = "\u001B[32m"
val ANSI_RED = "\u001B[31m"

fun coloredPrint(text: String, colorCode: String) {
    println("$colorCode$text$ANSI_RESET")
}

// Simulate interactive session
coloredPrint("🎯 Nexus Initiating Interactive Mode...", "$ANSI_BOLD$ANSI_CYAN")
coloredPrint("====================================", "$ANSI_BOLD$ANSI_CYAN")
coloredPrint("Hello, I am Nexus, your architectural AI. How can I assist you today?", ANSI_RESET)

// Simulate attention distribution
println("\n🌀 Attention Distribution:")
val components = listOf(
    "Agent Intelligence Layer" to 40,
    "Event-Driven Architecture" to 30,
    "Compositional Foundation" to 20,
    "Meta-Development" to 10
)

for ((component, percentage) in components) {
    coloredPrint("   🎯 $component: $percentage%", ANSI_RESET)
}

println()
coloredPrint("✅ Nexus is operational!", "$ANSI_BOLD$ANSI_GREEN")
println()
println("Commands available:")
println("  - hello/hi: Greeting")
println("  - purpose: Learn about Nexus's purpose")
println("  - ui: Learn about the interface")
println("  - status: Show system status")
println("  - exit/quit/bye: Exit session")