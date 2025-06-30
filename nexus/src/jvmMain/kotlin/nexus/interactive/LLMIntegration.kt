package nexus.interactive

import borg.trikeshed.lib.Either
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series as Indexed
import borg.trikeshed.lib.j
import borg.trikeshed.lib.toSeries
import k2script.ai.llm.LiteLLMClient
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration

/**
 * LLM Integration for Nexus Interactive with enhanced error handling
 */
object LLMIntegration {
    
    // Error types for better error handling
    sealed interface LLMError {
        data class NetworkError(val message: String, val cause: Throwable? = null) : LLMError
        data class ParseError(val message: String, val response: String) : LLMError
        data class ToolExecutionError(val toolName: String, val error: String) : LLMError
        data class TimeoutError(val duration: Duration) : LLMError
        data class ModelNotAvailable(val model: String) : LLMError
        data class RateLimitError(val retryAfter: Duration? = null) : LLMError
    }
    
    // Tool call representation
    @Serializable
    data class ToolCall(
        val name: String,
        val parameters: JsonObject
    )
    
    // LLM response types
    sealed interface LLMResponse {
        data class Text(val content: String) : LLMResponse
        data class ToolUse(val toolCall: ToolCall, val reasoning: String? = null) : LLMResponse
        data class Mixed(val text: String, val toolCalls: Indexed<ToolCall>) : LLMResponse
    }
    
    // Enhanced message formatting
    fun formatMessages(
        conversationHistory: Indexed<NexusInteractive.ConversationEntry>,
        tools: NexusInteractive.ToolRegistry,
        systemPrompt: String? = null
    ): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()
        
        // System prompt with tool descriptions
        val toolPrompt = buildString {
            appendLine(systemPrompt ?: "You are Nexus, an AI assistant with access to tools.")
            appendLine("\nAvailable tools:")
            tools.toIndexed().forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description}")
                if (tool.parameters.size > 0) {
                    appendLine("  Parameters:")
                    tool.parameters.forEach { param ->
                        val req = if (param.required) "required" else "optional"
                        appendLine("    - ${param.name} (${param.type}, $req): ${param.description}")
                    }
                }
            }
            appendLine("\nTo use a tool, respond with:")
            appendLine("TOOL_CALL: {\"name\": \"tool_name\", \"parameters\": {\"param\": \"value\"}}")
            appendLine("\nYou can use multiple tools by including multiple TOOL_CALL lines.")
        }
        
        messages.add(mapOf("role" to "system", "content" to toolPrompt))
        
        // Add conversation history
        conversationHistory.forEach { entry ->
            val role = when (entry.role) {
                NexusInteractive.ConversationRole.USER -> "user"
                NexusInteractive.ConversationRole.ASSISTANT -> "assistant"
                NexusInteractive.ConversationRole.SYSTEM -> "system"
                NexusInteractive.ConversationRole.TOOL -> "assistant" // Tool results as assistant
            }
            messages.add(mapOf("role" to role, "content" to entry.content))
        }
        
        return messages
    }
    
    // Parse LLM response for tool calls
    fun parseResponse(content: String): Either<LLMError, LLMResponse> {
        val toolCallPattern = Regex("TOOL_CALL:\\s*(.+)")
        val toolCalls = mutableListOf<ToolCall>()
        var remainingText = content
        
        toolCallPattern.findAll(content).forEach { match ->
            val jsonStr = match.groupValues[1].trim()
            try {
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                val name = json["name"]?.jsonPrimitive?.content
                    ?: return Either.left(LLMError.ParseError("Missing tool name", jsonStr))
                val params = json["parameters"]?.jsonObject
                    ?: JsonObject(emptyMap())
                
                toolCalls.add(ToolCall(name, params))
                remainingText = remainingText.replace(match.value, "")
            } catch (e: Exception) {
                return Either.left(LLMError.ParseError("Invalid tool call JSON: ${e.message}", jsonStr))
            }
        }
        
        return when {
            toolCalls.isEmpty() -> Either.right(LLMResponse.Text(content.trim()))
            remainingText.isBlank() -> {
                if (toolCalls.size == 1) {
                    Either.right(LLMResponse.ToolUse(toolCalls.first()))
                } else {
                    Either.right(LLMResponse.Mixed("", toolCalls.toTypedArray().toSeries()))
                }
            }
            else -> Either.right(LLMResponse.Mixed(
                remainingText.trim(),
                toolCalls.toTypedArray().toSeries()
            ))
        }
    }
    
    // Execute tool with proper error handling
    suspend fun executeTool(
        tool: NexusInteractive.Tool,
        parameters: JsonObject,
        context: NexusInteractive.ToolContext
    ): Either<LLMError, String> = withContext(Dispatchers.IO) {
        try {
            // Convert JSON parameters to expected types
            val params = mutableMapOf<String, Any>()
            
            tool.parameters.forEach { param ->
                val jsonValue = parameters[param.name]
                val value = when {
                    jsonValue == null && !param.required -> param.defaultValue
                    jsonValue == null && param.required -> 
                        return@withContext Either.left(
                            LLMError.ToolExecutionError(tool.name, "Missing required parameter: ${param.name}")
                        )
                    else -> parseJsonValue(jsonValue!!, param.type)
                }
                
                if (value != null) {
                    params[param.name] = value
                }
            }
            
            // Execute tool
            val toolContext = context.copy(parameters = params)
            when (val result = tool.execute(toolContext)) {
                is NexusInteractive.ToolResult.Success -> Either.right(result.output)
                is NexusInteractive.ToolResult.Error -> 
                    Either.left(LLMError.ToolExecutionError(tool.name, result.message))
                is NexusInteractive.ToolResult.Deferred -> {
                    try {
                        val output = result.job.await()
                        Either.right(output)
                    } catch (e: Exception) {
                        Either.left(LLMError.ToolExecutionError(tool.name, e.message ?: "Unknown error"))
                    }
                }
            }
        } catch (e: Exception) {
            Either.left(LLMError.ToolExecutionError(tool.name, e.message ?: "Unknown error"))
        }
    }
    
    private fun parseJsonValue(json: JsonElement, type: NexusInteractive.ParameterType): Any? {
        return when (type) {
            NexusInteractive.ParameterType.STRING -> json.jsonPrimitive.content
            NexusInteractive.ParameterType.NUMBER -> json.jsonPrimitive.double
            NexusInteractive.ParameterType.BOOLEAN -> json.jsonPrimitive.boolean
            NexusInteractive.ParameterType.FILE_PATH -> json.jsonPrimitive.content
            NexusInteractive.ParameterType.JSON -> json
        }
    }
    
    // Complete LLM interaction with retries and error handling
    suspend fun completeWithTools(
        messages: List<Map<String, String>>,
        model: String,
        tools: NexusInteractive.ToolRegistry,
        context: NexusInteractive.ToolContext,
        timeout: Duration,
        maxRetries: Int = 3
    ): Either<LLMError, String> = withContext(Dispatchers.IO) {
        
        var attempt = 0
        var lastError: LLMError? = null
        
        while (attempt < maxRetries) {
            try {
                val future = withTimeout(timeout) {
                    LiteLLMClient.complete(
                        model = model,
                        messages = messages,
                        temperature = 0.7,
                        maxTokens = 2000
                    )
                }
                
                val response = future.get()
                
                if (response.status != "success") {
                    lastError = LLMError.NetworkError(
                        response.error_message ?: "Unknown error",
                        null
                    )
                    attempt++
                    continue
                }
                
                val content = response.content ?: return@withContext Either.left(
                    LLMError.ParseError("Empty response", "")
                )
                
                // Parse response for tool calls
                return@withContext when (val parsed = parseResponse(content)) {
                    is Either.Left -> parsed
                    is Either.Right -> {
                        when (val llmResponse = parsed.value) {
                            is LLMResponse.Text -> Either.right(llmResponse.content)
                            is LLMResponse.ToolUse -> {
                                executeToolAndGetFinalResponse(
                                    llmResponse.toolCall,
                                    tools,
                                    context,
                                    messages,
                                    model,
                                    timeout
                                )
                            }
                            is LLMResponse.Mixed -> {
                                // Execute all tools and combine results
                                val toolResults = mutableListOf<String>()
                                
                                llmResponse.toolCalls.forEach { toolCall ->
                                    val tool = tools.findByName(toolCall.name)
                                    if (tool != null) {
                                        when (val result = executeTool(tool, toolCall.parameters, context)) {
                                            is Either.Right -> toolResults.add(
                                                "${toolCall.name}: ${result.value}"
                                            )
                                            is Either.Left -> toolResults.add(
                                                "${toolCall.name}: Error - ${result.value}"
                                            )
                                        }
                                    } else {
                                        toolResults.add("${toolCall.name}: Tool not found")
                                    }
                                }
                                
                                val combinedResult = buildString {
                                    if (llmResponse.text.isNotBlank()) {
                                        appendLine(llmResponse.text)
                                        appendLine()
                                    }
                                    appendLine("Tool Results:")
                                    toolResults.forEach { appendLine(it) }
                                }
                                
                                Either.right(combinedResult)
                            }
                        }
                    }
                }
                
            } catch (e: TimeoutCancellationException) {
                return@withContext Either.left(LLMError.TimeoutError(timeout))
            } catch (e: Exception) {
                lastError = LLMError.NetworkError(e.message ?: "Unknown error", e)
                attempt++
                
                // Check for rate limit errors
                if (e.message?.contains("rate limit", ignoreCase = true) == true) {
                    return@withContext Either.left(LLMError.RateLimitError())
                }
            }
        }
        
        Either.left(lastError ?: LLMError.NetworkError("Max retries exceeded", null))
    }
    
    private suspend fun executeToolAndGetFinalResponse(
        toolCall: ToolCall,
        tools: NexusInteractive.ToolRegistry,
        context: NexusInteractive.ToolContext,
        originalMessages: List<Map<String, String>>,
        model: String,
        timeout: Duration
    ): Either<LLMError, String> {
        val tool = tools.findByName(toolCall.name)
            ?: return Either.left(LLMError.ToolExecutionError(toolCall.name, "Tool not found"))
        
        // Execute the tool
        val toolResult = executeTool(tool, toolCall.parameters, context)
        
        val toolOutput = when (toolResult) {
            is Either.Right -> toolResult.value
            is Either.Left -> return toolResult
        }
        
        // Get final response incorporating tool result
        val updatedMessages = originalMessages + listOf(
            mapOf("role" to "assistant", "content" to "I'll use the ${toolCall.name} tool."),
            mapOf("role" to "user", "content" to "Tool result: $toolOutput\n\nPlease provide a natural response incorporating this result.")
        )
        
        val future = LiteLLMClient.complete(
            model = model,
            messages = updatedMessages,
            temperature = 0.7,
            maxTokens = 1000
        )
        
        val finalResponse = future.get()
        
        return if (finalResponse.status == "success") {
            Either.right(finalResponse.content ?: toolOutput)
        } else {
            Either.left(LLMError.NetworkError(finalResponse.error_message ?: "Unknown error"))
        }
    }
}