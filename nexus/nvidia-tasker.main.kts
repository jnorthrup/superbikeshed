#!/usr/bin/env ./k2script/src/k2script

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
import java.time.LocalDateTime
import java.io.File
import kotlin.system.exitProcess

/**
 * NVIDIA Nemotron Ultra AI Assistant with Coordinate-Based File Editing
 * 
 * Core Philosophy:
 * 1. Coordinate-based edits (line numbers) are safer than pattern matching
 * 2. Shell tool integration for inspection (cat -n, grep -n)
 * 3. Safe regional insertions using heredocs
 * 4. Clear FSM states for operation lifecycle
 */

// --- FSM States ---
enum class TaskState {
    AWAITING_INSTRUCTION,
    PARSING_INSTRUCTION,
    CALLING_AI,
    APPLYING_MODIFICATION,
    REPORTING_SUCCESS,
    ERROR
}

// --- Edit Instructions ---
sealed class EditInstruction {
    data class Insert(
        val filePath: String,
        val afterLine: Int,
        val content: String
    ) : EditInstruction()
    
    data class Replace(
        val filePath: String,
        val startLine: Int,
        val endLine: Int,
        val content: String
    ) : EditInstruction()
    
    data class Delete(
        val filePath: String,
        val startLine: Int,
        val endLine: Int
    ) : EditInstruction()
}

// --- Configuration ---
val API_KEYS = listOf(
    System.getenv("NVIDIA_API_KEY"),
    System.getenv("NVIDIA_API_KEY_2"),
    System.getenv("NVIDIA_API_KEY_3")
).filterNotNull().filter { it.isNotBlank() }

var currentKeyIndex = 0
val BASE_URL = "https://integrate.api.nvidia.com/v1"
val MODEL = "nvidia/llama-3.1-nemotron-ultra-253b-v1"

fun getNextApiKey(): String {
    if (API_KEYS.isEmpty()) {
        throw IllegalStateException("No API keys configured")
    }
    val key = API_KEYS[currentKeyIndex]
    currentKeyIndex = (currentKeyIndex + 1) % API_KEYS.size
    return key
}

// JSON configuration
val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    prettyPrint = true
}

// HTTP Client
val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

// --- Data Classes ---
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("top_p") val topP: Double = 0.95,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val stream: Boolean = false
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

// --- Core NVIDIA AI Integration ---
suspend fun callNvidia(
    messages: List<ChatMessage>,
    temperature: Double = 0.7,
    maxRetries: Int = 3
): String? = withContext(Dispatchers.IO) {
    var lastError: Exception? = null
    var keysTriedCount = 0
    val totalKeys = API_KEYS.size
    
    repeat(maxRetries * totalKeys) { totalAttempt ->
        val attempt = totalAttempt % maxRetries
        val apiKey = getNextApiKey()
        
        if (totalAttempt > 0 && attempt == 0) {
            keysTriedCount++
            println("[Key rotation] Trying next API key (${keysTriedCount + 1}/$totalKeys)")
        }
        
        try {
            val requestBody = ChatCompletionRequest(
                model = MODEL,
                messages = messages,
                temperature = temperature
            )
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/chat/completions"))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            
            when (response.statusCode()) {
                200 -> {
                    val parsed = json.decodeFromString<ChatCompletionResponse>(response.body())
                    return@withContext parsed.choices.firstOrNull()?.message?.content
                }
                429 -> {
                    println("[Rate limit] Key exhausted, rotating...")
                    if (keysTriedCount >= totalKeys - 1) {
                        println("[Rate limit] All keys exhausted. Waiting 30 seconds...")
                        delay(30000L)
                        keysTriedCount = 0
                    }
                }
                401 -> {
                    println("[Error] Invalid API key. Trying next...")
                    if (keysTriedCount >= totalKeys - 1) {
                        return@withContext null
                    }
                }
                else -> {
                    println("[Error] API returned ${response.statusCode()}")
                    if (attempt < maxRetries - 1) {
                        delay(2000L * (attempt + 1))
                    }
                }
            }
        } catch (e: Exception) {
            lastError = e
            println("[Error] Attempt failed: ${e.message}")
            if (attempt < maxRetries - 1) {
                delay(1000L * (attempt + 1))
            }
        }
    }
    
    println("[Fatal] All attempts failed. Last error: ${lastError?.message}")
    null
}

// --- File Operations with Coordinate Safety ---
class FileEditor {
    private val allowedPaths: List<File> = System.getenv("K2SCRIPT_ALLOWED_PATHS")
        ?.split(":")
        ?.map { File(it).normalize() }
        ?: emptyList()

    private fun isPathAllowed(filePath: String): Boolean {
        if (allowedPaths.isEmpty()) {
            // If no allowed paths are specified, allow all (no sandboxing)
            return true
        }
        val targetFile = File(filePath).normalize()
        return allowedPaths.any { allowedDir ->
            targetFile.startsWith(allowedDir)
        }
    }

    fun applyEdit(instruction: EditInstruction): Result<String> {
        return when (instruction) {
            is EditInstruction.Insert -> insertAfterLine(
                instruction.filePath,
                instruction.afterLine,
                instruction.content
            )
            is EditInstruction.Replace -> replaceLines(
                instruction.filePath,
                instruction.startLine,
                instruction.endLine,
                instruction.content
            )
            is EditInstruction.Delete -> deleteLines(
                instruction.filePath,
                instruction.startLine,
                instruction.endLine
            )
        }
    }
    
    private fun insertAfterLine(filePath: String, afterLine: Int, content: String): Result<String> {
        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(Exception("File not found: $filePath"))
        }
        
        return try {
            val lines = file.readLines()
            if (afterLine < 0 || afterLine > lines.size) {
                return Result.failure(Exception("Invalid line number: $afterLine. File has ${lines.size} lines."))
            }
            
            val newLines = mutableListOf<String>()
            newLines.addAll(lines.take(afterLine))
            content.lines().forEach { newLines.add(it) }
            newLines.addAll(lines.drop(afterLine))
            
            file.writeText(newLines.joinToString("\n"))
            Result.success("Inserted ${content.lines().size} lines after line $afterLine")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun replaceLines(filePath: String, startLine: Int, endLine: Int, content: String): Result<String> {
        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(Exception("File not found: $filePath"))
        }
        
        return try {
            val lines = file.readLines()
            if (startLine < 1 || endLine > lines.size || startLine > endLine) {
                return Result.failure(Exception("Invalid line range: $startLine-$endLine. File has ${lines.size} lines."))
            }
            
            val newLines = mutableListOf<String>()
            newLines.addAll(lines.take(startLine - 1))
            content.lines().forEach { newLines.add(it) }
            newLines.addAll(lines.drop(endLine))
            
            file.writeText(newLines.joinToString("\n"))
            Result.success("Replaced lines $startLine-$endLine with ${content.lines().size} new lines")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun deleteLines(filePath: String, startLine: Int, endLine: Int): Result<String> {
        if (!isPathAllowed(filePath)) {
            return Result.failure(Exception("File path not allowed by sandboxing policy: $filePath"))
        }
        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(Exception("File not found: $filePath"))
        }
        
        return try {
            val lines = file.readLines()
            if (startLine < 1 || endLine > lines.size || startLine > endLine) {
                return Result.failure(Exception("Invalid line range: $startLine-$endLine. File has ${lines.size} lines."))
            }
            
            val newLines = mutableListOf<String>()
            newLines.addAll(lines.take(startLine - 1))
            newLines.addAll(lines.drop(endLine))
            
            file.writeText(newLines.joinToString("\n"))
            Result.success("Deleted lines $startLine-$endLine")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// --- Instruction Parser ---
fun parseInstruction(text: String): EditInstruction? {
    val lines = text.trim().lines()
    if (lines.isEmpty()) return null
    
    return when {
        lines[0].startsWith("INSERT:") -> {
            val fileRegex = """^INSERT:\s*(.+)""".toRegex()
            val lineRegex = """^AFTER-LINE:\s*(\d+)""".toRegex()
            val codeRegex = """<<CODE\n([\s\S]+?)\nCODE""".toRegex()
            
            val filePath = fileRegex.find(lines[0])?.groupValues?.get(1)?.trim() ?: return null
            val afterLine = lines.find { it.startsWith("AFTER-LINE:") }?.let {
                lineRegex.find(it)?.groupValues?.get(1)?.toInt()
            } ?: return null
            val content = codeRegex.find(text)?.groupValues?.get(1) ?: return null
            
            EditInstruction.Insert(filePath, afterLine, content)
        }
        lines[0].startsWith("REPLACE:") -> {
            val fileRegex = """^REPLACE:\s*(.+)""".toRegex()
            val rangeRegex = """^LINES:\s*(\d+)-(\d+)""".toRegex()
            val codeRegex = """<<CODE\n([\s\S]+?)\nCODE""".toRegex()
            
            val filePath = fileRegex.find(lines[0])?.groupValues?.get(1)?.trim() ?: return null
            val range = lines.find { it.startsWith("LINES:") }?.let {
                rangeRegex.find(it)?.let { match ->
                    match.groupValues[1].toInt() to match.groupValues[2].toInt()
                }
            } ?: return null
            val content = codeRegex.find(text)?.groupValues?.get(1) ?: return null
            
            EditInstruction.Replace(filePath, range.first, range.second, content)
        }
        lines[0].startsWith("DELETE:") -> {
            val fileRegex = """^DELETE:\s*(.+)""".toRegex()
            val rangeRegex = """^LINES:\s*(\d+)-(\d+)""".toRegex()
            
            val filePath = fileRegex.find(lines[0])?.groupValues?.get(1)?.trim() ?: return null
            val range = lines.find { it.startsWith("LINES:") }?.let {
                rangeRegex.find(it)?.let { match ->
                    match.groupValues[1].toInt() to match.groupValues[2].toInt()
                }
            } ?: return null
            
            EditInstruction.Delete(filePath, range.first, range.second)
        }
        else -> null
    }
}

// --- Main NVIDIA Tasker ---
class NvidiaTasker {
    private var state = TaskState.AWAITING_INSTRUCTION
    private val editor = FileEditor()
    
    suspend fun processTask(input: String) {
        state = TaskState.PARSING_INSTRUCTION
        println("[$state] Processing task...")
        
        // Check if it's an edit instruction
        val editInstruction = parseInstruction(input)
        if (editInstruction != null) {
            state = TaskState.APPLYING_MODIFICATION
            println("[$state] Applying file modification...")
            
            editor.applyEdit(editInstruction).fold(
                onSuccess = { message ->
                    state = TaskState.REPORTING_SUCCESS
                    println("[$state] $message")
                },
                onFailure = { error ->
                    state = TaskState.ERROR
                    println("[$state] Error: ${error.message}")
                }
            )
        } else {
            // It's an AI query
            state = TaskState.CALLING_AI
            println("[$state] Calling NVIDIA AI...")
            
            val messages = listOf(
                ChatMessage("system", "You are a helpful AI assistant that can analyze code and suggest coordinate-based edits. When suggesting edits, always use line numbers for safety."),
                ChatMessage("user", input)
            )
            
            val response = callNvidia(messages)
            if (response != null) {
                state = TaskState.REPORTING_SUCCESS
                println("\n$response")
            } else {
                state = TaskState.ERROR
                println("[$state] Failed to get AI response")
            }
        }
    }
}

// --- Entry Point ---
suspend fun main(args: Array<String>) {
    println("=== NVIDIA Nemotron Ultra Tasker ===")
    println("Loaded ${API_KEYS.size} API key(s) for rotation")
    println()
    
    val tasker = NvidiaTasker()
    
    when {
        args.isEmpty() -> {
            // Interactive mode
            println("Interactive mode. Commands:")
            println("  - Direct AI queries")
            println("  - File edits with INSERT:, REPLACE:, DELETE:")
            println("  - Type 'exit' to quit")
            println()
            
            while (true) {
                print("> ")
                val input = readLine() ?: break
                if (input.trim() == "exit") break
                
                if (input.contains("<<CODE")) {
                    // Multi-line edit instruction
                    val fullInput = buildString {
                        appendLine(input)
                        while (true) {
                            val line = readLine() ?: break
                            appendLine(line)
                            if (line.trim() == "CODE") break
                        }
                    }
                    tasker.processTask(fullInput)
                } else {
                    tasker.processTask(input)
                }
                println()
            }
        }
        args[0] == "--edit" -> {
            // Edit mode from stdin
            val instruction = generateSequence(::readLine).joinToString("\n")
            tasker.processTask(instruction)
        }
        else -> {
            // Direct query mode
            val query = args.joinToString(" ")
            tasker.processTask(query)
        }
    }
}

// Run the coroutine
runBlocking {
    main(args)
}