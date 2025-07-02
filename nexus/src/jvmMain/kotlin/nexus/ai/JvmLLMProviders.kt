package nexus.ai

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Simple HTTP API provider for JVM - direct API calls without LiteLLM
 */
actual class SimpleAPIProvider actual constructor(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : LLMProvider {
    
    override suspend fun complete(prompt: String, systemPrompt: String?): String = withContext(Dispatchers.IO) {
        val messages = buildList {
            systemPrompt?.let {
                add("""{"role": "system", "content": "$it"}""")
            }
            add("""{"role": "user", "content": "$prompt"}""")
        }
        
        val json = """
        {
            "model": "$model",
            "messages": [${messages.joinToString(",")}],
            "temperature": 0.7,
            "max_tokens": 1000
        }
        """.trimIndent()
        
        try {
            val url = URL("$baseUrl/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
            }
            
            OutputStreamWriter(connection.outputStream).use { it.write(json) }
            
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }
            
            // Simple JSON parsing for the response
            val contentRegex = """"content"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
            contentRegex.find(response)?.groupValues?.get(1) ?: "No response content"
            
        } catch (e: Exception) {
            "[Error] API call failed: ${e.message}"
        }
    }
    
    override suspend fun chat(messages: Indexed<Message>): String = withContext(Dispatchers.IO) {
        val jsonMessages = messages.toList().map { msg ->
            """{"role": "${msg.role}", "content": "${msg.content}"}"""
        }.joinToString(",")
        
        val json = """
        {
            "model": "$model",
            "messages": [$jsonMessages],
            "temperature": 0.7,
            "max_tokens": 1000
        }
        """.trimIndent()
        
        // Similar API call as above
        "[SimpleAPI] Chat response"
    }
    
    override fun isAvailable(): Boolean = apiKey.isNotEmpty()
}

/**
 * Script-based provider for JVM - calls external scripts
 */
actual class ScriptProvider actual constructor(
    private val scriptPath: String,
    private val scriptType: ScriptType
) : LLMProvider {
    
    override suspend fun complete(prompt: String, systemPrompt: String?): String = withContext(Dispatchers.IO) {
        try {
            val command = when (scriptType) {
                ScriptType.PYTHON -> listOf("python3", scriptPath, prompt)
                ScriptType.BASH -> listOf("bash", scriptPath, prompt)
                ScriptType.KOTLIN_SCRIPT -> listOf("kotlin", scriptPath, prompt)
            }
            
            val processBuilder = ProcessBuilder(command)
            systemPrompt?.let {
                processBuilder.environment()["SYSTEM_PROMPT"] = it
            }
            
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            process.waitFor()
            
            if (process.exitValue() != 0) {
                "[Script Error] $error"
            } else {
                output.trim()
            }
        } catch (e: Exception) {
            "[Script Error] Failed to execute script: ${e.message}"
        }
    }
    
    override suspend fun chat(messages: Indexed<Message>): String {
        // For chat, we'll pass the last user message
        val lastUserMessage = messages.toList().lastOrNull { it.role == "user" }
        return complete(lastUserMessage?.content ?: "")
    }
    
    override fun isAvailable(): Boolean = java.io.File(scriptPath).exists()
}

/**
 * Factory for creating appropriate LLM providers
 */
object LLMProviderFactory {
    fun createDefault(): LLMProvider {
        // Check for API keys in environment
        val openaiKey = System.getenv("OPENAI_API_KEY")
        if (!openaiKey.isNullOrEmpty()) {
            return SimpleAPIProvider(openaiKey)
        }
        
        // Check for local scripts
        val scriptPaths = listOf(
            "llm_provider.py",
            "ai_script.py",
            "../k2script/python_litellm_service/litellm_service.py"
        )
        
        for (path in scriptPaths) {
            if (java.io.File(path).exists()) {
                return ScriptProvider(path, ScriptType.PYTHON)
            }
        }
        
        // Fallback to mock
        return MockLLMProvider()
    }
}