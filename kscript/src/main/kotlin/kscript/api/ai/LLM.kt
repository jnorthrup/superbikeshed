package kscript.api.ai

import kscript.ai.llm.LiteLLMClient
import kscript.ai.llm.LLMResponse // Make LLMResponse part of public API
import java.io.IOException // Explicit import for clarity
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
// No need to re-export LLMResponse with 'val LLMResponse = ...' if it's already public and imported.
// Ensure kscript.ai.llm.LLMResponse is public.

/**
 * Makes a request to a LiteLLM-compatible model and returns the full response object.
 *
 * This function provides access to the underlying LiteLLM service managed by kscript.
 * It will attempt to start the service if it's not already running.
 * The call is blocking and will wait for the LLM response or a timeout.
 *
 * @param model The model identifier (e.g., "gpt-3.5-turbo", "ollama/llama2", "claude-3-opus-20240229").
 * @param prompt The main user prompt or query.
 * @param systemPrompt Optional system prompt to guide the AI's overall behavior or persona.
 * @param apiKey Optional API key for the LLM provider. If null, LiteLLM will attempt to use
 *               environment variables (e.g., OPENAI_API_KEY, ANTHROPIC_API_KEY).
 * @param temperature Optional sampling temperature (e.g., 0.7). Higher values make output more random.
 * @param maxTokens Optional maximum number of tokens to generate in the completion.
 * @param baseUrl Optional base URL, required for some self-hosted or proxy setups (e.g., local Ollama, vLLM).
 * @param customLlmProvider Optional custom LLM provider name if not automatically determined by LiteLLM from the model string.
 * @param requestTimeoutSeconds The maximum time in seconds to wait for the LLM request to complete.
 * @return [LLMResponse] object containing the structured response from the LLM, including content, status, and any errors.
 * @throws IOException if the LLM request fails, times out, or if the underlying service cannot be started/contacted.
 *
 * Example usage in a .kts script:
 * ```kotlin
 * #!/usr/bin/env kscript
 *
 * import kscript.api.ai.askLLM
 * import kscript.api.ai.stopLiteLLMService // Optional: to explicitly stop the service
 *
 * try {
 *   val response = askLLM(
 *       model = "gpt-3.5-turbo", // Or any model LiteLLM supports
 *       prompt = "Write a short poem about Kotlin scripting."
 *       // Ensure OPENAI_API_KEY is set in your environment, or pass apiKey="sk-..."
 *   )
 *   println("LLM says: ${response.content}")
 *   // println("Full response: $response") // For more details
 * } catch (e: Exception) {
 *   System.err.println("Error calling LLM: ${e.message}")
 * } finally {
 *   // Optional: stop the service if you want to free up resources immediately.
 *   // stopLiteLLMService()
 *   // Otherwise, it will be stopped when the kscript process ends (or JVM exits).
 * }
 * ```
 */
@Throws(IOException::class)
fun askLLM(
    model: String,
    prompt: String,
    systemPrompt: String? = null,
    apiKey: String? = null,
    temperature: Double? = null,
    maxTokens: Int? = null,
    baseUrl: String? = null,
    customLlmProvider: String? = null,
    requestTimeoutSeconds: Long = 60
): LLMResponse {
    val messages = mutableListOf<Map<String, String>>()
    systemPrompt?.takeIf { it.isNotBlank() }?.let { messages.add(mapOf("role" to "system", "content" to it)) }
    messages.add(mapOf("role" to "user", "content" to prompt))

    val future = LiteLLMClient.complete(
        model = model,
        messages = messages,
        apiKey = apiKey,
        temperature = temperature,
        maxTokens = maxTokens,
        baseUrl = baseUrl,
        customLlmProvider = customLlmProvider,
        requestTimeoutSeconds = requestTimeoutSeconds // Passed to LiteLLMClient
    )

    try {
        // Blocking call for script-friendliness.
        // The timeout for future.get() should be slightly longer than requestTimeoutSeconds
        // to allow for the full duration of the request plus some IPC overhead.
        val response = future.get(requestTimeoutSeconds + 10, TimeUnit.SECONDS)
        if (response.status != "success") {
            throw IOException("LLM request failed: ${response.error_message ?: "Unknown error from LLM service."} (ID: ${response.id})")
        }
        return response
    } catch (e: TimeoutException) {
        throw IOException("LLM request timed out after ${requestTimeoutSeconds + 10} seconds waiting for response. (ID from future might not be available)", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt() // Preserve interrupt status
        throw IOException("LLM request was interrupted.", e)
    } catch (e: Exception) { // Catches ExecutionException from future.get()
        val cause = e.cause
        if (cause is IOException) throw cause // Re-throw underlying IOException
        throw IOException("LLM request failed: ${e.message ?: "Underlying cause: ${cause?.message}"}", e)
    }
}

/**
 * A simpler version of [askLLM] that directly returns the LLM's content as a String,
 * or throws an exception if the request fails or no content is returned.
 *
 * @param model The model identifier.
 * @param prompt The user's prompt string.
 * @param systemPrompt Optional system prompt.
 * @param apiKey Optional API key.
 * @param temperature Optional sampling temperature.
 * @param maxTokens Optional maximum tokens.
 * @param baseUrl Optional base URL.
 * @param customLlmProvider Optional custom LLM provider name.
 * @param requestTimeoutSeconds Timeout in seconds for the LLM request.
 * @return The content String from the LLM response.
 * @throws IOException if the request fails, times out, or the LLM returns no content.
 */
@Throws(IOException::class)
fun askLLMForContent(
    model: String,
    prompt: String,
    systemPrompt: String? = null,
    apiKey: String? = null,
    temperature: Double? = null,
    maxTokens: Int? = null,
    baseUrl: String? = null,
    customLlmProvider: String? = null,
    requestTimeoutSeconds: Long = 60
): String {
    val response = askLLM(
        model = model,
        prompt = prompt,
        systemPrompt = systemPrompt,
        apiKey = apiKey,
        temperature = temperature,
        maxTokens = maxTokens,
        baseUrl = baseUrl,
        customLlmProvider = customLlmProvider,
        requestTimeoutSeconds = requestTimeoutSeconds
    )
    return response.content ?: throw IOException("LLM returned no content. Status: ${response.status}, Error: ${response.error_message}, Raw: ${response.raw_response}")
}

/**
 * Explicitly stops the underlying LiteLLM Python service if it is running.
 *
 * Normally, the service is managed automatically and should shut down when the main kscript
 * process (JVM) exits. However, this function can be called for an earlier, controlled shutdown
 * if needed within a script, or for cleanup during testing.
 */
fun stopLiteLLMService() {
    LiteLLMClient.stopService()
}
