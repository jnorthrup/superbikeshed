package nexus.adaptation

import nexus.core.*
import borg.trikeshed.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import nexus.telemetry.K2ScriptTelemetryEvent // Added for telemetry
import kotlinx.serialization.json.Json // Added for telemetry
import kotlinx.serialization.encodeToString // Added for telemetry

// --- HTTP Client Imports (Ktor) ---
// Ensure Ktor client dependencies are in the build.gradle.kts for this module
// e.g., implementation("io.ktor:ktor-client-core:2.3.5")
// e.g., implementation("io.ktor:ktor-client-cio:2.3.5") // or another engine
// e.g., implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
// e.g., implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
import io.ktor.client.*
import nexus.core.NexusAgent // Added for NexusAgent
import nexus.core.DefaultNexusAgent // Added for DefaultNexusAgent
import borg.ipfs.IpfsPubSubService // Added for IpfsPubSubService
import borg.trikeshed.core.seriesOf // Added for seriesOf
import nexus.core.ActionNames // Added for ActionNames
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.plugins.contentnegotiation.* // Required for setBody with objects
import io.ktor.serialization.kotlinx.json.* // Required for Json { ... }

/**
 * Concrete IDE Adapter Implementations
 * 
 * Direct integrations with popular development environments.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// VS CODE ADAPTER
// ═══════════════════════════════════════════════════════════════════════════════

class VSCodeAdapter : IDEAdapter {
    override val supportsCodeEditing = true
    override var isConnected = false
    private var connection: VSCodeConnection? = null
    
    override suspend fun tryConnect(context: EnvironmentContext): Result<Connection> = runCatching {
        // Check for VS Code processes and extension API
        val vscodeProcess = findVSCodeProcess()
        if (vscodeProcess != null) {
            connection = VSCodeConnection(vscodeProcess)
            isConnected = true
            connection!!
        } else {
            throw IllegalStateException("VS Code not found or not running")
        }
    }
    
    override suspend fun execute(action: Action, context: ProjectContext): Result<Outcome> = runCatching {
        val conn = connection ?: throw IllegalStateException("Not connected to VS Code")
        
        when (action.type) {
            ActionType.CODE_EDIT -> handleCodeEdit(action, conn)
            ActionType.FILE_OPERATION -> handleFileOperation(action, conn)
            else -> Outcome("Action ${action.type} not supported by VS Code adapter")
        }
    }
    
    override suspend fun openFile(path: String): Result<Unit> = runCatching {
        connection?.sendCommand("vscode.open", path) 
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun editFile(path: String, edit: Edit): Result<Unit> = runCatching {
        connection?.sendCommand("editor.edit", "$path:${edit.startLine}:${edit.endLine}:${edit.newContent}")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun getOpenFiles(): Series<String> {
        return connection?.sendQuery("workspace.textDocuments")?.split(",")
            ?.let { Series.of(*it.toTypedArray()) } ?: Series.empty()
    }
    
    override suspend fun getCurrentSelection(): Selection? {
        val selectionData = connection?.sendQuery("editor.selection") ?: return null
        return parseSelection(selectionData)
    }
    
    private suspend fun findVSCodeProcess(): VSCodeProcess? {
        // Platform-specific process detection
        delay(50) // Simulate process check
        return VSCodeProcess("vscode-main", 12345)
    }
    
    private suspend fun handleCodeEdit(action: Action, conn: VSCodeConnection): Outcome {
        val result = conn.sendCommand("editor.action", action.data)
        return Outcome("VS Code edit completed: $result")
    }
    
    private suspend fun handleFileOperation(action: Action, conn: VSCodeConnection): Outcome {
        val result = conn.sendCommand("file.action", action.data)
        return Outcome("VS Code file operation completed: $result")
    }
    
    private fun parseSelection(data: String): Selection? {
        val parts = data.split(":")
        return if (parts.size >= 5) {
            Selection(parts[0], parts[1].toInt(), parts[2].toInt(), parts[3].toInt(), parts[4].toInt())
        } else null
    }
}

data class VSCodeProcess(val name: String, val pid: Int)

class VSCodeConnection(private val process: VSCodeProcess) : Connection {
    override val id = "vscode-${process.pid}"
    override val type = ConnectionType.IDE
    override var isActive = true
    
    suspend fun sendCommand(command: String, args: String): String {
        delay(10) // Simulate IPC call
        return "Command '$command' executed with args: $args"
    }
    
    suspend fun sendQuery(query: String): String {
        delay(10) // Simulate IPC call
        return when (query) {
            "workspace.textDocuments" -> "file1.kt,file2.kt,file3.ts"
            "editor.selection" -> "file1.kt:10:15:5:25"
            else -> "Unknown query: $query"
        }
    }
    
    override suspend fun getCapabilities(): Series<Capability> = Series.of(
        "editor" j Series.of("edit", "select", "navigate"),
        "filesystem" j Series.of("read", "write", "watch"),
        "debugger" j Series.of("breakpoints", "step", "inspect"),
        "terminal" j Series.of("execute", "shell")
    )
    
    override fun observeChanges(): Flow<Change> = flow {
        while (isActive) {
            delay(1000)
            emit(Change(ChangeType.FILE_SYSTEM, id, "file changed", System.currentTimeMillis()))
        }
    }
    
    override suspend fun close() {
        isActive = false
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// INTELLIJ IDEA ADAPTER
// ═══════════════════════════════════════════════════════════════════════════════

class IntelliJAdapter : IDEAdapter {
    override val supportsCodeEditing = true
    override var isConnected = false
    private var connection: IntelliJConnection? = null
    private val nexusAgent: NexusAgent // Added NexusAgent instance

    // Dummy IpfsPubSubService for DefaultNexusAgent instantiation
    private class DummyIpfsPubSubService : IpfsPubSubService {
        override suspend fun publish(topic: String, data: String) {
            println("DummyIpfsPubSubService: publish to $topic: $data")
        }
        override suspend fun subscribe(topic: String, handler: (String) -> Unit) {
            println("DummyIpfsPubSubService: subscribe to $topic")
        }
        override fun getSubscribedTopics(): List<String> = emptyList()
        override fun close() {}
        override val coroutineContext = kotlinx.coroutines.Dispatchers.Unconfined
    }

    init {
        // Initialize nexusAgent here.
        // In a real app, this might be injected or retrieved from a service locator.
        nexusAgent = DefaultNexusAgent(ipfsPubSubService = DummyIpfsPubSubService())
    }

    // TODO: Manage httpClient lifecycle properly (e.g., close on adapter disposal)
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // Important for schema evolution
            })
        }
    }

    // TODO: Make this URL configurable
    private val telemetryEndpointUrl = "http://localhost:8080/api/v1/telemetry/event"

    private suspend fun sendTelemetryEvent(event: K2ScriptTelemetryEvent) {
        // TODO: Secure this communication (e.g., API key in header - see SecretsManagementNotes.md)
        // TODO: Implement robust error handling (retries, circuit breaker for telemetry)
        try {
            println("Attempting to send telemetry event: $event to $telemetryEndpointUrl")
            val response: HttpResponse = httpClient.post(telemetryEndpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(event)
            }
            println("Telemetry response status: ${response.status}")
            if (response.status != HttpStatusCode.Accepted && response.status != HttpStatusCode.OK) {
                println("Error sending telemetry event: ${response.status} - ${response.bodyAsText()}")
            }
        } catch (e: Exception) {
            // Log error, but don't let telemetry failure block core functionality
            println("Failed to send telemetry event: ${e.message}")
            // e.printStackTrace() // For debugging, consider a proper logger
        }
    }
    
    override suspend fun tryConnect(context: EnvironmentContext): Result<Connection> = runCatching {
        val ideaProcess = findIntelliJProcess()
        if (ideaProcess != null) {
            // Pass the nexusAgent instance to IntelliJConnection
            connection = IntelliJConnection(ideaProcess, nexusAgent)
            isConnected = true
            connection!!
        } else {
            throw IllegalStateException("IntelliJ IDEA not found or not running")
        }
    }
    
    override suspend fun execute(action: Action, context: ProjectContext): Result<Outcome> = runCatching {
        val conn = connection ?: throw IllegalStateException("Not connected to IntelliJ")

        // K2Script execution detection and telemetry
        // Assuming k2script commands are identified by action.data starting with "k2script:"
        // A more robust way would be a dedicated ActionType, e.g., ActionType.K2SCRIPT_EXEC
        if (action.data.startsWith("k2script:")) {
            val scriptName = action.data.substringAfter("k2script:").trim().split(" ").firstOrNull() ?: "unknown.kts"
            val startTime = System.currentTimeMillis()

            // Send EXEC_START event
            sendTelemetryEvent(
                K2ScriptTelemetryEvent(
                    timestamp = startTime,
                    scriptName = scriptName,
                    eventType = "EXEC_START",
                    platform = "INTELLIJ_PLUGIN",
                    nexusVersion = "0.1.0-SNAPSHOT", // TODO: Retrieve actual Nexus version
                    k2scriptVersion = "1.0.0-SNAPSHOT" // TODO: Retrieve actual k2script version
                )
            )

            // Execute the actual k2script command (simulated here)
            // In a real scenario, this would involve invoking k2script via IntelliJ's mechanisms
            // For now, we'll assume the action.data IS the command and simulate its execution.
            // This part of the code remains conceptual for how IntelliJAdapter would normally execute.
            // We'll simulate the k2script execution.
            // If ActionType.CUSTOM were used, conn.sendIDEAction("customAction", action.data) could be called.
            // For this example, we simulate a successful outcome or an error based on action.data.
            val originalOutcomeResult: Result<Outcome> = try {
                // Simulate execution based on action.data for testing telemetry
                if (action.data.contains("error_simulation")) {
                    Result.failure(RuntimeException("Simulated k2script execution error"))
                } else {
                    // Simulate successful execution by IntelliJConnection
                    // This would typically be: conn.sendIDEAction("runK2Script", action.data) or similar
                    val resultData = conn.sendIDEAction("runK2Script_placeholder", action.data)
                    Result.success(Outcome("k2script executed: ${action.data}, Result: $resultData"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

            val endTime = System.currentTimeMillis()
            val durationMs = endTime - startTime

            originalOutcomeResult.fold(
                onSuccess = { outcome ->
                    sendTelemetryEvent(
                        K2ScriptTelemetryEvent(
                            timestamp = endTime,
                            scriptName = scriptName,
                            eventType = "EXEC_SUCCESS",
                            durationMs = durationMs,
                            platform = "INTELLIJ_PLUGIN",
                            nexusVersion = "0.1.0-SNAPSHOT", // TODO: Retrieve actual Nexus version
                            k2scriptVersion = "1.0.0-SNAPSHOT", // TODO: Retrieve actual k2script version
                            // TODO: Populate dependencies and k2scriptFeaturesUsed if possible
                            dependencies = null, // Placeholder
                            k2scriptFeaturesUsed = listOf("GenericExecution") // Placeholder
                        )
                    )
                    outcome // Return the original outcome
                },
                onFailure = { exception ->
                    sendTelemetryEvent(
                        K2ScriptTelemetryEvent(
                            timestamp = endTime,
                            scriptName = scriptName,
                            eventType = "EXEC_ERROR",
                            durationMs = durationMs,
                            errorMessage = exception.message ?: "Unknown error",
                            errorType = exception::class.simpleName ?: "UnknownException",
                            platform = "INTELLIJ_PLUGIN",
                            nexusVersion = "0.1.0-SNAPSHOT", // TODO: Retrieve actual Nexus version
                            k2scriptVersion = "1.0.0-SNAPSHOT" // TODO: Retrieve actual k2script version
                        )
                    )
                    throw exception // Re-throw the original exception
                }
            )
        } else {
            // Default handling for non-k2script actions
            when (action.type) {
                ActionType.CODE_EDIT -> handleCodeEdit(action, conn)
                ActionType.BUILD -> handleBuild(action, conn)
                ActionType.TEST -> handleTest(action, conn)
                else -> Outcome("Action ${action.type} not supported by IntelliJ adapter")
            }
        }
    }
    
    override suspend fun openFile(path: String): Result<Unit> = runCatching {
        connection?.sendIDEAction("openFile", path)
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun editFile(path: String, edit: Edit): Result<Unit> = runCatching {
        connection?.sendIDEAction("editFile", "$path|${edit.startLine}|${edit.endLine}|${edit.newContent}")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun getOpenFiles(): Series<String> {
        return connection?.queryIDE("getOpenFiles")?.split("|")
            ?.let { Series.of(*it.toTypedArray()) } ?: Series.empty()
    }
    
    override suspend fun getCurrentSelection(): Selection? {
        val data = connection?.queryIDE("getCurrentSelection") ?: return null
        return parseIntelliJSelection(data)
    }
    
    private suspend fun findIntelliJProcess(): IntelliJProcess? {
        delay(50) // Simulate process detection
        return IntelliJProcess("idea", 23456)
    }
    
    private suspend fun handleCodeEdit(action: Action, conn: IntelliJConnection): Outcome {
        val result = conn.sendIDEAction("codeEdit", action.data)
        return Outcome("IntelliJ code edit completed: $result")
    }
    
    private suspend fun handleBuild(action: Action, conn: IntelliJConnection): Outcome {
        val result = conn.sendIDEAction("build", action.data)
        return Outcome("IntelliJ build completed: $result")
    }
    
    private suspend fun handleTest(action: Action, conn: IntelliJConnection): Outcome {
        val result = conn.sendIDEAction("runTests", action.data)
        return Outcome("IntelliJ test execution completed: $result")
    }
    
    private fun parseIntelliJSelection(data: String): Selection? {
        val parts = data.split("|")
        return if (parts.size >= 5) {
            Selection(parts[0], parts[1].toInt(), parts[2].toInt(), parts[3].toInt(), parts[4].toInt())
        } else null
    }
}

data class IntelliJProcess(val name: String, val pid: Int)

class IntelliJConnection(
    private val process: IntelliJProcess,
    private val nexusAgent: NexusAgent // Added NexusAgent dependency
) : Connection {
    override val id = "intellij-${process.pid}"
    override val type = ConnectionType.IDE
    override var isActive = true
    
    suspend fun sendIDEAction(actionName: String, params: String): String {
        delay(15) // IntelliJ actions tend to be slightly slower

        if (actionName == "runK2Script_placeholder") {
            // Construct the Action for NexusAgent
            // Assuming params is "scriptPath arg1 arg2..."
            val parts = params.split(" ").filter { it.isNotBlank() }
            if (parts.isEmpty()) {
                return "Error: No script path provided for k2script execution."
            }
            // val scriptPath = parts[0]
            // val scriptArgs = parts.drop(1)

            val k2ScriptAction = ActionNames.K2SCRIPT_EXECUTE j seriesOf(*parts.toTypedArray())

            // Execute via NexusAgent
            val outcome = nexusAgent.executeAction(k2ScriptAction)
            // Return a summary of the outcome
            return outcome.materialize().joinToString("\n")
        }

        return "IntelliJ action '$actionName' executed with params: $params"
    }
    
    suspend fun queryIDE(query: String): String {
        delay(15)
        return when (query) {
            "getOpenFiles" -> "Main.kt|Utils.kt|Test.kt"
            "getCurrentSelection" -> "Main.kt|25|30|10|45"
            else -> "Unknown IntelliJ query: $query"
        }
    }
    
    override suspend fun getCapabilities(): Series<Capability> = Series.of(
        "editor" j Series.of("edit", "refactor", "analyze", "navigate"),
        "build" j Series.of("gradle", "maven", "compiler"),
        "debug" j Series.of("breakpoints", "evaluate", "profile"),
        "vcs" j Series.of("git", "commit", "merge", "history"),
        "test" j Series.of("junit", "testng", "coverage")
    )
    
    override fun observeChanges(): Flow<Change> = flow {
        while (isActive) {
            delay(800) // IntelliJ change detection frequency
            emit(Change(ChangeType.TOOL_STATE, id, "project structure changed", System.currentTimeMillis()))
        }
    }
    
    override suspend fun close() {
        isActive = false
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NEOVIM/VIM ADAPTER
// ═══════════════════════════════════════════════════════════════════════════════

class NeovimAdapter : IDEAdapter {
    override val supportsCodeEditing = true
    override var isConnected = false
    private var connection: NeovimConnection? = null
    
    override suspend fun tryConnect(context: EnvironmentContext): Result<Connection> = runCatching {
        val nvimSocket = findNeovimSocket()
        if (nvimSocket != null) {
            connection = NeovimConnection(nvimSocket)
            isConnected = true
            connection!!
        } else {
            throw IllegalStateException("Neovim not found or not running with RPC enabled")
        }
    }
    
    override suspend fun execute(action: Action, context: ProjectContext): Result<Outcome> = runCatching {
        val conn = connection ?: throw IllegalStateException("Not connected to Neovim")
        
        when (action.type) {
            ActionType.CODE_EDIT -> handleVimEdit(action, conn)
            ActionType.FILE_OPERATION -> handleVimFileOp(action, conn)
            else -> Outcome("Action ${action.type} not supported by Neovim adapter")
        }
    }
    
    override suspend fun openFile(path: String): Result<Unit> = runCatching {
        connection?.sendVimCommand(":edit $path")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun editFile(path: String, edit: Edit): Result<Unit> = runCatching {
        connection?.sendVimCommand(":edit $path | ${edit.startLine},${edit.endLine}c")
            ?: throw IllegalStateException("Not connected")
    }
    
    override suspend fun getOpenFiles(): Series<String> {
        return connection?.sendVimCommand(":ls")?.lines()
            ?.filter { it.contains(".") }
            ?.map { it.substringAfter("\"").substringBefore("\"") }
            ?.let { Series.of(*it.toTypedArray()) } ?: Series.empty()
    }
    
    override suspend fun getCurrentSelection(): Selection? {
        val bufferInfo = connection?.sendVimCommand(":echo bufname() . ':' . line('.') . ':' . col('.')") ?: return null
        return parseVimSelection(bufferInfo)
    }
    
    private suspend fun findNeovimSocket(): String? {
        delay(30) // Simulate socket discovery
        return "/tmp/nvim.12345" // Mock socket path
    }
    
    private suspend fun handleVimEdit(action: Action, conn: NeovimConnection): Outcome {
        val result = conn.sendVimCommand(action.data)
        return Outcome("Neovim edit completed: $result")
    }
    
    private suspend fun handleVimFileOp(action: Action, conn: NeovimConnection): Outcome {
        val result = conn.sendVimCommand(action.data)
        return Outcome("Neovim file operation completed: $result")
    }
    
    private fun parseVimSelection(data: String): Selection? {
        val parts = data.split(":")
        return if (parts.size >= 3) {
            Selection(parts[0], parts[1].toInt(), parts[1].toInt(), parts[2].toInt(), parts[2].toInt())
        } else null
    }
}

class NeovimConnection(private val socket: String) : Connection {
    override val id = "neovim-${socket.hashCode()}"
    override val type = ConnectionType.IDE
    override var isActive = true
    
    suspend fun sendVimCommand(command: String): String {
        delay(5) // Vim is typically very fast
        return "Vim executed: $command"
    }
    
    override suspend fun getCapabilities(): Series<Capability> = Series.of(
        "editor" j Series.of("edit", "search", "replace", "macro"),
        "filesystem" j Series.of("read", "write"),
        "terminal" j Series.of("shell", "execute")
    )
    
    override fun observeChanges(): Flow<Change> = flow {
        while (isActive) {
            delay(500) // Vim change detection
            emit(Change(ChangeType.FILE_SYSTEM, id, "buffer changed", System.currentTimeMillis()))
        }
    }
    
    override suspend fun close() {
        isActive = false
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ADAPTER FACTORY
// ═══════════════════════════════════════════════════════════════════════════════

fun createIDEAdapters(): Series<IDEAdapter> = Series.of(
    VSCodeAdapter(),
    IntelliJAdapter(), 
    NeovimAdapter()
)