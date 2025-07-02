package nexus.ai.providers

import nexus.ai.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

/**
 * Ollama Local LLM Provider - For running models locally
 * No API key needed, just have Ollama running
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama2"
) : LLMProvider {
    
    override suspend fun complete(prompt: String, systemPrompt: String?): String = withContext(Dispatchers.IO) {
        val fullPrompt = buildString {
            systemPrompt?.let {
                append("System: $it\n\n")
            }
            append("User: $prompt\n\nAssistant: ")
        }
        
        val json = """
        {
            "model": "$model",
            "prompt": "${fullPrompt.replace("\"", "\\\"")}",
            "stream": false
        }
        """.trimIndent()
        
        try {
            val url = URL("$baseUrl/api/generate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000 // 5 seconds
            
            conn.outputStream.use { 
                OutputStreamWriter(it).use { writer ->
                    writer.write(json)
                }
            }
            
            val response = conn.inputStream.bufferedReader().readText()
            
            // Extract response from Ollama format
            val responseMatch = """"response"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                .find(response)
            
            responseMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: "No response"
        } catch (e: Exception) {
            "[Ollama Error] ${e.message} - Is Ollama running at $baseUrl?"
        }
    }
    
    override suspend fun chat(messages: Indexed<Message>): String {
        // Ollama doesn't have native chat format, so we convert to prompt
        val prompt = messages.toList().joinToString("\n") { msg ->
            "${msg.role.capitalize()}: ${msg.content}"
        }
        return complete(prompt)
    }
    
    override fun isAvailable(): Boolean {
        return try {
            val url = URL("$baseUrl/api/tags")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000 // 1 second
            conn.connect()
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
}