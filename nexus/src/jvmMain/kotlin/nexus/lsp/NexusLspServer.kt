package nexus.lsp

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import nexus.scanner.EnvironmentScanner
import nexus.tools.ToolOrchestrator

/**
 * LSP (Language Server Protocol) implementation for Nexus
 * 
 * Provides language intelligence features to any editor supporting LSP
 */
class NexusLspServer(
    private val port: Int = 7777
) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val requestId = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    
    // LSP Message structures
    @Serializable
    data class JsonRpcRequest(
        val jsonrpc: String = "2.0",
        val id: String? = null,
        val method: String,
        val params: JsonObject? = null
    )
    
    @Serializable
    data class JsonRpcResponse(
        val jsonrpc: String = "2.0",
        val id: String,
        val result: JsonElement? = null,
        val error: JsonRpcError? = null
    )
    
    @Serializable
    data class JsonRpcError(
        val code: Int,
        val message: String,
        val data: JsonElement? = null
    )
    
    @Serializable
    data class InitializeParams(
        val processId: Int? = null,
        val rootUri: String? = null,
        val capabilities: ClientCapabilities? = null
    )
    
    @Serializable
    data class ClientCapabilities(
        val textDocument: TextDocumentClientCapabilities? = null,
        val workspace: WorkspaceClientCapabilities? = null
    )
    
    @Serializable
    data class TextDocumentClientCapabilities(
        val completion: CompletionClientCapabilities? = null,
        val hover: HoverClientCapabilities? = null,
        val definition: DefinitionClientCapabilities? = null
    )
    
    @Serializable
    data class CompletionClientCapabilities(
        val dynamicRegistration: Boolean? = null,
        val completionItem: CompletionItemCapabilities? = null
    )
    
    @Serializable
    data class CompletionItemCapabilities(
        val snippetSupport: Boolean? = null
    )
    
    @Serializable
    data class HoverClientCapabilities(
        val dynamicRegistration: Boolean? = null
    )
    
    @Serializable
    data class DefinitionClientCapabilities(
        val dynamicRegistration: Boolean? = null
    )
    
    @Serializable
    data class WorkspaceClientCapabilities(
        val applyEdit: Boolean? = null,
        val workspaceEdit: WorkspaceEditCapabilities? = null
    )
    
    @Serializable
    data class WorkspaceEditCapabilities(
        val documentChanges: Boolean? = null
    )
    
    @Serializable
    data class ServerCapabilities(
        val textDocumentSync: Int = 1, // Full sync
        val completionProvider: CompletionOptions? = null,
        val hoverProvider: Boolean = true,
        val definitionProvider: Boolean = true,
        val referencesProvider: Boolean = true,
        val documentFormattingProvider: Boolean = true,
        val codeActionProvider: Boolean = true
    )
    
    @Serializable
    data class CompletionOptions(
        val triggerCharacters: List<String> = listOf(".", ":", ">")
    )
    
    suspend fun start() = coroutineScope {
        println("Starting Nexus LSP Server on port $port...")
        
        serverSocket = ServerSocket(port)
        isRunning = true
        
        launch {
            while (isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        println("Error accepting client: ${e.message}")
                    }
                }
            }
        }
        
        println("Nexus LSP Server started on port $port")
    }
    
    fun stop() {
        isRunning = false
        serverSocket?.close()
        println("Nexus LSP Server stopped")
    }
    
    private suspend fun handleClient(socket: Socket) = coroutineScope {
        println("Client connected: ${socket.remoteSocketAddress}")
        
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = PrintWriter(socket.getOutputStream(), true)
        
        try {
            while (isActive && !socket.isClosed) {
                val contentLengthLine = input.readLine() ?: break
                if (!contentLengthLine.startsWith("Content-Length:")) continue
                
                val contentLength = contentLengthLine.substringAfter(":").trim().toInt()
                input.readLine() // Empty line
                
                val buffer = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = input.read(buffer, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                
                val message = String(buffer, 0, totalRead)
                
                launch {
                    handleMessage(message, output)
                }
            }
        } catch (e: Exception) {
            println("Client error: ${e.message}")
        } finally {
            socket.close()
            println("Client disconnected")
        }
    }
    
    private suspend fun handleMessage(message: String, output: PrintWriter) {
        try {
            val request = json.decodeFromString(JsonRpcRequest.serializer(), message)
            
            val response = when (request.method) {
                "initialize" -> handleInitialize(request)
                "initialized" -> null // No response needed
                "textDocument/completion" -> handleCompletion(request)
                "textDocument/hover" -> handleHover(request)
                "textDocument/definition" -> handleDefinition(request)
                "textDocument/formatting" -> handleFormatting(request)
                "textDocument/codeAction" -> handleCodeAction(request)
                "shutdown" -> handleShutdown(request)
                "exit" -> {
                    stop()
                    null
                }
                else -> {
                    println("Unhandled method: ${request.method}")
                    null
                }
            }
            
            response?.let { sendResponse(it, output) }
        } catch (e: Exception) {
            println("Error handling message: ${e.message}")
        }
    }
    
    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val capabilities = ServerCapabilities(
            completionProvider = CompletionOptions(),
            hoverProvider = true,
            definitionProvider = true,
            referencesProvider = true,
            documentFormattingProvider = true,
            codeActionProvider = true
        )
        
        val result = buildJsonObject {
            put("capabilities", Json.encodeToJsonElement(capabilities))
            putJsonObject("serverInfo") {
                put("name", "Nexus LSP Server")
                put("version", "0.1.0")
            }
        }
        
        return JsonRpcResponse(
            id = request.id ?: "0",
            result = result
        )
    }
    
    private fun handleCompletion(request: JsonRpcRequest): JsonRpcResponse {
        // Parse text document position from request
        val params = request.params
        val uri = params?.get("textDocument")?.jsonObject?.get("uri")?.jsonPrimitive?.content
        val position = params?.get("position")?.jsonObject
        val line = position?.get("line")?.jsonPrimitive?.int ?: 0
        val character = position?.get("character")?.jsonPrimitive?.int ?: 0
        
        // Get context-aware completions
        val completions = getCompletionsForContext(uri, line, character)
        
        return JsonRpcResponse(
            id = request.id ?: "0",
            result = buildJsonObject {
                put("isIncomplete", false)
                putJsonArray("items") {
                    completions.forEach { add(it) }
                }
            }
        )
    }
    
    private fun handleHover(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params
        val uri = params?.get("textDocument")?.jsonObject?.get("uri")?.jsonPrimitive?.content
        val position = params?.get("position")?.jsonObject
        val line = position?.get("line")?.jsonPrimitive?.int ?: 0
        val character = position?.get("character")?.jsonPrimitive?.int ?: 0
        
        // Get hover information based on context
        val hoverInfo = getHoverInfo(uri, line, character)
        
        return JsonRpcResponse(
            id = request.id ?: "0",
            result = buildJsonObject {
                put("contents", hoverInfo)
            }
        )
    }
    
    private fun handleDefinition(request: JsonRpcRequest): JsonRpcResponse {
        // TODO: Implement actual go-to-definition logic
        return JsonRpcResponse(
            id = request.id ?: "0",
            result = JsonArray(emptyList())
        )
    }
    
    private fun handleFormatting(request: JsonRpcRequest): JsonRpcResponse {
        // TODO: Implement actual formatting logic
        return JsonRpcResponse(
            id = request.id ?: "0",
            result = JsonArray(emptyList())
        )
    }
    
    private fun handleCodeAction(request: JsonRpcRequest): JsonRpcResponse {
        // TODO: Implement actual code actions
        val actions = listOf(
            buildJsonObject {
                put("title", "Convert to Series<T>")
                put("kind", "refactor")
            }
        )
        
        return JsonRpcResponse(
            id = request.id ?: "0",
            result = JsonArray(actions)
        )
    }
    
    private fun handleShutdown(request: JsonRpcRequest): JsonRpcResponse {
        return JsonRpcResponse(
            id = request.id ?: "0",
            result = null
        )
    }
    
    private fun sendResponse(response: JsonRpcResponse, output: PrintWriter) {
        val responseJson = json.encodeToString(JsonRpcResponse.serializer(), response)
        val contentLength = responseJson.toByteArray().size
        
        output.println("Content-Length: $contentLength")
        output.println()
        output.print(responseJson)
        output.flush()
    }
    
    // Code intelligence implementation
    private fun getCompletionsForContext(uri: String?, line: Int, character: Int): List<JsonObject> {
        val completions = mutableListOf<JsonObject>()
        
        // TrikeShed type completions
        completions.addAll(getTrikeShedCompletions())
        
        // Nexus-specific completions
        completions.addAll(getNexusCompletions())
        
        // Context-aware completions based on file type
        if (uri?.endsWith(".kts") == true) {
            completions.addAll(getKotlinScriptCompletions())
        }
        
        return completions
    }
    
    private fun getTrikeShedCompletions(): List<JsonObject> {
        return listOf(
            buildJsonObject {
                put("label", "Series<T>")
                put("kind", 7) // Class
                put("detail", "TrikeShed ordered collection")
                put("documentation", "An immutable ordered collection following TrikeShed patterns")
                put("insertText", "Series<\$1>")
                put("insertTextFormat", 2) // Snippet
            },
            buildJsonObject {
                put("label", "Indexed<T>")
                put("kind", 7) // Class
                put("detail", "TrikeShed indexed collection")
                put("documentation", "A key-value collection with efficient lookups")
                put("insertText", "Indexed<\$1>")
                put("insertTextFormat", 2)
            },
            buildJsonObject {
                put("label", "Join<A,B>")
                put("kind", 7) // Class
                put("detail", "TrikeShed pair type")
                put("documentation", "A functional pair type for associative data")
                put("insertText", "Join<\$1, \$2>")
                put("insertTextFormat", 2)
            },
            buildJsonObject {
                put("label", "Twin<T>")
                put("kind", 7) // Class
                put("detail", "TrikeShed twin type")
                put("documentation", "Two values of the same type")
                put("insertText", "Twin<\$1>")
                put("insertTextFormat", 2)
            },
            buildJsonObject {
                put("label", "a j b")
                put("kind", 15) // Snippet
                put("detail", "Create Join<A,B>")
                put("documentation", "Constructor for Join - 'a j b'")
                put("insertText", "\${1:a} j \${2:b}")
                put("insertTextFormat", 2)
            }
        )
    }
    
    private fun getNexusCompletions(): List<JsonObject> {
        return listOf(
            buildJsonObject {
                put("label", "EnvironmentScanner")
                put("kind", 7) // Class
                put("detail", "Scan project environment")
                put("documentation", "Discovers build tools, languages, and frameworks")
                put("insertText", "EnvironmentScanner()")
                put("insertTextFormat", 2)
            },
            buildJsonObject {
                put("label", "ToolOrchestrator")
                put("kind", 7) // Class
                put("detail", "Orchestrate development tools")
                put("documentation", "Discovers and executes development tools")
                put("insertText", "ToolOrchestrator()")
                put("insertTextFormat", 2)
            },
            buildJsonObject {
                put("label", "LiteLLMProvider")
                put("kind", 7) // Class
                put("detail", "AI provider interface")
                put("documentation", "Connect to LLM models for code assistance")
                put("insertText", "LiteLLMProvider()")
                put("insertTextFormat", 2)
            }
        )
    }
    
    private fun getKotlinScriptCompletions(): List<JsonObject> {
        return listOf(
            buildJsonObject {
                put("label", "@file:DependsOn")
                put("kind", 14) // Keyword
                put("detail", "Add Maven dependency")
                put("documentation", "Include a Maven dependency in the script")
                put("insertText", "@file:DependsOn(\"\${1:group:artifact:version}\")")
                put("insertTextFormat", 2)
            },
            buildJsonObject {
                put("label", "runBlocking")
                put("kind", 3) // Function
                put("detail", "Run coroutine blocking")
                put("documentation", "Bridges blocking and suspending code")
                put("insertText", "runBlocking {\n    \$0\n}")
                put("insertTextFormat", 2)
            }
        )
    }
    
    private fun getHoverInfo(uri: String?, line: Int, character: Int): JsonObject {
        // Mock implementation - in real implementation, would parse the file content
        // and determine what symbol is under the cursor
        
        // For demo purposes, return contextual information
        val markdown = when {
            uri?.contains("trikeshed") == true -> """
                |### TrikeShed Types
                |
                |TrikeShed provides functional data structures:
                |
                |- **Series<T>**: Ordered immutable collection
                |- **Indexed<T>**: Key-value collection with efficient lookups  
                |- **Join<A,B>**: Functional pair type (constructor: `a j b`)
                |- **Twin<T>**: Two values of the same type
                |
                |Anti-patterns:
                |- List<T> → use Indexed<T>
                |- Pair → use Join<A,B>
            """.trimMargin()
            
            uri?.endsWith(".kts") == true -> """
                |### Kotlin Script
                |
                |Available script annotations:
                |- `@file:DependsOn("group:artifact:version")`
                |- `@file:Repository("https://repo.url")`
                |
                |Nexus integration available via imports.
            """.trimMargin()
            
            else -> """
                |### Nexus LSP Server
                |
                |Provides code intelligence for:
                |- TrikeShed data types
                |- Nexus development tools
                |- Kotlin script support
                |
                |Use `nexus serve` to start the server.
            """.trimMargin()
        }
        
        return buildJsonObject {
            put("kind", "markdown")
            put("value", markdown)
        }
    }
}