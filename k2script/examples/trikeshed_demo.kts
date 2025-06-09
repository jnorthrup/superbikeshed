#!/usr/bin/env k2script

// Demo of TrikeShed patterns in k2script

println("=== K2script TrikeShed Demo ===", AnsiColor.CYAN)

// Series<T> with α transformations (the ONLY transformation operator)
val numbers = Series.of(1, 2, 3, 4, 5)
println("\nOriginal numbers: ${numbers.▶}")

val doubled = numbers.α { it * 2 }
println("Doubled with α: ${doubled.▶}")

val processed = numbers
    .α { it * it }           // Square each
    .α { "num_$it" }         // Convert to strings
println("Processed: ${processed.▶}")

// Join<A,B> with j operator (the ONLY composition operator)
val name = "kotlin" j "script"
println("\nJoin example: ${name.first} + ${name.second}")

val config = "database" j "localhost:5432"
println("Config: ${config.first} -> ${config.second}")

// Environment variables with TrikeShed context
println("\n=== Environment Context ===")
val apiKey = env("OPENAI_API_KEY")
if (apiKey != null) {
    println("✓ OpenAI API key found (${apiKey.take(10)}...)", AnsiColor.GREEN)
} else {
    println("⚠ No OpenAI API key found", AnsiColor.YELLOW)
}

// Available AI providers
val providers = EnvironmentManager.AI.getAvailableProviders()
if (providers.isNotEmpty()) {
    println("Available AI providers: ${providers.joinToString(", ")}", AnsiColor.GREEN)
} else {
    println("No AI providers configured", AnsiColor.YELLOW)
}

// Context and logging
println("\n=== Context & Logging Demo ===")
log.info("This is logged with context awareness")
log.debug("Debug message (only shown if K2SCRIPT_LOG_LEVEL=DEBUG)")

// Performance monitoring  
val result = perf.measure("heavy computation") {
    // Simulate some work
    (1..1000000).sum()
}
println("Computation result: $result")

// Memory info
println("\n=== Memory Management ===")
println(Memory.report())

println("\n=== Script Arguments ===")
println("Script received ${args.size} arguments:")
args.forEachIndexed { i, arg -> 
    println("  [$i]: $arg")
}

println("\nK2script demo complete! 🚀", AnsiColor.GREEN)