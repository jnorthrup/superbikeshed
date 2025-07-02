package nexus.ai.providers

import nexus.ai.*
import borg.trikeshed.lib.*

/**
 * Registry for available LLM providers
 * Easy to paste API keys or switch providers
 */
object ProviderRegistry {
    
    /**
     * Get provider by name
     * Supported: "nemotron", "nvidia", "openai", "anthropic", "ollama", "mock"
     */
    fun getProvider(name: String, apiKey: String? = null): LLMProvider {
        return when (name.lowercase()) {
            "nemotron", "nvidia" -> NemotronProvider(apiKey ?: System.getenv("NVIDIA_API_KEY") ?: System.getenv("HF_TOKEN") ?: "")
            "openai" -> OpenAIProvider(apiKey ?: System.getenv("OPENAI_API_KEY") ?: "")
            "anthropic", "claude" -> AnthropicProvider(apiKey ?: System.getenv("ANTHROPIC_API_KEY") ?: "")
            "ollama", "local" -> OllamaProvider()
            "mock", "test" -> MockLLMProvider()
            else -> {
                // Try to auto-detect available provider
                autoDetectProvider()
            }
        }
    }
    
    /**
     * Auto-detect first available provider
     * Priority: Nemotron (free) > Ollama (local) > OpenAI > Anthropic > Mock
     */
    fun autoDetectProvider(): LLMProvider {
        // Check Nemotron/NVIDIA first (free tier available)
        val nemotronKeys = listOf("NVIDIA_API_KEY", "HF_TOKEN")
        for (keyName in nemotronKeys) {
            System.getenv(keyName)?.let { key ->
                if (key.isNotEmpty()) {
                    println("[Nexus] Using Nemotron provider (via $keyName)")
                    return NemotronProvider(key)
                }
            }
        }
        
        // Check Ollama (no API key needed)
        val ollama = OllamaProvider()
        if (ollama.isAvailable()) {
            println("[Nexus] Using local Ollama provider")
            return ollama
        }
        
        // Check for other API keys
        System.getenv("OPENAI_API_KEY")?.let { key ->
            if (key.isNotEmpty()) {
                println("[Nexus] Using OpenAI provider")
                return OpenAIProvider(key)
            }
        }
        
        System.getenv("ANTHROPIC_API_KEY")?.let { key ->
            if (key.isNotEmpty()) {
                println("[Nexus] Using Anthropic provider")
                return AnthropicProvider(key)
            }
        }
        
        // Fallback to mock
        println("[Nexus] No LLM provider available, using mock")
        println("[Nexus] Tip: Get a free Nemotron API key at https://build.nvidia.com")
        return MockLLMProvider()
    }
    
    /**
     * List available providers and their status
     */
    fun listProviders(): Indexed<Join<String, Boolean>> {
        return 5 j { i ->
            when (i) {
                0 -> "Nemotron" j NemotronProvider().isAvailable()
                1 -> "OpenAI" j OpenAIProvider().isAvailable()
                2 -> "Anthropic" j AnthropicProvider().isAvailable()
                3 -> "Ollama" j OllamaProvider().isAvailable()
                4 -> "Mock" j true
                else -> "Unknown" j false
            }
        }
    }
}