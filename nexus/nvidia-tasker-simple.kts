#!/usr/bin/env k2script

/**
 * NVIDIA Tasker Simple - WORKING version without external dependencies
 */

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Configuration
val apiKey = "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"
val model = "nvidia/llama-3.1-nemotron-ultra-253b-v1"
val baseUrl = "https://integrate.api.nvidia.com/v1/chat/completions"

// HTTP Client
val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .build()

// Simple JSON builder
fun buildChatRequest(prompt: String): String {
    return """
    {
        "model": "$model",
        "messages": [
            {"role": "system", "content": "You are a helpful AI assistant."},
            {"role": "user", "content": "${prompt.replace("\"", "\\\"")}"}
        ],
        "temperature": 0.6,
        "top_p": 0.95,
        "max_tokens": 4096
    }
    """.trimIndent()
}

// Extract content from response
fun extractContent(json: String): String? {
    val contentMatch = """"content"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
        .find(json)
    return contentMatch?.groupValues?.get(1)
        ?.replace("\\n", "\n")
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
}

// Main function
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: nvidia-tasker-simple.kts <prompt>")
        return
    }
    
    val prompt = args.joinToString(" ")
    
    try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(buildChatRequest(prompt)))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
            val content = extractContent(response.body())
            println(content ?: "No response content found")
        } else {
            println("Error: HTTP ${response.statusCode()}")
            println("Body: ${response.body()}")
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    }
}

// Run
main(args)