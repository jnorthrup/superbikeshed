package com.v2superbikeshed.nexus.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.v2superbikeshed.nexus.rpc.*
import com.v2superbikeshed.nexus.service.IntelliJAccessService
import com.v2superbikeshed.nexus.service.NexusApiServer

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.InputStreamReader
import java.io.OutputStreamWriter

internal val logger = KotlinLogging.logger {}

/**
 * MCP Server Launcher that starts the unified MCP/RequestFactory server
 * and handles stdio communication for MCP protocol.
 */
@Service(Service.Level.APP)
class McpServerLauncher : StartupActivity {
    
    companion object {
        internal val LOG = Logger.getInstance(McpServerLauncher::class.java)
        
        @JvmStatic
        fun getInstance(): McpServerLauncher {
            return ApplicationManager.getApplication().getService(McpServerLauncher::class.java)
        }
    }
    
    internal var mcpStdioServer: McpStdioServer? = null
    internal var httpServer: NexusApiServer? = null
    internal var isRunning = false
    
    override fun runActivity(project: Project) {
        // Auto-start MCP server when IntelliJ starts
        ApplicationManager.getApplication().invokeLater {
            startMcpServer()
        }
    }
    
    /**
     * Start the MCP server with both stdio and HTTP interfaces
     */
    fun startMcpServer() {
        if (isRunning) {
            LOG.info("MCP server already running")
            return
        }
        
        try {
            // Start HTTP server with MCP bridge
            httpServer = NexusApiServer.getInstance().apply {
                start(63343)
            }
            
            // Start stdio server for native MCP protocol
            mcpStdioServer = McpStdioServer().apply {
                start()
            }
            
            isRunning = true
            LOG.info("MCP server started successfully")
            
            // Log server info
            LOG.info("HTTP/WebSocket endpoint: http://localhost:63343/api/nexus/mcp/")
            LOG.info("stdio interface: Active for MCP protocol")
            
        } catch (e: Exception) {
            LOG.error("Failed to start MCP server", e)
            stopMcpServer()
        }
    }
    
    /**
     * Stop the MCP server
     */
    fun stopMcpServer() {
        mcpStdioServer?.stop()
        httpServer?.stop()
        isRunning = false
        LOG.info("MCP server stopped")
    }
    
    fun isRunning(): Boolean = isRunning
}

/**
 * MCP stdio server that handles JSON-RPC over stdin/stdout
 */
class McpStdioServer {
    
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
    internal var isRunning = false
    internal var stdioJob: Job? = null
    
    fun start() {
        isRunning = true
        
        // Start coroutine for stdio handling
        stdioJob = GlobalScope.launch {
            try {
                handleStdio()
            } catch (e: Exception) {
                logger.error { "stdio handler error: ${e.message}" }
            }
        }
        
        // Send initialize response
        sendInitializeResponse()
    }
    
    fun stop() {
        isRunning = false
        stdioJob?.cancel()
    }
    
    internal suspend fun handleStdio() {
        val reader = InputStreamReader(System.`in`)
        val writer = OutputStreamWriter(System.out)
        val buffer = StringBuilder()
        
        while (isRunning) {
            try {
                // Read JSON-RPC messages from stdin
                if (reader.ready()) {
                    val char = reader.read()
                    if (char == -1) break
                    
                    buffer.append(char.toChar())
                    
                    // Try to parse complete JSON messages
                    val content = buffer.toString()
                    if (content.contains("}") && isCompleteJson(content)) {
                        handleStdioMessage(content.trim())
                        buffer.clear()
                    }
                }
                
                delay(10) // Small delay to prevent busy waiting
                
            } catch (e: Exception) {
                logger.error { "Error reading stdio: ${e.message}" }
            }
        }
    }
    
    internal fun isCompleteJson(content: String): Boolean {
        return try {
            json.parseToJsonElement(content)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    internal suspend fun handleStdioMessage(message: String) {
        try {
            val request = json.decodeFromString<McpRequest>(message)
            logger.info { "Received MCP request: ${request.method}" }
            
            val response = when (request.method) {
                "initialize" -> handleInitialize(request)
                "initialized" -> handleInitialized(request)
                "tools/list" -> handleToolsList(request)
                "resources/list" -> handleResourcesList(request)
                "prompts/list" -> handlePromptsList(request)
                else -> {
                    // Forward to appropriate node
                    forwardToNode(request)
                }
            }
            
            sendResponse(response)
            
        } catch (e: Exception) {
            logger.error { "Error handling message: ${e.message}" }
            sendError(JsonPrimitive("-1"), -32603, "Internal error: ${e.message}")
        }
    }
    
    internal fun handleInitialize(request: McpRequest): McpResponse {
        return McpResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonObject(mapOf(
                "protocolVersion" to JsonPrimitive("2024-11-05"),
                "capabilities" to JsonObject(mapOf(
                    "tools" to JsonObject(mapOf(
                        "listChanged" to JsonPrimitive(true)
                    )),
                    "resources" to JsonObject(mapOf(
                        "subscribe" to JsonPrimitive(true),
                        "listChanged" to JsonPrimitive(true)
                    )),
                    "prompts" to JsonObject(mapOf(
                        "listChanged" to JsonPrimitive(true)
                    )),
                    "logging" to JsonObject(emptyMap())
                )),
                "serverInfo" to JsonObject(mapOf(
                    "name" to JsonPrimitive("v2superbikeshed-mcp"),
                    "version" to JsonPrimitive("1.0.0")
                ))
            ))
        )
    }
    
    internal fun handleInitialized(request: McpRequest): McpResponse {
        // Send notifications about available tools
        sendNotification("notifications/tools/list_changed", JsonObject(emptyMap()))
        
        return McpResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonObject(emptyMap())
        )
    }
    
    internal fun handleToolsList(request: McpRequest): McpResponse {
        val tools = JsonArray(listOf(
            // PSI tools
            createTool(
                name = "find_symbols",
                description = "Find symbols in the project by pattern",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "pattern" to mapOf(
                            "type" to "string",
                            "description" to "Symbol name pattern (supports wildcards)"
                        ),
                        "includeLibraries" to mapOf(
                            "type" to "boolean",
                            "description" to "Include library symbols",
                            "default" to false
                        )
                    ),
                    "required" to listOf("pattern")
                )
            ),
            createTool(
                name = "rename_symbol",
                description = "Rename a symbol across the entire project",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "oldName" to mapOf(
                            "type" to "string",
                            "description" to "Current name of the symbol"
                        ),
                        "newName" to mapOf(
                            "type" to "string",
                            "description" to "New name for the symbol"
                        )
                    ),
                    "required" to listOf("oldName", "newName")
                )
            ),
            createTool(
                name = "get_file_ast",
                description = "Get the AST structure of a file",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "filePath" to mapOf(
                            "type" to "string",
                            "description" to "Path to the file"
                        )
                    ),
                    "required" to listOf("filePath")
                )
            ),
            
            // Analysis tools
            createTool(
                name = "analyze_code",
                description = "Run code inspections on a file or project",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "filePath" to mapOf(
                            "type" to "string",
                            "description" to "Optional file path (analyzes whole project if not provided)"
                        )
                    )
                )
            ),
            createTool(
                name = "find_compilation_errors",
                description = "Find all compilation errors in the project",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf()
                )
            ),
            
            // Refactoring tools
            createTool(
                name = "move_class",
                description = "Move a class to a different package",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "className" to mapOf(
                            "type" to "string",
                            "description" to "Fully qualified class name"
                        ),
                        "targetPackage" to mapOf(
                            "type" to "string",
                            "description" to "Target package name"
                        )
                    ),
                    "required" to listOf("className", "targetPackage")
                )
            )
        ))
        
        return McpResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonObject(mapOf("tools" to tools))
        )
    }
    
    internal fun handleResourcesList(request: McpRequest): McpResponse {
        val resources = JsonArray(listOf(
            JsonObject(mapOf(
                "uri" to JsonPrimitive("project://v2superbikeshed"),
                "name" to JsonPrimitive("v2superbikeshed Project"),
                "description" to JsonPrimitive("Access to the v2superbikeshed project structure"),
                "mimeType" to JsonPrimitive("application/json")
            ))
        ))
        
        return McpResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonObject(mapOf("resources" to resources))
        )
    }
    
    internal fun handlePromptsList(request: McpRequest): McpResponse {
        val prompts = JsonArray(listOf(
            JsonObject(mapOf(
                "name" to JsonPrimitive("refactor_series_to_indexed"),
                "description" to JsonPrimitive("Refactor Series type aliases to Indexed"),
                "arguments" to JsonArray(listOf(
                    JsonObject(mapOf(
                        "name" to JsonPrimitive("scope"),
                        "description" to JsonPrimitive("Scope of refactoring (file, module, project)"),
                        "required" to JsonPrimitive(false)
                    ))
                ))
            ))
        ))
        
        return McpResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonObject(mapOf("prompts" to prompts))
        )
    }
    
    internal suspend fun forwardToNode(request: McpRequest): McpResponse {
        // Extract node and method from the request
        val method = request.method
        val nodeMethod = when {
            method.startsWith("tools/call") -> {
                // Extract tool name from params
                val toolName = request.params?.get("name")?.jsonPrimitive?.content ?: ""
                mapNodeMethod(toolName)
            }
            method.contains(".") -> method
            else -> "psi-node.$method" // Default to psi-node
        }
        
        // Forward to HTTP endpoint
        return try {
            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(json)
                }
                // Prevent automatic downloads and external requests
                engine {
                    pipelining = false
                }
            }
            
            val httpResponse = httpClient.post("http://localhost:63343/api/nexus/mcp/rpc") {
                contentType(ContentType.Application.Json)
                setBody(request.copy(method = nodeMethod))
            }
            
            httpClient.close()
            
            json.decodeFromString<McpResponse>(httpResponse.bodyAsText())
            
        } catch (e: Exception) {
            McpResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = McpError(-32603, "Failed to forward request: ${e.message}")
            )
        }
    }
    
    internal fun mapNodeMethod(toolName: String): String {
        return when (toolName) {
            "find_symbols" -> "psi-node.find_symbols"
            "rename_symbol" -> "psi-node.rename_symbols"
            "get_file_ast" -> "psi-node.get_ast"
            "analyze_code" -> "analysis-node.analyze_code"
            "find_compilation_errors" -> "analysis-node.find_issues"
            "move_class" -> "refactoring-node.move"
            else -> "psi-node.$toolName"
        }
    }
    
    internal fun createTool(
        name: String,
        description: String,
        inputSchema: Map<String, Any>
    ): JsonObject {
        return JsonObject(mapOf(
            "name" to JsonPrimitive(name),
            "description" to JsonPrimitive(description),
            "inputSchema" to json.encodeToJsonElement(inputSchema)
        ))
    }
    
    internal fun sendInitializeResponse() {
        // Send server info on startup
        val serverInfo = JsonObject(mapOf(
            "jsonrpc" to JsonPrimitive("2.0"),
            "method" to JsonPrimitive("server.ready"),
            "params" to JsonObject(mapOf(
                "name" to JsonPrimitive("v2superbikeshed-mcp"),
                "version" to JsonPrimitive("1.0.0")
            ))
        ))
        
        println(json.encodeToString(serverInfo))
        System.out.flush()
    }
    
    internal fun sendResponse(response: McpResponse) {
        val jsonString = json.encodeToString(response)
        println(jsonString)
        System.out.flush()
        logger.info { "Sent response: $jsonString" }
    }
    
    internal fun sendNotification(method: String, params: JsonElement) {
        val notification = JsonObject(mapOf(
            "jsonrpc" to JsonPrimitive("2.0"),
            "method" to JsonPrimitive(method),
            "params" to params
        ))
        
        println(json.encodeToString(notification))
        System.out.flush()
    }
    
    internal fun sendError(id: JsonElement, code: Int, message: String) {
        val error = McpResponse(
            jsonrpc = "2.0",
            id = id,
            error = McpError(code, message)
        )
        sendResponse(error)
    }
}

/**
 * MCP client for testing the server
 */
class McpTestClient {
    
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    suspend fun testMcpServer() {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(this@McpTestClient.json)
            }
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            // Prevent automatic downloads and external requests
            engine {
                pipelining = false
            }
        }
        
        try {
            // Test REST endpoint
            logger.info { "Testing REST endpoint..." }
            val restResponse = client.post("http://localhost:63343/api/nexus/mcp/rpc") {
                contentType(ContentType.Application.Json)
                setBody(McpRequest(
                    id = JsonPrimitive("test-1"),
                    method = "psi-node.find_symbols",
                    params = JsonObject(mapOf(
                        "pattern" to JsonPrimitive("*Series")
                    ))
                ))
            }
            
            logger.info { "REST Response: ${restResponse.bodyAsText()}" }
            
            // Test WebSocket endpoint
            logger.info { "Testing WebSocket endpoint..." }
            client.webSocket("ws://localhost:63343/api/nexus/mcp/ws") {
                // Send request
                send(Frame.Text(json.encodeToString(McpRequest(
                    id = JsonPrimitive("test-2"),
                    method = "analysis-node.analyze_code",
                    params = JsonObject(mapOf(
                        "filePath" to JsonPrimitive("/path/to/file.kt")
                    ))
                ))))
                
                // Receive response
                val response = incoming.receive() as Frame.Text
                logger.info { "WebSocket Response: ${response.readText()}" }
                
                close()
            }
            
        } catch (e: Exception) {
            logger.error { "Test failed: ${e.message}" }
        } finally {
            client.close()
        }
    }
}