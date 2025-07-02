package nexus.ai.providers

import nexus.ai.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

/**
 * Anthropic Claude API Provider - Simple direct implementation
 * Just paste your API key when needed
 */
class AnthropicProvider(
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: ""
) : LLMProvider {
    
    private val baseUrl = "https://api.anthropic.com/v1"
    private val model = "claude-3-sonnet-20240229"
    private val anthropicVersion = "2023-06-01"
    
    override suspend fun complete(prompt: String, systemPrompt: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext "[Error] Anthropic API key not set"
        
        val json = buildString {
            append("""{"model":"$model",""")
            append(""""max_tokens":1000,""")
            systemPrompt?.let {
                append(""""system":"${it.replace("\"", "\\\"")}",""")
            }
            append(""""messages":[{"role":"user","content":"${prompt.replace("\"", "\\\"")}"}]""")
            append("}")
        }
        
        try {
            val url = URL("$baseUrl/messages")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", anthropicVersion)
            conn.doOutput = true
            
            conn.outputStream.use { 
                OutputStreamWriter(it).use { writer ->
                    writer.write(json)
                }
            }
            
            val response = conn.inputStream.bufferedReader().readText()
            
            // Extract content from Claude's response format
            val contentMatch = """"text"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                .find(response)
            
            contentMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: "No response"
        } catch (e: Exception) {
            "[Anthropic Error] ${e.message}"
        }
    }
    
    override suspend fun chat(messages: Indexed<Message>): String {
        val lastUser = messages.toList().lastOrNull { it.role == "user" }
        val systemMsg = messages.toList().firstOrNull { it.role == "system" }
        return complete(lastUser?.content ?: "", systemMsg?.content)
    }
    
    override fun isAvailable(): Boolean = apiKey.isNotEmpty()
}