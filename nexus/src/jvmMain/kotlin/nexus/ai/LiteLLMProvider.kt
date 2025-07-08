package nexus.ai

import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

/**
 * Simplified LiteLLM Provider for Nexus
 * 
 * Direct integration without Python dependency
 */
class LiteLLMProvider(
    private val apiKey: String? = System.getenv("OPENAI_API_KEY"),
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4"
) {
    
    data class Message(
        val role: String,
        val content: String
    )
    
    data class CompletionRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.7,
        val maxTokens: Int? = null
    )
    
    data class CompletionResponse(
        val content: String,
        val usage: Usage? = null
    )
    
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
    
    suspend fun complete(
        messages: List<Message>,
        temperature: Double = 0.7,
        maxTokens: Int? = null
    ): CompletionResponse = withContext(Dispatchers.IO) {
        
        require(!apiKey.isNullOrBlank()) { "API key not set. Please set OPENAI_API_KEY environment variable." }
        
        val url = URL("$baseUrl/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            // Configure request
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            
            // Build request body
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                put("temperature", temperature)
                maxTokens?.let { put("max_tokens", it) }
            }
            
            // Send request
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            // Read response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                
                val content = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                val usage = if (jsonResponse.has("usage")) {
                    val usageObj = jsonResponse.getJSONObject("usage")
                    Usage(
                        promptTokens = usageObj.getInt("prompt_tokens"),
                        completionTokens = usageObj.getInt("completion_tokens"),
                        totalTokens = usageObj.getInt("total_tokens")
                    )
                } else null
                
                CompletionResponse(content, usage)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw RuntimeException("API request failed with code $responseCode: $error")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    suspend fun completeTask(taskDescription: String): String {
        val messages = listOf(
            Message("system", "You are Nexus, a helpful development agent that assists with coding tasks."),
            Message("user", taskDescription)
        )
        
        return complete(messages).content
    }
}

/**
 * Factory for creating AI providers
 */
object AIProviderFactory {
    fun create(provider: String): LiteLLMProvider {
        return when (provider.lowercase()) {
            "litellm", "openai" -> LiteLLMProvider()
            "anthropic" -> LiteLLMProvider(
                apiKey = System.getenv("ANTHROPIC_API_KEY"),
                baseUrl = "https://api.anthropic.com/v1",
                model = "claude-3-opus-20240229"
            )
            else -> throw IllegalArgumentException("Unknown AI provider: $provider")
        }
    }
}