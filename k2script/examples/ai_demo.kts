#!/usr/bin/env k2script

// AI/LLM integration demo

println("=== K2script AI Demo ===", AnsiColor.CYAN)

// Check if we have any AI providers available
val providers = EnvironmentManager.AI.getAvailableProviders()
if (providers.isEmpty()) {
    println("❌ No AI providers configured!", AnsiColor.RED)
    println("Set environment variables like:")
    println("  export OPENAI_API_KEY=your_key")
    println("  export ANTHROPIC_API_KEY=your_key")
    exitProcess(1)
}

println("✓ Available providers: ${providers.joinToString(", ")}", AnsiColor.GREEN)

// Try to use AI if we have a provider
try {
    val model = when {
        "openai" in providers -> "gpt-3.5-turbo"
        "anthropic" in providers -> "claude-3-haiku-20240307"
        "groq" in providers -> "llama3-8b-8192"
        else -> providers.first()
    }
    
    println("\nUsing model: $model", AnsiColor.BLUE)
    
    // Simple AI call
    val response = askLLM(
        model = model,
        prompt = "Write a haiku about Kotlin scripting",
        systemPrompt = "You are a helpful programming assistant."
    )
    
    if (response.status == "success") {
        println("\n🤖 AI Response:", AnsiColor.GREEN)
        println(response.content)
    } else {
        println("❌ AI request failed: ${response.error_message}", AnsiColor.RED)
    }
    
    // Performance-measured AI call
    val quickResponse = perf.measure("AI quick question") {
        askLLMForContent(
            model = model,
            prompt = "What is 2+2?",
            maxTokens = 10
        )
    }
    
    println("\n🧮 Quick math: $quickResponse")
    
} catch (e: Exception) {
    log.error("AI demo failed", e)
    println("❌ AI integration error: ${e.message}", AnsiColor.RED)
}

println("\nAI demo complete!", AnsiColor.GREEN)