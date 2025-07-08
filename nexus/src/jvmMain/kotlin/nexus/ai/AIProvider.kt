package nexus.ai

/**
 * Common interface for AI providers
 */
interface AIProvider {
    suspend fun completeTask(task: String, context: String? = null): String
}

/**
 * Factory for creating AI providers
 */
object AIProviderFactory {
    fun create(provider: String): AIProvider {
        return when (provider.lowercase()) {
            "nemotron", "nemo" -> NemotronAIProvider()
            "litellm", "openai", "gpt" -> LiteLLMAIProvider()
            else -> throw IllegalArgumentException("Unknown AI provider: $provider")
        }
    }
    
    fun createThinkingProvider(): AIProvider = NemotronAIProvider()
    fun createQuickProvider(): AIProvider = LiteLLMAIProvider()
}