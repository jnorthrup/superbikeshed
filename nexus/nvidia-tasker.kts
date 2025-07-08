#!/usr/bin/env k2script

/**
 * NVIDIA Tasker - Primary Nexus Implementation
 * Status: WORKING
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Configuration
val apiKey = System.getenv("NVIDIA_API_KEY") ?: throw IllegalStateException("NVIDIA_API_KEY environment variable not set")
val model = "nvidia/llama-3.1-nemotron-ultra-253b-v1"

// JSON
val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

// HTTP Client
val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .build()

// Data classes
@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.6,
    @SerialName("top_p") val topP: Double = 0.95,
    @SerialName("max_tokens") val maxTokens: Int = 4096
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: ChatMessage
)

// Main function
suspend fun main(args: Array<String>) {
    println("=== NVIDIA Tasker (WORKING) ===")
    
    if (args.isEmpty()) {
        println("Usage: nvidia-tasker.kts <prompt>")
        return
    }
    
    val prompt = args.joinToString(" ")
    println("Prompt: $prompt")
    
    val messages = listOf(
        ChatMessage("system", "You are a helpful AI assistant."),
        ChatMessage("user", prompt)
    )
    
    try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://integrate.api.nvidia.com/v1/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(
                json.encodeToString(ChatRequest(model, messages))
            ))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
            val parsed = json.decodeFromString<ChatResponse>(response.body())
            val content = parsed.choices.firstOrNull()?.message?.content
            println("\nResponse:")
            println(content ?: "No response")
        } else {
            println("Error: ${response.statusCode()}")
            println(response.body())
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    }
}

// Run
runBlocking {
    main(args)
}