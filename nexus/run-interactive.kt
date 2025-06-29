#!/usr/bin/env kotlin -howtorun script

/**
 * Interactive Nexus Runner
 * Run with: kotlin run-interactive.kt
 */

println("🎯 Nexus Interactive Mode")
println("=" * 40)
println()
println("Available commands:")
println("  1. demo    - Run agent configuration demo")
println("  2. reactor - Run reactor pattern demo")
println("  3. server  - Start RelaxFactory server")
println("  4. help    - Show this help")
println("  5. exit    - Exit")
println()

while (true) {
    print("nexus> ")
    val input = readLine()?.trim()?.lowercase() ?: continue
    
    when (input) {
        "1", "demo" -> {
            println("Running agent demo...")
            val result = Runtime.getRuntime().exec(
                arrayOf("./gradlew", ":nexus:runStandaloneNexus")
            )
            result.inputStream.bufferedReader().forEachLine { println(it) }
            result.errorStream.bufferedReader().forEachLine { println(it) }
        }
        
        "2", "reactor" -> {
            println("Reactor pattern demonstration:")
            println("- Event-driven architecture")
            println("- Non-blocking I/O")
            println("- Attention distribution across components")
            println("(Full implementation in StandaloneNexus.kt)")
        }
        
        "3", "server" -> {
            println("RelaxFactory server features:")
            println("- QUIC/HTTP3 protocol")
            println("- CouchDB-compatible API")
            println("- IPFS integration")
            println("- Agent coordination")
            println("(Requires DSL generation to run)")
        }
        
        "4", "help" -> {
            println("\nNexus is an agent framework for autonomous development")
            println("It demonstrates architectural patterns through:")
            println("- Agent-based autonomous behavior")
            println("- Reactor pattern for event handling")
            println("- Attention distribution mechanisms")
            println("- Compositional patterns from Trikeshed")
        }
        
        "5", "exit", "quit" -> {
            println("👋 Exiting Nexus interactive mode")
            break
        }
        
        else -> {
            println("Unknown command: $input")
            println("Type 'help' for available commands")
        }
    }
    println()
}