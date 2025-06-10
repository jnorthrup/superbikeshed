
#!/usr/bin/env k2script

// k2script example for LiteLLM integration

// Import the necessary functions from the k2script AI API
import k2script.api.ai.askLLM
import k2script.api.ai.askLLMForContent
import k2script.api.ai.stopLiteLLMService // Optional: for explicit service shutdown

#!/usr/bin/env kscript

// kscript example for LiteLLM integration

// Import the necessary functions from the kscript AI API
import kscript.api.ai.askLLM
import kscript.api.ai.askLLMForContent
import kscript.api.ai.stopLiteLLMService // Optional: for explicit service shutdown
println("Kscript LiteLLM Integration Example")
>>>>>>> origin/jules_wip_12008771546559725757
println("===================================")

// Ensure your API keys are set as environment variables.
// For example, for OpenAI:
// export OPENAI_API_KEY="your-api-key"
// For Anthropic:
// export ANTHROPIC_API_KEY="your-api-key"
// LiteLLM supports many providers; check LiteLLM documentation for specific env var names.

val model = "gpt-3.5-turbo" // Or any model LiteLLM supports, e.g., "ollama/llama2", "claude-3-haiku-20240307"
val prompt = "Write a short haiku about Kotlin."
val systemPrompt = "You are a helpful assistant that writes concise poems."

try {
    println("\nAttempting to use askLLM (full response object):")
    // Using askLLM to get the full response object
    val response = askLLM(
        model = model,
        prompt = prompt,
        systemPrompt = systemPrompt,
        // apiKey = "sk-...", // Optionally pass directly, though env vars are preferred
        requestTimeoutSeconds = 45 // Example timeout
    )

    if (response.status == "success") {
        println("LLM (${model}) responded:")
        println(response.content)
        // println("\nRaw response object: $response") // Uncomment for full details
    } else {
        System.err.println("LLM request failed: ${response.error_message}")
        System.err.println("Raw response object: $response")
    }

    println("\n-----------------------------------")
    println("Attempting to use askLLMForContent (direct content):")
    // Using askLLMForContent for a simpler way to get just the content
    val content = askLLMForContent(
        model = model,
        prompt = "What is the capital of France?",
        requestTimeoutSeconds = 30
    )
    println("LLM (${model}) answered: $content")

} catch (e: Exception) {
    System.err.println("\nAn error occurred during LLM interaction:")
    e.printStackTrace()
} finally {
    // Optional: Stop the LiteLLM service explicitly.
    // If not called, the Python service should terminate when the kscript (JVM) exits.
    // It's good practice if you're done with LLM calls in a long-running script.
    // println("\nStopping LiteLLM service (if it was started)...")
    // stopLiteLLMService()
    // println("LiteLLM service stop requested.")
}

println("\nExample finished.")
