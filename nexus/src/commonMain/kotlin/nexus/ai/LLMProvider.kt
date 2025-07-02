package nexus.ai

import borg.trikeshed.lib.*

/**
 * Simple LLM provider interface for Nexus
 * Designed to be lightweight and KMP-friendly
 */
interface LLMProvider {
    suspend fun complete(prompt: String, systemPrompt: String? = null): String
    suspend fun chat(messages: Indexed<Message>): String
    fun isAvailable(): Boolean
}

data class Message(
    val role: String, // "system", "user", "assistant"
    val content: String
)

/**
 * Mock provider for testing without external dependencies
 */
class MockLLMProvider : LLMProvider {
    override suspend fun complete(prompt: String, systemPrompt: String?): String {
        return "[Mock Response] Processing: $prompt"
    }
    
    override suspend fun chat(messages: Indexed<Message>): String {
        val lastUserMessage = messages.toList().lastOrNull { it.role == "user" }
        return "[Mock Chat] Responding to: ${lastUserMessage?.content ?: "no message"}"
    }
    
    override fun isAvailable(): Boolean = true
}

/**
 * Simple HTTP API provider - direct API calls without LiteLLM
 */
expect class SimpleAPIProvider(
    apiKey: String,
    baseUrl: String = "https://api.openai.com/v1",
    model: String = "gpt-3.5-turbo"
) : LLMProvider

/**
 * Script-based provider for calling external scripts
 * Useful for integrating with existing Python/bash scripts
 */
expect class ScriptProvider(
    scriptPath: String,
    scriptType: ScriptType = ScriptType.PYTHON
) : LLMProvider

enum class ScriptType {
    PYTHON,
    BASH,
    KOTLIN_SCRIPT
}