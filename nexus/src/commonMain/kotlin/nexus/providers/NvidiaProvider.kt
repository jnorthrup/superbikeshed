package nexus.providers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import nexus.http.HttpMuxer
import nexus.http.HttpRequest
import nexus.http.HttpResponse
import kotlinx.serialization.serializer

@Serializable
internal data class NvidiaRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048
)

@Serializable
internal data class Message(
    val role: String,
    val content: String
)

@Serializable
internal data class NvidiaResponse(
    val id: String,
    val model: String,
    val choices: List<Choice>
)

@Serializable
internal data class Choice(
    val message: Message,
    val finish_reason: String
)

internal class NvidiaProvider(
    private val apiKey: String,
    private val endpoint: String = "https://integrate.api.nvidia.com/v1"
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun generateResponse(
        model: String,
        prompt: String,
        temperature: Double = 0.7,
        maxTokens: Int = 2048
    ): Flow<String> = flow {
        val request = NvidiaRequest(
            model = model,
            messages = listOf(Message("user", prompt)),
            temperature = temperature,
            max_tokens = maxTokens
        )
        
        val response = with(HttpMuxer) {
            val httpRequest = "$endpoint/chat/completions".POST("")
            val withHeaders = httpRequest.header("Authorization" to "Bearer $apiKey")
                .header("Content-Type" to "application/json")
            val withJson = withHeaders.json(request, NvidiaRequest.serializer())
            withJson.first()
        }
        
        if (response.statusCode == 200) {
            val nvidiaResponse = json.decodeFromString(NvidiaResponse.serializer(), String(response.body))
            emit(nvidiaResponse.choices.firstOrNull()?.message?.content ?: "No response generated")
        } else {
            throw Exception("NVIDIA API request failed: ${response.statusCode}")
        }
    }
    
    fun listModels(): Flow<List<String>> = flow {
        val response = with(HttpMuxer) {
            val httpRequest = "$endpoint/models".GET()
            val withHeaders = httpRequest.header("Authorization" to "Bearer $apiKey")
            withHeaders.first()
        }
        
        if (response.statusCode == 200) {
            val models = json.decodeFromString<List<Model>>(String(response.body))
            emit(models.map { it.id })
        } else {
            throw Exception("Failed to fetch NVIDIA models: ${response.statusCode}")
        }
    }
}

@Serializable
private data class Model(
    val id: String,
    val name: String
) 