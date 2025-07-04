package nexus.agent

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.coroutines.coroutineContext
import nexus.toIdx

/**
 * NVIDIA Agent - Unified implementation combining all NVIDIA tasker functionality
 * 
 * Features:
 * - API key rotation for rate limit handling
 * - Tool execution (file operations, time, etc.)
 * - Coordinate-based file editing for safety
 * - Interactive and batch modes
 * - File interaction analysis
 */
class NvidiaAgent(
    private val apiKeys: Indexed<String> = listOf(
        System.getenv("NVIDIA_API_KEY"),
        System.getenv("NVIDIA_API_KEY_2"), 
        System.getenv("NVIDIA_API_KEY_3"),
        "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",
        "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"
    ).filterNotNull().filter { it.isNotBlank() }.toIdx(),
    private val baseUrl: String = "https://integrate.api.nvidia.com/v1",
    private val model: String = "nvidia/llama-3.1-nemotron-ultra-253b-v1"
) {
    private var currentKeyIndex = 0
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }
    
    // Agent states (FSM)
    sealed class AgentState {
        object AwaitingInstruction : AgentState()
        object ParsingInstruction : AgentState()
        object CallingAI : AgentState()
        object ExecutingTool : AgentState()
        object ApplyingModification : AgentState()
        object ReportingSuccess : AgentState()
        data class Error(val message: String) : AgentState()
    }
    
    // Tool definitions
    sealed class Tool {
        abstract val name: String
        abstract val description: String
        abstract suspend fun execute(args: JsonObject): String
        
        object GetCurrentTime : Tool() {
            override val name = "get_current_time"
            override val description = "Get the current date and time"
            override suspend fun execute(args: JsonObject) = 
                java.time.LocalDateTime.now().toString()
        }
        
        data class ReadFile(val basePath: String = ".") : Tool() {
            override val name = "read_file"
            override val description = "Read contents of a file"
            override suspend fun execute(args: JsonObject): String {
                val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
                return try {
                    java.io.File(basePath, path).readText()
                } catch (e: Exception) {
                    "Error reading file: ${e.message}"
                }
            }
        }
        
        data class WriteFile(val basePath: String = ".") : Tool() {
            override val name = "write_file"
            override val description = "Write content to a file"
            override suspend fun execute(args: JsonObject): String {
                val path = args["path"]?.jsonPrimitive?.content ?: return "Error: path required"
                val content = args["content"]?.jsonPrimitive?.content ?: return "Error: content required"
                return try {
                    java.io.File(basePath, path).writeText(content)
                    "Successfully wrote to $path"
                } catch (e: Exception) {
                    "Error writing file: ${e.message}"
                }
            }
        }
        
        data class ListFiles(val basePath: String = ".") : Tool() {
            override val name = "list_files"
            override val description = "List files in a directory"
            override suspend fun execute(args: JsonObject): String {
                val path = args["path"]?.jsonPrimitive?.content ?: "."
                return try {
                    val dir = java.io.File(basePath, path)
                    if (!dir.exists()) return "Directory does not exist"
                    if (!dir.isDirectory) return "Path is not a directory"
                    dir.listFiles()?.joinToString("\n") { it.name } ?: "Empty directory"
                } catch (e: Exception) {
                    "Error listing files: ${e.message}"
                }
            }
        }
    }
    
    // Edit instructions for coordinate-based editing
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
    
    // File interaction analysis
    data class FileInteraction(
        val targetFile: String,
        val interactingFiles: Indexed<String>,
        val interactionCount: Int,
        val advice: EditAdvice
    )
    
    enum class EditAdvice {
        TODO,           // 0 interactions - file is isolated
        DCE,            // 0 interactions - dead code elimination candidate
        INLINE,         // 1 interaction - consider inlining
        PROPAGATE,      // 2+ interactions - changes must propagate
        UNSAFE          // High interaction count - requires careful analysis
    }
    
    private var state: AgentState = AgentState.AwaitingInstruction
    private val availableTools = arrayOf(
        Tool.GetCurrentTime,
        Tool.ReadFile(),
        Tool.WriteFile(),
        Tool.ListFiles()
    ).toIdx()
    
    // Get next API key in rotation
    private fun getNextApiKey(): String {
        val key = apiKeys.b(currentKeyIndex)
        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.a
        return key
    }
    
    // Core chat completion with retry and key rotation
    suspend fun chatCompletion(
        messages: Indexed<ChatMessage>,
        temperature: Double = 0.6,
        maxTokens: Int = 4096,
        topP: Double = 0.95,
        withTools: Boolean = false,
        maxRetries: Int = 3
    ): ChatCompletionResponse? = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        val totalKeys = apiKeys.a
        
        repeat(maxRetries * totalKeys) { totalAttempt ->
            val attempt = totalAttempt % maxRetries
            val apiKey = getNextApiKey()
            
            if (totalAttempt > 0 && attempt == 0) {
                println("[Key rotation] Trying next API key")
            }
            
            try {
                val requestBody = ChatCompletionRequest(
                    model = model,
                    messages = messages.toList(),
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                    tools = if (withTools) buildToolsJson() else null,
                    toolChoice = if (withTools) "auto" else null
                )
                
                val response = callApi(apiKey, requestBody)
                
                when (response.statusCode) {
                    200 -> return@withContext json.decodeFromString<ChatCompletionResponse>(response.body)
                    429 -> {
                        println("[Rate limit] Key exhausted, rotating...")
                        if ((totalAttempt / maxRetries) >= totalKeys - 1) {
                            println("[Rate limit] All keys exhausted. Waiting 30 seconds...")
                            delay(30000L)
                        }
                    }
                    401 -> {
                        println("[Error] Invalid API key. Trying next...")
                    }
                    else -> {
                        println("[Error] API returned ${response.statusCode}: ${response.body}")
                        delay(2000L * (attempt + 1))
                    }
                }
            } catch (e: Exception) {
                lastError = e
                println("[Error] Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                delay(1000L * (attempt + 1))
            }
        }
        
        println("[Fatal] All retry attempts failed. Last error: ${lastError?.message}")
        null
    }
    
    // Process user instruction
    suspend fun processInstruction(instruction: String): String {
        return when {
            instruction.startsWith("ANALYZE:") -> processAnalyzeCommand(instruction)
            instruction.contains("EDIT-FILE:") || instruction.contains("INSERT:") || 
            instruction.contains("REPLACE:") || instruction.contains("DELETE:") -> processEditInstruction(instruction)
            else -> processAIQuery(instruction)
        }
    }
    
    // Process AI query
    private suspend fun processAIQuery(query: String): String {
        state = AgentState.CallingAI
        
        val messages = arrayOf(
            ChatMessage("system", "You are a helpful AI assistant with access to tools for file operations and time checking."),
            ChatMessage("user", query)
        ).toIdx()
        
        val response = chatCompletion(messages, withTools = true)
        
        return if (response != null) {
            state = AgentState.ReportingSuccess
            response.choices.firstOrNull()?.message?.content ?: "No response received"
        } else {
            state = AgentState.Error("Failed to get AI response")
            "Error: Failed to get response from NVIDIA AI"
        }
    }
    
    // Process edit instruction
    private suspend fun processEditInstruction(instructionText: String): String {
        state = AgentState.ParsingInstruction
        
        val instruction = parseEditInstruction(instructionText)
        if (instruction == null) {
            state = AgentState.Error("Failed to parse instruction")
            return "Error: Failed to parse edit instruction"
        }
        
        state = AgentState.ApplyingModification
        
        return try {
            val result = applyEdit(instruction)
            state = AgentState.ReportingSuccess
            result
        } catch (e: Exception) {
            state = AgentState.Error(e.message ?: "Unknown error")
            "Error: ${e.message}"
        }
    }
    
    // Process analyze command
    private suspend fun processAnalyzeCommand(instruction: String): String {
        val filePath = instruction.substringAfter("ANALYZE:").trim()
        state = AgentState.ParsingInstruction
        
        val analysis = analyzeFileInteractions(filePath)
        state = AgentState.ReportingSuccess
        
        return buildString {
            appendLine("=== File Interaction Analysis ===")
            appendLine("File: $filePath")
            appendLine("Interaction Count: ${analysis.interactionCount}")
            appendLine("Advice: ${analysis.advice}")
            
            if (analysis.interactionCount > 0) {
                appendLine("\nDependent Files:")
                for (i in 0 until analysis.interactingFiles.a) {
                    appendLine("  - ${analysis.interactingFiles.b(i)}")
                }
            }
            
            appendLine("\nRecommendations:")
            when (analysis.advice) {
                EditAdvice.TODO -> {
                    appendLine("  ✓ File is isolated - safe for experimental changes")
                    appendLine("  ✓ Good candidate for adding TODOs or prototyping")
                }
                EditAdvice.DCE -> {
                    appendLine("  ? No other files depend on this")
                    appendLine("  ? Check if this is dead code that can be removed")
                }
                EditAdvice.INLINE -> {
                    appendLine("  → Single dependency detected")
                    appendLine("  → Consider inlining this code into its only consumer")
                }
                EditAdvice.PROPAGATE -> {
                    appendLine("  ⚠️  Multiple files depend on this")
                    appendLine("  ⚠️  Changes will need to be propagated carefully")
                }
                EditAdvice.UNSAFE -> {
                    appendLine("  ⛔ High coupling detected!")
                    appendLine("  ⛔ Refactoring may be needed before safe editing")
                }
            }
        }
    }
    
    // Helper functions would go here...
    // (parseEditInstruction, applyEdit, analyzeFileInteractions, callApi, buildToolsJson, etc.)
    
    companion object {
        val defaultApiKeys = listOf(
            System.getenv("NVIDIA_API_KEY"),
            System.getenv("NVIDIA_API_KEY_2"),
            System.getenv("NVIDIA_API_KEY_3"),
            "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",
            "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"
        ).filterNotNull().filter { it.isNotBlank() }
    }
}

// Data classes for API communication
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.6,
    @SerialName("top_p") val topP: Double = 0.95,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    @SerialName("frequency_penalty") val frequencyPenalty: Double = 0.0,
    @SerialName("presence_penalty") val presencePenalty: Double = 0.0,
    val stream: Boolean = false,
    val tools: JsonArray? = null,
    @SerialName("tool_choice") val toolChoice: String? = null
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

// Simple HTTP response holder
data class HttpResponse(
    val statusCode: Int,
    val body: String
)