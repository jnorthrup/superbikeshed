package nexus.mcp

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import nexus.ai.*
import nexus.scanner.EnvironmentScanner
import nexus.tools.ToolOrchestrator
import nexus.telemetry.UnifiedTelemetrySystem
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Nexus MCP Server - Standalone Model Context Protocol Server
 * 
 * Provides nexus capabilities via MCP:
 * - AI task execution (with Nemotron thinking/non-thinking)
 * - Environment scanning
 * - Tool orchestration
 * - Telemetry collection
 */
class NexusMCPServer(
    private val port: Int = 8765,
    private val workingDir: File = File(".")
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val activeConnections = ConcurrentHashMap<String, ClientConnection>()
    
    // Nexus components
    private val scanner = EnvironmentScanner(workingDir)
    private val toolOrchestrator = ToolOrchestrator(workingDir)
    private val telemetrySystem = UnifiedTelemetrySystem()
    private val aiProviders = mapOf(
        "litellm" to LiteLLMAIProvider(),
        "nemotron" to NemotronAIProvider(),
        "nemo" to NemotronAIProvider()
    )
    
    data class ClientConnection(
        val id: String,
        val socket: Socket,
        val reader: BufferedReader,
        val writer: PrintWriter
    )
    
    suspend fun start() = coroutineScope {
        if (isRunning) return@coroutineScope
        
        isRunning = true
        println("Starting Nexus MCP Server on port $port...")
        
        try {
            serverSocket = ServerSocket(port)
            println("Nexus MCP Server listening on port $port")
            
            // Start telemetry system
            launch {
                telemetrySystem.start()
            }
            
            // Accept connections
            while (isRunning) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(socket)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        println("Error accepting connection: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Server error: ${e.message}")
        }
    }
    
    private suspend fun handleClient(socket: Socket) {
        val clientId = "client-${System.currentTimeMillis()}"
        
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            
            val connection = ClientConnection(clientId, socket, reader, writer)
            activeConnections[clientId] = connection
            
            println("Client connected: $clientId from ${socket.remoteSocketAddress}")
            
            // Process messages
            while (isRunning && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                
                try {
                    val request = json.decodeFromString<MCPRequest>(line)
                    val response = handleRequest(request)
                    writer.println(json.encodeToString(response))
                } catch (e: Exception) {
                    val error = MCPResponse(
                        jsonrpc = "2.0",
                        error = MCPError(-32700, "Parse error: ${e.message}")
                    )
                    writer.println(json.encodeToString(error))
                }
            }
        } catch (e: Exception) {
            println("Client $clientId error: ${e.message}")
        } finally {
            activeConnections.remove(clientId)
            socket.close()
            println("Client disconnected: $clientId")
        }
    }
    
    private suspend fun handleRequest(request: MCPRequest): MCPResponse {
        return when (request.method) {
            "initialize" -> handleInitialize(request)
            "tools/list" -> handleToolsList(request)
            "resources/list" -> handleResourcesList(request)
            "nexus/executeTask" -> handleExecuteTask(request)
            "nexus/scan" -> handleScan(request)
            "nexus/runTool" -> handleRunTool(request)
            "nexus/telemetry" -> handleTelemetry(request)
            "nexus/status" -> handleStatus(request)
            else -> MCPResponse(
                jsonrpc = "2.0",
                id = request.id,
                error = MCPError(-32601, "Method not found: ${request.method}")
            )
        }
    }
    
    private fun handleInitialize(request: MCPRequest): MCPResponse {
        return MCPResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") {
                    putJsonObject("tools") {
                        put("listChanged", true)
                    }
                    putJsonObject("resources") {
                        put("listChanged", true)
                        put("subscribe", true)
                    }
                    putJsonObject("prompts") {
                        put("listChanged", true)
                    }
                }
                putJsonObject("serverInfo") {
                    put("name", "nexus-mcp")
                    put("version", "1.0.0")
                    put("vendor", "v2superbikeshed")
                }
            }
        )
    }
    
    private fun handleToolsList(request: MCPRequest): MCPResponse {
        val tools = buildJsonArray {
            // AI Tools
            addJsonObject {
                put("name", "nexus/executeTask")
                put("description", "Execute an AI-powered task with optional thinking mode")
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("task") {
                            put("type", "string")
                            put("description", "Task description")
                        }
                        putJsonObject("provider") {
                            put("type", "string")
                            put("enum", JsonArray(listOf(
                                JsonPrimitive("litellm"),
                                JsonPrimitive("nemotron"),
                                JsonPrimitive("nemo")
                            )))
                            put("default", "litellm")
                        }
                        putJsonObject("context") {
                            put("type", "string")
                            put("description", "Optional context")
                        }
                    }
                    putJsonArray("required") {
                        add("task")
                    }
                }
            }
            
            // Scanner Tool
            addJsonObject {
                put("name", "nexus/scan")
                put("description", "Scan environment for languages, tools, and project structure")
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("path") {
                            put("type", "string")
                            put("description", "Path to scan (default: current directory)")
                        }
                    }
                }
            }
            
            // Tool Runner
            addJsonObject {
                put("name", "nexus/runTool")
                put("description", "Execute a development tool")
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tool") {
                            put("type", "string")
                            put("description", "Tool name (e.g., gradle, maven, git)")
                        }
                        putJsonObject("args") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "string")
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("tool")
                    }
                }
            }
            
            // Telemetry Tool
            addJsonObject {
                put("name", "nexus/telemetry")
                put("description", "Get telemetry metrics and reports")
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("command") {
                            put("type", "string")
                            put("enum", JsonArray(listOf(
                                JsonPrimitive("status"),
                                JsonPrimitive("metrics"),
                                JsonPrimitive("report")
                            )))
                        }
                    }
                }
            }
        }
        
        return MCPResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = buildJsonObject {
                put("tools", tools)
            }
        )
    }
    
    private fun handleResourcesList(request: MCPRequest): MCPResponse {
        val resources = buildJsonArray {
            addJsonObject {
                put("uri", "nexus://environment")
                put("name", "Environment Info")
                put("description", "Current environment scan results")
                put("mimeType", "application/json")
            }
            addJsonObject {
                put("uri", "nexus://tools")
                put("name", "Available Tools")
                put("description", "List of discovered development tools")
                put("mimeType", "application/json")
            }
            addJsonObject {
                put("uri", "nexus://telemetry")
                put("name", "Telemetry Data")
                put("description", "Current telemetry metrics")
                put("mimeType", "application/json")
            }
        }
        
        return MCPResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = buildJsonObject {
                put("resources", resources)
            }
        )
    }
    
    private suspend fun handleExecuteTask(request: MCPRequest): MCPResponse {
        val params = request.params?.jsonObject ?: return errorResponse(request.id, "Missing parameters")
        
        val task = params["task"]?.jsonPrimitive?.content 
            ?: return errorResponse(request.id, "Missing task parameter")
        
        val provider = params["provider"]?.jsonPrimitive?.content ?: "litellm"
        val context = params["context"]?.jsonPrimitive?.content
        
        return try {
            val aiProvider = aiProviders[provider] 
                ?: return errorResponse(request.id, "Unknown AI provider: $provider")
            
            val result = aiProvider.completeTask(task, context)
            
            MCPResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = buildJsonObject {
                    put("result", result)
                    put("provider", provider)
                    put("task", task)
                }
            )
        } catch (e: Exception) {
            errorResponse(request.id, "Task execution failed: ${e.message}")
        }
    }
    
    private suspend fun handleScan(request: MCPRequest): MCPResponse {
        val params = request.params?.jsonObject
        val path = params?.get("path")?.jsonPrimitive?.content ?: "."
        
        return try {
            val scanDir = File(path)
            val tempScanner = EnvironmentScanner(scanDir)
            val info = tempScanner.scan()
            
            MCPResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = buildJsonObject {
                    putJsonArray("languages") {
                        info.languages.forEach { add(it.name) }
                    }
                    putJsonArray("buildTools") {
                        info.buildTools.forEach { add(it.name) }
                    }
                    put("projectType", info.projectType.name)
                    putJsonArray("frameworks") {
                        info.frameworks.forEach { add(it) }
                    }
                    putJsonObject("structure") {
                        put("totalFiles", info.structure.totalFiles)
                        put("totalSize", info.structure.totalSize)
                        putJsonArray("sourceRoots") {
                            info.structure.sourceRoots.forEach { add(it.path) }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            errorResponse(request.id, "Scan failed: ${e.message}")
        }
    }
    
    private suspend fun handleRunTool(request: MCPRequest): MCPResponse {
        val params = request.params?.jsonObject ?: return errorResponse(request.id, "Missing parameters")
        
        val tool = params["tool"]?.jsonPrimitive?.content 
            ?: return errorResponse(request.id, "Missing tool parameter")
        
        val args = params["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        
        return try {
            val result = toolOrchestrator.executeTool(tool, args)
            
            MCPResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = buildJsonObject {
                    put("tool", tool)
                    put("exitCode", result.exitCode)
                    put("output", result.output)
                    if (result.errorOutput.isNotEmpty()) {
                        put("error", result.errorOutput)
                    }
                }
            )
        } catch (e: Exception) {
            errorResponse(request.id, "Tool execution failed: ${e.message}")
        }
    }
    
    private suspend fun handleTelemetry(request: MCPRequest): MCPResponse {
        val params = request.params?.jsonObject
        val command = params?.get("command")?.jsonPrimitive?.content ?: "status"
        
        return when (command) {
            "status" -> {
                MCPResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = buildJsonObject {
                        put("status", "running") // Telemetry is always running when server is up
                        put("collectors", 3) // IntelliJ, VS Code, Eclipse
                    }
                )
            }
            "metrics" -> {
                val metrics = telemetrySystem.getMetrics()
                MCPResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = buildJsonObject {
                        metrics.forEach { (key, value) ->
                            putJsonObject(key) {
                                put("value", value.value)
                                put("unit", value.unit)
                                put("timestamp", value.timestamp)
                            }
                        }
                    }
                )
            }
            "report" -> {
                val report = telemetrySystem.generateReport()
                MCPResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = buildJsonObject {
                        put("totalEvents", report.totalEvents)
                        put("summary", report.summary)
                        putJsonObject("eventsByIDE") {
                            report.eventsByIDE.forEach { (ide, count) ->
                                put(ide, count)
                            }
                        }
                    }
                )
            }
            else -> errorResponse(request.id, "Unknown telemetry command: $command")
        }
    }
    
    private fun handleStatus(request: MCPRequest): MCPResponse {
        return MCPResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = buildJsonObject {
                put("server", "nexus-mcp")
                put("version", "1.0.0")
                put("status", "running")
                put("connections", activeConnections.size)
                put("uptime", System.currentTimeMillis())
                putJsonObject("capabilities") {
                    put("ai", true)
                    put("scanning", true)
                    put("tools", true)
                    put("telemetry", true)
                }
            }
        )
    }
    
    private fun errorResponse(id: JsonElement?, message: String): MCPResponse {
        return MCPResponse(
            jsonrpc = "2.0",
            id = id,
            error = MCPError(-32603, message)
        )
    }
    
    fun stop() {
        isRunning = false
        
        // Close all connections
        activeConnections.values.forEach { connection ->
            try {
                connection.socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        activeConnections.clear()
        
        // Stop server
        serverSocket?.close()
        serverSocket = null
        
        // Stop telemetry
        runBlocking {
            telemetrySystem.stop()
        }
        
        println("Nexus MCP Server stopped")
    }
}

// MCP Protocol Types
@kotlinx.serialization.Serializable
data class MCPRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null
)

@kotlinx.serialization.Serializable
data class MCPResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: MCPError? = null
)

@kotlinx.serialization.Serializable
data class MCPError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)