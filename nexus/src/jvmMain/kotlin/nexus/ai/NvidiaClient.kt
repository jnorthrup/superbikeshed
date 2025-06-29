package nexus.ai

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * NVIDIA API Client for Nemotron Ultra model
 * Simple integration to get nexus running with AI capabilities
 */
class NvidiaClient(
    private val apiKey: String = "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",
    private val baseUrl: String = "https://integrate.api.nvidia.com/v1",
    private val model: String = "nvidia/llama-3.1-nemotron-ultra-253b-v1"
) {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        temperature: Double = 0.6,
        maxTokens: Int = 4096,
        topP: Double = 0.95,
        stream: Boolean = false
    ): ChatCompletionResponse = withContext(Dispatchers.IO) {
        
        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0,
            stream = stream
        )
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("NVIDIA API error: ${response.statusCode()} - ${response.body()}")
        }
        
        json.decodeFromString<ChatCompletionResponse>(response.body())
    }
    
    suspend fun simpleChat(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant.",
        detailedThinking: Boolean = false
    ): String {
        val messages = listOf(
            ChatMessage("system", if (detailedThinking) "detailed thinking on" else "detailed thinking off"),
            ChatMessage("system", systemPrompt),
            ChatMessage("user", prompt)
        )
        
        val response = chatCompletion(messages)
        return response.choices.firstOrNull()?.message?.content ?: "No response"
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.6,
    @SerialName("top_p") val topP: Double = 0.95,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    @SerialName("frequency_penalty") val frequencyPenalty: Double = 0.0,
    @SerialName("presence_penalty") val presencePenalty: Double = 0.0,
    val stream: Boolean = false
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)