package nexus.ai.providers

import nexus.ai.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

/**
 * NVIDIA Nemotron Provider - Uses NVIDIA's API
 * Can use either NVIDIA API directly or through Hugging Face
 */
class NemotronProvider(
    private val apiKey: String = System.getenv("NVIDIA_API_KEY") ?: System.getenv("HF_TOKEN") ?: "",
    private val useHuggingFace: Boolean = System.getenv("HF_TOKEN") != null
) : LLMProvider {
    
    private val model = if (useHuggingFace) {
        "nvidia/Llama-3.1-Nemotron-70B-Instruct-HF"
    } else {
        "nvidia/llama-3.1-nemotron-70b-instruct"
    }
    
    private val baseUrl = if (useHuggingFace) {
        "https://api-inference.huggingface.co/models"
    } else {
        "https://integrate.api.nvidia.com/v1"
    }
    
    override suspend fun complete(prompt: String, systemPrompt: String?): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext "[Error] No NVIDIA_API_KEY or HF_TOKEN found. Nemotron is free - get a key at build.nvidia.com"
        
        val json = if (useHuggingFace) {
            // Hugging Face format
            val fullPrompt = buildString {
                systemPrompt?.let { append("System: $it\n\n") }
                append("User: $prompt\n\nAssistant:")
            }
            """{"inputs": "${fullPrompt.replace("\"", "\\\"")}", "parameters": {"max_new_tokens": 1000, "temperature": 0.7}}"""
        } else {
            // NVIDIA API format (OpenAI-compatible)
            buildString {
                append("""{"model":"$model","messages":[""")
                systemPrompt?.let {
                    append("""{"role":"system","content":"${it.replace("\"", "\\\"")}"},""")
                }
                append("""{"role":"user","content":"${prompt.replace("\"", "\\\"")}"}""")
                append("],\"temperature\":0.7,\"max_tokens\":1000}")
            }
        }
        
        try {
            val url = if (useHuggingFace) {
                URL("$baseUrl/$model")
            } else {
                URL("$baseUrl/chat/completions")
            }
            
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 10000 // 10 seconds
            
            conn.outputStream.use { 
                OutputStreamWriter(it).use { writer ->
                    writer.write(json)
                }
            }
            
            val response = conn.inputStream.bufferedReader().readText()
            
            // Extract content based on provider
            if (useHuggingFace) {
                // HF returns array: [{"generated_text": "..."}]
                val textMatch = """"generated_text"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                    .find(response)
                textMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: response
            } else {
                // NVIDIA uses OpenAI format
                val contentMatch = """"content"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                    .find(response)
                contentMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: "No response"
            }
        } catch (e: Exception) {
            "[Nemotron Error] ${e.message}\nTip: Get free API key at https://build.nvidia.com"
        }
    }
    
    override suspend fun chat(messages: Indexed<Message>): String {
        val lastUser = messages.toList().lastOrNull { it.role == "user" }
        val systemMsg = messages.toList().firstOrNull { it.role == "system" }
        return complete(lastUser?.content ?: "", systemMsg?.content)
    }
    
    override fun isAvailable(): Boolean = apiKey.isNotEmpty()
}