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
 * Nemotron Provider - Thinking and Non-Thinking Modes
 * 
 * Thinking Mode: Uses chain-of-thought reasoning for complex tasks
 * Non-Thinking Mode: Direct responses for simple queries
 */
class NemotronProvider(
    private val apiKey: String = System.getenv("NVIDIA_API_KEY") ?: "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",
    private val thinkingModel: String = "nvidia/llama-3.1-nemotron-ultra-253b-v1",
    private val nonThinkingModel: String = "nvidia/llama-3.1-nemotron-70b-instruct"
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }
    
    @Serializable
    data class Message(
        val role: String,
        val content: String
    )
    
    @Serializable
    data class NemotronRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.7,
        @SerialName("max_tokens")
        val maxTokens: Int = 4096,
        val stream: Boolean = false
    )
    
    @Serializable
    data class NemotronResponse(
        val choices: List<Choice>
    ) {
        @Serializable
        data class Choice(
            val message: Message
        )
    }
    
    enum class ThinkingMode {
        THINKING,      // Complex reasoning with chain-of-thought
        NON_THINKING   // Direct responses
    }
    
    /**
     * Complete a task with specified thinking mode
     */
    suspend fun complete(
        prompt: String,
        mode: ThinkingMode = ThinkingMode.NON_THINKING,
        temperature: Double = 0.7,
        systemPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        val messages = buildList {
            systemPrompt?.let {
                add(Message("system", it))
            }
            
            when (mode) {
                ThinkingMode.THINKING -> {
                    // Add thinking prompt prefix
                    add(Message("user", """
                        Please think step-by-step about this task:
                        
                        $prompt
                        
                        Begin with your reasoning process, then provide the solution.
                    """.trimIndent()))
                }
                ThinkingMode.NON_THINKING -> {
                    add(Message("user", prompt))
                }
            }
        }
        
        val model = when (mode) {
            ThinkingMode.THINKING -> thinkingModel
            ThinkingMode.NON_THINKING -> nonThinkingModel
        }
        
        val request = NemotronRequest(
            model = model,
            messages = messages,
            temperature = temperature
        )
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://integrate.api.nvidia.com/v1/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMinutes(2))
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(request)))
            .build()
        
        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("Nemotron API error: ${response.statusCode()} - ${response.body()}")
        }
        
        val nemotronResponse = json.decodeFromString<NemotronResponse>(response.body())
        return@withContext nemotronResponse.choices.firstOrNull()?.message?.content
            ?: throw RuntimeException("Empty response from Nemotron")
    }
    
    /**
     * Analyze code with thinking mode for complex analysis
     */
    suspend fun analyzeCode(
        code: String,
        question: String,
        useThinking: Boolean = true
    ): String {
        val mode = if (useThinking) ThinkingMode.THINKING else ThinkingMode.NON_THINKING
        val systemPrompt = "You are an expert code analyst. Analyze the provided code and answer questions about it."
        
        val prompt = """
            Code to analyze:
            ```
            $code
            ```
            
            Question: $question
        """.trimIndent()
        
        return complete(prompt, mode, systemPrompt = systemPrompt)
    }
    
    /**
     * Generate code with thinking mode for complex implementations
     */
    suspend fun generateCode(
        specification: String,
        language: String = "Kotlin",
        useThinking: Boolean = true
    ): String {
        val mode = if (useThinking) ThinkingMode.THINKING else ThinkingMode.NON_THINKING
        val systemPrompt = "You are an expert $language developer. Generate clean, idiomatic code."
        
        val prompt = """
            Generate $language code for the following specification:
            
            $specification
            
            Requirements:
            - Follow best practices
            - Include necessary imports
            - Add helpful comments
            - Handle errors appropriately
        """.trimIndent()
        
        return complete(prompt, mode, systemPrompt = systemPrompt)
    }
    
    /**
     * Quick query - always uses non-thinking mode
     */
    suspend fun quickQuery(query: String): String {
        return complete(query, ThinkingMode.NON_THINKING, temperature = 0.3)
    }
    
    /**
     * Deep analysis - always uses thinking mode
     */
    suspend fun deepAnalysis(topic: String, context: String): String {
        val prompt = """
            Topic: $topic
            
            Context:
            $context
            
            Please provide a comprehensive analysis.
        """.trimIndent()
        
        return complete(prompt, ThinkingMode.THINKING, temperature = 0.8)
    }
}

/**
 * Extension to integrate Nemotron with existing AI interface
 */
class NemotronAIProvider : AIProvider {
    private val nemotron = NemotronProvider()
    
    override suspend fun completeTask(task: String, context: String?): String {
        // Determine if task needs thinking based on keywords
        val needsThinking = task.contains("analyze", ignoreCase = true) ||
                           task.contains("design", ignoreCase = true) ||
                           task.contains("architect", ignoreCase = true) ||
                           task.contains("refactor", ignoreCase = true) ||
                           task.contains("complex", ignoreCase = true)
        
        val mode = if (needsThinking) {
            NemotronProvider.ThinkingMode.THINKING
        } else {
            NemotronProvider.ThinkingMode.NON_THINKING
        }
        
        val fullPrompt = buildString {
            append(task)
            context?.let {
                append("\n\nContext:\n$it")
            }
        }
        
        return nemotron.complete(fullPrompt, mode)
    }
}

/**
 * Factory for creating Nemotron instances
 */
object NemotronFactory {
    fun createThinkingNemo(): NemotronProvider {
        return NemotronProvider().apply {
            // Configure for maximum thinking
        }
    }
    
    fun createQuickNemo(): NemotronProvider {
        return NemotronProvider(
            nonThinkingModel = "nvidia/llama-3.1-nemotron-70b-instruct"
        ).apply {
            // Configure for speed
        }
    }
    
    fun createBalancedNemo(): NemotronProvider {
        return NemotronProvider()
    }
}