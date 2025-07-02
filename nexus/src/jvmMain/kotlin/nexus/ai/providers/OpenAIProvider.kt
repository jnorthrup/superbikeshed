package nexus.ai.providers

import nexus.ai.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

/**
 * OpenAI API Provider - Simple direct implementation
 * Just paste your API key when needed
 */
class OpenAIProvider(
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: ""
) : LLMProvider {
    
    private val baseUrl = "https://api.openai.com/v1"
    private val model = "gpt-3.5-turbo"
    
    override suspend fun complete(prompt: String, systemPrompt: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext "[Error] OpenAI API key not set"
        
        val json = buildString {
            append("""{"model":"$model","messages":[""")
            systemPrompt?.let {
                append("""{"role":"system","content":"$it"},""")
            }
            append("""{"role":"user","content":"${prompt.replace("\"", "\\\"")}"}""")
            append("],\"temperature\":0.7,\"max_tokens\":1000}")
        }
        
        try {
            val url = URL("$baseUrl/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            
            conn.outputStream.use { 
                OutputStreamWriter(it).use { writer ->
                    writer.write(json)
                }
            }
            
            val response = conn.inputStream.bufferedReader().readText()
            
            // Extract content from response
            val contentMatch = """"content"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                .find(response)
            
            contentMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: "No response"
        } catch (e: Exception) {
            "[OpenAI Error] ${e.message}"
        }
    }
    
    override suspend fun chat(messages: Indexed<Message>): String {
        val lastUser = messages.toList().lastOrNull { it.role == "user" }
        val systemMsg = messages.toList().firstOrNull { it.role == "system" }
        return complete(lastUser?.content ?: "", systemMsg?.content)
    }
    
    override fun isAvailable(): Boolean = apiKey.isNotEmpty()
}