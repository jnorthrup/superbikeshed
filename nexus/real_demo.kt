#!/usr/bin/env kotlin

@file:DependsOn("io.ktor:ktor-client-core-jvm:2.3.7")
@file:DependsOn("io.ktor:ktor-client-cio-jvm:2.3.7") 
@file:DependsOn("io.ktor:ktor-client-content-negotiation-jvm:2.3.7")
@file:DependsOn("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.7")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.2")

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<AnthropicMessage>
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContent>
)

@Serializable
data class AnthropicContent(
    val text: String
)

suspend fun callAnthropic(prompt: String): String {
    val apiKey = System.getenv("ANTHROPIC_API_KEY") ?: return "No Anthropic API key found"
    
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }
    
    try {
        val response = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(AnthropicRequest(
                model = "claude-3-5-sonnet-20241022",
                max_tokens = 1000,
                messages = listOf(AnthropicMessage("user", prompt))
            ))
        }
        
        val result: AnthropicResponse = response.body()
        return result.content.firstOrNull()?.text ?: "No response"
    } catch (e: Exception) {
        return "Error: ${e.message}"
    } finally {
        client.close()
    }
}

fun main() = runBlocking {
    println("=== NEXUS REAL PROVIDER DEMO ===")
    
    val anthropicKey = System.getenv("ANTHROPIC_API_KEY")
    val openaiKey = System.getenv("OPENAI_API_KEY")
    
    println("Environment check:")
    println("Anthropic API Key: ${if (anthropicKey != null) "✓ Found" else "✗ Missing"}")
    println("OpenAI API Key: ${if (openaiKey != null) "✓ Found" else "✗ Missing"}")
    
    if (anthropicKey != null) {
        println("\n--- Real Anthropic Call ---")
        val prompt = "Create a simple Kotlin function that validates an email address. Just return the code, no explanation."
        println("Prompt: $prompt")
        println("Response:")
        val response = callAnthropic(prompt)
        println(response)
    } else {
        println("\n--- Skipping Anthropic (no API key) ---")
    }
    
    println("\n=== REAL DEMO COMPLETE ===")
}